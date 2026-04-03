package org.telegram.litegram;

import android.content.SharedPreferences;
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

    public void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        String savedToken = LitegramDeviceToken.getAccessToken();
        if (!TextUtils.isEmpty(savedToken)) {
            api.setAccessToken(savedToken);
        }

        if (isAlreadyConnectedToLitegramProxy()) {
            FileLog.d("litegram: proxy already active, skipping reconnect");
            return;
        }

        Utilities.globalQueue.postRunnable(this::connectProxy);
    }

    private boolean isAlreadyConnectedToLitegramProxy() {
        if (!SharedConfig.isProxyEnabled() || SharedConfig.currentProxy == null) {
            return false;
        }
        int state = ConnectionsManager.getInstance(0).getConnectionState();
        return state == ConnectionsManager.ConnectionStateConnected
                || state == ConnectionsManager.ConnectionStateUpdating;
    }

    public interface ReconnectCallback {
        void onResult(boolean success, String error);
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
                String proxyAddr = SharedConfig.currentProxy != null
                        ? SharedConfig.currentProxy.address + ":" + SharedConfig.currentProxy.port
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
                SharedConfig.ProxyInfo current = SharedConfig.currentProxy;
                if (current != null
                        && SharedConfig.isProxyEnabled()
                        && server.host.equals(current.address)
                        && server.port == current.port
                        && server.secret.equals(current.secret)) {
                    FileLog.d("litegram: proxy already set to " + server.host + ":" + server.port + ", skipping");
                    return;
                }
            }

            SharedConfig.ProxyInfo proxyInfo = new SharedConfig.ProxyInfo(
                    server.host,
                    server.port,
                    "",
                    "",
                    server.secret
            );

            SharedConfig.replaceProxyListWithSingle(proxyInfo);

            SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
            editor.putString("proxy_ip", server.host);
            editor.putInt("proxy_port", server.port);
            editor.putString("proxy_user", "");
            editor.putString("proxy_pass", "");
            editor.putString("proxy_secret", server.secret);
            editor.putBoolean("proxy_enabled", true);
            editor.commit();

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
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String deviceToken = LitegramDeviceToken.getDeviceToken();
                LitegramApi.AuthResult result = api.register(
                        String.valueOf(telegramId),
                        deviceToken
                );

                LitegramDeviceToken.saveAccessToken(result.accessToken);
                FileLog.d("litegram: registered, userId=" + result.userId);

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
