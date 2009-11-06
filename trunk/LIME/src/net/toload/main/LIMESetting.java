
/* 
**
** Copyright 2008, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**     http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing, software 
** distributed under the License is distributed on an "AS IS" BASIS, 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

package net.toload.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
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
public class LIMESetting extends Activity {

	private ArrayList<File> filelist;

	private Button btnLoadSd;
	private Button btnLoadLocal;
	private Button btnReset;
	private Button btnResetDictionary;
	private Button btnBackup;
	private Button btnRestore;

	private AlertDialog ad;

	private String localRoot = "/sdcard";
	private boolean hasSelectFile ;
	
	private static LimeDB limedb;
	
	private ProgressDialog myDialog;
	
	private File mappingSrc;
	private TextView txtAmount;
	private TextView txtDictionaryAmount;
	
	private Thread thread = null;
	private Resources res = null;
	private Context ctx = null;
	
	NotificationManager barManager ;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		this.setContentView(R.layout.main);
		
		if(res == null){
			res = this.getResources();
		}
		if(ctx == null){
			ctx = this.getApplicationContext();
		}
		
		// Handle Local Files
		btnLoadLocal = (Button) this.findViewById(R.id.btnLoadLocal);
		btnLoadLocal.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				hasSelectFile = false;
				selectLimeFile(localRoot);
			}
		});
		
		
		btnReset = (Button) this.findViewById(R.id.btnReset);
		btnReset.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {

				limedb = new LimeDB(v.getContext());	
				limedb.deleteAll();
				
				new AlertDialog.Builder(v.getContext()).setTitle(
						R.string.lime_setting_menu_about_title).setMessage(
						R.string.lime_setting_menu_reset_message).setNeutralButton(
						R.string.lime_setting_btn_close, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dlg, int sumthin) {
							}
						}).show();

				// Update Information
				updateInfomation();
			}
		});
		

		// Handle Reset Dictionary
		btnResetDictionary = (Button) this.findViewById(R.id.btnResetDictionary);
		btnResetDictionary.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {

				limedb = new LimeDB(v.getContext());	
				limedb.deleteDictionaryAll();
				
				new AlertDialog.Builder(v.getContext()).setTitle(
						R.string.lime_setting_menu_about_title).setMessage(
						R.string.lime_setting_menu_reset_dictionary_message).setNeutralButton(
						R.string.lime_setting_btn_close, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dlg, int sumthin) {
							}
						}).show();

				// Update Information
				updateInfomation();
			}
		});
		

		// Backup Related Database
		btnBackup = (Button) this.findViewById(R.id.btnBackup);
		btnBackup.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				limedb = new LimeDB(v.getContext());	
				limedb.backupRelatedDb();
				new AlertDialog.Builder(v.getContext()).setTitle(
						R.string.lime_setting_backtup_db).setMessage(
						R.string.lime_setting_backup_message).setNeutralButton(
						R.string.lime_setting_btn_close, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dlg, int sumthin) {
							}
						}).show();
			}
		});

		// Restore Related Database
		btnRestore = (Button) this.findViewById(R.id.btnRestore);
		btnRestore.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				limedb = new LimeDB(v.getContext());	
				limedb.restoreRelatedDb();
				new AlertDialog.Builder(v.getContext()).setTitle(
						R.string.lime_setting_restore_db).setMessage(
						R.string.lime_setting_restore_message).setNeutralButton(
						R.string.lime_setting_btn_close, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dlg, int sumthin) {
							}
						}).show();
				
				// Update Information
				updateInfomation();
			}
		});
		
		// Initial and Create Database
		if(limedb == null){
			limedb = new LimeDB(this);	
		}
		
		// Initial Notification Bar
		if(barManager == null){
			barManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		}
		
		// Update Information
		updateInfomation();
	}
	
	/**
	 * Update Interface Information
	 */
	public void updateInfomation(){
		try{
			limedb = new LimeDB(this);
			txtAmount = (TextView)this.findViewById(R.id.txtInfoAmount);
			txtAmount.setText(String.valueOf(limedb.countAll()));
	
			txtDictionaryAmount = (TextView)this.findViewById(R.id.txtInfoDictionaryAmount);
			txtDictionaryAmount.setText(String.valueOf(limedb.countDictionaryAll()));
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	/**
	 * Select file to be import from the list
	 * @param path
	 */
	public void selectLimeFile(String path) {

		// Retrieve Filelist
		filelist = getAvailableFiles(path);

		ArrayList<String> showlist = new ArrayList<String>();

		int count=0;
		for (File unit : filelist) {
			if(count==0){
				showlist.add(this.getResources().getText(R.string.lime_setting_select_root).toString());
				count++;
				continue;
			}else if(count==1){
				showlist.add(this.getResources().getText(R.string.lime_setting_select_parent).toString());
				count++;
				continue;
			}
			if (unit.isDirectory()) {
				showlist.add(" +[" + unit.getName() + "]");
			} else {
				showlist.add(" " + unit.getName());
			}
			count++;
		}

		// get a builder and set the view
		LayoutInflater li = LayoutInflater.from(this);
		View view = li.inflate(R.layout.filelist, null);

		ArrayAdapter<String> adapterlist = new ArrayAdapter<String>(this,
				R.layout.filerow, showlist);
		ListView listview = (ListView) view.findViewById(R.id.list);
		listview.setAdapter(adapterlist);
		listview.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> arg0, View vi, int position, long id) {
				selectLimeFile(filelist.get(position).getAbsolutePath());
			}
		});

		// if AlertDialog exists then dismiss before create
		if (ad != null) {
			ad.dismiss();
		}

		if(!hasSelectFile){
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.lime_setting_btn_load_local_notice);
			builder.setView(view);
			builder.setNeutralButton(R.string.lime_setting_btn_close,
					new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dlg, int sumthin) {
					
				}
			});
			
			ad = builder.create();
			ad.show();
		}
	}

	/**
	 * Get list of the file from the path
	 * @param path
	 * @return
	 */
	private ArrayList<File> getAvailableFiles(String path) {

		ArrayList<File> templist = new ArrayList<File>();

		File check = new File(path);
		
		if (check.exists() && check.isDirectory()) {

			File root = null;
				 root = new File(localRoot);
			
			// Fixed first 1 & 2 
			// Root
			templist.add(root);

			// Back to Parent
			if( check.getParentFile().getAbsolutePath().equals("/") ){
				templist.add(root);
			}else{
				templist.add(check.getParentFile());
			}
			
			File rootPath = new File(path);
			File list[] = rootPath.listFiles();
			for (File unit : list) {
				if(unit.isDirectory() || (unit.isFile() &&
					unit.getName().toLowerCase().endsWith(".lime"))){
					templist.add(unit);
				}
			}

		} else if (check.exists() && check.isFile()
				&& check.getName().toLowerCase().endsWith(".lime")) {
			
			hasSelectFile = true;
			loadMapping(check);		
		}
		return templist;
	}
	
	/**
	 * Import mapping table into database
	 * @param unit
	 */
	public void loadMapping(File unit){
		
		// Show Dialo
		myDialog = ProgressDialog.show(this, 
						this.getResources().getText(R.string.lime_setting_loading), 
						this.getResources().getText(R.string.lime_setting_loading_message),
						true);
		
		// Setup Notificaiton
		Notification barMsg = new Notification(
				android.R.drawable.stat_sys_download,
				this.getResources().getText(R.string.lime_setting_notification_loading),
				System.currentTimeMillis()
				);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, LIMESetting.class), 0);
		barMsg.setLatestEventInfo(this, res.getText(R.string.ime_setting), res.getText(R.string.lime_setting_notification_loading), contentIntent);
		barManager.notify(0, barMsg);
		

		limedb = new LimeDB(this);	
       	// Reset Database
       	limedb.deleteAll();
       	limedb.setFilename(unit);
       	
       	thread = new Thread(){
       		public void run(){
       			int total = 0;
				
       			
       		   	while(!limedb.isFinish()){
       		   		try {
						this.sleep(10000);
						total = limedb.getCount();
						
		       			// Notification
		       			Notification barMsg = new Notification(
		       					android.R.drawable.stat_sys_download,
		       					"LIME / " + total,
		       					System.currentTimeMillis()
		       					);
		       			PendingIntent contentIntent = PendingIntent.getActivity(ctx, 0, new Intent(ctx, LIMESetting.class), 0);
		       			barMsg.setLatestEventInfo(ctx, res.getText(R.string.ime_setting), res.getText(R.string.lime_setting_notification_loading_start) + " " + total +  " " + res.getText(R.string.lime_setting_notification_loading_end), contentIntent);
		       			barManager.notify(0, barMsg);
       		   		} catch (InterruptedException e) {
						e.printStackTrace();
					}
       		   	}
       		   	
       			// Finish Notificaiton
       			Notification barMsgEnd = new Notification(
       					android.R.drawable.stat_sys_download_done,
       					res.getText(R.string.lime_setting_notification_finish),
       					System.currentTimeMillis()
       					);
       					
       			PendingIntent contentIntent = PendingIntent.getActivity(ctx, 0, new Intent(ctx, LIMESetting.class), 0);
       			barMsgEnd.setLatestEventInfo(ctx, res.getText(R.string.ime_setting), res.getText(R.string.lime_setting_notification_finish), contentIntent);
       			barManager.notify(0, barMsgEnd);
       			uiCallback.sendEmptyMessage(0);
       		}
       	};
       	thread.start();
		
       	limedb.loadFile();

	}
	
	/**
	 * Handle myDialog activity
	 */
    private Handler uiCallback = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
			updateInfomation();
			if(myDialog != null){
				myDialog.dismiss();
			}
        }
    };
    
	public class EmptyListener implements
			android.content.DialogInterface.OnClickListener {
		public void onClick(DialogInterface v, int buttonId) {

		}
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onPause()
	 */
	@Override
	protected void onPause() {
		super.onPause();
	    uiCallback.sendEmptyMessage(0);
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onRestart()
	 */
	@Override
	protected void onRestart() {
		// TODO Auto-generated method stub
		super.onRestart();
	}
	
}