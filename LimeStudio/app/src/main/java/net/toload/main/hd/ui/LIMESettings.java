package net.toload.main.hd.ui;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowInsetsControllerCompat;
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
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigationrail.NavigationRailView;
import net.toload.main.hd.ui.view.LIMESettingsView;

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
 * <p>LIMESettings serves as the primary container and coordinator for the IME management UI.
 * It manages the lifecycle of all major controllers, managers, and UI fragments, ensuring
 * they are properly initialized before fragments are instantiated.
 *
 * <h2>Architecture</h2>
 * <p>The activity follows a clean architecture pattern with clear separation of concerns:
 * <ul>
 *   <li><b>Controllers</b>: {@link SetupImController}, {@link ManageImController} - handle business logic</li>
 *   <li><b>Managers</b>: {@link NavigationManager}, {@link ProgressManager}, {@link ShareManager} - manage UI concerns</li>
 *   <li><b>Handlers</b>: {@link IntentHandler} - process incoming intents</li>
 *   <li><b>Fragments</b>: SetupFragment, TwoPaneHostFragment, LimePreferenceFragment, DbManagerFragment - provide UI</li>
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
 *   <li>Tab selection and legacy position mapping</li>
 *   <li>ActionBar title updates</li>
 * </ul>
 *
 * <h2>UI Updates</h2>
 * <p>This activity implements {@link LIMESettingsView} to provide UI update callbacks:
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
 * @see LIMESettingsView
 * @see NavigationManager
 * @see SetupImController
 * @see ManageImController
 * @see ProgressManager
 */
public class LIMESettings extends AppCompatActivity implements LIMESettingsView {

    static {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }


    private static final String TAG = "LIMESettings";

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

        // Hide the activity-level ActionBar so each fragment's MaterialToolbar
        // becomes the sole top bar (prevents double-bar stacking).
        androidx.appcompat.app.ActionBar ab = getSupportActionBar();
        if (ab != null) ab.hide();

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
        setupImController.setNavigationManager(navigationManager);

        // initial imList
        navigationManager.setImConfigFullNameList(manageImController.getImConfigFullNameList());

        // Wire bottom nav (phone) or navigation rail (tablet) — whichever is present in the layout
        BottomNavigationView bottomNav = findViewById(R.id.main_bottom_nav);
        NavigationRailView navRail = findViewById(R.id.main_nav_rail);

        if (bottomNav != null) {
            bottomNav.setOnItemSelectedListener(item -> {
                onTabSelected(item.getItemId());
                return true;
            });
        }
        if (navRail != null) {
            navRail.setOnItemSelectedListener(item -> {
                onTabSelected(item.getItemId());
                return true;
            });
        }

