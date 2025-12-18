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


package net.toload.main.hd.limedb;



import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;




/**
 * @author Art Hung
 */
public class LimeHanConverter extends SQLiteOpenHelper {

	private final static boolean DEBUG = false;
	private final static String TAG = "LimeHanConverter";
	
	
	private final static String DATABASE_NAME = "hanconvertv2.db";
	private final static int DATABASE_VERSION = 59;


	private static final String FIELD_CODE = "code";
	private static final String FIELD_WORD = "word";
	private static final String FIELD_SCORE = "score";

	
	public LimeHanConverter(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}
	/**
	 * Count total amount of specific table


	public int countMapping(String table) {
		if(DEBUG)
			Log.i(TAG,"countMapping() on table:" + table);

		try {
			SQLiteDatabase db = this.getReadableDatabase();
			
			Cursor cursor = db.rawQuery("SELECT * FROM " + table, null);
			if(cursor ==null) return 0; 
			int total = cursor.getCount();
			cursor.close();
			if(DEBUG)
					Log.i(TAG, "countMapping" + "Table," + table + ": " + total);
			return total;
		} catch (Exception e) {
			Log.e(TAG, "Error in Han conversion", e);
		}
		return 0;
	}
	/**
	 * Create SQLite Database and create related tables
	 */
	@Override
	public void onCreate(SQLiteDatabase db) {
		// ignore error when create tables
	}

	/**
	 * Upgrade current database
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

		
	}
	
	public int getBaseScore(String input) {
        if (DEBUG)
            Log.i(TAG, "getBaseScore()");
        int score = 0;
        if (input != null && !input.isEmpty()) {
            Cursor cursor = null;

            try {
                SQLiteDatabase db = this.getReadableDatabase();

                // Use parameterized query to prevent SQL injection
                cursor = db.query("TCSC", null, FIELD_CODE + " = ?"
                        , new String[]{input}, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int scoreColumn = cursor.getColumnIndex(FIELD_SCORE);
                    score = cursor.getInt(scoreColumn);
                } else if (input.length() > 1)
                    score = 1;  //phase has default score = 1

            } catch (Exception e) {
                Log.e(TAG, "Error in Han conversion", e);
            } finally {
                // Ensure cursor is closed even if exception occurs
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return score;
    }
	
	public String convert(String input, Integer hanConvertOption){
		StringBuilder output= new StringBuilder(input);
		//Log.i("LimeHanConverter.convert()","hanConvertOption:"+hanConvertOption);
		if(!input.isEmpty() && hanConvertOption != 0){
			String tablename = "";
			Cursor cursor = null;
			if(hanConvertOption == 1 ) { //TC to SC
				tablename = "TCSC";
			}else if(hanConvertOption == 2) {// SC to TC
				tablename = "SCTC";
			}
			try {
				SQLiteDatabase db = this.getReadableDatabase();
				
				output = new StringBuilder();
				for(int i=0;i<input.length();i++){
					// Validate table name to prevent SQL injection
					if (!tablename.equals("TCSC") && !tablename.equals("SCTC")) {
						Log.e(TAG, "convert(): Invalid table name: " + tablename);
						break;
					}
					// Use parameterized query to prevent SQL injection
					String charStr = String.valueOf(input.charAt(i));
					cursor = db.query(tablename, null, FIELD_CODE + " = ?"
							, new String[]{charStr}, null, null, null, null);
				
					if (cursor != null && cursor.moveToFirst()) {
						//int codeColumn = cursor.getColumnIndex(FIELD_CODE);
						int wordColumn = cursor.getColumnIndex(FIELD_WORD);
						String word = cursor.getString(wordColumn);
						output.append(word);
					} else {
						output.append(input.charAt(i));
					}
					
					// Close cursor after each iteration to prevent resource leak
					if (cursor != null) {
						cursor.close();
						cursor = null;
					}
				}
            } catch (Exception e) {
				Log.e(TAG, "Error in Han conversion", e);
			} finally {
				// Ensure cursor is closed even if exception occurs
				if (cursor != null) {
					cursor.close();
				}
			}
					
		}
		return output.toString();
	}
}
