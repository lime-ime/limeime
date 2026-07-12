package org.limeime.voice;

public final class LIMEVoiceInputRouter {

    private LIMEVoiceInputRouter() {
    }

    public static VoiceInputRoute chooseRoute(
            boolean inlineFeatureEnabled,
            VoiceInputMode selectedMode,
            VoicePermissionState permissionState,
            boolean inlineRecognizerAvailable,
            boolean voiceImeAvailable,
            boolean recognizerFallbackAvailable) {

        VoiceInputMode mode = selectedMode == null ? VoiceInputMode.AUTO : selectedMode;
        VoicePermissionState permission = permissionState == null
                ? VoicePermissionState.NOT_REQUESTED
                : permissionState;

        switch (mode) {
            case LIME_INLINE:
                if (canUseInline(inlineFeatureEnabled, permission, inlineRecognizerAvailable)) {
                    return VoiceInputRoute.INLINE_DICTATION;
                }
                return chooseDelegatedRoute(voiceImeAvailable, recognizerFallbackAvailable);
            case VOICE_IME:
                if (voiceImeAvailable) {
                    return VoiceInputRoute.VOICE_IME;
                }
                return recognizerFallbackAvailable
                        ? VoiceInputRoute.RECOGNIZER_INTENT
                        : VoiceInputRoute.UNAVAILABLE;
            case RECOGNIZER_INTENT:
                return recognizerFallbackAvailable
                        ? VoiceInputRoute.RECOGNIZER_INTENT
                        : VoiceInputRoute.UNAVAILABLE;
            case AUTO:
            default:
                if (canUseInline(inlineFeatureEnabled, permission, inlineRecognizerAvailable)) {
                    return VoiceInputRoute.INLINE_DICTATION;
                }
                return chooseDelegatedRoute(voiceImeAvailable, recognizerFallbackAvailable);
        }
    }

    private static boolean canUseInline(
            boolean inlineFeatureEnabled,
            VoicePermissionState permissionState,
            boolean inlineRecognizerAvailable) {
        return inlineFeatureEnabled
                && permissionState == VoicePermissionState.GRANTED
                && inlineRecognizerAvailable;
    }

    private static VoiceInputRoute chooseDelegatedRoute(
            boolean voiceImeAvailable,
            boolean recognizerFallbackAvailable) {
        if (voiceImeAvailable) {
            return VoiceInputRoute.VOICE_IME;
        }
        if (recognizerFallbackAvailable) {
            return VoiceInputRoute.RECOGNIZER_INTENT;
        }
        return VoiceInputRoute.UNAVAILABLE;
    }
}
