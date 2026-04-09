package org.telegram.litegram;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;

import java.util.ArrayList;
import java.util.List;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    private static final long WATCHER_POLL_MS = 1_000;
    private static final long NO_PROXY_GRACE_MS = 3_000;
    private static final long DIRECT_RECONNECT_GRACE_MS = 5_000;
    private static final long PROXY_RETRY_COOLDOWN_MS = 3_000;
    private static final long APP_FOREGROUND_RECHECK_MS = 2_000;
    private static final long UNAUTHORIZED_PROXY_RETRY_MS = 7_000;
    private volatile boolean reclaimScheduled;
    private volatile long noProxyConnectingSinceMs;
    private volatile long lastProxyAttemptAtMs;
    private volatile long lastAppForegroundCheckMs;
    private volatile long lastUnauthorizedProxyAttemptMs;
    private volatile boolean startupConnect = true;
    private volatile long directCheckStartedAtMs;
    private volatile boolean proxyAppliedByUs;
    private volatile long lastDirectConnectedMs;

    public void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        String savedToken = LitegramDeviceToken.getAccessToken();
        if (!TextUtils.isEmpty(savedToken)) {
            api.setAccessToken(savedToken);
        }

        if (LitegramConfig.hasProxy()) {
            // Start with direct connection first; enable proxy only when needed (>3s connecting).
            ConnectionsManager.setProxySettings(false, "", 0, "", "", "");
        }

        clearSharedConfigProxy();
        proxyAppliedByUs = false;
        directCheckStartedAtMs = System.currentTimeMillis();

        resolveTelegramId();

        Utilities.globalQueue.postRunnable(() -> {
            refreshSubscriptionStatus();
            fetchServersForCache();
        });
        scheduleConnectionWatcher();
    }

    /**
     * Re-check direct connectivity whenever app is opened/resumed.
     * If direct works, proxy stays disabled. Fallback logic enables proxy only when needed.
     */
    public void onAppForeground() {
        long now = System.currentTimeMillis();
        if (now - lastAppForegroundCheckMs < APP_FOREGROUND_RECHECK_MS) {
            return;
        }
        lastAppForegroundCheckMs = now;

        noProxyConnectingSinceMs = 0;
        directCheckStartedAtMs = now;
        reclaimScheduled = false;
        proxyAppliedByUs = false;

        AndroidUtilities.runOnUIThread(() -> {
            ConnectionsManager.setProxySettings(false, "", 0, "", "", "");
            clearSharedConfigProxy();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        });
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
                int state = ConnectionsManager.getInstance(0).getConnectionState();
                boolean connected = state == ConnectionsManager.ConnectionStateConnected
                        || state == ConnectionsManager.ConnectionStateUpdating;
                boolean connecting = state == ConnectionsManager.ConnectionStateConnecting
                        || state == ConnectionsManager.ConnectionStateConnectingToProxy;
                boolean waitingForNetwork = state == ConnectionsManager.ConnectionStateWaitingForNetwork;
                long now = System.currentTimeMillis();

                if (!proxyAppliedByUs) {
                    // Direct mode: proxy is off, check if direct connection works
                    if (connected) {
                        noProxyConnectingSinceMs = 0;
                        lastDirectConnectedMs = now;
                    } else if (waitingForNetwork) {
                        noProxyConnectingSinceMs = 0;
                    } else if (connecting) {
                        if (noProxyConnectingSinceMs == 0) {
                            noProxyConnectingSinceMs = now;
                        } else {
                            long grace = NO_PROXY_GRACE_MS;
                            if (lastDirectConnectedMs > 0 && now - lastDirectConnectedMs < 60_000) {
                                grace = DIRECT_RECONNECT_GRACE_MS;
                            }
                            if (now - noProxyConnectingSinceMs >= grace) {
                                maybeReconnectProxy("litegram: direct connection >" + (grace / 1000) + "s, enabling proxy fallback", now);
                            }
                        }
                    } else {
                        if (directCheckStartedAtMs > 0 && now - directCheckStartedAtMs >= NO_PROXY_GRACE_MS) {
                            maybeReconnectProxy("litegram: startup direct check timeout, enabling proxy fallback", now);
                            directCheckStartedAtMs = 0;
                        }
                    }
                } else {
                    // Proxy mode: proxy was applied by us, monitor its health
                    noProxyConnectingSinceMs = 0;
                    directCheckStartedAtMs = 0;
                    if (!connected) {
                        maybeReconnectProxy("litegram: proxy disconnected, re-claim", now);
                    }
                }
                AndroidUtilities.runOnUIThread(this, WATCHER_POLL_MS);
            }
        }, WATCHER_POLL_MS);
    }

    private void maybeReconnectProxy(String reason, long nowMs) {
        if (reclaimScheduled || nowMs - lastProxyAttemptAtMs < PROXY_RETRY_COOLDOWN_MS) {
            return;
        }
        if (UserConfig.getActivatedAccountsCount() == 0
                && nowMs - lastUnauthorizedProxyAttemptMs < UNAUTHORIZED_PROXY_RETRY_MS) {
            return;
        }
        reclaimScheduled = true;
        lastProxyAttemptAtMs = nowMs;
        if (UserConfig.getActivatedAccountsCount() == 0) {
            lastUnauthorizedProxyAttemptMs = nowMs;
        }
        FileLog.d(reason);
        Utilities.globalQueue.postRunnable(() -> {
            connectProxy();
            reclaimScheduled = false;
        });
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
                LitegramConfig.saveServersCache(servers);
                AndroidUtilities.runOnUIThread(() -> callback.onResult(servers, null));
            } catch (LitegramApi.AuthExpiredException e) {
                FileLog.d("litegram: token expired during fetchServers, re-authenticating");
                if (reAuthenticate()) {
                    try {
                        List<LitegramApi.ServerInfo> servers = api.getProxyServers();
                        LitegramConfig.saveServersCache(servers);
                        AndroidUtilities.runOnUIThread(() -> callback.onResult(servers, null));
                        return;
                    } catch (Exception retryErr) {
                        FileLog.e("litegram: fetchServers retry failed", retryErr);
                    }
                }
                List<LitegramApi.ServerInfo> cached = LitegramConfig.loadServersCache();
                AndroidUtilities.runOnUIThread(() -> {
                    if (!cached.isEmpty()) {
                        callback.onResult(cached, null);
                    } else {
                        callback.onResult(null, "Auth expired");
                    }
                });
            } catch (Exception e) {
                FileLog.e("litegram: fetchServers failed", e);
                List<LitegramApi.ServerInfo> cached = LitegramConfig.loadServersCache();
                AndroidUtilities.runOnUIThread(() -> {
                    if (!cached.isEmpty()) {
                        callback.onResult(cached, null);
                    } else {
                        callback.onResult(null, e.getMessage());
                    }
                });
            }
        });
    }

    public void connectToServer(LitegramApi.ServerInfo server, ReconnectCallback callback) {
        forceApply = true;
        AndroidUtilities.runOnUIThread(() -> {
            proxyAppliedByUs = true;
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

    private static final int PROXY_CONNECT_TIMEOUT_MS = 5_000;
    private static final int PROXY_POLL_INTERVAL_MS = 300;

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

    private static final String DEFAULT_COUNTRY = "RU";
    private static final int AUTO_SWITCH_SERVER_BUDGET_MS = 3_000;
    private static final int AUTO_SWITCH_POLL_MS = 150;
    private static final int QUICK_PROBE_TIMEOUT_MS = 500;

    private LitegramApi.ServerInfo pickServerForContext(List<LitegramApi.ServerInfo> servers) {
        if (startupConnect) {
            startupConnect = false;
            String savedHost = LitegramConfig.getProxyHost();
            if (!TextUtils.isEmpty(savedHost)) {
                for (LitegramApi.ServerInfo s : servers) {
                    if (savedHost.equals(s.host)) {
                        FileLog.d("litegram: startup — restoring user's saved server " + s.host);
                        return s;
                    }
                }
            }
            for (LitegramApi.ServerInfo s : servers) {
                if (DEFAULT_COUNTRY.equalsIgnoreCase(s.country)) {
                    FileLog.d("litegram: startup — default country server " + s.host);
                    return s;
                }
            }
            return servers.get(0);
        }
        return pickPreferredServer(servers);
    }

    private LitegramApi.ServerInfo pickPreferredServer(List<LitegramApi.ServerInfo> servers) {
        String savedHost = LitegramConfig.getProxyHost();
        if (!TextUtils.isEmpty(savedHost)) {
            for (LitegramApi.ServerInfo s : servers) {
                if (savedHost.equals(s.host)) {
                    FileLog.d("litegram: restoring previously selected server " + s.host);
                    return s;
                }
            }
        }
        for (LitegramApi.ServerInfo s : servers) {
            if (DEFAULT_COUNTRY.equalsIgnoreCase(s.country)) {
                FileLog.d("litegram: using default country server " + s.host);
                return s;
            }
        }
        return servers.get(0);
    }

    private boolean isRussiaServer(LitegramApi.ServerInfo server) {
        return server != null && DEFAULT_COUNTRY.equalsIgnoreCase(server.country);
    }

    private List<LitegramApi.ServerInfo> buildAutofallbackOrder(List<LitegramApi.ServerInfo> servers) {
        List<LitegramApi.ServerInfo> nonRu = new ArrayList<>();
        List<LitegramApi.ServerInfo> ru = new ArrayList<>();
        for (LitegramApi.ServerInfo s : servers) {
            if (isRussiaServer(s)) {
                ru.add(s);
            } else {
                nonRu.add(s);
            }
        }
        nonRu.addAll(ru);
        return nonRu;
    }

    private boolean isConnectedState() {
        int state = ConnectionsManager.getInstance(0).getConnectionState();
        return state == ConnectionsManager.ConnectionStateConnected
                || state == ConnectionsManager.ConnectionStateUpdating;
    }

    private void applyProxySync(LitegramApi.ServerInfo server) {
        CountDownLatch latch = new CountDownLatch(1);
        AndroidUtilities.runOnUIThread(() -> {
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
            proxyAppliedByUs = true;
            latch.countDown();
        });
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean waitConnected(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isConnectedState()) {
                return true;
            }
            try {
                Thread.sleep(AUTO_SWITCH_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return isConnectedState();
    }

    private boolean quickTcpProbe(LitegramApi.ServerInfo server) {
        if (server == null || TextUtils.isEmpty(server.host) || server.port <= 0) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(server.host, server.port), QUICK_PROBE_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Priority: all locations except RU, then RU as last fallback.
     * Each location gets a 5s connection window.
     */
    private LitegramApi.ServerInfo connectWithAutoFallback(List<LitegramApi.ServerInfo> servers) {
        List<LitegramApi.ServerInfo> ordered = buildAutofallbackOrder(servers);
        for (LitegramApi.ServerInfo s : ordered) {
            long attemptStart = System.currentTimeMillis();
            String label = (s.name != null && !s.name.isEmpty()) ? s.name : s.host;
            FileLog.d("litegram: trying proxy " + label + " (" + s.country + ")");
            if (!quickTcpProbe(s)) {
                FileLog.d("litegram: fast probe failed, skipping " + s.host + ":" + s.port);
                continue;
            }
            applyProxySync(s);
            long elapsed = System.currentTimeMillis() - attemptStart;
            long remaining = AUTO_SWITCH_SERVER_BUDGET_MS - elapsed;
            if (remaining <= 0) {
                FileLog.d("litegram: attempt budget exceeded, switching immediately");
                continue;
            }
            if (waitConnected(remaining)) {
                FileLog.d("litegram: connected via " + s.host + ":" + s.port);
                return s;
            }
            FileLog.d("litegram: proxy timed out quickly, trying next location");
        }
        return null;
    }

    private String connectAuthenticated() {
        Exception lastError = null;
        List<LitegramApi.ServerInfo> cached = LitegramConfig.loadServersCache();
        if (!cached.isEmpty()) {
            LitegramApi.ServerInfo cachedConnected = connectWithAutoFallback(cached);
            if (cachedConnected != null) {
                return null;
            }
        }
        try {
            List<LitegramApi.ServerInfo> servers = api.getProxyServers();
            if (!servers.isEmpty()) {
                LitegramConfig.saveServersCache(servers);
                LitegramApi.ServerInfo connected = connectWithAutoFallback(servers);
                if (connected != null) {
                    return null;
                }
                return "No proxy location connected in 3 seconds";
            }
        } catch (LitegramApi.AuthExpiredException e) {
            FileLog.d("litegram: token expired during connectAuthenticated, re-authenticating");
            if (reAuthenticate()) {
                try {
                    List<LitegramApi.ServerInfo> servers = api.getProxyServers();
                    if (!servers.isEmpty()) {
                        LitegramConfig.saveServersCache(servers);
                        LitegramApi.ServerInfo connected = connectWithAutoFallback(servers);
                        if (connected != null) {
                            return null;
                        }
                        return "No proxy location connected in 3 seconds";
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

            proxyAppliedByUs = true;
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
                    LitegramConfig.saveServersCache(servers);
                }

                if (!proxyAppliedByUs && isConnectedState()) {
                    FileLog.d("litegram: direct connection active after auth, skipping proxy");
                    return;
                }

                if (!servers.isEmpty()) {
                    LitegramApi.ServerInfo connected = connectWithAutoFallback(servers);
                    if (connected == null) {
                        FileLog.d("litegram: no location connected after auth within 3s windows");
                    }
                }
            } catch (Exception e) {
                FileLog.e("litegram: onTelegramAuth failed", e);
            }
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

    private void fetchServersForCache() {
        try {
            if (LitegramDeviceToken.hasAccessToken()) {
                List<LitegramApi.ServerInfo> servers = api.getProxyServers();
                if (servers != null && !servers.isEmpty()) {
                    LitegramConfig.saveServersCache(servers);
                    FileLog.d("litegram: cached " + servers.size() + " servers (proxy disabled)");
                }
            }
        } catch (Exception e) {
            FileLog.e("litegram: fetchServersForCache failed", e);
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

    public void refreshSubscription(Runnable onComplete) {
        Utilities.globalQueue.postRunnable(() -> {
            refreshSubscriptionStatus();
            if (onComplete != null) onComplete.run();
        });
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
