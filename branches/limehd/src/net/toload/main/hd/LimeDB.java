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
 */

package net.toload.main.hd;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

/**
 * @author Art Hung
 */
public class LimeDB extends SQLiteOpenHelper {

	private static boolean DEBUG = false;

	private final static int DATABASE_VERSION = 66;
	private final static int DATABASE_RELATED_SIZE = 50;

	public final static String FIELD_id = "_id";
	public final static String FIELD_CODE = "code";
	public final static String FIELD_WORD = "word";
	public final static String FIELD_RELATED = "related";
	public final static String FIELD_SCORE = "score";
	public final static String FIELD_CODE3R = "code3r";

	public final static String FIELD_DIC_id = "_id";
	public final static String FIELD_DIC_pcode = "pcode";
	public final static String FIELD_DIC_pword = "pword";
	public final static String FIELD_DIC_ccode = "ccode";
	public final static String FIELD_DIC_cword = "cword";
	public final static String FIELD_DIC_score = "score";
	public final static String FIELD_DIC_is = "isDictionary";

	// for keyToChar
	public final static String BPMF_KEY = "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/-";
	public final static String BPMF_CHAR = "ㄅㄆㄇㄈㄉㄊㄋㄌˇㄍㄎㄏˋㄐㄑㄒㄓㄔㄕㄖˊㄗㄘㄙ˙ㄧㄨㄩㄚㄛㄜㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦ";

	public final static String CJ_KEY = "qwertyuiopasdfghjklzxcvbnm";
	public final static String CJ_CHAR = "手田水口廿卜山戈人心日尸木火土竹十大中重難金女月弓一";


	public String DELIMITER = "";

	private File filename = null;
	private String tablename = "custom";

	private int count = 0;
	private int ncount = 0;
	private boolean finish = false;
	private boolean relatedfinish = false;

	private LIMEPreferenceManager mLIMEPref;
	private Map<String, String> code3rMap = new HashMap<String, String>();

	private Context ctx;

	// Db loading thread.
	private Thread thread = null;

	public boolean isFinish() {
		return this.finish;
	}

	public void setFinish(boolean value) {
		this.finish = value;
	}
	
	/*
	 * For DBService to set the filename to be load to database
	 */
	public void setFilename(File filename) {
		this.filename = filename;
	}

	/*
	 * For LIMEService to setup tablename for further word mapping query
	 */
	public void setTablename(String tablename) {
		this.tablename = tablename;
		if (DEBUG) {
			Log.i("setTablename", "tablename:" + tablename + " this.tablename:"
					+ this.tablename);
		}
	}
	
	public String getTablename(){
		return this.tablename;
	}

	/*
	 * Initialize LIME database, Context and LIMEPreferenceManager
	 */
	public LimeDB(Context context) {
		
		super(context, LIME.DATABASE_NAME, null, DATABASE_VERSION);
		this.ctx = context;
		for(int i=0; i< LIME.THREE_ROW_KEY.length(); i++){
			String key = LIME.THREE_ROW_KEY.substring(i,i+1);
			String value = LIME.THREE_ROW_KEY_REMAP.substring(i,i+1);
			code3rMap.put(key, value);
			code3rMap.put(value, value);
		}
		
		mLIMEPref = new LIMEPreferenceManager(ctx.getApplicationContext());
	/*	String dbtarget = mLIMEPref.getParameterString("dbtarget");
		if(dbtarget.equals("device")){
			super(context, LIME.DATABASE_NAME, null, DATABASE_VERSION);
		}else{
			super(context, LIME.DATABASE_NAME, null, DATABASE_VERSION);
		}*/


	}

	/**
	 * Create SQLite Database and create related tables
	 */
	@Override
	public void onCreate(SQLiteDatabase db) {
		// Start from 3.0v no need to create internal database
	}

	/*
	 * Update Database Schema
	 * 
	 * @see
	 * android.database.sqlite.SQLit eOpenHelper#onUpgrade(android.database.sqlite
	 * .SQLiteDatabase, int, int)
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// Start from 3.0v no need to create internal database
	}
	
	public SQLiteDatabase getSqliteDb(boolean readonly){
		try{
			SQLiteDatabase db = null;
			String dbtarget = mLIMEPref.getParameterString("dbtarget");
			//Log.i("ART", "Load Database Target : " + dbtarget);
			if(dbtarget.equals("sdcard")){
				String sdcarddb = LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + LIME.DATABASE_NAME;

				if(readonly){
					db = SQLiteDatabase.openDatabase(sdcarddb, null, SQLiteDatabase.OPEN_READONLY);
				}else{
					db = SQLiteDatabase.openDatabase(sdcarddb, null, SQLiteDatabase.OPEN_READWRITE);
				}
			}else{
				String devicedb = LIME.DATABASE_DECOMPRESS_FOLDER + File.separator + LIME.DATABASE_NAME;
				
				if(readonly){
					db = SQLiteDatabase.openDatabase(devicedb, null, SQLiteDatabase.OPEN_READONLY);
					//db = this.getReadableDatabase();
				}else{
					db = SQLiteDatabase.openDatabase(devicedb, null, SQLiteDatabase.OPEN_READWRITE);
					//db = this.getWritableDatabase();
				}
			}
			//Log.i("ART", "Load Database Result : " + db);
			
			return db;
		}catch(Exception e){e.printStackTrace();}
			 
		return null;

	}

	/**
	 * Base on given table name to remove records
	 */
	public void deleteAll(String table) {
		SQLiteDatabase db = this.getSqliteDb(false);
		db.execSQL("DELETE FROM " + table);
		finish = false;
		resetImInfo(table);
		mLIMEPref.setParameter("im_loading", false);
		mLIMEPref.setParameter("im_loading_table", "");
		if(thread != null){
			thread.interrupt();
		}
		db.close();
	}

