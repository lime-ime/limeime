package org.limeime;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.limeime.SearchServer;
import org.limeime.data.Record;
import org.limeime.ui.controller.ManageImController;
import org.limeime.ui.view.ManageImView;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for ManageImController asynchronous loading paths.
 */
@RunWith(AndroidJUnit4.class)
public class ManageImControllerTest {

    private static class StubManageImView implements ManageImView {
        private final AtomicReference<String> errorRef;

        StubManageImView(AtomicReference<String> errorRef) {
            this.errorRef = errorRef;
        }

        @Override
        public void displayRecords(List<Record> records) { /* no-op */ }

        @Override
        public void updateRecordCount(int count) { /* no-op */ }

        @Override
        public void refreshRecordList() { /* no-op */ }

        @Override
        public void showDeleteConfirmDialog(long id) { /* no-op */ }

        @Override
        public void showEditRecordDialog(Record record) { /* no-op */ }

        @Override
        public void showAddRecordDialog() { /* no-op */ }

        @Override
        public void onError(String message) {
            errorRef.set(message);
        }


    }

    @Test
    public void loadRecordsAsync_invalidTable_reportsError() {
        SearchServer searchServer = new SearchServer(ApplicationProvider.getApplicationContext());
        ManageImController controller = new ManageImController(searchServer);

        AtomicReference<String> errorRef = new AtomicReference<>();
        controller.setManageImView(new StubManageImView(errorRef));

        // Invalid table name should trigger validation failure and onError
        controller.loadRecordsAsync("nonexistent_table", "", false, 0, 10);

        assertNotNull("Invalid table should report error", errorRef.get());
    }

    @Test
    public void updateIMMetadata_persistsNameAndVersion() {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        DBServer dbServer = DBServer.getInstance(context);
        SearchServer searchServer = new SearchServer(context);
        ManageImController controller = new ManageImController(searchServer);

        String suffix = String.valueOf(System.currentTimeMillis());
        String editedName = "Edited Custom " + suffix;
        String editedVersion = "Version " + suffix;

        assertTrue(controller.updateIMMetadata("custom", editedName, editedVersion));
        assertEquals(editedName, dbServer.getImConfig("custom", "name"));
        assertEquals(editedVersion, dbServer.getImConfig("custom", "version"));
    }

    @Test
    public void updateIMMetadata_rejectsEmptyName() {
        SearchServer searchServer = new SearchServer(ApplicationProvider.getApplicationContext());
        ManageImController controller = new ManageImController(searchServer);

        assertFalse(controller.updateIMMetadata("custom", "   ", "Version 2026.05"));
    }

    @Test
    public void updateIMMetadataField_persistsIndependentVersion() {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        DBServer dbServer = DBServer.getInstance(context);
        SearchServer searchServer = new SearchServer(context);
        ManageImController controller = new ManageImController(searchServer);

        String suffix = String.valueOf(System.currentTimeMillis());
        String editedVersion = "Independent Version " + suffix;

        assertTrue(controller.updateIMMetadataField("custom", "version", editedVersion));
        assertEquals(editedVersion, dbServer.getImConfig("custom", "version"));
    }

    @Test
    public void updateIMMetadataField_allowsLimeEndkey() {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        DBServer dbServer = DBServer.getInstance(context);
        SearchServer searchServer = new SearchServer(context);
        ManageImController controller = new ManageImController(searchServer);

        assertTrue(controller.updateIMMetadataField("custom", "limeendkey", " ;/ "));
        assertEquals(";/", dbServer.getImConfig("custom", "limeendkey"));

        assertTrue(controller.updateIMMetadataField("custom", "limeendkey", " "));
        assertEquals("", dbServer.getImConfig("custom", "limeendkey"));
    }
}
