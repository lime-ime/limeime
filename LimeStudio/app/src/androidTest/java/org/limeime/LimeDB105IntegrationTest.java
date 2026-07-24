/*
 * Integration tests for the DB 105 seed, upgrade, restore, repair, and emoji
 * refresh paths.
 */
package org.limeime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.limeime.global.LIME;
import org.limeime.limedb.LimeDB;

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
public class LimeDB105IntegrationTest {

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
            originalDbBackup = new File(appContext.getCacheDir(), "lime_db_105_original_backup.db");
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
    public void freshInstallCopies105SeedAndRefreshesEmojiData() throws Exception {
        LimeDB db = new LimeDB(appContext);
        db.close();

        assertEquals(105, queryUserVersion());
        assertEquals("bundled lime.db is a schema-only IM seed", 0,
                queryInt("SELECT COUNT(*) FROM im WHERE code NOT IN ('emoji', 'dictionary')"));
        assertCj4SchemaExists();
        assertEmojiSchemaExists();
        assertEmojiDataLoaded();
    }

    @Test
    public void openingVersion102DatabaseAddsEmojiSchemaAndData() throws Exception {
        replaceAppDatabaseWith(createSeedVariant("lime_102_no_emoji.db", 102, true, false));

        LimeDB db = new LimeDB(appContext);
        db.close();

        assertEquals(105, queryUserVersion());
        assertCj4SchemaExists();
        assertEmojiSchemaExists();
        assertEmojiDataLoaded();
    }

    @Test
    public void openingVersion103DatabaseRepairsMissingEmojiSchema() throws Exception {
        replaceAppDatabaseWith(createSeedVariant("lime_103_no_emoji.db", 103, true, false));

        LimeDB db = new LimeDB(appContext);
        db.close();

        assertEquals(105, queryUserVersion());
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

        assertEquals(105, queryUserVersion());
        assertCj4SchemaExists();
        assertEmojiSchemaExists();
        assertEmojiDataLoaded();
    }

    @Test
    public void restoreVersion101BackupRunsFullUpgradeAndEmojiRefresh() throws Exception {
        File oldDb = createSeedVariant("lime_restore_101_no_emoji.db", 101, true, false);
        File restoreZip = new File(appContext.getCacheDir(), "lime_restore_101_no_emoji.zip");
        createDatabaseRestoreZip(oldDb, restoreZip);

        DBServer.getInstance(appContext).restoreDatabase(restoreZip.getPath());

        assertEquals(105, queryUserVersion());
        assertCj4SchemaExists();
        assertEmojiSchemaExists();
        assertEmojiDataLoaded();
    }

    @Test
    public void restoreOldBackupWithStaleEmojiFtsSchemaRecreatesEmojiFts() throws Exception {
        File oldDb = createLegacyDbWithStaleEmojiFtsSchema("lime_restore_102_stale_emoji_fts.db", 102);
        File restoreZip = new File(appContext.getCacheDir(), "lime_restore_102_stale_emoji_fts.zip");
        createDatabaseRestoreZip(oldDb, restoreZip);

        DBServer.getInstance(appContext).restoreDatabase(restoreZip.getPath());

        assertEquals(105, queryUserVersion());
        assertCj4SchemaExists();
        assertEmojiSchemaExists();
        assertEmojiDataLoaded();
        String emojiFtsSql = queryString("SELECT sql FROM sqlite_master WHERE name = ?", "emoji_fts").toLowerCase();
        assertTrue("emoji_fts should be recreated as Android platform FTS4",
                emojiFtsSql.contains("using fts4"));
    }

    @Test
    public void restoreBareLimeDbBackupMovesDatabaseIntoAndroidDatabaseFolder() throws Exception {
        File oldDb = createSeedVariant("lime_restore_bare_102_no_emoji.db", 102, true, false);
        File restoreZip = new File(appContext.getCacheDir(), "lime_restore_bare_102_no_emoji.zip");
        createDatabaseRestoreZip(oldDb, restoreZip, LIME.DATABASE_NAME);

        DBServer.getInstance(appContext).restoreDatabase(restoreZip.getPath());

        assertTrue("restored DB should exist in Android databases folder", appDb.exists());
        assertEquals(105, queryUserVersion());
        assertCj4SchemaExists();
        assertEmojiSchemaExists();
        assertEmojiDataLoaded();
    }

