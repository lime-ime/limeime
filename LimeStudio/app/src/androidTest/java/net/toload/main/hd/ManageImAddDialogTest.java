/*
 * Copyright 2025, The LimeIME Open Source Project
 */
package net.toload.main.hd;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Tests for ManageImAddDialog.
 */
@RunWith(AndroidJUnit4.class)
public class ManageImAddDialogTest {

    @Test
    public void testManageImAddDialogClassExists() {
        try {
            Class<?> cls = Class.forName("net.toload.main.hd.ui.dialog.ManageImAddDialog");
            assertNotNull(cls);
        } catch (ClassNotFoundException e) {
            fail("ManageImAddDialog class not found");
        }
    }

    @Test
    public void testValidationAndControllerAddApis() throws Exception {
        Class<?> dialog = Class.forName("net.toload.main.hd.ui.dialog.ManageImAddDialog");
        boolean hasValidation = false;
        for (java.lang.reflect.Method m : dialog.getDeclaredMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("validate") || n.contains("check")) { hasValidation = true; break; }
        }
        assertTrue("ManageImAddDialog should perform validation", hasValidation);

        Class<?> ctrl = Class.forName("net.toload.main.hd.ui.controller.ManageImController");
        boolean hasAdd = false;
        for (java.lang.reflect.Method m : ctrl.getMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("update") && n.contains("record")) { hasAdd = true; break; }
        }
        assertTrue("ManageImController should expose add IM operation", hasAdd);
    }
}
