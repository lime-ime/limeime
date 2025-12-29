package net.toload.main.hd.ui.view;

import net.toload.main.hd.data.Related;
import java.util.List;

/**
 * Interface for ManageRelatedFragment view updates.
 * 
 * <p>This interface defines the contract for ManageRelatedFragment to receive
 * updates from ImController.
 */
public interface ManageRelatedView extends ViewUpdateListener {
    /**
     * Displays the list of related phrases.
     * 
     * @param phrases The list of related phrases to display
     */
    void displayRelatedPhrases(List<Related> phrases);
    
    /**
     * Updates the phrase count display.
     * 
     * @param count The total number of phrases
     */
    void updatePhraseCount(int count);
    
    /**
     * Shows the add phrase dialog.
     */
    void showAddPhraseDialog();
    
    /**
     * Shows the edit phrase dialog.
     * 
     * @param phrase The phrase to edit
     */
    void showEditPhraseDialog(Related phrase);
    
    /**
     * Shows the delete confirmation dialog.
     * 
     * @param id The ID of the phrase to delete
     */
    void showDeleteConfirmDialog(long id);
    
    /**
     * Refreshes the phrase list display.
     */
    void refreshPhraseList();
}

