package org.telegram.litegram;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

public class LitegramActivity extends BaseFragment {

    private View saveTrafficItem;
    private TextView statusView;

    public LitegramActivity() {
        super();
    }

    public LitegramActivity(Bundle args) {
        super(args);
    }

    /** Цвета из текущей темы Telegram (на момент создания экрана). */
    private int c(int key) {
        return getThemedColor(key);
    }

    @Override
    public View createView(Context context) {
        boolean isTab = getArguments() != null && getArguments().getBoolean("hasMainTabs", false);
        if (isTab) {
            actionBar.setVisibility(View.GONE);
        } else {
            applyLitegramActionBar();
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setAllowOverlayTitle(true);
            actionBar.setTitle(LocaleController.getString(R.string.MainTabsLitegram));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(c(Theme.key_windowBackgroundGray));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        if (isTab) {
            content.addView(createTabTitle(context));
        }
        content.addView(wrapWithSideMargin(context, createProfileSection(context), 16));
        content.addView(createSpacer(context, 12));
        content.addView(wrapWithSideMargin(context, createMenuSection(context), 16));
        content.addView(createSpacer(context, 20));
        content.addView(wrapWithSideMargin(context, createTryAllButton(context), 16));
        content.addView(createSpacer(context, 24));

        fragmentView = scrollView;

        boolean shouldHighlight = getArguments() != null
                && getArguments().getBoolean("highlightSaveTraffic", false);
        if (shouldHighlight && saveTrafficItem != null) {
            saveTrafficItem.postDelayed(() -> highlightView(saveTrafficItem), 400);
        }

        return fragmentView;
    }

    private void applyLitegramActionBar() {
        actionBar.setBackgroundColor(c(Theme.key_actionBarDefault));
        actionBar.setTitleColor(c(Theme.key_actionBarDefaultTitle));
        actionBar.setItemsColor(c(Theme.key_actionBarDefaultIcon), false);
        actionBar.setCastShadows(false);
    }

    private View createTabTitle(Context context) {
        TextView tv = new TextView(context);
        tv.setText(LocaleController.getString(R.string.MainTabsLitegram));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setTextColor(c(Theme.key_windowBackgroundWhiteBlackText));
        tv.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        tv.setPadding(AndroidUtilities.dp(18), AndroidUtilities.statusBarHeight + AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        return tv;
    }

    private static LinearLayout wrapWithSideMargin(Context context, View child, int marginDp) {
        LinearLayout wrap = new LinearLayout(context);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int m = AndroidUtilities.dp(marginDp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = m;
        lp.rightMargin = m;
        wrap.addView(child, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        wrap.setLayoutParams(lp);
        return wrap;
    }

    private void highlightView(View view) {
        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofFloat(0f, 1f, 0f, 1f, 0f);
        anim.setDuration(1500);
        anim.addUpdateListener(animation -> {
            float v = (float) animation.getAnimatedValue();
            int alpha = (int) (v * 50);
            int accent = 0xFF7B5EA7;
            view.setBackgroundColor(Color.argb(alpha, Color.red(accent), Color.green(accent), Color.blue(accent)));
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                view.setBackgroundColor(Color.TRANSPARENT);
            }
        });
        anim.start();
    }

    private View createProfileSection(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF5B2D8E, 0xFF9E84B6});
        bg.setCornerRadius(AndroidUtilities.dp(24));
        card.setBackground(bg);

        card.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(24),
                AndroidUtilities.dp(20), AndroidUtilities.dp(22));

        TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();

        BackupImageView avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(AndroidUtilities.dp(40));
        if (user != null) {
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(user);
            avatarView.getImageReceiver().setCurrentAccount(currentAccount);
            avatarView.setForUserOrChat(user, avatarDrawable);
        }
        card.addView(avatarView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL));

