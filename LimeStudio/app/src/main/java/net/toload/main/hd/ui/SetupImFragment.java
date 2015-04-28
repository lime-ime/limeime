package net.toload.main.hd.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.android.AuthActivity;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.client2.session.Session;

import net.toload.main.hd.Lime;
import net.toload.main.hd.MainActivity;
import net.toload.main.hd.R;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.handler.CloudBackupServiceRunnable;
import net.toload.main.hd.handler.CloudRestoreServiceRunnable;
import net.toload.main.hd.handler.CloudServierHandler;
import net.toload.main.hd.handler.DropboxDBBackup;
import net.toload.main.hd.handler.DropboxDBRestore;
import net.toload.main.hd.limesettings.DBServer;

import java.io.File;

/**
 * Fragment used for managing interactions for and presentation of a navigation drawer.
 * See the <a href="https://developer.android.com/design/patterns/navigation-drawer.html#Interaction">
 * design guidelines</a> for a complete explanation of the behaviors implemented here.
 */
/**
 * A placeholder fragment containing a simple view.
 */
public class SetupImFragment extends Fragment {

    // Parameters from previous version
    private final boolean DEBUG = false;

    private final String TAG = "SetupImFragment";

    // Dropbox API by Jeremy '12,12,22
    // Note that this is a really insecure way to do this, and you shouldn't
    // ship code which contains your key & secret in such an obvious way.
    // Obfuscation is good.
    final static private String APP_KEY = "keuuzhfc6efjf6t";
    final static private String APP_SECRET = "4y8fy4rqk8rofd8";
    // If you'd like to change the access type to the full Dropbox instead of
    // an app folder, change this value.
    final static private Session.AccessType ACCESS_TYPE = Session.AccessType.APP_FOLDER;
    // You don't need to change these, leave them alone.
    final static private String ACCOUNT_PREFS_NAME = "prefs";
    final static private String ACCESS_KEY_NAME = "ACCESS_KEY";
    final static private String ACCESS_SECRET_NAME = "ACCESS_SECRET";
    DropboxAPI<AndroidAuthSession> mDropboxApi;

    private boolean mDropboxLoggedIn;
    private boolean pendingDropboxBackup = false;
    private boolean pendingDropboxRestore = false;

    private ProgressDialog cloudpd;
    private CloudServierHandler cHandler;
    private Thread bTask;
    private Thread rTask;

    // Custom Import
    private Button btnSetupImImportStandard;
    private Button btnSetupImImportRelated;

    // Default IME
    private Button btnSetupImPhonetic;
    private Button btnSetupImCj;
    private Button btnSetupImCj5;
    private Button btnSetupImScj;
    private Button btnSetupImEcj;
    private Button btnSetupImDayi;
    private Button btnSetupImEz;
    private Button btnSetupImArray;
    private Button btnSetupImArray10;
    private Button btnSetupImHs;
    private Button btnSetupImPinyin;

