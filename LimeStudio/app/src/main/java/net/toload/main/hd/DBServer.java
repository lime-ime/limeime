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

package net.toload.main.hd;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import androidx.core.content.ContextCompat;

import net.toload.main.hd.data.KeyboardObj;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.global.LIMEProgressListener;
import net.toload.main.hd.global.LIMEUtilities;
import net.toload.main.hd.limedb.LimeDB;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

//Jeremy '12,5,1 renamed from DBServer and change from service to ordinary class.
// Singleton pattern: Ensures only one instance exists and safely manages Application Context
public class  DBServer {
	private static final boolean DEBUG = false;
	private static final String TAG = "LIME.DBServer";
	
	// Singleton instance
	@SuppressLint("StaticFieldLeak")
    private static volatile DBServer instance = null;
	
	// Application Context - safe to store as static since ApplicationContext lives for app lifetime
	private final Context appContext;
	
	// Static context accessor for backward compatibility with static methods
	// Safe to store ApplicationContext in static field - it lives for app lifetime
	protected Context ctx = null;
	
	protected LimeDB datasource = null;  // LIMEDB for shared LIMEDB between DBServer instances
	protected LIMEPreferenceManager mLIMEPref;
	private String loadingTablename = "";  //Jeremy '12,6,2 change all variable to static for all instances to share these variables

	/**
	 * Private constructor to prevent direct instantiation.
	 * Use getInstance() instead.
	 * 
	 * @param context Context (will be converted to ApplicationContext)
	 */
	private DBServer(Context context) {
		// Always use ApplicationContext to prevent memory leaks
		// ApplicationContext lives for the app lifetime, matching singleton lifetime
		this.appContext = context.getApplicationContext();
		
		if (ctx == null) {
			ctx = this.appContext;
			mLIMEPref = new LIMEPreferenceManager(this.appContext);
			if (datasource == null) {
				datasource = new LimeDB(this.appContext);
			}
		}
	}

	/**
	 * Get the singleton instance of DBServer.
	 * Thread-safe implementation using double-checked locking.
	 * 
	 * @param context Context (will be converted to ApplicationContext on first call)
	 * @return The singleton DBServer instance
	 */
	public static DBServer getInstance(Context context) {
		if (instance == null) {
			synchronized (DBServer.class) {
				if (instance == null) {
					instance = new DBServer(context);
				}
			}
		}
		return instance;
	}

	/**
	 * Get the singleton instance if already initialized.
	 * Use this when you're certain the instance has been created.
	 * 
	 * @return The singleton DBServer instance, or null if not initialized
	 */
	public static DBServer getInstance() {
		return instance;
	}

	/**
	 * Get the Application Context used by this singleton.
	 * 
	 * @return Application Context
	 */
	public Context getContext() {
		return appContext;
	}

	public void loadMapping(String filename, String tablename, LIMEProgressListener progressListener) throws RemoteException {

		File sourcefile = new File(filename);
		loadMapping(sourcefile, tablename, progressListener);
	}

	public void loadMapping(File sourcefile, String tablename, LIMEProgressListener progressListener) {
		if (DEBUG)
			Log.i(TAG, "loadMapping() on " + loadingTablename);


		loadingTablename = tablename;


		datasource.setFinish(false);
		datasource.setFilename(sourcefile);

		//showNotificationMessage(ctx.getText(R.string.lime_setting_notification_loading) + "");
		datasource.loadFileV2(tablename, progressListener);
		//datasource.close();

		// Reset for SearchSrv
		//mLIMEPref.setResetCacheFlag(true);
		resetCache();
	}

	public void resetMapping(final String tablename) throws RemoteException {

		if (DEBUG)
			Log.i(TAG, "resetMapping() on " + loadingTablename);

		datasource.deleteAll(tablename);

		// Reset cache in SearchSrv
		//mLIMEPref.setResetCacheFlag(true);
		resetCache();
	}
	public void resetCache(){
		SearchServer.resetCache(true);
	}

