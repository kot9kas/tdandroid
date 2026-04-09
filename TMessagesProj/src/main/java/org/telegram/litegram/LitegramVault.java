package org.telegram.litegram;

import android.text.TextUtils;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public class LitegramVault {

    private static final java.util.Set<Integer> ALLOWED_MEDIA = new java.util.HashSet<>(java.util.Arrays.asList(
            LitegramStorage.MEDIA_NONE,
            LitegramStorage.MEDIA_VOICE
    ));

    public static void captureBeforeDelete(SQLiteDatabase database, long dialogId, String midsStr, int account) {
        if (!LitegramConfig.isVaultDeletedEnabled()) return;
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized(String.format(Locale.US,
                    "SELECT data, mid, date FROM messages_v2 WHERE mid IN(%s) AND uid = %d", midsStr, dialogId));
            long currentUser = UserConfig.getInstance(account).getClientUserId();
            while (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data != null) {
                    TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    message.readAttachPath(data, currentUser);
                    data.reuse();
                    message.id = cursor.intValue(1);
                    message.date = cursor.intValue(2);
                    message.dialog_id = dialogId;
                    interceptDeletedMessage(message, dialogId, account);
                }
            }
        } catch (Exception e) {
            FileLog.e("litegram vault: captureBeforeDelete error", e);
        } finally {
            if (cursor != null) cursor.dispose();
        }
    }

    public static void interceptDeletedMessage(TLRPC.Message message, long dialogId, int account) {
        try {
            if (message == null || DialogObject.isEncryptedDialog(dialogId)) return;

            int mediaType = classifyMedia(message);
            if (!ALLOWED_MEDIA.contains(mediaType)) return;

            String text = message.message;
            if ((text == null || text.isEmpty()) && mediaType == LitegramStorage.MEDIA_NONE) return;

            long fromId = MessageObject.getFromChatId(message);
            String senderName = resolveName(fromId, account);
            String chatTitle = resolveDialogTitle(dialogId, account);

            String mediaPath = "";
            byte[] docData = null;
            if (mediaType == LitegramStorage.MEDIA_VOICE) {
                mediaPath = copyVoiceFile(message, account);
                TLRPC.MessageMedia media = MessageObject.getMedia(message);
                if (media != null && media.document != null) {
                    docData = serializeDocument(media.document);
                    if (mediaPath.isEmpty()) {
                        FileLog.d("litegram vault: voice file not found, doc serialized for later download");
                    }
                }
            }

            LitegramStorage.getInstance().saveDeletedMessage(
                    message.id, dialogId, fromId,
                    senderName, chatTitle,
                    text, mediaType, mediaPath,
                    docData, message.date
            );
            FileLog.d("litegram vault: saved deleted msg mid=" + message.id + " dialog=" + dialogId);
        } catch (Exception e) {
            FileLog.e("litegram vault: interceptDeletedMessage error", e);
        }
    }

    private static String copyVoiceFile(TLRPC.Message message, int account) {
        try {
            TLRPC.MessageMedia media = MessageObject.getMedia(message);
            if (media == null || media.document == null) {
                FileLog.d("litegram vault: voice - no document in message");
                return "";
            }
            TLRPC.Document doc = media.document;
            FileLog.d("litegram vault: voice doc id=" + doc.id + " dc=" + doc.dc_id
                    + " mime=" + doc.mime_type + " size=" + doc.size);

            File src = findVoiceFile(doc, message, account);
            if (src == null || !src.exists()) {
                FileLog.d("litegram vault: voice file NOT FOUND after all strategies");
                return "";
            }

            FileLog.d("litegram vault: voice found at " + src.getAbsolutePath()
                    + " size=" + src.length());

            File destDir = getVoiceDir();
            String ext = getExtension(src.getName());
            if (ext.isEmpty()) ext = ".ogg";
            File dest = new File(destDir, "voice_" + message.dialog_id + "_" + message.id + ext);
            if (dest.exists()) return dest.getAbsolutePath();

            try (InputStream in = new FileInputStream(src);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
            FileLog.d("litegram vault: voice copied to " + dest.getAbsolutePath());
            return dest.getAbsolutePath();
        } catch (Exception e) {
            FileLog.e("litegram vault: copyVoiceFile failed", e);
            return "";
        }
    }

    private static File findVoiceFile(TLRPC.Document doc, TLRPC.Message message, int account) {
        File[] dirs = {
                FileLoader.getDirectory(FileLoader.MEDIA_DIR_AUDIO),
                FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE),
                FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT),
        };

        String attachName = FileLoader.getAttachFileName(doc);
        String baseName = doc.dc_id + "_" + doc.id;
        String[] extensions = {".ogg", ".mp3", ".m4a", ".oga", ".opus", ".wav", ""};

        FileLog.d("litegram vault: findVoice attachName=" + attachName + " baseName=" + baseName);

        for (File dir : dirs) {
            if (dir == null) continue;
            if (attachName != null && !attachName.isEmpty()) {
                File f = new File(dir, attachName);
                if (f.exists()) {
                    FileLog.d("litegram vault: found by attachName in " + dir.getName());
                    return f;
                }
            }
        }

        for (File dir : dirs) {
            if (dir == null) continue;
            for (String ext : extensions) {
                File f = new File(dir, baseName + ext);
                if (f.exists()) {
                    FileLog.d("litegram vault: found by baseName+ext in " + dir.getName());
                    return f;
                }
            }
        }

        String idStr = String.valueOf(doc.id);
        for (File dir : dirs) {
            if (dir == null || !dir.isDirectory()) continue;
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (f.isFile() && f.getName().contains(idStr)) {
                    FileLog.d("litegram vault: found by docId scan in " + dir.getName()
                            + " -> " + f.getName());
                    return f;
                }
            }
        }

        try {
            File f = FileLoader.getInstance(account).getPathToMessage(message, true);
            if (f != null && f.exists()) {
                FileLog.d("litegram vault: found by getPathToMessage(queue=true)");
                return f;
            }
        } catch (Exception e) {
            FileLog.e("litegram vault: getPathToMessage(queue=true) failed", e);
        }

        try {
            File f = FileLoader.getInstance(account).getPathToAttach(doc, true);
            if (f != null && f.exists()) {
                FileLog.d("litegram vault: found by getPathToAttach(forceCache=true)");
                return f;
            }
        } catch (Exception e) {
            FileLog.e("litegram vault: getPathToAttach(forceCache) failed", e);
        }

        File intCacheDir = ApplicationLoader.applicationContext.getCacheDir();
        if (intCacheDir != null && intCacheDir.isDirectory()) {
            if (attachName != null && !attachName.isEmpty()) {
                File f = new File(intCacheDir, attachName);
                if (f.exists()) {
                    FileLog.d("litegram vault: found in internal cache by attachName");
                    return f;
                }
            }
            for (String ext : extensions) {
                File f = new File(intCacheDir, baseName + ext);
                if (f.exists()) {
                    FileLog.d("litegram vault: found in internal cache by baseName+ext");
                    return f;
                }
            }
        }

        return null;
    }

    /**
     * Retry finding a voice file from a @doc:dc:id reference saved when the original copy failed.
     * Copies the file to vault dir and updates the DB record. Returns the path or null.
     */
    public static String retryResolveVoice(String docRef, long dialogId, int mid) {
        if (docRef == null || !docRef.startsWith("@doc:")) return null;
        try {
            String[] parts = docRef.substring(5).split(":");
            if (parts.length < 2) return null;
            int dcId = Integer.parseInt(parts[0]);
            long docId = Long.parseLong(parts[1]);

            File[] dirs = {
                    FileLoader.getDirectory(FileLoader.MEDIA_DIR_AUDIO),
                    FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE),
                    FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT),
            };
            String baseName = dcId + "_" + docId;
            String[] extensions = {".ogg", ".mp3", ".m4a", ".oga", ".opus", ".wav", ""};

            File src = null;
            for (File dir : dirs) {
                if (dir == null) continue;
                for (String ext : extensions) {
                    File f = new File(dir, baseName + ext);
                    if (f.exists()) { src = f; break; }
                }
                if (src != null) break;
            }
            if (src == null) {
                String idStr = String.valueOf(docId);
                for (File dir : dirs) {
                    if (dir == null || !dir.isDirectory()) continue;
                    File[] files = dir.listFiles();
                    if (files == null) continue;
                    for (File f : files) {
                        if (f.isFile() && f.getName().contains(idStr)) { src = f; break; }
                    }
                    if (src != null) break;
                }
            }
            if (src == null) {
                File intCache = ApplicationLoader.applicationContext.getCacheDir();
                if (intCache != null && intCache.isDirectory()) {
                    for (String ext : extensions) {
                        File f = new File(intCache, baseName + ext);
                        if (f.exists()) { src = f; break; }
                    }
                }
            }

            if (src == null || !src.exists()) return null;

            File destDir = getVoiceDir();
            String ext = getExtension(src.getName());
            if (ext.isEmpty()) ext = ".ogg";
            File dest = new File(destDir, "voice_" + dialogId + "_" + mid + ext);
            if (!dest.exists()) {
                try (InputStream in = new FileInputStream(src);
                     OutputStream out = new FileOutputStream(dest)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }
            }

            String path = dest.getAbsolutePath();
            LitegramStorage.getInstance().updateMediaPath(mid, dialogId, path);
            FileLog.d("litegram vault: retryResolveVoice success -> " + path);
            return path;
        } catch (Exception e) {
            FileLog.e("litegram vault: retryResolveVoice failed", e);
            return null;
        }
    }

    private static byte[] serializeDocument(TLRPC.Document doc) {
        try {
            int size = doc.getObjectSize();
            NativeByteBuffer buffer = new NativeByteBuffer(size);
            doc.serializeToStream(buffer);
            byte[] bytes = new byte[size];
            buffer.position(0);
            buffer.readBytes(bytes, false);
            buffer.reuse();
            return bytes;
        } catch (Exception e) {
            FileLog.e("litegram vault: serializeDocument failed", e);
            return null;
        }
    }

    public static TLRPC.Document deserializeDocument(byte[] data) {
        if (data == null || data.length == 0) return null;
        try {
            NativeByteBuffer buffer = new NativeByteBuffer(data.length);
            buffer.writeBytes(data);
            buffer.position(0);
            int constructor = buffer.readInt32(false);
            TLRPC.Document doc = TLRPC.Document.TLdeserialize(buffer, constructor, false);
            buffer.reuse();
            return doc;
        } catch (Exception e) {
            FileLog.e("litegram vault: deserializeDocument failed", e);
            return null;
        }
    }

    /**
     * Download a voice file using FileLoader and copy it to vault dir.
     * Must be called from a background thread. Returns the local path or null.
     */
    public static String downloadVoiceFile(byte[] docData, long dialogId, int mid, int account) {
        TLRPC.Document doc = deserializeDocument(docData);
        if (doc == null) return null;
        try {
            File existing = findVoiceFile(doc, null, account);
            if (existing != null && existing.exists()) {
                return copyToVault(existing, dialogId, mid);
            }

            FileLoader loader = FileLoader.getInstance(account);
            File path = loader.getPathToAttach(doc);
            if (path != null && path.exists()) {
                return copyToVault(path, dialogId, mid);
            }
            path = loader.getPathToAttach(doc, true);
            if (path != null && path.exists()) {
                return copyToVault(path, dialogId, mid);
            }

            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final File[] result = new File[1];

            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                loader.loadFile(doc, null, FileLoader.PRIORITY_HIGH, 0);
            });

            for (int attempt = 0; attempt < 30; attempt++) {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                File f = loader.getPathToAttach(doc);
                if (f != null && f.exists() && f.length() > 0) {
                    result[0] = f;
                    break;
                }
                f = loader.getPathToAttach(doc, true);
                if (f != null && f.exists() && f.length() > 0) {
                    result[0] = f;
                    break;
                }
            }

            if (result[0] != null && result[0].exists()) {
                return copyToVault(result[0], dialogId, mid);
            }
        } catch (Exception e) {
            FileLog.e("litegram vault: downloadVoiceFile failed", e);
        }
        return null;
    }

    private static String copyToVault(File src, long dialogId, int mid) {
        try {
            File destDir = getVoiceDir();
            String ext = getExtension(src.getName());
            if (ext.isEmpty()) ext = ".ogg";
            File dest = new File(destDir, "voice_" + dialogId + "_" + mid + ext);
            if (dest.exists()) return dest.getAbsolutePath();
            try (InputStream in = new FileInputStream(src);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
            return dest.getAbsolutePath();
        } catch (Exception e) {
            FileLog.e("litegram vault: copyToVault failed", e);
            return null;
        }
    }

    static File getVoiceDir() {
        File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "litegram_voice");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }

    public static int classifyMedia(TLRPC.Message message) {
        if (message == null || message.media == null) return LitegramStorage.MEDIA_NONE;
        TLRPC.MessageMedia media = MessageObject.getMedia(message);
        if (media instanceof TLRPC.TL_messageMediaPhoto) {
            return LitegramStorage.MEDIA_PHOTO;
        }
        if (media instanceof TLRPC.TL_messageMediaDocument && media.document != null) {
            TLRPC.Document doc = media.document;
            for (TLRPC.DocumentAttribute attr : doc.attributes) {
                if (attr instanceof TLRPC.TL_documentAttributeVideo) {
                    if (attr.round_message) return LitegramStorage.MEDIA_ROUND;
                    return LitegramStorage.MEDIA_VIDEO;
                }
                if (attr instanceof TLRPC.TL_documentAttributeAudio) {
                    if (attr.voice) return LitegramStorage.MEDIA_VOICE;
                    return LitegramStorage.MEDIA_DOCUMENT;
                }
                if (attr instanceof TLRPC.TL_documentAttributeSticker) {
                    return LitegramStorage.MEDIA_STICKER;
                }
                if (attr instanceof TLRPC.TL_documentAttributeAnimated) {
                    return LitegramStorage.MEDIA_GIF;
                }
            }
            return LitegramStorage.MEDIA_DOCUMENT;
        }
        return LitegramStorage.MEDIA_NONE;
    }

    private static String resolveName(long userId, int account) {
        try {
            if (userId > 0) {
                TLRPC.User user = MessagesController.getInstance(account).getUser(userId);
                if (user != null) {
                    StringBuilder sb = new StringBuilder();
                    if (!TextUtils.isEmpty(user.first_name)) sb.append(user.first_name);
                    if (!TextUtils.isEmpty(user.last_name)) {
                        if (sb.length() > 0) sb.append(" ");
                        sb.append(user.last_name);
                    }
                    return sb.toString();
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-userId);
                if (chat != null && !TextUtils.isEmpty(chat.title)) {
                    return chat.title;
                }
            }
        } catch (Exception ignored) {}
        return String.valueOf(userId);
    }

    private static String resolveDialogTitle(long dialogId, int account) {
        try {
            if (dialogId > 0) {
                return resolveName(dialogId, account);
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
                if (chat != null && !TextUtils.isEmpty(chat.title)) {
                    return chat.title;
                }
            }
        } catch (Exception ignored) {}
        return String.valueOf(dialogId);
    }
}
