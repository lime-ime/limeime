/*
 * Copyright 2026, The LimeIME Open Source Project
 */
package org.limeime;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Tests for ManageImKeyboardDialog (keyboard assignment delegation).
 */
@RunWith(AndroidJUnit4.class)
public class ManageImKeyboardDialogTest {

    @Test
    public void testManageImKeyboardDialogClassExists() {
        try {
            Class<?> cls = Class.forName("org.limeime.ui.dialog.ManageImKeyboardDialog");
            assertNotNull(cls);
        } catch (ClassNotFoundException e) {
            fail("ManageImKeyboardDialog class not found");
        }
    }

    @Test
    public void testActivityProvidesController() {
        try (androidx.test.core.app.ActivityScenario<org.limeime.ui.LIMESettings> scenario = androidx.test.core.app.ActivityScenario.launch(org.limeime.ui.LIMESettings.class)) {
            scenario.onActivity(activity -> {
                try {
                    java.lang.reflect.Method getter = activity.getClass().getMethod("getManageImController");
                    Object controller = getter.invoke(activity);
                    assertNotNull("LIMESettings.getManageImController() should return controller", controller);
                } catch (Exception e) {
                    fail("LIMESettings should expose getManageImController(): " + e.getMessage());
                }
            });
        }
    }

    @Test
    public void testControllerKeyboardApisExist() throws Exception {
        Class<?> ctrl = Class.forName("org.limeime.ui.controller.ManageImController");
        boolean hasGetKeyboardList = false, hasSetImKeyboard = false;
        for (java.lang.reflect.Method m : ctrl.getMethods()) {
            String n = m.getName();
            if (n.equals("getKeyboardList")) hasGetKeyboardList = true;
            if (n.equals("setIMKeyboard")) hasSetImKeyboard = true;
        }
        assertTrue("ManageImController.getKeyboardList() present", hasGetKeyboardList);
        assertTrue("ManageImController.setIMKeyboard(...) present", hasSetImKeyboard);
    }
}
