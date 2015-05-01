package net.toload.main.hd.ui;

import android.os.RemoteException;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;

import net.toload.main.hd.Lime;
import net.toload.main.hd.limesettings.DBServer;

import java.io.File;
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

        if(type.equals(Lime.LOCAL) || type.equals(Lime.GOOGLE)){
            try {
                dbsrv.backupDatabase();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        if(type.equals(Lime.GOOGLE)){
            // Preparing the file to be backup
            try {
                dbsrv.backupDatabase();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            File sourcefile = new File(Lime.DATABASE_FOLDER_EXTERNAL  + Lime.DATABASE_BACKUP_NAME);
            backupToGoogle(sourcefile);
        }else if(type.equals(Lime.DROPBOX)){

        }
    }

    private void backupToGoogle(File fileContent){

        // File's binary content
        FileContent uploadtarget = new FileContent("application/zip", fileContent);

        // File's metadata.
        com.google.api.services.drive.model.File body = new com.google.api.services.drive.model.File();
                                                body.setTitle(Lime.GOOGLE_BACKUP_FILENAME);
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
    }

}
