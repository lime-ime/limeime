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
import net.toload.main.hd.global.LIMEUtilities;
import net.toload.main.hd.limedb.LimeDB;
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
import java.io.IOException;
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

    private LimeDB datasource;
    private List<Im> imlist;

    private AlertDialog progress;
    private MainActivityHandler handler;
    private Thread sharethread;

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

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

    @Override
    protected void onStop() {
        super.onStop();
    }


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
                File importFile = new File(importDir, fileName);
                String importFilepath = importFile.getAbsolutePath();
                
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
                    List<String> unzipPaths;
                    try {
                        unzipPaths = LIMEUtilities.unzip(importFile.getAbsolutePath(), importFile.getParent(), true);

                        if (unzipPaths.size() != 1) {
                            showToastMessage(getResources().getString(R.string.error_import_db), Toast.LENGTH_LONG);
                            return;
                        }
                        File fileToImport = new File(unzipPaths.get(0));
                         
                        // Check if table is empty before importing
                        int recordCount = dbServer.countMapping(tableName);
                        if (recordCount > 0) {
                            // Table is not empty, show confirmation dialog
                            
                            String message = getResources().getString(R.string.setup_im_dialog_import_confirm_overwrite);
                            
                            new AlertDialog.Builder(this)
                                .setTitle(getResources().getString(R.string.setup_im_dialog_import_confirm_title))
                                .setMessage(message)
                                .setPositiveButton(getResources().getString(R.string.dialog_confirm), (dialog, which) -> {
                                    // User confirmed, proceed with import
                                    performLimedbImport(dbServer, fileToImport, tableName);
                                })
                                .setNegativeButton(getResources().getString(R.string.dialog_cancel), (dialog, which) -> dialog.dismiss())
                                .show();
                        } else {
                            // Table is empty, proceed directly with import
                            performLimedbImport(dbServer, fileToImport, tableName);
                        }
                    } catch (IOException e) {
                        String errorMessage = getResources().getString(R.string.error_import_db);
                        Log.e(TAG, errorMessage, e);
                        showToastMessage(errorMessage, Toast.LENGTH_LONG);
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

        String cversion = mLIMEPref.getParameterString("current_version", "");
        if (cversion == null || cversion.isEmpty() || !cversion.equals(versionStr)) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            HelpDialog dialog = HelpDialog.newInstance();
            dialog.show(ft, "helpdialog");
            mLIMEPref.setParameter("current_version", versionStr);
        }

    }

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

    void handleSendText(Intent intent) {
        String importtext = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (importtext != null && !importtext.isEmpty()) {
            androidx.fragment.app.FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ImportDialog dialog = ImportDialog.newInstance(importtext);
            dialog.show(ft, "importdialog");
        }
    }
    
    void handleImportTxt(String filePath) {
        if (filePath != null && !filePath.isEmpty()) {
            androidx.fragment.app.FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ImportDialog dialog = ImportDialog.newInstanceForFile(filePath);
            dialog.setOnImportTypeSelectedListener(this);
            dialog.show(ft, "importdialog");
        }
    }
    
    @Override
    public void onImportTypeSelected(String imType, String filePath) {
        try {
            showProgress();
            DBServer dbServer = DBServer.getInstance(this);
            dbServer.loadMapping(filePath, imType, null);
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
     * Checks if a table name is valid using LimeDB validation.
     * @param tableName The table name to validate
     * @return true if valid table name
     */
    private boolean isValidTableName(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return false;
        }
        if (datasource == null) {
            datasource = new LimeDB(this);
        }
        return datasource.isValidTableName(tableName);
    }

    
    /**
     * Performs the actual import of a .limedb file to the specified table.
     * @param dbServer The DBServer instance
     * @param fileToImport The file to import
     * @param tableName The table name (without extension)
     */
    private void performLimedbImport(DBServer dbServer, File fileToImport, String tableName) {
        if ("related".equals(tableName)) {
            // If filename is "related", import with importBackupRelatedDb
            try {
                showProgress();
                dbServer.importBackupRelatedDb(fileToImport);
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
                    dbServer.importBackupDb(fileToImport, tableName);
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

    public void initialImList() {

        if (datasource == null)
            datasource = new LimeDB(this);

        imlist = new ArrayList<>();
        List<Im> result = datasource.getIm(null, LIME.IM_TYPE_NAME);
        if (result != null) {
            imlist = result;
        }
        // Ensure imlist is never null to prevent NullPointerException
        if (imlist == null) {
            imlist = new ArrayList<>();
        }
    }

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

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        //actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD); //setNavigationMode is deprecated after API21 (v5.0).
        if (actionBar == null) throw new AssertionError();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }


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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    public void showToastMessage(String msg, int length) {
        Toast toast = Toast.makeText(this, msg, length);
        toast.show();
    }

    public void initialShare(String imtype) {
        sharethread = new Thread(new ShareTxtRunnable(this, imtype, handler));
        sharethread.start();
    }

    public void initialShareDb(String imtype) {
        sharethread = new Thread(new ShareDbRunnable(this, imtype, handler));
        sharethread.start();
    }

    public void initialShareRelated() {
        sharethread = new Thread(new ShareRelatedTxtRunnable(this, handler));
        sharethread.start();
    }

    public void initialShareRelatedDb() {
        sharethread = new Thread(new ShareRelatedDbRunnable(this, handler));
        sharethread.start();
    }

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

    public void cancelProgress() {
        if (progress != null && progress.isShowing()) {
            progress.dismiss();
        }
        progress = null;
    }

    public void updateProgress(int value) {
        if (progress != null && progress.isShowing()) {
            ProgressBar pb = progress.findViewById(R.id.progress_bar);
            if(pb != null){
                pb.setProgress(value);
            }
        }
    }

    public void updateProgress(String value) {
        if (progress != null && progress.isShowing()) {
            TextView tv = progress.findViewById(R.id.progress_text);
            if(tv != null){
                tv.setText(value);
            }
        }
    }

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

    public void initialDefaultPreference() {


    }

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

    @Override
    public void onStart() {
        super.onStart();
    }
}
