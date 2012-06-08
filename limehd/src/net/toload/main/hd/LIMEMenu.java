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
import net.toload.main.hd.R;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.global.LIMEUtilities;
import net.toload.main.hd.limesettings.LIMEIMSetting;
import net.toload.main.hd.limesettings.LIMEInitial;
import net.toload.main.hd.limesettings.LIMEPreference;
import net.toload.main.hd.limesettings.LIMEPreferenceHC;

import android.app.AlertDialog;
import android.app.TabActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TabHost;
import android.widget.Toast;

/**
 * @author Art Hung
 */
@SuppressWarnings("deprecation")
public class LIMEMenu extends TabActivity {

	private LIMEPreferenceManager mLIMEPref;
	private final String TAG = "LIMEMenu";
	private final boolean DEBUG = true;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		mLIMEPref = new LIMEPreferenceManager(this);
		
		
        final TabHost tabHost = getTabHost();

        //int tabno = 0;
   
		File checkSdFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + LIME.DATABASE_NAME);
		File checkDbFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER + File.separator + LIME.DATABASE_NAME);
		final boolean dbFileNotExists =  !checkSdFile.exists() && !checkDbFile.exists();
		//if(!checkSdFile.exists() && !checkDbFile.exists())
		//	tabno = 2;
		
		
        tabHost.addTab(tabHost.newTabSpec("tab1")
        		.setIndicator(this.getText(R.string.l3_tab_manage))
                .setContent(new Intent(this, LIMEIMSetting.class)));

        if(android.os.Build.VERSION.SDK_INT < 11){  //Jeremy '12,4,30 Add for deprecated preferenceActivity after API 11 (HC)
        	tabHost.addTab(tabHost.newTabSpec("tab2")
        			.setIndicator(this.getText(R.string.l3_tab_preference))
        			.setContent(new Intent(this, LIMEPreference.class)));
        }else{
        	tabHost.addTab(tabHost.newTabSpec("tab2")
        			.setIndicator(this.getText(R.string.l3_tab_preference))
        			.setContent(new Intent(this, LIMEPreferenceHC.class)));

        }

        tabHost.addTab(tabHost.newTabSpec("tab3")
        		.setIndicator(this.getText(R.string.l3_tab_initial))
                .setContent(new Intent(this, LIMEInitial.class)));
        /*
        tabHost.addTab(tabHost.newTabSpec("tab4")
        		.setIndicator(this.getText(R.string.l3_tab_bluetooth))
                .setContent(new Intent(this, LIMEBluetooth.class)));*/

        
        
        
        try {
			PackageInfo pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			if(mLIMEPref.getParameterString("version_code").equals("") ||
					!mLIMEPref.getParameterString("version_code").equals(String.valueOf(pinfo.versionCode))){
	
    				mLIMEPref.setParameter("version_code", String.valueOf(pinfo.versionCode));
	    			new AlertDialog.Builder(this)
			    	.setTitle("LIME v" + pinfo.versionName + " - " + pinfo.versionCode)
			    	.setMessage(R.string.release_note)
			    	.setNeutralButton("Close", new DialogInterface.OnClickListener() {
			    	public void onClick(DialogInterface dlg, int sumthin) {
			    		//jeremy '12,5,1 if update dialog is shown wait until use press closed button and then switch to the db intial tab if db file is not exist
			    		if(dbFileNotExists) tabHost.setCurrentTab(2);
			    	}
			    	}).show();
	    			 
			}else  //jeremy '12,5,1 if update dialog not shown switch to the db intial tab if db file is not exist 
				if(dbFileNotExists) tabHost.setCurrentTab(2);
			
		} catch (Exception e) {
			mLIMEPref.setParameter("version_code", "0");
			e.printStackTrace();
		}
		
       
     
        
    }
    

    
    @SuppressWarnings("unused")
	private void checkIfLIMEEnabledAndActive(){

         
         if(DEBUG)
         	Log.i(TAG, "LIMEEnabled:" + LIMEUtilities.isLIMEEnabled(this) + " LIMEActive:" + LIMEUtilities.isLIMEActive(this));
         if(!LIMEUtilities.isLIMEEnabled(this)){
         	Log.i(TAG, "LIME-HD is not enabled, call showInputMethodSettingsPage() and ask user to enable it");
         	Toast.makeText(this, "LIME-HD is not enabled, please enable it and press back to go back to LIME-HD settings.", Toast.LENGTH_SHORT ).show();
         	LIMEUtilities.showInputMethodSettingsPage(this);
         }
         if(LIMEUtilities.isLIMEEnabled(this) && !LIMEUtilities.isLIMEActive(this)){
         	Log.i(TAG, "LIME-HD is not active, call showInputMethodPicker() and ask user to select it");
         	Toast.makeText(this, "LIME-HD is not active, please select it and press back to go back to LIME-HD settings.", Toast.LENGTH_SHORT ).show();
         	LIMEUtilities.showInputMethodPicker(this);
         }
    }
   
    
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

		//boolean hasSwitch = false;
		try{
	    	switch(item.getItemId()){
	    		case (Menu.FIRST):
	    			PackageInfo pinfo = this.getPackageManager().getPackageInfo(this.getPackageName(), 0);
	    		 	
		    		new AlertDialog.Builder(this)
				    	.setTitle("LIME v" + pinfo.versionName + " - " + pinfo.versionCode)
				    	.setMessage(R.string.release_note)
				    	.setNeutralButton("Close", new DialogInterface.OnClickListener() {
				    	public void onClick(DialogInterface dlg, int sumthin) {
				    	}
				    	}).show();
				    	
	    		break;
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
	
}
