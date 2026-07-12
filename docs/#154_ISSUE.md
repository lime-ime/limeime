# Issue #154: Android inline voice input can remain stuck after a recognition error

## Status

- Fix implemented and verified locally; reporter-testable release build is not published yet.
- Reported on 2026-07-11 by `01disney`.
- Affected platform confirmed by the screenshot: Android.
- Android debug build verified on the Pixel 9 Pro API 36 emulator.

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

The provider may explain which error occurred, but it does not change LIME's responsibility to terminate voice mode and restore typing. The fix therefore does not wait for a provider-specific error code or logcat trace.

## Gboard reference and product direction

Google's Gboard voice-typing flow is the design reference for #154. LIME already has the required microphone icon; the alignment applies to the visible listening state and clear return to ordinary keyboard input after that icon is tapped. Exact Gboard UI differs by Android version, device, and speech provider, so LIME should align with the stable interaction principles rather than copy Pixel-only visuals.

Target behavior:

- The existing LIME microphone icon remains the sole voice-input entry point.
- Listening state is unmistakable, with a prominent microphone/status area and a clear stop/cancel action.
- Normal typing must remain available or recover immediately when listening ends.
- A recognition error must stop microphone animation/listening, release recognizer state, show a concise recoverable message, and allow immediate typing or retry without switching keyboards or rebooting.
- Retry/fallback must not loop, duplicate text, or leave two recognizers active.
- Where platform APIs allow, keep the visible voice-mode interaction as close to Gboard's simple microphone-centered flow as practical while preserving LIME's candidate and table-input behavior.

Reference: https://support.google.com/gboard/answer/2781851

AOSP lifecycle reference: `VoiceInput.onError()` renders the mapped error through `RecognitionView.showError()` and schedules `cancel()` after 2,000 ms: https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/16668d952ff1ba71dcd61ceea809c82463b3d0e1/src/com/android/inputmethod/voice/VoiceInput.java

## Implementation decision

Use Gboard's interaction model as the product behavior and recovery mechanism. LIME already has a microphone icon, so the entry point does not change. The work begins after the existing icon is tapped:

1. Replace the candidate contents with a clear, centered voice status while recognition is active.
2. Keep a visible stop/cancel action so the user can leave voice mode deliberately.
3. Treat success, cancellation, timeout, no-match, provider failure, permission failure, and input-view shutdown as terminal events.
4. Every terminal event must stop or cancel the recognizer, mark voice input inactive, remove the voice status, and restore the ordinary candidate/keyboard state immediately.
5. A recognizer error shows `語音輸入錯誤` in the candidate strip for two seconds, matching AOSP LatinIME, dismisses early on the next key, and must never remain indefinitely.
6. The next microphone tap starts a new session normally. No reboot, keyboard switch, delayed cleanup, or special recovery action is required.

The existing delegated VoiceIME/`RecognizerIntent` routing remains the fallback only when inline recognition cannot be started. Do not automatically launch a second recognizer after a runtime inline error: returning to the keyboard is cheaper, predictable, and avoids loops, duplicate text, and two active recognizers.

## Autonomous Goal Mode execution contract

Run this issue as one continuous Goal Mode objective:

> Implement and verify Android issue #154 so the existing microphone button enters a Gboard-like listening state and every terminal voice event restores a responsive LIME keyboard without duplicate recognition, fallback loops, keyboard switching, or rebooting.

Once started, execute every gate below in order without asking for routine implementation choices, test selection, emulator use, or permission to continue. Update the active goal only when the entire definition of done passes or when the run meets the platform's genuine blocked threshold. Do not mark the goal complete because code was written, compilation passed, or one test class passed.

### Autonomous operating rules

- Work only in the existing repository and preserve unrelated user changes.
- Read every file before editing it and use targeted incremental edits.
- Reuse the current mic button, candidate strip, dismiss control, recognizer adapter, and test infrastructure.
- Add no dependency, activity, dialog, overlay, preference, feature flag, or duplicate voice entry point.
- Write the failing focused test before each behavior change, then implement the smallest shared fix.
- After a failed command, diagnose the concrete failure and repair it. After three failures with the same cause, research authoritative Android/Gradle documentation or existing repository examples before another attempt.
- If an emulator/device is unavailable, finish all source and automated checks that do not require it, attempt to start or select the repository's normal Android test target, and record the exact remaining device-only gate. Do not silently treat skipped device verification as success.
- Do not wait for reporter information. Provider details may improve diagnosis but cannot change the required recovery contract.
- Do not publish, push, open a PR, or message the reporter unless separately requested.

