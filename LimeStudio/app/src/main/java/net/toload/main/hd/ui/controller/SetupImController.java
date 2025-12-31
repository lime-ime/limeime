package net.toload.main.hd.ui.controller;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import net.toload.main.hd.data.ImConfig;
import net.toload.main.hd.ui.view.MainActivityView;
import net.toload.main.hd.ui.view.NavigationDrawerView;
import net.toload.main.hd.ui.view.NavigationMenuItem;
import net.toload.main.hd.ui.view.SetupImView;
import net.toload.main.hd.DBServer;
import net.toload.main.hd.SearchServer;
import net.toload.main.hd.ui.view.NavigationDrawerFragment;
import net.toload.main.hd.ui.NavigationManager;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEUtilities;
import net.toload.main.hd.R;

import net.toload.main.hd.ui.dialog.ImportDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller responsible for IM setup and import/export operations.
 *
 * <p>Handles initialization of IM-related menu items, button state management,
 * import/export, backup/restore flows, and provides view callback wiring for
 * the setup UI.
 */
public class SetupImController extends BaseController implements ImportDialog.OnImportIMSelectedListener {
    private static final String TAG = "SetupImController";
    
    private final Context context;
    private final DBServer dbServer;
    private final SearchServer searchServer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private MainActivityView mainActivityView;
    private NavigationDrawerView navigationDrawerView;
    private SetupImView setupImView;
    private NavigationManager navigationManager;

    private NavigationDrawerFragment.NavigationDrawerCallbacks navigationCallbacks;
    private File fileToImport;
    
    public SetupImController(Context context, DBServer dbServer, SearchServer searchServer) {
        this.context = context.getApplicationContext();
        this.dbServer = dbServer;
        this.searchServer = searchServer;
    }
    
    public void setMainActivityView(MainActivityView view) {
        this.mainActivityView = view;
    }
    
    public void setNavigationDrawerView(NavigationDrawerView view) {
        this.navigationDrawerView = view;
    }
    
    public void setNavigationCallbacks(NavigationDrawerFragment.NavigationDrawerCallbacks callbacks) {
        this.navigationCallbacks = callbacks;
    }

    public void setNavigationManager(NavigationManager manager) {
        this.navigationManager = manager;
    }

    public void setSetupImView(SetupImView view) {
        this.setupImView = view;
    }
    
    /**
     * Sets the file to import (called by IntentHandler or MainActivity).
     * @param file The file to import
     */
    public void setFileToImport(File file) {
        this.fileToImport = file;
    }


    /**
     * Callback method called when user selects a IM table for import in ImportDialog.
     * 
     * <p>This method is called after the user selects which IM table to import the
     * file into. It directly calls importTxtTable with the selected parameters.
     * 
     * @param tableName The IM table selected for import
     * @param restoreUserRecords If true, restores user-learned records from backup table after import
     */

    @Override
    public void onImportDialogImSelected(String tableName, boolean restoreUserRecords) {
        if (fileToImport == null) {
            Log.e(TAG, "No file set for import");
            handleError(setupImView, "No file selected for import", null);
            return;
        }
        importTxtTable(fileToImport, tableName, restoreUserRecords);
    }

    public void loadNavigationMenu() {
        List<NavigationMenuItem> items;
        
        // Skip loading if server is not initialized (e.g., in test mode)
        if (searchServer == null) {
            // Provide minimal default menu items for test mode
            items = new ArrayList<>();
            items.add(new NavigationMenuItem(null, 0, false)); // Initial
            items.add(new NavigationMenuItem(null, 1, false)); // Related
        } else {
            items = addImNavigationMenuItem();
        }
        
        if (navigationDrawerView != null) {
            navigationDrawerView.updateMenuItems(items);
        }
    }

