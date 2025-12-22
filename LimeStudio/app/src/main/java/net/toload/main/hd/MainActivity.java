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

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;



import net.toload.main.hd.data.Im;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.ui.HelpDialog;
import net.toload.main.hd.ui.ImportDialog;
import net.toload.main.hd.ui.ManageImFragment;
import net.toload.main.hd.ui.ManageRelatedFragment;
import net.toload.main.hd.ui.NewsDialog;
import net.toload.main.hd.ui.SetupImFragment;
import net.toload.main.hd.ui.ShareDbRunnable;
import net.toload.main.hd.ui.ShareRelatedDbRunnable;
import net.toload.main.hd.ui.ShareRelatedTxtRunnable;
import net.toload.main.hd.ui.ShareTxtRunnable;
import net.toload.main.hd.ui.ImportDialog.OnImportTypeSelectedListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks, OnImportTypeSelectedListener {

    final String TAG = "MainActivity";
    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;
    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;
    //private CharSequence mCode;

    private SearchServer searchServer;
    private List<Im> imlist;

    private AlertDialog progress;
    private MainActivityHandler handler;
    private Thread sharethread;

    private File fileToImport;

    /**
     * Called when the activity is being destroyed.
     * 
     * <p>This is the final call the activity receives before it is destroyed. The activity
     * should release any remaining resources here.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * Called when the window focus changes (e.g., when the activity gains or loses focus).
     * 
     * <p>When the window gains focus and the SetupImFragment is visible, this method
     * refreshes the IM buttons to reflect the current state of the database.
     * 
     * @param hasFocus True if the window has focus, false otherwise
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        SetupImFragment ImFragment = (SetupImFragment) getSupportFragmentManager().findFragmentByTag("SetupImFragment");
        if (ImFragment == null) return;
        if (hasFocus && ImFragment.isVisible()) ImFragment.initialbutton();

    }

    /**
     * Handle back button press using OnBackPressedDispatcher for predictive back gesture support.
     * This replaces the deprecated onKeyDown() approach and provides better integration with
     * Android's navigation system, including support for predictive back gesture (API 33+).
     */
    private void setupBackPressHandler() {
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Show exit confirmation dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(getResources().getString(R.string.global_exit_title));
                builder.setCancelable(false);
                builder.setPositiveButton(getResources().getString(R.string.dialog_confirm),
                        (dialog, id) -> {
                            // Close all activities in the task and let Android manage process lifecycle
                            finishAffinity();
                        });
                builder.setNegativeButton(getResources().getString(R.string.dialog_cancel),
                        (dialog, id) -> {
                            // Dialog dismissed, stay in app
                        });

                AlertDialog alert = builder.create();
                alert.show();
            }
        };
        
        // Register the callback with OnBackPressedDispatcher
        // This enables predictive back gesture support (API 33+)
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    /**
     * Called when the activity is no longer visible to the user.
     * 
     * <p>This method is called after {@link #onPause()} when the activity is no longer
     * visible. The activity may be finishing or about to be destroyed.
     */
    @Override
    protected void onStop() {
        super.onStop();
    }


    /**
     * Called when the activity is first created.
     * 
     * <p>This method performs the following initialization:
     * <ul>
     *   <li>Enables edge-to-edge display for modern Android versions</li>
     *   <li>Sets up the ActionBar with white background</li>
     *   <li>Configures window insets for edge-to-edge display</li>
     *   <li>Sets up back button handler with exit confirmation</li>
     *   <li>Initializes the IM list from the database</li>
     *   <li>Sets up the navigation drawer</li>
     *   <li>Handles ACTION_SEND and ACTION_VIEW intents for file imports</li>
     *   <li>Shows help dialog for new app versions</li>
     * </ul>
     * 
     * <p>Supported intent actions:
     * <ul>
     *   <li>ACTION_SEND with text/plain: Opens ImportDialog for text import</li>
     *   <li>ACTION_VIEW with text/plain (.lime/.cin): Opens ImportDialog for file import</li>
     *   <li>ACTION_VIEW with application/zip (.limedb): Imports database file</li>
     * </ul>
     * 
     * @param savedInstanceState If the activity is being re-initialized after previously
     *                           being shut down, this Bundle contains the data it most
     *                           recently supplied in {@link #onSaveInstanceState(Bundle)}.
     *                           Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // Enable edge-to-edge display for API 35+ (Android 15+)
        // API 35 is required for edge-to-edge, but we enable it for all API levels for consistency
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_main);

        // Ensure ActionBar title is displayed with white background
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            // Set white background for actionbar
            actionBar.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE));
            // Remove elevation/shadow for cleaner look
            actionBar.setElevation(0);
        }

        // Handle window insets for edge-to-edge display
        setupEdgeToEdge();

        // Setup back button handler using OnBackPressedDispatcher for predictive back gesture support
        setupBackPressHandler();

        handler = new MainActivityHandler(this);

        //ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        LIMEPreferenceManager mLIMEPref = new LIMEPreferenceManager(this);

        LIME.PACKAGE_NAME = getApplicationContext().getPackageName();

        // initial imlist
        initialImList();

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        //mTitle = getTitle();

        // Set up the drawer.
        assert mNavigationDrawerFragment != null;
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                findViewById(R.id.drawer_layout));

        

        // Handle Import .lime/.cin (text/plain) and .limedb (application/zip)
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = getIntent().getType();
        


        // 1. For ACTION_SEND, use handleSendText() to process
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                handleSendText(getIntent());
            }
        } 
        // 2. For ACTION_VIEW, handle file imports
        else if (Intent.ACTION_VIEW.equals(action) && type != null) {
            String scheme = intent.getScheme();
            ContentResolver resolver = getContentResolver();

            if (ContentResolver.SCHEME_CONTENT.equals(scheme)
                    || ContentResolver.SCHEME_FILE.equals(scheme)
                    || scheme != null && (scheme.equals("http") || scheme.equals("https") || scheme.equals("ftp"))) {
                Uri uri = intent.getData();
                if (uri == null) {
                    Log.e(TAG, "Intent data URI is null");
                    return;
                }
                
                String fileName = getContentName(resolver, uri);
                if (fileName == null) {
                    fileName = uri.getLastPathSegment();
                }
                if (fileName == null) {
                    String errorMessage = getResources().getString(R.string.error_no_file_name);
                    Log.e(TAG, errorMessage);
                    showToastMessage(errorMessage, Toast.LENGTH_SHORT);
                    return;
                }
                
                String extension = getFileExtension(fileName);
                
                // 3. Check if type matches extension
                if (!isFileTypeValid(type, extension)) {
                    String errorMessage = getResources().getString(R.string.error_file_format);
                    Log.w(TAG, errorMessage);
                    showToastMessage(errorMessage, Toast.LENGTH_SHORT);
                    return;
                }
                
                InputStream input;
                try {
                    input = resolver.openInputStream(uri);
                } catch (FileNotFoundException e) {
                    String errorMessage = getResources().getString(R.string.error_file_opening_error);
                    Log.e(TAG, errorMessage, e);
                    showToastMessage(errorMessage, Toast.LENGTH_SHORT);
                    return;
                }

                File importDir = new File(getCacheDir(), "imports");
                if (!importDir.exists() && !importDir.mkdirs()) {
                    Log.w(TAG, "Failed to create import dir: " + importDir.getAbsolutePath());
                }
                fileToImport = new File(importDir, fileName);
                String importFilepath = fileToImport.getAbsolutePath();
                
                if (input != null) {
                    InputStreamToFile(input, importFilepath);
                } else {
                    Log.e(TAG, "Input stream is null");
                    showToastMessage("Error reading file", Toast.LENGTH_SHORT);
                    return;
                }
                
                // Handle based on file type
                if ("text/plain".equals(type) && ("lime".equals(extension) || "cin".equals(extension))) {
                    // 4. For text/plain with .lime or .cin, call handleImportTxt
                    handleImportTxt(importFilepath);
                } else if ("application/zip".equals(type) && "limedb".equals(extension)) {
                    // 5. For .limedb file, handle import
                    String tableName = getFileNameWithoutExtension(fileName);
                    DBServer dbServer = DBServer.getInstance(this);
                    
                    // Check if table is empty before importing
                    if (searchServer == null) {
                        searchServer = new SearchServer(this);
                    }
                    int recordCount = searchServer.countRecords(tableName);
                    if (recordCount > 0) {
                        // Table is not empty, show confirmation dialog
                        
                        String message = getResources().getString(R.string.setup_im_dialog_import_confirm_overwrite);
                        
                        new AlertDialog.Builder(this)
                            .setTitle(getResources().getString(R.string.setup_im_dialog_import_confirm_title))
                            .setMessage(message)
                            .setPositiveButton(getResources().getString(R.string.dialog_yes), (dialog, which) -> {
                                // User selected Yes: proceed with import and restore user records if available
                                searchServer.backupUserRecords(tableName);
                                performLimedbImport(dbServer, fileToImport, tableName);
                                if (searchServer.checkBackuptable(tableName)) {
                                    searchServer.restoreUserRecords(tableName);
                                }
                            })
                            .setNeutralButton(getResources().getString(R.string.dialog_no), (dialog, which) -> {
                                // User selected No: proceed with import but don't restore user records
                                performLimedbImport(dbServer, fileToImport, tableName);
                            })
                            .setNegativeButton(getResources().getString(R.string.dialog_cancel), (dialog, which) -> dialog.dismiss())
                            .show();
                    } else {
                        // Table is empty, proceed directly with import
                        performLimedbImport(dbServer, fileToImport, tableName);
                    }
                } else {
                    String errorMessage = getResources().getString(R.string.error_file_format);

                    Log.w(TAG, errorMessage + type + " with extension: " + extension);
                    showToastMessage(errorMessage, Toast.LENGTH_SHORT);
                }
            }
            

        }

        String versionStr = "";
        PackageInfo pInfo;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            long versionCode = PackageInfoCompat.getLongVersionCode(pInfo);
            versionStr = getString(R.string.version_format, pInfo.versionName, versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error getting package info", e);
        }

        String currentVersion = mLIMEPref.getParameterString("current_version", "");
        if (currentVersion == null || currentVersion.isEmpty() || !currentVersion.equals(versionStr)) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            HelpDialog dialog = HelpDialog.newInstance();
            dialog.show(ft, "helpdialog");
            mLIMEPref.setParameter("current_version", versionStr);
        }

    }

    /**
     * Retrieves the display name of a content URI.
     * 
     * <p>This method queries the content resolver to get the display name (filename)
     * from a content URI. It uses MediaStore.MediaColumns.DISPLAY_NAME to extract
     * the filename.
     * 
     * @param resolver The ContentResolver to use for querying
     * @param uri The content URI to query
     * @return The display name (filename) if found, null otherwise
     */
    private String getContentName(ContentResolver resolver, Uri uri) {
        Cursor cursor = resolver.query(uri, null, null, null, null);
        if (cursor == null) return null;
        cursor.moveToFirst();
        int nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
        if (nameIndex >= 0) {
            return cursor.getString(nameIndex);
        } else {
            cursor.close();
            return null;
        }
    }

    /**
     * Writes an InputStream to a file.
     * 
     * <p>This method reads data from the input stream and writes it to the specified
     * file path. It uses a buffer of size {@link LIME#BUFFER_SIZE_64KB} for efficient
     * reading and writing.
     * 
     * <p>If an error occurs during the operation, it is logged but not thrown.
     * 
     * @param in The InputStream to read from
     * @param file The file path to write to
     */
    private void InputStreamToFile(InputStream in, String file) {
        try {
            OutputStream out = new FileOutputStream(file);

            int size;
            byte[] buffer = new byte[LIME.BUFFER_SIZE_64KB];

            while ((size = in.read(buffer)) != -1) {
                out.write(buffer, 0, size);
            }

            out.close();
        } catch (Exception e) {
            Log.e("MainActivity", "InputStreamToFile exception: " + e.getMessage());
        }
    }

    /**
     * Handles ACTION_SEND intent with text/plain type.
     * 
     * <p>This method is called when the activity receives an ACTION_SEND intent with
     * text/plain MIME type. It extracts the text from the intent and opens an
     * ImportDialog to allow the user to select which IM table to import the text into.
     * 
     * @param intent The ACTION_SEND intent containing the text to import
     */
    void handleSendText(Intent intent) {
        String importtext = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (importtext != null && !importtext.isEmpty()) {
            androidx.fragment.app.FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ImportDialog dialog = ImportDialog.newInstance(importtext);
            dialog.show(ft, "importdialog");
        }
    }
    
    /**
     * Handles import of a text file (.lime or .cin).
     * 
     * <p>This method is called when the activity receives an ACTION_VIEW intent with
     * a text/plain file (.lime or .cin). It opens an ImportDialog to allow the user
     * to select which IM table to import the file into.
     * 
     * @param filePath The path to the text file to import
     */
    void handleImportTxt(String filePath) {
        if (filePath != null && !filePath.isEmpty()) {
            androidx.fragment.app.FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ImportDialog dialog = ImportDialog.newInstanceForFile(filePath);
            dialog.setOnImportTypeSelectedListener(this);
            dialog.show(ft, "importdialog");
        }
    }
    
    /**
     * Callback method called when user selects an IM type for import in ImportDialog.
     * 
     * <p>This method is called after the user selects which IM table to import the
     * file into. It performs the actual import operation by calling
     * {@link SetupImFragment#importTxtTable(File, String, boolean)} if the fragment
     * is visible.
     * 
     * <p>The method shows a progress dialog during import and displays completion
     * or error messages as appropriate.
     * 
     * @param imType The IM type (table name) selected for import
     * @param restoreUserRecords If true, restores user-learned records from backup table after import
     */
    @Override
    public void onImportTypeSelected(String imType, boolean restoreUserRecords) {
        try {
            showProgress();
            // Call importTxtTable in setupImFragment to update progress and update button when finished.
            SetupImFragment setupImFragment = (SetupImFragment) getSupportFragmentManager().findFragmentByTag("SetupImFragment");
            if (setupImFragment != null && setupImFragment.isVisible()) {
                setupImFragment.importTxtTable(fileToImport, imType, restoreUserRecords);
            }

        } catch (Exception e) {
            String errorMessage = getResources().getString(R.string.error_import_db);
            Log.e(TAG, errorMessage, e);
            showToastMessage(errorMessage + e.getMessage(), Toast.LENGTH_LONG);
        } finally {
            cancelProgress();
            showToastMessage(getResources().getString(R.string.setup_im_import_complete), Toast.LENGTH_SHORT);
        }

    }
    
    /**
     * Extracts file extension from filename.
     * @param fileName The filename
     * @return The extension (without dot), or empty string if no extension
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }
    
    /**
     * Gets filename without extension.
     * @param fileName The filename
     * @return The filename without extension
     */
    private String getFileNameWithoutExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(0, lastDot);
        }
        return fileName;
    }
    
    /**
     * Checks if file type matches expected extension.
     * @param mimeType The MIME type
     * @param extension The file extension
     * @return true if type matches extension
     */
    private boolean isFileTypeValid(String mimeType, String extension) {
        if (mimeType == null || extension == null) {
            return false;
        }
        
        if ("text/plain".equals(mimeType)) {
            return "lime".equals(extension) || "cin".equals(extension);
        } else if ("application/zip".equals(mimeType)) {
            return "limedb".equals(extension);
        }
        
        return false;
    }
    
    /**
     * Checks if a table name is valid using SearchServer validation.
     * @param tableName The table name to validate
     * @return true if valid table name
     */
    private boolean isValidTableName(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return false;
        }
        if (searchServer == null) {
            searchServer = new SearchServer(this);
        }
        return searchServer.isValidTableName(tableName);
    }


    /**
     * Performs import of a compressed database file (.limedb).
     * 
     * <p>This method delegates to DBServer methods that handle unzip and import operations.
     * File operations are centralized in DBServer to maintain architecture compliance.
     * 
     * @param dbServer The DBServer instance to use for import
     * @param fileToImport The compressed database file (.limedb) to import
     * @param tableName The table name to import into (or "related" for related table)
     */
    private void performLimedbImport(DBServer dbServer, File fileToImport, String tableName) {
        if ("related".equals(tableName)) {
            // If filename is "related", import with importZippedDbRelated
            try {
                showProgress();
                dbServer.importZippedDbRelated(fileToImport);
                showToastMessage(getResources().getString(R.string.import_related_success), Toast.LENGTH_SHORT);
            } catch (Exception e) {
                String errorMessage = getResources().getString(R.string.error_import_db);
                Log.e(TAG, errorMessage, e);
                showToastMessage(errorMessage, Toast.LENGTH_SHORT);
            } finally {
                cancelProgress();
            }
        } else {
            // Otherwise, use filename (without extension) as imType
            // Check if filename is valid tableName first
            if (isValidTableName(tableName)) {
                try {
                    showProgress();
                    dbServer.importZippedDb(fileToImport, tableName);
                    showToastMessage(getResources().getString(R.string.setup_im_import_complete), Toast.LENGTH_SHORT);
                } catch (Exception e) {
                    String errorMessage = getResources().getString(R.string.error_import_db);
                    Log.e(TAG, errorMessage, e);
                    showToastMessage(errorMessage, Toast.LENGTH_LONG);
                } finally {
                    cancelProgress();
                }
            } else {
                String errorMessage = getResources().getString(R.string.error_table_name);
                Log.e(TAG, errorMessage + ": " + tableName);
                showToastMessage(errorMessage + ": " + tableName, Toast.LENGTH_LONG);
            }
        }
    }

    /**
     * Initializes the list of input methods from the database.
     * 
     * <p>This method queries the database for all input methods and stores them
     * in the imlist field. The list is used to populate the navigation drawer
     * and to navigate to individual IM management fragments.
     * 
     * <p>If the query returns null, an empty list is created to prevent
     * NullPointerException.
     */
    public void initialImList() {

        if (searchServer == null)
            searchServer = new SearchServer(this);

        imlist = new ArrayList<>();
        List<Im> result = searchServer.getIm(null, LIME.IM_TYPE_NAME);
        if (result != null) {
            imlist = result;
        }
        // Ensure imlist is never null to prevent NullPointerException
        if (imlist == null) {
            imlist = new ArrayList<>();
        }
    }

    /**
     * Called when an item in the navigation drawer is selected.
     * 
     * <p>This method handles navigation to different fragments based on the selected
     * position:
     * <ul>
     *   <li>Position 0: Shows SetupImFragment (IM setup)</li>
     *   <li>Position 1: Shows ManageRelatedFragment (related phrases)</li>
     *   <li>Position 2+: Shows ManageImFragment for the corresponding IM table</li>
     * </ul>
     * 
     * <p>All fragment transactions are added to the back stack to allow navigation
     * back through the history.
     * 
     * @param position The position of the selected item in the navigation drawer
     */
    @Override
    public void onNavigationDrawerItemSelected(int position) {
        // update the main content by replacing fragments

        FragmentManager fragmentManager = getSupportFragmentManager();
        if (position == 0) {

            fragmentManager.beginTransaction()
                    .replace(R.id.container, SetupImFragment.newInstance(position), "SetupImFragment")
                    .addToBackStack("SetupImFragment")
                    .commit();
        } else if (position == 1) {
            fragmentManager.beginTransaction()
                    .replace(R.id.container, ManageRelatedFragment.newInstance(position), "ManageRelatedFragment")
                    .addToBackStack("ManageRelatedFragment")
                    .commit();
        } else {

            initialImList();
            int number = position - 2;
            // Check if imlist is null or empty before accessing
            if (imlist == null || imlist.isEmpty() || number < 0 || number >= imlist.size()) {
                // Handle error case - show error message or return early
                return;
            }
            String table = imlist.get(number).getCode();
            fragmentManager.beginTransaction()
                    .replace(R.id.container, ManageImFragment.newInstance(position, table), "ManageImFragment_" + table)
                    .addToBackStack("ManageImFragment_" + table)
                    .commit();
        }
    }

    /**
     * Called when a navigation section is attached to update the ActionBar title.
     * 
     * <p>This method updates the mTitle field based on the section number:
     * <ul>
     *   <li>Section 0: Sets title to "Initial" (IM setup)</li>
     *   <li>Section 1: Sets title to "Related" (related phrases)</li>
     *   <li>Section 2+: Sets title to the IM description from imlist</li>
     * </ul>
     * 
     * <p>The title is later used by {@link #restoreActionBar()} to update the
     * ActionBar display.
     * 
     * @param number The section number (0 = initial, 1 = related, 2+ = IM index)
     */
    public void onSectionAttached(int number) {
        if (number == 0) {
            mTitle = this.getResources().getString(R.string.default_menu_initial);
            //mCode = "initial";
        } else if (number == 1) {
            mTitle = this.getResources().getString(R.string.default_menu_related);
            //mCode = "related";
        } else {
            int position = number - 2;
            // Check if imlist is null or empty before accessing
            if (imlist != null && !imlist.isEmpty() && position >= 0 && position < imlist.size()) {
                mTitle = imlist.get(position).getDesc();
                //mCode = imlist.get(position).getCode();
            } else {
                // Fallback to default title if imlist is not available
                mTitle = this.getResources().getString(R.string.default_menu_initial);
            }
        }
    }

    /**
     * Restores the ActionBar title to the current section title.
     * 
     * <p>This method updates the ActionBar to display the title stored in mTitle.
     * The mTitle field is set by {@link #onSectionAttached(int)} when a navigation
     * section is attached.
     */
    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        //actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD); //setNavigationMode is deprecated after API21 (v5.0).
        if (actionBar == null) throw new AssertionError();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }


    /**
     * Initialize the contents of the Activity's standard options menu.
     * 
     * <p>This method inflates the menu resource and restores the ActionBar title
     * if the navigation drawer is not open. If the drawer is open, the drawer
     * decides what to show in the action bar.
     * 
     * @param menu The options menu in which items are placed
     * @return True if the menu should be displayed, false otherwise
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.main, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Called when an options menu item is selected.
     * 
     * <p>This method delegates to the parent class implementation. Menu item
     * handling is typically done by fragments or the navigation drawer.
     * 
     * @param item The menu item that was selected
     * @return True if the event was handled, false otherwise
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    /**
     * Displays a toast message to the user.
     * 
     * <p>This is a convenience method for showing toast messages throughout
     * the activity.
     * 
     * @param msg The message text to display
     * @param length The duration to display the message (Toast.LENGTH_SHORT or Toast.LENGTH_LONG)
     */
    public void showToastMessage(String msg, int length) {
        Toast toast = Toast.makeText(this, msg, length);
        toast.show();
    }

    /**
     * Initiates sharing of an IM table as a text file.
     * 
     * <p>This method starts a background thread that exports the specified IM table
     * to a text file and then shares it using the Android share intent.
     * 
     * @param imtype The IM type (table name) to share
     */
    public void initialShare(String imtype) {
        sharethread = new Thread(new ShareTxtRunnable(this, imtype, handler));
        sharethread.start();
    }

    /**
     * Initiates sharing of an IM table as a compressed database file.
     * 
     * <p>This method starts a background thread that exports the specified IM table
     * to a compressed .limedb file and then shares it using the Android share intent.
     * 
     * @param imtype The IM type (table name) to share
     */
    public void initialShareDb(String imtype) {
        sharethread = new Thread(new ShareDbRunnable(this, imtype, handler));
        sharethread.start();
    }

    /**
     * Initiates sharing of the related phrases table as a text file.
     * 
     * <p>This method starts a background thread that exports the related phrases
     * table to a text file and then shares it using the Android share intent.
     */
    public void initialShareRelated() {
        sharethread = new Thread(new ShareRelatedTxtRunnable(this, handler));
        sharethread.start();
    }

    /**
     * Initiates sharing of the related phrases table as a compressed database file.
     * 
     * <p>This method starts a background thread that exports the related phrases
     * table to a compressed .limedb file and then shares it using the Android share intent.
     */
    public void initialShareRelatedDb() {
        sharethread = new Thread(new ShareRelatedDbRunnable(this, handler));
        sharethread.start();
    }

    /**
     * Shows a progress dialog if it is not already showing.
     * 
     * <p>This method creates a progress dialog if it doesn't exist, or shows it
     * if it exists but is not currently displayed. The progress dialog is
     * non-cancelable and displays a progress bar and text area for status updates.
     */
    public void showProgress() {
        if (progress == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setCancelable(false);
            View view = LayoutInflater.from(this).inflate(R.layout.progress, null);
            builder.setView(view);
            progress = builder.create();
        }
        if (!progress.isShowing()) {
            progress.show();
        }
    }

    /**
     * Dismisses the progress dialog if it is showing.
     * 
     * <p>This method dismisses and nullifies the progress dialog, releasing
     * resources. It should be called when a long-running operation completes.
     */
    public void cancelProgress() {
        if (progress != null && progress.isShowing()) {
            progress.dismiss();
        }
        progress = null;
    }

    /**
     * Updates the progress bar value in the progress dialog.
     * 
     * <p>This method updates the progress bar to the specified value (0-100).
     * If the progress dialog is not showing, this method does nothing.
     * 
     * @param value The progress value (0-100)
     */
    public void updateProgress(int value) {
        if (progress != null && progress.isShowing()) {
            ProgressBar pb = progress.findViewById(R.id.progress_bar);
            if(pb != null){
                pb.setProgress(value);
            }
        }
    }

    /**
     * Updates the progress text in the progress dialog.
     * 
     * <p>This method updates the status text displayed in the progress dialog.
     * If the progress dialog is not showing, this method does nothing.
     * 
     * @param value The status text to display
     */
    public void updateProgress(String value) {
        if (progress != null && progress.isShowing()) {
            TextView tv = progress.findViewById(R.id.progress_text);
            if(tv != null){
                tv.setText(value);
            }
        }
    }

    /**
     * Shares a file using Android's share intent.
     * 
     * <p>This method creates a share intent for the specified file and launches
     * the Android share chooser. The file is shared using FileProvider to ensure
     * proper URI permissions.
     * 
     * <p>The share intent includes:
     * <ul>
     *   <li>The file URI with read permission granted</li>
     *   <li>The file name as extra text</li>
     * </ul>
     * 
     * @param filepath The path to the file to share
     * @param type The MIME type of the file (e.g., "text/plain" or "application/zip")
     */
    public void shareTo(String filepath, String type) {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType(type);

        File target = new File(filepath);
        
        Uri targetfile = androidx.core.content.FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".fileprovider", target);
        sharingIntent.putExtra(Intent.EXTRA_STREAM, targetfile);
        sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        sharingIntent.putExtra(Intent.EXTRA_TEXT, target.getName());
        startActivity(Intent.createChooser(sharingIntent, target.getName()));
    }

    /**
     * Initializes default preferences for the application.
     * 
     * <p>This method is currently empty and reserved for future use. It may be
     * used to set default preference values when the app is first launched.
     */
    public void initialDefaultPreference() {


    }

    /**
     * Shows the news/message board dialog.
     * 
     * <p>This method displays a dialog containing news or announcements.
     * If an error occurs while showing the dialog, it is logged but not thrown.
     */
    public void showMessageBoard() {
        try {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            NewsDialog dialog = NewsDialog.newInstance();
            dialog.show(ft, "newsdialog");
        } catch (Exception e) {
            Log.e(TAG, "Error showing news dialog", e);
        }
    }

    /**
     * Setup edge-to-edge display with proper window insets handling.
     * This ensures UI elements are not obscured by system bars on API 35+.
     */
    @SuppressWarnings("deprecation")
    private void setupEdgeToEdge() {
        // Apply window insets to the main content container (FrameLayout)
        // ActionBar already handles its own space, so we only need to account for status bar
        View container = findViewById(R.id.container);
        if (container != null) {
            ViewCompat.setOnApplyWindowInsetsListener(container, (v, insets) -> {
                int systemBarsType = WindowInsetsCompat.Type.systemBars();
                int topInset = insets.getInsets(systemBarsType).top;
                int bottomInset = insets.getInsets(systemBarsType).bottom;
                int leftInset = insets.getInsets(systemBarsType).left;
                int rightInset = insets.getInsets(systemBarsType).right;
                
                // Apply padding: top = status bar only (ActionBar handles its own space),
                // left/right/bottom = system bars
                v.setPadding(leftInset, topInset, rightInset, bottomInset);

                return insets;
            });
        }
        
        // DrawerLayout extends to edges - no padding needed on DrawerLayout itself
        // The drawer fragment's ListView will handle its own content insets if needed

        // Set status bar and navigation bar to transparent for edge-to-edge effect
        // This works on all API levels, but is required for API 35+
        // Note: setStatusBarColor and setNavigationBarColor are deprecated in API 35+,
        // but we use them with suppression for backward compatibility

        android.view.Window window = getWindow();
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        
        // Set status bar icon appearance to dark (black icons) for better visibility
        // Since status bar is transparent and content behind may be light, use dark icons
        View decorView = getWindow().getDecorView();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            // API 23+ (Marshmallow+): Use WindowInsetsControllerCompat
            WindowInsetsControllerCompat windowInsetsController = ViewCompat.getWindowInsetsController(decorView);
            if (windowInsetsController != null) {
                // Use dark status bar icons (black) for visibility on light backgrounds
                // setAppearanceLightStatusBars(true) = light status bar appearance = dark icons
                windowInsetsController.setAppearanceLightStatusBars(true);
                // Use dark navigation bar icons for consistency
                windowInsetsController.setAppearanceLightNavigationBars(true);
            }
        } else {
            // API 21-22: SYSTEM_UI_FLAG_LIGHT_STATUS_BAR is not available (introduced in API 23)
            // On API 21-22, we cannot change icon color programmatically
            // Set a dark status bar so white icons are visible (compromise for API 21-22)
            // Use a dark color so white icons are visible
            // This maintains some edge-to-edge while ensuring icons are visible
            window.setStatusBarColor(0xFF000000); // Solid black
        }
    }

    /**
     * Called when the activity is becoming visible to the user.
     * 
     * <p>This method is called after {@link #onCreate(Bundle)} or after
     * {@link #onRestart()} if the activity was stopped. The activity is
     * visible but may not be in the foreground.
     */
    @Override
    public void onStart() {
        super.onStart();
    }
}
