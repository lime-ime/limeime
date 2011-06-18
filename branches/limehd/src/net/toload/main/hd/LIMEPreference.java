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

//import java.io.File;
import net.toload.main.hd.R;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
//import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;



/**
 * @author Art Hung
 */
public class LIMEPreference extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	private final boolean DEBUG = false;
	private Context ctx = null;
	private IDBService DBSrv = null;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //--------------------
        //Jeremy '10, 4, 18 .  
        if (ctx == null) {
			ctx = this.getApplicationContext();
		}
        
        //-----------------------

		addPreferencesFromResource(R.xml.preference);
		
		
		// Startup Search Service
		if (DBSrv == null) {
			try {
				ctx.bindService(new Intent(IDBService.class.getName()),
						serConn, Context.BIND_AUTO_CREATE);
			} catch (Exception e) {
				Log.i("ART", "Failed to connect Search Service");
			}
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
    
    
    @Override
    protected void onResume() {
        super.onResume();

        // Set up a listener whenever a key changes            
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister the listener whenever a key changes            
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);    
    }

	@Override
	public void onContentChanged() {
		// TODO Auto-generated method stub
		super.onContentChanged();
	}


	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if(DEBUG) 
			Log.i("LIMEPreference:OnChanged()"," key:" + key);
			
		if(key.equals("phonetic_keyboard_type")){
			String selectedPhoneticKeyboardType = 
				PreferenceManager.getDefaultSharedPreferences(ctx).getString("phonetic_keyboard_type", "");
			Log.i("LIMEPreference:OnChanged()", "phonetickeyboardtype:" + selectedPhoneticKeyboardType);
			
			try {
				if(DEBUG)
					Log.i("LIMEPreference:OnChanged()", "PhoneticIMInfo.kyeboard:" + 
							DBSrv.getImInfo("phonetic", "keyboard"));	
				if(selectedPhoneticKeyboardType.equals("standard")){
					DBSrv.setKeyboardInfo("phonetic",  
							DBSrv.getKeyboardInfo("phonetic", "desc"), "phonetic");
				}else if(selectedPhoneticKeyboardType.equals("eten")){
					DBSrv.setKeyboardInfo("phonetic", 
							DBSrv.getKeyboardInfo("dayi", "desc"), "dayi");
				}else if(selectedPhoneticKeyboardType.equals("eten26")){
					if(PreferenceManager.getDefaultSharedPreferences(ctx).
							getBoolean("number_row_in_english", false)){
						DBSrv.setKeyboardInfo("phonetic", 
								DBSrv.getKeyboardInfo("limenum", "desc"), "limenum");
					}else{
						DBSrv.setKeyboardInfo("phonetic", 
								DBSrv.getKeyboardInfo("lime", "desc"), "lime");
					}
				}
				if(DEBUG) Log.i("LIMEPreference:OnChanged()", "PhoneticIMInfo.kyeboard:" + 
							DBSrv.getImInfo("phonetic", "keyboard"));	
			} catch (RemoteException e) {
				Log.i("LIMEPreference:OnChanged()", "WriteIMinfo for selected phonetic keyboard failed!!");
				e.printStackTrace();
			}
			
		}
	}
	
	private ServiceConnection serConn = new ServiceConnection() {
		public void onServiceConnected(ComponentName name, IBinder service) {
			if(DBSrv == null){
				//Log.i("ART","Start up db service");
				DBSrv = IDBService.Stub.asInterface(service);
			}
		}
		public void onServiceDisconnected(ComponentName name) {}

	};
	
}