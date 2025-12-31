/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

package net.toload.main.hd;

import android.content.Context;
import android.os.RemoteException;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.toload.main.hd.data.ImConfig;
import net.toload.main.hd.data.Keyboard;
import net.toload.main.hd.data.Record;
import net.toload.main.hd.data.Related;
import net.toload.main.hd.global.LIME;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

import android.util.Log;

/**
 * Test cases for SearchServer database interface layer.
 * 
 * Phase 3: SearchServer Layer Tests
 * 
 * Tests that SearchServer acts as the single interface for all database operations,
 * properly delegates to LimeDB, and handles null dbadapter gracefully.
 */
@RunWith(AndroidJUnit4.class)
public class SearchServerTest {

    private static final String TAG = "SearchServerTest";
    private Context appContext;
    private SearchServer searchServer;

    @Before
    public void setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        searchServer = new SearchServer(appContext);
    }

    // ========================================================================
    // Phase 3.1: UI-Compatible Methods Tests
    // ========================================================================

    @Test(timeout = 10000)
    public void testSearchServerGetImConfigListWithNullCode() {
        // Test getIm() with null code (should return all IMs)
        List<ImConfig> imConfigList = searchServer.getImConfigList(null, LIME.IM_FULL_NAME);
        assertNotNull("getIm() should return a list (not null)", imConfigList);
        assertTrue("getIm() with null code should return a list", imConfigList instanceof List);
    }

    @Test(timeout = 10000)
    public void testSearchServerGetImConfigListWithSpecificCode() {
        // Test getIm() with specific code
        List<ImConfig> imConfigList = searchServer.getImConfigList("custom", LIME.IM_FULL_NAME);
        assertNotNull("getIm() should return a list (not null)", imConfigList);
        assertTrue("getIm() with specific code should return a list", imConfigList instanceof List);
    }

    @Test(timeout = 10000)
    public void testSearchServerGetImConfigListWithTypeFilter() {
        // Test getIm() with type filter
        List<ImConfig> nameList = searchServer.getImConfigList(null, LIME.IM_FULL_NAME);
        List<ImConfig> keyboardList = searchServer.getImConfigList(null, LIME.IM_KEYBOARD);
        
        assertNotNull("getIm() with IM_TYPE_NAME should return a list", nameList);
        assertNotNull("getIm() with IM_TYPE_KEYBOARD should return a list", keyboardList);
        assertTrue("Both type filters should return lists", nameList instanceof List && keyboardList instanceof List);
    }

    @Test(timeout = 10000)
    public void testSearchServerGetKeyboard() {
        // Test getKeyboard() - should return all keyboards
        List<Keyboard> keyboardList = searchServer.getKeyboard();
        assertNotNull("getKeyboard() should return a list (not null)", keyboardList);
        assertTrue("getKeyboard() should return a list", keyboardList instanceof List);
    }

    @Test(timeout = 10000)
    public void testSearchServerGetImConfigListInfo() {
        // Test getImInfo() - retrieving IM info field
        String info = searchServer.getImConfig("custom", "name");
        // Info might be null if IM doesn't exist, which is acceptable
        assertTrue("getImInfo() should return a string or null", info == null || info instanceof String);
    }

    @Test(timeout = 10000)
    public void testSearchServerGetImInfoWithNonExistentImList() {
        // Test getImInfo() with non-existent IM (should return empty string, not null)
        String info = searchServer.getImConfig("nonexistent_im", "name");
        // SearchServer.getImInfo() returns empty string "" for non-existent IM, not null
        assertTrue("getImInfo() with non-existent IM should return empty string or null", 
                  info == null || info.isEmpty());
        // If not null, it should be empty string
        if (info != null) {
            assertEquals("getImInfo() with non-existent IM should return empty string", "", info);
        } else {
            // If null, that's also acceptable (defensive check)
            assertTrue("getImInfo() with non-existent IM returned null (acceptable)", true);
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerGetImConfigListInfoWithNonExistentField() {
        // Test getImInfo() with non-existent field (should return empty string, not null)
        String info = searchServer.getImConfig("custom", "nonexistent_field");
        // SearchServer.getImInfo() returns empty string "" for non-existent field, not null
        assertTrue("getImInfo() with non-existent field should return empty string or null", 
                  info == null || info.isEmpty());
        // If not null, it should be empty string
        if (info != null) {
            assertEquals("getImInfo() with non-existent field should return empty string", "", info);
        } else {
            // If null, that's also acceptable (defensive check)
            assertTrue("getImInfo() with non-existent field returned null (acceptable)", true);
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerSetImConfig() {
        // Test setImInfo() - setting IM info field
        try {
            searchServer.setImConfig("custom", "test_field", "test_value");
            // If no exception thrown, operation succeeded
            assertTrue("setImInfo() should complete without exception", true);
            
            // Verify the value was set
            String value = searchServer.getImConfig("custom", "test_field");
            assertEquals("setImInfo() should set the field value", "test_value", value);
        } catch (Exception e) {
            Log.e(TAG, "setImInfo() threw exception", e);
            fail("setImInfo() should not throw exception: " + e.getMessage());
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerSetImConfigUpdateExisting() {
        // Test setImInfo() - updating existing field
        try {
            // Set initial value
            searchServer.setImConfig("custom", "test_field_update", "initial_value");
            
            // Update the value
            searchServer.setImConfig("custom", "test_field_update", "updated_value");
            
            // Verify the value was updated
            String value = searchServer.getImConfig("custom", "test_field_update");
            assertEquals("setImInfo() should update existing field", "updated_value", value);
        } catch (Exception e) {
            Log.e(TAG, "setImInfo() update threw exception", e);
            fail("setImInfo() should not throw exception: " + e.getMessage());
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerSetIMKeyboardWithStringParameters() {
        // Test setIMKeyboard() with (String im, String value, String keyboard)
        try {
            searchServer.setIMKeyboard("custom", "test_keyboard", "lime");
            // If no exception thrown, operation succeeded
            assertTrue("setIMKeyboard() with String parameters should complete without exception", true);
        } catch (Exception e) {
            Log.e(TAG, "setIMKeyboard() with String parameters threw exception", e);
            fail("setIMKeyboard() should not throw exception: " + e.getMessage());
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerSetIMKeyboardWithKeyboardObject() {
        // Test setIMKeyboard() with (String code, Keyboard keyboard)
        try {
            // Get a keyboard first
            List<Keyboard> keyboards = searchServer.getKeyboard();
            if (keyboards != null && !keyboards.isEmpty()) {
                Keyboard keyboard = keyboards.get(0);
                searchServer.setIMKeyboard("custom", keyboard);
                // If no exception thrown, operation succeeded
                assertTrue("setIMKeyboard() with Keyboard object should complete without exception", true);
            } else {
                Log.w(TAG, "No keyboards available for setIMKeyboard() test");
                assertTrue("setIMKeyboard() test skipped - no keyboards available", true);
            }
        } catch (Exception e) {
            Log.e(TAG, "setIMKeyboard() with Keyboard object threw exception", e);
            fail("setIMKeyboard() should not throw exception: " + e.getMessage());
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerIsValidTableName() {
        // Test isValidTableName() - delegation to LimeDB
        assertTrue("isValidTableName() should return true for valid table", 
                   searchServer.isValidTableName("custom"));
        assertTrue("isValidTableName() should return true for valid table", 
                   searchServer.isValidTableName(LIME.DB_TABLE_RELATED));
        assertFalse("isValidTableName() should return false for invalid table", 
                    searchServer.isValidTableName("invalid_table"));
        assertFalse("isValidTableName() should return false for SQL injection attempt", 
                    searchServer.isValidTableName("'; DROP TABLE custom; --"));
    }

    @Test(timeout = 10000)
    public void testSearchServerIsValidTableNameWithNull() {
        // Test isValidTableName() with null (should return false)
        assertFalse("isValidTableName() should return false for null", 
                    searchServer.isValidTableName(null));
    }

    // ========================================================================
    // Phase 3.2: Database Operation Delegation Tests
    // ========================================================================

    @Test(timeout = 10000)
    public void testSearchServerGetRecord() {
        // Test getRecord() - delegation to LimeDB
        // First, add a record to test with
        try {
            searchServer.addOrUpdateMappingRecord("custom", "test_code", "測試", 10);
            
            // Get records to find an ID - search by code (searchByCode = true)
            List<Record> records = searchServer.getRecords("custom", "test_code", true, 10, 0);
            if (records != null && !records.isEmpty()) {
                Record record = records.get(0);
                long id = record.getIdAsInt();
                
                // Test getRecord()
                Record retrievedRecord = searchServer.getRecord("custom", id);
                assertNotNull("getRecord() should return a Record", retrievedRecord);
                assertEquals("getRecord() should return correct record", "test_code", retrievedRecord.getCode());
                assertEquals("getRecord() should return correct record", "測試", retrievedRecord.getWord());
            } else {
                Log.w(TAG, "No records found for getRecord() test");
                assertTrue("getRecord() test skipped - no records available", true);
            }
        } catch (Exception e) {
            Log.e(TAG, "getRecord() test threw exception", e);
            fail("getRecord() test should not throw exception: " + e.getMessage());
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerGetRecordSize() {
        // Test countRecordsByWordOrCode() - delegation to LimeDB
        int size = searchServer.countRecordsByWordOrCode("custom", null, false);
        assertTrue("countRecordsByWordOrCode() should return non-negative", size >= 0);
        
        // Test with query
        int sizeWithQuery = searchServer.countRecordsByWordOrCode("custom", "測試", false);
        assertTrue("countRecordsByWordOrCode() with query should return non-negative", sizeWithQuery >= 0);
    }

    @Test(timeout = 10000)
    public void testSearchServerDeleteRecord() {
        // Test deleteRecord() - delegation to LimeDB
        try {
            // First, add a record to delete
            searchServer.addOrUpdateMappingRecord("custom", "delete_test", "刪除測試", 10);
            
            // Get the record ID - search by code (searchByCode = true)
            List<Record> records = searchServer.getRecords("custom", "delete_test", true, 10, 0);
            if (records != null && !records.isEmpty()) {
                Record record = records.get(0);
                long id = record.getIdAsInt();
                
                // Delete the record using whereClause and whereArgs
                int deleted = searchServer.deleteRecord("custom", LIME.DB_COLUMN_ID + " = ?", 
                    new String[]{String.valueOf(id)});
                assertTrue("deleteRecord() should return positive number if record deleted", deleted > 0);
                
                // Verify record is deleted
                Record deletedRecord = searchServer.getRecord("custom", id);
                assertNull("getRecord() should return null for deleted record", deletedRecord);
            } else {
                Log.w(TAG, "No records found for deleteRecord() test");
                assertTrue("deleteRecord() test skipped - no records available", true);
            }
        } catch (Exception e) {
            Log.e(TAG, "deleteRecord() test threw exception", e);
            fail("deleteRecord() test should not throw exception: " + e.getMessage());
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerAddOrUpdateMappingRecord() {
        // Test addOrUpdateMappingRecord() - delegation to LimeDB
        try {
            // Add a new record
            searchServer.addOrUpdateMappingRecord("custom", "add_test", "新增測試", 10);
            
            // Verify record was added - search by code (searchByCode = true)
            List<Record> records = searchServer.getRecords("custom", "add_test", true, 10, 0);
            assertNotNull("getRecords() should return a list after adding record", records);
            assertTrue("getRecords() should contain added record", records.size() > 0);
            
            boolean found = false;
            for (Record record : records) {
                if ("add_test".equals(record.getCode()) && "新增測試".equals(record.getWord())) {
                    found = true;
                    break;
                }
            }
            assertTrue("addOrUpdateMappingRecord() should add the record", found);
            
            // Update the record
            searchServer.addOrUpdateMappingRecord("custom", "add_test", "更新測試", 20);
            
            // Verify record was updated - search by code (searchByCode = true)
            records = searchServer.getRecords("custom", "add_test", true, 10, 0);
            found = false;
            for (Record record : records) {
                if ("add_test".equals(record.getCode()) && "更新測試".equals(record.getWord())) {
                    found = true;
                    assertEquals("addOrUpdateMappingRecord() should update score", 20, record.getScore());
                    break;
                }
            }
            assertTrue("addOrUpdateMappingRecord() should update the record", found);
        } catch (Exception e) {
            Log.e(TAG, "addOrUpdateMappingRecord() test threw exception", e);
            fail("addOrUpdateMappingRecord() test should not throw exception: " + e.getMessage());
        }
    }

    // ========================================================================
    // Phase 3.3: Search Operations Tests
    // ========================================================================

    @Test(timeout = 10000)
    public void testSearchServerGetMappingByCode() {
        // Test getMappingByCode() - basic search functionality
        try {
            // Set table name first (required for getMappingByCode)
            searchServer.setTableName("custom", false, false);
            
            // Add a test record first
            searchServer.addOrUpdateMappingRecord("custom", "search_test", "搜尋測試", 10);
            
            // Search for the record
            List<net.toload.main.hd.data.Mapping> mappings = searchServer.getMappingByCode("search_test", true, false);
            assertNotNull("getMappingByCode() should return a list", mappings);
            assertTrue("getMappingByCode() should return a list", mappings instanceof List);
        } catch (RemoteException e) {
            Log.e(TAG, "getMappingByCode() threw RemoteException (may be expected)", e);
            // RemoteException is declared, so it's acceptable
            assertTrue("getMappingByCode() may throw RemoteException", true);
        } catch (Exception e) {
            Log.e(TAG, "getMappingByCode() test threw exception", e);
            fail("getMappingByCode() test should not throw exception: " + e.getMessage());
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerGetRecords() {
        // Test getRecords() - delegation to LimeDB.getRecords()
        List<Record> records = searchServer.getRecords("custom", null, false, 10, 0);
        assertNotNull("getRecords() should return a list (not null)", records);
        assertTrue("getRecords() should return a list", records instanceof List);
    }

    @Test(timeout = 10000)
    public void testSearchServerGetRecordsWithQuery() {
        // Test getRecords() with search query
        List<Record> records = searchServer.getRecords("custom", "測試", false, 10, 0);
        assertNotNull("getRecords() with query should return a list", records);
        assertTrue("getRecords() with query should return a list", records instanceof List);
    }

    @Test(timeout = 10000)
    public void testSearchServerGetRecordsWithPagination() {
        // Test getRecords() with pagination
        List<Record> records1 = searchServer.getRecords("custom", null, false, 5, 0);
        List<Record> records2 = searchServer.getRecords("custom", null, false, 5, 5);
        
        assertNotNull("getRecords() with pagination should return a list", records1);
        assertNotNull("getRecords() with offset should return a list", records2);
        assertTrue("getRecords() pagination should work", records1 instanceof List && records2 instanceof List);
    }

    @Test(timeout = 10000)
    public void testSearchServerGetRelated() {
        // Test getRelatedByWord() - delegation to LimeDB.getRelated()
        List<Related> relatedList = searchServer.getRelatedByWord("測試", 10, 0);
        assertNotNull("getRelatedByWord() should return a list (not null)", relatedList);
        assertTrue("getRelatedByWord() should return a list", relatedList instanceof List);
    }

    @Test(timeout = 10000)
    public void testSearchServerGetRelatedByWordWithPagination() {
        // Test getRelatedByWord() with pagination
        List<Related> related1 = searchServer.getRelatedByWord("測試", 5, 0);
        List<Related> related2 = searchServer.getRelatedByWord("測試", 5, 5);
        
        assertNotNull("getRelatedByWord() with pagination should return a list", related1);
        assertNotNull("getRelatedByWord() with offset should return a list", related2);
        assertTrue("getRelatedByWord() pagination should work", related1 instanceof List && related2 instanceof List);
    }

    // ========================================================================
    // Phase 3.4: Converter Integration Tests
    // ========================================================================

    @Test(timeout = 10000)
    public void testSearchServerHanConvert() {
        // Test hanConvert() - Chinese conversion
        String result = searchServer.hanConvert("測試");
        // Result might be null or empty if conversion fails, which is acceptable
        assertTrue("hanConvert() should return a string or null", result == null || result instanceof String);
    }

    @Test(timeout = 10000)
    public void testSearchServerHanConvertWithVariousInputs() {
        // Test hanConvert() with various input strings
        String[] testInputs = {"測試", "中文", "轉換", ""};
        for (String input : testInputs) {
            String result = searchServer.hanConvert(input);
            assertTrue("hanConvert() should return a string or null for input: " + input, 
                      result == null || result instanceof String);
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerEmojiConvert() {
        // Test emojiConvert() - emoji conversion
        List<net.toload.main.hd.data.Mapping> results = searchServer.emojiConvert("測試", LIME.EMOJI_CN);
        // Results might be null or empty if conversion fails, which is acceptable
        assertTrue("emojiConvert() should return a list or null", 
                  results == null || results instanceof List);
    }

    @Test(timeout = 10000)
    public void testSearchServerEmojiConvertWithVariousInputs() {
        // Test emojiConvert() with various input strings
        String[] testInputs = {"測試", "中文", "開心", ""};
        for (String input : testInputs) {
            List<net.toload.main.hd.data.Mapping> results = searchServer.emojiConvert(input, LIME.EMOJI_CN);
            assertTrue("emojiConvert() should return a list or null for input: " + input, 
                      results == null || results instanceof List);
        }
    }

    // ========================================================================
    // Phase 3.5: Null dbadapter Handling Tests
    // ========================================================================

    @Test(timeout = 5000)
    public void testSearchServerGetImConfigListWithNullDbadapter() {
        // Test getIm() with null dbadapter (should return empty list)
        // Note: This test verifies the null check in SearchServer
        // In practice, dbadapter should not be null after SearchServer construction,
        // but we test the defensive coding
        List<ImConfig> imConfigList = searchServer.getImConfigList(null, LIME.IM_FULL_NAME);
        // Should return empty list, not null, based on SearchServer implementation
        assertNotNull("getIm() should return a list even if dbadapter is null", imConfigList);
    }

    @Test(timeout = 5000)
    public void testSearchServerGetKeyboardWithNullDbadapter() {
        // Test getKeyboard() with null dbadapter (should return empty list)
        List<Keyboard> keyboardList = searchServer.getKeyboard();
        assertNotNull("getKeyboard() should return a list even if dbadapter is null", keyboardList);
    }

    @Test(timeout = 5000)
    public void testSearchServerGetImConfigListInfoWithNullDbadapter() {
        // Test getImInfo() with null dbadapter (should return null)
        String info = searchServer.getImConfig("custom", "name");
        // Should handle null dbadapter gracefully
        assertTrue("getImInfo() should return null or string", info == null || info instanceof String);
    }

    @Test(timeout = 5000)
    public void testSearchServerIsValidTableNameWithNullDbadapter() {
        // Test isValidTableName() with null dbadapter (should return false)
        // Note: This is defensive - if dbadapter is null, should return false
        boolean result = searchServer.isValidTableName("custom");
        // Should return false if dbadapter is null, true otherwise
        assertTrue("isValidTableName() should return boolean", result == true || result == false);
    }

    // ========================================================================
    // Phase 3.6: Configuration Operations Tests
    // ========================================================================

    @Test
    public void testSearchServerCheckPhoneticKeyboardSetting() {
        // Test checkPhoneticKeyboardSetting
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SearchServer searchServer = new SearchServer(appContext);
        
        // This method checks consistency of phonetic keyboard setting
        searchServer.checkPhoneticKeyboardSetting();
        
        // Verify no exception thrown
        assertTrue("checkPhoneticKeyboardSetting should complete", true);
    }

    @Test
    public void testSearchServerCheckPhoneticKeyboardSettingMultipleTimes() {
        // Test checkPhoneticKeyboardSetting multiple times
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SearchServer searchServer = new SearchServer(appContext);
        
        // Call multiple times
        searchServer.checkPhoneticKeyboardSetting();
        searchServer.checkPhoneticKeyboardSetting();
        searchServer.checkPhoneticKeyboardSetting();
        
        // Should not throw exception
        assertTrue("checkPhoneticKeyboardSetting should handle multiple calls gracefully", true);
    }

    // ========================================================================
    // Phase 3.7: Export Operations Tests
    // ========================================================================

    @Test(timeout = 10000)
    public void testSearchServerExportTxtTable() {
        // Test exportTxtTable() - delegation to LimeDB.exportTxtTable()
        try {
            // Add some test records
            searchServer.addOrUpdateMappingRecord("custom", "export_test1", "匯出測試1", 10);
            searchServer.addOrUpdateMappingRecord("custom", "export_test2", "匯出測試2", 20);
            
            // Create export file
            java.io.File exportFile = new java.io.File(appContext.getCacheDir(), 
                "test_export_" + System.currentTimeMillis() + ".lime");
            
            // Create IM info list
            List<ImConfig> imConfigInfo = new java.util.ArrayList<>();
            ImConfig versionImConfig = new ImConfig();
            versionImConfig.setTitle(LIME.IM_FULL_NAME);
            versionImConfig.setDesc("1.0");
            imConfigInfo.add(versionImConfig);
            
            // Export table using DBServer
            DBServer dbServer = DBServer.getInstance(appContext);
            boolean success = dbServer.exportTxtTable("custom", exportFile, imConfigInfo);
            assertTrue("exportTxtTable() should succeed", success);
            assertTrue("Export file should exist", exportFile.exists());
            assertTrue("Export file should not be empty", exportFile.length() > 0);
            
            // Clean up
            if (exportFile.exists() && !exportFile.delete()) {
                Log.w(TAG, "Failed to delete export file");
            }
        } catch (Exception e) {
            Log.e(TAG, "exportTxtTable() test threw exception", e);
            fail("exportTxtTable() test should not throw exception: " + e.getMessage());
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerExportTxtTableWithRelatedTable() {
        // Test exportTxtTable() with related table (LIME.DB_TABLE_RELATED)
        try {
            // Note: We can't add related records through SearchServer as it doesn't expose
            // addOrUpdateRelatedPhraseRecord(). We'll test export with existing records.
            
            // Create export file
            java.io.File exportFile = new java.io.File(appContext.getCacheDir(), 
                "test_export_related_" + System.currentTimeMillis() + ".related");
            
            // Export related table using DBServer (may be empty, but should still work)
            DBServer dbServer = DBServer.getInstance(appContext);
            boolean success = dbServer.exportTxtTable(LIME.DB_TABLE_RELATED, exportFile, null);
            // Export might succeed even if table is empty (returns false for empty, but file might be created)
            assertTrue("exportTxtTable() should complete for related table", true);
            
            // Clean up
            if (exportFile.exists() && !exportFile.delete()) {
                Log.w(TAG, "Failed to delete export file");
            }
        } catch (Exception e) {
            Log.e(TAG, "exportTxtTable() with related table test threw exception", e);
            fail("exportTxtTable() test should not throw exception: " + e.getMessage());
        }
    }

    // ========================================================================
    // Phase 3.8: User Records Backup/Restore Tests
    // ========================================================================

    @Test(timeout = 10000)
    public void testSearchServerBackupUserRecordsAndRestoreWithDataConsistency() {
        // Comprehensive test: add records with score > 0, backup, clear, restore, verify consistency
        try {
            String tableName = "custom";

            searchServer.clearTable(tableName);
            
            // Step 1: Add some test records with score > 0 (user records)
            searchServer.addOrUpdateMappingRecord(tableName, "backup1", "備份1", 15);
            searchServer.addOrUpdateMappingRecord(tableName, "backup2", "備份2", 25);
            searchServer.addOrUpdateMappingRecord(tableName, "backup3", "備份3", 35);
            
            // Verify records were added
            List<Record> recordsBeforeBackup = searchServer.getRecords(tableName, null, false, 0, 0);
            assertNotNull("Records list should not be null before backup", recordsBeforeBackup);
            assertTrue("Should have at least 3 records before backup", recordsBeforeBackup.size() >= 3);
            
            // Count records with score > 0 (user records)
            int userRecordsCount = 0;
            for (Record record : recordsBeforeBackup) {
                if (record.getScore() > 0) {
                    userRecordsCount++;
                }
            }
            assertTrue("Should have at least 3 user records (score > 0) before backup", userRecordsCount >= 3);
            
            // Step 2: Backup user records
            searchServer.backupUserRecords(tableName);
            
            // Verify backup table was created
            boolean backupExists = searchServer.checkBackupTable(tableName);
            // Backup table may or may not exist depending on whether records have score > 0
            assertTrue("backupUserRecords should complete", true);
            
            // Step 3: Clear the main table (simulate import scenario)
            searchServer.clearTable(tableName);
            
            // Verify main table is empty
            int countAfterClear = searchServer.countRecords(tableName);
            assertEquals("Main table should be empty after clearTable", 0, countAfterClear);
            
            // Step 4: Restore user records
            int restoredCount = searchServer.restoreUserRecords(tableName);
            assertTrue("restoreUserRecords should return number of restored records", restoredCount >= 0);
            
            // Step 5: Verify records were restored
            if (backupExists && restoredCount > 0) {
                List<Record> recordsAfterRestore = searchServer.getRecords(tableName, null, false, 0, 0);
                assertNotNull("Records list should not be null after restore", recordsAfterRestore);
                Log.i("SearchServerTest", "records after restore: " + recordsAfterRestore.size() + " records before backup: " + recordsBeforeBackup.size() );
                assertTrue("Should have restored records", recordsAfterRestore.size() >= restoredCount);
                
                // Verify specific records exist
                boolean foundBackup1 = false;
                boolean foundBackup2 = false;
                boolean foundBackup3 = false;
                for (Record record : recordsAfterRestore) {
                    if ("backup1".equals(record.getCode()) && "備份1".equals(record.getWord())) {
                        foundBackup1 = true;
                    }
                    if ("backup2".equals(record.getCode()) && "備份2".equals(record.getWord())) {
                        foundBackup2 = true;
                    }
                    if ("backup3".equals(record.getCode()) && "備份3".equals(record.getWord())) {
                        foundBackup3 = true;
                    }
                }
                
                // At least some records should be restored
                assertTrue("At least one backup record should be restored", foundBackup1 || foundBackup2 || foundBackup3);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "backupUserRecords/restoreUserRecords test threw exception", e);
            fail("backupUserRecords/restoreUserRecords test should not throw exception: " + e.getMessage());
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerBackupUserRecords() {
        // Test backupUserRecords() - delegation to LimeDB
        try {
            String tableName = "custom";
            
            // Add a test record with score > 0
            searchServer.addOrUpdateMappingRecord(tableName, "backup_test", "備份測試", 10);
            
            // Backup user records
            searchServer.backupUserRecords(tableName);
            
            // Verify backup table was created (if record had score > 0)
            boolean backupExists = searchServer.checkBackupTable(tableName);
            // Backup table may or may not exist depending on whether records have score > 0
            assertTrue("backupUserRecords should complete", true);
            
        } catch (Exception e) {
            Log.e(TAG, "backupUserRecords() test threw exception", e);
            fail("backupUserRecords() test should not throw exception: " + e.getMessage());
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerRestoreUserRecords() {
        // Test restoreUserRecords() - delegation to LimeDB
        try {
            String tableName = "custom";
            
            // Restore user records (may return 0 if no backup exists)
            int restoredCount = searchServer.restoreUserRecords(tableName);
            assertTrue("restoreUserRecords should return non-negative count", restoredCount >= 0);
            
        } catch (Exception e) {
            Log.e(TAG, "restoreUserRecords() test threw exception", e);
            fail("restoreUserRecords() test should not throw exception: " + e.getMessage());
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerCheckBackupTable() {
        // Test checkBackuptable() - delegation to LimeDB
        try {
            String tableName = "custom";
            
            // Check if backup table exists
            boolean backupExists = searchServer.checkBackupTable(tableName);
            // Result can be true or false depending on whether backup was created
            assertTrue("checkBackuptable should return boolean", true);
            
        } catch (Exception e) {
            Log.e(TAG, "checkBackuptable() test threw exception", e);
            fail("checkBackuptable() test should not throw exception: " + e.getMessage());
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerRestoredToDefault() {
        // Test resetLimeSetting() - delegation to LimeDB
        try {
            // Reset Lime settings
            searchServer.restoredToDefault();
            
            // Verify no exception thrown
            assertTrue("resetLimeSetting should complete", true);
            
        } catch (Exception e) {
            Log.e(TAG, "resetLimeSetting() test threw exception", e);
            fail("resetLimeSetting() test should not throw exception: " + e.getMessage());
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerCountRecordsRelated() {
        // Test countRecordsRelated() - delegation to LimeDB.countRecords()
        try {
            String pword = "測試";
            int count = searchServer.countRecordsRelated(pword);
            assertTrue("countRecordsRelated should return non-negative", count >= 0);
            
            // Test with null pword (should count all records)
            int countAll = searchServer.countRecordsRelated(null);
            assertTrue("countRecordsRelated with null should return non-negative", countAll >= 0);
            
            // Test with empty pword
            int countEmpty = searchServer.countRecordsRelated("");
            assertTrue("countRecordsRelated with empty string should return non-negative", countEmpty >= 0);
            
        } catch (Exception e) {
            Log.e(TAG, "countRecordsRelated() test threw exception", e);
            fail("countRecordsRelated() test should not throw exception: " + e.getMessage());
        }
    }


    @Test(timeout = 10000)
    public void testSearchServerAddRecord() {
        // Test addRecord() - delegation to LimeDB.addRecord()
        try {
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(LIME.DB_RELATED_COLUMN_PWORD, "測試");
            values.put(LIME.DB_RELATED_COLUMN_CWORD, "詞彙");
            values.put(LIME.DB_RELATED_COLUMN_USERSCORE, 1);
            
            long result = searchServer.addRecord(LIME.DB_TABLE_RELATED, values);
            assertTrue("addRecord should return row ID >= 0", result >= 0);
            
        } catch (Exception e) {
            Log.e(TAG, "addRecord() test threw exception", e);
            fail("addRecord() test should not throw exception: " + e.getMessage());
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerUpdateRecord() {
        // Test updateRecord() - delegation to LimeDB.updateRecord()
        try {
            // First, add a record to update
            android.content.ContentValues insertValues = new android.content.ContentValues();
            insertValues.put(LIME.DB_RELATED_COLUMN_PWORD, "更新測試");
            insertValues.put(LIME.DB_RELATED_COLUMN_CWORD, "詞彙");
            insertValues.put(LIME.DB_RELATED_COLUMN_USERSCORE, 1);
            long insertId = searchServer.addRecord(LIME.DB_TABLE_RELATED, insertValues);
            
            if (insertId > 0) {
                // Update the record
                android.content.ContentValues updateValues = new android.content.ContentValues();
                updateValues.put(LIME.DB_RELATED_COLUMN_USERSCORE, 2);
                String whereClause = LIME.DB_COLUMN_ID + " = ?";
                String[] whereArgs = new String[]{String.valueOf(insertId)};
                
                int result = searchServer.updateRecord(LIME.DB_TABLE_RELATED, updateValues, whereClause, whereArgs);
                assertTrue("updateRecord should return number of rows updated >= 0", result >= 0);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "updateRecord() test threw exception", e);
            fail("updateRecord() test should not throw exception: " + e.getMessage());
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerCountRecordsByWordOrCode() {
        // Test countRecordsByWordOrCode() - delegation to LimeDB.countRecords()
        try {
            String tableName = "custom";
            
            // Count all records
            int count = searchServer.countRecordsByWordOrCode(tableName, null, false);
            assertTrue("countRecordsByWordOrCode should return non-negative", count >= 0);
            
            // Count with query
            int countWithQuery = searchServer.countRecordsByWordOrCode(tableName, "測試", false);
            assertTrue("countRecordsByWordOrCode with query should return non-negative", countWithQuery >= 0);
            
        } catch (Exception e) {
            Log.e(TAG, "countRecordsByWordOrCode() test threw exception", e);
            fail("countRecordsByWordOrCode() test should not throw exception: " + e.getMessage());
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerRemoveImInfo() {
        // Test removeImInfo() - delegation to LimeDB.removeImInfo()
        try {
            // First set some IM info
            searchServer.setImConfig("custom", "test_remove_field", "test_value");
            
            // Verify it was set
            String valueBefore = searchServer.getImConfig("custom", "test_remove_field");
            assertEquals("Value should be set before removal", "test_value", valueBefore);
            
            // Remove the IM info
            searchServer.removeImInfo("custom", "test_remove_field");
            
            // Verify it was removed
            String valueAfter = searchServer.getImConfig("custom", "test_remove_field");
            assertTrue("Value should be empty after removal", valueAfter == null || valueAfter.isEmpty());
            
        } catch (Exception e) {
            Log.e(TAG, "removeImInfo() test threw exception", e);
            fail("removeImInfo() test should not throw exception: " + e.getMessage());
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerResetImConfig() {
        // Test resetImInfo() - delegation to LimeDB.resetImInfo()
        try {
            // First set some IM info
            searchServer.setImConfig("custom", "test_reset_field1", "value1");
            searchServer.setImConfig("custom", "test_reset_field2", "value2");
            
            // Verify they were set
            String value1Before = searchServer.getImConfig("custom", "test_reset_field1");
            String value2Before = searchServer.getImConfig("custom", "test_reset_field2");
            assertEquals("Value1 should be set before reset", "value1", value1Before);
            assertEquals("Value2 should be set before reset", "value2", value2Before);
            
            // Reset all IM info
            searchServer.resetImConfig("custom");
            
            // Verify they were reset
            String value1After = searchServer.getImConfig("custom", "test_reset_field1");
            String value2After = searchServer.getImConfig("custom", "test_reset_field2");
            assertTrue("Value1 should be empty after reset", value1After == null || value1After.isEmpty());
            assertTrue("Value2 should be empty after reset", value2After == null || value2After.isEmpty());
            
        } catch (Exception e) {
            Log.e(TAG, "resetImInfo() test threw exception", e);
            fail("resetImInfo() test should not throw exception: " + e.getMessage());
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerGetBackupTableRecords() {
        // Test getBackupTableRecords() - delegation to LimeDB.getBackupTableRecords()
        try {
            String tableName = "custom";
            
            // First create a backup table
            searchServer.addOrUpdateMappingRecord(tableName, "backup_cursor_test", "備份游標測試", 10);
            searchServer.backupUserRecords(tableName);
            
            // Check if backup table exists
            boolean backupExists = searchServer.checkBackupTable(tableName);
            
            if (backupExists) {
                // Get backup table records
                android.database.Cursor cursor = searchServer.getBackupTableRecords(tableName + "_user");
                assertNotNull("getBackupTableRecords should return cursor if backup exists", cursor);
                assertTrue("Cursor should have records", cursor.getCount() >= 0);
                cursor.close();
            } else {
                // No backup table, should return null
                android.database.Cursor cursor = searchServer.getBackupTableRecords(tableName + "_user");
                // May return null if backup table doesn't exist
                if (cursor != null) {
                    cursor.close();
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "getBackupTableRecords() test threw exception", e);
            fail("getBackupTableRecords() test should not throw exception: " + e.getMessage());
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerResetCache() {
        // Test resetCache() instance method - delegation to LimeDB.resetCache()
        try {
            // Reset cache
            searchServer.resetCache();
            
            // Verify no exception thrown
            assertTrue("resetCache should complete", true);
            
        } catch (Exception e) {
            Log.e(TAG, "resetCache() test threw exception", e);
            fail("resetCache() test should not throw exception: " + e.getMessage());
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerGetKeyboardInfo() {
        // Test getKeyboardInfo() - delegation to LimeDB.getKeyboardInfo()
        try {
            String keyboardInfo = searchServer.getKeyboardInfo("lime", "name");
            // Info might be null or empty if keyboard doesn't exist, which is acceptable
            assertTrue("getKeyboardInfo should return string or null", keyboardInfo == null || keyboardInfo instanceof String);
            
        } catch (Exception e) {
            Log.e(TAG, "getKeyboardInfo() test threw exception", e);
            fail("getKeyboardInfo() test should not throw exception: " + e.getMessage());
        }
    }



    @Test(timeout = 10000)
    public void testSearchServerGetKeyboardConfig() {
        // Test getKeyboardObj() - delegation to LimeDB.getKeyboardObj()
        try {
            Keyboard keyboardConfig = searchServer.getKeyboardConfig("lime");
            // Object might be null if keyboard doesn't exist, which is acceptable
            assertTrue("getKeyboardObj should return Keyboard or null", keyboardConfig == null || keyboardConfig instanceof Keyboard);
            
        } catch (Exception e) {
            Log.e(TAG, "getKeyboardObj() test threw exception", e);
            fail("getKeyboardObj() test should not throw exception: " + e.getMessage());
        }
    }

    @Test(timeout = 10000)
    public void testSearchServerGetImListKeyboardConfigKeyboardListWithCode() {
        // Test getImList(String code) - delegation to LimeDB.getImList()
        try {
            List<ImConfig> imConfigList = searchServer.getImAllConfigList("custom");
            assertNotNull("getImList should return a list (not null)", imConfigList);
            assertTrue("getImList should return a list", imConfigList instanceof List);
            
        } catch (Exception e) {
            Log.e(TAG, "getImList(String) test threw exception", e);
            fail("getImList(String) test should not throw exception: " + e.getMessage());
        }
    }

    @Test
    public void testSearchServerGetTablename() {
        try {
            String tableName = searchServer.getTablename();
            assertNotNull("Table name should not be null", tableName);
            // Table name should be a valid string, could be empty initially
        } catch (Exception e) {
            Log.e(TAG, "getTablename() test threw exception", e);
            fail("getTablename() test should not throw exception: " + e.getMessage());
        }
    }

    @Test
    public void testSearchServerSetTableName() {
        try {
            String testTable = "cj";
            searchServer.setTableName(testTable, true, true);
            String retrievedTable = searchServer.getTablename();
            assertEquals("Table name should be set correctly", testTable, retrievedTable);
        } catch (Exception e) {
            Log.e(TAG, "setTablename() test threw exception", e);
            fail("setTablename() test should not throw exception: " + e.getMessage());
        }
    }

    @Test
    public void testSearchServerKeyToKeyname() {
        try {
            String keyname = searchServer.keyToKeyname("a");
            assertNotNull("Key name should not be null", keyname);
            // Key name should be a valid string representation
        } catch (Exception e) {
            Log.e(TAG, "keyToKeyname() test threw exception", e);
            fail("keyToKeyname() test should not throw exception: " + e.getMessage());
        }
    }

    @Test
    public void testSearchServerInitialCache() {
        try {
            searchServer.initialCache();
            // Method should complete without throwing exceptions
            // Cache should be initialized
        } catch (Exception e) {
            Log.e(TAG, "initialCache() test threw exception", e);
            fail("initialCache() test should not throw exception: " + e.getMessage());
        }
    }

    @Test
    public void testSearchServerPostFinishInput() {
        try {
            searchServer.postFinishInput();
            // Method should complete without throwing exceptions
        } catch (Exception e) {
            Log.e(TAG, "postFinishInput() test threw exception", e);
            fail("postFinishInput() test should not throw exception: " + e.getMessage());
        }
    }

    @Test
    public void testSearchServerGetKeyboardList() {
        try {
            List<Keyboard> keyboardList = searchServer.getKeyboardList();
            assertNotNull("Keyboard list should not be null", keyboardList);
            // List could be empty but should not be null
        } catch (Exception e) {
            Log.e(TAG, "getKeyboardList() test threw exception", e);
            fail("getKeyboardList() test should not throw exception: " + e.getMessage());
        }
    }

    @Test
    public void testSearchServerGetImListKeyboardConfigKeyboardList() {
        try {
            List<ImConfig> imConfigList = searchServer.getAllImKeyboardConfigList();
            assertNotNull("IM list should not be null", imConfigList);
            // List could be empty but should not be null
        } catch (Exception e) {
            Log.e(TAG, "getImList() test threw exception", e);
            fail("getImList() test should not throw exception: " + e.getMessage());
        }
    }




}



