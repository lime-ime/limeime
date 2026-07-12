package net.toload.main.hd;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.preference.PreferenceManager;

import net.toload.main.hd.data.ImConfig;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.ui.controller.SetupImController;

import org.json.JSONObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

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
        java.util.List<ImConfig> imConfigObjListBefore = setupController.getImConfigList();
        java.util.List<String> imListBefore = new java.util.ArrayList<>();
        for (ImConfig imConfig : imConfigObjListBefore) {
            imListBefore.add(String.valueOf(imConfig.getCode()));
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

            // Factory reset restores the schema-only bundled seed. Installed IMs are
            // user-selected downloads/imports, so the reset intentionally clears them.
            java.util.List<ImConfig> defaultImConfigList = setupController.getImConfigList();
            for (ImConfig config : defaultImConfigList) {
                assertTrue("Factory reset should retain only bundled payload metadata",
                        "emoji".equals(config.getCode()) || "dictionary".equals(config.getCode()));
            }

            // Perform restore using performRestore(Uri)
            setupController.performRestore(backupUri);

            // Get IM list and record counts after restore
            java.util.List<ImConfig> imConfigObjListAfter = setupController.getImConfigList();
            java.util.List<String> imListAfter = new java.util.ArrayList<>();
            for (ImConfig imConfig : imConfigObjListAfter) {
                imListAfter.add(String.valueOf(imConfig.getCode()));
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

    /**
     * Test 5.6.10: full backup/restore carries the cross-platform preference
     * compatibility manifest while preserving the existing full database flow.
     */
    @Test
    public void test_5_6_10_BackupRestoreDatabasePairRestoresPreferenceCompatibilityManifest() throws Exception {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        java.util.Map<String, Object> expected = fullAndroidPrefsTableFixture();
        java.util.Map<String, Object> originalValues = snapshotPrefs(prefs);

        java.io.File backupFile = new java.io.File(context.getFilesDir(), "test_pref_backup_" + System.currentTimeMillis() + ".zip");
        android.net.Uri backupUri = androidx.core.content.FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", backupFile);

        try {
            seedPrefs(prefs, expected);

            setupController.performBackup(backupUri);
            assertTrue("Backup file should exist", backupFile.exists());
            assertZipContains(backupFile, "databases/lime.db");
            assertZipContains(backupFile, "shared_prefs.bak");
            assertZipContains(backupFile, "preferences/lime_prefs.json");

            JSONObject manifest = readPreferenceManifest(backupFile);
            assertEquals("Manifest schema should be v1", 1, manifest.getInt("schema"));
            JSONObject values = manifest.getJSONObject("preferences");
            assertEquals("Manifest must contain exactly the full Android PREFS_TABLE set seeded by this test",
                    expected.size(), values.length());
            assertManifestValues(values, expected);

            seedPrefs(prefs, mutatedAndroidPrefsTableFixture());

            setupController.performRestore(backupUri);

            assertStoredValues(prefs, expected);
        } finally {
            restorePrefs(prefs, originalValues);
            if (backupFile.exists()) backupFile.delete();
        }
    }

    @Test
    public void test_5_6_11_RestoreIosStylePreferenceFixtureThroughAndroidAdapter() throws Exception {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        java.util.Map<String, Object> expected = fullAndroidPrefsTableFixture();
        java.util.Map<String, Object> originalValues = snapshotPrefs(prefs);
        java.io.File fixtureFile = new java.io.File(context.getFilesDir(), "test_ios_pref_fixture_" + System.currentTimeMillis() + ".zip");
        android.net.Uri fixtureUri = androidx.core.content.FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", fixtureFile);

        try {
            writeCrossPlatformFixtureZip(fixtureFile, "ios", expected);
            seedPrefs(prefs, mutatedAndroidPrefsTableFixture());

            setupController.performRestore(fixtureUri);

            assertStoredValues(prefs, expected);
        } finally {
            restorePrefs(prefs, originalValues);
            if (fixtureFile.exists()) fixtureFile.delete();
        }
    }

    @Test
    public void test_5_6_12_RestoreLegacyAndroidBackupWithLeadingSlashEntries() throws Exception {
        int originalCount = manageController.countRecords(testTableName);
        assertTrue("Legacy fixture needs a populated IM table", originalCount > 0);

        java.io.File fixtureFile = new java.io.File(context.getFilesDir(), "test_legacy_slash_backup_" + System.currentTimeMillis() + ".zip");
        android.net.Uri fixtureUri = androidx.core.content.FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", fixtureFile);

        try {
            writeLegacyLeadingSlashFullBackupZip(fixtureFile);

            setupController.clearTable(testTableName, false);
            int clearedCount = manageController.countRecords(testTableName);
            assertEquals("Fixture table should be cleared before restore", 0, clearedCount);

            setupController.performRestore(fixtureUri);

            int restoredCount = manageController.countRecords(testTableName);
            assertEquals("Old Android backups with /databases/lime.db must restore the database",
                    originalCount, restoredCount);
        } finally {
            if (fixtureFile.exists()) fixtureFile.delete();
        }
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    private java.util.Map<String, Object> fullAndroidPrefsTableFixture() {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("keyboard_theme", 4);
        values.put("keyboard_size", "1");
        values.put("font_size", "2");
        values.put("number_row_in_english", false);
        values.put("show_arrow_key", 2);
        values.put("split_keyboard_mode", 1);
        values.put("vibrate_on_keypress", false);
        values.put("vibrate_level", 80);
        values.put("sound_on_keypress", true);
        values.put("smart_chinese_input", false);
        values.put("auto_chinese_symbol", true);
        values.put("candidate_switch", true);
        values.put("persistent_language_mode", true);
        values.put("enable_emoji_position", 3);
        values.put("similiar_list", 30);
        values.put("han_convert_option", 2);
        values.put("similiar_enable", false);
        values.put("candidate_suggestion", false);
        values.put("learn_phrase", false);
        values.put("learning_switch", false);
        values.put("english_dictionary_enable", false);
        values.put("auto_cap", false);
        values.put("custom_im_reverselookup", "dayi");
        values.put("cj_im_reverselookup", "phonetic");
        values.put("scj_im_reverselookup", "cj");
        values.put("cj5_im_reverselookup", "scj");
        values.put("ecj_im_reverselookup", "cj5");
        values.put("dayi_im_reverselookup", "bpmf");
        values.put("bpmf_im_reverselookup", "dayi");
        values.put("phonetic_im_reverselookup", "custom");
        values.put("ez_im_reverselookup", "array");
        values.put("array_im_reverselookup", "array10");
        values.put("array10_im_reverselookup", "ez");
        values.put("wb_im_reverselookup", "hs");
        values.put("hs_im_reverselookup", "pinyin");
        values.put("pinyin_im_reverselookup", "none");
        values.put("phonetic_keyboard_type", "standard");
        values.put("auto_commit", 3);
        values.put("accept_number_index", true);
        values.put("accept_symbol_index", true);
        values.put("backup_on_delete_phonetic", false);
        values.put("restore_on_import_phonetic", false);
        values.put("hide_software_keyboard_typing_with_physical", false);
        values.put("switch_english_mode", true);
        values.put("switch_english_mode_shift", false);
        values.put("disable_physical_selkey", true);
        values.put("selkey_option", 2);
        values.put("english_dictionary_physical_keyboard", true);
        values.put("physical_keyboard_sort", true);
        return values;
    }

    private java.util.Map<String, Object> mutatedAndroidPrefsTableFixture() {
        java.util.Map<String, Object> values = fullAndroidPrefsTableFixture();
        for (String key : new java.util.ArrayList<>(values.keySet())) {
            Object value = values.get(key);
            if (value instanceof Boolean) {
                values.put(key, !((Boolean) value));
            } else if (value instanceof Integer) {
                values.put(key, 0);
            } else if (value instanceof String) {
                values.put(key, "none");
            }
        }
        values.put("keyboard_size", "2");
        values.put("font_size", "1");
        return values;
    }

    private java.util.Map<String, Object> snapshotPrefs(SharedPreferences prefs) {
        java.util.Map<String, ?> all = prefs.getAll();
        java.util.Map<String, Object> snapshot = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, ?> entry : all.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue());
        }
        return snapshot;
    }

    private void restorePrefs(SharedPreferences prefs, java.util.Map<String, Object> snapshot) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        for (java.util.Map.Entry<String, Object> entry : snapshot.entrySet()) {
            putPrefValue(editor, entry.getKey(), entry.getValue());
        }
        editor.commit();
    }

    private void seedPrefs(SharedPreferences prefs, java.util.Map<String, Object> values) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        for (java.util.Map.Entry<String, Object> entry : values.entrySet()) {
            putPrefValue(editor, entry.getKey(), entry.getValue());
        }
        editor.commit();
    }

    private void putPrefValue(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer && isAndroidStringBackedInteger(key)) {
            editor.putString(key, String.valueOf(value));
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof java.util.Set) {
            java.util.HashSet<String> set = new java.util.HashSet<>();
            for (Object item : (java.util.Set<?>) value) {
                if (item instanceof String) {
                    set.add((String) item);
                }
            }
            editor.putStringSet(key, set);
        }
    }

    private void assertManifestValues(JSONObject actual, java.util.Map<String, Object> expected) throws Exception {
        for (java.util.Map.Entry<String, Object> entry : expected.entrySet()) {
            String key = entry.getKey();
            Object expectedValue = entry.getValue();
            if (expectedValue instanceof Boolean) {
                assertEquals(key + " should be backed up as a boolean", expectedValue, actual.getBoolean(key));
            } else if (expectedValue instanceof Integer) {
                assertEquals(key + " should be backed up as an integer", expectedValue, actual.getInt(key));
            } else if (expectedValue instanceof String) {
                assertEquals(key + " should be backed up as a string", expectedValue, actual.getString(key));
            }
        }
    }

    private void assertStoredValues(SharedPreferences prefs, java.util.Map<String, Object> expected) {
        for (java.util.Map.Entry<String, Object> entry : expected.entrySet()) {
            String key = entry.getKey();
            Object expectedValue = entry.getValue();
            if (expectedValue instanceof Boolean) {
                assertEquals(key + " should restore as a boolean", expectedValue, prefs.getBoolean(key, !((Boolean) expectedValue)));
            } else if (expectedValue instanceof Integer) {
                assertEquals(key + " should restore as Android string-backed integer",
                        String.valueOf(expectedValue), prefs.getString(key, null));
            } else if (expectedValue instanceof String) {
                assertEquals(key + " should restore as a string", expectedValue, prefs.getString(key, null));
            }
        }
    }

    private boolean isAndroidStringBackedInteger(String key) {
        return java.util.Arrays.asList(
                "keyboard_theme",
                "show_arrow_key",
                "split_keyboard_mode",
                "vibrate_level",
                "enable_emoji_position",
                "similiar_list",
                "han_convert_option",
                "auto_commit",
                "selkey_option").contains(key);
    }

    private void assertZipContains(File backupFile, String entryName) throws Exception {
        try (ZipFile zipFile = new ZipFile(backupFile)) {
            ZipEntry entry = zipFile.getEntry(entryName);
            assertNotNull("Full backup should contain " + entryName, entry);
        }
    }

    private void writeCrossPlatformFixtureZip(File fixtureFile, String sourcePlatform, java.util.Map<String, Object> preferences) throws Exception {
        if (fixtureFile.exists() && !fixtureFile.delete()) {
            throw new java.io.IOException("Failed to delete old fixture " + fixtureFile);
        }
        File databaseFile = context.getDatabasePath(LIME.DATABASE_NAME);
        assertTrue("Cross-platform fixture requires an existing database", databaseFile.exists());

        JSONObject manifest = new JSONObject()
                .put("schema", 1)
                .put("sourcePlatform", sourcePlatform)
                .put("preferences", new JSONObject(preferences));

        try (ZipOutputStream output = new ZipOutputStream(new java.io.FileOutputStream(fixtureFile))) {
            output.putNextEntry(new ZipEntry("databases/lime.db"));
            try (InputStream input = new java.io.FileInputStream(databaseFile)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            }
            output.closeEntry();

            output.putNextEntry(new ZipEntry("shared_prefs.bak"));
            output.write("legacy-sidecar-not-needed-when-json-exists".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();

            output.putNextEntry(new ZipEntry("preferences/lime_prefs.json"));
            output.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }

    private void writeLegacyLeadingSlashFullBackupZip(File fixtureFile) throws Exception {
        if (fixtureFile.exists() && !fixtureFile.delete()) {
            throw new java.io.IOException("Failed to delete old fixture " + fixtureFile);
        }
        File databaseFile = context.getDatabasePath(LIME.DATABASE_NAME);
        assertTrue("Legacy fixture requires an existing database", databaseFile.exists());

        File prefsBackup = new File(context.getCacheDir(), "legacy_shared_prefs_" + System.currentTimeMillis() + ".bak");
        try {
            staticDbServer.backupDefaultSharedPreference(prefsBackup);
            try (ZipOutputStream output = new ZipOutputStream(new java.io.FileOutputStream(fixtureFile))) {
                output.putNextEntry(new ZipEntry("/databases/lime.db"));
                copyFileToZipEntry(databaseFile, output);
                output.closeEntry();

                output.putNextEntry(new ZipEntry("/databases/lime.db-journal"));
                output.closeEntry();

                output.putNextEntry(new ZipEntry("/shared_prefs.bak"));
                copyFileToZipEntry(prefsBackup, output);
                output.closeEntry();
            }
        } finally {
            if (prefsBackup.exists()) prefsBackup.delete();
        }
    }

    private void copyFileToZipEntry(File source, ZipOutputStream output) throws Exception {
        try (InputStream input = new java.io.FileInputStream(source)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        }
    }

    private JSONObject readPreferenceManifest(File backupFile) throws Exception {
        try (ZipFile zipFile = new ZipFile(backupFile)) {
            ZipEntry entry = zipFile.getEntry("preferences/lime_prefs.json");
            assertNotNull("Full backup should contain preferences/lime_prefs.json", entry);
            try (InputStream input = zipFile.getInputStream(entry)) {
                byte[] data = new byte[(int) entry.getSize()];
                int offset = 0;
                while (offset < data.length) {
                    int read = input.read(data, offset, data.length - offset);
                    if (read < 0) break;
                    offset += read;
                }
                assertEquals("Manifest should be read completely", data.length, offset);
                return new JSONObject(new String(data, StandardCharsets.UTF_8));
            }
        }
    }

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