	public void importBackupRelatedDb(File sourcedb){
        datasource.importBackupRelatedDb(sourcedb);
    }

	public void importBackupDb(File sourcedb, String imtype) {
        datasource.importBackupDb(sourcedb, imtype);
    }

	public void importMapping(File compressedSourceDB, String imtype) {


		//String sourcedbfile = LIME.LIME_SDCARD_FOLDER + imtype;
		List<String> unzipFilePaths = new ArrayList<>();
		try {
			File unzipTargetDir = new File(appContext.getCacheDir(), "limehd");
			unzipFilePaths = LIMEUtilities.unzip(compressedSourceDB.getAbsolutePath(), unzipTargetDir.getAbsolutePath(),true);
		} catch (Exception e) {
			Log.e(TAG, "Error unzipping compressed database", e);
		}
		if(unzipFilePaths.size() == 1){
            datasource.importDb(unzipFilePaths.get(0), imtype);
			resetCache();
        }
	}


	public int getLoadingMappingCount() {
		return datasource.getCount();
	}

	/**
	 * Get the data directory path, compatible with all API levels.
	 * Uses ContextCompat.getDataDir() which handles API level differences automatically.
	 */
	private String getDataDirPath() {
		File dataDir = ContextCompat.getDataDir(appContext);
		return dataDir != null ? dataDir.getAbsolutePath() : appContext.getFilesDir().getParent();
	}





    public void backupDatabase(Uri uri) throws RemoteException {
        if (DEBUG)
            Log.i(TAG, "backupDatabase()");

        String dataDir = getDataDirPath();
        //backup shared preferences

        File fileSharedPrefsBackup = new File(dataDir, LIME.SHARED_PREFS_BACKUP_NAME);
        if(fileSharedPrefsBackup.exists() && !fileSharedPrefsBackup.delete()) Log.w(TAG, "Failed to delete existing shared preferences backup file");
        backupDefaultSharedPreference(fileSharedPrefsBackup);

        // create backup file list.
        String limeDBPath = ctx.getDatabasePath(LIME.DATABASE_NAME).getAbsolutePath();
        String limeDBJournalPath = ctx.getDatabasePath(LIME.DATABASE_JOURNAL).getAbsolutePath();
        if (limeDBPath.startsWith(dataDir)) {
            limeDBPath = limeDBPath.substring(dataDir.length());
        }
        if (limeDBJournalPath.startsWith(dataDir)) {
            limeDBJournalPath = limeDBJournalPath.substring(dataDir.length());
        }
        List<String> backupFileList = new ArrayList<>();
        //backupFileList.add(LIME.DATABASE_RELATIVE_FOLDER + File.separator + LIME.DATABASE_NAME);
        //backupFileList.add(LIME.DATABASE_RELATIVE_FOLDER + File.separator + LIME.DATABASE_JOURNAL);
        backupFileList.add(limeDBPath);
        backupFileList.add(limeDBJournalPath);
        backupFileList.add(LIME.SHARED_PREFS_BACKUP_NAME);

        // hold database connection and close database.
        datasource.holdDBConnection(); //Jeremy '15,5,23
        closeDatabase();

        //ready to zip backup file list
        File tempZip = new File(appContext.getCacheDir(), LIME.DATABASE_BACKUP_NAME);
        if(tempZip.exists() && !tempZip.delete()) Log.w(TAG, "Failed to delete existing temp zip file");
        OutputStream outputStream = null;
        FileInputStream inputStream = null;

        try {
            LIMEUtilities.zip(tempZip.getAbsolutePath(), backupFileList, dataDir , true);
            //saveFileToDownloads(tempZip);
            // Copy temp zip to the User selected URI
            inputStream = new FileInputStream(tempZip);
            outputStream = appContext.getContentResolver().openOutputStream(uri);

            byte[] buffer = new byte[LIME.BUFFER_SIZE_4KB];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error backing up database", e);
            showNotificationMessage(appContext.getText(R.string.l3_initial_backup_error) + "");
        } finally {
            try {
                if (outputStream != null) outputStream.close();
                if (inputStream != null) inputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams during backup", e);
            }
            // Reopen DB
            if (datasource != null) {
                datasource.openDBConnection(true);
            }
            if (fileSharedPrefsBackup.exists() && !fileSharedPrefsBackup.delete()) Log.w(TAG, "Failed to delete shared preferences backup file in finally");
            if (tempZip.exists() && !tempZip.delete()) Log.w(TAG, "Failed to delete temp zip file in finally");

            showNotificationMessage(appContext.getText(R.string.l3_initial_backup_end) + "");
        }



        // backup finished.  unhold the database connection and false reopen the database.
        datasource.unHoldDBConnection(); //Jeremy '15,5,23
        //mLIMEPref.holdDatabaseConnection(false);
        datasource.openDBConnection(true);

        //cleanup the shared preference backup file.
        if(fileSharedPrefsBackup.exists() && !fileSharedPrefsBackup.delete()) Log.w(TAG, "Failed to delete shared preferences backup file at end");


    }

