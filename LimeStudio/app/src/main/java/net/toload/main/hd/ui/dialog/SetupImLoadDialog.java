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

package net.toload.main.hd.ui.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.DialogFragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;

import net.toload.main.hd.global.LIME;
import net.toload.main.hd.R;
import net.toload.main.hd.ui.view.SetupImFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
// java.util collections not used here


/**
 * Dialog fragment to load or remove IM table data.
 *
 * <p>Handles selecting files for import, restoring default tables, and
 * removing existing table contents. Integrates with `SetupImFragment` for
 * executing the selected operations.
 */
public class SetupImLoadDialog extends DialogFragment {

    private final String TAG = "SetupImLoadDialog";
    
    // Supported file extensions
    private static final String SUPPORT_FILE_EXT_TXT = "txt";
    private static final String SUPPORT_FILE_EXT_LIME = "lime";
    private static final String SUPPORT_FILE_EXT_LIMEDB = "limedb";
    private static final String SUPPORT_FILE_EXT_CIN = "cin";

    private SetupImFragment fragment;

    private CheckBox chkSetupImBackupLearning;
    private CheckBox chkSetupImRestoreLearning;

    private String tableName = null;

    private static final String TABLE_NAME = "TABLE_NAME";

    private ActivityResultLauncher<Intent> filePickerLauncher;

    //public SetupImLoadDialog() {}

    public void setFragment(SetupImFragment fragment) {
        this.fragment = fragment;
    }

    public static SetupImLoadDialog newInstance(String tableName) {
        SetupImLoadDialog frg = new SetupImLoadDialog();
        Bundle args = new Bundle();
        args.putString(TABLE_NAME, tableName);
        frg.setArguments(args);
        return frg;
    }
    
