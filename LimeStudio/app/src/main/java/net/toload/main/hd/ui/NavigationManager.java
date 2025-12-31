package net.toload.main.hd.ui;

import android.util.Log;

import androidx.fragment.app.FragmentManager;

import net.toload.main.hd.R;
import net.toload.main.hd.data.ImConfig;
import net.toload.main.hd.ui.view.ManageImFragment;
import net.toload.main.hd.ui.view.ManageRelatedFragment;
import net.toload.main.hd.ui.view.NavigationDrawerFragment;
import net.toload.main.hd.ui.view.SetupImFragment;

import java.util.List;

/**
 * Manages navigation between fragments in MainActivity.
 * 
 * <p>This class encapsulates all navigation-related functionality, including:
 * <ul>
 *   <li>Fragment transaction management</li>
 *   <li>Navigation drawer item selection handling</li>
 *   <li>ActionBar title updates based on current section</li>
 *   <li>IM list management for navigation</li>
 * </ul>
 * 
 * <p>This extraction reduces MainActivity's complexity and provides a dedicated
 * component for managing fragment navigation flow.
 */
public class NavigationManager implements NavigationDrawerFragment.NavigationDrawerCallbacks {
    
    private static final String TAG = "NavigationManager";
    
    private final MainActivity activity;
    private List<ImConfig> imConfigFullNameList;
    private CharSequence currentTitle;
    
    /**
     * Data class representing a navigation menu item.
     * Used for rendering navigation drawer items.
     */
    public static class NavigationMenuItem {
        private final String code;
        private final String title;
        private final int iconResId;
        
        public NavigationMenuItem(String code, String title, int iconResId) {
            this.code = code;
            this.title = title;
            this.iconResId = iconResId;
        }
        
        public String getCode() { return code; }
        public String getTitle() { return title; }
        public int getIconResId() { return iconResId; }
    }
    
    /**
     * Creates a new NavigationManager.
     * 
     * @param activity The activity context for navigation operations
     */
    public NavigationManager(MainActivity activity) {
        this.activity = activity;
    }
    
    /**
     * Sets the IM list used for navigation.
     * 
     * <p>The IM list is used to determine which fragment to display when
     * a user selects an IM from the navigation drawer (positions 2+).
     * 
     * @param imConfigList The list of available IM tables
     */
    public void setImConfigFullNameList(List<ImConfig> imConfigList) {
        this.imConfigFullNameList = imConfigList;
    }
    

    private int selectedPosition = -1;

    /**
     * Sets the currently selected navigation position.
     * 
     * @param position The selected position
     */
    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
    }

    /**
     * Gets the currently selected navigation position.
     * 
     * @return The selected position, or -1 if none selected
     */
    public int getSelectedPosition() {
        return this.selectedPosition;
    }
    
    /**
     * Callback from NavigationDrawerFragment when a navigation item is selected.
     * 
     * <p>This method is called when the user selects an item from the navigation
     * drawer. It delegates to {@link #navigateToFragment(int)} to handle the actual
     * navigation.
     * 
     * @param position The position of the selected item in the navigation drawer
     */
    @Override
    public void onNavigationDrawerItemSelected(int position) {
        navigateToFragment(position);
    }
    
    /**
     * Navigates to a fragment based on the selected position.
     * 
     * <p>This method handles navigation to different fragments:
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
     */
    public void navigateToFragment(int position) {
        FragmentManager fragmentManager = activity.getSupportFragmentManager();
        
        if (position == 0) {
            // Navigate to SetupImFragment (IM setup)
            fragmentManager.beginTransaction()
                    .replace(R.id.container, SetupImFragment.newInstance(position), "SetupImFragment")
                    .addToBackStack("SetupImFragment")
                    .commit();
            updateTitle(position);
        } else if (position == 1) {
            // Navigate to ManageRelatedFragment (related phrases)
            fragmentManager.beginTransaction()
                    .replace(R.id.container, ManageRelatedFragment.newInstance(position), "ManageRelatedFragment")
                    .addToBackStack("ManageRelatedFragment")
                    .commit();
            updateTitle(position);
        } else {
            // Navigate to ManageImFragment for specific IM table
            if (imConfigFullNameList == null || imConfigFullNameList.isEmpty()) {
                Log.w(TAG, "IM list is empty, cannot navigate to position " + position);
                return;
            }
            
            int imIndex = position - 2;
            
            // Validate index
            if (imIndex < 0 || imIndex >= imConfigFullNameList.size()) {
                Log.w(TAG, "Invalid IM index: " + imIndex + " (list size: " + imConfigFullNameList.size() + ")");
                return;
            }
            
            String tableName = imConfigFullNameList.get(imIndex).getCode();
            fragmentManager.beginTransaction()
                    .replace(R.id.container, ManageImFragment.newInstance(position, tableName), 
                             "ManageImFragment_" + tableName)
                    .addToBackStack("ManageImFragment_" + tableName)
                    .commit();
            updateTitle(position);
        }
    }
    
    /**
     * Updates the ActionBar title based on the current section.
     * 
     * <p>This method updates the title displayed in the ActionBar:
     * <ul>
     *   <li>Position 0: "Initial" (IM setup)</li>
     *   <li>Position 1: "Related" (related phrases)</li>
     *   <li>Position 2+: The IM description from imList</li>
     * </ul>
     * 
     * @param position The current section position
     */
    public void updateTitle(int position) {
        if (position == 0) {
            currentTitle = activity.getResources().getString(R.string.default_menu_initial);
        } else if (position == 1) {
            currentTitle = activity.getResources().getString(R.string.default_menu_related);
        } else {
            int imIndex = position - 2;
            
            // Validate IM list and index
            if (imConfigFullNameList != null && !imConfigFullNameList.isEmpty() && imIndex >= 0 && imIndex < imConfigFullNameList.size()) {
                currentTitle = imConfigFullNameList.get(imIndex).getDesc();
            } else {
                // Fallback to empty string if invalid
                currentTitle = "";
                Log.w(TAG, "Cannot update title - invalid IM index: " + imIndex);
            }
        }
        
        // Update the ActionBar with the new title
        activity.restoreActionBar();
    }
    
    /**
     * Gets the current title displayed in the ActionBar.
     * 
     * @return The current title, or null if not set
     */
    public CharSequence getCurrentTitle() {
        return currentTitle;
    }
    

}