    @Test
    public void factoryResetRestores105SeedAndEmojiData() throws Exception {
        replaceAppDatabaseWith(createSeedVariant("lime_factory_103_no_emoji.db", 103, true, false));

        LimeDB db = new LimeDB(appContext);
        db.restoredToDefault();
        db.close();

        assertEquals(105, queryUserVersion());
        assertEquals("factory reset restores the schema-only IM seed", 0,
                queryInt("SELECT COUNT(*) FROM im WHERE code NOT IN ('emoji', 'dictionary')"));
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
        File dbFile = new File(appContext.getFilesDir(), name);
        copyRawResourceToFile(R.raw.lime, dbFile);

        SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
        try {
            if (dropEmojiSchema) {
                // Drop the FTS4 virtual table properly so SQLite removes its shadow
                // tables and their auto-indexes; deleting sqlite_master rows by name
                // pattern misses sqlite_autoindex_* and orphans them -> malformed db.
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

    private File createLegacyDbWithStaleEmojiFtsSchema(String name, int userVersion) {
        File dbFile = new File(appContext.getFilesDir(), name);
        if (dbFile.exists()) {
            dbFile.delete();
        }
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
        try {
            db.execSQL("CREATE TABLE im (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "code TEXT, title TEXT, desc TEXT, keyboard TEXT, disable TEXT, " +
                    "selkey TEXT, endkey TEXT, spacestyle TEXT)");
            db.execSQL("CREATE TABLE keyboard (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "code TEXT, name TEXT, desc TEXT, type TEXT, image TEXT, " +
                    "imkb TEXT, imshiftkb TEXT, engkb TEXT, engshiftkb TEXT, " +
                    "symbolkb TEXT, symbolshiftkb TEXT, defaultkb TEXT, defaultshiftkb TEXT, " +
                    "extendedkb TEXT, extendedshiftkb TEXT, disable TEXT)");
            db.execSQL("INSERT INTO keyboard " +
                    "(code, name, desc, type, image, imkb, imshiftkb, engkb, engshiftkb, " +
                    "symbolkb, symbolshiftkb, defaultkb, defaultshiftkb, extendedkb, extendedshiftkb, disable) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new Object[]{
                            LIME.DB_TABLE_CJ, "倉頡", "倉頡建盤", "phone", "cj_keyboard_preview",
                            "lime_cj", "lime_cj_shift", "lime_abc", "lime_abc_shift",
                            "symbols", "symbols_shift", "lime", "lime_shift", "lime", "lime_shift", ""
                    });
            insertStaleEmojiFtsSchema(db);
            db.setVersion(userVersion);
        } finally {
            db.close();
        }
        return dbFile;
    }

    private void insertStaleEmojiFtsSchema(SQLiteDatabase db) {
        db.execSQL("PRAGMA writable_schema=ON");
        try {
            db.execSQL("INSERT INTO sqlite_master(type, name, tbl_name, rootpage, sql) " +
                    "VALUES('table', 'emoji_fts', 'emoji_fts', 0, " +
                    "'CREATE VIRTUAL TABLE emoji_fts USING fts5(" +
                    "name_en, name_tw, tags_en, tags_tw, " +
                    "content=''emoji_data'', content_rowid=''rowid'', " +
                    "tokenize=''unicode61 remove_diacritics 1'')')");
        } finally {
            db.execSQL("PRAGMA writable_schema=OFF");
        }
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

    // ---- Scored English dictionary (docs/ENG_AUTO_COMPLETION.md) ----

    @Test
    public void freshInstallImportsScoredDictionaryFromPayload() throws Exception {
        LimeDB db = new LimeDB(appContext);
        db.close();

        // user_version unchanged; dictionary is the scored shape (not fts); rows imported;
        // im version row stamped.
        assertEquals(105, queryUserVersion());
        assertEquals("dictionary must be a plain (non-fts) table",
                0, queryInt("SELECT COUNT(*) FROM sqlite_master WHERE name='dictionary' "
                        + "AND sql LIKE '%USING fts%'"));
        assertTrue("scored dictionary must have basescore + score columns",
                dictionaryHasColumn("basescore") && dictionaryHasColumn("score"));
        assertTrue("dictionary rows imported from payload",
                queryInt("SELECT COUNT(*) FROM dictionary") > 0);
        assertEquals("1.0", queryString(
                "SELECT desc FROM im WHERE code='dictionary' AND title='version'"));
    }

    @Test
    public void openingLegacyFtsDictionaryRebuildsScoredTable() throws Exception {
        File fixture = createLegacyFtsDictionaryFixture("lime_legacy_fts_dictionary.db");
        replaceAppDatabaseWith(fixture);

        LimeDB db = new LimeDB(appContext);
        db.close();

        // The legacy fts3 dictionary is dropped and rebuilt as the scored table, populated.
        assertEquals(0, queryInt("SELECT COUNT(*) FROM sqlite_master WHERE name='dictionary' "
                + "AND sql LIKE '%USING fts%'"));
        assertEquals("no fts shadow tables remain", 0,
                queryInt("SELECT COUNT(*) FROM sqlite_master WHERE type='table' "
                        + "AND name LIKE 'dictionary\\_%' ESCAPE '\\'"));
        assertTrue(dictionaryHasColumn("basescore") && dictionaryHasColumn("score"));
        assertTrue("scored dictionary populated after legacy upgrade",
                queryInt("SELECT COUNT(*) FROM dictionary") > 0);
        assertEquals("1.0", queryString(
                "SELECT desc FROM im WHERE code='dictionary' AND title='version'"));
    }

    @Test
    public void secondOpenDoesNotReimportDictionary() throws Exception {
        LimeDB db1 = new LimeDB(appContext);
        db1.close();
        int rowsAfterFirst = queryInt("SELECT COUNT(*) FROM dictionary");
        int imRowsAfterFirst = queryInt(
                "SELECT COUNT(*) FROM im WHERE code='dictionary' AND title='version'");

        LimeDB db2 = new LimeDB(appContext);
        db2.close();

        assertEquals("dictionary rows unchanged on second open",
                rowsAfterFirst, queryInt("SELECT COUNT(*) FROM dictionary"));
        assertEquals("no duplicate im dictionary version row",
                imRowsAfterFirst, queryInt(
                        "SELECT COUNT(*) FROM im WHERE code='dictionary' AND title='version'"));
        assertEquals(1, imRowsAfterFirst);
    }

    @Test
    public void recordEnglishUsageIncrementsScore() throws Exception {
        LimeDB db = new LimeDB(appContext);
        try {
            // pick a word that exists in the payload
            String word = queryString("SELECT word FROM dictionary LIMIT 1");
            int before = queryInt("SELECT score FROM dictionary WHERE word = ?", word);
            db.recordEnglishUsage(word);
            int after = queryInt("SELECT score FROM dictionary WHERE word = ?", word);
            assertEquals(before + 1, after);
            // unknown word is a no-op (UPDATE-only)
            db.recordEnglishUsage("zzqqxx_not_a_word");
        } finally {
            db.close();
        }
    }

    @Test
    public void englishUserScoreTakesPriorityOverBaseScoreLikeChineseIm() throws Exception {
        LimeDB db = new LimeDB(appContext);
        db.close();

        SQLiteDatabase writable = SQLiteDatabase.openDatabase(
                appDb.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
        try {
            writable.execSQL("INSERT OR REPLACE INTO dictionary(word, basescore, score) VALUES(?, ?, ?)",
                    new Object[]{"zzalignlearned", 0, 1});
            writable.execSQL("INSERT OR REPLACE INTO dictionary(word, basescore, score) VALUES(?, ?, ?)",
                    new Object[]{"zzalignpopular", 10000, 0});
        } finally {
            writable.close();
        }

        LimeDB reopened = new LimeDB(appContext);
        try {
            assertEquals("zzalignlearned", reopened.getEnglishSuggestions("zzalign").get(0));
        } finally {
            reopened.close();
        }
    }

    private boolean dictionaryHasColumn(String column) {
        SQLiteDatabase db = SQLiteDatabase.openDatabase(appDb.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        Cursor c = null;
        try {
            c = db.rawQuery("PRAGMA table_info(dictionary)", null);
            int nameIdx = c.getColumnIndex("name");
            while (c.moveToNext()) {
                if (column.equals(c.getString(nameIdx))) return true;
            }
            return false;
        } finally {
            if (c != null) c.close();
            db.close();
        }
    }

    /** A seed copy whose scored dictionary is replaced by a populated legacy fts3 table. */
    private File createLegacyFtsDictionaryFixture(String name) throws Exception {
        File dbFile = new File(appContext.getFilesDir(), name);
        copyRawResourceToFile(R.raw.lime, dbFile);
        SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
        try {
            db.execSQL("DROP TABLE IF EXISTS dictionary");
            db.execSQL("CREATE VIRTUAL TABLE dictionary USING fts3(word)");
            db.execSQL("INSERT INTO dictionary(word) VALUES ('the'),('and'),('salt'),('year')");
            db.execSQL("DELETE FROM im WHERE code='dictionary'");
        } finally {
            db.close();
        }
        return dbFile;
    }
}
