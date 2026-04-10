package org.telegram.litegram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

public class LitegramChatProtection {

    private static final String PREFS_NAME = "litegram_chat_protection";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_PIN_SALT = "pin_salt";
    private static final String KEY_BIOMETRIC = "biometric_enabled";
    private static final String KEY_PROTECTED = "protected_chats";
    private static final long SESSION_DURATION_MS = 5 * 60 * 1000;

    private static volatile LitegramChatProtection instance;
    private final SharedPreferences prefs;
    private Set<Long> protectedIds;
    private long sessionUntil;

    public static LitegramChatProtection getInstance() {
        if (instance == null) {
            synchronized (LitegramChatProtection.class) {
                if (instance == null) {
                    instance = new LitegramChatProtection();
                }
            }
        }
        return instance;
    }

    private LitegramChatProtection() {
        prefs = ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadProtectedIds();
    }

    public boolean isPinSet() {
        return prefs.getString(KEY_PIN_HASH, null) != null;
    }

    public void setPIN(String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String saltHex = bytesToHex(salt);
        String hash = hashPin(pin, saltHex);
        prefs.edit()
                .putString(KEY_PIN_HASH, hash)
                .putString(KEY_PIN_SALT, saltHex)
                .apply();
    }

    public boolean checkPIN(String pin) {
        String storedHash = prefs.getString(KEY_PIN_HASH, null);
        String salt = prefs.getString(KEY_PIN_SALT, "");
        if (storedHash == null) return false;
        return storedHash.equals(hashPin(pin, salt));
    }

    public void resetPIN() {
        prefs.edit()
                .remove(KEY_PIN_HASH)
                .remove(KEY_PIN_SALT)
                .apply();
        sessionUntil = 0;
    }

    public boolean isProtected(long dialogId) {
        return protectedIds.contains(dialogId);
    }

    public void addProtected(long dialogId) {
        protectedIds.add(dialogId);
        saveProtectedIds();
    }

    public void removeProtected(long dialogId) {
        protectedIds.remove(dialogId);
        saveProtectedIds();
    }

    public Set<Long> getProtectedIds() {
        return new HashSet<>(protectedIds);
    }

    public boolean isSessionActive() {
        return System.currentTimeMillis() < sessionUntil;
    }

    public void refreshSession() {
        sessionUntil = System.currentTimeMillis() + SESSION_DURATION_MS;
    }

    public void clearSession() {
        sessionUntil = 0;
    }

    public boolean isBiometricEnabled() {
        return prefs.getBoolean(KEY_BIOMETRIC, false);
    }

    public void setBiometricEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply();
    }

    private void loadProtectedIds() {
        protectedIds = new HashSet<>();
        String raw = prefs.getString(KEY_PROTECTED, "");
        if (raw.isEmpty()) return;
        try {
            for (String s : raw.split(",")) {
                if (!s.isEmpty()) {
                    protectedIds.add(Long.parseLong(s.trim()));
                }
            }
        } catch (Exception e) {
            FileLog.e("litegram: loadProtectedIds failed", e);
        }
    }

    private void saveProtectedIds() {
        StringBuilder sb = new StringBuilder();
        for (Long id : protectedIds) {
            if (sb.length() > 0) sb.append(",");
            sb.append(id);
        }
        prefs.edit().putString(KEY_PROTECTED, sb.toString()).apply();
    }

    private static String hashPin(String pin, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((salt + pin).getBytes("UTF-8"));
            return bytesToHex(md.digest());
        } catch (Exception e) {
            FileLog.e("litegram: hashPin failed", e);
            return "";
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
