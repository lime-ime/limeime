/*
 * Tests for HelpDialog lifecycle and links (Phase 4.12).
 */
package org.limeime;

import android.content.Context;
import androidx.fragment.app.DialogFragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.limeime.ui.LIMESettings;
import org.limeime.ui.dialog.HelpDialog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class HelpDialogTest {

    private static DBServer sharedDbServer = null;

    @Before
    public void setUp() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        if (sharedDbServer == null) {
            sharedDbServer = DBServer.getInstance(appContext);
        }
        
        // Wait for database to become available (up to 10 seconds)
        boolean stillOnHold = false;
        for (int i = 0; i < 100; i++) {
            if (!sharedDbServer.isDatabseOnHold()) {
                break;
            }
            stillOnHold = true;
            if (i < 99) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        // If database is still on hold after waiting, force release it
        if (stillOnHold && sharedDbServer.isDatabseOnHold()) {
            try {
                java.lang.reflect.Field datasourceField = DBServer.class.getDeclaredField("datasource");
                datasourceField.setAccessible(true);
                org.limeime.limedb.LimeDB datasource = (org.limeime.limedb.LimeDB) datasourceField.get(sharedDbServer);
                if (datasource != null) {
                    datasource.openDBConnection(true);
                }
            } catch (Exception e) {
                // Ignore reflection errors
            }
        }
    }

    @After
    public void tearDown() {
        if (sharedDbServer != null && sharedDbServer.isDatabseOnHold()) {
            try {
                java.lang.reflect.Field datasourceField = DBServer.class.getDeclaredField("datasource");
                datasourceField.setAccessible(true);
                org.limeime.limedb.LimeDB datasource = (org.limeime.limedb.LimeDB) datasourceField.get(sharedDbServer);
                if (datasource != null) {
                    datasource.openDBConnection(true);
                }
            } catch (Exception e) {
                // Ignore reflection errors
            }
        }
    }

    @Test
    public void testHelpDialogClassExists() {
        try {
            Class<?> cls = Class.forName("org.limeime.ui.dialog.HelpDialog");
            assertNotNull(cls);
        } catch (ClassNotFoundException e) {
            fail("HelpDialog class not found");
        }
    }

    @Test
    public void testHasLinkOrButtonHandlers() throws Exception {
        Class<?> cls = Class.forName("org.limeime.ui.dialog.HelpDialog");
        boolean hasClick = false;
        for (java.lang.reflect.Method m : cls.getDeclaredMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("click") || n.contains("button") || n.contains("link")) { hasClick = true; break; }
        }
        assertTrue("HelpDialog should define link/button handlers", hasClick);
    }

    /**
     * Test that HelpDialog can be shown and survives activity recreation.
     *
     * Note: This test has an extended timeout (60 seconds) to accommodate slower emulators.
     */
    @org.junit.Ignore("Deprecated: first-launch help splash disabled in commit 6f36521a and LIMESettings startup lifecycle changed (docs/LIME_SETTINGS_BACKPORT.md, docs/DEPCECATED_UI_TESTS.md).")
    @Test(timeout = 60000)
    public void testHelpDialogSurvivesRecreation() throws Exception {
        try (ActivityScenario<LIMESettings> scenario = ActivityScenario.launch(LIMESettings.class)) {
            // Show the HelpDialog
            scenario.onActivity(activity -> {
                DialogFragment dialog = HelpDialog.newInstance();
                dialog.show(activity.getSupportFragmentManager(), "helpdialog-smoke");
            });

            // Wait for dialog to be displayed
            Thread.sleep(500);

            // Verify dialog exists before recreation
            scenario.onActivity(activity -> {
                DialogFragment dialog = (DialogFragment) activity.getSupportFragmentManager()
                    .findFragmentByTag("helpdialog-smoke");
                assertNotNull("Dialog should be shown before recreation", dialog);
            });

            // Recreate activity (simulates configuration change)
            scenario.recreate();

            // Wait for recreation to complete
            Thread.sleep(500);

            // Verify dialog survives recreation
            scenario.onActivity(activity -> {
                DialogFragment dialog = (DialogFragment) activity.getSupportFragmentManager()
                    .findFragmentByTag("helpdialog-smoke");
                assertNotNull("Dialog should survive recreation", dialog);

                // Clean up - dismiss dialog
                if (dialog != null) {
                    dialog.dismissAllowingStateLoss();
                }
            });

            // Wait for dismiss to complete
            Thread.sleep(200);
        }
    }
}
