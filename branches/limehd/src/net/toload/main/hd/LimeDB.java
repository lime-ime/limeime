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
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * @author Art Hung
 */
public class LimeDB extends SQLiteOpenHelper {

	private static boolean DEBUG = false;

	private final static int DATABASE_VERSION = 68; //66 -> 68 for new lime_number_symbol keybaord designed for ETEN
	//private final static int DATABASE_RELATED_SIZE = 50;

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
	private final static String DAYI_KEY = "1234567890qwertyuiopasdfghjkl;zxcvbnm,./";
	private final static String DAYI_CHAR =
		"言|牛|目|四|王|門|田|米|足|金|石|山|一|工|糸|火|艸|木|口|耳|人|革|日|土|手|鳥|月|立|女|虫|心|水|鹿|禾|馬|魚|雨|力|舟|竹";
	private final static String ARRAY_KEY = "qazwsxedcrfvtgbyhnujmik,ol.p;/";
	private final static String ARRAY_CHAR =
		"1^|1-|1v|2^|2-|2v|3^|3-|3v|4^|4-|4v|5^|5-|5v|6^|6-|6v|7^|7-|7v|8^|8-|8v|9^|9-|9v|0^|0-|0v|";
	private final static String BPMF_KEY = "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/-";
	private final static String BPMF_CHAR = 
		"ㄅ|ㄆ|ㄇ|ㄈ|ㄉ|ㄊ|ㄋ|ㄌ|ˇ|ㄍ|ㄎ|ㄏ|ˋ|ㄐ|ㄑ|ㄒ|ㄓ|ㄔ|ㄕ|ㄖ|ˊ|ㄗ|ㄘ|ㄙ|˙|ㄧ|ㄨ|ㄩ|ㄚ|ㄛ|ㄜ|ㄝ|ㄞ|ㄟ|ㄠ|ㄡ|ㄢ|ㄣ|ㄤ|ㄥ|ㄦ";
	private final static String ETEN_KEY = 		 			"@`abcdefghijklmnopqrstuvwxyz12347890-=;',./?";
	private final static String ETEN_KEY_REMAP = 			"@`81v2uzrc9bdxasiqoknwme,j.l7634f0p;/-y/5tg?";
	//private final static String DESIREZ_ETEN_KEY_REMAP = 	"-`81v2uzrc9bdxasiqoknwme,j.l7634f0p;/-yh5tg/";
	//private final static String MILESTONE_ETEN_KEY_REMAP =  "-`81v2uzrc9bdxasiqoknwme,j.l7634f0p;/-yh5tg/";
	//private final static String MILESTONE3_ETEN_KEY_REMAP = "-h81v2uzrc9bdxasiqoknwme,j.l7634f0p;/-yh5tg/";
	private final static String DESIREZ_ETEN_DUALKEY 	= 		"o,ukm9iq5axes"; // remapped from "qweruiop,mlvn";
	private final static String DESIREZ_ETEN_DUALKEY_REMAP = 	"7634f0p;thg/-";
	private final static String MILESTONE_ETEN_DUALKEY 	= 		"o,ukm9iq5aec"; //remapped from "qweruiop,mvh";
	private final static String MILESTONE_ETEN_DUALKEY_REMAP = 	"7634f0p;th/-";
	private final static String MILESTONE2_ETEN_DUALKEY 	= 		"o,ukm9iq5aer"; //remapped from "qweruiop,mvg";
	private final static String MILESTONE2_ETEN_DUALKEY_REMAP = 	"7634f0p;th/-";
	private final static String MILESTONE3_ETEN_DUALKEY 	= 		"5aew"; // ",mvt"
	private final static String MILESTONE3_ETEN_DUALKEY_REMAP = 	"th/-";
	private final static String ETEN_CHAR= 
		"@|`|ㄚ|ㄅ|ㄒ|ㄉ|ㄧ|ㄈ|ㄐ|ㄏ|ㄞ|ㄖ|ㄎ|ㄌ|ㄇ|ㄋ|ㄛ|ㄆ|ㄟ|ㄜ|ㄙ|ㄊ|ㄩ|ㄍ|ㄝ|ㄨ|ㄡ|ㄠ" +
		"|˙|ˊ|ˇ|ˋ|ㄑ|ㄢ|ㄣ|ㄤ|ㄥ|ㄦ|ㄗ|ㄘ|ㄓ|ㄔ|ㄕ|?";
	private final static String DESIREZ_ETEN_CHAR= 
		"@|`|ㄚ|ㄅ|ㄒ|ㄉ|(ㄧ/ˇ)|ㄈ|ㄐ|ㄏ|(ㄞ/ㄢ)|ㄖ|ㄎ|(ㄌ/ㄕ)|(ㄇ/ㄘ)|(ㄋ/ㄦ)|(ㄛ/ㄣ)|(ㄆ/ㄤ)|(ㄟ/˙)" +
		"|(ㄜ/ˋ)|ㄙ|ㄊ|(ㄩ/ㄑ)|(ㄍ/ㄥ)|(ㄝ/ˊ)|ㄨ|ㄡ|ㄠ" +
		"|˙|ˊ|ˇ|ˋ|ㄑ|ㄢ|ㄣ|ㄤ|ㄥ|ㄦ|ㄗ|ㄘ|(ㄓ/ㄗ)|ㄔ|ㄕ|?";
	private final static String MILESTONE_ETEN_CHAR= 
		"ㄦ|`|ㄚ|ㄅ|ㄒ|ㄉ|(ㄧ/ˇ)|ㄈ|ㄐ|(ㄏ/ㄦ)|(ㄞ/ㄢ)|ㄖ|ㄎ|ㄌ|(ㄇ/ㄘ)|ㄋ|(ㄛ/ㄣ)|(ㄆ/ㄤ)|(ㄟ/˙)" +
		"|(ㄜ/ˋ)|ㄙ|ㄊ|(ㄩ/ㄑ)|(ㄍ/ㄥ)|(ㄝ/ˊ)|ㄨ|ㄡ|ㄠ" +
		"|˙|ˊ|ˇ|ˋ|ㄑ|ㄢ|ㄣ|ㄤ|ㄥ|ㄦ|ㄗ|ㄘ|(ㄓ/ㄗ)|ㄔ|ㄕ|ㄥ";
	private final static String MILESTONE2_ETEN_CHAR= 
		"ㄦ|`|ㄚ|ㄅ|ㄒ|ㄉ|(ㄧ/ˇ)|ㄈ|(ㄐ/ㄦ)|ㄏ|(ㄞ/ㄢ)|ㄖ|ㄎ|ㄌ|(ㄇ/ㄘ)|ㄋ|(ㄛ/ㄣ)|(ㄆ/ㄤ)|(ㄟ/˙)" +
		"|(ㄜ/ˋ)|ㄙ|ㄊ|(ㄩ/ㄑ)|(ㄍ/ㄥ)|(ㄝ/ˊ)|ㄨ|ㄡ|ㄠ" +
		"|˙|ˊ|ˇ|ˋ|ㄑ|ㄢ|ㄣ|ㄤ|ㄥ|ㄦ|ㄗ|ㄘ|(ㄓ/ㄗ)|ㄔ|ㄕ|ㄥ";
	private final static String MILESTONE3_ETEN_CHAR= 
		"ㄦ|ㄘ|ㄚ|ㄅ|ㄒ|ㄉ|ㄧ|ㄈ|ㄐ|ㄏ|ㄞ|ㄖ|ㄎ|ㄌ|(ㄇ/ㄘ)|ㄋ|ㄛ|ㄆ|ㄟ|ㄜ|ㄙ|(ㄊ/ㄦ)|ㄩ|ㄍ|ㄝ|ㄨ|ㄡ|ㄠ" +
		"|˙|ˊ|ˇ|ˋ|ㄑ|ㄢ|ㄣ|ㄤ|(ㄍ/ㄥ)|ㄦ|ㄗ|ㄘ|(ㄓ/ㄗ)|ㄔ|ㄕ|ㄥ";
	