    // Backup Restore
    private Button btnSetupImBackupLocal;
    private Button btnSetupImRestoreLocal;
    private Button btnSetupImBackupGoogle;
    private Button btnSetupImRestoreGoogle;
    private Button btnSetupImBackupDropbox;
    private Button btnSetupImRestoreDropbox;

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
        SetupImFragment fragment = new SetupImFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    public SetupImFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {


        mLIMEPref = new LIMEPreferenceManager(getActivity());
        DBSrv = new DBServer(getActivity());
        activity = getActivity();
        connManager = (ConnectivityManager) this.activity.getSystemService(this.activity.CONNECTIVITY_SERVICE);

        //Dropbox initialization  '12,12,23 Jermey
        // We create a new AuthSession so that we can use the Dropbox API.
        AndroidAuthSession session = buildSession();
        mDropboxApi = new DropboxAPI<AndroidAuthSession>(session);
        checkAppKeySetup();
        mDropboxLoggedIn = mDropboxApi.getSession().isLinked();


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

        btnSetupImBackupLocal = (Button) rootView.findViewById(R.id.btnSetupImBackupLocal);
        btnSetupImBackupLocal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                File srcFile = new File(Lime.DATABASE_FOLDER + File.separator + Lime.DATABASE_NAME);
                File srcFile2 = new File(Lime.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + Lime.DATABASE_NAME);

                if((srcFile2.exists() && srcFile2.length() > 1024) || (srcFile.exists() && srcFile.length() > 1024)){
                    AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
                    builder.setMessage(getText(R.string.l3_initial_backup_confirm));
                    builder.setCancelable(false);
                    builder.setPositiveButton(v.getResources().getString(R.string.dialog_confirm),
                            new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            backupDatabase();
                        }
                    });
                    builder.setNegativeButton(v.getResources().getString(R.string.dialog_cancel),
                            new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                        }
                    });

                    AlertDialog alert = builder.create();
                    alert.show();
                }else{
                    Toast.makeText(v.getContext(), getText(R.string.l3_initial_backup_error), Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnSetupImRestoreLocal = (Button) rootView.findViewById(R.id.btnSetupImRestoreLocal);
        btnSetupImRestoreLocal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                File srcFile = new File(Lime.DATABASE_FOLDER + File.separator + Lime.DATABASE_NAME);
                File srcFile2 = new File(Lime.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + Lime.DATABASE_NAME);
                if((srcFile2.exists() && srcFile2.length() > 1024) || (srcFile.exists() && srcFile.length() > 1024)){

                    AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
                    builder.setMessage(getText(R.string.l3_initial_restore_confirm));
                    builder.setCancelable(false);
                    builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {

                            String dbtarget = mLIMEPref.getParameterString("dbtarget");
                            /*
                            if(dbtarget.equals("device")){
                                btnStoreSdcard.setText("");
                            }else if(dbtarget.equals("sdcard")){
                                btnStoreDevice.setText("");
                            }
                            btnStoreSdcard.setEnabled(false);
                            btnStoreDevice.setEnabled(false);*/

                            restoreDatabase();
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
                    Toast.makeText(v.getContext(), getText(R.string.l3_initial_restore_error), Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnSetupImBackupGoogle = (Button) rootView.findViewById(R.id.btnSetupImBackupGoogle);
        btnSetupImBackupGoogle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){


                    File srcFile = new File(Lime.DATABASE_FOLDER + File.separator + Lime.DATABASE_NAME);
                    File srcFile2 = new File(Lime.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + Lime.DATABASE_NAME);

                    if((srcFile2.exists() && srcFile2.length() > 1024) || (srcFile.exists() && srcFile.length() > 1024)){
                        AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
                        builder.setMessage(getText(R.string.l3_initial_cloud_backup_confirm));
                        builder.setCancelable(false);
                        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                backupDatabaseGooldDrive();
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
                }
            }
        });


        btnSetupImRestoreGoogle = (Button) rootView.findViewById(R.id.btnSetupImRestoreGoogle);
        btnSetupImRestoreGoogle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){

                    AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
                    builder.setMessage(getText(R.string.l3_initial_cloud_restore_confirm));
                    builder.setCancelable(false);
                    builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            restoreDatabaseGoogleDrive();
                            /*btnStoreSdcard.setEnabled(false);
                            btnStoreDevice.setEnabled(false);*/
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
        });

        btnSetupImBackupDropbox = (Button) rootView.findViewById(R.id.btnSetupImBackupDropbox);
        btnSetupImBackupDropbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(DEBUG)
                    Log.i(TAG, "Dropbox link, mDropboxLoggedIn = " + mDropboxLoggedIn);

                if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){

                    File srcFile = new File(Lime.DATABASE_FOLDER + File.separator + Lime.DATABASE_NAME);
                    File srcFile2 = new File(Lime.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + Lime.DATABASE_NAME);

                    if((srcFile2.exists() && srcFile2.length() > 1024) || (srcFile.exists() && srcFile.length() > 1024)){
                        AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
                        builder.setMessage(getText(R.string.l3_initial_dropbox_backup_confirm));
                        builder.setCancelable(false);
                        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {

                                if (!mDropboxLoggedIn) {
                                    // Start the remote authentication
                                    mDropboxApi.getSession().startAuthentication(activity);
                                    pendingDropboxBackup = true; //do database backup after on Resume();
                                }else{
                                    //
                                    backupDatabaseDropbox();
                                }
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
                }
            }
        });

        btnSetupImRestoreDropbox = (Button) rootView.findViewById(R.id.btnSetupImRestoreDropbox);
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
                                mDropboxApi.getSession().startAuthentication(activity);
                                pendingDropboxRestore = true; //do database backup after on Resume();
                            }else{
                                //
                                restoreDatabaseDropbox();
                            }

                            /*btnStoreSdcard.setEnabled(false);
                            btnStoreDevice.setEnabled(false);*/
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
        });

        return rootView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        ((MainActivity) activity).onSectionAttached(
                getArguments().getInt(ARG_SECTION_NUMBER));
    }

    public void backupDatabase(){
        BackupRestoreTask task = new BackupRestoreTask(this, DBSrv, BackupRestoreTask.BACKUP);
        task.execute("");
    }

    public void restoreDatabase(){
        BackupRestoreTask task = new BackupRestoreTask(this, DBSrv, BackupRestoreTask.RESTORE);
        task.execute("");
    }

    private void backupDatabaseDropbox(){
        BackupRestoreTask task = new BackupRestoreTask(this, DBSrv, BackupRestoreTask.DROPBOXBACKUP);
        task.execute("");
    }

    public void backupDatabaseGooldDrive() {

        //tempFile.deleteOnExit();

        BackupRestoreTask task = new BackupRestoreTask(this, DBSrv, BackupRestoreTask.CLOUDBACKUP);
        task.execute("");


		/*  Jeremy '12,12,23 Moved to postExcute of bakcupRestoreTask.  Zip db first (backupdatabase) before backup to google drive now.
		cHandler = new CloudServierHandler(this);
		bTask = new Thread(new CloudBackupServiceRunnable(cHandler, this, tempFile));
		bTask.start();
		*/
        //showProgressDialog(true);
		/*BackupRestoreTask task = new BackupRestoreTask(this,this.getApplicationContext(), DBSrv, tempFile, BackupRestoreTask.CLOUDBACKUP);
							  task.execute("");*/
    }

    public void restoreDatabaseGoogleDrive() {
        File limedir = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator);
        if(!limedir.exists()){
            limedir.mkdirs();
        }

        File tempFile = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator + LIME.DATABASE_CLOUD_TEMP);
        tempFile.deleteOnExit();

        cHandler = new CloudServierHandler(this);
        bTask = new Thread(new CloudRestoreServiceRunnable(cHandler, activity, tempFile));
        bTask.start();

        showProgressDialog(false);
		/*BackupRestoreTask task = new BackupRestoreTask(this,this.getApplicationContext(), DBSrv, tempFile, BackupRestoreTask.CLOUDRESTORE);
						      task.execute("");*/
        //initialButton();
    }

    public void restoreDatabaseDropbox(){
        File limedir = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator);
        if(!limedir.exists()){
            limedir.mkdirs();
        }
        File tempFile = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator + LIME.DATABASE_CLOUD_TEMP);
        tempFile.deleteOnExit();

        DropboxDBRestore download =
                new DropboxDBRestore(this, activity, mDropboxApi,LIME.DATABASE_BACKUP_NAME , tempFile);
        download.execute();

        //initialButton();

    }

    public void showProgressDialog(boolean isBackup){
        if(isBackup){
            cloudpd = new ProgressDialog(activity);
            cloudpd.setTitle(this.getText(R.string.l3_initial_cloud_backup_database));
            cloudpd.setMessage(this.getText(R.string.l3_initial_cloud_backup_start));
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
            cloudpd.setTitle(this.getText(R.string.l3_initial_cloud_restore_database));
            cloudpd.setMessage(this.getText(R.string.l3_initial_cloud_restore_start));
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
        mDropboxApi.getSession().unlink();

        // Clear our stored keys
        clearKeys();
        // Change UI state to display logged out version
        mDropboxLoggedIn = false;
    }

    private void checkAppKeySetup() {
        // Check to make sure that we have a valid app key
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
        }
    }

    private void showToast(String msg) {
        Toast error = Toast.makeText(this.activity, msg, Toast.LENGTH_LONG);
        error.show();
    }

    /**
     * Shows keeping the access keys returned from Trusted Authenticator in a local
     * store, rather than storing user name & password, and re-authenticating each
     * time (which is not to be done, ever).
     *
     * @return Array of [access_key, access_secret], or null if none stored
     */
    private String[] getKeys() {
        SharedPreferences prefs = this.activity.getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
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

    /**
     * Shows keeping the access keys returned from Trusted Authenticator in a local
     * store, rather than storing user name & password, and re-authenticating each
     * time (which is not to be done, ever).
     */
    private void storeKeys(String key, String secret) {
        // Save the access key for later
        SharedPreferences prefs = this.activity.getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString(ACCESS_KEY_NAME, key);
        edit.putString(ACCESS_SECRET_NAME, secret);
        edit.commit();
    }

    private void clearKeys() {
        SharedPreferences prefs = this.activity.getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        SharedPreferences.Editor edit = prefs.edit();
        edit.clear();
        edit.commit();
    }

    private AndroidAuthSession buildSession() {
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
    }


    public class BackupRestoreTask extends AsyncTask<String,Integer,Integer> {

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
            activity = fragment.getActivity();
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
        }

        protected void onPostExecute(Integer result){
            pd.cancel();
            if(type == CLOUDBACKUP){
                File sourceFile = new File(Lime.DATABASE_FOLDER_EXTERNAL + File.separator + Lime.DATABASE_BACKUP_NAME);
                cHandler = new CloudServierHandler(fragment);
                bTask = new Thread(new CloudBackupServiceRunnable(cHandler, fragment, sourceFile));
                bTask.start();
                showProgressDialog(true);
            }else if(type == DROPBOXBACKUP){
                // Jeremy  '12,12,23 do dropbox backup now.
                File sourceFile = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator + LIME.DATABASE_BACKUP_NAME);
                DropboxDBBackup upload = new DropboxDBBackup(activity, mDropboxApi, "", sourceFile);
                upload.execute();
            }else if(type == CLOUDRESTORE || type == RESTORE){
                //activity.initialButton();
                dbsrv.checkPhoneticKeyboardSetting();//Jeremy '12,6,8 check the pheonetic keyboard consistency
                mLIMEPref.setResetCacheFlag(true);  //Jeremy '12,7,8 reset cache.
            }
        }

        @Override
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
    }

}