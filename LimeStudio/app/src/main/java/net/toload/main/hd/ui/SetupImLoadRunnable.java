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
import android.content.Context;
import android.os.Looper;
import android.util.Log;

import net.toload.main.hd.DBServer;
import net.toload.main.hd.SearchServer;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.R;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.global.LIMEUtilities;

import java.io.File;
import java.util.List;

public class SetupImLoadRunnable implements Runnable{
    private final static boolean DEBUG = false;
    private final static String TAG = "SetupImLoadRunnable";

    // Global
    private final String url;
    private final String imtype;
    private final String type;

    private final Activity activity;
    private final DBServer dbsrv;
    private final SetupImHandler handler;

    private final SearchServer searchServer;
    private final LIMEPreferenceManager mLIMEPref;

    private final Context mContext;
    private final boolean restorePreference;

    public SetupImLoadRunnable(Activity activity, SetupImHandler handler, String imtype, String type, String url, boolean restorePreference) {
        this.handler = handler;
        this.imtype = imtype;
        this.type = type;
        this.url = url;
        this.activity = activity;
        this.dbsrv = DBServer.getInstance(activity);
        this.searchServer = new SearchServer(activity);
        this.mLIMEPref = new LIMEPreferenceManager(activity);
        this.restorePreference = restorePreference;
        this.mContext = activity.getBaseContext();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }

    @Override
    public void run() {

        Looper.prepare();

        //Log.i("LIME", "showProgress Runnable:");
        handler.showProgress(false, activity.getResources().getString(R.string.setup_load_download));

        // Download DB File
        //handler.updateProgress(activity.getResources().getString(R.string.setup_load_download));
        File tempfile = downloadRemoteFile(mContext, url);

        final int minFileSizeBytes = 100000; // Minimum file size threshold in bytes
        if(tempfile == null || tempfile.length() < minFileSizeBytes){
            assert tempfile != null;
            Log.e(TAG, "Invalid file size: " + tempfile.length());
            return;
        }


        // Load DB
        handler.updateProgress(activity.getResources().getString(R.string.setup_load_migrate_load));
        dbsrv.importZippedDb(tempfile, imtype);

        mLIMEPref.setParameter("_table", "");
        //mLIMEPref.setResetCacheFlag(true);
        searchServer.resetCache();

        if(restorePreference){
            handler.updateProgress(activity.getResources().getString(R.string.setup_im_restore_learning_data));
            handler.updateProgress(0);
            boolean check = searchServer.checkBackuptable(imtype);
            handler.updateProgress(5);

            if(check){
                handler.updateProgress(10);
                int restoredCount = searchServer.restoreUserRecords(imtype);
                if (restoredCount > 0) {
                    handler.updateProgress(LIME.PROGRESS_COMPLETE_PERCENT);
                }
            }
        }

        handler.finishLoading(imtype);
        handler.initialImButtons();

    }


    // loadWord() method moved to LimeDB.getRecordsFromSourceDB()
    // Use datasource.loadWordFromSourceDB(sourceDb, tableName) instead
    
    // setImInfo(), setIMKeyboardOnDB(), removeImInfoOnDB() methods removed
    // Use datasource.setImInfo(), datasource.setIMKeyboard() directly instead


    public synchronized void setIMKeyboard(String im, String value,  String keyboard) {
        try{
            // Use LimeDB method directly
            searchServer.setIMKeyboard(im, value, keyboard);
        }catch (Exception e) {
            Log.e(TAG, "Error in operation", e);
        }
    }


    public synchronized void removeImInfo(String im, String field) {
        try{
            // Use LimeDB method directly
            searchServer.removeImInfo(im, field);
        }catch(Exception e){Log.e(TAG, "Error in operation", e);}
    }



    /*
	 * Download Remote File
	 * Uses shared utility method from LIMEUtilities
	 */
    public File downloadRemoteFile(Context ctx, String url){
        // Use shared download utility with progress callback
        return LIMEUtilities.downloadRemoteFile(url, null, ctx.getCacheDir(),
                handler::updateProgress, null);
    }


}
