package net.toload.main.hd.ui.controller;

import net.toload.main.hd.data.ImConfig;
import net.toload.main.hd.data.Keyboard;
import net.toload.main.hd.ui.view.ManageImView;
import net.toload.main.hd.ui.view.ManageRelatedView;
import net.toload.main.hd.SearchServer;
import net.toload.main.hd.data.Record;
import net.toload.main.hd.data.Related;
import net.toload.main.hd.global.LIME;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for IM-related operations.
 * 
 * <p>This controller handles:
 * <ul>
 *   <li>IM setup operations (button states, IM info)</li>
 *   <li>IM file operations (import/export IM databases, backup/restore)</li>
 *   <li>Record CRUD operations (ManageIm)</li>
 *   <li>Related phrase CRUD operations (ManageRelated)</li>
 *   <li>Search and filter logic</li>
 * </ul>
 */
public class ManageImController extends BaseController {
    private static final String TAG = "ManageImController";
    
    private final SearchServer searchServer;

    // Executor used for asynchronous controller operations
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    private ManageImView manageImView;
    private ManageRelatedView manageRelatedView;
    
    /**
     * Creates a new ManageImController.
     * 
     * @param searchServer The SearchServer instance for database operations
     */
    public ManageImController(SearchServer searchServer) {
        this.searchServer = searchServer;
    }


    
    /**
     * Sets the ManageIm view for this controller.
     * 
     * @param view The ManageImView implementation
     */
    public void setManageImView(ManageImView view) {
        this.manageImView = view;
    }
    
    /**
     * Sets the ManageRelated view for this controller.
     * 
     * @param view The ManageRelatedView implementation
     */
    public void setManageRelatedView(ManageRelatedView view) {
        this.manageRelatedView = view;
    }

