package net.toload.main.hd.ui;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AppKeyPair;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.services.drive.DriveScopes;

import net.toload.main.hd.Lime;
import net.toload.main.hd.MainActivity;
import net.toload.main.hd.R;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.limesettings.DBServer;

import java.util.Arrays;

/**
 * Fragment used for managing interactions for and presentation of a navigation drawer.
 * See the <a href="https://developer.android.com/design/patterns/navigation-drawer.html#Interaction">
 * design guidelines</a> for a complete explanation of the behaviors implemented here.
 */
/**
 * A placeholder fragment containing a simple view.
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

    private ProgressDialog progress;

    // Google API
    private GoogleAccountCredential credential;
    static final int REQUEST_ACCOUNT_PICKER_BACKUP = 1;
    static final int REQUEST_ACCOUNT_PICKER_RESTORE = 2;
    static final int REQUEST_AUTHORIZATION_BACKUP = 3;
    static final int REQUEST_AUTHORIZATION_RESTORE = 4;

    static final int REQUEST_LOCAL_BACKUP = 3;
    static final int REQUEST_LOCAL_RESTORE = 4;
    static final int REQUEST_GOOGLE_BACKUP = 5;
    static final int REQUEST_GOOGLE_RESTORE = 6;
    static final int REQUEST_DROPBOX_BACKUP = 7;
    static final int REQUEST_DROPBOX_RESTORE = 8;

    // Dropbox
    DropboxAPI<AndroidAuthSession> mdbapi;
    String dropboxAccessToken;

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
    Button btnSetupImPinyin;

    // Backup Restore
    Button btnSetupImBackupLocal;
    Button btnSetupImRestoreLocal;
    Button btnSetupImBackupGoogle;
    Button btnSetupImRestoreGoogle;
    Button btnSetupImBackupDropbox;
    Button btnSetupImRestoreDropbox;

    private ConnectivityManager connManager;

    private DBServer DBSrv = null;
    private Activity activity;
    private LIMEPreferenceManager mLIMEPref;

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

        boolean dropboxrequest = mLIMEPref.getParameterBoolean(Lime.DROPBOX_REQUEST_FLAG, false);

        if (dropboxrequest && mdbapi != null && mdbapi.getSession().authenticationSuccessful()) {
            try {
                // Required to complete auth, sets the access token on the session
                mdbapi.getSession().finishAuthentication();
                dropboxAccessToken = mdbapi.getSession().getOAuth2AccessToken();

                mLIMEPref.setParameter(Lime.DROPBOX_ACCESS_TOKEN, dropboxAccessToken);
                String type = mLIMEPref.getParameterString(Lime.DROPBOX_TYPE, null);

                if(type != null && type.equals(Lime.BACKUP)){
                    backupDropboxDrive(mdbapi);
                }else if(type != null && type.equals(Lime.RESTORE)){
                    restoreDropboxDrive(mdbapi);
                }

            } catch (IllegalStateException e) {
                Log.i("DbAuthLog", "Error authenticating", e);
            }
        }

        // Reset DropBox Request
        mLIMEPref.setParameter(Lime.DROPBOX_REQUEST_FLAG, false);

    }

    public void showProgress() {
        if (!progress.isShowing()) {
            progress.show();
        }
    }

    public void cancelProgress(){
        if(progress.isShowing()){
            progress.dismiss();
        }
    }
    public void updateProgress(int value){
        if(!progress.isShowing()){
           progress.setProgress(value);
        }
    }
    public void updateProgress(String value){
        if(!progress.isShowing()){
            progress.setMessage(value);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        handler = new SetupImHandler(this);

        activity = getActivity();
        progress = new ProgressDialog(activity);
        progress.setMax(100);
        progress.setCancelable(false);

        DBSrv = new DBServer(activity);
        mLIMEPref = new LIMEPreferenceManager(activity);

        connManager = (ConnectivityManager) SetupImFragment.this.activity.getSystemService(
                SetupImFragment.this.activity.CONNECTIVITY_SERVICE);

        View rootView = inflater.inflate(R.layout.fragment_setup_im, container, false);

        btnSetupImImportStandard = (Button) rootView.findViewById(R.id.btnSetupImImportStandard);
        btnSetupImImportRelated = (Button) rootView.findViewById(R.id.btnSetupImImportRelated);
        btnSetupImPhonetic = (Button) rootView.findViewById(R.id.btnSetupImPhonetic);
        btnSetupImCj = (Button) rootView.findViewById(R.id.btnSetupImCj);
        btnSetupImCj5 = (Button) rootView.findViewById(R.id.btnSetupImCj5);
        btnSetupImScj = (Button) rootView.findViewById(R.id.btnSetupImScj);
        btnSetupImEcj = (Button) rootView.findViewById(R.id.btnSetupImEcj);
        btnSetupImDayi = (Button) rootView.findViewById(R.id.btnSetupImDayi);
        btnSetupImEz = (Button) rootView.findViewById(R.id.btnSetupImEz);
        btnSetupImArray = (Button) rootView.findViewById(R.id.btnSetupImArray);
        btnSetupImArray10 = (Button) rootView.findViewById(R.id.btnSetupImArray10);
        btnSetupImHs = (Button) rootView.findViewById(R.id.btnSetupImHs);
        btnSetupImPinyin = (Button) rootView.findViewById(R.id.btnSetupImPinyin);


        // Backup and Restore Setting
        btnSetupImBackupLocal = (Button) rootView.findViewById(R.id.btnSetupImBackupLocal);
        btnSetupImBackupLocal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAlertDialog(Lime.BACKUP, Lime.LOCAL, getResources().getString(R.string.l3_initial_backup_confirm));
            }
        });

        btnSetupImRestoreLocal = (Button) rootView.findViewById(R.id.btnSetupImRestoreLocal);
        btnSetupImRestoreLocal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAlertDialog(Lime.RESTORE, Lime.LOCAL, getResources().getString(R.string.l3_initial_restore_confirm));
            }
        });

        btnSetupImBackupGoogle = (Button) rootView.findViewById(R.id.btnSetupImBackupGoogle);
        btnSetupImBackupGoogle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()) {
                    showAlertDialog(Lime.BACKUP, Lime.GOOGLE, getResources().getString(R.string.l3_initial_cloud_backup_confirm));
                }
            }
        });


        btnSetupImRestoreGoogle = (Button) rootView.findViewById(R.id.btnSetupImRestoreGoogle);
        btnSetupImRestoreGoogle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()) {
                    showAlertDialog(Lime.RESTORE, Lime.GOOGLE, getResources().getString(R.string.l3_initial_cloud_restore_confirm));
                }
            }
        });

        btnSetupImBackupDropbox = (Button) rootView.findViewById(R.id.btnSetupImBackupDropbox);
        btnSetupImBackupDropbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()) {
                    showAlertDialog(Lime.BACKUP, Lime.DROPBOX, getResources().getString(R.string.l3_initial_dropbox_backup_confirm));
                }
            }
        });

        btnSetupImRestoreDropbox = (Button) rootView.findViewById(R.id.btnSetupImRestoreDropbox);
        btnSetupImRestoreDropbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()) {
                    showAlertDialog(Lime.RESTORE, Lime.DROPBOX, getResources().getString(R.string.l3_initial_dropbox_restore_confirm));
                }
            }
        });

        return rootView;
    }

    public void showAlertDialog(final String action, final String type, String message){
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage(message);
        builder.setCancelable(false);
        builder.setPositiveButton(getResources().getString(R.string.dialog_confirm),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if(action != null){
                            if(action.equalsIgnoreCase(Lime.BACKUP)) {

                                if(type.equalsIgnoreCase(Lime.LOCAL)){
                                    backupLocalDrive();
                                }else if(type.equalsIgnoreCase(Lime.GOOGLE)){
                                    requestGoogleDrive(Lime.BACKUP);
                                }else if(type.equalsIgnoreCase(Lime.DROPBOX)){
                                    requestDropboxDrive(Lime.BACKUP);
                                }

                            }else if(action.equalsIgnoreCase(Lime.RESTORE)){

                                if(type.equalsIgnoreCase(Lime.LOCAL)){
                                    restoreLocalDrive();
                                }else if(type.equalsIgnoreCase(Lime.GOOGLE)){
                                    requestGoogleDrive(Lime.RESTORE);
                                }else if(type.equalsIgnoreCase(Lime.DROPBOX)){
                                    requestDropboxDrive(Lime.RESTORE);
                                }

                            }
                        }
                    }
                });
        builder.setNegativeButton(getResources().getString(R.string.dialog_cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });

        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        ((MainActivity) activity).onSectionAttached(
                getArguments().getInt(ARG_SECTION_NUMBER));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){

        switch (requestCode) {
            case REQUEST_ACCOUNT_PICKER_BACKUP:
                if (resultCode == activity.RESULT_OK && data != null && data.getExtras() != null) {
                    String accountname = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountname != null) {
                        mLIMEPref.setParameter(Lime.GOOGLE_ACCOUNT_NAME, accountname);
                        credential.setSelectedAccountName(accountname);
                        backupGoogleDrive(credential);
                    }
                }
                break;
            case REQUEST_ACCOUNT_PICKER_RESTORE:
                if (resultCode == activity.RESULT_OK && data != null && data.getExtras() != null) {
                    String accountname = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountname != null) {
                        mLIMEPref.setParameter(Lime.GOOGLE_ACCOUNT_NAME, accountname);
                        credential.setSelectedAccountName(accountname);
                        restoreGoogleDrive(credential);
                    }
                }
                break;
        }
    }

    public void requestGoogleDrive(String type){

        credential = GoogleAccountCredential.usingOAuth2(activity, Arrays.asList(DriveScopes.DRIVE));
        String accountname = mLIMEPref.getParameterString(Lime.GOOGLE_ACCOUNT_NAME, null);

        if(type != null && type.equals(Lime.BACKUP)){
            if(accountname == null){
                this.startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER_BACKUP);
            }else{
                mLIMEPref.setParameter(Lime.GOOGLE_ACCOUNT_NAME, accountname);
                credential.setSelectedAccountName(accountname);
                backupGoogleDrive(credential);
            }
        }else if(type != null && type.equals(Lime.RESTORE)){
            if(accountname == null){
                this.startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER_RESTORE);
            }else{
                mLIMEPref.setParameter(Lime.GOOGLE_ACCOUNT_NAME, accountname);
                credential.setSelectedAccountName(accountname);
                restoreGoogleDrive(credential);
            }
        }
    }

    public void requestDropboxDrive(String type){

        mLIMEPref.setParameter(Lime.DROPBOX_TYPE, type);
        mLIMEPref.setParameter(Lime.DROPBOX_REQUEST_FLAG, true);

        AppKeyPair appKeys = new AppKeyPair(Lime.DROPBOX_APP_KEY, Lime.DROPBOX_APP_SECRET);
        AndroidAuthSession session = new AndroidAuthSession(appKeys);
        mdbapi = new DropboxAPI<AndroidAuthSession>(session);

        dropboxAccessToken = mLIMEPref.getParameterString(Lime.DROPBOX_ACCESS_TOKEN, null);
        if(dropboxAccessToken == null){
            mdbapi.getSession().startOAuth2Authentication(this.getActivity().getApplicationContext());
        }else{

            mdbapi = new DropboxAPI<AndroidAuthSession>(new AndroidAuthSession(appKeys, dropboxAccessToken));

            if(mdbapi.getSession().isLinked()){
                if(type != null && type.equals(Lime.BACKUP)){
                    backupDropboxDrive(mdbapi);
                }else if(type != null && type.equals(Lime.RESTORE)){
                    restoreDropboxDrive(mdbapi);
                }
            }else{
                mdbapi.getSession().startOAuth2Authentication(this.getActivity().getApplicationContext());
            }
        }
    }

    public void backupDropboxDrive(DropboxAPI mdbapi){
        initialThreadTask(Lime.BACKUP, Lime.DROPBOX, null, mdbapi);
    }

    public void restoreDropboxDrive(DropboxAPI mdbapi){
        initialThreadTask(Lime.RESTORE, Lime.DROPBOX, null, mdbapi);
    }

    public void backupGoogleDrive(GoogleAccountCredential credential){
        initialThreadTask(Lime.BACKUP, Lime.GOOGLE, credential, null);
    }

    public void restoreGoogleDrive(GoogleAccountCredential credential){
        initialThreadTask(Lime.RESTORE, Lime.GOOGLE, credential, null);
    }

    public void backupLocalDrive(){
        initialThreadTask(Lime.BACKUP, Lime.LOCAL, null, null);
    }

    public void restoreLocalDrive(){
        initialThreadTask(Lime.RESTORE, Lime.LOCAL, null, null);
    }

    public void initialThreadTask(String action, String type, GoogleAccountCredential credential, DropboxAPI mdbapi) {

        // Default Setting
        mLIMEPref.setParameter("dbtarget", Lime.DEVICE);

        if (action.equals(Lime.BACKUP)) {
            if(backupthread != null && backupthread.isAlive()){
                handler.removeCallbacks(backupthread);
            }
            backupthread = new Thread(new SetupImBackupRunnable(this, handler, type, credential, mdbapi));
            backupthread.start();
        }else if(action.equals(Lime.RESTORE)){
            if(restorethread != null && restorethread.isAlive()){
                handler.removeCallbacks(restorethread);
            }
            restorethread = new Thread(new SetupImRestoreRunnable(this, handler, type, credential, mdbapi));
            restorethread.start();
        }
    }

    public void showToastMessage(String msg, int length) {
        Toast toast = Toast.makeText(activity, msg, length);
        toast.show();
    }

    /*private AndroidAuthSession buildSession() {

        AppKeyPair appKeyPair = new AppKeyPair(Lime.APP_KEY, Lime.APP_SECRET);
        AndroidAuthSession session;

        String[] stored = getDropboxKeys();
        if (stored != null) {
            AccessTokenPair accessToken = new AccessTokenPair(stored[0], stored[1]);
            session = new AndroidAuthSession(appKeyPair, accessToken);
        } else {
            session = new AndroidAuthSession(appKeyPair);
        }

        return session;
    }*/

    private String[] getDropboxKeys() {
        SharedPreferences prefs = activity.getSharedPreferences(Lime.ACCOUNT_PREFS_NAME, 0);
        String key = prefs.getString(Lime.ACCESS_KEY_NAME, null);
        String secret = prefs.getString(Lime.ACCESS_SECRET_NAME, null);
        if (key != null && secret != null) {
            String[] ret = new String[2];
            ret[0] = key;
            ret[1] = secret;
            return ret;
        } else {
            return null;
        }
    }

    private void storeKeys(String key, String secret) {
        // Save the access key for later
        SharedPreferences prefs = activity.getSharedPreferences(Lime.ACCOUNT_PREFS_NAME, 0);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString(Lime.ACCESS_KEY_NAME, key);
        edit.putString(Lime.ACCESS_SECRET_NAME, secret);
        edit.commit();
    }



}