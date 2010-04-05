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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;


/**
 * @author Art Hung
 */
public class LimeDB extends SQLiteOpenHelper {

	private static boolean DEBUG = false;
	
	private final static String DATABASE_NAME = "lime";
	private final static int DATABASE_VERSION = 39;
	private final static int DATABASE_RELATED_SIZE = 50;
	private final static String TOTAL_RECORD = "total_record";
	// Add by Jeremy '10, 3 ,27. Multi table extension.
	private final static String CJ_TOTAL_RECORD = "cj_total_record";
	private final static String BPMF_TOTAL_RECORD = "bpmf_total_record";
	private final static String DAYI_TOTAL_RECORD = "dayi_total_record";
	private final static String EZ_TOTAL_RECORD = "ez_total_record";
	private final static String RELATED_TOTAL_RECORD = "related_total_record";
	//----------add by Jeremy '10,3,12 ----------------------------------------
	private final static String TOTAL_USERDICT_RECORD = "total_userdict_record";
	//-------------------------------------------------------------------------
	private final static String MAPPING_FILE = "mapping_file";
	// Add by Jeremy '10, 3 ,27. Multi table extension.
	private final static String CJ_MAPPING_FILE = "cj_mapping_file";
	private final static String BPMF_MAPPING_FILE = "bpmf_mapping_file";
	private final static String DAYI_MAPPING_FILE = "dayi_mapping_file";
	private final static String EZ_MAPPING_FILE = "ez_mapping_file";
	private final static String RELATED_MAPPING_FILE = "dayi_mapping_file";
	// Add by Jeremy '10, 3 ,27. Multi table extension.
	private final static String MAPPING_FILE_TEMP = "mapping_file_temp";
	private final static String CJ_MAPPING_FILE_TEMP = "cj_mapping_file_temp";
	private final static String DAYI_MAPPING_FILE_TEMP = "dayi_mapping_file_temp";
	private final static String EZ_MAPPING_FILE_TEMP = "ez_mapping_file_temp";
	private final static String BPMF_MAPPING_FILE_TEMP = "bpmf_mapping_file_temp";
	private final static String RELATED_MAPPING_FILE_TEMP = "related_mapping_file_temp";
	private final static String MAPPING_VERSION = "mapping_version";
	// Add by Jeremy '10, 3 ,27. Multi table extension.
	private final static String CJ_MAPPING_VERSION = "cj_mapping_version";
	private final static String BPMF_MAPPING_VERSION = "bmpf_mapping_version";
	private final static String DAYI_MAPPING_VERSION = "dayi_mapping_version";
	private final static String EZ_MAPPING_VERSION = "ez_mapping_version";
	private final static String RELATED_MAPPING_VERSION = "related_mapping_version";
	private final static String MAPPING_LOADING = "mapping_loading";
	private final static String MAPPING_IMPORT_LINE = "mapping_import_line";
	private final static String CANDIDATE_SUGGESTION = "candidate_suggestion";
	private final static String LEARNING_SWITCH = "learning_switch";
	private final static String THREE_ROW_REMAP = "three_rows_remapping";

	//Add by Jeremy '10, 4, 1. For reverse lookup
	private final static String CJ_R_LOOKUP = "cj_im_reverselookup";
	private final static String DAYI_R_LOOKUP = "dayi_im_reverselookup";
	private final static String EZ_R_LOOKUP = "ez_im_reverselookup";
	private final static String BPMF_R_LOOKUP = "bpmf_im_reverselookup";
	private final static String DEFAULT_R_LOOKUP = "default_im_reverselookup";
	
	
	public final static String FIELD_id = "_id";
	public final static String FIELD_CODE = "code";
	public final static String FIELD_WORD = "word";
	public final static String FIELD_RELATED = "related";
	public final static String FIELD_SCORE = "score";

	public final static String FIELD_DIC_id = "_id";
	public final static String FIELD_DIC_pcode = "pcode";
	public final static String FIELD_DIC_pword = "pword";
	public final static String FIELD_DIC_ccode = "ccode";
	public final static String FIELD_DIC_cword = "cword";
	public final static String FIELD_DIC_score = "score";
	public final static String FIELD_DIC_is = "isDictionary";
	
	// for keyToChar
	public final static String BPMF_KEY = "1QAZ2WSX3EDC4RFV5TGB6YHN7UJM8IK,9OL.0P;/-";
	public final static String BPMF_CHAR = "ㄅㄆㄇㄈㄉㄊㄋㄌˇㄍㄎㄏˋㄐㄑㄒㄓㄗㄕㄖˊㄗㄘㄙ˙ㄧㄨㄩㄚㄛㄜㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦ";
	
	public final static String CJ_KEY = "QWERTYUIOPASDFGHJKLZXCVBNM";
	public final static String CJ_CHAR = "手田水口廿卜山戈人心日尸木火土竹十大中重難金女月弓一";
	
	// 3 rows remap
	
	public final static String THREE_ROW_KEY_REMAP = "1234567890;-";
	public final static String THREE_ROW_KEY = "QWERTYUIOP,V";
	

	public String DELIMITER = "";
	private String limit = "10";

	private File filename = null;
	private String tablename = "mapping";

	private int count = 0;
	private int relatedcount = 0;
	private boolean finish = false;
	private boolean relatedfinish = false;

	private Context ctx;
	
	// Db loading thread.
	private Thread thread = null;

	public File getFilename() {
		return filename;
	}

	public boolean isRelatedFinish() {
		return this.relatedfinish;
	}
	
	public boolean isFinish() {
		return this.finish;
	}

	public void setFilename(File filename) {
		this.filename = filename;
	}
	
	public void setTablename(String tablename){
		
		this.tablename = tablename;
		if(tablename.equals("cj")){
			SharedPreferences sp1 = ctx.getSharedPreferences(CJ_TOTAL_RECORD, 0);
			if(sp1.getString(CJ_TOTAL_RECORD, "0").equals("0")){
				this.tablename = "mapping";
			}
		}else if(tablename.equals("phonetic")){
			SharedPreferences sp2 = ctx.getSharedPreferences(BPMF_TOTAL_RECORD, 0);
			if(sp2.getString(BPMF_TOTAL_RECORD, "0").equals("0")){
				this.tablename = "mapping";
			}
		}else if(tablename.equals("dayi")){
			SharedPreferences sp3 = ctx.getSharedPreferences(DAYI_TOTAL_RECORD, 0);
			if(sp3.getString(DAYI_TOTAL_RECORD, "0").equals("0")){
				this.tablename = "mapping";
			}
		}else if(tablename.equals("ez")){
			SharedPreferences sp4 = ctx.getSharedPreferences(EZ_TOTAL_RECORD, 0);
			if(sp4.getString(EZ_TOTAL_RECORD, "0").equals("0")){
				this.tablename = "mapping";
			}
		}
		
		if(DEBUG){
			Log.i("setTablename", "tablename:" + tablename+ " this.tablename:"+ this.tablename);
		}
	
	}
	
	public String getTablename(){
		return this.tablename;
	}
		

	public LimeDB(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		this.ctx = context;
	}

