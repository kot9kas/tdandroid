package org.telegram.litegram;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.LoginActivity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Импорт/экспорт пакетов сессии Telethon / Pyrogram (+ JSON метаданные).
 * Формат tdata (Telegram Desktop) на Android не поддерживается — только .session и .json.
 */
public final class LitegramSessionTransfer {

    private LitegramSessionTransfer() {}

    public static void openSessionFilePicker(LoginActivity loginActivity) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/zip",
                "application/x-zip-compressed",
                "application/octet-stream",
                "application/x-sqlite3",
                "*/*"
        });
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        } else {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        loginActivity.startActivityForResult(
                Intent.createChooser(intent, LocaleController.getString(R.string.LitegramSessionImport)),
                LoginActivity.REQUEST_LITEGRAM_SESSION_IMPORT);
    }

    public static void handleImportUris(LoginActivity loginActivity, int currentAccount, ArrayList<Uri> uris) {
        if (loginActivity.getParentActivity() == null || uris == null || uris.isEmpty()) {
            return;
        }
        Activity parent = loginActivity.getParentActivity();
        loginActivity.prepareNetworkForImportedSession();
        AlertDialog progress = new AlertDialog(parent, AlertDialog.ALERT_TYPE_SPINNER);
        progress.showDelayed(300);
        Utilities.globalQueue.postRunnable(() -> {
            try {
                File work = new File(ApplicationLoader.applicationContext.getCacheDir(), "session_import");
                deleteRecursive(work);
                work.mkdirs();
                List<File> files = new ArrayList<>();
                ContentResolver resolver = parent.getContentResolver();
                int i = 0;
                for (Uri uri : uris) {
                    String name = queryDisplayName(resolver, uri);
                    if (name == null || name.isEmpty()) {
                        name = "file_" + (i++) + ".dat";
                    }
                    name = new File(name).getName();
                    File out = new File(work, name);
                    copyUriToFile(resolver, uri, out);
                    files.add(out);
                }
                SessionPayload payload = resolvePayload(work, files);
                AndroidUtilities.runOnUIThread(() -> {
                    progress.dismiss();
                    if (payload == null || payload.authKey == null || payload.authKey.length != 256) {
                        Toast.makeText(parent,
                                LocaleController.formatString(R.string.LitegramSessionImportError,
                                        describeImportFailure(work)),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (payload.onlyTdata) {
                        Toast.makeText(parent, LocaleController.getString(R.string.LitegramSessionTdataUnsupported), Toast.LENGTH_LONG).show();
                        return;
                    }
                    runNativeImportAndGetUser(loginActivity, currentAccount, payload);
                });
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    progress.dismiss();
                    String detail = e.getMessage();
                    if (TextUtils.isEmpty(detail)) {
                        detail = e.getClass().getSimpleName();
                    }
                    Toast.makeText(parent,
                            LocaleController.formatString(R.string.LitegramSessionImportError, detail),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static void runNativeImportAndGetUser(LoginActivity loginActivity, int account, SessionPayload payload) {
        Activity parent = loginActivity.getParentActivity();
        normalizeEndpointsForNative(payload);
        ConnectionsManager cm = ConnectionsManager.getInstance(account);
        if (cm.isTestBackend()) {
            cm.switchBackend(false);
        }
        cm.cleanup(true);
        String host = payload.host != null ? payload.host : defaultHostForDc(payload.dcId);
        ConnectionsManager.importPermanentAuthKey(
                account,
                payload.dcId,
                payload.authKey,
                payload.userId,
                host,
                payload.port,
                !host.isEmpty());

        Utilities.globalQueue.postRunnable(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    if (ConnectionsManager.getInstance(account).getCurrentAuthKeyId() != 0) {
                        break;
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
                if (ConnectionsManager.getInstance(account).getCurrentAuthKeyId() == 0) {
                    AndroidUtilities.runOnUIThread(() -> Toast.makeText(parent,
                            LocaleController.getString(R.string.LitegramSessionImportErrorNetwork), Toast.LENGTH_LONG).show());
                    return;
                }
                TLRPC.TL_users_getFullUser req = new TLRPC.TL_users_getFullUser();
                req.id = new TLRPC.TL_inputUserSelf();
                ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
                    if (response instanceof TLRPC.TL_users_userFull) {
                        TLRPC.TL_users_userFull full = (TLRPC.TL_users_userFull) response;
                        TLRPC.User user = null;
                        if (!full.users.isEmpty()) {
                            user = full.users.get(0);
                        }
                        if (user == null && full.full_user != null) {
                            for (TLRPC.User u : full.users) {
                                if (u.id == full.full_user.id) {
                                    user = u;
                                    break;
                                }
                            }
                        }
                        TLRPC.User resolved = user;
                        AndroidUtilities.runOnUIThread(() -> {
                            if (resolved != null) {
                                if (payload.expectedUserId != 0 && resolved.id != payload.expectedUserId) {
                                    Toast.makeText(parent, LocaleController.getString(R.string.LitegramSessionImportUserMismatch), Toast.LENGTH_LONG).show();
                                    return;
                                }
                                loginActivity.finishLoginWithImportedUser(resolved);
                            } else {
                                Toast.makeText(parent, LocaleController.getString(R.string.LitegramSessionImportErrorNetwork), Toast.LENGTH_LONG).show();
                            }
                        });
                    } else {
                        String msg = (error != null && error.text != null && !error.text.isEmpty()) ? error.text : "rpc";
                        AndroidUtilities.runOnUIThread(() -> Toast.makeText(parent,
                                LocaleController.formatString(R.string.LitegramSessionImportError, msg),
                                Toast.LENGTH_LONG).show());
                    }
                }, ConnectionsManager.RequestFlagFailOnServerErrors);
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> Toast.makeText(parent, e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    public static void exportAndShareSessionPack(org.telegram.ui.ActionBar.BaseFragment fragment, int currentAccount) {
        if (!UserConfig.getInstance(currentAccount).isClientActivated()) {
            Toast.makeText(fragment.getParentActivity(), LocaleController.getString(R.string.LitegramSessionExportNeedLogin), Toast.LENGTH_SHORT).show();
            return;
        }
        Activity parent = fragment.getParentActivity();
        AlertDialog progress = new AlertDialog(parent, AlertDialog.ALERT_TYPE_SPINNER);
        progress.showDelayed(200);
        Utilities.globalQueue.postRunnable(() -> {
            try {
                int dcId = ConnectionsManager.getInstance(currentAccount).getCurrentDatacenterId();
                byte[] authKey = ConnectionsManager.exportPermanentAuthKey(currentAccount, dcId);
                if (authKey == null || authKey.length != 256) {
                    AndroidUtilities.runOnUIThread(() -> {
                        progress.dismiss();
                        Toast.makeText(parent, LocaleController.getString(R.string.LitegramSessionExportError), Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                long userId = UserConfig.getInstance(currentAccount).getClientUserId();
                String base = String.valueOf(userId);
                File outDir = new File(ApplicationLoader.applicationContext.getFilesDir(), "session_share");
                //noinspection ResultOfMethodCallIgnored
                outDir.mkdirs();
                File telethonF = new File(outDir, base + "_telethon.session");
                File pyrogramF = new File(outDir, base + "_pyrogram.session");
                File jsonF = new File(outDir, base + ".json");
                writeTelethonSession(telethonF, dcId, authKey, defaultHostForDc(dcId), 443);
                writePyrogramSession(pyrogramF, dcId, authKey, userId, currentAccount);
                writeJsonMetadata(jsonF, currentAccount, dcId, userId);
                File zip = new File(outDir, "litegram_" + base + "_session.zip");
                zipFiles(zip, telethonF, pyrogramF, jsonF);
                AndroidUtilities.runOnUIThread(() -> {
                    progress.dismiss();
                    shareZip(fragment, zip);
                });
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    progress.dismiss();
                    Toast.makeText(parent, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static void shareZip(org.telegram.ui.ActionBar.BaseFragment fragment, File zip) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        try {
            String authority = fragment.getParentActivity().getPackageName() + ".provider";
            Uri uri = FileProvider.getUriForFile(fragment.getParentActivity(), authority, zip);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/zip");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            fragment.getParentActivity().startActivity(Intent.createChooser(share,
                    LocaleController.getString(R.string.LitegramSessionExport)));
        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(fragment.getParentActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static void zipFiles(File zip, File... inputs) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            for (File f : inputs) {
                ZipEntry entry = new ZipEntry(f.getName());
                zos.putNextEntry(entry);
                try (FileInputStream in = new FileInputStream(f)) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) > 0) {
                        zos.write(buf, 0, r);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    private static void writeTelethonSession(File file, int dcId, byte[] authKey, String host, int port) {
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        db.execSQL("CREATE TABLE entities (id integer primary key, hash integer not null, username text, phone integer, name text, date integer)");
        db.execSQL("CREATE TABLE sent_files (md5_digest blob, file_size integer, type integer, id integer, hash integer, primary key(md5_digest, file_size, type))");
        db.execSQL("CREATE TABLE sessions (dc_id integer primary key, server_address text, port integer, auth_key blob, takeout_id integer)");
        db.execSQL("CREATE TABLE update_state (id integer primary key, pts integer, qts integer, date integer, seq integer)");
        db.execSQL("CREATE TABLE version (version integer primary key)");
        db.execSQL("INSERT INTO version VALUES (7)");
        android.content.ContentValues cv = new android.content.ContentValues();
        cv.put("dc_id", dcId);
        cv.put("server_address", host);
        cv.put("port", port);
        cv.put("auth_key", authKey);
        db.insert("sessions", null, cv);
        db.close();
    }

    private static void writePyrogramSession(File file, int dcId, byte[] authKey, long userId, int account) {
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        db.execSQL("CREATE TABLE sessions (dc_id INTEGER PRIMARY KEY, test_mode INTEGER, auth_key BLOB, date INTEGER NOT NULL, user_id INTEGER, is_bot INTEGER)");
        db.execSQL("CREATE TABLE peers (id INTEGER PRIMARY KEY, access_hash INTEGER, type INTEGER NOT NULL, username TEXT, phone_number TEXT, last_update_on INTEGER NOT NULL DEFAULT (CAST(STRFTIME('%s', 'now') AS INTEGER)))");
        db.execSQL("CREATE TABLE version (number INTEGER PRIMARY KEY)");
        int now = ConnectionsManager.getInstance(account).getCurrentTime();
        android.content.ContentValues cv = new android.content.ContentValues();
        cv.put("dc_id", dcId);
        cv.put("test_mode", 0);
        cv.put("auth_key", authKey);
        cv.put("date", now);
        cv.put("user_id", userId);
        cv.put("is_bot", 0);
        db.insert("sessions", null, cv);
        android.content.ContentValues v = new android.content.ContentValues();
        v.put("number", 2);
        db.insert("version", null, v);
        db.close();
    }

    private static void writeJsonMetadata(File file, int account, int dcId, long userId) throws Exception {
        TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        JSONObject o = new JSONObject();
        o.put("app_id", BuildVars.APP_ID);
        o.put("app_hash", BuildVars.APP_HASH);
        o.put("sdk", "Android");
        o.put("device", android.os.Build.MODEL);
        o.put("app_version", BuildVars.BUILD_VERSION_STRING);
        o.put("lang_pack", "android");
        o.put("system_lang_pack", java.util.Locale.getDefault().toString());
        o.put("user_id", userId);
        o.put("id", userId);
        o.put("dc_id", dcId);
        o.put("session_file", "");
        if (user != null) {
            o.put("phone", user.phone != null ? user.phone : "");
            o.put("username", user.username);
            o.put("first_name", user.first_name != null ? user.first_name : "");
            o.put("last_name", user.last_name != null ? user.last_name : "");
            o.put("is_premium", user.premium);
        }
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(o.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Первый IPv4 для каждого DC — как в {@code ConnectionsManager::initDatacenters} (production),
     * чтобы импорт/экспорт совпадали с остальным клиентом и с типичными Telethon StringSession dc→ip маппингами.
     */
    private static String defaultHostForDc(int dcId) {
        switch (dcId) {
            case 1: return "149.154.175.50";
            case 2: return "149.154.167.51";
            case 3: return "149.154.175.100";
            case 4: return "149.154.167.91";
            case 5: return "149.154.171.5";
            default: return "149.154.167.51";
        }
    }

    /** Адреса из .session Telethon часто не подходят клиенту; используем те же DC, что и для Pyrogram. */
    private static void normalizeEndpointsForNative(SessionPayload p) {
        if (p == null) {
            return;
        }
        p.host = defaultHostForDc(p.dcId);
        p.port = 443;
    }

    private static String describeImportFailure(File workDir) {
        if (workDirHasJsonButNoSessionDb(workDir)) {
            return LocaleController.getString(R.string.LitegramSessionImportJsonOnly);
        }
        return LocaleController.getString(R.string.LitegramSessionImportErrorParse);
    }

    private static boolean workDirHasJsonButNoSessionDb(File workDir) {
        ArrayList<File> all = new ArrayList<>();
        collectFiles(workDir, all);
        boolean hasJson = false;
        boolean hasSqlite = false;
        for (File f : all) {
            String n = f.getName().toLowerCase();
            if (n.endsWith(".json") && !n.contains("tgnet")) {
                hasJson = true;
            }
            if (isSqliteDatabaseFile(f)) {
                hasSqlite = true;
            }
        }
        return hasJson && !hasSqlite;
    }

    private static class SessionPayload {
        int dcId;
        byte[] authKey;
        long userId;
        long expectedUserId;
        String host;
        int port;
        boolean onlyTdata;
    }

    private static SessionPayload resolvePayload(File workDir, List<File> roots) throws Exception {
        SessionPayload tele = null;
        SessionPayload pyro = null;
        JSONObject jsonMeta = null;
        boolean sawTdata = false;
        for (File root : roots) {
            if (isZipFile(root)) {
                unzipTo(workDir, root);
            }
        }
        ArrayList<File> all = new ArrayList<>();
        collectFiles(workDir, all);
        for (File f : all) {
            String n = f.getName().toLowerCase();
            if (n.endsWith(".json") && !n.contains("tgnet")) {
                try {
                    String s = readWholeFile(f);
                    jsonMeta = new JSONObject(s);
                } catch (Exception ignored) { }
            } else if (isSqliteDatabaseFile(f)) {
                if (tele == null && sqliteHasTelethonSchema(f)) {
                    SessionPayload t = readTelethon(f);
                    if (t != null) {
                        tele = t;
                    }
                }
                if (pyro == null && tele == null && !sqliteHasTelethonSchema(f)) {
                    SessionPayload p = readPyrogram(f);
                    if (p != null) {
                        pyro = p;
                    }
                }
                if (tele == null && pyro == null) {
                    SessionPayload a = readTelethon(f);
                    if (a != null) {
                        tele = a;
                    } else {
                        SessionPayload b = readPyrogram(f);
                        if (b != null) {
                            pyro = b;
                        }
                    }
                }
            } else if (n.endsWith("_telethon.session") || (n.endsWith(".session") && !n.contains("pyrogram") && sqliteHasTelethonSchema(f))) {
                if (tele == null) {
                    tele = readTelethon(f);
                }
            } else if (n.contains("pyrogram") && n.endsWith(".session")) {
                if (pyro == null) {
                    pyro = readPyrogram(f);
                }
            } else if (n.endsWith(".session") && pyro == null && tele == null) {
                SessionPayload a = readTelethon(f);
                if (a != null) {
                    tele = a;
                } else {
                    SessionPayload b = readPyrogram(f);
                    if (b != null) {
                        pyro = b;
                    }
                }
            }
            String path = f.getAbsolutePath().toLowerCase();
            if (path.contains("tdata" + File.separator + "key_datas") || path.endsWith("key_datas")) {
                sawTdata = true;
            }
        }
        SessionPayload use = tele != null ? tele : pyro;
        if (use == null) {
            if (sawTdata) {
                SessionPayload p = new SessionPayload();
                p.onlyTdata = true;
                return p;
            }
            return null;
        }
        if (jsonMeta != null) {
            long ju = jsonMeta.optLong("user_id", jsonMeta.optLong("id", 0));
            if (ju != 0) {
                use.expectedUserId = ju;
                if (use.userId == 0) {
                    use.userId = ju;
                }
            }
        }
        use.onlyTdata = false;
        return use;
    }

    /**
     * Telethon: таблица sessions с dc_id и auth_key, без колонки test_mode (это маркер Pyrogram).
     * Раньше требовали server_address — из‑за этого многие реальные Telethon .session не распознавались.
     */
    private static boolean sqliteHasTelethonSchema(File f) {
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(f.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='sessions'", null);
            if (!c.moveToFirst()) {
                c.close();
                db.close();
                return false;
            }
            c.close();
            c = db.rawQuery("PRAGMA table_info(sessions)", null);
            boolean hasDc = false;
            boolean hasKey = false;
            boolean hasTestMode = false;
            while (c.moveToNext()) {
                String col = c.getString(1);
                if ("dc_id".equals(col)) {
                    hasDc = true;
                }
                if ("auth_key".equals(col)) {
                    hasKey = true;
                }
                if ("test_mode".equals(col)) {
                    hasTestMode = true;
                }
            }
            c.close();
            db.close();
            return hasDc && hasKey && !hasTestMode;
        } catch (Exception e) {
            return false;
        }
    }

    private static SessionPayload readTelethon(File f) {
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(f.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            Cursor c = db.rawQuery("SELECT * FROM sessions LIMIT 1", null);
            if (!c.moveToFirst()) {
                c.close();
                db.close();
                return null;
            }
            int dcIdx = c.getColumnIndex("dc_id");
            int keyIdx = c.getColumnIndex("auth_key");
            if (dcIdx < 0 || keyIdx < 0) {
                c.close();
                db.close();
                return null;
            }
            SessionPayload p = new SessionPayload();
            p.dcId = c.getInt(dcIdx);
            byte[] key = c.getBlob(keyIdx);
            c.close();
            db.close();
            if (key == null || key.length != 256) {
                return null;
            }
            p.authKey = key;
            normalizeEndpointsForNative(p);
            return p;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static SessionPayload readPyrogram(File f) {
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(f.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            Cursor c = db.rawQuery("SELECT dc_id, auth_key, user_id FROM sessions LIMIT 1", null);
            if (!c.moveToFirst()) {
                c.close();
                db.close();
                return null;
            }
            SessionPayload p = new SessionPayload();
            p.dcId = c.getInt(0);
            byte[] key = c.getBlob(1);
            if (!c.isNull(2)) {
                p.userId = c.getLong(2);
            }
            c.close();
            db.close();
            if (key == null || key.length != 256) {
                return null;
            }
            p.authKey = key;
            p.host = defaultHostForDc(p.dcId);
            p.port = 443;
            return p;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static void collectFiles(File dir, ArrayList<File> out) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) {
                collectFiles(f, out);
            } else {
                out.add(f);
            }
        }
    }

    private static void unzipTo(File destDir, File zipFile) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = zis.getNextEntry()) != null) {
                File out = new File(destDir, e.getName());
                if (e.isDirectory()) {
                    //noinspection ResultOfMethodCallIgnored
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null) {
                        //noinspection ResultOfMethodCallIgnored
                        parent.mkdirs();
                    }
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        int r;
                        while ((r = zis.read(buf)) > 0) {
                            fos.write(buf, 0, r);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void copyUriToFile(ContentResolver resolver, Uri uri, File dest) throws Exception {
        try (InputStream in = resolver.openInputStream(uri);
             FileOutputStream out = new FileOutputStream(dest)) {
            if (in == null) {
                throw new IllegalStateException("openInputStream");
            }
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) {
                out.write(buf, 0, r);
            }
        }
        if (!dest.exists() || dest.length() == 0) {
            throw new IllegalStateException(LocaleController.getString(R.string.LitegramSessionImportEmptyFile));
        }
    }

    private static String queryDisplayName(ContentResolver resolver, Uri uri) {
        if (uri == null) {
            return null;
        }
        try (Cursor c = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && !c.isNull(i)) {
                    String name = c.getString(i);
                    if (name != null && !name.isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        if (last != null) {
            int slash = last.lastIndexOf('/');
            if (slash >= 0 && slash < last.length() - 1) {
                last = last.substring(slash + 1);
            }
            if (!last.isEmpty() && !last.contains(":")) {
                return last;
            }
        }
        return null;
    }

    /** SQLite database file signature (Telethon / Pyrogram .session). */
    private static boolean isSqliteDatabaseFile(File f) {
        if (f == null || !f.isFile() || f.length() < 16) {
            return false;
        }
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] h = new byte[16];
            if (in.read(h) != 16) {
                return false;
            }
            return h[0] == 'S' && h[1] == 'Q' && h[2] == 'L' && h[3] == 'i'
                    && h[4] == 't' && h[5] == 'e' && h[6] == ' ' && h[15] == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** ZIP (экспорт Litegram или любой архив с .session). */
    private static boolean isZipFile(File f) {
        if (f == null || !f.isFile() || f.length() < 4) {
            return false;
        }
        String name = f.getName().toLowerCase();
        if (name.endsWith(".zip")) {
            return true;
        }
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] h = new byte[4];
            if (in.read(h) != 4) {
                return false;
            }
            if (h[0] != 0x50 || h[1] != 0x4B) {
                return false;
            }
            return h[2] == 0x03 || h[2] == 0x05 || h[2] == 0x07;
        } catch (Exception e) {
            return false;
        }
    }

    private static String readWholeFile(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int o = 0;
            while (o < b.length) {
                int r = in.read(b, o, b.length - o);
                if (r <= 0) break;
                o += r;
            }
            return new String(b, StandardCharsets.UTF_8);
        }
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] ch = f.listFiles();
            if (ch != null) {
                for (File c : ch) {
                    deleteRecursive(c);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