        // If activity is started fresh (not restoring), show tab 0 (設定)
        // Skip initial navigation in test mode to prevent blocking startActivitySync()
        if (savedInstanceState == null && !isRunningInTestMode()) {
            onTabSelected(R.id.nav_setup);
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
        if (shouldShowInitialHelpDialog(currentVersion, versionStr)) {
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

    public static boolean shouldShowInitialHelpDialog(String currentVersion, String versionStr) {
        return false;
    }



    /**
     * Navigates to a fragment based on the selected position.
     *
     * <p>This method implements {@link LIMESettingsView} and delegates the actual navigation
     * to {@link NavigationManager}, which handles fragment transactions, back stack management,
     * and title updates.
     *
     * <p>This method handles navigation to different fragments based on the selected
     * position:
     * <ul>
     *   <li>Position 0: Shows the setup tab</li>
     *   <li>Position 1+: Shows the input-method manager tab</li>
     * </ul>
     *
     * <p>All fragment transactions are added to the back stack to allow navigation
     * back through the history.
     *
     * @param position The legacy navigation position
     * @see NavigationManager#navigateToFragment(int)
     */
    @Override
    public void navigateToFragment(int position) {
        // Map old drawer positions to tab item IDs:
        // 0 = 設定 tab, 1+ = 輸入法 tab (IM management)
        int itemId;
        if (position == 0) {
            itemId = R.id.nav_setup;
        } else {
            itemId = R.id.nav_im;
        }
        onTabSelected(itemId);
        // Sync the selected tab indicator on whichever nav control is present
        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav = findViewById(R.id.main_bottom_nav);
        if (bottomNav != null) bottomNav.setSelectedItemId(itemId);
        com.google.android.material.navigationrail.NavigationRailView navRail = findViewById(R.id.main_nav_rail);
        if (navRail != null) navRail.setSelectedItemId(itemId);
    }

    private void onTabSelected(int itemId) {
        androidx.fragment.app.Fragment fragment;
        if (itemId == R.id.nav_setup) {
            fragment = net.toload.main.hd.ui.view.SetupFragment.newInstance();
        } else if (itemId == R.id.nav_im) {
            fragment = net.toload.main.hd.ui.view.TwoPaneHostFragment.newInstance();
        } else if (itemId == R.id.nav_prefs) {
            fragment = net.toload.main.hd.ui.view.LimePreferenceFragment.newInstance();
        } else if (itemId == R.id.nav_db) {
            fragment = net.toload.main.hd.ui.view.DbManagerFragment.newInstance();
        } else {
            fragment = net.toload.main.hd.ui.view.SetupFragment.newInstance();
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.main_fragment_container, fragment)
                .commit();
    }

    /**
     * Shows a progress overlay with an optional message.
     *
     * <p>This method implements {@link LIMESettingsView} and delegates to
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
     * <p>This method implements {@link LIMESettingsView} and delegates to
     * {@link ProgressManager} to dismiss the progress dialog or hide the overlay.
     */
    @Override
    public void hideProgress() {
        if (progressManager != null) progressManager.dismiss();
    }

    /**
     * Shows a toast message to the user.
     *
     * <p>This method implements {@link LIMESettingsView} and delegates to
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
     * <p>This method implements {@link LIMESettingsView} and provides a way for
     * controllers to request the activity to close itself.
     */
    @Override
    public void finishActivity() {
        finish();
    }

    /**
     * Handles an error by logging and displaying a toast message.
     *
     * <p>This method implements {@link LIMESettingsView} and is called when an
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
     * <p>This method implements {@link LIMESettingsView} and is called during long-running
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
     * <p>No-op stub kept for compatibility with ManageImFragment and ManageRelatedFragment.
     *
     * @param number The section number (unused)
     */
    /** No-op stub kept for compatibility with ManageImFragment and ManageRelatedFragment. */
    public void onSectionAttached(int number) {
        // no-op — bottom nav / nav rail replaces the old drawer navigation
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
        View container = findViewById(R.id.main_fragment_container);
        if (container != null) {
            ViewCompat.setOnApplyWindowInsetsListener(container, (v, insets) -> {
                int systemBarsType = WindowInsetsCompat.Type.systemBars();
                int topInset = insets.getInsets(systemBarsType).top;
                int bottomInset = insets.getInsets(systemBarsType).bottom;
                int leftInset = insets.getInsets(systemBarsType).left;
                int rightInset = insets.getInsets(systemBarsType).right;

                // Apply padding: top = status bar only (ActionBar handles its own space),
                // left/right/bottom = system bars
                v.setPadding(leftInset, topInset, rightInset, 0);

                return insets;
            });
        }

        // Set status bar and navigation bar to transparent for edge-to-edge effect
        // This works on all API levels, but is required for API 35+
        // Note: setStatusBarColor and setNavigationBarColor are deprecated in API 35+,
        // but we use them with suppression for backward compatibility

        android.view.Window window = getWindow();
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        int uiMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isLight = (uiMode != Configuration.UI_MODE_NIGHT_YES);
        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            WindowInsetsControllerCompat controller =
                    new WindowInsetsControllerCompat(getWindow(), decorView);
            controller.setAppearanceLightStatusBars(isLight);
            controller.setAppearanceLightNavigationBars(isLight);
        } else {
            // API 21-22: cannot toggle icon brightness; use solid dark bars so the
            // default white icons remain visible regardless of theme.
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
     * overlays through the coordinator (LIMESettings) without needing to manage
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
