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
 * Represents an Input Method (IM) configuration record in the database.
 * 
 * <p>An Im record contains configuration information for an input method,
 * including keyboard assignment, selection keys, end keys, and spacing style.
 * 
 * <p>This class provides static helper methods to convert Cursor objects
 * to Im instances, but does not contain any SQL code. All database
 * operations should be performed through {@link net.toload.main.hd.limedb.LimeDB}.
 * 
 * @author LimeIME Team
 */
public class Im {

	private int id;
	private String code;
	private String title;
	private String desc;
	private String keyboard;
	private boolean disable;
	private String selkey;
	private String endkey;
	private String spacestyle;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}

	public String getKeyboard() {
		return keyboard;
	}

	public void setKeyboard(String keyboard) {
		this.keyboard = keyboard;
	}

	public boolean isDisable() {
		return disable;
	}

	public void setDisable(boolean disable) {
		this.disable = disable;
	}

	public String getSelkey() {
		return selkey;
	}

	public void setSelkey(String selkey) {
		this.selkey = selkey;
	}

	public String getEndkey() {
		return endkey;
	}

	public void setEndkey(String endkey) {
		this.endkey = endkey;
	}

	public String getSpacestyle() {
		return spacestyle;
	}

	public void setSpacestyle(String spacestyle) {
		this.spacestyle = spacestyle;
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
	 * Creates an Im object from a Cursor row.
	 * 
	 * <p>This method reads the current row from the cursor and creates
	 * an Im object with the column values. The cursor should be
	 * positioned at the desired row before calling this method.
	 * 
	 * @param cursor The Cursor positioned at the desired row
	 * @return A new Im object populated with cursor data
	 */
	public static Im get(Cursor cursor){
		Im record = new Im();
		record.setId(getCursorInt(cursor, LIME.DB_IM_COLUMN_ID));
		record.setCode(getCursorString(cursor, LIME.DB_IM_COLUMN_CODE));
		record.setTitle(getCursorString(cursor, LIME.DB_IM_COLUMN_TITLE));
		record.setDesc(getCursorString(cursor, LIME.DB_IM_COLUMN_DESC));
		record.setKeyboard(getCursorString(cursor, LIME.DB_IM_COLUMN_KEYBOARD));
		String disableStr = getCursorString(cursor, LIME.DB_IM_COLUMN_DISABLE);
		record.setDisable(Boolean.getBoolean(disableStr));
		record.setSelkey(getCursorString(cursor, LIME.DB_IM_COLUMN_SELKEY));
		record.setEndkey(getCursorString(cursor, LIME.DB_IM_COLUMN_ENDKEY));
		record.setSpacestyle(getCursorString(cursor, LIME.DB_IM_COLUMN_SPACESTYLE));
		return record;
	}

	/**
	 * Converts a Cursor to a List of Im objects.
	 * 
	 * <p>This method iterates through all rows in the cursor and creates
	 * Im objects for each row. The cursor is closed after processing.
	 * 
	 * @param cursor The Cursor containing database query results
	 * @return List of Im objects
	 */
	public static List<Im> getList(Cursor cursor){
		List<Im> list = new ArrayList<>();
		cursor.moveToFirst();
		while(!cursor.isAfterLast()){
			list.add(get(cursor));
			cursor.moveToNext();
		}
		cursor.close();
		return list;
	}

}
