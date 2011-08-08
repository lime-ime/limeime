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

import net.toload.main.hd.LIMEMenu;
import net.toload.main.hd.R;
import net.toload.main.hd.IDBService;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 
 * @author Art Hung
 * 
 */

public class LIMEMappingLoading extends Activity {

	//private LIMEPreferenceManager mLIMEPref = null;
	private IDBService DBSrv = null;
	
	private NotificationManager notificationMgr;
	
	TextView txtLoadingStatus = null;
	Button btnCancel = null;
	ProgressBar progressBar = null;
	
	//private String imtype;
	
    final Handler mHandler = new Handler();
    // Create runnable for posting
    final Runnable mUpdateUI = new Runnable() {
        public void run() {
        	int percentageDone = 0, loadedMappingCount =0;
        	boolean remoteFileDownloading = false;
			try {
				percentageDone = DBSrv.getLoadingMappingPercentageDone();
				loadedMappingCount = DBSrv.getLoadingMappingCount();
				remoteFileDownloading = DBSrv.isRemoteFileDownloading();
			} catch (RemoteException e1) {
				e1.printStackTrace();
			}
        	txtLoadingStatus.setText( percentageDone+ "%");
        	progressBar.setProgress(percentageDone);
        	if(remoteFileDownloading){
        		setTitle(getText(R.string.l3_message_table_downloading) + " ");
        	}
        	else if(percentageDone < 50 && loadedMappingCount != 0){
        				setTitle(
        						getText(R.string.lime_setting_notification_loading_build) + " "
        						+ getText(R.string.lime_setting_notification_loading_import) + " "
        						+ loadedMappingCount + " "
        						+ getText(R.string.lime_setting_notification_loading_end) );
        	} else if(percentageDone == 100){
        		setTitle(getText(R.string.lime_setting_notification_finish) + " ");
        	} else if(percentageDone >= 50 ){
        		setTitle(getText(R.string.lime_setting_notification_related) + " ");
        	}
        }
    };

	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		// Startup Service
		getApplicationContext().bindService(new Intent(IDBService.class.getName()), serConn, Context.BIND_AUTO_CREATE);
		
		this.setContentView(R.layout.progress);
		this.setTitle(getText(R.string.lime_setting_loading)+"...");
		//mLIMEPref = new LIMEPreferenceManager(this);
		
		txtLoadingStatus = (TextView) findViewById(R.id.txtLoadingStatus);
		progressBar = (ProgressBar)findViewById(R.id.progressBar);
		btnCancel = (Button) findViewById(R.id.btn_cancel);
		
//		try{
//			Bundle bundle = this.getIntent().getExtras();
//			if(bundle != null){
//				imtype = bundle.getString("keyboard");
//			}
//		}catch(Exception e){
//			e.printStackTrace();
//		}
		
		btnCancel.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				abortConfirmDialog();
			}
		});
		
	}
	
	private void abortConfirmDialog(){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(getText(R.string.l3_message_table_abort_confirm));
			builder.setCancelable(false);
			builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					//mLIMEPref.setParameter("im_loading", false);
					//mLIMEPref.setParameter("im_loading_table", "");
					try {
						//DBSrv.resetMapping(imtype);
						DBSrv.abortRemoteFileDownload();
						DBSrv.abortLoadMapping();
					} catch (RemoteException e) {
						e.printStackTrace();
					}
					finish();
				}
					
	     });

	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
	    	public void onClick(DialogInterface dialog, int id) {
	        	}
	     });   

		AlertDialog alert = builder.create();
					alert.show();
	}
	
	
	@Override
	protected void onResume() {

		super.onResume();
		
		//mLIMEPref.setParameter("db_finish", false);
		
		Thread thread = new Thread() {
			public void run() {
				
				do{
					try {
						Thread.sleep(1000);
						if(DBSrv.isRemoteFileDownloading() ||
								DBSrv.isLoadingMappingThreadAlive() ){
							mHandler.post(mUpdateUI);			
						}else{
							if(DBSrv.isLoadingMappingFinished()){
								showNotificationMessage(getText(R.string.lime_setting_notification_finish)+ "");
							}else{
								showNotificationMessage(getText(R.string.lime_setting_notification_failed)+ "");
							}
							//mLIMEPref.setParameter("db_finish", true);
							break;
						}
					
					} catch (RemoteException e) {
						e.printStackTrace();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
				}while(true);
				finish();
			}
		};
		thread.start();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if ((keyCode == KeyEvent.KEYCODE_BACK)) {
			abortConfirmDialog();
	        return true;
	    }

		return super.onKeyDown(keyCode, event);
	}

	private ServiceConnection serConn = new ServiceConnection() {
		public void onServiceConnected(ComponentName name, IBinder service) {
			if(DBSrv == null){
				DBSrv = IDBService.Stub.asInterface(service);
			}
		}
		public void onServiceDisconnected(ComponentName name) {
		}
	};
	
	private void showNotificationMessage(String message) {
		Notification notification = new Notification(R.drawable.icon, message, System.currentTimeMillis());
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,new Intent(this, LIMEMenu.class), 0);
		notification.setLatestEventInfo(this, this .getText(R.string.ime_setting), message, contentIntent);
		if(notificationMgr == null){
			notificationMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		}
		notificationMgr.notify(0, notification);
	}

	
		
}