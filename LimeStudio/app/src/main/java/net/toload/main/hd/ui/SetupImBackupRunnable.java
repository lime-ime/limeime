package net.toload.main.hd.ui;

import android.os.RemoteException;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.ProgressListener;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxFileSizeException;
import com.dropbox.client2.exception.DropboxIOException;
import com.dropbox.client2.exception.DropboxParseException;
import com.dropbox.client2.exception.DropboxPartialFileException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.exception.DropboxUnlinkedException;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;

import net.toload.main.hd.Lime;
import net.toload.main.hd.R;
import net.toload.main.hd.limesettings.DBServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Art Hung on 2015/4/26.
 */
public class SetupImBackupRunnable implements Runnable{

    // Global
    private String type = null;
    private DBServer dbsrv = null;
    private SetupImFragment fragment = null;

    // Google
    private static Drive service;
    private SetupImHandler handler;
    private GoogleAccountCredential credential;

    // Dropbox
    private DropboxAPI<AndroidAuthSession> mdbapi;
    private DropboxAPI.UploadRequest mRequest;

    public SetupImBackupRunnable(SetupImFragment fragment, SetupImHandler handler, String type, GoogleAccountCredential credential, DropboxAPI mdbapi) {
        this.credential = credential;
        this.handler = handler;
        this.type = type;
        this.mdbapi = mdbapi;
        this.fragment = fragment;
        this.dbsrv = new DBServer(this.fragment.getActivity());
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        handler.cancelProgress();
    }

    @Override
    public void run() {

        handler.showProgress();

        // Preparing the file to be backup
        if(type.equals(Lime.LOCAL) || type.equals(Lime.GOOGLE) || type.equals(Lime.DROPBOX)){
            try {
                dbsrv.backupDatabase();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        if(type.equals(Lime.GOOGLE)){
            File sourcefile = new File(Lime.DATABASE_FOLDER_EXTERNAL  + Lime.DATABASE_BACKUP_NAME);
            backupToGoogle(sourcefile);
        }else if(type.equals(Lime.DROPBOX)){
            File sourcefile = new File(Lime.DATABASE_FOLDER_EXTERNAL  + Lime.DATABASE_BACKUP_NAME);
            backupToDropbox(sourcefile);
        }
    }

    private void backupToGoogle(File fileContent){

        // File's binary content
        FileContent uploadtarget = new FileContent("application/zip", fileContent);

        // File's metadata.
        com.google.api.services.drive.model.File body = new com.google.api.services.drive.model.File();
                                                body.setTitle(Lime.GOOGLE_BACKUP_FILENAME);
                                                body.setMimeType("application/zip");
                                                body.setDescription("This is the backup file uploaded by LIMEIME");

        service = getDriveService(credential);

        List<com.google.api.services.drive.model.File> result = new ArrayList<com.google.api.services.drive.model.File>();
        try {
            int count = 0;
            boolean continueload = false;
            Drive.Files.List request = service.files().list();
            com.google.api.services.drive.model.File cloudbackupfile = null;

            // Search and get cloudbackupfile
            do {
                FileList files = request.execute();
                for(com.google.api.services.drive.model.File f: files.getItems()){
                    if(f.getTitle().equalsIgnoreCase(Lime.GOOGLE_BACKUP_FILENAME)){
                        cloudbackupfile = f;
                        continueload = false;
                        break;
                    }
                }
                count += files.getItems().size();
                if(!continueload || count >= Lime.GOOGLE_RETRIEVE_MAXIMUM){
                    break;
                }
            } while (request.getPageToken() != null &&
                    request.getPageToken().length() > 0);

            // Remove the file on the google drive if exists
            if(cloudbackupfile != null){
                service.files().delete(cloudbackupfile.getId()).execute();
            }

            // Upload file to the google drive
            com.google.api.services.drive.model.File checkfile = service.files().insert(body, uploadtarget).execute();
            if (checkfile != null) {
                handler.showToastMessage(fragment.getResources().getString(R.string.l3_initial_cloud_backup_end), Toast.LENGTH_LONG);
            }else{
                handler.showToastMessage(fragment.getResources().getString(R.string.l3_initial_cloud_restore_error), Toast.LENGTH_LONG);
            }
        } catch (Exception e1) {
            e1.printStackTrace();
        }

    }

    private Drive getDriveService(GoogleAccountCredential credential) {
        return new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential).build();
    }


    private void backupToDropbox(File fileContent){

        try {

            // By creating a request, we get a handle to the putFile operation,
            // so we can cancel it later if we want to

            FileInputStream fis = new FileInputStream(fileContent);
            String path = fileContent.getName();
            mRequest = mdbapi.putFileOverwriteRequest(path, fis, fileContent.length(),
                    new ProgressListener() {
                        @Override
                        public long progressInterval() {
                            // Update the progress bar every half-second or so
                            return 500;
                        }
                        @Override
                        public void onProgress(long bytes, long total) {
                            //publishProgress(bytes);
                        }
                    });

            if (mRequest != null) {
                mRequest.upload();
            }

        } catch (DropboxUnlinkedException e) {
            // This session wasn't authenticated properly or user unlinked
            //mErrorMsg = "This app wasn't authenticated properly.";
            //mErrorMsg = mContext.getText(R.string.l3_initial_dropbox_authetication_failed).toString();
        } catch (DropboxFileSizeException e) {
            // File size too big to upload via the API
            //mErrorMsg = "This file is too big to upload";
            //mErrorMsg = mContext.getText(R.string.l3_initial_dropbox_large).toString();
        } catch (DropboxPartialFileException e) {
            // We canceled the operation
           // mErrorMsg = "Upload canceled";
           // mErrorMsg = mContext.getText(R.string.l3_initial_dropbox_canceled).toString();
        } catch (DropboxServerException e) {
            // Server-side exception.  These are examples of what could happen,
            // but we don't do anything special with them here.
            if (e.error == DropboxServerException._401_UNAUTHORIZED) {
                // Unauthorized, so we should unlink them.  You may want to
                // automatically log the user out in this case.
            } else if (e.error == DropboxServerException._403_FORBIDDEN) {
                // Not allowed to access this
            } else if (e.error == DropboxServerException._404_NOT_FOUND) {
                // path not found (or if it was the thumbnail, can't be
                // thumbnailed)
            } else if (e.error == DropboxServerException._507_INSUFFICIENT_STORAGE) {
                // user is over quota
            } else {
                // Something else
            }
            // This gets the Dropbox error, translated into the user's language
           // mErrorMsg = e.body.userError;
           // if (mErrorMsg == null) {
           //     mErrorMsg = e.body.error;
           // }
        } catch (DropboxIOException e) {
            // Happens all the time, probably want to retry automatically.
            //mErrorMsg = "Network error.  Try again.";
           // mErrorMsg = mContext.getText(R.string.l3_initial_dropbox_failed).toString();
        } catch (DropboxParseException e) {
            // Probably due to Dropbox server restarting, should retry
            //mErrorMsg = "Dropbox error.  Try again.";
            //mErrorMsg = mContext.getText(R.string.l3_initial_dropbox_failed).toString();
        } catch (DropboxException e) {
            // Unknown error
            //mErrorMsg = "Unknown error.  Try again.";
            //mErrorMsg = mContext.getText(R.string.l3_initial_dropbox_failed).toString();
        } catch (FileNotFoundException e) {
        }

    }

}
