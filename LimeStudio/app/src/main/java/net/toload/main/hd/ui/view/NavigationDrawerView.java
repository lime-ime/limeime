package net.toload.main.hd.ui.view;

import net.toload.main.hd.ui.view.NavigationMenuItem;

import java.util.List;

/**
 * Interface for NavigationDrawerFragment view updates.
 * 
 * <p>This interface defines the contract for NavigationDrawerFragment to receive
 * updates from MainController.
 */
public interface NavigationDrawerView extends ViewUpdateListener {
    /**
     * Updates the menu items in the navigation drawer.
     * 
     * @param items The list of navigation menu items to display
     */
    void updateMenuItems(List<NavigationMenuItem> items);
    
    /**
     * Sets the currently selected item in the navigation drawer.
     * 
     * @param position The position of the selected item
     */
    void setSelectedItem(int position);
    
    /**
     * Refreshes the navigation menu.
     */
    void refreshMenu();
}

