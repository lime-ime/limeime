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

import java.io.File;

import net.toload.main.hd.R;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEPreferenceManager;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

/**
 * 
 * @author Art Hung
 * 
 */
public class LIMEIMSetting extends Activity {
	
	final static String TAG = "LIMESetting";
	final static boolean DEBUG = false;
	
	Button btnSetupCustom = null;
	Button btnSetupPhonetic = null;
	Button btnSetupCJ = null;
	Button btnSetupSCJ= null;
	Button btnSetupCJ5= null;
	Button btnSetupECJ= null;
	Button btnSetupDayi = null;
	Button btnSetupEz = null;
	Button btnSetupArray = null;
	Button btnSetupArray10 = null;
	Button btnSetupWb = null;
	Button btnSetupHs = null;
	
	String table = "";

	
	LIMEPreferenceManager mLIMEPref;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle icicle) {
		
		super.onCreate(icicle);
		

			this.setContentView(R.layout.imsetting);
			mLIMEPref = new LIMEPreferenceManager(this.getApplicationContext());
			// Initial Buttons
			initialButton();
			
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
	
			btnSetupCJ5.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					Intent intent = new Intent();
					intent.setClass(v.getContext(), LIMEMappingSetting.class);
					   Bundle bundle = new Bundle();
					   		  bundle.putString("keyboard", "cj5");
					   intent.putExtras(bundle);
					startActivity(intent);
				}
			});
	
			btnSetupECJ.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					Intent intent = new Intent();
					intent.setClass(v.getContext(), LIMEMappingSetting.class);
					   Bundle bundle = new Bundle();
					   		  bundle.putString("keyboard", "ecj");
					   intent.putExtras(bundle);
					startActivity(intent);
				}
			});
	
			btnSetupWb.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					Intent intent = new Intent();
					intent.setClass(v.getContext(), LIMEMappingSetting.class);
					   Bundle bundle = new Bundle();
					   		  bundle.putString("keyboard", "wb");
					   intent.putExtras(bundle);
					startActivity(intent);
				}
			});

			
			btnSetupHs.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					Intent intent = new Intent();
					intent.setClass(v.getContext(), LIMEMappingSetting.class);
					   Bundle bundle = new Bundle();
					   		  bundle.putString("keyboard", "hs");
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
		if(DEBUG)
			Log.i(TAG,"onResume()");
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
			btnSetupCJ5 = (Button) findViewById(R.id.btnSetupCJ5);
			btnSetupECJ = (Button) findViewById(R.id.btnSetupECJ);
			btnSetupDayi = (Button) findViewById(R.id.btnSetupDayi);
			btnSetupEz = (Button) findViewById(R.id.btnSetupEz);		
			btnSetupArray = (Button) findViewById(R.id.btnSetupArray);
			btnSetupArray10 = (Button) findViewById(R.id.btnSetupArray10);
			btnSetupWb = (Button) findViewById(R.id.btnSetupWb);			
			btnSetupHs = (Button) findViewById(R.id.btnSetupHs);	
		}
		
		String dbtarget = mLIMEPref.getParameterString("dbtarget");
		if(dbtarget.equals("")){
			dbtarget = "device";
			mLIMEPref.setParameter("dbtarget","device");
		}
		File checkSdFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + LIME.DATABASE_NAME);
		File checkDbFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER + File.separator + LIME.DATABASE_NAME);
		if((!checkSdFile.exists() && dbtarget.equals("sdcard") )  
				|| ( !checkDbFile.exists()) && dbtarget.equals("device")) {
			btnSetupCustom.setEnabled(false);
			btnSetupPhonetic.setEnabled(false);
			btnSetupCJ.setEnabled(false);
			btnSetupSCJ.setEnabled(false);
			btnSetupCJ5.setEnabled(false);
			btnSetupECJ.setEnabled(false);
			btnSetupDayi.setEnabled(false);
			btnSetupEz.setEnabled(false);
			btnSetupArray.setEnabled(false);
			btnSetupArray10.setEnabled(false);
			btnSetupWb.setEnabled(false);
			btnSetupHs.setEnabled(false);
		}else{
			btnSetupCustom.setEnabled(true);
			btnSetupPhonetic.setEnabled(true);
			btnSetupCJ.setEnabled(true);
			btnSetupSCJ.setEnabled(true);
			btnSetupCJ5.setEnabled(true);
			btnSetupECJ.setEnabled(true);
			btnSetupDayi.setEnabled(true);
			btnSetupEz.setEnabled(true);	
			btnSetupArray.setEnabled(true);
			btnSetupArray10.setEnabled(true);
			btnSetupWb.setEnabled(true);
			btnSetupHs.setEnabled(true);
		}
		
	}
	/* move to LIMEMENU
	@Override
    public boolean onCreateOptionsMenu(Menu menu){
    	int idGroup = 0;
    	int orderMenuItem1 = Menu.NONE;
    	int orderMenuItem2 = Menu.NONE+1;
    	int orderMenuItem3 = Menu.NONE+2;
    	
    	try {
			PackageInfo pinfo = this.getPackageManager().getPackageInfo(this.getPackageName(), 0);
	    	menu.add(idGroup, Menu.FIRST, orderMenuItem1, "LIME v" + pinfo.versionName + " - " + pinfo.versionCode);
	    	menu.add(idGroup, Menu.FIRST+1, orderMenuItem2, R.string.experienced_device);
	    	menu.add(idGroup, Menu.FIRST+2, orderMenuItem3, R.string.license);
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
		    		new AlertDialog.Builder(this)
				    	.setTitle(R.string.experienced_device)
				    	.setMessage(R.string.ad_zippy)
				    	.setNeutralButton("Close", new DialogInterface.OnClickListener() {
				    	public void onClick(DialogInterface dlg, int sumthin) {
				    	}
				    	}).show();
		    		break;
		    	case (Menu.FIRST+2):
		    		new AlertDialog.Builder(this)
				    	.setTitle(R.string.license)
				    	.setMessage(R.string.license_detail)
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
    */

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if(DEBUG)
			Log.i(TAG,"onTouchEvent()");
		return super.onTouchEvent(event);
	}
	
}