package org.limeime;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.limeime.keyboard.LIMEBaseKeyboard;

/**
 * Issue #169 regression: the legacy split_keyboard_mode contract. Through
 * v6.1.32 SPLIT_KEYBOARD_ALWAYS rendered split in BOTH orientations, phones
 * included; v6.1.33 gated it behind (tablet || landscape) so portrait phone
 * split stopped rendering. These assertions pin the v6.1.32 contract.
 */
public class SplitKeyboardModeTest {

    private static final int NEVER = LIMEBaseKeyboard.SPLIT_KEYBOARD_NEVER;          // 0
    private static final int ALWAYS = LIMEBaseKeyboard.SPLIT_KEYBOARD_ALWAYS;        // 1
    private static final int LANDSCAPE_ONLY = LIMEBaseKeyboard.SPLIT_KEYBOARD_LANDSCAPD_ONLY; // 2

    private static final boolean PORTRAIT = false, LANDSCAPE = true;
    private static final boolean PHONE = false, TABLET = true;
    private static final boolean ELIGIBLE = true, NUMPAD = false;
    private static final int NO_ARROWS = 0, ARROWS = 1;

    // THE regression: ALWAYS must render split in portrait on a phone (v6.1.32 behavior).
    @Test
    public void alwaysSplitsInPortraitOnPhone() {
        assertTrue(LIMEBaseKeyboard.splitKeyboardEligible(ELIGIBLE, PORTRAIT, PHONE, NO_ARROWS, ALWAYS));
    }

    @Test
    public void alwaysSplitsInLandscapeOnPhone() {
        assertTrue(LIMEBaseKeyboard.splitKeyboardEligible(ELIGIBLE, LANDSCAPE, PHONE, NO_ARROWS, ALWAYS));
    }

    @Test
    public void alwaysSplitsInPortraitOnTablet() {
        assertTrue(LIMEBaseKeyboard.splitKeyboardEligible(ELIGIBLE, PORTRAIT, TABLET, NO_ARROWS, ALWAYS));
    }

    // LANDSCAPE_ONLY stays landscape-only in both form factors.
    @Test
    public void landscapeOnlyDoesNotSplitInPortrait() {
        assertFalse(LIMEBaseKeyboard.splitKeyboardEligible(ELIGIBLE, PORTRAIT, PHONE, NO_ARROWS, LANDSCAPE_ONLY));
        assertFalse(LIMEBaseKeyboard.splitKeyboardEligible(ELIGIBLE, PORTRAIT, TABLET, NO_ARROWS, LANDSCAPE_ONLY));
    }

    @Test
    public void landscapeOnlySplitsInLandscape() {
        assertTrue(LIMEBaseKeyboard.splitKeyboardEligible(ELIGIBLE, LANDSCAPE, PHONE, NO_ARROWS, LANDSCAPE_ONLY));
    }

    // NEVER never splits, regardless of orientation / form factor.
    @Test
    public void neverDoesNotSplit() {
        assertFalse(LIMEBaseKeyboard.splitKeyboardEligible(ELIGIBLE, PORTRAIT, PHONE, NO_ARROWS, NEVER));
        assertFalse(LIMEBaseKeyboard.splitKeyboardEligible(ELIGIBLE, LANDSCAPE, TABLET, NO_ARROWS, NEVER));
    }

    // Numpad / T9 layouts are ineligible even under ALWAYS.
    @Test
    public void numpadNeverSplitsEvenWithAlways() {
        assertFalse(LIMEBaseKeyboard.splitKeyboardEligible(NUMPAD, PORTRAIT, PHONE, NO_ARROWS, ALWAYS));
        assertFalse(LIMEBaseKeyboard.splitKeyboardEligible(NUMPAD, LANDSCAPE, TABLET, ARROWS, ALWAYS));
    }

    // Landscape arrow-key split trigger is landscape-only (unchanged legacy behavior).
    @Test
    public void arrowKeysSplitOnlyInLandscape() {
        assertTrue(LIMEBaseKeyboard.splitKeyboardEligible(ELIGIBLE, LANDSCAPE, PHONE, ARROWS, NEVER));
        assertFalse(LIMEBaseKeyboard.splitKeyboardEligible(ELIGIBLE, PORTRAIT, PHONE, ARROWS, NEVER));
    }

    // Issue #169 precedence: an active portrait split (eligible + ALWAYS) wins over
    // one-hand anchoring. This predicate gates the one-hand path in the switcher.
    @Test
    public void portraitSplitActiveWhenEligibleAndAlways() {
        assertTrue(LIMEBaseKeyboard.portraitSplitActive(ELIGIBLE, ALWAYS));
    }

    @Test
    public void portraitSplitInactiveForNumpadEvenWithAlways() {
        assertFalse(LIMEBaseKeyboard.portraitSplitActive(NUMPAD, ALWAYS));
    }

    @Test
    public void portraitSplitInactiveForLandscapeOnlyAndNever() {
        assertFalse(LIMEBaseKeyboard.portraitSplitActive(ELIGIBLE, LANDSCAPE_ONLY));
        assertFalse(LIMEBaseKeyboard.portraitSplitActive(ELIGIBLE, NEVER));
    }
}
