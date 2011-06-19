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

package net.toload.main.hd;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.toload.main.hd.IDBService;
import net.toload.main.hd.ISearchService;
import net.toload.main.hd.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * 
 * @author Art Hung
 * 
 */

public class LIMEMappingSetting extends Activity {
	

		private AlertDialog ad;
		private ArrayList<File> filelist;
		private boolean hasSelectFile;
		
		private IDBService DBSrv = null;
		private ISearchService SearchSrv = null;

		Button btnBackToPreviousPage = null;
		Button btnLoadMapping = null;
		Button btnResetMapping = null;
		Button btnSelectKeyboard = null;
		
		TextView labSource = null;
		TextView labVersion = null;
		TextView labTotalAmount = null;
		TextView labImportDate = null;
		TextView labMappingSettingTitle = null;
		TextView labKeyboard = null;
		private ScrollView scrollSetting;
		
		LinearLayout extendLayout = null;
		Button extendButton = null;
		
		private String imtype = null;
		List<KeyboardObj> kblist = null;
		
		LIMEPreferenceManager mLIMEPref;
		
		private AlertDialog mOptionsDialog;
		
		/** Called when the activity is first created. */
		@Override
		public void onCreate(Bundle icicle) {
			
			super.onCreate(icicle);
			this.setContentView(R.layout.kbsetting);


			// Startup Service
			getApplicationContext().bindService(new Intent(IDBService.class.getName()), serConn, Context.BIND_AUTO_CREATE);
			getApplicationContext().bindService(new Intent(ISearchService.class.getName()), serConn2, Context.BIND_AUTO_CREATE);
			

			mLIMEPref = new LIMEPreferenceManager(this.getApplicationContext());
			
	        try{
		        Bundle bundle = this.getIntent().getExtras();
		        if(bundle != null){
		        	imtype = bundle.getString("keyboard");
		        }
	        }catch(Exception e){
	        	e.printStackTrace();
	        }

	        String im_loading_table = mLIMEPref.getParameterString("im_loading_table");
	        if(im_loading_table != null && im_loading_table.length() > 0){
	        	imtype = im_loading_table;
	        }
	        
			// Initial Buttons
			initialButton();
			
			// Setup Extended Button

			extendLayout = (LinearLayout) findViewById(R.id.extendLayout);
			if(imtype != null && imtype.equals("dayi")){
				Button extendButton = new Button(this);
				extendButton.setText(getResources().getString(R.string.l3_im_download_from_ov));
				extendLayout.addView(extendButton);

				extendButton.setOnClickListener(new OnClickListener() {
					public void onClick(View v) {
						try {
							hasSelectFile = true;
							resetLabelInfo();
    						DBSrv.resetMapping("dayi");
							DBSrv.downloadDayiOvCin();
							mLIMEPref.setParameter("im_loading", true);
							mLIMEPref.setParameter("im_loading_table", imtype);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
					}
				});
				
			}else{
				extendLayout.removeView(extendButton);
			}

			scrollSetting.setOnTouchListener(new OnTouchListener(){
				@Override
				public boolean onTouch(View v, MotionEvent event) {
					updateLabelInfo();
					return false;
				}
			});
			
			btnSelectKeyboard.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					showKeyboardPicker();
				}
			});
			
			btnBackToPreviousPage.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					finish();
				}
			});

			
			btnLoadMapping.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					hasSelectFile = false;
                	selectLimeFile(LIME.IM_LOAD_LIME_ROOT_DIRECTORY, imtype);
				}
			});

			
			btnResetMapping.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					
					AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
     				builder.setMessage(getText(R.string.l3_message_table_reset_confirm));
     				builder.setCancelable(false);
     				builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
     					public void onClick(DialogInterface dialog, int id) {
		    					initialButton();
		    					try {
		    						resetLabelInfo();
		    						updateLabelInfo();
		    						DBSrv.resetMapping(imtype);
		    						mLIMEPref.setParameter("im_loading", false);
		    						mLIMEPref.setParameter("im_loading_table", "");
		    						btnLoadMapping.setEnabled(true);
		    					} catch (RemoteException e) {
		    						e.printStackTrace();
		    					}
		    	        	}
		    	     });
        
		    	    builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
		    	    	public void onClick(DialogInterface dialog, int id) {
		    	        	}
		    	     });   
        
					AlertDialog alert = builder.create();
								alert.show();
								
					
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
		
		
		private void initialButton(){

			btnBackToPreviousPage = (Button) findViewById(R.id.btnBackToPreviousPage);
			btnLoadMapping =  (Button) findViewById(R.id.btnLoadMapping);
			btnResetMapping =  (Button) findViewById(R.id.btnResetMapping);
			btnSelectKeyboard = (Button) findViewById(R.id.btnSelectKeyboard);

			labSource = (TextView) findViewById(R.id.labSource);	
			labVersion = (TextView) findViewById(R.id.labVersion);	
			labTotalAmount = (TextView) findViewById(R.id.labTotalAmount);	
			labImportDate = (TextView) findViewById(R.id.labImportDate);
			labMappingSettingTitle = (TextView) findViewById(R.id.labMappingSettingTitle);
			labKeyboard = (TextView) findViewById(R.id.labKeyboard);
			
			scrollSetting = (ScrollView) this.findViewById(R.id.IMSettingScrollView);
			
			if(mLIMEPref.getParameterBoolean("im_loading") == true){
				btnLoadMapping.setEnabled(false);
			}
			
		}

		public void resetLabelInfo(){
				labSource.setText("");
				labVersion.setText("");
				labTotalAmount.setText("");
				labImportDate.setText("");
				labKeyboard.setText("");
		}
		
		public void updateLabelInfo(){
			
			if(mLIMEPref.getParameterBoolean("im_loading") == true){
				labSource.setText("Loading...");
				labVersion.setText("Loading...");
				labTotalAmount.setText("Loading...");
				labImportDate.setText("Loading...");
			}else{
				try{
					labSource.setText(DBSrv.getImInfo(imtype, "source"));
					labVersion.setText(DBSrv.getImInfo(imtype, "name"));
					labTotalAmount.setText(DBSrv.getImInfo(imtype, "amount"));
					labImportDate.setText(DBSrv.getImInfo(imtype, "import"));
				}catch(Exception e){
					e.printStackTrace();
				}
			}
			
			if(imtype.equalsIgnoreCase("cj")){
				labMappingSettingTitle.setText(getText(R.string.l3_manage_cj) +" "+ getText(R.string.l3_im_setting_title) );
			}else if(imtype.equalsIgnoreCase("scj")){
				labMappingSettingTitle.setText(getText(R.string.l3_manage_scj) +" "+ getText(R.string.l3_im_setting_title) );
			}else if(imtype.equalsIgnoreCase("ez")){
				labMappingSettingTitle.setText(getText(R.string.l3_manage_eazy) +" "+ getText(R.string.l3_im_setting_title) );
			}else if(imtype.equalsIgnoreCase("array")){
				labMappingSettingTitle.setText(getText(R.string.l3_manage_array) +" "+ getText(R.string.l3_im_setting_title) );
			}else if(imtype.equalsIgnoreCase("array10")){
				labMappingSettingTitle.setText(getText(R.string.l3_manage_array10) +" "+ getText(R.string.l3_im_setting_title) );
			}else if(imtype.equalsIgnoreCase("dayi")){
				labMappingSettingTitle.setText(getText(R.string.l3_manage_dayi) +" "+ getText(R.string.l3_im_setting_title) );
			}else if(imtype.equalsIgnoreCase("custom")){
				labMappingSettingTitle.setText(getText(R.string.l3_manage_default) +" "+ getText(R.string.l3_im_setting_title) );
			}else if(imtype.equalsIgnoreCase("phonetic")){
				labMappingSettingTitle.setText(getText(R.string.l3_manage_phonetic) +" "+ getText(R.string.l3_im_setting_title) );
			}
			
			// Display Keyboard Selection
			try {
				labKeyboard.setText(DBSrv.getImInfo(imtype, "keyboard"));
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			
		}

		private ServiceConnection serConn = new ServiceConnection() {
			public void onServiceConnected(ComponentName name, IBinder service) {
				//Log.i("ART","DB Service connected!");
				if(DBSrv == null){
					DBSrv = IDBService.Stub.asInterface(service);
					updateLabelInfo();
				}
			}
			public void onServiceDisconnected(ComponentName name) {
				//Log.i("ART","DB Service disconnected!");
			}
		};

		/*
		 * Construct SerConn
		 */
		private ServiceConnection serConn2 = new ServiceConnection() {
			public void onServiceConnected(ComponentName name, IBinder service) {
				SearchSrv = ISearchService.Stub.asInterface(service);
				try {
					SearchSrv.clear();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}

			public void onServiceDisconnected(ComponentName name) {
			}
		};
		
		/**
		 * Select file to be import from the list
		 * 
		 * @param path
		 */
		public void selectLimeFile(String srcpath, String tablename) {

			// Retrieve Filelist
			filelist = getAvailableFiles(srcpath, tablename);

			ArrayList<String> showlist = new ArrayList<String>();

			int count = 0;
			for (File unit : filelist) {
				if (count == 0) {
					showlist.add(this.getResources().getText(
							R.string.lime_setting_select_root).toString());
					count++;
					continue;
				} else if (count == 1) {
					showlist.add(this.getResources().getText(
							R.string.lime_setting_select_parent).toString());
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
			final String table = new String(tablename);
			ListView listview = (ListView) view.findViewById(R.id.list);
			
			listview.setAdapter(adapterlist);
			listview.setOnItemClickListener(new OnItemClickListener() {
				public void onItemClick(AdapterView<?> arg0, View vi, int position,
						long id) {
					selectLimeFile(filelist.get(position).getAbsolutePath(), table);
				}
			});
			
			// if AlertDialog exists then dismiss before create
			if (ad != null) {
				ad.dismiss();
			}

			if (!hasSelectFile) {
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(R.string.lime_setting_btn_load_local_notice);
				builder.setView(view);
				builder.setNeutralButton(R.string.label_close_key,
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
		 * 
		 * @param path
		 * @return
		 */
		private ArrayList<File> getAvailableFiles(String path, String tablename) {

			ArrayList<File> templist = new ArrayList<File>();

			File check = new File(path);
			
			if (check.exists() && check.isDirectory()) {

				File root = null;
				root = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY);

				// Fixed first 1 & 2
				templist.add(root);

				// Back to Parent
				if (check.getParentFile().getAbsolutePath().equals("/")) {
					templist.add(root);
				} else {
					templist.add(check.getParentFile());
				}

				File rootPath = new File(path);
				File list[] = rootPath.listFiles();
				for (File unit : list) {
					if (unit.isDirectory()
							|| (unit.isFile() && unit.getName().toLowerCase().endsWith(".lime"))
							|| (unit.isFile() && unit.getName().toLowerCase().endsWith(".cin"))) {
						templist.add(unit);
					}
				}

			} else { //if (check.exists() && check.isFile()
					//&& (  true || check.getName().toLowerCase().endsWith(".lime") || check.getName().toLowerCase().endsWith(".cin"))  ) {
				//Log.i("ART","run load mapping method : " + imtype);
				hasSelectFile = true;
				loadMapping(check);
				resetLabelInfo();
				mLIMEPref.setParameter("im_loading", true);
				mLIMEPref.setParameter("im_loading_table", imtype);
			}
			return templist;
		}
		
		/**
		 * Import mapping table into database
		 * 
		 * @param unit
		 */
		public void loadMapping(File unit) {
			try {
				DBSrv.loadMapping(unit.getAbsolutePath(), imtype);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		
		/*
		 * Show keyboard picker
		 */
		@SuppressWarnings("unchecked")
		private void showKeyboardPicker() {

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setCancelable(true);
			builder.setIcon(R.drawable.sym_keyboard_done);
			builder.setNegativeButton(android.R.string.cancel, null);
			builder.setTitle(getResources().getString(R.string.keyboard_list));

			try {
				kblist = DBSrv.getKeyboardList();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			
			if(kblist != null){
				String curSelectKb = "";
				int curSelectKbPosition = 0;
				
				try {
					curSelectKb = DBSrv.getKeyboardCode(imtype);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
				
				CharSequence[] items = new CharSequence[kblist.size()];
				for (int i = 0; i < kblist.size(); i++) {
					items[i] = kblist.get(i).getDescription();
					
					// Check if keyboard has been set, then record the position of item.
					if(kblist.get(i).getCode() != null &&
							curSelectKb != null && kblist.get(i).getCode().equals(curSelectKb)){
						curSelectKbPosition = i;
					}
				}
				
				builder.setSingleChoiceItems(items, curSelectKbPosition,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface di, int position) {
							di.dismiss();
							handlKeyboardSelection(position);
						}
				});
	
				mOptionsDialog = builder.create();
				//Window window = mOptionsDialog.getWindow();
				mOptionsDialog.show();
			}
		}
		
		private void handlKeyboardSelection(int position) {
			KeyboardObj kobj = kblist.get(position);
			try {
				DBSrv.setIMKeyboard(imtype, kobj.getDescription(), kobj.getCode());
				labKeyboard.setText(DBSrv.getImInfo(imtype, "keyboard"));
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			
		}

}