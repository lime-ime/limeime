package org.limeime;

import static org.junit.Assert.assertEquals;

import org.limeime.voice.LIMEVoiceInputRouter;
import org.limeime.voice.VoiceInputMode;
import org.limeime.voice.VoiceInputRoute;
import org.limeime.voice.VoicePermissionState;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class LIMEVoiceInputRouterTest {

    @Test
    public void autoGrantedUsesInlineDictation() {
        assertEquals(VoiceInputRoute.INLINE_DICTATION,
                LIMEVoiceInputRouter.chooseRoute(
                        true,
                        VoiceInputMode.AUTO,
                        VoicePermissionState.GRANTED,
                        true,
                        true,
                        true));
    }

    @Test
    public void deniedPermissionFallsBackToVoiceImeFirst() {
        assertEquals(VoiceInputRoute.VOICE_IME,
                LIMEVoiceInputRouter.chooseRoute(
                        true,
                        VoiceInputMode.AUTO,
                        VoicePermissionState.DENIED_CAN_ASK,
                        true,
                        true,
                        true));
    }

    @Test
    public void missingPermissionFallsBackToVoiceImeFirst() {
        assertEquals(VoiceInputRoute.VOICE_IME,
                LIMEVoiceInputRouter.chooseRoute(
                        true,
                        VoiceInputMode.AUTO,
                        VoicePermissionState.NOT_REQUESTED,
                        true,
                        true,
                        true));
    }

    @Test
    public void missingPermissionFallsBackToRecognizerWhenVoiceImeMissing() {
        assertEquals(VoiceInputRoute.RECOGNIZER_INTENT,
                LIMEVoiceInputRouter.chooseRoute(
                        true,
                        VoiceInputMode.AUTO,
                        VoicePermissionState.NOT_REQUESTED,
                        true,
                        false,
                        true));
    }

    @Test
    public void deniedPermissionFallsBackToRecognizerWhenVoiceImeMissing() {
        assertEquals(VoiceInputRoute.RECOGNIZER_INTENT,
                LIMEVoiceInputRouter.chooseRoute(
                        true,
                        VoiceInputMode.AUTO,
                        VoicePermissionState.DENIED_DO_NOT_ASK_AGAIN,
                        true,
                        false,
                        true));
    }

    @Test
    public void voiceImeModeIgnoresInlineEvenWhenPermissionGranted() {
        assertEquals(VoiceInputRoute.VOICE_IME,
                LIMEVoiceInputRouter.chooseRoute(
                        true,
                        VoiceInputMode.VOICE_IME,
                        VoicePermissionState.GRANTED,
                        true,
                        true,
                        true));
    }

    @Test
    public void recognizerIntentModeSkipsInlineAndVoiceIme() {
        assertEquals(VoiceInputRoute.RECOGNIZER_INTENT,
                LIMEVoiceInputRouter.chooseRoute(
                        true,
                        VoiceInputMode.RECOGNIZER_INTENT,
                        VoicePermissionState.GRANTED,
                        true,
                        true,
                        true));
    }

    @Test
    public void noAvailableRoutesReturnsUnavailable() {
        assertEquals(VoiceInputRoute.UNAVAILABLE,
                LIMEVoiceInputRouter.chooseRoute(
                        true,
                        VoiceInputMode.AUTO,
                        VoicePermissionState.DENIED_CAN_ASK,
                        false,
                        false,
                        false));
    }

    @Test
    public void modePreferenceValueFallsBackToAuto() {
        assertEquals(VoiceInputMode.AUTO, VoiceInputMode.fromPreferenceValue(null));
        assertEquals(VoiceInputMode.AUTO, VoiceInputMode.fromPreferenceValue("unknown"));
        assertEquals(VoiceInputMode.VOICE_IME, VoiceInputMode.fromPreferenceValue("voice_ime"));
    }
}
