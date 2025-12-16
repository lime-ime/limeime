/*
 *
 *  *
 *  **    Copyright 2015, The LimeIME Open Source Project
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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

//Jeremy '12,5,1 renamed from DBServer and change from service to ordinary class.
public class  DBServer {
	private static final boolean DEBUG = false;
	private static final String TAG = "LIME.DBServer";
	//private NotificationManager notificationMgr;

	protected static LimeDB datasource = null;  //static LIMEDB for shared LIMEDB between DBServer instances
	protected static LIMEPreferenceManager mLIMEPref;

    static {
        mLIMEPref = null;
    }

    private static boolean remoteFileDownloading = false;

	private static String loadingTablename = "";  //Jeremy '12,6,2 change all variable to static for all instances to share these variables
	private static boolean abortDownload = false;


	protected static Context ctx = null;


	public DBServer(Context context) {
		DBServer.ctx = context;
		mLIMEPref = new LIMEPreferenceManager(ctx);
		//loadLimeDB();
		if (datasource == null)
			datasource = new LimeDB(ctx);
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
	public static void resetCache(){
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
			File unzipTargetDir = new File(ctx.getCacheDir(), "limehd");
			unzipFilePaths = LIMEUtilities.unzip(compressedSourceDB.getAbsolutePath(), unzipTargetDir.getAbsolutePath(),true);
		} catch (Exception e) {
			e.printStackTrace();
		}
		if(unzipFilePaths.size() == 1){
        //}
		//else {
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
	private static String getDataDirPath(Context context) {
		File dataDir = ContextCompat.getDataDir(context);
		return dataDir != null ? dataDir.getAbsolutePath() : context.getFilesDir().getParent();
	}





    public static void backupDatabase(Uri uri) throws RemoteException {
        if (DEBUG)
            Log.i(TAG, "backupDatabase()");

        String dataDir = getDataDirPath(ctx);
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
        closeDatabse();

        //ready to zip backup file list
        File tempZip = new File(ctx.getCacheDir(), LIME.DATABASE_BACKUP_NAME);
        if(tempZip.exists() && !tempZip.delete()) Log.w(TAG, "Failed to delete existing temp zip file");
        OutputStream outputStream = null;
        FileInputStream inputStream = null;

        try {
            LIMEUtilities.zip(tempZip.getAbsolutePath(), backupFileList, dataDir , true);
            //saveFileToDownloads(tempZip);
            // Copy temp zip to the User selected URI
            inputStream = new FileInputStream(tempZip);
            outputStream = ctx.getContentResolver().openOutputStream(uri);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

        } catch (Exception e) {
            e.printStackTrace();
            showNotificationMessage(ctx.getText(R.string.l3_initial_backup_error) + "");
        } finally {
            try {
                if (outputStream != null) outputStream.close();
                if (inputStream != null) inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Reopen DB
            if (datasource != null) {
                datasource.openDBConnection(true);
            }
            if (fileSharedPrefsBackup.exists() && !fileSharedPrefsBackup.delete()) Log.w(TAG, "Failed to delete shared preferences backup file in finally");
            if (tempZip.exists() && !tempZip.delete()) Log.w(TAG, "Failed to delete temp zip file in finally");

            showNotificationMessage(ctx.getText(R.string.l3_initial_backup_end) + "");
        }



        // backup finished.  unhold the database connection and false reopen the database.
        datasource.unHoldDBConnection(); //Jeremy '15,5,23
        //mLIMEPref.holdDatabaseConnection(false);
        datasource.openDBConnection(true);

        //cleanup the shared preference backup file.
        if(fileSharedPrefsBackup.exists() && !fileSharedPrefsBackup.delete()) Log.w(TAG, "Failed to delete shared preferences backup file at end");


    }

    public static void restoreDatabase(Uri uri) {
        if (DEBUG)
            Log.i(TAG, "restoreDatabase(Uri) Starting....");

        File tempZip = new File(ctx.getCacheDir(), LIME.DATABASE_BACKUP_NAME);
        if(tempZip.exists() && tempZip.delete()) Log.w(TAG, "Failed to delete shared preferences backup file after restore");

        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            inputStream = ctx.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                throw new FileNotFoundException("Could not open input stream for URI: " + uri);
            }
            outputStream = new FileOutputStream(tempZip);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            Log.i(TAG, "restoreDatabase(Uri) temp file created: " + tempZip.getAbsolutePath());
            restoreDatabase(tempZip.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
            showNotificationMessage(ctx.getText(R.string.l3_initial_restore_error) + "");
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (tempZip.exists() && tempZip.delete())Log.w(TAG, "Failed to delete shared preferences backup file after restore");
        }
    }

	public static void restoreDatabase(String srcFilePath) {

		File check = new File(srcFilePath);
        String dataDir = getDataDirPath(ctx);

		if(check.exists()){

			datasource.holdDBConnection(); //Jeremy '15,5,23
			closeDatabse();
            //restore shared preference
            File checkpref = new File(dataDir, LIME.SHARED_PREFS_BACKUP_NAME);
            if(checkpref.exists() && !checkpref.delete()) Log.w(TAG, "Failed to delete shared preferences backup file after restore");


            try {
				LIMEUtilities.unzip(srcFilePath, dataDir, true);
			} catch (Exception e) {
				e.printStackTrace();
				showNotificationMessage(ctx.getText(R.string.l3_initial_restore_error) + "");
			}
			finally {
				showNotificationMessage(ctx.getText(R.string.l3_initial_restore_end) + "");
			}

			datasource.unHoldDBConnection(); //Jeremy '15,5,23
			datasource.openDBConnection(true);


			restoreDefaultSharedPreference(checkpref);

			//mLIMEPref.setResetCacheFlag(true);
			resetCache();

			// Check and upgrade the database table
			datasource.checkAndUpdateRelatedTable();

		}else{
			showNotificationMessage(ctx.getText(R.string.error_restore_not_found) + "");

		}

	}

	public static void backupDefaultSharedPreference(File sharePrefs) {

		if(sharePrefs.exists() && !sharePrefs.delete()) Log.w(TAG, "Failed to delete existing shared preferences backup file");

		ObjectOutputStream output = null;
		try {
			output = new ObjectOutputStream(new FileOutputStream(sharePrefs));
			SharedPreferences pref = ctx.getSharedPreferences(ctx.getPackageName() + "_preferences", Context.MODE_PRIVATE);
			output.writeObject(pref.getAll());

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (output != null) {
					output.flush();
					output.close();
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}

	}

    @SuppressWarnings("unchecked")
	public static void restoreDefaultSharedPreference(File sharePrefs )
	{

        try (ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(sharePrefs))) {
            try {
                SharedPreferences.Editor prefEdit = ctx.getSharedPreferences(ctx.getPackageName() + "_preferences", Context.MODE_PRIVATE).edit();
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
                prefEdit.commit();

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
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




	public static void closeDatabse() {
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
	 * Select Remote File to download
	 */
	public File downloadRemoteFile(String backup_url, String url, String folder, String filename){

		File olddbfile = new File(folder + filename);
		if(olddbfile.exists() && !olddbfile.delete()) Log.w(TAG, "Failed to delete existing olddbfile");

		//mLIMEPref.setParameter("reload_database", true);
		abortDownload = false;
		remoteFileDownloading = true;
		File target = downloadRemoteFile(url, folder, filename);
		if(!target.exists() || target.length() == 0){
			target = downloadRemoteFile(backup_url, folder, filename);
		}
		remoteFileDownloading = false;
		return target;
	}

	/*
	 * Download Remote File
	 */	public File downloadRemoteFile(String url, String folder, String filename){

		if(DEBUG)
			Log.i(TAG,"downloadRemoteFile() Starting....");

		try {
			URL downloadUrl = new URL(url);
			URLConnection conn = downloadUrl.openConnection();
			conn.connect();
			InputStream is = conn.getInputStream();
			long remoteFileSize = conn.getContentLength();
			long downloadedSize = 0;

			if(DEBUG)
				Log.i(TAG, "downloadRemoteFile() contentLength:");

			if(is == null){
				throw new RuntimeException("stream is null");
			}

			File downloadFolder = new File(folder);
            if(!downloadFolder.exists() &&
                    downloadFolder.mkdirs()) {
                Log.w(TAG, "Failed to create target folder");
            }

			File downloadedFile = new File(downloadFolder.getAbsolutePath() + File.separator + filename);
			if(downloadedFile.exists() && !downloadedFile.delete()) Log.w(TAG, "Failed to delete existing downloadedFile");

			FileOutputStream fos;
			fos = new FileOutputStream(downloadedFile);
			// '04,12,27 Jeremy modified buf size from 128 to 128k and dramatically speed-up downloading speed on modern devices
			byte[] buf = new byte[128000];
			do{
				Thread.yield();
				int numread = is.read(buf);
				downloadedSize += numread;

				if(DEBUG)
					Log.i(TAG, "downloadRemoteFile(), contentLength:"
							+ remoteFileSize+ ". downloadedSize:" + downloadedSize);

				if(numread <=0){break;}
				fos.write(buf, 0, numread);
			}while(!abortDownload);
			fos.close();
			is.close();

			return downloadedFile;

		} catch (FileNotFoundException e) {
			Log.d(TAG,"downloadRemoteFile(); can't open temp file on sdcard for writing.");
			showNotificationMessage(ctx.getText(R.string.l3_initial_download_write_sdcard_failed)+ "");
			e.printStackTrace();

		} catch (MalformedURLException e) {
			Log.d(TAG, "downloadRemoteFile() MalformedURLException....");
			showNotificationMessage(ctx.getText(R.string.l3_initial_download_failed)+ "");
			e.printStackTrace();
		} catch (IOException e){
			Log.d(TAG, "downloadRemoteFile() IOException....");
			showNotificationMessage(ctx.getText(R.string.l3_initial_download_failed)+ "");
			e.printStackTrace();
		} catch (Exception e){
			Log.d(TAG, "downloadRemoteFile() Others....");
			showNotificationMessage(ctx.getText(R.string.l3_initial_download_failed)+ "");
			e.printStackTrace();
		}
		if(DEBUG)
			Log.i(TAG, "downloadRemoteFile() failed.");
		return null;
	}

	/*
	 * Decompress retrieved file to target folder
	 */
	public static void decompressFile(File sourceFile, String targetFolder, String targetFile, boolean removeOriginal){
		if(DEBUG)
			Log.i(TAG, "decompressFile(), souce = " + sourceFile.toString() +	", target = " + targetFolder + "/" + targetFile);

		try {

			File targetFolderObj = new File(targetFolder);
			if(!targetFolderObj.exists() &&
				targetFolderObj.mkdirs()) {
				Log.w(TAG, "Failed to create target folder");
			}

			FileInputStream fis = new FileInputStream(sourceFile);
            ZipInputStream zis = getZipInputStream(targetFile, fis, targetFolderObj);
            zis.close();
			fis.close();

			if(removeOriginal && !sourceFile.delete()) Log.w(TAG, "Failed to delete original source file");
        } catch (Exception e) {
			showNotificationMessage(ctx.getText(R.string.l3_initial_download_failed)+ "");
			e.printStackTrace();
		}
    }

    private static ZipInputStream getZipInputStream(String targetFile, FileInputStream fis, File targetFolderObj) throws IOException {
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis));
        //ZipEntry entry;
        while (( zis.getNextEntry()) != null) {

            int size;
            byte[] buffer = new byte[2048];

            File OutputFile = new File(targetFolderObj.getAbsolutePath() + File.separator + targetFile);
            if(OutputFile.exists() && !OutputFile.delete()) Log.w(TAG, "Failed to delete existing output file");

            FileOutputStream fos = new FileOutputStream(OutputFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos, buffer.length);
            while ((size = zis.read(buffer, 0, buffer.length)) != -1) {
                bos.write(buffer, 0, size);
            }
            bos.flush();
            bos.close();

            //Log.i("ART","uncompress Output File:"+OutputFile.getAbsolutePath() + " / " + OutputFile.length());

        }
        return zis;
    }

    public void compressFile(File sourceFile, String targetFolder, String targetFile){
		if(DEBUG)
			Log.i(TAG, "compressFile(), srouce = " + sourceFile.toString() +
					", target = " + targetFolder + "/" + targetFile);
		try{
			final int BUFFER = 2048;

			File targetFolderObj = new File(targetFolder);
			if(!targetFolderObj.exists()&&
				targetFolderObj.mkdirs()) {
				Log.w(TAG, "Failed to create target folder");
			}


			File OutputFile = new File(targetFolderObj.getAbsolutePath() + File.separator + targetFile);
			if(OutputFile.exists() && !OutputFile.delete()) Log.w(TAG, "Failed to delete existing output file for compression");

			FileOutputStream dest = new FileOutputStream(OutputFile);
			ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));

			byte[] data = new byte[BUFFER];

			FileInputStream fi = new  FileInputStream(sourceFile);
			BufferedInputStream origin = new BufferedInputStream(fi, BUFFER);
			ZipEntry entry = new ZipEntry(sourceFile.getAbsolutePath());
			out.putNextEntry(entry);
			int count;
			while((count = origin.read(data, 0, BUFFER)) != -1) {
				out.write(data, 0, count);
			}
			origin.close();
			out.close();

			//Log.i("ART","compress Output File:"+OutputFile.getAbsolutePath() + " / " + OutputFile.length());

		} catch(Exception e) {
			e.printStackTrace();
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


	public int getLoadingMappingPercentageDone() {
		if(remoteFileDownloading) return 0;
		else return datasource.getProgressPercentageDone();
	}
/*
	@Deprecated //by Jeremy '12,6,6
	public void forceUpgrad() throws RemoteException {
		//if (datasource == null) {loadLimeDB();}
		datasource.forceUpgrade();
	}*/

	//	}


	//	public IBinder onBind(Intent arg0) {
	//		return new DBServiceImpl(this);
	//	}
	//
	//	/*
	//	 * (non-Javadoc)
	//	 * 
	//	 * @see android.app.Service#onCreate()
	//	 */
	//	
	//	public void onCreate() {
	//		super.onCreate();
	//	}
	//
	//	/*
	//	 * (non-Javadoc)
	//	 * 
	//	 * @see android.app.Service#onDestroy()
	//	 */
	//	
	//	public void onDestroy() {
	//		if (datasource != null) {
	//			datasource.close();
	//			datasource = null;
	//
	//		}
	//		notificationMgr.cancelAll();
	//		super.onDestroy();
	//	}

	//Jeremy '12,4,23 rewriting using alert notification builder in LIME utilities to replace the deprecated method
	public static void showNotificationMessage(String message) {

		Intent i;
		i = new Intent(ctx, MainActivity.class);

		LIMEUtilities.showNotification(
				ctx, true, ctx.getText(R.string.ime_setting), message, i);

	}

	public void renameTableName(String source, String target) {
		if(datasource != null){
			datasource.renameTableName(source, target);
		}
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
		return datasource.isDatabseOnHold();
	}


}
