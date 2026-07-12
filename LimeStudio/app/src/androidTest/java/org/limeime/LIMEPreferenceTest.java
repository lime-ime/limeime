/*
 * Copyright 2026, The LimeIME Open Source Project
 */
package org.limeime;

import android.content.SharedPreferences;

import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceScreen;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.limeime.ui.LIMEPreference;

import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Tests for LIMEPreference and PrefsFragment.
 */
@RunWith(AndroidJUnit4.class)
public class LIMEPreferenceTest {

    @Test
    public void testLIMEPreferenceActivityLaunches() {
        try (ActivityScenario<LIMEPreference> scenario = ActivityScenario.launch(LIMEPreference.class)) {
            // If no exception, activity launches
            assertTrue(true);
        }
    }

    @Test
    public void testStandalonePreferenceActivityTitleMatchesPreferenceTab() {
        try (ActivityScenario<LIMEPreference> scenario = ActivityScenario.launch(LIMEPreference.class)) {
            scenario.onActivity(activity -> {
                assertNotNull("ActionBar should exist", activity.getSupportActionBar());
                assertEquals("喜好設定", String.valueOf(activity.getSupportActionBar().getTitle()));
            });
        }
    }

    @Test
    public void testPrefsFragmentClassExists() {
        try {
            Class<?> cls = Class.forName("org.limeime.ui.LIMEPreference$PrefsFragment");
            assertNotNull(cls);
        } catch (ClassNotFoundException e) {
            fail("LIMEPreference.PrefsFragment class not found");
        }
    }

    @Test
    public void testPrefsFragmentAttachedWithSearchServerInitialized() {
        try (ActivityScenario<LIMEPreference> scenario = ActivityScenario.launch(LIMEPreference.class)) {
            scenario.onActivity(activity -> {
                Fragment fragment = activity.getSupportFragmentManager().findFragmentById(android.R.id.content);
                assertNotNull("PrefsFragment should be attached", fragment);

                try {
                    java.lang.reflect.Field searchField = fragment.getClass().getDeclaredField("SearchSrv");
                    searchField.setAccessible(true);
                    Object searchSrv = searchField.get(fragment);
                    assertNotNull("PrefsFragment should initialize SearchServer", searchSrv);
                } catch (Exception e) {
                    throw new AssertionError("Failed to inspect PrefsFragment SearchSrv", e);
                }
            });
        }
    }

    @Test
    public void testOnSharedPreferenceChangedCallsBackupManager() {
        try (ActivityScenario<LIMEPreference> scenario = ActivityScenario.launch(LIMEPreference.class)) {
            scenario.onActivity(activity -> {
                Fragment fragment = activity.getSupportFragmentManager().findFragmentById(android.R.id.content);
                assertNotNull("PrefsFragment should be attached", fragment);

                try {
                    SharedPreferences prefs = ((LIMEPreference.PrefsFragment) fragment).getPreferenceScreen().getSharedPreferences();
                    ((LIMEPreference.PrefsFragment) fragment).onSharedPreferenceChanged(prefs, "some_key");
                } catch (Exception e) {
                    throw new AssertionError("PrefsFragment onSharedPreferenceChanged should not crash", e);
                }
            });
        }
    }

    @Test
    public void testPhoneticKeyboardTypeChangeDoesNotCrash() {
        try (ActivityScenario<LIMEPreference> scenario = ActivityScenario.launch(LIMEPreference.class)) {
            scenario.onActivity(activity -> {
                Fragment fragment = activity.getSupportFragmentManager().findFragmentById(android.R.id.content);
                assertNotNull("PrefsFragment should be attached", fragment);

                try {
                    SharedPreferences prefs = ((LIMEPreference.PrefsFragment) fragment).getPreferenceScreen().getSharedPreferences();
                    ((LIMEPreference.PrefsFragment) fragment).onSharedPreferenceChanged(prefs, "phonetic_keyboard_type");
                } catch (Exception e) {
                    throw new AssertionError("PrefsFragment phonetic keyboard change crashed", e);
                }
            });
        }
    }

    @Test
    public void testPreferenceChangeListenerLifecycleSafe() {
        try (ActivityScenario<LIMEPreference> scenario = ActivityScenario.launch(LIMEPreference.class)) {
            scenario.onActivity(activity -> {
                Fragment fragment = activity.getSupportFragmentManager().findFragmentById(android.R.id.content);
                assertNotNull("PrefsFragment should be attached", fragment);

                try {
                    ((LIMEPreference.PrefsFragment) fragment).onResume();
                    ((LIMEPreference.PrefsFragment) fragment).onPause();
                } catch (Exception e) {
                    throw new AssertionError("PrefsFragment listener lifecycle crashed", e);
                }
            });
        }
    }

