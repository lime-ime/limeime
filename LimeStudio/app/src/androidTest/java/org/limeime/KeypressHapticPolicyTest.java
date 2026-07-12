package org.limeime;

import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class KeypressHapticPolicyTest {

    @Test
    public void googleApi31UsesSystemKeyboardTapHaptic() {
        assertTrue(KeypressHapticPolicy.shouldUseSystemKeyboardTapHaptic(
                Build.VERSION_CODES.S,
                "Google",
                "google"));
    }

    @Test
    public void googleApi33UsesSystemKeyboardTapHaptic() {
        assertTrue(KeypressHapticPolicy.shouldUseSystemKeyboardTapHaptic(
                Build.VERSION_CODES.TIRAMISU,
                "Google",
                "google"));
    }

    @Test
    public void pixelBrandApi33UsesSystemKeyboardTapHaptic() {
        assertTrue(KeypressHapticPolicy.shouldUseSystemKeyboardTapHaptic(
                Build.VERSION_CODES.TIRAMISU,
                null,
                "Pixel"));
    }

    @Test
    public void samsungApi31UsesRawPulse() {
        assertFalse(KeypressHapticPolicy.shouldUseSystemKeyboardTapHaptic(
                Build.VERSION_CODES.S,
                "samsung",
                "samsung"));
    }

    @Test
    public void samsungApi33UsesRawPulse() {
        assertFalse(KeypressHapticPolicy.shouldUseSystemKeyboardTapHaptic(
                Build.VERSION_CODES.TIRAMISU,
                "samsung",
                "samsung"));
    }

    @Test
    public void unknownApi33UsesRawPulse() {
        assertFalse(KeypressHapticPolicy.shouldUseSystemKeyboardTapHaptic(
                Build.VERSION_CODES.TIRAMISU,
                "Example",
                "example"));
    }

    @Test
    public void googleBelowApi31UsesRawPulse() {
        assertFalse(KeypressHapticPolicy.shouldUseSystemKeyboardTapHaptic(
                Build.VERSION_CODES.R,
                "Google",
                "google"));
    }

    @Test
    public void vibrateLevelHiddenForGoogleApi31SystemKeyboardTapPath() {
        assertTrue(KeypressHapticPolicy.shouldHideVibrateLevelPreference(
                Build.VERSION_CODES.S,
                "Google",
                "google"));
    }

    @Test
    public void vibrateLevelVisibleForSamsungRawPulsePath() {
        assertFalse(KeypressHapticPolicy.shouldHideVibrateLevelPreference(
                Build.VERSION_CODES.S,
                "samsung",
                "samsung"));
    }
}
