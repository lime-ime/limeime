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
import java.util.HashMap;
import java.util.Iterator;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * @author Art Hung
 */
public class LimeDB extends SQLiteOpenHelper {

	private final static String DATABASE_NAME = "lime";
	private final static int DATABASE_VERSION = 15;
	private final static String TABLE_NAME = "mapping";
	private final static String TOTAL_RECORD = "total_record";
	private final static String TOTAL_RELATED = "total_related";
	private final static String MAPPING_VERSION = "mapping_version";
	private final static String RESET_STATUS = "reset_status";

	private final static int start = 65;
	private final static int end = 90;

	public final static String FIELD_id = "_id";
	public final static String FIELD_CODE = "code";
	public final static String FIELD_WORD = "word";
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

	private boolean finish = false;
	private int count = 0;
	
	private Context ctx;

	public boolean isFinish() {
		return finish;
	}

	public File getFilename() {
		return filename;
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
		for (int i = start; i <= end; i++) {
			for (int j = 1; j <= 9; j++) {
				db.execSQL("CREATE TABLE " + String.valueOf((char) i) + j
						+ TABLE_NAME + " (" + FIELD_id
						+ " INTEGER primary key autoincrement, " + " "
						+ FIELD_CODE + " text, " + FIELD_WORD + " text, "
						+ FIELD_SCORE + " integer)");
				db.execSQL("CREATE INDEX "+String.valueOf((char) i)+j+"INDEX ON " + String.valueOf((char) i) + j+ TABLE_NAME + "("+FIELD_CODE+")");
			}
		}
		for (int j = 1; j <= 9; j++) {
			db.execSQL("CREATE TABLE NUMBER" + j + TABLE_NAME + " (" + FIELD_id
					+ " INTEGER primary key autoincrement, " + " " + FIELD_CODE
					+ " text, " + FIELD_WORD + " text, " + FIELD_SCORE
					+ " integer)");
			db.execSQL("CREATE INDEX  NUMBER"+j+"INDEX ON NUMBER" + j+ TABLE_NAME + "("+FIELD_CODE+")");
		}
		db.execSQL("CREATE TABLE SYMBOL" + TABLE_NAME + " (" + FIELD_id
				+ " INTEGER primary key autoincrement, " + " " + FIELD_CODE
				+ " text, " + FIELD_WORD + " text, " + FIELD_SCORE
				+ " integer)");
		db.execSQL("CREATE INDEX SYMBOLINDEX ON SYMBOL" + TABLE_NAME + "("+FIELD_CODE+")");

		for (int i = start; i <= end; i++) {

			for (int j = 1; j <= 9; j++) {
				db.execSQL("CREATE TABLE DICTIONARY" + String.valueOf((char) i)
						+ j + TABLE_NAME + " (" + FIELD_DIC_id
						+ " INTEGER primary key autoincrement, " + " "
						+ FIELD_DIC_pcode + " text, " + FIELD_DIC_ccode
						+ " text, " + FIELD_DIC_pword + " text, "
						+ FIELD_DIC_cword + " text, " + FIELD_DIC_score
						+ " integer)");
				db.execSQL("CREATE INDEX DICTIONARY"+String.valueOf((char) i)+j+"INDEX ON DICTIONARY" + String.valueOf((char) i)+j+ TABLE_NAME + "("+FIELD_DIC_pcode+","+FIELD_DIC_ccode+")");
			}
		}
		db.execSQL("CREATE TABLE DICTIONARY" + TABLE_NAME + " (" + FIELD_DIC_id
				+ " INTEGER primary key autoincrement, " + " "
				+ FIELD_DIC_pcode + " text, " + FIELD_DIC_ccode + " text, "
				+ FIELD_DIC_pword + " text, " + FIELD_DIC_cword + " text, "
				+ FIELD_DIC_score + " integer)");
		db.execSQL("CREATE INDEX DICTIONARYINDEX ON DICTIONARY" + TABLE_NAME + "("+FIELD_DIC_pcode+","+FIELD_DIC_ccode+")");

	}

