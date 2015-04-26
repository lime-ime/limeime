package net.toload.main.hd.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import net.toload.main.hd.Lime;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 資料庫的各項查詢操作
 */
public class DataSource {

	private SQLiteDatabase database;
	private DBHelper dbhelper;
	
	private Context ctx;
	
	public DataSource(Context ctx){
		this.ctx = ctx;
	}
	
	public void beginTransaction(){
		if(database != null && database.isOpen()){
			database.beginTransaction();
		}
	}
	
	public void endTransaction(){
		if(database != null && database.isOpen()){
			database.setTransactionSuccessful();
			database.endTransaction();
		}
	}
	
	public void open() throws SQLException{
		if(dbhelper == null && ctx != null){
			dbhelper = new DBHelper(ctx);
		}
		database = dbhelper.getWritableDatabase();
	}
	
	public void close() {
		if(dbhelper != null && database != null){
			dbhelper.close();
		}
	}

    /**
     * 取得表格內的所有記錄
     * @param table
     * @return
     */
	public Cursor list(String table){
		Cursor cursor = null;
		if(database != null && database.isOpen()){
			cursor = database.query(table, null, null, null, null, null, null);
		}
		return cursor;
	}

    /**
     * 依 SQL 指令進行資料新增
     * @param insertsql
     */
	public void insert(String insertsql){
		if(database != null && database.isOpen() && 
				insertsql != null && insertsql.toLowerCase().trim().startsWith("insert")){	
			database.execSQL(insertsql);
		}
	}

    /**
     * 移除 SQL 指令的操作
     * @param removesql
     */
	public void remove(String removesql){
		if(database != null && database.isOpen()){
			if(removesql.toLowerCase().startsWith("delete")){
				database.execSQL(removesql);
			}
		}
	}

	public List<Im> getIm(String code, String type){
		List<Im> result = new ArrayList<Im>();
		if(database != null && database.isOpen()){
			Cursor cursor = null;
			String query = null;
			if(code != null && code.length() > 1) {
				query = Lime.DB_IM_COLUMN_CODE + "='"+code+"'";
			}
			if(type != null && type.length() > 1){
				if(query != null){
					query += " AND ";
				}else{
					query = "";
				}

				query += " " + Lime.DB_IM_COLUMN_TITLE + "='"+type+"'";;
			}

			cursor = database.query(Lime.DB_IM,
					null, query,
					null, null, null, Lime.DB_IM_COLUMN_DESC + " ASC");
			cursor.moveToFirst();
			while(!cursor.isAfterLast()){
				Im r = Im.get(cursor);
				result.add(r);
				cursor.moveToNext();
			}
			cursor.close();
		}
		return result;
	}

	public List<Word> loadWord(String code, String query, boolean searchroot) {
		List<Word> result = new ArrayList<Word>();
		if(database != null && database.isOpen()){
			Cursor cursor = null;
			if(query != null && query.length() >= 1){
				if(searchroot){
					query = Lime.DB_COLUMN_CODE + " LIKE '"+query+"%' AND ifnull("+Lime.DB_COLUMN_WORD+", '') <> ''";
				}else{
					query = Lime.DB_COLUMN_WORD + " LIKE '%"+query+"%' AND ifnull("+Lime.DB_COLUMN_WORD+", '') <> ''";
				}
			}else{
				query = "ifnull("+Lime.DB_COLUMN_WORD+", '') <> ''";
			}

			String order = "";

			if(searchroot){
				order = Lime.DB_COLUMN_CODE + " ASC";
			}else{
				order = Lime.DB_COLUMN_WORD + " ASC";
			}

			cursor = database.query(code,
					null, query,
					null, null, null, order);

			cursor.moveToFirst();
			while(!cursor.isAfterLast()){
				Word r = Word.get(cursor);
				result.add(r);
				cursor.moveToNext();
			}
			cursor.close();
		}
		return result;
	}

}
