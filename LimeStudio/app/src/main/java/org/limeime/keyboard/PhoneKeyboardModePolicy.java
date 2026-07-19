package org.limeime.keyboard;

/** Shared phone keyboard geometry semantics for migration and rendering. */
public final class PhoneKeyboardModePolicy {
    public static final int PORTRAIT_STANDARD = 0;
    public static final int PORTRAIT_SPLIT = 1;
    public static final int PORTRAIT_ONE_HAND_LEFT = 2;
    public static final int PORTRAIT_ONE_HAND_RIGHT = 3;

    private PhoneKeyboardModePolicy() {}

    /**
     * Issue #169: whether the integrated phone controls (直向鍵盤模式 /
     * 橫向分離鍵盤 and one-hand rendering) apply. They apply to EVERY phone and
     * NEVER to tablets — there is no screen-width or physical-size gate. Tablets
     * (Android smallestScreenWidthDp &gt;= 600, iPad) use split_keyboard_mode /
     * numpad_anchor instead.
     */
    public static boolean phoneControlsApply(boolean isTablet) {
        return !isTablet;
    }

    public static int migratePortraitMode(int legacyOneHand, int legacySplit,
                                          boolean legacyPhoneSplitSupported) {
        if (legacyOneHand == 1) return PORTRAIT_ONE_HAND_LEFT;
        if (legacyOneHand == 2) return PORTRAIT_ONE_HAND_RIGHT;
        if (legacyPhoneSplitSupported && legacySplit == 1) return PORTRAIT_SPLIT;
        return PORTRAIT_STANDARD;
    }

    public static boolean migrateLandscapeSplit(int legacySplit,
                                                boolean legacyPhoneSplitSupported) {
        return legacyPhoneSplitSupported && legacySplit != 0;
    }

    public static boolean phoneSplitActive(boolean landscape, boolean splitEligible,
                                           int portraitMode, boolean landscapeSplit) {
        if (!splitEligible) return false;
        return landscape ? landscapeSplit : portraitMode == PORTRAIT_SPLIT;
    }

    public static int oneHandAnchorMode(boolean landscape, int portraitMode) {
        if (landscape) return 0;
        if (portraitMode == PORTRAIT_ONE_HAND_LEFT) return 1;
        if (portraitMode == PORTRAIT_ONE_HAND_RIGHT) return 2;
        return 0;
    }
}
