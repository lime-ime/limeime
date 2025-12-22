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
 * Represents a related phrase record in the database.
 * 
 * <p>A Related record contains a parent word and a child word that are
 * commonly used together, along with scoring information.
 * 
 * <p>This class provides static helper methods to convert Cursor objects
 * to Related instances, but does not contain any SQL code. All database
 * operations should be performed through {@link net.toload.main.hd.limedb.LimeDB}.
 * 
 * @author LimeIME Team
 */
public class Related {

	private int id;
	private String pword;
	private String cword;
	private int basescore;
	private int userscore;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getPword() {
		return pword;
	}

	public void setPword(String pword) {
		this.pword = pword;
	}

	public String getCword() {
		return cword;
	}

	public void setCword(String cword) {
		this.cword = cword;
	}

	public int getBasescore() {
		return basescore;
	}

	public void setBasescore(int basescore) {
		this.basescore = basescore;
	}

	public int getUserscore() {return userscore;}

	public void setUserscore(int userscore) {this.userscore = userscore;}

	// Helper to safely get a String from cursor (validates column index >= 0)
	private static String getCursorString(Cursor cursor, String columnName) {
		int index = cursor.getColumnIndex(columnName);
		if (index >= 0) {
			return cursor.getString(index);
		}
		return ""; // Return empty string if column is missing
	}

	// Helper to safely get an Int from cursor (validates column index >= 0)
	private static int getCursorInt(Cursor cursor, String columnName) {
		int index = cursor.getColumnIndex(columnName);
		if (index >= 0) {
			return cursor.getInt(index);
		}
		return 0; // Return 0 if column is missing
	}

	public static Related get(Cursor cursor){
		Related record = new Related();
			// Use helper methods to safely get column values (validates column index >= 0)
			record.setId(getCursorInt(cursor, LIME.DB_RELATED_COLUMN_ID));
			record.setPword(getCursorString(cursor, LIME.DB_RELATED_COLUMN_PWORD));
			record.setCword(getCursorString(cursor, LIME.DB_RELATED_COLUMN_CWORD));
			record.setUserscore(getCursorInt(cursor, LIME.DB_RELATED_COLUMN_USERSCORE));
			record.setBasescore(getCursorInt(cursor, LIME.DB_RELATED_COLUMN_BASESCORE));
		return record;
	}

	/**
	 * Converts a Cursor to a List of Related objects.
	 * 
	 * <p>This method iterates through all rows in the cursor and creates
	 * Related objects for each row. The cursor is closed after processing.
	 * 
	 * @param cursor The Cursor containing database query results
	 * @return List of Related objects
	 */
	public static List<Related> getList(Cursor cursor){
		List<Related> list = new ArrayList<>();
		cursor.moveToFirst();
		while(!cursor.isAfterLast()){
			list.add(get(cursor));
			cursor.moveToNext();
		}
		cursor.close();
		return list;
	}

}
