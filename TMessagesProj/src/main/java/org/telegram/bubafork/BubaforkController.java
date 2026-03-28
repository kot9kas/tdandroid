package org.telegram.bubafork;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;

import java.util.List;

public class BubaforkController {

    private static volatile BubaforkController instance;

    private final BubaforkApi api = new BubaforkApi();
    private volatile boolean initialized;

    public static BubaforkController getInstance() {
        if (instance == null) {
            synchronized (BubaforkController.class) {
                if (instance == null) {
                    instance = new BubaforkController();
                }
            }
        }
        return instance;
    }

    private BubaforkController() {}

    public void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        String savedToken = BubaforkDeviceToken.getAccessToken();
        if (!TextUtils.isEmpty(savedToken)) {
            api.setAccessToken(savedToken);
        }

        Utilities.globalQueue.postRunnable(this::connectProxy);
    }

    private void connectProxy() {
        try {
            if (BubaforkDeviceToken.hasAccessToken()) {
                connectAuthenticated();
            } else {
                connectAnonymous();
            }
        } catch (Exception e) {
            FileLog.e("bubafork: connectProxy failed", e);
        }
    }

    private void connectAuthenticated() {
        try {
            List<BubaforkApi.ServerInfo> servers = api.getProxyServers();
            if (!servers.isEmpty()) {
                applyProxy(servers.get(0));
                return;
            }
        } catch (Exception e) {
            FileLog.e("bubafork: getProxyServers failed, falling back to claim", e);
        }
        connectAnonymous();
    }

    private void connectAnonymous() {
        try {
            String deviceToken = BubaforkDeviceToken.getDeviceToken();
            BubaforkApi.ServerInfo server = api.claimTempProxy(deviceToken);
            applyProxy(server);
        } catch (Exception e) {
            FileLog.e("bubafork: claimTempProxy failed", e);
        }
    }

    private void applyProxy(BubaforkApi.ServerInfo server) {
        AndroidUtilities.runOnUIThread(() -> {
            SharedConfig.ProxyInfo proxyInfo = new SharedConfig.ProxyInfo(
                    server.host,
                    server.port,
                    "",
                    "",
                    server.secret
            );

            SharedConfig.addProxy(proxyInfo);
            SharedConfig.currentProxy = proxyInfo;

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

            FileLog.d("bubafork: proxy applied " + server.host + ":" + server.port);
        });
    }

    /**
     * Called after Telegram user authentication completes.
     * Registers the device with the backend and switches to a permanent proxy.
     */
    public void onTelegramAuth(long telegramId) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String deviceToken = BubaforkDeviceToken.getDeviceToken();
                BubaforkApi.AuthResult result = api.register(
                        String.valueOf(telegramId),
                        deviceToken
                );

                BubaforkDeviceToken.saveAccessToken(result.accessToken);
                FileLog.d("bubafork: registered, userId=" + result.userId);

                List<BubaforkApi.ServerInfo> servers = api.getProxyServers();
                if (!servers.isEmpty()) {
                    applyProxy(servers.get(0));
                }
            } catch (Exception e) {
                FileLog.e("bubafork: onTelegramAuth failed", e);
            }
        });
    }
}
