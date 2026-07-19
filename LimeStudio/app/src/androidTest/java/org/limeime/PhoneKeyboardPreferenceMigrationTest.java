package org.limeime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.limeime.global.LIMEPreferenceManager;
import org.limeime.keyboard.PhoneKeyboardModePolicy;

@RunWith(AndroidJUnit4.class)
public class PhoneKeyboardPreferenceMigrationTest {
    private SharedPreferences prefs;
    private LIMEPreferenceManager manager;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit()
                .remove("phone_portrait_keyboard_mode")
                .remove("phone_landscape_split")
                .remove("one_hand_mode")
                .remove("split_keyboard_mode")
                .commit();
        manager = new LIMEPreferenceManager(context);
    }

    @After
    public void tearDown() {
        prefs.edit()
                .remove("phone_portrait_keyboard_mode")
                .remove("phone_landscape_split")
                .remove("one_hand_mode")
                .remove("split_keyboard_mode")
                .commit();
    }

    @Test
    public void splitOnlyUserMigratesToPortraitAndLandscapeSplitWithoutChangingTabletPref() {
        prefs.edit().putString("split_keyboard_mode", "1").commit();

        assertEquals(PhoneKeyboardModePolicy.PORTRAIT_SPLIT,
                manager.getPhonePortraitKeyboardMode());
        assertTrue(manager.getPhoneLandscapeSplit());
        assertEquals("1", prefs.getString("split_keyboard_mode", null));
        assertTrue(prefs.contains("phone_portrait_keyboard_mode"));
        assertTrue(prefs.contains("phone_landscape_split"));
    }

    @Test
    public void explicitOneHandWinsButLegacyTabletSplitRemainsPortable() {
        prefs.edit()
                .putString("split_keyboard_mode", "1")
                .putString("one_hand_mode", "2")
                .commit();

        assertEquals(PhoneKeyboardModePolicy.PORTRAIT_ONE_HAND_RIGHT,
                manager.getPhonePortraitKeyboardMode());
        assertEquals("1", prefs.getString("split_keyboard_mode", null));
    }

    @Test
    public void canonicalPhoneChangesNeverRewriteTabletSplitOrLegacyOneHand() {
        prefs.edit()
                .putString("split_keyboard_mode", "2")
                .putString("one_hand_mode", "1")
                .commit();

        manager.setPhonePortraitKeyboardMode(PhoneKeyboardModePolicy.PORTRAIT_SPLIT);
        manager.setPhoneLandscapeSplit(false);

        assertEquals("2", prefs.getString("split_keyboard_mode", null));
        assertEquals("1", prefs.getString("one_hand_mode", null));
        assertEquals(PhoneKeyboardModePolicy.PORTRAIT_SPLIT,
                manager.getPhonePortraitKeyboardMode());
        assertFalse(manager.getPhoneLandscapeSplit());
    }

    @Test
    public void migrationWritesBothKeysAtomicallyWhilePreservingAnExistingCanonicalValue() {
        prefs.edit()
                .putString("split_keyboard_mode", "2")
                .putString("one_hand_mode", "1")
                .putString("phone_portrait_keyboard_mode", "3")
                .remove("phone_landscape_split")
                .commit();

        manager.ensurePhoneKeyboardPreferencesMigrated();

        assertEquals("3", prefs.getString("phone_portrait_keyboard_mode", null));
        assertTrue(prefs.getBoolean("phone_landscape_split", false));
        assertTrue(prefs.contains("phone_portrait_keyboard_mode"));
        assertTrue(prefs.contains("phone_landscape_split"));
    }
}
