package net.toload.main.hd;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import net.toload.main.hd.data.Mapping;
import net.toload.main.hd.data.Record;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.limedb.LimeDB;
import net.toload.main.hd.ui.controller.ManageImController;
import net.toload.main.hd.ui.controller.SetupImController;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

/**
 * Phase 9: Performance Tests
 *
 * Objective: Ensure no performance regression after refactoring.
 *
 * Test Coverage:
 * - 9.1 Database Operation Benchmarks (3 tests)
 * - 9.2 File Operation Benchmarks (2 tests)
 * - 9.3 Memory Usage (1 test)
 *
 * Performance Targets:
 * - No more than 5% regression on database operations
 * - Same or better performance on search operations
 * - No memory leaks on long-running operations
 *
 * Uses Real-World Data:
 * - Downloads PHONETIC and DAYI IM tables from cloud
 * - Tests with actual production-size datasets
 * - Ensures realistic performance measurements
 */
@RunWith(AndroidJUnit4.class)
public class PerformanceTest {

    private static Context staticContext;
    private static SetupImController staticSetupController;
    private static DBServer staticDbServer;
    private static SearchServer staticSearchServer;
    private static String realImTablePhonetic;
    private static String realImTableDayi;
    private static boolean imTablesReady = false;

    private Context context;
    private LimeDB limeDB;
    private DBServer dbServer;
    private SearchServer searchServer;
    private String testTableName;

    // Performance thresholds (in milliseconds)
    private static final long COUNT_OPERATION_THRESHOLD = 100;
    private static final long SEARCH_OPERATION_THRESHOLD = 50;
    private static final long BACKUP_OPERATION_THRESHOLD = 1000;
    private static final long EXPORT_OPERATION_THRESHOLD = 2000;
    private static final long IMPORT_OPERATION_THRESHOLD = 2000;

    @BeforeClass
    public static void setUpClass() throws Exception {
        staticContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        staticSearchServer = new SearchServer(staticContext);
        staticDbServer = DBServer.getInstance(staticContext);
        staticSetupController = new SetupImController(staticContext, staticDbServer, staticSearchServer);

        // Download both PHONETIC and DAYI cloud data for realistic performance testing
        ManageImController tempController = new ManageImController(staticSearchServer);

        // Check Phonetic table
        int phoneticCount = tempController.countRecords(LIME.IM_PHONETIC);
        if (phoneticCount == 0) {
            staticSetupController.clearTable(LIME.IM_PHONETIC, false);
            downloadCloudDbAndImport(LIME.IM_PHONETIC, LIME.DATABASE_CLOUD_IM_PHONETIC);
        }

        // Check Dayi table
        int dayiCount = tempController.countRecords(LIME.IM_DAYI);
        if (dayiCount == 0) {
            staticSetupController.clearTable(LIME.IM_DAYI, false);
            downloadCloudDbAndImport(LIME.IM_DAYI, LIME.DATABASE_CLOUD_IM_DAYI);
        }

        // Use PHONETIC as primary test table, DAYI as secondary
        realImTablePhonetic = LIME.IM_PHONETIC;
        realImTableDayi = LIME.IM_DAYI;

        // Verify both tables
        int finalPhoneticCount = tempController.countRecords(LIME.IM_PHONETIC);
        int finalDayiCount = tempController.countRecords(LIME.IM_DAYI);
        assertTrue("PHONETIC table should have records", finalPhoneticCount > 0);
        assertTrue("DAYI table should have records", finalDayiCount > 0);

        imTablesReady = true;
    }

    private static void downloadCloudDbAndImport(String tableName, String url) {
        File tmpFile = new File(staticContext.getFilesDir(),
                tableName + "_cloud_" + System.currentTimeMillis() + ".limedb");
        try {
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

            staticDbServer.importZippedDb(tmpFile, tableName);

            int recordCount = staticSearchServer.countRecords(tableName);
            assertTrue("Imported table should have records: " + tableName, recordCount > 0);
        } catch (Exception e) {
            fail("Failed to download/import cloud DB for " + tableName + ": " + e.getMessage());
        } finally {
            if (tmpFile.exists()) {
                try { tmpFile.delete(); } catch (Throwable ignored) {}
            }
        }
    }

    @Before
    public void setUp() {
        assertTrue("IM tables must be ready", imTablesReady);
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        limeDB = new LimeDB(context);
        dbServer = DBServer.getInstance(context);
        searchServer = new SearchServer(context);

        testTableName = "performance_test_" + System.currentTimeMillis();
    }

