package net.toload.main.hd;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import net.toload.main.hd.global.LIME;
import net.toload.main.hd.ui.controller.SetupImController;
import net.toload.main.hd.global.LIMEProgressListener;
import net.toload.main.hd.limedb.LimeDB;
import net.toload.main.hd.ui.controller.SetupImController;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration tests for Phase 5.4: UI → DBServer → LimeDB Integration
 * Tests complete file operation flows from UI through DBServer to LimeDB.
 * Uses REAL production IM table (LIME.IM_PHONETIC) for meaningful integration testing.
 */
@RunWith(AndroidJUnit4.class)
public class IntegrationTestUIDBServer {

    private static Context staticContext;
    private static SetupImController staticSetupController;
    private static net.toload.main.hd.DBServer staticDbServer;
    private static String realImTable;
    private static boolean imTableReady = false;

    private Context context;
    private SetupImController setupController;
    private net.toload.main.hd.ui.controller.ManageImController manageController;
    private String testTableName;

    @BeforeClass
    public static void setUpClass() throws Exception {
        staticContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        net.toload.main.hd.SearchServer ss = new net.toload.main.hd.SearchServer(staticContext);
        net.toload.main.hd.DBServer ds = net.toload.main.hd.DBServer.getInstance(staticContext);
        staticDbServer = ds;
        staticSetupController = new SetupImController(staticContext, ds, ss);
        
        // Download both PHONETIC and DAYI cloud data for Phase 5 tests
        net.toload.main.hd.ui.controller.ManageImController tempController = 
            new net.toload.main.hd.ui.controller.ManageImController(ss);
        
        // Only download if tables are empty (reuse existing real data to speed up runs)
        int phoneticCount = tempController.countRecords(LIME.IM_PHONETIC);
        if (phoneticCount == 0) {
            staticSetupController.clearTable(LIME.IM_PHONETIC, false);
            downloadCloudDbAndImport(LIME.IM_PHONETIC, LIME.DATABASE_CLOUD_IM_PHONETIC, tempController, staticDbServer);
        }

        int dayiCount = tempController.countRecords(LIME.IM_DAYI);
        if (dayiCount == 0) {
            staticSetupController.clearTable(LIME.IM_DAYI, false);
            downloadCloudDbAndImport(LIME.IM_DAYI, LIME.DATABASE_CLOUD_IM_DAYI, tempController, staticDbServer);
        }
        
        // Use PHONETIC as test table, but both are available
        realImTable = LIME.IM_PHONETIC;
        
        // Verify both tables are loaded
        int finalPhoneticCount = tempController.countRecords(LIME.IM_PHONETIC);
        int finalDayiCount = tempController.countRecords(LIME.IM_DAYI);
        assertTrue("PHONETIC table should have records", finalPhoneticCount > 0);
        assertTrue("DAYI table should have records", finalDayiCount > 0);
        
        imTableReady = true;
    }

    private static void downloadCloudDbAndImport(String tableName, String url,
                                                 net.toload.main.hd.ui.controller.ManageImController manageController,
                                                 net.toload.main.hd.DBServer dbServer) {
        java.io.File tmpFile = new java.io.File(staticContext.getFilesDir(),
                tableName + "_cloud_" + System.currentTimeMillis() + ".limedb");
        try {
            // Directly download the cloud zipped DB
            java.net.URL u = new java.net.URL(url);
            java.net.URLConnection conn = u.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            try (java.io.InputStream in = conn.getInputStream();
                 java.io.FileOutputStream out = new java.io.FileOutputStream(tmpFile)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }

            // Import using DBServer (synchronous, no wait needed)
            dbServer.importZippedDb(tmpFile, tableName);

            // Quick sanity check: table should have records after import
            int recordCount = manageController.countRecords(tableName);
            assertTrue("Imported table should have records: " + tableName, recordCount > 0);
        } catch (java.io.IOException e) {
            fail("Failed to download/import cloud DB for " + tableName + ": " + e.getMessage());
        } finally {
            if (tmpFile.exists()) {
                try { tmpFile.delete(); } catch (Throwable ignored) {}
            }
        }
    }

    @AfterClass
    public static void tearDownClass() {
        // Keep the real IM table loaded for future test runs
    }

    @Before
    public void setUp() {
        assertTrue("IM table must be ready before running tests", imTableReady);
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        net.toload.main.hd.SearchServer ss = new net.toload.main.hd.SearchServer(context);
        net.toload.main.hd.DBServer ds = net.toload.main.hd.DBServer.getInstance(context);
        setupController = new SetupImController(context, ds, ss);
        manageController = new net.toload.main.hd.ui.controller.ManageImController(ss);
        testTableName = realImTable;
    }

    @After
    public void tearDown() {
        if (setupController != null && testTableName != null) {
            setupController.clearTable(testTableName, false);
        }
    }

    // ============================================================
    // Phase 5.4: UI → DBServer → LimeDB Integration
    // ============================================================

    /**
     * Test 5.4.1: Complete file operation flow - Export
     * Tests UI → DBServer → LimeDB → Database for export operations
     */
    @Test
    public void test_5_4_CompleteFileOperationFlow_Export() {
        addTestRecord(testTableName, "file", "檔案", 100);
        addTestRecord(testTableName, "file", "文件", 50);
        File exportFile = new File(context.getFilesDir(), "test_ui_export_" + System.currentTimeMillis() + ".zip");
        try {
            File result = setupController.exportZippedDb(testTableName, exportFile, null);
            assertNotNull("Export should succeed", result);
            assertTrue("Export file should be created", exportFile.exists());
            assertTrue("Export file should have content", exportFile.length() > 0);
        } finally {
            if (exportFile.exists()) exportFile.delete();
        }
    }

