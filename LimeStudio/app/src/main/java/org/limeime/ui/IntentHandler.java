package org.limeime.ui;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import org.limeime.R;
import org.limeime.ui.controller.SetupImController;
import org.limeime.ui.dialog.ImportDialog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Handles intent processing for LIMESettings.
 * 
 * <p>This class encapsulates all intent-related logic, including:
 * <ul>
 *   <li>ACTION_SEND: text/plain file imports (.lime, .cin files)</li>
 *   <li>ACTION_VIEW: file imports with URI scheme handling</li>
 *   <li>File validation and type checking</li>
 *   <li>Input stream to file conversion</li>
 * </ul>
 * 
 * <p>This extraction reduces LIMESettings's complexity and centralizes intent
 * handling logic for better maintainability.
 */
public class IntentHandler {
    
    private static final String TAG = "IntentHandler";
    
    private final LIMESettings activity;
    private final SetupImController setupImController;
    
    public IntentHandler(LIMESettings activity, SetupImController setupImController) {
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
        if (!isSupportedImportExtension(extension)) {
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
        
        // Always ask the user which destination IM table to import into.
        // The selected table is the semantic identity; source filenames are only
        // kept as metadata and must not decide the import target.
        handleImportFile(importFilepath);
    }
    
    /**
     * Handles text file import (.lime or .cin).
     * 
     * <p>Shows import dialog for user to select target IM table.
     * 
     * @param importFilepath The path to the text file to import
     */
    private void handleImportTxt(String importFilepath) {
        handleImportFile(importFilepath);
    }

    /**
     * Handles mapping/database file import (.lime, .cin, .limedb, or .zip).
     *
     * <p>Shows import dialog for user to select target IM table.
     *
     * @param importFilepath The path to the file to import
     */
    private void handleImportFile(String importFilepath) {
        try {
            File fileToImport = new File(importFilepath);
            setupImController.setFileToImport(fileToImport);  // Store for onImportTypeSelected callback
            
            androidx.fragment.app.FragmentTransaction ft = activity.getSupportFragmentManager().beginTransaction();
            ImportDialog dialog = ImportDialog.newInstanceForFile(importFilepath);
            dialog.setOnImportTypeSelectedListener(setupImController);
            ft.add(dialog, "ImportDialog");
            ft.commitAllowingStateLoss();
        } catch (Exception e) {
            String errorMessage = activity.getResources().getString(R.string.error_import_db);
            Log.e(TAG, errorMessage, e);
            showToast(errorMessage + ": " + e.getMessage());
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
    
    private boolean isSupportedImportExtension(String extension) {
        return "lime".equals(extension) || "cin".equals(extension) || "limedb".equals(extension) || "zip".equals(extension);
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
