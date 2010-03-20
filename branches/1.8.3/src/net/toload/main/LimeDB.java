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

	private final static String DATABASE_NAME = "lime";
	private final static int DATABASE_VERSION = 25;
	private final static int DATABASE_RELATED_SIZE = 50;
	private final static String TABLE_NAME = "mapping";
	private final static String TOTAL_RECORD = "total_record";
	//----------add by Jeremy '10,3,12 ----------------------------------------
	private final static String TOTAL_USERDICT_RECORD = "total_userdict_record";
	//-------------------------------------------------------------------------
	private final static String MAPPING_FILE = "mapping_file";
	private final static String MAPPING_VERSION = "mapping_version";
	private final static String MAPPING_LOADING = "mapping_loading";
	private final static String MAPPING_IMPORT_LINE = "mapping_import_line";
	private final static String CANDIDATE_SUGGESTION = "candidate_suggestion";
	private final static String LEARNING_SWITCH = "learning_switch";

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

	public String DELIMITER = "";
	private String limit = "10";

	private File filename = null;

	private int count = 0;
	private int relatedcount = 0;
	private boolean finish = false;
	private boolean relatedfinish = false;

	private Context ctx;

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

	public LimeDB(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		this.ctx = context;
	}

	/**
	 * Create SQLite Database and create related tables
	 */
	@Override
	public void onCreate(SQLiteDatabase db) {

		db.execSQL("CREATE TABLE custom (" + FIELD_id
				+ " INTEGER primary key autoincrement, " + " " + FIELD_CODE
				+ " text, " + FIELD_WORD + " text, " + FIELD_SCORE
				+ " integer)");
		db.execSQL("CREATE INDEX custom_idx ON custom (" + FIELD_CODE + ")");

		db.execSQL("CREATE TABLE mapping (" + FIELD_id
				+ " INTEGER primary key autoincrement, " + " " + FIELD_CODE
				+ " text, " + FIELD_WORD + " text, " + FIELD_RELATED
				+ " text, " + FIELD_SCORE + " integer)");
		db.execSQL("CREATE INDEX mapping_idx ON mapping (" + FIELD_CODE + ")");

		db.execSQL("CREATE TABLE userdic(" + FIELD_DIC_id
				+ " INTEGER primary key autoincrement, " + " "
				+ FIELD_DIC_pcode + " text, " + FIELD_DIC_ccode + " text, "
				+ FIELD_DIC_pword + " text, " + FIELD_DIC_cword + " text, "
				+ FIELD_DIC_score + " integer)");
		db.execSQL("CREATE INDEX userdic_idx_pcode ON userdic (" + FIELD_DIC_pcode + ")");
		db.execSQL("CREATE INDEX userdic_idx_pword ON userdic (" + FIELD_DIC_pword + ")");

	}

	/**
	 * Upgrade current database
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

		db.execSQL("DROP TABLE IF EXISTS custom");
		db.execSQL("DROP TABLE IF EXISTS custom_idx");
		db.execSQL("DROP TABLE IF EXISTS mapping");
		db.execSQL("DROP TABLE IF EXISTS mapping_idx");
		db.execSQL("DROP TABLE IF EXISTS userdic");
		db.execSQL("DROP TABLE IF EXISTS userdic_idx");

		onCreate(db);
	}

	/**
	 * Empty database records
	 */
	public void deleteAll() {

		SQLiteDatabase db = this.getWritableDatabase();
		db.delete("mapping", null, null);
		db.close();

		SharedPreferences sp1 = ctx.getSharedPreferences(TOTAL_RECORD, 0);
		sp1.edit().putString(TOTAL_RECORD, String.valueOf(0)).commit();

		SharedPreferences sp2 = ctx.getSharedPreferences(MAPPING_VERSION, 0);
		sp2.edit().putString(MAPPING_VERSION, "").commit();

		SharedPreferences sp3 = ctx.getSharedPreferences(MAPPING_LOADING, 0);
		sp3.edit().putString(MAPPING_LOADING, "no").commit();

		SharedPreferences sp4 = ctx
				.getSharedPreferences(MAPPING_IMPORT_LINE, 0);
		sp4.edit().putString(MAPPING_IMPORT_LINE, "").commit();

		SharedPreferences sp5 = ctx.getSharedPreferences(MAPPING_FILE, 0);
		sp5.edit().putString(MAPPING_FILE, "").commit();
		
		SharedPreferences sp6 = ctx.getSharedPreferences(MAPPING_VERSION, 0);
						  sp6.edit().putString(MAPPING_VERSION, "").commit();
						  
		count = 0;
		relatedcount = 0;
		finish = false;
		relatedfinish = false;
	}

	/**
	 * Empty Dictionary table records
	 */
	public void deleteDictionaryAll() {
		//---------------add by Jeremy '10,3,12-----------------------------------
		SharedPreferences sp1 = ctx.getSharedPreferences(TOTAL_USERDICT_RECORD, 0);
		sp1.edit().putString(TOTAL_USERDICT_RECORD, String.valueOf(0)).commit();
		//-------------------------------------------------------------------------
		SQLiteDatabase db = this.getWritableDatabase();
		db.delete("userdic", null, null);
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
	public int countMapping() {

		int total = 0;
		try {
			SQLiteDatabase db = this.getReadableDatabase();
			total += db.rawQuery("SELECT * FROM mapping", null).getCount();
			db.close();
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
			total += db.rawQuery("SELECT * FROM userdic", null).getCount();
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
	 * Batch add dictionary items into database
	 * 
	 * @param srclist
	 */
	//---------------------------------------------------------------
	//Removed by Jeremy '10, 3,12. Code moved to restoreRelatedUserdic 
	/*
	public void batchAddDictionary(List<Mapping> srclist) {

		int total = 0;
		SQLiteDatabase db = this.getWritableDatabase();
		db.beginTransaction();
		try{ 
		for (Mapping unit : srclist) {

			String code = unit.getPcode();

			ContentValues cv = new ContentValues();
			cv.put(FIELD_DIC_pcode, unit.getPcode());
			cv.put(FIELD_DIC_pword, unit.getPword());
			cv.put(FIELD_DIC_ccode, unit.getCode());
			cv.put(FIELD_DIC_cword, unit.getWord());
			cv.put(FIELD_DIC_score, unit.getScore());

			db.insert("userdic", null, cv);
			total++;
			}
		}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			db.setTransactionSuccessful();
			db.endTransaction();
		}

	}
	*/
	//----------------------------------------------------------------------------------------------------------------------------------
	/**
	 * Create dictionary database
	 * 
	 * @param srclist
	 */
	public void addDictionary(List<Mapping> srclist) {

		if (srclist != null && srclist.size() > 0) {

			try {
				SQLiteDatabase db = this.getWritableDatabase();
				for (int i = 0; i < srclist.size(); i++) {

					Mapping unit = srclist.get(i);
					// Log.i("ART","unit 1->"+unit);
					Mapping unit2 = srclist.get((i + 1));
					// Log.i("ART","unit 2->"+unit2);

					if (unit2 != null) {
						// Log.i("ART","unit 3->"+unit2);
						if (unit != null && unit.getCode().length() > 0
								&& unit2 != null
								&& unit2.getCode().length() > 0) {

							// Log.i("ART","unit 4->"+unit2);
							if (!this.isExists(unit.getCode(), unit.getWord(),
									unit2.getCode(), unit2.getWord())) {

								try {
									// Log.i("ART","unit 5->"+unit2);
									ContentValues cv = new ContentValues();
									//--------------------------------------------
									// Modified by Jeremy '10,03,20 replace pccode, code with hashcode
									//cv.put(FIELD_DIC_pcode, unit.getCode());
									cv.put(FIELD_DIC_pcode, unit.getWord().hashCode());
									cv.put(FIELD_DIC_pword, unit.getWord());
									//cv.put(FIELD_DIC_ccode, unit2.getCode());
									cv.put(FIELD_DIC_ccode, unit2.getWord().hashCode());
									cv.put(FIELD_DIC_cword, unit2.getWord());
									//-------------------------------------------------------
									cv.put(FIELD_DIC_score, 0);

									db.insert("userdic", null, cv);
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
			}
		}
	}

	public List<Mapping> getMapping(String keyword) {

		List<Mapping> result = new LinkedList<Mapping>();

		try {
			// Create Suggestions (Exactly Matched)
			if (keyword != null && !keyword.trim().equals("")) {

				Cursor cursor = null;

				try {
					keyword = keyword.toUpperCase();
				} catch (Exception e) {
					e.printStackTrace();
				}

				SQLiteDatabase db = this.getReadableDatabase();


				SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
				boolean item = sp.getBoolean(LEARNING_SWITCH, false);
				
				if(item){
					cursor = db.query("mapping", null, FIELD_CODE + " = \"" + keyword + "\"", null, null, null, FIELD_SCORE + " DESC", null);
				}else{
					cursor = db.query("mapping", null, FIELD_CODE + " = \"" + keyword + "\"", null, null, null, null, null);
				}
				
				if (cursor.moveToFirst()) {
					int codeColumn = cursor.getColumnIndex(FIELD_CODE);
					int wordColumn = cursor.getColumnIndex(FIELD_WORD);
					int scoreColumn = cursor.getColumnIndex(FIELD_SCORE);
					int relatedColumn = cursor.getColumnIndex(FIELD_RELATED);
					int idColumn = cursor.getColumnIndex(FIELD_id);
					do {
						Mapping munit = new Mapping();
						munit.setId(cursor.getString(idColumn));
						munit.setCode(cursor.getString(codeColumn));
						munit.setWord(cursor.getString(wordColumn));
						munit.setRelated(cursor.getString(relatedColumn));
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
		} catch (Exception e) {
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

					where += " " + FIELD_CODE + " = \"" + item + "\" OR";
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
						cursor = db.query("mapping", null, where, null, null, null, FIELD_SCORE + " DESC", String.valueOf(size));
					}else{
						cursor = db.query("mapping", null, where, null, null, null, null, String.valueOf(size));
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
		if (pcode != null && !pcode.trim().equals("")) {

			Cursor cursor = null;

			try {
				pcode = pcode.toUpperCase();
			} catch (Exception e) {
				e.printStackTrace();
			}
			//Modified by Jeremy '10,3 ,20 for more specific related word
			// Replace pcode with hascode() for better perfoamnce and IM indepedent.
			//-----------------------------------------------------------

			SQLiteDatabase db = this.getReadableDatabase();
			//cursor = db.query("userdic", null, FIELD_DIC_pcode + " = \""
			//		+ keyword + "\"", null, null, null, FIELD_DIC_score
			
			cursor = db.query("userdic", null,
					FIELD_DIC_pcode + " = " + pword.hashCode() 
					, null, null, null, FIELD_DIC_score
					+ " DESC", null);
			//Log.i("Query", FIELD_DIC_pword + " = \"" + pword +"\":" + new Date().toString());
			
			//-----------------------------------------------------------

			 
			

			if (cursor.moveToFirst()) {
				int pcodeColumn = cursor.getColumnIndex(FIELD_DIC_pcode);
				int pwordColumn = cursor.getColumnIndex(FIELD_DIC_pword);
				int ccodeColumn = cursor.getColumnIndex(FIELD_DIC_ccode);
				int cwordColumn = cursor.getColumnIndex(FIELD_DIC_cword);
				int scoreColumn = cursor.getColumnIndex(FIELD_DIC_score);
				int idColumn = cursor.getColumnIndex(FIELD_id);
				do {
					Mapping munit = new Mapping();
					munit.setId(cursor.getString(idColumn));
					munit.setPcode(cursor.getString(pcodeColumn));
					munit.setPword(cursor.getString(pwordColumn));
					munit.setCode(cursor.getString(ccodeColumn));
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
		String pcode = srcunit.getPcode();
		String pword = srcunit.getPword();

		int score = srcunit.getScore();
		try {
			if (code != null && word != null && !code.trim().equals("")
					&& !word.trim().equals("")) {

				score++;

				if (srcunit.isDictionary()) {
					ContentValues cv = new ContentValues();
					cv.put(FIELD_SCORE, score);

					SQLiteDatabase db = this.getWritableDatabase();
					db.update("userdic", cv, FIELD_id + " = " + id, null);
				} else {
					ContentValues cv = new ContentValues();
					cv.put(FIELD_SCORE, score);

					SQLiteDatabase db = this.getWritableDatabase();
					db.update("mapping", cv, FIELD_id + " = " + id, null);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Load source file and add records into database
	 */
	public void loadFile() {

		Thread thread = new Thread() {
			public void run() {

				boolean hasMappingVersion = false;
				String line = "";
				finish = false;
				//relatedfinish = false;
				count = 0;
				relatedcount = 0;

				// Load previous import status
				/**
				SharedPreferences importlineset = ctx.getSharedPreferences(MAPPING_IMPORT_LINE, 0);
				String importstatus = "";
				try{
					importstatus = importlineset.getString(MAPPING_IMPORT_LINE, "");
					if(!importstatus.equals("")){
						if(importstatus.startsWith("mapping")){
							try{
								count = Integer.parseInt(importstatus.split(" ")[1]);
								//relatedcount = 0;
							}catch(Exception e){}
						}else{
							deleteAll();
						}
					}
				}catch(Exception e){}
				**/
				
				//if(relatedcount == 0){
					// Identify Delimiter
					try {
						
						// Prepare Source File
						FileReader fr = new FileReader(filename);
						BufferedReader buf = new BufferedReader(fr);
						boolean firstline = true;
						int i = 0;
						List templist = new ArrayList();
						while ((line = buf.readLine()) != null) {
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
					

					//Log.i("ART", "Prepare related list : " + new Date().toString());
					// Prepare related word list
					HashMap<String, TreeMap> hm = new HashMap<String, TreeMap>();
					try {
						
						// Prepare Source File
						FileReader fr = new FileReader(filename);
						BufferedReader buf = new BufferedReader(fr);
						boolean firstline = true;
						//Add by jeremy '10,03,10 for .cin compatibility
						boolean chardef = true;
						int i = 0;
						while ((line = buf.readLine()) != null) {
							i++;
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
						
							// 	Add by jeremy '10, 03 20 for .cin comptibility.
							// .cin comment start with #
							if(line.startsWith("#")) {continue;}
							// 	Add by jeremy '10, 03 20 for .cin comptibility.
							if(line.startsWith("%"))
							{
								if(line.trim().equalsIgnoreCase("%keyname begin")){
									chardef = false;
								}else if(line.trim().equalsIgnoreCase("%chardef begin")){
									chardef = true;
								}else if(line.trim().equalsIgnoreCase("%chardef end")){
									chardef = false;
																	
								}
								continue;
							}
							if(!chardef){continue;}	
							//---------------------------------------------------------
							String code = line.substring(0, line.indexOf(DELIMITER));
							String word = line.substring(line.indexOf(DELIMITER) + 1);
							if (code == null || code.trim().equals("")) {continue;}else{code = code.toUpperCase();}

							if (code.equalsIgnoreCase("@VERSION@")){
								continue;
							}
							
							
							// 	Add by jeremy '10, 03 20 for .cin comptibility.
							
							
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
					

					// Import into database
					//Log.i("ART", "Import start : " + new Date().toString());

					SQLiteDatabase db = getWritableDatabase();
						           db.beginTransaction();
					try {
						// Prepare Source File
						FileReader fr = new FileReader(filename);
						BufferedReader buf = new BufferedReader(fr);
						boolean firstline = true;
						// Add by jeremy '10,03,10 for .cin compatibility
						boolean chardef = true;
						//int countline = 0;
						
						
						while ((line = buf.readLine()) != null) {
							
							//// Check loading status
							//if(countline < count){
							//	countline++;
							//	continue;
							//}
							
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
							
							// 	Add by jeremy '10, 03 20 for .cin comptibility.
							// .cin comment start with #
							if(line.startsWith("#")) {continue;}
							if(line.startsWith("%"))
							{
								if(line.trim().equalsIgnoreCase("%keyname begin")){
									chardef = false;
								}else if(line.trim().equalsIgnoreCase("%chardef begin")){
									chardef = true;
								}else if(line.trim().equalsIgnoreCase("%chardef end")){
									chardef = false;
								}else if(line.trim().startsWith("%cname")){
									SharedPreferences version = ctx.getSharedPreferences(MAPPING_VERSION, 0);
									version.edit().putString(MAPPING_VERSION, line.substring(6)).commit();									
								}
								continue;
							}
							if(!chardef){continue;}	
							//---------------------------------------------------------
							
							String code = line.substring(0, line.indexOf(DELIMITER));
							String word = line.substring(line.indexOf(DELIMITER) + 1);
							if (code == null || code.trim().equals("")) {continue;}else{code = code.toUpperCase();}
							if (word == null || word.trim().equals("")) {continue;}
							if (code.equalsIgnoreCase("@VERSION@")){
								SharedPreferences version = ctx.getSharedPreferences(MAPPING_VERSION, 0);
								version.edit().putString(MAPPING_VERSION, word.trim()).commit();
								continue;
							}
							//Add by jeremy '10, 03 20 for .cin comptibility.

							String first = code.substring(0,1);
							
							insertWord(code, word, hm.get(first), 50);
							//db.insert("mapping", null, cv);	
							
							count++;
							//countline++;
							if(count % 100 == 0){
								SharedPreferences sp1 = ctx.getSharedPreferences(TOTAL_RECORD, 0);
												  sp1.edit().putString(TOTAL_RECORD, String.valueOf(count) + " (loading...)").commit();
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
					SharedPreferences sp1 = ctx.getSharedPreferences(TOTAL_RECORD, 0);
									  sp1.edit().putString(TOTAL_RECORD, String.valueOf(countMapping())).commit();
					SharedPreferences sp2 = ctx.getSharedPreferences(MAPPING_LOADING, 0);
									  sp2.edit().putString(MAPPING_LOADING, "yes").commit();
		            SharedPreferences sp3 = ctx.getSharedPreferences(MAPPING_IMPORT_LINE, 0);
						  			  sp3.edit().putString(MAPPING_IMPORT_LINE, "").commit();
				    
									  finish = true;
									
			}
			
		};
		thread.start();
	}
	
	public void insertWord(String code, String word, TreeMap<String, String> srclist, int size){

		String related = "";
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
		

		SQLiteDatabase db = getWritableDatabase();
		try{
			ContentValues cv = new ContentValues();
						  cv.put(FIELD_CODE, code);
						  cv.put(FIELD_WORD, word);
						  cv.put(FIELD_RELATED, related);
						  cv.put(FIELD_SCORE, 0);
			//db.beginTransaction();
			//db.setTransactionSuccessful();
			db.insert("mapping", null, cv);
			//Log.i("ART", "Insert -> " + code + " : " + related + " - "+ new Date().toString());
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			//db.setTransactionSuccessful();
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
	 * @param pcode
	 * @param pword
	 * @param ccode
	 * @param cword
	 * @return
	 */
	public boolean isExists(String pcode, String pword, String ccode,
			String cword) {

		// Create Suggestions (Exactly Matched)
		if (pcode != null && !pcode.trim().equals("") && ccode != null
				&& !ccode.trim().equals("")) {

			try {
				Cursor cursor = null;

				SQLiteDatabase db = this.getReadableDatabase();
				cursor = db.query("userdic", null, FIELD_DIC_pword + " = '"
						+ pword + "' AND " + FIELD_DIC_cword + " = '" + cword
						+ "' AND " + FIELD_DIC_pcode + " = '" + pcode
						+ "' AND " + FIELD_DIC_ccode + " = '" + ccode + "'",
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
		
		Thread thread = new Thread() {
			public void run() {

		File targetFile = new File("/sdcard/limedb.txt");
		targetFile.deleteOnExit();
		relatedfinish = false;

		Cursor cursor = null;

		//ArrayList temp = new ArrayList();
		FileOutputStream fos;
		
		try {

			SQLiteDatabase db = getWritableDatabase();
			cursor = db.rawQuery("SELECT * FROM userdic", null);
			
			OutputStream out = new FileOutputStream(targetFile, false);
			Writer writer = new OutputStreamWriter(out, "UTF-8");
			
			
			if (cursor.moveToFirst()) {
				int idColumn = cursor.getColumnIndex(FIELD_id);
				int pcodeColumn = cursor.getColumnIndex(FIELD_DIC_pcode);
				int pwordColumn = cursor.getColumnIndex(FIELD_DIC_pword);
				int codeColumn = cursor.getColumnIndex(FIELD_DIC_ccode);
				int wordColumn = cursor.getColumnIndex(FIELD_DIC_cword);
				int scoreColumn = cursor.getColumnIndex(FIELD_DIC_score);
				do {
					String line = cursor.getString(pcodeColumn) + "\t" + cursor.getString(pwordColumn)
					+ "\t" + cursor.getString(codeColumn) + "\t" +cursor.getString(wordColumn)
					+ "\t" + cursor.getInt(scoreColumn) + "\r\n";
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
		Thread thread = new Thread() {
			public void run() {

		File targetFile = new File("/sdcard/limedb.txt");
		
		relatedfinish = false;

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
					//try {
						String pcode = line.split("\t")[0];
						String pword = line.split("\t")[1];
						String code = line.split("\t")[2];
						String word = line.split("\t")[3];
						String score = line.split("\t")[4];
						//temp.add(new Mapping(pcode, pword, code, word, Integer
						//		.parseInt(score)));
						// insert into database
						
						ContentValues cv = new ContentValues();
						//------------------------------------------------------------------
						// Modified by Jeremy '10,03,20, replace pcode, code with hashcode.
						//cv.put(FIELD_DIC_pcode, pcode);
						cv.put(FIELD_DIC_pcode, pword.hashCode());
						cv.put(FIELD_DIC_pword, pword);
						//cv.put(FIELD_DIC_ccode, code);
						cv.put(FIELD_DIC_ccode, word.hashCode());
						//------------------------------------------------------------------
						cv.put(FIELD_DIC_cword, word);
						cv.put(FIELD_DIC_score, score);

						db.insert("userdic", null, cv);

						//total++;
						relatedcount++;
						if(relatedcount % 100 == 0){
							SharedPreferences sp1 = ctx.getSharedPreferences(TOTAL_USERDICT_RECORD, 0);
											  sp1.edit().putString(TOTAL_USERDICT_RECORD, String.valueOf(relatedcount) + " (loading...)").commit();
						}
				
				}
			//} catch (FileNotFoundException e) {
			//	e.printStackTrace();
			//} catch (IOException e) {
			//	e.printStackTrace();
			}catch(Exception e){
				e.printStackTrace();
			}finally{
				db.setTransactionSuccessful();
				db.endTransaction();
			}
			
			// Update total_userdict_records
			SharedPreferences sp1 = ctx.getSharedPreferences(TOTAL_USERDICT_RECORD, 0);
			sp1.edit().putString(TOTAL_USERDICT_RECORD, String.valueOf(countUserdic())).commit();
			relatedfinish = true;
			}
		}
	};
	thread.start();

 }
}
