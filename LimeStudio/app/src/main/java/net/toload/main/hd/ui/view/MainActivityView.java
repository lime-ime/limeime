package net.toload.main.hd.ui.view;

/**
 * Interface for MainActivity view updates.
 * 
 * <p>This interface defines the contract for MainActivity to receive updates
 * from MainController.
 */
public interface MainActivityView extends ViewUpdateListener {
    /**
     * Shows a progress dialog with the given message.
     * 
     * @param message The message to display in the progress dialog
     */
    void showProgress(String message);
    
    /**
     * Hides the progress dialog.
     */
    void hideProgress();
    
    /**
     * Shows a toast message to the user.
     * 
     * @param message The message to display
     * @param duration Toast.LENGTH_SHORT or Toast.LENGTH_LONG
     */
    void showToast(String message, int duration);
    
    /**
     * Navigates to the specified fragment.
     * 
     * @param position The fragment position to navigate to
     */
    void navigateToFragment(int position);
    
    /**
     * Finishes the activity.
     */
    void finishActivity();

    void onProgress( int percentage, String status);
}

