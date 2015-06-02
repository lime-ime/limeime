package net.toload.main.hd.ui;

import android.app.ProgressDialog;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import net.toload.main.hd.DBServer;
import net.toload.main.hd.Lime;
import net.toload.main.hd.R;
import net.toload.main.hd.global.LIME;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class SetupImGoogleActivity extends ActionBarActivity  implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "SetupImGoogleActivity";
    private static final int REQUEST_CODE_BACKUP = 1;
    private static final int REQUEST_CODE_RESTORE = 2;
    private static final int REQUEST_CODE_RESOLUTION = 3;

    private String action = "";

    private SetupImGoogleHandler handler;
    private ProgressDialog progress;
    private GoogleApiClient mGoogleApiClient;

    @Override
    protected void onResume() {
        super.onResume();
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        // Connect the client. Once connected, the camera is launched.
        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(progress != null && progress.isShowing()){
            progress.dismiss();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_im_google);

        handler = new SetupImGoogleHandler(this);

        action = getIntent().getExtras().getString("actiontype");

    }

    public void backupToGoogle() {

        // Create backup File Temp
        handler.show(this.getResources().getString(R.string.setup_im_backup_message));
        try {
            DBServer.backupDatabase();
        } catch (RemoteException e) {
            e.printStackTrace();
            finish();
        }

        // Load old backup file
        Query query = new Query.Builder()
                .addFilter(Filters.eq(SearchableField.TITLE, Lime.GOOGLE_BACKUP_FILENAME))
                .build();
        Drive.DriveApi.query(mGoogleApiClient, query).setResultCallback(querycallback);

    }


    final private ResultCallback<DriveApi.MetadataBufferResult> querycallback = new
            ResultCallback<DriveApi.MetadataBufferResult>() {
                @Override
                public void onResult(DriveApi.MetadataBufferResult result) {
                    if (!result.getStatus().isSuccess()) {
                        return;
                    }
                    int count = result.getMetadataBuffer().getCount();

                    for(int i=0 ; i < count ; i++){
                        Metadata m = result.getMetadataBuffer().get(i);
                        if(!m.isTrashed()){
                            DriveFile drivefile = Drive.DriveApi.getFile(mGoogleApiClient, m.getDriveId());
                            drivefile.trash(mGoogleApiClient).setResultCallback(null);
                        }
                    }
                    result.getMetadataBuffer().release();
                    startBackup();
                }
            };

    public void startBackup(){

        Drive.DriveApi.newDriveContents(mGoogleApiClient)

                .setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {

                    @Override
                    public void onResult(final DriveApi.DriveContentsResult result) {

                        // If the operation was not successful, we cannot do anything
                        // and must
                        // fail.
                        if (!result.getStatus().isSuccess()) {
                            return;
                        }


                        new Thread() {

                            @Override
                            public void run() {

                                File fileContent = new File(Lime.DATABASE_FOLDER_EXTERNAL + Lime.DATABASE_BACKUP_NAME);
                                OutputStream outputStream = result.getDriveContents().getOutputStream();
                                try {
                                    BufferedInputStream stream = new BufferedInputStream(new FileInputStream(fileContent));
                                    byte buffer[] = new byte[8192];
                                    int byteread;
                                    while ((byteread = stream.read(buffer)) != -1) {
                                        outputStream.write(buffer);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    finish();
                                }

                                MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder()
                                        .setMimeType("application/zip").setTitle(Lime.GOOGLE_BACKUP_FILENAME).build();

                                Drive.DriveApi.getRootFolder(mGoogleApiClient)
                                        .createFile(mGoogleApiClient, metadataChangeSet, result.getDriveContents()).await();

                                finish();

                            }
                        }.start();
                    }
                });
    }


    public void restoreFromGoogle() {

        handler.show(this.getResources().getString(R.string.setup_im_restore_message));

        // Load old backup file
        Query query = new Query.Builder()
                .addFilter(Filters.eq(SearchableField.TITLE, Lime.GOOGLE_BACKUP_FILENAME))
                .build();
        Drive.DriveApi.query(mGoogleApiClient, query).setResultCallback(restorecallback);

    }




    final private ResultCallback<DriveApi.MetadataBufferResult> restorecallback = new
            ResultCallback<DriveApi.MetadataBufferResult>() {
                @Override
                public void onResult(final DriveApi.MetadataBufferResult result) {
                    if (!result.getStatus().isSuccess()) {
                        return;
                    }

                    int count = result.getMetadataBuffer().getCount();

                    for(int i=0 ; i < count ; i++){

                        final Metadata m = result.getMetadataBuffer().get(i);

                        if(!m.isTrashed()){

                            new Thread() {

                                @Override
                                public void run() {

                                    DriveFile drivefile = Drive.DriveApi.getFile(mGoogleApiClient, m.getDriveId());
                                    DriveApi.DriveContentsResult driveContentsResult =
                                            drivefile.open(mGoogleApiClient, DriveFile.MODE_READ_ONLY, null).await();

                                    DriveContents driveContents = driveContentsResult.getDriveContents();

                                    File tempfile = new File(LIME.LIME_SDCARD_FOLDER + File.separator + LIME.DATABASE_CLOUD_TEMP);
                                    InputStream fi = driveContents.getInputStream();
                                    FileOutputStream fo = null;
                                    try {
                                        fo = new FileOutputStream(tempfile);
                                        int bytesRead;
                                        byte[] buffer = new byte[8 * 1024];

                                        if (fi != null) {
                                            while ((bytesRead = fi.read(buffer)) != -1) {
                                                fo.write(buffer, 0, bytesRead);
                                            }
                                        }
                                        DBServer.restoreDatabase(tempfile.getAbsolutePath());

                                        fo.close();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        finish();
                                    }

                                    driveContents.discard(mGoogleApiClient);
                                    result.getMetadataBuffer().release();
                                    finish();
                                }
                            }.start();
                        }
                    }
                }
            };


    public void showProgress(String message) {
        if(progress == null){
            progress = new ProgressDialog(this);
            progress.setCancelable(false);
        }

        if(progress.isShowing()){
            progress.dismiss();
        }

        progress.setMessage(message);
        progress.show();

    }

    @Override
    public void onConnected(Bundle bundle) {
        if(action.equals(Lime.BACKUP)){
            handler.backup();
        }else{
            handler.restore();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "GoogleApiClient connection suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());
        if (!result.hasResolution()) {
            GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(), this, 0).show();
            return;
        }
        try {
            result.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
            finish();
        }
    }

}