        TextView nameView = new TextView(context);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        nameView.setTextColor(Color.WHITE);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setGravity(Gravity.CENTER);
        nameView.setText(user != null ? UserObject.getUserName(user) : "");
        LinearLayout.LayoutParams nameParams = LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
        nameParams.topMargin = AndroidUtilities.dp(12);
        card.addView(nameView, nameParams);

        TextView idView = new TextView(context);
        idView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        idView.setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.8f)));
        idView.setGravity(Gravity.CENTER);
        idView.setText(user != null ? "ID: " + user.id : "");
        LinearLayout.LayoutParams idParams = LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
        idParams.topMargin = AndroidUtilities.dp(4);
        card.addView(idView, idParams);

        statusView = new TextView(context);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        statusView.setTypeface(AndroidUtilities.bold());
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(6),
                AndroidUtilities.dp(14), AndroidUtilities.dp(6));
        applySubscriptionBadge();
        LinearLayout.LayoutParams statusParams = LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
        statusParams.topMargin = AndroidUtilities.dp(14);
        card.addView(statusView, statusParams);

        LitegramController.getInstance().refreshSubscription(() ->
                AndroidUtilities.runOnUIThread(this::applySubscriptionBadge));

        return card;
    }

    private void applySubscriptionBadge() {
        if (statusView == null) {
            return;
        }
        statusView.setTypeface(AndroidUtilities.bold());
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        statusView.setAllCaps(true);
        statusView.setLetterSpacing(0.04f);
        statusView.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(8),
                AndroidUtilities.dp(18), AndroidUtilities.dp(8));
        if (LitegramConfig.isSubscriptionActive()) {
            statusView.setText(LocaleController.getString(R.string.LitegramConnPremium));
            statusView.setTextColor(Color.WHITE);
            GradientDrawable badge = new GradientDrawable();
            badge.setCornerRadius(AndroidUtilities.dp(24));
            badge.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            badge.setColors(new int[]{0xFF9B74FF, 0xFF6A45C1});
            badge.setStroke(AndroidUtilities.dp(1), 0x66FFFFFF);
            statusView.setBackground(badge);
        } else {
            statusView.setText(LocaleController.getString(R.string.LitegramConnFree));
            statusView.setTextColor(Color.WHITE);
            GradientDrawable badge = new GradientDrawable();
            badge.setCornerRadius(AndroidUtilities.dp(24));
            badge.setColor(0x2BFFFFFF);
            badge.setStroke(AndroidUtilities.dp(1), 0x5CFFFFFF);
            statusView.setBackground(badge);
        }
    }

    private View createMenuSection(Context context) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setCornerRadius(AndroidUtilities.dp(16));
        panelBg.setColor(c(Theme.key_windowBackgroundWhite));
        panel.setBackground(panelBg);
        int ph = AndroidUtilities.dp(4);
        panel.setPadding(0, ph, 0, ph);

        saveTrafficItem = createToggleItem(context, R.drawable.msg_download,
                LocaleController.getString(R.string.LitegramSaveTraffic),
                LocaleController.getString(R.string.LitegramSaveTrafficDesc),
                LitegramConfig.isSaveTrafficEnabled(),
                (enabled) -> LitegramConfig.setSaveTrafficEnabled(enabled));
        panel.addView(saveTrafficItem);

        panel.addView(createPanelDivider(context));

        panel.addView(createMenuItem(context, R.drawable.litegram_ic_connection,
                0xFF26A69A,
                LocaleController.getString(R.string.LitegramProtection),
                LocaleController.getString(R.string.LitegramProtectionDesc),
                () -> presentFragment(new LitegramConnectionActivity())));

        panel.addView(createPanelDivider(context));

        panel.addView(createMenuItem(context, R.drawable.msg_discussion,
                0xFF42A5F5,
                LocaleController.getString(R.string.LitegramChats),
                LocaleController.getString(R.string.LitegramChatsDesc),
                () -> presentFragment(new LitegramChatsActivity())));

        panel.addView(createPanelDivider(context));

        panel.addView(createMenuItem(context, R.drawable.litegram_ic_export,
                0xFF5C6BC0,
                LocaleController.getString(R.string.LitegramSessionExport),
                LocaleController.getString(R.string.LitegramSessionExportHint),
                () -> LitegramSessionTransfer.exportAndShareSessionPack(this, currentAccount)));

        panel.addView(createPanelDivider(context));

        panel.addView(createMenuItem(context, R.drawable.msg_help,
                0xFFFF9800,
                LocaleController.getString(R.string.LitegramSupport),
                LocaleController.getString(R.string.LitegramSupportDesc),
                this::showSupportSheet));

        return panel;
    }

    private View createPanelDivider(Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(c(Theme.key_divider));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        params.leftMargin = AndroidUtilities.dp(72);
        params.rightMargin = AndroidUtilities.dp(16);
        divider.setLayoutParams(params);
        return divider;
    }

    private View createMenuItem(Context context, int iconRes, int iconBoxColor,
                                String title, String subtitle, Runnable onClick) {
        FrameLayout item = new FrameLayout(context);
        item.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12),
                AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        item.setBackground(Theme.createSelectorDrawable(c(Theme.key_listSelector), 2));
        item.setOnClickListener(v -> onClick.run());

        FrameLayout iconBox = new FrameLayout(context);
        GradientDrawable ibg = new GradientDrawable();
        ibg.setCornerRadius(AndroidUtilities.dp(12));
        ibg.setColor(iconBoxColor);
        iconBox.setBackground(ibg);
        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        Drawable d = context.getResources().getDrawable(iconRes).mutate();
        d.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        icon.setImageDrawable(d);
        iconBox.addView(icon, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        item.addView(iconBox, LayoutHelper.createFrame(44, 44,
                Gravity.CENTER_VERTICAL | Gravity.START));

        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams textParams = LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL, 72, 0, 36, 0);
        item.addView(textContainer, textParams);

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(c(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setText(title);
        textContainer.addView(titleView);

        if (subtitle != null) {
            TextView subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitleView.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText2));
            subtitleView.setText(subtitle);
            LinearLayout.LayoutParams stParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stParams.topMargin = AndroidUtilities.dp(2);
            textContainer.addView(subtitleView, stParams);
        }

        ImageView arrow = new ImageView(context);
        arrow.setScaleType(ImageView.ScaleType.CENTER);
        Drawable arrowD = context.getResources().getDrawable(R.drawable.msg_arrowright).mutate();
        arrowD.setColorFilter(new PorterDuffColorFilter(
                c(Theme.key_windowBackgroundWhiteGrayText4), PorterDuff.Mode.SRC_IN));
        arrow.setImageDrawable(arrowD);
        item.addView(arrow, LayoutHelper.createFrame(24, 24,
                Gravity.CENTER_VERTICAL | Gravity.END));

        return item;
    }

    private interface ToggleCallback {
        void onToggle(boolean enabled);
    }

    /**
     * Telegram {@link Switch} не задаёт onMeasure: при WRAP_CONTENT в FrameLayout высота может
     * стать равной всему доступному пространству — строка меню «раздувается» и остальные пункты
     * уезжают за экран. Задаём размер как в {@link org.telegram.ui.Cells.TextCell} (37×20 dp).
     */
    private View createToggleItem(Context context, int iconRes, String title, String subtitle,
                                  boolean initialState, ToggleCallback callback) {
        FrameLayout item = new FrameLayout(context);
        item.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12),
                AndroidUtilities.dp(12), AndroidUtilities.dp(12));

        FrameLayout iconBox = new FrameLayout(context);
        GradientDrawable ibg = new GradientDrawable();
        ibg.setCornerRadius(AndroidUtilities.dp(12));
        ibg.setColor(0xFF4CAF50);
        iconBox.setBackground(ibg);
        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        Drawable d = context.getResources().getDrawable(iconRes).mutate();
        d.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        icon.setImageDrawable(d);
        iconBox.addView(icon, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        item.addView(iconBox, LayoutHelper.createFrame(44, 44,
                Gravity.CENTER_VERTICAL | Gravity.START));

        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams textParams = LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL, 72, 0, 58, 0);
        item.addView(textContainer, textParams);

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(c(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setText(title);
        textContainer.addView(titleView);

        if (subtitle != null) {
            TextView subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitleView.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText2));
            subtitleView.setText(subtitle);
            LinearLayout.LayoutParams stParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stParams.topMargin = AndroidUtilities.dp(2);
            textContainer.addView(subtitleView, stParams);
        }

        Switch toggle = new Switch(context, getResourceProvider());
        toggle.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked,
                Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        toggle.setChecked(initialState, false);
        toggle.setOnCheckedChangeListener((view, isChecked) -> callback.onToggle(isChecked));
        item.addView(toggle, LayoutHelper.createFrame(37, 20,
                Gravity.CENTER_VERTICAL | Gravity.END));

        item.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked(), true));

        return item;
    }

    private static final String LITEGRAM_BOT_USERNAME = "Litegram_robot";
    private static final String SUPPORT_BOT_USERNAME = "Litegram_support_bot";
    private static final String SUPPORT_EMAIL = "support@bubavpn.com";
    private static final String LITEGRAM_CHANNEL_USERNAME = "litegram_news";
    private static final String LITEGRAM_CHAT_USERNAME = "litegram_chat";

    private void openChat(String username) {
        try {
            MessagesController.getInstance(currentAccount).getUserNameResolver().resolve(
                    username, null, peerId -> {
                        if (peerId == null || peerId == 0) {
                            FileLog.e("litegram: failed to resolve @" + username);
                            return;
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            Bundle args = new Bundle();
                            if (peerId > 0) {
                                args.putLong("user_id", peerId);
                            } else {
                                args.putLong("chat_id", -peerId);
                            }
                            presentFragment(new ChatActivity(args));
                        });
                    });
        } catch (Exception e) {
            FileLog.e("litegram: openChat failed for @" + username, e);
        }
    }

    private void showSupportSheet() {
        Context ctx = getParentActivity();
        if (ctx == null) return;

        BottomSheet.Builder builder = new BottomSheet.Builder(ctx);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(20));

        TextView title = new TextView(ctx);
        title.setText(LocaleController.getString(R.string.LitegramSupport));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider()));
        title.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(8),
                AndroidUtilities.dp(22), AndroidUtilities.dp(16));
        root.addView(title);

        final BottomSheet[] sheetHolder = new BottomSheet[1];

        root.addView(createSupportRow(ctx, "\u2709\uFE0F",
                LocaleController.getString(R.string.LitegramSupportEmail),
                SUPPORT_EMAIL, () -> {
                    if (sheetHolder[0] != null) sheetHolder[0].dismiss();
                    try {
                        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                        emailIntent.setData(Uri.parse("mailto:" + SUPPORT_EMAIL));
                        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Litegram Support");
                        ctx.startActivity(emailIntent);
                    } catch (Exception e) {
                        Toast.makeText(ctx,
                                LocaleController.getString(R.string.LitegramSupportEmailCopied),
                                Toast.LENGTH_SHORT).show();
                    }
                }));

        root.addView(createSupportRow(ctx, "\uD83D\uDCAC",
                LocaleController.getString(R.string.LitegramSupportTelegram),
                "@" + SUPPORT_BOT_USERNAME, () -> {
                    if (sheetHolder[0] != null) sheetHolder[0].dismiss();
                    openSupportBot();
                }));

        builder.setCustomView(root);
        sheetHolder[0] = builder.create();
        showDialog(sheetHolder[0]);
    }

    private View createSupportRow(Context ctx, String emoji, String title,
                                  String subtitle, Runnable onClick) {
        FrameLayout row = new FrameLayout(ctx);
        row.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(14),
                AndroidUtilities.dp(22), AndroidUtilities.dp(14));
        row.setBackground(Theme.createSelectorDrawable(
                Theme.getColor(Theme.key_listSelector, getResourceProvider()), 2));
        row.setOnClickListener(v -> onClick.run());

        LinearLayout leftPart = new LinearLayout(ctx);
        leftPart.setOrientation(LinearLayout.HORIZONTAL);
        leftPart.setGravity(Gravity.CENTER_VERTICAL);

        TextView emojiView = new TextView(ctx);
        emojiView.setText(emoji);
        emojiView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        leftPart.addView(emojiView, new LinearLayout.LayoutParams(
                AndroidUtilities.dp(36), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(ctx);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider()));
        textCol.addView(titleView);

        TextView subtitleView = new TextView(ctx);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, getResourceProvider()));
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stLp.topMargin = AndroidUtilities.dp(2);
        textCol.addView(subtitleView, stLp);

        leftPart.addView(textCol, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(leftPart, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL));

        ImageView arrow = new ImageView(ctx);
        arrow.setScaleType(ImageView.ScaleType.CENTER);
        Drawable arrowD = ctx.getResources().getDrawable(R.drawable.msg_arrowright).mutate();
        arrowD.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, getResourceProvider()),
                PorterDuff.Mode.SRC_IN));
        arrow.setImageDrawable(arrowD);
        row.addView(arrow, LayoutHelper.createFrame(24, 24,
                Gravity.CENTER_VERTICAL | Gravity.END));

        return row;
    }

    private void openSupportBot() {
        try {
            MessagesController.getInstance(currentAccount).getUserNameResolver().resolve(
                    SUPPORT_BOT_USERNAME, null, peerId -> {
                        if (peerId == null || peerId == 0) {
                            FileLog.e("litegram: failed to resolve bot @" + SUPPORT_BOT_USERNAME);
                            return;
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            Bundle args = new Bundle();
                            args.putLong("user_id", peerId);
                            ChatActivity chatActivity = new ChatActivity(args);
                            presentFragment(chatActivity);

                            SendMessagesHelper.getInstance(currentAccount).sendMessage(
                                    SendMessagesHelper.SendMessageParams.of("/start", peerId)
                            );
                        });
                    });
        } catch (Exception e) {
            FileLog.e("litegram: openSupportBot failed", e);
        }
    }

    private View createTryAllButton(Context context) {
        FrameLayout container = new FrameLayout(context);

        TextView button = new TextView(context);
        button.setText("\u2728 " + LocaleController.getString(R.string.LitegramTryAllFeatures));
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        button.setTextColor(Color.WHITE);
        button.setTypeface(AndroidUtilities.bold());
        button.setGravity(Gravity.CENTER);
        button.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(14),
                AndroidUtilities.dp(24), AndroidUtilities.dp(14));

        GradientDrawable btnBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF5B2D8E, 0xFF9E84B6});
        btnBg.setCornerRadius(AndroidUtilities.dp(14));
        button.setBackground(btnBg);

        button.setOnClickListener(v -> openBotChat());

        container.addView(button, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        return container;
    }

    private void openBotChat() {
        try {
            MessagesController.getInstance(currentAccount).getUserNameResolver().resolve(
                    LITEGRAM_BOT_USERNAME, null, peerId -> {
                        if (peerId == null || peerId == 0) {
                            FileLog.e("litegram: failed to resolve bot @" + LITEGRAM_BOT_USERNAME);
                            return;
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            Bundle args = new Bundle();
                            args.putLong("user_id", peerId);
                            ChatActivity chatActivity = new ChatActivity(args);
                            presentFragment(chatActivity);

                            SendMessagesHelper.getInstance(currentAccount).sendMessage(
                                    SendMessagesHelper.SendMessageParams.of("/start", peerId)
                            );
                        });
                    });
        } catch (Exception e) {
            FileLog.e("litegram: openBotChat failed", e);
        }
    }

    private View createSpacer(Context context, int heightDp) {
        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(heightDp)));
        return spacer;
    }
}
