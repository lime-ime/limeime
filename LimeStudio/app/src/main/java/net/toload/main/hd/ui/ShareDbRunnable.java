package net.toload.main.hd.ui;

import android.app.Activity;

import net.toload.main.hd.DBServer;
import net.toload.main.hd.Lime;
import net.toload.main.hd.MainActivityHandler;
import net.toload.main.hd.R;
import net.toload.main.hd.global.LIMEUtilities;
import net.toload.main.hd.limedb.LimeDB;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Art Hung on 2015/4/26.
 */
public class ShareDbRunnable implements Runnable{

    private static boolean DEBUG = true;
    private static String TAG = "ShareRunnable";

    // Global
    private String imtype = null;
    private Activity activity;
    private MainActivityHandler handler;

    private LimeDB datasource;
    private DBServer dbsrv = null;
    //private LIMEPreferenceManager mLIMEPref;

    public ShareDbRunnable(Activity activity, String imtype, MainActivityHandler handler) {
        this.handler = handler;
        this.imtype = imtype;
        this.activity = activity;
        this.dbsrv = new DBServer(activity);
        this.datasource = new LimeDB(activity);
        //this.mLIMEPref = new LIMEPreferenceManager(activity);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }

    @Override
    public void run() {

        handler.showProgress();

        File targetfile = new File(Lime.DATABASE_FOLDER_EXTERNAL + imtype + Lime.DATABASE_EXT);
        if(targetfile.exists()){
            targetfile.deleteOnExit();
        }

        File targetfilezip = new File(Lime.DATABASE_FOLDER_EXTERNAL + imtype + ".limedb");
        if(targetfilezip.exists()){
            targetfilezip.deleteOnExit();
        }

        // Prepare database file
        handler.updateProgress(activity.getResources().getString(R.string.share_step_initial));


        // Copy Database File
        try {
            InputStream from = activity.getResources().openRawResource( R.raw.blank );
            FileOutputStream to = new FileOutputStream(targetfile);
            byte[] buffer = new byte[4096];
            int bytes_read;
            while ((bytes_read = from.read(buffer)) != -1) {
                to.write(buffer, 0, bytes_read);
            }
            if (from != null) {from.close();}
            if (to != null) {to.close();}
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Open Database File
        handler.updateProgress(activity.getResources().getString(R.string.share_step_write));
        datasource.prepareBackupDb(targetfile.getAbsolutePath(), imtype);

        //ready to zip backup file list
        try {
            List<String> backupFileList = new ArrayList<>();
            backupFileList.add(targetfile.getAbsolutePath());
            LIMEUtilities.zip(targetfilezip.getAbsolutePath(), targetfile.getAbsolutePath(), true);
            targetfile.deleteOnExit();
        } catch (Exception e) {
            e.printStackTrace();
        }

        handler.cancelProgress();
        handler.shareZipTo(targetfilezip.getAbsolutePath());
    }

}