	/**
	 * Create SQLite Database and create related tables
	 */
	@Override
	public void onCreate(SQLiteDatabase db) {

		db.execSQL("CREATE TABLE IF NOT EXISTS custom (" + FIELD_id
				+ " INTEGER primary key autoincrement, " + " " + FIELD_CODE
				+ " text, " + FIELD_WORD + " text, " + FIELD_SCORE
				+ " integer)");
		db.execSQL("CREATE INDEX IF NOT EXISTS custom_idx ON custom (" + FIELD_CODE + ")");

		db.execSQL("CREATE TABLE IF NOT EXISTS mapping (" + FIELD_id
				+ " INTEGER primary key autoincrement, " + " " + FIELD_CODE
				+ " text, " + FIELD_WORD + " text, " + FIELD_RELATED
				+ " text, " + FIELD_SCORE + " integer)");
		db.execSQL("CREATE INDEX IF NOT EXISTS mapping_idx_code ON mapping (" + FIELD_CODE + ")");
		db.execSQL("CREATE INDEX IF NOT EXISTS mapping_idx_word ON mapping (" + FIELD_CODE + ")");
		
		db.execSQL("CREATE TABLE IF NOT EXISTS cj (" + FIELD_id
				+ " INTEGER primary key autoincrement, " + " " + FIELD_CODE
				+ " text, " + FIELD_WORD + " text, " + FIELD_RELATED
				+ " text, " + FIELD_SCORE + " integer)");
		db.execSQL("CREATE INDEX IF NOT EXISTS cj_idx_code ON cj (" + FIELD_CODE + ")");
		db.execSQL("CREATE INDEX IF NOT EXISTS cj_idx_word ON cj (" + FIELD_WORD + ")");
		
		db.execSQL("CREATE TABLE IF NOT EXISTS dayi (" + FIELD_id
				+ " INTEGER primary key autoincrement, " + " " + FIELD_CODE
				+ " text, " + FIELD_WORD + " text, " + FIELD_RELATED
				+ " text, " + FIELD_SCORE + " integer)");
		db.execSQL("CREATE INDEX IF NOT EXISTS dayi_idx_code ON dayi (" + FIELD_CODE + ")");
		db.execSQL("CREATE INDEX IF NOT EXISTS dayi_idx_word ON dayi (" + FIELD_WORD + ")");
		
		db.execSQL("CREATE TABLE IF NOT EXISTS ez (" + FIELD_id
				+ " INTEGER primary key autoincrement, " + " " + FIELD_CODE
				+ " text, " + FIELD_WORD + " text, " + FIELD_RELATED
				+ " text, " + FIELD_SCORE + " integer)");
		db.execSQL("CREATE INDEX IF NOT EXISTS ez_idx_code ON ez (" + FIELD_CODE + ")");
		db.execSQL("CREATE INDEX IF NOT EXISTS ez_idx_word ON ez (" + FIELD_WORD + ")");

		db.execSQL("CREATE TABLE IF NOT EXISTS phonetic (" + FIELD_id
				+ " INTEGER primary key autoincrement, " + " " + FIELD_CODE
				+ " text, " + FIELD_WORD + " text, " + FIELD_RELATED
				+ " text, " + FIELD_SCORE + " integer)");
		db.execSQL("CREATE INDEX IF NOT EXISTS phonetic_idx_code ON phonetic (" + FIELD_CODE + ")");
		db.execSQL("CREATE INDEX IF NOT EXISTS phonetic_idx_word ON phonetic (" + FIELD_CODE + ")");
	
		db.execSQL("CREATE TABLE IF NOT EXISTS related(" 
				+ FIELD_DIC_id 	+ " INTEGER primary key autoincrement, " 
				//+ FIELD_DIC_pcode + " text, " + FIELD_DIC_ccode + " text, "
				+ FIELD_DIC_cword + " text, " + FIELD_DIC_pword + " text, "
				+ FIELD_DIC_score + " integer)");
		db.execSQL("CREATE INDEX IF NOT EXISTS related_idx_pword ON related (" + FIELD_DIC_pword + ")");
		//db.execSQL("CREATE INDEX IF NOT EXISTS related_idx_cword ON related (" + FIELD_DIC_cword + ")");

	}

	/**
	 * Upgrade current database
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		/*
		db.execSQL("DROP TABLE IF EXISTS custom");
		db.execSQL("DROP INDEX IF EXISTS custom_idx");
		db.execSQL("DROP TABLE IF EXISTS mapping");
		db.execSQL("DROP INDEX IF EXISTS mapping_idx_code");
		db.execSQL("DROP INDEX IF EXISTS mapping_idx_word");
		db.execSQL("DROP TABLE IF EXISTS cj");
		db.execSQL("DROP INDEX IF EXISTS cj_idx_code");
		db.execSQL("DROP INDEX IF EXISTS cj_idx_word");
		db.execSQL("DROP TABLE IF EXISTS dayi");
		db.execSQL("DROP INDEX IF EXISTS dayi_idx_code");
		db.execSQL("DROP INDEX IF EXISTS dayi_idx_word");
		db.execSQL("DROP TABLE IF EXISTS phonetic");
		db.execSQL("DROP INDEX IF EXISTS phonetic_idx_code");
		db.execSQL("DROP INDEX IF EXISTS phonetic_idx_word");
		db.execSQL("DROP TABLE IF EXISTS userdic");
		db.execSQL("DROP INDEX IF EXISTS userdic_idx");
		db.execSQL("DROP INDEX IF EXISTS userdic_idx_pcode");
		db.execSQL("DROP INDEX IF EXISTS userdic_idx_pword");
		*/
		db.execSQL("DROP INDEX IF EXISTS mapping_idx");
		db.execSQL("DROP INDEX IF EXISTS userdic_idx");
		db.execSQL("CREATE TABLE IF NOT EXISTS userdic(" + FIELD_DIC_id
				+ " INTEGER primary key autoincrement, " + " "
				+ FIELD_DIC_pcode + " text, " + FIELD_DIC_ccode + " text, "
				+ FIELD_DIC_cword + " text, " + FIELD_DIC_pword + " text, "
				+ FIELD_DIC_score + " integer)");
		db.execSQL("DROP TABLE IF EXISTS related");
		db.execSQL("CREATE TABLE related(" 
				+ FIELD_DIC_id 	+ " INTEGER primary key autoincrement, " 
				+ FIELD_DIC_cword + " text, " + FIELD_DIC_pword + " text, "
				+ FIELD_DIC_score + " integer)");
		db.execSQL("INSERT INTO related ("
				+ FIELD_DIC_pword + ", " + FIELD_DIC_cword + ", " + FIELD_DIC_score + ")"
				+ " SELECT "
				+ FIELD_DIC_pword + ", " + FIELD_DIC_cword + ", " + FIELD_DIC_score + "+1"
				+ " FROM userdic"
				);
		db.execSQL("CREATE INDEX IF NOT EXISTS related_idx_pword ON related (" + FIELD_DIC_pword + ")");
		// Drop old userdic table
		db.execSQL("DROP TABLE IF EXISTS userdic");
		db.execSQL("DROP INDEX IF EXISTS userdic_idx");
		db.execSQL("DROP INDEX IF EXISTS userdic_idx_pcode");
		db.execSQL("DROP INDEX IF EXISTS userdic_idx_pword");

