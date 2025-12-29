/*
 * Copyright 2025, The LimeIME Open Source Project
 */
package net.toload.main.hd;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Tests for ManageRelatedAddDialog.
 */
@RunWith(AndroidJUnit4.class)
public class ManageRelatedAddDialogTest {

    @Test
    public void testManageRelatedAddDialogClassExists() {
        try {
            Class<?> cls = Class.forName("net.toload.main.hd.ui.dialog.ManageRelatedAddDialog");
            assertNotNull(cls);
        } catch (ClassNotFoundException e) {
            fail("ManageRelatedAddDialog class not found");
        }
    }

    @Test
    public void testValidationAndControllerAddRelatedApis() throws Exception {
        Class<?> dialog = Class.forName("net.toload.main.hd.ui.dialog.ManageRelatedAddDialog");
        boolean hasValidation = false;
        for (java.lang.reflect.Method m : dialog.getDeclaredMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("validate") || n.contains("check")) { hasValidation = true; break; }
        }
        assertTrue("ManageRelatedAddDialog should perform validation", hasValidation);

        Class<?> ctrl = Class.forName("net.toload.main.hd.ui.controller.ManageImController");
        boolean hasAddRelated = false;
        for (java.lang.reflect.Method m : ctrl.getMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("add") && n.contains("related")) { hasAddRelated = true; break; }
        }
        assertTrue("ManageImController should expose add related operation", hasAddRelated);
    }
}
