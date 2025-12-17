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

package net.toload.main.hd;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.content.ContextCompat;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.MainActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Application test using AndroidX Test framework.
 * Tests basic application functionality and context availability.
 */
@RunWith(AndroidJUnit4.class)
public class ApplicationTest {

    @Test
    public void testApplicationContext() {
        // Verify that the application context is not null
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertNotNull("Application context should not be null", appContext);
        
        Application application = (Application) appContext.getApplicationContext();
        assertNotNull("Application instance should not be null", application);
    }

    @Test
    public void testPackageName() {
        // Verify that the package name is correct
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String packageName = appContext.getPackageName();
        assertEquals("net.toload.main.hd2025", packageName);
    }

    @Test
    public void testApplicationInfo() throws PackageManager.NameNotFoundException {
        // Verify that application info can be retrieved
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageManager pm = appContext.getPackageManager();
        ApplicationInfo appInfo = pm.getApplicationInfo(appContext.getPackageName(), 0);
        
        assertNotNull("Application info should not be null", appInfo);
        assertNotNull("Application label should not be null", 
                pm.getApplicationLabel(appInfo));
    }

    @Test
    public void testApplicationResources() {
        // Verify that application resources are available
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertNotNull("Resources should not be null", appContext.getResources());
        assertNotNull("String resources should be available", 
                appContext.getResources().getString(R.string.app_name));
    }

