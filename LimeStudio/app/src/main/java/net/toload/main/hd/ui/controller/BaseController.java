package net.toload.main.hd.ui.controller;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import net.toload.main.hd.ui.view.MainActivityView;
import net.toload.main.hd.ui.view.ViewUpdateListener;

/**
 * Abstract base class for all controllers.
 * 
 * <p>This class provides common functionality for controllers, including
 * error handling and logging.
 */
public abstract class BaseController {
    protected static final String TAG = "BaseController";
    protected final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    /**
     * Handles an error by logging it and notifying the view if available.
     * Safe to call from any thread - UI operations are posted to main thread.
     * 
     * @param view The view to notify (may be null)
     * @param message The error message
     * @param exception The exception that occurred (may be null)
     */
    protected void handleError(ViewUpdateListener view, String message, Exception exception) {
        if (exception != null) {
            Log.e(TAG, message, exception);
        } else {
            Log.e(TAG, message);
        }
        
        if (view != null) {
            // If already on main thread, call directly; otherwise post
            if (Looper.myLooper() == Looper.getMainLooper()) {
                view.onError(message);
            } else {
                mainHandler.post(() -> view.onError(message));
            }
        }
    }
    
    /**
     * Updates progress on the view if available.
     * Safe to call from any thread - UI operations are posted to main thread.
     * 
     * @param view The view to update (may be null)
     * @param percentage The progress percentage (0-100)
     * @param status The status message
     */
    protected void updateProgress(MainActivityView view, int percentage, String status) {
        if (view != null) {
            mainHandler.post(() -> view.onProgress(percentage, status));
        }
    }
    
    /**
     * Shows progress on the view if available.
     * Safe to call from any thread - UI operations are posted to main thread.
     * 
     * @param view The view to update (may be null)
     * @param message The progress message to display
     */
    protected void showProgress(MainActivityView view, String message) {
        if (view != null) {
            mainHandler.post(() -> view.showProgress(message));
        }
    }
    
    /**
     * Hides progress on the view if available.
     * Safe to call from any thread - UI operations are posted to main thread.
     * 
     * @param view The view to update (may be null)
     */
    protected void hideProgress(MainActivityView view) {
        if (view != null) {
            mainHandler.post(view::hideProgress);
        }
    }
    
    /**
     * Shows a toast message on the view if available.
     * Safe to call from any thread - UI operations are posted to main thread.
     * 
     * @param view The view to update (may be null)
     * @param message The toast message to display
     * @param duration The toast duration (e.g., Toast.LENGTH_SHORT or Toast.LENGTH_LONG)
     */
    protected void showToast(MainActivityView view, String message, int duration) {
        if (view != null) {
            mainHandler.post(() -> view.showToast(message, duration));
        }
    }
    
    /**
     * Shows progress with Object view parameter (for test compatibility).
     * 
     * @param view The view object
     * @param message The progress message
     */
    public void showProgress(Object view, String message) {
        if (view instanceof MainActivityView) {
            showProgress((MainActivityView) view, message);
        }
    }
    
    /**
     * Hides progress with Object view parameter (for test compatibility).
     * 
     * @param view The view object
     */
    public void hideProgress(Object view) {
        if (view instanceof MainActivityView) {
            hideProgress((MainActivityView) view);
        }
    }
    
    /**
     * Shows toast with Object view parameter (for test compatibility).
     * 
     * @param view The view object
     * @param message The toast message
     * @param duration The toast duration
     */
    public void showToast(Object view, String message, int duration) {
        if (view instanceof MainActivityView) {
            showToast((MainActivityView) view, message, duration);
        }
    }
}
