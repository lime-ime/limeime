package net.toload.main.hd.ui;

import android.os.RemoteException;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.services.drive.Drive;

import net.toload.main.hd.Lime;
import net.toload.main.hd.limesettings.DBServer;

/**
 * Created by Art Hung on 2015/4/26.
 */
public class SetupImRestoreRunnable implements Runnable{

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

    public SetupImRestoreRunnable(SetupImFragment fragment, SetupImHandler handler, String type, GoogleAccountCredential credential, DropboxAPI mdbapi) {
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

    public void run() {
        handler.showProgress();

        if(type.equals(Lime.LOCAL)){
            try {
                dbsrv.restoreDatabase();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }else if(type.equals(Lime.GOOGLE)){

        }else if(type.equals(Lime.DROPBOX)){

        }

    }

}
