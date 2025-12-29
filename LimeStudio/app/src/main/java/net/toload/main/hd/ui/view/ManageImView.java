package net.toload.main.hd.ui.view;

import net.toload.main.hd.data.Record;
import java.util.List;

/**
 * Interface for ManageImFragment view updates.
 * 
 * <p>This interface defines the contract for ManageImFragment to receive
 * updates from ImController.
 */
public interface ManageImView extends ViewUpdateListener {
    /**
     * Displays the list of records.
     * 
     * @param records The list of records to display
     */
    void displayRecords(List<Record> records);
    
    /**
     * Updates the record count display.
     * 
     * @param count The total number of records
     */
    void updateRecordCount(int count);
    
    /**
     * Shows the add record dialog.
     */
    void showAddRecordDialog();
    
    /**
     * Shows the edit record dialog.
     * 
     * @param record The record to edit
     */
    void showEditRecordDialog(Record record);
    
    /**
     * Shows the delete confirmation dialog.
     * 
     * @param id The ID of the record to delete
     */
    void showDeleteConfirmDialog(long id);
    
    /**
     * Refreshes the record list display.
     */
    void refreshRecordList();
}

