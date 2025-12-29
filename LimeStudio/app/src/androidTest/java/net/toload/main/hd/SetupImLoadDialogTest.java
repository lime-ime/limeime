/*
 * Copyright 2025, The LimeIME Open Source Project
 */
package net.toload.main.hd;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Tests for SetupImLoadDialog (file selection & download delegation).
 */
@RunWith(AndroidJUnit4.class)
public class SetupImLoadDialogTest {

    @Test
    public void testSetupImLoadDialogClassExists() {
        try {
            Class<?> cls = Class.forName("net.toload.main.hd.ui.dialog.SetupImLoadDialog");
            assertNotNull(cls);
        } catch (ClassNotFoundException e) {
            fail("SetupImLoadDialog class not found");
        }
    }

    @Test
    public void testHasDelegationToDownloadImport() {
        try {
            Class<?> cls = Class.forName("net.toload.main.hd.ui.dialog.SetupImLoadDialog");
            boolean hasEitherMethod = false;
            // Support either legacy dialog method or updated import delegation
            for (java.lang.reflect.Method m : cls.getMethods()) {
                String n = m.getName();
                if (n.equals("downloadAndLoadIm") || n.equals("downloadAndImportZippedDb")) {
                    hasEitherMethod = true;
                    break;
                }
            }
            assertTrue("SetupImLoadDialog should delegate to a download/import method", hasEitherMethod);
        } catch (ClassNotFoundException e) {
            fail("SetupImLoadDialog class not found");
        }
    }

    @Test
    public void testLocalLimedbSelectionFlowApis() throws Exception {
        Class<?> dialog = Class.forName("net.toload.main.hd.ui.dialog.SetupImLoadDialog");
        Class<?> frag = Class.forName("net.toload.main.hd.ui.view.SetupImFragment");
        Class<?> ctrl = Class.forName("net.toload.main.hd.ui.controller.SetupImController");

        // Dialog should have some ActivityResultLauncher or similar picker method (heuristic: methods mentioning "picker" or "launcher")
        boolean hasPicker = false;
        for (java.lang.reflect.Method m : dialog.getMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("launch") || n.contains("picker") || n.contains("activityresult")) { hasPicker = true; break; }
        }
        assertTrue("SetupImLoadDialog should expose a picker/launcher method", hasPicker);

        // Fragment should expose importDb/importZippedDb
        boolean hasImportDb = false;
        for (java.lang.reflect.Method m : frag.getMethods()) {
            String n = m.getName();
            if (n.equals("importDb") || n.equals("importZippedDb")) { hasImportDb = true; break; }
        }
        assertTrue("SetupImFragment should expose importDb/importZippedDb", hasImportDb);

        // Controller should have importZippedDb
        boolean hasCtrlImportZip = false;
        for (java.lang.reflect.Method m : ctrl.getMethods()) {
            if (m.getName().equals("importZippedDb")) { hasCtrlImportZip = true; break; }
        }
        assertTrue("SetupImController.importZippedDb present", hasCtrlImportZip);
    }

    @Test
    public void testLocalTextSelectionFlowApis() throws Exception {
        Class<?> frag = Class.forName("net.toload.main.hd.ui.view.SetupImFragment");
        Class<?> ctrl = Class.forName("net.toload.main.hd.ui.controller.SetupImController");

        boolean hasImportTxtInFrag = false;
        for (java.lang.reflect.Method m : frag.getMethods()) {
            if (m.getName().equals("importTxtTable")) { hasImportTxtInFrag = true; break; }
        }
        assertTrue("SetupImFragment.importTxtTable present", hasImportTxtInFrag);

        boolean hasImportTxtInCtrl = false;
        for (java.lang.reflect.Method m : ctrl.getMethods()) {
            if (m.getName().equals("importTxtTable")) { hasImportTxtInCtrl = true; break; }
        }
        assertTrue("SetupImController.importTxtTable present", hasImportTxtInCtrl);
    }

    @Test
    public void testDownloadButtonFlowApis() throws Exception {
        Class<?> dialog = Class.forName("net.toload.main.hd.ui.dialog.SetupImLoadDialog");
        Class<?> frag = Class.forName("net.toload.main.hd.ui.view.SetupImFragment");
        Class<?> ctrl = Class.forName("net.toload.main.hd.ui.controller.SetupImController");

        // Fragment should route download to controller
        boolean hasFragmentDownload = false;
        for (java.lang.reflect.Method m : frag.getMethods()) {
            String n = m.getName();
            if (n.equals("downloadAndImportZippedDb")) { hasFragmentDownload = true; break; }
        }
        assertTrue("SetupImFragment.downloadAndImportZippedDb present", hasFragmentDownload);

        boolean hasCtrlDownload = false;
        for (java.lang.reflect.Method m : ctrl.getMethods()) {
            if (m.getName().equals("downloadAndImportZippedDb")) { hasCtrlDownload = true; break; }
        }
        assertTrue("SetupImController.downloadAndImportZippedDb present", hasCtrlDownload);

        // Dialog should have methods referencing download buttons (heuristic: method names containing "download")
        boolean hasDownloadHandler = false;
        for (java.lang.reflect.Method m : dialog.getMethods()) {
            if (m.getName().toLowerCase().contains("download")) { hasDownloadHandler = true; break; }
        }
        assertTrue("SetupImLoadDialog should have download handlers", hasDownloadHandler);
    }

    @Test
    public void testCheckboxAndValidationIndicators() throws Exception {
        Class<?> dialog = Class.forName("net.toload.main.hd.ui.dialog.SetupImLoadDialog");

        boolean hasCheckboxFields = false;
        for (java.lang.reflect.Field f : dialog.getDeclaredFields()) {
            String n = f.getName().toLowerCase();
            if (n.contains("checkbox") || n.contains("restore") || n.contains("backup")) { hasCheckboxFields = true; break; }
        }
        assertTrue("SetupImLoadDialog should have backup/restore checkbox fields", hasCheckboxFields);

        boolean hasValidationMethods = false;
        for (java.lang.reflect.Method m : dialog.getDeclaredMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("validate") || n.contains("check") || n.contains("extension")) { hasValidationMethods = true; break; }
        }
        assertTrue("SetupImLoadDialog should validate file types/inputs", hasValidationMethods);
    }

    @Test
    public void testErrorHandlingIndicators() throws Exception {
        Class<?> dialog = Class.forName("net.toload.main.hd.ui.dialog.SetupImLoadDialog");

        boolean hasErrorMethod = false;
        for (java.lang.reflect.Method m : dialog.getDeclaredMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("error") || n.contains("handle") || n.contains("failure") || n.contains("exception")) {
                hasErrorMethod = true;
                break;
            }
        }
        assertTrue("SetupImLoadDialog should expose error-handling methods", hasErrorMethod);
    }
}
