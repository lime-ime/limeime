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

package net.toload.main.hd.ui;

import android.app.backup.BackupManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.ListPreference;

import net.toload.main.hd.KeypressHapticPolicy;
import net.toload.main.hd.R;
import net.toload.main.hd.SearchServer;
import net.toload.main.hd.data.ImConfig;
import net.toload.main.hd.data.Keyboard;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.ui.view.ScrollableTabHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class LIMEPreference extends AppCompatActivity {

	static {
		AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
	}

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
			actionBar.setTitle(R.string.title_lime_preference);
			actionBar.setDisplayHomeAsUpEnabled(false);
			actionBar.setHomeButtonEnabled(false);
		}
		getSupportFragmentManager().addOnBackStackChangedListener(this::syncActionBarToBackStack);

		// Handle window insets for edge-to-edge display
		setupEdgeToEdge();
	}

	@Override
	public boolean onSupportNavigateUp() {
		if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
			getSupportFragmentManager().popBackStack();
			return true;
		}
		finish();
		return true;
	}

	private void syncActionBarToBackStack() {
		ActionBar actionBar = getSupportActionBar();
		if (actionBar == null) return;
		boolean canGoBack = getSupportFragmentManager().getBackStackEntryCount() > 0;
		actionBar.setDisplayHomeAsUpEnabled(canGoBack);
		actionBar.setHomeButtonEnabled(canGoBack);

		androidx.fragment.app.Fragment top =
				getSupportFragmentManager().findFragmentById(android.R.id.content);
		if (top instanceof PreferenceFragmentCompat) {
			PreferenceFragmentCompat pf = (PreferenceFragmentCompat) top;
			if (pf.getPreferenceScreen() != null && pf.getPreferenceScreen().getTitle() != null) {
				actionBar.setTitle(pf.getPreferenceScreen().getTitle());
			}
		}
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
				int uiMode = getResources().getConfiguration().uiMode
						& Configuration.UI_MODE_NIGHT_MASK;
				boolean isLight = (uiMode != Configuration.UI_MODE_NIGHT_YES);
				windowInsetsController.setAppearanceLightStatusBars(isLight);
				windowInsetsController.setAppearanceLightNavigationBars(isLight);
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
		private SearchServer SearchSrv = null;
		private LIMEPreferenceManager mLIMEPref = null;

		@Override
		public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
			super.onViewCreated(view, savedInstanceState);
			ScrollableTabHelper.applyToRecyclerView(getActivity(), getListView());
		}

		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			// Load the preferences from an XML resource (scoped to rootKey for nested PreferenceScreen drill-down)
			setPreferencesFromResource(R.xml.preference, rootKey);

			// Remove the reserved icon space so rows aren't indented (iconSpaceReserved on
			// the XML root doesn't cascade — apply it recursively to every Preference).
			disableIconSpaceReserved(getPreferenceScreen());

			// Sync the host fragment's toolbar (title + back chevron) to this screen
			// — the OnBackStackChangedListener fires before the new fragment loads
			// its preferences, so we need a follow-up nudge once the screen is ready.
			// Defer via view.post(...) so the sync runs after layout — calling it
			// mid-transaction can leave the toolbar nav button in a state where the
			// first tap is eaten.
			androidx.fragment.app.Fragment parent = getParentFragment();
			if (parent instanceof net.toload.main.hd.ui.view.LimePreferenceFragment) {
				final net.toload.main.hd.ui.view.LimePreferenceFragment host =
						(net.toload.main.hd.ui.view.LimePreferenceFragment) parent;
				android.view.View hostView = host.getView();
				if (hostView != null) {
					hostView.post(host::syncToolbarToBackStack);
				} else {
					host.syncToolbarToBackStack();
				}
			}

			if (ctx == null) {
				ctx = requireActivity().getApplicationContext();
			}
			mLIMEPref = new LIMEPreferenceManager(ctx);
			SearchSrv = new SearchServer(ctx);
			configureReverseLookupPreferenceEntries();

			// Hide duration only when keypress vibration uses system keyboard-tap haptics.
			// Raw-pulse devices, including Samsung, still use vibrate_level as duration.
			if (KeypressHapticPolicy.shouldHideVibrateLevelPreference()) {
				androidx.preference.Preference vibrateLevelPref = findPreference("vibrate_level");
				if (vibrateLevelPref != null) {
					vibrateLevelPref.setVisible(false);
				}
			}
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

	

		// Nested PreferenceScreen navigation: handle ONLY via onNavigateToScreen.
		// PreferenceScreen also bubbles a tap up through onPreferenceTreeClick, so
		// overriding both pushes the same fragment transaction twice — the visible
		// symptom is the back chevron requiring two taps to return to the parent.
		@Override
		public void onNavigateToScreen(androidx.preference.PreferenceScreen preferenceScreen) {
			android.util.Log.d(TAG, "onNavigateToScreen: " + preferenceScreen.getKey());
			navigateToNested(preferenceScreen.getKey());
		}

		private void disableIconSpaceReserved(PreferenceGroup group) {
			if (group == null) return;
			group.setIconSpaceReserved(false);
			for (int i = 0; i < group.getPreferenceCount(); i++) {
				Preference p = group.getPreference(i);
				// Keep icon space for rows that actually have a leading icon
				// (the design's section-leading rows); only collapse it for
				// icon-less rows so they aren't indented.
				if (p.getIcon() == null) {
					p.setIconSpaceReserved(false);
				}
				if (p instanceof PreferenceGroup) {
					disableIconSpaceReserved((PreferenceGroup) p);
				}
			}
		}

		private void navigateToNested(String rootKey) {
			PrefsFragment newFragment = new PrefsFragment();
			Bundle args = new Bundle();
			args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, rootKey);
			newFragment.setArguments(args);
			int containerId = android.R.id.content;
			View parent = (View) requireView().getParent();
			if (parent != null && parent.getId() != View.NO_ID) {
				containerId = parent.getId();
			}
			androidx.fragment.app.FragmentManager fm = getParentFragmentManager();
			fm.beginTransaction()
					.replace(containerId, newFragment)
					.addToBackStack(null)
					.commit();
		}

		private void configureReverseLookupPreferenceEntries() {
			List<LIMEPreferenceManager.ReverseLookupOption> options = loadReverseLookupOptions();
			CharSequence[] labels = LIMEPreferenceManager.reverseLookupLabels(options);
			CharSequence[] values = LIMEPreferenceManager.reverseLookupValues(options);
			PreferenceGroup root = getPreferenceScreen();
			applyReverseLookupEntries(root, labels, values);
		}

		private List<LIMEPreferenceManager.ReverseLookupOption> loadReverseLookupOptions() {
			try {
				if (SearchSrv != null) {
					List<ImConfig> all = SearchSrv.getImConfigList(null, LIME.IM_FULL_NAME);
					List<ImConfig> active = new ArrayList<>();
					for (ImConfig im : all) {
						if (im != null && !"emoji".equals(im.getCode()) && !im.isDisable()) {
							active.add(im);
						}
					}
					return LIMEPreferenceManager.buildReverseLookupOptions(active, "無");
				}
			} catch (Exception e) {
				Log.w(TAG, "loadReverseLookupOptions(): fallback to saved active IM state", e);
			}
			return mLIMEPref != null
					? mLIMEPref.getReverseLookupOptions()
					: LIMEPreferenceManager.buildReverseLookupOptions((String) null, "無");
		}

		private void applyReverseLookupEntries(PreferenceGroup group,
				CharSequence[] labels, CharSequence[] values) {
			if (group == null) return;
			for (int i = 0; i < group.getPreferenceCount(); i++) {
				Preference pref = group.getPreference(i);
				if (pref instanceof ListPreference && isReverseLookupPreference(pref.getKey())) {
					ListPreference listPreference = (ListPreference) pref;
					listPreference.setEntries(labels);
					listPreference.setEntryValues(values);
				}
				if (pref instanceof PreferenceGroup) {
					applyReverseLookupEntries((PreferenceGroup) pref, labels, values);
				}
			}
		}

		private boolean isReverseLookupPreference(String key) {
			return key != null && key.endsWith("_im_reverselookup");
		}

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
			if(DEBUG)
				Log.i(TAG,"onSharedPreferenceChanged(), key:" + key);

			if(mLIMEPref != null){
				mLIMEPref.resetStartupConfigVersionIfStartupPreferenceChanged(key);
			}

			if("phonetic_keyboard_type".equals(key)){
				String selectedPhoneticKeyboardType = mLIMEPref.getPhoneticKeyboardType();
				//PreferenceManager.getDefaultSharedPreferences(ctx).getString("phonetic_keyboard_type", "");
				try {
					// Ensure SearchServer instance is initialized
					if (SearchSrv == null) {
						if (ctx == null) {
							ctx = requireActivity().getApplicationContext();
						}
						SearchSrv = new SearchServer(ctx);
					}

					Keyboard keyboardConfig = SearchSrv.getKeyboardConfig(LIME.DB_TABLE_PHONETIC);

                    switch (selectedPhoneticKeyboardType) {
                        case LIME.IM_PHONETIC_STANDARD:
                            keyboardConfig = SearchSrv.getKeyboardConfig("phonetic");
                            break;
                        case LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN:
                            keyboardConfig = SearchSrv.getKeyboardConfig("phoneticet41");
                            break;
                        case LIME.IM_PHONETIC_KEYBOARD_TYPE_ETEN26:
                            if (mLIMEPref.getParameterBoolean("number_row_in_english", false)) {
                                keyboardConfig = SearchSrv.getKeyboardConfig("limenum");
                            } else {
                                keyboardConfig = SearchSrv.getKeyboardConfig("lime");
                            }
                            break;
                        case "eten26_symbol":
                            keyboardConfig = SearchSrv.getKeyboardConfig("et26");
                            break;
                        case LIME.IM_PHONETIC_KEYBOARD_HSU:  //Jeremy '12,7,6 Add HSU english keyboard support
                            if (mLIMEPref.getParameterBoolean("number_row_in_english", false)) {
                                keyboardConfig = SearchSrv.getKeyboardConfig("limenum");
                            } else {
                                keyboardConfig = SearchSrv.getKeyboardConfig("lime");
                            }
                            break;
                        case "hsu_symbol":
                            keyboardConfig = SearchSrv.getKeyboardConfig(LIME.IM_PHONETIC_KEYBOARD_HSU);
                            break;
                    }
                    SearchSrv.setIMKeyboard("phonetic", keyboardConfig.getDescription(), keyboardConfig.getCode());
					if(DEBUG) Log.i(TAG, "onSharedPreferenceChanged() PhoneticIMInfo.kyeboard:" + 
							SearchSrv.getImConfig("phonetic", "keyboard"));
				} catch (Exception e) {
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
