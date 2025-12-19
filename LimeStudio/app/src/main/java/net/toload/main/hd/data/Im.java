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
import net.toload.main.hd.limedb.LimeDB;

import java.util.ArrayList;
import java.util.List;

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


	public static Im get(LimeDB db, Cursor cursor){
		Im record = new Im();
			record.setId(db.getCursorInt(cursor, LIME.DB_IM_COLUMN_ID));
			record.setCode(db.getCursorString(cursor, LIME.DB_IM_COLUMN_CODE));
			record.setTitle(db.getCursorString(cursor, LIME.DB_IM_COLUMN_TITLE));
			record.setDesc(db.getCursorString(cursor, LIME.DB_IM_COLUMN_DESC));
			record.setKeyboard(db.getCursorString(cursor, LIME.DB_IM_COLUMN_KEYBOARD));
            record.setDisable(Boolean.getBoolean(db.getCursorString(cursor,LIME.DB_IM_COLUMN_DISABLE)));
			record.setSelkey(db.getCursorString(cursor, LIME.DB_IM_COLUMN_SELKEY));
			record.setEndkey(db.getCursorString(cursor, LIME.DB_IM_COLUMN_ENDKEY));
			record.setSpacestyle(db.getCursorString(cursor, LIME.DB_IM_COLUMN_SPACESTYLE));
		return record;
	}


	public static List<Im> getList(LimeDB db, Cursor cursor){
		List<Im> list = new ArrayList<>();
		cursor.moveToFirst();
		while(!cursor.isAfterLast()){
			list.add(get(db, cursor));
			cursor.moveToNext();
		}
		cursor.close();
		return list;
	}
		
	public static String getInsertQuery(Im record){

        return "INSERT INTO " + LIME.DB_IM + "(" +
                LIME.DB_IM_COLUMN_CODE + ", " +
                LIME.DB_IM_COLUMN_TITLE + ", " +
                LIME.DB_IM_COLUMN_DESC + ", " +
                LIME.DB_IM_COLUMN_KEYBOARD + ", " +
                LIME.DB_IM_COLUMN_DISABLE + ", " +
                LIME.DB_IM_COLUMN_SELKEY + ", " +
                LIME.DB_IM_COLUMN_ENDKEY + ", " +
                LIME.DB_IM_COLUMN_SPACESTYLE + ") VALUES(" +
                "\"" + record.getCode() + "\"," +
                "\"" + record.getTitle() + "\"," +
                "\"" + record.getDesc() + "\"," +
                "\"" + record.getKeyboard() + "\"," +
                "\"" + record.isDisable() + "\"," +
                "\"" + record.getSelkey() + "\"," +
                "\"" + record.getEndkey() + "\"," +
                "\"" + record.getSpacestyle() + "\"" +
                ")";
	}

}
