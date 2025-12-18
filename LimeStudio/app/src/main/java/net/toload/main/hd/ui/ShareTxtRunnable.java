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
import net.toload.main.hd.data.Im;
import net.toload.main.hd.data.Word;
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
public class ShareTxtRunnable implements Runnable{

    private static final boolean DEBUG = true;
    private static final String TAG = "ShareRunnable";

    // Global
    private final String imtype;
    private final Activity activity;
    private final MainActivityHandler handler;

    private final LimeDB datasource;

    public ShareTxtRunnable(Activity activity, String imtype, MainActivityHandler handler) {
        this.handler = handler;
        this.imtype = imtype;
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
        List<Word> wordlist = new ArrayList<>();
        Cursor cursor = datasource.list(imtype);
        cursor.moveToFirst();
        while(!cursor.isAfterLast()){
            Word r = Word.get(cursor);
            wordlist.add(r);
            cursor.moveToNext();
        }

        File cacheDir = activity.getExternalCacheDir();
        if (cacheDir == null) {
            cacheDir = activity.getCacheDir();
        }
        File target = new File(cacheDir, imtype + ".lime");
        if(target.exists() && !target.delete()){
            Log.e(TAG, "Error in file deletion");
        }

        
        String targetfile = target.getAbsolutePath();

        List<Im> iminfo = datasource.getImList(imtype);

        if(iminfo != null && !iminfo.isEmpty() && !wordlist.isEmpty()){

            handler.updateProgress(activity.getResources().getString(R.string.share_step_write));

            try {

                BufferedWriter fout = getBufferedWriter(target, iminfo, wordlist);

                fout.close();

            } catch (IOException e) {
                Log.e(TAG, "Error in operation", e);
            }
        }

        handler.cancelProgress();
        handler.shareTxtTo(targetfile);
    }

    private static BufferedWriter getBufferedWriter(File target, List<Im> iminfo, List<Word> wordlist) throws IOException {
        Writer writer = new OutputStreamWriter( new FileOutputStream(target), StandardCharsets.UTF_8);
        BufferedWriter fout = new BufferedWriter(writer);

        for(Im i: iminfo){

            if(i.getTitle().equals(LIME.IM_TYPE_NAME)){
                String s = "@version@|"+i.getDesc();
                fout.write(s);
                fout.newLine();
            }
            if(i.getTitle().equals(LIME.IM_TYPE_SELKEY)){
                String s = "@selkey@|"+i.getDesc();
                fout.write(s);
                fout.newLine();
            }
            if(i.getTitle().equals(LIME.IM_TYPE_ENDKEY)){
                String s = "@endkey@|"+i.getDesc();
                fout.write(s);
                fout.newLine();
            }
            if(i.getTitle().equals(LIME.IM_TYPE_SPACESTYLE)){
                String s = "@spacestyle@|"+i.getDesc();
                fout.write(s);
                fout.newLine();
            }

        }

        for(Word w: wordlist){
            if(w.getWord() == null || w.getWord().equals("null")){continue;}
            String s = w.getCode()+"|"+w.getWord()+"|"+w.getScore()+"|"+w.getBasescore();
            fout.write(s);
            fout.newLine();
        }
        return fout;
    }

}
