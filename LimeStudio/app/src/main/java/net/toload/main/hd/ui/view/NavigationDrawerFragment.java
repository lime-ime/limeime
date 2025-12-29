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
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

package net.toload.main.hd.ui.view;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.view.MenuProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import net.toload.main.hd.R;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.global.LIMEUtilities;
import net.toload.main.hd.ui.MainActivity;
import net.toload.main.hd.ui.NavigationManager;
import net.toload.main.hd.ui.controller.SetupImController;
import net.toload.main.hd.ui.dialog.HelpDialog;
import net.toload.main.hd.ui.dialog.ShareDialog;

import java.util.List;

/**
 * Fragment used for managing interactions for and presentation of a navigation drawer.
 * See the <a href="https://developer.android.com/design/patterns/navigation-drawer.html#Interaction">
 * design guidelines</a> for a complete explanation of the behaviors implemented here.
 */
public class NavigationDrawerFragment extends Fragment implements NavigationDrawerView {

    private static final String TAG = "NavigationDrawerFrag";

    /**
     * Remember the position of the selected item.
     */
    private static final String STATE_SELECTED_POSITION = "selected_navigation_drawer_position";

    /**
     * Per the design guidelines, you should show the drawer on launch until the user manually
     * expands it. This shared preference tracks this.
     */
    private static final String PREF_USER_LEARNED_DRAWER = "navigation_drawer_learned";

    /**
     * Helper component that ties the action bar to the navigation drawer.
     */
    private ActionBarDrawerToggle mDrawerToggle;

    private DrawerLayout mDrawerLayout;
    private ListView mDrawerListView;
    private View mFragmentContainerView;

    private int mCurrentSelectedPosition = 0;
    private boolean mUserLearnedDrawer;


    private LIMEPreferenceManager mLIMEPref;

    private SetupImController setupImController;


    public NavigationDrawerFragment(){}

