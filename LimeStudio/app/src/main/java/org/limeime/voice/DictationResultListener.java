package org.limeime.voice;

public interface DictationResultListener {
    void onDictationStateChanged(DictationState state);
    void onDictationPartialText(String text);
    void onDictationFinalText(String text);
    void onDictationError(int errorCode, boolean shouldFallback);
    void onDictationCancelled();
}
