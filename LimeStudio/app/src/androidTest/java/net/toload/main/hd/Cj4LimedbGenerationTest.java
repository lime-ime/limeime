package net.toload.main.hd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEProgressListener;
import net.toload.main.hd.ui.controller.ManageImController;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class Cj4LimedbGenerationTest {

    private static final String INPUT_FILENAME = "cj4_haha_20260523_162540.lime";
    private static final String OUTPUT_FILENAME = "cj4.limedb";
    private static final int EXPECTED_RECORD_COUNT = 33021;

    @Test
    public void generateCj4LimedbFromPreparedLimeFile() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File externalDir = context.getExternalFilesDir(null);
        assertNotNull("External files directory should be available", externalDir);

        File inputFile = new File(externalDir, INPUT_FILENAME);
        assumeTrue("Push " + INPUT_FILENAME + " to " + externalDir.getAbsolutePath()
                + " before running this local generation test", inputFile.exists());

        SearchServer searchServer = new SearchServer(context);
        DBServer dbServer = DBServer.getInstance(context);
        ManageImController manageController = new ManageImController(searchServer);

        assertTrue("cj4 should be a valid import/export table",
                searchServer.isValidTableName(LIME.DB_TABLE_CJ4));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>("");

        dbServer.importTxtTable(inputFile.getAbsolutePath(), LIME.DB_TABLE_CJ4,
                new LIMEProgressListener() {
                    @Override
                    public void onProgress(long percentageDone, long estimatedRemainingTime, String status) {
                    }

                    @Override
                    public void onStatusUpdate(String status) {
                    }

                    @Override
                    public void onError(int code, String source) {
                        error.set(source != null ? source : "Import failed with code " + code);
                        latch.countDown();
                    }

                    @Override
                    public void onPostExecute(boolean success, String status, int code) {
                        if (!success) {
                            error.set(status != null ? status : "Import failed with code " + code);
                        }
                        latch.countDown();
                    }
                });

        assertTrue("cj4 import should complete within five minutes",
                latch.await(5, TimeUnit.MINUTES));
        assertEquals("cj4 import should not report an error", "", error.get());

        int recordCount = manageController.countRecords(LIME.DB_TABLE_CJ4);
        assertEquals("cj4 imported record count", EXPECTED_RECORD_COUNT, recordCount);
        assertEquals("cj4 should reuse the existing Cangjie keyboard",
                LIME.DATABASE_CLOUD_IM_CJ4_KEYBOARD,
                getImKeyboardCode(context, LIME.DB_TABLE_CJ4));

        File exportFile = new File(externalDir, OUTPUT_FILENAME);
        File result = dbServer.exportZippedDb(LIME.DB_TABLE_CJ4, exportFile, null);
        assertNotNull("cj4 limedb export should return a file", result);
        assertTrue("cj4 limedb export should exist", exportFile.exists());
        assertTrue("cj4 limedb export should not be empty", exportFile.length() > 1000);

        dbServer.importZippedDb(exportFile, LIME.DB_TABLE_CJ4);
        int roundTripCount = manageController.countRecords(LIME.DB_TABLE_CJ4);
        assertEquals("cj4 limedb round-trip record count", EXPECTED_RECORD_COUNT, roundTripCount);
        assertEquals("cj4 limedb should preserve the Cangjie keyboard assignment",
                LIME.DATABASE_CLOUD_IM_CJ4_KEYBOARD,
                getImKeyboardCode(context, LIME.DB_TABLE_CJ4));
    }

    private String getImKeyboardCode(Context context, String tableName) {
        File dbFile = context.getDatabasePath(LIME.DATABASE_NAME);
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(
                dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor cursor = db.rawQuery(
                     "SELECT keyboard FROM im WHERE code=? AND title='keyboard' LIMIT 1",
                     new String[]{tableName})) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        }
        return "";
    }
}