	private final static String ETEN26_KEY =            	"qazwsxedcrfvtgbyhnujmikolp";
	private final static String ETEN26_KEY_REMAP_INITIAL = 	"y8lhnju2vkzewr1tcsmba9dixq";
	private final static String ETEN26_KEY_REMAP_FINAL =   	"y8lhnju7vk6e;r1t-pm3094i/.";
	private final static String ETEN26_CHAR_INITIAL = 	
		"ㄗ|ㄚ|ㄠ|ㄘ|ㄙ|ㄨ|ㄧ|ㄉ|(ㄕ/ㄒ)|ㄜ|ㄈ|(ㄍ/ㄑ)|ㄊ|(ㄐ/ㄓ)|ㄅ|ㄔ|ㄏ|ㄋ|ㄩ|ㄖ|ㄇ|ㄞ|ㄎ|ㄛ|ㄌ|ㄆ";
	private final static String ETEN26_CHAR_FINAL = 	
		"ㄟ|ㄚ|ㄠ|ㄝ|ㄙ|ㄨ|ㄧ|˙|(ㄕ/ㄒ)|ㄜ|ˊ|(ㄍ/ㄑ)|ㄤ|(ㄐ/ㄓ)|ㄅ|ㄔ|ㄦ|ㄣ|ㄩ|ˇ|ㄢ|ㄞ|ˋ|ㄛ|ㄥ|ㄡ";
	
	private final static String ETEN26_DUALKEY_REMAP = 	"o,gf;5p0-/.";
	private final static String ETEN26_DUALKEY = 		"yhvewrsacxq";
	
	
	private final static String DESIREZ_KEY =            			"@qazwsxedcrfvtgbyhnujmik?olp,.";
	private final static String DESIREZ_BPMF_KEY_REMAP = 			"1qaz2wsedc5tg6yh4uj8ik9ol0;-,.";
	private final static String DESIREZ_BPMF_DUALKEY_REMAP = 	"xrfvb3n7m,.p/";
	private final static String DESIREZ_BPMF_DUALKEY = 			"sedcg6h4jkl0;";
	private final static String DESIREZ_DUALKEY_REMAP = 		"1234567890;-/='";
	private final static String DESIREZ_DUALKEY = 				"qwertyuiop,vlnm";
	private final static String DESIREZ_BPMF_CHAR = 
		"ㄅ|ㄆ|ㄇ|ㄈ|ㄉ|ㄊ|(ㄋ/ㄌ)|(ㄍ/ㄐ)|(ㄎ/ㄑ)|(ㄏ/ㄒ)|ㄓ|ㄔ|(ㄕ/ㄖ)|(ˊ/ˇ)|ㄗ|(ㄘ/ㄙ)|(ˋ/˙)" +
		"|ㄧ|(ㄨ/ㄩ)|ㄚ|ㄛ|(ㄜ/ㄝ)|ㄞ|ㄟ|(ㄠ/ㄡ)|(ㄢ/ㄣ)|(ㄤ/ㄥ)|ㄦ|,|.";
	
	
	private final static String DESIREZ_DAYI_CHAR =
		"@|(言/石)|人|心|(牛/山)|革|水|(目/一)|日|鹿|(四/工)|土|禾|(王/糸)|手|馬|(門/火)|鳥|魚|(田/艸)|月|雨|"
		+"(米/木)|立|?|(足/口)|(女/竹)|(金/耳)|(力/虫)|舟";
	
	private final static String MILESTONE_DUALKEY_REMAP = 	"1234567890;'=";
	private final static String MILESTONE_DUALKEY = 		"qwertyuiop,mh"; 
	private final static String MILESTONE_KEY = "qazwsxedcrfvtgbyhnujmik,ol.p/?";
	private final static String MILESTONE_BPMF_CHAR = 
		"(ㄅ/ㄆ)|ㄇ|ㄈ|(ㄉ/ㄊ)|ㄋ|ㄌ|(ㄍ/ˇ)|ㄎ|ㄏ|(ㄐ/ˋ)|ㄑ|ㄒ|(ㄓ/ㄔ)|ㄕ|ㄖ|(ㄗ/ˊ)|ㄘ|ㄙ|(ㄧ/˙)" +
		"|ㄨ|ㄩ|(ㄚ/ㄛ)|ㄜ|(ㄝ/ㄤ)|(ㄞ/ㄟ)|ㄠ|ㄡ|(ㄢ/ㄣ)|ㄥ|ㄦ";
	private final static String MILESTONE_DAYI_CHAR = 
		"(言/石)|人|心|(牛/山)|革|水|(目/一)|日|鹿|(四/工)|土|禾|(王/糸)|手|馬|(門/火)|鳥|魚|(田/艸)|月|雨|"
		+"(米/木)|立|(力/虫)|(足/口)|女|舟|(金/耳)|竹|?";
	