    @Override
    public void onStart() {
        super.onStart();

        Dialog dialog = getDialog();
        if (dialog != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            Uri uri = data.getData();
                            if (uri != null) {
                                File file = saveUriToFile(uri);
                                if (file != null) {
                                    handleFileSelection(file);
                                }
                            }
                        }
                    }
                });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        assert getArguments() != null;
        tableName = getArguments().getString(TABLE_NAME);

        View rootView = inflater.inflate(R.layout.fragment_dialog_im, container, false);

        chkSetupImBackupLearning = rootView.findViewById(R.id.chkSetupImBackupLearning);
        chkSetupImRestoreLearning = rootView.findViewById(R.id.chkSetupImRestoreLearning);

        Button btnSetupImDialogCustom = rootView.findViewById(R.id.btnSetupImDialogCustom);
        Button btnSetupImDialogLoad1 = rootView.findViewById(R.id.btnSetupImDialogLoad1);
        Button btnSetupImDialogLoad2 = rootView.findViewById(R.id.btnSetupImDialogLoad2);
        Button btnSetupImDialogLoad3 = rootView.findViewById(R.id.btnSetupImDialogLoad3);
        Button btnSetupImDialogLoad4 = rootView.findViewById(R.id.btnSetupImDialogLoad4);
        Button btnSetupImDialogLoad5 = rootView.findViewById(R.id.btnSetupImDialogLoad5);
        Button btnSetupImDialogLoad6 = rootView.findViewById(R.id.btnSetupImDialogLoad6);

        if (tableName.equalsIgnoreCase(LIME.DB_TABLE_RELATED)) {
            assert getDialog() != null;
            getDialog().getWindow().setTitle(getResources().getString(R.string.setup_im_related_title));

            btnSetupImDialogCustom.setText(getResources().getString(R.string.setup_im_import_related_default));
            btnSetupImDialogCustom.setOnClickListener(v -> showConfirmDialog(
                    getResources().getString(R.string.setup_im_import_related_default_confirm),
                    () -> {
                        fragment.importZippedDbDefaultRelated();
                        if (fragment != null) fragment.refreshButtonState();
                        dismiss();
                    }));

            btnSetupImDialogLoad1.setText(getResources().getString(R.string.setup_im_import_related));
            btnSetupImDialogLoad1.setOnClickListener(v -> showFilePicker());

            hideButtons(btnSetupImDialogLoad2, btnSetupImDialogLoad3, btnSetupImDialogLoad4,
                    btnSetupImDialogLoad5, btnSetupImDialogLoad6);
            chkSetupImBackupLearning.setVisibility(View.GONE);
            chkSetupImRestoreLearning.setVisibility(View.GONE);
        } else {
            int recordCount = (fragment != null) ? fragment.countRecords(tableName) : 0;
            if (recordCount > 0) {
                assert getDialog() != null;
                getDialog().getWindow().setTitle(getResources().getString(R.string.setup_im_dialog_title_remove));
                btnSetupImDialogLoad1.setText(getResources().getString(R.string.setup_im_dialog_remove));
                btnSetupImDialogLoad1.setOnClickListener(v -> showConfirmDialog(
                        getResources().getString(R.string.setup_im_dialog_remove_confirm_message),
                        () -> {
                            boolean backupUserRecords = chkSetupImBackupLearning.isChecked();
                            fragment.clearTable(tableName, backupUserRecords);
                            if (tableName.equals(LIME.DB_TABLE_CUSTOM)) {
                                fragment.restoreCustomButtonText();
                            }
                            if (fragment != null) fragment.refreshButtonState();
                            dismiss();
                        }));
                hideButtons(btnSetupImDialogLoad2, btnSetupImDialogLoad3, btnSetupImDialogLoad4,
                        btnSetupImDialogLoad5, btnSetupImDialogLoad6, btnSetupImDialogCustom);
                chkSetupImBackupLearning.setVisibility(View.VISIBLE);
                chkSetupImRestoreLearning.setVisibility(View.GONE);
            } else {  // Empty table. Show import and download buttons
                chkSetupImBackupLearning.setVisibility(View.GONE);
                chkSetupImRestoreLearning.setVisibility(View.VISIBLE);
                assert getDialog() != null;
                getDialog().getWindow().setTitle(getResources().getString(R.string.setup_im_dialog_title));
                btnSetupImDialogCustom.setEnabled(true);
                btnSetupImDialogCustom.setOnClickListener(v -> showFilePicker());

                setupButtons(btnSetupImDialogLoad1, btnSetupImDialogLoad2, btnSetupImDialogLoad3,
                        btnSetupImDialogLoad4, btnSetupImDialogLoad5, btnSetupImDialogLoad6);
            }
        }

        Button btnSetupImDialogCancel = rootView.findViewById(R.id.btnSetupImDialogCancel);
        btnSetupImDialogCancel.setOnClickListener(v -> dismiss());

        return rootView;
    }

    /**
     * Handles file selection based on file type and IM type.
     */
    private void handleFileSelection(File file) {
        String fileName = file.getName().toLowerCase();
        boolean restoreUserRecords = chkSetupImRestoreLearning.isChecked();
        
        if (tableName.equalsIgnoreCase(LIME.DB_TABLE_RELATED)) {
            fragment.importZippedDbRelated(file);
            dismiss();
        } else if (isTextFile(fileName)) {
            fragment.importTxtTable(file, tableName, restoreUserRecords);
            dismiss();
        } else if (fileName.endsWith(SUPPORT_FILE_EXT_LIMEDB)) {
            fragment.importZippedDb(file, tableName, restoreUserRecords);
            dismiss();
        }
    }
    
    /**
     * Checks if file is a supported text format (.txt, .lime, .cin).
     */
    private boolean isTextFile(String fileName) {
        return fileName.endsWith(SUPPORT_FILE_EXT_TXT) ||
               fileName.endsWith(SUPPORT_FILE_EXT_LIME) ||
               fileName.endsWith(SUPPORT_FILE_EXT_CIN);
    }
    
    private void showFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        filePickerLauncher.launch(intent);
    }

    /**
     * Downloads and loads an IM database from the cloud.
     */
    public void downloadAndLoadIm(String tableName, String imTableVariant) {

        fragment.downloadAndImportZippedDb(tableName, imTableVariant, chkSetupImRestoreLearning.isChecked());
        dismiss();
    }

    /**
     * Shows a confirmation dialog with positive and negative buttons.
     */
    private void showConfirmDialog(String message, Runnable onConfirm) {
        Activity activity = getActivity();
        if (activity == null) return;
        
        AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setMessage(message);
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, 
                getResources().getString(R.string.dialog_confirm),
                (dialog, which) -> onConfirm.run());
        alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, 
                getResources().getString(R.string.dialog_cancel),
                (dialog, which) -> dialog.dismiss());
        alertDialog.show();
    }
    
    /**
     * Sets up download buttons based on IM type.
     */
    private void setupButtons(Button btn1, Button btn2, Button btn3, Button btn4, Button btn5, Button btn6) {
        Button[] buttons = {btn1, btn2, btn3, btn4, btn5, btn6};
        ButtonConfig[] configs = getButtonConfigsForTableName(tableName);
        
        // Setup visible buttons
        for (int i = 0; i < configs.length && i < buttons.length; i++) {
            ButtonConfig config = configs[i];
            if (config != null) {
                setupDownloadButtons(buttons[i], config.textResId, config.count, tableName, config.imTableVariant);
            }
        }
        
        // Hide unused buttons
        for (int i = configs.length; i < buttons.length; i++) {
            buttons[i].setVisibility(View.GONE);
        }
    }
    
    /**
     * Configuration class for download buttons.
     */
    private static class ButtonConfig {
        final int textResId;
        final String count;
        final String imTableVariant;
        
        ButtonConfig(int textResId, String count, String imTableVariant) {
            this.textResId = textResId;
            this.count = count;
            this.imTableVariant = imTableVariant;
        }
    }
    
    /**
     * Gets button configurations for a specific IM type.
     */
    private ButtonConfig[] getButtonConfigsForTableName(String tableName) {
        switch (tableName) {
            case LIME.DB_TABLE_PHONETIC:
                return new ButtonConfig[]{
                    new ButtonConfig(R.string.l3_im_download_from_phonetic_big5, "15,945", LIME.IM_PHONETIC_BIG5),
                    new ButtonConfig(R.string.l3_im_download_from_phonetic, "34,838", LIME.IM_PHONETIC_ADV),
                    new ButtonConfig(R.string.l3_im_download_from_phonetic_adv_big5, "76,122", LIME.IM_PHONETIC_ADV_BIG5),
                    new ButtonConfig(R.string.l3_im_download_from_phonetic_adv, "95,029", LIME.IM_PHONETIC_ADV)
                };
            case LIME.DB_TABLE_CJ:
                return new ButtonConfig[]{
                    new ButtonConfig(R.string.l3_im_download_from_cj_big5, "13,859", LIME.IM_CJ_BIG5),
                    new ButtonConfig(R.string.l3_im_download_from_cj, "28,596", LIME.IM_CJ),
                    new ButtonConfig(R.string.l3_im_download_from_cjk_hk_cj, "30,278", LIME.IM_CJHK)
                };
            case LIME.DB_TABLE_CJ5:
                return new ButtonConfig[]{
                    new ButtonConfig(R.string.l3_im_download_from_cj5, "24,004", LIME.IM_CJ5)
                };
            case LIME.DB_TABLE_SCJ:
                return new ButtonConfig[]{
                    new ButtonConfig(R.string.l3_im_download_from_scj, "74,250", LIME.IM_SCJ)
                };
            case LIME.DB_TABLE_ECJ:
                return new ButtonConfig[]{
                    new ButtonConfig(R.string.l3_im_download_from_ecj, "13,119", LIME.IM_ECJ),
                    new ButtonConfig(R.string.l3_im_download_from_cjk_hk_ecj, "27,853", LIME.IM_ECJHK)
                };
            case LIME.DB_TABLE_DAYI:
                return new ButtonConfig[]{
                    new ButtonConfig(R.string.setup_load_download_dayiuni, "27,198", LIME.IM_DAYIUNI),
                    new ButtonConfig(R.string.setup_load_download_dayiunip, "117,766", LIME.IM_DAYIUNIP),
                    new ButtonConfig(R.string.l3_im_download_from_dayi, "18,638", LIME.IM_DAYI)
                };
            case LIME.DB_TABLE_EZ:
                return new ButtonConfig[]{
                    new ButtonConfig(R.string.l3_im_download_from_ez, "14,422", LIME.IM_EZ)
                };
            case LIME.DB_TABLE_ARRAY:
                return new ButtonConfig[]{
                    new ButtonConfig(R.string.l3_im_download_from_array, "32,386", LIME.IM_ARRAY)
                };
            case LIME.DB_TABLE_ARRAY10:
                return new ButtonConfig[]{
                    new ButtonConfig(R.string.l3_im_download_from_array10, "32,120", LIME.IM_ARRAY10)
                };
            case LIME.DB_TABLE_PINYIN:
                return new ButtonConfig[]{
                    new ButtonConfig(R.string.l3_im_download_from_pinyin_big5, "34,753", LIME.IM_PINYIN)
                };
            case LIME.DB_TABLE_WB:
                return new ButtonConfig[]{
                    new ButtonConfig(R.string.l3_im_download_from_wb, "26,378", LIME.IM_WB)
                };
            case LIME.DB_TABLE_HS:
                return new ButtonConfig[]{
                    new ButtonConfig(R.string.l3_im_download_from_hs, "183,659", LIME.IM_HS),
                    new ButtonConfig(R.string.l3_im_download_from_hs_v1, "50,845", LIME.IM_HS_V1),
                    new ButtonConfig(R.string.l3_im_download_from_hs_v2, "50,838", LIME.IM_HS_V2),
                    new ButtonConfig(R.string.l3_im_download_from_hs_v3, "64,324", LIME.IM_HS_V3)
                };
            default:
                return new ButtonConfig[0];
        }
    }
    
    /**
     * Sets up a download button with text and click listener.
     */
    private void setupDownloadButtons(Button button, int textResId, String count, String tableName, String imTableVariant) {
        button.setText(getString(R.string.download_button_with_count, getString(textResId), count));
        button.setOnClickListener(v -> downloadAndLoadIm(tableName, imTableVariant));
    }


    /**
     * Hides multiple buttons at once.
     */
    private void hideButtons(Button... buttons) {
        for (Button button : buttons) {
            button.setVisibility(View.GONE);
        }
    }

    /**
     * Validates file extension and type.
     * Checks if the file has a supported extension (.txt, .lime, .limedb, .cin)
     */
    private boolean validateFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith("." + SUPPORT_FILE_EXT_TXT) ||
               lowerName.endsWith("." + SUPPORT_FILE_EXT_LIME) ||
               lowerName.endsWith("." + SUPPORT_FILE_EXT_LIMEDB) ||
               lowerName.endsWith("." + SUPPORT_FILE_EXT_CIN);
    }

    /**
     * Validates input file selection.
     * Ensures file has valid extension and format.
     */
    private boolean validateInput(File file) {
        if (file == null || !file.exists()) {
            handleError("File does not exist");
            return false;
        }
        if (!validateFileExtension(file.getName())) {
            handleError("Unsupported file extension");
            return false;
        }
        return true;
    }

    /**
     * Handles errors during file selection or validation.
     * Displays error message to user.
     */
    private void handleError(String errorMessage) {
        Log.e(TAG, "Error: " + errorMessage);
        if (getActivity() != null) {
            android.widget.Toast.makeText(getActivity(), errorMessage, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private File saveUriToFile(Uri uri) {
        Activity activity = getActivity();
        if (activity == null) return null;
        
        try (InputStream inputStream = activity.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) return null;

            String fileName = getFileName(uri);
            File file = new File(activity.getCacheDir(), fileName);
            
            try (OutputStream outputStream = new FileOutputStream(file)) {
                byte[] buffer = new byte[LIME.BUFFER_SIZE_1KB];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
            }
            
            return file;
        } catch (Exception e) {
            Log.e(TAG, "Error saving file from URI", e);
            return null;
        }
    }

    private String getFileName(Uri uri) {
        Activity activity = getActivity();
        if (activity == null) return "";
        
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = activity.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if(nameIndex != -1){
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

}
