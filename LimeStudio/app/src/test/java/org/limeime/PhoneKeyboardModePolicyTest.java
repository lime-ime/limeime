package org.limeime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.limeime.keyboard.PhoneKeyboardModePolicy;

public class PhoneKeyboardModePolicyTest {

    @Test
    public void androidMigrationRestoresLegacyPortraitSplit() {
        assertEquals(PhoneKeyboardModePolicy.PORTRAIT_SPLIT,
                PhoneKeyboardModePolicy.migratePortraitMode(0, 1, true));
    }

    @Test
    public void explicitLegacyOneHandWinsOverLegacySplit() {
        assertEquals(PhoneKeyboardModePolicy.PORTRAIT_ONE_HAND_LEFT,
                PhoneKeyboardModePolicy.migratePortraitMode(1, 1, true));
        assertEquals(PhoneKeyboardModePolicy.PORTRAIT_ONE_HAND_RIGHT,
                PhoneKeyboardModePolicy.migratePortraitMode(2, 1, true));
    }

    @Test
    public void landscapeOnlyDoesNotBecomePortraitSplit() {
        assertEquals(PhoneKeyboardModePolicy.PORTRAIT_STANDARD,
                PhoneKeyboardModePolicy.migratePortraitMode(0, 2, true));
    }

    @Test
    public void iosMigrationDoesNotInventLegacyPhoneSplit() {
        assertEquals(PhoneKeyboardModePolicy.PORTRAIT_STANDARD,
                PhoneKeyboardModePolicy.migratePortraitMode(0, 1, false));
        assertFalse(PhoneKeyboardModePolicy.migrateLandscapeSplit(1, false));
    }

    @Test
    public void androidLandscapeMigrationPreservesEitherLegacySplitMode() {
        assertTrue(PhoneKeyboardModePolicy.migrateLandscapeSplit(1, true));
        assertTrue(PhoneKeyboardModePolicy.migrateLandscapeSplit(2, true));
        assertFalse(PhoneKeyboardModePolicy.migrateLandscapeSplit(0, true));
    }

    @Test
    public void portraitModesAreMutuallyExclusiveRenderingInputs() {
        assertTrue(PhoneKeyboardModePolicy.phoneSplitActive(
                false, true, PhoneKeyboardModePolicy.PORTRAIT_SPLIT, false));
        assertFalse(PhoneKeyboardModePolicy.phoneSplitActive(
                false, true, PhoneKeyboardModePolicy.PORTRAIT_ONE_HAND_LEFT, true));
        assertEquals(1, PhoneKeyboardModePolicy.oneHandAnchorMode(
                false, PhoneKeyboardModePolicy.PORTRAIT_ONE_HAND_LEFT));
        assertEquals(2, PhoneKeyboardModePolicy.oneHandAnchorMode(
                false, PhoneKeyboardModePolicy.PORTRAIT_ONE_HAND_RIGHT));
        assertEquals(0, PhoneKeyboardModePolicy.oneHandAnchorMode(
                false, PhoneKeyboardModePolicy.PORTRAIT_SPLIT));
    }

    @Test
    public void landscapeUsesOnlyPhoneLandscapeSplit() {
        assertTrue(PhoneKeyboardModePolicy.phoneSplitActive(
                true, true, PhoneKeyboardModePolicy.PORTRAIT_STANDARD, true));
        assertFalse(PhoneKeyboardModePolicy.phoneSplitActive(
                true, true, PhoneKeyboardModePolicy.PORTRAIT_SPLIT, false));
    }

    @Test
    public void numpadNeverSplitsButCanUsePortraitOneHand() {
        assertFalse(PhoneKeyboardModePolicy.phoneSplitActive(
                false, false, PhoneKeyboardModePolicy.PORTRAIT_SPLIT, true));
        assertEquals(2, PhoneKeyboardModePolicy.oneHandAnchorMode(
                false, PhoneKeyboardModePolicy.PORTRAIT_ONE_HAND_RIGHT));
    }

    // Issue #169: the integrated phone controls apply to EVERY phone and NEVER to
    // tablets — there is no screen-width/physical-size gate anywhere.
    @Test
    public void phoneControlsApplyToEveryPhoneNeverTablets() {
        assertTrue(PhoneKeyboardModePolicy.phoneControlsApply(false));
        assertFalse(PhoneKeyboardModePolicy.phoneControlsApply(true));
    }

    // A narrow phone (previously below the removed one-hand width gate) still
    // resolves its portrait one-hand anchor purely from the stored mode: width is
    // not an input to any decision here.
    @Test
    public void narrowPhoneStillResolvesPortraitOneHandWithoutWidthGate() {
        assertTrue(PhoneKeyboardModePolicy.phoneControlsApply(false));
        assertEquals(1, PhoneKeyboardModePolicy.oneHandAnchorMode(
                false, PhoneKeyboardModePolicy.PORTRAIT_ONE_HAND_LEFT));
        assertEquals(2, PhoneKeyboardModePolicy.oneHandAnchorMode(
                false, PhoneKeyboardModePolicy.PORTRAIT_ONE_HAND_RIGHT));
        assertTrue(PhoneKeyboardModePolicy.phoneSplitActive(
                false, true, PhoneKeyboardModePolicy.PORTRAIT_SPLIT, false));
    }
}
