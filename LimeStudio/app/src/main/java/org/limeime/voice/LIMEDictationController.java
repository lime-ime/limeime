package org.limeime.voice;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

public class LIMEDictationController implements RecognitionListener {

    private final SpeechRecognizerAdapter recognizer;
    private final DictationResultListener listener;

    private DictationState state = DictationState.IDLE;
    private boolean active;
    private boolean finalDelivered;

    public LIMEDictationController(SpeechRecognizerAdapter recognizer, DictationResultListener listener) {
        this.recognizer = recognizer;
        this.listener = listener;
    }

    public boolean isRecognitionAvailable() {
        return recognizer != null && recognizer.isRecognitionAvailable();
    }

    public boolean isActive() {
        return active;
    }

    public DictationState getState() {
        return state;
    }

    public void start(String languageTag) {
        if (active) {
            return;
        }
        if (!isRecognitionAvailable()) {
            emitError(SpeechRecognizer.ERROR_CLIENT, true);
            return;
        }

        finalDelivered = false;
        active = true;
        recognizer.setRecognitionListener(this);
        emitState(DictationState.LISTENING);
        recognizer.startListening(createRecognizerIntent(languageTag));
    }

    public void stopAndCommit() {
        if (!active || recognizer == null) {
            return;
        }
        recognizer.stopListening();
    }

    public void cancel() {
        if (!active) {
            return;
        }
        if (recognizer != null) {
            recognizer.cancel();
        }
        active = false;
        emitState(DictationState.CANCELLED);
        if (listener != null) {
            listener.onDictationCancelled();
        }
    }

    public void destroy() {
        if (recognizer != null) {
            recognizer.destroy();
        }
        active = false;
        finalDelivered = false;
        state = DictationState.IDLE;
    }

    Intent createRecognizerIntent(String languageTag) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        if (languageTag != null && languageTag.length() > 0) {
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag);
        }
        return intent;
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        emitState(DictationState.LISTENING);
    }

    @Override
    public void onBeginningOfSpeech() {
        emitState(DictationState.LISTENING);
    }

    @Override
    public void onRmsChanged(float rmsdB) {
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
    }

    @Override
    public void onEndOfSpeech() {
        emitState(DictationState.FINALIZING);
    }

    @Override
    public void onError(int error) {
        if (!active) {
            return;
        }
        active = false;
        if (recognizer != null) {
            recognizer.cancel();
        }
        if (finalDelivered) {
            return;
        }
        emitError(error, false);
    }

    @Override
    public void onResults(Bundle results) {
        String text = firstRecognitionText(results);
        active = false;
        if (text == null || text.length() == 0 || finalDelivered) {
            return;
        }
        finalDelivered = true;
        emitState(DictationState.FINALIZING);
        if (recognizer != null) {
            recognizer.stopListening();
        }
        if (listener != null) {
            listener.onDictationFinalText(text);
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        String text = firstRecognitionText(partialResults);
        if (text == null || text.length() == 0) {
            return;
        }
        emitState(DictationState.PARTIAL);
        if (listener != null) {
            listener.onDictationPartialText(text);
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
    }

    private String firstRecognitionText(Bundle results) {
        if (results == null) {
            return null;
        }
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        return matches.get(0);
    }

    private void emitState(DictationState nextState) {
        state = nextState;
        if (listener != null) {
            listener.onDictationStateChanged(nextState);
        }
    }

    private void emitError(int errorCode, boolean shouldFallback) {
        emitState(DictationState.ERROR);
        if (listener != null) {
            listener.onDictationError(errorCode, shouldFallback);
        }
    }

}
