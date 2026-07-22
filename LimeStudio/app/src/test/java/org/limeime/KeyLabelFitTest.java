package org.limeime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.limeime.keyboard.KeyLabelFit;

public class KeyLabelFitTest {

    @Test
    public void shrinksToFitWidthOrHeightWithoutGoingBelowMinimum() {
        assertEquals(0.5f, KeyLabelFit.scale(50, 40, 100, 20, 0.5f), 0.001f);
        assertEquals(0.6f, KeyLabelFit.scale(100, 30, 50, 100, 0.6f), 0.001f);
        assertEquals(1f, KeyLabelFit.scale(100, 40, 80, 30, 0.5f), 0.001f);
    }

    @Test
    public void portraitSplitKeysKeepLabelsStackedEvenWhenKeysAreWiderThanTall() {
        assertTrue(KeyLabelFit.useVerticalLabels(true, 66, 64, 2, false));
        assertFalse(KeyLabelFit.useVerticalLabels(false, 66, 64, 2, false));
        assertTrue(KeyLabelFit.useVerticalLabels(false, 66, 64, 3, false));
        assertTrue(KeyLabelFit.useVerticalLabels(false, 66, 64, 2, true));
    }
}
