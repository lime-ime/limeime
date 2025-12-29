package net.toload.main.hd.ui.view;

/**
 * Base interface for all view update listeners.
 * 
 * <p>This interface provides common methods for error handling and progress updates
 * that all views should implement.
 */
public interface ViewUpdateListener {
    /**
     * Called when an error occurs during an operation.
     * 
     * @param message The error message to display
     */
    void onError(String message);

}

