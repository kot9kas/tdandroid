package org.telegram.litegram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class LitegramConfig {

    public static final String API_BASE_URL = "https://test.enderfall.net";
    public static final String API_FALLBACK_URL = "https://64.188.60.243";
    public static final String API_VERSION = "v1";
    public static final String PLATFORM = "android";

    public static final int CONNECTION_TIMEOUT_MS = 10_000;
    public static final int ANON_CONNECTION_TIMEOUT_MS = 5_000;

    /**
     * Emergency fallback proxy servers hardcoded in the client.
     * Used when the backend API is completely unreachable (e.g. white-list blocking in RU).
     * To add servers: new LitegramApi.ServerInfo(host, port, secret, name, country, priority)
     */
    public static final LitegramApi.ServerInfo[] HARDCODED_FALLBACK_SERVERS = {
            // TODO: populate with emergency proxy servers
            // new LitegramApi.ServerInfo("proxy.example.com", 443, "ee...", "Emergency RU", "RU", 0),
    };

    private static final String KEY_SAVE_TRAFFIC = "litegram_save_traffic";
    private static final String KEY_PROXY_HOST = "litegram_proxy_host";
    private static final String KEY_PROXY_PORT = "litegram_proxy_port";
    private static final String KEY_PROXY_SECRET = "litegram_proxy_secret";
    private static final String KEY_PROXY_ENABLED = "litegram_proxy_enabled";
    private static final String KEY_PROXY_NAME = "litegram_proxy_name";
    private static final String KEY_PROXY_COUNTRY = "litegram_proxy_country";
    private static final String KEY_SUB_STATUS = "litegram_sub_status";
    private static final String KEY_SUB_EXPIRES = "litegram_sub_expires";
    private static final String KEY_SERVERS_CACHE = "litegram_servers_cache";

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
        return LitegramApi.isoCountryToFlagEmoji(getProxyCountry());
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
        if (!"active".equals(getSubscriptionStatus())) return false;
        String expires = getSubscriptionExpires();
        if (expires == null || expires.isEmpty()) return true;
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            String clean = expires.length() > 19 ? expires.substring(0, 19) : expires;
            Date expiryDate = fmt.parse(clean);
            if (expiryDate != null && expiryDate.before(new Date())) {
                saveSubscription("expired", expires);
                return false;
            }
        } catch (Exception ignored) {}
        return true;
    }

    public static void saveServersCache(List<LitegramApi.ServerInfo> servers) {
        try {
            JSONArray arr = new JSONArray();
            for (LitegramApi.ServerInfo s : servers) {
                JSONObject obj = new JSONObject();
                obj.put("host", s.host);
                obj.put("port", s.port);
                obj.put("secret", s.secret);
                if (s.name != null) obj.put("name", s.name);
                if (s.country != null) obj.put("country", s.country);
                obj.put("priority", s.priority);
                arr.put(obj);
            }
            getPrefs().edit().putString(KEY_SERVERS_CACHE, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static List<LitegramApi.ServerInfo> loadServersCache() {
        List<LitegramApi.ServerInfo> result = new ArrayList<>();
        try {
            String json = getPrefs().getString(KEY_SERVERS_CACHE, null);
            if (json == null || json.isEmpty()) return result;
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                result.add(new LitegramApi.ServerInfo(
                        obj.getString("host"),
                        obj.optInt("port", 443),
                        obj.getString("secret"),
                        obj.optString("name", null),
                        obj.optString("country", null),
                        obj.optInt("priority", i)
                ));
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences("litegram_prefs", Context.MODE_PRIVATE);
    }

    private LitegramConfig() {}
}
