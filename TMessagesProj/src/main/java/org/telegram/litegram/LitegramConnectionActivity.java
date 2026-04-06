package org.telegram.litegram;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Shader;
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
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

import java.util.ArrayList;
import java.util.List;

public class LitegramConnectionActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int COLOR_HEADER_START = 0xFF0D0618;
    private static final int COLOR_HEADER_END = 0xFF2D1654;
    private static final int COLOR_ACCENT = 0xFF7B5EA7;
    private static final int COLOR_ACCENT_LIGHT = 0xFF9E84B6;
    private static final int COLOR_BTN_START = 0xFF5B2D8E;
    private static final int COLOR_BTN_END = 0xFF9E84B6;
    private static final int COLOR_DISCONNECT = 0xFF8B3A3A;
    private static final int COLOR_DISCONNECT_END = 0xFFB85454;
    private static final int COLOR_CONNECTED_DOT = 0xFF66BB6A;
    private static final int COLOR_FEATURE_BULLET = 0xFF9E84B6;

    public LitegramConnectionActivity() {
        super();
    }

    public LitegramConnectionActivity(Bundle args) {
        super(args);
    }

    private TextView statusText;
    private TextView subtitleText;
    private TextView serverValue;
    private TextView planValue;
    private TextView actionButton;
    private GradientDrawable actionBtnBg;
    private RLottieImageView lottieView;
    private View headerView;
    private int headerLottieRes;

    private boolean connected;
    private boolean connecting;
    private Runnable pollRunnable;
    private List<LitegramApi.ServerInfo> availableServers = new ArrayList<>();
    private FrameLayout serverRow;

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

    @Override
    public View createView(Context context) {
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

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(createHeader(context));
        content.addView(createInfoSection(context));
        content.addView(createActionButton(context));
        content.addView(createDivider(context));
        content.addView(createFeaturesSection(context));

        updateUI();

        fragmentView = scrollView;
        return fragmentView;
    }

    private View createHeader(Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        header.setPadding(0, 0, 0, AndroidUtilities.dp(28));
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(260)));

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{COLOR_HEADER_START, COLOR_HEADER_END});
        header.setBackground(bg);
        headerView = header;

        lottieView = new RLottieImageView(context);
        lottieView.setAutoRepeat(false);
        lottieView.setOnClickListener(v -> {
            if (lottieView.getAnimatedDrawable() != null) {
                lottieView.getAnimatedDrawable().setCurrentFrame(0, false);
            }
            lottieView.playAnimation();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                AndroidUtilities.dp(100), AndroidUtilities.dp(100));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        header.addView(lottieView, lp);

        statusText = new TextView(context);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        statusText.setTextColor(Color.WHITE);
        statusText.setTypeface(AndroidUtilities.bold());
        statusText.setGravity(Gravity.CENTER);
        statusText.setText("\u25CB " + LocaleController.getString(R.string.LitegramConnStatusDisconnected));
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        stp.topMargin = AndroidUtilities.dp(14);
        header.addView(statusText, stp);

        subtitleText = new TextView(context);
        subtitleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleText.setTextColor(0x99FFFFFF);
        subtitleText.setGravity(Gravity.CENTER);
        subtitleText.setText(LocaleController.getString(R.string.LitegramConnSubtitle));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = AndroidUtilities.dp(4);
        header.addView(subtitleText, sp);

        return header;
    }

    private View createInfoSection(Context context) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(18),
                AndroidUtilities.dp(22), AndroidUtilities.dp(8));

        serverRow = new FrameLayout(context);
        serverRow.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
        serverRow.setBackground(Theme.createSelectorDrawable(
                Theme.getColor(Theme.key_listSelector), 2));
        serverRow.setOnClickListener(v -> showServerPicker());

        TextView serverLabel = new TextView(context);
        serverLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        serverLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        serverLabel.setText(LocaleController.getString(R.string.LitegramConnServer));
        serverRow.addView(serverLabel, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL));

        LinearLayout serverRight = new LinearLayout(context);
        serverRight.setOrientation(LinearLayout.HORIZONTAL);
        serverRight.setGravity(Gravity.CENTER_VERTICAL);

        serverValue = new TextView(context);
        serverValue.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        serverValue.setTextColor(COLOR_ACCENT);
        serverValue.setGravity(Gravity.END);

        ImageView serverArrow = new ImageView(context);
        serverArrow.setScaleType(ImageView.ScaleType.CENTER);
        Drawable arrowD = context.getResources().getDrawable(R.drawable.msg_arrowright).mutate();
        arrowD.setColorFilter(new PorterDuffColorFilter(COLOR_ACCENT_LIGHT, PorterDuff.Mode.SRC_IN));
        serverArrow.setImageDrawable(arrowD);

        serverRight.addView(serverValue);
        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(
                AndroidUtilities.dp(20), AndroidUtilities.dp(20));
        arrowLp.leftMargin = AndroidUtilities.dp(4);
        serverRight.addView(serverArrow, arrowLp);

        serverRow.addView(serverRight, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL));

        section.addView(serverRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        planValue = addInfoRow(context, section, LocaleController.getString(R.string.LitegramConnPlan));

        loadServers();

        return section;
    }

    private TextView addInfoRow(Context context, LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));

        TextView labelView = new TextView(context);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        labelView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        labelView.setText(label);
        row.addView(labelView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(context);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        valueView.setGravity(Gravity.END);
        row.addView(valueView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        return valueView;
    }

    private View createActionButton(Context context) {
        FrameLayout buttonContainer = new FrameLayout(context);
        buttonContainer.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(12),
                AndroidUtilities.dp(22), AndroidUtilities.dp(12));

        actionButton = new TextView(context);
        actionButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        actionButton.setTextColor(Color.WHITE);
        actionButton.setTypeface(AndroidUtilities.bold());
        actionButton.setGravity(Gravity.CENTER);
        actionButton.setPadding(0, AndroidUtilities.dp(13), 0, AndroidUtilities.dp(13));

        actionBtnBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{COLOR_BTN_START, COLOR_BTN_END});
        actionBtnBg.setCornerRadius(AndroidUtilities.dp(10));
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

    private View createDivider(Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        params.topMargin = AndroidUtilities.dp(4);
        params.bottomMargin = AndroidUtilities.dp(4);
        params.leftMargin = AndroidUtilities.dp(22);
        params.rightMargin = AndroidUtilities.dp(22);
        divider.setLayoutParams(params);
        return divider;
    }

    private View createFeaturesSection(Context context) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(8),
                AndroidUtilities.dp(22), AndroidUtilities.dp(24));

        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(LocaleController.getString(R.string.LitegramConnFeatures));
        sectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        sectionTitle.setTypeface(AndroidUtilities.bold());
        sectionTitle.setTextColor(COLOR_ACCENT);
        sectionTitle.setAllCaps(true);
        sectionTitle.setLetterSpacing(0.08f);
        sectionTitle.setPadding(0, 0, 0, AndroidUtilities.dp(8));
        section.addView(sectionTitle);

        addFeatureRow(context, section, "\uD83D\uDD12",
                LocaleController.getString(R.string.LitegramConnFeatEncrypted),
                LocaleController.getString(R.string.LitegramConnFeatEncryptedDesc));
        addFeatureRow(context, section, "\u26A1",
                LocaleController.getString(R.string.LitegramConnFeatAutoConnect),
                LocaleController.getString(R.string.LitegramConnFeatAutoConnectDesc));
        addFeatureRow(context, section, "\uD83C\uDF0D",
                LocaleController.getString(R.string.LitegramConnFeatBypass),
                LocaleController.getString(R.string.LitegramConnFeatBypassDesc));
        addFeatureRow(context, section, "\uD83D\uDEE1\uFE0F",
                LocaleController.getString(R.string.LitegramConnFeatNoLogs),
                LocaleController.getString(R.string.LitegramConnFeatNoLogsDesc));
        addFeatureRow(context, section, "\uD83D\uDD17",
                LocaleController.getString(R.string.LitegramConnFeatBinding),
                LocaleController.getString(R.string.LitegramConnFeatBindingDesc));

        return section;
    }

    private void addFeatureRow(Context context, LinearLayout parent,
                               String emoji, String title, String description) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
        row.setGravity(Gravity.TOP);

        TextView emojiView = new TextView(context);
        emojiView.setText(emoji);
        emojiView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        LinearLayout.LayoutParams emojiLp = new LinearLayout.LayoutParams(
                AndroidUtilities.dp(32), ViewGroup.LayoutParams.WRAP_CONTENT);
        row.addView(emojiView, emojiLp);

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setText(title);
        textCol.addView(titleView);

        TextView descView = new TextView(context);
        descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        descView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        descView.setText(description);
        descView.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        textCol.addView(descView);

        row.addView(textCol, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private String findCurrentServerFlag() {
        String currentHost = LitegramConfig.getProxyHost();
        for (LitegramApi.ServerInfo s : availableServers) {
            if (s.host.equals(currentHost)) {
                return s.getFlagEmoji();
            }
        }
        return LitegramConfig.getProxyFlagEmoji();
    }

    private void loadServers() {
        List<LitegramApi.ServerInfo> cached = LitegramConfig.loadServersCache();
        if (!cached.isEmpty() && availableServers.isEmpty()) {
            availableServers.addAll(cached);
            updateUI();
        }
        LitegramController.getInstance().fetchServers((servers, error) -> {
            if (servers != null && !servers.isEmpty()) {
                availableServers.clear();
                availableServers.addAll(servers);
                updateUI();
            }
        });
        LitegramController.getInstance().refreshSubscription();
    }

    private void showServerPicker() {
        if (availableServers.isEmpty()) {
            if (getParentActivity() != null) {
                Toast.makeText(getParentActivity(),
                        LocaleController.getString(R.string.LitegramConnLoadingServers), Toast.LENGTH_SHORT).show();
            }
            loadServers();
            return;
        }

        Context ctx = getParentActivity();
        if (ctx == null) return;

        String currentHost = LitegramConfig.getProxyHost();

        BottomSheet.Builder builder = new BottomSheet.Builder(ctx);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(16));

        TextView title = new TextView(ctx);
        title.setText(LocaleController.getString(R.string.LitegramConnSelectServer));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(8),
                AndroidUtilities.dp(22), AndroidUtilities.dp(16));
        root.addView(title);

        for (int i = 0; i < availableServers.size(); i++) {
            LitegramApi.ServerInfo server = availableServers.get(i);
            boolean isCurrent = server.host.equals(currentHost);

            FrameLayout row = new FrameLayout(ctx);
            row.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(14),
                    AndroidUtilities.dp(22), AndroidUtilities.dp(14));
            row.setBackground(Theme.createSelectorDrawable(
                    Theme.getColor(Theme.key_listSelector), 2));

            LinearLayout leftPart = new LinearLayout(ctx);
            leftPart.setOrientation(LinearLayout.HORIZONTAL);
            leftPart.setGravity(Gravity.CENTER_VERTICAL);

            String flag = server.getFlagEmoji();
            if (!flag.isEmpty()) {
                TextView flagView = new TextView(ctx);
                flagView.setText(flag);
                flagView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
                LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                        AndroidUtilities.dp(36), ViewGroup.LayoutParams.WRAP_CONTENT);
                leftPart.addView(flagView, flp);
            }

            String baseName = server.name != null ? server.name : server.host;
            TextView nameView = new TextView(ctx);
            nameView.setText(baseName);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameView.setTextColor(isCurrent
                    ? COLOR_ACCENT
                    : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            if (isCurrent) {
                nameView.setTypeface(AndroidUtilities.bold());
            }
            leftPart.addView(nameView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            row.addView(leftPart, LayoutHelper.createFrame(
                    LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.START | Gravity.CENTER_VERTICAL));

            if (isCurrent) {
                View checkCircle = new View(ctx) {
                    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    {
                        circlePaint.setColor(COLOR_ACCENT);
                        circlePaint.setStyle(Paint.Style.FILL);
                        checkPaint.setColor(Color.WHITE);
                        checkPaint.setStyle(Paint.Style.STROKE);
                        checkPaint.setStrokeWidth(AndroidUtilities.dp(2));
                        checkPaint.setStrokeCap(Paint.Cap.ROUND);
                        checkPaint.setStrokeJoin(Paint.Join.ROUND);
                    }
                    @Override
                    protected void onDraw(Canvas canvas) {
                        float cx = getWidth() / 2f;
                        float cy = getHeight() / 2f;
                        float r = Math.min(cx, cy);
                        canvas.drawCircle(cx, cy, r, circlePaint);
                        float s = r * 0.45f;
                        canvas.drawLine(cx - s * 0.6f, cy, cx - s * 0.05f, cy + s * 0.5f, checkPaint);
                        canvas.drawLine(cx - s * 0.05f, cy + s * 0.5f, cx + s * 0.7f, cy - s * 0.5f, checkPaint);
                    }
                };
                row.addView(checkCircle, LayoutHelper.createFrame(
                        24, 24, Gravity.END | Gravity.CENTER_VERTICAL));
            }

            row.setOnClickListener(v -> {
                builder.getDismissRunnable().run();
                actionButton.setText(LocaleController.getString(R.string.LitegramConnConnecting));
                LitegramController.getInstance().connectToServer(server, (success, err) -> {
                    if (!success && getParentActivity() != null) {
                        String msg = err != null ? err : LocaleController.getString(R.string.LitegramConnUnknownError);
                        Toast.makeText(getParentActivity(),
                                LocaleController.formatString(R.string.LitegramConnFailed, msg), Toast.LENGTH_LONG).show();
                    }
                    updateUI();
                });
            });

            root.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        builder.setCustomView(root);
        showDialog(builder.create());
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
            if (connected) {
                statusText.setText("\u25CF " + LocaleController.getString(R.string.LitegramConnStatusConnected));
                statusText.setTextColor(COLOR_CONNECTED_DOT);
            } else if (connecting) {
                statusText.setText("\u25CB " + LocaleController.getString(R.string.LitegramConnStatusConnecting));
                statusText.setTextColor(COLOR_ACCENT_LIGHT);
            } else {
                statusText.setText("\u25CB " + LocaleController.getString(R.string.LitegramConnStatusDisconnected));
                statusText.setTextColor(0xAAFFFFFF);
            }
        }

        if (serverValue != null) {
            if ((connected || connecting) && LitegramConfig.hasProxy()) {
                String name = LitegramConfig.getProxyName();
                String display = name != null ? name : LitegramConfig.getProxyHost();
                String flag = findCurrentServerFlag();
                serverValue.setText(flag.isEmpty() ? display : flag + " " + display);
            } else {
                serverValue.setText(LocaleController.getString(R.string.LitegramConnNotConnected));
            }
        }

        if (planValue != null) {
            if (LitegramConfig.isSubscriptionActive()) {
                planValue.setText("\u2B50 " + LocaleController.getString(R.string.LitegramConnPremium));
                planValue.setTextColor(COLOR_ACCENT);
            } else {
                planValue.setText(LocaleController.getString(R.string.LitegramConnFree));
                planValue.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            }
        }

        if (actionButton != null && actionBtnBg != null) {
            if (connected || connecting) {
                actionButton.setText(LocaleController.getString(R.string.LitegramConnDisconnect));
                actionBtnBg.setColors(new int[]{COLOR_DISCONNECT, COLOR_DISCONNECT_END});
                actionBtnBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            } else {
                actionButton.setText(LocaleController.getString(R.string.LitegramConnConnect));
                actionBtnBg.setColors(new int[]{COLOR_BTN_START, COLOR_BTN_END});
                actionBtnBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            }
            actionButton.setEnabled(true);
            actionButton.setAlpha(1f);
        }

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
