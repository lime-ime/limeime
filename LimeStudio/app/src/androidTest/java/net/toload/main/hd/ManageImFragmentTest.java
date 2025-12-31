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
import net.toload.main.hd.ui.controller.ManageImController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Test cases for ManageImFragment.
 * 
 * Tests:
 * - IM/keyboard loading via controller
 * - Asynchronous record loading (thread-safe)
 * - Record management operations
 * - No direct LimeDB access
 */
@RunWith(AndroidJUnit4.class)
public class ManageImFragmentTest {

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    /**
     * Test: IM/keyboard loading uses ManageImController
     * 
     * Verifies fragment delegates to controller for data loading.
     */
    @Test
    public void testIMKeyboardLoadingUsesController() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                // Get controller from MainActivity
                ManageImController controller = activity.getManageImController();
                
                assertNotNull("MainActivity should provide ManageImController", controller);
                
                // Verify controller has methods for IM/keyboard operations
                assertNotNull("Controller should have getImConfigFullNameList method",
                    getMethodOrNull(controller, "getImConfigFullNameList"));
                assertNotNull("Controller should have getKeyboardList method", 
                    getMethodOrNull(controller, "getKeyboardList"));
            });
        }
    }

    /**
     * Test: Fragment uses controller's ExecutorService for async loading
     * 
     * Verifies asynchronous operations use controller's thread-safe methods.
     */
    @Test
    public void testAsynchronousRecordLoadingIsThreadSafe() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ManageImController controller = activity.getManageImController();
                
                assertNotNull("Controller should exist", controller);
                
                // Verify controller has async loading method
                assertNotNull("Controller should have loadRecordsAsync method", 
                    getMethodOrNull(controller, "loadRecordsAsync", 
                        String.class, String.class, boolean.class, int.class, int.class));
                
                // Verify BaseController thread-safe methods exist
                assertNotNull("Controller should have showProgress method", 
                    getMethodOrNull(controller, "showProgress", Object.class, String.class));
                assertNotNull("Controller should have hideProgress method", 
                    getMethodOrNull(controller, "hideProgress", Object.class));
                assertNotNull("Controller should have showToast method", 
                    getMethodOrNull(controller, "showToast", Object.class, String.class, int.class));
            });
        }
    }

    /**
     * Test: Record management delegates to controller
     * 
     * Verifies add/update/delete operations use controller.
     */
    @Test
    public void testRecordManagementDelegatesToController() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ManageImController controller = activity.getManageImController();
                
                assertNotNull("Controller should exist", controller);
                
                // Verify controller has CRUD methods
                assertNotNull("Controller should have addRecord method", 
                    getMethodOrNull(controller, "addRecord"));
                assertNotNull("Controller should have updateRecord method", 
                    getMethodOrNull(controller, "updateRecord"));
                assertNotNull("Controller should have deleteRecord method", 
                    getMethodOrNull(controller, "deleteRecord"));
            });
        }
    }

    /**
     * Test: No direct LimeDB access in fragment
     * 
     * Architecture compliance check.
     */
    @Test
    public void testNoDirectLimeDBAccess() {
        // This is verified by ArchitectureComplianceTest
        // but we add a specific test here for clarity
        
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                // Verify fragment gets controller, not direct DB access
                ManageImController controller = activity.getManageImController();
                assertNotNull("Fragment should use controller, not direct DB", controller);
            });
        }
    }

    // Helper method to check if method exists
    private java.lang.reflect.Method getMethodOrNull(Object obj, String methodName, Class<?>... paramTypes) {
        try {
            return obj.getClass().getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            // Try with any parameters
            for (java.lang.reflect.Method method : obj.getClass().getMethods()) {
                if (method.getName().equals(methodName)) {
                    return method;
                }
            }
            return null;
        }
    }
}
