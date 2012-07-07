/*    
**    Copyright 2010, The LimeIME Open Source Project
** 
**    Project Url: http://code.google.com/p/limeime/
**                 http://android.toload.net/
**
**    This program is free software: you can redistribute it and/or modify
**    it under the terms of the GNU General Public License as published by
**    the Free Software Foundation, either version 3 of the License, or
**    (at your option) any later version.

**    This program is distributed in the hope that it will be useful,
**    but WITHOUT ANY WARRANTY; without even the implied warranty of
**    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
**    GNU General Public License for more details.

**    You should have received a copy of the GNU General Public License
**    along with this program.  If not, see <http://www.gnu.org/licenses/>.
**
*/


package net.toload.main.hd.limedb;


import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;



/**
 * @author Art Hung
 */
public class LimeHanConverter extends SQLiteOpenHelper {

	//private static boolean DEBUG = false;
	
	private final static String DATABASE_NAME = "hanconvertv2.db";
	private final static int DATABASE_VERSION = 59;


	private static final String FIELD_CODE = "code";
	private static final String FIELD_WORD = "word";
	private static final String FIELD_SCORE = "score";

	

	//private Context ctx;
	
	public LimeHanConverter(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		//this.ctx = context;
	}

	/**
	 * Create SQLite Database and create related tables
	 */
	@Override
	public void onCreate(SQLiteDatabase db) {

		// ignore error when create tables
		try{
		

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Upgrade current database
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		
		try{
		
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public int getBaseScore(String input){
		int score = 0;
		if(input!=null && !input.equals("")) {
			Cursor cursor = null;
			
			try {
				SQLiteDatabase db = this.getReadableDatabase();
				
				cursor = db.query("TCSC", null, FIELD_CODE + " = '" + input + "' "
							, null, null, null, null, null);		
					if (cursor.moveToFirst()) {
						int scoreColumn = cursor.getColumnIndex(FIELD_SCORE);
						score = cursor.getInt(scoreColumn);
					}else if(input.length()>1)
						score = 1;  //phase has default score = 1
					
				if (cursor != null) {
					cursor.deactivate();
					cursor.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		return score;
	}
	
	public String convert(String input, Integer hanConvertOption){
		String output = new String(input);
		//Log.i("LimeHanConverter.convert()","hanConvertOption:"+hanConvertOption);
		if(input!=null && !input.equals("") && hanConvertOption != 0){
			String tablename = new String("");
			Cursor cursor = null;
			if(hanConvertOption == 1 ) { //TC to SC
				tablename = "TCSC";
			}else if(hanConvertOption == 2) {// SC to TC
				tablename = "SCTC";
			}
			try {
				SQLiteDatabase db = this.getReadableDatabase();
				
				output = "";
				for(int i=0;i<input.length();i++){
					
					cursor = db.query(tablename, null, FIELD_CODE + " = '" + input.substring(i,i+1) + "' "
							, null, null, null, null, null);
				
					if (cursor.moveToFirst()) {
						//int codeColumn = cursor.getColumnIndex(FIELD_CODE);
						int wordColumn = cursor.getColumnIndex(FIELD_WORD);
						String word = cursor.getString(wordColumn);
						output += word; 
							
					}else
						output += input.substring(i,i+1);
						
				}
					
				if (cursor != null) {
					cursor.deactivate();
					cursor.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
					
		}
		return output;
	}
}
