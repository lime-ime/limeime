/*
 * Copyright 2025, The LimeIME Open Source Project
 */
package net.toload.main.hd;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Tests for ManageRelatedEditDialog.
 */
@RunWith(AndroidJUnit4.class)
public class ManageRelatedEditDialogTest {

    @Test
    public void testManageRelatedEditDialogClassExists() {
        try {
            Class<?> cls = Class.forName("net.toload.main.hd.ui.dialog.ManageRelatedEditDialog");
            assertNotNull(cls);
        } catch (ClassNotFoundException e) {
            fail("ManageRelatedEditDialog class not found");
        }
    }

    @Test
    public void testValidationAndControllerUpdateRelatedApis() throws Exception {
        Class<?> dialog = Class.forName("net.toload.main.hd.ui.dialog.ManageRelatedEditDialog");
        boolean hasValidation = false;
        for (java.lang.reflect.Method m : dialog.getDeclaredMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("validate") || n.contains("check")) { hasValidation = true; break; }
        }
        assertTrue("ManageRelatedEditDialog should perform validation", hasValidation);

        Class<?> ctrl = Class.forName("net.toload.main.hd.ui.controller.ManageImController");
        boolean hasUpdateRelated = false;
        for (java.lang.reflect.Method m : ctrl.getMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("update") && n.contains("related")) { hasUpdateRelated = true; break; }
        }
        assertTrue("ManageImController should expose update related operation", hasUpdateRelated);
    }
}
