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

public class Keyboard {

	private int id;
	private String code;
	private String name;
	private String desc;
	private String type;
	private String image;
	private String imkb;
	private String imshiftkb;
	private String engkb;
	private String engshiftkb;
	private String symbolkb;
	private String symbolshiftkb;
	private String defaultkb;
	private String defaultshiftkb;
	private String extendedkb;
	private String extendedshiftkb;

	private boolean disable;

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

	public static Keyboard get(Cursor cursor){
		Keyboard record = new Keyboard();
			// Use helper methods to safely get column values (validates column index >= 0)
			record.setId(getCursorInt(cursor, LIME.DB_KEYBOARD_COLUMN_ID));
			record.setCode(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_CODE));
			record.setName(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_NAME));
			record.setDesc(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_DESC));
			record.setType(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_TYPE));
			record.setImage(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_IMAGE));
			record.setImkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_IMKB));
			record.setImshiftkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_IMSHIFTKB));
			record.setEngkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_ENGKB));
			record.setEngshiftkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_ENGSHIFTKB));
			record.setSymbolkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_SYMBOLKB));
			record.setSymbolshiftkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_SYMBOLSHIFTKB));
			record.setDefaultkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_DEFAULTKB));
			record.setDefaultshiftkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_DEFAULTSHIFTKB));
			record.setExtendedkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_EXTENDEDKB));
			record.setExtendedshiftkb(getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_EXTENDEDSHIFTKB));
			String disableStr = getCursorString(cursor, LIME.DB_KEYBOARD_COLUMN_DISABLE);
			record.setDisable(Boolean.getBoolean(disableStr));
		return record;
	}

	public static List<Keyboard> getList(Cursor cursor){
		List<Keyboard> list = new ArrayList<Keyboard>();
		cursor.moveToFirst();
		while(!cursor.isAfterLast()){
			list.add(get(cursor));
			cursor.moveToNext();
		}
		cursor.close();
		return list;
	}


	public static String getInsertQuery(Keyboard record){
		StringBuffer sb = new StringBuffer();
		sb.append("INSERT INTO " + LIME.DB_KEYBOARD + "(");
		sb.append(LIME.DB_KEYBOARD_COLUMN_ID +", ");
		sb.append(LIME.DB_KEYBOARD_COLUMN_CODE+", ");
		sb.append(LIME.DB_KEYBOARD_COLUMN_NAME+", ");
		sb.append(LIME.DB_KEYBOARD_COLUMN_DESC +", ");
		sb.append(LIME.DB_KEYBOARD_COLUMN_TYPE +", ");
		sb.append(LIME.DB_KEYBOARD_COLUMN_IMAGE+", ");
		sb.append(LIME.DB_KEYBOARD_COLUMN_IMKB +", ");
		sb.append(LIME.DB_KEYBOARD_COLUMN_IMSHIFTKB+", ");
		sb.append(LIME.DB_KEYBOARD_COLUMN_ENGKB +", ");
		sb.append(LIME.DB_KEYBOARD_COLUMN_ENGSHIFTKB+", ");
		sb.append(LIME.DB_KEYBOARD_COLUMN_SYMBOLKB +", ");
		sb.append(LIME.DB_KEYBOARD_COLUMN_SYMBOLSHIFTKB +", ");
		sb.append(LIME.DB_KEYBOARD_COLUMN_DEFAULTKB +", ");
		sb.append(LIME.DB_KEYBOARD_COLUMN_DEFAULTSHIFTKB +", ");
		sb.append(LIME.DB_KEYBOARD_COLUMN_EXTENDEDKB +", ");
		sb.append(LIME.DB_KEYBOARD_COLUMN_EXTENDEDSHIFTKB +", ");
		sb.append(LIME.DB_KEYBOARD_COLUMN_DISABLE +") VALUES(");
		sb.append("\""+record.getId()+"\",");
		sb.append("\""+record.getCode()+"\",");
		sb.append("\""+record.getName()+"\",");
		sb.append("\""+record.getDesc()+"\",");
		sb.append("\""+record.getType()+"\",");
		sb.append("\""+record.getImage()+"\",");
		sb.append("\""+record.getImkb()+"\",");
		sb.append("\""+record.getImshiftkb()+"\",");
		sb.append("\""+record.getEngkb()+"\",");
		sb.append("\""+record.getEngshiftkb()+"\",");
		sb.append("\""+record.getSymbolkb()+"\",");
		sb.append("\""+record.getSymbolshiftkb()+"\",");
		sb.append("\""+record.getDefaultkb()+"\",");
		sb.append("\""+record.getDefaultshiftkb()+"\",");
		sb.append("\""+record.getExtendedkb()+"\",");
		sb.append("\""+record.getExtendedshiftkb()+"\",");
		sb.append("\""+record.isDisable()+"\"");
		sb.append(")");
		return sb.toString();
	}

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

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}

	public String getImkb() {
		return imkb;
	}

	public void setImkb(String imkb) {
		this.imkb = imkb;
	}

	public String getImshiftkb() {
		return imshiftkb;
	}

	public void setImshiftkb(String imshiftkb) {
		this.imshiftkb = imshiftkb;
	}

	public String getEngkb() {
		return engkb;
	}

	public void setEngkb(String engkb) {
		this.engkb = engkb;
	}

	public String getEngshiftkb() {
		return engshiftkb;
	}

	public void setEngshiftkb(String engshiftkb) {
		this.engshiftkb = engshiftkb;
	}

	public String getSymbolkb() {
		return symbolkb;
	}

	public void setSymbolkb(String symbolkb) {
		this.symbolkb = symbolkb;
	}

	public String getSymbolshiftkb() {
		return symbolshiftkb;
	}

	public void setSymbolshiftkb(String symbolshiftkb) {
		this.symbolshiftkb = symbolshiftkb;
	}

	public String getDefaultkb() {
		return defaultkb;
	}

	public void setDefaultkb(String defaultkb) {
		this.defaultkb = defaultkb;
	}

	public String getDefaultshiftkb() {
		return defaultshiftkb;
	}

	public void setDefaultshiftkb(String defaultshiftkb) {
		this.defaultshiftkb = defaultshiftkb;
	}

	public String getExtendedkb() {
		return extendedkb;
	}

	public void setExtendedkb(String extendedkb) {
		this.extendedkb = extendedkb;
	}

	public String getExtendedshiftkb() {
		return extendedshiftkb;
	}

	public void setExtendedshiftkb(String extendedshiftkb) {
		this.extendedshiftkb = extendedshiftkb;
	}

	public boolean isDisable() {
		return disable;
	}

	public void setDisable(boolean disable) {
		this.disable = disable;
	}
}
