package org.telegram.litegram;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
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
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

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

public class LitegramActivity extends BaseFragment {

    private static final int COLOR_HEADER_START = 0xFF0D0618;
    private static final int COLOR_HEADER_END = 0xFF2D1654;
    private static final int COLOR_ACCENT = 0xFF7B5EA7;
    private static final int COLOR_ACCENT_LIGHT = 0xFF9E84B6;
    private static final int COLOR_BTN_START = 0xFF5B2D8E;
    private static final int COLOR_BTN_END = 0xFF9E84B6;
    private static final int COLOR_TOGGLE_ON_TRACK = 0xFF7B5EA7;
    private static final int COLOR_TOGGLE_ON_THUMB = 0xFFFFFFFF;
    private static final int COLOR_TOGGLE_OFF_TRACK = 0xFF555555;
    private static final int COLOR_TOGGLE_OFF_THUMB = 0xFFCCCCCC;

    private View saveTrafficItem;
    private TextView statusView;

    public LitegramActivity() {
        super();
    }

    public LitegramActivity(Bundle args) {
        super(args);
    }

    @Override
    public View createView(Context context) {
        boolean isTab = getArguments() != null && getArguments().getBoolean("hasMainTabs", false);
        if (isTab) {
            actionBar.setVisibility(View.GONE);
        } else {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setAllowOverlayTitle(true);
            actionBar.setTitle("Litegram");
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
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(createProfileSection(context));
        content.addView(createSpacer(context, 12));
        content.addView(createMenuSection(context));
        content.addView(createSpacer(context, 24));
        content.addView(createTryAllButton(context));
        content.addView(createSpacer(context, 24));

        fragmentView = scrollView;

        boolean shouldHighlight = getArguments() != null
                && getArguments().getBoolean("highlightSaveTraffic", false);
        if (shouldHighlight && saveTrafficItem != null) {
            saveTrafficItem.postDelayed(() -> highlightView(saveTrafficItem), 400);
        }

        return fragmentView;
    }

    private void highlightView(View view) {
        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofFloat(0f, 1f, 0f, 1f, 0f);
        anim.setDuration(1500);
        anim.addUpdateListener(animation -> {
            float v = (float) animation.getAnimatedValue();
            int alpha = (int) (v * 50);
            view.setBackgroundColor(Color.argb(alpha, 123, 94, 167));
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            }
        });
        anim.start();
    }

