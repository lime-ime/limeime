package net.toload.main.hd.ui;

import android.util.Log;

import androidx.fragment.app.FragmentManager;

import net.toload.main.hd.R;
import net.toload.main.hd.data.ImConfig;
import net.toload.main.hd.ui.view.SetupFragment;
import net.toload.main.hd.ui.view.TwoPaneHostFragment;

import java.util.List;

/**
 * Manages navigation between fragments in LIMESettings.
 * 
 * <p>This class encapsulates all navigation-related functionality, including:
 * <ul>
 *   <li>Fragment transaction management</li>
 *   <li>Legacy drawer-position mapping to the new tab host</li>
 *   <li>ActionBar title updates based on current section</li>
 *   <li>IM list management for navigation</li>
 * </ul>
 * 
 * <p>This extraction reduces LIMESettings's complexity and provides a dedicated
 * component for managing fragment navigation flow.
 */
public class NavigationManager {
    
    private static final String TAG = "NavigationManager";
    
    private final LIMESettings activity;
    private List<ImConfig> imConfigFullNameList;
    private CharSequence currentTitle;
    
    /**
     * Creates a new NavigationManager.
     * 
     * @param activity The activity context for navigation operations
     */
    public NavigationManager(LIMESettings activity) {
        this.activity = activity;
    }
    
    /**
     * Sets the IM list used for navigation.
     * 
     * <p>The IM list is kept for legacy callers that still pass drawer positions.
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
     * Navigates to a fragment based on the selected position.
     * 
     * <p>This method maps old drawer positions to the new tab-based UI:
     * <ul>
     *   <li>Position 0: Shows SetupFragment (Setup tab)</li>
     *   <li>Position 1+: Shows TwoPaneHostFragment (IM tab)</li>
     * </ul>
     * 
     * <p>All fragment transactions are added to the back stack to allow navigation
     * back through the history.
     * 
     * @param position The legacy drawer position
     */
    public void navigateToFragment(int position) {
        FragmentManager fragmentManager = activity.getSupportFragmentManager();
        
        if (position == 0) {
            fragmentManager.beginTransaction()
                    .replace(R.id.main_fragment_container, SetupFragment.newInstance(), "SetupFragment")
                    .addToBackStack("SetupFragment")
                    .commit();
            updateTitle(position);
        } else {
            fragmentManager.beginTransaction()
                    .replace(R.id.main_fragment_container, TwoPaneHostFragment.newInstance(), "TwoPaneHostFragment")
                    .addToBackStack("TwoPaneHostFragment")
                    .commit();
            updateTitle(position);
        }
    }
    
    /**
     * Updates the ActionBar title based on the current section.
     * 
     * <p>This method updates the title displayed in the ActionBar:
     * <ul>
     *   <li>Position 0: "Initial" (setup)</li>
     *   <li>Position 1+: "Related" or the IM description from the legacy IM list</li>
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