    public void restoreDatabase(Uri uri) {
        if (DEBUG)
            Log.i(TAG, "restoreDatabase(Uri) Starting....");

        File tempZip = new File(appContext.getCacheDir(), LIME.DATABASE_BACKUP_NAME);
        if(tempZip.exists() && tempZip.delete()) Log.w(TAG, "Failed to delete shared preferences backup file after restore");

        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            inputStream = appContext.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                throw new FileNotFoundException("Could not open input stream for URI: " + uri);
            }
            outputStream = new FileOutputStream(tempZip);

            byte[] buffer = new byte[LIME.BUFFER_SIZE_4KB];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            Log.i(TAG, "restoreDatabase(Uri) temp file created: " + tempZip.getAbsolutePath());
            restoreDatabase(tempZip.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "Error restoring database", e);
            showNotificationMessage(appContext.getText(R.string.l3_initial_restore_error) + "");
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams during restore", e);
            }
            if (tempZip.exists() && tempZip.delete())Log.w(TAG, "Failed to delete shared preferences backup file after restore");
        }
    }

	public void restoreDatabase(String srcFilePath) {
		File check = new File(srcFilePath);
        String dataDir = getDataDirPath();

		if(check.exists()){

			datasource.holdDBConnection(); //Jeremy '15,5,23
			closeDatabase();
            //restore shared preference
            File sharedPref = new File(dataDir, LIME.SHARED_PREFS_BACKUP_NAME);

            try {
				LIMEUtilities.unzip(srcFilePath, dataDir, true);
			} catch (Exception e) {
				Log.e(TAG, "Error unzipping restore file", e);
				showNotificationMessage(appContext.getText(R.string.l3_initial_restore_error) + "");
			}
			finally {
				showNotificationMessage(appContext.getText(R.string.l3_initial_restore_end) + "");
			}

			datasource.unHoldDBConnection(); //Jeremy '15,5,23
			datasource.openDBConnection(true);


			restoreDefaultSharedPreference(sharedPref);
            //Delete the shared preference backup file after restored.
            if(sharedPref.exists() && !sharedPref.delete()) Log.w(TAG, "Failed to delete shared preferences backup file after restore");
			//mLIMEPref.setResetCacheFlag(true);
			resetCache();

			// Check and upgrade the database table
			datasource.checkAndUpdateRelatedTable();

		}else{
			showNotificationMessage(appContext.getText(R.string.error_restore_not_found) + "");

		}

	}

	public void backupDefaultSharedPreference(File sharePrefs) {
		if(sharePrefs.exists() && !sharePrefs.delete()) Log.w(TAG, "Failed to delete existing shared preferences backup file");

		ObjectOutputStream output = null;
		try {
			output = new ObjectOutputStream(new FileOutputStream(sharePrefs));
			SharedPreferences pref = appContext.getSharedPreferences(appContext.getPackageName() + "_preferences", Context.MODE_PRIVATE);
			output.writeObject(pref.getAll());

		} catch (IOException e) {
			Log.e(TAG, "Error backing up shared preferences", e);
		} finally {
			try {
				if (output != null) {
					output.flush();
					output.close();
				}
			} catch (IOException ex) {
				Log.e(TAG, "Error closing shared preferences backup stream", ex);
			}
		}

	}

    @SuppressWarnings("unchecked")
	public void restoreDefaultSharedPreference(File sharePrefs )
	{
        try (ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(sharePrefs))) {
            try {
                SharedPreferences.Editor prefEdit = appContext.getSharedPreferences(appContext.getPackageName() + "_preferences", Context.MODE_PRIVATE).edit();
                prefEdit.clear();
                Map<String, ?> entries = (Map<String, ?>) inputStream.readObject();
                for (Map.Entry<String, ?> entry : entries.entrySet()) {
                    Object v = entry.getValue();
                    String key = entry.getKey();

                    if (v instanceof Boolean)
                        prefEdit.putBoolean(key, (Boolean) v);
                    else if (v instanceof Float)
                        prefEdit.putFloat(key, (Float) v);
                    else if (v instanceof Integer)
                        prefEdit.putInt(key, (Integer) v);
                    else if (v instanceof Long)
                        prefEdit.putLong(key, (Long) v);
                    else if (v instanceof String) {
                        if (!v.equals("PAYMENT_FLAG"))
                            prefEdit.putString(key, ((String) v));
                    }
                }
                prefEdit.apply();

            } catch (IOException | ClassNotFoundException e) {
                Log.e(TAG, "Error restoring shared preferences", e);
            }
        } catch (IOException ex) {
            Log.e(TAG, "Error reading shared preferences backup file", ex);
        }
	}

	public String getImInfo(String im, String field) throws RemoteException {
		//if (datasource == null) {loadLimeDB();}
		return datasource.getImInfo(im, field);
	}


	public String getKeyboardInfo(String keyboardCode, String field) {
		//if (datasource == null) {loadLimeDB();}
		return datasource.getKeyboardInfo(keyboardCode, field);
	}


	public void removeImInfo(String im, String field) {
		//if (datasource == null) {loadLimeDB();}
		datasource.removeImInfo(im, field);

	}


	public void resetImInfo(String im) {
		//if (datasource == null) {loadLimeDB();}
		datasource.resetImInfo(im);

	}


	public void setImInfo(String im, String field, String value) {
		//if (datasource == null) {loadLimeDB();}
		datasource.setImInfo(im, field, value);

	}




	public void closeDatabase() {
		Log.i(TAG,"closeDatabase()");
		if (datasource != null) {
			datasource.close();
		}
	}


	public void setIMKeyboard(String im, String value,
							  String keyboard) throws RemoteException {

		datasource.setIMKeyboard(im, value, keyboard);
	}


	public String getKeyboardCode(String im) {
		//if (datasource == null) {loadLimeDB();}
		return datasource.getKeyboardCode(im);
	}




	/*
	 * Decompress retrieved file to target folder
	 */
	public void decompressFile(File sourceFile, String targetFolder, String targetFile, boolean removeOriginal){
		if(DEBUG)
			Log.i(TAG, "decompressFile(), souce = " + sourceFile.toString() +	", target = " + targetFolder + "/" + targetFile);

		try {

			File targetFolderObj = new File(targetFolder);
			if(!targetFolderObj.exists() &&
				!targetFolderObj.mkdirs()) {
				Log.w(TAG, "Failed to create target folder");
			}

			// Use try-with-resources to ensure streams are closed even if exceptions occur
			try (FileInputStream fis = new FileInputStream(sourceFile)) {
				ZipInputStream zis = getZipInputStream(targetFile, fis, targetFolderObj);
				zis.close();
			}

			if(removeOriginal && !sourceFile.delete()) Log.w(TAG, "Failed to delete original source file");
        } catch (Exception e) {
			showNotificationMessage(appContext.getText(R.string.l3_initial_download_failed)+ "");
			Log.e(TAG, "Error compressing file", e);
		}
    }

    private ZipInputStream getZipInputStream(String targetFile, FileInputStream fis, File targetFolderObj) throws IOException {
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis));
        //ZipEntry entry;
        while (( zis.getNextEntry()) != null) {

            int size;
            byte[] buffer = new byte[LIME.BUFFER_SIZE_2KB];

            File OutputFile = new File(targetFolderObj.getAbsolutePath() + File.separator + targetFile);
            if(OutputFile.exists() && !OutputFile.delete()) Log.w(TAG, "Failed to delete existing output file");

            // Use try-with-resources to ensure streams are closed even if exceptions occur
            try (FileOutputStream fos = new FileOutputStream(OutputFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos, buffer.length)) {
                while ((size = zis.read(buffer, 0, buffer.length)) != -1) {
                    bos.write(buffer, 0, size);
                }
                bos.flush();
            }

            //Log.i("ART","uncompress Output File:"+OutputFile.getAbsolutePath() + " / " + OutputFile.length());

        }
        return zis;
    }

    public void compressFile(File sourceFile, String targetFolder, String targetFile){
		if(DEBUG)
			Log.i(TAG, "compressFile(), srouce = " + sourceFile.toString() +
					", target = " + targetFolder + "/" + targetFile);
		try{
			final int BUFFER = LIME.BUFFER_SIZE_2KB;

			File targetFolderObj = new File(targetFolder);
			if(!targetFolderObj.exists()&&
				targetFolderObj.mkdirs()) {
				Log.w(TAG, "Failed to create target folder");
			}


			File OutputFile = new File(targetFolderObj.getAbsolutePath() + File.separator + targetFile);
			if(OutputFile.exists() && !OutputFile.delete()) Log.w(TAG, "Failed to delete existing output file for compression");

			byte[] data = new byte[BUFFER];

			// Use try-with-resources to ensure streams are closed even if exceptions occur
			try (FileOutputStream dest = new FileOutputStream(OutputFile);
			     ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));
			     FileInputStream fi = new FileInputStream(sourceFile);
			     BufferedInputStream origin = new BufferedInputStream(fi, BUFFER)) {
				ZipEntry entry = new ZipEntry(sourceFile.getAbsolutePath());
				out.putNextEntry(entry);
				int count;
				while((count = origin.read(data, 0, BUFFER)) != -1) {
					out.write(data, 0, count);
				}
			}

			//Log.i("ART","compress Output File:"+OutputFile.getAbsolutePath() + " / " + OutputFile.length());

		} catch(Exception e) {
			Log.e(TAG, "Error compressing file", e);
		}
	}
	/**
	 * Check the consistency of phonetic keyboard setting in preference and db.
	 * Jeremy '12,6,8
	 *
	 */
	public void checkPhoneticKeyboardSetting(){
		datasource.checkPhoneticKeyboardSetting();
	}


	//Jeremy '12,4,23 rewriting using alert notification builder in LIME utilities to replace the deprecated method
	public void showNotificationMessage(String message) {
		Intent i;
		i = new Intent(appContext, MainActivity.class);

		LIMEUtilities.showNotification(
				appContext, true, appContext.getText(R.string.ime_setting), message, i);

	}

	/**
	 * Jeremy '12,7,6 get keyboard object from table name
	 * @param table table
	 * @return KeyboardObj
	 */
	public KeyboardObj getKeyboardObj(String table){
		KeyboardObj kobj = null;
		if(datasource != null)
			kobj = datasource.getKeyboardObj(table);
		return kobj;
	}

	public boolean isDatabseOnHold() {
		return datasource.isDatabaseOnHold();
	}


}

