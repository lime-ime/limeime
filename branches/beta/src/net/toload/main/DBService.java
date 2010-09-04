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

package net.toload.main;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.*;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class DBService extends Service {
	
	private NotificationManager notificationMgr;

	private LimeDB db = null;
	private LIMEPreferenceManager mLIMEPref = null;
	
	// Monitoring thread.
	private Thread thread = null;

	public class DBServiceImpl extends IDBService.Stub {

		Context ctx = null;

		DBServiceImpl(Context ctx) {
			this.ctx = ctx;
			mLIMEPref = new LIMEPreferenceManager(ctx);
			loadLimeDB();
		}
		
		public void loadLimeDB(){	db = new LimeDB(ctx); }
		
		public void loadMapping(String filename, String tablename) throws RemoteException {

			
			File sourcefile = new File(filename);
			
			// Start Loading
			if (db == null) {loadLimeDB();}

			db.setFilename(sourcefile);
			db.loadFile(tablename);
			db.close();
		}

		public void resetMapping(final String tablename) throws RemoteException {
			if (db == null) {loadLimeDB();}
			db.deleteAll(tablename);
		}
		
		
		File downloadedFile = null;
		
		@Override
		public void resetDownloadDatabase() throws RemoteException {
			File delTargetFile1 = new File(LIME.DATABASE_DECOMPRESS_FOLDER + File.separator + LIME.DATABASE_NAME);
			File delTargetFile3 = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator + LIME.DATABASE_SOURCE_FILENAME);
			if(delTargetFile1.exists()){delTargetFile1.delete();}
			if(delTargetFile3.exists()){delTargetFile3.delete();}			
		}
		 
		@Override
		public void downloadPreloadedDatabase() throws RemoteException {
			if (db == null) {loadLimeDB();}
			Thread threadTask = new Thread() {
				public void run() {
					displayNotificationMessage("Download preloaded database, this action will take few minutes please wait.");
					downloadedFile = downloadRemoteFile(LIME.IM_DOWNLOAD_TARGET_PRELOADED, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_SOURCE_FILENAME);
					if(decompressFile(downloadedFile, LIME.DATABASE_DECOMPRESS_FOLDER, LIME.DATABASE_NAME)){
						Thread threadTask = new Thread() {
							public void run() {
								downloadedFile.delete();
							}
						};
						threadTask.start();
					}
					displayNotificationMessage("Initialize database.");
					initialMappingInfo("custom");
					initialMappingInfo("cj");
					initialMappingInfo("scj");
					initialMappingInfo("array");
					initialMappingInfo("ez");
					initialMappingInfo("phonetic");
					initialMappingInfo("dayi");
					getSharedPreferences(LIME.DATABASE_DOWNLOAD_STATUS, 0).edit().putString(LIME.DATABASE_DOWNLOAD_STATUS, "true").commit();
					displayNotificationMessage("Preloaded database was loaded.");
				}
				
				public void initialMappingInfo(String table){
					int size = db.countMapping(table);
					if(size > 0){
						mLIMEPref.setParameter(table + LIME.IM_MAPPING_FILENAME, LIME.DATABASE_SOURCE_FILENAME);
						mLIMEPref.setParameter(table + LIME.IM_MAPPING_VERSION, "Preloaded");
						mLIMEPref.setParameter(table + LIME.IM_MAPPING_TOTAL, size);
					}else{
						mLIMEPref.setParameter(table + LIME.IM_MAPPING_TOTAL, 0);
					}
				}
			};
			threadTask.start();
		}
		
		/*
		 * Download Remote File
		 */
		public File downloadRemoteFile(String url, String folder, String filename){
			
			try {

				displayNotificationMessage("Convert database file.");
				URL downloadUrl = new URL(url);
				URLConnection conn = downloadUrl.openConnection();
				conn.connect();
				
				InputStream is = conn.getInputStream();
				
				if(is == null){
					throw new RuntimeException("stream is null");
				}
				
				File downloadFolder = new File(folder);
				if(!downloadFolder.exists()){
					downloadFolder.mkdirs();
				}
				
				File downloadedFile = new File(downloadFolder.getAbsolutePath() + File.separator + filename);
					 downloadedFile.delete();
				
				FileOutputStream fos = new FileOutputStream(downloadedFile);
				byte buf[] = new byte[128];
				do{
					int numread = is.read(buf);
					if(numread <=0){break;}
					fos.write(buf, 0, numread);
				}while(true);
				
				try{
					is.close();
				}catch(Exception e){
					e.printStackTrace();
				}
				return downloadedFile;
				
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e){
				e.printStackTrace();
			}
			return null;
			
		}
		
		/*
		 * Decompress retrieved file to target folder
		 */
		public boolean decompressFile(File sourceFile, String targetFolder, String targetFile){

			try {   
				
				File targetFolderObj = new File(targetFolder);
				if(!targetFolderObj.exists()){
					targetFolderObj.mkdirs();
				}
				
				FileInputStream fis = new FileInputStream(sourceFile); 
				ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis));
				ZipEntry entry; 
				while ((entry = zis.getNextEntry()) != null) {
					
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
					
					Log.i("ART","uncompress Output File:"+OutputFile.getAbsolutePath() + " / " + OutputFile.length());
					
				} 
				zis.close(); 
				fis.close(); 
				return true;
			} catch (IOException e) { 
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
				
				Log.i("ART","compress Output File:"+OutputFile.getAbsolutePath() + " / " + OutputFile.length());
				
			} catch(Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		public void backupDatabase() throws RemoteException {
			File srcFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER + File.separator + LIME.DATABASE_NAME);
			compressFile(srcFile, LIME.IM_LOAD_LIME_ROOT_DIRECTORY, LIME.DATABASE_BACKUP_NAME);
		}

		@Override
		public void restoreDatabase() throws RemoteException {
			File srcFile = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator + LIME.DATABASE_BACKUP_NAME);
			decompressFile(srcFile, LIME.DATABASE_DECOMPRESS_FOLDER, LIME.DATABASE_NAME);
			getSharedPreferences(LIME.DATABASE_DOWNLOAD_STATUS, 0).edit().putString(LIME.DATABASE_DOWNLOAD_STATUS, "true").commit();
		}
		
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return new DBServiceImpl(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Service#onCreate()
	 */
	@Override
	public void onCreate() {
		notificationMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		super.onCreate();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Service#onDestroy()
	 */
	@Override
	public void onDestroy() {
		if (db != null) {
			db.close();
			db = null;
			
		}
		notificationMgr.cancelAll();
		super.onDestroy();
	}

	private void displayNotificationMessage(String message) {
		Notification notification = new Notification(R.drawable.icon, message, System.currentTimeMillis());
		// FLAG_AUTO_CANCEL add by jeremy '10, 3 24
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,new Intent(this, LIMEMenu.class), 0);
		notification.setLatestEventInfo(this, this .getText(R.string.ime_setting), message, contentIntent);
		notificationMgr.notify(0, notification);
	}
	
}
