package org.telegram.litegram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

public final class LitegramConfig {

    public static final String API_BASE_URL = "https://test.enderfall.net";
    public static final String API_FALLBACK_URL = "https://64.188.60.243";
    public static final String API_VERSION = "v1";
    public static final String PLATFORM = "android";

    public static final int CONNECTION_TIMEOUT_MS = 10_000;

    private static final String KEY_SAVE_TRAFFIC = "litegram_save_traffic";
    private static final String KEY_PROXY_HOST = "litegram_proxy_host";
    private static final String KEY_PROXY_PORT = "litegram_proxy_port";
    private static final String KEY_PROXY_SECRET = "litegram_proxy_secret";
    private static final String KEY_PROXY_ENABLED = "litegram_proxy_enabled";

    private static volatile boolean useFallback;
    private static volatile Boolean saveTrafficCached;

    public static String apiUrl(String path) {
        String base = useFallback ? API_FALLBACK_URL : API_BASE_URL;
        return base + "/api/" + API_VERSION + path;
    }

    public static void enableFallback() {
        useFallback = true;
    }

    public static boolean isFallback() {
        return useFallback;
    }

    public static boolean isSaveTrafficEnabled() {
        if (saveTrafficCached == null) {
            saveTrafficCached = getPrefs().getBoolean(KEY_SAVE_TRAFFIC, true);
        }
        return saveTrafficCached;
    }

    public static void setSaveTrafficEnabled(boolean enabled) {
        saveTrafficCached = enabled;
        getPrefs().edit().putBoolean(KEY_SAVE_TRAFFIC, enabled).apply();
    }

    public static void saveProxy(String host, int port, String secret) {
        getPrefs().edit()
                .putString(KEY_PROXY_HOST, host)
                .putInt(KEY_PROXY_PORT, port)
                .putString(KEY_PROXY_SECRET, secret)
                .putBoolean(KEY_PROXY_ENABLED, true)
                .apply();
    }

    public static String getProxyHost() {
        return getPrefs().getString(KEY_PROXY_HOST, "");
    }

    public static int getProxyPort() {
        return getPrefs().getInt(KEY_PROXY_PORT, 0);
    }

    public static String getProxySecret() {
        return getPrefs().getString(KEY_PROXY_SECRET, "");
    }

    public static boolean isProxyEnabled() {
        return getPrefs().getBoolean(KEY_PROXY_ENABLED, false) && hasProxy();
    }

    public static void setProxyEnabled(boolean enabled) {
        getPrefs().edit().putBoolean(KEY_PROXY_ENABLED, enabled).apply();
    }

    public static boolean hasProxy() {
        String host = getProxyHost();
        return host != null && !host.isEmpty();
    }

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences("litegram_prefs", Context.MODE_PRIVATE);
    }

    private LitegramConfig() {}
}
