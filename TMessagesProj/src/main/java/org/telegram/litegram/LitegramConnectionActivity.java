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
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

import java.util.ArrayList;
import java.util.List;

public class LitegramConnectionActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int COLOR_PAGE_BG = 0xFF000000;
    private static final int COLOR_CARD_START = 0xFF8E2DE2;
    private static final int COLOR_CARD_END = 0xFF4A00E0;
    private static final int COLOR_BTN_CONNECT_A = 0xFF8E2DE2;
    private static final int COLOR_BTN_CONNECT_B = 0xFF4A00E0;
    private static final int COLOR_BTN_DISCONNECT = 0xFFFF3B30;
    private static final int COLOR_FEATURES_PANEL = 0xFF1C1C1E;
    private static final int COLOR_SECTION_LABEL = 0xFF8E8E93;

    private static final int ICON_FAST = 0xFFFF9500;
    private static final int ICON_PRIVACY = 0xFFFF3B30;
    private static final int ICON_ACCESS = 0xFFFF375F;
    private static final int ICON_NOLIMIT = 0xFFAF52DE;
    private static final int ICON_AUTOREC = 0xFF0A84FF;
    private static final int ICON_MULTI = 0xFF5AC8FA;
    private static final int ICON_EASY = 0xFF30D158;

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

    private void applyDarkActionBar() {
        actionBar.setBackgroundColor(COLOR_PAGE_BG);
        actionBar.setTitleColor(0xFFFFFFFF);
        actionBar.setItemsColor(0xFFFFFFFF, false);
        actionBar.setCastShadows(false);
    }

    @Override
    public View createView(Context context) {
        applyDarkActionBar();
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
        scrollView.setBackgroundColor(COLOR_PAGE_BG);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(COLOR_PAGE_BG);
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
                new int[]{COLOR_CARD_START, COLOR_CARD_END});
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
        statusText.setTextColor(Color.WHITE);
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
        subtitleText.setTextColor(0xE6FFFFFF);
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
        planBadge.setTextColor(0xFFFFFFFF);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(AndroidUtilities.dp(20));
        badgeBg.setColor(0x59000000);
        planBadge.setBackground(badgeBg);
        int bx = AndroidUtilities.dp(14);
        int by = AndroidUtilities.dp(6);
        planBadge.setPadding(bx, by, bx, by);
        planBadge.setText(LocaleController.getString(R.string.LitegramConnFree));
        badgeWrap.addView(planBadge);

        return card;
    }

    private View createServersSection(Context context) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, AndroidUtilities.dp(18), 0, 0);

        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(LocaleController.getString(R.string.LitegramConnServersSection));
        sectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        sectionTitle.setTypeface(AndroidUtilities.bold());
        sectionTitle.setTextColor(COLOR_SECTION_LABEL);
        sectionTitle.setAllCaps(true);
        sectionTitle.setLetterSpacing(0.12f);
        sectionTitle.setPadding(AndroidUtilities.dp(4), 0, 0, AndroidUtilities.dp(10));
        section.addView(sectionTitle);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setCornerRadius(AndroidUtilities.dp(16));
        panelBg.setColor(COLOR_FEATURES_PANEL);

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
            empty.setTextColor(0xFF8E8E93);
            empty.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
            serversListContainer.addView(empty);
            return;
        }

        String currentHost = LitegramConfig.getProxyHost();
        for (int i = 0; i < availableServers.size(); i++) {
            LitegramApi.ServerInfo server = availableServers.get(i);
            if (i > 0) {
                View div = new View(context);
                div.setBackgroundColor(0x28FFFFFF);
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
            row.setBackground(Theme.createSelectorDrawable(0x18FFFFFF, 2));

            TextView flagView = new TextView(context);
            flagView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            flagView.setTextColor(0xFFFFFFFF);
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
            nameView.setTextColor(0xFFFFFFFF);
            if (selected) {
                nameView.setTypeface(AndroidUtilities.bold());
            }
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nlp.leftMargin = AndroidUtilities.dp(6);
            row.addView(nameView, nlp);

            TextView check = new TextView(context);
            check.setText("\u2713");
            check.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            check.setTextColor(0xFF8E8E93);
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
        actionButton.setTextColor(Color.WHITE);
        actionButton.setTypeface(AndroidUtilities.bold());
        actionButton.setAllCaps(true);
        actionButton.setGravity(Gravity.CENTER);
        actionButton.setPadding(0, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16));

        actionBtnBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{COLOR_BTN_CONNECT_A, COLOR_BTN_CONNECT_B});
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
        sectionTitle.setTextColor(COLOR_SECTION_LABEL);
        sectionTitle.setAllCaps(true);
        sectionTitle.setLetterSpacing(0.12f);
        sectionTitle.setPadding(AndroidUtilities.dp(4), 0, 0, AndroidUtilities.dp(12));
        section.addView(sectionTitle);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setCornerRadius(AndroidUtilities.dp(16));
        panelBg.setColor(COLOR_FEATURES_PANEL);
        panel.setBackground(panelBg);
        int ph = AndroidUtilities.dp(4);
        panel.setPadding(ph, AndroidUtilities.dp(8), ph, AndroidUtilities.dp(8));

        addFeatureTile(context, panel, ICON_FAST, "\u26A1",
                R.string.LitegramConnFeatFast, R.string.LitegramConnFeatFastDesc);
        addFeatureTile(context, panel, ICON_PRIVACY, "\uD83D\uDEE1\uFE0F",
                R.string.LitegramConnFeatPrivacy, R.string.LitegramConnFeatPrivacyDesc);
        addFeatureTile(context, panel, ICON_ACCESS, "\uD83C\uDF0D",
                R.string.LitegramConnFeatAccess, R.string.LitegramConnFeatAccessDesc);
        addFeatureTile(context, panel, ICON_NOLIMIT, "\u00D72",
                R.string.LitegramConnFeatNoLimits, R.string.LitegramConnFeatNoLimitsDesc);
        addFeatureTile(context, panel, ICON_AUTOREC, "\uD83D\uDCAC",
                R.string.LitegramConnFeatAutoReconnect, R.string.LitegramConnFeatAutoReconnectDesc);
        addFeatureTile(context, panel, ICON_MULTI, "\uD83D\uDDFA\uFE0F",
                R.string.LitegramConnFeatMultiServer, R.string.LitegramConnFeatMultiServerDesc);
        addFeatureTile(context, panel, ICON_EASY, "Aa",
                R.string.LitegramConnFeatEasy, R.string.LitegramConnFeatEasyDesc);

        section.addView(panel);
        return section;
    }

    private void addFeatureTile(Context context, LinearLayout panel, int iconBgColor, String symbol,
                                int titleRes, int descRes) {
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
        sym.setTextColor(Color.WHITE);
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
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setText(LocaleController.getString(titleRes));
        textCol.addView(titleView);

        TextView descView = new TextView(context);
        descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        descView.setTextColor(0xFFAEAEB2);
        descView.setText(LocaleController.getString(descRes));
        descView.setPadding(0, AndroidUtilities.dp(3), 0, 0);
        descView.setLineSpacing(AndroidUtilities.dp(2), 1f);
        textCol.addView(descView);

        row.addView(textCol, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView chev = new ImageView(context);
        chev.setScaleType(ImageView.ScaleType.CENTER);
        Drawable cd = context.getResources().getDrawable(R.drawable.msg_arrowright).mutate();
        cd.setColorFilter(new PorterDuffColorFilter(0xFF636366, PorterDuff.Mode.SRC_IN));
        chev.setImageDrawable(cd);
        row.addView(chev, new LinearLayout.LayoutParams(AndroidUtilities.dp(18), AndroidUtilities.dp(18)));

        panel.addView(row);
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
            statusText.setTextColor(Color.WHITE);
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
        }

        if (actionButton != null && actionBtnBg != null) {
            if (connected || connecting) {
                actionButton.setText(LocaleController.getString(R.string.LitegramConnDisconnect));
                actionBtnBg.setColors(new int[]{COLOR_BTN_DISCONNECT, COLOR_BTN_DISCONNECT});
                actionBtnBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            } else {
                actionButton.setText(LocaleController.getString(R.string.LitegramConnConnect));
                actionBtnBg.setColors(new int[]{COLOR_BTN_CONNECT_A, COLOR_BTN_CONNECT_B});
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
