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
    private static final String KEY_PROXY_NAME = "litegram_proxy_name";
    private static final String KEY_PROXY_COUNTRY = "litegram_proxy_country";
    private static final String KEY_SUB_STATUS = "litegram_sub_status";
    private static final String KEY_SUB_EXPIRES = "litegram_sub_expires";

    private static volatile boolean useFallback;
    private static volatile Boolean saveTrafficCached;
    private static volatile String subStatusCached;

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

    public static void saveProxy(String host, int port, String secret, String name) {
        saveProxy(host, port, secret, name, null);
    }

    public static void saveProxy(String host, int port, String secret, String name, String country) {
        SharedPreferences.Editor editor = getPrefs().edit()
                .putString(KEY_PROXY_HOST, host)
                .putInt(KEY_PROXY_PORT, port)
                .putString(KEY_PROXY_SECRET, secret)
                .putBoolean(KEY_PROXY_ENABLED, true);
        if (name != null) {
            editor.putString(KEY_PROXY_NAME, name);
        } else {
            editor.remove(KEY_PROXY_NAME);
        }
        if (country != null) {
            editor.putString(KEY_PROXY_COUNTRY, country);
        } else {
            editor.remove(KEY_PROXY_COUNTRY);
        }
        editor.apply();
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

    public static String getProxyName() {
        return getPrefs().getString(KEY_PROXY_NAME, null);
    }

    public static String getProxyCountry() {
        return getPrefs().getString(KEY_PROXY_COUNTRY, null);
    }

    public static String getProxyFlagEmoji() {
        String cc = getProxyCountry();
        if (cc == null || cc.length() != 2) return "";
        cc = cc.toUpperCase();
        int first = Character.toCodePoint('\uD83C', (char) ('\uDDE6' + cc.charAt(0) - 'A'));
        int second = Character.toCodePoint('\uD83C', (char) ('\uDDE6' + cc.charAt(1) - 'A'));
        return new String(Character.toChars(first)) + new String(Character.toChars(second));
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

    public static void saveSubscription(String status, String expiresAt) {
        subStatusCached = status;
        SharedPreferences.Editor editor = getPrefs().edit()
                .putString(KEY_SUB_STATUS, status != null ? status : "none");
        if (expiresAt != null) {
            editor.putString(KEY_SUB_EXPIRES, expiresAt);
        } else {
            editor.remove(KEY_SUB_EXPIRES);
        }
        editor.apply();
    }

    public static String getSubscriptionStatus() {
        if (subStatusCached == null) {
            subStatusCached = getPrefs().getString(KEY_SUB_STATUS, "none");
        }
        return subStatusCached;
    }

    public static String getSubscriptionExpires() {
        return getPrefs().getString(KEY_SUB_EXPIRES, null);
    }

    public static boolean isSubscriptionActive() {
        return "active".equals(getSubscriptionStatus());
    }

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences("litegram_prefs", Context.MODE_PRIVATE);
    }

    private LitegramConfig() {}
}
