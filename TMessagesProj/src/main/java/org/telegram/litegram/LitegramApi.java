package org.telegram.litegram;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.HostnameVerifier;

public class LitegramApi {

    private String accessToken;

    /** ISO 3166-1 alpha-2 (e.g. "NL") → regional indicator flag emoji. */
    public static String isoCountryToFlagEmoji(String countryCode) {
        if (countryCode == null || countryCode.length() != 2) return "";
        String cc = countryCode.toUpperCase(Locale.US);
        char c1 = cc.charAt(0);
        char c2 = cc.charAt(1);
        if (c1 < 'A' || c1 > 'Z' || c2 < 'A' || c2 > 'Z') return "";
        int cp1 = 0x1F1E6 + (c1 - 'A');
        int cp2 = 0x1F1E6 + (c2 - 'A');
        return new String(Character.toChars(cp1)) + new String(Character.toChars(cp2));
    }

    public static class ServerInfo {
        public final String host;
        public final int port;
        public final String secret;
        public final String name;
        public final String country;
        public final int priority;

        public ServerInfo(String host, int port, String secret, String name, String country, int priority) {
            this.host = host;
            this.port = port;
            this.secret = secret;
            this.name = name;
            this.country = country;
            this.priority = priority;
        }

        public ServerInfo(String host, int port, String secret, String name, String country) {
            this(host, port, secret, name, country, Integer.MAX_VALUE);
        }

        public String getFlagEmoji() {
            return isoCountryToFlagEmoji(country);
        }
    }

    public static class AuthResult {
        public final String accessToken;
        public final String userId;
        public final String subscriptionStatus;
        public final String subscriptionExpiresAt;

        public AuthResult(String accessToken, String userId, String subscriptionStatus, String subscriptionExpiresAt) {
            this.accessToken = accessToken;
            this.userId = userId;
            this.subscriptionStatus = subscriptionStatus;
            this.subscriptionExpiresAt = subscriptionExpiresAt;
        }
    }

    public static class SubscriptionInfo {
        public final String status;
        public final String expiresAt;
        public final String productId;

        public SubscriptionInfo(String status, String expiresAt, String productId) {
            this.status = status;
            this.expiresAt = expiresAt;
            this.productId = productId;
        }
    }

    public static class AdInfo {
        public final String id;
        public final String title;
        public final String description;
        public final String imageUrl;
        public final String linkUrl;
        public final int intervalSeconds;

