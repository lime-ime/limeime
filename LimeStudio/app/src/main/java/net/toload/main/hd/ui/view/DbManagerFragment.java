package net.toload.main.hd.ui.view;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import net.toload.main.hd.R;
import net.toload.main.hd.ui.LIMESettings;
import net.toload.main.hd.ui.controller.SetupImController;

import java.io.File;
import java.util.List;

public class DbManagerFragment extends Fragment {

    private static final String TAG = "DbManagerFragment";

    private enum BackupRestoreType {
        BACKUP,
        RESTORE,
        BACKUP_TO_DOWNLOADS
    }

    private SetupImController setupImController;
    private Activity activity;

    private MaterialCardView dbStatusCard;
    private TextView dbStatusText;

    private ActivityResultLauncher<Intent> backupLauncher;
    private ActivityResultLauncher<Intent> restoreLauncher;

    public static DbManagerFragment newInstance() {
        return new DbManagerFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        backupLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) performBackup(uri);
                    }
                });

        restoreLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) performRestore(uri);
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        activity = getActivity();
        if (activity instanceof LIMESettings) {
            setupImController = ((LIMESettings) activity).getSetupImController();
        }

        View root = inflater.inflate(R.layout.fragment_db_manager, container, false);
        NestedScrollView scrollView = root.findViewById(R.id.db_manager_scroll);
        if (scrollView != null) {
            ScrollableTabHelper.applyToNestedScrollView(activity, scrollView);
        }

        dbStatusCard = root.findViewById(R.id.dbStatusCard);
        dbStatusText = root.findViewById(R.id.dbStatusText);

        MaterialButton btnBackup = root.findViewById(R.id.btnDbBackup);
        MaterialButton btnRestore = root.findViewById(R.id.btnDbRestore);
        MaterialButton btnRestoreDefault = root.findViewById(R.id.btnDbRestoreDefault);

        btnBackup.setOnClickListener(v -> backupLocalDrive());
        btnRestore.setOnClickListener(v -> restoreLocalDrive());
        btnRestoreDefault.setOnClickListener(v -> confirmRestoreDefault());

        return root;
    }

    // -----------------------------------------------------------------------
    // Backup
    // -----------------------------------------------------------------------

    public void backupLocalDrive() {
        if (backupLauncher == null) {
            showToastMessage(getString(R.string.l3_initial_backup_error), Toast.LENGTH_SHORT);
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_TITLE, "limeBackup.zip");

            PackageManager pm = requireActivity().getPackageManager();
            List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
            if (intent.resolveActivity(pm) == null || activities == null || activities.isEmpty()) {
                showAlertDialog(BackupRestoreType.BACKUP_TO_DOWNLOADS);
                return;
            }
            showAlertDialog(BackupRestoreType.BACKUP);
        } catch (Exception e) {
            Log.e(TAG, "Error checking backup options", e);
            showToastMessage(getString(R.string.l3_initial_backup_error), Toast.LENGTH_SHORT);
        }
    }

    private void launchBackupFilePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_TITLE, "limeBackup.zip");

            PackageManager pm = requireActivity().getPackageManager();
            List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
            if (intent.resolveActivity(pm) == null || activities == null || activities.isEmpty()) {
                saveBackupToDownloads();
                return;
            }
            backupLauncher.launch(Intent.createChooser(intent, "Save Backup"));
        } catch (Exception e) {
            Log.e(TAG, "Error launching backup file picker", e);
            saveBackupToDownloads();
        }
    }

    private void saveBackupToDownloads() {
        Activity act = activity;
        if (act == null) return;
        android.content.ContentResolver resolver = act.getContentResolver();
        String errorMsg = getString(R.string.l3_initial_backup_error);
        String pkgName = act.getApplicationContext().getPackageName();

        new Thread(() -> {
            try {
                Uri backupUri;
                String fileName = "limeBackup.zip";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    backupUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (backupUri == null) {
                        runOnUi(() -> showToastMessage(errorMsg, Toast.LENGTH_SHORT));
                        return;
                    }
                } else {
                    @SuppressWarnings("deprecation")
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (downloadsDir == null) {
                        runOnUi(() -> showToastMessage(errorMsg, Toast.LENGTH_SHORT));
                        return;
                    }
                    if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
                        runOnUi(() -> showToastMessage(errorMsg, Toast.LENGTH_SHORT));
                        return;
                    }
                    File backupFile = new File(downloadsDir, fileName);
                    int counter = 1;
                    while (backupFile.exists()) {
                        backupFile = new File(downloadsDir, "limeBackup(" + counter + ").zip");
                        counter++;
                    }
                    backupUri = FileProvider.getUriForFile(act, pkgName + ".fileprovider", backupFile);
                }
                final Uri finalUri = backupUri;
                runOnUi(() -> performBackup(finalUri));
            } catch (Exception e) {
                Log.e(TAG, "Error saving backup to Downloads", e);
                runOnUi(() -> showToastMessage(errorMsg, Toast.LENGTH_SHORT));
            }
        }).start();
    }

    private void performBackup(Uri uri) {
        try {
            if (setupImController != null) setupImController.performBackup(uri);
            runOnUi(() -> setStatus(getString(R.string.db_status_backup_ok)));
        } catch (Exception e) {
            Log.e(TAG, "Failed to backup database", e);
            showToastMessage(getString(R.string.l3_initial_backup_error), Toast.LENGTH_LONG);
            runOnUi(() -> setStatus(getString(R.string.db_status_backup_fail, e.getMessage() != null ? e.getMessage() : "unknown")));
        }
    }

    // -----------------------------------------------------------------------
    // Restore
    // -----------------------------------------------------------------------

    public void restoreLocalDrive() {
        if (restoreLauncher == null) {
            showToastMessage(getString(R.string.l3_initial_restore_error), Toast.LENGTH_SHORT);
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/zip");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            Intent chooserIntent = Intent.createChooser(intent, "Select Backup");
            if (chooserIntent.resolveActivity(requireActivity().getPackageManager()) == null) {
                showToastMessage(getString(R.string.l3_initial_restore_error), Toast.LENGTH_SHORT);
                return;
            }
            showAlertDialog(BackupRestoreType.RESTORE);
        } catch (Exception e) {
            Log.e(TAG, "Error checking restore options", e);
            showToastMessage(getString(R.string.l3_initial_restore_error), Toast.LENGTH_SHORT);
        }
    }

    private void launchRestoreFilePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/zip");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            Intent chooserIntent = Intent.createChooser(intent, "Select Backup");
            if (chooserIntent.resolveActivity(requireActivity().getPackageManager()) == null) {
                showToastMessage(getString(R.string.l3_initial_restore_error), Toast.LENGTH_SHORT);
                return;
            }
            restoreLauncher.launch(chooserIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error launching restore file picker", e);
            showToastMessage(getString(R.string.l3_initial_restore_error), Toast.LENGTH_SHORT);
        }
    }

    private void performRestore(Uri uri) {
        if (setupImController == null) {
            showToastMessage(getString(R.string.l3_initial_restore_error), Toast.LENGTH_LONG);
            runOnUi(() -> setStatus(getString(R.string.db_status_restore_fail, "controller unavailable")));
            return;
        }
        try {
            setupImController.performRestore(uri);
            // Only reached when no exception propagated from the controller/DBServer chain.
            runOnUi(() -> setStatus(getString(R.string.db_status_restore_ok)));
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore database", e);
            showToastMessage(getString(R.string.l3_initial_restore_error), Toast.LENGTH_LONG);
            runOnUi(() -> setStatus(getString(R.string.db_status_restore_fail, e.getMessage() != null ? e.getMessage() : "unknown")));
        }
    }

    // -----------------------------------------------------------------------
    // Restore default
    // -----------------------------------------------------------------------

    private void confirmRestoreDefault() {
        new AlertDialog.Builder(requireActivity())
                .setMessage(R.string.l3_restore_default_confirm)
                .setCancelable(false)
                .setPositiveButton(R.string.dialog_confirm, (d, w) -> {
                    if (setupImController != null) {
                        setupImController.restoredToDefault();
                        setStatus(getString(R.string.db_status_default_ok));
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, (d, w) -> {})
                .show();
    }

    // -----------------------------------------------------------------------
    // Shared confirm dialog (backup / restore)
    // -----------------------------------------------------------------------

    private void showAlertDialog(BackupRestoreType type) {
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
        new AlertDialog.Builder(requireActivity())
                .setMessage(getString(messageResId))
                .setCancelable(false)
                .setPositiveButton(R.string.dialog_confirm, (d, w) -> onConfirm.run())
                .setNegativeButton(R.string.dialog_cancel, (d, w) -> {})
                .show();
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        activity = null;
        setupImController = null;
    }

    private void setStatus(String message) {
        if (dbStatusCard != null) dbStatusCard.setVisibility(android.view.View.VISIBLE);
        if (dbStatusText != null) dbStatusText.setText(message);
    }

    private void showToastMessage(String msg, int length) {
        runOnUi(() -> {
            android.content.Context ctx = getContext();
            if (ctx != null) Toast.makeText(ctx, msg, length).show();
        });
    }

    private void runOnUi(Runnable r) {
        if (activity == null || r == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            activity.runOnUiThread(r);
        }
    }
}
