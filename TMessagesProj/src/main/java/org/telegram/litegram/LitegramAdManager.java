package org.telegram.litegram;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LitegramAdManager {

    private static volatile LitegramAdManager instance;

    private static final long SHOW_INTERVAL_MS = 60 * 60 * 1000; // 1 hour
    private static final String KEY_LAST_AD_SHOWN = "litegram_last_ad_shown";
    private static final String KEY_LAST_AD_ID = "litegram_last_ad_id";

    private final LitegramApi api = new LitegramApi();
    private volatile boolean fetching;

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

    private LitegramAdManager() {}

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences("litegram_prefs", Context.MODE_PRIVATE);
    }

    private boolean shouldShow() {
        long lastShown = getPrefs().getLong(KEY_LAST_AD_SHOWN, 0);
        return System.currentTimeMillis() - lastShown >= SHOW_INTERVAL_MS;
    }

    private void markShown(String adId) {
        getPrefs().edit()
                .putLong(KEY_LAST_AD_SHOWN, System.currentTimeMillis())
                .putString(KEY_LAST_AD_ID, adId)
                .apply();
    }

    public void tryShowAd(BaseFragment fragment) {
        if (!shouldShow() || fetching) return;
        fetching = true;

        String savedToken = LitegramDeviceToken.getAccessToken();
        if (!TextUtils.isEmpty(savedToken)) {
            api.setAccessToken(savedToken);
        }

        Utilities.globalQueue.postRunnable(() -> {
            try {
                LitegramApi.AdInfo ad = api.getActiveAd();
                if (ad == null) {
                    fetching = false;
                    return;
                }
                Bitmap bitmap = null;
                if (!TextUtils.isEmpty(ad.imageUrl)) {
                    bitmap = downloadBitmap(ad.imageUrl);
                }
                final Bitmap finalBitmap = bitmap;
                AndroidUtilities.runOnUIThread(() -> {
                    fetching = false;
                    showAdDialog(fragment, ad, finalBitmap);
                });
            } catch (Exception e) {
                FileLog.e("litegram: ad fetch failed", e);
                fetching = false;
            }
        });
    }

    private Bitmap downloadBitmap(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            try (InputStream is = conn.getInputStream()) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 1;
                return BitmapFactory.decodeStream(is, null, opts);
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            FileLog.e("litegram: ad image download failed", e);
            return null;
        }
    }

    private void showAdDialog(BaseFragment fragment, LitegramApi.AdInfo ad, Bitmap image) {
        Activity activity = fragment.getParentActivity();
        if (activity == null || activity.isFinishing()) return;

        Context ctx = activity;
        BottomSheet.Builder builder = new BottomSheet.Builder(ctx);

        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(
                AndroidUtilities.dp(20), AndroidUtilities.dp(16),
                AndroidUtilities.dp(20), AndroidUtilities.dp(20));

        // Close button row
        FrameLayout topBar = new FrameLayout(ctx);
        TextView closeBtn = new TextView(ctx);
        closeBtn.setText("\u2715");
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        closeBtn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        closeBtn.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4),
                AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        topBar.addView(closeBtn, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.TOP));
        container.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Image
        if (image != null) {
            ImageView imageView = new ImageView(ctx);
            imageView.setImageBitmap(image);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setAdjustViewBounds(true);

            GradientDrawable imgBg = new GradientDrawable();
            imgBg.setCornerRadius(AndroidUtilities.dp(12));
            imageView.setClipToOutline(true);
            imageView.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                            AndroidUtilities.dp(12));
                }
            });

            LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(200));
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
        titleParams.topMargin = AndroidUtilities.dp(16);
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
            linkButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                    AndroidUtilities.dp(8),
                    Theme.getColor(Theme.key_featuredStickers_addButton),
                    Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
            linkButton.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(ad.linkUrl));
                    activity.startActivity(intent);
                } catch (Exception e) {
                    FileLog.e("litegram: ad link open failed", e);
                }
            });
            LinearLayout.LayoutParams linkParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            linkParams.topMargin = AndroidUtilities.dp(16);
            container.addView(linkButton, linkParams);
        }

        // Close text button
        TextView dismissButton = new TextView(ctx);
        dismissButton.setText("Close");
        dismissButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        dismissButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        dismissButton.setGravity(Gravity.CENTER);
        dismissButton.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(4));
        LinearLayout.LayoutParams dismissParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dismissParams.topMargin = AndroidUtilities.dp(8);
        container.addView(dismissButton, dismissParams);

        builder.setCustomView(container);
        BottomSheet sheet = builder.create();

        closeBtn.setOnClickListener(v -> sheet.dismiss());
        dismissButton.setOnClickListener(v -> sheet.dismiss());

        fragment.showDialog(sheet);
        markShown(ad.id);
        FileLog.d("litegram: ad shown, id=" + ad.id);
    }
}
