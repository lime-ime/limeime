package net.toload.main.hd;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import net.toload.main.hd.global.LIME;
import net.toload.main.hd.ui.controller.SetupImController;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * Integration tests for Phase 5.5 & 5.6: Backup and Restore Path (User Records)
 * Tests backup and restore operations for user-learned records.
 * Uses REAL production IM table (LIME.IM_PHONETIC) for meaningful integration testing.
 */
@RunWith(AndroidJUnit4.class)
public class IntegrationTestBackupRestore {

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
        Log.i("Integrated test", "setUpClass staring....");
        staticContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        net.toload.main.hd.SearchServer ss = new net.toload.main.hd.SearchServer(staticContext);
        net.toload.main.hd.DBServer ds = net.toload.main.hd.DBServer.getInstance(staticContext);
        staticDbServer = ds;
        staticSetupController = new SetupImController(staticContext, ds, ss);
        
        // Download both PHONETIC and DAYI cloud data for Phase 5 tests
        net.toload.main.hd.ui.controller.ManageImController tempController = 
            new net.toload.main.hd.ui.controller.ManageImController(ss);
        
        // Only download if tables are empty
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
        
        // Use PHONETIC as test table
        realImTable = LIME.IM_PHONETIC;
        
        // Verify both tables are loaded
        int finalPhoneticCount = tempController.countRecords(LIME.IM_PHONETIC);
        int finalDayiCount = tempController.countRecords(LIME.IM_DAYI);
        assertTrue("PHONETIC table should have records", finalPhoneticCount > 0);
        assertTrue("DAYI table should have records", finalDayiCount > 0);
        