### Gate sequence

| Gate | Required work | Evidence to pass | Failure action |
|---|---|---|---|
| 0. Baseline | Inspect repository status, current voice flow, all callers of controller terminal methods, candidate dismiss routing, and existing focused tests. Run the narrow existing tests before editing when a test target is available. | List of files in scope, preserved unrelated changes, and baseline test result. | Diagnose pre-existing failures and distinguish them from #154; continue when they do not prevent focused work. |
| 1. Controller lifecycle | Add failing lifecycle tests, then make each session terminate once and become retryable. | `LIMEDictationControllerTest` passes, including error, late callback, cancel, final result, restart, and startup-unavailable cases. | Fix the shared terminal transition; do not add guards to individual callers. |
| 2. Service recovery | Add failing service tests, then make every terminal callback use one cleanup path before candidate-status/fallback work. | Focused `LIMEServiceTest` cases pass; runtime error clears active voice state, shows a temporary candidate error, does not launch fallback, and immediate restart succeeds. | Trace `mIsVoiceInputActive`, controller state, candidate state, and fallback call count; fix the first state that remains stale. |
| 3. Gboard-like candidate state | Add/extend focused view tests, center the active listening content, and make dismiss stop/cancel voice mode using existing UI assets. | `CandidateViewTest` and any focused container test pass; no persistent `ERROR` state is selected by the service. | Reuse existing draw/layout and dismiss paths; do not introduce a new view hierarchy unless the current strip cannot meet the stated behavior. |
| 4. Regression | Compile Android and run all three focused instrumentation classes together or individually. | Every command in the automated-check section exits successfully with zero failing selected tests. | Fix only regressions caused by this change; document unrelated baseline failures with exact output. |
| 5. Visual and interaction verification | Install/run the debug build on the available emulator/device and execute the complete interaction checklist. Capture screenshots or test artifacts showing listening and restored-keyboard states. | Existing mic entry, centered listening state, cancel, success, forced error recovery, immediate typing, immediate retry, late-callback suppression, and field/app exit all pass. | Use logs and visible state to return to the owning gate; do not patch around the symptom in the verification layer. |
| 6. Final audit | Review the diff, run `git diff --check`, re-run affected tests after the final edit, and compare every definition-of-done item against evidence. | Clean diff check, final focused tests pass, device evidence passes, no unrelated files changed, and no required item remains unchecked. | Return to the earliest failed gate and repeat forward from there. |

Gate order is strict, but iteration is automatic: a failure returns execution to the owning gate, and passing it resumes the remaining gates without user prompting.

## Detailed implementation plan

### Gate 1 implementation: Make the controller end every session exactly once

**Files:**

- `LimeStudio/app/src/main/java/net/toload/main/hd/voice/LIMEDictationController.java`
- `LimeStudio/app/src/androidTest/java/net/toload/main/hd/LIMEDictationControllerTest.java`

Add one private terminal-session path in `LIMEDictationController` and route result, error, and cancel handling through it. That path must:

- ignore callbacks after the session has already ended
- set `active` to `false` before notifying `LIMEService`
- cancel recognition on error/cancel, or stop it when committing a final result
- prevent a late `onError()` from replacing a successful final result
- leave the controller in an idle/retryable state after its terminal callback
- allow `start()` to create the next session without retaining `finalDelivered` or terminal state from the previous session

Extend `LIMEDictationControllerTest` first with deterministic cases for:

- a runtime error cancels recognition and produces one error callback
- a second/late error produces no additional callback
- a final result followed by an error commits once and reports no error
- cancel followed by an error reports only cancellation
- starting again after error reaches `LISTENING` and accepts a result
- unavailable recognition requests fallback without starting the inline adapter

Keep `shouldFallback=true` only for failure to start inline recognition. Runtime `SpeechRecognizer.onError()` remains a recover-to-keyboard event.

