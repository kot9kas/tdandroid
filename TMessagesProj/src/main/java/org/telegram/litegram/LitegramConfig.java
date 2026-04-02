package org.telegram.litegram;

public final class LitegramConfig {

    public static final String API_BASE_URL = "https://test.enderfall.net";
    public static final String API_FALLBACK_URL = "https://64.188.60.243";
    public static final String API_VERSION = "v1";
    public static final String PLATFORM = "android";

    public static final int CONNECTION_TIMEOUT_MS = 10_000;

    private static volatile boolean useFallback;

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

    private LitegramConfig() {}
}
