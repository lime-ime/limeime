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