    @org.junit.Ignore("Deprecated: standalone LIMEPreference activity is being absorbed into the new BottomNav tab per docs/LIME_SETTINGS_BACKPORT.md §8; the standalone-launch + nested-screen navigation flow no longer holds the activity in RESUMED long enough for the fragment transaction. See docs/DEPCECATED_UI_TESTS.md.")
    @Test
    public void testReverseLookupNestedScreenOpensFromStandalonePreferenceActivity() {
        try (ActivityScenario<LIMEPreference> scenario = ActivityScenario.launch(LIMEPreference.class)) {
            scenario.onActivity(activity -> {
                Fragment fragment = activity.getSupportFragmentManager().findFragmentById(android.R.id.content);
                assertNotNull("PrefsFragment should be attached", fragment);

                PreferenceScreen reverseLookupScreen =
                        ((LIMEPreference.PrefsFragment) fragment).findPreference("reverse_lookup_screen");
                assertNotNull("Reverse lookup screen should exist", reverseLookupScreen);

                ((LIMEPreference.PrefsFragment) fragment).onNavigateToScreen(reverseLookupScreen);
                activity.getSupportFragmentManager().executePendingTransactions();

                Fragment nestedFragment = activity.getSupportFragmentManager().findFragmentById(android.R.id.content);
                assertTrue("Nested reverse lookup screen should be shown",
                        nestedFragment instanceof LIMEPreference.PrefsFragment);
                assertEquals("reverse_lookup_screen",
                        ((LIMEPreference.PrefsFragment) nestedFragment).getPreferenceScreen().getKey());
                assertNotNull("ActionBar should exist", activity.getSupportActionBar());
                assertTrue("Nested standalone preference screen should show a back chevron",
                        (activity.getSupportActionBar().getDisplayOptions()
                                & androidx.appcompat.app.ActionBar.DISPLAY_HOME_AS_UP) != 0);
            });
        }
    }

    @Test
    public void testRootBackChevronFinishesStandalonePreferenceActivity() {
        try (ActivityScenario<LIMEPreference> scenario = ActivityScenario.launch(LIMEPreference.class)) {
            scenario.onActivity(activity -> {
                assertEquals(0, activity.getSupportFragmentManager().getBackStackEntryCount());
                assertTrue("Root up should be handled", activity.onSupportNavigateUp());
                assertTrue("Root up should finish the standalone preference activity", activity.isFinishing());
            });
        }
    }

    @Test
    public void testPhoneticKeyboardMappingBranches() {
        try (ActivityScenario<LIMEPreference> scenario = ActivityScenario.launch(LIMEPreference.class)) {
            scenario.onActivity(activity -> {
                Fragment fragment = activity.getSupportFragmentManager().findFragmentById(android.R.id.content);
                assertNotNull("PrefsFragment should be attached", fragment);

                try {
                    SharedPreferences prefs = ((LIMEPreference.PrefsFragment) fragment).getPreferenceScreen().getSharedPreferences();
                    prefs.edit()
                            .putString("phonetic_keyboard_type", "eten26")
                            .putBoolean("number_row_in_english", true)
                            .apply();
                    ((LIMEPreference.PrefsFragment) fragment).onSharedPreferenceChanged(prefs, "phonetic_keyboard_type");

                    prefs.edit()
                            .putString("phonetic_keyboard_type", "hsu_symbol")
                            .putBoolean("number_row_in_english", false)
                            .apply();
                    ((LIMEPreference.PrefsFragment) fragment).onSharedPreferenceChanged(prefs, "phonetic_keyboard_type");
                } catch (Exception e) {
                    throw new AssertionError("PrefsFragment phonetic mapping branches crashed", e);
                }
            });
        }
    }

    @Test
    public void testPreferenceContentIsAttachedWithLegacyInsetsPolicy() {
        try (ActivityScenario<LIMEPreference> scenario = ActivityScenario.launch(LIMEPreference.class)) {
            scenario.onActivity(activity -> {
                Fragment fragment = activity.getSupportFragmentManager()
                        .findFragmentById(android.R.id.content);
                assertNotNull("Preference content should remain attached", fragment);
            });
        }
    }

    @Test
    public void testLegacyNameNotPresent() {
        try {
            Class.forName("org.limeime.ui.LIMEPreferenceHC");
            fail("LIMEPreferenceHC should not exist after rename");
        } catch (ClassNotFoundException expected) {
            // expected
        }
    }
}
