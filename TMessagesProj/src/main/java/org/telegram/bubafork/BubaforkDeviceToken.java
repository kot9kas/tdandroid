package org.telegram.bubafork;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;

import java.util.UUID;

public class BubaforkDeviceToken {

    private static final String PREFS_NAME = "bubafork_prefs";
    private static final String KEY_DEVICE_TOKEN = "device_token";
    private static final String KEY_ACCESS_TOKEN = "access_token";

    private static volatile String cachedDeviceToken;
    private static volatile String cachedAccessToken;

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

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
