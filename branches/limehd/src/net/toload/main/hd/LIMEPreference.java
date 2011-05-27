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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;

/**
 * @author Art Hung
 */
public class LIMEPreference extends PreferenceActivity
{
	private Context ctx = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //--------------------
        //Jeremy '10, 4, 18 .  
        if (ctx == null) {
			ctx = this.getApplicationContext();
		}
        
        //-----------------------

		File srcFile = new File(LIME.IM_LOAD_LIME_ROOT_DIRECTORY + File.separator + LIME.DATABASE_BACKUP_NAME);
		File srcFile2 = new File(LIME.DATABASE_DECOMPRESS_FOLDER_SDCARD + File.separator + LIME.DATABASE_NAME);
		if((srcFile2.exists() && srcFile2.length() > 1024) || (srcFile.exists() && srcFile.length() > 1024)){
			addPreferencesFromResource(R.xml.preference);
		}else{
			addPreferencesFromResource(R.layout.error);
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