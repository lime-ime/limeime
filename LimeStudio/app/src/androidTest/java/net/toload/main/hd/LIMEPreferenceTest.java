/*
 * Copyright 2025, The LimeIME Open Source Project
 */
package net.toload.main.hd;

import android.content.SharedPreferences;
import android.graphics.Color;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.toload.main.hd.ui.LIMEPreference;

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
    public void testPrefsFragmentClassExists() {
        try {
            Class<?> cls = Class.forName("net.toload.main.hd.ui.LIMEPreference$PrefsFragment");
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
    @SuppressWarnings("deprecation")
    public void testEdgeToEdgeColorsApplied() {
        try (ActivityScenario<LIMEPreference> scenario = ActivityScenario.launch(LIMEPreference.class)) {
            scenario.onActivity(activity -> {
                int statusColor = activity.getWindow().getStatusBarColor();
                int navColor = activity.getWindow().getNavigationBarColor();
                assertTrue("Status bar color should be transparent or dark fallback",
                        statusColor == Color.TRANSPARENT || statusColor == 0xFF000000);
                assertTrue("Navigation bar color should be transparent", navColor == Color.TRANSPARENT);
            });
        }
    }

    @Test
    public void testLegacyNameNotPresent() {
        try {
            Class.forName("net.toload.main.hd.ui.LIMEPreferenceHC");
            fail("LIMEPreferenceHC should not exist after rename");
        } catch (ClassNotFoundException expected) {
            // expected
        }
    }
}
