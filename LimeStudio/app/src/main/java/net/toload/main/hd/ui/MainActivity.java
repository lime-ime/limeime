package net.toload.main.hd.ui;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentTransaction;

import net.toload.main.hd.DBServer;
import net.toload.main.hd.R;
import net.toload.main.hd.SearchServer;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.ui.controller.ManageImController;
import net.toload.main.hd.ui.controller.SetupImController;
import net.toload.main.hd.ui.dialog.HelpDialog;
import net.toload.main.hd.ui.dialog.NewsDialog;
import net.toload.main.hd.ui.view.MainActivityView;
import net.toload.main.hd.ui.view.NavigationDrawerFragment;

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
 */

/**
 * Main activity for the LimeIME application.
 * 
 * <p>MainActivity serves as the primary container and coordinator for the IME management UI.
 * It manages the lifecycle of all major controllers, managers, and UI fragments, ensuring
 * they are properly initialized before fragments are instantiated.
 * 
 * <h2>Architecture</h2>
 * <p>The activity follows a clean architecture pattern with clear separation of concerns:
 * <ul>
 *   <li><b>Controllers</b>: {@link SetupImController}, {@link ManageImController} - handle business logic</li>
 *   <li><b>Managers</b>: {@link NavigationManager}, {@link ProgressManager}, {@link ShareManager} - manage UI concerns</li>
 *   <li><b>Handlers</b>: {@link IntentHandler} - process incoming intents</li>
 *   <li><b>Fragments</b>: SetupImFragment, ManageRelatedFragment, ManageImFragment - provide UI</li>
 * </ul>
 * 
 * <h2>Initialization Sequence</h2>
 * <p>Controllers are initialized in {@link #onCreate(Bundle)} <b>BEFORE</b> {@code setContentView()}
 * to prevent race conditions when fragments are instantiated during layout inflation. This ensures
 * fragments can safely access controllers via getter methods.
 * 
 * <h2>Fragment Navigation</h2>
 * <p>Fragment navigation is delegated to {@link NavigationManager}, which orchestrates:
 * <ul>
 *   <li>Fragment transaction management</li>
 *   <li>Navigation drawer item selection</li>
 *   <li>ActionBar title updates</li>
 * </ul>
 * 
 * <h2>UI Updates</h2>
 * <p>This activity implements {@link MainActivityView} to provide UI update callbacks:
 * <ul>
 *   <li>{@link #navigateToFragment(int)} - navigate to fragment by position</li>
 *   <li>{@link #showProgress(String)} - show progress overlay</li>
 *   <li>{@link #hideProgress()} - hide progress overlay</li>
 *   <li>{@link #showToast(String, int)} - show toast message</li>
 *   <li>{@link #onError(String)} - handle errors</li>
 *   <li>{@link #onProgress(int, String)} - update progress status</li>
 * </ul>
 * 
 * <h2>Edge-to-Edge Display</h2>
 * <p>The activity supports edge-to-edge display on modern Android devices (API 21+) while
 * maintaining backward compatibility. Window insets are properly handled to avoid obscuring
 * UI elements behind system bars.
 * 
 * @see MainActivityView
 * @see NavigationManager
 * @see SetupImController
 * @see ManageImController
 * @see ProgressManager
 */
public class MainActivity extends AppCompatActivity implements MainActivityView {


    private static final String TAG = "MainActivity";

    // UI Components

    private CharSequence mTitle;
    private NavigationDrawerFragment mNavigationDrawerFragment;
    
    // Controllers
    private SetupImController setupImController;
    private ManageImController manageImController;


    // Handlers/Managers
    private IntentHandler intentHandler;
    private ProgressManager progressManager;
    private ShareManager shareManager;
    private NavigationManager navigationManager;
    
    // Import callback

