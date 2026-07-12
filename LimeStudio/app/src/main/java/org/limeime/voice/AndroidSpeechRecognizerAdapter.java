package org.limeime.voice;

import android.content.Context;
import android.content.Intent;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;

public class AndroidSpeechRecognizerAdapter implements SpeechRecognizerAdapter {
    private final Context context;
    private SpeechRecognizer speechRecognizer;

    public AndroidSpeechRecognizerAdapter(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    @Override
    public void setRecognitionListener(RecognitionListener listener) {
        SpeechRecognizer recognizer = getOrCreateRecognizer();
        if (recognizer != null) {
            recognizer.setRecognitionListener(listener);
        }
    }

    @Override
    public void startListening(Intent intent) {
        SpeechRecognizer recognizer = getOrCreateRecognizer();
        if (recognizer != null) {
            recognizer.startListening(intent);
        }
    }

    @Override
    public void stopListening() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
    }

    @Override
    public void cancel() {
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
        }
    }

    @Override
    public void destroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }

    @Override
    public boolean isRecognitionAvailable() {
        return context != null && SpeechRecognizer.isRecognitionAvailable(context);
    }

    private SpeechRecognizer getOrCreateRecognizer() {
        if (context == null || !isRecognitionAvailable()) {
            return null;
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        }
        return speechRecognizer;
    }
}
