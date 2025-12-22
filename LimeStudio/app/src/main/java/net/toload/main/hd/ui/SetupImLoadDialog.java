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
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
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
import android.widget.Toast;

import net.toload.main.hd.DBServer;
import net.toload.main.hd.data.Record;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.R;
import net.toload.main.hd.global.LIMEUtilities;
import net.toload.main.hd.SearchServer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SetupImLoadDialog extends DialogFragment {

    private final String TAG = "SetupImLoadDialog";
    
    // Supported file extensions
    private static final String SUPPORT_FILE_EXT_TXT = "txt";
    private static final String SUPPORT_FILE_EXT_LIME = "lime";
    private static final String SUPPORT_FILE_EXT_LIMEDB = "limedb";
    private static final String SUPPORT_FILE_EXT_CIN = "cin";

    // Map IM types to their download URLs
    private static final Map<String, String> IM_TYPE_TO_URL = new HashMap<>();
    static {
        IM_TYPE_TO_URL.put(LIME.IM_ARRAY, LIME.DATABASE_CLOUD_IM_ARRAY);
        IM_TYPE_TO_URL.put(LIME.IM_ARRAY10, LIME.DATABASE_CLOUD_IM_ARRAY10);
        IM_TYPE_TO_URL.put(LIME.IM_CJ_BIG5, LIME.DATABASE_CLOUD_IM_CJ_BIG5);
        IM_TYPE_TO_URL.put(LIME.IM_CJ, LIME.DATABASE_CLOUD_IM_CJ);
        IM_TYPE_TO_URL.put(LIME.IM_CJHK, LIME.DATABASE_CLOUD_IM_CJHK);
        IM_TYPE_TO_URL.put(LIME.IM_CJ5, LIME.DATABASE_CLOUD_IM_CJ5);
        IM_TYPE_TO_URL.put(LIME.IM_DAYI, LIME.DATABASE_CLOUD_IM_DAYI);
        IM_TYPE_TO_URL.put(LIME.IM_DAYIUNI, LIME.DATABASE_CLOUD_IM_DAYIUNI);
        IM_TYPE_TO_URL.put(LIME.IM_DAYIUNIP, LIME.DATABASE_CLOUD_IM_DAYIUNIP);
        IM_TYPE_TO_URL.put(LIME.IM_ECJ, LIME.DATABASE_CLOUD_IM_ECJ);
        IM_TYPE_TO_URL.put(LIME.IM_ECJHK, LIME.DATABASE_CLOUD_IM_ECJHK);
        IM_TYPE_TO_URL.put(LIME.IM_EZ, LIME.DATABASE_CLOUD_IM_EZ);
        IM_TYPE_TO_URL.put(LIME.IM_PHONETIC_BIG5, LIME.DATABASE_CLOUD_IM_PHONETIC_BIG5);
        IM_TYPE_TO_URL.put(LIME.IM_PHONETIC_ADV_BIG5, LIME.DATABASE_CLOUD_IM_PHONETICCOMPLETE_BIG5);
        IM_TYPE_TO_URL.put(LIME.IM_PHONETIC, LIME.DATABASE_CLOUD_IM_PHONETIC);
        IM_TYPE_TO_URL.put(LIME.IM_PHONETIC_ADV, LIME.DATABASE_CLOUD_IM_PHONETICCOMPLETE);
        IM_TYPE_TO_URL.put(LIME.IM_PINYIN, LIME.DATABASE_CLOUD_IM_PINYIN);
        IM_TYPE_TO_URL.put(LIME.IM_PINYINGB, LIME.DATABASE_CLOUD_IM_PINYINGB);
        IM_TYPE_TO_URL.put(LIME.IM_SCJ, LIME.DATABASE_CLOUD_IM_SCJ);
        IM_TYPE_TO_URL.put(LIME.IM_WB, LIME.DATABASE_CLOUD_IM_WB);
        IM_TYPE_TO_URL.put(LIME.IM_HS, LIME.DATABASE_CLOUD_IM_HS);
        IM_TYPE_TO_URL.put(LIME.IM_HS_V1, LIME.DATABASE_CLOUD_IM_HS_V1);
        IM_TYPE_TO_URL.put(LIME.IM_HS_V2, LIME.DATABASE_CLOUD_IM_HS_V2);
        IM_TYPE_TO_URL.put(LIME.IM_HS_V3, LIME.DATABASE_CLOUD_IM_HS_V3);
    }

    private SetupImHandler handler;

    private CheckBox chkSetupImBackupLearning;
    private CheckBox chkSetupImRestoreLearning;

    private String imtype = null;

    private SearchServer searchServer;
    private DBServer DBSrv = null;
    private Activity activity;

    private static final String IM_TYPE = "IM_TYPE";

    private ActivityResultLauncher<Intent> filePickerLauncher;

    public SetupImLoadDialog() {}

    public void setHandler(SetupImHandler handler) {
        this.handler = handler;
    }

    public static SetupImLoadDialog newInstance(String imtype, SetupImHandler handler) {
        SetupImLoadDialog frg = new SetupImLoadDialog();
        Bundle args = new Bundle();
        args.putString(IM_TYPE, imtype);
        frg.setArguments(args);
        frg.setHandler(handler);
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
        imtype = getArguments().getString(IM_TYPE);
        activity = getActivity();
        searchServer = new SearchServer(activity);
        DBSrv = DBServer.getInstance(activity);

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

        if (imtype.equalsIgnoreCase(LIME.DB_TABLE_RELATED)) {
            assert getDialog() != null;
            getDialog().getWindow().setTitle(getResources().getString(R.string.setup_im_related_title));

            btnSetupImDialogCustom.setText(getResources().getString(R.string.setup_im_import_related_default));
            btnSetupImDialogCustom.setOnClickListener(v -> showConfirmDialog(
                    getResources().getString(R.string.setup_im_import_related_default_confirm),
                    () -> {
                        importDefaultRelated();
                        handler.initialImButtons();
                        dismiss();
                    }));

            btnSetupImDialogLoad1.setText(getResources().getString(R.string.setup_im_import_related));
            btnSetupImDialogLoad1.setOnClickListener(v -> selectMappingFile());

            hideButtons(btnSetupImDialogLoad2, btnSetupImDialogLoad3, btnSetupImDialogLoad4,
                    btnSetupImDialogLoad5, btnSetupImDialogLoad6);
            chkSetupImBackupLearning.setVisibility(View.GONE);
            chkSetupImRestoreLearning.setVisibility(View.GONE);
        } else {
            int imcount = searchServer.countRecords(imtype);
            if (imcount > 0) {
                assert getDialog() != null;
                getDialog().getWindow().setTitle(getResources().getString(R.string.setup_im_dialog_title_remove));
                btnSetupImDialogLoad1.setText(getResources().getString(R.string.setup_im_dialog_remove));
                btnSetupImDialogLoad1.setOnClickListener(v -> showConfirmDialog(
                        getResources().getString(R.string.setup_im_dialog_remove_confirm_message),
                        () -> {
                            boolean backupUserRecords = chkSetupImBackupLearning.isChecked();
                            handler.resetImTable(imtype, backupUserRecords);
                            if (imtype.equals(LIME.DB_TABLE_CUSTOM)) {
                                handler.updateCustomButton();
                            }
                            handler.initialImButtons();
                            dismiss();
                        }));
                hideButtons(btnSetupImDialogLoad2, btnSetupImDialogLoad3, btnSetupImDialogLoad4,
                        btnSetupImDialogLoad5, btnSetupImDialogLoad6, btnSetupImDialogCustom);
                chkSetupImBackupLearning.setVisibility(View.VISIBLE);
                chkSetupImRestoreLearning.setVisibility(View.GONE);
            } else {
                chkSetupImBackupLearning.setVisibility(View.GONE);
                chkSetupImRestoreLearning.setVisibility(View.VISIBLE);
                assert getDialog() != null;
                getDialog().getWindow().setTitle(getResources().getString(R.string.setup_im_dialog_title));
                btnSetupImDialogCustom.setEnabled(true);
                btnSetupImDialogCustom.setOnClickListener(v -> selectMappingFile());

                setupDownloadButtons(btnSetupImDialogLoad1, btnSetupImDialogLoad2, btnSetupImDialogLoad3,
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
        
        if (imtype.equalsIgnoreCase(LIME.DB_TABLE_RELATED)) {
            importDbRelated(file);
            dismiss();
        } else if (isTextFile(fileName)) {
            boolean restoreUserRecords = chkSetupImRestoreLearning.isChecked();
            handler.importTxtTable(file, imtype, restoreUserRecords);
            dismiss();
        } else if (fileName.endsWith(SUPPORT_FILE_EXT_LIMEDB)) {
            importDb(file);
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
    
    private void selectMappingFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        filePickerLauncher.launch(intent);
    }
    public boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Use modern API for API 23+ (getActiveNetwork() + getNetworkCapabilities())
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;

            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            );
        } else {
            // Use deprecated API for API < 23 (minSdk is 21, so we need this for API 21-22)
            // getActiveNetworkInfo() is deprecated in API 29+, but we only use it for API < 23
            @SuppressWarnings("deprecation")
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            if (activeNetworkInfo != null) {
                @SuppressWarnings("deprecation")
                boolean isConnected = activeNetworkInfo.isConnected();
                return isConnected;
            }
            return false;
        }
    }

    public void downloadAndLoadIm(String code, String type) {
        boolean restoreLearning = chkSetupImRestoreLearning.isChecked();
        if (!isNetworkAvailable(activity)) {
            showToastMessage(getResources().getString(R.string.l3_tab_initial_error), Toast.LENGTH_LONG);
            return;
        }
        
        String url = IM_TYPE_TO_URL.get(type);
        if (url == null) {
            Log.e(TAG, "No URL found for IM type: " + type);
            showToastMessage(getResources().getString(R.string.error_import_db), Toast.LENGTH_LONG);
            return;
        }

        Thread loadthread = new Thread(new SetupImLoadRunnable(getActivity(), handler, code, type, url, restoreLearning));
        loadthread.start();
        dismiss();
    }

    /**
     * Shows a confirmation dialog with positive and negative buttons.
     */
    private void showConfirmDialog(String message, Runnable onConfirm) {
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
    private void setupDownloadButtons(Button btn1, Button btn2, Button btn3, Button btn4, Button btn5, Button btn6) {
        Button[] buttons = {btn1, btn2, btn3, btn4, btn5, btn6};
        ButtonConfig[] configs = getButtonConfigsForImType(imtype);
        
        // Setup visible buttons
        for (int i = 0; i < configs.length && i < buttons.length; i++) {
            ButtonConfig config = configs[i];
            if (config != null) {
                setupDownloadButton(buttons[i], config.textResId, config.count, config.imType);
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
        final String imType;
        
        ButtonConfig(int textResId, String count, String imType) {
            this.textResId = textResId;
            this.count = count;
            this.imType = imType;
        }
    }
    
    /**
     * Gets button configurations for a specific IM type.
     */
    private ButtonConfig[] getButtonConfigsForImType(String imType) {
        switch (imType) {
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
                    new ButtonConfig(R.string.l3_im_download_from_array, "31,999", LIME.IM_ARRAY)
                };
            case LIME.DB_TABLE_ARRAY10:
                return new ButtonConfig[]{
                    new ButtonConfig(R.string.l3_im_download_from_array10, "31,700", LIME.IM_ARRAY10)
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
    private void setupDownloadButton(Button button, int textResId, String count, String imType) {
        button.setText(getString(R.string.download_button_with_count, getString(textResId), count));
        button.setOnClickListener(v -> downloadAndLoadIm(imtype, imType));
    }
    
    /**
     * Hides multiple buttons at once.
     */
    private void hideButtons(Button... buttons) {
        for (Button button : buttons) {
            button.setVisibility(View.GONE);
        }
    }
    
    public void showToastMessage(String msg, int length) {
        Toast toast = Toast.makeText(activity, msg, length);
        toast.show();
    }

    private File saveUriToFile(Uri uri) {
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

    /**
     * Imports the default related database from raw resources.
     * 
     * <p>This method imports the default related database file from raw resources.
     * The default related database is always zipped, so this method handles the
     * unzip operation before importing.
     * 
     * <p>This method:
     * <ul>
     *   <li>Copies the zipped database file from raw resources to a temporary location</li>
     *   <li>Delegates to DBServer.importZippedDbRelated() to handle unzip and import</li>
     * </ul>
     */
    public void importDefaultRelated() {
        try {
            File relateDBFile = LIMEUtilities.isFileExist(activity.getDatabasePath("related.db").getAbsolutePath());
            if (relateDBFile != null && !relateDBFile.delete()) Log.w(TAG,"Failed to delete related.db");
            File relatedDbPath = LIMEUtilities.isFileNotExist(activity.getDatabasePath("related.db").getAbsolutePath());
            if (relatedDbPath != null) {
                // Copy zipped database file from raw resources
                LIMEUtilities.copyRAWFile(activity.getResources().openRawResource(R.raw.lime), relatedDbPath);
                // Import using DBServer method that handles unzip
                DBSrv.importZippedDbRelated(relatedDbPath);
                relatedDbPath.deleteOnExit();
                showToastMessage(activity.getResources().getString(R.string.setup_im_import_complete), Toast.LENGTH_LONG);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in operation", e);
            showToastMessage(activity.getResources().getString(R.string.error_import_db), Toast.LENGTH_LONG);
        }
    }

    /**
     * Imports a compressed related database file (.limedb).
     * 
     * <p>This method delegates to DBServer.importZippedDbRelated() which handles
     * the unzip operation and database import. File operations are centralized
     * in DBServer to maintain architecture compliance.
     * 
     * @param unit The compressed related database file (.limedb) to import
     */
    public void importDbRelated(File unit) {
        try {
            DBSrv.importZippedDbRelated(unit);
            showToastMessage(activity.getResources().getString(R.string.setup_im_import_complete), Toast.LENGTH_LONG);
        } catch (Exception e) {
            Log.e(TAG, "Error in operation", e);
            showToastMessage(activity.getResources().getString(R.string.error_import_db), Toast.LENGTH_LONG);
        }
    }

    /**
     * Imports a compressed database file (.limedb) into the specified IM table.
     * 
     * <p>This method delegates to DBServer.importZippedDb() which handles the
     * unzip operation and database import. File operations are centralized in
     * DBServer to maintain architecture compliance.
     * 
     * @param unit The compressed database file (.limedb) to import
     */
    public void importDb(File unit) {
        try {
            DBSrv.importZippedDb(unit, imtype);
            showToastMessage(activity.getResources().getString(R.string.setup_im_import_complete), Toast.LENGTH_LONG);
        } catch (Exception e) {
            Log.e(TAG, "Error in operation", e);
            showToastMessage(activity.getResources().getString(R.string.error_import_db), Toast.LENGTH_LONG);
        }
    }

}