    /**
     * Called when the activity is first created.
     * 
     * <p><b>IMPORTANT</b>: Controllers are initialized <b>BEFORE</b> {@code setContentView()} is called.
     * This is critical to prevent race conditions where fragments are instantiated during layout
     * inflation and need to access controllers via getter methods. The initialization order is:
     * <ol>
     *   <li>Create {@link SearchServer}, {@link DBServer} instances</li>
     *   <li>Create {@link ManageImController} and {@link SetupImController}</li>
     *   <li>Call {@code setContentView(R.layout.activity_main)}</li>
     *   <li>Create {@link ProgressManager}, {@link ShareManager}, {@link NavigationManager}</li>
     *   <li>Configure managers and register callbacks</li>
     * </ol>
     * 
     * <p>The activity also:
     * <ul>
     *   <li>Sets up edge-to-edge display</li>
     *   <li>Initializes preference manager and package name</li>
     *   <li>Registers navigation and intent callbacks</li>
     * </ul>
     * 
     * @param savedInstanceState If the activity is being re-initialized after previously
     *                           being shut down, this Bundle contains the most recent data
     *                           supplied. If not provided, this value will be null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Register back gesture/press callback for AndroidX
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
        
        // Initialize controllers BEFORE setContentView() to prevent race conditions
        // when fragments are instantiated during layout inflation
        // In test mode, use lightweight mock instances to avoid blocking database operations
        SearchServer searchServer;
        DBServer dbServer;
        if (isRunningInTestMode()) {
            // Use null for servers in test mode - controllers will handle gracefully
            searchServer = null;
            dbServer = null;
        } else {
            searchServer = new SearchServer(this);
            dbServer = DBServer.getInstance(this);
        }
        manageImController = new ManageImController(searchServer);
        setupImController = new SetupImController(this, dbServer, searchServer);
        
        // NOW inflate layout - fragments will find initialized controllers via getters
        setContentView(R.layout.activity_main);

        // Setup edge-to-edge display
        setupEdgeToEdge();


        //ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        LIMEPreferenceManager mLIMEPref = new LIMEPreferenceManager(this);

        LIME.PACKAGE_NAME = getApplicationContext().getPackageName();

        setupImController.setMainActivityView(this);

        // Initialize managers
        progressManager = new ProgressManager(this);
        shareManager = new ShareManager(this, setupImController, progressManager);
        navigationManager = new NavigationManager(this);
        
        // Set navigation callbacks to NavigationManager
        setupImController.setNavigationCallbacks(navigationManager);
        setupImController.setNavigationManager(navigationManager);

        // initial imList
        navigationManager.setImConfigFullNameList(manageImController.getImConfigFullNameList());

        // Set up the navigation drawer - fragments are now guaranteed to find initialized controllers
        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);

        // Set up the drawer.
        assert mNavigationDrawerFragment != null;
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                findViewById(R.id.drawer_layout));
        
        // If activity is started fresh (not restoring), show the default Setup IM fragment
        // Skip initial navigation in test mode to prevent blocking startActivitySync()
        if (savedInstanceState == null && !isRunningInTestMode()) {
            navigateToFragment(0);
        }


        // Delegate intent handling to IntentHandler
        if (intentHandler == null) {
            intentHandler = new IntentHandler(this, setupImController);
        }
        // Don't process intent in onCreate during tests to avoid blocking startActivitySync()
        if (!isRunningInTestMode()) {
            intentHandler.processIntent(getIntent());
        }

        String versionStr = "";
        PackageInfo pInfo;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            long versionCode = PackageInfoCompat.getLongVersionCode(pInfo);
            versionStr = getString(R.string.version_format, pInfo.versionName, versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error getting package info", e);
        }

        String currentVersion = mLIMEPref.getParameterString("current_version", "");
        if (currentVersion == null || currentVersion.isEmpty() || !currentVersion.equals(versionStr)) {
            // Skip HelpDialog in test environment to prevent blocking startActivitySync()
            boolean isTest = isRunningInTestMode();
            Log.d(TAG, "isRunningInTestMode: " + isTest);
            if (!isTest) {
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                HelpDialog dialog = HelpDialog.newInstance();
                dialog.show(ft, "helpdialog");
            } else {
                Log.d(TAG, "Skipping HelpDialog in test mode");
            }
            mLIMEPref.setParameter("current_version", versionStr);
        }

    }

    
    
    /**
     * Navigates to a fragment based on the selected position.
     * 
     * <p>This method implements {@link MainActivityView} and delegates the actual navigation
     * to {@link NavigationManager}, which handles fragment transactions, back stack management,
     * and title updates.
     * 
     * <p>This method handles navigation to different fragments based on the selected
     * position:
     * <ul>
     *   <li>Position 0: Shows SetupImFragment (IM setup)</li>
     *   <li>Position 1: Shows ManageRelatedFragment (related phrases)</li>
     *   <li>Position 2+: Shows ManageImFragment for the corresponding IM table</li>
     * </ul>
     * 
     * <p>All fragment transactions are added to the back stack to allow navigation
     * back through the history.
     * 
     * @param position The position of the selected item in the navigation drawer
     * @see NavigationManager#navigateToFragment(int)
     */
    @Override
    public void navigateToFragment(int position) {
        if (navigationManager != null) {
            navigationManager.navigateToFragment(position);
        }
    }
    
