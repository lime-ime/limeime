/*    
 **    Copyright 2010, The LimeIME Open Source Project
 ** 
 **    Project Url: http://code.google.com/p/limeime/
 **                 http://android.toload.net/
 **
 **    This program is free software: you can redistribute it and/or modify
 **    it under the terms of the GNU General Public License as published by
 **    the Free Software Foundation, either version 3 of the License, or
 **    (at your option) any later version.

 **    This program is distributed in the hope that it will be useful,
 **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 **    GNU General Public License for more details.

 **    You should have received a copy of the GNU General Public License
 **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.toload.main.hd.limesettings;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.zip.*;

import net.toload.main.hd.LIMEMenu;
import net.toload.main.hd.R;
import net.toload.main.hd.global.KeyboardObj;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.global.LIMEUtilities;
import net.toload.main.hd.limedb.LimeDB;

import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.util.Log;

//Jeremy '12,5,1 renamed from DBServer and change from service to ordinary class.
public class  DBServer {
	private final boolean DEBUG = false;
	private final String TAG = "LIME.DBServer";
	//private NotificationManager notificationMgr;

	protected static LimeDB dbAdapter = null;  //static LIMEDB for shared LIMEDB between DBServer instances
	protected static LIMEPreferenceManager mLIMEPref = null;

	private static boolean remoteFileDownloading = false;
	private static int percentageDone = 0;

	private static String loadingTablename = "";  //Jeremy '12,6,2 change all variable to static for all instances to share these variables
	private static boolean abortDownload = false;

	public final static int intentLIMEMenu = 0;
	public final static int intentLIMEMappingLoading = 1;
	public final static int intentLIMEInitial = 2;

	protected static Context ctx = null;

	// Monitoring thread.
	//	private Thread thread = null;

	//	public class DBServiceImpl extends IDBService.Stub {
	//
	//		Context ctx = null;
	//		//private Thread thread = null;
	//
	//		DBServiceImpl(Context ctx) {
	//			this.ctx = ctx;
	//			mLIMEPref = new LIMEPreferenceManager(ctx);
	//			loadLimeDB();
	//		}
	public DBServer(Context context){
		DBServer.ctx = context;
		mLIMEPref = new LIMEPreferenceManager(ctx);
		//loadLimeDB();
		if(dbAdapter==null)
			dbAdapter = new LimeDB(ctx); 
	}
/* deprecated by jeremy '12,5,2
	public void loadLimeDB(){	
		if(dbAdapter==null)
			dbAdapter = new LimeDB(ctx); 
	}
*/

	public void loadMapping(String filename, String tablename) throws RemoteException {
		if(DEBUG)
			Log.i(TAG,"loadMapping() on " + loadingTablename);


		loadingTablename = tablename;


		File sourcefile = new File(filename);

		// Start Loading
		//if (dbAdapter == null) {loadLimeDB();}

		dbAdapter.setFinish(false);
		dbAdapter.setFilename(sourcefile);

		showNotificationMessage(ctx.getText(R.string.lime_setting_notification_loading)+ "", intentLIMEMappingLoading);
		dbAdapter.loadFileV2(tablename);
		//dbAdapter.close();

		// Reset for SearchSrv
		mLIMEPref.setParameter(LIME.SEARCHSRV_RESET_CACHE,false);
	}

	public void resetMapping(final String tablename) throws RemoteException {

		if(DEBUG)
			Log.i(TAG,"resetMapping() on " + loadingTablename);

		dbAdapter.deleteAll(tablename);

		// Reset cache in SearchSrv
		mLIMEPref.setParameter(LIME.SEARCHSRV_RESET_CACHE,false);
	}

	public int getLoadingMappingCount(){
		return dbAdapter.getCount();
	}

	public boolean isLoadingMappingFinished(){
		if(abortDownload)  //Jeremy '12,4,9 to avoid showing finished importing when download stage is aborted. 
			return false;
		else
			return dbAdapter.isLoadingMappingFinished();
	}

	public boolean isLoadingMappingThreadAborted(){
		return dbAdapter.isLoadingMappingThreadAborted();
	}

	public boolean isLoadingMappingThreadAlive(){		
		if(DEBUG) Log.i(TAG, "isLoadingMappingThreadAlive()"+  dbAdapter.isLoadingMappingThreadAlive() + "loadingTablename = " + loadingTablename);
		return dbAdapter.isLoadingMappingThreadAlive();
	}

	public boolean isRemoteFileDownloading(){
		if(DEBUG) Log.i(TAG, "isRemoteFIleDownloading():"+ remoteFileDownloading+"");
		return remoteFileDownloading;
	}

	public void abortLoadMapping(){
		if(DEBUG)
			Log.i(TAG,"abortLoadMapping() on " + loadingTablename);
		try {
			if(loadingTablename.equals("")) //Jeremy '12,4,9 means abort doloading. no need to reset mappings.
				return;
			resetMapping(loadingTablename);
		} catch (RemoteException e) {
			e.printStackTrace();
		}

	}

	public void abortRemoteFileDownload(){
		remoteFileDownloading = false;
		abortDownload = true;
	}

	File downloadedFile = null;

	public void resetDownloadDatabase() throws RemoteException {

		//Jeremy '12,4,7 close db before replace db file
		closeDatabse();

		String dbtarget = mLIMEPref.getParameterString("dbtarget");
		if(dbtarget.equals("device")){
			File delTargetFile1 = new File(LIME.DATABASE_DECOMPRESS_FOLDER + File.separator + LIME.DATABASE_NAME);
			if(delTargetFile1.exists()){delTargetFile1.delete();}			

		}if(dbtarget.equals("sdcard")){ //Jeremy '11,8,17
			File delTargetFile2 = new File(LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + LIME.DATABASE_NAME);
			if(delTargetFile2.exists()){delTargetFile2.delete();}			
		}
		File delTargetFile3 = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator + LIME.DATABASE_SOURCE_FILENAME);
		File delTargetFile2 = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator + LIME.DATABASE_SOURCE_FILENAME_EMPTY);
		if(delTargetFile3.exists()){delTargetFile3.delete();}	
		if(delTargetFile2.exists()){delTargetFile2.delete();}			
	}

	public void downloadEmptyDatabase() throws RemoteException {
		//if (dbAdapter == null) 	loadLimeDB();
		
		resetDownloadDatabase();

		Thread threadTask = new Thread() {
			public void run() {
				showNotificationMessage(ctx.getText(R.string.l3_dbservice_download_start_empty)+ "", intentLIMEInitial);
				downloadedFile = downloadRemoteFile(LIME.IM_DOWNLOAD_TARGET_EMPTY, LIME.G_IM_DOWNLOAD_TARGET_EMPTY, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_SOURCE_FILENAME_EMPTY);
				if(downloadedFile==null){
					mLIMEPref.setParameter(LIME.DOWNLOAD_START, false);
				}else{
					String dbtarget = mLIMEPref.getParameterString("dbtarget");
					String folder = "";
					if(dbtarget.equals("device")){
						folder = LIME.DATABASE_DECOMPRESS_FOLDER;
					}else{
						folder = LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD;
					}
					if(downloadedFile.exists()){
						if(decompressFile(downloadedFile, folder, LIME.DATABASE_NAME)){
							checkPhoneticKeyboardSetting();//Jeremy '12,6,8 check the pheonetic keyboard consistency
							Thread threadTask = new Thread() {
								public void run() {
									downloadedFile.delete();
									mLIMEPref.setParameter(LIME.DOWNLOAD_START, false);
								}
							};
							threadTask.start();
						}
						//getSharedPreferences(LIME.DATABASE_DOWNLOAD_STATUS, 0).edit().putString(LIME.DATABASE_DOWNLOAD_STATUS, "true").commit();
						mLIMEPref.setParameter(LIME.DATABASE_DOWNLOAD_STATUS, "true");
						showNotificationMessage(ctx.getText(R.string.l3_dbservice_download_loaded)+ "", intentLIMEMenu);
						dbAdapter.openDBConnection(true);
						dbAdapter.checkPhoneticKeyboardSetting();//Jeremy '12,6,8 check the pheonetic keyboard consistency
					}
				}
			}

		};
		threadTask.start();
	}

	public void downloadPhoneticHsOnlyDatabase() throws RemoteException {
		//if (dbAdapter == null) {loadLimeDB();}
		resetDownloadDatabase();
		Thread threadTask = new Thread() {
			public void run() { 
				showNotificationMessage(ctx.getText(R.string.l3_dbservice_download_start)+ "", intentLIMEInitial);
				downloadedFile = downloadRemoteFile(LIME.IM_DOWNLOAD_TARGET_PHONETIC_HS_ONLY, LIME.G_IM_DOWNLOAD_TARGET_PHONETIC_HS_ONLY, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_SOURCE_FILENAME);
				if(downloadedFile==null){
					//showNotificationMessage(ctx.getText(R.string.l3_dbservice_download_loaded)+ "", intentLIMEMenu);
					mLIMEPref.setParameter(LIME.DOWNLOAD_START, false);
				}else{
					String dbtarget = mLIMEPref.getParameterString("dbtarget");
					String folder = ""; 
					if(dbtarget.equals("device")){
						folder = LIME.DATABASE_DECOMPRESS_FOLDER;
					}else{
						folder = LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD;
					}

					if(downloadedFile.exists()){
						if(decompressFile(downloadedFile, folder, LIME.DATABASE_NAME)){
							Thread threadTask = new Thread() {
								public void run() {
									downloadedFile.delete();
									mLIMEPref.setParameter(LIME.DOWNLOAD_START, false);
								}
							};
							threadTask.start();
						}
						//getSharedPreferences(LIME.DATABASE_DOWNLOAD_STATUS, 0).edit().putString(LIME.DATABASE_DOWNLOAD_STATUS, "true").commit();
						mLIMEPref.setParameter(LIME.DATABASE_DOWNLOAD_STATUS, "true");
						showNotificationMessage(ctx.getText(R.string.l3_dbservice_download_loaded)+ "", intentLIMEMenu);
						//Jeremy '12,4,7 re-open the dbconnection
						dbAdapter.openDBConnection(true);
						dbAdapter.checkPhoneticKeyboardSetting();//Jeremy '12,6,8 check the pheonetic keyboard consistency
					}
				}
			}

		};
		threadTask.start();
	}


	public void downloadPhoneticOnlyDatabase() throws RemoteException {
		//if (dbAdapter == null) {loadLimeDB();}
		resetDownloadDatabase();
		Thread threadTask = new Thread() {
			public void run() { 
				showNotificationMessage(ctx.getText(R.string.l3_dbservice_download_start)+ "", intentLIMEInitial);
				downloadedFile = downloadRemoteFile(LIME.IM_DOWNLOAD_TARGET_PHONETIC_ONLY, LIME.G_IM_DOWNLOAD_TARGET_PHONETIC_ONLY, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_SOURCE_FILENAME);
				if(downloadedFile==null){
					//showNotificationMessage(ctx.getText(R.string.l3_dbservice_download_loaded)+ "", intentLIMEMenu);
					mLIMEPref.setParameter(LIME.DOWNLOAD_START, false);
				}else{
					String dbtarget = mLIMEPref.getParameterString("dbtarget");
					String folder = ""; 
					if(dbtarget.equals("device")){
						folder = LIME.DATABASE_DECOMPRESS_FOLDER;
					}else{
						folder = LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD;
					}

					if(downloadedFile.exists()){
						if(decompressFile(downloadedFile, folder, LIME.DATABASE_NAME)){
							Thread threadTask = new Thread() {
								public void run() {
									downloadedFile.delete();
									mLIMEPref.setParameter(LIME.DOWNLOAD_START, false);
								}
							};
							threadTask.start();
						}
						//getSharedPreferences(LIME.DATABASE_DOWNLOAD_STATUS, 0).edit().putString(LIME.DATABASE_DOWNLOAD_STATUS, "true").commit();
						mLIMEPref.setParameter(LIME.DATABASE_DOWNLOAD_STATUS, "true");
						showNotificationMessage(ctx.getText(R.string.l3_dbservice_download_loaded)+ "", intentLIMEMenu);
						//Jeremy '12,4,7 re-open the dbconnection
						dbAdapter.openDBConnection(true);
						dbAdapter.checkPhoneticKeyboardSetting();//Jeremy '12,6,8 check the pheonetic keyboard consistency
					}
				}
			}

		};
		threadTask.start();
	}


	public void downloadPreloadedDatabase() throws RemoteException {
		//if (dbAdapter == null) {loadLimeDB();}
		resetDownloadDatabase();
		Thread threadTask = new Thread() {
			public void run() { 
				showNotificationMessage(ctx.getText(R.string.l3_dbservice_download_start)+ "", intentLIMEInitial);
				downloadedFile = downloadRemoteFile(LIME.IM_DOWNLOAD_TARGET_PRELOADED, LIME.G_IM_DOWNLOAD_TARGET_PRELOADED, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_SOURCE_FILENAME);
				if(downloadedFile==null){
					//showNotificationMessage(ctx.getText(R.string.l3_dbservice_download_loaded)+ "", intentLIMEMenu);
					mLIMEPref.setParameter(LIME.DOWNLOAD_START, false);
				}else{
					String dbtarget = mLIMEPref.getParameterString("dbtarget");
					String folder = ""; 
					if(dbtarget.equals("device")){
						folder = LIME.DATABASE_DECOMPRESS_FOLDER;
					}else{
						folder = LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD;
					}
					if(downloadedFile.exists()){
						if(decompressFile(downloadedFile, folder, LIME.DATABASE_NAME)){
							Thread threadTask = new Thread() {
								public void run() {
									downloadedFile.delete();
									mLIMEPref.setParameter(LIME.DOWNLOAD_START, false);
								}
							};
							threadTask.start();
						}
						//getSharedPreferences(LIME.DATABASE_DOWNLOAD_STATUS, 0).edit().putString(LIME.DATABASE_DOWNLOAD_STATUS, "true").commit();
						mLIMEPref.setParameter(LIME.DATABASE_DOWNLOAD_STATUS, "true");
						showNotificationMessage(ctx.getText(R.string.l3_dbservice_download_loaded)+ "", intentLIMEMenu);
						//Jeremy '12,4,7 re-open the dbconnection
						dbAdapter.openDBConnection(true);
						dbAdapter.checkPhoneticKeyboardSetting();//Jeremy '12,6,8 check the pheonetic keyboard consistency
					}
				}
			}

		};
		threadTask.start();
	}

	public void backupDatabase() throws RemoteException {
		showNotificationMessage(ctx.getText(R.string.l3_initial_backup_start)+ "", intentLIMEInitial);

		closeDatabse(); // Jeremy '12,5,1 close database here.
		File srcFile = null;
		String dbtarget = mLIMEPref.getParameterString("dbtarget");
		if(dbtarget.equals("device")){
			srcFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER + File.separator + LIME.DATABASE_NAME);
		}else{
			srcFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + LIME.DATABASE_NAME);
		}			
		compressFile(srcFile, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_BACKUP_NAME);
		showNotificationMessage(ctx.getText(R.string.l3_initial_backup_end)+ "", intentLIMEMenu);

		//Jeremy '12,4,7 re-open the dbconnection
		dbAdapter.openDBConnection(true);
	}


	public void restoreDatabase() throws RemoteException {

		closeDatabse(); // Jeremy '12,5,1 close database here.
		mLIMEPref.setParameter("reload_database", true);

		showNotificationMessage(ctx.getText(R.string.l3_initial_restore_start)+ "", intentLIMEInitial);
		File srcFile = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator + LIME.DATABASE_BACKUP_NAME);

		String dbtarget = mLIMEPref.getParameterString("dbtarget");
		if(dbtarget.equals("device")){
			decompressFile(srcFile, LIME.DATABASE_DECOMPRESS_FOLDER, LIME.DATABASE_NAME);
		}else{
			decompressFile(srcFile, LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD, LIME.DATABASE_NAME);
		}			
		//getSharedPreferences(LIME.DATABASE_DOWNLOAD_STATUS, 0).edit().putString(LIME.DATABASE_DOWNLOAD_STATUS, "true").commit();
		mLIMEPref.setParameter(LIME.DATABASE_DOWNLOAD_STATUS, "true");
		showNotificationMessage(ctx.getText(R.string.l3_initial_restore_end)+ "", intentLIMEMenu); 

		//Jeremy '12,4,7 re-open the dbconnection
		dbAdapter.openDBConnection(true);
	}


	public String getImInfo(String im, String field) throws RemoteException {
		//if (dbAdapter == null) {loadLimeDB();}
		return dbAdapter.getImInfo(im, field);
	}


	public String getKeyboardInfo(String keyboardCode, String field) throws RemoteException {
		//if (dbAdapter == null) {loadLimeDB();}
		return dbAdapter.getKeyboardInfo(keyboardCode, field);
	}


	public void removeImInfo(String im, String field)
			throws RemoteException {
		//if (dbAdapter == null) {loadLimeDB();}
		dbAdapter.removeImInfo(im, field);

	}


	public void resetImInfo(String im) throws RemoteException {
		//if (dbAdapter == null) {loadLimeDB();}
		dbAdapter.resetImInfo(im);

	}


	public void setImInfo(String im, String field, String value)
			throws RemoteException {
		//if (dbAdapter == null) {loadLimeDB();}
		dbAdapter.setImInfo(im, field, value);

	}


	public void closeDatabse() throws RemoteException {
		if (dbAdapter != null) {
			dbAdapter.close();
		}
	}


	public List<KeyboardObj> getKeyboardList() throws RemoteException {
		List<KeyboardObj> result = dbAdapter.getKeyboardList();
		return result;
	}


	public void setIMKeyboard(String im, String value,
			String keyboard) throws RemoteException {
		//if (dbAdapter == null) {loadLimeDB();}
		dbAdapter.setIMKeyboard(im, value, keyboard);
	}


	public String getKeyboardCode(String im)
			throws RemoteException {
		//if (dbAdapter == null) {loadLimeDB();}
		return dbAdapter.getKeyboardCode(im);
	}


	public void downloadDayi() throws RemoteException {
		Thread threadTask = new Thread() {
			public void run() {
				showNotificationMessage(ctx.getText(R.string.l3_im_download_from_dayi_start)+ "", intentLIMEMappingLoading);
				downloadedFile = downloadRemoteFile(LIME.DAYI_DOWNLOAD_URL, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_SOURCE_DAYI);
				if(downloadedFile!=null){
					showNotificationMessage(ctx.getText(R.string.l3_im_download_from_dayi_install)+ "", intentLIMEMappingLoading);
					try {
						loadMapping(downloadedFile.getAbsolutePath(), "dayi");
					} catch (RemoteException e) {
						e.printStackTrace();
						showNotificationMessage("Download failed, please check your internet connection.", intentLIMEMenu);
					}
				}
			}
		};
		threadTask.start();
	}



	public void downloadCjCns() throws RemoteException {
		Thread threadTask = new Thread() {
			public void run() {
				showNotificationMessage(ctx.getText(R.string.l3_im_download_from_cj_cns_start)+ "", intentLIMEMappingLoading);
				downloadedFile = downloadRemoteFile(LIME.G_CJ_11643_DOWNLOAD_URL, LIME.G_CJ_11643_DOWNLOAD_URL, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_SOURCE_CJ_CNS);
				if(downloadedFile!=null){
					showNotificationMessage(ctx.getText(R.string.l3_im_download_from_cj_cns_install)+ "", intentLIMEMappingLoading);
					try {
						loadMapping(downloadedFile.getAbsolutePath(), "cj");
					} catch (RemoteException e) {
						e.printStackTrace();
						showNotificationMessage("Download failed, please check your internet connection.", intentLIMEMenu);
					}
				}
			}
		};
		threadTask.start();
	}



	public void downloadPhoneticCns() throws RemoteException {
		Thread threadTask = new Thread() {
			public void run() {
				showNotificationMessage(ctx.getText(R.string.l3_im_download_from_phonetic_cns_start)+ "", intentLIMEMappingLoading);
				downloadedFile = downloadRemoteFile(LIME.G_PHONETIC_11643_DOWNLOAD_URL, LIME.G_PHONETIC_11643_DOWNLOAD_URL, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_SOURCE_PHONETIC_CNS);
				if(downloadedFile!=null){
					showNotificationMessage(ctx.getText(R.string.l3_im_download_from_phonetic_cns_install)+ "", intentLIMEMappingLoading);
					try {
						loadMapping(downloadedFile.getAbsolutePath(), "phonetic");
					} catch (RemoteException e) {
						e.printStackTrace();
						showNotificationMessage("Download failed, please check your internet connection.", intentLIMEMenu);
					}
				}
			}
		};
		threadTask.start();
	}


	public void downloadPhoneticAdv() throws RemoteException {
		Thread threadTask = new Thread() {
			public void run() {
				showNotificationMessage(ctx.getText(R.string.l3_im_download_from_phonetic_adv_start)+ "", intentLIMEMappingLoading);
				downloadedFile = downloadRemoteFile(LIME.PHONETICADV_DOWNLOAD_URL, LIME.G_PHONETICADV_DOWNLOAD_URL, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_SOURCE_PHONETICADV);
				if(downloadedFile!=null){
					showNotificationMessage(ctx.getText(R.string.l3_im_download_from_phonetic_adv_install)+ "", intentLIMEMappingLoading);
					try {
						loadMapping(downloadedFile.getAbsolutePath(), "phonetic");
					} catch (RemoteException e) {
						e.printStackTrace();
						showNotificationMessage("Download failed, please check your internet connection.", intentLIMEMenu);
					}
				}
			}
		};
		threadTask.start();
	}


	public void downloadPhonetic() throws RemoteException {
		Thread threadTask = new Thread() {
			public void run() {
				showNotificationMessage(ctx.getText(R.string.l3_im_download_from_phonetic_start)+ "", intentLIMEMappingLoading);
				downloadedFile = downloadRemoteFile(LIME.PHONETIC_DOWNLOAD_URL, LIME.G_PHONETIC_DOWNLOAD_URL, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_SOURCE_PHONETIC);
				if(downloadedFile!=null){
					showNotificationMessage(ctx.getText(R.string.l3_im_download_from_phonetic_install)+ "", intentLIMEMappingLoading);
					try {
						loadMapping(downloadedFile.getAbsolutePath(), "phonetic");
					} catch (RemoteException e) {
						e.printStackTrace();
						showNotificationMessage("Download failed, please check your internet connection.", intentLIMEMenu);
					}
				}
			}
		};
		threadTask.start();
	}


	public void downloadCj5() throws RemoteException {
		Thread threadTask = new Thread() {
			public void run() {
				showNotificationMessage(ctx.getText(R.string.l3_im_download_from_cj5_start)+ "", intentLIMEMappingLoading);
				downloadedFile = downloadRemoteFile(LIME.CJ5_DOWNLOAD_URL, LIME.G_CJ5_DOWNLOAD_URL, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_SOURCE_CJ5);
				if(downloadedFile!=null){
					showNotificationMessage(ctx.getText(R.string.l3_im_download_from_cj5_install)+ "", intentLIMEMappingLoading);
					try {
						loadMapping(downloadedFile.getAbsolutePath(), "cj5");
					} catch (RemoteException e) {
						e.printStackTrace();
						showNotificationMessage("Download failed, please check your internet connection.", intentLIMEMenu);
					}
				}
			}
		};
		threadTask.start();
	}


	public void downloadEcj() throws RemoteException {
		Thread threadTask = new Thread() {
			public void run() {
				showNotificationMessage(ctx.getText(R.string.l3_im_download_from_ecj_start)+ "", intentLIMEMappingLoading);
				downloadedFile = downloadRemoteFile(LIME.ECJ_DOWNLOAD_URL, LIME.G_ECJ_DOWNLOAD_URL, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_SOURCE_ECJ);
				if(downloadedFile!=null){
					showNotificationMessage(ctx.getText(R.string.l3_im_download_from_ecj_install)+ "", intentLIMEMappingLoading);
					try {
						loadMapping(downloadedFile.getAbsolutePath(), "ecj");
					} catch (RemoteException e) {
						e.printStackTrace();
						showNotificationMessage("Download failed, please check your internet connection.", intentLIMEMenu);
					}
				}
			}
		};
		threadTask.start();
	}


	public void downloadHs() throws RemoteException {
		Thread threadTask = new Thread() {
			public void run() {
				showNotificationMessage(ctx.getText(R.string.l3_im_download_from_hs_start)+ "", intentLIMEMappingLoading);
				downloadedFile = downloadRemoteFile(LIME.HS_DOWNLOAD_URL, LIME.G_HS_DOWNLOAD_URL, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_SOURCE_HS);
				if(downloadedFile!=null){
					showNotificationMessage(ctx.getText(R.string.l3_im_download_from_hs_install)+ "", intentLIMEMappingLoading);
					try {
						loadMapping(downloadedFile.getAbsolutePath(), "hs");
					} catch (RemoteException e) {
						e.printStackTrace();
						showNotificationMessage("Download failed, please check your internet connection.", intentLIMEMenu);
					}
				}
			}
		};
		threadTask.start();
	}


	public void downloadWb() throws RemoteException {
		Thread threadTask = new Thread() {
			public void run() {
				showNotificationMessage(ctx.getText(R.string.l3_im_download_from_wb_start)+ "", intentLIMEMappingLoading);
				downloadedFile = downloadRemoteFile(LIME.WB_DOWNLOAD_URL, LIME.G_WB_DOWNLOAD_URL, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_SOURCE_WB);
				if(downloadedFile!=null){
					showNotificationMessage(ctx.getText(R.string.l3_im_download_from_wb_install)+ "", intentLIMEMappingLoading);
					try {
						loadMapping(downloadedFile.getAbsolutePath(), "wb");
					} catch (RemoteException e) {
						e.printStackTrace();
						showNotificationMessage("Download failed, please check your internet connection.", intentLIMEMenu);
					}
				}
			}
		};
		threadTask.start();
	}


	public void downloadCj() throws RemoteException {
		Thread threadTask = new Thread() {
			public void run() {
				showNotificationMessage(ctx.getText(R.string.l3_im_download_from_cj_start)+ "", intentLIMEMappingLoading);
				downloadedFile = downloadRemoteFile(LIME.CJ_DOWNLOAD_URL, LIME.G_CJ_DOWNLOAD_URL, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_SOURCE_CJ);
				if(downloadedFile!=null){
					showNotificationMessage(ctx.getText(R.string.l3_im_download_from_cj_install)+ "", intentLIMEMappingLoading);
					try {
						loadMapping(downloadedFile.getAbsolutePath(), "cj");
					} catch (RemoteException e) {
						e.printStackTrace();
						showNotificationMessage("Download failed, please check your internet connection.", intentLIMEMenu);
					}
				}
			}
		};
		threadTask.start();
	}


	public void downloadScj() throws RemoteException {
		Thread threadTask = new Thread() {
			public void run() {
				showNotificationMessage(ctx.getText(R.string.l3_im_download_from_scj_start)+ "", intentLIMEMappingLoading);
				downloadedFile = downloadRemoteFile(LIME.SCJ_DOWNLOAD_URL, LIME.G_SCJ_DOWNLOAD_URL, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_SOURCE_SCJ);
				if(downloadedFile!=null){
					showNotificationMessage(ctx.getText(R.string.l3_im_download_from_scj_install)+ "", intentLIMEMappingLoading);
					try {
						loadMapping(downloadedFile.getAbsolutePath(), "scj");
					} catch (RemoteException e) {
						e.printStackTrace();
						showNotificationMessage("Download failed, please check your internet connection.", intentLIMEMenu);
					}
				}
			}
		};
		threadTask.start();
	}


	public void downloadEz() throws RemoteException {
		Thread threadTask = new Thread() {
			public void run() {
				showNotificationMessage(ctx.getText(R.string.l3_im_download_from_ez_start)+ "", intentLIMEMappingLoading);
				downloadedFile = downloadRemoteFile(LIME.EZ_DOWNLOAD_URL, LIME.G_EZ_DOWNLOAD_URL, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_SOURCE_EZ);
				if(downloadedFile!=null){
					showNotificationMessage(ctx.getText(R.string.l3_im_download_from_ez_install)+ "", intentLIMEMappingLoading);
					try {
						loadMapping(downloadedFile.getAbsolutePath(), "ez");
					} catch (RemoteException e) {
						e.printStackTrace();
						showNotificationMessage("Download failed, please check your internet connection.", intentLIMEMenu);
					}
				}
			}
		};
		threadTask.start();
	}


	public void downloadArray() throws RemoteException {
		Thread threadTask = new Thread() {
			public void run() {
				showNotificationMessage(ctx.getText(R.string.l3_im_download_from_array_start)+ "", intentLIMEMappingLoading);
				downloadedFile = downloadRemoteFile(LIME.ARRAY_DOWNLOAD_URL, LIME.G_ARRAY_DOWNLOAD_URL, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_SOURCE_ARRAY);
				if(downloadedFile!=null){
					showNotificationMessage(ctx.getText(R.string.l3_im_download_from_array_install)+ "", intentLIMEMappingLoading);
					try {
						loadMapping(downloadedFile.getAbsolutePath(), "array");
					} catch (RemoteException e) {
						e.printStackTrace();
						showNotificationMessage("Download failed, please check your internet connection.", intentLIMEMenu);
					}
				}
			}
		};
		threadTask.start();
	}


	public void downloadArray10() throws RemoteException {
		Thread threadTask = new Thread() {
			public void run() {
				showNotificationMessage(ctx.getText(R.string.l3_im_download_from_array10_start)+ "", intentLIMEMappingLoading);
				downloadedFile = downloadRemoteFile(LIME.ARRAY10_DOWNLOAD_URL, LIME.G_ARRAY10_DOWNLOAD_URL, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_SOURCE_ARRAY10);
				if(downloadedFile!=null){
					showNotificationMessage(ctx.getText(R.string.l3_im_download_from_array10_install)+ "", intentLIMEMappingLoading);
					try {
						loadMapping(downloadedFile.getAbsolutePath(), "array10");
					} catch (RemoteException e) {
						e.printStackTrace();
						showNotificationMessage("Download failed, please check your internet connection.", intentLIMEMenu);
					}
				}
			}
		};
		threadTask.start();
	}

	/*
	 * Select Remote File to download
	 */
	public File downloadRemoteFile(String url, String backup_url, String folder, String filename){

		mLIMEPref.setParameter("reload_database", true);
		abortDownload = false;
		remoteFileDownloading = true;
		File target = downloadRemoteFile(url, folder, filename);
		if(target == null){
			target = downloadRemoteFile(backup_url, folder, filename);
		}
		remoteFileDownloading = false;
		return target;
	}

	/*
	 * Download Remote File
	 */
	public File downloadRemoteFile(String url, String folder, String filename){

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
			downloadFolder.mkdirs();

			//Log.i("ART","downloadFolder Folder status :"+ downloadFolder.exists());

			File downloadedFile = new File(downloadFolder.getAbsolutePath() + File.separator + filename);
			if(downloadedFile.exists()){
				downloadedFile.delete();
			}

			FileOutputStream fos = null;
			fos = new FileOutputStream(downloadedFile);

			byte buf[] = new byte[128];
			do{

				int numread = is.read(buf);
				downloadedSize += numread;

				if(downloadedSize ==-1){
					percentageDone = 0;
				}else{
					percentageDone = (int) ((float)downloadedSize/(float)remoteFileSize *100);
				}
				if(DEBUG) 
					Log.i(TAG, "downloadRemoteFile(), contentLength:" 
							+ remoteFileSize+ ". downloadedSize:" + downloadedSize
							+ ". percentage done:" + percentageDone);

				if(numread <=0){break;}
				fos.write(buf, 0, numread);
			}while(!abortDownload);

			is.close();

			return downloadedFile;

		} catch (FileNotFoundException e) {
			Log.d(TAG,"downloadRemoteFile(); can't open temp file on sdcard for writing.");
			showNotificationMessage(ctx.getText(R.string.l3_initial_download_write_sdcard_failed)+ "",intentLIMEMenu );
			e.printStackTrace();

		} catch (MalformedURLException e) {
			Log.d(TAG, "downloadRemoteFile() error....");
			showNotificationMessage(ctx.getText(R.string.l3_initial_download_failed)+ "", intentLIMEMenu);
			e.printStackTrace();
		} catch (IOException e){
			Log.d(TAG, "downloadRemoteFile() error....");
			showNotificationMessage(ctx.getText(R.string.l3_initial_download_failed)+ "", intentLIMEMenu);
			e.printStackTrace();
		} catch (Exception e){
			Log.d(TAG, "downloadRemoteFile() error....");
			showNotificationMessage(ctx.getText(R.string.l3_initial_download_failed)+ "", intentLIMEMenu);
			e.printStackTrace();
		}
		//if(DEBUG)
		Log.i(TAG, "downloadRemoteFile() failed.");
		return null;
	}

	/*
	 * Decompress retrieved file to target folder
	 */
	protected static boolean decompressFile(File sourceFile, String targetFolder, String targetFile){

		try {   

			File targetFolderObj = new File(targetFolder);
			if(!targetFolderObj.exists()){
				targetFolderObj.mkdirs();
			}

			FileInputStream fis = new FileInputStream(sourceFile); 
			ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis));
			//ZipEntry entry; 
			while (( zis.getNextEntry()) != null) {

				int size; 
				byte[] buffer = new byte[2048]; 

				File OutputFile = new File(targetFolderObj.getAbsolutePath() + File.separator + targetFile);
				OutputFile.delete();

				FileOutputStream fos = new FileOutputStream(OutputFile); 
				BufferedOutputStream bos = new BufferedOutputStream(fos, buffer.length); 
				while ((size = zis.read(buffer, 0, buffer.length)) != -1) { 
					bos.write(buffer, 0, size); 
				} 
				bos.flush(); 
				bos.close(); 

				//Log.i("ART","uncompress Output File:"+OutputFile.getAbsolutePath() + " / " + OutputFile.length());

			} 
			zis.close(); 
			fis.close(); 
			return true;
		} catch (Exception e) { 
			showNotificationMessage(ctx.getText(R.string.l3_initial_download_failed)+ "", intentLIMEMenu);
			e.printStackTrace(); 
		}
		return false;
	}

	public void compressFile(File sourceFile, String targetFolder, String targetFile){
		try{
			final int BUFFER = 2048;

			File targetFolderObj = new File(targetFolder);
			if(!targetFolderObj.exists()){
				targetFolderObj.mkdirs();
			}


			File OutputFile = new File(targetFolderObj.getAbsolutePath() + File.separator + targetFile);
			OutputFile.delete();

			FileOutputStream dest = new FileOutputStream(OutputFile);
			ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));

			byte data[] = new byte[BUFFER];

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
		dbAdapter.checkPhoneticKeyboardSetting();
	}
	
	
	public int getLoadingMappingPercentageDone() throws RemoteException {
		if(remoteFileDownloading) return 0;
		else return dbAdapter.getPercentageDone();
	}
