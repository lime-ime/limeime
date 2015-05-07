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

	public void add(String addsql) {
		if(database != null && database.isOpen()){
			if(addsql.toLowerCase().startsWith("insert")){
				database.execSQL(addsql);
			}
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


	public void update(String updatesql) {
		if(database != null && database.isOpen()){
			if(updatesql.toLowerCase().startsWith("update")){
				database.execSQL(updatesql);
			}
		}
	}


	public List<Keyboard> getKeyboard(){
		List<Keyboard> result = new ArrayList<Keyboard>();
		if(database != null && database.isOpen()){
			Cursor cursor = database.query(Lime.DB_KEYBOARD,null, null,
												null, null, null, Lime.DB_KEYBOARD_COLUMN_NAME + " ASC");
			cursor.moveToFirst();
			while(!cursor.isAfterLast()){
				Keyboard r = Keyboard.get(cursor);
				result.add(r);
				cursor.moveToNext();
			}
			cursor.close();
		}
		return result;
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

	public List<Word> loadWord(String code, String query, boolean searchroot, int maximum, int offset) {
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

			if(maximum > 0){
				order += " LIMIT " + maximum + " OFFSET " + offset;
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

	public Word getWord(String code, long id) {
		Word w = null;
		if(database != null && database.isOpen()){
			Cursor cursor = null;

			String query = Lime.DB_COLUMN_ID + " = '"+id+"' ";

			cursor = database.query(code,
					null, query,
					null, null, null, null);

			cursor.moveToFirst();
			w = Word.get(cursor);
			cursor.close();
		}
		return w;
	}

	public void setImKeyboard(String code, Keyboard keyboard) {
		if(database != null && database.isOpen()){

			String removesql = "DELETE FROM " + Lime.DB_IM + " WHERE " + Lime.DB_IM_COLUMN_CODE + " = '" + code +"'";
			        removesql += " AND " + Lime.DB_IM_COLUMN_TITLE + " = '"+Lime.IM_TYPE_KEYBOARD+"'";
			database.execSQL(removesql);

			Im im = new Im();
			   im.setCode(code);
			   im.setKeyboard(keyboard.getCode());
			   im.setTitle(Lime.IM_TYPE_KEYBOARD);
			   im.setDesc(keyboard.getDesc());

			String addsql = Im.getInsertQuery(im);
			database.execSQL(addsql);
		}
	}

	public List<Related> loadRelated(String pword, int maximum, int offset) {

		List<Related> result = new ArrayList<Related>();

		if(database != null && database.isOpen()){
			Cursor cursor = null;

			String query = "";
			if(pword != null && !pword.isEmpty()){
				query = Lime.DB_RELATED_COLUMN_PWORD + " = '"+pword +
						"' AND ";
			}

			query += "ifnull("+Lime.DB_RELATED_COLUMN_CWORD +", '') <> ''";

			String order = Lime.DB_RELATED_COLUMN_PWORD+" asc," + Lime.DB_RELATED_COLUMN_SCORE + " desc";

			if(maximum > 0){
				order += " LIMIT " + maximum + " OFFSET " + offset;
			}

			cursor = database.query(Lime.DB_RELATED,
					null, query,
					null, null, null, order);

			cursor.moveToFirst();
			while(!cursor.isAfterLast()){
				Related r = Related.get(cursor);
				result.add(r);
				cursor.moveToNext();
			}
			cursor.close();
		}
		return result;
	}

	public Related getRelated(long id) {
		Related w = null;
		if(database != null && database.isOpen()){
			Cursor cursor = null;

			String query = Lime.DB_RELATED_COLUMN_ID + " = '"+id+"' ";

			cursor = database.query(Lime.DB_RELATED,
					null, query,
					null, null, null, null);

			cursor.moveToFirst();
			w = Related.get(cursor);
			cursor.close();
		}
		return w;
	}

	public int count(String table) {

		int total = 0;

		if(database != null && database.isOpen()){
			Cursor cursor = null;
			String query = "SELECT COUNT(*) as count FROM " + table;
			cursor = database.rawQuery(query, null);
			cursor.moveToFirst();
			total = cursor.getInt(cursor.getColumnIndex(Lime.DB_TOTAL_COUNT));
			cursor.close();
		}

		return total;

	}

	public int getWordSize(String table, String curquery, boolean searchroot) {

		int total = 0;

		if(database != null && database.isOpen()){

			Cursor cursor = null;

			String query = "SELECT COUNT(*) as count FROM " + table + " WHERE ";

			if(curquery != null && curquery.length() >= 1){
				if(searchroot){
					query += Lime.DB_COLUMN_CODE + " LIKE '"+curquery+"%' AND ifnull("+Lime.DB_COLUMN_WORD+", '') <> ''";
				}else{
					query += Lime.DB_COLUMN_WORD + " LIKE '%"+curquery+"%' AND ifnull("+Lime.DB_COLUMN_WORD+", '') <> ''";
				}
			}else{
				query += " ifnull("+Lime.DB_COLUMN_WORD+", '') <> ''";
			}

			cursor = database.rawQuery(query, null);

			cursor.moveToFirst();
			total = cursor.getInt(cursor.getColumnIndex(Lime.DB_TOTAL_COUNT));
			cursor.close();
		}

		return total;

	}


	public int getRelatedSize(String pword) {

		int total = 0;

		if(database != null && database.isOpen()){
			Cursor cursor = null;

			String query = "SELECT COUNT(*) as count FROM " + Lime.DB_RELATED + " WHERE ";

			if(pword != null && !pword.isEmpty()){
				query += Lime.DB_RELATED_COLUMN_PWORD + " = '"+pword +
						"' AND ";
			}

			query += "ifnull("+Lime.DB_RELATED_COLUMN_CWORD +", '') <> ''";

			cursor = database.rawQuery(query, null);

			cursor.moveToFirst();
			total = cursor.getInt(cursor.getColumnIndex(Lime.DB_TOTAL_COUNT));
			cursor.close();
		}
		return total;
	}
}