    private View createProfileSection(Context context) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setGravity(Gravity.CENTER_HORIZONTAL);

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{COLOR_HEADER_START, COLOR_HEADER_END});
        section.setBackground(bg);

        int statusBarPadding = AndroidUtilities.statusBarHeight + AndroidUtilities.dp(24);
        section.setPadding(AndroidUtilities.dp(16), statusBarPadding,
                AndroidUtilities.dp(16), AndroidUtilities.dp(28));

        TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();

        BackupImageView avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(AndroidUtilities.dp(40));
        if (user != null) {
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(user);
            avatarView.getImageReceiver().setCurrentAccount(currentAccount);
            avatarView.setForUserOrChat(user, avatarDrawable);
        }
        section.addView(avatarView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_HORIZONTAL));

        TextView nameView = new TextView(context);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        nameView.setTextColor(Color.WHITE);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setGravity(Gravity.CENTER);
        nameView.setText(user != null ? UserObject.getUserName(user) : "");
        LinearLayout.LayoutParams nameParams = LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
        nameParams.topMargin = AndroidUtilities.dp(12);
        section.addView(nameView, nameParams);

        TextView idView = new TextView(context);
        idView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        idView.setTextColor(0x99FFFFFF);
        idView.setGravity(Gravity.CENTER);
        idView.setText(user != null ? "ID: " + user.id : "");
        LinearLayout.LayoutParams idParams = LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
        idParams.topMargin = AndroidUtilities.dp(4);
        section.addView(idView, idParams);

        statusView = new TextView(context);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        statusView.setTypeface(AndroidUtilities.bold());
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(5),
                AndroidUtilities.dp(14), AndroidUtilities.dp(5));
        applySubscriptionBadge();
        LinearLayout.LayoutParams statusParams = LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
        statusParams.topMargin = AndroidUtilities.dp(12);
        section.addView(statusView, statusParams);

        LitegramController.getInstance().refreshSubscription(() ->
                AndroidUtilities.runOnUIThread(this::applySubscriptionBadge));

        return section;
    }

    private void applySubscriptionBadge() {
        if (statusView == null) return;
        if (LitegramConfig.isSubscriptionActive()) {
            statusView.setText("\u2B50 " + LocaleController.getString(R.string.LitegramConnPremium));
            statusView.setTextColor(0xFFF5E6FF);
            GradientDrawable badge = new GradientDrawable();
            badge.setCornerRadius(AndroidUtilities.dp(12));
            badge.setColor(COLOR_ACCENT);
            statusView.setBackground(badge);
        } else {
            statusView.setText(LocaleController.getString(R.string.LitegramConnFree));
            statusView.setTextColor(0xCCFFFFFF);
            GradientDrawable badge = new GradientDrawable();
            badge.setCornerRadius(AndroidUtilities.dp(12));
            badge.setColor(0x44FFFFFF);
            statusView.setBackground(badge);
        }
    }

    private View createMenuSection(Context context) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        saveTrafficItem = createToggleItem(context, R.drawable.msg_download,
                LocaleController.getString(R.string.LitegramSaveTraffic),
                LocaleController.getString(R.string.LitegramSaveTrafficDesc),
                LitegramConfig.isSaveTrafficEnabled(),
                (enabled) -> LitegramConfig.setSaveTrafficEnabled(enabled));
        section.addView(saveTrafficItem);

        section.addView(createDivider(context));

        section.addView(createMenuItem(context, R.drawable.msg_secret,
                LocaleController.getString(R.string.LitegramConnection),
                LocaleController.getString(R.string.LitegramConnectionDesc),
                () -> presentFragment(new LitegramConnectionActivity())));

        section.addView(createDivider(context));

        section.addView(createMenuItem(context, R.drawable.msg_help,
                LocaleController.getString(R.string.LitegramSupport),
                LocaleController.getString(R.string.LitegramSupportDesc),
                this::showSupportSheet));

        return section;
    }

    private View createMenuItem(Context context, int iconRes, String title, String subtitle, Runnable onClick) {
        FrameLayout item = new FrameLayout(context);
        item.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(14),
                AndroidUtilities.dp(21), AndroidUtilities.dp(14));
        item.setBackground(Theme.createSelectorDrawable(
                Theme.getColor(Theme.key_listSelector), 2));
        item.setOnClickListener(v -> onClick.run());

        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        Drawable d = context.getResources().getDrawable(iconRes).mutate();
        d.setColorFilter(new PorterDuffColorFilter(COLOR_ACCENT_LIGHT, PorterDuff.Mode.SRC_IN));
        icon.setImageDrawable(d);
        item.addView(icon, LayoutHelper.createFrame(24, 24,
                Gravity.CENTER_VERTICAL | Gravity.START));

        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams textParams = LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL, 52, 0, 40, 0);
        item.addView(textContainer, textParams);

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setText(title);
        textContainer.addView(titleView);

        if (subtitle != null) {
            TextView subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            subtitleView.setText(subtitle);
            LinearLayout.LayoutParams stParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stParams.topMargin = AndroidUtilities.dp(2);
            textContainer.addView(subtitleView, stParams);
        }

        ImageView arrow = new ImageView(context);
        arrow.setScaleType(ImageView.ScaleType.CENTER);
        Drawable arrowD = context.getResources().getDrawable(R.drawable.msg_arrowright).mutate();
        arrowD.setColorFilter(new PorterDuffColorFilter(COLOR_ACCENT_LIGHT, PorterDuff.Mode.SRC_IN));
        arrow.setImageDrawable(arrowD);
        item.addView(arrow, LayoutHelper.createFrame(24, 24,
                Gravity.CENTER_VERTICAL | Gravity.END));

        return item;
    }

    private interface ToggleCallback {
        void onToggle(boolean enabled);
    }

    private View createToggleItem(Context context, int iconRes, String title, String subtitle,
                                  boolean initialState, ToggleCallback callback) {
        FrameLayout item = new FrameLayout(context);
        item.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(14),
                AndroidUtilities.dp(21), AndroidUtilities.dp(14));

        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        Drawable d = context.getResources().getDrawable(iconRes).mutate();
        d.setColorFilter(new PorterDuffColorFilter(COLOR_ACCENT_LIGHT, PorterDuff.Mode.SRC_IN));
        icon.setImageDrawable(d);
        item.addView(icon, LayoutHelper.createFrame(24, 24,
                Gravity.CENTER_VERTICAL | Gravity.START));

        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams textParams = LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL, 52, 0, 62, 0);
        item.addView(textContainer, textParams);

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setText(title);
        textContainer.addView(titleView);

        if (subtitle != null) {
            TextView subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            subtitleView.setText(subtitle);
            LinearLayout.LayoutParams stParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stParams.topMargin = AndroidUtilities.dp(2);
            textContainer.addView(subtitleView, stParams);
        }

        Switch toggle = new Switch(context);
        toggle.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{COLOR_TOGGLE_ON_TRACK, COLOR_TOGGLE_OFF_TRACK}
        ));
        toggle.setThumbTintList(new android.content.res.ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{COLOR_TOGGLE_ON_THUMB, COLOR_TOGGLE_OFF_THUMB}
        ));
        toggle.setChecked(initialState);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> callback.onToggle(isChecked));
        item.addView(toggle, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | Gravity.END));

        item.setOnClickListener(v -> toggle.toggle());

        return item;
    }

    private static final String LITEGRAM_BOT_USERNAME = "Litegram_robot";
    private static final String SUPPORT_BOT_USERNAME = "Litegram_support_bot";
    private static final String SUPPORT_EMAIL = "support@bubavpn.com";

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
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
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
                Theme.getColor(Theme.key_listSelector), 2));
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
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textCol.addView(titleView);

        TextView subtitleView = new TextView(ctx);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(COLOR_ACCENT_LIGHT);
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
        arrowD.setColorFilter(new PorterDuffColorFilter(COLOR_ACCENT_LIGHT, PorterDuff.Mode.SRC_IN));
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
        container.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);

        TextView button = new TextView(context);
        button.setText(LocaleController.getString(R.string.LitegramTryAllFeatures));
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        button.setTextColor(Color.WHITE);
        button.setTypeface(AndroidUtilities.bold());
        button.setGravity(Gravity.CENTER);
        button.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(14),
                AndroidUtilities.dp(24), AndroidUtilities.dp(14));

        GradientDrawable btnBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{COLOR_BTN_START, COLOR_BTN_END});
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

    private View createDivider(Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        params.leftMargin = AndroidUtilities.dp(52);
        divider.setLayoutParams(params);
        return divider;
    }

    private View createSpacer(Context context, int heightDp) {
        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(heightDp)));
        return spacer;
    }
}
