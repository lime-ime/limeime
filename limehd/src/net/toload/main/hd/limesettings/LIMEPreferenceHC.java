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
import android.app.Activity;
import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceFragment;

import android.util.Log;



@TargetApi(11)
public class LIMEPreferenceHC extends Activity { 
	


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Display the fragment as the main content.
		getFragmentManager().beginTransaction().replace(android.R.id.content,
				new PrefsFragment()).commit();

	}




	public static class PrefsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener{ 
		private final boolean DEBUG = false;
		private final String TAG = "LIMEPreferenceHC";
		private Context ctx = null;
		private DBServer DBSrv = null;
		private LIMEPreferenceManager mLIMEPref = null;
		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			// Load the preferences from an XML resource
			addPreferencesFromResource(R.xml.preference);

			if (ctx == null) {
				ctx = getActivity().getApplicationContext();
			}
			mLIMEPref = new LIMEPreferenceManager(ctx);
			DBSrv = new DBServer(ctx);
			//-----------------------
//			// Startup Search Service
//			if (DBSrv == null) {
//				try {
//					ctx.bindService(new Intent(IDBService.class.getName()),
//							serConn, Context.BIND_AUTO_CREATE);
//				} catch (Exception e) {
//					Log.i(TAG, "Failed to connect Search Service");
//				}
//			}
		}




		@Override
		public void onResume() {
			super.onResume();

			// Set up a listener whenever a key changes            
			getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
		}

		@Override
		public void onPause() {
			super.onPause();

			// Unregister the listener whenever a key changes            
			getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);

		}

	

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
			if(DEBUG) 
				Log.i(TAG,"onSharedPreferenceChanged(), key:" + key);

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
					if(DEBUG) Log.i(TAG, "onSharedPreferenceChanged() PhoneticIMInfo.kyeboard:" + 
							DBSrv.getImInfo("phonetic", "keyboard"));	
				} catch (RemoteException e) {
					Log.i(TAG, "onSharedPreferenceChanged(), WriteIMinfo for selected phonetic keyboard failed!!");
					e.printStackTrace();
				}

			}
			BackupManager backupManager = new BackupManager(ctx);
			backupManager.dataChanged();  //Jeremy '12,4,29 call backup manager to backup the changes.


		}


//		private ServiceConnection serConn = new ServiceConnection() {
//			public void onServiceConnected(ComponentName name, IBinder service) {
//				if(DBSrv == null){
//					DBSrv = IDBService.Stub.asInterface(service);
//				}
//			}
//			public void onServiceDisconnected(ComponentName name) {}
//
//		};
	}

}