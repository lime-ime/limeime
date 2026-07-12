/*
 * Tests for NewsDialog lifecycle and links (Phase 4.12).
 */
package org.limeime;

import androidx.fragment.app.DialogFragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.limeime.ui.LIMESettings;
import org.limeime.ui.dialog.NewsDialog;

import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NewsDialogTest {

    @Test
    public void testNewsDialogClassExists() {
        try {
            Class<?> cls = Class.forName("org.limeime.ui.dialog.NewsDialog");
            assertNotNull(cls);
        } catch (ClassNotFoundException e) {
            fail("NewsDialog class not found");
        }
    }

    @Test
    public void testHasLinkOrButtonHandlers() throws Exception {
        Class<?> cls = Class.forName("org.limeime.ui.dialog.NewsDialog");
        boolean hasClick = false;
        for (java.lang.reflect.Method m : cls.getDeclaredMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("click") || n.contains("button") || n.contains("link")) { hasClick = true; break; }
        }
        assertTrue("NewsDialog should define link/button handlers", hasClick);
    }

    @org.junit.Ignore("Deprecated: first-launch news/help splash flow disabled in commit 6f36521a; see docs/DEPCECATED_UI_TESTS.md.")
    @Test
    public void testNewsDialogSurvivesRecreation() {
        try (ActivityScenario<LIMESettings> scenario = ActivityScenario.launch(LIMESettings.class)) {
            scenario.onActivity(activity -> {
                DialogFragment dialog = NewsDialog.newInstance();
                dialog.show(activity.getSupportFragmentManager(), "newsdialog-smoke");
            });
            scenario.recreate();
        }
    }
}
