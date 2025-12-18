/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
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
import android.util.Log;

import net.toload.main.hd.DBServer;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.MainActivityHandler;
import net.toload.main.hd.R;
import net.toload.main.hd.global.LIMEUtilities;
import net.toload.main.hd.limedb.LimeDB;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Art Hung on 2015/4/26.
 */
public class ShareDbRunnable implements Runnable{

    private static final boolean DEBUG = true;
    private static final String TAG = "ShareDbRunnable";

    // Global
    private final String imtype;
    private final Activity activity;
    private final MainActivityHandler handler;

    private final LimeDB datasource;
    //private LIMEPreferenceManager mLIMEPref;

    public ShareDbRunnable(Activity activity, String imtype, MainActivityHandler handler) {
        this.handler = handler;
        this.imtype = imtype;
        this.activity = activity;
        DBServer dbsrv = DBServer.getInstance(activity);
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

        File cacheDir = activity.getExternalCacheDir();
        if (cacheDir == null) {
            cacheDir = activity.getCacheDir();
        }

        File targetfile = new File(cacheDir, imtype + LIME.DATABASE_EXT);
        if(targetfile.exists() && !targetfile.delete()){
            Log.e(TAG, "Error in file deletion");
        }

        File targetfilezip = new File(cacheDir, imtype + ".limedb");
        if(targetfilezip.exists() && !targetfilezip.delete()){
            Log.e(TAG, "Error in file deletion");
        }

        // Prepare database file
        handler.updateProgress(activity.getResources().getString(R.string.share_step_initial));


        // Copy Database File
        try {
            InputStream from = activity.getResources().openRawResource( R.raw.blank );
            FileOutputStream to = new FileOutputStream(targetfile);
            byte[] buffer = new byte[LIME.BUFFER_SIZE_4KB];
            int bytes_read;
            while ((bytes_read = from.read(buffer)) != -1) {
                to.write(buffer, 0, bytes_read);
            }
            from.close();
            to.close();
        } catch (IOException e) {
            Log.e(TAG, "Error in operation", e);
        }

        // Open Database File
        handler.updateProgress(activity.getResources().getString(R.string.share_step_write));
        datasource.prepareBackupDb(targetfile.getAbsolutePath(), imtype);

        //ready to zip backup file list
        try {

            LIMEUtilities.zip(targetfilezip.getAbsolutePath(), targetfile.getAbsolutePath(), true);
            if(targetfilezip.exists() && !targetfile.delete())
                Log.e(TAG, "Error in file deletion");
        } catch (Exception e) {
            Log.e(TAG, "Error in operation", e);
        }

        handler.cancelProgress();
        handler.shareDBTo(targetfilezip.getAbsolutePath());
    }

}
