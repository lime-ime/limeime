package org.limeime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.limeime.global.LIME;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Verifies the checked-in Tricode archive through Android's production importer. */
@RunWith(AndroidJUnit4.class)
public class TricodeLimedbImportOrderTest {

    @Test(timeout = 120_000)
    public void committedArchiveImportsEveryMappingInSourceIdOrder() throws Exception {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        File archive = new File(appContext.getCacheDir(), "instrumented-tricode.limedb");
        File expectedDb = new File(appContext.getCacheDir(), "instrumented-tricode-expected.db");

        copyAsset(testContext, "tricode.limedb", archive);
        extractOnlyDatabase(archive, expectedDb);

        DBServer dbServer = DBServer.getInstance(appContext);
        SearchServer searchServer = new SearchServer(appContext);
        // This focused test owns the custom table and always leaves it empty.
        searchServer.clearTable(LIME.DB_TABLE_CUSTOM);

        try {
            dbServer.importZippedDb(archive, LIME.DB_TABLE_CUSTOM);
            File installedDb = appContext.getDatabasePath(LIME.DATABASE_NAME);
            assertTrue("Android destination database must exist", installedDb.isFile());

            try (SQLiteDatabase expected = SQLiteDatabase.openDatabase(
                         expectedDb.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                 SQLiteDatabase actual = SQLiteDatabase.openDatabase(
                         installedDb.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                 Cursor expectedRows = expected.rawQuery(
                         "SELECT _id, code, word, score, basescore FROM custom ORDER BY _id ASC",
                         null);
                 Cursor actualRows = actual.rawQuery(
                         "SELECT _id, code, word, score, basescore FROM custom ORDER BY _id ASC",
                         null)) {

                assertEquals("Tricode fixture row count", 15_934, expectedRows.getCount());
                assertEquals("Imported row count must match the archive",
                        expectedRows.getCount(), actualRows.getCount());

                int row = 0;
                while (expectedRows.moveToNext()) {
                    assertTrue("Destination ended before source at row " + row,
                            actualRows.moveToNext());
                    for (int column = 0; column < expectedRows.getColumnCount(); column++) {
                        assertEquals(
                                "Imported column " + column + " differs at source row " + row,
                                expectedRows.getString(column), actualRows.getString(column));
                    }
                    row++;
                }
                assertFalse("Destination contains rows after source row " + row,
                        actualRows.moveToNext());
            }
        } finally {
            searchServer.clearTable(LIME.DB_TABLE_CUSTOM);
            archive.delete();
            expectedDb.delete();
        }
    }

    private static void copyAsset(Context context, String name, File destination) throws Exception {
        try (InputStream input = context.getAssets().open(name);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        }
    }

    private static void extractOnlyDatabase(File archive, File destination) throws Exception {
        int databaseEntries = 0;
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()
                        || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".db")) {
                    continue;
                }
                databaseEntries++;
                try (FileOutputStream output = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                }
            }
        }
        assertEquals(".limedb must contain exactly one SQLite database", 1, databaseEntries);
    }
}
