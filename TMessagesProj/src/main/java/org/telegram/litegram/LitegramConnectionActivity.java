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

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

import java.util.ArrayList;
import java.util.List;

public class LitegramConnectionActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private int c(int key) {
        return getThemedColor(key);
    }

    public LitegramConnectionActivity() {
        super();
    }

    public LitegramConnectionActivity(Bundle args) {
        super(args);
    }

    private TextView statusText;
    private TextView subtitleText;
    private TextView planBadge;
    private TextView actionButton;
    private GradientDrawable actionBtnBg;
    private RLottieImageView lottieView;
    private int headerLottieRes;

    private LinearLayout serversListContainer;

    private boolean connected;
    private boolean connecting;
    private Runnable pollRunnable;
    private List<LitegramApi.ServerInfo> availableServers = new ArrayList<>();

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didUpdateConnectionState);
        startPolling();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        stopPolling();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didUpdateConnectionState);
    }

    private void startPolling() {
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                updateUI();
                AndroidUtilities.runOnUIThread(this, 1500);
            }
        };
        AndroidUtilities.runOnUIThread(pollRunnable, 1500);
    }

    private void stopPolling() {
        if (pollRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(pollRunnable);
            pollRunnable = null;
        }
    }

    private void applyThemedActionBar() {
        actionBar.setBackgroundColor(c(Theme.key_actionBarDefault));
        actionBar.setTitleColor(c(Theme.key_actionBarDefaultTitle));
        actionBar.setItemsColor(c(Theme.key_actionBarDefaultIcon), false);
        actionBar.setCastShadows(false);
    }

    @Override
    public View createView(Context context) {
        applyThemedActionBar();
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.LitegramConnection));
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
        content.setBackgroundColor(c(Theme.key_windowBackgroundGray));
        int hPad = AndroidUtilities.dp(16);
        content.setPadding(hPad, AndroidUtilities.dp(8), hPad, AndroidUtilities.dp(28));

        content.addView(createConnectionCard(context));
        content.addView(createServersSection(context));
        content.addView(createActionButton(context));
        content.addView(createFeaturesSection(context));

        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        loadServers();

        updateUI();

        fragmentView = scrollView;
        return fragmentView;
    }

    private View createConnectionCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);

        GradientDrawable cardBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        c(Theme.key_featuredStickers_addButton),
                        c(Theme.key_featuredStickers_addButton2)
                });
        cardBg.setCornerRadius(AndroidUtilities.dp(24));
        card.setBackground(cardBg);
        int cardPad = AndroidUtilities.dp(22);
        card.setPadding(cardPad, AndroidUtilities.dp(24), cardPad, AndroidUtilities.dp(20));

        lottieView = new RLottieImageView(context);
        lottieView.setAutoRepeat(false);
        lottieView.setOnClickListener(v -> {
            if (lottieView.getAnimatedDrawable() != null) {
                lottieView.getAnimatedDrawable().setCurrentFrame(0, false);
            }
            lottieView.playAnimation();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                AndroidUtilities.dp(108), AndroidUtilities.dp(108));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(lottieView, lp);

        statusText = new TextView(context);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
        statusText.setTextColor(c(Theme.key_featuredStickers_buttonText));
        statusText.setTypeface(AndroidUtilities.bold());
        statusText.setGravity(Gravity.CENTER);
        statusText.setText(LocaleController.getString(R.string.LitegramConnStatusDisconnected));
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        stp.topMargin = AndroidUtilities.dp(16);
        card.addView(statusText, stp);

        subtitleText = new TextView(context);
        subtitleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleText.setTextColor(ColorUtils.setAlphaComponent(
                c(Theme.key_featuredStickers_buttonText), 0xE6));
        subtitleText.setGravity(Gravity.CENTER);
        subtitleText.setLineSpacing(AndroidUtilities.dp(2), 1f);
        subtitleText.setText(LocaleController.getString(R.string.LitegramConnSubtitle));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = AndroidUtilities.dp(8);
        card.addView(subtitleText, sp);

        LinearLayout badgeWrap = new LinearLayout(context);
        badgeWrap.setOrientation(LinearLayout.HORIZONTAL);
        badgeWrap.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bwp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bwp.topMargin = AndroidUtilities.dp(14);
        card.addView(badgeWrap, bwp);

        planBadge = new TextView(context);
        planBadge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        planBadge.setTypeface(AndroidUtilities.bold());
        applyPlanBadgeAppearance();
        int bx = AndroidUtilities.dp(14);
        int by = AndroidUtilities.dp(6);
        planBadge.setPadding(bx, by, bx, by);
        planBadge.setText(LocaleController.getString(R.string.LitegramConnFree));
        badgeWrap.addView(planBadge);

        return card;
    }

    private void applyPlanBadgeAppearance() {
        if (planBadge == null) {
            return;
        }
        if (LitegramConfig.isSubscriptionActive()) {
            planBadge.setTextColor(c(Theme.key_featuredStickers_buttonText));
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(AndroidUtilities.dp(20));
            badgeBg.setColor(c(Theme.key_featuredStickers_addButtonPressed));
            planBadge.setBackground(badgeBg);
        } else {
            planBadge.setTextColor(c(Theme.key_featuredStickers_buttonText));
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(AndroidUtilities.dp(20));
            badgeBg.setColor(ColorUtils.setAlphaComponent(
                    c(Theme.key_windowBackgroundWhiteBlackText), 0x59));
            planBadge.setBackground(badgeBg);
        }
    }

    private View createServersSection(Context context) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, AndroidUtilities.dp(18), 0, 0);

        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(LocaleController.getString(R.string.LitegramConnServersSection));
        sectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        sectionTitle.setTypeface(AndroidUtilities.bold());
        sectionTitle.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText2));
        sectionTitle.setAllCaps(true);
        sectionTitle.setLetterSpacing(0.12f);
        sectionTitle.setPadding(AndroidUtilities.dp(4), 0, 0, AndroidUtilities.dp(10));
        section.addView(sectionTitle);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setCornerRadius(AndroidUtilities.dp(16));
        panelBg.setColor(c(Theme.key_windowBackgroundWhite));

        serversListContainer = new LinearLayout(context);
        serversListContainer.setOrientation(LinearLayout.VERTICAL);
        serversListContainer.setBackground(panelBg);
        int ph = AndroidUtilities.dp(2);
        serversListContainer.setPadding(ph, AndroidUtilities.dp(4), ph, AndroidUtilities.dp(4));

        section.addView(serversListContainer);
        return section;
    }

    private void rebuildServersList() {
        if (serversListContainer == null) {
            return;
        }
        serversListContainer.removeAllViews();
        Context context = serversListContainer.getContext();

        if (availableServers.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(LocaleController.getString(R.string.LitegramConnLoadingServers));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            empty.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText2));
            empty.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
            serversListContainer.addView(empty);
            return;
        }

        String currentHost = LitegramConfig.getProxyHost();
        for (int i = 0; i < availableServers.size(); i++) {
            LitegramApi.ServerInfo server = availableServers.get(i);
            if (i > 0) {
                View div = new View(context);
                div.setBackgroundColor(c(Theme.key_divider));
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
                dlp.leftMargin = AndroidUtilities.dp(52);
                dlp.rightMargin = AndroidUtilities.dp(12);
                serversListContainer.addView(div, dlp);
            }

            boolean selected = server.host.equals(currentHost) && LitegramConfig.isProxyEnabled();

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
            row.setBackground(Theme.createSelectorDrawable(c(Theme.key_listSelector), 2));

            TextView flagView = new TextView(context);
            flagView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            flagView.setTextColor(c(Theme.key_windowBackgroundWhiteBlackText));
            flagView.setIncludeFontPadding(false);
            flagView.setText(Emoji.replaceEmoji(server.getFlagEmoji(),
                    flagView.getPaint().getFontMetricsInt(), false));
            NotificationCenter.listenEmojiLoading(flagView);
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                    AndroidUtilities.dp(40), ViewGroup.LayoutParams.WRAP_CONTENT);
            row.addView(flagView, flp);

            TextView nameView = new TextView(context);
            nameView.setText(formatServerLabel(server));
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            nameView.setTextColor(c(Theme.key_windowBackgroundWhiteBlackText));
            if (selected) {
                nameView.setTypeface(AndroidUtilities.bold());
            }
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nlp.leftMargin = AndroidUtilities.dp(6);
            row.addView(nameView, nlp);

            TextView check = new TextView(context);
            check.setText("\u2713");
            check.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            check.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText4));
            check.setGravity(Gravity.CENTER);
            check.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            row.addView(check, new LinearLayout.LayoutParams(
                    AndroidUtilities.dp(22), ViewGroup.LayoutParams.WRAP_CONTENT));

            final LitegramApi.ServerInfo srv = server;
            row.setOnClickListener(v -> {
                actionButton.setText(LocaleController.getString(R.string.LitegramConnConnecting));
                LitegramController.getInstance().connectToServer(srv, (success, err) -> {
                    if (!success && getParentActivity() != null) {
                        String msg = err != null ? err : LocaleController.getString(R.string.LitegramConnUnknownError);
                        Toast.makeText(getParentActivity(),
                                LocaleController.formatString(R.string.LitegramConnFailed, msg), Toast.LENGTH_LONG).show();
                    }
                    updateUI();
                });
            });

            serversListContainer.addView(row);
        }
    }

    private static String formatServerLabel(LitegramApi.ServerInfo s) {
        String name = (s.name != null && !s.name.isEmpty()) ? s.name : (s.country != null ? s.country : s.host);
        String cc = s.country != null ? s.country.toUpperCase() : "";
        if (cc.isEmpty()) {
            return name;
        }
        return name + " (" + cc + ")";
    }

    private View createActionButton(Context context) {
        FrameLayout buttonContainer = new FrameLayout(context);
        buttonContainer.setPadding(0, AndroidUtilities.dp(18), 0, AndroidUtilities.dp(6));

        actionButton = new TextView(context);
        actionButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        actionButton.setTextColor(c(Theme.key_featuredStickers_buttonText));
        actionButton.setTypeface(AndroidUtilities.bold());
        actionButton.setAllCaps(true);
        actionButton.setGravity(Gravity.CENTER);
        actionButton.setPadding(0, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16));

        actionBtnBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        c(Theme.key_featuredStickers_addButton),
                        c(Theme.key_featuredStickers_addButton2)
                });
        actionBtnBg.setCornerRadius(AndroidUtilities.dp(14));
        actionButton.setBackground(actionBtnBg);

        actionButton.setOnClickListener(v -> {
            if (connected || connecting) {
                LitegramConfig.setProxyEnabled(false);
                ConnectionsManager.setProxySettings(false, "", 0, "", "", "");
                NotificationCenter.getGlobalInstance()
                        .postNotificationName(NotificationCenter.proxySettingsChanged);
                updateUI();
            } else {
                actionButton.setText(LocaleController.getString(R.string.LitegramConnConnecting));
                LitegramController.getInstance().reconnect((success, error) -> {
                    if (!success && getParentActivity() != null) {
                        String msg = error != null ? error : LocaleController.getString(R.string.LitegramConnUnknownError);
                        Toast.makeText(getParentActivity(),
                                LocaleController.formatString(R.string.LitegramConnFailed, msg), Toast.LENGTH_LONG).show();
                    }
                    updateUI();
                });
            }
        });

        buttonContainer.addView(actionButton, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        return buttonContainer;
    }

    private View createFeaturesSection(Context context) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, AndroidUtilities.dp(12), 0, 0);

        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(LocaleController.getString(R.string.LitegramConnWhatsIncluded));
        sectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        sectionTitle.setTypeface(AndroidUtilities.bold());
        sectionTitle.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText2));
        sectionTitle.setAllCaps(true);
        sectionTitle.setLetterSpacing(0.12f);
        sectionTitle.setPadding(AndroidUtilities.dp(4), 0, 0, AndroidUtilities.dp(12));
        section.addView(sectionTitle);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setCornerRadius(AndroidUtilities.dp(16));
        panelBg.setColor(c(Theme.key_windowBackgroundWhite));
        panel.setBackground(panelBg);
        int ph = AndroidUtilities.dp(4);
        panel.setPadding(ph, AndroidUtilities.dp(8), ph, AndroidUtilities.dp(8));

        addFeatureTile(context, panel, c(Theme.key_statisticChartLine_orange), "\u26A1",
                R.string.LitegramConnFeatFast, R.string.LitegramConnFeatFastDesc, R.string.LitegramConnFeatFastDetail);
        addFeatureTile(context, panel, c(Theme.key_statisticChartLine_red), "\uD83D\uDEE1\uFE0F",
                R.string.LitegramConnFeatPrivacy, R.string.LitegramConnFeatPrivacyDesc, R.string.LitegramConnFeatPrivacyDetail);
        addFeatureTile(context, panel, c(Theme.key_statisticChartLine_blue), "\uD83C\uDF0D",
                R.string.LitegramConnFeatAccess, R.string.LitegramConnFeatAccessDesc, R.string.LitegramConnFeatAccessDetail);
        addFeatureTile(context, panel, c(Theme.key_statisticChartLine_purple), "\u00D72",
                R.string.LitegramConnFeatNoLimits, R.string.LitegramConnFeatNoLimitsDesc, R.string.LitegramConnFeatNoLimitsDetail);
        addFeatureTile(context, panel, c(Theme.key_statisticChartLine_lightblue), "\uD83D\uDCAC",
                R.string.LitegramConnFeatAutoReconnect, R.string.LitegramConnFeatAutoReconnectDesc, R.string.LitegramConnFeatAutoReconnectDetail);
        addFeatureTile(context, panel, c(Theme.key_statisticChartLine_cyan), "\uD83D\uDDFA\uFE0F",
                R.string.LitegramConnFeatMultiServer, R.string.LitegramConnFeatMultiServerDesc, R.string.LitegramConnFeatMultiServerDetail);
        addFeatureTile(context, panel, c(Theme.key_statisticChartLine_green), "Aa",
                R.string.LitegramConnFeatEasy, R.string.LitegramConnFeatEasyDesc, R.string.LitegramConnFeatEasyDetail);

        section.addView(panel);
        return section;
    }

    private void addFeatureTile(Context context, LinearLayout panel, int iconBgColor, String symbol,
                                int titleRes, int descRes, int detailRes) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12),
                AndroidUtilities.dp(10), AndroidUtilities.dp(12));

        FrameLayout iconBox = new FrameLayout(context);
        int box = AndroidUtilities.dp(44);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(box, box);
        iconLp.rightMargin = AndroidUtilities.dp(12);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setCornerRadius(AndroidUtilities.dp(12));
        iconBg.setColor(iconBgColor);
        iconBox.setBackground(iconBg);

        TextView sym = new TextView(context);
        sym.setGravity(Gravity.CENTER);
        sym.setIncludeFontPadding(false);
        sym.setTextColor(c(Theme.key_featuredStickers_buttonText));
        if ("Aa".equals(symbol)) {
            sym.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            sym.setTypeface(AndroidUtilities.bold());
            sym.setText(symbol);
        } else if ("\u00D72".equals(symbol)) {
            sym.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            sym.setTypeface(AndroidUtilities.bold());
            sym.setText(symbol);
        } else {
            sym.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            sym.setText(Emoji.replaceEmoji(symbol, sym.getPaint().getFontMetricsInt(), false));
            NotificationCenter.listenEmojiLoading(sym);
        }
        iconBox.addView(sym, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        row.addView(iconBox, iconLp);

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(c(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setText(LocaleController.getString(titleRes));
        textCol.addView(titleView);

        TextView descView = new TextView(context);
        descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        descView.setTextColor(c(Theme.key_windowBackgroundWhiteGrayText2));
        descView.setText(LocaleController.getString(descRes));
        descView.setPadding(0, AndroidUtilities.dp(3), 0, 0);
        descView.setLineSpacing(AndroidUtilities.dp(2), 1f);
        textCol.addView(descView);

        row.addView(textCol, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView chev = new ImageView(context);
        chev.setScaleType(ImageView.ScaleType.CENTER);
        Drawable cd = context.getResources().getDrawable(R.drawable.msg_arrowright).mutate();
        cd.setColorFilter(new PorterDuffColorFilter(
                c(Theme.key_windowBackgroundWhiteGrayText4), PorterDuff.Mode.SRC_IN));
        chev.setImageDrawable(cd);
        row.addView(chev, new LinearLayout.LayoutParams(AndroidUtilities.dp(18), AndroidUtilities.dp(18)));

        row.setBackground(Theme.createSelectorDrawable(
                Theme.getColor(Theme.key_listSelector), 2));
        row.setOnClickListener(v -> showFeatureDetail(titleRes, detailRes));

        panel.addView(row);
    }

    private void showFeatureDetail(int titleRes, int detailRes) {
        Context ctx = getParentActivity();
        if (ctx == null) return;

        BottomSheet.Builder builder = new BottomSheet.Builder(ctx);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(16),
                AndroidUtilities.dp(22), AndroidUtilities.dp(24));

        TextView title = new TextView(ctx);
        title.setText(LocaleController.getString(titleRes));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        root.addView(title);

        View divider = new View(ctx);
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        divLp.topMargin = AndroidUtilities.dp(12);
        divLp.bottomMargin = AndroidUtilities.dp(12);
        root.addView(divider, divLp);

        TextView detail = new TextView(ctx);
        detail.setText(LocaleController.getString(detailRes));
        detail.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        detail.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        detail.setLineSpacing(AndroidUtilities.dp(3), 1f);
        root.addView(detail);

        TextView closeBtn = new TextView(ctx);
        closeBtn.setText(LocaleController.getString(R.string.Close));
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        closeBtn.setTypeface(AndroidUtilities.bold());
        closeBtn.setTextColor(c(Theme.key_featuredStickers_buttonText));
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(c(Theme.key_featuredStickers_addButton));
        btnBg.setCornerRadius(AndroidUtilities.dp(10));
        closeBtn.setBackground(btnBg);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = AndroidUtilities.dp(16);
        root.addView(closeBtn, btnLp);

        builder.setCustomView(root);
        BottomSheet sheet = builder.create();
        closeBtn.setOnClickListener(v -> sheet.dismiss());
        showDialog(sheet);
    }

    private void loadServers() {
        List<LitegramApi.ServerInfo> cached = LitegramConfig.loadServersCache();
        if (!cached.isEmpty() && availableServers.isEmpty()) {
            availableServers.addAll(cached);
        }
        LitegramController.getInstance().fetchServers((servers, error) -> {
            if (servers != null && !servers.isEmpty()) {
                availableServers.clear();
                availableServers.addAll(servers);
            }
            AndroidUtilities.runOnUIThread(this::updateUI);
        });
        LitegramController.getInstance().refreshSubscription();
    }

    private void updateUI() {
        int connectionState = ConnectionsManager.getInstance(currentAccount).getConnectionState();
        boolean proxyEnabled = LitegramConfig.isProxyEnabled();
        connected = proxyEnabled
                && (connectionState == ConnectionsManager.ConnectionStateConnected
                || connectionState == ConnectionsManager.ConnectionStateUpdating);
        connecting = proxyEnabled
                && (connectionState == ConnectionsManager.ConnectionStateConnectingToProxy
                || connectionState == ConnectionsManager.ConnectionStateConnecting);

        if (statusText != null) {
            statusText.setTextColor(c(Theme.key_featuredStickers_buttonText));
            if (connected) {
                statusText.setText(LocaleController.getString(R.string.LitegramConnStatusConnected));
            } else if (connecting) {
                statusText.setText(LocaleController.getString(R.string.LitegramConnStatusConnecting));
            } else {
                statusText.setText(LocaleController.getString(R.string.LitegramConnStatusDisconnected));
            }
        }

        if (subtitleText != null) {
            if (connected) {
                subtitleText.setText(LocaleController.getString(R.string.LitegramConnSecureSubtitleConnected));
            } else {
                subtitleText.setText(LocaleController.getString(R.string.LitegramConnSubtitle));
            }
        }

        if (planBadge != null) {
            if (LitegramConfig.isSubscriptionActive()) {
                planBadge.setText("\u2B50 " + LocaleController.getString(R.string.LitegramConnPremium));
            } else {
                planBadge.setText(LocaleController.getString(R.string.LitegramConnFree));
            }
            applyPlanBadgeAppearance();
        }

        if (actionButton != null && actionBtnBg != null) {
            int disconnectColor = c(Theme.key_fill_RedNormal);
            if (connected || connecting) {
                actionButton.setText(LocaleController.getString(R.string.LitegramConnDisconnect));
                actionBtnBg.setColors(new int[]{disconnectColor, disconnectColor});
                actionBtnBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            } else {
                actionButton.setText(LocaleController.getString(R.string.LitegramConnConnect));
                actionBtnBg.setColors(new int[]{
                        c(Theme.key_featuredStickers_addButton),
                        c(Theme.key_featuredStickers_addButton2)
                });
                actionBtnBg.setOrientation(GradientDrawable.Orientation.TL_BR);
            }
            actionButton.setEnabled(true);
            actionButton.setAlpha(1f);
        }

        rebuildServersList();
        updateHeaderLottie();
    }

    private void updateHeaderLottie() {
        if (lottieView == null) {
            return;
        }
        int want = connected ? R.raw.utyan_streaming : R.raw.media_forbidden;
        if (want == headerLottieRes) {
            return;
        }
        headerLottieRes = want;
        lottieView.setAnimation(want, 100, 100);
        lottieView.setAutoRepeat(false);
        lottieView.playAnimation();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxySettingsChanged || id == NotificationCenter.didUpdateConnectionState) {
            AndroidUtilities.runOnUIThread(this::updateUI);
        }
    }
}
