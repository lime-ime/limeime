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
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.RemoteException;
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

public class SetupImGoogleActivity extends Activity implements
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

        action = getIntent().getExtras().getString("actiontype");

        if(action.equals(Lime.BACKUP)){
            handler.show(this.getResources().getString(R.string.setup_im_backup_message));
        }else{
            handler.show(this.getResources().getString(R.string.setup_im_restore_message));
        }

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
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_im_google);

        handler = new SetupImGoogleHandler(this);

    }

    public void backupToGoogle() {

        // Create backup File Temp
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

                                long total = fileContent.length();
                                long uploadsize = 0;

                                try {
                                    BufferedInputStream stream = new BufferedInputStream(new FileInputStream(fileContent));
                                    byte buffer[] = new byte[8192];
                                    int byteread;
                                    while ((byteread = stream.read(buffer)) != -1) {
                                        outputStream.write(buffer);
                                        uploadsize += buffer.length;

                                        float percent = (float)uploadsize / (float)total;
                                        percent = percent * 100;
                                        handler.update((int)percent);
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

                                    long total = m.getFileSize();
                                    long downloadsize = 0;

                                    try {
                                        File tempdir = new File(LIME.LIME_SDCARD_FOLDER + File.separator);
                                        if(!tempdir.exists()){
                                            tempdir.mkdirs();
                                        }
                                        File tempfile = new File(LIME.LIME_SDCARD_FOLDER + File.separator + LIME.DATABASE_CLOUD_TEMP);
                                        InputStream fi = driveContents.getInputStream();
                                        FileOutputStream fo = null;

                                        fo = new FileOutputStream(tempfile);
                                        int bytesRead;
                                        byte[] buffer = new byte[8 * 1024];

                                        if (fi != null) {
                                            while ((bytesRead = fi.read(buffer)) != -1) {
                                                fo.write(buffer, 0, bytesRead);
                                                downloadsize += buffer.length;

                                                float percent = (float)downloadsize / (float)total;
                                                percent = percent * 100;
                                                handler.update((int)percent);
                                            }
                                        }
                                        DBServer.restoreDatabase(tempfile.getAbsolutePath());

                                        fo.close();

                                        driveContents.discard(mGoogleApiClient);
                                        result.getMetadataBuffer().release();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    finish();
                                }
                            }.start();
                        }
                    }
                }
            };

    public void updateProgress(int value) {
        if(progress.isShowing()){
            progress.setProgress(value);
        }
    }

    public void showProgress(String message) {
        if(progress == null){
            progress = new ProgressDialog(this, R.style.LIMEAlertDialogTheme);
            progress.setCancelable(false);
        }

        if(progress.isShowing()){
            progress.dismiss();
        }

        progress.setMax(100);
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMessage(message);
        progress.show();

    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.e(TAG, "Google Drive Connected");
        if(action.equals(Lime.BACKUP)){
            handler.backup();
        }else{
            handler.restore();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "GoogleApiClient connection suspended");
        finish();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());
        if (!result.hasResolution()) {
            GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(), this, 0).show();
            finish();
        }
        try {
            result.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_CANCELED) {
            Log.i(TAG, "#onActivityResult RC_SIGN_IN user cancelled");
            finish();
        }
    }


}
