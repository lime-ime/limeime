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

import net.toload.main.hd.R;

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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
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
public class LIMEIMSetting extends Activity {
	
	Button btnSetupCustom = null;
	Button btnSetupPhonetic = null;
	Button btnSetupCJ = null;
	Button btnSetupSCJ= null;
	Button btnSetupDayi = null;
	Button btnSetupEz = null;
	Button btnSetupArray = null;
	Button btnSetupArray10 = null;
	
	String table = "";
	
	LIMEPreferenceManager mLIMEPref;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle icicle) {
		
		super.onCreate(icicle);
		

			this.setContentView(R.layout.imsetting);
	
			// Initial Buttons
			initialButton();
			
			mLIMEPref = new LIMEPreferenceManager(this.getApplicationContext());
			
	
			btnSetupCustom.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					Intent intent = new Intent();
					intent.setClass(v.getContext(), LIMEMappingSetting.class);
					   Bundle bundle = new Bundle();
					   		  bundle.putString("keyboard", "custom");
					   intent.putExtras(bundle);
					startActivity(intent);
				}
			});
	
	
			btnSetupPhonetic.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					Intent intent = new Intent();
					intent.setClass(v.getContext(), LIMEMappingSetting.class);
					   Bundle bundle = new Bundle();
					   		  bundle.putString("keyboard", "phonetic");
					   intent.putExtras(bundle);
					startActivity(intent);
				}
			});
	
	
			btnSetupCJ.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					Intent intent = new Intent();
					intent.setClass(v.getContext(), LIMEMappingSetting.class);
					   Bundle bundle = new Bundle();
					   		  bundle.putString("keyboard", "cj");
					   intent.putExtras(bundle);
					startActivity(intent);
				}
			});
			
	
	
			btnSetupSCJ.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					Intent intent = new Intent();
					intent.setClass(v.getContext(), LIMEMappingSetting.class);
					   Bundle bundle = new Bundle();
					   		  bundle.putString("keyboard", "scj");
					   intent.putExtras(bundle);
					startActivity(intent);
				}
			});
			
	
	
			btnSetupDayi.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					Intent intent = new Intent();
					intent.setClass(v.getContext(), LIMEMappingSetting.class);
					   Bundle bundle = new Bundle();
					   		  bundle.putString("keyboard", "dayi");
					   intent.putExtras(bundle);
					startActivity(intent);
				}
			});
	
			btnSetupArray.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					Intent intent = new Intent();
					intent.setClass(v.getContext(), LIMEMappingSetting.class);
					   Bundle bundle = new Bundle();
					   		  bundle.putString("keyboard", "array");
					   intent.putExtras(bundle);
					startActivity(intent);
				}
			});
	
			btnSetupArray10.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					Intent intent = new Intent();
					intent.setClass(v.getContext(), LIMEMappingSetting.class);
					   Bundle bundle = new Bundle();
					   		  bundle.putString("keyboard", "array10");
					   intent.putExtras(bundle);
					startActivity(intent);
				}
			});
	
			btnSetupEz.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					Intent intent = new Intent();
					intent.setClass(v.getContext(), LIMEMappingSetting.class);
					   Bundle bundle = new Bundle();
					   		  bundle.putString("keyboard", "ez");
					   intent.putExtras(bundle);
					startActivity(intent);
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

		// Check if button 
		if(btnSetupCustom == null){
			btnSetupCustom = (Button) findViewById(R.id.btnSetupCustom);
			btnSetupPhonetic = (Button) findViewById(R.id.btnSetupPhonetic);
			btnSetupCJ = (Button) findViewById(R.id.btnSetupCJ);
			btnSetupSCJ = (Button) findViewById(R.id.btnSetupSCJ);
			btnSetupDayi = (Button) findViewById(R.id.btnSetupDayi);
			btnSetupEz = (Button) findViewById(R.id.btnSetupEz);		
			btnSetupArray = (Button) findViewById(R.id.btnSetupArray);
			btnSetupArray10 = (Button) findViewById(R.id.btnSetupArray10);	
		}

		File checkSdFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + LIME.DATABASE_NAME);
		File checkDbFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER + File.separator + LIME.DATABASE_NAME);
		if(!checkSdFile.exists() && !checkDbFile.exists()){
			btnSetupCustom.setEnabled(false);
			btnSetupPhonetic.setEnabled(false);
			btnSetupCJ.setEnabled(false);
			btnSetupSCJ.setEnabled(false);
			btnSetupDayi.setEnabled(false);
			btnSetupEz.setEnabled(false);
			btnSetupArray.setEnabled(false);
			btnSetupArray10.setEnabled(false);
		}else{
			btnSetupCustom.setEnabled(true);
			btnSetupPhonetic.setEnabled(true);
			btnSetupCJ.setEnabled(true);
			btnSetupSCJ.setEnabled(true);
			btnSetupDayi.setEnabled(true);
			btnSetupEz.setEnabled(true);	
			btnSetupArray.setEnabled(true);
			btnSetupArray10.setEnabled(true);
		}
		
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu){
    	int idGroup = 0;
    	int orderMenuItem1 = Menu.NONE;
    	int orderMenuItem2 = Menu.NONE+1;
    	
    	try {
			PackageInfo pinfo = this.getPackageManager().getPackageInfo(this.getPackageName(), 0);
	    	menu.add(idGroup, Menu.FIRST, orderMenuItem1, "LIME v" + pinfo.versionName + " - " + pinfo.versionCode);
	    	menu.add(idGroup, Menu.FIRST+1, orderMenuItem2, R.string.experienced_device);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
    	return super.onCreateOptionsMenu(menu);
    }
	
    @Override
    public boolean onOptionsItemSelected(MenuItem item){

		boolean hasSwitch = false;
		try{
	    	switch(item.getItemId()){
		    	case (Menu.FIRST+1):
		    		String message = "ÂÅªÞÁä½L´ú¸Õ¤ä´©\n";
			 			   message += "ZIPPY BT-540 ÂÅªÞ¤@¹ï¤»Áä½L - http://www.zippy.com (Sam Lin/sam_lin@hotmail.com)";
			 			   
		    		new AlertDialog.Builder(this)
				    	.setTitle(R.string.experienced_device)
				    	.setMessage(message)
				    	.setNeutralButton("Close", new DialogInterface.OnClickListener() {
				    	public void onClick(DialogInterface dlg, int sumthin) {
				    	}
				    	}).show();
		    		break;
	    	}
    	}catch(Exception e){
    		e.printStackTrace();
    	}
		return super.onOptionsItemSelected(item);
    }
	
}