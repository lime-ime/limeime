/*
 *
 *  *
 *  **    Copyright 2015, The LimeIME Open Source Project
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
import android.os.Bundle;
import android.os.RemoteException;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
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
import net.toload.main.hd.Lime;
import net.toload.main.hd.R;
import net.toload.main.hd.data.Im;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.global.LIMEUtilities;
import net.toload.main.hd.limedb.LimeDB;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;


/**
 * A placeholder fragment containing a simple rootView.
 */
public class SetupImFragment extends Fragment {

    // IM Log Tag
    private final String TAG = "SetupImFragment";

    // Debug Flag
    private final boolean DEBUG = false;

    // Basic
    private SetupImHandler handler;
    private Thread backupthread;
    private Thread restorethread;

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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        datasource = new LimeDB(this.getActivity());

        handler = new SetupImHandler(this);

        activity = getActivity();

        DBSrv = new DBServer(activity);
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

        btnSetupImBackupLocal.setOnClickListener(v -> showAlertDialog(Lime.BACKUP, Lime.LOCAL, getResources().getString(R.string.l3_initial_backup_confirm)));

        btnSetupImRestoreLocal.setOnClickListener(v -> showAlertDialog(Lime.RESTORE, Lime.LOCAL, getResources().getString(R.string.l3_initial_restore_confirm)));





