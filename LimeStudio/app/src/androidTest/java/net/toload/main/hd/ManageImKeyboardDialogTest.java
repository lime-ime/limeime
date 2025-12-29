/*
 * Copyright 2025, The LimeIME Open Source Project
 */
package net.toload.main.hd;

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
            Class<?> cls = Class.forName("net.toload.main.hd.ui.dialog.ManageImKeyboardDialog");
            assertNotNull(cls);
        } catch (ClassNotFoundException e) {
            fail("ManageImKeyboardDialog class not found");
        }
    }

    @Test
    public void testActivityProvidesController() {
        try (androidx.test.core.app.ActivityScenario<net.toload.main.hd.ui.MainActivity> scenario = androidx.test.core.app.ActivityScenario.launch(net.toload.main.hd.ui.MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    java.lang.reflect.Method getter = activity.getClass().getMethod("getManageImController");
                    Object controller = getter.invoke(activity);
                    assertNotNull("MainActivity.getManageImController() should return controller", controller);
                } catch (Exception e) {
                    fail("MainActivity should expose getManageImController(): " + e.getMessage());
                }
            });
        }
    }

    @Test
    public void testControllerKeyboardApisExist() throws Exception {
        Class<?> ctrl = Class.forName("net.toload.main.hd.ui.controller.ManageImController");
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
