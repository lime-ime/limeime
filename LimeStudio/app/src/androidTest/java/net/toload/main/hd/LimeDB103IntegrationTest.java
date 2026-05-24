/*
 * Integration tests for the DB 104 seed, upgrade, restore, repair, and emoji
 * refresh paths.
 */
package net.toload.main.hd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import net.toload.main.hd.global.LIME;
import net.toload.main.hd.limedb.LimeDB;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RunWith(AndroidJUnit4.class)
public class LimeDB103IntegrationTest {

    private Context appContext;
    private File appDb;
    private File appDbWal;
    private File appDbShm;
    private File appDbJournal;
    private File originalDbBackup;
    private boolean hadOriginalDb;

    @Before
    public void setUp() throws Exception {
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        appDb = appContext.getDatabasePath(LIME.DATABASE_NAME);
        appDbWal = new File(appDb.getPath() + "-wal");
        appDbShm = new File(appDb.getPath() + "-shm");
        appDbJournal = appContext.getDatabasePath(LIME.DATABASE_JOURNAL);

        closeCurrentDatabase();
        hadOriginalDb = appDb.exists();
        if (hadOriginalDb) {
            originalDbBackup = new File(appContext.getCacheDir(), "lime_db_103_original_backup.db");
            copyFile(appDb, originalDbBackup);
        }
        deleteAppDatabaseFiles();
    }

    @After
    public void tearDown() throws Exception {
        closeCurrentDatabase();
        deleteAppDatabaseFiles();
        if (hadOriginalDb && originalDbBackup != null && originalDbBackup.exists()) {
            copyFile(originalDbBackup, appDb);
            originalDbBackup.delete();
        }
    }

    @Test
    public void freshInstallCopies103SeedAndRefreshesEmojiData() throws Exception {
        LimeDB db = new LimeDB(appContext);
        db.close();

        assertEquals(104, queryUserVersion());
        assertTrue("bundled lime.db must keep core IM rows", queryInt("SELECT COUNT(*) FROM im WHERE title = ?", "name") > 0);
        assertCj4SchemaExists();
        assertEmojiSchemaExists();
        assertEmojiDataLoaded();
    }

    @Test
    public void openingVersion102DatabaseAddsEmojiSchemaAndData() throws Exception {
        replaceAppDatabaseWith(createSeedVariant("lime_102_no_emoji.db", 102, true, false));

        LimeDB db = new LimeDB(appContext);
        db.close();

        assertEquals(104, queryUserVersion());
        assertCj4SchemaExists();
        assertEmojiSchemaExists();
        assertEmojiDataLoaded();
    }

    @Test
    public void openingVersion103DatabaseRepairsMissingEmojiSchema() throws Exception {
        replaceAppDatabaseWith(createSeedVariant("lime_103_no_emoji.db", 103, true, false));

        LimeDB db = new LimeDB(appContext);
        db.close();

        assertEquals(104, queryUserVersion());
        assertCj4SchemaExists();
        assertEmojiSchemaExists();
        assertEmojiDataLoaded();
    }

    @Test
    public void openingDatabaseRemovesStaleCj4KeyboardRow() throws Exception {
        File dbFile = createSeedVariant("lime_104_stale_cj4_keyboard.db", 104, false, false);
        SQLiteDatabase writable = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
        try {
            writable.execSQL(
                    "INSERT OR REPLACE INTO keyboard " +
                            "(code, name, desc, type, image, imkb, imshiftkb, engkb, engshiftkb, " +
                            "symbolkb, symbolshiftkb, defaultkb, defaultshiftkb, extendedkb, extendedshiftkb, disable) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new Object[]{
                            LIME.DB_TABLE_CJ4, "四碼倉頡", "四碼倉頡輸入法鍵盤", "phone", "cj_keyboard_preview",
                            "lime_cj", "lime_cj_shift", "lime", "lime_shift", "symbols", "symbols_shift",
                            "", "", "lime_cj_number", "lime_cj_number_shift", "false"
                    });
        } finally {
            writable.close();
        }
        replaceAppDatabaseWith(dbFile);

        LimeDB db = new LimeDB(appContext);
        db.ensureCurrentDatabase();
        db.close();