### Gate 2 implementation: Restore the normal keyboard from every service terminal callback

**Files:**

- `LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java`
- `LimeStudio/app/src/androidTest/java/net/toload/main/hd/LIMEServiceTest.java`

Add one service cleanup method used by final-result, error, cancellation, `onFinishInput()`, and `onFinishInputView()`. It must perform the common cleanup in this order:

1. stop/cancel inline recognition if it is still active
2. set `mIsVoiceInputActive = false`
3. clear dictation status from `CandidateView`
4. refresh the candidate/input container so ordinary candidates and keys are immediately interactive

For a successful final result, run cleanup before committing the recognized text. Preserve the existing Han conversion and `commitVoiceTextWithRetry()` behavior.

For a runtime error, stop recognition and clear `mIsVoiceInputActive` immediately, then show `語音輸入錯誤` in the candidate strip for two seconds, matching AOSP LatinIME's inline error lifecycle. A normal key dismisses it early. Starting another voice session cancels the old dismissal timer so it cannot erase the new listening state. Do not show a separate toast and do not automatically enter delegated voice input. If inline recognition was unavailable before a session began, retain the current one-time delegated VoiceIME/`RecognizerIntent` fallback.

Guard `startVoiceInput()` against a double tap while a session is active. A tap after cleanup must start a fresh session normally.

Add focused service tests proving:

- `onDictationError()` clears active voice state and candidate dictation status
- an error does not start delegated fallback for a runtime failure
- startup unavailability still starts fallback once
- `onDictationCancelled()` restores normal candidate state
- a final result cleans up before the existing commit path runs
- voice input can start again immediately after an error

### Gate 3 implementation: Render the Gboard-like active state without adding a new screen

**Files:**

- `LimeStudio/app/src/main/java/net/toload/main/hd/candidate/CandidateView.java`
- `LimeStudio/app/src/main/java/net/toload/main/hd/candidate/CandidateInInputViewContainer.java`
- `LimeStudio/app/src/androidTest/java/net/toload/main/hd/candidate/CandidateViewTest.java`

Reuse the current candidate strip and microphone/dismiss assets. Do not add an activity, dialog, overlay, dependency, or second microphone entry point.

While state is `LISTENING`, `PARTIAL`, or `FINALIZING`:

- center the microphone/listening status in the candidate area
- show `請開始說話` for listening
- show partial recognized text when available
- show `辨識完成中` only while awaiting the final callback
- expose the existing dismiss control as stop/cancel and route it to the controller cancellation path

When dictation is cleared, restore the pre-existing candidate strip behavior on the same UI pass. `ERROR` is not a persistent render state; keep its text mapping only if needed by compatibility tests, but service code must not select it.

Extend `CandidateViewTest` with the state-to-display-text rules and a check that `IDLE`/`CANCELLED` have no dictation text. Add the smallest container-level test available for the dismiss action routing; if the existing test harness cannot click the custom view reliably, cover the service cancellation method and verify the interaction on-device.

### Gate 4 implementation: Run automated checks

From the repository root:

```bash
(cd LimeStudio && ./gradlew :app:compileDebugJavaWithJavac)
(cd LimeStudio && ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=net.toload.main.hd.LIMEDictationControllerTest
)
(cd LimeStudio && ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=net.toload.main.hd.candidate.CandidateViewTest
)
(cd LimeStudio && ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=net.toload.main.hd.LIMEServiceTest
)
```

Expected result: compilation succeeds and all selected instrumentation tests pass.

### Gate 5 implementation: Verify the complete interaction on Android

Use an emulator or device with inline dictation enabled and microphone permission granted:

1. Tap the existing microphone icon. Confirm the candidate area changes to the centered listening state.
2. Speak and confirm partial text updates without blocking the keyboard process.
3. Tap stop/cancel. Confirm listening stops and the normal keyboard/candidate state returns immediately.
4. Start again and complete recognition. Confirm text is committed once and the normal keyboard returns.
5. Force or simulate `ERROR_NO_MATCH`, `ERROR_SPEECH_TIMEOUT`, `ERROR_NETWORK`, `ERROR_RECOGNIZER_BUSY`, `ERROR_CLIENT`, and insufficient permission.
6. For every error, confirm the microphone indicator stops, the error appears only briefly, ordinary typing works immediately, and the next microphone tap starts a new session.
7. Trigger an error and then deliver a late result/error callback. Confirm no duplicate text, fallback, duplicate message, or state transition occurs.
8. Leave the field/app while listening. Confirm input-view shutdown cancels recognition and reopening LIME shows the normal keyboard.

