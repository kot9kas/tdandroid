package org.telegram.litegram;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LitegramAdManager {

    private static volatile LitegramAdManager instance;

    private final LitegramApi api = new LitegramApi();
    private volatile boolean fetching;
    private volatile boolean adShownThisSession;

    private volatile LitegramApi.AdInfo pendingAd;
    private volatile Bitmap pendingBitmap;

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

    /**
     * Called from LitegramController.init() to pre-fetch the ad in background.
     */
    public void prefetch() {
        if (fetching) return;
        fetching = true;

        String savedToken = LitegramDeviceToken.getAccessToken();
        if (!TextUtils.isEmpty(savedToken)) {
            api.setAccessToken(savedToken);
        }

        Utilities.globalQueue.postRunnable(() -> {
            try {
                LitegramApi.AdInfo ad = api.getActiveAd();
                if (ad != null) {
                    Bitmap bitmap = null;
                    if (!TextUtils.isEmpty(ad.imageUrl)) {
                        bitmap = downloadBitmap(ad.imageUrl);
                    }
                    pendingAd = ad;
                    pendingBitmap = bitmap;
                    FileLog.d("litegram: ad prefetched, id=" + ad.id);
                }
            } catch (Exception e) {
                FileLog.e("litegram: ad prefetch failed", e);
            } finally {
                fetching = false;
            }
        });
    }

    /**
     * Shows the ad if one was prefetched and not yet shown this session.
     */
    public void tryShowAd(BaseFragment fragment) {
        if (adShownThisSession) return;

        if (pendingAd != null) {
            LitegramApi.AdInfo ad = pendingAd;
            Bitmap bitmap = pendingBitmap;
            pendingAd = null;
            pendingBitmap = null;
            adShownThisSession = true;
            AndroidUtilities.runOnUIThread(() -> showAdDialog(fragment, ad, bitmap));
            return;
        }

        if (fetching) {
            AndroidUtilities.runOnUIThread(() -> tryShowAd(fragment), 500);
            return;
        }

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
                adShownThisSession = true;
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
                return BitmapFactory.decodeStream(is);
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
        Dialog dialog = new Dialog(ctx);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        FrameLayout root = new FrameLayout(ctx);

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                AndroidUtilities.dp(20), AndroidUtilities.dp(16),
                AndroidUtilities.dp(20), AndroidUtilities.dp(20));

        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
        cardBg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cardBg.setCornerRadius(AndroidUtilities.dp(16));
        card.setBackground(cardBg);
        card.setElevation(AndroidUtilities.dp(8));

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
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        topBar.addView(closeBtn, new FrameLayout.LayoutParams(
                AndroidUtilities.dp(32), AndroidUtilities.dp(32),
                Gravity.END | Gravity.TOP));
        card.addView(topBar, new LinearLayout.LayoutParams(
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
            card.addView(imageView, imgParams);
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
        card.addView(titleView, titleParams);

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
            card.addView(descView, descParams);
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
            card.addView(linkButton, linkParams);
        }

        int maxWidth = Math.min(AndroidUtilities.displaySize.x - AndroidUtilities.dp(48),
                AndroidUtilities.dp(340));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                maxWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(card, cardParams);

        dialog.setContentView(root);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0x66000000));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.CENTER);
        }

        fragment.showDialog(dialog);
        FileLog.d("litegram: ad shown, id=" + ad.id);
    }
}
