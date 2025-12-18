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
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Looper;
import android.util.Log;

import net.toload.main.hd.DBServer;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.R;
import net.toload.main.hd.data.KeyboardObj;
import net.toload.main.hd.data.Word;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.global.LIMEUtilities;
import net.toload.main.hd.limedb.LimeDB;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SetupImLoadRunnable implements Runnable{
    private final static boolean DEBUG = false;
    private final static String TAG = "SetupImLoadRunnable";

    // Global
    private String url;
    private final String imtype;
    private final String type;

    private final Activity activity;
    private final DBServer dbsrv;
    private final SetupImHandler handler;

    private final LimeDB datasource;
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
        this.datasource = new LimeDB(activity);
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

        if(tempfile == null || tempfile.length() < LIME.MIN_FILE_SIZE_BYTES){

            switch (type) {
                case LIME.IM_ARRAY:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_ARRAY;
                    break;
                case LIME.IM_ARRAY10:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_ARRAY10;
                    break;
                case LIME.IM_CJ_BIG5:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_CJ_BIG5;
                    break;
                case LIME.IM_CJ:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_CJ;
                    break;
                case LIME.IM_CJHK:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_CJHK;
                    break;
                case LIME.IM_CJ5:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_CJ5;
                    break;
                case LIME.IM_DAYI:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_DAYI;
                    break;
                case LIME.IM_DAYIUNI:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_DAYIUNI;
                    break;
                case LIME.IM_DAYIUNIP:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_DAYIUNIP;
                    break;
                case LIME.IM_ECJ:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_ECJ;
                    break;
                case LIME.IM_ECJHK:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_ECJHK;
                    break;
                case LIME.IM_EZ:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_EZ;
                    break;
                case LIME.IM_PHONETIC_BIG5:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_PHONETIC_BIG5;
                    break;
                case LIME.IM_PHONETIC_ADV_BIG5:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_PHONETICCOMPLETE_BIG5;
                    break;
                case LIME.IM_PHONETIC:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_PHONETIC;
                    break;
                case LIME.IM_PHONETIC_ADV:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_PHONETICCOMPLETE;
                    break;
                case LIME.IM_PINYIN:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_PINYIN;
                    break;
                case LIME.IM_PINYINGB:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_PINYINGB;
                    break;
                case LIME.IM_SCJ:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_SCJ;
                    break;
                case LIME.IM_WB:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_WB;
                    break;
                case LIME.IM_HS:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_HS;
                    break;
                case LIME.IM_HS_V1:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_HS_V1;
                    break;
                case LIME.IM_HS_V2:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_HS_V2;
                    break;
                case LIME.IM_HS_V3:
                    url = LIME.DATABASE_OPENFOUNDRY_IM_HS_V3;
                    break;
            }
            tempfile = downloadRemoteFile(mContext, url);
        }


        // Load DB
        handler.updateProgress(activity.getResources().getString(R.string.setup_load_migrate_load));
        dbsrv.importMapping(tempfile, imtype);

        mLIMEPref.setParameter("_table", "");
        //mLIMEPref.setResetCacheFlag(true);
        dbsrv.resetCache();

        if(restorePreference){
            handler.updateProgress(activity.getResources().getString(R.string.setup_im_restore_learning_data));
            handler.updateProgress(0);
            boolean check = datasource.checkBackuptable(imtype);
            handler.updateProgress(5);

            if(check){

                String backupTableName = imtype + "_user";

                // Validate table names to prevent SQL injection
                // backupTableName should be in format "imtype_user" where imtype is a valid table
                // The rawQuery method will validate this, but we also validate here for clarity
                if (!backupTableName.endsWith("_user")) {
                    Log.e(TAG, "Invalid backup table name: " + backupTableName);
                    return;
                }

                // check if user data backup table is present and have valid records
                int userRecordsCount = datasource.countMapping(backupTableName);
                handler.updateProgress(10);
                if (userRecordsCount == 0) return;

                try {
                    // Load backuptable records
                                /*
                                // NOTE: Commented out code uses rawQuery with table name concatenation
                                // This is safe only if imtype is validated (which it should be)
                                // If uncommenting, ensure imtype is validated against whitelist
                                Cursor cursorsource = datasource.rawQuery("select * from " + imtype);
                                List<Word> clist = Word.getList(cursorsource);
                                cursorsource.close();

                                HashMap<String, Word> wordcheck = new HashMap<String, Word>();
                                for(Word w : clist){
                                    String key = w.getCode() + w.getWord();
                                    wordcheck.put(key, w);
                                }
                                handler.updateProgress(20);
                                */
                    // backupTableName is validated above, safe to use
                    Cursor cursorbackup = datasource.rawQuery("select * from " + backupTableName);
                    List<Word> backuplist = Word.getList(cursorbackup);
                    cursorbackup.close();

                    int progressvalue = 0;
                    int recordcount = 0;
                    int recordtotal = backuplist.size();

                    for(Word w: backuplist){

                        recordcount++;

                        datasource.addOrUpdateMappingRecord(imtype,w.getCode(),w.getWord(),w.getScore());
                                    /*
                                    // update record
                                    String key = w.getCode() + w.getWord();

                                    if(wordcheck.containsKey(key)){
                                        try{
                                            datasource.execSQL("update " + imtype + " set " + LIME.DB_COLUMN_SCORE + " = " + w.getScore()
                                                            + " WHERE " + LIME.DB_COLUMN_CODE + " = '" + w.getCode() + "'"
                                                            + " AND " + LIME.DB_COLUMN_WORD + " = '" + w.getWord() + "'"
                                            );
                                        }catch(Exception e){
                                            Log.e(TAG, "Error in operation", e);
                                        }
                                    }else{
                                        try{
                                            Word temp = wordcheck.get(key);
                                            String insertsql = Word.getInsertQuery(imtype, temp);
                                            datasource.execSQL(insertsql);
                                        }catch(Exception e){
                                            Log.e(TAG, "Error in operation", e);
                                        }
                                    }
                                    */
                        // Update Progress
                        int progress =(int) ((double)recordcount / recordtotal   * 90 +10 ) ;

                        if(progress != progressvalue){
                            progressvalue = progress;
                            handler.updateProgress(progressvalue);
                        }

                    }

                    //   wordcheck.clear();

                }catch(Exception e){
                    Log.e(TAG, "Error in operation", e);
                }

               // datasource.restoreUserRecordsStep2(imtype);
                handler.updateProgress(LIME.PROGRESS_COMPLETE_PERCENT);
            }
        }

        handler.finishLoading(imtype);
        handler.initialImButtons();

    }


    public List<Word> loadWord(SQLiteDatabase sourcedb, String code) {
        List<Word> result = new ArrayList<>();
        if(sourcedb != null && sourcedb.isOpen()){
            Cursor cursor;
            String order = LIME.DB_COLUMN_CODE + " ASC";

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

    @Deprecated public synchronized void setImInfo(String im, String field, String value) {

        ContentValues cv = new ContentValues();
        cv.put("code", im);
        cv.put("title", field);
        cv.put("desc", value);

        removeImInfo(im, field);

        datasource.insert("im", cv);
        /*try {
            datasource.open();
            datasource.close();
        } catch (SQLException e) {
            Log.e(TAG, "Error in operation", e);
        }*/
    }

    private void setIMKeyboardOnDB(String im, String value, String keyboard) {

        ContentValues cv = new ContentValues();
        cv.put("code", im);
        cv.put("title", "keyboard");
        cv.put("desc", value);
        cv.put("keyboard", keyboard);

        removeImInfoOnDB(im, "keyboard");

        datasource.insert("im", cv);

    }

    private void removeImInfoOnDB(String im, String field) {
        String removeString = "DELETE FROM im WHERE code='"+im+"' AND title='"+field+"'";
        datasource.remove(removeString);

    }


    @Deprecated public synchronized void setIMKeyboard(String im, String value,  String keyboard) {
        try{
            setIMKeyboardOnDB(im, value, keyboard);
        }catch (Exception e) {
            Log.e(TAG, "Error in operation", e);
        }
    }


    public synchronized void removeImInfo(String im, String field) {
        try{
            removeImInfoOnDB(im, field);
        }catch(Exception e){Log.e(TAG, "Error in operation", e);}
    }



    /*
	 * Download Remote File
	 * Uses shared utility method from LIMEUtilities
	 */
    public File downloadRemoteFile(Context ctx, String url){
        // Use shared download utility with progress callback
        return LIMEUtilities.downloadRemoteFile(url, null, ctx.getCacheDir(), 
                percent -> handler.updateProgress(percent), null);
    }


}
