package net.toload.main.hd.ui.view;

import net.toload.main.hd.data.Im;

/**
 * Data class representing a navigation menu item.
 *
 * <p>This class wraps an Im object with additional navigation-specific information.
 */
public class NavigationMenuItem {
    private final Im im;
    private final int position;
    private final boolean isSelected;

    public NavigationMenuItem(Im im, int position, boolean isSelected) {
        this.im = im;
        this.position = position;
        this.isSelected = isSelected;
    }

    public Im getIm() {
        return im;
    }

    public int getPosition() {
        return position;
    }

    public boolean isSelected() {
        return isSelected;
    }
}
