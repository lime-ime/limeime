package net.toload.main.hd.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import net.toload.main.hd.Lime;
import net.toload.main.hd.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 資料庫輔助類別
 */
public class DBHelper {

	Context ctx;
	File databasepath;

	private SQLiteDatabase database = null;

	DBHelper(Context context) {
		
		this.ctx = context;

		// Initial Database
		File destdir = new File(Lime.DATABASE_FOLDER + File.separator);
			 destdir.mkdirs();
			 
		File destpath = new File(Lime.DATABASE_FOLDER + File.separator + Lime.DATABASE_NAME);
		
		if (!destpath.exists()) {
			
			InputStream from = ctx.getResources().openRawResource( R.raw.lime);
			try {
				FileOutputStream to = new FileOutputStream(destpath);
				byte[] buffer = new byte[4096];
				int bytes_read;
				while ((bytes_read = from.read(buffer)) != -1) {
					to.write(buffer, 0, bytes_read);
				}
				if (from != null) {
					from.close();
				}
				if (to != null) {
					to.close();
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		databasepath = destpath;
	}
	
	public synchronized SQLiteDatabase getWritableDatabase() {
		if(database == null || !database.isOpen()){
			database = SQLiteDatabase.openDatabase(databasepath.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE
        			| SQLiteDatabase.NO_LOCALIZED_COLLATORS);
			return database;
		}
		return null;
    }
	
	public synchronized void close() {
		if(database != null && database.isOpen()){
			database.close();
		}
	}
	
	
	
}