    public Map<String, Boolean> loadButtonStates() {
        Map<String, Boolean> buttonStates = new HashMap<>();
        // Skip loading if server is not initialized (e.g., in test mode)
        if (searchServer == null) {
            return buttonStates;
        }
        
        try {
            List<ImConfig> imConfigList = searchServer.getImConfigList(null, LIME.IM_FULL_NAME);
            for (ImConfig imConfig : imConfigList) {
                String imName = imConfig.getCode();
                if (imName != null) {
                    String amount = searchServer.getImConfig(imName, "amount");
                    boolean isAvailable = amount != null && !amount.isEmpty() && !amount.equals("0");
                    buttonStates.put(imName, isAvailable);
                }
            }

            try {
                refreshSetupImButtonStates();
            } catch (Exception updateException) {
                Log.e(TAG, "Failed to update button buttonStates (fragment may be detached)", updateException);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to load button buttonStates", e);
                        }
        return buttonStates;
    }

    public List<ImConfig> getImConfigList() {
        List<ImConfig> result = searchServer.getImConfigList(null, LIME.IM_FULL_NAME);
        return result != null ? result : new ArrayList<>();
    }
    
    private List<NavigationMenuItem> addImNavigationMenuItem() {
        List<NavigationMenuItem> items = new ArrayList<>();

        try {
            List<ImConfig> imConfigList = searchServer.getImConfigList(null, LIME.IM_FULL_NAME);

            // Also update NavigationManager's imList
            if (navigationManager != null) {
                navigationManager.setImConfigFullNameList(imConfigList);
            }

            // Add default menu items
            items.add(new NavigationMenuItem(null, 0, false)); // Initial
            items.add(new NavigationMenuItem(null, 1, false)); // Related

            // Add IM menu items
            for (int i = 0; i < imConfigList.size(); i++) {
                ImConfig imConfig = imConfigList.get(i);
                items.add(new NavigationMenuItem(imConfig, i + 2, false));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load menu items", e);
        }

        return items;
    }
    
    public void onNavigationItemSelected(int position) {
        if (navigationCallbacks != null) {
            navigationCallbacks.onNavigationDrawerItemSelected(position);
        }
        
        if (navigationDrawerView != null) {
            navigationDrawerView.setSelectedItem(position);
        }
    }


    
    public boolean isValidTableName(String tableName) {
        try {
            return searchServer.isValidTableName(tableName);
        } catch (Exception e) {
            handleError(mainActivityView, "Table name validation failed", e);
            return false;
        }
    }

    public int countRecords(String tableName) {
        try {
            return searchServer.countRecords(tableName);
        } catch (Exception e) {
            handleError(mainActivityView, "Failed to count records", e);
            return 0;
        }
    }

    public void clearTable(String tableName, boolean backupUserRecords) {
        if (backupUserRecords) {
            searchServer.backupUserRecords(tableName);
        }
        searchServer.clearTable(tableName);
        refreshSetupImButtonStates();
    }

    /**
     * Performs a complete backup of the database to the specified URI.
     * This includes backing up custom user records and the entire database.
     * 
     * @param uri The URI where the backup should be saved
     */
    public void performBackup(Uri uri) throws Exception {
        try {
            showProgress(mainActivityView, "Backing up database...");
            dbServer.backupDatabase(uri);
            hideProgress(mainActivityView);
        } catch (Exception e) {
            handleError(mainActivityView, "Failed to backup database", e);
            throw e;
        }
    }

    /**
     * Performs a complete restore of the database from the specified URI.
     * This includes restoring all IM tables, related phrases, and shared preferences.
     * 
     * @param uri The URI of the backup file to restore from
     */
    public void performRestore(Uri uri) {
        try {
            showProgress(mainActivityView, "Restoring database...");
            dbServer.restoreDatabase(uri);
            hideProgress(mainActivityView);
            
            // Refresh the menu and UI after restore
            refreshNavigationMenu();
            refreshSetupImButtonStates();
        } catch (Exception e) {
            handleError(mainActivityView, "Failed to restore database", e);
        }
    }

    /**
     * Imports the default related database from raw resources.
     * 
     * <p>This method imports the default related database file from raw resources.
     * The default related database is always zipped, so this method handles the
     * unzip operation before importing.
     * 
     * <p>This method:
     * <ul>
     *   <li>Copies the zipped database file from raw resources to a temporary location</li>
     *   <li>Delegates to DBServer.importDbRelated() </li>
     * </ul>
     */
    public void importDbDefaultRelated() {
        try {
            showProgress(mainActivityView, context.getString(R.string.setup_im_import_related_default));
            
            File limeDbPath = new File(context.getCacheDir(), LIME.DATABASE_NAME);
            // Copy zipped database file from raw resources
            LIMEUtilities.copyRAWFile(context.getResources().openRawResource(R.raw.lime), limeDbPath);
            if(limeDbPath.exists()){
                dbServer.importDbRelated(limeDbPath);
                limeDbPath.deleteOnExit();
            }else {
                handleError(setupImView, context.getString(R.string.error_import_db), null);
            }
            refreshNavigationMenu();
            hideProgress(mainActivityView);

        } catch (Exception e) {
            hideProgress(mainActivityView);
            handleError(setupImView, context.getString(R.string.error_import_db), e);
        }
    }

    /**
     * Imports a compressed related database file (.limedb).
     * 
     * <p>This method delegates to DBServer.importZippedDbRelated() which handles
     * the unzip operation and database import. File operations are centralized
     * in DBServer to maintain architecture compliance.
     * 
     * @param unit The compressed related database file (.limedb) to import
     */
    public void importZippedDbRelated(File unit) {
        try {
            showProgress(mainActivityView, context.getString(R.string.setup_im_import_related));
            
            dbServer.importZippedDbRelated(unit);
            
            refreshNavigationMenu();
            
            hideProgress(mainActivityView);
            showToast(mainActivityView, context.getString(R.string.setup_im_import_complete), android.widget.Toast.LENGTH_LONG);

        } catch (Exception e) {
            hideProgress(mainActivityView);
            handleError(setupImView, context.getString(R.string.error_import_db), e);
        }
    }


    public void importZippedDb(File fileToImport, String tableName, boolean restoreUserRecords) {
        if ("related".equals(tableName)) {
            try {
                showProgress(mainActivityView, context.getString(R.string.setup_im_import_related));
                dbServer.importZippedDbRelated(fileToImport);
                searchServer.resetCache();
                showToast(mainActivityView, context.getString(R.string.setup_im_import_complete), android.widget.Toast.LENGTH_SHORT);
            } catch (Exception e) {
                handleError(mainActivityView, context.getString(R.string.error_import_db), e);
            } finally {
                hideProgress(mainActivityView);
            }
        } else {
            if (!isValidTableName(tableName)) {
                hideProgress(mainActivityView);
                handleError(setupImView, context.getString(R.string.error_table_name)+": " + tableName, null);
                return;
            }
            try {
                showProgress(mainActivityView, context.getString(R.string.setup_im_dialog_import_confirm_title));
                if (restoreUserRecords && countRecords(tableName) > 0) {
                    try {
                        searchServer.backupUserRecords(tableName);
                    } catch (Exception e) {
                        handleError(setupImView,context.getString(R.string.error_backup_user_records),e);
                    }
                }
                dbServer.importZippedDb(fileToImport, tableName);
                searchServer.resetCache();
                if (restoreUserRecords) {
                    try {
                        if (searchServer.checkBackupTable(tableName)) {
                            searchServer.restoreUserRecords(tableName);
                        }
                    } catch (Exception e) {
                        handleError(setupImView, context.getString(R.string.error_backup_user_records), e);
                    }
                }


            } catch (Exception e) {
                handleError(mainActivityView, context.getString(R.string.error_import_db) + e.getMessage(), e);
            } finally {
                hideProgress(mainActivityView);
                showToast(mainActivityView, context.getString(R.string.setup_im_import_complete), android.widget.Toast.LENGTH_SHORT);
                searchServer.resetCache();
                refreshNavigationMenu();
                refreshSetupImButtonStates();
            }
        }
    }

    /**
     * Downloads an IM database from the cloud and imports it.
     */
    public void downloadAndImportZippedDb(String tableName, String url, boolean restoreLearning) {
        if (context == null) {
            handleError(setupImView, "Context unavailable for download", null);
            return;
        }


        if (!isNetworkAvailable() || url == null || url.isEmpty()) {
            handleError(setupImView, context.getString(R.string.l3_tab_initial_error), null);
            return;
        }

        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                try {
                    showProgress(mainActivityView, context.getString(R.string.setup_load_download));

                    File tempfile = LIMEUtilities.downloadRemoteFile(
                            url,
                            null,
                            context.getCacheDir(),
                            percent -> updateProgress(mainActivityView, percent, context.getString(R.string.setup_load_download)),
                            null
                    );

                    final int minFileSizeBytes = 100000;
                    if (tempfile == null || tempfile.length() < minFileSizeBytes) {
                        hideProgress(mainActivityView);
                        handleError(setupImView, context.getString(R.string.error_import_db), null);
                        return;
                    }

                    importZippedDb(tempfile, tableName, restoreLearning);

                } catch (Exception e) {
                    hideProgress(mainActivityView);
                    handleError(setupImView, context.getString(R.string.error_import_db), e);
                }
            });
        } finally {
            executor.shutdown();
        }
    }

    public void restoredToDefault() {
        try {
            searchServer.restoredToDefault();
            showToast(mainActivityView, "Settings reset to defaults", android.widget.Toast.LENGTH_SHORT);
            refreshNavigationMenu();
        } catch (Exception e) {
            handleError(mainActivityView, "Failed to reset settings", e);
        }
    }

    private void refreshNavigationMenu() {
        mainHandler.post(this::loadNavigationMenu);
    }

    private void refreshSetupImButtonStates() {
        if(setupImView != null)
            mainHandler.post(setupImView::refreshButtonState);
    }



    public File exportTxtTable(String tableName, File targetFile, Runnable onProgress) {
        try {
            showProgress(mainActivityView, context.getString(R.string.setup_load_migrate_export));
            
            List<ImConfig> imConfigList = searchServer.getImAllConfigList(tableName);
            if (onProgress != null) {
                onProgress.run();
            }
            
            final File[] resultFile = {null};
            
            // Use progress listener to get real-time export progress
            dbServer.exportTxtTable(tableName, targetFile, imConfigList, new net.toload.main.hd.global.LIMEProgressListener() {
                @Override
                public void onProgress(long percentageDone, long var2, String status) {
                    updateProgress(mainActivityView, (int) percentageDone, status != null ? status : context.getString(R.string.setup_load_migrate_export));
                }

                @Override
                public void onError(int code, String source) {
                    hideProgress(mainActivityView);
                    if (setupImView != null && source != null && !source.isEmpty()) setupImView.onError(source);

                }

                @Override
                public void onPostExecute(boolean success, String status, int code) {
                    if (success) {
                        resultFile[0] = targetFile;
                    }
                    hideProgress(mainActivityView);
                    if (success) showToast(mainActivityView, context.getString(R.string.setup_load_export_finish), android.widget.Toast.LENGTH_SHORT);

                }
            });
            
            return resultFile[0];
        } catch (Exception e) {
            handleError(setupImView, context.getString(R.string.error_export_table), e);
            hideProgress(mainActivityView);

            return null;
        }
    }

    public File exportZippedDb(String tableName, File targetFile, Runnable onProgress) {
        try {
            showProgress(mainActivityView, context.getString(R.string.setup_load_migrate_export));
            
            if (targetFile != null && targetFile.exists() && !targetFile.delete()) {
                Log.w(TAG, "exportZippedDb: failed to delete existing target file");
            }

            File result = dbServer.exportZippedDb(tableName, targetFile, onProgress);
            
            hideProgress(mainActivityView);
            return result;
        } catch (Exception e) {
            hideProgress(mainActivityView);
            handleError(setupImView, context.getString(R.string.error_export_table), e);
            return null;
        }
    }

    public File exportTxtTableRelated(File targetFile, Runnable onProgress) {
        try {
            showProgress(mainActivityView, context.getString(R.string.setup_load_migrate_export));

            List<ImConfig> imConfigInfo = searchServer.getImAllConfigList(LIME.DB_TABLE_RELATED);
            if (onProgress != null) {
                onProgress.run();
            }
            
            final File[] resultFile = {null};
            
            // Use progress listener to get real-time export progress
            dbServer.exportTxtTable(LIME.DB_TABLE_RELATED, targetFile, imConfigInfo, new net.toload.main.hd.global.LIMEProgressListener() {
                @Override
                public void onProgress(long percentageDone, long var2, String status) {
                    updateProgress(mainActivityView, (int) percentageDone, status != null ? status : "Exporting...");
                }

                @Override
                public void onStatusUpdate(String status) {
                    if (status != null && !status.isEmpty()) {
                        updateProgress(mainActivityView, 0, status);
                    }
                }

                @Override
                public void onError(int code, String source) {
                    hideProgress(mainActivityView);
                    if (mainActivityView != null && source != null && !source.isEmpty()) {
                        mainActivityView.onError(source);
                    }
                }

                @Override
                public void onPostExecute(boolean success, String status, int code) {
                    if (success) {
                        resultFile[0] = targetFile;
                    }

                    hideProgress(mainActivityView);
                    if (success)
                        showToast(mainActivityView, "Export complete", android.widget.Toast.LENGTH_SHORT);
                }
            });
            
            return resultFile[0];
        } catch (Exception e) {
            handleError(setupImView, context.getString(R.string.error_export_table), e);
            hideProgress(mainActivityView);

            return null;
        }
    }

    public File exportZippedDbRelated(File targetFile, Runnable onProgress) {
        try {
            showProgress(mainActivityView, context.getString(R.string.setup_load_migrate_export));
            
            if (targetFile != null && targetFile.exists() && !targetFile.delete()) {
                Log.w(TAG, "exportRelatedZippedDb: failed to delete existing target file");
            }

            
            File result = dbServer.exportZippedDbRelated(targetFile, onProgress);
            
            hideProgress(mainActivityView);

            return result;
        } catch (Exception e) {
            handleError(setupImView, context.getString(R.string.error_export_table), e);
            hideProgress(mainActivityView);
            return null;
        }
    }




    /**
     * Imports a text file into an IM table with optional user record restore.
     */
    public void importTxtTable(File file, String tableName, boolean restoreUserRecords) {
        if (!searchServer.isValidTableName(tableName)) {
            handleError(setupImView, context.getString(R.string.error_table_name) + ": " + tableName, null);
            return;
        }

        
        try {
            showProgress(mainActivityView, context.getString(R.string.setup_im_import_standard));
            try {
                searchServer.backupUserRecords(tableName);
            } catch (Exception e) {
                handleError(setupImView, context.getString(R.string.error_backup_user_records), e);
            }

            dbServer.importTxtTable(file.getAbsolutePath(), tableName, new net.toload.main.hd.global.LIMEProgressListener() {
                @Override
                public void onProgress(long percentageDone, long var2, String status) {
                    updateProgress(mainActivityView, (int) percentageDone, status != null ? status : "");
                }

                @Override
                public void onStatusUpdate(String status) {
                    if (status != null && !status.isEmpty()) {
                        updateProgress(mainActivityView, 0, status);
                    }
                }

                @Override
                public void onError(int code, String source) {
                    hideProgress(mainActivityView);
                    if (source != null && !source.isEmpty()) {
                        handleError(setupImView, source, null);
                    }
                }

                @Override
                public void onPostExecute(boolean success, String status, int code) {
                    if (success) searchServer.resetCache();

                    updateProgress(mainActivityView, 100, context.getString(R.string.setup_im_import_complete));
                    hideProgress(mainActivityView);

                    if (restoreUserRecords && success) {
                        if (searchServer.checkBackupTable(tableName)) {
                            showProgress(mainActivityView, context.getString(R.string.setup_im_restore_learning_data));
                            searchServer.restoreUserRecords(tableName);
                            hideProgress(mainActivityView);
                        }
                    }
                    refreshSetupImButtonStates();
                    refreshNavigationMenu();
                }
            });
        } catch (Exception e) {
            handleError(setupImView, context.getString(R.string.error_import_db), e);
        }

    }


    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            );
        } else {
            @SuppressWarnings("deprecation")
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            if (activeNetworkInfo != null) {
                @SuppressWarnings("deprecation")
                boolean isConnected = activeNetworkInfo.isConnected();
                return isConnected;
            }
            return false;
        }
    }

}




