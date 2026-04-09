package org.telegram.litegram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LitegramChatLocks {

    private static final String PREFS_NAME = "litegram_chat_locks";
    private static final String KEY_PREFIX = "lock_";
    private static final String KEY_HINT_PREFIX = "hint_";
    private static final String KEY_TIMER_PREFIX = "timer_";
    private static final String KEY_HIDE_PREFIX = "hide_";
    private static final String KEY_FOLDER_PREFIX = "flk_";
    private static final String KEY_FOLDER_HINT_PREFIX = "fhint_";
    private static final String KEY_GRP_PREFIX = "grp_";
    private static final String KEY_GRP_NEXT_ID = "grp_next_id";
    private static final String KEY_AUTOLOCK = "autolock_seconds";
    private static final String KEY_USE_BIOMETRIC = "use_biometric";
    private static final String KEY_HIDE_PREVIEW = "hide_preview";
    private static final long FOLDER_ID_OFFSET = 0x7F00000000L;

    private static final String KEY_PIN_SALT = "pin_salt";
    /** Prefix that marks a salted SHA-256 hash (v2). Absent prefix = legacy unsalted hash. */
    private static final String HASH_V2_PREFIX = "v2:";

    private static final long SETTINGS_UNLOCK_DURATION_MS = 30_000;

    private static volatile LitegramChatLocks instance;
    private final SharedPreferences prefs;
    private final Map<Long, Long> lastUnlockTime = new HashMap<>();
    private final Map<Long, Long> lastSettingsUnlockTime = new HashMap<>();

    public static LitegramChatLocks getInstance() {
        if (instance == null) {
            synchronized (LitegramChatLocks.class) {
                if (instance == null) {
                    instance = new LitegramChatLocks();
                }
            }
        }
        return instance;
    }

    private LitegramChatLocks() {
        prefs = ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // --- Lock CRUD ---

    public void setLock(long dialogId, String pin) {
        prefs.edit().putString(KEY_PREFIX + dialogId, hashPin(pin)).apply();
    }

    public void removeLock(long dialogId) {
        prefs.edit()
                .remove(KEY_PREFIX + dialogId)
                .remove(KEY_HINT_PREFIX + dialogId)
                .remove(KEY_TIMER_PREFIX + dialogId)
                .remove(KEY_HIDE_PREFIX + dialogId)
                .apply();
        lastUnlockTime.remove(dialogId);
    }

    public boolean isLocked(long dialogId) {
        return prefs.contains(KEY_PREFIX + dialogId);
    }

    public boolean checkPin(long dialogId, String pin) {
        String stored = prefs.getString(KEY_PREFIX + dialogId, null);
        return verifyAndUpgrade(stored, pin, KEY_PREFIX + dialogId);
    }

    public List<Long> getLockedDialogIds() {
        List<Long> ids = new ArrayList<>();
        Map<String, ?> all = prefs.getAll();
        for (String key : all.keySet()) {
            if (key.startsWith(KEY_PREFIX)) {
                try {
                    ids.add(Long.parseLong(key.substring(KEY_PREFIX.length())));
                } catch (NumberFormatException ignored) {}
            }
        }
        return ids;
    }

    // --- Unlock state with timer ---

    public boolean isUnlockedNow(long dialogId) {
        Long ts = lastUnlockTime.get(dialogId);
        if (ts == null) return false;
        int seconds = getEffectiveAutolockSeconds(dialogId);
        if (seconds == 0) return false;
        return System.currentTimeMillis() - ts < (long) seconds * 1000;
    }

    /** @deprecated use isUnlockedNow */
    public boolean isUnlockedThisSession(long dialogId) {
        return isUnlockedNow(dialogId);
    }

    public void markUnlocked(long dialogId) {
        lastUnlockTime.put(dialogId, System.currentTimeMillis());
    }

    /** @deprecated use markUnlocked */
    public void markUnlockedThisSession(long dialogId) {
        markUnlocked(dialogId);
    }

    public void relockAll() {
        lastUnlockTime.clear();
        lastSettingsUnlockTime.clear();
    }

    public void onAppResumed() {
        // no-op now; timer-based auto-relock handles everything
    }

    public void markSettingsUnlocked(long entityId) {
        lastSettingsUnlockTime.put(entityId, System.currentTimeMillis());
    }

    public boolean isSettingsUnlockedNow(long entityId) {
        Long ts = lastSettingsUnlockTime.get(entityId);
        return ts != null && System.currentTimeMillis() - ts < SETTINGS_UNLOCK_DURATION_MS;
    }

    // --- Hint ---

    public void setHint(long dialogId, String text) {
        if (text == null || text.trim().isEmpty()) {
            prefs.edit().remove(KEY_HINT_PREFIX + dialogId).apply();
        } else {
            prefs.edit().putString(KEY_HINT_PREFIX + dialogId, text.trim()).apply();
        }
    }

    public String getHint(long dialogId) {
        return prefs.getString(KEY_HINT_PREFIX + dialogId, null);
    }

    // --- Per-chat timer ---

    public void setChatAutolockSeconds(long dialogId, int seconds) {
        if (seconds < 0) {
            prefs.edit().remove(KEY_TIMER_PREFIX + dialogId).apply();
        } else {
            prefs.edit().putInt(KEY_TIMER_PREFIX + dialogId, seconds).apply();
        }
    }

    public int getChatAutolockSeconds(long dialogId) {
        return prefs.getInt(KEY_TIMER_PREFIX + dialogId, -1);
    }

    public int getEffectiveAutolockSeconds(long dialogId) {
        int perChat = getChatAutolockSeconds(dialogId);
        if (perChat >= 0) return perChat;
        int groupId = findGroupForChat(dialogId);
        if (groupId >= 0) {
            int gt = getGroupTimer(groupId);
            if (gt >= 0) return gt;
        }
        return 300;
    }

    // --- Per-chat hide preview ---

    public void setChatHidePreview(long dialogId, int value) {
        if (value < 0) {
            prefs.edit().remove(KEY_HIDE_PREFIX + dialogId).apply();
        } else {
            prefs.edit().putInt(KEY_HIDE_PREFIX + dialogId, value).apply();
        }
    }

    public int getChatHidePreview(long dialogId) {
        return prefs.getInt(KEY_HIDE_PREFIX + dialogId, -1);
    }

    public boolean isEffectiveHidePreview(long dialogId) {
        int perChat = getChatHidePreview(dialogId);
        if (perChat >= 0) return perChat == 1;
        int groupId = findGroupForChat(dialogId);
        if (groupId >= 0) {
            int groupHide = getGroupHide(groupId);
            if (groupHide >= 0) return groupHide == 1;
        }
        return false;
    }

    // --- Global settings ---

    public int getAutolockSeconds() {
        return prefs.getInt(KEY_AUTOLOCK, 300);
    }

    public void setAutolockSeconds(int seconds) {
        prefs.edit().putInt(KEY_AUTOLOCK, seconds).apply();
        if (seconds == 0) relockAll();
    }

    public boolean isBiometricEnabled() {
        return prefs.getBoolean(KEY_USE_BIOMETRIC, false);
    }

    public void setBiometricEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_USE_BIOMETRIC, enabled).apply();
    }

    public boolean isHidePreview() {
        return prefs.getBoolean(KEY_HIDE_PREVIEW, true);
    }

    public void setHidePreview(boolean enabled) {
        prefs.edit().putBoolean(KEY_HIDE_PREVIEW, enabled).apply();
    }

    // --- Folder locks ---

    public static long folderDialogId(int filterId) {
        return FOLDER_ID_OFFSET + filterId;
    }

    public void setFolderLock(int filterId, String pin) {
        prefs.edit().putString(KEY_FOLDER_PREFIX + filterId, hashPin(pin)).apply();
    }

    public void removeFolderLock(int filterId) {
        prefs.edit()
                .remove(KEY_FOLDER_PREFIX + filterId)
                .remove(KEY_FOLDER_HINT_PREFIX + filterId)
                .remove("fltimer_" + filterId)
                .apply();
        lastUnlockTime.remove(folderDialogId(filterId));
    }

    public boolean isFolderLocked(int filterId) {
        return prefs.contains(KEY_FOLDER_PREFIX + filterId);
    }

    public boolean checkFolderPin(int filterId, String pin) {
        String stored = prefs.getString(KEY_FOLDER_PREFIX + filterId, null);
        return verifyAndUpgrade(stored, pin, KEY_FOLDER_PREFIX + filterId);
    }

    public List<Integer> getLockedFolderIds() {
        List<Integer> ids = new ArrayList<>();
        Map<String, ?> all = prefs.getAll();
        for (String key : all.keySet()) {
            if (key.startsWith(KEY_FOLDER_PREFIX)) {
                try {
                    ids.add(Integer.parseInt(key.substring(KEY_FOLDER_PREFIX.length())));
                } catch (NumberFormatException ignored) {}
            }
        }
        return ids;
    }

    public boolean isFolderUnlockedNow(int filterId) {
        Long ts = lastUnlockTime.get(folderDialogId(filterId));
        if (ts == null) return false;
        int seconds = getEffectiveFolderAutolockSeconds(filterId);
        if (seconds == 0) return false;
        return System.currentTimeMillis() - ts < (long) seconds * 1000;
    }

    public void markFolderUnlocked(int filterId) {
        markUnlocked(folderDialogId(filterId));
    }

    public void setFolderHint(int filterId, String text) {
        if (text == null || text.trim().isEmpty()) {
            prefs.edit().remove(KEY_FOLDER_HINT_PREFIX + filterId).apply();
        } else {
            prefs.edit().putString(KEY_FOLDER_HINT_PREFIX + filterId, text.trim()).apply();
        }
    }

    public String getFolderHint(int filterId) {
        return prefs.getString(KEY_FOLDER_HINT_PREFIX + filterId, null);
    }

    public void setFolderAutolockSeconds(int filterId, int seconds) {
        if (seconds < 0) {
            prefs.edit().remove("fltimer_" + filterId).apply();
        } else {
            prefs.edit().putInt("fltimer_" + filterId, seconds).apply();
        }
    }

    public int getFolderAutolockSeconds(int filterId) {
        return prefs.getInt("fltimer_" + filterId, -1);
    }

    public int getEffectiveFolderAutolockSeconds(int filterId) {
        int v = getFolderAutolockSeconds(filterId);
        return v >= 0 ? v : 300;
    }

    // --- Lock groups ---

    public int createGroup(String name, String pin, List<Long> chatIds) {
        int id = prefs.getInt(KEY_GRP_NEXT_ID, 1);
        SharedPreferences.Editor ed = prefs.edit();
        ed.putInt(KEY_GRP_NEXT_ID, id + 1);
        ed.putString(KEY_GRP_PREFIX + id + "_name", name);
        ed.putString(KEY_GRP_PREFIX + id + "_pin", hashPin(pin));
        ed.putString(KEY_GRP_PREFIX + id + "_chats", joinIds(chatIds));
        ed.putInt(KEY_GRP_PREFIX + id + "_timer", -1);
        ed.putInt(KEY_GRP_PREFIX + id + "_hide", -1);
        ed.apply();
        for (long cid : chatIds) {
            setLock(cid, pin);
            setChatAutolockSeconds(cid, -1);
        }
        return id;
    }

    public void removeGroup(int groupId) {
        List<Long> chats = getGroupChats(groupId);
        SharedPreferences.Editor ed = prefs.edit();
        ed.remove(KEY_GRP_PREFIX + groupId + "_name");
        ed.remove(KEY_GRP_PREFIX + groupId + "_pin");
        ed.remove(KEY_GRP_PREFIX + groupId + "_chats");
        ed.remove(KEY_GRP_PREFIX + groupId + "_timer");
        ed.remove(KEY_GRP_PREFIX + groupId + "_hide");
        ed.remove(KEY_GRP_PREFIX + groupId + "_hint");
        ed.apply();
        for (long cid : chats) {
            removeLock(cid);
        }
    }

    public String getGroupName(int groupId) {
        return prefs.getString(KEY_GRP_PREFIX + groupId + "_name", "");
    }

    public void setGroupName(int groupId, String name) {
        prefs.edit().putString(KEY_GRP_PREFIX + groupId + "_name", name).apply();
    }

    public boolean checkGroupPin(int groupId, String pin) {
        String stored = prefs.getString(KEY_GRP_PREFIX + groupId + "_pin", null);
        return verifyAndUpgrade(stored, pin, KEY_GRP_PREFIX + groupId + "_pin");
    }

    public void setGroupPin(int groupId, String newPin) {
        String hash = hashPin(newPin);
        prefs.edit().putString(KEY_GRP_PREFIX + groupId + "_pin", hash).apply();
        for (long cid : getGroupChats(groupId)) {
            setLock(cid, newPin);
        }
    }

    public List<Long> getGroupChats(int groupId) {
        String raw = prefs.getString(KEY_GRP_PREFIX + groupId + "_chats", "");
        return parseIds(raw);
    }

    public void setGroupChats(int groupId, List<Long> chatIds) {
        prefs.edit().putString(KEY_GRP_PREFIX + groupId + "_chats", joinIds(chatIds)).apply();
    }

    public void addChatToGroup(int groupId, long dialogId) {
        List<Long> chats = getGroupChats(groupId);
        if (!chats.contains(dialogId)) {
            chats.add(dialogId);
            setGroupChats(groupId, chats);
            String storedHash = prefs.getString(KEY_GRP_PREFIX + groupId + "_pin", "");
            prefs.edit().putString(KEY_PREFIX + dialogId, storedHash).apply();
        }
    }

    public void removeChatFromGroup(int groupId, long dialogId) {
        List<Long> chats = getGroupChats(groupId);
        chats.remove(dialogId);
        setGroupChats(groupId, chats);
        removeLock(dialogId);
    }

    public int getGroupTimer(int groupId) {
        return prefs.getInt(KEY_GRP_PREFIX + groupId + "_timer", -1);
    }

    public void setGroupTimer(int groupId, int seconds) {
        prefs.edit().putInt(KEY_GRP_PREFIX + groupId + "_timer", seconds).apply();
    }

    public int getGroupHide(int groupId) {
        return prefs.getInt(KEY_GRP_PREFIX + groupId + "_hide", -1);
    }

    public void setGroupHide(int groupId, int value) {
        prefs.edit().putInt(KEY_GRP_PREFIX + groupId + "_hide", value).apply();
    }

    public void setGroupHint(int groupId, String text) {
        if (text == null || text.trim().isEmpty()) {
            prefs.edit().remove(KEY_GRP_PREFIX + groupId + "_hint").apply();
        } else {
            prefs.edit().putString(KEY_GRP_PREFIX + groupId + "_hint", text.trim()).apply();
        }
    }

    public String getGroupHint(int groupId) {
        return prefs.getString(KEY_GRP_PREFIX + groupId + "_hint", null);
    }

    public List<Integer> getAllGroupIds() {
        List<Integer> ids = new ArrayList<>();
        Map<String, ?> all = prefs.getAll();
        for (String key : all.keySet()) {
            if (key.startsWith(KEY_GRP_PREFIX) && key.endsWith("_name")) {
                try {
                    String mid = key.substring(KEY_GRP_PREFIX.length(), key.length() - 5);
                    ids.add(Integer.parseInt(mid));
                } catch (NumberFormatException ignored) {}
            }
        }
        return ids;
    }

    public int findGroupForChat(long dialogId) {
        for (int gid : getAllGroupIds()) {
            if (getGroupChats(gid).contains(dialogId)) return gid;
        }
        return -1;
    }

    private static String joinIds(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    private static List<Long> parseIds(String raw) {
        List<Long> list = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return list;
        for (String s : raw.split(",")) {
            try { list.add(Long.parseLong(s.trim())); } catch (NumberFormatException ignored) {}
        }
        return list;
    }

    // --- Hash ---

    /**
     * Returns a salted SHA-256 hash of the PIN (format: "v2:<hex>").
     * The per-installation salt is generated once and stored in SharedPreferences.
     */
    private String hashPin(String pin) {
        return HASH_V2_PREFIX + sha256(getSalt() + pin);
    }

    /**
     * Verifies {@code pin} against {@code storedHash}.
     * Supports both v2 (salted) and legacy v1 (unsalted) hashes.
     * On successful v1 verification, silently upgrades the stored value to v2.
     */
    private boolean verifyAndUpgrade(String storedHash, String pin, String prefKey) {
        if (storedHash == null) return false;
        if (storedHash.startsWith(HASH_V2_PREFIX)) {
            return hashPin(pin).equals(storedHash);
        }
        // Legacy v1: plain SHA-256 without salt.
        if (sha256(pin).equals(storedHash)) {
            prefs.edit().putString(prefKey, hashPin(pin)).apply();
            return true;
        }
        return false;
    }

    private String getSalt() {
        String salt = prefs.getString(KEY_PIN_SALT, null);
        if (salt == null) {
            byte[] bytes = new byte[16];
            new SecureRandom().nextBytes(bytes);
            StringBuilder sb = new StringBuilder(32);
            for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
            salt = sb.toString();
            prefs.edit().putString(KEY_PIN_SALT, salt).apply();
        }
        return salt;
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            // SHA-256 is guaranteed on Android; this branch is unreachable in practice.
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
