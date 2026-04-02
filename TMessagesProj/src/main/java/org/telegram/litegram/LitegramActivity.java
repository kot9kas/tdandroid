package org.telegram.litegram;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
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

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class LitegramActivity extends BaseFragment {

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

        fragmentView = scrollView;
        return fragmentView;
    }

    private View createProfileSection(Context context) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setGravity(Gravity.CENTER_HORIZONTAL);

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFAE8BA1, 0xFFF2ECB6});
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
        nameView.setShadowLayer(4, 0, 1, 0x40000000);
        nameView.setText(user != null ? UserObject.getUserName(user) : "");
        LinearLayout.LayoutParams nameParams = LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
        nameParams.topMargin = AndroidUtilities.dp(12);
        section.addView(nameView, nameParams);

        TextView idView = new TextView(context);
        idView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        idView.setTextColor(0xCCFFFFFF);
        idView.setGravity(Gravity.CENTER);
        idView.setText(user != null ? "ID: " + user.id : "");
        LinearLayout.LayoutParams idParams = LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
        idParams.topMargin = AndroidUtilities.dp(4);
        section.addView(idView, idParams);

        boolean isPremium = user != null && user.premium;
        TextView statusView = new TextView(context);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        statusView.setTypeface(AndroidUtilities.bold());
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(4),
                AndroidUtilities.dp(12), AndroidUtilities.dp(4));
        if (isPremium) {
            statusView.setText("\u2B50 Premium");
            statusView.setTextColor(0xFFFFF3D0);
            statusView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                    AndroidUtilities.dp(12), 0xCC6C3FD1, 0xDD5A33B5));
        } else {
            statusView.setText("Free");
            statusView.setTextColor(Color.WHITE);
            statusView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                    AndroidUtilities.dp(12), 0x88000000, 0xAA000000));
        }
        LinearLayout.LayoutParams statusParams = LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
        statusParams.topMargin = AndroidUtilities.dp(10);
        section.addView(statusView, statusParams);

        return section;
    }

    private View createMenuSection(Context context) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        section.addView(createToggleItem(context, R.drawable.msg_download,
                "Save Traffic", "Download media only on tap",
                LitegramConfig.isSaveTrafficEnabled(),
                (enabled) -> LitegramConfig.setSaveTrafficEnabled(enabled)));

        section.addView(createDivider(context));

        section.addView(createMenuItem(context, R.drawable.msg_secret,
                "Litegram Connection", "Proxy settings and status",
                () -> presentFragment(new LitegramConnectionActivity())));

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
        d.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
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
        arrowD.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), PorterDuff.Mode.SRC_IN));
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
        d.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
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
        toggle.setChecked(initialState);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> callback.onToggle(isChecked));
        item.addView(toggle, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | Gravity.END));

        item.setOnClickListener(v -> toggle.toggle());

        return item;
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
