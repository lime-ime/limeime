package org.limeime.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import org.limeime.R;
import org.limeime.global.LIME;
import org.limeime.ui.controller.SetupImController;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Manages share operations for IM tables and related phrases.
 * 
 * <p>This class encapsulates all share-related functionality, including:
 * <ul>
 *   <li>Sharing IM tables as text files (.lime)</li>
 *   <li>Sharing IM tables as compressed database files (.limedb)</li>
 *   <li>Sharing related phrases as text files</li>
 *   <li>Sharing related phrases as compressed database files</li>
 *   <li>Android share intent creation and file provider URI handling</li>
 * </ul>
 * 
 * <p>This extraction reduces LIMESettings's complexity and provides a reusable
 * component for share operations across the application.
 */
public class ShareManager {
    
    private static final String TAG = "ShareManager";

    private final LIMESettings activity;
    private final SetupImController setupImController;
    private final ProgressManager progressManager;
    private final ActivityResultLauncher<Intent> saveExportLauncher;

    private Thread shareThread;
    private String pendingSaveTable;
    private boolean pendingSaveText;
    private boolean pendingSaveRelated;

    /**
     * Creates a new ShareManager.
     *
     * <p>Must be constructed before the activity reaches the STARTED state
     * (e.g. in {@code onCreate}), since it registers an
     * {@link ActivityResultLauncher} on the activity.
     *
     * @param activity The activity context for UI operations
     * @param setupImController The controller for export operations
     * @param progressManager The progress manager for showing export progress
     */
    public ShareManager(LIMESettings activity, SetupImController setupImController, ProgressManager progressManager) {
        this.activity = activity;
        this.setupImController = setupImController;
        this.progressManager = progressManager;
        this.saveExportLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleSaveResult(result.getResultCode(), result.getData()));
    }
    
    /**
     * Initiates sharing of an IM table as a text file.
     * 
     * <p>This method starts a background thread that exports the specified IM table
     * to a text file and then shares it using the Android share intent.
     * 
     * @param tableName The IM type (table name) to share
     */
    public void shareImAsText(String tableName) {
        shareThread = new Thread(() -> {
            if (progressManager != null) progressManager.show();
            if (progressManager != null) progressManager.updateProgress(activity.getResources().getString(R.string.share_step_initial));

            File cacheDir = activity.getExternalCacheDir();
            if (cacheDir == null) {
                cacheDir = activity.getCacheDir();
            }
            File target = new File(cacheDir, tableName + ".lime");

            File exported = setupImController.exportTxtTable(tableName, target,
                    () -> {
                        if (progressManager != null) {
                            progressManager.updateProgress(activity.getResources().getString(R.string.share_step_write));
                        }
                    });

            if (progressManager != null) progressManager.dismiss();
            if (exported != null) {
                shareFile(exported.getAbsolutePath(), "text/plain");
            } else {
                Log.e(TAG, "Failed to export table: " + tableName);
            }
        });
        shareThread.start();
    }

    /**
     * Initiates sharing of an IM table as a compressed database file.
     * 
     * <p>This method starts a background thread that exports the specified IM table
     * to a compressed .limedb file and then shares it using the Android share intent.
     * 
     * @param tableName The IM type (table name) to share
     */
    public void exportAndShareImTable(String tableName) {
        shareThread = new Thread(() -> {
            if (progressManager != null) progressManager.show();

            File cacheDir = activity.getExternalCacheDir();
            if (cacheDir == null) {
                cacheDir = activity.getCacheDir();
            }

            File targetFileZip = new File(cacheDir, tableName + ".limedb");

            if (progressManager != null) progressManager.updateProgress(activity.getResources().getString(R.string.share_step_initial));
            File exportedFile = setupImController.exportZippedDb(tableName, targetFileZip,
                    () -> {
                        if (progressManager != null) {
                            progressManager.updateProgress(activity.getResources().getString(R.string.share_step_write));
                        }
                    });

            if (progressManager != null) progressManager.dismiss();
            if (exportedFile != null) {
                shareFile(exportedFile.getAbsolutePath(), "application/zip");
            } else {
                Log.e(TAG, "Failed to export database: " + tableName);
            }
        });
        shareThread.start();
    }

    /**
     * Initiates sharing of the related phrases table as a compressed database file.
     * 
     * <p>This method starts a background thread that exports the related phrases
     * table to a compressed .limedb file and then shares it using the Android share intent.
     */
    public void shareRelatedAsDatabase() {
        shareThread = new Thread(() -> {
            if (progressManager != null) progressManager.show();

            File cacheDir = activity.getExternalCacheDir();
            if (cacheDir == null) {
                cacheDir = activity.getCacheDir();
            }

            File targetFileZip = new File(cacheDir, LIME.DB_TABLE_RELATED + ".limedb");

            if (progressManager != null) progressManager.updateProgress(activity.getResources().getString(R.string.share_step_initial));
            File exportedFile = setupImController.exportZippedDbRelated(targetFileZip,
                    () -> {
                        if (progressManager != null) {
                            progressManager.updateProgress(activity.getResources().getString(R.string.share_step_write));
                        }
                    });

            if (progressManager != null) progressManager.dismiss();
            if (exportedFile != null) {
                shareFile(exportedFile.getAbsolutePath(), "application/zip");
            } else {
                Log.e(TAG, "Failed to export database");
            }
        });
        shareThread.start();
    }

    public void saveImAsText(String tableName) {
        launchSavePicker(tableName, true, false);
    }

    public void saveImAsDatabase(String tableName) {
        launchSavePicker(tableName, false, false);
    }

    public void saveRelatedAsDatabase() {
        launchSavePicker(LIME.DB_TABLE_RELATED, false, true);
    }

    private void launchSavePicker(String tableName, boolean asText, boolean related) {
        pendingSaveTable = tableName;
        pendingSaveText = asText;
        pendingSaveRelated = related;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(asText ? "text/plain" : "application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, tableName + (asText ? ".lime" : ".limedb"));
        saveExportLauncher.launch(intent);
    }

    private void handleSaveResult(int resultCode, Intent data) {
        if (pendingSaveTable == null) return;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            clearPendingSave();
            return;
        }

        Uri targetUri = data.getData();
        String tableName = pendingSaveTable;
        boolean asText = pendingSaveText;
        boolean related = pendingSaveRelated;
        clearPendingSave();

        shareThread = new Thread(() -> exportAndWriteToUri(tableName, asText, related, targetUri));
        shareThread.start();
    }

    private void exportAndWriteToUri(String tableName, boolean asText, boolean related, Uri targetUri) {
        File cacheDir = activity.getExternalCacheDir();
        if (cacheDir == null) {
            cacheDir = activity.getCacheDir();
        }
        File tempFile = new File(cacheDir, tableName + (asText ? ".lime" : ".limedb"));

        File exported;
        if (asText) {
            exported = related
                    ? setupImController.exportTxtTableRelated(tempFile, null)
                    : setupImController.exportTxtTable(tableName, tempFile, null);
        } else {
            exported = related
                    ? setupImController.exportZippedDbRelated(tempFile, null)
                    : setupImController.exportZippedDb(tableName, tempFile, null);
        }

        if (exported == null) {
            Log.e(TAG, "Failed to export file for local save: " + tableName);
            return;
        }

        try (InputStream in = new FileInputStream(exported);
             OutputStream out = activity.getContentResolver().openOutputStream(targetUri)) {
            if (out == null) throw new java.io.IOException("Unable to open output stream");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            activity.runOnUiThread(() -> Toast.makeText(activity, R.string.share_save_local_finish, Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            Log.e(TAG, "Failed to save export file", e);
            activity.runOnUiThread(() -> Toast.makeText(activity, R.string.error_export_table, Toast.LENGTH_LONG).show());
        }
    }

    private void clearPendingSave() {
        pendingSaveTable = null;
        pendingSaveText = false;
        pendingSaveRelated = false;
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
     * @param filePath The path to the file to share
     * @param mimeType The MIME type of the file (e.g., "text/plain" or "application/zip")
     */
    public Intent createShareIntent(String filePath, String mimeType) {
        try {
            Intent sharingIntent = new Intent(Intent.ACTION_SEND);
            sharingIntent.setType(mimeType);

            File target = new File(filePath);
            
            Uri targetUri = androidx.core.content.FileProvider.getUriForFile(
                activity,
                activity.getApplicationContext().getPackageName() + ".fileprovider",
                target
            );
            
            sharingIntent.putExtra(Intent.EXTRA_STREAM, targetUri);
            sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            sharingIntent.putExtra(Intent.EXTRA_TEXT, target.getName());
            
            return Intent.createChooser(sharingIntent, target.getName());
        } catch (Exception e) {
            Log.e(TAG, "Error creating share intent", e);
            return null;
        }
    }

    public void shareFile(String filePath, String mimeType) {
        Intent intent = createShareIntent(filePath, mimeType);
        if (intent != null) {
            activity.startActivity(intent);
        }
    }

}
