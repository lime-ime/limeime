/*
 *
 *  *
 *  **    Copyright 2015, The LimeIME Open Source Project
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
import android.content.pm.PackageManager;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

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
        assertEquals("net.toload.main.hd", packageName);
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
}