    @Override
    public void onResume() {
        super.onResume();

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Construct Preference Manager object
        mLIMEPref = new LIMEPreferenceManager(getActivity());

        // Read in the flag indicating whether or not the user has demonstrated awareness of the
        // drawer. See PREF_USER_LEARNED_DRAWER for details.

        mUserLearnedDrawer = mLIMEPref.getParameterBoolean(PREF_USER_LEARNED_DRAWER, false);

        if (savedInstanceState != null) {
            mCurrentSelectedPosition = savedInstanceState.getInt(STATE_SELECTED_POSITION);
            //boolean mFromSavedInstanceState = true;
        }

        // Select either the default item (0) or the last selected item.
        selectItem(mCurrentSelectedPosition);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Get the shared SetupImController from MainActivity
        // Controllers are guaranteed to be initialized before fragments are created
        if (getActivity() instanceof MainActivity) {
            setupImController = ((MainActivity) getActivity()).getSetupImController();
            if (setupImController != null) {
                setupImController.setNavigationDrawerView(this);
                NavigationManager navigationManager = ((MainActivity) getActivity()).getNavigationManager();
                if (navigationManager != null) {
                    setupImController.setNavigationCallbacks(navigationManager);
                }
            }
        }

        mDrawerListView = (ListView) inflater.inflate(
                R.layout.fragment_navigation_drawer, container, false);
        // Use selectItem to ensure the drawer is closed after an item is selected
        mDrawerListView.setOnItemClickListener((parent, view, position, id) -> selectItem(position));
        
        // Handle window insets for edge-to-edge - drawer should account for ActionBar + status bar
        setupDrawerInsets();

        // Load menu items via controller
        setupImController.loadNavigationMenu();
        mDrawerListView.setItemChecked(mCurrentSelectedPosition, true);

        // Add MenuProvider to handle menu creation and selection
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                // If the drawer is open, show the global app actions in the action bar. See also
                // showGlobalContextActionBar, which controls the top-left area of the action bar.
                if (mDrawerLayout != null && isDrawerOpen()) {
                    menuInflater.inflate(R.menu.main, menu);
                    showGlobalContextActionBar();
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (mDrawerToggle.onOptionsItemSelected(menuItem)) {
                    return true;
                }

                if(menuItem.getItemId() == R.id.action_share){
                    FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                    // Pass the required String arguments for the dialog title and share message.
                    ShareDialog dialog = ShareDialog.newInstance();
                    dialog.show(ft, "share_dialog"); // Corrected the dialog tag as well
                    return true;
                }else if(menuItem.getItemId() == R.id.action_preference){
                    Intent setting = new Intent(getActivity(), net.toload.main.hd.ui.LIMEPreference.class);
                    startActivity(setting);
                    return true;
                    //}
                }else if(menuItem.getItemId() == R.id.action_help){
                    FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                    HelpDialog dialog = HelpDialog.newInstance();
                    dialog.show(ft, "helpdialog");
                    return true;
                }else if(menuItem.getItemId() == R.id.action_reset){
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setTitle(getResources().getString(R.string.reset_dialog_title));
                    builder.setMessage(getResources().getString(R.string.reset_dialog_confirm));
                    builder.setCancelable(false);
                    builder.setPositiveButton(getResources().getString(R.string.dialog_confirm),
                            (dialog, id) -> {
                                // Reset Lime preferences
                                SharedPreferences settings = requireActivity().getSharedPreferences(requireActivity().getPackageName() + "_preferences", Context.MODE_PRIVATE);
                                settings.edit().clear().apply();

                                // Reset Lime databases via controller
                                if (setupImController != null) {
                                    setupImController.restoredToDefault();
                                }
                                
                                // Close all activities in the task and let Android manage process lifecycle
                                Activity activity = getActivity();
                                if (activity != null) {
                                    activity.finishAffinity();
                                }

                            });
                    builder.setNegativeButton(getResources().getString(R.string.dialog_cancel),
                            (dialog, id) -> {
                            });

                    AlertDialog alert = builder.create();
                    alert.show();
                    return true;
                }

                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        return mDrawerListView;
    }

    public boolean isDrawerOpen() {
        return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(mFragmentContainerView);
    }

    /**
     * Users of this fragment must call this method to set up the navigation drawer interactions.
     *
     * @param fragmentId   The android:id of this fragment in its activity's layout.
     * @param drawerLayout The DrawerLayout containing this fragment's UI.
     */
    public void setUp(int fragmentId, DrawerLayout drawerLayout) {

        mFragmentContainerView = requireActivity().findViewById(fragmentId);
        mDrawerLayout = drawerLayout;

        // set a custom shadow that overlays the main content when the drawer opens
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        // set up the drawer's list view with items and click listener

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);

        // ActionBarDrawerToggle ties together the the proper interactions
        // between the navigation drawer and the action bar app icon.
        mDrawerToggle = new ActionBarDrawerToggle(
                getActivity(),                    /* host Activity */
                mDrawerLayout,                    /* DrawerLayout object */
               // R.drawable.ic_drawer,             /* nav drawer image to replace 'Up' caret */
                R.string.navigation_drawer_open,  /* "open drawer" description for accessibility */
                R.string.navigation_drawer_close  /* "close drawer" description for accessibility */
        ) {
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);

                if (!isAdded()) {
                    return;
                }

                requireActivity().invalidateOptionsMenu(); // calls onPrepareOptionsMenu()
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);

                // Reset ImList
                updateMenuItems();

                if (!isAdded()) {
                    return;
                }

                if (!mUserLearnedDrawer) {
                    // The user manually opened the drawer; store this flag to prevent auto-showing
                    // the navigation drawer automatically in the future.
                    mUserLearnedDrawer = true;
                    mLIMEPref.setParameter(PREF_USER_LEARNED_DRAWER, true);
                }

