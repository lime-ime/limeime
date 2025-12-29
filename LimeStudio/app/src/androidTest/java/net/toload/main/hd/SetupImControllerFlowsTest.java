/*
 * Controller flow API presence tests for SetupImController and collaborators.
 * These tests verify architecture contracts for import/export/backup/restore flows
 * without performing heavy I/O operations.
 */
package net.toload.main.hd;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SetupImControllerFlowsTest {

    @Test
    public void textImportFlow_ApisExist() throws Exception {
        Class<?> controller = Class.forName("net.toload.main.hd.ui.controller.SetupImController");
        Class<?> searchServer = Class.forName("net.toload.main.hd.SearchServer");
        Class<?> dbServer = Class.forName("net.toload.main.hd.DBServer");

        boolean hasImportText = false;
        for (java.lang.reflect.Method m : controller.getMethods()) {
            if (m.getName().equals("importTxtTable")) { hasImportText = true; break; }
        }
        assertTrue("SetupImController.importTxtTable(...) should exist", hasImportText);

        boolean hasValidate = false, hasResetCache = false, hasBackupUsers = false, hasRestoreUsers = false, hasCheckBackupTable = false;
        for (java.lang.reflect.Method m : searchServer.getMethods()) {
            if (m.getName().equals("isValidTableName")) hasValidate = true;
            if (m.getName().equals("resetCache")) hasResetCache = true;
            if (m.getName().equals("backupUserRecords")) hasBackupUsers = true;
            if (m.getName().equals("restoreUserRecords")) hasRestoreUsers = true;
            if (m.getName().equals("checkBackupTable")) hasCheckBackupTable = true;
        }
        assertTrue("SearchServer.isValidTableName(...) present", hasValidate);
        assertTrue("SearchServer.resetCache() present", hasResetCache);
        assertTrue("SearchServer.backupUserRecords(...) present", hasBackupUsers);
        assertTrue("SearchServer.restoreUserRecords(...) present", hasRestoreUsers);
        assertTrue("SearchServer.checkBackupTable() present", hasCheckBackupTable);

        boolean hasImportTxt = false;
        for (java.lang.reflect.Method m : dbServer.getMethods()) {
            if (m.getName().equals("importTxtTable")) { hasImportTxt = true; break; }
        }
        assertTrue("DBServer.importTxtTable(...) present", hasImportTxt);
    }

    @Test
    public void zippedDbImportFlow_ApisExist() throws Exception {
        Class<?> controller = Class.forName("net.toload.main.hd.ui.controller.SetupImController");
        Class<?> searchServer = Class.forName("net.toload.main.hd.SearchServer");
        Class<?> dbServer = Class.forName("net.toload.main.hd.DBServer");

        boolean hasImportZip = false;
        for (java.lang.reflect.Method m : controller.getMethods()) {
            if (m.getName().equals("importZippedDb")) { hasImportZip = true; break; }
        }
        assertTrue("SetupImController.importZippedDb(...) should exist", hasImportZip);

        boolean hasValidate = false, hasResetCache = false;
        for (java.lang.reflect.Method m : searchServer.getMethods()) {
            if (m.getName().equals("isValidTableName")) hasValidate = true;
            if (m.getName().equals("resetCache")) hasResetCache = true;
        }
        assertTrue("SearchServer.isValidTableName(...) present", hasValidate);
        assertTrue("SearchServer.resetCache() present", hasResetCache);

        boolean hasImportZippedDb = false, hasImportZippedDbRelated = false;
        for (java.lang.reflect.Method m : dbServer.getMethods()) {
            if (m.getName().equals("importZippedDb")) hasImportZippedDb = true;
            if (m.getName().equals("importZippedDbRelated")) hasImportZippedDbRelated = true;
        }
        assertTrue("DBServer.importZippedDb(...) present", hasImportZippedDb);
        assertTrue("DBServer.importZippedDbRelated(...) present", hasImportZippedDbRelated);
    }

    @Test
    public void downloadAndImportFlow_ApisExist() throws Exception {
        Class<?> controller = Class.forName("net.toload.main.hd.ui.controller.SetupImController");
        Class<?> utilities = Class.forName("net.toload.main.hd.global.LIMEUtilities");
        Class<?> searchServer = Class.forName("net.toload.main.hd.SearchServer");

        boolean hasDownloadAndImport = false;
        for (java.lang.reflect.Method m : controller.getMethods()) {
            if (m.getName().equals("downloadAndImportZippedDb")) { hasDownloadAndImport = true; break; }
        }
        assertTrue("downloadAndImportZippedDb(...) present", hasDownloadAndImport);

        boolean hasDownloadRemoteFile = false;
        for (java.lang.reflect.Method m : utilities.getMethods()) {
            if (m.getName().equals("downloadRemoteFile")) { hasDownloadRemoteFile = true; break; }
        }
        assertTrue("LIMEUtilities.downloadRemoteFile(...) present", hasDownloadRemoteFile);

        boolean hasResetCache = false;
        for (java.lang.reflect.Method m : searchServer.getMethods()) {
            if (m.getName().equals("resetCache")) { hasResetCache = true; break; }
        }
        assertTrue("SearchServer.resetCache() present", hasResetCache);

        // Verify controller or its base has a Handler for main thread posting
        boolean hasHandler = false;
        for (java.lang.reflect.Field f : controller.getDeclaredFields()) {
            if (f.getType().getName().equals("android.os.Handler") || f.getName().toLowerCase().contains("handler")) {
                hasHandler = true; break;
            }
        }
        if (!hasHandler) {
            // Try BaseController
            Class<?> base;
            try {
                base = Class.forName("net.toload.main.hd.ui.controller.BaseController");
                for (java.lang.reflect.Field f : base.getDeclaredFields()) {
                    if (f.getType().getName().equals("android.os.Handler") || f.getName().toLowerCase().contains("handler")) {
                        hasHandler = true; break;
                    }
                }
            } catch (ClassNotFoundException ignore) { }
        }
        assertTrue("Controller/BaseController should have a main thread Handler", hasHandler);
    }

    @Test
    public void backupRestoreExportFlows_ApisExist() throws Exception {
        Class<?> controller = Class.forName("net.toload.main.hd.ui.controller.SetupImController");
        Class<?> dbServer = Class.forName("net.toload.main.hd.DBServer");
        Class<?> utilities = Class.forName("net.toload.main.hd.global.LIMEUtilities");

        boolean hasBackup = false, hasRestore = false, hasExport = false, hasExportRelated = false;
        for (java.lang.reflect.Method m : controller.getMethods()) {
            String n = m.getName();
            if (n.equals("performBackup")) hasBackup = true;
            if (n.equals("performRestore")) hasRestore = true;
            if (n.equals("exportZippedDb")) hasExport = true;
            if (n.equals("exportZippedDbRelated")) hasExportRelated = true;
        }
        assertTrue("performBackup(Uri) present", hasBackup);
        assertTrue("performRestore(Uri) present", hasRestore);
        assertTrue("exportZippedDb(...) present", hasExport);
        assertTrue("exportZippedDbRelated(...) present", hasExportRelated);

        boolean hasDbBackup = false, hasDbRestore = false, hasDbExport = false, hasDbExportRelated = false;
        for (java.lang.reflect.Method m : dbServer.getMethods()) {
            String n = m.getName();
            if (n.equals("backupDatabase")) hasDbBackup = true;
            if (n.equals("restoreDatabase")) hasDbRestore = true;
            if (n.equals("exportZippedDb")) hasDbExport = true;
            if (n.equals("exportZippedDbRelated")) hasDbExportRelated = true;
        }
        assertTrue("DBServer.backupDatabase(...) present", hasDbBackup);
        assertTrue("DBServer.restoreDatabase(...) present", hasDbRestore);
        assertTrue("DBServer.exportZippedDb(...) present", hasDbExport);
        assertTrue("DBServer.exportZippedDbRelated(...) present", hasDbExportRelated);

        boolean hasZip = false, hasUnzip = false;
        for (java.lang.reflect.Method m : utilities.getMethods()) {
            String n = m.getName();
            if (n.equals("zip")) hasZip = true;
            if (n.equals("unzip")) hasUnzip = true;
        }
        assertTrue("LIMEUtilities.zip(...) present", hasZip);
        assertTrue("LIMEUtilities.unzip(...) present", hasUnzip);
    }
}
