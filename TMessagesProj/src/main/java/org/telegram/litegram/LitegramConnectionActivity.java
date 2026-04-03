package org.telegram.litegram;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RLottieImageView;

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

    private boolean connected;
    private boolean connecting;
    private Runnable pollRunnable;

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
        lottieView.setAnimation(R.raw.utyan_passcode, 110, 110);
        lottieView.setAutoRepeat(false);
        lottieView.playAnimation();
        lottieView.setOnClickListener(v -> {
            lottieView.getAnimatedDrawable().setCurrentFrame(0, false);
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

        serverValue = addInfoRow(context, section, "Server");
        planValue = addInfoRow(context, section, "Plan");

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
            if (connecting) return;
            if (connected) {
                SharedConfig.currentProxy = null;
                org.telegram.messenger.MessagesController.getGlobalMainSettings().edit()
                        .putBoolean("proxy_enabled", false).commit();
                SharedConfig.saveProxyList();
                ConnectionsManager.setProxySettings(false, "", 0, "", "", "");
                NotificationCenter.getGlobalInstance()
                        .postNotificationName(NotificationCenter.proxySettingsChanged);
            } else {
                actionButton.setText("Connecting...");
                actionButton.setEnabled(false);
                actionButton.setAlpha(0.6f);
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

    private void updateUI() {
        int connectionState = ConnectionsManager.getInstance(currentAccount).getConnectionState();
        boolean proxyEnabled = SharedConfig.isProxyEnabled();
        connected = proxyEnabled
                && (connectionState == ConnectionsManager.ConnectionStateConnected
                || connectionState == ConnectionsManager.ConnectionStateUpdating);
        connecting = proxyEnabled
                && connectionState == ConnectionsManager.ConnectionStateConnectingToProxy;

        if (statusText != null) {
            String dot = connected ? "\u25CF " : "\u25CB ";
            statusText.setText(dot + (connected ? "Connected" : connecting ? "Connecting..." : "Disconnected"));
        }

        if (serverValue != null) {
            if (connected && SharedConfig.currentProxy != null) {
                serverValue.setText(SharedConfig.currentProxy.address);
            } else {
                serverValue.setText("Not connected");
            }
        }

        if (planValue != null) {
            planValue.setText("Free");
        }

        if (actionButton != null) {
            if (connecting) {
                actionButton.setText("Connecting...");
                actionButton.setEnabled(false);
                actionButton.setAlpha(0.6f);
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
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxySettingsChanged || id == NotificationCenter.didUpdateConnectionState) {
            AndroidUtilities.runOnUIThread(this::updateUI);
        }
    }
}
