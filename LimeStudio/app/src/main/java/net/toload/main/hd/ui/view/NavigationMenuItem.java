package net.toload.main.hd.ui.view;

import net.toload.main.hd.data.ImConfig;

/**
 * Data class representing a navigation menu item.
 *
 * <p>This class wraps an Im object with additional navigation-specific information.
 */
public class NavigationMenuItem {
    private final ImConfig imConfig;
    private final int position;
    private final boolean isSelected;

    public NavigationMenuItem(ImConfig imConfig, int position, boolean isSelected) {
        this.imConfig = imConfig;
        this.position = position;
        this.isSelected = isSelected;
    }

    public ImConfig getIm() {
        return imConfig;
    }

    public int getPosition() {
        return position;
    }

    public boolean isSelected() {
        return isSelected;
    }
}