        public AdInfo(String id, String title, String description, String imageUrl, String linkUrl, int intervalSeconds) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.imageUrl = imageUrl;
            this.linkUrl = linkUrl;
            this.intervalSeconds = intervalSeconds;
        }
    }

    public void setAccessToken(String token) {
        this.accessToken = token;
    }

    public String getAccessToken() {
        return accessToken;
    }

    /**
     * POST /proxy/public/claim — get temporary proxy servers for unauthenticated device.
     * Returns all servers from the response (not just the first one).
     */
    public List<ServerInfo> claimTempProxyAll(String deviceToken, int timeoutMs) throws Exception {
        JSONObject body = new JSONObject();
        body.put("deviceToken", deviceToken);

        String response = httpPostWithTimeout("/proxy/public/claim", body.toString(), timeoutMs);
        JSONObject json = new JSONObject(response);

        if (json.has("error")) {
            throw new Exception("claim failed: " + json.getString("error"));
        }

        List<ServerInfo> servers = new ArrayList<>();
        JSONArray regular = json.optJSONArray("regular");
        if (regular != null) {
            for (int i = 0; i < regular.length(); i++) {
                JSONObject s = regular.getJSONObject(i);
                servers.add(new ServerInfo(
                        s.getString("host"),
                        s.optInt("port", 443),
                        s.getString("secret"),
                        s.optString("name", null),
                        s.optString("country", null),
                        s.optInt("priority", i)
                ));
            }
        }
        JSONArray bypass = json.optJSONArray("bypass");
        if (bypass != null) {
            for (int i = 0; i < bypass.length(); i++) {
                JSONObject s = bypass.getJSONObject(i);
                servers.add(new ServerInfo(
                        s.getString("host"),
                        s.optInt("port", 443),
                        s.getString("secret"),
                        s.optString("name", null),
                        s.optString("country", null),
                        s.optInt("priority", i)
                ));
            }
        }
        if (servers.isEmpty() && json.has("host") && json.has("secret")) {
            servers.add(new ServerInfo(
                    json.getString("host"),
                    json.optInt("port", 443),
                    json.getString("secret"),
                    json.optString("name", null),
                    json.optString("country", null)
            ));
        }
        if (servers.isEmpty()) {
            throw new Exception("No server in claim response");
        }
        return servers;
    }

    /**
     * @deprecated Use {@link #claimTempProxyAll(String, int)} for multi-server support.
     */
    public ServerInfo claimTempProxy(String deviceToken) throws Exception {
        List<ServerInfo> servers = claimTempProxyAll(deviceToken, LitegramConfig.CONNECTION_TIMEOUT_MS);
        return servers.get(0);
    }

    /**
     * POST /auth/register — register device and claim temp proxy to user
     */
    public AuthResult register(String telegramId, String deviceToken) throws Exception {
        JSONObject body = new JSONObject();
        body.put("telegramId", telegramId);
        body.put("deviceToken", deviceToken);
        body.put("platform", LitegramConfig.PLATFORM);

        String response = httpPost("/auth/register", body.toString());
        JSONObject json = new JSONObject(response);

        String token = json.optString("accessToken", "");
        if (TextUtils.isEmpty(token)) {
            throw new Exception("register failed: no accessToken");
        }

        this.accessToken = token;

        String userId = "";
        String subStatus = "none";
        String subExpires = null;
        JSONObject user = json.optJSONObject("user");
        if (user != null) {
            userId = user.optString("id", "");
            subStatus = user.optString("subscriptionStatus", "none");
            subExpires = user.has("subscriptionExpiresAt") && !user.isNull("subscriptionExpiresAt")
                    ? user.optString("subscriptionExpiresAt", null)
                    : null;
        }

        return new AuthResult(token, userId, subStatus, subExpires);
    }

    /**
     * GET /proxy/servers — get permanent proxy server list (requires auth)
     */
    public List<ServerInfo> getProxyServers() throws Exception {
        String response = httpGet("/proxy/servers");
        JSONObject json = new JSONObject(response);

        List<ServerInfo> servers = new ArrayList<>();

        JSONArray regular = json.optJSONArray("regular");
        if (regular != null) {
            for (int i = 0; i < regular.length(); i++) {
                JSONObject s = regular.getJSONObject(i);
                servers.add(new ServerInfo(
                        s.getString("host"),
                        s.optInt("port", 443),
                        s.getString("secret"),
                        s.optString("name", null),
                        s.optString("country", null),
                        s.optInt("priority", i)
                ));
            }
        }

        JSONArray bypass = json.optJSONArray("bypass");
        if (bypass != null) {
            for (int i = 0; i < bypass.length(); i++) {
                JSONObject s = bypass.getJSONObject(i);
                servers.add(new ServerInfo(
                        s.getString("host"),
                        s.optInt("port", 443),
                        s.getString("secret"),
                        s.optString("name", null),
                        s.optString("country", null),
                        s.optInt("priority", i)
                ));
            }
        }

        return servers;
    }

    /**
     * GET /subscription/status — get current subscription status
     */
    public SubscriptionInfo getSubscriptionStatus() throws Exception {
        String response = httpGet("/subscription/status");
        JSONObject json = new JSONObject(response);
        return new SubscriptionInfo(
                json.optString("status", "none"),
                json.has("expiresAt") && !json.isNull("expiresAt")
                        ? json.optString("expiresAt", null) : null,
                json.has("productId") && !json.isNull("productId")
                        ? json.optString("productId", null) : null
        );
    }

    /**
     * GET /user/me — get current user profile
     */
    public SubscriptionInfo getUserProfile() throws Exception {
        String response = httpGet("/user/me");
        JSONObject json = new JSONObject(response);
        return new SubscriptionInfo(
                json.optString("subscriptionStatus", "none"),
                json.has("subscriptionExpiresAt") && !json.isNull("subscriptionExpiresAt")
                        ? json.optString("subscriptionExpiresAt", null) : null,
                null
        );
    }

    /**
     * GET /advertising/active — get the currently active advertisement
     */
    public AdInfo getActiveAd() throws Exception {
        String response = httpGet("/advertising/active");
        if (response == null || response.isEmpty() || "null".equals(response)) {
            return null;
        }
        JSONObject json = new JSONObject(response);
        if (!json.has("id")) {
            return null;
        }
        return new AdInfo(
                json.getString("id"),
                json.optString("title", ""),
                json.optString("description", ""),
                json.optString("imageUrl", null),
                json.optString("linkUrl", null),
                json.optInt("intervalSeconds", 3600)
        );
    }

    private void configureFallbackSsl(HttpURLConnection conn) {
        if (!LitegramConfig.isFallback() || !(conn instanceof HttpsURLConnection)) {
            return;
        }
        try {
            HttpsURLConnection https = (HttpsURLConnection) conn;
            https.setHostnameVerifier((hostname, session) -> true);
            https.setRequestProperty("Host", "test.enderfall.net");
        } catch (Exception ignored) {}
    }

    private String httpGet(String path) throws Exception {
        URL url = new URL(LitegramConfig.apiUrl(path));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            configureFallbackSsl(conn);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(LitegramConfig.CONNECTION_TIMEOUT_MS);
            conn.setReadTimeout(LitegramConfig.CONNECTION_TIMEOUT_MS);
            if (!TextUtils.isEmpty(accessToken)) {
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            }
            return readResponse(conn);
        } finally {
            conn.disconnect();
        }
    }

    private String httpPost(String path, String jsonBody) throws Exception {
        return httpPostWithTimeout(path, jsonBody, LitegramConfig.CONNECTION_TIMEOUT_MS);
    }

    private String httpPostWithTimeout(String path, String jsonBody, int timeoutMs) throws Exception {
        URL url = new URL(LitegramConfig.apiUrl(path));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            configureFallbackSsl(conn);
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("Content-Type", "application/json");
            if (!TextUtils.isEmpty(accessToken)) {
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            }
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
            return readResponse(conn);
        } finally {
            conn.disconnect();
        }
    }

    public static class AuthExpiredException extends Exception {
        public AuthExpiredException() {
            super("JWT token expired (401)");
        }
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        BufferedReader reader;
        if (code >= 200 && code < 300) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
        }
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();

        if (code == 401) {
            throw new AuthExpiredException();
        }
        if (code < 200 || code >= 300) {
            throw new Exception("HTTP " + code + ": " + sb);
        }
        return sb.toString();
    }

    private ServerInfo parseFirstServer(JSONObject json) throws Exception {
        JSONArray regular = json.optJSONArray("regular");
        if (regular != null && regular.length() > 0) {
            JSONObject s = regular.getJSONObject(0);
            return new ServerInfo(
                    s.getString("host"),
                    s.optInt("port", 443),
                    s.getString("secret"),
                    s.optString("name", null),
                    s.optString("country", null)
            );
        }

        JSONArray bypass = json.optJSONArray("bypass");
        if (bypass != null && bypass.length() > 0) {
            JSONObject s = bypass.getJSONObject(0);
            return new ServerInfo(
                    s.getString("host"),
                    s.optInt("port", 443),
                    s.getString("secret"),
                    s.optString("name", null),
                    s.optString("country", null)
            );
        }

        if (json.has("host") && json.has("secret")) {
            return new ServerInfo(
                    json.getString("host"),
                    json.optInt("port", 443),
                    json.getString("secret"),
                    json.optString("name", null),
                    json.optString("country", null)
            );
        }

        throw new Exception("No server in claim response");
    }
}