        assertCj4SchemaExists();
    }

    @Test
    public void emojiRefreshPreservesValidUserUsageAndPrunesInvalidUsage() throws Exception {
        LimeDB db = new LimeDB(appContext);
        db.close();
        String emoji = queryString("SELECT value FROM emoji_data LIMIT 1");

        SQLiteDatabase writable = SQLiteDatabase.openDatabase(appDb.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
        try {
            writable.execSQL("UPDATE im SET desc = ? WHERE code = ?", new Object[]{"0.0", "emoji"});
            writable.execSQL(
                    "INSERT OR REPLACE INTO emoji_user(value, use_count, last_used) VALUES(?, ?, ?)",
                    new Object[]{emoji, 7, 1000});
            writable.execSQL(
                    "INSERT OR REPLACE INTO emoji_user(value, use_count, last_used) VALUES(?, ?, ?)",
                    new Object[]{"not-an-emoji", 3, 1000});
        } finally {
            writable.close();
        }

        LimeDB reopened = new LimeDB(appContext);
        reopened.close();

        assertEquals(7, queryInt("SELECT use_count FROM emoji_user WHERE value = ?", emoji));
        assertEquals(0, queryInt("SELECT COUNT(*) FROM emoji_user WHERE value = ?", "not-an-emoji"));
        assertEmojiDataLoaded();
    }

    @Test
    public void restoreOldBackupRunsUpgradeRepairAndEmojiRefresh() throws Exception {
        File oldDb = createSeedVariant("lime_restore_102_no_emoji.db", 102, true, false);
        File restoreZip = new File(appContext.getCacheDir(), "lime_restore_102_no_emoji.zip");
        createDatabaseRestoreZip(oldDb, restoreZip);

        DBServer.getInstance(appContext).restoreDatabase(restoreZip.getPath());

        assertEquals(104, queryUserVersion());
        assertCj4SchemaExists();
        assertEmojiSchemaExists();
        assertEmojiDataLoaded();
    }

    @Test
    public void restoreBareLimeDbBackupMovesDatabaseIntoAndroidDatabaseFolder() throws Exception {
        File oldDb = createSeedVariant("lime_restore_bare_102_no_emoji.db", 102, true, false);
        File restoreZip = new File(appContext.getCacheDir(), "lime_restore_bare_102_no_emoji.zip");
        createDatabaseRestoreZip(oldDb, restoreZip, LIME.DATABASE_NAME);

        DBServer.getInstance(appContext).restoreDatabase(restoreZip.getPath());

        assertTrue("restored DB should exist in Android databases folder", appDb.exists());
        assertEquals(104, queryUserVersion());
        assertCj4SchemaExists();
        assertEmojiSchemaExists();
        assertEmojiDataLoaded();
    }

    @Test
    public void factoryResetRestores103SeedAndEmojiData() throws Exception {
        replaceAppDatabaseWith(createSeedVariant("lime_factory_103_no_emoji.db", 103, true, false));

        LimeDB db = new LimeDB(appContext);
        db.restoredToDefault();
        db.close();

        assertEquals(104, queryUserVersion());
        assertTrue("factory reset must restore core IM rows", queryInt("SELECT COUNT(*) FROM im WHERE title = ?", "name") > 0);
        assertCj4SchemaExists();
        assertEmojiSchemaExists();
        assertEmojiDataLoaded();
    }

    @Test
    public void secondOpenDoesNotDuplicateEmojiImRows() throws Exception {
        LimeDB db = new LimeDB(appContext);
        db.close();
        int emojiImRowsAfterFirstOpen = queryInt("SELECT COUNT(*) FROM im WHERE code = ?", "emoji");
        int emojiDataRowsAfterFirstOpen = queryInt("SELECT COUNT(*) FROM emoji_data");

        LimeDB reopened = new LimeDB(appContext);
        reopened.close();

        assertEquals(emojiImRowsAfterFirstOpen, queryInt("SELECT COUNT(*) FROM im WHERE code = ?", "emoji"));
        assertEquals(emojiDataRowsAfterFirstOpen, queryInt("SELECT COUNT(*) FROM emoji_data"));
    }

    private void assertEmojiSchemaExists() {
        assertEquals(1, queryInt("SELECT COUNT(*) FROM sqlite_master WHERE name = ?", "emoji_data"));
        assertEquals(1, queryInt("SELECT COUNT(*) FROM sqlite_master WHERE name = ?", "emoji_user"));
        assertEquals(1, queryInt("SELECT COUNT(*) FROM sqlite_master WHERE name = ?", "emoji_fts"));
    }

    private void assertEmojiDataLoaded() {
        assertTrue("emoji.db payload must be copied into lime.db", queryInt("SELECT COUNT(*) FROM emoji_data") > 0);
        assertTrue("emoji IM rows must be rebuilt from emoji data", queryInt("SELECT COUNT(*) FROM im WHERE code = ?", "emoji") > 0);
    }

    private void assertCj4SchemaExists() {
        assertEquals(1, queryInt("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?", LIME.DB_TABLE_CJ4));
        assertEquals(1, queryInt("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?", "cj4_idx_code"));
        assertEquals(0, queryInt("SELECT COUNT(*) FROM keyboard WHERE code = ?", LIME.DB_TABLE_CJ4));
        assertEquals(1, queryInt("SELECT COUNT(*) FROM keyboard WHERE code = ? AND imkb = ?",
                LIME.DB_TABLE_CJ, "lime_cj"));
    }

    private File createSeedVariant(String name, int userVersion, boolean dropEmojiSchema, boolean forceOldEmojiVersion)
            throws Exception {
        File dbFile = new File(appContext.getCacheDir(), name);
        copyRawResourceToFile(R.raw.lime, dbFile);

        SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
        try {
            if (dropEmojiSchema) {
                db.execSQL("DROP TABLE IF EXISTS emoji_fts");
                db.execSQL("DROP TABLE IF EXISTS emoji_user");
                db.execSQL("DROP TABLE IF EXISTS emoji_data");
                db.execSQL("DELETE FROM im WHERE code = ?", new Object[]{"emoji"});
            }
            if (forceOldEmojiVersion) {
                db.execSQL("UPDATE im SET desc = ? WHERE code = ?", new Object[]{"0.0", "emoji"});
            }
            db.setVersion(userVersion);
        } finally {
            db.close();
        }
        return dbFile;
    }

    private void replaceAppDatabaseWith(File source) throws Exception {
        closeCurrentDatabase();
        deleteAppDatabaseFiles();
        copyFile(source, appDb);
    }

    private void closeCurrentDatabase() {
        try {
            LimeDB db = new LimeDB(appContext);
            db.close();
        } catch (Exception ignored) {
            // Best-effort close before replacing the database file under test.
        }
    }

    private void deleteAppDatabaseFiles() {
        appDb.delete();
        appDbWal.delete();
        appDbShm.delete();
        appDbJournal.delete();
    }

    private void copyRawResourceToFile(int rawResourceId, File target) throws IOException {
        InputStream input = appContext.getResources().openRawResource(rawResourceId);
        try {
            OutputStream output = new FileOutputStream(target);
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }
    }

    private void copyFile(File source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        InputStream input = new FileInputStream(source);
        try {
            OutputStream output = new FileOutputStream(target);
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }
    }

    private void createDatabaseRestoreZip(File dbFile, File zipFile) throws IOException {
        createDatabaseRestoreZip(dbFile, zipFile, "databases/" + LIME.DATABASE_NAME);
    }

    private void createDatabaseRestoreZip(File dbFile, File zipFile, String entryName) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile));
        try {
            zip.putNextEntry(new ZipEntry(entryName));
            InputStream input = new FileInputStream(dbFile);
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    zip.write(buffer, 0, read);
                }
            } finally {
                input.close();
            }
            zip.closeEntry();
        } finally {
            zip.close();
        }
    }

    private int queryUserVersion() {
        return queryInt("PRAGMA user_version");
    }

    private int queryInt(String sql, String... args) {
        Cursor cursor = null;
        SQLiteDatabase db = SQLiteDatabase.openDatabase(appDb.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            cursor = db.rawQuery(sql, args);
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
    }

    private String queryString(String sql, String... args) {
        Cursor cursor = null;
        SQLiteDatabase db = SQLiteDatabase.openDatabase(appDb.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            cursor = db.rawQuery(sql, args);
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
    }
}
