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
		
//		// Startup Search Service
//		if (DBSrv == null) {
//			try {
//				ctx.bindService(new Intent(IDBService.class.getName()),
//						serConn, Context.BIND_AUTO_CREATE);
//			} catch (Exception e) {
//				Log.i("ART", "Failed to connect Search Service");
//			}
//		}

	
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


	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if(DEBUG) 
			Log.i("LIMEPreference:OnChanged()"," key:" + key);
			
		if(key.equals("phonetic_keyboard_type")){
			String selectedPhoneticKeyboardType = mLIMEPref.getPhoneticKeyboardType();
				//PreferenceManager.getDefaultSharedPreferences(ctx).getString("phonetic_keyboard_type", "");
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
				}else if(selectedPhoneticKeyboardType.equals("eten26")||selectedPhoneticKeyboardType.equals("hsu")){
					if(mLIMEPref.getShowNumberRowInEnglish()){
						DBSrv.setIMKeyboard("phonetic", 
								DBSrv.getKeyboardInfo("limenum", "desc"), "limenum");
					}else{
						DBSrv.setIMKeyboard("phonetic", 
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
		BackupManager backupManager = new BackupManager(ctx);
		backupManager.dataChanged();  //Jeremy '12,4,29 call backup manager to backup the changes.
		
		
	}
	
	
//	private ServiceConnection serConn = new ServiceConnection() {
//		public void onServiceConnected(ComponentName name, IBinder service) {
//			if(DBSrv == null){
//				DBSrv = IDBService.Stub.asInterface(service);
//			}
//		}
//		public void onServiceDisconnected(ComponentName name) {}
//
//	};
	
}