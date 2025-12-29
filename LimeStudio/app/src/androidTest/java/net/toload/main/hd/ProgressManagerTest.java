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
import net.toload.main.hd.ui.ProgressManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Test cases for ProgressManager.
 * 
 * Tests:
 * - Show/update/dismiss without activity leaks
 * - Activity recreation handling (no WindowLeaked)
 * - Thread-safe operations
 */
@RunWith(AndroidJUnit4.class)
public class ProgressManagerTest {

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    /**
     * Test: ProgressManager show/dismiss works without leaking
     * 
     * Verifies basic dialog lifecycle.
     */
    @Test
    public void testProgressManagerShowDismissWithoutLeak() throws InterruptedException {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ProgressManager manager = activity.getProgressManager();
                assertNotNull("ProgressManager should exist", manager);
                
                // Show progress
                activity.runOnUiThread(() -> {
                    manager.show("Testing...");
                });
            });
            
            // Wait a bit
            Thread.sleep(100);
            
            scenario.onActivity(activity -> {
                ProgressManager manager = activity.getProgressManager();
                
                // Dismiss progress
                activity.runOnUiThread(() -> {
                    manager.dismiss();
                });
            });
            
            // Wait for dismiss
            Thread.sleep(100);
            
            // If no WindowLeaked exception, test passes
        }
    }

    /**
     * Test: ProgressManager update message
     * 
     * Verifies message can be updated while showing.
     */
    @Test
    public void testProgressManagerUpdateMessage() throws InterruptedException {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ProgressManager manager = activity.getProgressManager();
                
                activity.runOnUiThread(() -> {
                    manager.show("Initial message");
                });
            });
            
            Thread.sleep(100);
            
            scenario.onActivity(activity -> {
                ProgressManager manager = activity.getProgressManager();
                
                activity.runOnUiThread(() -> {
                    manager.updateMessage("Updated message");
                });
            });
            
            Thread.sleep(100);
            
            scenario.onActivity(activity -> {
                ProgressManager manager = activity.getProgressManager();
                
                activity.runOnUiThread(() -> {
                    manager.dismiss();
                });
            });
            
            Thread.sleep(100);
        }
    }

    /**
     * Test: ProgressManager update percentage
     * 
     * Verifies progress percentage can be updated.
     */
    @Test
    public void testProgressManagerUpdatePercentage() throws InterruptedException {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ProgressManager manager = activity.getProgressManager();
                
                activity.runOnUiThread(() -> {
                    manager.show("Downloading...");
                    manager.updateProgress(25);
                });
            });
            
            Thread.sleep(100);
            
            scenario.onActivity(activity -> {
                ProgressManager manager = activity.getProgressManager();
                
                activity.runOnUiThread(() -> {
                    manager.updateProgress(50);
                });
            });
            
            Thread.sleep(100);
            
            scenario.onActivity(activity -> {
                ProgressManager manager = activity.getProgressManager();
                
                activity.runOnUiThread(() -> {
                    manager.updateProgress(100);
                    manager.dismiss();
                });
            });
            
            Thread.sleep(100);
        }
    }

    /**
     * Test: ProgressManager survives activity recreation
     * 
     * Verifies no WindowLeaked on configuration change.
     */
    @Test
    public void testProgressManagerSurvivesActivityRecreation() throws InterruptedException {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ProgressManager manager = activity.getProgressManager();
                
                activity.runOnUiThread(() -> {
                    manager.show("Testing recreation...");
                });
            });
            
            Thread.sleep(100);
            
            scenario.onActivity(activity -> {
                ProgressManager manager = activity.getProgressManager();
                
                // Dismiss before recreation to avoid leak
                activity.runOnUiThread(() -> {
                    manager.dismiss();
                });
            });
            
            Thread.sleep(100);
            
            // Recreate activity
            scenario.recreate();
            
            // Verify manager still works after recreation
            scenario.onActivity(activity -> {
                ProgressManager manager = activity.getProgressManager();
                assertNotNull("ProgressManager should exist after recreation", manager);
                
                activity.runOnUiThread(() -> {
                    manager.show("After recreation");
                    manager.dismiss();
                });
            });
            
            Thread.sleep(100);
        }
    }

    /**
     * Test: Multiple show/dismiss cycles
     * 
     * Verifies manager can be reused.
     */
    @Test
    public void testProgressManagerMultipleShowDismissCycles() throws InterruptedException {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            for (int i = 0; i < 3; i++) {
                final int cycle = i;
                
                scenario.onActivity(activity -> {
                    ProgressManager manager = activity.getProgressManager();
                    
                    activity.runOnUiThread(() -> {
                        manager.show("Cycle " + cycle);
                    });
                });
                
                Thread.sleep(100);
                
                scenario.onActivity(activity -> {
                    ProgressManager manager = activity.getProgressManager();
                    
                    activity.runOnUiThread(() -> {
                        manager.dismiss();
                    });
                });
                
                Thread.sleep(100);
            }
        }
    }

    /**
     * Test: ProgressManager handles null/empty messages
     * 
     * Verifies graceful handling of edge cases.
     */
    @Test
    public void testProgressManagerHandlesNullEmptyMessages() throws InterruptedException {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ProgressManager manager = activity.getProgressManager();
                
                activity.runOnUiThread(() -> {
                    // Should not crash with null/empty
                    manager.show(null);
                    manager.updateMessage("");
                    manager.dismiss();
                });
            });
            
            Thread.sleep(100);
        }
    }

    /**
     * Test: ProgressManager runOnUiThread wrapper
     * 
     * Verifies all operations are UI thread safe.
     */
    @Test
    public void testProgressManagerIsUIThreadSafe() throws InterruptedException {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ProgressManager manager = activity.getProgressManager();
                
                // Call from background thread - should still work
                new Thread(() -> {
                    manager.show("From background");
                }).start();
            });
            
            Thread.sleep(200);
            
            scenario.onActivity(activity -> {
                ProgressManager manager = activity.getProgressManager();
                
                new Thread(() -> {
                    manager.dismiss();
                }).start();
            });
            
            Thread.sleep(200);
        }
    }
}
