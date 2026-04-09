package org.telegram.litegram;

import android.content.Context;
import android.graphics.Color;
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

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

public class LitegramChatsActivity extends BaseFragment {

    private static final String LITEGRAM_BOT_USERNAME = "Litegram_robot";
    private static final String LITEGRAM_CHANNEL_USERNAME = "litegram_news";
    private static final String LITEGRAM_CHAT_USERNAME = "litegram_chat";

    private TextView sizeLabel;

    public LitegramChatsActivity() {
        super();
    }

    public LitegramChatsActivity(Bundle args) {
        super(args);
    }

    private int c(int key) {
        return getThemedColor(key);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackgroundColor(c(Theme.key_actionBarDefault));
        actionBar.setTitleColor(c(Theme.key_actionBarDefaultTitle));
        actionBar.setItemsColor(c(Theme.key_actionBarDefaultIcon), false);
        actionBar.setCastShadows(false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.LitegramChats));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(c(Theme.key_windowBackgroundGray));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int hPad = AndroidUtilities.dp(16);
        content.setPadding(hPad, AndroidUtilities.dp(12), hPad, AndroidUtilities.dp(28));

        content.addView(createVaultSection(context));
        content.addView(createSpacer(context, 18));
        content.addView(createCommunitySection(context));

        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        fragmentView = scrollView;
        return fragmentView;
    }

    private View createVaultSection(Context context) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);

        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(LocaleController.getString(R.string.LitegramChatsVaultSection));
        sectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        sectionTitle.setTypeface(AndroidUtilities.bold());
        sectionTitle.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText2));
        sectionTitle.setAllCaps(true);
        sectionTitle.setLetterSpacing(0.12f);
        sectionTitle.setPadding(AndroidUtilities.dp(4), 0, 0, AndroidUtilities.dp(10));
        section.addView(sectionTitle);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setCornerRadius(AndroidUtilities.dp(16));
        panelBg.setColor(c(Theme.key_windowBackgroundWhite));
        panel.setBackground(panelBg);
        int ph = AndroidUtilities.dp(4);
        panel.setPadding(0, ph, 0, ph);

        panel.addView(createVaultRow(context,
                "\uD83D\uDDD1\uFE0F",
                LocaleController.getString(R.string.LitegramChatsDeleted),
                LocaleController.getString(R.string.LitegramChatsDeletedDesc),
                LitegramConfig.isVaultDeletedEnabled(),
                enabled -> LitegramConfig.setVaultDeletedEnabled(enabled),
                () -> presentFragment(new LitegramVaultActivity(LitegramVaultActivity.MODE_DELETED)),
                () -> confirmClear(LitegramVaultActivity.MODE_DELETED)));

        section.addView(panel);

        TextView disclaimer = new TextView(context);
        disclaimer.setText(LocaleController.getString(R.string.LitegramVaultDisclaimer));
        disclaimer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        disclaimer.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText4));
        disclaimer.setLineSpacing(AndroidUtilities.dp(2), 1f);
        disclaimer.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(10),
                AndroidUtilities.dp(4), 0);
        section.addView(disclaimer);

        return section;
    }

    private interface ToggleCb { void onToggle(boolean enabled); }

    private View createVaultRow(Context context, String emoji, String title, String subtitle,
                                boolean initialState, ToggleCb toggleCb,
                                Runnable onOpen, Runnable onClear) {
        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);

        FrameLayout row = new FrameLayout(context);
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14),
                AndroidUtilities.dp(12), AndroidUtilities.dp(8));

        TextView emojiView = new TextView(context);
        emojiView.setText(emoji);
        emojiView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        row.addView(emojiView, LayoutHelper.createFrame(
                32, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.START));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams textLp = LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL, 48, 0, 58, 0);
        row.addView(textCol, textLp);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(c(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTypeface(AndroidUtilities.bold());
        textCol.addView(titleView);

        TextView subtitleView = new TextView(context);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText2));
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stLp.topMargin = AndroidUtilities.dp(4);
        textCol.addView(subtitleView, stLp);

        Switch toggle = new Switch(context, getResourceProvider());
        toggle.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked,
                Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        toggle.setChecked(initialState, false);
        toggle.setOnCheckedChangeListener((view, isChecked) -> toggleCb.onToggle(isChecked));
        row.addView(toggle, LayoutHelper.createFrame(37, 20,
                Gravity.CENTER_VERTICAL | Gravity.END));

        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked(), true));

        outer.addView(row);

        FrameLayout buttonsRow = new FrameLayout(context);
        buttonsRow.setPadding(AndroidUtilities.dp(48), AndroidUtilities.dp(2),
                AndroidUtilities.dp(16), AndroidUtilities.dp(14));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);

        TextView openBtn = new TextView(context);
        openBtn.setText(LocaleController.getString(R.string.LitegramVaultOpen));
        openBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        openBtn.setTextColor(0xFF7B5EA7);
        openBtn.setTypeface(AndroidUtilities.bold());
        openBtn.setGravity(Gravity.CENTER);
        openBtn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(7),
                AndroidUtilities.dp(16), AndroidUtilities.dp(7));
        GradientDrawable openBg = new GradientDrawable();
        openBg.setCornerRadius(AndroidUtilities.dp(8));
        openBg.setStroke(AndroidUtilities.dp(1), 0xFF7B5EA7);
        openBg.setColor(0x00000000);
        openBtn.setBackground(openBg);
        openBtn.setOnClickListener(v -> onOpen.run());
        buttons.addView(openBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView clearBtn = new TextView(context);
        clearBtn.setText(LocaleController.getString(R.string.LitegramVaultClear));
        clearBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        clearBtn.setTextColor(0xFFE53935);
        clearBtn.setTypeface(AndroidUtilities.bold());
        clearBtn.setGravity(Gravity.CENTER);
        clearBtn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(7),
                AndroidUtilities.dp(16), AndroidUtilities.dp(7));
        GradientDrawable clearBgD = new GradientDrawable();
        clearBgD.setCornerRadius(AndroidUtilities.dp(8));
        clearBgD.setStroke(AndroidUtilities.dp(1), 0xFFE53935);
        clearBgD.setColor(0x00000000);
        clearBtn.setBackground(clearBgD);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = AndroidUtilities.dp(10);
        clearBtn.setOnClickListener(v -> onClear.run());
        buttons.addView(clearBtn, clp);

        buttonsRow.addView(buttons, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL));

        sizeLabel = new TextView(context);
        sizeLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        sizeLabel.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText4));
        refreshSizeLabel();
        buttonsRow.addView(sizeLabel, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL));

        outer.addView(buttonsRow);

        return outer;
    }

    private void clearVoiceFiles() {
        try {
            java.io.File voiceDir = LitegramVault.getVoiceDir();
            if (voiceDir.isDirectory()) {
                java.io.File[] files = voiceDir.listFiles();
                if (files != null) {
                    for (java.io.File f : files) f.delete();
                }
            }
        } catch (Exception ignored) {}
    }

    private void refreshSizeLabel() {
        if (sizeLabel == null) return;
        sizeLabel.setText(formatFileSize(getVaultSizeBytes()));
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshSizeLabel();
    }

    private long getVaultSizeBytes() {
        long total = 0;
        try {
            java.io.File voiceDir = LitegramVault.getVoiceDir();
            if (voiceDir.isDirectory()) {
                java.io.File[] files = voiceDir.listFiles();
                if (files != null) {
                    for (java.io.File f : files) {
                        if (f.isFile()) total += f.length();
                    }
                }
            }
        } catch (Exception ignored) {}
        total += LitegramStorage.getInstance().getDeletedMessagesDataSize();
        return total;
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " Б";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f КБ", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(java.util.Locale.US, "%.1f МБ", bytes / (1024.0 * 1024));
        return String.format(java.util.Locale.US, "%.2f ГБ", bytes / (1024.0 * 1024 * 1024));
    }

    private void confirmClear(int mode) {
        Context ctx = getParentActivity();
        if (ctx == null) return;

        BottomSheet.Builder builder = new BottomSheet.Builder(ctx);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(16),
                AndroidUtilities.dp(22), AndroidUtilities.dp(20));

        TextView title = new TextView(ctx);
        title.setText(LocaleController.getString(R.string.LitegramVaultClearConfirmTitle));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        root.addView(title);

        TextView desc = new TextView(ctx);
        desc.setText(LocaleController.getString(R.string.LitegramVaultClearConfirmDesc));
        desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        desc.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        desc.setLineSpacing(AndroidUtilities.dp(2), 1f);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = AndroidUtilities.dp(10);
        root.addView(desc, dlp);

        final BottomSheet[] holder = new BottomSheet[1];

        TextView confirmBtn = new TextView(ctx);
        confirmBtn.setText(LocaleController.getString(R.string.LitegramVaultClearConfirm));
        confirmBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        confirmBtn.setTypeface(AndroidUtilities.bold());
        confirmBtn.setTextColor(Color.WHITE);
        confirmBtn.setGravity(Gravity.CENTER);
        confirmBtn.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(c(Theme.key_text_RedRegular));
        btnBg.setCornerRadius(AndroidUtilities.dp(10));
        confirmBtn.setBackground(btnBg);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = AndroidUtilities.dp(16);
        confirmBtn.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            LitegramStorage.getInstance().clearDeletedMessages();
            clearVoiceFiles();
            refreshSizeLabel();
            Toast.makeText(ctx,
                    LocaleController.getString(R.string.LitegramVaultCleared),
                    Toast.LENGTH_SHORT).show();
        });
        root.addView(confirmBtn, blp);

        builder.setCustomView(root);
        holder[0] = builder.create();
        showDialog(holder[0]);
    }

    private View createCommunitySection(Context context) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);

        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(LocaleController.getString(R.string.LitegramChatsCommunitySection));
        sectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        sectionTitle.setTypeface(AndroidUtilities.bold());
        sectionTitle.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText2));
        sectionTitle.setAllCaps(true);
        sectionTitle.setLetterSpacing(0.12f);
        sectionTitle.setPadding(AndroidUtilities.dp(4), 0, 0, AndroidUtilities.dp(10));
        section.addView(sectionTitle);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setCornerRadius(AndroidUtilities.dp(16));
        panelBg.setColor(c(Theme.key_windowBackgroundWhite));
        panel.setBackground(panelBg);
        int ph = AndroidUtilities.dp(4);
        panel.setPadding(0, ph, 0, ph);

        panel.addView(createNavRow(context, "\uD83D\uDCE2",
                LocaleController.getString(R.string.LitegramChatsChannel),
                "@" + LITEGRAM_CHANNEL_USERNAME,
                () -> openChat(LITEGRAM_CHANNEL_USERNAME)));

        panel.addView(createPanelDivider(context));

        panel.addView(createNavRow(context, "\uD83D\uDCAC",
                LocaleController.getString(R.string.LitegramChatsCommunity),
                "@" + LITEGRAM_CHAT_USERNAME,
                () -> openChat(LITEGRAM_CHAT_USERNAME)));

        panel.addView(createPanelDivider(context));

        panel.addView(createNavRow(context, "\uD83E\uDD16",
                LocaleController.getString(R.string.LitegramChatsBot),
                "@" + LITEGRAM_BOT_USERNAME,
                this::openBotChat));

        section.addView(panel);
        return section;
    }

    private View createNavRow(Context context, String emoji, String title, String subtitle, Runnable onClick) {
        FrameLayout row = new FrameLayout(context);
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14),
                AndroidUtilities.dp(12), AndroidUtilities.dp(14));
        row.setBackground(Theme.createSelectorDrawable(c(Theme.key_listSelector), 2));
        row.setOnClickListener(v -> onClick.run());

        TextView emojiView = new TextView(context);
        emojiView.setText(emoji);
        emojiView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        row.addView(emojiView, LayoutHelper.createFrame(
                32, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.START));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams textLp = LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL, 48, 0, 30, 0);
        row.addView(textCol, textLp);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(c(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTypeface(AndroidUtilities.bold());
        textCol.addView(titleView);

        TextView subtitleView = new TextView(context);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(c(Theme.key_windowBackgroundWhiteBlueText4));
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stLp.topMargin = AndroidUtilities.dp(2);
        textCol.addView(subtitleView, stLp);

        ImageView arrow = new ImageView(context);
        arrow.setScaleType(ImageView.ScaleType.CENTER);
        Drawable arrowD = context.getResources().getDrawable(R.drawable.msg_arrowright).mutate();
        arrowD.setColorFilter(new PorterDuffColorFilter(
                c(Theme.key_windowBackgroundWhiteGrayText4), PorterDuff.Mode.SRC_IN));
        arrow.setImageDrawable(arrowD);
        row.addView(arrow, LayoutHelper.createFrame(24, 24,
                Gravity.CENTER_VERTICAL | Gravity.END));

        return row;
    }

    private View createPanelDivider(Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(c(Theme.key_divider));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        params.leftMargin = AndroidUtilities.dp(48);
        params.rightMargin = AndroidUtilities.dp(16);
        divider.setLayoutParams(params);
        return divider;
    }

    private void openChat(String username) {
        try {
            MessagesController.getInstance(currentAccount).getUserNameResolver().resolve(
                    username, null, peerId -> {
                        if (peerId == null || peerId == 0) return;
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
            FileLog.e("litegram: openChat failed", e);
        }
    }

    private void openBotChat() {
        try {
            MessagesController.getInstance(currentAccount).getUserNameResolver().resolve(
                    LITEGRAM_BOT_USERNAME, null, peerId -> {
                        if (peerId == null || peerId == 0) return;
                        AndroidUtilities.runOnUIThread(() -> {
                            Bundle args = new Bundle();
                            args.putLong("user_id", peerId);
                            presentFragment(new ChatActivity(args));
                            SendMessagesHelper.getInstance(currentAccount).sendMessage(
                                    SendMessagesHelper.SendMessageParams.of("/start", peerId));
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
