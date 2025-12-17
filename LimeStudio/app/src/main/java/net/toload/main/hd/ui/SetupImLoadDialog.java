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
import android.os.RemoteException;
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
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.R;
import net.toload.main.hd.data.Word;
import net.toload.main.hd.global.LIMEProgressListener;
import net.toload.main.hd.global.LIMEUtilities;
import net.toload.main.hd.limedb.LimeDB;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;


public class SetupImLoadDialog extends DialogFragment {

    private final String TAG = "SetupImLoadDialog";

    private SetupImHandler handler;

    private CheckBox chkSetupImBackupLearning;
    private CheckBox chkSetupImRestoreLearning;

    private String imtype = null;

    private LimeDB datasource;
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
                                    if (imtype.equalsIgnoreCase(LIME.DB_RELATED)) {
                                        loadDbRelatedMapping(file);
                                        dismiss();
                                    } else {
                                        if (file.getName().toLowerCase().endsWith(LIME.SUPPORT_FILE_EXT_TXT) ||
                                                file.getName().toLowerCase().endsWith(LIME.SUPPORT_FILE_EXT_LIME) ||
                                                file.getName().toLowerCase().endsWith(LIME.SUPPORT_FILE_EXT_CIN)) {
                                            loadMapping(file);
                                            dismiss();
                                        } else if( file.getName().toLowerCase().endsWith(LIME.SUPPORT_FILE_EXT_LIMEDB)) {
                                            loadDbMapping(file);
                                            dismiss();
                                        }
                                    }
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
        datasource = new LimeDB(activity);
        DBSrv = new DBServer(activity);

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