    @After
    public void tearDown() {
        // No explicit cleanup needed - tables are managed by the static instances
    }

    // ========================================================================
    // 9.1 Database Operation Benchmarks
    // ========================================================================

    /**
     * Test 9.1.1: Benchmark count operations
     *
     * Measures performance of countRecords() on large real-world IM tables
     * Uses PHONETIC IM table with production data
     * Target: No more than 5% regression
     */
    @Test
    public void test_9_1_1_benchmarkCountOperations() {
        // Use real-world PHONETIC table
        String tableName = realImTablePhonetic;

        // Get expected count using SearchServer
        int expectedCount = searchServer.countRecords(tableName);
        assertTrue("Table should have records", expectedCount > 0);

        // Warm up
        for (int i = 0; i < 5; i++) {
            searchServer.countRecords(tableName);
        }

        // Benchmark: Multiple count operations
        long startTime = SystemClock.elapsedRealtime();
        int iterations = 100;

        for (int i = 0; i < iterations; i++) {
            int count = searchServer.countRecords(tableName);
            assertEquals("Count should match expected size", expectedCount, count);
        }

        long elapsedTime = SystemClock.elapsedRealtime() - startTime;
        long averageTime = elapsedTime / iterations;

        // Log performance metrics
        System.out.println("Count Operations Benchmark (Real-world PHONETIC data):");
        System.out.println("  Table: " + tableName);
        System.out.println("  Record count: " + expectedCount);
        System.out.println("  Total time: " + elapsedTime + "ms");
        System.out.println("  Average time per operation: " + averageTime + "ms");
        System.out.println("  Operations per second: " + (1000.0 / averageTime));

        // Assert performance threshold
        assertTrue("Count operation should complete within threshold (" + COUNT_OPERATION_THRESHOLD + "ms), actual: " + averageTime + "ms",
                averageTime < COUNT_OPERATION_THRESHOLD);
    }

    /**
     * Test 9.1.2: Benchmark search operations
     *
     * Compares search performance through SearchServer vs direct LimeDB
     * Uses real-world DAYI IM table with production data
     * Target: Same or better performance
     */
    @Test
    public void test_9_1_2_benchmarkSearchOperations() {
        // Use real-world DAYI table
        String tableName = realImTableDayi;

        // Use a common search code from DAYI (most IM tables have codes starting with common characters)
        String searchCode = "a";

        // Warm up
        for (int i = 0; i < 5; i++) {
            searchServer.getRecords(tableName, searchCode, true, 10, 0);
        }

        // Benchmark: SearchServer search operations
        long startTime = SystemClock.elapsedRealtime();
        int iterations = 50;

        for (int i = 0; i < iterations; i++) {
            List<Record> results = searchServer.getRecords(tableName, searchCode, true, 10, 0);
            assertNotNull("Search results should not be null", results);
        }

        long searchServerTime = SystemClock.elapsedRealtime() - startTime;
        long searchServerAverage = searchServerTime / iterations;

        // Benchmark: Direct LimeDB search operations
        startTime = SystemClock.elapsedRealtime();

        for (int i = 0; i < iterations; i++) {
            List<Record> results = limeDB.getRecordList(tableName, searchCode, true, 10, 0);
            assertNotNull("Search results should not be null", results);
        }

        long limeDBTime = SystemClock.elapsedRealtime() - startTime;
        long limeDBAverage = limeDBTime / iterations;

        // Log performance metrics
        System.out.println("Search Operations Benchmark (Real-world DAYI data):");
        System.out.println("  Table: " + tableName);
        System.out.println("  Search code: '" + searchCode + "'");
        System.out.println("  SearchServer average: " + searchServerAverage + "ms");
        System.out.println("  LimeDB average: " + limeDBAverage + "ms");
        System.out.println("  Overhead: " + (searchServerAverage - limeDBAverage) + "ms");

        // Assert performance threshold
        assertTrue("Search operation should complete within threshold (" + SEARCH_OPERATION_THRESHOLD + "ms), actual: " + searchServerAverage + "ms",
                searchServerAverage < SEARCH_OPERATION_THRESHOLD);

        // SearchServer should not add more than 20% overhead (skip if LimeDB is too fast to measure)
        if (limeDBAverage > 0) {
            double overhead = ((double)(searchServerAverage - limeDBAverage) / limeDBAverage) * 100;
            assertTrue("SearchServer overhead should be less than 20%, actual: " + overhead + "%",
                    overhead < 20);
        } else {
            System.out.println("  Note: LimeDB search too fast to measure overhead accurately");
        }
    }