        PackageInfo pInfo;
        try {
            pInfo = requireActivity().getPackageManager().getPackageInfo(requireActivity().getPackageName(), 0);
            long versionCode = pInfo.getLongVersionCode();
            String versionstr = "v"+ pInfo.versionName + " - " + versionCode;
            txtVersion = rootView.findViewById(R.id.txtVersion);
            txtVersion.setText(versionstr);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
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
                imlist = datasource.getIm(null, Lime.IM_TYPE_NAME);
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

                if(check.get(Lime.DB_TABLE_CUSTOM) != null){
                    btnSetupImImportStandard.setAlpha(Lime.HALF_ALPHA_VALUE);
                    btnSetupImImportStandard.setText(check.get(Lime.DB_TABLE_CUSTOM));
                    btnSetupImImportStandard.setTypeface(null, Typeface.ITALIC);
                }else {
                    btnSetupImImportStandard.setAlpha(Lime.NORMAL_ALPHA_VALUE);
                    btnSetupImImportStandard.setTypeface(null, Typeface.BOLD);
                }



                btnSetupImImportStandard.setOnClickListener(v -> {
                    FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                    SetupImLoadDialog dialog = SetupImLoadDialog.newInstance(Lime.DB_TABLE_CUSTOM, handler);
                    dialog.show(ft, "loadimdialog");

                });

                // User can always load new related table ...
                btnSetupImImportRelated.setOnClickListener(v -> {
                    FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                    SetupImLoadDialog dialog = SetupImLoadDialog.newInstance(Lime.DB_RELATED, handler);
                    dialog.show(ft, "loadimdialog");

                });

                if(check.get(Lime.DB_TABLE_PHONETIC) != null){
                    btnSetupImPhonetic.setAlpha(Lime.HALF_ALPHA_VALUE);
                    btnSetupImPhonetic.setTypeface(null, Typeface.ITALIC);
                }else {
                    btnSetupImPhonetic.setAlpha(Lime.NORMAL_ALPHA_VALUE);
                    btnSetupImPhonetic.setTypeface(null, Typeface.BOLD);
                }

                btnSetupImPhonetic.setOnClickListener(v -> {
                    FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                    SetupImLoadDialog dialog = SetupImLoadDialog.newInstance(Lime.DB_TABLE_PHONETIC, handler);
                    dialog.show(ft, "loadimdialog");
                });


                if(check.get(Lime.DB_TABLE_CJ) != null){
                    btnSetupImCj.setAlpha(Lime.HALF_ALPHA_VALUE);
                    btnSetupImCj.setTypeface(null, Typeface.ITALIC);
                }else {
                    btnSetupImCj.setAlpha(Lime.NORMAL_ALPHA_VALUE);
                    btnSetupImCj.setTypeface(null, Typeface.BOLD);
                }

                btnSetupImCj.setOnClickListener(v -> {
                    FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                    SetupImLoadDialog dialog = SetupImLoadDialog.newInstance(Lime.DB_TABLE_CJ, handler);
                    dialog.show(ft, "loadimdialog");
                });



                if(check.get(Lime.DB_TABLE_CJ5) != null){
                    btnSetupImCj5.setAlpha(Lime.HALF_ALPHA_VALUE);
                    btnSetupImCj5.setTypeface(null, Typeface.ITALIC);
                }else {
                    btnSetupImCj5.setAlpha(Lime.NORMAL_ALPHA_VALUE);
                    btnSetupImCj5.setTypeface(null, Typeface.BOLD);
                }

                btnSetupImCj5.setOnClickListener(v -> {
                    FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                    SetupImLoadDialog dialog = SetupImLoadDialog.newInstance(Lime.DB_TABLE_CJ5, handler);
                    dialog.show(ft, "loadimdialog");
                });

                if(check.get(Lime.DB_TABLE_SCJ) != null){
                    btnSetupImScj.setAlpha(Lime.HALF_ALPHA_VALUE);
                    btnSetupImScj.setTypeface(null, Typeface.ITALIC);
                }else {
                    btnSetupImScj.setAlpha(Lime.NORMAL_ALPHA_VALUE);
                    btnSetupImScj.setTypeface(null, Typeface.BOLD);
                }
                btnSetupImScj.setOnClickListener(v -> {
                    FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                    SetupImLoadDialog dialog = SetupImLoadDialog.newInstance(Lime.DB_TABLE_SCJ, handler);
                    dialog.show(ft, "loadimdialog");
                });

                if(check.get(Lime.DB_TABLE_ECJ) != null){
                    btnSetupImEcj.setAlpha(Lime.HALF_ALPHA_VALUE);
                    btnSetupImEcj.setTypeface(null, Typeface.ITALIC);
                }else {
                    btnSetupImEcj.setAlpha(Lime.NORMAL_ALPHA_VALUE);
                    btnSetupImEcj.setTypeface(null, Typeface.BOLD);
                }

                btnSetupImEcj.setOnClickListener(v -> {
                    FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                    SetupImLoadDialog dialog = SetupImLoadDialog.newInstance(Lime.DB_TABLE_ECJ, handler);
                    dialog.show(ft, "loadimdialog");
                });

                if(check.get(Lime.DB_TABLE_DAYI) != null){
                    btnSetupImDayi.setAlpha(Lime.HALF_ALPHA_VALUE);
                    btnSetupImDayi.setTypeface(null, Typeface.ITALIC);
                }else {
                    btnSetupImDayi.setAlpha(Lime.NORMAL_ALPHA_VALUE);
                    btnSetupImDayi.setTypeface(null, Typeface.BOLD);
                }

                btnSetupImDayi.setOnClickListener(v -> {
                    FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                    SetupImLoadDialog dialog = SetupImLoadDialog.newInstance(Lime.DB_TABLE_DAYI, handler);
                    dialog.show(ft, "loadimdialog");
                });

                if(check.get(Lime.DB_TABLE_EZ) != null){
                    btnSetupImEz.setAlpha(Lime.HALF_ALPHA_VALUE);
                    btnSetupImEz.setTypeface(null, Typeface.ITALIC);
                }else {
                    btnSetupImEz.setAlpha(Lime.NORMAL_ALPHA_VALUE);
                    btnSetupImEz.setTypeface(null, Typeface.BOLD);
                }

                btnSetupImEz.setOnClickListener(v -> {
                    FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                    SetupImLoadDialog dialog = SetupImLoadDialog.newInstance(Lime.DB_TABLE_EZ, handler);
                    dialog.show(ft, "loadimdialog");
                });

                if(check.get(Lime.DB_TABLE_ARRAY) != null){
                    btnSetupImArray.setAlpha(Lime.HALF_ALPHA_VALUE);
                    btnSetupImArray.setTypeface(null, Typeface.ITALIC);
                }else {
                    btnSetupImArray.setAlpha(Lime.NORMAL_ALPHA_VALUE);
                    btnSetupImArray.setTypeface(null, Typeface.BOLD);
                }

                btnSetupImArray.setOnClickListener(v -> {
                    FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                    SetupImLoadDialog dialog = SetupImLoadDialog.newInstance(Lime.DB_TABLE_ARRAY, handler);
                    dialog.show(ft, "loadimdialog");
                });

                if(check.get(Lime.DB_TABLE_ARRAY10) != null){
                    btnSetupImArray10.setAlpha(Lime.HALF_ALPHA_VALUE);
                    btnSetupImArray10.setTypeface(null, Typeface.ITALIC);
                }else {
                    btnSetupImArray10.setAlpha(Lime.NORMAL_ALPHA_VALUE);
                    btnSetupImArray10.setTypeface(null, Typeface.BOLD);
                }

                btnSetupImArray10.setOnClickListener(v -> {
                    FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                    SetupImLoadDialog dialog = SetupImLoadDialog.newInstance(Lime.DB_TABLE_ARRAY10, handler);
                    dialog.show(ft, "loadimdialog");
                });


                if(check.get(Lime.DB_TABLE_HS) != null){
                    btnSetupImHs.setAlpha(Lime.HALF_ALPHA_VALUE);
                    btnSetupImHs.setTypeface(null, Typeface.ITALIC);
                }else {
                    btnSetupImHs.setAlpha(Lime.NORMAL_ALPHA_VALUE);
                    btnSetupImHs.setTypeface(null, Typeface.BOLD);
                }

                btnSetupImHs.setOnClickListener(v -> {
                    FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                    SetupImLoadDialog dialog = SetupImLoadDialog.newInstance(Lime.DB_TABLE_HS, handler);
                    dialog.show(ft, "loadimdialog");
                });

                if(check.get(Lime.DB_TABLE_WB) != null){
                    btnSetupImWb.setAlpha(Lime.HALF_ALPHA_VALUE);
                    btnSetupImWb.setTypeface(null, Typeface.ITALIC);
                }else {
                    btnSetupImWb.setAlpha(Lime.NORMAL_ALPHA_VALUE);
                    btnSetupImWb.setTypeface(null, Typeface.BOLD);
                }

                btnSetupImWb.setOnClickListener(v -> {
                    FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                    SetupImLoadDialog dialog = SetupImLoadDialog.newInstance(Lime.DB_TABLE_WB, handler);
                    dialog.show(ft, "loadimdialog");
                });

                if(check.get(Lime.DB_TABLE_PINYIN) != null){
                    btnSetupImPinyin.setAlpha(Lime.HALF_ALPHA_VALUE);
                    btnSetupImPinyin.setTypeface(null, Typeface.ITALIC);
                }else {
                    btnSetupImPinyin.setAlpha(Lime.NORMAL_ALPHA_VALUE);
                    btnSetupImPinyin.setTypeface(null, Typeface.BOLD);
                }

                btnSetupImPinyin.setOnClickListener(v -> {
                    FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                    SetupImLoadDialog dialog = SetupImLoadDialog.newInstance(Lime.DB_TABLE_PINYIN, handler);
                    dialog.show(ft, "loadimdialog");
                });




            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
    }