        if (imtype.equalsIgnoreCase(LIME.DB_RELATED)) {
            assert getDialog() != null;
            getDialog().getWindow().setTitle(getResources().getString(R.string.setup_im_related_title));

            btnSetupImDialogCustom.setText(getResources().getString(R.string.setup_im_import_related_default));
            btnSetupImDialogCustom.setOnClickListener(v -> {
                AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
                alertDialog.setMessage(activity.getResources().getString(R.string.setup_im_import_related_default_confirm));
                alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(R.string.dialog_confirm),
                        (dialog, which) -> {
                            loadDefaultRelated();
                            handler.initialImButtons();
                            dismiss();
                        });
                alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, activity.getResources().getString(R.string.dialog_cancel),
                        (dialog, which) -> dialog.dismiss());
                alertDialog.show();
            });

            btnSetupImDialogLoad1.setText(getResources().getString(R.string.setup_im_import_related));
            btnSetupImDialogLoad1.setOnClickListener(v -> selectMappingFile());

            btnSetupImDialogLoad2.setVisibility(View.GONE);
            btnSetupImDialogLoad3.setVisibility(View.GONE);
            btnSetupImDialogLoad4.setVisibility(View.GONE);
            btnSetupImDialogLoad5.setVisibility(View.GONE);
            btnSetupImDialogLoad6.setVisibility(View.GONE);
            chkSetupImBackupLearning.setVisibility(View.GONE);
            chkSetupImRestoreLearning.setVisibility(View.GONE);
        } else {
            int imcount = datasource.count(imtype);
            if (imcount > 0) {
                assert getDialog() != null;
                getDialog().getWindow().setTitle(getResources().getString(R.string.setup_im_dialog_title_remove));
                btnSetupImDialogLoad1.setText(getResources().getString(R.string.setup_im_dialog_remove));
                btnSetupImDialogLoad1.setOnClickListener(v -> {
                    AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
                    alertDialog.setMessage(activity.getResources().getString(R.string.setup_im_dialog_remove_confirm_message));
                    alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getResources().getString(R.string.dialog_confirm),
                            (dialog, which) -> {
                                boolean backuplearning = chkSetupImBackupLearning.isChecked();
                                handler.resetImTable(imtype, backuplearning);
                                if (imtype.equals(LIME.DB_TABLE_CUSTOM)) {
                                    handler.updateCustomButton();
                                }
                                handler.initialImButtons();
                                dismiss();
                            });
                    alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, activity.getResources().getString(R.string.dialog_cancel),
                            (dialog, which) -> dialog.dismiss());
                    alertDialog.show();
                });
                btnSetupImDialogLoad2.setVisibility(View.GONE);
                btnSetupImDialogLoad3.setVisibility(View.GONE);
                btnSetupImDialogLoad4.setVisibility(View.GONE);
                btnSetupImDialogLoad5.setVisibility(View.GONE);
                btnSetupImDialogLoad6.setVisibility(View.GONE);
                btnSetupImDialogCustom.setVisibility(View.GONE);
                chkSetupImBackupLearning.setVisibility(View.VISIBLE);
                chkSetupImRestoreLearning.setVisibility(View.GONE);
            } else {
                chkSetupImBackupLearning.setVisibility(View.GONE);
                chkSetupImRestoreLearning.setVisibility(View.VISIBLE);
                assert getDialog() != null;
                getDialog().getWindow().setTitle(getResources().getString(R.string.setup_im_dialog_title));
                btnSetupImDialogCustom.setEnabled(true);
                btnSetupImDialogCustom.setOnClickListener(v -> selectMappingFile());

                switch (imtype) {
                    case LIME.DB_TABLE_PHONETIC:
                        btnSetupImDialogLoad1.setText(getString(R.string.download_button_with_count, getString(R.string.l3_im_download_from_phonetic_big5), "15,945"));
                        btnSetupImDialogLoad1.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_PHONETIC, LIME.IM_PHONETIC_BIG5));
                        btnSetupImDialogLoad2.setText(getString(R.string.download_button_with_count, getString(R.string.l3_im_download_from_phonetic), "34,838"));
                        btnSetupImDialogLoad2.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_PHONETIC, LIME.IM_PHONETIC_ADV));
                        btnSetupImDialogLoad3.setText(getString(R.string.download_button_with_count, getString(R.string.l3_im_download_from_phonetic_adv_big5), "76,122"));
                        btnSetupImDialogLoad3.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_PHONETIC, LIME.IM_PHONETIC_ADV_BIG5));
                        btnSetupImDialogLoad4.setText(getString(R.string.download_button_with_count, getString(R.string.l3_im_download_from_phonetic_adv), "95,029"));
                        btnSetupImDialogLoad4.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_PHONETIC, LIME.IM_PHONETIC_ADV));
                        btnSetupImDialogLoad5.setVisibility(View.GONE);
                        btnSetupImDialogLoad6.setVisibility(View.GONE);
                        break;
                    case LIME.DB_TABLE_CJ:
                        btnSetupImDialogLoad1.setText(getString(R.string.download_button_with_count, getString(R.string.l3_im_download_from_cj_big5), "13,859"));
                        btnSetupImDialogLoad1.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_CJ, LIME.IM_CJ_BIG5));
                        btnSetupImDialogLoad2.setText(getString(R.string.download_button_with_count, getString(R.string.l3_im_download_from_cj), "28,596"));
                        btnSetupImDialogLoad2.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_CJ, LIME.IM_CJ));
                        btnSetupImDialogLoad3.setText(getString(R.string.download_button_with_count, getString(R.string.l3_im_download_from_cjk_hk_cj), "30,278"));
                        btnSetupImDialogLoad3.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_CJ, LIME.IM_CJHK));
                        btnSetupImDialogLoad4.setVisibility(View.GONE);
                        btnSetupImDialogLoad5.setVisibility(View.GONE);
                        btnSetupImDialogLoad6.setVisibility(View.GONE);
                        break;
                    case LIME.DB_TABLE_CJ5:
                        btnSetupImDialogLoad1.setText(getString(R.string.download_button_with_count, getString(R.string.l3_im_download_from_cj5), "24,004"));
                        btnSetupImDialogLoad1.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_CJ5, LIME.IM_CJ5));
                        btnSetupImDialogLoad2.setVisibility(View.GONE);
                        btnSetupImDialogLoad3.setVisibility(View.GONE);
                        btnSetupImDialogLoad4.setVisibility(View.GONE);
                        btnSetupImDialogLoad5.setVisibility(View.GONE);
                        btnSetupImDialogLoad6.setVisibility(View.GONE);
                        break;
                    case LIME.DB_TABLE_SCJ:
                        btnSetupImDialogLoad1.setText(getString(R.string.download_button_with_count, getString(R.string.l3_im_download_from_scj), "74,250"));
                        btnSetupImDialogLoad1.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_SCJ, LIME.IM_SCJ));
                        btnSetupImDialogLoad2.setVisibility(View.GONE);
                        btnSetupImDialogLoad3.setVisibility(View.GONE);
                        btnSetupImDialogLoad4.setVisibility(View.GONE);
                        btnSetupImDialogLoad5.setVisibility(View.GONE);
                        btnSetupImDialogLoad6.setVisibility(View.GONE);
                        break;
                    case LIME.DB_TABLE_ECJ:
                        btnSetupImDialogLoad1.setText(getString(R.string.download_button_with_count, getString(R.string.l3_im_download_from_ecj), "13,119"));
                        btnSetupImDialogLoad1.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_ECJ, LIME.IM_ECJ));
                        btnSetupImDialogLoad2.setText(getString(R.string.download_button_with_count, getString(R.string.l3_im_download_from_cjk_hk_ecj), "27,853"));
                        btnSetupImDialogLoad2.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_ECJ, LIME.IM_ECJHK));
                        btnSetupImDialogLoad3.setVisibility(View.GONE);
                        btnSetupImDialogLoad4.setVisibility(View.GONE);
                        btnSetupImDialogLoad5.setVisibility(View.GONE);
                        btnSetupImDialogLoad6.setVisibility(View.GONE);
                        break;
                    case LIME.DB_TABLE_DAYI:
                        btnSetupImDialogLoad1.setText(getString(R.string.download_button_with_count, getString(R.string.setup_load_download_dayiuni), "27,198"));
                        btnSetupImDialogLoad1.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_DAYI, LIME.IM_DAYIUNI));
                        btnSetupImDialogLoad2.setText(getString(R.string.download_button_with_count, getString(R.string.setup_load_download_dayiunip), "117,766"));
                        btnSetupImDialogLoad2.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_DAYI, LIME.IM_DAYIUNIP));
                        btnSetupImDialogLoad3.setText(getString(R.string.download_button_with_count, getString(R.string.l3_im_download_from_dayi), "18,638"));
                        btnSetupImDialogLoad3.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_DAYI, LIME.IM_DAYI));
                        btnSetupImDialogLoad4.setVisibility(View.GONE);
                        btnSetupImDialogLoad5.setVisibility(View.GONE);
                        btnSetupImDialogLoad6.setVisibility(View.GONE);
                        break;
                    case LIME.DB_TABLE_EZ:
                        btnSetupImDialogLoad1.setText(getString(R.string.download_button_with_count, getString(R.string.l3_im_download_from_ez), "14,422"));
                        btnSetupImDialogLoad1.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_EZ, LIME.IM_EZ));
                        btnSetupImDialogLoad2.setVisibility(View.GONE);
                        btnSetupImDialogLoad3.setVisibility(View.GONE);
                        btnSetupImDialogLoad4.setVisibility(View.GONE);
                        btnSetupImDialogLoad5.setVisibility(View.GONE);
                        btnSetupImDialogLoad6.setVisibility(View.GONE);
                        break;
                    case LIME.DB_TABLE_ARRAY:
                        btnSetupImDialogLoad1.setText(getString(R.string.download_button_with_count, getString(R.string.l3_im_download_from_array), "31,999"));
                        btnSetupImDialogLoad1.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_ARRAY, LIME.IM_ARRAY));
                        btnSetupImDialogLoad2.setVisibility(View.GONE);
                        btnSetupImDialogLoad3.setVisibility(View.GONE);
                        btnSetupImDialogLoad4.setVisibility(View.GONE);
                        btnSetupImDialogLoad5.setVisibility(View.GONE);
                        btnSetupImDialogLoad6.setVisibility(View.GONE);
                        break;
                    case LIME.DB_TABLE_ARRAY10:
                        btnSetupImDialogLoad1.setText(getString(R.string.download_button_with_count, getString(R.string.l3_im_download_from_array10), "31,700"));
                        btnSetupImDialogLoad1.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_ARRAY10, LIME.IM_ARRAY10));
                        btnSetupImDialogLoad2.setVisibility(View.GONE);
                        btnSetupImDialogLoad3.setVisibility(View.GONE);
                        btnSetupImDialogLoad4.setVisibility(View.GONE);
                        btnSetupImDialogLoad5.setVisibility(View.GONE);
                        btnSetupImDialogLoad6.setVisibility(View.GONE);
                        break;
                    case LIME.DB_TABLE_PINYIN:
                        btnSetupImDialogLoad1.setText(getString(R.string.download_button_with_count, getString(R.string.l3_im_download_from_pinyin_big5), "34,753"));
                        btnSetupImDialogLoad1.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_PINYIN, LIME.IM_PINYIN));
                        btnSetupImDialogLoad2.setVisibility(View.GONE);
                        btnSetupImDialogLoad3.setVisibility(View.GONE);
                        btnSetupImDialogLoad4.setVisibility(View.GONE);
                        btnSetupImDialogLoad5.setVisibility(View.GONE);
                        btnSetupImDialogLoad6.setVisibility(View.GONE);
                        break;
                    case LIME.DB_TABLE_WB:
                        btnSetupImDialogLoad1.setText(getString(R.string.download_button_with_count, getString(R.string.l3_im_download_from_wb), "26,378"));
                        btnSetupImDialogLoad1.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_WB, LIME.IM_WB));
                        btnSetupImDialogLoad2.setVisibility(View.GONE);
                        btnSetupImDialogLoad3.setVisibility(View.GONE);
                        btnSetupImDialogLoad4.setVisibility(View.GONE);
                        btnSetupImDialogLoad5.setVisibility(View.GONE);
                        btnSetupImDialogLoad6.setVisibility(View.GONE);
                        break;
                    case LIME.DB_TABLE_HS:
                        btnSetupImDialogLoad1.setText(getString(R.string.download_button_with_count, getString(R.string.l3_im_download_from_hs), "183,659"));
                        btnSetupImDialogLoad1.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_HS, LIME.IM_HS));
                        btnSetupImDialogLoad2.setText(getString(R.string.download_button_with_count, getString(R.string.l3_im_download_from_hs_v1), "50,845"));
                        btnSetupImDialogLoad2.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_HS, LIME.IM_HS_V1));
                        btnSetupImDialogLoad3.setText(getString(R.string.download_button_with_count, getString(R.string.l3_im_download_from_hs_v2), "50,838"));
                        btnSetupImDialogLoad3.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_HS, LIME.IM_HS_V2));
                        btnSetupImDialogLoad4.setText(getString(R.string.download_button_with_count, getString(R.string.l3_im_download_from_hs_v3), "64,324"));
                        btnSetupImDialogLoad4.setOnClickListener(v -> downloadAndLoadIm(LIME.DB_TABLE_HS, LIME.IM_HS_V3));
                        btnSetupImDialogLoad5.setVisibility(View.GONE);
                        btnSetupImDialogLoad6.setVisibility(View.GONE);
                        break;
                    default:
                        btnSetupImDialogLoad1.setVisibility(View.GONE);
                        btnSetupImDialogLoad2.setVisibility(View.GONE);
                        btnSetupImDialogLoad3.setVisibility(View.GONE);
                        btnSetupImDialogLoad4.setVisibility(View.GONE);
                        btnSetupImDialogLoad5.setVisibility(View.GONE);
                        btnSetupImDialogLoad6.setVisibility(View.GONE);
                        break;
                }
            }
        }

        Button btnSetupImDialogCancel = rootView.findViewById(R.id.btnSetupImDialogCancel);
        btnSetupImDialogCancel.setOnClickListener(v -> dismiss());

        return rootView;
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
        boolean restorelearning = chkSetupImRestoreLearning.isChecked();
        if (isNetworkAvailable(activity)) {
            String url = null;
            switch (type) {
                case LIME.IM_ARRAY:
                    url = LIME.DATABASE_CLOUD_IM_ARRAY;
                    break;
                case LIME.IM_ARRAY10:
                    url = LIME.DATABASE_CLOUD_IM_ARRAY10;
                    break;
                case LIME.IM_CJ_BIG5:
                    url = LIME.DATABASE_CLOUD_IM_CJ_BIG5;
                    break;
                case LIME.IM_CJ:
                    url = LIME.DATABASE_CLOUD_IM_CJ;
                    break;
                case LIME.IM_CJHK:
                    url = LIME.DATABASE_CLOUD_IM_CJHK;
                    break;
                case LIME.IM_CJ5:
                    url = LIME.DATABASE_CLOUD_IM_CJ5;
                    break;
                case LIME.IM_DAYI:
                    url = LIME.DATABASE_CLOUD_IM_DAYI;
                    break;
                case LIME.IM_DAYIUNI:
                    url = LIME.DATABASE_CLOUD_IM_DAYIUNI;
                    break;
                case LIME.IM_DAYIUNIP:
                    url = LIME.DATABASE_CLOUD_IM_DAYIUNIP;
                    break;
                case LIME.IM_ECJ:
                    url = LIME.DATABASE_CLOUD_IM_ECJ;
                    break;
                case LIME.IM_ECJHK:
                    url = LIME.DATABASE_CLOUD_IM_ECJHK;
                    break;
                case LIME.IM_EZ:
                    url = LIME.DATABASE_CLOUD_IM_EZ;
                    break;
                case LIME.IM_PHONETIC_BIG5:
                    url = LIME.DATABASE_CLOUD_IM_PHONETIC_BIG5;
                    break;
                case LIME.IM_PHONETIC_ADV_BIG5:
                    url = LIME.DATABASE_CLOUD_IM_PHONETICCOMPLETE_BIG5;
                    break;
                case LIME.IM_PHONETIC:
                    url = LIME.DATABASE_CLOUD_IM_PHONETIC;
                    break;
                case LIME.IM_PHONETIC_ADV:
                    url = LIME.DATABASE_CLOUD_IM_PHONETICCOMPLETE;
                    break;
                case LIME.IM_PINYIN:
                    url = LIME.DATABASE_CLOUD_IM_PINYIN;
                    break;
                case LIME.IM_PINYINGB:
                    url = LIME.DATABASE_CLOUD_IM_PINYINGB;
                    break;
                case LIME.IM_SCJ:
                    url = LIME.DATABASE_CLOUD_IM_SCJ;
                    break;
                case LIME.IM_WB:
                    url = LIME.DATABASE_CLOUD_IM_WB;
                    break;
                case LIME.IM_HS:
                    url = LIME.DATABASE_CLOUD_IM_HS;
                    break;
                case LIME.IM_HS_V1:
                    url = LIME.DATABASE_CLOUD_IM_HS_V1;
                    break;
                case LIME.IM_HS_V2:
                    url = LIME.DATABASE_CLOUD_IM_HS_V2;
                    break;
                case LIME.IM_HS_V3:
                    url = LIME.DATABASE_CLOUD_IM_HS_V3;
                    break;
            }

            Thread loadthread = new Thread(new SetupImLoadRunnable(getActivity(), handler, code, type, url, restorelearning));
            loadthread.start();
            dismiss();
        } else {
            showToastMessage(getResources().getString(R.string.l3_tab_initial_error), Toast.LENGTH_LONG);
        }
    }

    public void showToastMessage(String msg, int length) {
        Toast toast = Toast.makeText(activity, msg, length);
        toast.show();
    }

    private File saveUriToFile(Uri uri) {
        try {
            InputStream inputStream = activity.getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;

            String fileName = getFileName(uri);
            File file = new File(activity.getCacheDir(), fileName);
            OutputStream outputStream = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
            inputStream.close();
            return file;
        } catch (Exception e) {
            e.printStackTrace();
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

    public void loadDefaultRelated() {
        try {
            File relateDBFile = LIMEUtilities.isFileExist(activity.getDatabasePath("related.db").getAbsolutePath());
            if (relateDBFile != null && !relateDBFile.delete()) Log.w(TAG,"Failed to delete related.db");
            File relatedDbPath = LIMEUtilities.isFileNotExist(activity.getDatabasePath("related.db").getAbsolutePath());
            if (relatedDbPath != null) LIMEUtilities.copyRAWFile(activity.getResources().openRawResource(R.raw.lime), relatedDbPath);
            DBSrv.importBackupRelatedDb(relatedDbPath);
            assert relatedDbPath != null;
            relatedDbPath.deleteOnExit();
            showToastMessage(activity.getResources().getString(R.string.setup_im_import_complete), Toast.LENGTH_LONG);
        } catch (Exception e) {
            e.printStackTrace();
            showToastMessage(activity.getResources().getString(R.string.error_import_db), Toast.LENGTH_LONG);
        }
    }

    public void loadDbRelatedMapping(File unit) {
        try {
            List<String> unzipPaths = LIMEUtilities.unzip(unit.getAbsolutePath(), unit.getParent(), true);
            if (unzipPaths.size() != 1) {
                showToastMessage(activity.getResources().getString(R.string.error_import_db), Toast.LENGTH_LONG);
            } else {
                File fileToImport = new File(unzipPaths.get(0));
                DBSrv.importBackupRelatedDb(fileToImport);
                fileToImport.deleteOnExit();
                showToastMessage(activity.getResources().getString(R.string.setup_im_import_complete), Toast.LENGTH_LONG);
            }
        } catch (Exception e) {
            e.printStackTrace();
            showToastMessage(activity.getResources().getString(R.string.error_import_db), Toast.LENGTH_LONG);
        }
    }

    public void loadDbMapping(File unit) {
        try {
            List<String> unzipPaths = LIMEUtilities.unzip(unit.getAbsolutePath(), unit.getParent(), true);
            if (unzipPaths.size() != 1) {
                showToastMessage(activity.getResources().getString(R.string.error_import_db), Toast.LENGTH_LONG);
            } else {
                File fileToImport = new File(unzipPaths.get(0));
                DBSrv.importBackupDb(fileToImport.getAbsoluteFile(), imtype);
                fileToImport.deleteOnExit();
                showToastMessage(activity.getResources().getString(R.string.setup_im_import_complete), Toast.LENGTH_LONG);
            }
        } catch (Exception e) {
            e.printStackTrace();
            showToastMessage(activity.getResources().getString(R.string.error_import_db), Toast.LENGTH_LONG);
        }
    }

    public void loadMapping(File unit) {
        handler.showProgress(false, activity.getResources().getString(R.string.setup_im_dialog_custom));
        try {
            DBSrv.loadMapping(unit.getAbsolutePath(), imtype, new LIMEProgressListener() {
                @Override
                public void onProgress(long percentageDone, long var2, String status) {
                    if (status != null && !status.isEmpty()) handler.updateProgress(status);
                    handler.updateProgress((int) percentageDone);
                }

                @Override
                public void onStatusUpdate(String status) {
                    if (status != null && !status.isEmpty()) handler.updateProgress(status);
                }

                @Override
                public void onError(int code, String source) {
                    if (source != null && !source.isEmpty()) showToastMessage(source, Toast.LENGTH_LONG);
                }

                @Override
                public void onPostExecute(boolean success, String status, int code) {
                    boolean restorelearning = chkSetupImRestoreLearning.isChecked();
                    if (restorelearning) {
                        handler.updateProgress(activity.getResources().getString(R.string.setup_im_restore_learning_data));
                        handler.updateProgress(0);
                        boolean check = datasource.checkBackuptable(imtype);
                        handler.updateProgress(5);
                        if (check) {
                            String backupTableName = imtype + "_user";
                            int userRecordsCount = datasource.countMapping(backupTableName);
                            handler.updateProgress(10);
                            if (userRecordsCount == 0) return;
                            try {
                                Cursor cursorbackup = datasource.rawQuery("select * from " + backupTableName);
                                List<Word> backuplist = Word.getList(cursorbackup);
                                cursorbackup.close();
                                int progressvalue = 0;
                                int recordcount = 0;
                                int recordtotal = backuplist.size();
                                for (Word w : backuplist) {
                                    recordcount++;
                                    datasource.addOrUpdateMappingRecord(imtype, w.getCode(), w.getWord(), w.getScore());
                                    int progress = (int) ((double) recordcount / recordtotal * 90 + 10);
                                    if (progress != progressvalue) {
                                        progressvalue = progress;
                                        handler.updateProgress(progressvalue);
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            handler.updateProgress(100);
                        }
                    }
                    handler.cancelProgress();
                }
            });
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}