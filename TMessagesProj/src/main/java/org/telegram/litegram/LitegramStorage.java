package org.telegram.litegram;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LitegramStorage {

    private static final String DB_NAME = "litegram_vault.db";
    private static final int DB_VERSION = 1;

    private static volatile LitegramStorage instance;
    private final DbHelper helper;

    public static LitegramStorage getInstance() {
        if (instance == null) {
            synchronized (LitegramStorage.class) {
                if (instance == null) {
                    instance = new LitegramStorage(ApplicationLoader.applicationContext);
                }
            }
        }
        return instance;
    }

    private LitegramStorage(Context context) {
        helper = new DbHelper(context);
    }

    public static class SavedMessage {
        public int mid;
        public long dialogId;
        public long fromId;
        public String senderName;
        public String chatTitle;
        public String text;
        public int mediaType;
        public String mediaPath;
        public int date;
        public long savedAt;
        public int type; // 0 = deleted, 1 = once_media
    }

    public void saveDeletedMessage(int mid, long dialogId, long fromId,
                                   String senderName, String chatTitle,
                                   String text, int mediaType, String mediaPath, int date) {
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            SQLiteStatement st = db.compileStatement(
                    "INSERT OR REPLACE INTO deleted_messages " +
                    "(mid, dialog_id, from_id, sender_name, chat_title, text, media_type, media_path, date, saved_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            st.bindLong(1, mid);
            st.bindLong(2, dialogId);
            st.bindLong(3, fromId);
            st.bindString(4, senderName != null ? senderName : "");
            st.bindString(5, chatTitle != null ? chatTitle : "");
            st.bindString(6, text != null ? text : "");
            st.bindLong(7, mediaType);
            st.bindString(8, mediaPath != null ? mediaPath : "");
            st.bindLong(9, date);
            st.bindLong(10, System.currentTimeMillis() / 1000);
            st.executeInsert();
            st.close();
        } catch (Exception e) {
            FileLog.e("litegram: saveDeletedMessage failed", e);
        }
    }

    public void saveOnceMedia(int mid, long dialogId, long fromId,
                              String senderName, String chatTitle,
                              String text, int mediaType, String mediaPath, int date) {
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            SQLiteStatement st = db.compileStatement(
                    "INSERT OR REPLACE INTO once_media " +
                    "(mid, dialog_id, from_id, sender_name, chat_title, text, media_type, media_path, date, saved_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            st.bindLong(1, mid);
            st.bindLong(2, dialogId);
            st.bindLong(3, fromId);
            st.bindString(4, senderName != null ? senderName : "");
            st.bindString(5, chatTitle != null ? chatTitle : "");
            st.bindString(6, text != null ? text : "");
            st.bindLong(7, mediaType);
            st.bindString(8, mediaPath != null ? mediaPath : "");
            st.bindLong(9, date);
            st.bindLong(10, System.currentTimeMillis() / 1000);
            st.executeInsert();
            st.close();
        } catch (Exception e) {
            FileLog.e("litegram: saveOnceMedia failed", e);
        }
    }

    public List<SavedMessage> getDeletedMessages(int limit, int offset) {
        return query("deleted_messages", 0, limit, offset);
    }

    public List<SavedMessage> getOnceMedia(int limit, int offset) {
        return query("once_media", 1, limit, offset);
    }

    public int getDeletedMessagesCount() {
        return count("deleted_messages");
    }

    public int getOnceMediaCount() {
        return count("once_media");
    }

    public void deleteDeletedMessage(int mid, long dialogId) {
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.execSQL("DELETE FROM deleted_messages WHERE mid = ? AND dialog_id = ?",
                    new Object[]{mid, dialogId});
        } catch (Exception e) {
            FileLog.e("litegram: deleteDeletedMessage failed", e);
        }
    }

    public void deleteOnceMedia(int mid, long dialogId) {
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.execSQL("DELETE FROM once_media WHERE mid = ? AND dialog_id = ?",
                    new Object[]{mid, dialogId});
        } catch (Exception e) {
            FileLog.e("litegram: deleteOnceMedia failed", e);
        }
    }

    public void clearDeletedMessages() {
        try {
            helper.getWritableDatabase().execSQL("DELETE FROM deleted_messages");
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void clearOnceMedia() {
        try {
            helper.getWritableDatabase().execSQL("DELETE FROM once_media");
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static File getOnceMediaDir() {
        File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "litegram_once");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private List<SavedMessage> query(String table, int type, int limit, int offset) {
        List<SavedMessage> result = new ArrayList<>();
        Cursor c = null;
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            c = db.rawQuery("SELECT mid, dialog_id, from_id, sender_name, chat_title, text, " +
                    "media_type, media_path, date, saved_at FROM " + table +
                    " ORDER BY saved_at DESC LIMIT ? OFFSET ?",
                    new String[]{String.valueOf(limit), String.valueOf(offset)});
            while (c.moveToNext()) {
                SavedMessage m = new SavedMessage();
                m.mid = c.getInt(0);
                m.dialogId = c.getLong(1);
                m.fromId = c.getLong(2);
                m.senderName = c.getString(3);
                m.chatTitle = c.getString(4);
                m.text = c.getString(5);
                m.mediaType = c.getInt(6);
                m.mediaPath = c.getString(7);
                m.date = c.getInt(8);
                m.savedAt = c.getLong(9);
                m.type = type;
                result.add(m);
            }
        } catch (Exception e) {
            FileLog.e("litegram: query " + table + " failed", e);
        } finally {
            if (c != null) c.close();
        }
        return result;
    }

    private int count(String table) {
        Cursor c = null;
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            c = db.rawQuery("SELECT COUNT(*) FROM " + table, null);
            if (c.moveToFirst()) return c.getInt(0);
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (c != null) c.close();
        }
        return 0;
    }

    private static class DbHelper extends SQLiteOpenHelper {

        DbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS deleted_messages (" +
                    "mid INTEGER, " +
                    "dialog_id INTEGER, " +
                    "from_id INTEGER, " +
                    "sender_name TEXT, " +
                    "chat_title TEXT, " +
                    "text TEXT, " +
                    "media_type INTEGER DEFAULT 0, " +
                    "media_path TEXT, " +
                    "date INTEGER, " +
                    "saved_at INTEGER, " +
                    "PRIMARY KEY (mid, dialog_id))");

            db.execSQL("CREATE TABLE IF NOT EXISTS once_media (" +
                    "mid INTEGER, " +
                    "dialog_id INTEGER, " +
                    "from_id INTEGER, " +
                    "sender_name TEXT, " +
                    "chat_title TEXT, " +
                    "text TEXT, " +
                    "media_type INTEGER DEFAULT 0, " +
                    "media_path TEXT, " +
                    "date INTEGER, " +
                    "saved_at INTEGER, " +
                    "PRIMARY KEY (mid, dialog_id))");

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_del_saved ON deleted_messages(saved_at DESC)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_once_saved ON once_media(saved_at DESC)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }

    public static final int MEDIA_NONE = 0;
    public static final int MEDIA_PHOTO = 1;
    public static final int MEDIA_VIDEO = 2;
    public static final int MEDIA_DOCUMENT = 3;
    public static final int MEDIA_VOICE = 4;
    public static final int MEDIA_ROUND = 5;
    public static final int MEDIA_STICKER = 6;
    public static final int MEDIA_GIF = 7;
}