    /**
     * Shows a progress overlay with an optional message.
     * 
     * <p>This method implements {@link MainActivityView} and delegates to
     * {@link ProgressManager}, which displays a modal progress dialog or
     * an activity-level overlay depending on what's available.
     * 
     * <p>If a message is provided, it will be displayed in the progress view.
     * 
     * @param message The message to display in the progress view, or null/empty
     *                to show the progress view without a message
     */
    @Override
    public void showProgress(String message) {
        if (progressManager != null) {
            progressManager.show();
            if (message != null && !message.isEmpty()) {
                progressManager.updateProgress(message);
            }
        }
    }
    
    /**
     * Hides the progress overlay.
     * 
     * <p>This method implements {@link MainActivityView} and delegates to
     * {@link ProgressManager} to dismiss the progress dialog or hide the overlay.
     */
    @Override
    public void hideProgress() {
        if (progressManager != null) progressManager.dismiss();
    }
    
    /**
     * Shows a toast message to the user.
     * 
     * <p>This method implements {@link MainActivityView} and delegates to
     *
     * @param message The message text to display
     * @param duration The duration to show the message ({@code Toast.LENGTH_SHORT}
     *                 or {@code Toast.LENGTH_LONG})
     */
    @Override
    public void showToast(String message, int duration) {
        Toast toast = Toast.makeText(this, message, duration);
        toast.show();
    }

    /**
     * Finishes this activity.
     * 
     * <p>This method implements {@link MainActivityView} and provides a way for
     * controllers to request the activity to close itself.
     */
    @Override
    public void finishActivity() {
        finish();
    }
    
    /**
     * Handles an error by logging and displaying a toast message.
     * 
     * <p>This method implements {@link MainActivityView} and is called when an
     * error occurs in a controller or fragment. The error is logged at ERROR level
     * and displayed to the user as a long-duration toast.
     * 
     * @param message The error message to log and display
     */
    @Override
    public void onError(String message) {
        Log.e(TAG, message);
        showToast(message, Toast.LENGTH_LONG);
    }
    
    /**
     * Updates progress information on the progress overlay.
     * 
     * <p>This method implements {@link MainActivityView} and is called during long-running
     * operations to update the progress percentage and status message. Both parameters
     * are optional and only update their respective views if provided.
     * 
     * <p>This method only updates the progress if a progress view is currently showing.
     * 
     * @param percentage The progress percentage (0-100), or -1 to skip percentage update
     * @param status The status message to display, or null/empty to skip message update
     */
    @Override
    public void onProgress(int percentage, String status) {
        if (progressManager != null && progressManager.isShowing()) {
            if (status != null && !status.isEmpty()) {
                progressManager.updateProgress(status);
            }
            if (percentage >= 0) {
                progressManager.updateProgress(percentage);
            }
        }
    }

    /**
     * Called when a navigation section is attached to update the ActionBar title.
     * 
     * <p>This method is called by {@link NavigationDrawerFragment} when a navigation
     * item is selected. It delegates to {@link NavigationManager} to update the title
     * based on the section number:
     * <ul>
     *   <li>Section 0: Sets title to "Initial" (IM setup)</li>
     *   <li>Section 1: Sets title to "Related" (related phrases)</li>
     *   <li>Section 2+: Sets title to the IM description from imlist</li>
     * </ul>
     * 
     * <p>The title is later used by {@link #restoreActionBar()} to update the
     * ActionBar display.
     * 
     * @param number The section number (0 = initial, 1 = related, 2+ = IM index)
     * @see #restoreActionBar()
     * @see NavigationManager#updateTitle(int)
     */
    public void onSectionAttached(int number) {
        if (navigationManager != null) {
            navigationManager.updateTitle(number);
            mTitle = navigationManager.getCurrentTitle();
        }
    }

