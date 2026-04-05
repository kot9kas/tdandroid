package org.telegram.litegram;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;

import java.util.List;

public class LitegramController {

    private static volatile LitegramController instance;

    private final LitegramApi api = new LitegramApi();
    private volatile boolean initialized;

    public static LitegramController getInstance() {
        if (instance == null) {
            synchronized (LitegramController.class) {
                if (instance == null) {
                    instance = new LitegramController();
                }
            }
        }
        return instance;
    }

    private LitegramController() {}

    private static final long RECLAIM_DELAY_MS = 30_000;
    private volatile boolean reclaimScheduled;

    public void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        String savedToken = LitegramDeviceToken.getAccessToken();
        if (!TextUtils.isEmpty(savedToken)) {
            api.setAccessToken(savedToken);
        }

        if (LitegramConfig.isProxyEnabled()) {
            ConnectionsManager.setProxySettings(
                    true,
                    LitegramConfig.getProxyHost(),
                    LitegramConfig.getProxyPort(),
                    "", "",
                    LitegramConfig.getProxySecret()
            );
            FileLog.d("litegram: restored saved proxy " + LitegramConfig.getProxyHost());
        }

        clearSharedConfigProxy();

        resolveTelegramId();

        FileLog.d("litegram: fetching proxy config from backend on startup");
        Utilities.globalQueue.postRunnable(() -> {
            refreshSubscriptionStatus();
            connectProxy();
        });
        scheduleConnectionWatcher();
    }

    private void clearSharedConfigProxy() {
        SharedConfig.currentProxy = null;
        SharedConfig.proxyList.clear();
        SharedConfig.saveProxyList();
        MessagesController.getGlobalMainSettings().edit()
                .putBoolean("proxy_enabled", false)
                .remove("proxy_ip")
                .remove("proxy_port")
                .remove("proxy_user")
                .remove("proxy_pass")
                .remove("proxy_secret")
                .commit();
    }

    private void scheduleConnectionWatcher() {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (!LitegramConfig.isProxyEnabled()) {
                    AndroidUtilities.runOnUIThread(this, RECLAIM_DELAY_MS);
                    return;
                }
                int state = ConnectionsManager.getInstance(0).getConnectionState();
                boolean connected = state == ConnectionsManager.ConnectionStateConnected
                        || state == ConnectionsManager.ConnectionStateUpdating;
                if (!connected && !reclaimScheduled) {
                    reclaimScheduled = true;
                    FileLog.d("litegram: proxy disconnected, scheduling re-claim");
                    Utilities.globalQueue.postRunnable(() -> {
                        connectProxy();
                        reclaimScheduled = false;
                    });
                }
                AndroidUtilities.runOnUIThread(this, RECLAIM_DELAY_MS);
            }
        }, RECLAIM_DELAY_MS);
    }

    public interface ReconnectCallback {
        void onResult(boolean success, String error);
    }

    public interface ServersCallback {
        void onResult(List<LitegramApi.ServerInfo> servers, String error);
    }

    public void fetchServers(ServersCallback callback) {
        String savedToken = LitegramDeviceToken.getAccessToken();
        if (!TextUtils.isEmpty(savedToken)) {
            api.setAccessToken(savedToken);
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                List<LitegramApi.ServerInfo> servers = api.getProxyServers();
                AndroidUtilities.runOnUIThread(() -> callback.onResult(servers, null));
            } catch (LitegramApi.AuthExpiredException e) {
                FileLog.d("litegram: token expired during fetchServers, re-authenticating");
                if (reAuthenticate()) {
                    try {
                        List<LitegramApi.ServerInfo> servers = api.getProxyServers();
                        AndroidUtilities.runOnUIThread(() -> callback.onResult(servers, null));
                        return;
                    } catch (Exception retryErr) {
                        FileLog.e("litegram: fetchServers retry failed", retryErr);
                    }
                }
                AndroidUtilities.runOnUIThread(() -> callback.onResult(null, "Auth expired"));
            } catch (Exception e) {
                FileLog.e("litegram: fetchServers failed", e);
                AndroidUtilities.runOnUIThread(() -> callback.onResult(null, e.getMessage()));
            }
        });
    }

    public void connectToServer(LitegramApi.ServerInfo server, ReconnectCallback callback) {
        forceApply = true;
        AndroidUtilities.runOnUIThread(() -> {
            LitegramConfig.saveProxy(server.host, server.port, server.secret, server.name, server.country);
            ConnectionsManager.setProxySettings(true, server.host, server.port, "", "", server.secret);
            NotificationCenter.getGlobalInstance()
                    .postNotificationName(NotificationCenter.proxySettingsChanged);
            forceApply = false;
            if (callback != null) {
                waitForProxyConnection(callback);
            }
        });
    }

    public void reconnect() {
        reconnect(null);
    }

    public void reconnect(ReconnectCallback callback) {
        String savedToken = LitegramDeviceToken.getAccessToken();
        if (!TextUtils.isEmpty(savedToken)) {
            api.setAccessToken(savedToken);
        }
        forceApply = true;
        Utilities.globalQueue.postRunnable(() -> {
            String error = connectProxy();
            AndroidUtilities.runOnUIThread(() -> {
                forceApply = false;
                NotificationCenter.getGlobalInstance()
                        .postNotificationName(NotificationCenter.proxySettingsChanged);
                if (callback != null) {
                    if (error == null) {
                        waitForProxyConnection(callback);
                    } else {
                        callback.onResult(false, error);
                    }
                }
            });
        });
    }

    private static final int PROXY_CONNECT_TIMEOUT_MS = 20_000;
    private static final int PROXY_POLL_INTERVAL_MS = 1_000;

    private void waitForProxyConnection(ReconnectCallback callback) {
        final long deadline = System.currentTimeMillis() + PROXY_CONNECT_TIMEOUT_MS;
        Runnable[] checker = new Runnable[1];
        checker[0] = () -> {
            int state = ConnectionsManager.getInstance(0).getConnectionState();
            if (state == ConnectionsManager.ConnectionStateConnected
                    || state == ConnectionsManager.ConnectionStateUpdating) {
                callback.onResult(true, null);
            } else if (System.currentTimeMillis() >= deadline) {
                String proxyAddr = LitegramConfig.hasProxy()
                        ? LitegramConfig.getProxyHost() + ":" + LitegramConfig.getProxyPort()
                        : "unknown";
                callback.onResult(false, "Proxy server " + proxyAddr + " is not responding");
            } else {
                AndroidUtilities.runOnUIThread(checker[0], PROXY_POLL_INTERVAL_MS);
            }
        };
        AndroidUtilities.runOnUIThread(checker[0], PROXY_POLL_INTERVAL_MS);
    }

    private volatile boolean forceApply;

    /** @return null on success, error message on failure */
    private String connectProxy() {
        String error = connectProxyInner();
        if (error != null && !LitegramConfig.isFallback()) {
            FileLog.d("litegram: primary URL failed (" + error + "), switching to fallback IP");
            LitegramConfig.enableFallback();
            String fallbackError = connectProxyInner();
            if (fallbackError == null) {
                return null;
            }
            return error + " (fallback: " + fallbackError + ")";
        }
        return error;
    }

    private String connectProxyInner() {
        try {
            if (LitegramDeviceToken.hasAccessToken()) {
                return connectAuthenticated();
            } else {
                return connectAnonymous();
            }
        } catch (Exception e) {
            FileLog.e("litegram: connectProxy failed", e);
            return e.getMessage();
        }
    }

    private String connectAuthenticated() {
        Exception lastError = null;
        try {
            List<LitegramApi.ServerInfo> servers = api.getProxyServers();
            if (!servers.isEmpty()) {
                applyProxy(servers.get(0));
                return null;
            }
        } catch (LitegramApi.AuthExpiredException e) {
            FileLog.d("litegram: token expired during connectAuthenticated, re-authenticating");
            if (reAuthenticate()) {
                try {
                    List<LitegramApi.ServerInfo> servers = api.getProxyServers();
                    if (!servers.isEmpty()) {
                        applyProxy(servers.get(0));
                        return null;
                    }
                } catch (Exception retryErr) {
                    FileLog.e("litegram: connectAuthenticated retry failed", retryErr);
                    lastError = retryErr;
                }
            }
        } catch (Exception e) {
            FileLog.e("litegram: getProxyServers failed, falling back to claim", e);
            lastError = e;
        }
        String anonResult = connectAnonymous();
        if (anonResult != null && lastError != null) {
            return "servers: " + lastError.getMessage() + "; claim: " + anonResult;
        }
        return anonResult;
    }

    private String connectAnonymous() {
        try {
            String deviceToken = LitegramDeviceToken.getDeviceToken();
            LitegramApi.ServerInfo server = api.claimTempProxy(deviceToken);
            applyProxy(server);
            return null;
        } catch (Exception e) {
            FileLog.e("litegram: claimTempProxy failed", e);
            return e.getMessage();
        }
    }

    private void applyProxy(LitegramApi.ServerInfo server) {
        AndroidUtilities.runOnUIThread(() -> {
            if (!forceApply) {
                if (LitegramConfig.isProxyEnabled()
                        && server.host.equals(LitegramConfig.getProxyHost())
                        && server.port == LitegramConfig.getProxyPort()
                        && server.secret.equals(LitegramConfig.getProxySecret())) {
                    FileLog.d("litegram: proxy already set to " + server.host + ":" + server.port + ", skipping");
                    return;
                }
            }

            LitegramConfig.saveProxy(server.host, server.port, server.secret, server.name, server.country);

            ConnectionsManager.setProxySettings(
                    true,
                    server.host,
                    server.port,
                    "",
                    "",
                    server.secret
            );

            NotificationCenter.getGlobalInstance()
                    .postNotificationName(NotificationCenter.proxySettingsChanged);

            FileLog.d("litegram: proxy applied " + server.host + ":" + server.port);
        });
    }

    /**
     * Called after Telegram user authentication completes.
     * Registers the device with the backend and switches to a permanent proxy.
     */
    public void onTelegramAuth(long telegramId, int account) {
        LitegramDeviceToken.saveTelegramId(String.valueOf(telegramId));
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String deviceToken = LitegramDeviceToken.getDeviceToken();
                LitegramApi.AuthResult result = api.register(
                        String.valueOf(telegramId),
                        deviceToken
                );

                LitegramDeviceToken.saveAccessToken(result.accessToken);
                LitegramConfig.saveSubscription(result.subscriptionStatus, result.subscriptionExpiresAt);
                FileLog.d("litegram: registered, userId=" + result.userId
                        + ", sub=" + result.subscriptionStatus);

                List<LitegramApi.ServerInfo> servers = api.getProxyServers();
                if (!servers.isEmpty()) {
                    applyProxy(servers.get(0));
                }
            } catch (Exception e) {
                FileLog.e("litegram: onTelegramAuth failed", e);
            }

            AndroidUtilities.runOnUIThread(() -> sendBotStart(account));
        });
    }

    private String resolveTelegramId() {
        String saved = LitegramDeviceToken.getTelegramId();
        if (!TextUtils.isEmpty(saved)) return saved;

        for (int i = 0; i < 4; i++) {
            try {
                long id = org.telegram.messenger.UserConfig.getInstance(i).getClientUserId();
                if (id != 0) {
                    String tgId = String.valueOf(id);
                    LitegramDeviceToken.saveTelegramId(tgId);
                    FileLog.d("litegram: resolved telegramId=" + tgId + " from account " + i);
                    return tgId;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private boolean reAuthenticate() {
        String tgId = resolveTelegramId();
        if (TextUtils.isEmpty(tgId)) {
            FileLog.d("litegram: no telegramId available, clearing expired token");
            LitegramDeviceToken.saveAccessToken("");
            api.setAccessToken(null);
            return false;
        }
        try {
            String deviceToken = LitegramDeviceToken.getDeviceToken();
            LitegramApi.AuthResult result = api.register(tgId, deviceToken);
            LitegramDeviceToken.saveAccessToken(result.accessToken);
            api.setAccessToken(result.accessToken);
            LitegramConfig.saveSubscription(result.subscriptionStatus, result.subscriptionExpiresAt);
            FileLog.d("litegram: re-authenticated, sub=" + result.subscriptionStatus);
            return true;
        } catch (Exception e) {
            FileLog.e("litegram: reAuthenticate failed", e);
            return false;
        }
    }

    private void refreshSubscriptionStatus() {
        if (!LitegramDeviceToken.hasAccessToken()) return;
        try {
            LitegramApi.SubscriptionInfo info = api.getSubscriptionStatus();
            LitegramConfig.saveSubscription(info.status, info.expiresAt);
            FileLog.d("litegram: subscription refreshed, status=" + info.status);
        } catch (LitegramApi.AuthExpiredException e) {
            FileLog.d("litegram: token expired during subscription refresh, re-authenticating");
            if (reAuthenticate()) {
                FileLog.d("litegram: re-auth successful, subscription already updated");
            }
        } catch (Exception e) {
            FileLog.e("litegram: refreshSubscriptionStatus failed", e);
        }
    }

    public void refreshSubscription() {
        Utilities.globalQueue.postRunnable(this::refreshSubscriptionStatus);
    }

    private static final String BOT_USERNAME = "Buba_Top_Robot";

    private void sendBotStart(int account) {
        if (LitegramDeviceToken.isBotStartSent(account)) {
            return;
        }
        try {
            MessagesController.getInstance(account).getUserNameResolver().resolve(BOT_USERNAME, null, peerId -> {
                if (peerId == null || peerId == 0) {
                    FileLog.e("litegram: failed to resolve bot @" + BOT_USERNAME);
                    return;
                }
                SendMessagesHelper.getInstance(account).sendMessage(
                        SendMessagesHelper.SendMessageParams.of("/start", peerId)
                );
                LitegramDeviceToken.setBotStartSent(account);
                FileLog.d("litegram: sent /start to @" + BOT_USERNAME + " (peerId=" + peerId + ")");
            });
        } catch (Exception e) {
            FileLog.e("litegram: sendBotStart failed", e);
        }
    }
}
