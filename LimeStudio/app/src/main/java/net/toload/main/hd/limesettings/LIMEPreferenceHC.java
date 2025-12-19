/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

package net.toload.main.hd.limesettings;

import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.preference.PreferenceFragmentCompat;

import net.toload.main.hd.DBServer;
import net.toload.main.hd.R;
import net.toload.main.hd.SearchServer;
import net.toload.main.hd.data.KeyboardObj;
import net.toload.main.hd.global.LIMEPreferenceManager;

import java.util.Objects;


public class LIMEPreferenceHC extends AppCompatActivity {

	private SearchServer SearchSrv = null;

	@Override
	protected void onPause() {
		super.onPause();

		this.SearchSrv.initialCache();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Enable edge-to-edge display for API 35+ (Android 15+)
		WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

		this.SearchSrv = new SearchServer(this);

		// Display the fragment as the main content.
		getSupportFragmentManager().beginTransaction().replace(android.R.id.content,
				new PrefsFragment()).commit();

		// Ensure ActionBar title is displayed
		androidx.appcompat.app.ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayShowTitleEnabled(true);
		}

		// Handle window insets for edge-to-edge display
		setupEdgeToEdge();
	}

	/**
	 * Setup edge-to-edge display with proper window insets handling.
	 * This ensures UI elements are not obscured by system bars on API 35+.
	 */
    @SuppressWarnings("deprecation")
	private void setupEdgeToEdge() {
		// Apply window insets to the content view (where PreferenceFragment is displayed)
		View contentView = findViewById(android.R.id.content);
		if (contentView != null) {
			ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, insets) -> {
				int systemBarsType = WindowInsetsCompat.Type.systemBars();
				int topInset = insets.getInsets(systemBarsType).top;
				int bottomInset = insets.getInsets(systemBarsType).bottom;
				int leftInset = insets.getInsets(systemBarsType).left;
				int rightInset = insets.getInsets(systemBarsType).right;

				// Apply padding: top = status bar only (ActionBar handles its own space),
				// left/right/bottom = system bars
				v.setPadding(leftInset, topInset, rightInset, bottomInset);

				return insets;
			});
		}

		// Set status bar and navigation bar to transparent for edge-to-edge effect
		// Note: setStatusBarColor and setNavigationBarColor are deprecated in API 35+,
		// but we use them with suppression for backward compatibility
        android.view.Window window = getWindow();
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);

		
		// Set status bar icon appearance to dark (black icons) for better visibility
		// Since status bar is transparent and content behind may be light, use dark icons
		View decorView = getWindow().getDecorView();
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
			// API 23+ (Marshmallow+): Use WindowInsetsControllerCompat
			// Note: getWindowInsetsController() is deprecated in API 35+, but necessary for API 23-34
			@SuppressWarnings("deprecation")
			WindowInsetsControllerCompat windowInsetsController = ViewCompat.getWindowInsetsController(decorView);
			if (windowInsetsController != null) {
				// Use dark status bar icons (black) for visibility on light backgrounds
				// setAppearanceLightStatusBars(true) = light status bar appearance = dark icons
				windowInsetsController.setAppearanceLightStatusBars(true);
				// Use dark navigation bar icons for consistency
				windowInsetsController.setAppearanceLightNavigationBars(true);
			}
		} else {
			// API 21-22: SYSTEM_UI_FLAG_LIGHT_STATUS_BAR is not available (introduced in API 23)
			// On API 21-22, we cannot change icon color programmatically
			// Set a dark status bar so white icons are visible (compromise for API 21-22)
			//@SuppressWarnings("deprecation")
			//android.view.Window window = getWindow();
			// Use a dark color so white icons are visible
			// This maintains some edge-to-edge while ensuring icons are visible
			window.setStatusBarColor(0xFF000000); // Solid black
		}
	}

	public static class PrefsFragment extends PreferenceFragmentCompat implements OnSharedPreferenceChangeListener{
		private final boolean DEBUG = false;
		private final String TAG = "LIMEPreferenceHC";
		private Context ctx = null;
		private DBServer DBSrv = null;
		private LIMEPreferenceManager mLIMEPref = null;
		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			// Load the preferences from an XML resource
			addPreferencesFromResource(R.xml.preference);

			if (ctx == null) {
				ctx = requireActivity().getApplicationContext();
			}
			mLIMEPref = new LIMEPreferenceManager(ctx);
			DBSrv = DBServer.getInstance(ctx);
		}

		@Override
		public void onResume() {
			super.onResume();

			// Set up a listener whenever a key changes
			Objects.requireNonNull(getPreferenceScreen().getSharedPreferences()).registerOnSharedPreferenceChangeListener(this);
		}

		@Override
		public void onPause() {
			super.onPause();

			// Unregister the listener whenever a key changes
			Objects.requireNonNull(getPreferenceScreen().getSharedPreferences()).unregisterOnSharedPreferenceChangeListener(this);

		}

	

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
			if(DEBUG)
				Log.i(TAG,"onSharedPreferenceChanged(), key:" + key);

			if(key.equals("phonetic_keyboard_type")){
				String selectedPhoneticKeyboardType = mLIMEPref.getPhoneticKeyboardType();
				//PreferenceManager.getDefaultSharedPreferences(ctx).getString("phonetic_keyboard_type", "");
				try {
					// Ensure DBServer instance is initialized
					if (DBSrv == null) {
						if (ctx == null) {
							ctx = requireActivity().getApplicationContext();
						}
						DBSrv = DBServer.getInstance(ctx);
					}

					KeyboardObj kobj = DBSrv.getKeyboardObj("phonetic");

                    switch (selectedPhoneticKeyboardType) {
                        case "standard":
                            kobj = DBSrv.getKeyboardObj("phonetic");
                            break;
                        case "eten":
                            kobj = DBSrv.getKeyboardObj("phoneticet41");
                            break;
                        case "eten26":
                            if (mLIMEPref.getParameterBoolean("number_row_in_english", false)) {
                                kobj = DBSrv.getKeyboardObj("limenum");
                            } else {
                                kobj = DBSrv.getKeyboardObj("lime");
                            }
                            break;
                        case "eten26_symbol":
                            kobj = DBSrv.getKeyboardObj("et26");
                            break;
                        case "hsu":  //Jeremy '12,7,6 Add HSU english keyboard support
                            if (mLIMEPref.getParameterBoolean("number_row_in_english", false)) {
                                kobj = DBSrv.getKeyboardObj("limenum");
                            } else {
                                kobj = DBSrv.getKeyboardObj("lime");
                            }
                            break;
                        case "hsu_symbol":
                            kobj = DBSrv.getKeyboardObj("hsu");
                            break;
                    }
                    DBSrv.setIMKeyboard("phonetic", kobj.getDescription(), kobj.getCode());
					if(DEBUG) Log.i(TAG, "onSharedPreferenceChanged() PhoneticIMInfo.kyeboard:" + 
							DBSrv.getImInfo("phonetic", "keyboard"));	
				} catch (RemoteException e) {
					Log.i(TAG, "onSharedPreferenceChanged(), WriteIMinfo for selected phonetic keyboard failed!!");
					Log.e(TAG, "Error in operation", e);
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
