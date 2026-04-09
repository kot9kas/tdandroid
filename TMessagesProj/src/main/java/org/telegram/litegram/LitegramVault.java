package org.telegram.litegram;

import android.text.TextUtils;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
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

    public static void captureBeforeDelete(SQLiteDatabase database, long dialogId, String midsStr, int account) {
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
            long fromId = MessageObject.getFromChatId(message);
            String senderName = resolveName(fromId, account);
            String chatTitle = resolveDialogTitle(dialogId, account);
            String text = message.message;
            int mediaType = classifyMedia(message);
            String mediaPath = resolveMediaPath(message, account);

            LitegramStorage.getInstance().saveDeletedMessage(
                    message.id, dialogId, fromId,
                    senderName, chatTitle,
                    text, mediaType, mediaPath,
                    message.date
            );
            FileLog.d("litegram vault: saved deleted msg mid=" + message.id + " dialog=" + dialogId);
        } catch (Exception e) {
            FileLog.e("litegram vault: interceptDeletedMessage error", e);
        }
    }

    public static void interceptOnceMedia(TLRPC.Message message, long dialogId, int account) {
        try {
            if (message == null || DialogObject.isEncryptedDialog(dialogId)) return;
            long fromId = MessageObject.getFromChatId(message);
            String senderName = resolveName(fromId, account);
            String chatTitle = resolveDialogTitle(dialogId, account);
            String text = message.message;
            int mediaType = classifyMedia(message);

            String savedPath = copyMediaFile(message, account);
            if (savedPath == null) {
                savedPath = resolveMediaPath(message, account);
            }

            LitegramStorage.getInstance().saveOnceMedia(
                    message.id, dialogId, fromId,
                    senderName, chatTitle,
                    text, mediaType, savedPath,
                    message.date
            );
            FileLog.d("litegram vault: saved once media mid=" + message.id + " dialog=" + dialogId);
        } catch (Exception e) {
            FileLog.e("litegram vault: interceptOnceMedia error", e);
        }
    }

    private static String copyMediaFile(TLRPC.Message message, int account) {
        try {
            File src = FileLoader.getInstance(account).getPathToMessage(message);
            if (src == null || !src.exists()) return null;

            File destDir = LitegramStorage.getOnceMediaDir();
            String ext = getExtension(src.getName());
            File dest = new File(destDir, "once_" + message.dialog_id + "_" + message.id + ext);
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
            FileLog.e("litegram vault: copyMediaFile failed", e);
            return null;
        }
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

    public static boolean isOnceMedia(TLRPC.Message message) {
        if (message == null) return false;
        TLRPC.MessageMedia media = MessageObject.getMedia(message);
        if (media == null) return false;
        if (media.ttl_seconds == 0x7FFFFFFF) return true;
        if (media.ttl_seconds > 0) return true;
        return false;
    }

    private static String resolveMediaPath(TLRPC.Message message, int account) {
        try {
            File f = FileLoader.getInstance(account).getPathToMessage(message);
            if (f != null && f.exists()) return f.getAbsolutePath();
        } catch (Exception ignored) {}
        return "";
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