    /**
     * Restores the ActionBar title to the current section title.
     * 
     * <p>This method updates the ActionBar to display the title stored in mTitle or
     * fetched from {@link NavigationManager}. The title is set by {@link #onSectionAttached(int)}
     * when a navigation section is attached.
     * 
     * @see #onSectionAttached(int)
     * @see NavigationManager#getCurrentTitle()
     */
    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        //actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD); //setNavigationMode is deprecated after API21 (v5.0).
        if (actionBar == null) throw new AssertionError();
        actionBar.setDisplayShowTitleEnabled(true);
        CharSequence title = mTitle;
        if (navigationManager != null && navigationManager.getCurrentTitle() != null) {
            title = navigationManager.getCurrentTitle();
        }
        actionBar.setTitle(title);
    }


    /**
     * Initialize the contents of the Activity's standard options menu.
     * 
     * <p>This method is called when the options menu is first created. It inflates the
     * menu resource and restores the ActionBar title if the navigation drawer is not open.
     * If the drawer is open, the drawer decides what menu items to show in the action bar.
     * 
     * <p>This behavior prevents menu items from appearing cluttered when the navigation
     * drawer is visible, providing a cleaner user interface.
     * 
     * @param menu The options menu in which items are placed
     * @return True if the menu should be displayed, false otherwise
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.main, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * Called when an options menu item is selected.
     * 
     * <p>This method handles menu item selection. By default, it delegates to the parent
     * class implementation. Specific menu item handling is typically done by fragments
     * or the navigation drawer.
     * 
     * @param item The menu item that was selected
     * @return True if the event was handled, false otherwise
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    /**
     * Shows the news/message board dialog.
     * 
     * <p>This method displays a {@link NewsDialog} containing news, announcements, or
     * other information to the user. The dialog is shown using the FragmentManager
     * and added to the fragment transaction queue.
     * 
     * <p>If an error occurs while showing the dialog (e.g., activity has been destroyed),
     * the error is logged but not thrown. This prevents crashes if the activity is
     * finishing when this method is called.
     */
    public void showMessageBoard() {
        try {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            NewsDialog dialog = NewsDialog.newInstance();
            dialog.show(ft, "newsdialog");
        } catch (Exception e) {
            Log.e(TAG, "Error showing news dialog", e);
        }
    }

    /**
     * Setup edge-to-edge display with proper window insets handling.
     * 
     * <p>This method enables edge-to-edge display on modern Android devices (API 21+)
     * while maintaining backward compatibility. It handles:
     * <ul>
     *   <li>Window insets for main content container to avoid system bars</li>
     *   <li>Transparent status bar and navigation bar for full screen immersion</li>
     *   <li>Status bar icon color based on API level</li>
     * </ul>
     * 
     * <p><b>API Compatibility:</b>
     * <ul>
     *   <li><b>API 35+</b>: Uses modern window insets handling and transparent bars</li>
     *   <li><b>API 23-34</b>: Uses {@code setSystemUiVisibility()} for light status bar icons</li>
     *   <li><b>API 21-22</b>: Uses dark status bar (SYSTEM_UI_FLAG_LIGHT_STATUS_BAR not available)</li>
     * </ul>
     * 
     * <p>The method ensures UI elements are not obscured by system bars while maintaining
     * visual consistency across API levels.
     */
    @SuppressWarnings("deprecation")
    private void setupEdgeToEdge() {
        // Apply window insets to the main content container (FrameLayout)
        // ActionBar already handles its own space, so we only need to account for status bar
        View container = findViewById(R.id.container);
        if (container != null) {
            ViewCompat.setOnApplyWindowInsetsListener(container, (v, insets) -> {
                int systemBarsType = WindowInsetsCompat.Type.systemBars();
                int topInset = insets.getInsets(systemBarsType).top;
                int bottomInset = insets.getInsets(systemBarsType).bottom;
                int leftInset = insets.getInsets(systemBarsType).left;
                int rightInset = insets.getInsets(systemBarsType).right;
                
                // Apply padding: top = status bar only (ActionBar handles its own space),
                // left/right/bottom = system bars
                v.setPadding(leftInset, topInset, rightInset, bottomInset);

                return insets;
            });
        }
        
        // DrawerLayout extends to edges - no padding needed on DrawerLayout itself
        // The drawer fragment's ListView will handle its own content insets if needed

        // Set status bar and navigation bar to transparent for edge-to-edge effect
        // This works on all API levels, but is required for API 35+
        // Note: setStatusBarColor and setNavigationBarColor are deprecated in API 35+,
        // but we use them with suppression for backward compatibility

        android.view.Window window = getWindow();
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        // Set status bar icon appearance to dark (black icons) for better visibility.
        // Background is white/light, so we need dark icons to be legible.
        // SYSTEM_UI_FLAG_LIGHT_STATUS_BAR (when SET) = dark icons on light background.
        // SYSTEM_UI_FLAG_LIGHT_STATUS_BAR (when CLEARED) = light/white icons (invisible on white).
        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // API 26+: request dark icons on both status bar and navigation bar
            int flags = decorView.getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decorView.setSystemUiVisibility(flags);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // API 23-25: dark status bar icons available, but not navigation bar icons;
            // use solid dark navigation bar so default white system icons remain visible
            int flags = decorView.getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            decorView.setSystemUiVisibility(flags);
            getWindow().setNavigationBarColor(0xFF000000);
        } else {
            // API 21-22: neither light status bar nor light navigation bar flags available;
            // use solid dark bars so the default white system icons remain visible
            getWindow().setStatusBarColor(0xFF000000);
            getWindow().setNavigationBarColor(0xFF000000);
        }
    }

    /**
     * Called when the activity is becoming visible to the user.
     * 
     * <p>This method is called after {@link #onCreate(Bundle)} or after
     * {@link #onRestart()} if the activity was previously stopped. At this point,
     * the activity is visible but may not be in the foreground.
     * 
     * <p>Currently, this method performs minimal work. Subclasses may override to
     * perform initialization that requires the activity to be visible.
     */
    @Override
    public void onStart() {
        super.onStart();
    }


    /**
     * Gets the SetupImController instance.
     * 
     * <p>This method is called by fragments to access the SetupImController,
     * which manages import, export, and setup operations. The controller is
     * guaranteed to be initialized in {@link #onCreate(Bundle)} before
     * fragments are instantiated.
     * 
     * @return The SetupImController instance
     */
    public SetupImController getSetupImController() {
        return setupImController;
    }

    /**
     * Gets the ManageImController instance.
     * 
     * <p>This method is called by fragments to access the ManageImController,
     * which manages IM table operations. The controller is guaranteed to be
     * initialized in {@link #onCreate(Bundle)} before fragments are instantiated.
     * 
     * @return The ManageImController instance
     */
    public ManageImController getManageImController() {
        return manageImController;
    }

    /**
     * Gets the NavigationManager instance.
     * 
     * <p>This method is called by fragments to access the NavigationManager,
     * which handles fragment navigation and title updates. The manager is
     * guaranteed to be initialized in {@link #onCreate(Bundle)}.
     * 
     * <p>The NavigationManager implements {@link NavigationDrawerFragment.NavigationDrawerCallbacks}
     * to handle navigation drawer item selection.
     * 
     * @return The NavigationManager instance
     * @see NavigationManager
     */
    public NavigationManager getNavigationManager() {
        return navigationManager;
    }

    /**
     * Gets the ShareManager instance.
     * 
     * <p>This method is called by dialogs to access the ShareManager,
     * which handles share operations and dialog coordination. The manager
     * is guaranteed to be initialized in {@link #onCreate(Bundle)}.
     * 
     * @return The ShareManager instance
     */
    public ShareManager getShareManager() {
        return shareManager;
    }

    /**
     * Gets the ProgressManager instance.
     *
     * <p>This allows fragments and dialogs to show or hide activity-level progress
     * overlays through the coordinator (MainActivity) without needing to manage
     * their own progress UI.
     *
     * @return The ProgressManager instance
     */
    public ProgressManager getProgressManager() {
        return progressManager;
    }

    /**
     * Checks if the app is running in test mode (instrumentation tests).
     * 
     * <p>This is used to skip UI dialogs (like HelpDialog) that would block
     * test execution by preventing ActivityScenario.launch() from completing.
     * 
     * @return true if running under instrumentation tests, false otherwise
     */
    private boolean isRunningInTestMode() {
        // Check if test runner class is available in the classpath
        // This is the most reliable way that doesn't depend on process state
        try {
            Class.forName("androidx.test.runner.AndroidJUnitRunner");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }


}