    /**
     * Loads records asynchronously using the controller's executor. The view's
     * callback methods are used for progress and results; the view implementations
     * are expected to marshal UI updates to the main thread as needed.
        *
        * @param table the database table name to query
        * @param query the search query string (word or code depending on searchByCode)
        * @param searchByCode true to search by code, false to search by word
        * @param offset pagination offset
        * @param limit pagination limit (max number of records to return)
     */
    public void loadRecordsAsync(final String table, final String query, final boolean searchByCode, final int offset, final int limit) {
        if (searchServer == null) {
            // Validation errors are synchronous - log and report immediately
            android.util.Log.e(TAG, "SearchServer not initialized");
            if (manageImView != null) {
                manageImView.onError("SearchServer not initialized");
            }
            return;
        }
        if (!searchServer.isValidTableName(table)) {
            // Validation errors are synchronous - log and report immediately
            android.util.Log.e(TAG, "Invalid table name: " + table);
            if (manageImView != null) {
                manageImView.onError("Invalid table name: " + table);
            }
            return;
        }
        // Run the operation in a background executor thread
        executor.submit(() -> {
            try {


                // Diagnostic logging
                android.util.Log.i(TAG, "loadRecordsAsync(): table=" + table + ", query=" + query + ", searchByCode=" + searchByCode + ", offset=" + offset + ", limit=" + limit);

                List<Record> records = searchServer.getRecords(table, query, searchByCode, limit, offset);
                int count = searchServer.countRecordsByWordOrCode(table, query, searchByCode);

                android.util.Log.i(TAG, "loadRecordsAsync(): result size=" + (records == null ? "null" : records.size()) + ", count=" + count);

                if (manageImView != null) {
                    // Post UI updates to main thread
                    mainHandler.post(() -> {
                        manageImView.updateRecordCount(count);
                        manageImView.displayRecords(records);
                    });
                }
            } catch (Exception e) {
                handleError(manageImView, "Failed to load records async: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Adds a new record.
     * 
     * @param table The table name
     * @param code The code
     * @param word The word
     * @param score The score
     */
    public void addRecord(String table, String code, String word, int score) {
        if (!searchServer.isValidTableName(table)) {
            handleError(manageImView, "Invalid table name: " + table, null);
            return;
        }
        
        try {
            searchServer.addOrUpdateMappingRecord(table, code, word, score);
            
            if (manageImView != null) {
                manageImView.refreshRecordList();
            }
        } catch (Exception e) {
            handleError(manageImView, "Failed to add record: " + e.getMessage(), e);
        }
    }

    
    /**
     * Updates an existing record.
     * 
     * @param table The table name
     * @param id The record ID
     * @param code The new code
     * @param word The new word
     * @param score The new score
     */
    public void updateRecord(String table, long id, String code, String word, int score) {
        if (!searchServer.isValidTableName(table)) {
            handleError(manageImView, "Invalid table name: " + table, null);
            return;
        }
        
        try {
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(LIME.DB_COLUMN_CODE, code);
            cv.put(LIME.DB_COLUMN_WORD, word);
            cv.put(LIME.DB_COLUMN_SCORE, score);
            searchServer.updateRecord(table, cv, LIME.DB_COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
            
            if (manageImView != null) {
                manageImView.refreshRecordList();
            }
        } catch (Exception e) {
            handleError(manageImView, "Failed to update record: " + e.getMessage(), e);
        }
    }

    
    /**
     * Deletes a record.
     * 
     * @param table The table name
     * @param id The record ID
     */
    public void deleteRecord(String table, long id) {
        if (!searchServer.isValidTableName(table)) {
            handleError(manageImView, "Invalid table name: " + table, null);
            return;
        }
        
        try {
            searchServer.deleteRecord(table, LIME.DB_COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
            
            if (manageImView != null) {
                manageImView.refreshRecordList();
            }
        } catch (Exception e) {
            handleError(manageImView, "Failed to delete record: " + e.getMessage(), e);
        }
    }
    
    // ========== Related Phrase Operations ==========
    
    /**
     * Loads related phrases.
     * 
     * @param pWord The parent word (null for all)
     * @param offset The offset for pagination
     * @param limit The limit for pagination
     */
    public void loadRelatedPhrases(String pWord, int offset, int limit) {
        try {

            List<Related> phrases = searchServer.getRelatedByWord(pWord, limit, offset);
            int count = searchServer.countRecordsRelated(pWord);
            
            if (manageRelatedView != null) {
                manageRelatedView.updatePhraseCount(count);
                manageRelatedView.displayRelatedPhrases(phrases);
            }
        } catch (Exception e) {
            handleError(manageRelatedView, "Failed to load related phrases: " + e.getMessage(), e);
        }
    }
    
    /**
     * Adds a related phrase.
     * 
     * @param pWord The parent word
     * @param cWord The child word
     * @param score The score
     */
    public void addRelatedPhrase(String pWord, String cWord, int score) {
        try {
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(LIME.DB_RELATED_COLUMN_PWORD, pWord);
            cv.put(LIME.DB_RELATED_COLUMN_CWORD, cWord);
            cv.put(LIME.DB_RELATED_COLUMN_USERSCORE, score);
            if (searchServer.hasRelated(pWord, cWord)) {
                searchServer.updateRecord(LIME.DB_TABLE_RELATED, cv, LIME.DB_RELATED_COLUMN_PWORD + " = ? AND " + LIME.DB_RELATED_COLUMN_CWORD + " = ?", new String[]{pWord, cWord});
            } else {
                searchServer.addRecord(LIME.DB_TABLE_RELATED, cv);
            }
            
            if (manageRelatedView != null) {
                manageRelatedView.refreshPhraseList();
            }
        } catch (Exception e) {
            handleError(manageRelatedView, "Failed to add related phrase: " + e.getMessage(), e);
        }
    }
    
    /**
     * Updates a related phrase.
     * 
     * @param id The phrase ID
     * @param pWord The new parent word
     * @param cWord The new child word
     * @param score The new score
     */
    public void updateRelatedPhrase(long id, String pWord, String cWord, int score) {
        try {
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(LIME.DB_RELATED_COLUMN_PWORD, pWord);
            cv.put(LIME.DB_RELATED_COLUMN_CWORD, cWord);
            cv.put(LIME.DB_RELATED_COLUMN_BASESCORE, score);
            searchServer.updateRecord(LIME.DB_TABLE_RELATED, cv, LIME.DB_COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
            
            if (manageRelatedView != null) {
                manageRelatedView.refreshPhraseList();
            }
        } catch (Exception e) {
            handleError(manageRelatedView, "Failed to update related phrase: " + e.getMessage(), e);
        }
    }
    
    /**
     * Deletes a related phrase.
     * 
     * @param id The phrase ID
     */
    public void deleteRelatedPhrase(long id) {
        try {
            searchServer.deleteRecord(LIME.DB_TABLE_RELATED, LIME.DB_COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
            
            if (manageRelatedView != null) {
                manageRelatedView.refreshPhraseList();
            }
        } catch (Exception e) {
            handleError(manageRelatedView, "Failed to delete related phrase: " + e.getMessage(), e);
        }
    }
    
    /**
     * Returns the list of IMs.
     *
     * @return a non-null list of `Im` objects; may be empty when the server is uninitialized
     */
    public List<ImConfig> getImConfigFullNameList() {
        // Return empty list if server is not initialized (e.g., in test mode)
        if (searchServer == null) {
            return new java.util.ArrayList<>();
        }
        List<ImConfig> result = searchServer.getImConfigList(null, LIME.IM_FULL_NAME);
        return result != null ? result : new java.util.ArrayList<>();
    }

    /**
     * Returns the record count for a table.
     *
     * @param tableName the table to count records for
     * @return the number of records in the table, or 0 if the server is not initialized
     */
    public int countRecords(String tableName) {
        // Return 0 if server is not initialized (e.g., in test mode)
        if (searchServer == null) {
            return 0;
        }
        return searchServer.countRecords(tableName);
    }


    /**
     * Returns a list of available keyboards.
     *
     * @return a non-null list of `Keyboard` objects; may be empty
     */
    public List<Keyboard> getKeyboardList() {
        List<Keyboard> keyboards = searchServer.getKeyboard();
        return keyboards != null ? keyboards : new java.util.ArrayList<>();
    }

    /**
     * Sets the keyboard for an IM table.
     *
     * @param table the IM table name
     * @param keyboard the `Keyboard` to set for the table
     */
    public void setIMKeyboard(String table, Keyboard keyboard) {
        try {
            searchServer.setIMKeyboard(table, keyboard);
            if (manageImView != null) {
                // Signal completion and refresh the list so UI shows latest data
                manageImView.refreshRecordList();
                android.util.Log.i(TAG, "setIMKeyboard(): updated keyboard for table=" + table);
            }
        } catch (Exception e) {
            handleError(manageImView, "Failed to set keyboard", e);
        }
    }

    /**
     * Exposes the SearchServer instance for callers that need to run background tasks.
     *
     * @return the `SearchServer` used by this controller; may be null in tests
     */
    public SearchServer getSearchServer() {
        return this.searchServer;
    }








}