	private final static String MILESTONE2_DUALKEY_REMAP = 	"1234567890;'=";
	private final static String MILESTONE2_DUALKEY = 		"qwertyuiop,mg";
	
	
	private final static String MILESTONE3_KEY = "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p/";
	private final static String MILESTONE3_DUALKEY_REMAP = 	";";
	private final static String MILESTONE3_DUALKEY = 		",";  
	private final static String MILESTONE3_BPMF_CHAR = 
		"ㄅ|ㄆ|ㄇ|ㄈ|ㄉ|ㄊ|ㄋ|ㄌ|ˇ|ㄍ|ㄎ|ㄏ|ˋ|ㄐ|ㄑ|ㄒ|ㄓ|ㄔ|ㄕ|ㄖ|ˊ|ㄗ|ㄘ|ㄙ|˙|" +
		"ㄧ|ㄨ|ㄩ|ㄚ|ㄛ|ㄜ|(ㄝ/ㄤ)|ㄞ|ㄟ|ㄠ|ㄡ|ㄢ|ㄣ|(ㄥ/ㄦ)";
	private final static String MILESTONE3_DAYI_CHAR = 
		"言|石|人|心|牛|山|革|水|目|一|日|鹿|四|工|土|禾|王|糸|手|馬|門|火|鳥|魚|田|" +
		"艸|月|雨|米|木|立|(力/虫)|足|口|女|舟|金|耳|竹";
	

	private final static String CJ_KEY = "qwertyuiopasdfghjklzxcvbnm";
	private final static String CJ_CHAR = "手|田|水|口|廿|卜|山|戈|人|心|日|尸|木|火|土|竹|十|大|中|重|難|金|女|月|弓|一";
	
	private HashMap<String, HashMap<String,String>> keysDefMap = new HashMap<String, HashMap<String,String>>();
	private HashMap<String, HashMap<String,String>> keysReMap = new HashMap<String, HashMap<String,String>>();
	private HashMap<String, HashMap<String,String>> keys3rMap = new HashMap<String, HashMap<String,String>>();


	public String DELIMITER = "";

	private File filename = null;
	private String tablename = "custom";

	private int count = 0;
	//private int ncount = 0;
	private boolean finish = false;
	//private boolean relatedfinish = false;
	//Jeremy '11,6,16 keep the soft/physical keyboard flag from getmapping()
	private boolean isPhysicalKeyboardPressed = false;

	private LIMEPreferenceManager mLIMEPref;
	//private Map<String, String> code3rMap = new HashMap<String, String>();

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
			Log.i("LIMEDB:getSqliteDb()", "database version:" + db.getVersion());
			// Insert the new lime_number_symbol keyboard record
			if(!readonly && db.getVersion() < 68){
				ContentValues 	cv = new ContentValues();
								cv.put("code", "limenumsym");
								cv.put("name", "LIMENUMSYM");
								cv.put("desc", "LIME+數字符號鍵盤");
								cv.put("type", "phone");
								cv.put("image", "lime_number_symbol_keyboard_priview");
								cv.put("imkb", "lime_number_symbol");
								cv.put("imshiftkb", "lime_number_symbol_shift");
						  		cv.put("engkb", "lime_english_number");
						  		cv.put("engshiftkb", "lime_english_shift");
						  		cv.put("symbolkb", "symbol");
						  		cv.put("symbolshiftkb", "symbol_shift");
						  		cv.put("disable", "false");
			
				db.insert("keyboard",null, cv);
				db.setVersion(68);
			}
			
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