    @Test
    public void testPackageInfoCompat() throws PackageManager.NameNotFoundException {
        // Test PackageInfoCompat.getLongVersionCode() for API compatibility
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageManager pm = appContext.getPackageManager();
        PackageInfo pInfo = pm.getPackageInfo(appContext.getPackageName(), 0);
        
        // Verify PackageInfoCompat works correctly
        long versionCode = PackageInfoCompat.getLongVersionCode(pInfo);
        assertTrue("Version code should be positive", versionCode > 0);
        
        // Verify it works on all API levels (21-36)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // On API 28+, versionCode is long
            assertTrue("Version code should match", versionCode == pInfo.getLongVersionCode() || 
                      versionCode == pInfo.versionCode);
        } else {
            // On older APIs, versionCode is int
            assertEquals("Version code should match", versionCode, pInfo.versionCode);
        }
    }

    @Test
    public void testContextCompatGetDataDir() {
        // Test ContextCompat.getDataDir() for API compatibility
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        
        File dataDir = ContextCompat.getDataDir(appContext);
        assertNotNull("Data directory should not be null", dataDir);
        assertTrue("Data directory should exist", dataDir.exists());
        assertTrue("Data directory should be a directory", dataDir.isDirectory());
        
        // Verify it works on all API levels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // On API 24+, getDataDir() is available
            assertEquals("Data dir should match Context.getDataDir()", 
                    appContext.getDataDir().getAbsolutePath(), dataDir.getAbsolutePath());
        } else {
            // On older APIs, should fallback to getFilesDir().getParent()
            String expectedPath = appContext.getFilesDir().getParent();
            assertEquals("Data dir should match fallback path", 
                    expectedPath, dataDir.getAbsolutePath());
        }
    }

    @Test
    public void testLIMEPreferenceManager() {
        // Test LIMEPreferenceManager initialization and basic operations
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LIMEPreferenceManager prefManager = new LIMEPreferenceManager(appContext);
        
        assertNotNull("PreferenceManager should not be null", prefManager);
        
        // Test setting and getting a string parameter
        String testKey = "test_key_" + System.currentTimeMillis();
        String testValue = "test_value";
        prefManager.setParameter(testKey, testValue);
        
        String retrievedValue = prefManager.getParameterString(testKey, "");
        assertEquals("Retrieved value should match set value", testValue, retrievedValue);
        
        // Clean up using SharedPreferences directly
        android.content.SharedPreferences prefs = 
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(appContext);
        prefs.edit().remove(testKey).apply();
    }

    @Test
    public void testAPILevelCompatibility() {
        // Verify the app supports the required API levels
        int currentApiLevel = Build.VERSION.SDK_INT;
        int minSdkVersion = 21;
        int targetSdkVersion = 36;
        
        assertTrue("Current API level should be >= minSdkVersion", 
                currentApiLevel >= minSdkVersion);
        assertTrue("Current API level should be <= targetSdkVersion", 
                currentApiLevel <= targetSdkVersion);
    }

    @Test
    public void testEdgeToEdgeSupport() {
        // Test that edge-to-edge compatibility libraries are available
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        
        // Verify WindowCompat is available (part of androidx.core)
        try {
            Class<?> windowCompatClass = Class.forName("androidx.core.view.WindowCompat");
            assertNotNull("WindowCompat class should be available", windowCompatClass);
        } catch (ClassNotFoundException e) {
            fail("WindowCompat should be available for edge-to-edge support");
        }
        
        // Verify ViewCompat is available
        try {
            Class<?> viewCompatClass = Class.forName("androidx.core.view.ViewCompat");
            assertNotNull("ViewCompat class should be available", viewCompatClass);
        } catch (ClassNotFoundException e) {
            fail("ViewCompat should be available for edge-to-edge support");
        }
        
        // Verify WindowInsetsCompat is available
        try {
            Class<?> windowInsetsCompatClass = Class.forName("androidx.core.view.WindowInsetsCompat");
            assertNotNull("WindowInsetsCompat class should be available", windowInsetsCompatClass);
        } catch (ClassNotFoundException e) {
            fail("WindowInsetsCompat should be available for edge-to-edge support");
        }
    }

    @Test
    public void testDatabasePathAccess() {
        // Test that database paths are accessible using compatibility methods
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        
        // Test getFilesDir() is available (API 1+)
        File filesDir = appContext.getFilesDir();
        assertNotNull("Files directory should not be null", filesDir);
        assertTrue("Files directory should exist", filesDir.exists());
        
        // Test getCacheDir() is available (API 1+)
        File cacheDir = appContext.getCacheDir();
        assertNotNull("Cache directory should not be null", cacheDir);
        assertTrue("Cache directory should exist", cacheDir.exists());
    }

    @Test
    public void testSharedPreferencesAccess() {
        // Test SharedPreferences access through PreferenceManager
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        
        // Test that we can access SharedPreferences
        android.content.SharedPreferences prefs = 
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(appContext);
        assertNotNull("SharedPreferences should not be null", prefs);
        
        // Test basic read/write operations
        String testKey = "test_pref_" + System.currentTimeMillis();
        String testValue = "test_value";
        
        prefs.edit().putString(testKey, testValue).apply();
        String retrievedValue = prefs.getString(testKey, null);
        assertEquals("Retrieved preference value should match", testValue, retrievedValue);
        
        // Clean up
        prefs.edit().remove(testKey).apply();
    }

    @Test
    public void testConnectivityManagerCompatibility() {
        // Test ConnectivityManager API compatibility
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.net.ConnectivityManager cm = 
                (android.net.ConnectivityManager) appContext.getSystemService(
                        Context.CONNECTIVITY_SERVICE);
        
        assertNotNull("ConnectivityManager should not be null", cm);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // API 23+: getActiveNetwork() should be available
            try {
                android.net.Network network = cm.getActiveNetwork();
                // Network might be null if no network is available, which is acceptable
            } catch (Exception e) {
                fail("getActiveNetwork() should be available on API 23+");
            }
        }
    }

    @Test
    public void testActivityIntents() {
        // Test that main activity can be launched
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        
        android.content.Intent mainIntent = new android.content.Intent(appContext, MainActivity.class);
        mainIntent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        
        android.content.pm.PackageManager pm = appContext.getPackageManager();
        android.content.pm.ResolveInfo resolveInfo = pm.resolveActivity(mainIntent, 0);
        
        assertNotNull("MainActivity should be resolvable", resolveInfo);
        assertNotNull("MainActivity component should not be null", resolveInfo.activityInfo);
    }

}
