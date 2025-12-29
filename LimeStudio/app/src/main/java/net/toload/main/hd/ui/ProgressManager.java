package net.toload.main.hd.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import net.toload.main.hd.R;

import java.lang.ref.WeakReference;

/**
 * Manages progress dialog creation, display, and updates.
 * 
 * <p>This class encapsulates all progress dialog logic, including:
 * <ul>
 *   <li>Creating and showing progress dialogs</li>
 *   <li>Updating progress bar values</li>
 *   <li>Updating status text messages</li>
 *   <li>Dismissing and cleaning up dialogs</li>
 * </ul>
 * 
 * <p>This extraction reduces MainActivity's UI management complexity and provides
 * a reusable component for progress tracking across the application.
 */
public class ProgressManager {
    
    private final Context context;
    private AlertDialog progress;
    private final WeakReference<Activity> activityRef;

    // Shared overlay views (preferred over modal dialog when available)
    private View overlayContainer;
    private ProgressBar overlayBar;
    private TextView overlayText;

    /**
     * Alternative constructor for Activity integration.
     * 
     * @param context The context for creating dialogs
     */
    public ProgressManager(Context context) {
        this.context = context;
        this.activityRef = new WeakReference<>((Activity) context);

        // Try to bind to activity-level overlay if present
        Activity activity = this.activityRef.get();
        if (activity != null) {
            overlayContainer = activity.findViewById(R.id.activity_progress_overlay);
            overlayBar = activity.findViewById(R.id.activity_progress_bar);
            overlayText = activity.findViewById(R.id.activity_progress_text);
            if (overlayBar != null) {
                overlayBar.setMax(100);
            }
        }
    }
    
    /**
     * Shows the progress dialog if not already showing.
     * 
     * <p>If the dialog does not exist, it is created with a progress bar and
     * text area for status updates. The dialog is non-cancelable.
     */
    public void show() {
        runOnUiThread(() -> {
            // Prefer overlay when available
            if (overlayContainer != null) {
                overlayContainer.setVisibility(View.VISIBLE);
                if (overlayBar != null) {
                    overlayBar.setIndeterminate(true);
                    overlayBar.setProgress(1);
                }
                return;
            }

            // Fallback to modal dialog
            if (progress == null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setCancelable(false);
                View view = LayoutInflater.from(context).inflate(R.layout.progress, null);
                builder.setView(view);
                progress = builder.create();

            }
            if (!progress.isShowing()) {
                progress.show();
            }
            
        });
    }

    /**
     * Shows progress and optionally sets an initial message.
     *
     * @param message Optional status message to display
     */
    public void show(String message) {
        show();
        if (message != null) {
            updateProgress(message);
        }
    }
    
    /**
     * Dismisses the progress dialog if showing.
     * 
     * <p>Also nullifies the dialog reference to allow garbage collection.
     */
    public void dismiss() {
        runOnUiThread(() -> {
            // Prefer hiding overlay
            if (overlayContainer != null) {
                overlayContainer.setVisibility(View.GONE);
                return;
            }

            Activity activity = activityRef.get();
            if (activity != null && !activity.isDestroyed() && progress != null && progress.isShowing()) {
                progress.dismiss();
            }
            progress = null;
        });
    }
    
    /**
     * Updates the progress bar value.
     * 
     * <p>The progress value should be between 0 and 100.
     * If the dialog is not showing, this method does nothing.
     * 
     * @param value The progress value (0-100)
     */
    public void updateProgress(int value) {
        runOnUiThread(() -> {
            if (overlayBar != null && overlayContainer != null && overlayContainer.getVisibility() == View.VISIBLE) {
                overlayBar.setIndeterminate(false);
                overlayBar.setProgress(value);
                return;
            }

            if (progress != null && progress.isShowing()) {
                ProgressBar pb = progress.findViewById(R.id.progress_bar);
                if (pb != null) {
                    pb.setProgress(value);
                }
            }
        });
    }
    
    /**
     * Updates the progress dialog text.
     * 
     * <p>Displays a status message in the progress dialog.
     * If the dialog is not showing, this method does nothing.
     * 
     * @param message The status message to display
     */
    public void updateProgress(String message) {
        runOnUiThread(() -> {
            if (overlayText != null && overlayContainer != null && overlayContainer.getVisibility() == View.VISIBLE) {
                overlayText.setText(message);
                return;
            }

            if (progress != null && progress.isShowing()) {
                TextView tv = progress.findViewById(R.id.progress_text);
                if (tv != null) {
                    tv.setText(message);
                }
            }
        });
    }

    /**
     * Backward-compatible alias for updating the progress message.
     *
     * @param message The status message to display
     */
    public void updateMessage(String message) {
        updateProgress(message);
    }
    
    /**
     * Returns whether the progress dialog is currently showing.
     * 
     * @return true if the dialog is showing, false otherwise
     */
    public boolean isShowing() {
        return (overlayContainer != null && overlayContainer.getVisibility() == View.VISIBLE)
                || (progress != null && progress.isShowing());
    }
    
    /**
     * Runs a runnable on the UI thread.
     * 
     * <p>If the context is an Activity, this uses Activity.runOnUiThread.
     * Otherwise, it uses a simple post mechanism.
     * 
     * @param runnable The runnable to execute
     */
    private void runOnUiThread(Runnable runnable) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(runnable);
        } else {
            runnable.run();
        }
    }
}
