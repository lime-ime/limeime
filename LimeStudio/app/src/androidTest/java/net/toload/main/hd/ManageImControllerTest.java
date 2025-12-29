package net.toload.main.hd;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.toload.main.hd.SearchServer;
import net.toload.main.hd.data.Record;
import net.toload.main.hd.ui.controller.ManageImController;
import net.toload.main.hd.ui.view.ManageImView;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotNull;

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
}
