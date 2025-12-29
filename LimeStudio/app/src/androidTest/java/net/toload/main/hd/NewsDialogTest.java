/*
 * Tests for NewsDialog lifecycle and links (Phase 4.12).
 */
package net.toload.main.hd;

import androidx.fragment.app.DialogFragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.toload.main.hd.ui.MainActivity;
import net.toload.main.hd.ui.dialog.NewsDialog;

import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NewsDialogTest {

    @Test
    public void testNewsDialogClassExists() {
        try {
            Class<?> cls = Class.forName("net.toload.main.hd.ui.dialog.NewsDialog");
            assertNotNull(cls);
        } catch (ClassNotFoundException e) {
            fail("NewsDialog class not found");
        }
    }

    @Test
    public void testHasLinkOrButtonHandlers() throws Exception {
        Class<?> cls = Class.forName("net.toload.main.hd.ui.dialog.NewsDialog");
        boolean hasClick = false;
        for (java.lang.reflect.Method m : cls.getDeclaredMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("click") || n.contains("button") || n.contains("link")) { hasClick = true; break; }
        }
        assertTrue("NewsDialog should define link/button handlers", hasClick);
    }

    @Test
    public void testNewsDialogSurvivesRecreation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                DialogFragment dialog = NewsDialog.newInstance();
                dialog.show(activity.getSupportFragmentManager(), "newsdialog-smoke");
            });
            scenario.recreate();
        }
    }
}
