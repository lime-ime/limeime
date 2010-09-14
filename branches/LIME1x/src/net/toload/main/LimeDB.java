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

package net.toload.main;

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

	private static boolean DEBUG = true;

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
	private Map code3rMap = new HashMap();

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
	 * android.database.sqlite.SQLiteOpenHelper#onUpgrade(android.database.sqlite
	 * .SQLiteDatabase, int, int)
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// Start from 3.0v no need to create internal database
	}

	/**
	 * Base on given table name to remove records
	 */
	public void deleteAll(String table) {
		SQLiteDatabase db = this.getWritableDatabase();
		db.execSQL("DELETE FROM " + table);
		finish = false;
		resetImInfo(table);
		mLIMEPref.setParameter("im_loading", false);
		mLIMEPref.setParameter("im_loading_table", "");
		if(thread != null){
			thread.interrupt();
		}
	}

	/**
	 * Empty Related table records
	 */
	public void deleteUserDictAll() {
		mLIMEPref.setTotalUserdictRecords("0");
		// -------------------------------------------------------------------------
		SQLiteDatabase db = this.getWritableDatabase();
		db.delete("related", FIELD_DIC_score + " >0", null);
		db.close();
	}

	/**
	 * Count total amount of specific table
	 * 
	 * @return
	 */
	public int countMapping(String table) {

		try {
			SQLiteDatabase db = this.getReadableDatabase();
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
			SQLiteDatabase db = this.getReadableDatabase();
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

		SQLiteDatabase db = this.getWritableDatabase();
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
		
		Log.i("ART", "srclist.size():" + srclist.size());
		
		if (srclist != null && srclist.size() > 0) {

			SQLiteDatabase db = this.getWritableDatabase();
			
			try {
				for (int i = 0; i < srclist.size(); i++) {

					Mapping unit = srclist.get(i);
					
					if(i+1 <srclist.size()){
						Mapping unit2 = srclist.get((i + 1));
						
						if (unit != null 
							&& unit.getWord() != null && !unit.getWord().equals("")
							&& unit2 != null
							&& unit2.getWord() != null && !unit2.getWord().equals("")) {

							Log.i("ART", "WORD + WORD2:" + unit.getWord() + " - " + unit2.getWord());
							Mapping munit = this.isExists(unit.getWord(),unit2.getWord());
							Log.i("ART", "munit:" + munit.getWord() + " - " + munit.getPword());
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
	
					SQLiteDatabase db = this.getWritableDatabase();
					db.update("related", cv, FIELD_id + " = " + srcunit.getId(), null);
				}else{
					ContentValues cv = new ContentValues();
					cv.put(FIELD_SCORE, srcunit.getScore() + 1);
	
					SQLiteDatabase db = this.getWritableDatabase();
					db.update(tablename, cv, FIELD_id + " = " + srcunit.getId(), null);
				}
				//Log.i("ART","Add Score for : " + srcunit.isDictionary() + " / " + srcunit.getId() + srcunit.getCode() + srcunit.getScore());
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
				SQLiteDatabase db = this.getReadableDatabase();
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
		HashSet<String> wordlist = new HashSet<String>();

		if (code != null && !code.trim().equals("")) {

			code = code.toLowerCase();
			Cursor cursor = null;
			String sql = null;

			SQLiteDatabase db = this.getReadableDatabase();

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

		if (code != null && !code.trim().equals("")) {

			code = code.toLowerCase();
			Cursor cursor = null;
			String sql = null;

			SQLiteDatabase db = this.getReadableDatabase();

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

		List<Mapping> result = new ArrayList();
		if (cursor.moveToFirst()) {

			String relatedlist = null;
			int codeColumn = cursor.getColumnIndex(FIELD_CODE);
			int wordColumn = cursor.getColumnIndex(FIELD_WORD);
			int scoreColumn = cursor.getColumnIndex(FIELD_SCORE);
			int relatedColumn = cursor.getColumnIndex(FIELD_RELATED);
			int idColumn = cursor.getColumnIndex(FIELD_id);
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
				
				if(munit.getWord() == null || munit.getWord().trim().equals("")){
					continue;
				}
				result.add(munit);
			} while (cursor.moveToNext());

			int ssize = mLIMEPref.getSimilarCodeCandidates();
			HashMap dedupHm = new HashMap();
			if (relatedlist != null && relatedlist.indexOf("|") != -1) {
				String templist[] = relatedlist.split("\\|");
				int scount = 0;
				for (String unit : templist) {
					if(ssize != 0 && scount > ssize){break;}
					if(dedupHm.get(unit) == null){
						Mapping munit = new Mapping();
						munit.setWord(unit);
						munit.setScore(0);
						munit.setCode("@RELATED@");
						result.add(munit);
						dedupHm.put(unit, unit);
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
		SQLiteDatabase db = this.getReadableDatabase();

		cursor = db.query("dictionary", null, null, null, null, null, null,
				null);
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

		if (pword != null && !pword.trim().equals("")) {

			Cursor cursor = null;

			SQLiteDatabase db = this.getReadableDatabase();

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
				ArrayList<ContentValues> resultlist = new ArrayList();

				String imname = "";
				String line = "";
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
					List templist = new ArrayList();
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
				Map<String, String> hm = new HashMap();

				SQLiteDatabase db = getWritableDatabase();
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
								if (!line.trim().toLowerCase().startsWith(
										"%cname")) {
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
							} else {
								code = code.toLowerCase();
							}
							
							if (code.length() > 1) {
								for (int k = 1; k < code.length(); k++) {
									String rootkey = code.substring(0, code.length()
											- k);
									if (hm.get(rootkey) != null) {
										String tempvalue = hm.get(rootkey);
										if (hm.get(rootkey) != null
												&& hm.get(rootkey).indexOf(word) == -1) {
											if(hm.get(rootkey).split("\\|").length < 50){
												hm.put(rootkey, tempvalue + "|" + word);
											}
										}
									} else {
										hm.put(rootkey, word);
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
				}

				db = getWritableDatabase();
				db.beginTransaction();
				try{
					for(Entry<String, String> entry: hm.entrySet())
			        {
						try{
							ContentValues cv = new ContentValues();
										  cv.put(FIELD_RELATED, entry.getValue());
							String code = entry.getKey().replaceAll("'", "''");
							db.update(table, cv, FIELD_CODE +"='"+code+"'", null);
						}catch(Exception e2){
							// Just ignore all problem statement
						}
			        }
				}catch (Exception e){
					setImInfo(table, "amount", "0");
					setImInfo(table, "source", "Failed!!!");
					e.printStackTrace();
				} finally {
					db.setTransactionSuccessful();
					db.endTransaction();
					mLIMEPref.setParameter("im_loading", false);
					mLIMEPref.setParameter("im_loading_table", "");
					setImInfo(table, "source", filename.getName());
					setImInfo(table, "name", imname);
					setImInfo(table, "amount", String.valueOf(count));
					setImInfo(table, "import", new Date().toLocaleString());
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
				SQLiteDatabase db = this.getReadableDatabase();
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
		SQLiteDatabase db = this.getWritableDatabase();
			           db.execSQL(removeString);
	}
	
	/**
	 * @param srcunit
	 */
	public String getImInfo(String im, String field) {
		try{
			String value = "";
			String selectString = "SELECT * FROM im WHERE code='"+im+"' AND title='"+field+"'";
			SQLiteDatabase db = this.getReadableDatabase();
	
			Cursor cursor = db.rawQuery(selectString ,null);
			if (cursor.getCount() > 0) {
				cursor.moveToFirst();
				int descCol = cursor.getColumnIndex("desc");
				return cursor.getString(descCol);
			}
		}catch(Exception e){}
		return "";
	}
	
	/**
	 * @param srcunit
	 */
	public void removeImInfo(String im, String field) {
		String removeString = "DELETE FROM im WHERE code='"+im+"' AND title='"+field+"'";
		SQLiteDatabase db = this.getWritableDatabase();
			           db.execSQL(removeString);
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
					  
		SQLiteDatabase db = this.getWritableDatabase();
			           db.insert("im",null, cv);
		
	}

}
