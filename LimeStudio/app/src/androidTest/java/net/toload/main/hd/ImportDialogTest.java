/*
 * Copyright 2025, The LimeIME Open Source Project
 */
package net.toload.main.hd;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Tests for ImportDialog (listener-based delegation).
 * Verifies class exists and exposes expected APIs.
 */
@RunWith(AndroidJUnit4.class)
public class ImportDialogTest {

    @Test
    public void testImportDialogClassExists() {
        try {
            Class<?> cls = Class.forName("net.toload.main.hd.ui.dialog.ImportDialog");
            assertNotNull(cls);
        } catch (ClassNotFoundException e) {
            fail("ImportDialog class not found");
        }
    }

    @Test
    public void testHasNewInstanceForFileFactory() {
        try {
            Class<?> cls = Class.forName("net.toload.main.hd.ui.dialog.ImportDialog");
            boolean hasFactory = false;
            for (java.lang.reflect.Method m : cls.getMethods()) {
                if (m.getName().contains("newInstance") && m.getParameterCount() > 0) {
                    hasFactory = true;
                    break;
                }
            }
            assertTrue("ImportDialog should have a factory method (newInstance...)", hasFactory);
        } catch (ClassNotFoundException e) {
            fail("ImportDialog class not found");
        }
    }

    @Test
    public void testDelegationPatternIndicators() {
        try {
            Class<?> cls = Class.forName("net.toload.main.hd.ui.dialog.ImportDialog");
            // Check that it likely references SetupImController listener
            boolean mentionsListener = false;
            for (Class<?> inner : cls.getDeclaredClasses()) {
                if (inner.getSimpleName().toLowerCase().contains("listener")) {
                    mentionsListener = true;
                    break;
                }
            }
            // Not strict, but ensures architecture intent
            assertTrue("ImportDialog should use a listener/delegation pattern", mentionsListener);
        } catch (ClassNotFoundException e) {
            fail("ImportDialog class not found");
        }
    }

    @Test
    public void testSetupIMControllerProvidesListAndCounts() throws Exception {
        Class<?> ss = Class.forName("net.toload.main.hd.ui.controller.SetupImController");
        boolean hasGetIm = false, hasCountMapping = false;
        for (java.lang.reflect.Method m : ss.getMethods()) {
            if (m.getName().equals("getImConfigList")) hasGetIm = true;
            if (m.getName().equals("countRecords")) hasCountMapping = true;
        }
        assertTrue("SearchServer.getImList() present", hasGetIm);
        assertTrue("SearchServer.countRecords(...) present", hasCountMapping);
    }

    @Test
    public void testControllerHandlesSelectionAndImport() throws Exception {
        Class<?> ctrl = Class.forName("net.toload.main.hd.ui.controller.SetupImController");
        boolean hasSelectionCallback = false;
        boolean hasImportText = false, hasImportZip = false;
        for (java.lang.reflect.Method m : ctrl.getMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("onimport") || n.contains("importdialog")) hasSelectionCallback = true;
            if (m.getName().equals("importTxtTable")) hasImportText = true;
            if (m.getName().equals("importZippedDb")) hasImportZip = true;
        }
        assertTrue("SetupImController should have an import dialog selection handler", hasSelectionCallback);
        assertTrue("SetupImController.importTxtTable present", hasImportText);
        assertTrue("SetupImController.importZippedDb present", hasImportZip);
    }

    @Test
    public void testDbServerProvidesImportOperations() throws Exception {
        Class<?> db = Class.forName("net.toload.main.hd.DBServer");
        boolean hasImportTxt = false, hasImportZip = false;
        for (java.lang.reflect.Method m : db.getMethods()) {
            if (m.getName().equals("importTxtTable")) hasImportTxt = true;
            if (m.getName().equals("importZippedDb")) hasImportZip = true;
        }
        assertTrue("DBServer.importTxtTable present", hasImportTxt);
        assertTrue("DBServer.importZippedDb present", hasImportZip);
    }
}
