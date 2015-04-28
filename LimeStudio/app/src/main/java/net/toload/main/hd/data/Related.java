package net.toload.main.hd.data;

import android.database.Cursor;

import net.toload.main.hd.Lime;

import java.util.ArrayList;
import java.util.List;

public class Related {

	private int id;
	private String pword;
	private String cword;
	private int score;

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

	public int getScore() {
		return score;
	}

	public void setScore(int score) {
		this.score = score;
	}


	public static Related get(Cursor cursor){
		Related record = new Related();
				record.setId(cursor.getInt(cursor.getColumnIndex(Lime.DB_RELATED_COLUMN_ID)));
				record.setPword(cursor.getString(cursor.getColumnIndex(Lime.DB_RELATED_COLUMN_PWORD)));
				record.setCword(cursor.getString(cursor.getColumnIndex(Lime.DB_RELATED_COLUMN_CWORD)));
				record.setScore(cursor.getInt(cursor.getColumnIndex(Lime.DB_RELATED_COLUMN_SCORE)));
		return record;
	}

	public static List<Related> getList(Cursor cursor){
		List<Related> list = new ArrayList<Related>();
		cursor.moveToFirst();
		while(!cursor.isAfterLast()){
			list.add(get(cursor));
			cursor.moveToNext();
		}
		cursor.close();
		return list;
	}

	public static String getInsertQuery(Related record){
		StringBuffer sb = new StringBuffer();
		sb.append("INSERT INTO " + Lime.DB_RELATED + "(");
		sb.append(Lime.DB_RELATED_COLUMN_PWORD +", ");
		sb.append(Lime.DB_RELATED_COLUMN_CWORD +", ");
		sb.append(Lime.DB_RELATED_COLUMN_SCORE +") VALUES(");
		sb.append("\""+record.getPword()+"\",");
		sb.append("\""+record.getCword()+"\",");
		sb.append("\""+record.getScore()+"\"");
		sb.append(")");
		return sb.toString();
	}

}
