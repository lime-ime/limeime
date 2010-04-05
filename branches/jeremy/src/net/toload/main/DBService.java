package net.toload.main;

import java.io.File;

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
	
	// Add by Jeremy '10, 3 ,27. Multi table extension.
	private final static String MAPPING_FILE_TEMP = "mapping_file_temp";
	private final static String CJ_MAPPING_FILE_TEMP = "cj_mapping_file_temp";
	private final static String DAYI_MAPPING_FILE_TEMP = "dayi_mapping_file_temp";
	private final static String EZ_MAPPING_FILE_TEMP = "ez_mapping_file_temp";
	private final static String BPMF_MAPPING_FILE_TEMP = "bpmf_mapping_file_temp";
	private final static String RELATED_MAPPING_FILE_TEMP = "related_mapping_file_temp";
	private final static String MAPPING_FILE = "mapping_file";
	// Add by Jeremy '10, 3 ,27. Multi table extension.
	private final static String CJ_MAPPING_FILE = "cj_mapping_file";
	private final static String BPMF_MAPPING_FILE = "bpmf_mapping_file";
	private final static String DAYI_MAPPING_FILE = "dayi_mapping_file";
	private final static String EZ_MAPPING_FILE = "ez_mapping_file";
	private final static String RELATED_MAPPING_FILE = "related_mapping_file";
	private final static String TOTAL_RECORD = "total_record";
	// Add by Jeremy '10, 3 ,27. Multi table extension.
	private final static String CJ_TOTAL_RECORD = "cj_total_record";
	private final static String BPMF_TOTAL_RECORD = "bpmf_total_record";
	private final static String DAYI_TOTAL_RECORD = "dayi_total_record";
	private final static String EZ_TOTAL_RECORD = "ez_total_record";
	private final static String MAPPING_VERSION = "mapping_version";
	// Add by Jeremy '10, 3 ,27. Multi table extension.
	private final static String CJ_MAPPING_VERSION = "cj_mapping_version";
	private final static String BPMF_MAPPING_VERSION = "bmpf_mapping_version";
	private final static String DAYI_MAPPING_VERSION = "dayi_mapping_version";
	private final static String EZ_MAPPING_VERSION = "ez_mapping_version";
	private final static String MAPPING_LOADING = "mapping_loading";

	private NotificationManager notificationMgr;

	private LimeDB db = null;
	
	// Monitoring thread.
	private Thread thread = null;

	public class DBServiceImpl extends IDBService.Stub {

		Context ctx = null;

		DBServiceImpl(Context ctx) {
			this.ctx = ctx;
		}

		public void loadMapping(String filename, String tablename) throws RemoteException {

			// Start Loading
			if (db == null) {
				db = new LimeDB(ctx);
			}

			File sourcefile = new File(filename);
		
			//String sourcestatus = null;
			//SharedPreferences importset = ctx.getSharedPreferences(MAPPING_LOADING, 0);
			//String importstatus = importset.getString(MAPPING_LOADING, "no");
			if(tablename.equals("related")){
				db.deleteRelatedAll();
			}else{
				db.deleteAll(tablename);
			}
			/*
			if (importstatus.equals("no") && sourcestatus.equals("")) {
				
			}else if (importstatus.equals("no") && !sourcestatus.equals("") && !sourcestatus.equals(secret)) {
				db.deleteAll();
			}else if (importstatus.equals("yes")){
				db.deleteAll();
			}*/
			String secret = sourcefile.getName();
			SharedPreferences sourceset =null, sourcetempset= null;
			if(tablename.equals("cj")){
				sourceset = ctx.getSharedPreferences(CJ_MAPPING_FILE, 0);
				sourceset.edit().putString(CJ_MAPPING_FILE, secret).commit();
				sourcetempset = ctx.getSharedPreferences(CJ_MAPPING_FILE_TEMP, 0);
				sourcetempset.edit().putString(CJ_MAPPING_FILE_TEMP, secret).commit();
			}else if(tablename.equals("dayi")){
				sourceset = ctx.getSharedPreferences(DAYI_MAPPING_FILE, 0);
				sourceset.edit().putString(DAYI_MAPPING_FILE, secret).commit();
				sourcetempset = ctx.getSharedPreferences(DAYI_MAPPING_FILE_TEMP, 0);
				sourcetempset.edit().putString(DAYI_MAPPING_FILE_TEMP, secret).commit();
			}else if(tablename.equals("phonetic")){
				sourceset = ctx.getSharedPreferences(BPMF_MAPPING_FILE, 0);
				sourceset.edit().putString(BPMF_MAPPING_FILE, secret).commit();
				sourcetempset = ctx.getSharedPreferences(BPMF_MAPPING_FILE_TEMP, 0);
				sourcetempset.edit().putString(BPMF_MAPPING_FILE_TEMP, secret).commit();
			}else if(tablename.equals("ez")){
				sourceset = ctx.getSharedPreferences(EZ_MAPPING_FILE, 0);
				sourceset.edit().putString(EZ_MAPPING_FILE, secret).commit();
				sourcetempset = ctx.getSharedPreferences(EZ_MAPPING_FILE_TEMP, 0);
				sourcetempset.edit().putString(EZ_MAPPING_FILE_TEMP, secret).commit();
			}else if(tablename.equals("related")){
				sourceset = ctx.getSharedPreferences(RELATED_MAPPING_FILE, 0);
				sourceset.edit().putString(RELATED_MAPPING_FILE, secret).commit();
				sourcetempset = ctx.getSharedPreferences(RELATED_MAPPING_FILE_TEMP, 0);
				sourcetempset.edit().putString(RELATED_MAPPING_FILE_TEMP, secret).commit();
			}else{
				sourceset = ctx.getSharedPreferences(MAPPING_FILE, 0);
				sourceset.edit().putString(MAPPING_FILE, secret).commit();
				sourcetempset = ctx.getSharedPreferences(MAPPING_FILE_TEMP, 0);
				sourcetempset.edit().putString(MAPPING_FILE_TEMP, secret).commit();
			}
			
			
			db.setFilename(sourcefile);

			displayNotificationMessage(ctx.getText(R.string.lime_setting_notification_loading)+ "");

			// Update Loading Status
			// Stop and clear the existing thread.
			if(thread!=null){
				thread.stop();
				thread = null;
			}
			thread = new Thread() {
				public void run() {
					int total = 0;
					//while (!db.isFinish() || !db.isRelatedFinish() ) {
					while (!db.isFinish()) {
						try {
							this.sleep(10000);
							
							if(db.getCount() != 0){
								displayNotificationMessage(
										ctx.getText(R.string.lime_setting_notification_loading_build) + " "
										+ ctx.getText(R.string.lime_setting_notification_loading_import) + " "
										+ db.getCount() + " "
										+ ctx.getText(R.string.lime_setting_notification_loading_end)
										);
							}
							
							
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}

					// Finish task
					if(db.isFinish()){
						displayNotificationMessage(ctx.getText(R.string.lime_setting_notification_finish)+ "");
						db.setCount(0);
					}else{
						displayNotificationMessage(ctx.getText(R.string.lime_setting_notification_failed)+ "");
					}

				}
			};
			thread.start();

			// Actually run the loading
			db.loadFile(tablename);
			db.close();
		}

		public void resetMapping(final String tablename) throws RemoteException {
		// Jeremy '10, 4, 2. Creating a thread doing this to avoid blocing activity when dropping large table
			if(thread!=null){
				thread.stop();
				thread = null;
			}
			thread = new Thread() {
				public void run() {
					// Add by Jeremy '10, 3, 28
					// stop thread here.
					
					if (db == null) {
						db = new LimeDB(ctx);
					}
						if(tablename.equals("related")){
							db.deleteRelatedAll();
						}else{
							db.deleteAll(tablename);
						}
						displayNotificationMessage(ctx
								.getText(R.string.lime_setting_notification_mapping_reset)
								+ "");
						db.close();
					}
				};
			thread.start();
			
		}
		
		//
		// Modified by Jeremy '10, 3,12
		//
		public void restoreRelatedUserdic() throws RemoteException {
			
			final File targetDir = new File(
					Environment.getExternalStorageDirectory().getAbsolutePath()+"/lime");
			final File targetFile = new File(targetDir + "/limedb.txt");
			
			if (!targetDir.exists()) {
				if(!targetDir.mkdirs()){
					Log.i("restoreRelated", "dir creation failed.");
					displayNotificationMessage(ctx.getText(R.string.lime_setting_restore_message_failed)+ "");
					return;
				}
			}
			if (!targetFile.exists()) {
				Log.i("restoreRelated", "file not exist");
				displayNotificationMessage(ctx.getText(R.string.lime_setting_restore_message_failed)+ "");
				return;
				};
			
			//Should do this at dbservice
			//db.deleteDictionaryAll();
			displayNotificationMessage(ctx
					.getText(R.string.lime_setting_restore_message)
					+ "");
			
			if (db == null) {
				db = new LimeDB(ctx);
				}
			
			// Stop and clear the existing thread.
			
			if(thread!=null){
				thread.stop();
				thread = null;
			}
			
			thread = new Thread() {
				public void run() {
					int total = 0;
				
					while (!db.isRelatedFinish()) {
						try {
							Thread.sleep(10000);
							
							if(db.getRelatedCount() != 0){
								displayNotificationMessage(
										ctx.getText(R.string.lime_setting_notification_loading_related) + " "
										+ db.getRelatedCount() + " "
										+ ctx.getText(R.string.lime_setting_notification_loading_end)
										);
							}
							
							
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}

					// Finish task
					if(db.isRelatedFinish()){
						displayNotificationMessage(ctx.getText(R.string.lime_setting_notification_userdic_restore)+ "");
						//db.setCount(0);
					}else{
						// we will never reach here!!
						displayNotificationMessage(ctx.getText(R.string.lime_setting_restore_message_failed)+ "");
					}

				}
			};
			thread.start();
			
			db.restoreRelatedUserdic();
			db.close();
		}

		public void resetUserBackup() throws RemoteException {
			if(thread!=null){
				thread.stop();
				thread = null;
			}
			thread = new Thread() {
				public void run() {
					
					if (db == null) {
						db = new LimeDB(ctx);
					}
					db.deleteDictionaryAll();
					displayNotificationMessage(ctx
							.getText(R.string.lime_setting_notification_userdic_reset)
							+ "");
					db.close();
				}
			};
		thread.start();
		}
		//
		// Modified by Jeremy '10, 3,12
		//
		public void executeUserBackup() throws RemoteException {
			
			// Add by Jeremy '10, 3, 30
			final File targetDir = new File(
					Environment.getExternalStorageDirectory().getAbsolutePath() +"/lime");
			if (!targetDir.exists()) {
				Log.i("backupRelated", "dir not exist, creating..");
				if(!targetDir.mkdirs()){
					Log.i("backupRelated", "dir creation failed.");
					displayNotificationMessage(ctx.getText(R.string.lime_setting_backup_message_failed)+ "");
					return;
				}
			}
			
			if (db == null) {
				db = new LimeDB(ctx);
			}
			
			Log.i("backupRelated", "Creating thread.");
			displayNotificationMessage(ctx
					.getText(R.string.lime_setting_backup_message)
					+ "");
			// Stop and clear the existing thread.
			if(thread!=null){
				thread.stop();
				thread = null;
			}
			Log.i("backupRelated", "monitoring thread started.");
			thread = new Thread() {
				public void run() {
					int total = 0;
					
					
					while (!db.isRelatedFinish()) {
						try {
							Thread.sleep(10000);
							
							if(db.getRelatedCount() != 0){
								displayNotificationMessage(
										ctx.getText(R.string.lime_setting_notification_loading_related) + " "
										+ db.getRelatedCount() + " "
										+ ctx.getText(R.string.lime_setting_notification_loading_end)
										);
							}
							
							
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}

					// Finish task
					if(db.isRelatedFinish()){
						displayNotificationMessage(ctx.getText(R.string.lime_setting_notification_userdic_backup)+ "");
						//db.setCount(0);
					}else{
						displayNotificationMessage(ctx.getText(R.string.lime_setting_backup_message_failed)+ "");
					}

				}
			};
			thread.start();
			db.backupRelatedUserdic();
			db.close();
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Service#onStart(android.content.Intent, int)
	 */
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
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