/*
	@Deprecated //by Jeremy '12,6,6
	public void forceUpgrad() throws RemoteException {
		//if (dbAdapter == null) {loadLimeDB();}
		dbAdapter.forceUpgrade();
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
	//		if (dbAdapter != null) {
	//			dbAdapter.close();
	//			dbAdapter = null;
	//
	//		}
	//		notificationMgr.cancelAll();
	//		super.onDestroy();
	//	}

	//Jeremy '12,4,23 rewriting using alert notification builder in LIME utilities to replace the deprecated method
	public static void showNotificationMessage(String message, int intent) {

		Intent i = null;
		if(intent == intentLIMEMenu)
			i = new Intent(ctx, LIMEMenu.class);
		else if(intent == intentLIMEMappingLoading)
			i = new Intent(ctx, LIMEMappingLoading.class);
		else if (intent == intentLIMEInitial)
			i = new Intent(ctx, LIMEInitial.class);

		LIMEUtilities.showNotification(
				ctx, true, R.drawable.icon, ctx.getText(R.string.ime_setting), message, i);

		/*
		Notification notification = new Notification(R.drawable.icon, message, System.currentTimeMillis());
		// FLAG_AUTO_CANCEL add by jeremy '10, 3 24
		notification.flags |= Notification.FLAG_AUTO_CANCEL;


		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,i, 0);
		notification.setLatestEventInfo(this, this .getText(R.string.ime_setting), message, contentIntent);
		if(notificationMgr == null){
			notificationMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		}
		notificationMgr.notify(0, notification);
		 */
	}


}
