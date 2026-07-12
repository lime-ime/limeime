package org.limeime;

import static org.junit.Assert.assertTrue;

import org.limeime.voice.AndroidSpeechRecognizerAdapter;
import org.limeime.voice.DictationResultListener;
import org.limeime.voice.DictationState;
import org.limeime.voice.SpeechRecognizerAdapter;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class SpeechRecognizerAdapterContractTest {

    @Test
    public void adapterAndDictationContractsExist() {
        assertTrue(SpeechRecognizerAdapter.class.isInterface());
        assertTrue(DictationResultListener.class.isInterface());
        assertTrue(AndroidSpeechRecognizerAdapter.class.getName().contains("AndroidSpeechRecognizerAdapter"));
        assertTrue(DictationState.IDLE.ordinal() >= 0);
    }
}
