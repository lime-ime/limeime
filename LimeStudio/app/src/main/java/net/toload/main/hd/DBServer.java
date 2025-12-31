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

import net.toload.main.hd.data.ImConfig;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.global.LIMEProgressListener;
import net.toload.main.hd.global.LIMEUtilities;
import net.toload.main.hd.limedb.LimeDB;
import net.toload.main.hd.ui.MainActivity;

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
	private static final String TAG = "DBServer";
	
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
    DBServer(Context context) {
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
	 * Imports a text mapping file into the database table.
	 * 
	 * <p>This method imports text mapping files (.lime, .cin, or delimited text) into
	 * the specified database table. It delegates to {@link LimeDB#importTxtTable(String, LIMEProgressListener)}
	 * to perform the actual import operation.
	 * 
	 * <p>This method:
	 * <ul>
	 *   <li>Sets the filename for the import operation</li>
	 *   <li>Delegates to LimeDB.importTxtTable() for the actual import</li>
	 *   <li>Resets the SearchServer cache after import</li>
	 * </ul>
	 * 
	 * @param filename The path to the text mapping file to import
	 * @param tablename The table name to import data into (must be valid)
	 * @param progressListener Optional progress listener for import updates (can be null)
	 * @throws RemoteException if the import operation fails
	 */
	public void importTxtTable(String filename, String tablename, LIMEProgressListener progressListener) throws RemoteException {

		File sourcefile = new File(filename);
		importTxtTable(sourcefile, tablename, progressListener);
	}

	/**
	 * Imports a text mapping file into the database table.
	 * 
	 * <p>This method imports text mapping files (.lime, .cin, or delimited text) into
	 * the specified database table. It delegates to {@link LimeDB#importTxtTable(String, LIMEProgressListener)}
	 * to perform the actual import operation.
	 * 
	 * <p>This method:
	 * <ul>
	 *   <li>Sets the filename for the import operation</li>
	 *   <li>Delegates to LimeDB.importTxtTable() for the actual import</li>
	 *   <li>Resets the SearchServer cache after import</li>
	 * </ul>
	 * 
	 * @param sourcefile The text mapping file to import
	 * @param tablename The table name to import data into (must be valid)
	 * @param progressListener Optional progress listener for import updates (can be null)
	 */
	public void importTxtTable(File sourcefile, String tablename, LIMEProgressListener progressListener) {
		if (DEBUG)
			Log.i(TAG, "importTxtTable() on " + loadingTablename);


		loadingTablename = tablename;


		datasource.setFinish(false);
		datasource.setFilename(sourcefile);

		//showNotificationMessage(ctx.getText(R.string.lime_setting_notification_loading) + "");
		datasource.importTxtTable(tablename, progressListener);
		//datasource.close();

		// Reset for SearchSrv
		//mLIMEPref.setResetCacheFlag(true);
		datasource.resetCache();
	}

	/**
	 * Exports a database table to a text mapping file with optional progress reporting.
	 * 
	 * <p>This method exports data from the specified database table to a text file.
	 * It delegates to {@link LimeDB#exportTxtTable(String, File, List, LIMEProgressListener)}
	 * to perform the actual export operation.
	 * 
	 * <p>This method:
	 * <ul>
	 *   <li>Validates the dbadapter is available</li>
	 *   <li>Delegates to LimeDB.exportTxtTable() for the actual export</li>
	 *   <li>Supports progress reporting through LIMEProgressListener</li>
	 * </ul>
	 * 
	 * @param table The table name to export (must be valid, use {@link LIME#DB_TABLE_RELATED} for related phrases)
	 * @param targetFile The target file to write to
	 * @param imConfigList List of Im objects containing IM configuration info (can be null, only used for regular tables)
	 * @param progressListener Progress listener for export updates (can be null)
	 * @return true if export successful, false otherwise
	 */
	public boolean exportTxtTable(String table, File targetFile, List<ImConfig> imConfigList, LIMEProgressListener progressListener) {
		if (datasource == null) {
			Log.e(TAG, "exportTxtTable(): datasource is null");
			return false;
		}
		return datasource.exportTxtTable(table, targetFile, imConfigList, progressListener);
	}

	/**
	 * Exports a database table to a text mapping file (backward compatibility).
	 * 
	 * <p>This is a convenience method that delegates to 
	 * {@link #exportTxtTable(String, File, List, LIMEProgressListener)} with null progress listener.
	 * 
	 * @param table The table name to export
	 * @param targetFile The target file to write to
	 * @param imConfigList List of Im objects containing IM configuration info
	 * @return true if export successful, false otherwise
	 */
	public boolean exportTxtTable(String table, File targetFile, List<ImConfig> imConfigList) {
		return exportTxtTable(table, targetFile, imConfigList, null);
	}

	/**
	 * Imports a related database file into the related table.
	 *
	 * <p>This method acts as a convenience wrapper for {@link LimeDB#importDbRelated(File)}
	 * to import related phrase data from a source database file.
	 *
	 * @param sourcedb The source database file to import
	 */
	public void importDbRelated(File sourcedb){
        datasource.importDbRelated(sourcedb);
    }

	/**
	 * Imports a compressed related database file (.limedb) into the related table.
	 * 
	 * <p>This method unzips the compressed database file and then imports it into
	 * the related table. It handles the file operations (unzip) and delegates the
	 * actual database import to {@link LimeDB#importDbRelated(File)}.
	 * 
	 * <p>This method:
	 * <ul>
	 *   <li>Unzips the compressed file to a temporary directory</li>
	 *   <li>Imports the first database file found from the zip</li>
	 *   <li>Resets the SearchServer cache after import</li>
	 * </ul>
	 * 
	 * @param compressedSourceDB The compressed database file (.limedb) to import
	 */
	public void importZippedDbRelated(File compressedSourceDB) {
		List<String> unzipFilePaths;
		try {
			File unzipTargetDir = new File(appContext.getCacheDir(), "limehd");
			unzipFilePaths = LIMEUtilities.unzip(compressedSourceDB.getAbsolutePath(), unzipTargetDir.getAbsolutePath(), true);
		} catch (Exception e) {
			Log.e(TAG, "Error unzipping compressed related database", e);
			return;
		}
		if (unzipFilePaths.size() == 1) {
			importDbRelated(new File(unzipFilePaths.get(0)));
			net.toload.main.hd.SearchServer.resetCache(true);
		} else {
			Log.e(TAG, "importZippedDbRelated(): Expected 1 file in zip, found " + unzipFilePaths.size());
		}
	}

	/**
	 * Imports a database file into the specified IM table.
	 * 
	 * <p>This method imports a database file into the specified input method table.
	 * It delegates to {@link LimeDB#importDb(File, List, boolean, boolean)} to perform
	 * the actual import operation with overwrite enabled.
	 * 
	 * <p>This method:
	 * <ul>
	 *   <li>Creates a list containing the specified IM type</li>
	 *   <li>Delegates to LimeDB.importDb() with overwriteExisting=true</li>
	 * </ul>
	 * 
	 * @param sourceDbFile The source database file to import
	 * @param tableName The IM type (table name) to import data into
	 */
	public void importDb(File sourceDbFile, String tableName) {
        List<String> tableNames = new ArrayList<>();
        tableNames.add(tableName);
        datasource.importDb(sourceDbFile, tableNames, false, true);
    }

	/**
	 * Imports a compressed database file (.limedb) into the specified IM table.
	 * 
	 * <p>This method unzips the compressed database file and then imports it into
	 * the specified input method table. It handles the file operations (unzip)
	 * and delegates the actual database import to {@link LimeDB#importDb(File, List, boolean, boolean)}.
	 * 
	 * <p>This method:
	 * <ul>
	 *   <li>Unzips the compressed file to a temporary directory</li>
	 *   <li>Imports the first database file found from the zip into the specified table</li>
	 *   <li>Resets the SearchServer cache after import</li>
	 * </ul>
	 * 
	 * @param sourceDbFile The compressed database file (.limedb) to import
	 * @param tableName The IM type (table name) to import data into
	 */
	public void importZippedDb(File sourceDbFile, String tableName) {
		List<String> unzipFilePaths;
		try {
			File unzipTargetDir = new File(appContext.getCacheDir(), "limehd");
			unzipFilePaths = LIMEUtilities.unzip(sourceDbFile.getAbsolutePath(), unzipTargetDir.getAbsolutePath(), true);
		} catch (Exception e) {
			Log.e(TAG, "Error unzipping compressed database", e);
			return;
		}
		if (unzipFilePaths.size() == 1) {
			List<String> tableNames = new ArrayList<>();
			tableNames.add(tableName);
			datasource.importDb(new File(unzipFilePaths.get(0)), tableNames, false, true);
			net.toload.main.hd.SearchServer.resetCache(true);
		} else {
			Log.e(TAG, "importZippedDb(): Expected 1 file in zip, found " + unzipFilePaths.size());
		}
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
            net.toload.main.hd.SearchServer.resetCache(true);

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





	private void closeDatabase() {
		Log.i(TAG,"closeDatabase()");
		if (datasource != null) {
			datasource.close();
		}
	}






	/*
	 * Decompress retrieved file to target folder
	 */
	public void unzip(File sourceFile, String targetFolder, String targetFile, boolean removeOriginal){
		if(DEBUG)
			Log.i(TAG, "unzip(), souce = " + sourceFile.toString() +	", target = " + targetFolder + "/" + targetFile);

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

    public void zip(File sourceFile, String targetFolder, String targetFile){
		if(DEBUG)
			Log.i(TAG, "zip(), source = " + sourceFile.toString() +
					", target = " + targetFolder + "/" + targetFile);
		try{
			final int BUFFER = LIME.BUFFER_SIZE_2KB;

			File targetFolderObj = new File(targetFolder);
			if(!targetFolderObj.exists()&&
				targetFolderObj.mkdirs()) {
				Log.w(TAG, "Failed to create target folder");
			}


		// If source doesn't exist, do not create output zip file — fail fast
		if (sourceFile == null || !sourceFile.exists() || !sourceFile.isFile()) {
			Log.e(TAG, "zip(): source file does not exist: " + sourceFile);
			return;
		}

		File OutputFile = new File(targetFolderObj.getAbsolutePath() + File.separator + targetFile);
			if(OutputFile.exists() && !OutputFile.delete()) Log.w(TAG, "Failed to delete existing output file for compression");

			byte[] data = new byte[BUFFER];

			// Use try-with-resources to ensure streams are closed even if exceptions occur
			try (FileOutputStream dest = new FileOutputStream(OutputFile);
			     ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));
			     FileInputStream fi = new FileInputStream(sourceFile);
			     BufferedInputStream origin = new BufferedInputStream(fi, BUFFER)) {
				// Use only the file name for the zip entry to avoid absolute paths
				// which are rejected by Android's SafeZipPathValidatorCallback.
				ZipEntry entry = new ZipEntry(sourceFile.getName());
				out.putNextEntry(entry);
				int count;
				while((count = origin.read(data, 0, BUFFER)) != -1) {
					out.write(data, 0, count);
				}
			}


		} catch(Exception e) {
			Log.e(TAG, "Error compressing file", e);
		}
	}


	//Jeremy '12,4,23 rewriting using alert notification builder in LIME utilities to replace the deprecated method
	private void showNotificationMessage(String message) {
		Intent i;
		i = new Intent(appContext, MainActivity.class);

		LIMEUtilities.showNotification(
				appContext, true, appContext.getText(R.string.ime_setting), message, i);

	}


	public boolean isDatabseOnHold() {
		return datasource.isDatabaseOnHold();
	}


	/**
	 * Exports an IM database to a zipped file for sharing.
	 * 
	 * <p>This method centralizes all file operations for exporting IM databases:
	 * <ul>
	 *   <li>Creates cache directory if needed</li>
	 *   <li>Deletes existing files</li>
	 *   <li>Copies blank database template from raw resources</li>
	 *   <li>Prepares backup using LimeDB</li>
	 *   <li>Zips the database file</li>
	 * </ul>
	 * 
	 * <p>This replaces the file operations previously in ShareDbRunnable.
	 * 
	 * @param tableName The IM type to export (e.g., "custom", "cj")
	 * @param targetDbFile The target file to write the zipped database to
	 * @param progressCallback Optional callback for progress updates (can be null)
	 * @return The zipped database file, or null if error
	 */
	public File exportZippedDb(String tableName, File targetDbFile, Runnable progressCallback) {
		if (tableName == null || targetDbFile == null) {
			Log.e(TAG, "exportZippedDb(): Invalid parameters");
			return null;
		}

		try {
			File cacheDir = appContext.getExternalCacheDir();
			if (cacheDir == null) {
				cacheDir = appContext.getCacheDir();
			}

			File dbFile = new File(cacheDir, tableName + LIME.DATABASE_EXT);
			if (dbFile.exists() && !dbFile.delete()) {
				Log.e(TAG, "exportZippedDb(): Error deleting existing file");
			}

			if (targetDbFile.exists() && !targetDbFile.delete()) {
				Log.e(TAG, "exportZippedDb(): Error deleting existing zip file");
			}

			if (progressCallback != null) {
				progressCallback.run();
			}

			// Copy blank database template from raw resources
			LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(net.toload.main.hd.R.raw.blank), dbFile);

			// Prepare backup using LimeDB
			List<String> tableNames = new ArrayList<>();
			tableNames.add(tableName);
			datasource.prepareBackup(dbFile, tableNames, false);

			// Zip the database file
			LIMEUtilities.zip(targetDbFile.getAbsolutePath(), dbFile.getAbsolutePath(), true);

			// Clean up unzipped file
			if (dbFile.exists() && !dbFile.delete()) {
				Log.e(TAG, "exportZippedDb(): Error deleting temp file");
			}

			return targetDbFile;
		} catch (Exception e) {
			Log.e(TAG, "exportZippedDb(): Error exporting database", e);
			return null;
		}
	}

	/**
	 * Exports the related phrase database to a zipped file for sharing.
	 * 
	 * <p>This method centralizes all file operations for exporting related phrase databases:
	 * <ul>
	 *   <li>Creates cache directory if needed</li>
	 *   <li>Deletes existing files</li>
	 *   <li>Copies blank database template from raw resources</li>
	 *   <li>Prepares backup using LimeDB</li>
	 *   <li>Zips the database file</li>
	 * </ul>
	 * 
	 * <p>This replaces the file operations previously in ShareRelatedDbRunnable.
	 * 
	 * @param targetFile The target file to write the zipped database to
	 * @param progressCallback Optional callback for progress updates (can be null)
	 * @return The zipped database file, or null if error
	 */
	public File exportZippedDbRelated(File targetFile, Runnable progressCallback) {
		if (targetFile == null) {
			Log.e(TAG, "exportZippedDbRelated(): Invalid parameters");
			return null;
		}

		try {
			File cacheDir = appContext.getExternalCacheDir();
			if (cacheDir == null) {
				cacheDir = appContext.getCacheDir();
			}

			File dbFile = new File(cacheDir, LIME.DB_TABLE_RELATED + LIME.DATABASE_EXT);
			if (dbFile.exists() && !dbFile.delete()) {
				Log.e(TAG, "exportZippedDbRelated(): Error deleting existing file");
			}

			if (targetFile.exists() && !targetFile.delete()) {
				Log.e(TAG, "exportZippedDbRelated(): Error deleting existing zip file");
			}

			if (progressCallback != null) {
				progressCallback.run();
			}

			// Copy blank database template from raw resources
			LIMEUtilities.copyRAWFile(appContext.getResources().openRawResource(net.toload.main.hd.R.raw.blankrelated), dbFile);

			// Prepare backup using LimeDB
			datasource.prepareBackup(dbFile, null, true);

			// Zip the database file
			LIMEUtilities.zip(targetFile.getAbsolutePath(), dbFile.getAbsolutePath(), true);

			// Clean up unzipped file
			if (dbFile.exists() && !dbFile.delete()) {
				Log.e(TAG, "exportZippedDbRelated(): Error deleting temp file");
			}

			return targetFile;
		} catch (Exception e) {
			Log.e(TAG, "exportZippedDbRelated(): Error exporting database", e);
			return null;
		}
	}

}

