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

import net.toload.main.hd.R;
import net.toload.main.hd.global.LIMEPreferenceManager;
import android.annotation.TargetApi;
import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceActivity;
import android.util.Log;



/**
 * @author Art Hung
 */
public class LIMEPreference extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	private final boolean DEBUG = false;
	private Context ctx = null;
	private DBServer DBSrv = null;
	private LIMEPreferenceManager mLIMEPref = null;

    @SuppressWarnings("deprecation")
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //--------------------
        //Jeremy '10, 4, 18 .  
        if (ctx == null) {
			ctx = this.getApplicationContext();
		}
        mLIMEPref = new LIMEPreferenceManager(ctx);
        //-----------------------

		addPreferencesFromResource(R.xml.preference);
       
		DBSrv = new DBServer(ctx);
		


	
    }
    

    
    @SuppressWarnings("deprecation")
	@Override
    protected void onResume() {
        super.onResume();

        // Set up a listener whenever a key changes            
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @SuppressWarnings("deprecation")
	@Override
    protected void onPause() {
        super.onPause();

        // Unregister the listener whenever a key changes            
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
 
    }

	@Override
	public void onContentChanged() {
		super.onContentChanged();
	}


	@TargetApi(8)
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if(DEBUG) 
			Log.i("LIMEPreference:OnChanged()"," key:" + key);
			
		if(key.equals("phonetic_keyboard_type")){
			String selectedPhoneticKeyboardType = mLIMEPref.getPhoneticKeyboardType();
				//PreferenceManager.getDefaultSharedPreferences(ctx).getString("phonetic_keyboard_type", "");
			if(DEBUG)
				Log.i("LIMEPreference:OnChanged()", "phonetickeyboardtype:" + selectedPhoneticKeyboardType);
			
			try {
				if(DEBUG)
					Log.i("LIMEPreference:OnChanged()", "PhoneticIMInfo.kyeboard:" + 
							DBSrv.getImInfo("phonetic", "keyboard"));	
				if(selectedPhoneticKeyboardType.equals("standard")){
					DBSrv.setIMKeyboard("phonetic",  
							DBSrv.getKeyboardInfo("phonetic", "desc"), "phonetic");
				}else if(selectedPhoneticKeyboardType.equals("eten")){
					DBSrv.setIMKeyboard("phonetic", 
							DBSrv.getKeyboardInfo("phoneticet41", "desc"), "phoneticet41");
				}else if(selectedPhoneticKeyboardType.equals("hsu")){
					DBSrv.setIMKeyboard("phonetic", 
							DBSrv.getKeyboardInfo("hsu", "desc"), "hsu");//jeremy '12,6,6 new hsu and et26 keybaord
				}else if(selectedPhoneticKeyboardType.equals("eten26")){
					DBSrv.setIMKeyboard("phonetic", 
								DBSrv.getKeyboardInfo("et26", "desc"), "et26");
					
				}
				if(DEBUG) Log.i("LIMEPreference:OnChanged()", "PhoneticIMInfo.kyeboard:" + 
							DBSrv.getImInfo("phonetic", "keyboard"));	
			} catch (RemoteException e) {
				Log.i("LIMEPreference:OnChanged()", "WriteIMinfo for selected phonetic keyboard failed!!");
				e.printStackTrace();
			}
			
		}

		if(android.os.Build.VERSION.SDK_INT > 7 ){ //Jeremy '12,5,4 Supported after api 8.
			BackupManager backupManager = new BackupManager(ctx);
			backupManager.dataChanged();  //Jeremy '12,4,29 call backup manager to backup the changes.
		}
		
		
	}
	

	
}