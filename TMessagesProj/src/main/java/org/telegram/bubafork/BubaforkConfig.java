package org.telegram.bubafork;

public final class BubaforkConfig {

    public static final String API_BASE_URL = "https://test.enderfall.net";
    public static final String API_VERSION = "v1";
    public static final String PLATFORM = "android";

    public static final int CONNECTION_TIMEOUT_MS = 10_000;

    public static String apiUrl(String path) {
        return API_BASE_URL + "/api/" + API_VERSION + path;
    }

    private BubaforkConfig() {}
}
