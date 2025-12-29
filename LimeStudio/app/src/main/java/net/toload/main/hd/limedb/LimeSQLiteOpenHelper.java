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

// Jeremy '12,4,7 derived from android SQLiteOpenHelper.java with stored in device or sdcard option
//and static dbconnection (all instances share the same db connection).

package net.toload.main.hd.limedb;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import net.toload.main.hd.global.LIME;
import net.toload.main.hd.R;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.global.LIMEUtilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

//import android.database.DatabaseErrorHandler;

/**
 * Lightweight replacement for Android's {@code SQLiteOpenHelper} used by LIME.
 *
 * <p>This helper provides application-specific database initialization logic
 * (copying a prepackaged database from resources), version upgrade handling,
 * and a shared static {@link android.database.sqlite.SQLiteDatabase} instance
 * used across the app's database components.
 *
 * <p>Subclasses should implement {@link #onUpgrade(SQLiteDatabase, int, int)}
 * to migrate schema changes between versions.
 */
public abstract class LimeSQLiteOpenHelper {

	private final static boolean DEBUG = false;
	private final static String TAG = "LimeSQLiteOpenHelper";

    private final String mName;
   // private final CursorFactory mFactory;
    private final int mNewVersion;

    private final Context mContext;

    private static SQLiteDatabase mDatabase = null;
    private boolean mIsInitializing = false;
    //private LIMEPreferenceManager mLIMEPref;





    public LimeSQLiteOpenHelper(Context context, String name, int version) {
        
        if (version < 1) throw new IllegalArgumentException("Version must be >= 1, was " + version);

        mContext = context;
        mName = name;
        //mFactory = factory;
        mNewVersion = version;
        //mErrorHandler = errorHandler;

        //LIMEPreferenceManager mLIMEPref = new LIMEPreferenceManager(context.getApplicationContext());

    }


    public synchronized SQLiteDatabase getWritableDatabase() {

        if(DEBUG)
            Log.i(TAG,"getWritableDatabase()");
        if (mDatabase != null) {
            if (!mDatabase.isOpen()) {
                // darn! the user closed the database by calling mDatabase.close()
                mDatabase = null;
            } else if (!mDatabase.isReadOnly()) {
                return mDatabase;  // The database is already open for business
            }
        }

        // Jeremy '12,5,1. No longer support db on sdcard for modern android.
        

        // Initial Database
        // Copy DB file from Raw Dir to Database Dir
        File dbPath = mContext.getDatabasePath(mName);
        File destdir = dbPath.getParentFile();
        if (destdir != null && !destdir.exists() && !destdir.mkdirs()){
            Log.e(TAG, "Error in creating database directory");
        }

        final int minDatabaseSizeBytes = 10000; // Minimum valid database file size
        if (!dbPath.exists() || dbPath.length() < minDatabaseSizeBytes) {

            InputStream from =  mContext.getResources().openRawResource( R.raw.lime);
            

            try {
                FileOutputStream to = new FileOutputStream(dbPath);
                byte[] buffer = new byte[LIME.BUFFER_SIZE_4KB];
                int bytes_read;
                if (from != null) {
                    while ((bytes_read = from.read(buffer)) != -1) {
                        to.write(buffer, 0, bytes_read);
                    }
                }
                if (from != null) {
                    from.close();
                }
                to.close();

                // The preloaded database has new column user_score in the table related

                LIMEPreferenceManager mLIMEPref = new LIMEPreferenceManager(mContext.getApplicationContext());

                mLIMEPref.setParameter(LIME.DB_CHECK_RELATED_USERSCORE, true);

            } catch (IOException e) {
                Log.e(TAG, "Error in database operation", e);
            }
        }
        //File dbPath = mContext.getDatabasePath(mName);

        if(LIMEUtilities.isFileExist(dbPath.getAbsolutePath())==null) return null; //database file is not exist. return null Jeremy '12,5,1

        if (mIsInitializing) {
            throw new IllegalStateException("getWritableDatabase called recursively");
        }

        // If we have a read-only database open, someone could be using it
        // (though they shouldn't), which would cause a lock to be held on
        // the file, and our attempts to open the database read-write would
        // fail waiting for the file lock.  To prevent that, we acquire the
        // lock on the read-only database, which shuts out other users.

        boolean success = false;
        SQLiteDatabase db;
        //if (mDatabase != null) mDatabase. .lock();
        try {

        	db = SQLiteDatabase.openDatabase(dbPath.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE
        			| SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        }catch(Exception e){

            File ch = new File(dbPath.getAbsolutePath());
            Log.i("LIME", ch.getAbsolutePath());
            Log.i("LIME", ch.length()+"");

            Log.e(TAG, "Error in database operation", e);
        	return null; //return null if db opened failed.
        }
        try {
        	 mIsInitializing = true;
            int version = db.getVersion();
            if(DEBUG)
            	Log.i(TAG,"getWritableDatabase(), db version= "+ version +"; newversion = " + mNewVersion + "WAL mode=" + db.isWriteAheadLoggingEnabled());
            if (version != mNewVersion) {
                db.beginTransaction();
                try {
                    onUpgrade(db, version, mNewVersion);
                    db.setVersion(mNewVersion);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            onOpen();
            success = true;
            return db;
        } finally {
            mIsInitializing = false;
            if (success) {
                if(DEBUG) Log.i(TAG,"getWritableDatabse(), success in finally section");
                mDatabase = db;
                //return db;
            } else {
            	Log.i(TAG,"getWritableDatabse(), not success in finally section and db closed");

                if (db != null  && db.isOpen())
                    db.close();
            }
        }

    }


    /**
     * Close any open database object.
     */
    public synchronized void close() {
        if(DEBUG)
            Log.i(TAG,"close()");
        if (mIsInitializing) throw new IllegalStateException("Closed during initialization");

        if (mDatabase != null && mDatabase.isOpen()) {
            mDatabase.close();
            mDatabase = null;
        }
    }

    /**
     * Called when the database needs to be upgraded. The implementation
     * should use this method to drop tables, add tables, or do anything else it
     * needs to upgrade to the new schema version.
     *
     * <p>The SQLite ALTER TABLE documentation can be found
     * <a href="http://sqlite.org/lang_altertable.html">here</a>. If you add new columns
     * you can use ALTER TABLE to insert them into a live table. If you rename or remove columns
     * you can use ALTER TABLE to rename the old table, then create the new table and then
     * populate the new table with the contents of the old table.
     *
     * @param db The database.
     * @param oldVersion The old database version.
     * @param newVersion The new database version.
     */
    public abstract void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion);



    /**
     * Called when the database has been opened.  The implementation
     * should check {@link SQLiteDatabase#isReadOnly} before updating the
     * database.
     *
     */
    public void onOpen() {}


}
