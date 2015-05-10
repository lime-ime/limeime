package net.toload.main.hd.ui;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.RemoteException;

import net.toload.main.hd.Lime;
import net.toload.main.hd.R;
import net.toload.main.hd.data.DataSource;
import net.toload.main.hd.data.Word;
import net.toload.main.hd.global.KeyboardObj;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.limesettings.DBServer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Art Hung on 2015/4/26.
 */
public class SetupImLoadRunnable implements Runnable{

    // Global
    private String url = null;
    private String imtype = null;

    private Activity activity;
    private DBServer dbsrv = null;
    private SetupImHandler handler;

    private DataSource datasource;
    private LIMEPreferenceManager mLIMEPref;

    public SetupImLoadRunnable(Activity activity, SetupImHandler handler, String imtype, String url) {
        this.handler = handler;
        this.imtype = imtype;
        this.url = url;
        this.activity = activity;
        this.dbsrv = new DBServer(activity);
        this.datasource = new DataSource(activity);
        this.mLIMEPref = new LIMEPreferenceManager(activity);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }

    @Override
    public void run() {
        handler.showProgress();

        // Download DB File
        handler.updateProgress(activity.getResources().getString(R.string.setup_load_download));
        File tempfile = downloadRemoteFile(url);

        // Load DB
        int count = migrateDb(tempfile, imtype);
        String im_input = activity.getResources().getString(R.string.im_input_method);
        String defaultname = imtype;
        if(imtype.equalsIgnoreCase(Lime.DB_TABLE_ARRAY)){
            defaultname = activity.getResources().getString(R.string.im_array) + im_input;
        }else if (imtype.equalsIgnoreCase(Lime.DB_TABLE_ARRAY10)){
            defaultname = activity.getResources().getString(R.string.im_array10) + im_input;
        }else if (imtype.equalsIgnoreCase(Lime.DB_TABLE_CJ)){
            defaultname = activity.getResources().getString(R.string.im_cj) + im_input;
        }else if (imtype.equalsIgnoreCase(Lime.DB_TABLE_CJ5)){
            defaultname = activity.getResources().getString(R.string.im_cj5) + im_input;
        }else if (imtype.equalsIgnoreCase(Lime.DB_TABLE_DAYI)){
            defaultname = activity.getResources().getString(R.string.im_dayi) + im_input;
        }else if (imtype.equalsIgnoreCase(Lime.DB_TABLE_ECJ)){
            defaultname = activity.getResources().getString(R.string.im_ecj) + im_input;
        }else if (imtype.equalsIgnoreCase(Lime.DB_TABLE_EZ)){
            defaultname = activity.getResources().getString(R.string.im_ez) + im_input;
        }else if (imtype.equalsIgnoreCase(Lime.DB_TABLE_PHONETIC)){
            defaultname = activity.getResources().getString(R.string.im_phonetic) + im_input;
        }else if (imtype.equalsIgnoreCase(Lime.DB_TABLE_PINYIN)){
            defaultname = activity.getResources().getString(R.string.im_pinyin) + im_input;
        }else if (imtype.equalsIgnoreCase(Lime.DB_TABLE_SCJ)){
            defaultname = activity.getResources().getString(R.string.im_scj) + im_input;
        }else if (imtype.equalsIgnoreCase(Lime.DB_TABLE_WB)){
            defaultname = activity.getResources().getString(R.string.im_wb) + im_input;
        }
        /**
         * else if (imtype.equalsIgnoreCase(Lime.DB_TABLE_HS)){
         defaultname = activity.getResources().getString(R.string.im_hs) + im_input;
         }
         */

        // Update Related Table
        mLIMEPref.setParameter("_table", "");

        setImInfo(imtype, "source", imtype);
        setImInfo(imtype, "name", defaultname);
        setImInfo(imtype, "original", imtype);
        setImInfo(imtype, "amount", String.valueOf(count));
        setImInfo(imtype, "import", new Date().toString());

        String selkey = "";
        String endkey = "";
        String spacestyle = "";
        String imkeys = "";
        String imkeynames = "";

        // If user download from LIME Default IM SET then fill in related information
        if(imtype.equals(Lime.DB_TABLE_PHONETIC)){
            setImInfo("phonetic", "selkey", "123456789");
            setImInfo("phonetic", "endkey", "3467'[]\\=<>?:\"{}|~!@#$%^&*()_+");
            setImInfo("phonetic", "imkeys", ",-./0123456789;abcdefghijklmnopqrstuvwxyz'[]\\=<>?:\"{}|~!@#$%^&*()_+");
            setImInfo("phonetic", "imkeynames", "ㄝ|ㄦ|ㄡ|ㄥ|ㄢ|ㄅ|ㄉ|ˇ|ˋ|ㄓ|ˊ|˙|ㄚ|ㄞ|ㄤ|ㄇ|ㄖ|ㄏ|ㄎ|ㄍ|ㄑ|ㄕ|ㄘ|ㄛ|ㄨ|ㄜ|ㄠ|ㄩ|ㄙ|ㄟ|ㄣ|ㄆ|ㄐ|ㄋ|ㄔ|ㄧ|ㄒ|ㄊ|ㄌ|ㄗ|ㄈ|、|「|」|＼|＝|，|。|？|：|；|『|』|│|～|！|＠|＃|＄|％|︿|＆|＊|（|）|－|＋");
        }if(imtype.equals(Lime.DB_TABLE_ARRAY)){
            setImInfo("array", "selkey", "1234567890");
            setImInfo("array", "imkeys", "abcdefghijklmnopqrstuvwxyz./;,?*#1#2#3#4#5#6#7#8#9#0");
            setImInfo("array", "imkeynames", "1-|5?|3?|3-|3?|4-|5-|6-|8?|7-|8-|9-|7?|6?|9?|0?|1?|4?|2-|5?|7?|4?|2?|2?|6?|1?|9?|0?|0-|8?|？|＊|1|2|3|4|5|6|7|8|9|0");
        }else{
            if (!selkey.equals("")) setImInfo(imtype, "selkey", selkey);
            if (!endkey.equals("")) setImInfo(imtype, "endkey", endkey);
            if (!spacestyle.equals("")) setImInfo(imtype, "spacestyle", spacestyle);
            if (!imkeys.equals("")) setImInfo(imtype, "imkeys", imkeys);
            if (!imkeynames.equals("")) setImInfo(imtype, "imkeynames", imkeynames);
        }

        KeyboardObj kobj = getKeyboardObj(imtype);
        if( imtype.equals("phonetic")){
            String selectedPhoneticKeyboardType =
            mLIMEPref.getParameterString("phonetic_keyboard_type", "standard");
            if(selectedPhoneticKeyboardType.equals("standard")){
                kobj = 	getKeyboardObj("phonetic");
            }else if(selectedPhoneticKeyboardType.equals("eten")){
                kobj = 	getKeyboardObj("phoneticet41");
            }else if(selectedPhoneticKeyboardType.equals("eten26")){
                   if(mLIMEPref.getParameterBoolean("number_row_in_english", false)){
                        kobj = 	getKeyboardObj("limenum");
                    }else{
                        kobj = 	getKeyboardObj("lime");
                    }
            }else if(selectedPhoneticKeyboardType.equals("eten26_symbol")){
                kobj = 	getKeyboardObj("et26");
            }else if(selectedPhoneticKeyboardType.equals("hsu")){ //Jeremy '12,7,6 Add HSU english keyboard support
                if(mLIMEPref.getParameterBoolean("number_row_in_english", false)){
                    kobj = 	getKeyboardObj("limenum");
                }else{
                    kobj = 	getKeyboardObj("lime");
                }
            }else if(selectedPhoneticKeyboardType.equals("hsu_symbol")){
                kobj = 	getKeyboardObj("hsu");
            }
        }else if( imtype.equals("dayi")){
            kobj = getKeyboardObj("dayisym");
        }else if( imtype.equals("cj5")){
            kobj = getKeyboardObj("cj");
        }else if( imtype.equals("ecj")){
            kobj = getKeyboardObj("cj");
        }else if( imtype.equals("array")){
            kobj = getKeyboardObj("arraynum");
        }else if( imtype.equals("array10")){
            kobj = getKeyboardObj("phonenum");
        }else if( imtype.equals("wb")){
            kobj = getKeyboardObj("wb");
        }else if( imtype.equals("hs")){
            kobj = getKeyboardObj("hs");
        }else if( kobj == null){	//Jeremy '12,5,21 chose english with number keyboard if the optione is on for default keyboard.
            if(mLIMEPref.getParameterBoolean("number_row_in_english", false)){
                kobj = 	getKeyboardObj("limenum");
            }else{
                kobj = 	getKeyboardObj("lime");
            }
        }
        setIMKeyboard(imtype, kobj.getDescription(), kobj.getCode());

        mLIMEPref.setResetCacheFlag(true);

        handler.cancelProgress();
        handler.initialImButtons();
    }

