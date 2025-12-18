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

public class Word {

	private int id;
	private String code;
	private String code3r;
	private String word;
	private String related;
	private int score;
	private int basescore;

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

	public String getCode3r() {
		return code3r;
	}

	public void setCode3r(String code3r) {
		this.code3r = code3r;
	}

	public String getWord() {
		return word;
	}

	public void setWord(String word) {
		this.word = word;
	}

	public String getRelated() {
		return related;
	}

	public void setRelated(String related) {
		this.related = related;
	}

	public int getScore() {
		return score;
	}

	public void setScore(int score) {
		this.score = score;
	}

	public int getBasescore() {
		return basescore;
	}

	public void setBasescore(int basescore) {
		this.basescore = basescore;
	}


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

	public static Word get(Cursor cursor){
		Word record = new Word();
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
	
	public static List<Word> getList(Cursor cursor){
		List<Word> list = new ArrayList<Word>();
		cursor.moveToFirst();
		while(!cursor.isAfterLast()){
			list.add(get(cursor));
			cursor.moveToNext();
		}
		cursor.close();
		return list;
	}

	public static String getInsertQuery(String table, Word record){
		StringBuffer sb = new StringBuffer();
		sb.append("INSERT INTO " + table + "(");
		sb.append(LIME.DB_COLUMN_CODE + ", ");
		if(table.equals("phonetic"))  sb.append(LIME.DB_COLUMN_CODE3R +", ");
		sb.append(LIME.DB_COLUMN_WORD +", ");
		sb.append(LIME.DB_COLUMN_RELATED +", ");
		sb.append(LIME.DB_COLUMN_SCORE +", ");
		sb.append(LIME.DB_COLUMN_BASESCORE +") VALUES(");
		sb.append("\""+LIME.formatSqlValue(record.getCode())+"\",");
		if(table.equals("phonetic")) sb.append("\""+LIME.formatSqlValue(record.getCode().replaceAll("[ 3467]", ""))+"\","); //Jeremy '15,6,6. remove 3467 tone keys from code as code3r
		sb.append("\""+LIME.formatSqlValue(record.getWord())+"\",");
		sb.append("\""+LIME.formatSqlValue(record.getRelated())+"\",");
		sb.append("\""+record.getScore()+"\",");
		sb.append("\""+record.getBasescore()+"\"");;
		sb.append(")");
		return sb.toString();
	}

	public static String getUpdateScoreQuery(String table, Word w){
		StringBuffer sb = new StringBuffer();
		sb.append("UPDATE " + table + " SET ");
		sb.append(LIME.DB_COLUMN_SCORE +"='");
		sb.append(w.getScore() +"', ");
		sb.append(LIME.DB_COLUMN_BASESCORE +"='");
		sb.append(w.getBasescore() +"' ");
		sb.append(" WHERE " + LIME.DB_COLUMN_ID + " ='");
		sb.append(w.getId() + "'");
		return sb.toString();
	}

}