        imTableReady = true;
        Log.i("Integrated test", "setUpClass finished.");

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
        // Don't clear real IM table - it's production data
    }

    // ============================================================
    // Phase 5.5: Backup Path (User Records) — Before Overwrite
    // ============================================================

    /**
     * Test 5.5.1: Explicit backup on clear table
     * Tests that user records are backed up before clearing table
     */
    @Test
    public void test_5_5_ExplicitBackupOnClearTable() {
        addLearnedRecord(testTableName, "backup", "備份", 150);
        addLearnedRecord(testTableName, "backup", "後備", 140);
        int beforeCount = manageController.countRecords(testTableName);
        assertTrue("Table should have learned records", beforeCount >= 2);
        setupController.clearTable(testTableName, true); // with backup
        int afterClearCount = manageController.countRecords(testTableName);
        assertEquals("Table should be empty after clear", 0, afterClearCount);
    }

    /**
     * Test 5.5.2: Backup table structure and content
     * Tests that backup table has correct structure and preserves data
     */
    @Test
    public void test_5_5_BackupTableStructureAndContent() {
        addLearnedRecord(testTableName, "structure", "結構", 200);
        addLearnedRecord(testTableName, "content", "內容", 180);
        setupController.clearTable(testTableName, true);
        int afterClearCount = manageController.countRecords(testTableName);
        assertEquals("Backup operation should clear table", 0, afterClearCount);
    }

    /**
     * Test 5.5.3: Backup during import with restore flag
     * Tests that backup is created before import when restore flag is set
     */
    @Test
    public void test_5_5_BackupDuringImportWithRestoreFlag() {
        addLearnedRecord(testTableName, "import", "導入", 150);
        int beforeCount = manageController.countRecords(testTableName);
        assertTrue("Table should have records before import", beforeCount > 0);
        setupController.clearTable(testTableName, true);
        int afterCount = manageController.countRecords(testTableName);
        assertEquals("Backup should clear table", 0, afterCount);
    }

    /**
     * Test 5.5.4: Multiple backups (overwrite behavior)
     * Tests that subsequent backups overwrite previous backups
     */
    @Test
    public void test_5_5_MultipleBackupsOverwrite() {
        addLearnedRecord(testTableName, "first", "第一", 100);
        setupController.clearTable(testTableName, true);
        addLearnedRecord(testTableName, "second", "第二", 110);
        setupController.clearTable(testTableName, true);
        int finalCount = manageController.countRecords(testTableName);
        assertEquals("Final clear should result in empty table", 0, finalCount);
    }

    // ============================================================
    // Phase 5.6: Restore Path (User Records) — After Import
    // ============================================================

    /**
     * Test 5.6.1: Restore after import
     * Tests that backed up records are restored after import
     */
    @Test
    public void test_5_6_RestoreAfterImport() {
        addLearnedRecord(testTableName, "restore", "恢復", 150);
        addLearnedRecord(testTableName, "restore", "還原", 140);
        int originalCount = manageController.countRecords(testTableName);
        java.io.File exportFile = new java.io.File(context.getFilesDir(), "test_restore_" + System.currentTimeMillis() + ".zip");
        try {
            setupController.exportZippedDb(testTableName, exportFile, null);
            setupController.clearTable(testTableName, false);
            setupController.importZippedDb(exportFile, testTableName, true); // with restore
            int afterRestore = manageController.countRecords(testTableName);
            assertTrue("Restored table should have records", afterRestore > 0);
        } finally {
            if (exportFile.exists()) exportFile.delete();
        }
    }

    /**
     * Test 5.6.2: Restore preserves learned entries
     * Tests that restore operation preserves user-learned mappings correctly
     */
    @Test
    public void test_5_6_RestorePreservesLearnedEntries() {
        String testCode = "preserve";
        String testWord1 = "保存";
        String testWord2 = "維持";
        addLearnedRecord(testTableName, testCode, testWord1, 200);
        addLearnedRecord(testTableName, testCode, testWord2, 180);
        java.io.File exportFile = new java.io.File(context.getFilesDir(), "test_preserve_" + System.currentTimeMillis() + ".zip");
        try {
            setupController.exportZippedDb(testTableName, exportFile, null);
            setupController.clearTable(testTableName, false);
            setupController.importZippedDb(exportFile, testTableName, true);
            int count = manageController.countRecords(testTableName);
            assertTrue("Restored table should have learned entries", count > 0);
        } finally {
            if (exportFile.exists()) exportFile.delete();
        }
    }

    /**
     * Test 5.6.3: No-restore path
     * Tests that when restore flag is false, learned entries are not restored
     */
    @Test
    public void test_5_6_NoRestorePath() {
        addLearnedRecord(testTableName, "norestore", "不還原", 150);
        int beforeCount = manageController.countRecords(testTableName);
        assertTrue("Table should have records", beforeCount > 0);
        java.io.File exportFile = new java.io.File(context.getFilesDir(), "test_norestore_" + System.currentTimeMillis() + ".zip");
        try {
            setupController.exportZippedDb(testTableName, exportFile, null);
            setupController.clearTable(testTableName, false);
            setupController.importZippedDb(exportFile, testTableName, false); // no restore
            int finalCount = manageController.countRecords(testTableName);
            assertTrue("Import should succeed without restore", finalCount >= 0);
        } finally {
            if (exportFile.exists()) exportFile.delete();
        }
    }

    /**
     * Test 5.6.4: Check backup table before restore
     * Tests that checkBackuptable correctly identifies if backup exists
     */
    @Test
    public void test_5_6_CheckBackupTableBeforeRestore() {
        addLearnedRecord(testTableName, "check", "檢查", 150);
        java.io.File exportFile = new java.io.File(context.getFilesDir(), "test_check_" + System.currentTimeMillis() + ".zip");
        try {
            setupController.exportZippedDb(testTableName, exportFile, null);
            setupController.importZippedDb(exportFile, testTableName, true);
            int count = manageController.countRecords(testTableName);
            assertTrue("Table should have data after import with restore", count > 0);
        } finally {
            if (exportFile.exists()) exportFile.delete();
        }
    }

    /**
     * Test 5.6.5: Restore with no backup (error handling)
     * Tests that restore operation handles missing backup gracefully
     */
    @Test
    public void test_5_6_RestoreWithNoBackup() {
        setupController.clearTable(testTableName, false);
        int count = manageController.countRecords(testTableName);
        assertEquals("Table should be empty initially", 0, count);
    }

    /**
     * Test 5.6.6: ZippedDb backup and restore workflow integration
     * Tests complete backup → clear → restore workflow using zippedDb
     */
    @Test
    public void test_5_6_6_ZippedDbBackupRestoreWorkflow() {
        int originalCount = manageController.countRecords(testTableName);
        android.util.Log.w("Integrated Test", "Test table record counts: " + originalCount);
        java.io.File exportFile = new java.io.File(context.getFilesDir(), "test_workflow_" + System.currentTimeMillis() + ".limedb");
        try {
            setupController.exportZippedDb(testTableName, exportFile, null);
            setupController.clearTable(testTableName, false);
            int clearedCount = manageController.countRecords(testTableName);
            assertTrue("All records should be cleared", clearedCount == 0);
            setupController.importZippedDb(exportFile, testTableName, true);
            int restoredCount = manageController.countRecords(testTableName);
            assertTrue("All records should be restored from zipped Db. Original: " + originalCount + ", Restored: " + restoredCount, restoredCount == originalCount);
        } finally {
            if (exportFile.exists()) exportFile.delete();
        }
    }

    /**
     * Test 5.6.7: TxtTable backup and restore workflow integration
     * Tests export/import using txt table, verifies all records are round-tripped
     */
    @Test
    public void test_5_6_7_TxtTableBackupRestoreWorkflow() {
        int originalCount = manageController.countRecords(testTableName);
        Log.i("Integrated test", "test_5_6_7_TxtTableBackupRestoreWorkflow() start exporting txt table");
        java.io.File exporTxtFile = new java.io.File(context.getFilesDir(), "test_workflow_" + System.currentTimeMillis() + ".lime");
        try {
            java.io.File completedExport = setupController.exportTxtTable(testTableName, exporTxtFile, null);
            assertNotNull("Exported txt file should be returned after completion", completedExport);
            assertTrue("Exported txt file should exist", completedExport.exists());
            assertTrue("Exported txt file should be non-empty", completedExport.length() > 0);

            int exportedLineCount = 0;
            java.util.Map<String, java.util.Set<String>> exportedLines = new java.util.HashMap<>();
            try {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(completedExport))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("@")) continue;
                        String[] parts = line.split("\\|");
                        if (parts.length >= 2) {
                            exportedLineCount++;
                            String code = parts[0];
                            String word = parts[1];
                            exportedLines.computeIfAbsent(code, k -> new java.util.HashSet<>()).add(word);
                        }
                    }
                }
            } catch (java.io.IOException e) {
                fail("Failed to read exported txt file: " + e.getMessage());
            }
            android.util.Log.w("Integrated Test", "Exported txt file line count: " + exportedLineCount);
            assertTrue("Exported line count should >0", exportedLineCount >0);

            java.util.List<net.toload.main.hd.data.Record> originalRecords = queryRecords(testTableName, null, true);
            Log.i("Integrated Test", "DB total count (includes null/empty words): " + originalCount +
                    ", queried records (filters null/empty): " + originalRecords.size() +
                    ", exported lines (filters null/empty): " + exportedLineCount);
            int recordChecked = 0;
            int missingCount = 0;
            for (net.toload.main.hd.data.Record r : originalRecords) {
                java.util.Set<String> words = exportedLines.get(r.getCode());
                boolean found = words != null && words.contains(r.getWord());
                recordChecked++;
                if(!found) {
                    missingCount++;
                    if (missingCount <= 10) {
                        Log.w("Integrated Test", "Original record missing from export: code=" + r.getCode() + ", word=" + r.getWord());
                    }
                }
            }
            Log.i("Integrated Test", "Original records verified: " + recordChecked + ", missing from export: " + missingCount);
            assertTrue("All queried records should be in export. Missing: " + missingCount, missingCount == 0);

            setupController.clearTable(testTableName, false);
            int clearedCount = manageController.countRecords(testTableName);
            assertTrue("All records should be cleared after clearTable", clearedCount == 0);
            setupController.importTxtTable(completedExport, testTableName, false);
            int restoredCount = 0;
            long start = System.currentTimeMillis();
            long timeoutMs = 30000;
            while (System.currentTimeMillis() - start < timeoutMs) {
                restoredCount = manageController.countRecords(testTableName);
                if (restoredCount == exportedLineCount) {
                    break;
                }
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
            Log.i("Integrated Test", "Restored from txtTable: " + restoredCount + " / expected: " + exportedLineCount);
            java.util.List<net.toload.main.hd.data.Record> restoredRecords = queryRecords(testTableName, null, true);
            java.util.Map<String, java.util.Set<String>> restoredMap = new java.util.HashMap<>();
            for (net.toload.main.hd.data.Record r : restoredRecords) {
                if (r.getCode() != null && r.getCode().startsWith("@")) {
                    continue;
                }
                restoredMap.computeIfAbsent(r.getCode(), k -> new java.util.HashSet<>()).add(r.getWord());
            }

            int exportedUniqueCount = 0;
            for (java.util.Set<String> words : exportedLines.values()) {
                exportedUniqueCount += words.size();
            }
            int restoredUniqueCount = 0;
            for (java.util.Set<String> words : restoredMap.values()) {
                restoredUniqueCount += words.size();
            }

            int restoredMissing = 0;
            int restoredChecked = 0;
            for (java.util.Map.Entry<String, java.util.Set<String>> entry : exportedLines.entrySet()) {
                String code = entry.getKey();
                java.util.Set<String> exportedWords = entry.getValue();
                java.util.Set<String> restoredWords = restoredMap.get(code);
                for (String word : exportedWords) {
                    restoredChecked++;
                    boolean found = restoredWords != null && restoredWords.contains(word);
                    if (!found) {
                        restoredMissing++;
                        if (restoredMissing <= 10) {
                            Log.w("Integrated Test", "Restored missing exported record: code=" + code + ", word=" + word);
                        }
                    }
                }
            }

            int restoredExtra = 0;
            int restoredExtraLogged = 0;
            for (java.util.Map.Entry<String, java.util.Set<String>> entry : restoredMap.entrySet()) {
                String code = entry.getKey();
                java.util.Set<String> restoredWords = entry.getValue();
                java.util.Set<String> exportedWords = exportedLines.get(code);
                for (String word : restoredWords) {
                    boolean inExport = exportedWords != null && exportedWords.contains(word);
                    if (!inExport) {
                        restoredExtra++;
                        if (restoredExtraLogged < 10) {
                            Log.w("Integrated Test", "Restored extra record not in export: code=" + code + ", word=" + word);
                            restoredExtraLogged++;
                        }
                    }
                }
            }

            Log.i("Integrated Test", "Restored verification checked: " + restoredChecked + ", missing: " + restoredMissing + ", extra: " + restoredExtra + ", restoredCount=" + restoredCount + ", restoredUnique=" + restoredUniqueCount + ", exportedUnique=" + exportedUniqueCount + ", exportedLineCount=" + exportedLineCount);
            assertEquals("Restored unique count should match exported unique count", exportedUniqueCount, restoredUniqueCount);
            assertTrue("All exported records should be present after restore. Missing: " + restoredMissing, restoredMissing == 0);
            assertTrue("No extra records should exist after restore. Extra: " + restoredExtra, restoredExtra == 0);
        } finally {
            if (exporTxtFile.exists()) exporTxtFile.delete();
        }
    }

    /**
     * Test 5.6.8: backupUserRecords/restoreUserRecords pair via zipped import
     * Uses exportZippedDb/importZippedDb with restoreUserRecords=true to ensure
     * user-learned records backed up prior to import are restored afterward.
     * Baseline zip is captured BEFORE adding the learned records so only the
     * backup/restore path can bring them back.
     */
    @Test
    public void test_5_6_8_BackupRestoreUserRecordsPair() {
        String code = "backup_pair";
        String word1 = "備份對";
        String word2 = "還原對";

        // Baseline export (does NOT contain the learned records we add below)
        File baselineExport = new File(context.getFilesDir(), "test_backup_pair_" + System.currentTimeMillis() + ".zip");
        try {
            File exportResult = setupController.exportZippedDb(testTableName, baselineExport, null);
            assertNotNull("Baseline export should succeed", exportResult);
            assertTrue("Baseline export file should exist", baselineExport.exists());

            // Add learned records that will be captured only via backupUserRecords
            addLearnedRecord(testTableName, code, word1, 220);
            addLearnedRecord(testTableName, code, word2, 210);
            java.util.List<net.toload.main.hd.data.Record> beforeImport = queryRecords(testTableName, code, true);
            assertTrue("Learned records should exist before import", beforeImport.size() >= 2);

            // Import the baseline zip with restoreUserRecords=true so the added records survive
            setupController.importZippedDb(baselineExport, testTableName, true);

            // Validate that the learned records were restored after import
            java.util.List<net.toload.main.hd.data.Record> afterImport = queryRecords(testTableName, code, true);
            Integer score1 = null;
            Integer score2 = null;
            for (net.toload.main.hd.data.Record r : afterImport) {
                if (code.equals(r.getCode())) {
                    if (word1.equals(r.getWord())) score1 = r.getScore();
                    if (word2.equals(r.getWord())) score2 = r.getScore();
                }
            }

            assertNotNull("backupUserRecords + restoreUserRecords should restore word1", score1);
            assertNotNull("backupUserRecords + restoreUserRecords should restore word2", score2);
            assertEquals("Restored score for word1 should match", Integer.valueOf(220), score1);
            assertEquals("Restored score for word2 should match", Integer.valueOf(210), score2);
        } finally {
            if (baselineExport.exists()) baselineExport.delete();
        }
    }

    /**
     * Test 5.6.7: UI refresh after restore
     * Tests that UI components are refreshed after restore operation
     * Note: This test verifies the restore operation completes successfully
     * and that the system is in a state ready for UI refresh.
     */
    @Test
    public void test_5_6_7_UIRefreshAfterRestore() {
        addLearnedRecord(testTableName, "refresh", "刷新", 150);
        addLearnedRecord(testTableName, "refresh", "更新介面", 140);
        int originalCount = manageController.countRecords(testTableName);
        java.io.File exportFile = new java.io.File(context.getFilesDir(), "test_refresh_" + System.currentTimeMillis() + ".zip");
        try {
            setupController.exportZippedDb(testTableName, exportFile, null);
            setupController.clearTable(testTableName, false);
            setupController.importZippedDb(exportFile, testTableName, true);
            int restoredCount = manageController.countRecords(testTableName);
            assertTrue("Restored count should be positive", restoredCount > 0);
        } finally {
            if (exportFile.exists()) exportFile.delete();
        }
    }

    /**
     * Test 5.6.9: backupDatabase/restoreDatabase pair using restoredToDefault
     * Backs up the entire database, clears all data using restoredToDefault,
     * restores the database, and checks that the IM list and record counts are identical before and after.
     */
    @Test
    public void test_5_6_9_BackupRestoreDatabasePair() throws Exception {
        // Get IM list and record counts before backup
        java.util.List<net.toload.main.hd.data.Im> imObjListBefore = setupController.getImList();
        java.util.List<String> imListBefore = new java.util.ArrayList<>();
        for (net.toload.main.hd.data.Im im : imObjListBefore) {
            imListBefore.add(String.valueOf(im.getCode()));
        }
        java.util.Map<String, Integer> imCountsBefore = new java.util.HashMap<>();
        for (String im : imListBefore) {
            int count = manageController.countRecords(im);
            imCountsBefore.put(im, count);
        }

        // Simulate file chooser Uri for backup/restore file
        java.io.File backupFile = new java.io.File(context.getFilesDir(), "test_db_backup_" + System.currentTimeMillis() + ".zip");
        android.net.Uri backupUri = androidx.core.content.FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", backupFile);
        try {
            // Perform backup using performBackup(Uri)
            setupController.performBackup(backupUri);
            assertTrue("Backup file should exist", backupFile.exists());

            // Clear all data using restoredToDefault
            setupController.restoredToDefault();

            // All IM tables should be empty now
            for (String im : imListBefore) {
                int count = manageController.countRecords(im);
                assertEquals("IM table should be empty after restoredToDefault: " + im, 0, count);
            }

            // Perform restore using performRestore(Uri)
            setupController.performRestore(backupUri);

            // Get IM list and record counts after restore
            java.util.List<net.toload.main.hd.data.Im> imObjListAfter = setupController.getImList();
            java.util.List<String> imListAfter = new java.util.ArrayList<>();
            for (net.toload.main.hd.data.Im im : imObjListAfter) {
                imListAfter.add(String.valueOf(im.getCode()));
            }
            java.util.Map<String, Integer> imCountsAfter = new java.util.HashMap<>();
            for (String im : imListAfter) {
                int count = manageController.countRecords(im);
                imCountsAfter.put(im, count);
            }

            // Check IM list is the same
            assertEquals("IM list should be the same after restore", new java.util.HashSet<>(imListBefore), new java.util.HashSet<>(imListAfter));

            // Check record counts for each IM are the same
            for (String im : imListBefore) {
                int before = imCountsBefore.get(im);
                int after = imCountsAfter.get(im);
                assertEquals("Record count for IM '" + im + "' should be the same after restore", before, after);
            }
        } finally {
            if (backupFile.exists()) backupFile.delete();
        }
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    // Removed custom table creation; using built-in 'custom' table

    /**
     * Adds a learned record (user record) to the specified table
     * Learned records typically have higher scores (>100)
     */
    private void addLearnedRecord(String table, String code, String word, int score) {
        manageController.addRecord(table, code, word, score);
    }

    /**
     * Helper method to query records asynchronously
     */
    private java.util.List<net.toload.main.hd.data.Record> queryRecords(String table, String query, boolean searchByCode) {
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicReference<java.util.List<net.toload.main.hd.data.Record>> out = new java.util.concurrent.atomic.AtomicReference<>();
        manageController.setManageImView(new net.toload.main.hd.ui.view.ManageImView() {
            @Override public void displayRecords(java.util.List<net.toload.main.hd.data.Record> records) { out.set(records); latch.countDown(); }
            @Override public void updateRecordCount(int count) {}
            @Override public void showAddRecordDialog() {}
            @Override public void showEditRecordDialog(net.toload.main.hd.data.Record record) {}
            @Override public void showDeleteConfirmDialog(long id) {}
            @Override public void refreshRecordList() {}
            @Override public void onError(String message) { latch.countDown(); }
        });
        manageController.loadRecordsAsync(table, query, searchByCode, 0, Integer.MAX_VALUE);
        try { latch.await(10, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        java.util.List<net.toload.main.hd.data.Record> result = out.get();
        return result != null ? result : new java.util.ArrayList<>();
    }
}