    public int migrateDb(File tempfile, String imtype){

        List<Word> results = null;

        String sourcedbfile = Lime.DATABASE_FOLDER_EXTERNAL + imtype;

        handler.updateProgress(activity.getResources().getString(R.string.setup_load_migrate_load));
        DBServer.decompressFile(tempfile, Lime.DATABASE_FOLDER_EXTERNAL, imtype, true);
        SQLiteDatabase sourcedb = SQLiteDatabase.openDatabase(sourcedbfile, null, SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        results = loadWord(sourcedb, imtype);
        sourcedb.close();

        try {

            // Remove Imtype and related info
            try {
                dbsrv.resetMapping(imtype);
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            int total = results.size();
            int c = 0;

            datasource.open();
            datasource.beginTransaction();

            for(Word w: results){
                c++;
                String insert = Word.getInsertQuery(imtype, w);
                datasource.add(insert);
                if(c % 100 == 0){
                    int p = (int)(c * 100 / total);
                    handler.updateProgress(activity.getResources().getString(R.string.setup_load_migrate_import) + " " + p + "%");
                }
            }
            datasource.endTransaction();
            datasource.close();
            return results.size();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public List<Word> loadWord(SQLiteDatabase sourcedb, String code) {
        List<Word> result = new ArrayList<Word>();
        if(sourcedb != null && sourcedb.isOpen()){
            Cursor cursor = null;
            String order = Lime.DB_COLUMN_CODE + " ASC";

            cursor = sourcedb.query(code, null, null, null, null, null, order);

            cursor.moveToFirst();
            while(!cursor.isAfterLast()){
                Word r = Word.get(cursor);
                result.add(r);
                cursor.moveToNext();
            }
            cursor.close();
        }
        return result;
    }

    public synchronized void setImInfo(String im, String field, String value) {

        ContentValues cv = new ContentValues();
        cv.put("code", im);
        cv.put("title", field);
        cv.put("desc", value);

        removeImInfo(im, field);

        try {
            datasource.open();
            datasource.insert("im", cv);
            datasource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void setIMKeyboardOnDB(String im, String value, String keyboard) {

        ContentValues cv = new ContentValues();
        cv.put("code", im);
        cv.put("title", "keyboard");
        cv.put("desc", value);
        cv.put("keyboard", keyboard);

        removeImInfoOnDB(im, "keyboard");

        try {
            datasource.open();
            datasource.insert("im", cv);
            datasource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void removeImInfoOnDB(String im, String field) {
        String removeString = "DELETE FROM im WHERE code='"+im+"' AND title='"+field+"'";
        try {
            datasource.open();
            datasource.remove(removeString);
            datasource.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public synchronized void setIMKeyboard(String im, String value,  String keyboard) {
        try{
            setIMKeyboardOnDB(im, value, keyboard);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }


    public synchronized void removeImInfo(String im, String field) {
        try{
            removeImInfoOnDB(im, field);
        }catch(Exception e){e.printStackTrace();}
    }


    public KeyboardObj getKeyboardObj(String keyboard){

        if( keyboard == null || keyboard.equals(""))
            return null;
        KeyboardObj kobj=null;

        if(!keyboard.equals("wb") && !keyboard.equals("hs")){
            try {
                //SQLiteDatabase db = this.getSqliteDb(true);


                datasource.open();
                Cursor cursor = datasource.query("keyboard", "code" +" = '"+keyboard+"'");
                if (cursor.moveToFirst()) {
                    kobj = new KeyboardObj();
                    kobj.setCode(cursor.getString(cursor.getColumnIndex("code")));
                    kobj.setName(cursor.getString(cursor.getColumnIndex("name")));
                    kobj.setDescription(cursor.getString(cursor.getColumnIndex("desc")));
                    kobj.setType(cursor.getString(cursor.getColumnIndex("type")));
                    kobj.setImage(cursor.getString(cursor.getColumnIndex("image")));
                    kobj.setImkb(cursor.getString(cursor.getColumnIndex("imkb")));
                    kobj.setImshiftkb(cursor.getString(cursor.getColumnIndex("imshiftkb")));
                    kobj.setEngkb(cursor.getString(cursor.getColumnIndex("engkb")));
                    kobj.setEngshiftkb(cursor.getString(cursor.getColumnIndex("engshiftkb")));
                    kobj.setSymbolkb(cursor.getString(cursor.getColumnIndex("symbolkb")));
                    kobj.setSymbolshiftkb(cursor.getString(cursor.getColumnIndex("symbolshiftkb")));
                    kobj.setDefaultkb(cursor.getString(cursor.getColumnIndex("defaultkb")));
                    kobj.setDefaultshiftkb(cursor.getString(cursor.getColumnIndex("defaultshiftkb")));
                    kobj.setExtendedkb(cursor.getString(cursor.getColumnIndex("extendedkb")));
                    kobj.setExtendedshiftkb(cursor.getString(cursor.getColumnIndex("extendedshiftkb")));
                }
                cursor.close();
                datasource.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }else if(keyboard.equals("wb")){
            kobj = new KeyboardObj();
            kobj.setCode("wb");
            kobj.setName("筆順五碼");
            kobj.setDescription("筆順五碼輸入法鍵盤");
            kobj.setType("phone");
            kobj.setImage("wb_keyboard_preview");
            kobj.setImkb("lime_wb");
            kobj.setImshiftkb("lime_wb");
            kobj.setEngkb("lime_abc");
            kobj.setEngshiftkb("lime_abc_shift");
            kobj.setSymbolkb("symbols");
            kobj.setSymbolshiftkb("symbols_shift");
        }else if(keyboard.equals("hs")){
            kobj = new KeyboardObj();
            kobj.setCode("hs");
            kobj.setName("華象直覺");
            kobj.setDescription("華象直覺輸入法鍵盤");
            kobj.setType("phone");
            kobj.setImage("hs_keyboard_preview");
            kobj.setImkb("lime_hs");
            kobj.setImshiftkb("lime_hs_shift");
            kobj.setEngkb("lime_abc");
            kobj.setEngshiftkb("lime_abc_shift");
            kobj.setSymbolkb("symbols");
            kobj.setSymbolshiftkb("symbols_shift");
        }

        return kobj;
    }


    /*
	 * Download Remote File
	 */
    public File downloadRemoteFile(String url){

        try {
            URL downloadUrl = new URL(url);
            URLConnection conn = downloadUrl.openConnection();
            conn.connect();
            InputStream is = conn.getInputStream();
            //long remoteFileSize = conn.getContentLength();
            //long downloadedSize = 0;

            if(is == null){
                throw new RuntimeException("stream is null");
            }

            File downloadFolder = new File(Lime.DATABASE_FOLDER_EXTERNAL);
            downloadFolder.mkdirs();

            File downloadedFile = new File(downloadFolder.getAbsolutePath() + File.separator + Lime.DATABASE_IM_TEMP);
            if(downloadedFile.exists()){
                downloadedFile.delete();
            }

            FileOutputStream fos = null;
            fos = new FileOutputStream(downloadedFile);

            byte buf[] = new byte[4096];
            do{
                int numread = is.read(buf);
                if(numread <=0){break;}
                fos.write(buf, 0, numread);
            }while(true);

            is.close();

            return downloadedFile;

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e){
            e.printStackTrace();
        } catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }


}