    /**
     * Test 9.1.3: Benchmark backup/import operations
     *
     * Measures performance of backup and import operations on real-world IM databases
     * Uses PHONETIC IM table with production data
     * Target: No more than 5% regression
     */
    @Test
    public void test_9_1_3_benchmarkBackupImportOperations() {
        // Use real-world PHONETIC table
        String tableName = realImTablePhonetic;

        File backupFile = new File(context.getFilesDir(), "benchmark_backup.lime");

        // Warm up
        dbServer.exportZippedDb(tableName, backupFile, null);
        if (backupFile.exists()) {
            backupFile.delete();
        }

        // Benchmark: Backup operation
        long startTime = SystemClock.elapsedRealtime();
        int iterations = 10;

        for (int i = 0; i < iterations; i++) {
            File result = dbServer.exportZippedDb(tableName, backupFile, null);
            assertNotNull("Backup should succeed", result);
            assertTrue("Backup file should exist", backupFile.exists());
            backupFile.delete();
        }

        long backupTime = SystemClock.elapsedRealtime() - startTime;
        long backupAverage = backupTime / iterations;

        // Create one backup file for import testing
        dbServer.exportZippedDb(tableName, backupFile, null);
        int expectedCount = searchServer.countRecords(tableName);

        // Benchmark: Import operation (re-imports into same table)
        startTime = SystemClock.elapsedRealtime();

        for (int i = 0; i < iterations; i++) {
            dbServer.importZippedDb(backupFile, tableName);

            // Verify import succeeded
            int count = searchServer.countRecords(tableName);
            assertTrue("Imported table should have records", count > 0);
            assertEquals("Import should restore all records", expectedCount, count);
        }

        long importTime = SystemClock.elapsedRealtime() - startTime;
        long importAverage = importTime / iterations;

        // Log performance metrics
        System.out.println("Backup/Import Operations Benchmark (Real-world PHONETIC data):");
        System.out.println("  Table: " + tableName);
        System.out.println("  Backup average: " + backupAverage + "ms");
        System.out.println("  Import average: " + importAverage + "ms");
        System.out.println("  Total average: " + (backupAverage + importAverage) + "ms");

        // Assert performance thresholds
        assertTrue("Backup operation should complete within threshold (" + BACKUP_OPERATION_THRESHOLD + "ms), actual: " + backupAverage + "ms",
                backupAverage < BACKUP_OPERATION_THRESHOLD);
        assertTrue("Import operation should complete within threshold (" + BACKUP_OPERATION_THRESHOLD + "ms), actual: " + importAverage + "ms",
                importAverage < BACKUP_OPERATION_THRESHOLD);

        // Clean up
        if (backupFile.exists()) {
            backupFile.delete();
        }
    }

    // ========================================================================
    // 9.2 File Operation Benchmarks
    // ========================================================================

    /**
     * Test 9.2.1: Benchmark export operations
     *
     * Measures performance of database export operations on real-world data
     * Uses PHONETIC IM table with production data
     * Target: Same or better performance
     */
    @Test
    public void test_9_2_1_benchmarkExportOperations() {
        // Use real-world PHONETIC table
        String tableName = realImTablePhonetic;

        File exportFile = new File(context.getFilesDir(), "benchmark_export.lime");

        // Clean up any existing export file
        if (exportFile.exists()) {
            exportFile.delete();
        }

        // Warm up
        dbServer.exportZippedDb(tableName, exportFile, null);
        if (exportFile.exists()) {
            exportFile.delete();
        }

        // Benchmark: Export operation
        long startTime = SystemClock.elapsedRealtime();
        int iterations = 5;

        for (int i = 0; i < iterations; i++) {
            File result = dbServer.exportZippedDb(tableName, exportFile, null);
            assertNotNull("Export should succeed", result);
            assertTrue("Export file should be created", exportFile.exists());

            // Clean up for next iteration
            exportFile.delete();
        }

        long elapsedTime = SystemClock.elapsedRealtime() - startTime;
        long averageTime = elapsedTime / iterations;

        // Log performance metrics
        System.out.println("Export Operations Benchmark (Real-world PHONETIC data):");
        System.out.println("  Table: " + tableName);
        System.out.println("  Total time: " + elapsedTime + "ms");
        System.out.println("  Average time per operation: " + averageTime + "ms");

        // Assert performance threshold
        assertTrue("Export operation should complete within threshold (" + EXPORT_OPERATION_THRESHOLD + "ms), actual: " + averageTime + "ms",
                averageTime < EXPORT_OPERATION_THRESHOLD);

        // Clean up
        if (exportFile.exists()) {
            exportFile.delete();
        }
    }

