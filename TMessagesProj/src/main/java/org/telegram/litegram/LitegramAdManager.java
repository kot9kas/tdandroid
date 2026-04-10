package org.telegram.litegram;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LitegramAdManager {

    private static volatile LitegramAdManager instance;

    private static final int MAX_WAIT_MS = 30_000;
    private static final int POLL_INTERVAL_MS = 2_000;
    private static final int INITIAL_DELAY_MS = 3_000;
    private static final String PREF_LAST_AD_SHOWN = "litegram_last_ad_shown_ms";
    private static final int DEFAULT_INTERVAL_SECONDS = 3600;

    private final LitegramApi api = new LitegramApi();
    private volatile boolean fetching;
    private volatile boolean showing;
    private volatile long lastShownMs;

    public static LitegramAdManager getInstance() {
        if (instance == null) {
            synchronized (LitegramAdManager.class) {
                if (instance == null) {
                    instance = new LitegramAdManager();
                }
            }
        }
        return instance;
    }

    private LitegramAdManager() {
        lastShownMs = ApplicationLoader.applicationContext
                .getSharedPreferences("litegram_prefs", Context.MODE_PRIVATE)
                .getLong(PREF_LAST_AD_SHOWN, 0);
    }

    private boolean isUserLoggedIn() {
        boolean hasToken = LitegramDeviceToken.hasAccessToken();
        boolean hasTgId = !TextUtils.isEmpty(LitegramDeviceToken.getTelegramId());
        FileLog.d("litegram-ad: login check hasToken=" + hasToken + " hasTgId=" + hasTgId);
        return hasToken && hasTgId;
    }

    private boolean isConnected() {
        int state = ConnectionsManager.getInstance(0).getConnectionState();
        return state == ConnectionsManager.ConnectionStateConnected
                || state == ConnectionsManager.ConnectionStateUpdating;
    }

    public void tryShowAd(BaseFragment fragment) {
        FileLog.d("litegram-ad: tryShowAd called, showing=" + showing + " fetching=" + fetching);
        if (showing || fetching) return;
        if (!isUserLoggedIn()) {
            FileLog.d("litegram-ad: user not logged in, skipping");
            return;
        }

        int savedInterval = ApplicationLoader.applicationContext
                .getSharedPreferences("litegram_prefs", Context.MODE_PRIVATE)
                .getInt("litegram_ad_interval_sec", DEFAULT_INTERVAL_SECONDS);
        long intervalMs = savedInterval * 1000L;
        long elapsed = System.currentTimeMillis() - lastShownMs;
        if (lastShownMs > 0 && elapsed < intervalMs) {
            FileLog.d("litegram-ad: interval not reached, elapsed=" + elapsed + "ms, need=" + intervalMs + "ms");
            return;
        }

        AndroidUtilities.runOnUIThread(() -> {
            if (showing || fetching) return;
            if (isConnected()) {
                FileLog.d("litegram-ad: connected, fetching ad");
                fetchAndShow(fragment);
            } else {
                FileLog.d("litegram-ad: not connected, starting poll");
                waitForConnectionThenShow(fragment, System.currentTimeMillis() + MAX_WAIT_MS);
            }
        }, INITIAL_DELAY_MS);
    }

    private void waitForConnectionThenShow(BaseFragment fragment, long deadline) {
        if (showing || fetching) return;
        AndroidUtilities.runOnUIThread(() -> {
            if (showing || fetching) return;
            if (isConnected()) {
                FileLog.d("litegram-ad: connected after wait, fetching ad");
                fetchAndShow(fragment);
            } else if (System.currentTimeMillis() < deadline) {
                waitForConnectionThenShow(fragment, deadline);
            } else {
                FileLog.d("litegram-ad: timeout, trying fetch anyway");
                fetchAndShow(fragment);
            }
        }, POLL_INTERVAL_MS);
    }

    private void fetchAndShow(BaseFragment fragment) {
        if (showing || fetching) return;
        fetching = true;

        String savedToken = LitegramDeviceToken.getAccessToken();
        if (!TextUtils.isEmpty(savedToken)) {
            api.setAccessToken(savedToken);
        }

        Utilities.globalQueue.postRunnable(() -> {
            try {
                FileLog.d("litegram-ad: fetching from " + LitegramConfig.apiUrl("/advertising/active"));
                LitegramApi.AdInfo ad = api.getActiveAd();
                if (ad == null) {
                    FileLog.d("litegram-ad: no active ad returned");
                    fetching = false;
                    return;
                }
                FileLog.d("litegram-ad: got ad id=" + ad.id + " title=" + ad.title + " interval=" + ad.intervalSeconds + "s");

                ApplicationLoader.applicationContext
                        .getSharedPreferences("litegram_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putInt("litegram_ad_interval_sec", ad.intervalSeconds)
                        .apply();

                long intervalMs = ad.intervalSeconds * 1000L;
                long elapsed = System.currentTimeMillis() - lastShownMs;
                if (lastShownMs > 0 && elapsed < intervalMs) {
                    FileLog.d("litegram-ad: interval check after fetch, skipping. elapsed=" + elapsed + "ms, need=" + intervalMs + "ms");
                    fetching = false;
                    return;
                }

                Bitmap bitmap = null;
                if (!TextUtils.isEmpty(ad.imageUrl)) {
                    bitmap = downloadBitmap(ad.imageUrl);
                    FileLog.d("litegram-ad: image downloaded=" + (bitmap != null));
                }
                final Bitmap finalBitmap = bitmap;
                AndroidUtilities.runOnUIThread(() -> {
                    fetching = false;
                    markAdShown();
                    showAdBottomSheet(fragment, ad, finalBitmap);
                });
            } catch (Exception e) {
                FileLog.e("litegram-ad: fetch failed: " + e.getMessage());
                fetching = false;
            }
        });
    }

    private void markAdShown() {
        lastShownMs = System.currentTimeMillis();
        ApplicationLoader.applicationContext
                .getSharedPreferences("litegram_prefs", Context.MODE_PRIVATE)
                .edit()
                .putLong(PREF_LAST_AD_SHOWN, lastShownMs)
                .apply();
    }

    private Bitmap downloadBitmap(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            try (InputStream is = conn.getInputStream()) {
                return BitmapFactory.decodeStream(is);
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            FileLog.e("litegram-ad: image download failed: " + e.getMessage());
            return null;
        }
    }

    private void showAdBottomSheet(BaseFragment fragment, LitegramApi.AdInfo ad, Bitmap image) {
        Activity activity = fragment.getParentActivity();
        if (activity == null || activity.isFinishing()) {
            FileLog.d("litegram-ad: activity null/finishing");
            return;
        }

        showing = true;
        Context ctx = activity;

        BottomSheet.Builder builder = new BottomSheet.Builder(ctx);
        builder.setApplyBottomPadding(false);
        builder.setApplyTopPadding(false);

        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(
                AndroidUtilities.dp(20), AndroidUtilities.dp(12),
                AndroidUtilities.dp(20), AndroidUtilities.dp(24));

        // X close button
        FrameLayout topBar = new FrameLayout(ctx);
        ImageView closeBtn = new ImageView(ctx);
        closeBtn.setScaleType(ImageView.ScaleType.CENTER);
        closeBtn.setImageResource(R.drawable.ic_close_white);
        closeBtn.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        closeBtn.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4),
                AndroidUtilities.dp(4), AndroidUtilities.dp(4));
        closeBtn.setBackground(Theme.createSelectorDrawable(
                Theme.getColor(Theme.key_listSelector), 1,
                AndroidUtilities.dp(16)));
        topBar.addView(closeBtn, new FrameLayout.LayoutParams(
                AndroidUtilities.dp(32), AndroidUtilities.dp(32),
                Gravity.END | Gravity.TOP));
        container.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Image
        if (image != null) {
            ImageView imageView = new ImageView(ctx);
            imageView.setImageBitmap(image);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setClipToOutline(true);
            imageView.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                            AndroidUtilities.dp(12));
                }
            });
            LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(180));
            imgParams.topMargin = AndroidUtilities.dp(4);
            container.addView(imageView, imgParams);
        }

        // Title
        TextView titleView = new TextView(ctx);
        titleView.setText(ad.title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = AndroidUtilities.dp(14);
        container.addView(titleView, titleParams);

        // Description
        if (!TextUtils.isEmpty(ad.description)) {
            TextView descView = new TextView(ctx);
            descView.setText(ad.description);
            descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            descView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            descView.setGravity(Gravity.CENTER);
            descView.setLineSpacing(AndroidUtilities.dp(2), 1f);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            descParams.topMargin = AndroidUtilities.dp(8);
            container.addView(descView, descParams);
        }

        // Link button
        if (!TextUtils.isEmpty(ad.linkUrl)) {
            TextView linkButton = new TextView(ctx);
            linkButton.setText("Open");
            linkButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            linkButton.setTextColor(Color.WHITE);
            linkButton.setGravity(Gravity.CENTER);
            linkButton.setTypeface(AndroidUtilities.bold());
            linkButton.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
            android.graphics.drawable.GradientDrawable linkBg = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0xFF5B2D8E, 0xFF9E84B6});
            linkBg.setCornerRadius(AndroidUtilities.dp(8));
            linkButton.setBackground(linkBg);
            linkButton.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(ad.linkUrl));
                    activity.startActivity(intent);
                } catch (Exception e) {
                    FileLog.e("litegram-ad: link open failed", e);
                }
            });
            LinearLayout.LayoutParams linkParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            linkParams.topMargin = AndroidUtilities.dp(16);
            container.addView(linkButton, linkParams);
        }

        builder.setCustomView(container);
        BottomSheet sheet = builder.create();
        sheet.setOnDismissListener(d -> showing = false);
        closeBtn.setOnClickListener(v -> sheet.dismiss());

        fragment.showDialog(sheet, true, null);
        FileLog.d("litegram-ad: showDialog called, id=" + ad.id);
    }
}
