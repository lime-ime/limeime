package org.limeime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.limeime.voice.VoicePermissionHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class VoicePermissionHelperTest {
    private Context appContext;

    @Before
    public void setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PreferenceManager.getDefaultSharedPreferences(appContext)
                .edit()
                .remove(VoicePermissionHelper.PREF_RECORD_AUDIO_PERMISSION_PROMPTED)
                .commit();
    }

    @Test
    public void promptedFlagDefaultsToFalseAndCanBeMarked() {
        assertFalse(VoicePermissionHelper.wasRecordAudioPermissionPrompted(appContext));

        VoicePermissionHelper.markRecordAudioPermissionPrompted(appContext);

        assertTrue(VoicePermissionHelper.wasRecordAudioPermissionPrompted(appContext));
    }

    @Test
    public void appSettingsIntentTargetsThisPackage() {
        Intent intent = VoicePermissionHelper.createAppSettingsIntent(appContext);

        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.getAction());
        assertEquals("package:" + appContext.getPackageName(), intent.getDataString());
    }

}
