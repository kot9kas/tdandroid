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

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.HostnameVerifier;

public class LitegramApi {

    private String accessToken;

    public static class ServerInfo {
        public final String host;
        public final int port;
        public final String secret;

        public ServerInfo(String host, int port, String secret) {
            this.host = host;
            this.port = port;
            this.secret = secret;
        }
    }

    public static class AuthResult {
        public final String accessToken;
        public final String userId;

        public AuthResult(String accessToken, String userId) {
            this.accessToken = accessToken;
            this.userId = userId;
        }
    }

    public void setAccessToken(String token) {
        this.accessToken = token;
    }

    public String getAccessToken() {
        return accessToken;
    }

    /**
     * POST /proxy/public/claim — get a temporary proxy for unauthenticated device
     */
    public ServerInfo claimTempProxy(String deviceToken) throws Exception {
        JSONObject body = new JSONObject();
        body.put("deviceToken", deviceToken);

        String response = httpPost("/proxy/public/claim", body.toString());
        JSONObject json = new JSONObject(response);

        if (json.has("error")) {
            throw new Exception("claim failed: " + json.getString("error"));
        }

        return parseFirstServer(json);
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
        JSONObject user = json.optJSONObject("user");
        if (user != null) {
            userId = user.optString("id", "");
        }

        return new AuthResult(token, userId);
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
                        s.getString("secret")
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
                        s.getString("secret")
                ));
            }
        }

        return servers;
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
        URL url = new URL(LitegramConfig.apiUrl(path));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            configureFallbackSsl(conn);
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(LitegramConfig.CONNECTION_TIMEOUT_MS);
            conn.setReadTimeout(LitegramConfig.CONNECTION_TIMEOUT_MS);
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
                    s.getString("secret")
            );
        }

        JSONArray bypass = json.optJSONArray("bypass");
        if (bypass != null && bypass.length() > 0) {
            JSONObject s = bypass.getJSONObject(0);
            return new ServerInfo(
                    s.getString("host"),
                    s.optInt("port", 443),
                    s.getString("secret")
            );
        }

        if (json.has("host") && json.has("secret")) {
            return new ServerInfo(
                    json.getString("host"),
                    json.optInt("port", 443),
                    json.getString("secret")
            );
        }

        throw new Exception("No server in claim response");
    }
}