	/**
	 * Upgrade current database
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

		for (int i = start; i <= end; i++) {
			for (int j = 1; j <= 9; j++) {
				db.execSQL("DROP TABLE IF EXISTS " + String.valueOf((char) i)
						+ j + TABLE_NAME);
				db.execSQL("DROP INDEX IF EXISTS "+String.valueOf((char) i)+j+"INDEX");
			}
		}
		for (int j = 1; j <= 9; j++) {
			db.execSQL("DROP TABLE IF EXISTS NUMBER" + j + TABLE_NAME);
			db.execSQL("DROP INDEX IF EXISTS NUMBER"+j+"INDEX");
		}
		db.execSQL("DROP TABLE IF EXISTS SYMBOL" + TABLE_NAME);
		db.execSQL("DROP INDEX IF EXISTS SYMBOLINDEX");

		for (int i = start; i <= end; i++) {
			for (int j = 1; j <= 9; j++) {
				db.execSQL("DROP TABLE IF EXISTS DICTIONARY"
						+ String.valueOf((char) i) + j + TABLE_NAME);
				db.execSQL("DROP INDEX IF EXISTS DICTIONARY"+String.valueOf((char) i)+j+"INDEX");
			}
		}
		db.execSQL("DROP TABLE IF EXISTS DICTIONARY" + TABLE_NAME);
		db.execSQL("DROP INDEX IF EXISTS DICTIONARYINDEX");
		
		
		onCreate(db);
	}

	/**
	 * Empty database records
	 */
	public void deleteAll() {

		SQLiteDatabase db = this.getWritableDatabase();

		for (int i = start; i <= end; i++) {
			for (int j = 1; j <= 9; j++) {
				db
						.delete(String.valueOf((char) i) + j + TABLE_NAME,
								null, null);
			}
		}
		for (int j = 1; j <= 9; j++) {
			db.delete("NUMBER" + j + TABLE_NAME, null, null);
		}
		db.delete("SYMBOL" + TABLE_NAME, null, null);

		for (int i = start; i <= end; i++) {
			for (int j = 1; j <= 9; j++) {
				db.delete("DICTIONARY" + String.valueOf((char) i) + j
						+ TABLE_NAME, null, null);
			}
		}
		db.delete("DICTIONARY" + TABLE_NAME, null, null);

		
		this.DELIMITER = "";
		this.finish = false;
		
		SharedPreferences storeset = ctx.getSharedPreferences(TOTAL_RECORD, 0);
		storeset.edit().putString(TOTAL_RECORD, String.valueOf(0)).commit();

		SharedPreferences resetstatus  = ctx.getSharedPreferences(RESET_STATUS, 0);
		resetstatus.edit().putString(RESET_STATUS, "yes").commit();
	}

	/**
	 * Empty Dictionary table records
	 */
	public void deleteDictionaryAll() {
	
		SQLiteDatabase db = this.getWritableDatabase();

		for (int i = start; i <= end; i++) {
			for (int j = 1; j <= 9; j++) {
				db.delete("DICTIONARY" + String.valueOf((char) i) + j
						+ TABLE_NAME, null, null);
			}
		}
		db.delete("DICTIONARY" + TABLE_NAME, null, null);

		this.DELIMITER = "";

		SharedPreferences storeset = ctx.getSharedPreferences(TOTAL_RELATED, 0);
		storeset.edit().putString(TOTAL_RELATED, String.valueOf(0)).commit();
		
	}

	public int getCount() {
		return count;
	}

