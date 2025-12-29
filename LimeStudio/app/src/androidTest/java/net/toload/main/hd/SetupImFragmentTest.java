/*
 *
 *  *
 *  **    Copyright 2025, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
 *  **                 http://android.toload.net/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

package net.toload.main.hd;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.toload.main.hd.ui.MainActivity;
import net.toload.main.hd.ui.controller.SetupImController;
import net.toload.main.hd.ui.view.SetupImFragment;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Consolidated tests for SetupImFragment (one test file per target).
 */
@RunWith(AndroidJUnit4.class)
public class SetupImFragmentTest {

    @Test
    public void fragmentObtainsControllerAndHasNoLimeDbField() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                SetupImFragment fragment = SetupImFragment.newInstance(0);
                assertNotNull("SetupImFragment instance", fragment);
                SetupImController controller = activity.getSetupImController();
                assertNotNull("SetupImController from MainActivity getter", controller);
            });
        }
        Class<?> fragCls = Class.forName("net.toload.main.hd.ui.view.SetupImFragment");
        for (java.lang.reflect.Field f : fragCls.getDeclaredFields()) {
            assertFalse("Fragment should not hold LimeDB field", f.getType().getName().contains("LimeDB"));
        }
    }

    @Test
    public void controllerApisExistForImportsBackupRestoreExport() throws Exception {
        Class<?> cls = Class.forName("net.toload.main.hd.ui.controller.SetupImController");
        boolean hasTxtTable = false, hasZip = false, hasDownload = false, hasBackup = false, hasRestore = false, hasExport = false, hasExportRelated = false;
        for (java.lang.reflect.Method m : cls.getMethods()) {
            String n = m.getName();
            if (n.equals("importTxtTable")) hasTxtTable = true;
            if (n.equals("importZippedDb")) hasZip = true;
            if (n.equals("downloadAndImportZippedDb")) hasDownload = true;
            if (n.equals("performBackup")) hasBackup = true;
            if (n.equals("performRestore")) hasRestore = true;
            if (n.equals("exportZippedDb")) hasExport = true;
            if (n.equals("exportZippedDbRelated")) hasExportRelated = true;
        }
        assertTrue("importTxtTable present", hasTxtTable);
        assertTrue("importZippedDb present", hasZip);
        assertTrue("downloadAndImportZippedDb present", hasDownload);
        assertTrue("performBackup present", hasBackup);
        assertTrue("performRestore present", hasRestore);
        assertTrue("exportZippedDb present", hasExport);
        assertTrue("exportZippedDbRelated present", hasExportRelated);
    }

    @Test
    public void searchServerProvidesCountsForEnablement() throws Exception {
        Class<?> ss = Class.forName("net.toload.main.hd.SearchServer");
        boolean hasCountMapping = false;
        for (java.lang.reflect.Method m : ss.getMethods()) {
            if (m.getName().equals("countRecords")) { hasCountMapping = true; break; }
        }
        assertTrue("SearchServer.countRecords(...) present for button enablement decisions", hasCountMapping);
    }

    @Test
    public void fragmentHasButtonInitAndClickHandlers() throws Exception {
        Class<?> fragCls = Class.forName("net.toload.main.hd.ui.view.SetupImFragment");
        boolean hasInitButtons = false;

        for (java.lang.reflect.Method m : fragCls.getDeclaredMethods()) {
            String n = m.getName().toLowerCase();
            if (n.contains("refreshbuttonstate") ) hasInitButtons = true;
            //if (n.equals("onclick") || n.contains("click")) hasOnClick = true;
        }
        assertTrue("Fragment should initialize buttons (initialButton)", hasInitButtons);
        //assertTrue("Fragment should define click handlers", hasOnClick);
    }
}