	/**
	 * Empty Related table records
	 */
	public void deleteUserDictAll() {
		mLIMEPref.setTotalUserdictRecords("0");
		// -------------------------------------------------------------------------
		SQLiteDatabase db = this.getSqliteDb(false);
		db.delete("related", FIELD_DIC_score + " > 0", null);
		db.close();
	}

	/**
	 * Count total amount of specific table
	 * 
	 * @return
	 */
	public int countMapping(String table) {

		try {
			SQLiteDatabase db = this.getSqliteDb(true);
			int total = db.rawQuery("SELECT * FROM " + table, null).getCount();
			db.close();
			//Log.i("countMapping", "Table," + table + ": " + total);
			return total;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0;
	}
	
	public int getCount(){
		return count;
	}

	/**
	 * Count total amount loaded records amount
	 * 
	 * @return
	 */
	public int countUserdic() {

		int total = 0;
		try {
			SQLiteDatabase db = this.getSqliteDb(true);
			total += db.rawQuery(
					"SELECT * FROM related where " + FIELD_DIC_score + " > 0",
					null).getCount();
			db.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return total;
	}

	/**
	 * Insert mapping item into database
	 * 
	 * @param source
	 */
	public void insertList(ArrayList<String> source) {

		this.identifyDelimiter(source);

		SQLiteDatabase db = this.getSqliteDb(false);
		for (String unit : source) {

			try {
				String code = unit.substring(0, unit.indexOf(this.DELIMITER));
				String word = unit.substring(unit.indexOf(this.DELIMITER) + 1);

				if (code == null || code.trim().equals("")) {
					continue;
				} else {
					code = code.toLowerCase();
				}
				if (word == null || word.trim().equals("")) {
					continue;
				}
				if (code.equalsIgnoreCase("@VERSION@")) {
					mLIMEPref.setTableVersion("lime", word.trim());
					continue;
				}

				ContentValues cv = new ContentValues();
				cv.put(FIELD_CODE, code);
				cv.put(FIELD_WORD, word);
				cv.put(FIELD_SCORE, 0);

				db.insert("mapping", null, cv);
				count++;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		db.close();
	}

	/**
	 * Create dictionary database
	 * 
	 * @param srclist
	 */
	public void addDictionary(List<Mapping> srclist) {

		//Log.i("addDictionary:", "Etnering addDictionary:"+srclist);
		if(DEBUG){
			Log.i("addDictionary:", "Etnering addDictionary");
		}
		
		int dictotal = 0;
		try {
			SharedPreferences settings = ctx.getSharedPreferences(LIME.TOTAL_USERDICT_RECORD, 0);
			String recordString = settings.getString(LIME.TOTAL_USERDICT_RECORD, "0");
			dictotal = Integer.parseInt(recordString);
		} catch (Exception e) {}
		
		
		// Check if build related word enable. 
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		if( !sp.getBoolean(LIME.CANDIDATE_SUGGESTION, false) ){
			if(DEBUG){
				Log.i("addDictionary:", "CANDIDATE_SUGGESTION:false returning...");
			}
			return;
		}
		
		//Log.i("ART", "srclist.size():" + srclist.size());
		
		if (srclist != null && srclist.size() > 0) {

			SQLiteDatabase db = this.getSqliteDb(false);
			
			try {
				for (int i = 0; i < srclist.size(); i++) {

					Mapping unit = srclist.get(i);
				    if(unit == null){continue;}
				    
					if(i+1 <srclist.size()){
						Mapping unit2 = srclist.get((i + 1));
					    if(unit2 == null){continue;}
						
						if (unit != null 
							&& unit.getWord() != null && !unit.getWord().equals("")
							&& unit2 != null
							&& unit2.getWord() != null && !unit2.getWord().equals("")) {

							//Log.i("ART", "WORD + WORD2:" + unit.getWord() + " - " + unit2.getWord());
							Mapping munit = this.isExists(unit.getWord(),unit2.getWord());
							//Log.i("ART", "munit:" + munit.getWord() + " - " + munit.getPword());
							if (munit == null) {
								try {
									ContentValues cv = new ContentValues();
									cv.put(FIELD_DIC_pword, unit.getWord());
									cv.put(FIELD_DIC_cword, unit2.getWord());
									cv.put(FIELD_DIC_score, 1);
									db.insert("related", null, cv);
									dictotal++;
								} catch (Exception e) {
									e.printStackTrace();
								}
							}else{//the item exist in preload related database. 
									ContentValues cv = new ContentValues();
							  					  cv.put(FIELD_SCORE, munit.getScore()+1);
							  		db.update("related", cv, FIELD_id + " = " + munit.getId(), null);
									//Log.i("ART","Add Score for Dictionary : " + munit.getId() + munit.getCode() + munit.getScore());
									
								}
							}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				SharedPreferences sp1 = ctx.getSharedPreferences(LIME.TOTAL_USERDICT_RECORD, 0);
				sp1.edit().putString(LIME.TOTAL_USERDICT_RECORD, String.valueOf(dictotal)).commit();
				if(DEBUG){
					Log.i("addDictionary:", "update userdict total records:" + dictotal);
				}
			}
			
			db.close();
		}
	}
	
	/**
	 * Add score to the mapping item
	 * 
	 * @param srcunit
	 */
	public void addScore(Mapping srcunit) {
		try {
			if (srcunit != null && srcunit.getId() != null &&
					srcunit.getWord() != null  &&
					!srcunit.getWord().trim().equals("") ) {
				//Log.i("ART","LimeDB addScore:"+srcunit.getCode());

				if(srcunit.isDictionary()){
					ContentValues cv = new ContentValues();
					cv.put(FIELD_SCORE, srcunit.getScore() + 1);
	
					SQLiteDatabase db = this.getSqliteDb(false);
					db.update("related", cv, FIELD_id + " = " + srcunit.getId(), null);
					db.close();
				}else{
					ContentValues cv = new ContentValues();
					cv.put(FIELD_SCORE, srcunit.getScore() + 1);
	
					SQLiteDatabase db = this.getSqliteDb(false);
					db.update(tablename, cv, FIELD_id + " = " + srcunit.getId(), null);
					db.close();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	// Add by jeremy '10, 4, 1. For reverse lookup
	/**
	 * Reverse lookup on keyword.
	 * 
	 * @param keyword
	 * @return
	 */
	public String getRMapping(String keyword) {

		//Log.i("ART", "run get rmapping:"+ keyword);
		String Rtable = mLIMEPref.getRerverseLookupTable(tablename);

		if (Rtable.equals("none")) {
			return null;
		}

		String result = new String("");
		try {

			if (keyword != null && !keyword.trim().equals("")) {
				Cursor cursor = null;
				SQLiteDatabase db = this.getSqliteDb(true);
				cursor = db.query(Rtable, null, FIELD_WORD + " = '" + keyword +"'", null, null,
						null, null, null);
				if (DEBUG) {
					Log.i("getRmapping", "tablename:" + Rtable + "  keyworad:"
							+ keyword + "  cursor.getCount:"
							+ cursor.getCount());
				}

				if (cursor.moveToFirst()) {
					int codeColumn = cursor.getColumnIndex(FIELD_CODE);
					int wordColumn = cursor.getColumnIndex(FIELD_WORD);
					result = cursor.getString(wordColumn) + "="
							+ keyToChar(cursor.getString(codeColumn), Rtable);
					if (DEBUG) {
						Log.i("getRmapping", "Code:"
								+ cursor.getString(codeColumn));
					}

					while (cursor.moveToNext()) {
						result = result
								+ "; "
								+ keyToChar(cursor.getString(codeColumn),
										Rtable);
						if (DEBUG) {
							Log.i("getRmapping", "Code:"
									+ cursor.getString(codeColumn));
						}
					}
				}

				if (cursor != null) {
					cursor.deactivate();
					cursor.close();
				}
				db.close();
			}
		} catch (Exception e) {
		}

		if (DEBUG) {
			Log.i("getRmapping", "Result:" + result);
		}

		return result;
	}

	public String keyToChar(String code, String Rtable) {
		String result = new String("");
		if (Rtable.equals("cj")) {
			int i, j;
			for (i = 0; i < code.length(); i++) {
				for (j = 0; j < CJ_KEY.length(); j++) {
					if (code.substring(i, i + 1).equals(
							CJ_KEY.substring(j, j + 1))) {
						result = result + CJ_CHAR.substring(j, j + 1);
						break;
					}
				}

			}
		}else if (Rtable.equals("scj")) {
			int i, j;
			for (i = 0; i < code.length(); i++) {
				for (j = 0; j < CJ_KEY.length(); j++) {
					if (code.substring(i, i + 1).equals(
							CJ_KEY.substring(j, j + 1))) {
						result = result + CJ_CHAR.substring(j, j + 1);
						break;
					}
				}

			}
		} else if (Rtable.equals("dayi")) {
			result = code;
		} else if (Rtable.equals("ez")) {
			result = code;
		} else if (Rtable.equals("phonetic")) {
			int i, j;
			for (i = 0; i < code.length(); i++) {
				for (j = 0; j < BPMF_KEY.length(); j++) {
					if (code.substring(i, i + 1).equals(
							BPMF_KEY.substring(j, j + 1))) {
						result = result + BPMF_CHAR.substring(j, j + 1);
						break;
					}
				}
			}
		} else {
			result = code;
		}
		return result;
	}

	/*
	 * Retrieve matched records
	 */
	public List<Mapping> getMappingSimiliar(String code) {

		List<Mapping> result = new LinkedList<Mapping>();
		
		if(mLIMEPref.getSimilarCodeCandidates() > 0){
			HashSet<String> wordlist = new HashSet<String>();
	
			if (code != null && !code.trim().equals("")) {
	
				code = code.toLowerCase();
				Cursor cursor = null;
				String sql = null;
	
				SQLiteDatabase db = this.getSqliteDb(true);
	
				int ssize = mLIMEPref.getSimilarCodeCandidates();
				boolean sort = mLIMEPref.getSortSuggestions();
							
				// Process the escape characters of query
				if(code != null){
					code = code.replaceAll("'", "''");
				}
				
				try {
					// When Code3r mode is disable
					if(sort){
						cursor = db.query(tablename, null, FIELD_CODE + " LIKE '"
								+ code + "%' ", null, null, null, FIELD_SCORE +" DESC LIMIT " + ssize, null);
					}else{
						cursor = db.query(tablename, null, FIELD_CODE + " LIKE '"
								+ code + "%' LIMIT " + ssize, null, null, null, null, null);
					}
	
					result = buildQueryResult(cursor);
	
					if (cursor != null) {
						cursor.deactivate();
						cursor.close();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				db.close();
			}
		}
		return result;
	}
	
	/*
	 * Retrieve matched records
	 */
	public List<Mapping> getMapping(String code, boolean softwareKeyboard) {

		//Log.i("ART", "run get (Mapping):"+ code);
		//Log.i("ART","Run MAPPING : " + code);
		// Add by Jeremy '10, 3, 27. Extension on multi table query.

		List<Mapping> result = new LinkedList<Mapping>();
		HashSet<String> wordlist = new HashSet<String>();

		try{
			if (code != null && !code.trim().equals("")) {
	
				code = code.toLowerCase();
				Cursor cursor = null;
				SQLiteDatabase db = this.getSqliteDb(true);
	
				boolean sort = mLIMEPref.getSortSuggestions();
				boolean remap3row = mLIMEPref.getThreerowRemapping();
							
				boolean iscode3r = false;
	
				String code3r = code;
				if(!softwareKeyboard){
					for (int i = 0; i < LIME.THREE_ROW_KEY.length(); i++) {
						code3r = code3r.replace(LIME.THREE_ROW_KEY.substring(i, i + 1), LIME.THREE_ROW_KEY_REMAP.substring(i, i + 1));
					}
					if(!code3r.equalsIgnoreCase(code)){
						iscode3r = true;
						code3r = expendCode3r(code);
					}
				}
				
				// Process the escape characters of query
				if(code != null){
					code = code.replaceAll("'", "''");
				}
				
				//Log.i("ART","==>remap3row:"+remap3row);
				//Log.i("ART","==>code3r:"+code3r + " / "+ iscode3r + " from code:"+code);
	
				try {
	
					// When Code3r mode is enable
					if(remap3row && iscode3r && !softwareKeyboard){
						if(sort){
							cursor = db.query(tablename, null, FIELD_CODE + " = '" + code + "' " + code3r
									, null, null, null, FIELD_SCORE +" DESC", null);
							//Log.i("ART","SORT -> run code3r a:"+tablename + " ->count:" + cursor.getCount() + " " + FIELD_CODE + " = '" + code + "' " + code3r);
						}else{
							cursor = db.query(tablename, null, FIELD_CODE + " = '" + code + "' " + code3r
									, null, null, null, null, null);
							//Log.i("ART","NO SORT -> run code3r a:"+tablename + " ->count:" + cursor.getCount() + " " + FIELD_CODE + " = '" + code + "' " + code3r);
						}
					}else{
						// When Code3r mode is disable
						if(sort){
							cursor = db.query(tablename, null, FIELD_CODE + " = '" + code + "'", null, null, null, FIELD_SCORE +" DESC", null);
						}else{
							cursor = db.query(tablename, null, FIELD_CODE + " = '" + code + "'", null, null, null, null, null);
						}
					}
	
					result = buildQueryResult(cursor);
	
					if (cursor != null) {
						cursor.deactivate();
						cursor.close();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				db.close();
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		return result;
	}
	
	private String expendCode3r(String code){
		//code3r = code3r.replace(LIME.THREE_ROW_KEY.substring(i, i + 1), LIME.THREE_ROW_KEY_REMAP.substring(i, i + 1));

		String result = "";
		if(code.length() == 1){
			result = " OR " + FIELD_CODE + "= '"+code3rMap.get(code)+"'";
		}else if(code.length() == 2){
			result += " OR " + FIELD_CODE + "= '"+code.substring(0,1)+code3rMap.get(code.substring(1,2))+"' OR ";
			result += FIELD_CODE + "= '"+code3rMap.get(code.substring(0,1))+code.substring(1,2)+"' OR ";
			result += FIELD_CODE + "= '"+code3rMap.get(code.substring(0,1))+code3rMap.get(code.substring(1,2))+"'";
		}else if(code.length() == 3){
			result += " OR " + FIELD_CODE + "= '"+code.substring(0,1)+code3rMap.get(code.substring(1,2))+code.substring(2,3)+"' OR ";
			result += FIELD_CODE + "= '"+code.substring(0,1)+code.substring(1,2)+code3rMap.get(code.substring(2,3))+"' OR ";
			result += FIELD_CODE + "= '"+code.substring(0,1)+code3rMap.get(code.substring(1,2))+code3rMap.get(code.substring(2,3))+"' OR ";
			result += FIELD_CODE + "= '"+code3rMap.get(code.substring(0,1))+code3rMap.get(code.substring(1,2))+code3rMap.get(code.substring(2,3))+"' OR ";
			result += FIELD_CODE + "= '"+code3rMap.get(code.substring(0,1))+code.substring(1,2)+code.substring(2,3)+"' OR ";
			result += FIELD_CODE + "= '"+code3rMap.get(code.substring(0,1))+code.substring(1,2)+code3rMap.get(code.substring(2,3))+"' OR ";
			result += FIELD_CODE + "= '"+code3rMap.get(code.substring(0,1))+code3rMap.get(code.substring(1,2))+code.substring(2,3)+"' ";
		}else if(code.length() == 4){
			result += " OR " + FIELD_CODE + "= '"+code.substring(0,1)+code.substring(1,2)+code.substring(2,3)+code.substring(3,4)+"' OR ";
			result += FIELD_CODE + "= '"+code.substring(0,1)+code3rMap.get(code.substring(1,2))+code.substring(2,3)+code.substring(3,4)+"' OR ";
			result += FIELD_CODE + "= '"+code.substring(0,1)+code.substring(1,2)+code3rMap.get(code.substring(2,3))+code.substring(3,4)+"' OR ";
			result += FIELD_CODE + "= '"+code.substring(0,1)+code.substring(1,2)+code.substring(2,3)+code3rMap.get(code.substring(3,4))+"' OR ";
			result += FIELD_CODE + "= '"+code.substring(0,1)+code3rMap.get(code.substring(1,2))+code3rMap.get(code.substring(2,3))+code.substring(3,4)+"' OR ";
			result += FIELD_CODE + "= '"+code.substring(0,1)+code3rMap.get(code.substring(1,2))+code3rMap.get(code.substring(2,3))+code3rMap.get(code.substring(3,4))+"' OR ";
			result += FIELD_CODE + "= '"+code.substring(0,1)+code3rMap.get(code.substring(1,2))+code.substring(2,3)+code3rMap.get(code.substring(3,4))+"' OR ";
			result += FIELD_CODE + "= '"+code.substring(0,1)+code.substring(1,2)+code3rMap.get(code.substring(2,3))+code3rMap.get(code.substring(3,4))+"' OR ";
			result += FIELD_CODE + "= '"+code3rMap.get(code.substring(0,1))+code.substring(1,2)+code.substring(2,3)+code.substring(3,4)+"' OR ";
			result += FIELD_CODE + "= '"+code3rMap.get(code.substring(0,1))+code3rMap.get(code.substring(1,2))+code.substring(2,3)+code.substring(3,4)+"' OR ";
			result += FIELD_CODE + "= '"+code3rMap.get(code.substring(0,1))+code.substring(1,2)+code3rMap.get(code.substring(2,3))+code.substring(3,4)+"' OR ";
			result += FIELD_CODE + "= '"+code3rMap.get(code.substring(0,1))+code.substring(1,2)+code.substring(2,3)+code3rMap.get(code.substring(3,4))+"' OR ";
			result += FIELD_CODE + "= '"+code3rMap.get(code.substring(0,1))+code3rMap.get(code.substring(1,2))+code3rMap.get(code.substring(2,3))+code.substring(3,4)+"' OR ";
			result += FIELD_CODE + "= '"+code3rMap.get(code.substring(0,1))+code3rMap.get(code.substring(1,2))+code3rMap.get(code.substring(2,3))+code3rMap.get(code.substring(3,4))+"' OR ";
			result += FIELD_CODE + "= '"+code3rMap.get(code.substring(0,1))+code3rMap.get(code.substring(1,2))+code.substring(2,3)+code3rMap.get(code.substring(3,4))+"' OR ";
			result += FIELD_CODE + "= '"+code3rMap.get(code.substring(0,1))+code.substring(1,2)+code3rMap.get(code.substring(2,3))+code3rMap.get(code.substring(3,4))+"' ";
		}
		
		return result;
	}

	/*
	 * Process search results
	 */
	private List<Mapping> buildQueryResult(Cursor cursor) {
		
		if(DEBUG)
			Log.i("LimDB", "buildQueryResult");
		List<Mapping> result = new ArrayList<Mapping>();
		if (cursor.moveToFirst()) {

			String relatedlist = null;
			int codeColumn = cursor.getColumnIndex(FIELD_CODE);
			int wordColumn = cursor.getColumnIndex(FIELD_WORD);
			int scoreColumn = cursor.getColumnIndex(FIELD_SCORE);
			int relatedColumn = cursor.getColumnIndex(FIELD_RELATED);
			int idColumn = cursor.getColumnIndex(FIELD_id);
			HashMap<String, String> duplicateCheck = new HashMap<String, String>();
			do {
				Mapping munit = new Mapping();
				munit.setWord(cursor.getString(wordColumn));
				munit.setId(cursor.getString(idColumn));
				munit.setCode(cursor.getString(codeColumn));
				if (relatedlist == null
						&& cursor.getString(relatedColumn) != null) {
					relatedlist = cursor.getString(relatedColumn);
				}
				munit.setScore(cursor.getInt(scoreColumn));
				munit.setDictionary(false);
				
				if(munit.getWord() == null || munit.getWord().trim().equals(""))
					continue;
				
				if(duplicateCheck.get(munit.getWord()) == null){
					result.add(munit);
					duplicateCheck.put(munit.getWord(), munit.getWord());
				}
			} while (cursor.moveToNext());

			int ssize = mLIMEPref.getSimilarCodeCandidates();
			//Jeremy '11,6,1 The related field may have only one word and thus no "|" inside
			if (ssize > 0 && relatedlist != null ){//&& relatedlist.indexOf("|") != -1) { 
				String templist[] = relatedlist.split("\\|");
				int scount = 0;
				for (String unit : templist) {
					if(ssize != 0 && scount > ssize){break;}
					if(duplicateCheck.get(unit) == null){
						Mapping munit = new Mapping();
						munit.setWord(unit);
						munit.setScore(0);
						munit.setCode("@RELATED@");
						result.add(munit);
						duplicateCheck.put(unit, unit);
						scount++;
					}
				}
			}
		}
		return result;
	}

	/**
	 * 
	 * @return Cursor for
	 */
	public Cursor getDictionaryAll() {
		Cursor cursor = null;
		SQLiteDatabase db = this.getSqliteDb(true);

		cursor = db.query("dictionary", null, null, null, null, null, null, null);
		return cursor;
	}

	/**
	 * Get dictionary database contents
	 * 
	 * @param keyword
	 * @return
	 */
	public List<Mapping> queryUserDict(String pword) {
		
		List<Mapping> result = new LinkedList<Mapping>();


		if(mLIMEPref.getSimiliarEnable()){
			
			if (pword != null && !pword.trim().equals("")) {
	
				Cursor cursor = null;
	
				SQLiteDatabase db = this.getSqliteDb(true);
	
				cursor = db.query("related", null, FIELD_DIC_pword + " = '"
						+ pword + "'", null, null, null, FIELD_SCORE + " DESC", null);
	
				if (cursor.moveToFirst()) {
	
					int pwordColumn = cursor.getColumnIndex(FIELD_DIC_pword);
					int cwordColumn = cursor.getColumnIndex(FIELD_DIC_cword);
					int scoreColumn = cursor.getColumnIndex(FIELD_DIC_score);
					int idColumn = cursor.getColumnIndex(FIELD_id);
					do {
						Mapping munit = new Mapping();
						munit.setId(cursor.getString(idColumn));
						munit.setPword(cursor.getString(pwordColumn));
						munit.setCode("");
						munit.setWord(cursor.getString(cwordColumn));
						munit.setScore(cursor.getInt(scoreColumn));
						munit.setDictionary(true);
						result.add(munit);
					} while (cursor.moveToNext());
				}
	
				if (cursor != null) {
					cursor.deactivate();
					cursor.close();
				}
				db.close();
			}
		}
		return result;
	}

	/**
	 * Load source file and add records into database
	 */
	public void loadFile(final String table) {

		if (thread != null) {
			thread.stop();
			thread = null;
		}

		// Loading Information
		
		// Reset Database Table
		deleteAll(table);
		finish = false;

		thread = new Thread() {

			public void run() {

				boolean hasMappingVersion = false;
				boolean isCinFormat = false;
				ArrayList<ContentValues> resultlist = new ArrayList<ContentValues>();

				String imname = "";
				String line = "";
				String endkey ="";
				String selkey ="";
				finish = false;
				// relatedfinish = false;
				count = 0;
				ncount = 0;

				// Check if source file is .cin format
				if (filename.getName().toLowerCase().endsWith(".cin")) {
					isCinFormat = true;
				}

				// Base on first 100 line to identify the Delimiter
				try {
					// Prepare Source File
					FileReader fr = new FileReader(filename);
					BufferedReader buf = new BufferedReader(fr);
					boolean firstline = true;
					int i = 0;
					List<String> templist = new ArrayList<String>();
					while ((line = buf.readLine()) != null
							&& isCinFormat == false) {
						templist.add(line);
						if (i >= 100) {
							break;
						} else {
							i++;
						}
					}
					identifyDelimiter(templist);
					templist.clear();
					buf.close();
					fr.close();
				} catch (Exception e) {
				}

				// Create Related Words
				Map<String, String> hm = new HashMap<String, String>();

				SQLiteDatabase db = getSqliteDb(false);
				db.beginTransaction();

				try {
					// Prepare Source File
					FileReader fr = new FileReader(filename);
					BufferedReader buf = new BufferedReader(fr);
					boolean firstline = true;
					boolean cinFormatStart = false;
					String precode = "";
					
					while ((line = buf.readLine()) != null) {

						/*
						 * If source is cin format start from the tag %chardef
						 * begin until %chardef end
						 */
						if (isCinFormat) {
							if (!cinFormatStart) {
								// Modified by Jeremy '10, 3, 28. Some .cin have
								// double space between $chardef and begin or
								// end
								if (line != null
										&& line.trim().toLowerCase()
												.startsWith("%chardef")
										&& line.trim().toLowerCase().endsWith(
												"begin")) {
									cinFormatStart = true;
								}
								// Add by Jeremy '10, 3 , 27
								// use %cname as mapping_version of .cin
								if (!(  line.trim().toLowerCase().startsWith("%cname")
									  ||line.trim().toLowerCase().startsWith("%selkey")
									  ||line.trim().toLowerCase().startsWith("%endkey")
										)) {
									continue;
								}
							}
							if (line != null
									&& line.trim().toLowerCase().startsWith(
											"%chardef")
									&& line.trim().toLowerCase()
											.endsWith("end")) {
								break;
							}
						}

						// Check if file contain BOM MARK at file header
						if (firstline) {
							byte srcstring[] = line.getBytes();
							if (srcstring.length > 3) {
								if (srcstring[0] == -17 && srcstring[1] == -69
										&& srcstring[2] == -65) {
									byte tempstring[] = new byte[srcstring.length - 3];
									int a = 0;
									for (int j = 3; j < srcstring.length; j++) {
										tempstring[j - 3] = srcstring[j];
									}
									line = new String(tempstring);
								}
							}
							firstline = false;
						} else {
							if (line == null) {
								continue;
							}
							if (line.trim().equals("")) {
								continue;
							}
							if (line.length() < 3) {
								continue;
							}
						}

						try {

							String code = null, word = null;
							if (isCinFormat) {
								if (line.indexOf("\t") != -1) {
									code = line
											.substring(0, line.indexOf("\t"));
									word = line
											.substring(line.indexOf("\t") + 1);
								} else if (line.indexOf(" ") != -1) {
									code = line.substring(0, line.indexOf(" "));
									word = line
											.substring(line.indexOf(" ") + 1);
								}
							} else {
								code = line.substring(0, line
										.indexOf(DELIMITER));
								word = line
										.substring(line.indexOf(DELIMITER) + 1);
							}
							if (code == null || code.trim().equals("")) {
								continue;
							} else {
								code = code.trim();
							}
							if (word == null || word.trim().equals("")) {
								continue;
							} else {
								word = word.trim();
							}
							if (code.toLowerCase().contains("@version@")) {
								imname = word.trim();
								continue;
							} else if (code.toLowerCase().contains("%cname")) {
								imname = word.trim();
								continue;
							} else if (code.toLowerCase().contains("%selkey")) {
								selkey = word.trim();
								//Log.i("LimeDB:Loadfile","selkey:"+selkey);
								continue;
							} else if (code.toLowerCase().contains("%endkey")) {
								endkey = word.trim();
								//Log.i("LimeDB:Loadfile","endkey:"+endkey);
								continue;	
							} else {
								code = code.toLowerCase();
							}
							
							if (code.length() > 1) {
								//Jeremy '11,6,1  put the exact match word in the first word of related field
								if (hm.get(code) != null && hm.get(code).startsWith("|"))
									hm.put(code, word+hm.get(code));
								else
									hm.put(code,word);	
								//
								for (int k = 1; k < code.length(); k++) {
									String rootkey = code.substring(0, code.length() - k);
									if (hm.get(rootkey) != null) {
										String tempvalue = hm.get(rootkey);
										if (hm.get(rootkey) != null
												&& hm.get(rootkey).indexOf(word) == -1) {
											if(hm.get(rootkey).split("\\|").length < 50){
												hm.put(rootkey, tempvalue + "|" + word);
											}
										}
									} else {
										hm.put(rootkey, "|"+word);
									}
								}
							}
							
							count++;
							db.insert(table, null, getInsertItem(code, word));

						} catch (StringIndexOutOfBoundsException e) {}
					}

					buf.close();
					fr.close();


				} catch (Exception e) {
					setImInfo(table, "amount", "0");
					setImInfo(table, "source", "Failed!!!");
					e.printStackTrace();
				} finally {
					db.setTransactionSuccessful();
					db.endTransaction();
					db.close();
				}

				db = getSqliteDb(false);
				db.beginTransaction();
				try{
					for(Entry<String, String> entry: hm.entrySet())
			        {
						if(!entry.getValue().contains("|"))  // does not have related words; only has exact mappings
							continue;
						try{
							ContentValues cv = new ContentValues();
							String code = entry.getKey().replaceAll("'", "''");
							String tempValue = entry.getValue();
							String newValue = "";							
							//The related field starts with "|" mean no exact code coreesponding and has to insert new one.
							if (entry.getValue().startsWith("|")){
								cv.put(FIELD_CODE, code);
								newValue = tempValue.substring(1, tempValue.length());
								cv.put(FIELD_RELATED, newValue);
								db.insert(table, null, cv);
							}
							else{
							//The first word is the exact code corresponding word and has to be trimmed from related field
								newValue = tempValue.substring(tempValue.indexOf("|")+1
										, tempValue.length());
								cv.put(FIELD_RELATED, newValue);
								db.update(table, cv, FIELD_CODE +"='"+code+"'", null);
							}
							if(DEBUG)
								Log.i("loadfile",
										"create related field. code ="+entry.getKey()+" related = " + entry.getValue()+" trimmedRelated:" + newValue);
							
							
						}catch(Exception e2){
							// Just ignore all problem statement
							Log.i("loadfile","create related field error on code ="+entry.getKey()+" related = " + entry.getValue());
						}
			        }
				}catch (Exception e){
					setImInfo(table, "amount", "0");
					setImInfo(table, "source", "Failed!!!");
					e.printStackTrace();
				} finally {
					db.setTransactionSuccessful();
					db.endTransaction();
					db.close();
					mLIMEPref.setParameter("im_loading", false);
					mLIMEPref.setParameter("im_loading_table", "");
					setImInfo(table, "source", filename.getName());
					setImInfo(table, "name", imname);
					setImInfo(table, "amount", String.valueOf(count));
					setImInfo(table, "import", new Date().toLocaleString());
					setImInfo(table, "selkey", selkey);
					setImInfo(table, "endkey", endkey);
					
					// If there is no keyboard assigned for current input method then use default keyboard layout
					//String keyboard = getImInfo(table, "keyboard");
					//if(keyboard == null || keyboard.equals("")){
					//setImInfo(table, "keyboard", "lime");
					// '11,5,23 by Jeremy: Preset keyboard info. by tablename
					KeyboardObj kobj = getKeyboardObj(table);
					if( kobj == null){					
						kobj = getKeyboardObj("lime");
					 }
					setKeyboardInfo(table, kobj.getDescription(), kobj.getCode());
					
				}
				
				finish = true;
			}

		};
		thread.start();
	}
	
	public ContentValues getInsertItem(String code, String word) {
		try {
				ContentValues cv = new ContentValues();
				cv.put(FIELD_CODE, code);
				cv.put(FIELD_CODE3R, "0");
				cv.put(FIELD_WORD, word);
				cv.put(FIELD_SCORE, 0);
				return cv;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Identify the delimiter of the source file
	 * 
	 * @param src
	 */
	public void identifyDelimiter(List<String> src) {

		int commaCount = 0;
		int tabCount = 0;
		int pipeCount = 0;

		if (this.DELIMITER.equals("")) {
			for (String line : src) {
				if (line.indexOf("\t") != -1) {
					tabCount++;
				}
				if (line.indexOf(",") != -1) {
					commaCount++;
				}
				if (line.indexOf("|") != -1) {
					pipeCount++;
				}
			}
			if (commaCount > 0 || tabCount > 0 || pipeCount > 0) {
				if (commaCount >= tabCount && commaCount >= pipeCount) {
					this.DELIMITER = ",";
				} else if (tabCount >= commaCount && tabCount >= pipeCount) {
					this.DELIMITER = "\t";
				} else if (pipeCount >= tabCount && pipeCount >= commaCount) {
					this.DELIMITER = "|";
				}
			}
		}
	}

	/**
	 * Check if dictionary record exists
	 * 
	 * @param pword
	 * @param cword
	 * @return
	 */
	public Mapping isExists(String pword, String cword) {

		Mapping munit =null;
		if (pword != null && !pword.trim().equals("") && cword != null
				&& !cword.trim().equals("")) {

			try {
				Cursor cursor = null;
				SQLiteDatabase db = this.getSqliteDb(true);
				cursor = db.query("related", null, FIELD_DIC_pword + " = '"
						+ pword + "'" + " AND " + FIELD_DIC_cword + " = '"
						+ cword + "'", null, null, null, null, null);
				
				if (cursor.moveToFirst()) {
					int pwordColumn = cursor.getColumnIndex(FIELD_DIC_pword);
					int cwordColumn = cursor.getColumnIndex(FIELD_DIC_cword);
					int scoreColumn = cursor.getColumnIndex(FIELD_DIC_score);
					int idColumn = cursor.getColumnIndex(FIELD_id);
					
					munit = new Mapping();
						munit.setId(cursor.getString(idColumn));
						munit.setPword(cursor.getString(pwordColumn));
						munit.setWord(cursor.getString(cwordColumn));
						munit.setScore(cursor.getInt(scoreColumn));
						munit.setDictionary(true);
					
				} 
				if (cursor != null && cursor.getCount() > 0) {
					return munit;
				}
				db.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}
	

	/**
	 * @param srcunit
	 */
	public void resetImInfo(String im) {
		String removeString = "DELETE FROM im WHERE code='"+im+"'";
		SQLiteDatabase db = this.getSqliteDb(false);
			           db.execSQL(removeString);
					   db.close();
	}
	
	/**
	 * @param srcunit
	 */
	public String getImInfo(String im, String field) {
		try{
			String value = "";
			String selectString = "SELECT * FROM im WHERE code='"+im+"' AND title='"+field+"'";
			SQLiteDatabase db = this.getSqliteDb(true);
	
			Cursor cursor = db.rawQuery(selectString ,null);
			if (cursor.getCount() > 0) {
				cursor.moveToFirst();
				int descCol = cursor.getColumnIndex("desc");
				return cursor.getString(descCol);
			}
			
			db.close();
		}catch(Exception e){}
		return "";
	}
	
	/**
	 * @param srcunit
	 */
	public void removeImInfo(String im, String field) {
		String removeString = "DELETE FROM im WHERE code='"+im+"' AND title='"+field+"'";
		SQLiteDatabase db = this.getSqliteDb(false);
			           db.execSQL(removeString);
					   db.close();
	}
	

	/**
	 * @param srcunit
	 */
	public void setImInfo(String im, String field, String value) {

		ContentValues cv = new ContentValues();
					  cv.put("code", im);
					  cv.put("title", field);
					  cv.put("desc", value);
		
					  removeImInfo(im, field);
					  
		SQLiteDatabase db = this.getSqliteDb(false);
			           db.insert("im",null, cv);
					   db.close();
	}

	public List<ImObj> getImList() {
		List<ImObj> result = new LinkedList<ImObj>();
		try {
			SQLiteDatabase db = this.getSqliteDb(true);
			Cursor cursor = db.query("im", null, null, null, null, null, "code ASC", null);
			if (cursor.moveToFirst()) {
				do{
					String title = cursor.getString(cursor.getColumnIndex("title"));
					if(title.equals("keyboard")){
						ImObj kobj = new ImObj();
							  kobj.setCode(cursor.getString(cursor.getColumnIndex("code")));
							  kobj.setKeyboard(cursor.getString(cursor.getColumnIndex("keyboard")));
							  result.add(kobj);
					}
				} while (cursor.moveToNext());
			}
			db.close();
		} catch (Exception e) {
			Log.i("ART","Cannot get IM List : " + e );
		}
		return result;
	}
	
	public KeyboardObj getKeyboardObj(String keyboard){
		if(keyboard == null || keyboard.equals(""))
			return null;
		KeyboardObj kobj=null;
		try {
			SQLiteDatabase db = this.getSqliteDb(true);
			Cursor cursor = db.query("keyboard", null, FIELD_CODE +" = '"+keyboard+"'", null, null, null, null, null);
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
		} catch (Exception e) {
			e.printStackTrace();
		}
		return kobj;
	}

	public List<KeyboardObj> getKeyboardList() {
		List<KeyboardObj> result = new LinkedList<KeyboardObj>();
		try {
			SQLiteDatabase db = this.getSqliteDb(true);
			Cursor cursor = db.query("keyboard", null, null, null, null, null, "name ASC", null);
			if (cursor.moveToFirst()) {
				do{
					KeyboardObj kobj = new KeyboardObj();
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
					result.add(kobj);
				} while (cursor.moveToNext());
			}
			db.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	public void setKeyboardInfo(String im, String value,
			String keyboard) {

		ContentValues cv = new ContentValues();
					  cv.put("code", im);
					  cv.put("title", "keyboard");
					  cv.put("desc", value);
					  cv.put("keyboard", keyboard);
		
					  removeImInfo(im, "keyboard");
					  
		SQLiteDatabase db = this.getSqliteDb(false);
			           db.insert("im",null, cv);
			           db.close();
			           
		
	}

	public String getKeyboardCode(String im) {
		try{
			String value = "";
			String selectString = "SELECT * FROM im WHERE code='"+im+"' AND title='keyboard'";
			SQLiteDatabase db = this.getSqliteDb(true);
	
			Cursor cursor = db.rawQuery(selectString ,null);
			if (cursor.getCount() > 0) {
				cursor.moveToFirst();
				int descCol = cursor.getColumnIndex("keyboard");
				return cursor.getString(descCol);
			}
			db.close();
		}catch(Exception e){}
		return "";
	}

	public List<String> queryDictionary(String word) {
		List<String> result = new ArrayList<String>();
		try{
			String value = "";
			int ssize = mLIMEPref.getSimilarCodeCandidates();
			String selectString = "SELECT word FROM dictionary WHERE word MATCH '"+word+"*' ORDER BY word ASC LIMIT "+ssize+";";
			SQLiteDatabase db = this.getSqliteDb(true);
	
			Cursor cursor = db.rawQuery(selectString ,null);
			if (cursor.getCount() > 0) {
				cursor.moveToFirst();
				do{
					String w = cursor.getString(cursor.getColumnIndex("word"));
					if(w != null && !w.equals("")){
						result.add(w);
					}
				} while (cursor.moveToNext());
			}
			
			db.close();
		}catch(Exception e){}
		
		return result;
	}

}