    public void showAlertDialog(final String action, final String type, String message){

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage(message);
        builder.setCancelable(false);
        builder.setPositiveButton(getResources().getString(R.string.dialog_confirm),
                (dialog, id) -> {
                    if (action != null) {
                        if (action.equalsIgnoreCase(Lime.BACKUP)) {

                            if (type.equalsIgnoreCase(Lime.LOCAL)) {
                                backupLocalDrive();
                            }

                        } else if (type.equalsIgnoreCase(Lime.LOCAL) && action.equalsIgnoreCase(Lime.RESTORE)) {
                                restoreLocalDrive();
                        }
                    }
                });
        builder.setNegativeButton(getResources().getString(R.string.dialog_cancel),
                (dialog, id) -> {
                });

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
                DBServer.backupDatabase(uri);
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
                // Create a temp file
                File tempFile = File.createTempFile("restore_backup", ".zip", activity.getCacheDir());
                tempFile.deleteOnExit();

                // Copy from URI to temp file
                InputStream inputStream = activity.getContentResolver().openInputStream(uri);
                FileOutputStream outputStream = new FileOutputStream(tempFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.close();
                inputStream.close();

                // Perform restore
                DBServer.restoreDatabase(tempFile.getAbsolutePath());

            } catch (Exception e) {
                e.printStackTrace();
                activity.runOnUiThread(() -> showToastMessage(activity.getString(R.string.l3_initial_restore_error), Toast.LENGTH_LONG));
            } finally {
                if (handler != null) {
                    handler.cancelProgress();
                }
            }
        }).start();
    }




    public void backupLocalDrive(){
        // Launch file picker for saving backup
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, "limeBackup.zip");

        // Replace startActivityForResult with the new launcher
        backupLauncher.launch(intent);
    }

    public void restoreLocalDrive(){
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/zip");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Replace startActivityForResult with the new launcher
        restoreLauncher.launch(Intent.createChooser(intent, "Select Backup"));
    }

//    public void initialThreadTask(String action, String type) {
//
//        // Default Setting
//        mLIMEPref.setParameter("dbtarget", Lime.DEVICE);
//
//        if (action.equals(Lime.BACKUP)) {
//            // The local backup is now handled by onActivityResult, not a direct runnable here.
//            if(!type.equals(Lime.LOCAL)) {
//                if (backupthread != null && backupthread.isAlive()) {
//                    handler.removeCallbacks(backupthread);
//                }
//                backupthread = new Thread(new SetupImBackupRunnable(this, handler, type));
//                backupthread.start();
//            }
//        }else if(action.equals(Lime.RESTORE)){
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
            e.printStackTrace();
        }
    }

    public void finishProgress() {

        cancelProgress();

    }
}
