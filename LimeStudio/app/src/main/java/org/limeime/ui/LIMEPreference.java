/*
 *
 *  *
 *  **    Copyright 2026, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
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

package org.limeime.ui;

import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.ListPreference;

import org.limeime.KeypressHapticPolicy;
import org.limeime.R;
import org.limeime.SearchServer;
import org.limeime.data.ImConfig;
import org.limeime.data.Keyboard;
import org.limeime.global.LIME;
import org.limeime.global.LIMEPreferenceManager;
import org.limeime.ui.view.ScrollableTabHelper;

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

		// Enable edge-to-edge across all API levels via the AndroidX helper. Replaces
		// manual setDecorFitsSystemWindows + the deprecated setStatusBarColor/
		// setNavigationBarColor calls (no-ops on API 35+; flagged by Play Console).
		if (LIMESettings.shouldUseEdgeToEdgeHelper(Build.VERSION.SDK_INT)) {
			EdgeToEdge.enable(this);
		} else {
			// Preserve the pre-Android-15 layout used by this standalone preference screen.
			WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
		}

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
		// Transparent system bars and light/dark bar-icon appearance are handled by
		// EdgeToEdge.enable(this) in onCreate (auto-detects night mode across API levels).
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
			if (parent instanceof org.limeime.ui.view.LimePreferenceFragment) {
				final org.limeime.ui.view.LimePreferenceFragment host =
						(org.limeime.ui.view.LimePreferenceFragment) parent;
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
