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

import net.toload.main.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;


/**
 * 
 * @author Art Hung
 * 
 */
public class LIMEInitial extends Activity {
	

	private IDBService DBSrv = null;
	Button btnInitPreloadDB = null;
	Button btnResetDB = null;
	Button btnBackupDB = null;
	Button btnRestoreDB = null;
	
	boolean hasReset = false;
	LIMEPreferenceManager mLIMEPref;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle icicle) {
		
		super.onCreate(icicle);
		this.setContentView(R.layout.initial);

		// Startup Service
		getApplicationContext().bindService(new Intent(IDBService.class.getName()), serConn, Context.BIND_AUTO_CREATE);
		mLIMEPref = new LIMEPreferenceManager(this.getApplicationContext());
		
		// Initial Buttons
		initialButton();

		btnResetDB.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				
				// Reset Table Information
				resetLabelInfo("custom");
				resetLabelInfo("cj");
				resetLabelInfo("scj");
				resetLabelInfo("array");
				resetLabelInfo("dayi");
				resetLabelInfo("ez");
				resetLabelInfo("phonetic");
				
				btnResetDB.setEnabled(false);
				
				AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
		    	     				builder.setMessage("Would you like to reset database?");
		    	     				builder.setCancelable(false);
		    	     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
		    	     					public void onClick(DialogInterface dialog, int id) {
						    					getSharedPreferences(LIME.DATABASE_DOWNLOAD_STATUS, 0).edit().putString(LIME.DATABASE_DOWNLOAD_STATUS, "false").commit();
						    					initialButton();
							    				try {
							    					DBSrv.resetDownloadDatabase();
							    				} catch (RemoteException e) {
							    					e.printStackTrace();
							    				}
							    				hasReset = true;
						    	        	}
						    	     });
		    	        
						    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
						    	    	public void onClick(DialogInterface dialog, int id) {
						    	        		hasReset = false;
						    	        	}
						    	     });   
		    	        
				AlertDialog alert = builder.create();
		    				alert.show();
		    	    
				btnResetDB.setEnabled(true);
			}
		});
		

		btnInitPreloadDB.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				try {
					initialButton();
					Toast.makeText(v.getContext(), "Download Preloaded Database", Toast.LENGTH_LONG).show();
					DBSrv.downloadPreloadedDatabase();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
		});
		
		
		btnBackupDB.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				try {
					File srcFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER + File.separator + LIME.DATABASE_NAME);
					if(srcFile.exists() && srcFile.length() > 1024){
						Toast.makeText(v.getContext(), "Backup LIME Database", Toast.LENGTH_LONG).show();
						DBSrv.backupDatabase();
					}else{
						Toast.makeText(v.getContext(), "You don have database to be backup.", Toast.LENGTH_LONG).show();
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
		});
		
		btnRestoreDB.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				try {
					File srcFile = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator + LIME.DATABASE_BACKUP_NAME);
					if(srcFile.exists() && srcFile.length() > 1024){
						Toast.makeText(v.getContext(), "Restore LIME Database", Toast.LENGTH_LONG).show();
						DBSrv.restoreDatabase();
					}else{
						Toast.makeText(v.getContext(), "You don't have backup to be restore.", Toast.LENGTH_LONG).show();
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
		});
		
		

	}

	
	/* (non-Javadoc)
	 * @see android.app.Activity#onStart()
	 */
	@Override
	protected void onStart() {
		super.onStart();
		initialButton();
	}
	

	/* (non-Javadoc)
	 * @see android.app.Activity#onStart()
	 */
	@Override
	protected void onResume() {
		super.onStart();
		initialButton();
	}

	public void resetLabelInfo(String imtype){
		mLIMEPref.setParameter(imtype+LIME.IM_MAPPING_FILENAME,"");
		mLIMEPref.setParameter(imtype+LIME.IM_MAPPING_VERSION,"");
		mLIMEPref.setParameter(imtype+LIME.IM_MAPPING_TOTAL,0);
		mLIMEPref.setParameter(imtype+LIME.IM_MAPPING_DATE,"");
	}
	
	
	private void initialButton(){

		// Check if button 
		if(btnResetDB == null){
			btnResetDB = (Button) findViewById(R.id.btnResetDB);
			btnInitPreloadDB = (Button) findViewById(R.id.btnInitPreloadDB);	
			btnBackupDB = (Button) findViewById(R.id.btnBackupDB);
			btnRestoreDB = (Button) findViewById(R.id.btnRestoreDB);	
		}
		
		SharedPreferences sp = getSharedPreferences(LIME.DATABASE_DOWNLOAD_STATUS, 0);
		if(sp.getString(LIME.DATABASE_DOWNLOAD_STATUS, "false").equals("false")){
			btnInitPreloadDB.setEnabled(true);
		}else{
			btnInitPreloadDB.setEnabled(false);
		}
	}

	private ServiceConnection serConn = new ServiceConnection() {
		public void onServiceConnected(ComponentName name, IBinder service) {
			if(DBSrv == null){
				Log.i("ART","Start up db service");
				DBSrv = IDBService.Stub.asInterface(service);
			}else{
				Log.i("ART","Stop up db service");
			}
		}
		public void onServiceDisconnected(ComponentName name) {}

	};
	

}