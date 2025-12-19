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

package net.toload.main.hd.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;


import net.toload.main.hd.DBServer;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.R;
import net.toload.main.hd.data.Im;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.global.LIMEUtilities;
import net.toload.main.hd.limedb.LimeDB;

import java.io.File;
import java.util.HashMap;
import java.util.List;


/**
 * A placeholder fragment containing a simple rootView.
 */
public class SetupImFragment extends Fragment {

    /**
     * Simple enum for backup/restore dialog types.
     */
    public enum BackupRestoreType {
        RESTORE,
        BACKUP,
        BACKUP_TO_DOWNLOADS
    }

    // IM Log Tag
    private final String TAG = "SetupImFragment";

    // Debug Flag
    private final boolean DEBUG = false;

    // Basic
    private SetupImHandler handler;

    // UI for Progress
    private View progressContainer;
    private ProgressBar progressBar;
    private TextView progressText;


    //Activate LIME IM

    Button btnSetupImSystemSettings;
    Button btnSetupImSystemIMPicker;


    // Custom Import
    Button btnSetupImImportStandard;
    Button btnSetupImImportRelated;

    // Default IME
    Button btnSetupImPhonetic;
    Button btnSetupImCj;
    Button btnSetupImCj5;
    Button btnSetupImScj;
    Button btnSetupImEcj;
    Button btnSetupImDayi;
    Button btnSetupImEz;
    Button btnSetupImArray;
    Button btnSetupImArray10;
    Button btnSetupImHs;
    Button btnSetupImWb;
    Button btnSetupImPinyin;

    // Backup Restore
    Button btnSetupImBackupLocal;
    Button btnSetupImRestoreLocal;


    private View rootView;
    private LimeDB datasource;
    private DBServer DBSrv = null;
    private Activity activity;
    private LIMEPreferenceManager mLIMEPref;

    List<Im> imlist;

    TextView txtVersion;

