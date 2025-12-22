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

package net.toload.main.hd.data;

import android.database.Cursor;

import net.toload.main.hd.global.LIME;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a mapping record in the database.
 * 
 * <p>A Record contains the mapping between an input code and an output word,
 * along with scoring information and optional related phrase data.
 * 
 * <p>This class provides static helper methods to convert Cursor objects
 * to Record instances, but does not contain any SQL code. All database
 * operations should be performed through {@link net.toload.main.hd.limedb.LimeDB}.
 * 
 * @author LimeIME Team
 */
public class Record {

	private int id;
	private String code;
	private String code3r;
	private String word;
	private String related;
	private int score;
	private int basescore;

	/**
	 * Gets the record ID.
	 * 
	 * @return The unique identifier for this record
	 */
	public int getId() {
		return id;
	}

	/**
	 * Sets the record ID.
	 * 
	 * @param id The unique identifier for this record
	 */
	public void setId(int id) {
		this.id = id;
	}

	/**
	 * Gets the input code for this mapping.
	 * 
	 * @return The input code string
	 */
	public String getCode() {
		return code;
	}

	/**
	 * Sets the input code for this mapping.
	 * 
	 * @param code The input code string
	 */
	public void setCode(String code) {
		this.code = code;
	}

	/**
	 * Gets the code without tone keys (for phonetic table).
	 * 
	 * @return The code without tone keys (3, 4, 6, 7)
	 */
	public String getCode3r() {
		return code3r;
	}

	/**
	 * Sets the code without tone keys.
	 * 
	 * @param code3r The code without tone keys
	 */
	public void setCode3r(String code3r) {
		this.code3r = code3r;
	}

	/**
	 * Gets the output word for this mapping.
	 * 
	 * @return The output word string
	 */
	public String getWord() {
		return word;
	}

	/**
	 * Sets the output word for this mapping.
	 * 
	 * @param word The output word string
	 */
	public void setWord(String word) {
		this.word = word;
	}

	/**
	 * Gets the related phrase information.
	 * 
	 * @return The related phrase string, or null if none
	 */
	public String getRelated() {
		return related;
	}

	/**
	 * Sets the related phrase information.
	 * 
	 * @param related The related phrase string
	 */
	public void setRelated(String related) {
		this.related = related;
	}

	/**
	 * Gets the user score for this mapping.
	 * 
	 * <p>Higher scores indicate more frequently used mappings.
	 * 
	 * @return The user score
	 */
	public int getScore() {
		return score;
	}

	/**
	 * Sets the user score for this mapping.
	 * 
	 * @param score The user score
	 */
	public void setScore(int score) {
		this.score = score;
	}

	/**
	 * Gets the base score for this mapping.
	 * 
	 * <p>The base score is the original score from the preloaded database.
	 * 
	 * @return The base score
	 */
	public int getBasescore() {
		return basescore;
	}

	/**
	 * Sets the base score for this mapping.
	 * 
	 * @param basescore The base score
	 */
	public void setBasescore(int basescore) {
		this.basescore = basescore;
	}


	/**
	 * Helper method to safely get a String from cursor.
	 * 
	 * <p>Validates that the column exists before accessing it.
	 * 
	 * @param cursor The Cursor to read from
	 * @param columnName The column name to read
	 * @return The column value, or empty string if column is missing
	 */
	private static String getCursorString(Cursor cursor, String columnName) {
		int index = cursor.getColumnIndex(columnName);
		if (index >= 0) {
			return cursor.getString(index);
		}
		return ""; // Return empty string if column is missing
	}

	/**
	 * Helper method to safely get an int from cursor.
	 * 
	 * <p>Validates that the column exists before accessing it.
	 * 
	 * @param cursor The Cursor to read from
	 * @param columnName The column name to read
	 * @return The column value, or 0 if column is missing
	 */
	private static int getCursorInt(Cursor cursor, String columnName) {
		int index = cursor.getColumnIndex(columnName);
		if (index >= 0) {
			return cursor.getInt(index);
		}
		return 0; // Return 0 if column is missing
	}

	/**
	 * Creates a Record object from a Cursor row.
	 * 
	 * <p>This method reads the current row from the cursor and creates
	 * a Record object with the column values. The cursor should be
	 * positioned at the desired row before calling this method.
	 * 
	 * @param cursor The Cursor positioned at the desired row
	 * @return A new Record object populated with cursor data
	 */
	public static Record get(Cursor cursor){
		Record record = new Record();
			// Use helper methods to safely get column values (validates column index >= 0)
			record.setId(getCursorInt(cursor, LIME.DB_COLUMN_ID));
			record.setCode(getCursorString(cursor, LIME.DB_COLUMN_CODE));
			//record.setCode3r(cursor.getString(cursor.getColumnIndex(LIME.DB_COLUMN_CODE3R)));  Jeremy '15,6,6 may not present in old db.
			record.setWord(getCursorString(cursor, LIME.DB_COLUMN_WORD));
			record.setRelated(getCursorString(cursor, LIME.DB_COLUMN_RELATED));
			record.setScore(getCursorInt(cursor, LIME.DB_COLUMN_SCORE));
			record.setBasescore(getCursorInt(cursor, LIME.DB_COLUMN_BASESCORE));
		return record;
	}
	
	/**
	 * Converts a Cursor to a List of Record objects.
	 * 
	 * <p>This method iterates through all rows in the cursor and creates
	 * Record objects for each row. The cursor is closed after processing.
	 * 
	 * @param cursor The Cursor containing database query results
	 * @return List of Record objects
	 */
	public static List<Record> getList(Cursor cursor){
		List<Record> list = new ArrayList<>();
		cursor.moveToFirst();
		while(!cursor.isAfterLast()){
			list.add(get(cursor));
			cursor.moveToNext();
		}
		cursor.close();
		return list;
	}

}
