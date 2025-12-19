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
import android.database.Cursor;
import android.util.Log;

import net.toload.main.hd.global.LIME;
import net.toload.main.hd.MainActivityHandler;
import net.toload.main.hd.R;
import net.toload.main.hd.data.Related;
import net.toload.main.hd.limedb.LimeDB;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Art Hung on 2015/4/26.
 */
public class ShareRelatedTxtRunnable implements Runnable{

    private static final boolean DEBUG = true;
    private static final String TAG = "ShareRelatedTxtRunnable";

    // Global
    private final Activity activity;
    private final MainActivityHandler handler;

    private final LimeDB datasource;

    public ShareRelatedTxtRunnable(Activity activity, MainActivityHandler handler) {
        this.handler = handler;
        this.activity = activity;
        this.datasource = new LimeDB(activity);
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

        // Load
        List<Related> relatedlist = new ArrayList<>();
        Cursor cursor = datasource.list(LIME.DB_RELATED);
        cursor.moveToFirst();
        while(!cursor.isAfterLast()){
            Related r = Related.get(cursor);
            relatedlist.add(r);
            cursor.moveToNext();
        }

        File cacheDir = activity.getExternalCacheDir();
        if (cacheDir == null) {
            cacheDir = activity.getCacheDir();
        }
        File target = new File(cacheDir, LIME.EXPORT_FILENAME_RELATED);
        if(target.exists() && !target.delete()){
            Log.e(TAG, "Error in file deletion");
        }

        handler.updateProgress(activity.getResources().getString(R.string.share_step_write));

        try {

            Writer writer = new OutputStreamWriter( new FileOutputStream(target), StandardCharsets.UTF_8);
            BufferedWriter fout = new BufferedWriter(writer);

            for(Related w: relatedlist){
                if(w.getPword() == null || w.getCword() == null || w.getCword().isEmpty() ){continue;}
                String s = w.getPword()+w.getCword()+"|"+w.getBasescore()+"|"+w.getUserscore();
                fout.write(s);
                fout.newLine();
            }

            fout.close();

        } catch (IOException e) {
            Log.e(TAG, "Error in operation", e);
        }

        handler.cancelProgress();
        handler.shareTxtTo(target.getAbsolutePath());
    }

}
