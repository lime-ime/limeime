package net.toload.main.hd.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.sql.SQLException;

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

	
}