    // Activity Result Launchers
    private ActivityResultLauncher<Intent> backupLauncher;
    private ActivityResultLauncher<Intent> restoreLauncher;



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize ActivityResultLaunchers
        backupLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            performBackup(uri);
                        }
                    }
                });

        restoreLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            performRestore(uri);
                        }
                    }
                });

    }

    @Override
    public void onPause() {
        super.onPause();

        // Update IM pick up list items
        if(imlist != null && !imlist.isEmpty()){
            mLIMEPref.syncIMActivatedState(imlist);
        }

    }

    /**
     * The fragment argument representing the section number for this
     * fragment.
     */
    private static final String ARG_SECTION_NUMBER = "section_number";

    /**
     * Returns a new instance of this fragment for the given section
     * number.
     */
    public static SetupImFragment newInstance(int sectionNumber) {
        SetupImFragment frg = new SetupImFragment();
        Bundle args = new Bundle();
                args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        frg.setArguments(args);
        return frg;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

    }

    @Override
    public void onResume() {

        super.onResume();


        initialbutton();

    }

    public void showProgress(boolean spinnerStyle, String message) {
        if (progressContainer != null) {
            progressContainer.setVisibility(View.VISIBLE);
        }
        if (progressBar != null) {
            progressBar.setIndeterminate(spinnerStyle);
        }
        if (progressText != null) {
            progressText.setText(message);
        }
    }

    public void cancelProgress(){
        if (progressContainer != null) {
            progressContainer.setVisibility(View.GONE);
        }
        handler.initialImButtons();
    }

    public void setProgressIndeterminate(boolean flag){
        if (progressBar != null) {
            progressBar.setIndeterminate(flag);
        }
    }

    public void updateProgress(int value){
        if (progressBar != null) {
            progressBar.setProgress(value);
        }
    }

    public void updateProgress(String value){
        if (progressText != null) {
            progressText.setText(value);
        }
    }

    /**
     * Helper method to configure an input method button with consistent styling and click handler.
     * Reduces code duplication by centralizing the logic for setting alpha, typeface, and click listener.
     * 
     * @param button The button to configure
     * @param tableName The database table name constant (e.g., LIME.DB_TABLE_PHONETIC)
     * @param check HashMap containing loaded table names
     * @param setText If true, also set button text from check HashMap (for custom table)
     */
    private void setupInputMethodButton(Button button, String tableName, HashMap<String, String> check, boolean setText) {
        if (button == null) return;
        
        String tableValue = check.get(tableName);
        if (tableValue != null) {
            // Table is loaded - show as half alpha and italic
            button.setAlpha(LIME.HALF_ALPHA_VALUE);
            button.setTypeface(null, Typeface.ITALIC);
            if (setText) {
                button.setText(tableValue);
            }
        } else {
            // Table is not loaded - show as normal alpha and bold
            button.setAlpha(LIME.NORMAL_ALPHA_VALUE);
            button.setTypeface(null, Typeface.BOLD);
        }
        
        // Set click listener to show SetupImLoadDialog
        button.setOnClickListener(v -> {
            FragmentTransaction ft = getParentFragmentManager().beginTransaction();
            SetupImLoadDialog dialog = SetupImLoadDialog.newInstance(tableName, handler);
            dialog.show(ft, "loadimdialog");
        });
    }

    /**
     * Overloaded helper method without setText parameter (defaults to false).
     */
    private void setupInputMethodButton(Button button, String tableName, HashMap<String, String> check) {
        setupInputMethodButton(button, tableName, check, false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        datasource = new LimeDB(this.getActivity());

        handler = new SetupImHandler(this);

        activity = getActivity();

        assert activity != null;
        DBSrv = DBServer.getInstance(activity);
        mLIMEPref = new LIMEPreferenceManager(activity);

        rootView = inflater.inflate(R.layout.fragment_setup_im, container, false);

        // Progress UI
        progressContainer = rootView.findViewById(R.id.progress_container);
        progressBar = rootView.findViewById(R.id.progress_bar);
        progressText = rootView.findViewById(R.id.progress_text);

        btnSetupImSystemSettings = rootView.findViewById(R.id.btnSetupImSystemSetting);
        btnSetupImSystemIMPicker = rootView.findViewById(R.id.btnSetupImSystemIMPicker);

        btnSetupImImportStandard = rootView.findViewById(R.id.btnSetupImImportStandard);
        btnSetupImImportRelated = rootView.findViewById(R.id.btnSetupImImportRelated);
        btnSetupImPhonetic = rootView.findViewById(R.id.btnSetupImPhonetic);
        btnSetupImCj = rootView.findViewById(R.id.btnSetupImCj);
        btnSetupImCj5 = rootView.findViewById(R.id.btnSetupImCj5);
        btnSetupImScj = rootView.findViewById(R.id.btnSetupImScj);
        btnSetupImEcj = rootView.findViewById(R.id.btnSetupImEcj);
        btnSetupImDayi = rootView.findViewById(R.id.btnSetupImDayi);
        btnSetupImEz = rootView.findViewById(R.id.btnSetupImEz);
        btnSetupImArray = rootView.findViewById(R.id.btnSetupImArray);
        btnSetupImArray10 = rootView.findViewById(R.id.btnSetupImArray10);
        btnSetupImHs = rootView.findViewById(R.id.btnSetupImHs);
        btnSetupImWb = rootView.findViewById(R.id.btnSetupImWb);
        btnSetupImPinyin = rootView.findViewById(R.id.btnSetupImPinyin);

        // Backup and Restore Setting
        btnSetupImBackupLocal = rootView.findViewById(R.id.btnSetupImBackupLocal);
        btnSetupImRestoreLocal = rootView.findViewById(R.id.btnSetupImRestoreLocal);

        btnSetupImBackupLocal.setOnClickListener(v -> backupLocalDrive());

        btnSetupImRestoreLocal.setOnClickListener(v -> restoreLocalDrive());





        PackageInfo pInfo;
        try {
            pInfo = requireActivity().getPackageManager().getPackageInfo(requireActivity().getPackageName(), 0);
            long versionCode = PackageInfoCompat.getLongVersionCode(pInfo);
            txtVersion = rootView.findViewById(R.id.txtVersion);
            txtVersion.setText(getString(R.string.version_format, pInfo.versionName, versionCode));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error in operation", e);
        }

        return rootView;
    }

    public void initialbutton(){

        HashMap<String, String> check = new HashMap<>();

        // Load Menu Item
        //if(!mLIMEPref.getDatabaseOnHold()){
        if(!DBSrv.isDatabseOnHold()){
            try {
                //datasource.open();
                imlist = datasource.getIm(null, LIME.IM_TYPE_NAME);
                for(int i = 0; i < imlist.size() ; i++){
                    check.put(imlist.get(i).getCode(), imlist.get(i).getDesc());
                }

                // Update IM pick up list items
                mLIMEPref.syncIMActivatedState(imlist);

                if(LIMEUtilities.isLIMEEnabled(requireActivity().getApplicationContext())){  //LIME is activated in system
                    btnSetupImSystemSettings.setVisibility(View.GONE);
                    rootView.findViewById(R.id.setup_im_system_settings_description).setVisibility(View.GONE);
                    rootView.findViewById(R.id.SetupImList).setVisibility(View.VISIBLE);
                    //LIME is activated, also the active Keyboard
                    if(LIMEUtilities.isLIMEActive(requireActivity().getApplicationContext())) {
                        btnSetupImSystemIMPicker.setVisibility(View.GONE);
                        rootView.findViewById(R.id.Setup_Wizard).setVisibility(View.GONE);

                    }
                    //LIME is activated, but not active keyboard
                    else
                    {

                        btnSetupImSystemIMPicker.setVisibility(View.VISIBLE);
                        rootView.findViewById(R.id.setup_im_system_impicker_description).setVisibility(View.VISIBLE);

                    }
                    btnSetupImBackupLocal.setEnabled(true);
                    btnSetupImRestoreLocal.setEnabled(true);
                    btnSetupImImportStandard.setEnabled(true);
                    btnSetupImImportRelated.setEnabled(true);

                }else {
                    btnSetupImSystemSettings.setVisibility(View.VISIBLE);
                    rootView.findViewById(R.id.setup_im_system_settings_description).setVisibility(View.VISIBLE);
                    btnSetupImSystemIMPicker.setVisibility(View.GONE);
                    rootView.findViewById(R.id.setup_im_system_impicker_description).setVisibility(View.GONE);
                    rootView.findViewById(R.id.SetupImList).setVisibility(View.GONE);
                }


                btnSetupImSystemSettings.setOnClickListener(v -> LIMEUtilities.showInputMethodSettingsPage(requireActivity().getApplicationContext()));

                btnSetupImSystemIMPicker.setOnClickListener(v -> {
                    LIMEUtilities.showInputMethodPicker(requireActivity().getApplicationContext());
                    rootView.invalidate();
                });

                // Setup custom import button (with text display)
                setupInputMethodButton(btnSetupImImportStandard, LIME.DB_TABLE_CUSTOM, check, true);

                // User can always load new related table ...
                btnSetupImImportRelated.setOnClickListener(v -> {
                    FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                    SetupImLoadDialog dialog = SetupImLoadDialog.newInstance(LIME.DB_TABLE_RELATED, handler);
                    dialog.show(ft, "loadimdialog");

                });

                // Setup all input method buttons using helper method
                setupInputMethodButton(btnSetupImPhonetic, LIME.DB_TABLE_PHONETIC, check);
                setupInputMethodButton(btnSetupImCj, LIME.DB_TABLE_CJ, check);
                setupInputMethodButton(btnSetupImCj5, LIME.DB_TABLE_CJ5, check);
                setupInputMethodButton(btnSetupImScj, LIME.DB_TABLE_SCJ, check);
                setupInputMethodButton(btnSetupImEcj, LIME.DB_TABLE_ECJ, check);
                setupInputMethodButton(btnSetupImDayi, LIME.DB_TABLE_DAYI, check);
                setupInputMethodButton(btnSetupImEz, LIME.DB_TABLE_EZ, check);
                setupInputMethodButton(btnSetupImArray, LIME.DB_TABLE_ARRAY, check);
                setupInputMethodButton(btnSetupImArray10, LIME.DB_TABLE_ARRAY10, check);
                setupInputMethodButton(btnSetupImHs, LIME.DB_TABLE_HS, check);
                setupInputMethodButton(btnSetupImWb, LIME.DB_TABLE_WB, check);
                setupInputMethodButton(btnSetupImPinyin, LIME.DB_TABLE_PINYIN, check);




            } catch (Exception e) {
                Log.e(TAG, "Error in operation", e);
            }
        }
        
    }

    public void showAlertDialog(BackupRestoreType type){
        int messageResId;
        Runnable onConfirm;

        switch (type) {
            case RESTORE:
                messageResId = R.string.l3_initial_restore_confirm;
                onConfirm = this::launchRestoreFilePicker;
                break;
            case BACKUP:
                messageResId = R.string.l3_initial_backup_confirm;
                onConfirm = this::launchBackupFilePicker;
                break;
            case BACKUP_TO_DOWNLOADS:
                messageResId = R.string.l3_initial_backup_confirm_downloads;
                onConfirm = this::saveBackupToDownloads;
                break;
            default:
                return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage(getResources().getString(messageResId));
        builder.setCancelable(false);
        builder.setPositiveButton(getResources().getString(R.string.dialog_confirm),
                (dialog, id) -> onConfirm.run());
        builder.setNegativeButton(getResources().getString(R.string.dialog_cancel),
                (dialog, id) -> {});

        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    private void performBackup(Uri uri) {
        showProgress(true, this.getResources().getString(R.string.setup_im_backup_message));

        new Thread(() -> {
            try {
                DBSrv.backupDatabase(uri);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }finally {
                if (handler != null) {
                    handler.cancelProgress();
                }
            }
        }).start();
    }

    private void performRestore(Uri uri) {
        showProgress(true, this.getResources().getString(R.string.setup_im_restore_message));

        new Thread(() -> {
            try {
                DBSrv.restoreDatabase(uri);
            } catch (Exception e) {
                Log.e(TAG, "Error in operation", e);
                activity.runOnUiThread(() -> showToastMessage(activity.getString(R.string.l3_initial_restore_error), Toast.LENGTH_LONG));
            } finally {
                if (handler != null) {
                    handler.cancelProgress();
                }
            }
        }).start();
    }




    public void backupLocalDrive(){
        // Check if launcher is initialized
        if (backupLauncher == null) {
            if (DEBUG) Log.e(TAG, "backupLauncher is null, onCreate() may not have been called");
            showToastMessage(getString(R.string.l3_initial_backup_error), Toast.LENGTH_SHORT);
            return;
        }

        try {
            // Launch file picker for saving backup
            // Use ACTION_CREATE_DOCUMENT (Storage Access Framework) for API 19+
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_TITLE, "limeBackup.zip");

            PackageManager pm = requireActivity().getPackageManager();
            
            // Check if intent can be resolved
            if (intent.resolveActivity(pm) == null) {
                if (DEBUG) Log.w(TAG, "Intent cannot be resolved, using fallback to Downloads folder");
                // Fallback: Show confirmation dialog for saving to Downloads folder
                showAlertDialog(BackupRestoreType.BACKUP_TO_DOWNLOADS);
                return;
            }

            // Also check queryIntentActivities to see if there are actually any activities
            // On some tablets, resolveActivity() returns non-null but queryIntentActivities() returns empty
            // which means the chooser will show an empty list
            List<android.content.pm.ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
            if (activities == null || activities.isEmpty()) {
                if (DEBUG) Log.w(TAG, "No activities found for ACTION_CREATE_DOCUMENT, using fallback to Downloads folder");
                // Fallback: Show confirmation dialog for saving to Downloads folder
                showAlertDialog(BackupRestoreType.BACKUP_TO_DOWNLOADS);
                return;
            }

            // Show confirmation dialog before launching file picker to choose location
            showAlertDialog(BackupRestoreType.BACKUP);
        } catch (Exception e) {
            if (DEBUG) Log.e(TAG, "Error checking backup options: " + e.getMessage(), e);
            Log.e(TAG, "Error in operation", e);
            showToastMessage(getString(R.string.l3_initial_backup_error), Toast.LENGTH_SHORT);
        }
    }
    
    /**
     * Actually launch the backup file picker after user confirms.
     * Called from showAlertDialog when user clicks confirm.
     */
    private void launchBackupFilePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_TITLE, "limeBackup.zip");

            PackageManager pm = requireActivity().getPackageManager();
            
            // Double-check that intent can be resolved
            if (intent.resolveActivity(pm) == null) {
                if (DEBUG) Log.w(TAG, "Intent cannot be resolved, falling back to Downloads");
                // Fallback to Downloads folder
                saveBackupToDownloads();
                return;
            }

            // Also check queryIntentActivities to see if there are actually any activities
            List<android.content.pm.ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
            if (activities == null || activities.isEmpty()) {
                if (DEBUG) Log.w(TAG, "No activities found, falling back to Downloads");
                // Fallback to Downloads folder
                saveBackupToDownloads();
                return;
            }

            // Wrap in createChooser() for better compatibility on tablets
            Intent chooserIntent = Intent.createChooser(intent, "Save Backup");

            // Launch the file picker
            backupLauncher.launch(chooserIntent);
        } catch (Exception e) {
            if (DEBUG) Log.e(TAG, "Error launching backup file picker: " + e.getMessage(), e);
            Log.e(TAG, "Error in operation", e);
            // On error, try fallback to Downloads
            saveBackupToDownloads();
        }
    }

    /**
     * Fallback method to save backup directly to Downloads folder when ACTION_CREATE_DOCUMENT is not available.
     * Uses MediaStore API for API 29+ or direct file access for older APIs.
     */
    private void saveBackupToDownloads() {
        new Thread(() -> {
            try {
                Uri backupUri;
                String fileName = "limeBackup.zip";

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // API 29+: Use MediaStore API
                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                    backupUri = requireActivity().getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    
                    if (backupUri == null) {
                        activity.runOnUiThread(() -> showToastMessage(getString(R.string.l3_initial_backup_error), Toast.LENGTH_SHORT));
                        return;
                    }
                } else {
                    // API < 29: Use direct file access (deprecated but works)
                    @SuppressWarnings("deprecation")
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                    
                    if (downloadsDir == null) {
                        activity.runOnUiThread(() -> showToastMessage(getString(R.string.l3_initial_backup_error), Toast.LENGTH_SHORT));
                        return;
                    }
                    
                    if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
                        activity.runOnUiThread(() -> showToastMessage(getString(R.string.l3_initial_backup_error), Toast.LENGTH_SHORT));
                        return;
                    }

                    File backupFile = new File(downloadsDir, fileName);
                    // Handle duplicate file names
                    int counter = 1;
                    while (backupFile.exists()) {
                        backupFile = new File(downloadsDir, 
                            "limeBackup(" + counter + ").zip");
                        counter++;
                    }
                    
                    // Use FileProvider instead of deprecated Uri.fromFile()
                    backupUri = androidx.core.content.FileProvider.getUriForFile(
                        activity,
                        activity.getApplicationContext().getPackageName() + ".fileprovider",
                        backupFile);
                }

                if (backupUri != null) {
                    final Uri finalUri = backupUri;
                    activity.runOnUiThread(() -> {
                        performBackup(finalUri);
                        String message = getString(R.string.setup_im_backup_message) + 
                            "\nSaved to: Downloads/" + fileName;
                        showToastMessage(message, Toast.LENGTH_LONG);
                    });
                } else {
                    activity.runOnUiThread(() -> showToastMessage(getString(R.string.l3_initial_backup_error), Toast.LENGTH_SHORT));
                }
            } catch (Exception e) {
                if (DEBUG) Log.e(TAG, "Error saving backup to Downloads: " + e.getMessage(), e);
                Log.e(TAG, "Error in operation", e);
                activity.runOnUiThread(() -> showToastMessage(getString(R.string.l3_initial_backup_error), Toast.LENGTH_SHORT));
            }
        }).start();
    }

    public void restoreLocalDrive(){
        // Check if launcher is initialized
        if (restoreLauncher == null) {
            if (DEBUG) Log.e(TAG, "restoreLauncher is null, onCreate() may not have been called");
            showToastMessage(getString(R.string.l3_initial_restore_error), Toast.LENGTH_SHORT);
            return;
        }

        try {
            // Launch file picker for selecting backup file
            // Use ACTION_GET_CONTENT (Storage Access Framework) for API 19+
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/zip");
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            // Wrap in createChooser() for better compatibility
            // createChooser can work even when queryIntentActivities returns empty
            Intent chooserIntent = Intent.createChooser(intent, "Select Backup");
            
            // Check if chooser intent can be resolved
            // This is more reliable than queryIntentActivities which may not return system activities
            if (chooserIntent.resolveActivity(requireActivity().getPackageManager()) == null) {
                if (DEBUG) Log.e(TAG, "Chooser intent cannot be resolved");
                showToastMessage(getString(R.string.l3_initial_restore_error), Toast.LENGTH_SHORT);
                return;
            }

            // Show confirmation dialog before launching file picker to select backup file
            showAlertDialog(BackupRestoreType.RESTORE);
        } catch (Exception e) {
            if (DEBUG) Log.e(TAG, "Error checking restore options: " + e.getMessage(), e);
            Log.e(TAG, "Error in operation", e);
            showToastMessage(getString(R.string.l3_initial_restore_error), Toast.LENGTH_SHORT);
        }
    }
    
    /**
     * Actually launch the restore file picker after user confirms.
     * Called from showAlertDialog when user clicks confirm.
     */
    private void launchRestoreFilePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/zip");
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            // Wrap in createChooser() for better compatibility on tablets
            Intent chooserIntent = Intent.createChooser(intent, "Select Backup");
            
            // Double-check that chooser can be resolved
            if (chooserIntent.resolveActivity(requireActivity().getPackageManager()) == null) {
                if (DEBUG) Log.e(TAG, "Chooser intent cannot be resolved");
                showToastMessage(getString(R.string.l3_initial_restore_error), Toast.LENGTH_SHORT);
                return;
            }

            // Launch the file picker
            restoreLauncher.launch(chooserIntent);
        } catch (Exception e) {
            if (DEBUG) Log.e(TAG, "Error launching restore file picker: " + e.getMessage(), e);
            Log.e(TAG, "Error in operation", e);
            showToastMessage(getString(R.string.l3_initial_restore_error), Toast.LENGTH_SHORT);
        }
    }

