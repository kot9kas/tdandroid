package org.telegram.litegram;

import android.content.Context;
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
import android.widget.TextView;

import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
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
    private RLottieImageView lottieView;
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
        actionBar.setTitle("Litegram Connection");
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
        header.setPadding(0, 0, 0, AndroidUtilities.dp(24));
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(260)));

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFAE8BA1, 0xFFF2ECB6});
        header.setBackground(bg);

        lottieView = new RLottieImageView(context);
        lottieView.setAutoRepeat(false);
        lottieView.setOnClickListener(v -> {
            if (lottieView.getAnimatedDrawable() != null) {
                lottieView.getAnimatedDrawable().setCurrentFrame(0, false);
            }
            lottieView.playAnimation();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                AndroidUtilities.dp(110), AndroidUtilities.dp(110));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        header.addView(lottieView, lp);

        statusText = new TextView(context);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        statusText.setTextColor(Color.WHITE);
        statusText.setTypeface(AndroidUtilities.bold());
        statusText.setGravity(Gravity.CENTER);
        statusText.setShadowLayer(4, 0, 2, 0x40000000);
        statusText.setText("\u25CB Disconnected");
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        stp.topMargin = AndroidUtilities.dp(12);
        header.addView(statusText, stp);

        subtitleText = new TextView(context);
        subtitleText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleText.setTextColor(0xCCFFFFFF);
        subtitleText.setGravity(Gravity.CENTER);
        subtitleText.setText("Secure proxy for unrestricted access");
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
        section.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(16),
                AndroidUtilities.dp(22), AndroidUtilities.dp(8));

        serverRow = new FrameLayout(context);
        serverRow.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        serverRow.setBackground(Theme.createSelectorDrawable(
                Theme.getColor(Theme.key_listSelector), 2));
        serverRow.setOnClickListener(v -> showServerPicker());

        TextView serverLabel = new TextView(context);
        serverLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        serverLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        serverLabel.setText("Server");
        serverRow.addView(serverLabel, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL));

        LinearLayout serverRight = new LinearLayout(context);
        serverRight.setOrientation(LinearLayout.HORIZONTAL);
        serverRight.setGravity(Gravity.CENTER_VERTICAL);

        serverValue = new TextView(context);
        serverValue.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        serverValue.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
        serverValue.setGravity(Gravity.END);

        ImageView serverArrow = new ImageView(context);
        serverArrow.setScaleType(ImageView.ScaleType.CENTER);
        Drawable arrowD = context.getResources().getDrawable(R.drawable.msg_arrowright).mutate();
        arrowD.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), PorterDuff.Mode.SRC_IN));
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

        planValue = addInfoRow(context, section, "Plan");

        loadServers();

        return section;
    }

    private TextView addInfoRow(Context context, LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));

        TextView labelView = new TextView(context);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        labelView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        labelView.setText(label);
        row.addView(labelView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(context);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
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
        buttonContainer.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(8),
                AndroidUtilities.dp(22), AndroidUtilities.dp(8));

        actionButton = new TextView(context);
        actionButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        actionButton.setTextColor(Color.WHITE);
        actionButton.setGravity(Gravity.CENTER);
        actionButton.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        actionButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                AndroidUtilities.dp(8),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));

        actionButton.setOnClickListener(v -> {
            if (connected || connecting) {
                LitegramConfig.setProxyEnabled(false);
                ConnectionsManager.setProxySettings(false, "", 0, "", "", "");
                NotificationCenter.getGlobalInstance()
                        .postNotificationName(NotificationCenter.proxySettingsChanged);
                updateUI();
            } else {
                actionButton.setText("Connecting...");
                LitegramController.getInstance().reconnect((success, error) -> {
                    if (!success && getParentActivity() != null) {
                        String msg = error != null ? error : "Unknown error";
                        Toast.makeText(getParentActivity(),
                                "Connection failed: " + msg, Toast.LENGTH_LONG).show();
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
        params.topMargin = AndroidUtilities.dp(8);
        params.bottomMargin = AndroidUtilities.dp(8);
        divider.setLayoutParams(params);
        return divider;
    }

    private View createFeaturesSection(Context context) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(8),
                AndroidUtilities.dp(22), AndroidUtilities.dp(16));

        addFeatureRow(context, section,
                "Auto-Connect",
                "Connects to the best proxy on every launch.");
        addFeatureRow(context, section,
                "Encrypted Tunnels",
                "Traffic routed through MTProto encrypted proxy.");
        addFeatureRow(context, section,
                "Bypass Restrictions",
                "Access Telegram in censored regions.");
        addFeatureRow(context, section,
                "No Logs",
                "No connection logs or activity tracking.");
        addFeatureRow(context, section,
                "Device Binding",
                "Unique proxy assigned per device for security.");

        return section;
    }

    private void addFeatureRow(Context context, LinearLayout parent, String title, String description) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));

        TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setText(title);
        row.addView(titleView);

        TextView descView = new TextView(context);
        descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        descView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        descView.setText(description);
        descView.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        row.addView(descView);

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
                        "Loading servers...", Toast.LENGTH_SHORT).show();
            }
            loadServers();
            return;
        }

        Context ctx = getParentActivity();
        if (ctx == null) return;

        String currentHost = LitegramConfig.getProxyHost();

        BottomSheet.Builder builder = new BottomSheet.Builder(ctx);
        builder.setTitle("Select Server");

        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));

        for (int i = 0; i < availableServers.size(); i++) {
            LitegramApi.ServerInfo server = availableServers.get(i);
            String baseName = server.name != null ? server.name : server.host;
            String flag = server.getFlagEmoji();
            String displayName = flag.isEmpty() ? baseName : flag + " " + baseName;
            boolean isCurrent = server.host.equals(currentHost);

            TextView item = new TextView(ctx);
            item.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            item.setTextColor(isCurrent
                    ? Theme.getColor(Theme.key_windowBackgroundWhiteValueText)
                    : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            item.setTypeface(isCurrent ? AndroidUtilities.bold() : null);
            item.setText(isCurrent ? "\u2713 " + displayName : "    " + displayName);
            item.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(14),
                    AndroidUtilities.dp(22), AndroidUtilities.dp(14));
            item.setBackground(Theme.createSelectorDrawable(
                    Theme.getColor(Theme.key_listSelector), 2));

            item.setOnClickListener(v -> {
                builder.getDismissRunnable().run();
                actionButton.setText("Connecting...");
                LitegramController.getInstance().connectToServer(server, (success, err) -> {
                    if (!success && getParentActivity() != null) {
                        String msg = err != null ? err : "Unknown error";
                        Toast.makeText(getParentActivity(),
                                "Connection failed: " + msg, Toast.LENGTH_LONG).show();
                    }
                    updateUI();
                });
            });

            list.addView(item, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        builder.setCustomView(list);
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
                statusText.setText("\u25CF Connected");
            } else if (connecting) {
                statusText.setText("\u25CB Connecting...");
            } else {
                statusText.setText("\u25CB Disconnected");
            }
        }

        if (serverValue != null) {
            if ((connected || connecting) && LitegramConfig.hasProxy()) {
                String name = LitegramConfig.getProxyName();
                String display = name != null ? name : LitegramConfig.getProxyHost();
                String flag = findCurrentServerFlag();
                serverValue.setText(flag.isEmpty() ? display : flag + " " + display);
            } else {
                serverValue.setText("Not connected");
            }
        }

        if (planValue != null) {
            planValue.setText(LitegramConfig.isSubscriptionActive() ? "Premium" : "Free");
        }

        if (actionButton != null) {
            if (connecting) {
                actionButton.setText("Disconnect");
                actionButton.setEnabled(true);
                actionButton.setAlpha(1f);
            } else if (connected) {
                actionButton.setText("Disconnect");
                actionButton.setEnabled(true);
                actionButton.setAlpha(1f);
            } else {
                actionButton.setText("Connect");
                actionButton.setEnabled(true);
                actionButton.setAlpha(1f);
            }
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
        lottieView.setAnimation(want, 110, 110);
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
