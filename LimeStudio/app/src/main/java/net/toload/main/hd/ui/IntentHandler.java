package net.toload.main.hd.ui;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import net.toload.main.hd.R;
import net.toload.main.hd.ui.controller.SetupImController;
import net.toload.main.hd.ui.dialog.ImportDialog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Handles intent processing for MainActivity.
 * 
 * <p>This class encapsulates all intent-related logic, including:
 * <ul>
 *   <li>ACTION_SEND: text/plain file imports (.lime, .cin files)</li>
 *   <li>ACTION_VIEW: file imports with URI scheme handling</li>
 *   <li>File validation and type checking</li>
 *   <li>Input stream to file conversion</li>
 * </ul>
 * 
 * <p>This extraction reduces MainActivity's complexity and centralizes intent
 * handling logic for better maintainability.
 */
public class IntentHandler {
    
    private static final String TAG = "IntentHandler";
    
    private final MainActivity activity;
    private final SetupImController setupImController;
    
    public IntentHandler(MainActivity activity, SetupImController setupImController) {
        this.activity = activity;
        this.setupImController = setupImController;
    }
    
    /**
     * Processes intent and handles file imports.
     * 
     * <p>This method extracts intent data and routes to appropriate handlers:
     * <ul>
     *   <li>ACTION_SEND + text/plain: {@link #handleSendText(Intent)}</li>
     *   <li>ACTION_VIEW + content/file scheme: {@link #processFileImport(Intent)}</li>
     * </ul>
     * 
     * @param intent The intent to process
     */
    public void processIntent(Intent intent) {
        if (intent == null) return;
        
        String action = intent.getAction();
        String type = intent.getType();
        
        // 1. For ACTION_SEND, use handleSendText() to process
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                handleSendText(intent);
            }
        }
        // 2. For ACTION_VIEW, handle file imports
        else if (Intent.ACTION_VIEW.equals(action) && type != null) {
            processFileImport(intent);
        }
    }
    
    /**
     * Handles ACTION_SEND intent with text/plain type.
     * 
     * <p>Extracts the shared text and initiates file import process
     * by delegating to {@link #handleImportTxt(String)}.
     * 
     * @param intent The ACTION_SEND intent
     */
    private void handleSendText(Intent intent) {
        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (sharedText != null) {
            handleImportTxt(sharedText);
        }
    }
    
    /**
     * Processes ACTION_VIEW intent with file/content schemes.
     * 
     * <p>Validates URI, extracts filename, checks file type, and routes
     * to appropriate import handler based on file type.
     * 
     * @param intent The ACTION_VIEW intent
     */
    private void processFileImport(Intent intent) {
        String type = intent.getType();
        String scheme = intent.getScheme();
        Uri uri = intent.getData();
        
        if (uri == null) {
            Log.e(TAG, "Intent data URI is null");
            return;
        }
        
        // Validate scheme
        ContentResolver resolver = activity.getContentResolver();
        if (!isValidScheme(scheme)) {
            Log.e(TAG, "Invalid URI scheme: " + scheme);
            showToast(activity.getResources().getString(R.string.error_file_format));
            return;
        }
        
        // Extract filename
        String fileName = getContentName(resolver, uri);
        if (fileName == null) {
            fileName = uri.getLastPathSegment();
        }
        if (fileName == null) {
            String errorMessage = activity.getResources().getString(R.string.error_no_file_name);
            Log.e(TAG, errorMessage);
            showToast(errorMessage);
            return;
        }
        
        String extension = getFileExtension(fileName);
        if (extension.isEmpty()) {
            showToast(activity.getResources().getString(R.string.error_file_format));
            return;
        }
        
        // 3. Check if type matches extension
        if (!isFileTypeValid(type, extension)) {
            String errorMessage = activity.getResources().getString(R.string.error_file_format);
            Log.w(TAG, errorMessage);
            showToast(errorMessage);
            return;
        }
        
        // Read file from URI
        InputStream input;
        try {
            input = resolver.openInputStream(uri);
        } catch (FileNotFoundException e) {
            String errorMessage = activity.getResources().getString(R.string.error_file_opening_error);
            Log.e(TAG, errorMessage, e);
            showToast(errorMessage);
            return;
        }
        
        if (input == null) {
            Log.e(TAG, "Input stream is null");
            showToast(activity.getResources().getString(R.string.error_file_opening_error));
            return;
        }
        
        // Prepare import directory and file
        File importDir = new File(activity.getCacheDir(), "imports");
        if (!importDir.exists() && !importDir.mkdirs()) {
            Log.w(TAG, "Failed to create import dir: " + importDir.getAbsolutePath());
        }
        File fileToImport = new File(importDir, fileName);
        String importFilepath = fileToImport.getAbsolutePath();
        
        // Convert input stream to file
        InputStreamToFile(input, importFilepath);
        
        // Handle based on file type
        if ("text/plain".equals(type) && ("lime".equals(extension) || "cin".equals(extension))) {
            // 4. For text/plain with .lime or .cin, call handleImportTxt
            handleImportTxt(importFilepath);
        } else if ("application/zip".equals(type) && "limedb".equals(extension)) {
            // 5. For .limedb file, handle import
            String tableName = getFileNameWithoutExtension(fileName);
            handleLimedbImport(fileToImport, tableName);
        }
    }
    
    /**
     * Handles text file import (.lime or .cin).
     * 
     * <p>Shows import dialog for user to select target IM table.
     * 
     * @param importFilepath The path to the text file to import
     */
    private void handleImportTxt(String importFilepath) {
        try {
            File fileToImport = new File(importFilepath);
            setupImController.setFileToImport(fileToImport);  // Store for onImportTypeSelected callback
            
            androidx.fragment.app.FragmentTransaction ft = activity.getSupportFragmentManager().beginTransaction();
            ImportDialog dialog = ImportDialog.newInstanceForFile(importFilepath);
            dialog.setOnImportTypeSelectedListener(setupImController);
            dialog.show(ft, "ImportDialog");
        } catch (Exception e) {
            String errorMessage = activity.getResources().getString(R.string.error_import_db);
            Log.e(TAG, errorMessage, e);
            showToast(errorMessage + ": " + e.getMessage());
        }
    }
    
    /**
     * Handles .limedb (compressed database) import.
     * 
     * <p>Checks if table is empty before importing. If not empty, shows confirmation dialog.
     * If empty, proceeds with import. Delegates actual import to controller.
     * 
     * @param fileToImport The .limedb file to import
     * @param tableName The target table name
     */
    private void handleLimedbImport(File fileToImport, String tableName) {
        try {
            // Check if table is empty before importing
            int recordCount = setupImController.countRecords(tableName);
            
            if (recordCount > 0) {
                // Table is not empty: show single three-choice dialog (cancel/restore/don't restore)
                showImportConfirmationDialog(fileToImport, tableName);
            } else {
                // Table is empty: proceed with import (default: don't restore)
                performLimedbImport(fileToImport, tableName, false);
            }
        } catch (Exception e) {
            String errorMessage = activity.getResources().getString(R.string.error_import_db);
            Log.e(TAG, errorMessage, e);
            showToast(errorMessage + ": " + e.getMessage());
        }
    }
    
    /**
     * Shows confirmation dialog before importing to non-empty table.
     * 
     * @param fileToImport The .limedb file
     * @param tableName The target table name
     */
    private void showImportConfirmationDialog(File fileToImport, String tableName) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(activity);
        // Use the same strings as 1a alert dialog
        String title = activity.getResources().getString(R.string.setup_im_restore_learning_data);
        String message = activity.getResources().getString(R.string.setup_im_restore_learning_data_message);
        builder.setTitle(title);
        builder.setMessage(message);
        // Cancel
        builder.setNeutralButton(activity.getResources().getString(R.string.dialog_cancel),
            (dialog, which) -> dialog.dismiss());
        // Restore
        builder.setPositiveButton(activity.getResources().getString(R.string.dialog_yes),
            (dialog, which) -> performLimedbImport(fileToImport, tableName, true));
        // Don't restore
        builder.setNegativeButton(activity.getResources().getString(R.string.dialog_no),
            (dialog, which) -> performLimedbImport(fileToImport, tableName, false));
        builder.show();
    }
    
    /**
     * Performs the actual .limedb import.
     * 
     * @param fileToImport The .limedb file
     * @param tableName The target table name
     */
    private void performLimedbImport(File fileToImport, String tableName, boolean restoreUserRecords) {
        if (setupImController != null) {
            setupImController.importZippedDb(fileToImport, tableName, restoreUserRecords);
        }
    }
    
    /**
     * Converts input stream to file.
     * 
     * @param inputStream The input stream to read from
     * @param filePath The target file path to write to
     */
    private void InputStreamToFile(InputStream inputStream, String filePath) {
        try {
            OutputStream outputStream = new FileOutputStream(filePath);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
            inputStream.close();
        } catch (Exception e) {
            Log.e(TAG, "Error converting input stream to file", e);
            showToast(activity.getResources().getString(R.string.error_file_opening_error));
        }
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Extracts file extension from filename.
     * 
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
     * 
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
     * 
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
     * Validates URI scheme.
     * 
     * @param scheme The URI scheme to validate
     * @return true if scheme is valid for file operations
     */
    private boolean isValidScheme(String scheme) {
        if (scheme == null) return false;
        return ContentResolver.SCHEME_CONTENT.equals(scheme)
                || ContentResolver.SCHEME_FILE.equals(scheme)
                || "http".equals(scheme) || "https".equals(scheme) || "ftp".equals(scheme);
    }
    
    /**
     * Gets content name from URI.
     * 
     * @param resolver The ContentResolver
     * @param uri The URI
     * @return The content name, or null if not found
     */
    private String getContentName(ContentResolver resolver, Uri uri) {
        try {
            android.database.Cursor cursor = resolver.query(uri, null, null, null, null);
            if (cursor == null) return null;
            
            int nameIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME);
            cursor.moveToFirst();
            String name = cursor.getString(nameIndex);
            cursor.close();
            return name;
        } catch (Exception e) {
            Log.e(TAG, "Error getting content name", e);
            return null;
        }
    }
    
    /**
     * Shows a toast message.
     * 
     * @param message The message to show
     */
    private void showToast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
