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

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences("litegram_prefs", Context.MODE_PRIVATE);
    }

    private LitegramConfig() {}
}
