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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Art Hung on 2015/4/26.
 */
public class ShareRelatedDbRunnable implements Runnable{

    private static final boolean DEBUG = true;
    private static final String TAG = "ShareRelatedDbRunnable";

    // Global
    private final Activity activity;
    private final MainActivityHandler handler;

    private final DBServer dbsrv;

    public ShareRelatedDbRunnable(Activity activity, MainActivityHandler handler) {
        this.handler = handler;
        this.activity = activity;
        this.dbsrv = DBServer.getInstance(activity);
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

        File targetfilezip = new File(cacheDir, LIME.DB_TABLE_RELATED + ".limedb");
        if(targetfilezip.exists() && !targetfilezip.delete()){
            Log.e(TAG, "Error in file deletion");
        }

        // Use DBServer's centralized export method
        handler.updateProgress(activity.getResources().getString(R.string.share_step_initial));
        File exportedFile = dbsrv.exportZippedDbRelated(targetfilezip, 
            () -> handler.updateProgress(activity.getResources().getString(R.string.share_step_write)));

        handler.cancelProgress();
        if (exportedFile != null) {
            handler.shareDBTo(exportedFile.getAbsolutePath());
        } else {
            Log.e(TAG, "Failed to export database");
        }
    }

}
