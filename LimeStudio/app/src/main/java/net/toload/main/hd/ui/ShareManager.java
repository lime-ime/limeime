package net.toload.main.hd.ui;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import net.toload.main.hd.R;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.ui.controller.SetupImController;

import java.io.File;

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
 * <p>This extraction reduces MainActivity's complexity and provides a reusable
 * component for share operations across the application.
 */
public class ShareManager {
    
    private static final String TAG = "ShareManager";
    
    private final MainActivity activity;
    private final SetupImController setupImController;
    private final ProgressManager progressManager;
    
    private Thread shareThread;
    
    /**
     * Creates a new ShareManager.
     * 
     * @param activity The activity context for UI operations
     * @param setupImController The controller for export operations
     * @param progressManager The progress manager for showing export progress
     */
    public ShareManager(MainActivity activity, SetupImController setupImController, ProgressManager progressManager) {
        this.activity = activity;
        this.setupImController = setupImController;
        this.progressManager = progressManager;
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
     * Initiates sharing of the related phrases table as a text file.
     * 
     * <p>This method starts a background thread that exports the related phrases
     * table to a text file and then shares it using the Android share intent.
     */
    public void shareRelatedAsText() {
        shareThread = new Thread(() -> {
            if (progressManager != null) progressManager.show();
            if (progressManager != null) progressManager.updateProgress(activity.getResources().getString(R.string.share_step_initial));

            File cacheDir = activity.getExternalCacheDir();
            if (cacheDir == null) {
                cacheDir = activity.getCacheDir();
            }
            File target = new File(cacheDir, "lime.related");

            File exported = setupImController.exportTxtTableRelated(target,
                    () -> {
                        if (progressManager != null) {
                            progressManager.updateProgress(activity.getResources().getString(R.string.share_step_write));
                        }
                    });

            if (progressManager != null) progressManager.dismiss();
            if (exported != null) {
                shareFile(exported.getAbsolutePath(), "text/plain");
            } else {
                Log.e(TAG, "Failed to export related table");
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