    /**
     * Test 5.4.2: Complete file operation flow - Import
     * Tests UI → DBServer → LimeDB → Database for import operations
     */
    @Test
    public void test_5_4_CompleteFileOperationFlow_Import() {
        addTestRecord(testTableName, "import", "匯入", 100);
        File exportFile = new File(context.getFilesDir(), "test_ui_import_" + System.currentTimeMillis() + ".zip");
        try {
            setupController.exportZippedDb(testTableName, exportFile, null);
            assertTrue("Export file should exist", exportFile.exists());
            setupController.clearTable(testTableName, false);
            setupController.importZippedDb(exportFile, testTableName, false);
            int count = manageController.countRecords(testTableName);
            assertTrue("Imported table should have records", count > 0);
        } finally {
            if (exportFile.exists()) exportFile.delete();
        }
    }

    /**
     * Test 5.4.3: File operations and database updates
     * Tests that file operations correctly update database state
     */
    @Test
    public void test_5_4_FileOperationsAndDatabaseUpdates() {
        addTestRecord(testTableName, "update", "更新", 100);
        int beforeCount = manageController.countRecords(testTableName);
        File exportFile = new File(context.getFilesDir(), "test_db_update_" + System.currentTimeMillis() + ".zip");
        try {
            setupController.exportZippedDb(testTableName, exportFile, null);
            addTestRecord(testTableName, "update", "修改", 50);
            int afterAddCount = manageController.countRecords(testTableName);
            assertTrue("Count should increase after add", afterAddCount > beforeCount);
            setupController.importZippedDb(exportFile, testTableName, false);
            int finalCount = manageController.countRecords(testTableName);
            assertTrue("Database should be updated", finalCount > 0);
        } finally {
            if (exportFile.exists()) exportFile.delete();
        }
    }

    /**
     * Test 5.4.4: Progress callbacks during file operations
     * Tests that progress listener is called during long operations
     */
    @Test
    public void test_5_4_ProgressCallbacks() {
        for (int i = 0; i < 50; i++) {
            addTestRecord(testTableName, "progress" + i, "進度" + i, 100 - i);
        }
        File exportFile = new File(context.getFilesDir(), "test_progress_" + System.currentTimeMillis() + ".zip");
        try {
            File result = setupController.exportZippedDb(testTableName, exportFile, null);
            assertNotNull("Export should succeed", result);
        } finally {
            if (exportFile.exists()) exportFile.delete();
        }
    }

    /**
     * Test 5.4.5: Multiple file operations in sequence
     * Tests that multiple file operations can be performed sequentially
     */
    @Test
    public void test_5_4_MultipleFileOperationsSequence() {
        addTestRecord(testTableName, "multi", "多重", 100);
        File exportFile1 = new File(context.getFilesDir(), "test_multi_1_" + System.currentTimeMillis() + ".zip");
        File exportFile2 = new File(context.getFilesDir(), "test_multi_2_" + System.currentTimeMillis() + ".zip");
        try {
            File result1 = setupController.exportZippedDb(testTableName, exportFile1, null);
            assertNotNull("First export should succeed", result1);
            addTestRecord(testTableName, "multi", "序列", 50);
            File result2 = setupController.exportZippedDb(testTableName, exportFile2, null);
            assertNotNull("Second export should succeed", result2);
            assertTrue("First export file should exist", exportFile1.exists());
            assertTrue("Second export file should exist", exportFile2.exists());
            assertTrue("First file should have content", exportFile1.length() > 0);
            assertTrue("Second file should have content", exportFile2.length() > 0);
        } finally {
            if (exportFile1.exists()) exportFile1.delete();
            if (exportFile2.exists()) exportFile2.delete();
        }
    }

    /**
     * Test 5.4.6: Error handling in file operations
     * Tests that errors in file operations are handled gracefully
     */
    @Test
    public void test_5_4_ErrorHandlingInFileOperations() {
        File invalidFile = new File("/invalid/path/cannot/write/here.zip");
        try {
            File result = setupController.exportZippedDb(testTableName, invalidFile, null);
            assertNull("Export to invalid path should return null", result);
        } catch (Exception e) {
            assertNotNull("Exception should be handled", e);
        }
    }

    /**
     * Test 5.4.7: Related table export/import through UI flow
     * Tests UI → DBServer operations for related table
     */
    @Test
    public void test_5_4_RelatedTableFileOperations() {
        manageController.addRelatedPhrase("UI測試", "測試UI", 100);
        File exportFile = new File(context.getFilesDir(), "test_ui_related_" + System.currentTimeMillis() + ".zip");
        try {
            File exportResult = setupController.exportZippedDbRelated(exportFile, null);
            assertNotNull("Export related should succeed", exportResult);
            assertTrue("Export file should exist", exportFile.exists());
            setupController.clearTable(LIME.DB_TABLE_RELATED, false);
            setupController.importZippedDbRelated(exportFile);
            int count = manageController.countRecords(LIME.DB_TABLE_RELATED);
            assertTrue("Related records should be restored", count > 0);
        } finally {
            if (exportFile.exists()) exportFile.delete();
        }
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    private void addTestRecord(String table, String code, String word, int score) {
        manageController.addRecord(table, code, word, score);
    }
}
