# Issue #154: Android inline voice input can remain stuck after a recognition error

## Status

- Open community bug report.
- Reported on 2026-07-11 by `01disney`.
- Affected platform confirmed by the screenshot: Android.
- No fix or reporter-testable build is available yet.

## Problem statement

The reporter says normal LIME typing works, but activating voice input produced an error, left the microphone indicator flashing, and made the input method stop responding until the phone was restarted. This was the first occurrence. The attached screenshot shows LIME's candidate/status area displaying `語音輸入錯誤` while the keyboard remains visible in another app.

The report also asks whether voice input should expose a clearer switch or a Google-style centered microphone/listening interface. That UI suggestion is related product feedback, but the tracked defect is the unrecoverable or poorly recoverable error state.

## Reproduction from the report

1. Use LIME normally in an Android text field.
2. Activate LIME voice input.
3. Voice recognition reports an error.
4. The microphone indicator continues flashing and LIME no longer responds.
5. Restart the phone to restore normal input.

This is a single observed occurrence. The exact LIME version, Android/device version, microphone-permission state, speech-recognition provider, recognition error code, and whether switching to another keyboard and back would recover were not provided yet.

## Evidence

- Issue: https://github.com/lime-ime/limeime/issues/154
- Screenshot: https://github.com/user-attachments/assets/cb3fc4a7-ab37-4192-bee7-d72ce2b6054f
- The screenshot shows the LIME keyboard still rendered and the candidate/status area displaying `語音輸入錯誤`.
- The screenshot does not expose the underlying Android `SpeechRecognizer` error code or prove whether the whole IME process, only the inline dictation controller, or only candidate/input handling was stuck.

## Relevant implementation

Android voice input is routed in `LIMEService.startVoiceInput()` through `LIMEVoiceInputRouter`. With the inline-dictation feature enabled, microphone permission granted, and speech recognition available, it uses `LIMEDictationController`; otherwise it delegates to a voice IME or launches a `RecognizerIntent` fallback.

`LIMEDictationController.onError()` marks the controller inactive, cancels the recognizer, and calls `onDictationError(error, false)`. `LIMEService.onDictationError()` displays the `ERROR` candidate-strip state and clears `mIsVoiceInputActive`, but does not clear the error UI or automatically offer the delegated recognizer fallback for ordinary recognizer errors. This is consistent with the visible `語音輸入錯誤` state, but it does not yet prove why the keyboard became unresponsive or why a reboot appeared necessary.

The controller/router change originated in commit `342b6a3b12665a2267f2c2fa7e79a9ad298e3938` for the earlier #63 voice-input routing work. Issue #63 concerned Simplified-Chinese recognition output on Xiaomi and was closed as an upstream recognizer limitation. Issue #154 is a different failure mode: error-state recovery and keyboard responsiveness.

## Existing test coverage and gaps

- `LIMEVoiceInputRouterTest` covers route selection.
- `VoiceInputActivityTest` covers the helper-activity recognizer path.
- `LIMEDictationControllerTest` covers isolated controller error behavior, including cancellation/inactive state, no fallback for a normal recognizer error, late errors after a final result, and unavailable-recognizer fallback signaling.
- No focused test was found for service-level candidate-strip error dismissal, repeated voice activation after an error, one-and-only-one fallback behavior, or whole-IME recovery after `SpeechRecognizer.onError()`.
- Device/instrumentation coverage is still needed because the report may depend on a specific Android speech provider or OEM implementation.

## Likely root cause

The leading hypothesis is incomplete cleanup/recovery after an inline `SpeechRecognizer` error. The controller becomes inactive, but the service leaves the candidate strip in an error state and does not automatically switch to the delegated recognizer path for normal runtime errors. A stale recognizer callback, candidate-view state, or related input lifecycle state may therefore leave the UI looking active or prevent a clean retry.

This remains a hypothesis. The recognition error code and a logcat trace are required to distinguish a LIME lifecycle bug from a speech-provider/OEM failure.

## Proposed investigation and fix direction

1. Reproduce inline voice recognition errors, including network, no-match, busy, client, permission, and provider failures.
2. Capture the `SpeechRecognizer` error code and inspect whether `LIMEDictationController`, `mIsVoiceInputActive`, candidate/status UI, and input connection all return to an idle/retryable state.
3. Ensure every terminal error path clears or converts the visible error status after a short, deterministic lifecycle and leaves normal typing responsive.
4. Decide which error classes should offer or automatically use the existing delegated voice/`RecognizerIntent` fallback without causing loops.
5. Add focused controller/service tests for error cleanup, one-and-only-one fallback, repeated voice activation after error, and normal typing after failure.
6. Keep any new user-facing voice-mode switch or centered listening UI as separate product work unless a maintainer confirms that direction.

## Follow-up questions for the reporter

1. What LIME version, phone model, Android version, and system version are affected?
2. Is LIME's microphone permission enabled, and which voice-recognition service is selected?
3. Does this reproduce again, and does switching to another keyboard and back recover without rebooting?
4. If possible, provide a short recording and Android logcat around the failure, especially lines for `SpeechRecognizer`, `LIMEService`, `LIMEDictationController`, and any recognition error code.

## Platform impact

### Android

Confirmed report scope. The screenshot and current implementation both point to Android's LIME inline voice-input path. The exact device/provider scope is not yet known.

### iOS

No iOS impact is established by this report. The Android `SpeechRecognizer`, `LIMEDictationController`, and `RecognizerIntent` paths do not exist on iOS in the same form. iOS voice/dictation behavior was not inspected deeply enough here to claim there is no analogous product-level recovery issue, so this document limits the confirmed defect to Android.

## Verification plan

- Add deterministic tests for controller error transitions and service cleanup/fallback decisions.
- On Android, force representative recognizer errors and verify:
  - the microphone/listening indicator stops
  - the error status does not remain indefinitely
  - normal key input remains responsive
  - voice input can be started again
  - fallback runs at most once when appropriate
  - switching fields/apps/keyboards does not require a device reboot
- Verify on at least the reporter's device/provider when details are available and on a reference Android device.
- Ask the reporter to retest only after a newer Android build contains a targeted recovery fix.