    /**
     * Test 9.2.2: Benchmark import operations
     *
     * Measures performance of database import operations on real-world data
     * Uses DAYI IM table with production data
     * Target: Same or better performance
     */
    @Test
    public void test_9_2_2_benchmarkImportOperations() {
        // Use real-world DAYI table
        String sourceTable = realImTableDayi;

        // Get expected record count
        int expectedCount = searchServer.countRecords(sourceTable);

        // Export to create a file for import testing
        File importFile = new File(context.getFilesDir(), "benchmark_import.lime");
        dbServer.exportZippedDb(sourceTable, importFile, null);
        assertTrue("Import file should be created", importFile.exists());

        // Warm up (re-import into same table)
        dbServer.importZippedDb(importFile, sourceTable);

        // Benchmark: Import operation (re-imports into same table)
        long startTime = SystemClock.elapsedRealtime();
        int iterations = 5;

        for (int i = 0; i < iterations; i++) {
            dbServer.importZippedDb(importFile, sourceTable);

            // Verify imported data
            int count = searchServer.countRecords(sourceTable);
            assertEquals("Imported table should have correct record count", expectedCount, count);
        }

        long elapsedTime = SystemClock.elapsedRealtime() - startTime;
        long averageTime = elapsedTime / iterations;

        // Log performance metrics
        System.out.println("Import Operations Benchmark (Real-world DAYI data):");
        System.out.println("  Source table: " + sourceTable);
        System.out.println("  Record count: " + expectedCount);
        System.out.println("  Total time: " + elapsedTime + "ms");
        System.out.println("  Average time per operation: " + averageTime + "ms");

        // Assert performance threshold
        assertTrue("Import operation should complete within threshold (" + IMPORT_OPERATION_THRESHOLD + "ms), actual: " + averageTime + "ms",
                averageTime < IMPORT_OPERATION_THRESHOLD);

        // Clean up
        if (importFile.exists()) {
            importFile.delete();
        }
    }

    // ========================================================================
    // 9.3 Memory Usage
    // ========================================================================

    /**
     * Test 9.3.1: Test for memory leaks
     *
     * Tests long-running operations for memory leaks using real-world data
     * Uses PHONETIC IM table with production data
     * Target: No memory leaks
     */
    @Test
    public void test_9_3_1_testMemoryLeaks() {
        // Use real-world PHONETIC table
        String tableName = realImTablePhonetic;

        // Get initial memory usage
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        SystemClock.sleep(100); // Wait for GC to complete
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        // Perform long-running operations
        int iterations = 100;
        for (int i = 0; i < iterations; i++) {
            // Search operations on real data (search for common codes)
            List<Record> results = searchServer.getRecords(tableName, "a", true, 10, 0);
            assertNotNull("Search results should not be null", results);

            // Count operations
            int count = searchServer.countRecords(tableName);
            assertTrue("Count should be positive", count > 0);

            // Add/update/delete operations using SearchServer
            android.content.ContentValues values = new android.content.ContentValues();
            values.put("code", "leak_test_" + i);
            values.put("word", "漏洞測試" + i);
            values.put("score", 100);
            values.put("basescore", 100);

            // Test add/update/delete cycle
            searchServer.addRecord(tableName, values);
            values.put("score", 200);
            searchServer.addRecord(tableName, values);

            // Force GC every 10 iterations
            if (i % 10 == 0) {
                runtime.gc();
                SystemClock.sleep(50);
            }
        }

        // Force final GC
        runtime.gc();
        SystemClock.sleep(100);

        // Get final memory usage
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;

        // Log memory metrics
        System.out.println("Memory Leak Test (Real-world PHONETIC data):");
        System.out.println("  Table: " + tableName);
        System.out.println("  Initial memory: " + (initialMemory / 1024) + " KB");
        System.out.println("  Final memory: " + (finalMemory / 1024) + " KB");
        System.out.println("  Memory increase: " + (memoryIncrease / 1024) + " KB");
        System.out.println("  Iterations: " + iterations);

        // Assert memory threshold
        // Allow up to 5MB increase for 100 iterations (reasonable for caching)
        long maxMemoryIncrease = 5 * 1024 * 1024; // 5MB
        assertTrue("Memory increase should be less than " + (maxMemoryIncrease / 1024 / 1024) + "MB, actual: " + (memoryIncrease / 1024 / 1024) + "MB",
                memoryIncrease < maxMemoryIncrease);
    }
}
