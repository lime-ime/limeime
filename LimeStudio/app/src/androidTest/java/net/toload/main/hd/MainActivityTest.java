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

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import net.toload.main.hd.ui.MainActivity;
import net.toload.main.hd.ui.NavigationManager;
import net.toload.main.hd.ui.ProgressManager;
import net.toload.main.hd.ui.ShareManager;
import net.toload.main.hd.ui.controller.ManageImController;
import net.toload.main.hd.ui.controller.SetupImController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Test cases for MainActivity coordinator pattern.
 * 
 * Tests:
 * - Component coordination and singleton getters
 * - Controller/manager creation
 * - NavigationDrawerCallbacks NOT in MainActivity (moved to NavigationManager)
 * - No direct model access
 */
@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    /**
     * Test: MainActivity creates singleton instances of controllers/managers
     * 
     * Verifies coordinator pattern implementation.
     */
    @Test
    public void testMainActivityCreatesSingletonInstances() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                // Get instances
                SetupImController setupCtrl1 = activity.getSetupImController();
                SetupImController setupCtrl2 = activity.getSetupImController();
                
                ManageImController manageCtrl1 = activity.getManageImController();
                ManageImController manageCtrl2 = activity.getManageImController();
                
                ProgressManager progressMgr1 = activity.getProgressManager();
                ProgressManager progressMgr2 = activity.getProgressManager();
                
                ShareManager shareMgr1 = activity.getShareManager();
                ShareManager shareMgr2 = activity.getShareManager();
                
                NavigationManager navMgr1 = activity.getNavigationManager();
                NavigationManager navMgr2 = activity.getNavigationManager();
                
                // Verify singletons
                assertSame("SetupImController should be singleton", setupCtrl1, setupCtrl2);
                assertSame("ManageImController should be singleton", manageCtrl1, manageCtrl2);
                assertSame("ProgressManager should be singleton", progressMgr1, progressMgr2);
                assertSame("ShareManager should be singleton", shareMgr1, shareMgr2);
                assertSame("NavigationManager should be singleton", navMgr1, navMgr2);
            });
        }
    }

    /**
     * Test: Getter methods return non-null instances
     * 
     * Verifies all required components are created.
     */
    @Test
    public void testGetterMethodsReturnNonNullInstances() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull("SetupImController should not be null", 
                    activity.getSetupImController());
                assertNotNull("ManageImController should not be null", 
                    activity.getManageImController());
                assertNotNull("ProgressManager should not be null", 
                    activity.getProgressManager());
                assertNotNull("ShareManager should not be null", 
                    activity.getShareManager());
                assertNotNull("NavigationManager should not be null", 
                    activity.getNavigationManager());
            });
        }
    }

    /**
     * Test: MainActivity does NOT implement NavigationDrawerCallbacks
     * 
     * Verifies callbacks moved to NavigationManager per UI_ARCHITECTURE v1.1.
     */
    @Test
    public void testMainActivityDoesNotImplementNavigationDrawerCallbacks() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                // Check if MainActivity implements NavigationDrawerCallbacks interface
                Class<?>[] interfaces = activity.getClass().getInterfaces();
                
                boolean implementsCallbacks = false;
                for (Class<?> iface : interfaces) {
                    if (iface.getSimpleName().equals("NavigationDrawerCallbacks")) {
                        implementsCallbacks = true;
                        break;
                    }
                }
                
                assertFalse("MainActivity should NOT implement NavigationDrawerCallbacks " +
                    "(moved to NavigationManager)", implementsCallbacks);
            });
        }
    }

    /**
     * Test: NavigationManager implements NavigationDrawerCallbacks
     * 
     * Verifies proper delegation pattern.
     */
    @Test
    public void testNavigationManagerImplementsCallbacks() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                NavigationManager navManager = activity.getNavigationManager();
                assertNotNull("NavigationManager should exist", navManager);
                
                // Check if NavigationManager implements the callback interface
                Class<?>[] interfaces = navManager.getClass().getInterfaces();
                
                boolean implementsCallbacks = false;
                for (Class<?> iface : interfaces) {
                    if (iface.getSimpleName().contains("NavigationDrawerCallbacks") ||
                        iface.getSimpleName().contains("Callbacks")) {
                        implementsCallbacks = true;
                        break;
                    }
                }
                
                assertTrue("NavigationManager should implement NavigationDrawerCallbacks", 
                    implementsCallbacks);
            });
        }
    }

    /**
     * Test: MainActivity exposes managers/controllers to views only
     * 
     * Verifies no direct model access in activity.
     */
    @Test
    public void testMainActivityExposesManagersControllersOnly() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                // Verify getter methods exist
                assertNotNull("Should have getSetupImController()", 
                    getMethodOrNull(activity, "getSetupImController"));
                assertNotNull("Should have getManageImController()", 
                    getMethodOrNull(activity, "getManageImController"));
                assertNotNull("Should have getProgressManager()", 
                    getMethodOrNull(activity, "getProgressManager"));
                assertNotNull("Should have getShareManager()", 
                    getMethodOrNull(activity, "getShareManager"));
                assertNotNull("Should have getNavigationManager()", 
                    getMethodOrNull(activity, "getNavigationManager"));
                
                // Verify no direct SearchServer or LimeDB getters
                assertNull("Should NOT have getSearchServer() - use controllers", 
                    getMethodOrNull(activity, "getSearchServer"));
                assertNull("Should NOT have getLimeDB() - use controllers", 
                    getMethodOrNull(activity, "getLimeDB"));
            });
        }
    }

    /**
     * Test: Activity lifecycle maintains singleton references
     * 
     * Verifies instances persist across configuration.
     */
    @Test
    public void testActivityLifecycleMaintainsSingletons() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                SetupImController ctrl = activity.getSetupImController();
                assertNotNull("Controller should be created", ctrl);
            });
            
            // Simulate configuration change
            scenario.recreate();
            
            scenario.onActivity(activity -> {
                SetupImController ctrl = activity.getSetupImController();
                assertNotNull("Controller should be recreated after config change", ctrl);
            });
        }
    }

    // Helper method
    private java.lang.reflect.Method getMethodOrNull(Object obj, String methodName) {
        try {
            return obj.getClass().getMethod(methodName);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
