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
import net.toload.main.hd.limesettings.LIMEIMSetting;
import net.toload.main.hd.limesettings.LIMEInitial;
import net.toload.main.hd.limesettings.LIMEPreference;

import android.app.AlertDialog;
import android.app.TabActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TabHost;

/**
 * @author Art Hung
 */
public class LIMEMenu extends TabActivity {
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        final TabHost tabHost = getTabHost();

        int tabno = 0;

		File checkSdFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + LIME.DATABASE_NAME);
		File checkDbFile = new File(LIME.DATABASE_DECOMPRESS_FOLDER + File.separator + LIME.DATABASE_NAME);
		if(!checkSdFile.exists() && !checkDbFile.exists()){
			tabno = 2;
		}
		
        tabHost.addTab(tabHost.newTabSpec("tab1")
        		.setIndicator(this.getText(R.string.l3_tab_manage))
                .setContent(new Intent(this, LIMEIMSetting.class)));
        
        tabHost.addTab(tabHost.newTabSpec("tab2")
        		.setIndicator(this.getText(R.string.l3_tab_preference))
        		.setContent(new Intent(this, LIMEPreference.class)));

        tabHost.addTab(tabHost.newTabSpec("tab3")
        		.setIndicator(this.getText(R.string.l3_tab_initial))
                .setContent(new Intent(this, LIMEInitial.class)));
        /*
        tabHost.addTab(tabHost.newTabSpec("tab4")
        		.setIndicator(this.getText(R.string.l3_tab_bluetooth))
                .setContent(new Intent(this, LIMEBluetooth.class)));*/

        if(tabno != 0){
            tabHost.setCurrentTab(tabno);
        }
        
       /* WindowManager manager = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        
        Log.i("ART",  display.getWidth() + " * "+ display.getHeight());*/
        // 09-16 15:53:47.042: INFO/ART(257): 320 * 480 OK

        
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