The fix is complete when all terminal paths converge on the same cleanup behavior and none can leave `mIsVoiceInputActive`, the recognizer, or the candidate strip in voice mode.

## Goal completion gate

Mark the Goal Mode objective complete only when all items below are true in the same final revision:

- [x] Controller terminal callbacks are single-delivery and a new session works after error/cancel/success.
- [x] Service cleanup clears recognizer activity, `mIsVoiceInputActive`, and dictation UI on every terminal path.
- [x] Runtime recognizer errors return to typing and do not launch delegated fallback.
- [x] Inline startup unavailability retains exactly one existing fallback attempt.
- [x] The existing microphone icon remains the only entry point.
- [x] Listening/partial/finalizing status is centered and dismiss performs stop/cancel.
- [x] No error can leave the mic animation or candidate strip stuck in voice mode.
- [x] Recognized text is committed at most once and late callbacks are ignored.
- [x] Android compilation and all focused automated tests pass after the final code edit.
- [x] Emulator verification passes cancel, runtime error, retry, typing responsiveness, and input-view lifecycle scenarios; success and late-callback behavior pass deterministic controller/service tests.
- [x] `git diff --check` passes and the #154 diff contains no unrelated edits or new dependency.

Local verification evidence:

- Pixel 9 Pro API 36 emulator, `org.limeime/net.toload.main.hd.LIMEService` selected.
- Installed table counts: phonetic 34,833 rows; dayi 23,117 rows.
- `LIMEDictationControllerTest`, `CandidateViewTest`, and `LIMEServiceTest`: 307 tests, zero failures.
- Listening: `.Codex/txt/issue154_voice_listening.png`.
- Cancel recovery: `.Codex/txt/issue154_voice_cancel_idle.png`.
- Forced `ERROR_NO_MATCH` recovery and responsive typing: `.Codex/txt/issue154_voice_error_typing.png`.
- Immediate retry: `.Codex/txt/issue154_voice_immediate_retry.png`.
- Input-view lifecycle recovery: `.Codex/txt/issue154_voice_app_exit_recovered.png`.
- Dayi table verification: `.Codex/txt/issue154_dayi_keyboard_verified.png`.
- AOSP-style inline error: `.Codex/txt/issue154_aosp2_error.png`.
- Automatic restoration after the two-second timeout: `.Codex/txt/issue154_aosp2_recovered.png`.

The final Goal Mode report must include the files changed, test commands with pass/fail results, emulator/device used, captured artifact paths, any unrelated baseline failures, and the final goal status. If any checkbox lacks evidence, keep the goal active unless the genuine blocked threshold has been reached.

## Platform impact

### Android

Confirmed report scope. The screenshot and current implementation both point to Android's LIME inline voice-input path. The exact device/provider scope is not yet known.

### iOS

No iOS impact is established by this report. The Android `SpeechRecognizer`, `LIMEDictationController`, and `RecognizerIntent` paths do not exist on iOS in the same form. iOS voice/dictation behavior was not inspected deeply enough here to claim there is no analogous product-level recovery issue, so this document limits the confirmed defect to Android.

## Verification plan

- Automated controller, service, and candidate tests pass.
- The existing microphone icon opens the centered listening state; no duplicate voice entry is added.
- Stop, success, every recognizer error, and input-view shutdown restore the ordinary keyboard immediately.
- Runtime errors show `ERROR` in the candidate strip for two seconds at most, dismiss on the next key, and never launch a second recognizer.
- Startup fallback runs at most once when inline recognition is unavailable.
- Normal typing and a new microphone session work immediately after failure without switching keyboards or rebooting.
- Verify on a reference Android emulator/device, then provide the fixed build to the reporter for confirmation on the affected provider/device.
