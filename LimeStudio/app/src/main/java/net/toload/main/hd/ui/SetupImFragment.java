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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AccessTokenPair;
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
    private DropboxAPI<AndroidAuthSession> mDBApi;

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

    public void showProgress(){
        if(!progress.isShowing()){
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        handler = new SetupImHandler(this);

        activity = getActivity();
        progress = new ProgressDialog(activity);
        progress.setMax(100);

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

/*
                if(DEBUG)
                    Log.i(TAG, "Dropbox link, mDropboxLoggedIn = " + mDropboxLoggedIn);

                if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){

                    File srcFile = new File(Lime.DATABASE_FOLDER + Lime.separator + Lime.DATABASE_NAME);
                    File srcFile2 = new File(Lime.DATABASE_DECOMPRESS_FOLDER_SDCARD + Lime.separator + Lime.DATABASE_NAME);

                    if((srcFile2.exists() && srcFile2.length() > 1024) || (srcFile.exists() && srcFile.length() > 1024)){
                        AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
                        builder.setMessage(getText(R.string.l3_initial_dropbox_backup_confirm));
                        builder.setCancelable(false);
                        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {

                                initialDropBoxSession();

                                backupDropbox();

                                    *//*mDBApi.getSession().startOAuth2Authentication(activity);

                                    if (!mDropboxLoggedIn) {
                                        // Start the remote authentication
                                       // mDropboxApi.getSession().startAuthentication(activity);
                                        pendingDropboxBackup = true; //do database backup after on Resume();
                                    }else{
                                        //
                                        backupDatabaseDropbox();
                                    }*//*
                            }
                        });
                        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                            }
                        });

                        AlertDialog alert = builder.create();
                        alert.show();
                    }else{
                        Toast.makeText(v.getContext(), getText(R.string.l3_initial_cloud_backup_error), Toast.LENGTH_SHORT).show();
                    }
                }else{
                    Toast.makeText(v.getContext(), getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
                }*/

       /* btnSetupImRestoreDropbox = (Button) rootView.findViewById(R.id.btnSetupImRestoreDropbox);
        btnSetupImRestoreDropbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(DEBUG)
                    Log.i(TAG, "Dropbox link, mDropboxLoggedIn = " +mDropboxLoggedIn);

                if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){

                    AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
                    builder.setMessage(getText(R.string.l3_initial_dropbox_restore_confirm));
                    builder.setCancelable(false);
                    builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {

                            if (!mDropboxLoggedIn) {
                                // Start the remote authentication
                                // mDropboxApi.getSession().startAuthentication(activity);
                                pendingDropboxRestore = true; //do database backup after on Resume();
                            }else{
                                //
                                restoreDatabaseDropbox();
                            }

                                *//*btnStoreSdcard.setEnabled(false);
                                btnStoreDevice.setEnabled(false);*//*
                        }
                    });
                    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                        }
                    });

                    AlertDialog alert = builder.create();
                    alert.show();

                    // Reset for SearchSrv
                    mLIMEPref.setResetCacheFlag(true);

                }else{
                    Toast.makeText(v.getContext(), getText(R.string.l3_tab_initial_error), Toast.LENGTH_SHORT).show();
                }
            }
        });*/

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
                                    restoreLocalDrive();
                                if(type.equalsIgnoreCase(Lime.LOCAL)){
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
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        credential.setSelectedAccountName(accountName);
                        backupGoogleDrive(credential);
                    }
                }
                break;
            case REQUEST_ACCOUNT_PICKER_RESTORE:
                if (resultCode == activity.RESULT_OK && data != null && data.getExtras() != null) {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        credential.setSelectedAccountName(accountName);
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
                credential.setSelectedAccountName(accountname);
                backupGoogleDrive(credential);
            }
        }else if(type != null && type.equals(Lime.RESTORE)){
            if(accountname == null){
                this.startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER_RESTORE);
            }else{
                credential.setSelectedAccountName(accountname);
                restoreGoogleDrive(credential);
            }
        }
    }

    public void requestDropboxDrive(String type){
        AndroidAuthSession session = buildSession();
        mDBApi = new DropboxAPI<>(session);

        if(type != null && type.equals(Lime.BACKUP)){
            backupDropboxDrive(mDBApi);
        }else if(type != null && type.equals(Lime.RESTORE)){
            restoreDropboxDrive(mDBApi);
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

    private AndroidAuthSession buildSession() {
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
    }

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


    /*private void initialDropBoxSession(){
        if(mDBApi == null || !mDBApi.getSession().isLinked()){
            AppKeyPair appKeys = new AppKeyPair(APP_KEY, APP_SECRET);
            AndroidAuthSession session = new AndroidAuthSession(appKeys);
            mDBApi = new DropboxAPI<AndroidAuthSession>(session);
        }
    }

    public void backupDropbox(){
        if (mDBApi.getSession().authenticationSuccessful()) {
            try {
                // Required to complete auth, sets the access token on the session
                mDBApi.getSession().finishAuthentication();
                String accessToken = mDBApi.getSession().getOAuth2AccessToken();
            } catch (IllegalStateException e) {
                Log.i("DbAuthLog", "Error authenticating", e);
            }
        }else{
            mDBApi.getSession().startOAuth2Authentication(fragment.getActivity());
        }
    }

    public void backupDatabase(){
        BackupRestoreTask task = new BackupRestoreTask(SetupImFragment.this, DBSrv, BackupRestoreTask.BACKUP);
        task.execute("");
    }

    public void restoreDatabase(){
        BackupRestoreTask task = new BackupRestoreTask(SetupImFragment.this, DBSrv, BackupRestoreTask.RESTORE);
        task.execute("");
    }

    private void backupDatabaseDropbox(){
        BackupRestoreTask task = new BackupRestoreTask(SetupImFragment.this, DBSrv, BackupRestoreTask.DROPBOXBACKUP);
        task.execute("");
    }

    public void backupDatabaseGooldDrive() {

        credential = GoogleAccountCredential.usingOAuth2(fragment.getActivity(), Arrays.asList(DriveScopes.DRIVE));
        fragment.startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);

        //tempFile.deleteOnExit();

        //BackupRestoreTask task = new BackupRestoreTask(this, DBSrv, BackupRestoreTask.CLOUDBACKUP);
        //task.execute("");


		*//*  Jeremy '12,12,23 Moved to postExcute of bakcupRestoreTask.  Zip db first (backupdatabase) before backup to google drive now.
		cHandler = new CloudServierHandler(this);
		bTask = new Thread(new CloudBackupServiceRunnable(cHandler, this, tempFile));
		bTask.start();
		*//*
        //showProgressDialog(true);
		*//*BackupRestoreTask task = new BackupRestoreTask(this,this.getApplicationContext(), DBSrv, tempFile, BackupRestoreTask.CLOUDBACKUP);
							  task.execute("");*//*
    }


    public void restoreDatabaseGoogleDrive() {

        credential = GoogleAccountCredential.usingOAuth2(fragment.getActivity(), Arrays.asList(DriveScopes.DRIVE));
        fragment.startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);


        *//*
        File limedir = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator);
        if(!limedir.exists()){
            limedir.mkdirs();
        }

        File tempFile = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + Lime.separator + LIME.DATABASE_CLOUD_TEMP);
        tempFile.deleteOnExit();

        cHandler = new CloudServierHandler(this);
        bTask = new Thread(new CloudRestoreServiceRunnable(cHandler, activity, tempFile));
        bTask.start();

        showProgressDialog(false);*//*
		*//*BackupRestoreTask task = new BackupRestoreTask(this,this.getApplicationContext(), DBSrv, tempFile, BackupRestoreTask.CLOUDRESTORE);
						      task.execute("");*//*
        //initialButton();
    }


    *//* @Override
    void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == this.getActivity().RESULT_OK && data != null && data.getExtras() != null) {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        credential.setSelectedAccountName(accountName);
                        service = getDriveService(credential);
                        // Do Something
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == Activity.RESULT_OK) {
                    // Do somehting backup
                    //saveFileToDrive();
                } else {
                    startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
                }
                break;
            case REQUEST_DROPBOX_BACKUP :
                if (resultCode == Activity.RESULT_OK) {
                    // Do Something backup
                    //saveFileToDrive();
                }
        }
    }*//*

    protected void saveFileToDrive() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // File's binary content
                    java.io.File fileContent = new java.io.File(Lime.DATABASE_FOLDER_EXTERNAL + "cloudtemp.zip");
                    java.io.File saveContent = new java.io.File(Lime.DATABASE_FOLDER_EXTERNAL + "cloudtempsaved.zip");
                    FileContent uploadtarget = new FileContent("application/zip", fileContent);

                    // File's metadata.
                    com.google.api.services.drive.model.File body = new com.google.api.services.drive.model.File();
                    body.setTitle(fileContent.getName());
                    body.setMimeType("application/zip");

                    Drive.Files.List request = null;
                    List<com.google.api.services.drive.model.File> result = new ArrayList<com.google.api.services.drive.model.File>();
                    try {
                        boolean continueload = true;
                        request = service.files().list();
                        String id = null;

                        do {
                            try {
                                FileList files = request.execute();
                                for(com.google.api.services.drive.model.File f: files.getItems()){
                                    if(f.getTitle().equalsIgnoreCase("cloudtemp.zip")){
                                        id = f.getId();
                                        continueload = true;

                                        if(saveContent.exists()){
                                            saveContent.delete();
                                        }

                                        InputStream fi = downloadFile(service, f);
                                        FileOutputStream fo = new FileOutputStream(saveContent);
                                        try {
                                            int bytesRead;
                                            byte[] buffer = new byte[8 * 1024];
                                            while ((bytesRead = fi.read(buffer)) != -1) {
                                                fo.write(buffer, 0, bytesRead);
                                            }
                                        } finally {
                                            fo.close();
                                        }

                                        break;
                                    }
                                }
                            } catch (IOException e) {
                                System.out.println("An error occurred: " + e);
                                request.setPageToken(null);
                            }
                            if(!continueload){
                                break;
                            }
                        } while (request.getPageToken() != null &&
                                request.getPageToken().length() > 0);

                        if(id != null){
                            service.files().delete(id).execute();
                        }
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }

                    com.google.api.services.drive.model.File file = service.files().insert(body, uploadtarget).execute();
                     if (file != null) {
                        //showToast("Photo uploaded: " + file.getTitle());
                        //startCameraIntent();
                         //showToast("Success");
                    }else{
                         //showToast("failed");
                     }

                } catch (UserRecoverableAuthIOException e) {
                    fragment.startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        t.start();
    }

    private static InputStream downloadFile(Drive service, com.google.api.services.drive.model.File file) {
        if (file.getDownloadUrl() != null && file.getDownloadUrl().length() > 0) {
            try {
                HttpResponse resp =
                        service.getRequestFactory().buildGetRequest(new GenericUrl(file.getDownloadUrl()))
                                .execute();
                return resp.getContent();
            } catch (IOException e) {
                // An error occurred.
                e.printStackTrace();
                return null;
            }
        } else {
            // The file doesn't have any content stored on Drive.
            return null;
        }
    }

    private Drive getDriveService(GoogleAccountCredential credential) {
        return new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential)
                .build();
    }

    public void restoreDatabaseDropbox(){
        File limedir = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator);
        if(!limedir.exists()){
            limedir.mkdirs();
        }
        File tempFile = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + Lime.separator + LIME.DATABASE_CLOUD_TEMP);
        tempFile.deleteOnExit();

      *//*  DropboxDBRestore download =
                new DropboxDBRestore(this, activity, mDropboxApi,LIME.DATABASE_BACKUP_NAME , tempFile);*//*
        //download.execute();

        //initialButton();

    }

    public void showProgressDialog(boolean isBackup){
        if(isBackup){
            cloudpd = new ProgressDialog(activity);
            cloudpd.setTitle(fragment.getText(R.string.l3_initial_cloud_backup_database));
            cloudpd.setMessage(fragment.getText(R.string.l3_initial_cloud_backup_start));
            cloudpd.setCancelable(false);
            cloudpd.setOnCancelListener(new DialogInterface.OnCancelListener(){
                @Override
                public void onCancel(DialogInterface dialog) {
                    mLIMEPref.setParameter("cloud_in_process",Boolean.valueOf(false));
                }
            });
            cloudpd.setIndeterminate(false);
            cloudpd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            cloudpd.setCanceledOnTouchOutside(false);
            cloudpd.setMax(100);
            cloudpd.show();
        }else{
            cloudpd = new ProgressDialog(activity);
            cloudpd.setTitle(fragment.getText(R.string.l3_initial_cloud_restore_database));
            cloudpd.setMessage(fragment.getText(R.string.l3_initial_cloud_restore_start));
            cloudpd.setCancelable(true);
            cloudpd.setOnCancelListener(new DialogInterface.OnCancelListener(){
                @Override
                public void onCancel(DialogInterface dialog) {
                    mLIMEPref.setParameter("cloud_in_process",Boolean.valueOf(false));
                }
            });
            cloudpd.setIndeterminate(false);
            cloudpd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            cloudpd.setCanceledOnTouchOutside(false);
            cloudpd.setMax(100);
            cloudpd.show();
        }
    }

    public void updateProgressDialog(int progress){
        if(cloudpd!=null && cloudpd.isShowing()){
            cloudpd.setProgress(progress);
        }
    }

    public void closeProgressDialog(){
        if(cloudpd != null && cloudpd.isShowing()){
            cloudpd.dismiss();
        }
    }

    private void logOut() {
        if(DEBUG)
            Log.i(TAG, "logout Dropbox session, mDropboxLoggedIn = " + mDropboxLoggedIn);
        // Remove credentials from the session
        //mDropboxApi.getSession().unlink();

        // Clear our stored keys
        clearKeys();
        // Change UI state to display logged out version
        mDropboxLoggedIn = false;
    }

  /*  private void checkAppKeySetup() {
        *//**//*//**//*//* Check to make sure that we have a valid app key
        if (APP_KEY.startsWith("CHANGE") ||
                APP_SECRET.startsWith("CHANGE")) {
            showToast("You must apply for an app key and secret from developers.dropbox.com, and add them to the DBRoulette ap before trying it.");
            this.activity.finish();
            return;
        }

        // Check if the app has set up its manifest properly.
        Intent testIntent = new Intent(Intent.ACTION_VIEW);
        String scheme = "db-" + APP_KEY;
        String uri = scheme + "://" + AuthActivity.AUTH_VERSION + "/test";
        testIntent.setData(Uri.parse(uri));
        PackageManager pm = this.activity.getPackageManager();
        if (0 == pm.queryIntentActivities(testIntent, 0).size()) {
            showToast("URL scheme in your app's " +
                    "manifest is not set up correctly. You should have a " +
                    "com.dropbox.client2.android.AuthActivity with the " +
                    "scheme: " + scheme);
            this.activity.finish();
        }*//**//*
    }

    *//**//**
     *
     * @param msg
     * @param length
     *//**//*

    *//**//**
     * Shows keeping the access keys returned from Trusted Authenticator in a local
     * store, rather than storing user name & password, and re-authenticating each
     * time (which is not to be done, ever).
     *
     * @return Array of [access_key, access_secret], or null if none stored
     *//**//*
    private String[] getKeys() {
        SharedPreferences prefs = SetupImFragment.this.activity.getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        String key = prefs.getString(ACCESS_KEY_NAME, null);
        String secret = prefs.getString(ACCESS_SECRET_NAME, null);
        if (key != null && secret != null) {
            String[] ret = new String[2];
            ret[0] = key;
            ret[1] = secret;
            return ret;
        } else {
            return null;
        }
    }

    *//**//**
     * Shows keeping the access keys returned from Trusted Authenticator in a local
     * store, rather than storing user name & password, and re-authenticating each
     * time (which is not to be done, ever).
     *//**//*
    private void storeKeys(String key, String secret) {
        // Save the access key for later
        SharedPreferences prefs = SetupImFragment.this.activity.getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString(ACCESS_KEY_NAME, key);
        edit.putString(ACCESS_SECRET_NAME, secret);
        edit.commit();
    }

    private void clearKeys() {
        SharedPreferences prefs = SetupImFragment.this.activity.getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        SharedPreferences.Editor edit = prefs.edit();
        edit.clear();
        edit.commit();
    }

    public void onResume() {
        fragment.onResume();
    }

   *//**//* private AndroidAuthSession buildSession() {
        if(DEBUG)
            Log.i(TAG, "buildSession()");

        AppKeyPair appKeyPair = new AppKeyPair(APP_KEY, APP_SECRET);
        AndroidAuthSession session;

        String[] stored = getKeys();
        if (stored != null) {
            if(DEBUG)
                Log.i(TAG, "Got stored key, key = " + stored[0] + ", secret = " + stored[1]);
            AccessTokenPair accessToken = new AccessTokenPair(stored[0], stored[1]);
            session = new AndroidAuthSession(appKeyPair, ACCESS_TYPE, accessToken);
        } else {
            if(DEBUG)
                Log.i(TAG, "no stored key. start a new session.");

            session = new AndroidAuthSession(appKeyPair, ACCESS_TYPE);
        }

        return session;
    }*//**//**/


    /*public class BackupRestoreTask extends AsyncTask<String,Integer,Integer> {

        private ProgressDialog pd;
        private DBServer dbsrv = null;
        private Activity activity;
        private SetupImFragment fragment;

        //private File tempfile;
        private int type;
        final public static int CLOUDBACKUP = 1;
        final public static int CLOUDRESTORE = 2;
        final public static int BACKUP = 3;
        final public static int RESTORE = 4;
        final public static int DROPBOXBACKUP = 5;
        final public static int DROPBOXRESTORE = 6;

        BackupRestoreTask(SetupImFragment fragment, DBServer db, int settype){
            dbsrv = db;
            this.fragment = fragment;
            activity = fragment.fragment.getActivity();
            type = settype;
        }

        protected void onPreExecute(){

            pd = new ProgressDialog(activity);
            if(type == BACKUP || type == CLOUDBACKUP || type == DROPBOXBACKUP){
                pd = ProgressDialog.show(activity, activity.getResources().getString(R.string.l3_initial_backup_database),
                        activity.getResources().getString(R.string.l3_initial_backup_start), true);
            }else if(type == RESTORE){
                pd = ProgressDialog.show(activity, activity.getResources().getString(R.string.l3_initial_restore_database),
                        activity.getResources().getString(R.string.l3_initial_restore_start), true);
            }

            mLIMEPref.setParameter("reload_database", true);
            try {
                dbsrv.closeDatabse();
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            mLIMEPref.setParameter("cloud_in_process", Boolean.valueOf(false));
        }*/

   /*     protected void onPostExecute(Integer result){
            pd.cancel();
            if(type == CLOUDBACKUP){
                File sourceFile = new File(Lime.DATABASE_FOLDER_EXTERNAL + Lime.separator + Lime.DATABASE_BACKUP_NAME);
                cHandler = new CloudServierHandler(fragment);
                bTask = new Thread(new CloudBackupServiceRunnable(cHandler, fragment, sourceFile));
                bTask.start();
                showProgressDialog(true);
            }else if(type == DROPBOXBACKUP){
                // Jeremy  '12,12,23 do dropbox backup now.
                File sourceFile = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + Lime.separator + LIME.DATABASE_BACKUP_NAME);
               // DropboxDBBackup upload = new DropboxDBBackup(activity, mDropboxApi, "", sourceFile);
               // upload.execute();
            }else if(type == CLOUDRESTORE || type == RESTORE){
                //activity.initialButton();
                dbsrv.checkPhoneticKeyboardSetting();//Jeremy '12,6,8 check the pheonetic keyboard consistency
                mLIMEPref.setResetCacheFlag(true);  //Jeremy '12,7,8 reset cache.
            }
        }*/

     /*   @Override
        protected Integer doInBackground(String... arg0) {

            if(type == BACKUP || type == CLOUDBACKUP || type == DROPBOXBACKUP){
                try {
                    dbsrv.backupDatabase();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                mLIMEPref.setParameter("cloud_in_process", Boolean.valueOf(false));
            }else if(type == RESTORE){
                try {
                    dbsrv.restoreDatabase();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                mLIMEPref.setParameter("cloud_in_process", Boolean.valueOf(false));
            }

            boolean inProcess = true;
            do{
                inProcess = mLIMEPref.getParameterBoolean("cloud_in_process");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }while(inProcess);
            pd.setProgress(100);
            return 1;
        }
    }*/


}