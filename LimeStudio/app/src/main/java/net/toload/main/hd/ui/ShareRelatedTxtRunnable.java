/*
 *
 *  **    Copyright 2015, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/jrywu/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package net.toload.main.hd.ui;

import android.app.Activity;
import android.database.Cursor;

import net.toload.main.hd.Lime;
import net.toload.main.hd.MainActivityHandler;
import net.toload.main.hd.R;
import net.toload.main.hd.data.Related;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.limedb.LimeDB;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Art Hung on 2015/4/26.
 */
public class ShareRelatedTxtRunnable implements Runnable{

    private static boolean DEBUG = true;
    private static String TAG = "ShareRelatedTxtRunnable";

    // Global
    private Activity activity;
    private MainActivityHandler handler;

    private LimeDB datasource;
    private LIMEPreferenceManager mLIMEPref;

    public ShareRelatedTxtRunnable(Activity activity, MainActivityHandler handler) {
        this.handler = handler;
        this.activity = activity;
        this.datasource = new LimeDB(activity);
        this.mLIMEPref = new LIMEPreferenceManager(activity);
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
        List<Related> relatedlist = new ArrayList<Related>();
        Cursor cursor = datasource.list(Lime.DB_RELATED);
        cursor.moveToFirst();
        while(!cursor.isAfterLast()){
            Related r = Related.get(cursor);
            relatedlist.add(r);
            cursor.moveToNext();
        }

        String targetfile = Lime.DATABASE_FOLDER_EXTERNAL + Lime.EXPORT_FILENAME_RELATED;

        handler.updateProgress(activity.getResources().getString(R.string.share_step_write));

        try {

            File target = new File(targetfile);
                 target.deleteOnExit();

            Writer writer = new OutputStreamWriter( new FileOutputStream(target), "UTF-8");
            BufferedWriter fout = new BufferedWriter(writer);

            for(Related w: relatedlist){
                if(w.getPword() == null || w.getCword() == null || w.getCword().isEmpty() ){continue;}
                String s = w.getPword()+w.getCword()+"|"+w.getBasescore()+"|"+w.getUserscore();
                fout.write(s);
                fout.newLine();
            }

            fout.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        handler.cancelProgress();
        handler.shareTxtTo(targetfile);
    }

}