	/**
	 * Count total amount loaded records amount
	 * @return
	 */
	public int countAll() {
		int total = 0;
		try {
			SQLiteDatabase db = this.getReadableDatabase();

			for (int i = start; i <= end; i++) {
				for (int j = 1; j <= 9; j++) {
					total += db.rawQuery(
							"SELECT * FROM " + String.valueOf((char) i) + j
									+ TABLE_NAME, null).getCount();
				}
			}
			for (int j = 1; j <= 9; j++) {
				total += db.rawQuery("SELECT * FROM NUMBER" + j + TABLE_NAME,
						null).getCount();
			}
			total += db.rawQuery("SELECT * FROM SYMBOL" + TABLE_NAME, null)
					.getCount();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return total;
	}

	/**
	 * Count total loaded dictionary amount.
	 * @return
	 */
	public int countDictionaryAll() {
		int total = 0;
		try {
			SQLiteDatabase db = this.getReadableDatabase();

			for (int i = start; i <= end; i++) {
				for (int j = 1; j <= 9; j++) {
					total += db
							.rawQuery(
									"SELECT * FROM DICTIONARY"
											+ String.valueOf((char) i) + j
											+ TABLE_NAME, null).getCount();
				}
			}
			total += db.rawQuery("SELECT * FROM DICTIONARY" + TABLE_NAME, null)
					.getCount();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return total;
	}
	
	
	/**
	 * Insert mapping item into database
	 * @param source
	 */
	public void insertList(ArrayList<String> source) {


		SharedPreferences settings = ctx.getSharedPreferences(TOTAL_RECORD, 0);        
		String recordString = settings.getString(TOTAL_RECORD, "");
		
		int total = 0;
		try{
			total = Integer.parseInt(recordString);
		}catch(Exception e){}
		
		SQLiteDatabase db = this.getWritableDatabase();
		this.identifyDelimiter(source);

		for (String unit : source) {

			try {
				String code = unit.substring(0, unit.indexOf(this.DELIMITER));
				String word = unit.substring(unit.indexOf(this.DELIMITER) + 1);

				if (code == null || code.trim().equals("")) {
					continue;
				}
				
				if (word == null || word.trim().equals("")) {
					continue;
				}

				if (code.equalsIgnoreCase("@VERSION@")){
					SharedPreferences version = ctx.getSharedPreferences(MAPPING_VERSION, 0);
					version.edit().putString(MAPPING_VERSION, word.trim()).commit();
					continue;
				}
				
				ContentValues cv = new ContentValues();
				cv.put(FIELD_WORD, word);
				cv.put(FIELD_SCORE, 0);
				String firstCharacter = code.substring(0, 1).toUpperCase();
				int j = code.length();
				if (firstCharacter.matches(".*?[^A-Z]")) {
					cv.put(FIELD_CODE, code.toUpperCase());
					if (firstCharacter.matches(".*?[^0-9]")) {
						db.insert("SYMBOL" + TABLE_NAME, null, cv);
						total++;
					} else {
						if (j < 9) {
							db.insert("NUMBER" + j + TABLE_NAME, null, cv);
							total++;
						} else {
							db.insert("NUMBER" + 9 + TABLE_NAME, null, cv);
							total++;
						}
					}
				} else {
					cv.put(FIELD_CODE, code.toUpperCase());
					if (j < 9) {
						db.insert(firstCharacter + j + TABLE_NAME, null, cv);
						total++;
					} else {
						db.insert(firstCharacter + 9 + TABLE_NAME, null, cv);
						total++;
					}
				}
				count++;

			} catch (Exception e) {
			}
		}
		
		SharedPreferences storeset = ctx.getSharedPreferences(TOTAL_RECORD, 0);
		storeset.edit().putString(TOTAL_RECORD, String.valueOf(total)).commit();
		
	}

	/**
	 * Batch add dictionary items into database
	 * @param srclist
	 */
	public void batchAddDictionary(ArrayList<Mapping> srclist) {

		SQLiteDatabase db = this.getReadableDatabase();

		int total = 0;
		for (Mapping unit : srclist) {

			String code = unit.getPcode();
			int j = code.length();

			String firstCharacter = code.substring(0, 1).toUpperCase();

			ContentValues cv = new ContentValues();
			cv.put(FIELD_DIC_pcode, unit.getPcode());
			cv.put(FIELD_DIC_pword, unit.getPword());
			cv.put(FIELD_DIC_ccode, unit.getCode());
			cv.put(FIELD_DIC_cword, unit.getWord());
			cv.put(FIELD_DIC_score, unit.getScore());

			if (firstCharacter.matches(".*?[^A-Z]")) {
				db.insert("DICTIONARY" + TABLE_NAME, null, cv);
			} else {
				if (j < 9) {
					db.insert("DICTIONARY" + firstCharacter + j + TABLE_NAME,
							null, cv);
				} else {
					db.insert("DICTIONARY" + firstCharacter + 9 + TABLE_NAME,
							null, cv);
				}
			}
			total++;
		}

		SharedPreferences settings = ctx.getSharedPreferences(TOTAL_RELATED, 0);
		settings.edit().putString(TOTAL_RELATED, String.valueOf(total)).commit();
		
	}

	/**
	 * Create dictionary database
	 * @param srclist
	 */
	public void addDictionary(ArrayList<Mapping> srclist) {

		SharedPreferences settings = ctx.getSharedPreferences(TOTAL_RELATED, 0);        
		String dictionarString = settings.getString(TOTAL_RELATED, "");
		
		int total = 0;
		
		try{
			total = Integer.parseInt(dictionarString);
		}catch(Exception e){}
		
		boolean first = true;
		Mapping preunit = null;

		SQLiteDatabase db = this.getReadableDatabase();

		for (Mapping unit : srclist) {

			String code = unit.getCode();

			if (!first) {
				preunit = unit;
				first = false;
				continue;
			}

			if (preunit != null && preunit.getCode().length() > 0
					&& unit != null && unit.getCode().length() > 0) {
				if (!this.isExists(preunit.getCode(), preunit.getWord(), unit
						.getCode(), unit.getWord())) {
					int j = preunit.getCode().length();
					String firstCharacter = preunit.getCode().substring(0, 1)
							.toUpperCase();

					ContentValues cv = new ContentValues();
					cv.put(FIELD_DIC_pcode, preunit.getCode());
					cv.put(FIELD_DIC_pword, preunit.getWord());
					cv.put(FIELD_DIC_ccode, unit.getCode());
					cv.put(FIELD_DIC_cword, unit.getWord());
					cv.put(FIELD_DIC_score, 0);

					if (firstCharacter.matches(".*?[^A-Z]")) {
						db.insert("DICTIONARY" + TABLE_NAME, null, cv);
						total++;
					} else {
						if (j < 9) {
							db.insert("DICTIONARY" + firstCharacter + j
									+ TABLE_NAME, null, cv);
							total++;
						} else {
							db.insert("DICTIONARY" + firstCharacter + 9
									+ TABLE_NAME, null, cv);
							total++;
						}
					}
				}
			}
			preunit = unit;
		}
		
		SharedPreferences storeset = ctx.getSharedPreferences(TOTAL_RELATED, 0);
		storeset.edit().putString(TOTAL_RELATED, String.valueOf(total)).commit();
		
	}

	/** 
	 * Get dictionary database contents
	 * @param keyword
	 * @return
	 */
	public ArrayList<Mapping> getDictionary(String keyword) {

		ArrayList<Mapping> result = new ArrayList<Mapping>();
		SQLiteDatabase db = this.getReadableDatabase();

		// Create Suggestions (Exactly Matched)
		if (keyword != null && !keyword.trim().equals("")) {

			String firstCharacter = keyword.substring(0, 1).toUpperCase();
			Cursor cursor = null;

			int j = keyword.length();

			try {
				keyword = keyword.toUpperCase();
			} catch (Exception e) {
			}

			// Get Exactly Matched
			if (firstCharacter.matches(".*?[^A-Z]")) {
				cursor = db.query("DICTIONARY" + TABLE_NAME, null,
						FIELD_DIC_pcode + " = '" + keyword + "'", null, null,
						null, FIELD_DIC_score + " DESC", null);
			} else {
				if (j < 9) {
					cursor = db.query("DICTIONARY" + firstCharacter + j
							+ TABLE_NAME, null, FIELD_DIC_pcode + " = '"
							+ keyword + "'", null, null, null, FIELD_DIC_score
							+ " DESC", null);
				} else {
					cursor = db.query("DICTIONARY" + firstCharacter + 9
							+ TABLE_NAME, null, FIELD_DIC_pcode + " = '"
							+ keyword + "'", null, null, null, FIELD_DIC_score
							+ " DESC", null);
				}
			}
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

			if(cursor != null){
				cursor.deactivate(); 
				cursor.close(); 
			}
		}
		
		return result;
	}

	/**
	 * Add score to the mapping item
	 * @param srcunit
	 */
	public void addScore(Mapping srcunit) {

		String id = srcunit.getId();
		String code = srcunit.getCode();
		String word = srcunit.getWord();
		String pcode = srcunit.getPcode();
		String pword = srcunit.getPword();

		int score = srcunit.getScore();
		int j = 0;

		if (code != null && word != null && !code.trim().equals("")
				&& !word.trim().equals("")) {

			SQLiteDatabase db = this.getReadableDatabase();
			String firstCharacter = "";

			if (srcunit.isDictionary()) {
				firstCharacter = pcode.substring(0, 1).toUpperCase();
				j = pcode.length();
			} else {
				firstCharacter = code.substring(0, 1).toUpperCase();
				j = code.length();
			}

			if (firstCharacter.matches(".*?[^A-Z]")) {
				score++;
				ContentValues cv = new ContentValues();
				cv.put(FIELD_SCORE, score);

				if (srcunit.isDictionary()) {
					if (firstCharacter.matches(".*?[^0-9]")) {
						db.update("DICTIONARY" + TABLE_NAME, cv, FIELD_id
								+ " = " + id, null);
					}
				} else {
					if (firstCharacter.matches(".*?[^0-9]")) {
						db.update("SYMBOL" + TABLE_NAME, cv, FIELD_id + " = "
								+ id, null);
					} else {
						if (j < 9) {
							db.update("NUMBER" + j + TABLE_NAME, cv, FIELD_id
									+ " = " + id, null);
						} else {
							db.update("NUMBER" + 9 + TABLE_NAME, cv, FIELD_id
									+ " = " + id, null);
						}
					}
				}
			} else {
				score++;
				ContentValues cv = new ContentValues();
				if (srcunit.isDictionary()) {
					cv.put(FIELD_DIC_score, score);
					if (j < 9) {

						db.update("DICTIONARY" + firstCharacter + j
								+ TABLE_NAME, cv, FIELD_id + " = " + id, null);
					} else {
						db.update("DICTIONARY" + firstCharacter + 9
								+ TABLE_NAME, cv, FIELD_id + " = " + id, null);
					}
				} else {
					cv.put(FIELD_SCORE, score);
					if (j < 9) {
						db.update(firstCharacter + j + TABLE_NAME, cv, FIELD_id
								+ " = " + id, null);
					} else {
						db.update(firstCharacter + 9 + TABLE_NAME, cv, FIELD_id
								+ " = " + id, null);
					}
				}
			}
			
		}
	}

	/**
	 * Load source file and add records into database
	 */
	public void loadFile() {


		SharedPreferences version = ctx.getSharedPreferences(MAPPING_VERSION, 0);
		version.edit().putString(MAPPING_VERSION, "").commit();
		
		Thread thread = new Thread() {
			public void run() {

				boolean hasMappingVersion = false;
				finish = false;
				String line = "";
				
				try {
					FileReader fr = new FileReader(filename);
					BufferedReader buf = new BufferedReader(fr);
					ArrayList list = new ArrayList();
					int cc = 0;
					while ((line = buf.readLine()) != null) {

						// Check Format
						if (line == null) {
							continue;
						}
						if (line.trim().equals("")) {
							continue;
						}
						list.add(line.trim());
						if (cc == 100) {
							insertList(list);
							list = new ArrayList();
							cc = 0;
						} else {
							cc++;
						}
					}
					// Insert Last Batch
					insertList(list);

				} catch (Exception e) {
					e.printStackTrace();
				}
				finish = true;
			}
		};
		thread.start();
	}

	/**
	 * Identify the delimiter of the source file
	 * @param src
	 */
	public void identifyDelimiter(ArrayList<String> src) {

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
	 * @param pcode
	 * @param pword
	 * @param ccode
	 * @param cword
	 * @return
	 */
	public boolean isExists(String pcode, String pword, String ccode,
			String cword) {

		SQLiteDatabase db = this.getReadableDatabase();

		// Create Suggestions (Exactly Matched)
		if (pcode != null && !pcode.trim().equals("") && ccode != null
				&& !ccode.trim().equals("")) {

			String firstCharacter = pcode.substring(0, 1).toUpperCase();
			Cursor cursor = null;

			int j = pcode.length();

			// Get Exactly Matched
			if (firstCharacter.matches(".*?[^A-Z]")) {
				cursor = db.query("DICTIONARY" + TABLE_NAME, null,
						FIELD_DIC_pcode + " = '" + pcode + "' AND "
								+ FIELD_DIC_ccode + " = '" + ccode + "'", null,
						null, null, null, null);
			} else {
				if (j < 9) {
					cursor = db.query("DICTIONARY" + firstCharacter + j
							+ TABLE_NAME, null, FIELD_DIC_pword + " = '"
							+ pword + "' AND " + FIELD_DIC_cword + " = '"
							+ cword + "' AND " + FIELD_DIC_pcode + " = '"
							+ pcode + "' AND " + FIELD_DIC_ccode + " = '"
							+ ccode + "'", null, null, null, null, null);
				} else {
					cursor = db.query("DICTIONARY" + firstCharacter + 9
							+ TABLE_NAME, null, FIELD_DIC_pword + " = '"
							+ pword + "' AND " + FIELD_DIC_cword + " = '"
							+ cword + "' AND " + FIELD_DIC_pcode + " = '"
							+ pcode + "' AND " + FIELD_DIC_ccode + " = '"
							+ ccode + "'", null, null, null, null, null);
				}
			}

			if(cursor != null){
				if (cursor.getCount() > 0) {
					cursor.deactivate(); 
					cursor.close(); 
					return true;
				}
				cursor.deactivate(); 
				cursor.close(); 

			}
		}
		return false;
	}

	/**
	 * Get Suggestion list
	 * @param keyword
	 * @return
	 */
	public ArrayList<Mapping> getSuggestions(String keyword, boolean hasSimiliar) {

		ArrayList<Mapping> result = new ArrayList<Mapping>();
		SQLiteDatabase db = this.getReadableDatabase();

		// Create Suggestions (Exactly Matched)
		if (keyword != null && !keyword.trim().equals("")) {

			if (keyword.indexOf("\n") != -1) {
				keyword = keyword.replaceAll("\n", "");
			}

			String firstCharacter = keyword.substring(0, 1).toUpperCase();
			Cursor cursor = null;
			Cursor cursorPossible = null;

			int j = keyword.length();

			String origKeyword = keyword;
			try {
				keyword = keyword.toUpperCase();
			} catch (Exception e) {
			}

			// Get Exactly Matched
			if (firstCharacter.matches(".*?[^A-Z]")) {
				if (firstCharacter.matches(".*?[^0-9]")) {
					cursor = db.query("SYMBOL" + TABLE_NAME, null, FIELD_CODE
							+ " = '" + keyword + "'", null, null, null,
							FIELD_SCORE + " DESC", null);
				} else {
					if (j < 9) {
						cursor = db.query("NUMBER" + j + TABLE_NAME, null,
								FIELD_CODE + " = '" + keyword + "'", null,
								null, null, FIELD_SCORE + " DESC", null);
					} else {
						cursor = db.query("NUMBER" + 9 + TABLE_NAME, null,
								FIELD_CODE + " = '" + keyword + "'", null,
								null, null, FIELD_SCORE + " DESC", null);
					}
				}
			} else {
				if (j < 9) {
					cursor = db.query(firstCharacter + j + TABLE_NAME, null,
							FIELD_CODE + " = '" + keyword + "'", null, null,
							null, FIELD_SCORE + " DESC", null);
				} else {
					cursor = db.query(firstCharacter + 9 + TABLE_NAME, null,
							FIELD_CODE + " = '" + keyword + "'", null, null,
							null, FIELD_SCORE + " DESC", null);
				}
			}
			result.add(new Mapping("", origKeyword, 0));
			try {
				if (cursor.moveToFirst()) {
					int wordColumn = cursor.getColumnIndex(FIELD_WORD);
					int codeColumn = cursor.getColumnIndex(FIELD_CODE);
					int scoreColumn = cursor.getColumnIndex(FIELD_SCORE);
					int idColumn = cursor.getColumnIndex(FIELD_id);
					do {
						Mapping munit = new Mapping();
						munit.setId(cursor.getString(idColumn));
						munit.setCode(cursor.getString(codeColumn));
						munit.setWord(cursor.getString(wordColumn));
						munit.setScore(cursor.getInt(scoreColumn));
						result.add(munit);
					} while (cursor.moveToNext());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			// Get Possible Matched
			if(hasSimiliar){
				if (firstCharacter.matches(".*?[^A-Z]")) {
					if (firstCharacter.matches(".*?[^0-9]")) {
						cursorPossible = db.query("SYMBOL" + TABLE_NAME, null,
								FIELD_CODE + " <> '" + keyword + "' AND "
										+ FIELD_CODE + " LIKE '" + keyword + "_'",
										null, null, null, FIELD_SCORE + " DESC", limit);
					} else {
						if (j < 8) {
							cursorPossible = db.query("NUMBER" + (j + 1)
									+ TABLE_NAME, null, FIELD_CODE + " <> '"
									+ keyword + "' AND " + FIELD_CODE + " LIKE '"
									+ keyword + "_'", null, null, null, FIELD_SCORE
									+ " DESC", limit);
						} else {
							cursorPossible = db.query("NUMBER" + 9 + TABLE_NAME,
									null, FIELD_CODE + " <> '" + keyword + "' AND "
											+ FIELD_CODE + " LIKE '" + keyword
											+ "_'", null, null, null, FIELD_SCORE
											+ " DESC", limit);
						}
					}
				} else {
					if (j < 8) {
						cursorPossible = db.query(firstCharacter + (j + 1)
								+ TABLE_NAME, null, FIELD_CODE + " LIKE '"
								+ keyword + "_'", null, null, null, FIELD_SCORE
								+ " DESC", limit);
					} else {
						cursorPossible = db.query(firstCharacter + 9 + TABLE_NAME,
								null, FIELD_CODE + " LIKE '" + keyword + "_'",
								null, null, null, FIELD_SCORE + " DESC", limit);
					}
				}
	
				try {
					if (cursorPossible.moveToFirst()) {
						int wordColumn = cursorPossible.getColumnIndex(FIELD_WORD);
						int codeColumn = cursorPossible.getColumnIndex(FIELD_CODE);
						int scoreColumn = cursorPossible
								.getColumnIndex(FIELD_SCORE);
						int idColumn = cursorPossible.getColumnIndex(FIELD_id);
						do {
							Mapping munit = new Mapping();
							munit.setId(cursorPossible.getString(idColumn));
							munit.setCode(cursorPossible.getString(codeColumn));
							munit.setWord(cursorPossible.getString(wordColumn));
							munit.setScore(cursorPossible.getInt(scoreColumn));
							result.add(munit);
						} while (cursorPossible.moveToNext());
					}
				} catch (Exception e) {
					e.printStackTrace();
				}

				if(cursorPossible != null){
					cursorPossible.deactivate(); 
					cursorPossible.close(); 
				}
			}
			
			if(cursor != null){
				cursor.deactivate(); 
				cursor.close(); 
			}

		}
		
		return result;
	}

	/**
	 * Sort array
	 * @param src
	 * @return
	 */
	public ArrayList<Mapping> sortArray(ArrayList<Mapping> src) {
		for (int i = 1; i < (src.size() - 1); i++) {
			for (int j = i + 1; j < src.size(); j++) {
				if (src.get(j).getScore() > src.get(i).getScore()) {
					Mapping dummy = src.get(i);
					src.set(i, src.get(j));
					src.set(j, dummy);
				}
			}
		}
		return src;
	}

	/** 
	 * Backup dictionary items
	 */
	public void backupRelatedDb() {

		File targetFile = new File("/sdcard/limedb.txt");
		targetFile.deleteOnExit();

		Cursor cursor = null;

		ArrayList temp = new ArrayList();

		try {
			SQLiteDatabase db = this.getReadableDatabase();

			for (int i = start; i <= end; i++) {
				for (int j = 1; j <= 9; j++) {
					cursor = db.rawQuery("SELECT * FROM DICTIONARY"
							+ String.valueOf((char) i) + j + TABLE_NAME, null);
					try {
						if (cursor.moveToFirst()) {
							int idColumn = cursor.getColumnIndex(FIELD_id);
							int pcodeColumn = cursor.getColumnIndex(FIELD_DIC_pcode);
							int pwordColumn = cursor.getColumnIndex(FIELD_DIC_pword);
							int codeColumn = cursor.getColumnIndex(FIELD_DIC_ccode);
							int wordColumn = cursor.getColumnIndex(FIELD_DIC_cword);
							int scoreColumn = cursor.getColumnIndex(FIELD_DIC_score);
							do {
								Mapping munit = new Mapping();
								munit.setId(cursor.getString(idColumn));
								munit.setPcode(cursor.getString(pcodeColumn));
								munit.setPword(cursor.getString(pwordColumn));
								munit.setCode(cursor.getString(codeColumn));
								munit.setWord(cursor.getString(wordColumn));
								munit.setScore(cursor.getInt(scoreColumn));
								temp.add(munit);
							} while (cursor.moveToNext());
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			cursor = db.rawQuery("SELECT * FROM DICTIONARY" + TABLE_NAME, null);

			try {
				if (cursor.moveToFirst()) {
					int idColumn = cursor.getColumnIndex(FIELD_id);
					int pcodeColumn = cursor.getColumnIndex(FIELD_DIC_pcode);
					int pwordColumn = cursor.getColumnIndex(FIELD_DIC_pword);
					int codeColumn = cursor.getColumnIndex(FIELD_DIC_ccode);
					int wordColumn = cursor.getColumnIndex(FIELD_DIC_cword);
					int scoreColumn = cursor.getColumnIndex(FIELD_DIC_score);
					do {
						Mapping munit = new Mapping();
						munit.setId(cursor.getString(idColumn));
						munit.setPcode(cursor.getString(pcodeColumn));
						munit.setPword(cursor.getString(pwordColumn));
						munit.setCode(cursor.getString(codeColumn));
						munit.setWord(cursor.getString(wordColumn));
						munit.setScore(cursor.getInt(scoreColumn));
						temp.add(munit);
					} while (cursor.moveToNext());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if(cursor != null){
			cursor.deactivate(); 
			cursor.close(); 
		}

		Iterator itr = temp.iterator();

		FileOutputStream fos;
		try {
			
			OutputStream out = new FileOutputStream(targetFile, false); 
			Writer writer = new OutputStreamWriter(out, "UTF-8"); 
			while (itr.hasNext()) {
				Mapping unit = (Mapping) itr.next();
				String line = unit.getPcode() + "\t" + unit.getPword() + "\t" +
							  unit.getCode() + "\t" + unit.getWord() + "\t" + 
							  unit.getScore() + "\r\n";
				writer.write(new String(line.getBytes("UTF-8")));
			}
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * Restore from backup file
	 */
	public void restoreRelatedDb() {
		
		File targetFile = new File("/sdcard/limedb.txt");
		
		ArrayList temp = new ArrayList();
		if(targetFile.exists()){
			
			this.deleteDictionaryAll();

			int total = 0 ;
			FileReader fis;
			try {
				fis = new FileReader(targetFile);
				BufferedReader br = new BufferedReader(fis);
				
				String line = "";
				
				while( (line = br.readLine()) != null){
					try{
						String pcode = line.split("\t")[0];
						String pword = line.split("\t")[1];
						String code = line.split("\t")[2];
						String word = line.split("\t")[3];
						String score = line.split("\t")[4];
						temp.add(new Mapping(pcode, pword, code, word, Integer.parseInt(score)));
						total++;
					}catch(Exception e){
						e.printStackTrace();
					}
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} 

			SharedPreferences settings = ctx.getSharedPreferences(TOTAL_RELATED, 0);
			settings.edit().putString(TOTAL_RELATED, String.valueOf(total)).commit();
		}
		
		this.batchAddDictionary(temp);
	}
	

}