                requireActivity().invalidateOptionsMenu(); // calls onPrepareOptionsMenu()
            }
        };

        // If the user hasn't 'learned' about the drawer, open it to introduce them to the drawer,
        // per the navigation drawer design guidelines.
        // If LIME is not enabled, the setup wizard will be launched and thus don't open the drawer
        if (LIMEUtilities.isLIMEEnabled(this.requireActivity()) && !mUserLearnedDrawer) {
                //( !mUserLearnedDrawer && !mFromSavedInstanceState) ){
            mDrawerLayout.openDrawer(mFragmentContainerView);
        }

        // Defer code dependent on restoration of previous instance state.
        mDrawerLayout.post(() -> mDrawerToggle.syncState());

        mDrawerLayout.addDrawerListener(mDrawerToggle);
    }

    private void selectItem(int position) {
        if (setupImController != null) {
            setupImController.onNavigationItemSelected(position);
        }
        if (mDrawerLayout != null) {
            mDrawerLayout.closeDrawer(mFragmentContainerView);
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SELECTED_POSITION, mCurrentSelectedPosition);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Forward the new configuration the drawer toggle component.
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    /**
     * Per the navigation drawer design guidelines, updates the action bar to show the global app
     * 'context', rather than just what's in the current screen.
     */
    private void showGlobalContextActionBar() {
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
       // actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD); //deprecated after API 21
        actionBar.setTitle(R.string.app_name);
    }

    private ActionBar getActionBar() {
        return ((AppCompatActivity) requireActivity()).getSupportActionBar();
    }

    public void updateMenuItems() {
        if (setupImController != null) {
            setupImController.loadNavigationMenu();
        }
    }
    
    // ========== NavigationDrawerView Interface Implementation ==========
    
    @Override
    public void updateMenuItems(List<NavigationMenuItem> items) {
        if (mDrawerListView != null && items != null) {
            int menuCount = items.size();
            String[] menulist = new String[menuCount];
            
            // First two items are default menu items
            menulist[0] = this.getResources().getString(R.string.default_menu_initial);
            menulist[1] = this.getResources().getString(R.string.default_menu_related);
            
            // Add IM menu items
            for (int i = 2; i < items.size(); i++) {
                NavigationMenuItem item = items.get(i);
                if (item.getIm() != null) {
                    menulist[i] = item.getIm().getDesc();
                }
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    getActionBar().getThemedContext(),
                    android.R.layout.simple_list_item_activated_1,
                    android.R.id.text1, menulist);
            
            mDrawerListView.setAdapter(adapter);
            adapter.setNotifyOnChange(true);
        }
    }
    
    @Override
    public void setSelectedItem(int position) {
        mCurrentSelectedPosition = position;
        if (mDrawerListView != null) {
            mDrawerListView.setItemChecked(position, true);
        }
    }
    
    @Override
    public void refreshMenu() {
        updateMenuItems();
    }
    
    @Override
    public void onError(String message) {
        android.util.Log.e(TAG, message);
        if (getActivity() != null) {
            android.widget.Toast.makeText(getActivity(), message, android.widget.Toast.LENGTH_LONG).show();
        }
    }


    /**
     * Setup window insets for the drawer ListView to account for ActionBar and status bar.
     * This ensures drawer content is not obscured by the ActionBar in edge-to-edge mode.
     */
    private void setupDrawerInsets() {
        if (mDrawerListView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mDrawerListView, (v, insets) -> {
                int systemBarsType = WindowInsetsCompat.Type.systemBars();
                int topInset = insets.getInsets(systemBarsType).top;
                int bottomInset = insets.getInsets(systemBarsType).bottom;
                
                // Get ActionBar height to add to top padding
                int actionBarHeight = 0;
                ActionBar actionBar = getActionBar();
                if (actionBar != null) {
                    android.util.TypedValue tv = new android.util.TypedValue();
                    if (getActivity() != null && getActivity().getTheme().resolveAttribute(
                            android.R.attr.actionBarSize, tv, true)) {
                        actionBarHeight = android.util.TypedValue.complexToDimensionPixelSize(
                            tv.data, getResources().getDisplayMetrics());
                    }
                }
                
                // Apply top padding = status bar + ActionBar (drawer needs to account for ActionBar),
                // bottom padding = navigation bar
                // Left and right should extend to edges
                v.setPadding(0, topInset + actionBarHeight, 0, bottomInset);
                
                return insets;
            });
        }
    }

    /**
     * Callbacks interface that all activities using this fragment must implement.
     */
    public  interface NavigationDrawerCallbacks {
        /**
         * Called when an item in the navigation drawer is selected.
         */
        void onNavigationDrawerItemSelected(int position);
    }
}
