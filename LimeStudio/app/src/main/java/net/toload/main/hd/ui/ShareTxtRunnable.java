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
import net.toload.main.hd.data.Im;
import net.toload.main.hd.SearchServer;

import java.io.File;
import java.util.List;

/**
 * Created by Art Hung on 2015/4/26.
 */
public class ShareTxtRunnable implements Runnable{

    private static final boolean DEBUG = true;
    private static final String TAG = "ShareRunnable";

    // Global
    private final String imtype;
    private final Activity activity;
    private final MainActivityHandler handler;

    private final SearchServer searchServer;

    public ShareTxtRunnable(Activity activity, String imtype, MainActivityHandler handler) {
        this.handler = handler;
        this.imtype = imtype;
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
        File target = new File(cacheDir, imtype + ".lime");
        
        String targetfile = target.getAbsolutePath();

        List<Im> iminfo = searchServer.getImList(imtype);

        handler.updateProgress(activity.getResources().getString(R.string.share_step_write));

        // Use SearchServer.exportTxtTable() to export records
        boolean success = searchServer.exportTxtTable(imtype, target, iminfo);
        
        if (!success) {
            Log.e(TAG, "Error exporting table to file");
        }

        handler.cancelProgress();
        handler.shareTxtTo(targetfile);
    }

}
