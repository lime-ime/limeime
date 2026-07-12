package org.limeime.voice;

public enum VoiceInputMode {
    AUTO("auto"),
    LIME_INLINE("lime_inline"),
    VOICE_IME("voice_ime"),
    RECOGNIZER_INTENT("recognizer_intent");

    public final String preferenceValue;

    VoiceInputMode(String preferenceValue) {
        this.preferenceValue = preferenceValue;
    }

    public static VoiceInputMode fromPreferenceValue(String value) {
        if (value == null) {
            return AUTO;
        }
        for (VoiceInputMode mode : values()) {
            if (mode.preferenceValue.equals(value)) {
                return mode;
            }
        }
        return AUTO;
    }
}