//    public void initialThreadTask(String action, String type) {
//
//        // Default Setting
//        mLIMEPref.setParameter("dbtarget", LIME.DEVICE);
//
//        if (action.equals(LIME.BACKUP)) {
//            // The local backup is now handled by onActivityResult, not a direct runnable here.
//            if(!type.equals(LIME.LOCAL)) {
//                if (backupthread != null && backupthread.isAlive()) {
//                    handler.removeCallbacks(backupthread);
//                }
//                backupthread = new Thread(new SetupImBackupRunnable(this, handler, type));
//                backupthread.start();
//            }
//        }else if(action.equals(LIME.RESTORE)){
//            if(restorethread != null && restorethread.isAlive()){
//                handler.removeCallbacks(restorethread);
//            }
//            restorethread = new Thread(new SetupImRestoreRunnable(this, handler, type));
//            restorethread.start();
//        }
//    }

    public void showToastMessage(String msg, int length) {
        Toast toast = Toast.makeText(activity, msg, length);
        toast.show();
    }

    public void updateCustomButton() {
        btnSetupImImportStandard.setText(getResources().getString(R.string.setup_im_load_standard));
    }

    public void resetImTable(String imtable, boolean backuplearning){
        try {
            if(backuplearning){
                datasource.backupUserRecords(imtable);
            }
            DBSrv.resetMapping(imtable);
        } catch (RemoteException e) {
            Log.e(TAG, "Error in operation", e);
        }
    }

    public void finishProgress() {

        cancelProgress();

    }
}