		onCreate(db);
	}

	/**
	 * Empty database records
	 */
	public void deleteAll(String table) {
		
		//stop thread first.
		if(thread!= null){
			thread.stop();
			thread =null;
		}

		SQLiteDatabase db = this.getWritableDatabase();
		//db.delete("mapping", null, null);
		
		db.execSQL("DROP TABLE IF EXISTS " + table);
		db.execSQL("DROP INDEX IF EXISTS " + table + "_idx_code");
		db.execSQL("DROP INDEX IF EXISTS " + table + "_idx_word");
		db.execSQL("CREATE TABLE " + table + " (" + FIELD_id
				+ " INTEGER primary key autoincrement, " + " " + FIELD_CODE
				+ " text, " + FIELD_WORD + " text, " + FIELD_RELATED
				+ " text, " + FIELD_SCORE + " integer)");
		db.execSQL("CREATE INDEX " + table + "_idx_code ON " + table + " (" + FIELD_CODE + ")");
		db.execSQL("CREATE INDEX " + table + "_idx_word ON " + table + " (" + FIELD_WORD + ")");
		
		db.close();
		
		SharedPreferences sp1=null, sp2=null, sp3=null, sp4=null;
		
		if(table.equals("cj")){
			sp1 = ctx.getSharedPreferences(CJ_TOTAL_RECORD, 0);
			sp1.edit().putString(CJ_TOTAL_RECORD, String.valueOf(0)).commit();
			sp2 = ctx.getSharedPreferences(CJ_MAPPING_VERSION, 0);
			sp2.edit().putString(CJ_MAPPING_VERSION, "").commit();
			sp3 = ctx.getSharedPreferences(CJ_MAPPING_FILE, 0);
			sp3.edit().putString(CJ_MAPPING_FILE, "").commit();
			sp4 = ctx.getSharedPreferences(CJ_MAPPING_FILE_TEMP, 0);
			sp4.edit().putString(CJ_MAPPING_FILE_TEMP, "").commit();
		}else if(table.equals("dayi")){
			sp1 = ctx.getSharedPreferences(DAYI_TOTAL_RECORD, 0);
			sp1.edit().putString(DAYI_TOTAL_RECORD, String.valueOf(0)).commit();
			sp2 = ctx.getSharedPreferences(DAYI_MAPPING_VERSION, 0);
			sp2.edit().putString(DAYI_MAPPING_VERSION, "").commit();
			sp3 = ctx.getSharedPreferences(DAYI_MAPPING_FILE, 0);
			sp3.edit().putString(DAYI_MAPPING_FILE, "").commit();
			sp4 = ctx.getSharedPreferences(DAYI_MAPPING_FILE_TEMP, 0);
			sp4.edit().putString(DAYI_MAPPING_FILE_TEMP, "").commit();
		}else if(table.equals("phonetic")){
			sp1 = ctx.getSharedPreferences(BPMF_TOTAL_RECORD, 0);
			sp1.edit().putString(BPMF_TOTAL_RECORD, String.valueOf(0)).commit();
			sp2 = ctx.getSharedPreferences(BPMF_MAPPING_VERSION, 0);
			sp2.edit().putString(BPMF_MAPPING_VERSION, "").commit();
			sp3 = ctx.getSharedPreferences(BPMF_MAPPING_FILE, 0);
			sp3.edit().putString(BPMF_MAPPING_FILE, "").commit();
			sp4 = ctx.getSharedPreferences(BPMF_MAPPING_FILE_TEMP, 0);
			sp4.edit().putString(BPMF_MAPPING_FILE_TEMP, "").commit();
		}else if(table.equals("ez")){
			sp1 = ctx.getSharedPreferences(EZ_TOTAL_RECORD, 0);
			sp1.edit().putString(EZ_TOTAL_RECORD, String.valueOf(0)).commit();
			sp2 = ctx.getSharedPreferences(EZ_MAPPING_VERSION, 0);
			sp2.edit().putString(EZ_MAPPING_VERSION, "").commit();
			sp3 = ctx.getSharedPreferences(EZ_MAPPING_FILE, 0);
			sp3.edit().putString(EZ_MAPPING_FILE, "").commit();
			sp4 = ctx.getSharedPreferences(EZ_MAPPING_FILE_TEMP, 0);
			sp4.edit().putString(EZ_MAPPING_FILE_TEMP, "").commit();
		}else {
			sp1 = ctx.getSharedPreferences(TOTAL_RECORD, 0);
			sp1.edit().putString(TOTAL_RECORD, String.valueOf(0)).commit();
			sp2 = ctx.getSharedPreferences(MAPPING_VERSION, 0);
			sp2.edit().putString(MAPPING_VERSION, "").commit();
			sp3 = ctx.getSharedPreferences(MAPPING_FILE, 0);
			sp3.edit().putString(MAPPING_FILE, "").commit();
			sp4 = ctx.getSharedPreferences(MAPPING_FILE_TEMP, 0);
			sp4.edit().putString(MAPPING_FILE_TEMP, "").commit();
		}
		
		//SharedPreferences sp5 = ctx.getSharedPreferences(MAPPING_LOADING, 0);
		//sp4.edit().putString(MAPPING_LOADING, "no").commit();
		SharedPreferences sp5 = ctx.getSharedPreferences(MAPPING_IMPORT_LINE, 0);
		sp5.edit().putString(MAPPING_IMPORT_LINE, "").commit();

						  
		count = 0;
		relatedcount = 0;
		finish = false;
		relatedfinish = false;
	}

	/**
	 * Empty Dictionary table records
	 */
	public void deleteRelatedAll() {

		SharedPreferences sp1 = ctx.getSharedPreferences(RELATED_TOTAL_RECORD, 0);
		sp1.edit().putString(RELATED_TOTAL_RECORD, String.valueOf(0)).commit();
		SharedPreferences sp2 = ctx.getSharedPreferences(RELATED_MAPPING_VERSION, 0);
		sp2.edit().putString(RELATED_MAPPING_VERSION, "").commit();
		SharedPreferences sp3 = ctx.getSharedPreferences(RELATED_MAPPING_FILE, 0);
		sp3.edit().putString(RELATED_MAPPING_FILE, "").commit();
		SharedPreferences sp4 = ctx.getSharedPreferences(RELATED_MAPPING_FILE_TEMP, 0);
		sp4.edit().putString(RELATED_MAPPING_FILE_TEMP, "").commit();
		
		SQLiteDatabase db = this.getWritableDatabase();
		
		db.delete("related", FIELD_DIC_score + " = 0", null);
		db.close();
	}
	
	/**
	 * Empty Related table records
	 */
	public void  deleteDictionaryAll() { 
		//---------------add by Jeremy '10,3,12-----------------------------------
		SharedPreferences sp1 = ctx.getSharedPreferences(TOTAL_USERDICT_RECORD, 0);
		sp1.edit().putString(TOTAL_USERDICT_RECORD, String.valueOf(0)).commit();
		//-------------------------------------------------------------------------
		SQLiteDatabase db = this.getWritableDatabase();
		//db.delete("userdic", null, null);
		/*
		db.execSQL("DROP TABLE IF EXISTS userdic");
		db.execSQL("DROP INDEX IF EXISTS userdic_idx_pcode");
		db.execSQL("DROP INDEX IF EXISTS userdic_idx_pword");
		db.execSQL("CREATE TABLE userdic(" + FIELD_DIC_id
				+ " INTEGER primary key autoincrement, " + " "
				+ FIELD_DIC_pcode + " text, " + FIELD_DIC_ccode + " text, "
				+ FIELD_DIC_pword + " text, " + FIELD_DIC_cword + " text, "
				+ FIELD_DIC_score + " integer)");
		db.execSQL("CREATE INDEX userdic_idx_pcode ON userdic (" + FIELD_DIC_pcode + ")");
		db.execSQL("CREATE INDEX userdic_idx_pword ON userdic (" + FIELD_DIC_pword + ")");
		*/
		db.delete("related", FIELD_DIC_score + " >0", null);
		db.close();
	}

	
	public int getRelatedCount() {
		return relatedcount;
	}
	
	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}
	
	/**
	 * Count total amount loaded records amount
	 * 
	 * @return
	 */
	public int countMapping(String table) {

		int total = 0;
		try {
			SQLiteDatabase db = this.getReadableDatabase();
			total += db.rawQuery("SELECT * FROM " + table, null).getCount();
			db.close();
			Log.i("countMapping", "Table," + table +": " + total);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return total;
	}
	public int countRelated() {

		int total = 0;
		try {
			SQLiteDatabase db = this.getReadableDatabase();
			total += db.rawQuery("SELECT * FROM related where " + FIELD_DIC_score + " = 0" , null).getCount();
			db.close();
			Log.i("countRelated", "Total related records: " + total);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return total;
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
			total += db.rawQuery("SELECT * FROM related where " + FIELD_DIC_score + " > 0" , null).getCount();
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
					code = code.toUpperCase();
				}
				if (word == null || word.trim().equals("")) {
					continue;
				}
				if (code.equalsIgnoreCase("@VERSION@")) {
					SharedPreferences version = ctx.getSharedPreferences(
							MAPPING_VERSION, 0);
					version.edit().putString(MAPPING_VERSION, word.trim())
							.commit();
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
		// Update Total Record
		SharedPreferences storeset = ctx.getSharedPreferences(TOTAL_RECORD, 0);
		storeset.edit().putString(TOTAL_RECORD, String.valueOf(count)).commit();

	}

	/**
	 * Create dictionary database
	 * 
	 * @param srclist
	 */
	public void addDictionary(List<Mapping> srclist) {
		
		if(DEBUG){
			Log.i("addDictionary:", "Etnering addDictionary");
		}
		
		int dictotal = 0;
		try {
			// modified by Jeremy '10, 3,12
			//dictotal = limedb.countUserdic();
			SharedPreferences settings = ctx.getSharedPreferences(TOTAL_USERDICT_RECORD, 0);
			String recordString = settings.getString(TOTAL_USERDICT_RECORD, "0");
			dictotal = Integer.parseInt(recordString);
		} catch (Exception e) {}
		
		
		// Check if build related word enable. 
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		if( !sp.getBoolean(CANDIDATE_SUGGESTION, false) ){
			if(DEBUG){
				Log.i("addDictionary:", "CANDIDATE_SUGGESTION:false returning...");
			}
			return;
		}
		
		if (srclist != null && srclist.size() > 0) {

			try {
				SQLiteDatabase db = this.getWritableDatabase();
				for (int i = 0; i < srclist.size(); i++) {

					Mapping unit = srclist.get(i);
					// Out of bound exception here!!
					//Mapping unit2 = srclist.get((i + 1));
					//if (unit2 != null) {
					if(i+1 <srclist.size()){
						Mapping unit2 = srclist.get((i + 1));
						
						if (unit != null && unit.getCode().length() > 0
								&& unit2 != null
								&& unit2.getCode().length() > 0) {

							// Log.i("ART","unit 4->"+unit2);
							if (!this.isExists(unit.getWord(),unit2.getWord())) {

								try {
									// Log.i("ART","unit 5->"+unit2);
									ContentValues cv = new ContentValues();
									//--------------------------------------------
									// Modified by Jeremy '10,03,20 replace pcode, code with hashcode
									// '10, 3, 30. pcode and code no more used.
									//cv.put(FIELD_DIC_pcode, unit.getCode());
									//cv.put(FIELD_DIC_pcode, unit.getWord().hashCode());
									//cv.put(FIELD_DIC_pcode, "-");
									cv.put(FIELD_DIC_pword, unit.getWord());
									//cv.put(FIELD_DIC_ccode, unit2.getCode());
									//cv.put(FIELD_DIC_ccode, "-");
									cv.put(FIELD_DIC_cword, unit2.getWord());
									//-------------------------------------------------------
									// Modified by jeremy '10, 3, 29. score 0->1.  Built-in phrase has score at 0. 
									// All userdict will have score >0 and build with score 1. 
									cv.put(FIELD_DIC_score, 1);

									db.insert("related", null, cv);
									dictotal++;
									// Log.i("ART","unit 6->"+unit2);
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				SharedPreferences sp1 = ctx.getSharedPreferences(TOTAL_USERDICT_RECORD, 0);
				sp1.edit().putString(TOTAL_USERDICT_RECORD, String.valueOf(dictotal)).commit();
				if(DEBUG){
					Log.i("addDictionary:", "update userdict total records:" + dictotal);
				}
			}
		}
	}
	
	// Add by jeremy '10, 4, 1.  For reverse lookup
	/**
	 * Reverse lookup on keyword.
	 * @param keyword
	 * @return
	 */
	public String getRMapping(String keyword) {
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		String Rtable = new String("none");
		if(tablename.equals("cj")){	
			Rtable = sp.getString(CJ_R_LOOKUP, "none");	
		}else if(tablename.equals("dayi")){ 
			Rtable = sp.getString(DAYI_R_LOOKUP, "none");
		}else if(tablename.equals("phonetic")){
			Rtable = sp.getString(BPMF_R_LOOKUP, "none");
		}else if(tablename.equals("ez")){
			Rtable = sp.getString(EZ_R_LOOKUP, "none");
		}else {
			Rtable = sp.getString(DEFAULT_R_LOOKUP, "none");
		}
		if(Rtable.equals("none")) { return null ;}
		
		String result = new String("");
		try {
			// Create Suggestions (Exactly Matched)
			if (keyword != null && !keyword.trim().equals("")) {
				Cursor cursor = null;
				SQLiteDatabase db = this.getReadableDatabase();
				cursor = db.query(Rtable, null, FIELD_WORD + " = \"" + keyword + "\"", null, null, null, null, null);
				if(DEBUG){
					Log.i("getRmapping","tablename:"+Rtable+"  keyworad:"+keyword+"  cursor.getCount:" + cursor.getCount());
				}
				
				if (cursor.moveToFirst()) {
					int codeColumn = cursor.getColumnIndex(FIELD_CODE);
					int wordColumn = cursor.getColumnIndex(FIELD_WORD);
					//int idColumn = cursor.getColumnIndex(FIELD_id);
					result = cursor.getString(wordColumn)+ "=" + keyToChar(cursor.getString(codeColumn), Rtable);
					if(DEBUG){
						Log.i("getRmapping","Code:"+cursor.getString(codeColumn));
					}
					
					while (cursor.moveToNext()){
						result = result + "; " + keyToChar(cursor.getString(codeColumn), Rtable);
						if(DEBUG){
							Log.i("getRmapping","Code:"+cursor.getString(codeColumn));
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
		
		if(DEBUG){
			Log.i("getRmapping","Result:" + result);
		}
		
		return result;
	}
	
	public String keyToChar(String code, String Rtable){
		String result = new String("");
		if(Rtable.equals("cj")){	
			int i, j;
			for(i=0;i<code.length();i++){
				for(j=0;j < CJ_KEY.length() ; j++ ){
					if(code.substring(i, i+1).equals(CJ_KEY.substring(j, j+1))){
						result=result+ CJ_CHAR.substring(j, j+1);
						break;
					}
				}
					
			}
		}else if(Rtable.equals("dayi")){ 
			result = code;
		}else if(Rtable.equals("ez")){ 
			result = code;
		}else if(Rtable.equals("phonetic")){
			int i, j;
			for(i=0;i<code.length();i++){
				for(j=0;j < BPMF_KEY.length() ; j++ ){
					if(code.substring(i, i+1).equals(BPMF_KEY.substring(j, j+1))){
						result=result+ BPMF_CHAR.substring(j, j+1);
						break;
					}
				}
					
			}
			
		}else {
			result = code;

		}
		return result;
	}
	
	private void buildQueryCodeList(String code, int relatedCodeLimit){
		
		SQLiteDatabase db = this.getWritableDatabase();
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		boolean remap = sp.getBoolean(THREE_ROW_REMAP, false);
		String sql =null;
		int similarCodeDepth = 3;
		
		try {
			db.execSQL("DROP TABLE IF EXISTS queryCodeList");
			db.execSQL("CREATE TEMP TABLE queryCodeList (" + FIELD_CODE + ")");
			//db.delete("queryCodeList", null, null);
			if(!remap) {//Perfomance issue! Suppose all IM has code length <= 5.
				if(code.length() > similarCodeDepth){
					sql = "INSERT INTO queryCodeList (" + FIELD_CODE + ") "
					+"SELECT DISTINCT " + FIELD_CODE + " FROM " + tablename  
					+" WHERE "  + FIELD_CODE + " = '" + code + "'";
				}else{
					sql = "INSERT INTO queryCodeList (" + FIELD_CODE + ") "
					+"SELECT DISTINCT " + FIELD_CODE + " FROM " + tablename  
					+" WHERE "  + FIELD_CODE + " LIKE '" + code + "%' LIMIT " + relatedCodeLimit;
				}
				
				db.execSQL(sql);
				
				if(DEBUG){
					Log.i("buildQueryCodeList", "SQL statement:" + sql);
				}
				
				return;
				
			}
			//db.execSQL("CREATE TEMP TABLE queryCodeList (" + FIELD_CODE + ")");
		}catch(Exception e){
			e.printStackTrace();
		}
		if(DEBUG){
			Log.i("buildQueryCodeList", "code:" + code);
		}
		
		List<String> result = new ArrayList<String>();
		result.add(code);
		try{
			if(code.length() > similarCodeDepth){
				sql = "INSERT INTO queryCodeList (" + FIELD_CODE + ") "
					+"SELECT DISTINCT " + FIELD_CODE + " FROM " + tablename  
					+" WHERE "  + FIELD_CODE + " = '" + code + "'";				
			}else{
				sql = "INSERT INTO queryCodeList (" + FIELD_CODE + ") "
					+"SELECT DISTINCT " + FIELD_CODE + " FROM " + tablename  
					+" WHERE "  + FIELD_CODE + " LIKE '" + code + "%'"
					+ " LIMIT " + relatedCodeLimit;	
			}
				
			db.execSQL(sql);
			if(DEBUG){
				Log.i("buildQueryCodeList", "SQL statement:" + sql);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		
		int i, j, k;
		for(i=0;i<code.length();i++){
			for(j=0;j< THREE_ROW_KEY.length(); j++){
				int size=0;
				if(code.substring(i,i+1).equals(THREE_ROW_KEY.substring(j, j+1)))
					size = result.size();
					for(k=0; k < size ;k++){
						String replacement = new String (
								result.get(k).substring(0, i) 
								+ THREE_ROW_KEY_REMAP.substring(j, j+1)
								+ result.get(k).substring(i+1));
						result.add(replacement);
						try{
							if(code.length() > similarCodeDepth){
								sql = "INSERT INTO queryCodeList (" + FIELD_CODE + ") "
									+"SELECT DISTINCT " + FIELD_CODE + " FROM " + tablename  
									+" WHERE "  + FIELD_CODE + " = '" + replacement + "'";			
							}else{
								sql = "INSERT INTO queryCodeList (" + FIELD_CODE + ") "
									+"SELECT DISTINCT " + FIELD_CODE + " FROM " + tablename  
									+" WHERE "  + FIELD_CODE + " LIKE '" + replacement + "%'"
									+ " LIMIT " + relatedCodeLimit;
							}
							db.execSQL(sql);
						}catch(Exception e){
							e.printStackTrace();
						}
						if(DEBUG){
							Log.i("buildQueryCodeList", "add remap:" + replacement);
						}
					}
			}
			
		}
		/*
		for(i=0;i<result.size();i++){
			try{
				
				String sql = new String(
						"INSERT INTO queryCodeList (" + FIELD_CODE + ") "
						+"SELECT DISTINCT " + FIELD_CODE + " FROM " + tablename  
						+" WHERE "  + FIELD_CODE + " LIKE '" + result.get(i) + "%'"
						+" AND "  + FIELD_CODE + " <> '" + result.get(i) +"'"
						+ " LIMIT " + relatedCodeLimit);
				db.execSQL(sql);
				if(DEBUG){
					Log.i("buildQueryCodeList", "SQL statement:" + sql);
				}
				
			}catch(Exception e){
				e.printStackTrace();
			}
			
		}
		*/
		result = null;
		return;
		
	}
private void prepareQuery(String code, int relatedCodeLimit){
		
		SQLiteDatabase db = this.getWritableDatabase();
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
		boolean remap = sp.getBoolean(THREE_ROW_REMAP, false);
		
		try {
			db.execSQL("DROP TABLE IF EXISTS prepare");
			db.execSQL("CREATE TEMP TABLE prepare" +
					"( localid INTEGER primary key autoincrement, "
					+ FIELD_id + " integer, " 
					+ FIELD_CODE + " text, " + FIELD_WORD + " text, " 
					+ FIELD_SCORE + " integer)"
					);
			//db.delete("queryCodeList", null, null);
			if(code.length() > 5 || !remap) {//Perfomance issue! Suppose all IM has code length <= 5.
				String sql = new String(
						"INSERT INTO prepare (" 
						+ FIELD_id + "," + FIELD_CODE + "," + FIELD_WORD +  "," + FIELD_SCORE +")"
						+" SELECT "  
						+ FIELD_id + "," + FIELD_CODE + "," + FIELD_WORD +  "," + FIELD_SCORE 
						+" FROM " + tablename  
						+" WHERE "  + FIELD_CODE + " IN "
						+"(SELECT " + FIELD_CODE + " FROM " + tablename 
						+" WHERE " + FIELD_CODE + " LIKE '" + code + "%' LIMIT " + relatedCodeLimit + ")"
						);	
				db.execSQL(sql);
				
				if(DEBUG){
					Log.i("prepareQuery", "SQL statement:" + sql);
				}
				
				return;
				
			}
			//db.execSQL("CREATE TEMP TABLE queryCodeList (" + FIELD_CODE + ")");
		}catch(Exception e){
			e.printStackTrace();
		}
		if(DEBUG){
			Log.i("prepareQuery", "code:" + code);
		}
		
		List<String> result = new ArrayList<String>();
		result.add(code);
		try{
			String sql = new String(
					"INSERT INTO prepare (" 
					+ FIELD_id + "," + FIELD_CODE + "," + FIELD_WORD +  "," + FIELD_SCORE +")"
					+" SELECT "  
					+ FIELD_id + "," + FIELD_CODE + "," + FIELD_WORD +  "," + FIELD_SCORE 
					+" FROM " + tablename  
					+" WHERE "  + FIELD_CODE + " = '" + code + "'"
					);	
			db.execSQL(sql);
			if(DEBUG){
				Log.i("prepareQuery", "SQL statement:" + sql);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		
		int i, j, k;
		for(i=0;i<code.length();i++){
			for(j=0;j< THREE_ROW_KEY.length(); j++){
				int size=0;
				if(code.substring(i,i+1).equals(THREE_ROW_KEY.substring(j, j+1)))
					size = result.size();
					for(k=0; k < size ;k++){
						String replacement = new String (
								result.get(k).substring(0, i) 
								+ THREE_ROW_KEY_REMAP.substring(j, j+1)
								+ result.get(k).substring(i+1));
						result.add(replacement);
						try{
							String sql = new String(
									"INSERT INTO prepare (" 
									+ FIELD_id + "," + FIELD_CODE + "," + FIELD_WORD +  "," + FIELD_SCORE +")"
									+" SELECT "  
									+ FIELD_id + "," + FIELD_CODE + "," + FIELD_WORD +  "," + FIELD_SCORE 
									+" FROM " + tablename  
									+" WHERE "  + FIELD_CODE + " = '" + replacement + "'"
									);
							if(DEBUG){
								Log.i("buildQueryCodeList", "SQL statement:" + sql);
							}
						}catch(Exception e){
							e.printStackTrace();
						}
						if(DEBUG){
							Log.i("threeRowRemap", "add remap:" + replacement);
						}
					}
			}
			
		}
		for(i=0;i<result.size();i++){
			try{
				
				String sql = new String(
						"INSERT INTO prepare (" 
						+ FIELD_id + "," + FIELD_CODE + "," + FIELD_WORD +  "," + FIELD_SCORE +")"
						+" SELECT "  
						+ FIELD_id + "," + FIELD_CODE + "," + FIELD_WORD +  "," + FIELD_SCORE 
						+" FROM " + tablename  
						+" WHERE "  + FIELD_CODE + " IN "
						+"(SELECT " + FIELD_CODE + " FROM " + tablename 
						+" WHERE " + FIELD_CODE + " LIKE '" + result.get(i)
						+"%' AND " + FIELD_CODE + " <> '" + result.get(i)
						+ "' LIMIT " + relatedCodeLimit + ")"
						);	
				db.execSQL(sql);
				if(DEBUG){
					Log.i("buildQueryCodeList", "SQL statement:" + sql);
				}
				
			}catch(Exception e){
				e.printStackTrace();
			}
			
		}
		result = null;
		return;
		
	}
	public List<Mapping> getMapping(String keyword, int relatedCodeLimit ) {
		
		// Add by Jeremy '10, 3, 27. Extension on multi table query.
		
		
		List<Mapping> result = new LinkedList<Mapping>();
		if (keyword != null && !keyword.trim().equals("")) {

			Cursor cursor = null;
			String sql = null;
			
			SQLiteDatabase db = this.getReadableDatabase();
			SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
			boolean item = sp.getBoolean(LEARNING_SWITCH, false);
			try {
				if(false){
				prepareQuery(keyword, relatedCodeLimit);
				
				if(item){
					sql = new String(  
							"SELECT * FROM prepare ORDER BY " 
							+ FIELD_SCORE + " DESC, localid");	
				}else{
					sql = new String( 
							"SELECT * FROM prepare ORDER BY localid");
				}	
				}else{
				buildQueryCodeList(keyword, relatedCodeLimit);
				if(item){
					sql = new String(  
							"SELECT * FROM " + tablename + " WHERE " + FIELD_CODE + " in (SELECT " 
							+ FIELD_CODE + " FROM queryCodeList) ORDER BY " 
							+ FIELD_SCORE + " DESC, LENGTH( " + FIELD_CODE + ")");	
				}else{
					sql = new String( 
							"SELECT * FROM " + tablename + " WHERE " + FIELD_CODE + " IN (SELECT " 
							+ FIELD_CODE + " FROM queryCodeList) ORDER BY LENGTH( " + FIELD_CODE + ")," );  
				}
				}
				cursor = db.rawQuery(sql ,null);
				if(DEBUG){
					Log.i("Query","SQL statement:"+ sql);
					Log.i("Query","tablename:"+tablename+"  keyworad:"+keyword+"  cursor.getCount:"+cursor.getCount());
				}
			
				if (cursor.moveToFirst()) {
					int codeColumn = cursor.getColumnIndex(FIELD_CODE);
					int wordColumn = cursor.getColumnIndex(FIELD_WORD);
					int scoreColumn = cursor.getColumnIndex(FIELD_SCORE);
					//int relatedColumn = cursor.getColumnIndex(FIELD_RELATED);
					int idColumn = cursor.getColumnIndex(FIELD_id);
					do {
						Mapping munit = new Mapping();
						munit.setId(cursor.getString(idColumn));
						munit.setCode(cursor.getString(codeColumn));
						munit.setWord(cursor.getString(wordColumn));
						//munit.setRelated(cursor.getString(relatedColumn));
						munit.setScore(cursor.getInt(scoreColumn));
						munit.setDictionary(false);
						result.add(munit);
					} while (cursor.moveToNext());
				}

				if (cursor != null) {
					cursor.deactivate();
					cursor.close();
				}
			}catch(Exception e){
				e.printStackTrace();
			}
				
		}
		return result;
	}

	public List<Mapping> getSuggestion(String related, int size) {

		List<Mapping> result = new LinkedList<Mapping>();

		try {
			// Create Suggestions (Exactly Matched)
			if (related != null && !related.trim().equals("")) {

				Cursor cursor = null;

				try {
					related = related.toUpperCase();
				} catch (Exception e) {
					e.printStackTrace();
				}

				String where = "";

				String klist[] = related.split("\t");
				for (int i = 0; i < klist.length; i++) {
					String item = klist[i];
					// Jeremy '10, 4, 2.  change " to '
					where += " " + FIELD_CODE + " = '" + item + "' OR";
					if (i == size) {
						break;
					}
				}

				if (where.endsWith("OR")) {
					where = where.substring(0, (where.length() - 2));
				}

				if (!where.equals("")) {
					SQLiteDatabase db = this.getReadableDatabase();

					SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
					boolean item = sp.getBoolean(LEARNING_SWITCH, false);
					
					if(item){
						//cursor = db.query("mapping", null, where, null, null, null, FIELD_SCORE + " DESC", String.valueOf(size));
						cursor = db.query(tablename, null, where, null, null, null, FIELD_SCORE + " DESC", String.valueOf(size));
					}else{
						//cursor = db.query("mapping", null, where, null, null, null, null, String.valueOf(size));
						cursor = db.query(tablename, null, where, null, null, null, null, String.valueOf(size));
					}
					if (cursor.moveToFirst()) {
						int codeColumn = cursor.getColumnIndex(FIELD_CODE);
						int wordColumn = cursor.getColumnIndex(FIELD_WORD);
						int scoreColumn = cursor.getColumnIndex(FIELD_SCORE);
						int idColumn = cursor.getColumnIndex(FIELD_id);
						do {
							Mapping munit = new Mapping();
							munit.setId(cursor.getString(idColumn));
							munit.setCode(cursor.getString(codeColumn));
							munit.setWord(cursor.getString(wordColumn));
							munit.setScore(cursor.getInt(scoreColumn));
							munit.setDictionary(false);
							result.add(munit);
						} while (cursor.moveToNext());
					}

					if (cursor != null) {
						cursor.deactivate();
						cursor.close();
					}
				}

			}
		} catch (Exception e) {
		}
		return result;
	}

	/**
	 * Get dictionary database contents
	 * 
	 * @param keyword
	 * @return
	 */
	//Modified by Jeremy '10,3 ,12 for more specific related word
	//-----------------------------------------------------------
	public List<Mapping> getDictionary(String pcode, String pword) {
	//-----------------------------------------------------------

		List<Mapping> result = new LinkedList<Mapping>();

		// Create Suggestions (Exactly Matched)
		// Modified by Jeremy '10, 4, 1.  pcode->pword
		if (pword != null && !pword.trim().equals("")) {

			Cursor cursor = null;
			/*
			try {
				pcode = pcode.toUpperCase();
			} catch (Exception e) {
				e.printStackTrace();
			}
			*/
			//Modified by Jeremy '10,3 ,20 for more specific related word
			// Replace pcode with hascode() for better perfoamnce and IM indepedent.
			//-----------------------------------------------------------

			SQLiteDatabase db = this.getReadableDatabase();
			//cursor = db.query("userdic", null, FIELD_DIC_pcode + " = \""
			//		+ keyword + "\"", null, null, null, FIELD_DIC_score
			
			cursor = db.query("related", null,
					FIELD_DIC_pword + " = \"" + pword + "\"" 
					, null, null, null, FIELD_DIC_score
					+ " DESC", null);
			
			//-----------------------------------------------------------


			if (cursor.moveToFirst()) {
				//int pcodeColumn = cursor.getColumnIndex(FIELD_DIC_pcode);
				int pwordColumn = cursor.getColumnIndex(FIELD_DIC_pword);
				//int ccodeColumn = cursor.getColumnIndex(FIELD_DIC_ccode);
				int cwordColumn = cursor.getColumnIndex(FIELD_DIC_cword);
				int scoreColumn = cursor.getColumnIndex(FIELD_DIC_score);
				int idColumn = cursor.getColumnIndex(FIELD_id);
				do {
					Mapping munit = new Mapping();
					munit.setId(cursor.getString(idColumn));
					//munit.setPcode(cursor.getString(pcodeColumn));
					munit.setPword(cursor.getString(pwordColumn));
					//munit.setCode(cursor.getString(ccodeColumn));
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
	 * Add score to the mapping item
	 * 
	 * @param srcunit
	 */
	public void addScore(Mapping srcunit) {

		String id = srcunit.getId();
		String code = srcunit.getCode();
		String word = srcunit.getWord();
		//String pcode = srcunit.getPcode();
		String pword = srcunit.getPword();

		int score = srcunit.getScore();
		try {
			if (word != null  
					&& !word.trim().equals("")) {

				score++;

				if (srcunit.isDictionary()) {
					ContentValues cv = new ContentValues();
					cv.put(FIELD_SCORE, score);

					SQLiteDatabase db = this.getWritableDatabase();
					db.update("related", cv, FIELD_id + " = " + id
							//FIELD_DIC_pword + " = " + word
							//+FIELD_DIC_cword + " = " + pword
							, null);
					if(score == 1){ // Update userdic total ++
						SharedPreferences sp1 = ctx.getSharedPreferences(TOTAL_USERDICT_RECORD, 0);
						String recordString = sp1.getString(TOTAL_USERDICT_RECORD, "0");
						int dictotal = Integer.parseInt(recordString);
						dictotal++;
						sp1.edit().putString(TOTAL_USERDICT_RECORD, String.valueOf(dictotal)).commit();
					}
				} else {
					ContentValues cv = new ContentValues();
					cv.put(FIELD_SCORE, score);

					SQLiteDatabase db = this.getWritableDatabase();
					// Add by jeremy '10,3,31. Multi-table extension.
					//db.update("mapping", cv, FIELD_id + " = " + id, null);
					db.update(tablename, cv, FIELD_id + " = " + id, null);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private HashMap<String, TreeMap> buildRelatedCodeTable(boolean isCinFormat){
		// Prepare related word list
		HashMap<String, TreeMap> hm = new HashMap<String, TreeMap>();
		String line = "";
		try {
			
			// Prepare Source File
			FileReader fr = new FileReader(filename);
			BufferedReader buf = new BufferedReader(fr);
			boolean firstline = true;
			boolean cinFormatStart = false;
			int i = 0;

			while ((line = buf.readLine()) != null) {
				i++;
		
				/*
				 * If source is cin format start from the tag %chardef begin until %chardef end
				 */
				if(isCinFormat){
					// Modified by Jeremy '10, 3, 28. Some .cin have double space between $chardef and begin or end
					if(!cinFormatStart){
						if(line != null  
								&&line.trim().toLowerCase().startsWith("%chardef")
								&&line.trim().toLowerCase().endsWith("begin")){
							cinFormatStart = true;
						}
						continue;
					}
					
					if(line != null 
							&& line.trim().toLowerCase().startsWith("%chardef")
							&&line.trim().toLowerCase().endsWith("end")){										
						break;
					}
				}
				
				if(firstline){
					byte srcstring[] = line.getBytes();
					if(srcstring.length > 3){
						if(srcstring[0] == -17 && srcstring[1] == -69 && srcstring[2] == -65){
							byte tempstring[] = new byte[srcstring.length -3];
							int a=0;
							for(int j = 3 ; j < srcstring.length ; j++){
								tempstring[j-3] = srcstring[j];
							}
							line = new String(tempstring);
						}
					}
					firstline = false;
				}else{
					if (line == null) {continue;}
					if (line.trim().equals("")) {continue;}
					if (line.length() < 3) {continue;}
				}
				
				String code = null, word = null;
				// Modified by Jeremy '10, 3, 28. We don't need word here.
				if(isCinFormat){
					if(line.indexOf("\t") != -1){
						code = line.substring(0, line.indexOf("\t"));
						//word = line.substring(line.indexOf("\t") + 1);
					}else if(line.indexOf(" ") != -1){
						code = line.trim().substring(0, line.indexOf(" "));
						//word = line.trim().substring(line.indexOf(" ")+1);
					}
				}else{
					code = line.substring(0, line.indexOf(DELIMITER));
					//word = line.substring(line.indexOf(DELIMITER) + 1);
				}
				// Modified by Jeremy '10, 3, 28.  Trim spaces.
				if (code == null || code.trim().equals("")) {continue;}else{code = code.toUpperCase();}

				if (code.equalsIgnoreCase("@VERSION@")){
					continue;
				}
				
				String first = code.substring(0,1);
				
				if(hm.get(first) != null){
					TreeMap tm = hm.get(first);
							tm.put(code, word);
					hm.put(first, tm);
				}else{
					TreeMap tm = new TreeMap();
							tm.put(code, word);
					hm.put(first, tm);
				}
				
				/**
				if(i % 1000 == 0){
					//Log.i("ART", "code : " + i + " " + new Date().toString());
				}**/
			}
			buf.close();
			fr.close();
		
		}catch(Exception e){}
		return hm;
	}
	/**
	 * Load source file and add records into database
	 */
	public void loadFile(final String table) {
		
		if(thread != null){
			thread.stop();
			thread = null;
		}
		SharedPreferences sp = ctx.getSharedPreferences(MAPPING_LOADING, 0);
		sp.edit().putString(MAPPING_LOADING, "yes").commit();
		
		thread = new Thread() {
			public void run() {

				boolean hasMappingVersion = false;
				boolean isCinFormat= false;
				String line = "";
				finish = false;
				//relatedfinish = false;
				count = 0;
				relatedcount = 0;

				
				// Check if source file is .cin format
				if(filename.getName().toLowerCase().endsWith(".cin")){
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
						while ((line = buf.readLine()) != null && isCinFormat == false) {
							templist.add(line);
							if(i >= 100){
								break;
							}else{
								i++;
							}
						}
						identifyDelimiter(templist);
						templist.clear();
						buf.close();
						fr.close();
					}catch(Exception e){}
					
					// Build HashMap of related codes table
					/*
					HashMap<String, TreeMap> hm = null;
					
					if(!table.equals("related")){ // We don't need the related code table in related table.
						hm = buildRelatedCodeTable(isCinFormat);
					}
					*/

					// Import into database
					//Log.i("ART", "Import start : " + new Date().toString());

					SQLiteDatabase db = getWritableDatabase();
						           db.beginTransaction();
					try {
						// Prepare Source File
						FileReader fr = new FileReader(filename);
						BufferedReader buf = new BufferedReader(fr);
						boolean firstline = true;
						boolean cinFormatStart = false;						
						
						while ((line = buf.readLine()) != null) {
							
							/*
							 * If source is cin format start from the tag %chardef begin until %chardef end
							 */
							if(isCinFormat){
								
								if(!cinFormatStart){
									// Modified by Jeremy '10, 3, 28. Some .cin have double space between $chardef and begin or end
									if(line != null  
											&&line.trim().toLowerCase().startsWith("%chardef")
											&&line.trim().toLowerCase().endsWith("begin")){
										cinFormatStart = true;
									}
									// Add by Jeremy '10, 3 , 27
									// use %cname as mapping_version of .cin
									if( !line.trim().toLowerCase().startsWith("%cname")){
										continue;
									}
								}
								
								if(line != null 
										&& line.trim().toLowerCase().startsWith("%chardef")
										&&line.trim().toLowerCase().endsWith("end")){													
									break;
								}
							}

							// Check Format
							if(firstline){
								byte srcstring[] = line.getBytes();
								if(srcstring.length > 3){
									if(srcstring[0] == -17 && srcstring[1] == -69 && srcstring[2] == -65){
										byte tempstring[] = new byte[srcstring.length -3];
										int a=0;
										for(int j = 3 ; j < srcstring.length ; j++){
											tempstring[j-3] = srcstring[j];
										}
										line = new String(tempstring);
									}
								}
								firstline = false;
							}else{
								if (line == null) {continue;}
								if (line.trim().equals("")) {continue;}
								if (line.length() < 3) {continue;}
							}
	
							try{

								String code = null, word = null;
								if(isCinFormat){
									if(line.indexOf("\t") != -1){
										code = line.substring(0, line.indexOf("\t"));
										word = line.substring(line.indexOf("\t") + 1);
									}else if(line.indexOf(" ") != -1){
										code = line.substring(0, line.indexOf(" "));
										word = line.substring(line.indexOf(" ") + 1);
									}
								}else{
									code = line.substring(0, line.indexOf(DELIMITER));
									word = line.substring(line.indexOf(DELIMITER) + 1);
								}
								if (code == null || code.trim().equals("")) {continue;}else{code = code.toUpperCase();}
								// Add by Jeremy '10, 3 , 28. Trim spaces.
								if (word == null || word.trim().equals("")) {continue;}else{word = word.trim();}
								// Add by Jeremy '10, 3 , 27
								// use %cname as mapping_version of .cin
								if (code.equalsIgnoreCase("@VERSION@")||code.equalsIgnoreCase("%cname")){
								// Add by Jeremy '10,3, 28 for multi-table extension	
									SharedPreferences version = null;
									if(table.equals("cj")){
										version = ctx.getSharedPreferences(CJ_MAPPING_VERSION, 0);
										version.edit().putString(CJ_MAPPING_VERSION, word.trim()).commit();
									}else if(table.equals("dayi")){
										version = ctx.getSharedPreferences(DAYI_MAPPING_VERSION, 0);
										version.edit().putString(DAYI_MAPPING_VERSION, word.trim()).commit();
									}else if(table.equals("phonetic")){
										version = ctx.getSharedPreferences(BPMF_MAPPING_VERSION, 0);
										version.edit().putString(BPMF_MAPPING_VERSION, word.trim()).commit();
									}else if(table.equals("ez")){
										version = ctx.getSharedPreferences(EZ_MAPPING_VERSION, 0);
										version.edit().putString(EZ_MAPPING_VERSION, word.trim()).commit();
									}else if(table.equals("related")){
										version = ctx.getSharedPreferences(RELATED_MAPPING_VERSION, 0);
										version.edit().putString(RELATED_MAPPING_VERSION, word.trim()).commit();
									}else {
										version = ctx.getSharedPreferences(MAPPING_VERSION, 0);
										version.edit().putString(MAPPING_VERSION, word.trim()).commit();									
									}
																		
									continue;
								}
	
								String first = code.substring(0,1);
								
								if(!table.equals("related")){
									// Regular table
									insertWord(table, code, word);
											//, hm.get(first), 50);
								}else{
									// Related table.
									insertDictionary(code, word, 0);
								}
								
								
							}catch(StringIndexOutOfBoundsException e){
								Log.i("ART", line+":"+ e);
							}
							
							count++;
							//countline++;
							if(count % 100 == 0){
								// Jeremy '10, 3, 28 multi-table extenstion
								SharedPreferences sp = null; 
								if(table.equals("cj")){
									sp = ctx.getSharedPreferences(CJ_TOTAL_RECORD, 0);
									sp.edit().putString(CJ_TOTAL_RECORD, String.valueOf(count) + " (loading...)").commit();
								}else if(table.equals("dayi")){
									sp = ctx.getSharedPreferences(DAYI_TOTAL_RECORD, 0);
									sp.edit().putString(DAYI_TOTAL_RECORD, String.valueOf(count) + " (loading...)").commit();
								}else if(table.equals("phonetic")){
									sp = ctx.getSharedPreferences(BPMF_TOTAL_RECORD, 0);
									sp.edit().putString(BPMF_TOTAL_RECORD, String.valueOf(count) + " (loading...)").commit();
								}else if(table.equals("ez")){
									sp = ctx.getSharedPreferences(EZ_TOTAL_RECORD, 0);
									sp.edit().putString(EZ_TOTAL_RECORD, String.valueOf(count) + " (loading...)").commit();
								}else if(table.equals("related")){
									sp = ctx.getSharedPreferences(RELATED_TOTAL_RECORD, 0);
									sp.edit().putString(RELATED_TOTAL_RECORD, String.valueOf(count) + " (loading...)").commit();
								}else {
									sp = ctx.getSharedPreferences(TOTAL_RECORD, 0);
									sp.edit().putString(TOTAL_RECORD, String.valueOf(count) + " (loading...)").commit();
								}
							}
							
						}
	
						buf.close();
						fr.close();
	
					}catch(Exception e){
						e.printStackTrace();
					}finally{
						db.setTransactionSuccessful();
						db.endTransaction();
					}
					
	
					// Update Total Record
					// Modified by Jeremy '10,3, 28 for multi-table extension
					SharedPreferences sp1 = null, sp2 = null; 
					if(table.equals("cj")){
						sp1 = ctx.getSharedPreferences(CJ_TOTAL_RECORD, 0);
						sp1.edit().putString(CJ_TOTAL_RECORD, String.valueOf(countMapping(table))).commit();
						sp2 = ctx.getSharedPreferences(CJ_MAPPING_FILE_TEMP, 0);
						sp2.edit().putString(CJ_MAPPING_FILE_TEMP, "").commit();
					}else if(table.equals("dayi")){
						sp1 = ctx.getSharedPreferences(DAYI_TOTAL_RECORD, 0);
						sp1.edit().putString(DAYI_TOTAL_RECORD, String.valueOf(countMapping(table))).commit();
						sp2 = ctx.getSharedPreferences(DAYI_MAPPING_FILE_TEMP, 0);
						sp2.edit().putString(DAYI_MAPPING_FILE_TEMP, "").commit();
					}else if(table.equals("phonetic")){
						sp1 = ctx.getSharedPreferences(BPMF_TOTAL_RECORD, 0);
						sp1.edit().putString(BPMF_TOTAL_RECORD, String.valueOf(countMapping(table))).commit();
						sp2 = ctx.getSharedPreferences(BPMF_MAPPING_FILE_TEMP, 0);
						sp2.edit().putString(BPMF_MAPPING_FILE_TEMP, "").commit();
					}else if(table.equals("ez")){
						sp1 = ctx.getSharedPreferences(EZ_TOTAL_RECORD, 0);
						sp1.edit().putString(EZ_TOTAL_RECORD, String.valueOf(countMapping(table))).commit();
						sp2 = ctx.getSharedPreferences(EZ_MAPPING_FILE_TEMP, 0);
						sp2.edit().putString(EZ_MAPPING_FILE_TEMP, "").commit();
					}else if(table.equals("related")){
						sp1 = ctx.getSharedPreferences(RELATED_TOTAL_RECORD, 0);
						sp1.edit().putString(RELATED_TOTAL_RECORD, String.valueOf(countRelated())).commit();
						sp2 = ctx.getSharedPreferences(RELATED_MAPPING_FILE_TEMP, 0);
						sp2.edit().putString(RELATED_MAPPING_FILE_TEMP, "").commit();
					}else {
						sp1 = ctx.getSharedPreferences(TOTAL_RECORD, 0);
						sp1.edit().putString(TOTAL_RECORD, String.valueOf(countMapping(table))).commit();
						sp2 = ctx.getSharedPreferences(MAPPING_FILE_TEMP, 0);
						sp2.edit().putString(MAPPING_FILE_TEMP, "").commit();
					}
					SharedPreferences sp3 = ctx.getSharedPreferences(MAPPING_LOADING, 0);
									  sp3.edit().putString(MAPPING_LOADING, "no").commit();
		            SharedPreferences sp4 = ctx.getSharedPreferences(MAPPING_IMPORT_LINE, 0);
						  			  sp4.edit().putString(MAPPING_IMPORT_LINE, "").commit();
									  finish = true;
									
			}
			
		};
		thread.start();
	}
	
	public void insertDictionary(String pword, String cword, int score){
		SQLiteDatabase db = getWritableDatabase();
		try{
			ContentValues cv = new ContentValues();
						  cv.put(FIELD_DIC_pword, pword);
						  cv.put(FIELD_DIC_cword, cword);
						  cv.put(FIELD_SCORE, score);
			
			db.insert("related", null, cv);
			
		}catch(Exception e){
			e.printStackTrace();
		}
		
	}
	
	public void insertWord(String table, String code, String word ){
			//, TreeMap<String, String> srclist, int size){

		//String related = "";
		/*
		if(srclist != null){
			Set set = srclist.tailMap(code).entrySet();
			Iterator i = set.iterator();
			int wordcount = 0;
			boolean hasLoad = false;
			while(i.hasNext()){
				
				Map.Entry me = (Map.Entry)i.next();
				String key = String.valueOf(me.getKey());
				if(!key.equals(code) && key.startsWith(code)){
					related += key + "\t";
				}else if(!key.equals(code) && !key.startsWith(code)){
					break;
				}
				
				wordcount++;
				if(wordcount > size ){
					break;
				}
			}
		}
		
		*/
		SQLiteDatabase db = getWritableDatabase();
		try{
			ContentValues cv = new ContentValues();
						  cv.put(FIELD_CODE, code);
						  cv.put(FIELD_WORD, word);
						  cv.put(FIELD_RELATED, "");//related);
						  cv.put(FIELD_SCORE, 0);
			db.insert(table, null, cv);
			//Log.i("ART", "Insert -> " + code + " : " + related + " - "+ new Date().toString());
		}catch(Exception e){
			e.printStackTrace();
		}
		
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
	public boolean isExists(String pword, String cword) {

		// Create Suggestions (Exactly Matched)
		if (pword != null && !pword.trim().equals("") && cword != null
				&& !cword.trim().equals("")) {

			/**
			 * 
						cv.put(FIELD_DIC_pcode, pword.hashCode());
						cv.put(FIELD_DIC_pword, pword);
						cv.put(FIELD_DIC_ccode, word.hashCode());
			 */
			try {
				Cursor cursor = null;

				SQLiteDatabase db = this.getReadableDatabase();
				cursor = db.query("related", null,
						// Modified '10, 3, 31 on check pwork and cword
						FIELD_DIC_pword + " = '" + pword + "'" 
						+" AND " +	FIELD_DIC_cword + " = '" + cword + "'",
						null, null, null, null, null);

				if (cursor != null && cursor.getCount() > 0) {
					cursor.deactivate();
					cursor.close();
					return true;
				} else {
					if (cursor != null) {
						cursor.deactivate();
						cursor.close();
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	/**
	 * Backup dictionary items
	 */
	//
	// Modified by Jeremy '10, 3,12
	//
	public void backupRelatedUserdic() {
		final File targetFile = new File("/sdcard/lime/limedb.txt");
		targetFile.deleteOnExit();
		
		SharedPreferences sp = ctx.getSharedPreferences(MAPPING_LOADING, 0);
		sp.edit().putString(MAPPING_LOADING, "yes").commit();
		
		if(thread!=null){
			thread.stop();
			thread = null;
		}
		thread = new Thread() {
			public void run() {
		
				//Modified by Jeremy '10, 3, 30.  Use /sdcard/lime as user space storage space.

		
				relatedfinish = false;

				Cursor cursor = null;

				//ArrayList temp = new ArrayList();
				FileOutputStream fos;
		
				try {

					SQLiteDatabase db = getWritableDatabase();
					// Modified '10, 3, 30 by Jeremy. All userdict should >0.
					cursor = db.rawQuery("SELECT * FROM related " 
							+"where " + FIELD_DIC_score + "> 0" 
							, null);
			
					OutputStream out = new FileOutputStream(targetFile, false);
					Writer writer = new OutputStreamWriter(out, "UTF-8");
						
					if (cursor.moveToFirst()) {
						int idColumn = cursor.getColumnIndex(FIELD_id);
						//int pcodeColumn = cursor.getColumnIndex(FIELD_DIC_pcode);
						int pwordColumn = cursor.getColumnIndex(FIELD_DIC_pword);
						//int codeColumn = cursor.getColumnIndex(FIELD_DIC_ccode);
						int wordColumn = cursor.getColumnIndex(FIELD_DIC_cword);
						int scoreColumn = cursor.getColumnIndex(FIELD_DIC_score);
						do {
							//	Modified by jeremy.  Skip pcode code here.
							String line = 
								//cursor.getString(pcodeColumn) + "\t" + 
								cursor.getString(pwordColumn) + "\t" + 
								//cursor.getString(codeColumn) + "\t" +
								cursor.getString(wordColumn) + "\t" + 
								cursor.getInt(scoreColumn) + "\r\n";
								writer.write(new String(line.getBytes("UTF-8")));
						} while (cursor.moveToNext());
					}
					db.close();
					writer.close();
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}
		
				if (cursor != null) {
					cursor.deactivate();
					cursor.close();
				}
				relatedfinish = true;
				SharedPreferences sp = ctx.getSharedPreferences(MAPPING_LOADING, 0);
				sp.edit().putString(MAPPING_LOADING, "no").commit();
				}
			};
			thread.start();
		

	}

	/**
	 * Restore from backup file
	 */
	//
	// Modified by Jeremy '10, 3,12
	//
	public void restoreRelatedUserdic() {
		relatedfinish = false;
		final File targetFile = new File("/sdcard/lime/limedb.txt");
		SharedPreferences sp = ctx.getSharedPreferences(MAPPING_LOADING, 0);
		sp.edit().putString(MAPPING_LOADING, "yes").commit();
			
		if(thread!=null){
				thread.stop();
				thread = null;
		}
		
		thread = new Thread() {
			public void run() {

		
		
	

		//ArrayList temp = new ArrayList();
		if (targetFile.exists()) {

			deleteDictionaryAll();
			
			
			//int total = 0;
			relatedcount = 0;
			FileReader fis;
			SQLiteDatabase db = getWritableDatabase();
			db.beginTransaction();
			try {
				fis = new FileReader(targetFile);
				BufferedReader br = new BufferedReader(fis);

				String line = "";

				while ((line = br.readLine()) != null) {
					
					line = line.trim();
					
					try {
						/*
						String pcode = line.split("\t")[0];
						String pword = line.split("\t")[1];
						String code = line.split("\t")[2];
						String word = line.split("\t")[3];
						String score = "0";
						
						try{
							// To ignore incomplete import row
							score = line.split("\t")[4];
						}catch(ArrayIndexOutOfBoundsException e){}
						*/
						//Modified by Jeremy '10, 3, 30.  
						String [] temp=null;
						String pcode=null, pword=null, code=null, cword=null, score=null;
						temp = line.split("\t");
						if(DEBUG){
							Log.i("restoreRelatedUserdic","colums:"+temp.length + 
									" c1:" + temp[0] + 
							        " c2:" + temp[1] +
							        " c3:" + temp[1] );
						}
						if(temp.length == 5) { // old format, 5 columns
							//pcode = temp[0];
							pword = temp[1];
							//code = temp[2];
							cword = temp[3];
							score = temp[4];
						}else if(temp.length == 3) { // new format , 3 colums
							pword = temp[0];
							cword = temp[1];
							score = temp[2];
						}else {
							continue; // incomplete row!!
						}
						//Userdict must have score >0
						if(score.trim().equals("0")) {score = "1";}
							
						//temp.add(new Mapping(pcode, pword, code, word, Integer
						//		.parseInt(score)));
						// insert into database
						
						ContentValues cv = new ContentValues();
						//------------------------------------------------------------------
						// Modified by Jeremy '10,03,20, replace pcode, code with hashcode.
						//cv.put(FIELD_DIC_pcode, pcode);
						// '10, 3, 30.  pcode and ccode no more used.
						//cv.put(FIELD_DIC_pcode, "-");
						cv.put(FIELD_DIC_pword, pword);
						//cv.put(FIELD_DIC_ccode, code);
						//cv.put(FIELD_DIC_ccode, "-");
						//------------------------------------------------------------------
						cv.put(FIELD_DIC_cword, cword);
						cv.put(FIELD_DIC_score, score);
						
						//
						if(isExists(pword, cword)){
							db.update("related", cv,
									FIELD_DIC_pword + " = '" + pword + "'" 
									+" AND " +	FIELD_DIC_cword + " = '" + cword + "'"
									, null);
						}else{
							db.insert("related", null, cv);
						}

						//total++;
						relatedcount++;
						if(relatedcount % 100 == 0){
							SharedPreferences sp1 = ctx.getSharedPreferences(TOTAL_USERDICT_RECORD, 0);
											  sp1.edit().putString(TOTAL_USERDICT_RECORD, String.valueOf(relatedcount) + " (loading...)").commit();
						}
					}catch(ArrayIndexOutOfBoundsException e){
						//Error to parse the line
					}
				
				}

			}catch(Exception e){
				e.printStackTrace();
			}finally{
				db.setTransactionSuccessful();
				db.endTransaction();
				
			}
			
			// Update total_userdict_records
			SharedPreferences sp1 = ctx.getSharedPreferences(TOTAL_USERDICT_RECORD, 0);
			sp1.edit().putString(TOTAL_USERDICT_RECORD, String.valueOf(countUserdic())).commit();
			SharedPreferences sp = ctx.getSharedPreferences(MAPPING_LOADING, 0);
			sp.edit().putString(MAPPING_LOADING, "no").commit();
			relatedfinish = true;
			}
		}
	};
	thread.start();

 }
}
