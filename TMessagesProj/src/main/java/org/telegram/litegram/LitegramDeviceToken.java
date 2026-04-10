package org.telegram.litegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;

import java.util.UUID;

public class LitegramDeviceToken {

    private static final String PREFS_NAME = "litegram_prefs";
    private static final String KEY_DEVICE_TOKEN = "device_token";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_TELEGRAM_ID = "telegram_id";
    private static final String KEY_BOT_START_SENT = "bot_start_sent_";

    private static volatile String cachedDeviceToken;
    private static volatile String cachedAccessToken;
    private static volatile String cachedTelegramId;

    public static String getDeviceToken() {
        if (cachedDeviceToken != null) {
            return cachedDeviceToken;
        }
        SharedPreferences prefs = getPrefs();
        String token = prefs.getString(KEY_DEVICE_TOKEN, "");
        if (TextUtils.isEmpty(token)) {
            token = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_DEVICE_TOKEN, token).apply();
        }
        cachedDeviceToken = token;
        return token;
    }

    public static void saveAccessToken(String token) {
        cachedAccessToken = token;
        getPrefs().edit().putString(KEY_ACCESS_TOKEN, token).apply();
    }

    public static String getAccessToken() {
        if (cachedAccessToken != null) {
            return cachedAccessToken;
        }
        cachedAccessToken = getPrefs().getString(KEY_ACCESS_TOKEN, "");
        return cachedAccessToken;
    }

    public static boolean hasAccessToken() {
        return !TextUtils.isEmpty(getAccessToken());
    }

    public static void saveTelegramId(String id) {
        cachedTelegramId = id;
        getPrefs().edit().putString(KEY_TELEGRAM_ID, id).apply();
    }

    public static String getTelegramId() {
        if (cachedTelegramId != null) {
            return cachedTelegramId;
        }
        cachedTelegramId = getPrefs().getString(KEY_TELEGRAM_ID, "");
        return cachedTelegramId;
    }

    public static boolean hasTelegramId() {
        return !TextUtils.isEmpty(getTelegramId());
    }

    public static boolean isBotStartSent(int account) {
        return getPrefs().getBoolean(KEY_BOT_START_SENT + account, false);
    }

    public static void setBotStartSent(int account) {
        getPrefs().edit().putBoolean(KEY_BOT_START_SENT + account, true).apply();
    }

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
