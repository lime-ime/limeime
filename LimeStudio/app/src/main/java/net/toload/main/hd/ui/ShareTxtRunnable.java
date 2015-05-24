package net.toload.main.hd.ui;

import android.app.Activity;
import android.database.Cursor;

import net.toload.main.hd.Lime;
import net.toload.main.hd.MainActivityHandler;
import net.toload.main.hd.R;
import net.toload.main.hd.data.Im;
import net.toload.main.hd.data.Word;
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
public class ShareTxtRunnable implements Runnable{

    private static boolean DEBUG = true;
    private static String TAG = "ShareRunnable";

    // Global
    private String imtype = null;
    private Activity activity;
    private MainActivityHandler handler;

    private LimeDB datasource;
    private LIMEPreferenceManager mLIMEPref;

    public ShareTxtRunnable(Activity activity, String imtype, MainActivityHandler handler) {
        this.handler = handler;
        this.imtype = imtype;
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
        List<Word> wordlist = new ArrayList<Word>();
        Cursor cursor = datasource.list(imtype);
        cursor.moveToFirst();
        while(!cursor.isAfterLast()){
            Word r = Word.get(cursor);
            wordlist.add(r);
            cursor.moveToNext();
        }

        String targetfile = Lime.DATABASE_FOLDER_EXTERNAL + imtype + ".lime";

        List<Im> iminfo = datasource.getImList(imtype);

        if(iminfo != null && iminfo.size() > 0 && wordlist.size() > 0){

            handler.updateProgress(activity.getResources().getString(R.string.share_step_write));

            try {

                File target = new File(targetfile);
                     target.deleteOnExit();

                Writer writer = new OutputStreamWriter( new FileOutputStream(target), "UTF-8");
                BufferedWriter fout = new BufferedWriter(writer);

                for(Im i: iminfo){

                    if(i.getTitle().equals(Lime.IM_TYPE_NAME)){
                        String s = "@version@|"+i.getDesc();
                        fout.write(s);
                        fout.newLine();
                    }
                    if(i.getTitle().equals(Lime.IM_TYPE_SELKEY)){
                        String s = "@selkey@|"+i.getDesc();
                        fout.write(s);
                        fout.newLine();
                    }
                    if(i.getTitle().equals(Lime.IM_TYPE_ENDKEY)){
                        String s = "@endkey@|"+i.getDesc();
                        fout.write(s);
                        fout.newLine();
                    }
                    if(i.getTitle().equals(Lime.IM_TYPE_SPACESTYLE)){
                        String s = "@spacestyle@|"+i.getDesc();
                        fout.write(s);
                        fout.newLine();
                    }

                }

                for(Word w: wordlist){
                    if(w.getWord() == null || w.getWord().equals("null")){continue;}
                    String s = w.getCode()+"|"+w.getWord();
                    fout.write(s);
                    fout.newLine();
                }

                fout.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        handler.cancelProgress();
        handler.shareTxtTo(targetfile);
    }

}
