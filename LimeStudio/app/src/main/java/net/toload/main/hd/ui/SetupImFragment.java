package net.toload.main.hd.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import net.toload.main.hd.Lime;
import net.toload.main.hd.MainActivity;
import net.toload.main.hd.R;
import net.toload.main.hd.global.LIMEPreferenceManager;
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

    // Debug Tag
    private final boolean DEBUG = false;

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


    private DBServer DBSrv = null;
    LIMEPreferenceManager mLIMEPref;


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

        /*btnSetupImRestoreLocal = (Button) rootView.findViewById(R.id.btnSetupImRestoreLocal);
        btnSetupImRestoreLocal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                File srcFile = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator + LIME.DATABASE_BACKUP_NAME);
                File srcFile2 = new File(LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + LIME.DATABASE_NAME);
                if((srcFile2.exists() && srcFile2.length() > 1024) || (srcFile.exists() && srcFile.length() > 1024)){

                    AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
                    builder.setMessage(getText(R.string.l3_initial_restore_confirm));
                    builder.setCancelable(false);
                    builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {

                            String dbtarget = mLIMEPref.getParameterString("dbtarget");
                            if(dbtarget.equals("device")){
                                btnStoreSdcard.setText("");
                            }else if(dbtarget.equals("sdcard")){
                                btnStoreDevice.setText("");
                            }
                            btnStoreSdcard.setEnabled(false);
                            btnStoreDevice.setEnabled(false);

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

                    File srcFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER + File.separator + LIME.DATABASE_NAME);
                    File srcFile2 = new File(LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + LIME.DATABASE_NAME);

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
                            btnStoreSdcard.setEnabled(false);
                            btnStoreDevice.setEnabled(false);
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
                    Log.i(TAG, "Dropbox link, mDropboxLoggedIn = " +mDropboxLoggedIn);

                if(connManager.getActiveNetworkInfo() != null && connManager.getActiveNetworkInfo().isConnected()){

                    File srcFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER + File.separator + LIME.DATABASE_NAME);
                    File srcFile2 = new File(LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + LIME.DATABASE_NAME);
                    if((srcFile2.exists() && srcFile2.length() > 1024) || (srcFile.exists() && srcFile.length() > 1024)){
                        AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
                        builder.setMessage(getText(R.string.l3_initial_dropbox_backup_confirm));
                        builder.setCancelable(false);
                        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {

                                if (!mDropboxLoggedIn) {
                                    // Start the remote authentication
                                    mDropboxApi.getSession().startAuthentication(LIMEInitial.this);
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
                                mDropboxApi.getSession().startAuthentication(LIMEInitial.this);
                                pendingDropboxRestore = true; //do database backup after on Resume();
                            }else{
                                //
                                restoreDatabaseDropbox();
                            }

                            btnStoreSdcard.setEnabled(false);
                            btnStoreDevice.setEnabled(false);
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
           /* pd.cancel();
            if(type == CLOUDBACKUP){
                File sourceFile = new File(Lime.DATABASE_FOLDER_EXTERNAL + File.separator + Lime.DATABASE_BACKUP_NAME);
                cHandler = new CloudServierHandler(LIMEInitial.this);
                bTask = new Thread(new CloudBackupServiceRunnable(cHandler, LIMEInitial.this, sourceFile));
                bTask.start();
                showProgressDialog(true);
            }else if(type == DROPBOXBACKUP){
                // Jeremy  '12,12,23 do dropbox backup now.
                File sourceFile = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator + LIME.DATABASE_BACKUP_NAME);
                DropboxDBBackup upload = new DropboxDBBackup(LIMEInitial.this, mDropboxApi, "", sourceFile);
                upload.execute();
            }else if(type == CLOUDRESTORE || type == RESTORE){
                activity.initialButton();
                dbsrv.checkPhoneticKeyboardSetting();//Jeremy '12,6,8 check the pheonetic keyboard consistency
                mLIMEPref.setResetCacheFlag(true);  //Jeremy '12,7,8 reset cache.
            }*/
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