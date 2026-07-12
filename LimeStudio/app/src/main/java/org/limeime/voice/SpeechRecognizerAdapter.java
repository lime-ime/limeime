package org.limeime.voice;

import android.content.Intent;
import android.speech.RecognitionListener;

public interface SpeechRecognizerAdapter {
    void setRecognitionListener(RecognitionListener listener);
    void startListening(Intent intent);
    void stopListening();
    void cancel();
    void destroy();
    boolean isRecognitionAvailable();
}
