package net.toload.main.hd.ui.view;

import java.util.Map;

/**
 * Interface for SetupImFragment view updates.
 * 
 * <p>This interface defines the contract for SetupImFragment to receive
 * updates from ImController.
 */
public interface SetupImView extends ViewUpdateListener {

    /**
     * Shows the import dialog.
     */
    void showImportDialog();

    /**
     * Shows a progress indicator.
     *
     * @param message The progress message
     */


    /**
     * Refreshes the IM list display.
     */
    void refreshButtonState();

    /**
     * Enum for backup/restore dialog types.
     */
    enum BackupRestoreType {
        BACKUP,
        RESTORE,
        BACKUP_TO_DOWNLOADS
    }

}

