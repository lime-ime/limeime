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

package net.toload.main.hd.ui.view;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.os.Looper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import net.toload.main.hd.DBServer;
import net.toload.main.hd.data.ImConfig;
import net.toload.main.hd.ui.MainActivity;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.R;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.global.LIMEProgressListener;
import net.toload.main.hd.global.LIMEUtilities;
import net.toload.main.hd.ui.controller.SetupImController;
import net.toload.main.hd.ui.dialog.ImportDialog;
import net.toload.main.hd.ui.dialog.SetupImLoadDialog;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * A placeholder fragment containing a simple rootView.
 */
public class SetupImFragment extends Fragment implements SetupImView {


    // IM Log Tag
    private final String TAG = "SetupImFragment";

    // Debug Flag
    private final boolean DEBUG = false;

    // BroadcastReceiver to listen for IME changes
    private BroadcastReceiver imeChangeReceiver = null;

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
    private SetupImController setupImController;
    private Activity activity;
    private LIMEPreferenceManager mLIMEPref;

    List<ImConfig> imlist;

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

        // Unregister BroadcastReceiver to prevent memory leaks
        unregisterImeChangeReceiver();
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
    public void onResume() {

        super.onResume();

        // Register BroadcastReceiver to listen for IME changes
        // This detects when user enables/disables/switches IME in system settings
        registerImeChangeReceiver();

        // Also refresh immediately in case no broadcast is sent
        if (rootView != null) {
            new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (isAdded() && rootView != null) {
                    Log.i(TAG, "onResume() - refreshing button state");
                    refreshButtonState();
                }
            }, 500); // 500ms delay ensures system processes the IME change
        }

    }

    /**
     * Register receiver to listen for IME changes
     * Detects when user enables/disables LIME or switches to another IME
     */
    private void registerImeChangeReceiver() {
        if (imeChangeReceiver != null) {
            return; // Already registered
        }

        imeChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() != null && 
                    (intent.getAction().equals("android.intent.action.INPUT_METHOD_CHANGED") ||
                     intent.getAction().equals("android.settings.INPUT_METHOD_SETTINGS"))) {
                    
                    Log.i(TAG, "IME change detected via broadcast - refreshing UI");
                    if (rootView != null && isAdded()) {
                        refreshButtonState();
                    }
                }
            }
        };

        Context context = requireActivity();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.INPUT_METHOD_CHANGED");
        
        // Register receiver for API compatibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(imeChangeReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(imeChangeReceiver, filter);
        }
    }

    /**
     * Unregister the IME change receiver
     */
    private void unregisterImeChangeReceiver() {
        if (imeChangeReceiver != null) {
            try {
                requireActivity().unregisterReceiver(imeChangeReceiver);
                Log.i(TAG, "IME change receiver unregistered");
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "IME change receiver was not registered: " + e.getMessage());
            }
            imeChangeReceiver = null;
        }
    }




    /**
     * Helper method to configure an input method button with consistent styling and click handler.
     * Reduces code duplication by centralizing the logic for setting alpha, typeface, and click listener.
     *
     * @param button The button to configure
     * @param tableName The database table name constant (e.g., LIME.DB_TABLE_PHONETIC)
     * @param imConfigList List of IMs to check if table is loaded
     *
     */
    private void setupInputMethodButton(Button button, String tableName, List<ImConfig> imConfigList) {
        if (button == null) return;

        String tableValue = null;
        if (imConfigList != null) {
            for (ImConfig imConfig : imConfigList) {
                if (tableName.equals(imConfig.getCode())) {
                    tableValue = imConfig.getDesc();
                    break;
                }
            }
        }
        if (tableValue != null) {
            // Table is loaded - show as half alpha and italic
            button.setAlpha(LIME.HALF_ALPHA_VALUE);
            button.setTypeface(null, Typeface.ITALIC);
        } else {
            // Table is not loaded - show as normal alpha and bold
            button.setAlpha(LIME.NORMAL_ALPHA_VALUE);
            button.setTypeface(null, Typeface.BOLD);
        }

        // Set click listener to show SetupImLoadDialog
        button.setOnClickListener(v -> {
            FragmentTransaction ft = getParentFragmentManager().beginTransaction();
            SetupImLoadDialog dialog = SetupImLoadDialog.newInstance(tableName);
            dialog.setFragment(this);
            dialog.show(ft, "loadimdialog");
        });
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        activity = getActivity();

        if (activity instanceof MainActivity) {
            setupImController = ((MainActivity) activity).getSetupImController();
            if (setupImController != null) {
                setupImController.setSetupImView(this);
            } else {
                Log.w(TAG, "SetupImController is null; UI operations may fail");
            }
        } else {
            Log.w(TAG, "Activity is not MainActivity; SetupImController unavailable");
        }

        assert activity != null;
        mLIMEPref = new LIMEPreferenceManager(activity);

        rootView = inflater.inflate(R.layout.fragment_setup_im, container, false);


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
    @Override
    public void refreshButtonState(){
        // Safety checks: ensure fragment is attached and views are ready
        if (!isAdded() || activity == null || rootView == null) {
            if (DEBUG) Log.w(TAG, "refreshButtonState skipped: fragment not ready");
            return;
        }

        if (setupImController != null) {
            try {
                // Get IM list for other operations
                imlist = setupImController.getImConfigList();
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


                btnSetupImSystemSettings.setOnClickListener(v -> {
                    Log.i(TAG, "Opening IME settings to enable LimeIME");
                    LIMEUtilities.showInputMethodSettingsPage(requireActivity().getApplicationContext());
                });

                btnSetupImSystemIMPicker.setOnClickListener(v -> {
                    Log.i(TAG, "Opening IME picker to select LimeIME");
                    LIMEUtilities.showInputMethodPicker(requireActivity().getApplicationContext());
                });

                // Setup custom import button - always keep it active/clickable
                if (btnSetupImImportStandard != null) {
                    String customTableName =null;
                    for (ImConfig imConfig : imlist ){
                        if (imConfig.getCode().equals(LIME.DB_TABLE_CUSTOM)) {
                            customTableName = imConfig.getDesc();
                        }
                    }
                    if (customTableName != null) {
                        // Table is loaded - show name in italic and dimmed
                        btnSetupImImportStandard.setText(customTableName);
                        btnSetupImImportStandard.setAlpha(LIME.HALF_ALPHA_VALUE);
                        btnSetupImImportStandard.setTypeface(null, Typeface.ITALIC);
                    } else {
                        // No table loaded - show as normal and bold
                        btnSetupImImportStandard.setAlpha(LIME.NORMAL_ALPHA_VALUE);
                        btnSetupImImportStandard.setTypeface(null, Typeface.BOLD);
                    }
                    // Always keep button clickable so users can import new custom tables
                    btnSetupImImportStandard.setOnClickListener(v -> {
                        FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                        SetupImLoadDialog dialog = SetupImLoadDialog.newInstance(LIME.DB_TABLE_CUSTOM);
                        dialog.setFragment(this);
                        dialog.show(ft, "loadimdialog");
                    });
                }

                // User can always load new related table ...
                btnSetupImImportRelated.setOnClickListener(v -> {
                    FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                    SetupImLoadDialog dialog = SetupImLoadDialog.newInstance(LIME.DB_TABLE_RELATED);
                    dialog.setFragment(this);
                    dialog.show(ft, "loadimdialog");

                });

                // Setup all input method buttons using helper method
                setupInputMethodButton(btnSetupImPhonetic, LIME.DB_TABLE_PHONETIC, imlist);
                setupInputMethodButton(btnSetupImCj, LIME.DB_TABLE_CJ, imlist);
                setupInputMethodButton(btnSetupImCj5, LIME.DB_TABLE_CJ5, imlist);
                setupInputMethodButton(btnSetupImScj, LIME.DB_TABLE_SCJ, imlist);
                setupInputMethodButton(btnSetupImEcj, LIME.DB_TABLE_ECJ, imlist);
                setupInputMethodButton(btnSetupImDayi, LIME.DB_TABLE_DAYI, imlist);
                setupInputMethodButton(btnSetupImEz, LIME.DB_TABLE_EZ, imlist);
                setupInputMethodButton(btnSetupImArray, LIME.DB_TABLE_ARRAY, imlist);
                setupInputMethodButton(btnSetupImArray10, LIME.DB_TABLE_ARRAY10, imlist);
                setupInputMethodButton(btnSetupImHs, LIME.DB_TABLE_HS, imlist);
                setupInputMethodButton(btnSetupImWb, LIME.DB_TABLE_WB, imlist);
                setupInputMethodButton(btnSetupImPinyin, LIME.DB_TABLE_PINYIN, imlist);




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
        try {
            if (setupImController != null) {
                setupImController.performBackup(uri);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to backup database", e);
            showToastMessage(getString(R.string.l3_initial_backup_error), Toast.LENGTH_LONG);
        }
    }

    private void performRestore(Uri uri) {
        try {
            if (setupImController != null) {
                setupImController.performRestore(uri);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore database", e);
            showToastMessage(getString(R.string.l3_initial_restore_error), Toast.LENGTH_LONG);
        }
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
            List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
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
            List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
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
                    ContentValues values = new ContentValues();
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
                    backupUri = FileProvider.getUriForFile(
                        activity,
                        activity.getApplicationContext().getPackageName() + ".fileprovider",
                        backupFile);
                }

                if (backupUri != null) {
                    final Uri finalUri = backupUri;
                    activity.runOnUiThread(() -> performBackup(finalUri));
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


    public void showToastMessage(String msg, int length) {
        runOnUi(() -> {
            Toast toast = Toast.makeText(activity, msg, length);
            toast.show();
        });
    }

    public void restoreCustomButtonText() {
        btnSetupImImportStandard.setText(getResources().getString(R.string.setup_im_load_standard));
    }

    public void clearTable(String tableName, boolean restoreUserRecords){
        if (setupImController != null) {
            setupImController.clearTable(tableName, restoreUserRecords);
        }
    }



    /**
     * Returns the number of records in a table. Used by dialogs/handlers.
     */
    public int countRecords(String tabelName) {
        if (setupImController != null) {
            return setupImController.countRecords(tabelName);
        }
        return 0;
    }



    /**
     * Imports a text mapping file (.lime, .cin, or delimited text) into the specified database table.
     *
     * <p>This method imports text mapping files into the database table. It delegates to
     * {@link DBServer#importTxtTable(String, String, LIMEProgressListener)} to perform the actual
     * import operation. After import completes, it optionally restores user-learned records from
     * a backup table if requested.
     *
     * <p>The method performs the following operations:
     * <ul>
     *   <li>Shows progress indicator with custom message</li>
     *   <li>Delegates to DBServer.importTxtTable() for the actual import</li>
     *   <li>Updates progress during import via LIMEProgressListener callbacks</li>
     *   <li>Optionally restores user-learned records from backup table after import</li>
     *   <li>Cancels progress indicator when complete</li>
     * </ul>
     *
     * <p>This method is called from SetupImLoadDialog when a user selects a text file to import.
     *
     * @param sourceFile The text file to import (.lime, .cin, or delimited text)
     * @param tableName The IM type (table name) to import into (e.g., "custom", "phonetic")
     * @param restoreUserRecords If true, restores user-learned records from backup table after import
     */
    public void importTxtTable(File sourceFile, String tableName, boolean restoreUserRecords) {
        setupImController.importTxtTable(sourceFile, tableName, restoreUserRecords);

    }

    /**
     * Imports the default related database from raw resources.
     */
    public void importZippedDbDefaultRelated() {
        if (setupImController != null) {
            setupImController.importDbDefaultRelated();
        }
    }

    /**
     * Imports a compressed related database file (.limedb).
     */
    public void importZippedDbRelated(File unit) {
        if (setupImController != null) {
            setupImController.importZippedDbRelated(unit);
        }
    }

    /**
     * Imports a compressed database file (.limedb) into the specified IM table.
     */
    public void importZippedDb(File unit, String tableName, boolean restoreUserRecords) {
        if (setupImController != null) {
            setupImController.importZippedDb(unit, tableName, restoreUserRecords);
        }
    }

    /**
     * Downloads and loads an IM database from the cloud.
     *
     * <p>This method checks network availability, then delegates the download
     * and import flow to the controller. After import completes, it optionally
     * restores user-learned records from a backup table if requested.
     *
     *
     * @param tableName The IM table to import into (e.g., "custom", "phonetic")
     * @param imTableVariant One of available table variants for a specific IM
     * @param restoreLearning Whether to restore user-learned records after import
     */
    public void downloadAndImportZippedDb(String tableName, String imTableVariant, boolean restoreLearning) {
        if (setupImController != null) {
            setupImController.downloadAndImportZippedDb(tableName, getUrlForImTableVariant(imTableVariant) , restoreLearning);
        }
    }




    @Override
    public void showImportDialog() {
        FragmentTransaction ft = getParentFragmentManager().beginTransaction();
        // Pass empty import text when shown from the setup UI
        ImportDialog dialog = ImportDialog.newInstance("");
        dialog.show(ft, "importdialog");
    }





    // Utility: run UI updates on the main thread safely
    private void runOnUi(Runnable r) {
        if (activity == null || r == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            activity.runOnUiThread(r);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (setupImController != null) {
            setupImController.setSetupImView(null);
        }
    }

    @Override
    public void onError(String message) {
        Log.e(TAG, message);
        showToastMessage(message, Toast.LENGTH_LONG);
    }


    private String getUrlForImTableVariant(String imTableVariant) {
        Map<String, String> imTableVariantToUrl = new HashMap<>();
        imTableVariantToUrl.put(LIME.IM_ARRAY, LIME.DATABASE_CLOUD_IM_ARRAY);
        imTableVariantToUrl.put(LIME.IM_ARRAY10, LIME.DATABASE_CLOUD_IM_ARRAY10);
        imTableVariantToUrl.put(LIME.IM_CJ_BIG5, LIME.DATABASE_CLOUD_IM_CJ_BIG5);
        imTableVariantToUrl.put(LIME.IM_CJ, LIME.DATABASE_CLOUD_IM_CJ);
        imTableVariantToUrl.put(LIME.IM_CJHK, LIME.DATABASE_CLOUD_IM_CJHK);
        imTableVariantToUrl.put(LIME.IM_CJ5, LIME.DATABASE_CLOUD_IM_CJ5);
        imTableVariantToUrl.put(LIME.IM_DAYI, LIME.DATABASE_CLOUD_IM_DAYI);
        imTableVariantToUrl.put(LIME.IM_DAYIUNI, LIME.DATABASE_CLOUD_IM_DAYIUNI);
        imTableVariantToUrl.put(LIME.IM_DAYIUNIP, LIME.DATABASE_CLOUD_IM_DAYIUNIP);
        imTableVariantToUrl.put(LIME.IM_ECJ, LIME.DATABASE_CLOUD_IM_ECJ);
        imTableVariantToUrl.put(LIME.IM_ECJHK, LIME.DATABASE_CLOUD_IM_ECJHK);
        imTableVariantToUrl.put(LIME.IM_EZ, LIME.DATABASE_CLOUD_IM_EZ);
        imTableVariantToUrl.put(LIME.IM_PHONETIC_BIG5, LIME.DATABASE_CLOUD_IM_PHONETIC_BIG5);
        imTableVariantToUrl.put(LIME.IM_PHONETIC_ADV_BIG5, LIME.DATABASE_CLOUD_IM_PHONETICCOMPLETE_BIG5);
        imTableVariantToUrl.put(LIME.IM_PHONETIC, LIME.DATABASE_CLOUD_IM_PHONETIC);
        imTableVariantToUrl.put(LIME.IM_PHONETIC_ADV, LIME.DATABASE_CLOUD_IM_PHONETICCOMPLETE);
        imTableVariantToUrl.put(LIME.IM_PINYIN, LIME.DATABASE_CLOUD_IM_PINYIN);
        imTableVariantToUrl.put(LIME.IM_PINYINGB, LIME.DATABASE_CLOUD_IM_PINYINGB);
        imTableVariantToUrl.put(LIME.IM_SCJ, LIME.DATABASE_CLOUD_IM_SCJ);
        imTableVariantToUrl.put(LIME.IM_WB, LIME.DATABASE_CLOUD_IM_WB);
        imTableVariantToUrl.put(LIME.IM_HS, LIME.DATABASE_CLOUD_IM_HS);
        imTableVariantToUrl.put(LIME.IM_HS_V1, LIME.DATABASE_CLOUD_IM_HS_V1);
        imTableVariantToUrl.put(LIME.IM_HS_V2, LIME.DATABASE_CLOUD_IM_HS_V2);
        imTableVariantToUrl.put(LIME.IM_HS_V3, LIME.DATABASE_CLOUD_IM_HS_V3);
        return imTableVariantToUrl.get(imTableVariant);
    }


}