	private void addOrUpdateUserdictRecord(String pword, String cword){
		// Jeremy '11,6,12
		// Return if not learing related words and cword is not null (recording word frequency in IM relatedlist filed)
		if(!mLIMEPref.getLearnRelatedWord() && cword!=null) return;

		int dictotal = Integer.parseInt(mLIMEPref.getTotalUserdictRecords());
		
		if(DEBUG) Log.i("LIMEDB:addOrUpdateUserdictRecord()","pword:"+pword+" cword:"+cword + "dictotoal:"+dictotal);
		
		Mapping munit = this.isExists(pword , cword);
		
		SQLiteDatabase db = this.getSqliteDb(false);
		
		ContentValues cv = new ContentValues();
		try {
			if (munit == null) {
				cv.put(FIELD_DIC_pword, pword);
				cv.put(FIELD_DIC_cword, cword);
				cv.put(FIELD_DIC_score, 1);
				db.insert("related", null, cv);
				dictotal++;
				mLIMEPref.setTotalUserdictRecords(String.valueOf(dictotal));
				if(DEBUG) 
					Log.i("LIMEDB:addOrUpdateUserdictRecord()","new record, dictotal:"+String.valueOf(dictotal));
			}else{//the item exist in preload related database.
				int score = munit.getScore()+1;
			  	cv.put(FIELD_SCORE, score);
			  	db.update("related", cv, FIELD_id + " = " + munit.getId(), null);
			  	if(DEBUG) 
			  		Log.i("LIMEDB:addOrUpdateUserdictRecord()","update score on existing record; score:"+score);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		db.close();

	}
	/**
	 * Create dictionary database
	 * 
	 * @param srclist
	 */
	//Jeremy '11,6,12 rename from addDictionary for consistency 
	public void addUserDict(List<Mapping> srclist) {
		//Jeremy '11,6,12 move the db updating to addOrUpdateUserdictRecord
		if(DEBUG){
			Log.i("addDictionary:", "Etnering addDictionary");
		}
		
		
		if (srclist != null && srclist.size() > 0) {

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
						addOrUpdateUserdictRecord(unit.getWord(),unit2.getWord());
					}
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
			
			// Jeremy '11,6,12 Id=null denotes selection from related list in im table
			if(srcunit !=null && srcunit.getId()== null && 
				srcunit.getWord() != null  && !srcunit.getWord().trim().equals("")){
				String code = srcunit.getCode().trim().toLowerCase();
				if(DEBUG) Log.i("LIMEDb.addScore()","related selectd, code:" + code);
				addOrUpdateUserdictRecord(srcunit.getWord(),null);
				// Update relatedlist in IM table now.
				SQLiteDatabase db = this.getSqliteDb(false);
				
				Cursor cursor = db.query(tablename, null, 
						FIELD_CODE + " = '" + code + "'", 
						null, null, null, null, null);
				//int codeColumn = cursor.getColumnIndex(FIELD_CODE);
				//Log.i("LIMEDB:addScore()","cursor size:" + cursor.getCount());
				if(cursor.moveToFirst()){
					int relatedColumn = cursor.getColumnIndex(FIELD_RELATED);
					//int idColumn = cursor.getColumnIndex(FIELD_id);
					//String code = cursor.getString(codeColumn);
					String relatedlist = cursor.getString(relatedColumn);
					if(DEBUG) 
						Log.i("LIMEDB:addScore()","the original relatedlist:" + relatedlist);
					String templist[] = relatedlist.split("\\|");
					LinkedList <Mapping> scorelist = new LinkedList<Mapping>();
					for (String unit : templist) {
						Mapping munit =isExists(unit,null);
						if(munit==null){
							Mapping mu = new Mapping();
							mu.setWord(unit);
							mu.setScore(0);
							scorelist.addLast(mu);
							//Log.i("LIMEDB:addScore()","score 0, added in last");
						}else{
							Mapping mu = new Mapping();
							mu.setWord(unit);
							mu.setScore(munit.getScore());
							if(scorelist.isEmpty()) scorelist.add(mu);
							else{
								boolean added = false;
								for(int i=0;i<scorelist.size();i++){
									if(munit.getScore() >= scorelist.get(i).getScore()){
										scorelist.add(i, mu);
										//Log.i("LIMEDB:addScore()","score is not 0, added in location "+i+"; with score:" +munit.getScore() );
										added = true;
										break;
									}
								}
								if(!added) scorelist.addLast(mu);
								
							}			
						}
					}
					
					String newRelatedlist = "";
					for(Mapping munit : scorelist){
						if(newRelatedlist.equals("")) newRelatedlist = munit.getWord();
						else newRelatedlist = newRelatedlist + "|" + munit.getWord();
							
					}
					if(!newRelatedlist.equals(relatedlist)){
						ContentValues cv = new ContentValues();
						cv.put(FIELD_RELATED, newRelatedlist);
						db.update(tablename, cv, FIELD_CODE + " = '" + code + "'", null);
					}
					if(DEBUG) 
						Log.i("LIMEDB:addScore()","the new relatedlist:" + newRelatedlist);	
				}
				
				db.close();
			}
				
			if (srcunit != null && srcunit.getId() != null &&
					srcunit.getWord() != null  &&
					!srcunit.getWord().trim().equals("") ) {
				if(DEBUG) Log.i("LIMEDb.addScore()","addScore on code:"+srcunit.getCode());

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
							+ keyToKeyname(cursor.getString(codeColumn), Rtable);
					if (DEBUG) {
						Log.i("getRmapping", "Code:"
								+ cursor.getString(codeColumn));
					}

					while (cursor.moveToNext()) {
						result = result
								+ "; "
								+ keyToKeyname(cursor.getString(codeColumn),
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
//Rewrite by Jeremy 11,6,4.  Supoort for array and dayi now.
	public String keyToKeyname(String code, String Rtable) {
		if(DEBUG)
			Log.i("limedb:keyToKeyname()","code:" + code + 
					" Rtable:"+Rtable + " tablename:" + tablename);
		String keyboardtype = mLIMEPref.getPhysicalKeyboardType();
		String phonetickeyboardtype = mLIMEPref.getPhoneticKeyboardType();
		String keytable = Rtable;
		if(isPhysicalKeyboardPressed){
			if(Rtable.equals("phonetic") && tablename.equals("phonetic") )
				keytable = Rtable + keyboardtype + phonetickeyboardtype;
			else
				keytable = Rtable + keyboardtype;
		}else if(Rtable.equals("phonetic") && tablename.equals("phonetic") ){
				keytable = Rtable + phonetickeyboardtype;
		}
		if(DEBUG)
		Log.i("limedb:keyToKeyname()","code:" + code + 
				" Rtable:"+Rtable + " tablename:" + tablename + " keytable:"+keytable);
		
		if(keysDefMap.get(keytable)==null
				|| keysDefMap.get(keytable).size()==0){
			String keyString="", keynameString="", finalKeynameString = null;
			//Jeremy 11,6,4 Load keys and keynames from im table.
			//keyString = getImInfo(Rtable,"imkeys");
			//keynameString = getImInfo(Rtable,"imkeynames");
			
			
			if(Rtable.equals("phonetic")|| !keyboardtype.equals("normal_keyboard")
					||keyString.equals("")||keynameString.equals("")){
				if(Rtable.equals("cj")||Rtable.equals("scj")){
					keyString = CJ_KEY;
					keynameString = CJ_CHAR;
				}else if(Rtable.equals("phonetic") ) { 
					if(tablename.equals("phonetic") ){  // only do this on composing mapping popup
						if(phonetickeyboardtype.equals("eten")){
							keyString = ETEN_KEY;
							if(keyboardtype.equals("milestone") && isPhysicalKeyboardPressed)
								keynameString = MILESTONE_ETEN_CHAR;
							else if(keyboardtype.equals("milestone2") && isPhysicalKeyboardPressed)
								keynameString = MILESTONE2_ETEN_CHAR;
							else if(keyboardtype.equals("milestone3") && isPhysicalKeyboardPressed)
								keynameString = MILESTONE3_ETEN_CHAR;
							else if(keyboardtype.equals("desireZ") && isPhysicalKeyboardPressed)
								keynameString = DESIREZ_ETEN_CHAR;
							else
								keynameString = ETEN_CHAR;
						}else if(phonetickeyboardtype.equals("eten26")){
							keyString = ETEN26_KEY;
							keynameString = ETEN26_CHAR_INITIAL;
							finalKeynameString = ETEN26_CHAR_FINAL;
						}else if((keyboardtype.equals("milestone")||keyboardtype.equals("milestone2")) 
								&& isPhysicalKeyboardPressed){
							keyString = MILESTONE_KEY;
							keynameString = MILESTONE_BPMF_CHAR;
						}else if(keyboardtype.equals("milestone3") && isPhysicalKeyboardPressed){
								keyString = MILESTONE3_KEY;
								keynameString = MILESTONE3_BPMF_CHAR;
						}else if(keyboardtype.equals("desireZ") && isPhysicalKeyboardPressed){
							keyString = DESIREZ_KEY;
							keynameString = DESIREZ_BPMF_CHAR;
						}else{
							keyString = BPMF_KEY;
							keynameString = BPMF_CHAR;
						}
							
					}else{ 
						keyString = BPMF_KEY;
						keynameString = BPMF_CHAR;
					}
				}else if(Rtable.equals("array")) {
					keyString = ARRAY_KEY;
					keynameString = ARRAY_CHAR;
				}else if(Rtable.equals("dayi")) {
					if(isPhysicalKeyboardPressed){ // only do this on composing mapping popup
						if(keyboardtype.equals("milestone")||keyboardtype.equals("milestone2")){
							keyString = MILESTONE_KEY;
							keynameString = MILESTONE_DAYI_CHAR;
						}else if(keyboardtype.equals("milestone3")){
								keyString = MILESTONE3_KEY;
								keynameString = MILESTONE3_DAYI_CHAR;
						}else if(keyboardtype.equals("desireZ")){
							keyString = DESIREZ_KEY;
							keynameString = DESIREZ_DAYI_CHAR;
						}else{
							keyString = DAYI_KEY;
							keynameString = DAYI_CHAR;
						}
					}else{
						keyString = DAYI_KEY;
						keynameString = DAYI_CHAR;
					}
				}
			}
			if(DEBUG) 
				Log.i("limedb:keyToKeyname()", "keyboardtype:" +keyboardtype + " phonetickeyboardtype:" + phonetickeyboardtype + 
					" keyString:"+keyString + " keynameString:" +keynameString + " finalkeynameString:" + finalKeynameString);
			
			HashMap<String,String> keyMap = new HashMap<String,String>();
			HashMap<String,String> finalKeyMap = null;
			if(finalKeynameString != null)
				finalKeyMap = new HashMap<String,String>();
			
			String charlist[] = keynameString.split("\\|");
			String finalCharlist[] = null;
			
			if(finalKeyMap != null)
				finalCharlist = finalKeynameString.split("\\|");
				
			for (int i = 0; i < keyString.length(); i++) {
				keyMap.put(keyString.substring(i, i + 1), charlist[i]);
				if(finalKeyMap != null && finalCharlist!=null)
					finalKeyMap.put(keyString.substring(i, i + 1), finalCharlist[i]);
			}
			keysDefMap.put(keytable, keyMap);
			if(finalKeyMap != null)
				keysDefMap.put("final_"+keytable, finalKeyMap);
			
		}
		
		if(keysDefMap.get(keytable)==null 
				|| keysDefMap.get(keytable).size()==0){
			if(DEBUG) Log.i("limedb:keyToKeyname()","nokeysDefMap found!!");
			return code;
		}else{
			String result = new String("");
			HashMap <String,String> keyMap = keysDefMap.get(keytable);
			HashMap <String,String> finalKeyMap = keysDefMap.get("final_"+keytable);
			
			if(finalKeyMap == null){
				for (int i = 0; i < code.length(); i++) {
					String c = keyMap.get(code.substring(i, i + 1));
					if(c!=null) result = result + c;
				}
			}else{
				if(code.length()==1){
					String c = "";
					if(code.equals("q") || code.equals("w")){ // Dual mapped INITIALS have words mapped for ㄗ and ㄘ. 
						c = keyMap.get(code);
					}else{
						c = finalKeyMap.get(code);
					}
					if(c!=null) result = c.trim();
				}else{
					for (int i = 0; i < code.length(); i++) {
						String c = "";
						if(i>0)
							c = finalKeyMap.get(code.substring(i, i + 1));
						else
							c = keyMap.get(code.substring(i, i + 1));
						if(c!=null) result = result + c.trim();
					}
				}
			}
				
			if(DEBUG) 
				Log.i("limedb:keyToKeyname()","returning:" + result);
			if(result.equals(""))
				return code;
			else
				return result;
		}
		
		
	}

	/*
	 * Retrieve matched records
	 */
	public List<Mapping> getMappingSimiliar(String code) {

		List<Mapping> result = new LinkedList<Mapping>();
		
		if(mLIMEPref.getSimilarCodeCandidates() > 0){
			//HashSet<String> wordlist = new HashSet<String>();
	
			if (code != null && !code.trim().equals("")) {
	
				code = code.toLowerCase();
				Cursor cursor = null;
				//String sql = null;
	
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
	public List<Mapping> getMapping(String code, boolean softKeyboard) {

		// Add by Jeremy '10, 3, 27. Extension on multi table query.

		List<Mapping> result = new LinkedList<Mapping>();
		
		isPhysicalKeyboardPressed = !softKeyboard;

		//Two-steps qeury code pre-processing. Jeremy '11,6,15
		// Step.1 Code re-mapping.  
		code = preProcessingRemappingCode(code);
		// Step.2 Build extra query conditions. (e.g. 3row remap)
		String extraConditions = preProcessingForExtraQueryConditions(code);
		//Jeremy '11,6,11 seperated suggestions sorting option for physical keyboard
		boolean sort = true;
		if(softKeyboard) sort = mLIMEPref.getSortSuggestions();
		else sort = mLIMEPref.getPhysicalKeyboardSortSuggestions();
		
		if(DEBUG) Log.i("LIMEDB;getmapping()","sort:"+ sort +" soft:" + 
						mLIMEPref.getSortSuggestions()+" physical:"+mLIMEPref.getSortSuggestions());

		try{
			if (!code.equals("")) {
				Cursor cursor = null;
				SQLiteDatabase db = this.getSqliteDb(true);
				try {
					// Jeremy '11,6,15 Using query with proprocessed code and extra query conditions.
					if(sort){
						cursor = db.query(tablename, null, FIELD_CODE + " = '" + code + "' " + extraConditions
								, null, null, null, FIELD_SCORE +" DESC", null);
						//Log.i("ART","SORT -> run code3r a:"+tablename + " ->count:" + cursor.getCount() + " " + FIELD_CODE + " = '" + code + "' " + code3r);
					}else{
						cursor = db.query(tablename, null, FIELD_CODE + " = '" + code + "' " + extraConditions
								, null, null, null, null, null);
						//Log.i("ART","NO SORT -> run code3r a:"+tablename + " ->count:" + cursor.getCount() + " " + FIELD_CODE + " = '" + code + "' " + code3r);
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
	
	private String preProcessingRemappingCode(String code){
		if(DEBUG) Log.i("LIMEDB.preProcessingRemappingCode()", "code="+code);
		if(code != null){
			String keyboardtype = mLIMEPref.getPhysicalKeyboardType();
			String phonetickeyboardtype = mLIMEPref.getPhoneticKeyboardType();
			String keyString = "", keyRemapString ="", finalKeyRemapString = null;
			String newcode = code;
			String remaptable = tablename;
			if(isPhysicalKeyboardPressed ){
				if(tablename.equals("phonetic"))
					remaptable = tablename + keyboardtype + phonetickeyboardtype;
				else
					remaptable = tablename + keyboardtype;
			}else if(tablename.equals("phonetic")) {
					remaptable = tablename + phonetickeyboardtype;
			}
			
			if(keysReMap.get(remaptable)==null
					|| keysReMap.get(remaptable).size()==0){
			
				if(tablename.equals("phonetic")&&phonetickeyboardtype.equals("eten26")){
					keyString = ETEN26_KEY;
					keyRemapString = ETEN26_KEY_REMAP_INITIAL;
					finalKeyRemapString = ETEN26_KEY_REMAP_FINAL;
				}if(tablename.equals("phonetic")&&phonetickeyboardtype.equals("eten")){
					keyString = ETEN_KEY;
					/*
					if((keyboardtype.equals("milestone")||keyboardtype.equals("milestone2")) 
							&& isPhysicalKeyboardPressed)
						keyRemapString = MILESTONE_ETEN_KEY_REMAP;
					else if(keyboardtype.equals("milestone3") && isPhysicalKeyboardPressed)
						keyRemapString = MILESTONE3_ETEN_KEY_REMAP;
					else if(keyboardtype.equals("desireZ") && isPhysicalKeyboardPressed)
						keyRemapString = DESIREZ_ETEN_KEY_REMAP;
					else
					*/
					keyRemapString = ETEN_KEY_REMAP;
				}else if(isPhysicalKeyboardPressed 
					&& tablename.equals("phonetic") && keyboardtype.equals("desireZ")){
					//Desire Z phonetic keybaord
					keyString = DESIREZ_KEY;
					keyRemapString = DESIREZ_BPMF_KEY_REMAP;
				}
				
				HashMap<String,String> reMap = new HashMap<String,String>();
				HashMap<String,String> finalReMap = null;
				if( finalKeyRemapString!=null)
					finalReMap = new HashMap<String,String>();
				
				for (int i = 0; i < keyString.length(); i++) {
					reMap.put(keyString.substring(i, i + 1), keyRemapString.substring(i, i + 1));
					if(finalReMap!=null)
						finalReMap.put(keyString.substring(i, i + 1), finalKeyRemapString.substring(i, i + 1));
				}
				keysReMap.put(remaptable, reMap);
				if(finalReMap!=null)
					keysReMap.put("final_"+remaptable, finalReMap);
			}
					
					
			if(keysReMap.get(remaptable)==null 
						|| keysReMap.get(remaptable).size()==0){
				return code;
			}
			else{
				HashMap<String,String> reMap = keysReMap.get(remaptable);
				HashMap<String,String> finalReMap =  keysReMap.get("final_"+remaptable);
				
				newcode = "";
				for (int i = 0; i < code.length(); i++) {
					String c = null;
					if(finalReMap == null){
						c = reMap.get(code.substring(i, i + 1));
					}else {
						if(i>0)
							c = finalReMap.get(code.substring(i, i + 1));
						else
							c = reMap.get(code.substring(i, i + 1));
					}
					
					if(c!=null) newcode = newcode + c;
				
				}
				if(DEBUG) Log.i("LIMEDB.preProcessingRemappingCode()", "newcode="+newcode);
			}
			
			
			//Process the escape characters of query
			newcode = newcode.replaceAll("'", "''");
			
			return newcode;
		}else
			return "";
	}
	
	private String preProcessingForExtraQueryConditions(String code){
		if(DEBUG) 
			Log.i("LIMEDB.preProcessingForExtraQueryConditions()", "code="+code);
		
		if(code != null ){
			String keyboardtype = mLIMEPref.getPhysicalKeyboardType();
			String phonetickeyboardtype = mLIMEPref.getPhoneticKeyboardType();
			String code3r =code;
			String dualKey = "";
			String dualKeyRemap = "";
			String remaptable = tablename;
			if(isPhysicalKeyboardPressed ){
				if(tablename.equals("phonetic"))
					remaptable = tablename + keyboardtype + phonetickeyboardtype;
				else
					remaptable = tablename + keyboardtype;
			}else if(tablename.equals("phonetic")){
					remaptable = tablename + phonetickeyboardtype;
			}
			
			
			if(keys3rMap.get(remaptable)==null
					|| keys3rMap.get(remaptable).size()==0){
				if(tablename.equals("phonetic")&&phonetickeyboardtype.equals("eten26")){
					dualKey = ETEN26_DUALKEY;
					dualKeyRemap = ETEN26_DUALKEY_REMAP;	
				}else if(keyboardtype.equals("milestone") && isPhysicalKeyboardPressed ){
					if(tablename.equals("phonetic")&&phonetickeyboardtype.equals("eten")){
						dualKey = MILESTONE_ETEN_DUALKEY;
						dualKeyRemap = MILESTONE_ETEN_DUALKEY_REMAP;
					}else{
						dualKey = MILESTONE_DUALKEY;
						dualKeyRemap = MILESTONE_DUALKEY_REMAP;
					}
				}else if(keyboardtype.equals("milestone2") && isPhysicalKeyboardPressed ){
					if(tablename.equals("phonetic")&&phonetickeyboardtype.equals("eten")){
						dualKey = MILESTONE2_ETEN_DUALKEY;
						dualKeyRemap = MILESTONE2_ETEN_DUALKEY_REMAP;
					}else{
						dualKey = MILESTONE2_DUALKEY;
						dualKeyRemap = MILESTONE2_DUALKEY_REMAP;
					}
				}else if(keyboardtype.equals("milestone3") && isPhysicalKeyboardPressed ){
					if(tablename.equals("phonetic")&&phonetickeyboardtype.equals("eten")){
						dualKey = MILESTONE3_ETEN_DUALKEY;
						dualKeyRemap = MILESTONE3_ETEN_DUALKEY_REMAP;
					}else{
						dualKey = MILESTONE3_DUALKEY;
						dualKeyRemap = MILESTONE3_DUALKEY_REMAP;
					}
				}else if(keyboardtype.equals("desireZ") && isPhysicalKeyboardPressed ) {
					if(tablename.equals("phonetic")&&phonetickeyboardtype.equals("eten")){
						dualKey = DESIREZ_ETEN_DUALKEY;
						dualKeyRemap = DESIREZ_ETEN_DUALKEY_REMAP;
					}else if(tablename.equals("phonetic")&&phonetickeyboardtype.equals("standard")){
						dualKey = DESIREZ_BPMF_DUALKEY;
						dualKeyRemap = DESIREZ_BPMF_DUALKEY_REMAP;
					}else{
						dualKey = DESIREZ_DUALKEY;
						dualKeyRemap = DESIREZ_DUALKEY_REMAP;
					}
				}
				HashMap<String,String> reMap = new HashMap<String,String>();
				if(DEBUG)
					Log.i("LIMEDB.preProcessingForExtraQueryConditions()", 
							"dualKey="+dualKey+" dualKeyRemap="+dualKeyRemap);
				for(int i=0; i< dualKey.length(); i++){
					String key = dualKey.substring(i,i+1);
					String value = dualKeyRemap.substring(i,i+1);
					//Process the escape characters of query
					if(key.equals("'")) key = "''";
					if(value.equals("'")) value = "''";
					reMap.put(key, value);
					reMap.put(value, value);
				}
				keys3rMap.put(remaptable, reMap);
			}
			
			if(keys3rMap.get(remaptable)==null
					|| keys3rMap.get(remaptable).size()==0){
				return "";
			}else{
				
				HashMap<String,String> reMap = keys3rMap.get(remaptable);
				code3r = "";
				for (int i = 0; i < code.length(); i++) {
					String c = reMap.get(code.substring(i, i + 1));
					if(c!=null) code3r = code3r + c;
				}
				if(DEBUG)
					Log.i("LIMEDB.preProcessingForExtraQueryConditions()", "code3r="+code3r);
				
				
			}
			if(!code3r.equalsIgnoreCase(code)){
				return expandCode3r(code, remaptable);
			}
		}
		return "";
	}
	
	private String expandCode3r(String code, String keytablename){
		
		HashMap<String,String> code3rMap = keys3rMap.get(keytablename);

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

			//String relatedlist = null;
			int codeColumn = cursor.getColumnIndex(FIELD_CODE);
			int wordColumn = cursor.getColumnIndex(FIELD_WORD);
			int scoreColumn = cursor.getColumnIndex(FIELD_SCORE);
			int relatedColumn = cursor.getColumnIndex(FIELD_RELATED);		
			HashMap<String, String> duplicateCheck = new HashMap<String, String>();
			HashMap<String, String> relatedMap = new HashMap<String, String>();
			do {
				int idColumn = cursor.getColumnIndex(FIELD_id);
				String code = cursor.getString(codeColumn);
				String relatedlist = cursor.getString(relatedColumn);
				Mapping munit = new Mapping();
				munit.setWord(cursor.getString(wordColumn));
				munit.setId(cursor.getString(idColumn));
				munit.setCode(code);
				//if (relatedlist == null
				//		&& cursor.getString(relatedColumn) != null) {
				//relatedlist = cursor.getString(relatedColumn);
				//}
				if(relatedMap.get(code) == null){
					relatedMap.put(code, relatedlist);
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
			//Jeremy '11,6,11 allow multiple relatedlist from different codes.
			int scount = 0;
			for(Entry<String, String> entry: relatedMap.entrySet()){
				String relatedlist = entry.getValue();
				if (ssize > 0 && relatedlist != null && scount <= ssize){ 
					String templist[] = relatedlist.split("\\|");
				
					for (String unit : templist) {
						if(ssize != 0 && scount > ssize){break;}
						if(duplicateCheck.get(unit) == null){
							Mapping munit = new Mapping();
							munit.setWord(unit);
							//munit.setPword(relatedlist);
							munit.setScore(0);
							munit.setCode(entry.getKey());
							//Jeremy '11,6,18 skip if word is empty
							if(munit.getWord() == null || munit.getWord().trim().equals(""))
								continue;
							result.add(munit);
							duplicateCheck.put(unit, unit);
							scount++;
						}
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
				//Jeremy '11,6,12, Add constraint on cword is not null (cword =null is for recoding im related list selected count).
				cursor = db.query("related", null, FIELD_DIC_pword + " = '"
						+ pword + "' AND " + FIELD_DIC_cword + " IS NOT NULL"
						, null, null, null, FIELD_SCORE + " DESC", null);
	
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

				//boolean hasMappingVersion = false;
				boolean isCinFormat = false;
				//ArrayList<ContentValues> resultlist = new ArrayList<ContentValues>();

				String imname = "";
				String line = "";
				String endkey ="";
				String selkey ="";
				String spacestyle = "";
				String imkeys = "";
				String imkeynames = "";
				finish = false;
				// relatedfinish = false;
				count = 0;
				//ncount = 0;

				// Check if source file is .cin format
				if (filename.getName().toLowerCase().endsWith(".cin")) {
					isCinFormat = true;
				}

				// Base on first 100 line to identify the Delimiter
				try {
					// Prepare Source File
					FileReader fr = new FileReader(filename);
					BufferedReader buf = new BufferedReader(fr);
					//boolean firstline = true;
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
					boolean inChardefBlock = false;
					boolean inKeynameBlock = false;
					//String precode = "";
					
					while ((line = buf.readLine()) != null) {

						/*
						 * If source is cin format start from the tag %chardef
						 * begin until %chardef end
						 */
						if (isCinFormat) {
							if (!(inChardefBlock || inKeynameBlock)) {
								// Modified by Jeremy '10, 3, 28. Some .cin have
								// double space between $chardef and begin or
								// end
								if (line != null
										&& line.trim().toLowerCase().startsWith("%chardef")
										&& line.trim().toLowerCase().endsWith("begin")
										) {
									inChardefBlock = true;
								}
								if (line != null
										&& line.trim().toLowerCase().startsWith("%keyname")
										&& line.trim().toLowerCase().endsWith("begin")
										) {
									inKeynameBlock = true;
								}
								// Add by Jeremy '10, 3 , 27
								// use %cname as mapping_version of .cin
								// Jeremy '11,6,5 add selkey, endkey and spacestyle support
								if (!(  line.trim().toLowerCase().startsWith("%cname")
									  ||line.trim().toLowerCase().startsWith("%selkey")
									  ||line.trim().toLowerCase().startsWith("%endkey")
									  ||line.trim().toLowerCase().startsWith("%spacestyle")
										)) {
									continue;
								}
							}
							if (line != null
									&& line.trim().toLowerCase().startsWith("%keyname")
									&& line.trim().toLowerCase().endsWith("end")
								) {
								inKeynameBlock = false;
								continue;
							}
							if (line != null
									&& line.trim().toLowerCase().startsWith("%chardef")
									&& line.trim().toLowerCase().endsWith("end")
								) {
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
									//int a = 0;
									for (int j = 3; j < srcstring.length; j++) {
										tempstring[j - 3] = srcstring[j];
									}
									line = new String(tempstring);
								}
							}
							firstline = false;
						} else 	if (line == null || line.trim().equals("") || line.length() < 3) {
								continue;
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
							} else if (code.toLowerCase().contains("%spacestyle")) {
								spacestyle = word.trim();
							
								continue;	
							} else {
								code = code.toLowerCase();
							}
							if(inKeynameBlock) {  //Jeremy '11,6,5 preserve keyname blocks here.
								imkeys = imkeys + code.toLowerCase().trim();
								String c = word.trim();
								if(!c.equals("")){
									if(imkeynames.equals(""))
										imkeynames = c;
									else
										imkeynames = imkeynames + "|"+c; 
								}
																
							}
							else {
								if (code.length() > 1) {
								//Jeremy '11,6,1  put the exact match word in the first word of related field
									if (hm.get(code) != null && hm.get(code).startsWith("|"))
										hm.put(code, word+hm.get(code));
									else
										hm.put(code,word);	
								
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
							}

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
					if (!selkey.equals("")) setImInfo(table, "selkey", selkey);
					if (!endkey.equals("")) setImInfo(table, "endkey", endkey);
					if (!spacestyle.equals("")) setImInfo(table, "spacestyle", spacestyle);
					if (!imkeys.equals("")) setImInfo(table, "imkeys", imkeys);
					if (!imkeynames.equals("")) setImInfo(table, "imkeynames", imkeynames);
					if(DEBUG) Log.i("limedb:loadfile()","imkeys:" +imkeys + " imkeynames:"+imkeynames);
					
					// If there is no keyboard assigned for current input method then use default keyboard layout
					//String keyboard = getImInfo(table, "keyboard");
					//if(keyboard == null || keyboard.equals("")){
					//setImInfo(table, "keyboard", "lime");
					// '11,5,23 by Jeremy: Preset keyboard info. by tablename
					KeyboardObj kobj = getKeyboardObj(table);
					if( kobj == null){					
						kobj = getKeyboardObj("lime");
					 }
					setIMKeyboard(table, kobj.getDescription(), kobj.getCode());
					
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
		if (pword != null && !pword.trim().equals("") 
		//		&& cword != null && !cword.trim().equals("") //Jeremy '11,6,12 allow cowrd =null
				){

			try {
				Cursor cursor = null;
				SQLiteDatabase db = this.getSqliteDb(true);
				if(cword==null || cword.trim().equals("")){
					cursor = db.query("related", null, FIELD_DIC_pword + " = '"
							+ pword + "'" + " AND " + FIELD_DIC_cword + " IS NULL"
							, null, null, null, null, null);
				}else{
					cursor = db.query("related", null, FIELD_DIC_pword + " = '"
						+ pword + "'" + " AND " + FIELD_DIC_cword + " = '"
						+ cword + "'", null, null, null, null, null);
				}
				
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
					db.close();
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
			//String value = "";
			String selectString = "SELECT * FROM im WHERE code='"+im+"' AND title='"+field+"'";
			SQLiteDatabase db = this.getSqliteDb(true);
	
			Cursor cursor = db.rawQuery(selectString ,null);
			if (cursor.getCount() > 0) {
				cursor.moveToFirst();
				int descCol = cursor.getColumnIndex("desc");
				String iminfo = cursor.getString(descCol);
				db.close();
				return iminfo;
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
			db.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return kobj;
	}
	
	public String getKeyboardInfo(String keyboardCode, String field) {
		String info=null;
		try {
			SQLiteDatabase db = this.getSqliteDb(true);
			Cursor cursor = db.query("keyboard", null, FIELD_CODE +" = '"+keyboardCode+"'"
					, null, null, null, null, null);
			if (cursor.moveToFirst()) {
				info = cursor.getString(cursor.getColumnIndex(field));
			}
			db.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return info;
	
	}

	public List<KeyboardObj> getKeyboardList() {
		List<KeyboardObj> result = new LinkedList<KeyboardObj>();
		try {
			SQLiteDatabase db = this.getSqliteDb(false);
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

	public void setIMKeyboard(String im, String value,
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
			//String value = "";
			String selectString = "SELECT * FROM im WHERE code='"+im+"' AND title='keyboard'";
			SQLiteDatabase db = this.getSqliteDb(true);
	
			Cursor cursor = db.rawQuery(selectString ,null);
			if (cursor.getCount() > 0) {
				cursor.moveToFirst();
				int descCol = cursor.getColumnIndex("keyboard");
				String keyboardCode = cursor.getString(descCol);
				db.close();
				return keyboardCode;
			}
			db.close();
		}catch(Exception e){}
		return "";
	}

	public List<String> queryDictionary(String word) {
		List<String> result = new ArrayList<String>();
		try{
			//String value = "";
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
