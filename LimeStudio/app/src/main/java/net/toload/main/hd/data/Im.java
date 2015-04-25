package net.toload.main.hd.data;

import android.database.Cursor;

import net.toload.main.hd.Lime;

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


	public static Im get(Cursor cursor){
		Im record = new Im();
			record.setId(cursor.getInt(cursor.getColumnIndex(Lime.DB_IM_COLUMN_ID)));
			record.setCode(cursor.getString(cursor.getColumnIndex(Lime.DB_IM_COLUMN_CODE)));
			record.setTitle(cursor.getString(cursor.getColumnIndex(Lime.DB_IM_COLUMN_TITLE)));
			record.setDesc(cursor.getString(cursor.getColumnIndex(Lime.DB_IM_COLUMN_DESC)));
			record.setKeyboard(cursor.getString(cursor.getColumnIndex(Lime.DB_IM_COLUMN_KEYBOARD)));
			record.setDisable(Boolean.getBoolean(cursor.getString(cursor.getColumnIndex(Lime.DB_IM_COLUMN_DISABLE))));
			record.setSelkey(cursor.getString(cursor.getColumnIndex(Lime.DB_IM_COLUMN_SELKEY)));
			record.setEndkey(cursor.getString(cursor.getColumnIndex(Lime.DB_IM_COLUMN_ENDKEY)));
			record.setSpacestyle(cursor.getString(cursor.getColumnIndex(Lime.DB_IM_COLUMN_SPACESTYLE)));
		return record;
	}


	public static List<Im> getList(Cursor cursor){
		List<Im> list = new ArrayList<Im>();
		cursor.moveToFirst();
		while(!cursor.isAfterLast()){
			list.add(get(cursor));
			cursor.moveToNext();
		}
		cursor.close();
		return list;
	}
		
	public static String getInsertQuery(Im record){
		StringBuffer sb = new StringBuffer();
					 sb.append("INSERT INTO " + Lime.DB_IM + "(");
					 sb.append(Lime.DB_IM_COLUMN_ID +", ");
					 sb.append(Lime.DB_IM_COLUMN_CODE +", ");
					 sb.append(Lime.DB_IM_COLUMN_TITLE +", ");
					 sb.append(Lime.DB_IM_COLUMN_DESC +", ");
					 sb.append(Lime.DB_IM_COLUMN_KEYBOARD +", ");
					 sb.append(Lime.DB_IM_COLUMN_DISABLE +", ");
					 sb.append(Lime.DB_IM_COLUMN_SELKEY +", ");
					 sb.append(Lime.DB_IM_COLUMN_ENDKEY +", ");
					 sb.append(Lime.DB_IM_COLUMN_SPACESTYLE +") VALUES(");
					 sb.append("\""+record.getId()+"\",");
					 sb.append("\""+record.getCode()+"\",");
					 sb.append("\""+record.getTitle()+"\",");
					 sb.append("\""+record.getDesc()+"\",");
					 sb.append("\""+record.getKeyboard()+"\",");
					 sb.append("\""+record.isDisable()+"\",");
					 sb.append("\""+record.getSelkey()+"\",");
					 sb.append("\""+record.getEndkey()+"\",");
					 sb.append("\""+record.getSpacestyle()+"\"");;
					 sb.append(")");
		return sb.toString();
	}

}
