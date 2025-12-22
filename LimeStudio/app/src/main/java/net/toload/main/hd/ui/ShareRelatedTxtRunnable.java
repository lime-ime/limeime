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

import net.toload.main.hd.MainActivityHandler;
import net.toload.main.hd.R;
import net.toload.main.hd.SearchServer;
import net.toload.main.hd.global.LIME;

import java.io.File;

/**
 * Created by Art Hung on 2015/4/26.
 */
public class ShareRelatedTxtRunnable implements Runnable{
    private static final String EXPORT_FILENAME_RELATED = "lime.related"; // Export filename for related data

    private static final boolean DEBUG = true;
    private static final String TAG = "ShareRelatedTxtRunnable";

    // Global
    private final Activity activity;
    private final MainActivityHandler handler;

    private final SearchServer searchServer;

    public ShareRelatedTxtRunnable(Activity activity, MainActivityHandler handler) {
        this.handler = handler;
        this.activity = activity;
        this.searchServer = new SearchServer(activity);
        //LIMEPreferenceManager mLIMEPref = new LIMEPreferenceManager(activity);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }

    @Override
    public void run() {

        handler.showProgress();

        handler.updateProgress(activity.getResources().getString(R.string.share_step_initial));

        File cacheDir = activity.getExternalCacheDir();
        if (cacheDir == null) {
            cacheDir = activity.getCacheDir();
        }
        File target = new File(cacheDir, EXPORT_FILENAME_RELATED);

        handler.updateProgress(activity.getResources().getString(R.string.share_step_write));

        // Use SearchServer.exportTxtTable() with LIME.DB_TABLE_RELATED to export related records
        boolean success = searchServer.exportTxtTable(LIME.DB_TABLE_RELATED, target, null);
        
        if (!success) {
            Log.e(TAG, "Error exporting related table to file");
        }

        handler.cancelProgress();
        handler.shareTxtTo(target.getAbsolutePath());
    }

}
