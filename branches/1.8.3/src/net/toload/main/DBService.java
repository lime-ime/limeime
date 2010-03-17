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
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class DBService extends Service {

	private final static String MAPPING_FILE_TEMP = "mapping_file_temp";
	private final static String MAPPING_FILE = "mapping_file";
	private final static String TOTAL_RECORD = "total_record";
	private final static String MAPPING_VERSION = "mapping_version";
	private final static String MAPPING_LOADING = "mapping_loading";

	private NotificationManager notificationMgr;

	private LimeDB db = null;

	public class DBServiceImpl extends IDBService.Stub {

		Context ctx = null;

		DBServiceImpl(Context ctx) {
			this.ctx = ctx;
		}

		public void loadMapping(String filename) throws RemoteException {

			// Start Loading
			if (db == null) {
				db = new LimeDB(ctx);
			}

			File sourcefile = new File(filename);

			String secret = sourcefile.getName();

			SharedPreferences sourceset = ctx.getSharedPreferences(MAPPING_FILE, 0);
			String sourcestatus = sourceset.getString(MAPPING_FILE, "");

			SharedPreferences importset = ctx.getSharedPreferences(MAPPING_LOADING, 0);
			String importstatus = importset.getString(MAPPING_LOADING, "no");

			db.deleteAll();
			/**
			if (importstatus.equals("no") && sourcestatus.equals("")) {
				
			}else if (importstatus.equals("no") && !sourcestatus.equals("") && !sourcestatus.equals(secret)) {
				db.deleteAll();
			}else if (importstatus.equals("yes")){
				db.deleteAll();
			}**/
			
			sourceset.edit().putString(MAPPING_FILE, secret).commit();

			SharedPreferences sourcetempset = ctx.getSharedPreferences(MAPPING_FILE_TEMP, 0);
			sourcetempset.edit().putString(MAPPING_FILE_TEMP, sourcefile.getName() ).commit();
			
			
			db.setFilename(sourcefile);

			displayNotificationMessage(ctx.getText(R.string.lime_setting_notification_loading)+ "");

			// Update Loading Status
			Thread thread = new Thread() {
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
			db.loadFile();
			db.close();
		}

		public void resetMapping() throws RemoteException {
			if (db == null) {
				db = new LimeDB(ctx);
			}
			db.deleteAll();
			displayNotificationMessage(ctx
					.getText(R.string.lime_setting_notification_mapping_reset)
					+ "");
			db.close();
		}

		public void restoreRelatedUserdic() throws RemoteException {
			if (db == null) {
				db = new LimeDB(ctx);
			}
			db.deleteDictionaryAll();
			db.restoreRelatedUserdic();
			displayNotificationMessage(ctx
					.getText(R.string.lime_setting_notification_userdic_restore)
					+ "");
			db.close();
		}

		public void resetUserBackup() throws RemoteException {
			if (db == null) {
				db = new LimeDB(ctx);
			}
			db.deleteDictionaryAll();
			displayNotificationMessage(ctx
					.getText(R.string.lime_setting_notification_userdic_reset)
					+ "");
			db.close();
		}

		public void executeUserBackup() throws RemoteException {
			if (db == null) {
				db = new LimeDB(ctx);
			}
			db.backupRelatedUserdic();
			displayNotificationMessage(ctx
					.getText(R.string.lime_setting_notification_userdic_backup)
					+ "");
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
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,new Intent(this, LIMEMenu.class), 0);
		notification.setLatestEventInfo(this, this .getText(R.string.ime_setting), message, contentIntent);
		notificationMgr.notify(0, notification);
	}

}
