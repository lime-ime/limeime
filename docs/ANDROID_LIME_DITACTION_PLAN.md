# Android LIME Dictation Implementation Plan

**Status:** Phase 1 implemented; Phase 2 human/device verification pending
**Source design:** [ANDROID_LIME_DITACTION.md](ANDROID_LIME_DITACTION.md)
**Related:** [ANDROID_VOICE_INPUT.md](ANDROID_VOICE_INPUT.md), [#63_ISSUE.md](#63_ISSUE.md)

---

## Goal

Implement LIME-owned inline dictation for Android while preserving the current
delegated fallback stack:

1. LIME inline dictation when enabled and `RECORD_AUDIO` permission is granted.
2. Google/vendor VoiceIME switching when inline dictation is unavailable,
   disabled, or permission is denied.
3. `RecognizerIntent` fallback when no VoiceIME target exists or switching does
   not actually take.

This plan has two phases:

1. **Implementation phase:** sequential source-code steps. Each step is gated
   only by automated verification that Codex can run.
2. **Human verification phase:** manual/device validation after all functional
   code modifications are complete.

Do not gate intermediate implementation steps on manual phone/emulator behavior.
Manual checks happen only after the functional code path is fully implemented
and automated verification passes.

## Current Code Anchors

Existing voice entry and fallback:

- `LimeStudio/app/src/main/java/org/limeime/LIMEService.java`
  - `startVoiceInput()`
  - `getVoiceIntent()`
  - `resolveVoiceRecognitionLanguageTag(Locale)`
  - `launchRecognizerIntent(Intent)`
  - `commitVoiceTextWithRetry(String, int)`
  - `prepareVoiceTextForCommit(String)`
  - voice result receiver and pending text handling
- `LimeStudio/app/src/main/java/org/limeime/VoiceInputActivity.java`
  - `RecognizerIntent` result trampoline and static pending text delivery
- `LimeStudio/app/src/main/java/org/limeime/global/LIMEUtilities.java`
  - `isVoiceSearchServiceExist(Context)`
  - `isVoiceInputMethodId(String)`

Existing mic key and candidate UI:

- `LimeStudio/app/src/main/java/org/limeime/candidate/CandidateView.java`
  - `startVoiceInput()`
  - candidate strip drawing and touch handling
- `LimeStudio/app/src/main/java/org/limeime/candidate/CandidateViewContainer.java`
  - candidate strip container and embedded composing view wiring
- `LimeStudio/app/src/main/java/org/limeime/candidate/CandidateInInputViewContainer.java`
  - in-input candidate container behavior
- `LimeStudio/app/src/main/res/layout/candidates.xml`
  - `embeddedComposing`
  - `CandidateView`
  - right-side expand/dismiss controls

Existing Setup tab:

- `LimeStudio/app/src/main/java/org/limeime/ui/view/SetupFragment.java`
  - status refresh on resume
  - IME settings and picker buttons
- `LimeStudio/app/src/main/res/layout/fragment_setup.xml`
  - status card
  - setup instructions and buttons
- `LimeStudio/app/src/main/res/values/strings.xml`
  - user-facing setup labels and status strings
- `docs/LIME_SETTINGS.md`
  - Setup tab design contract to update during documentation step.

Existing tests:

- `LimeStudio/app/src/androidTest/java/org/limeime/LIMEServiceTest.java`
- `LimeStudio/app/src/androidTest/java/org/limeime/VoiceInputActivityTest.java`
- `LimeStudio/app/src/androidTest/java/org/limeime/candidate/CandidateViewTest.java`
- `LimeStudio/app/src/androidTest/java/org/limeime/SetupImFragmentTest.java`
- `LimeStudio/app/src/androidTest/java/org/limeime/LIMEServiceWithStubActivityTest.java`

## Automated Gate

Run this automated gate after every implementation step:

```powershell
cd LimeStudio
.\gradlew.bat :app:compileDebugJavaWithJavac
.\gradlew.bat :app:compileDebugAndroidTestJavaWithJavac
```

When a step adds targeted tests that can run without a connected device, also
run the narrowest available Gradle test task. If no such task exists, compile
the test sources and keep the new tests ready for connected execution.

Do not run manual UI/device checks between implementation steps. Manual
verification begins only in **Phase 2**.

## Phase 1: Implementation Steps

### Step 1: Protect Delegated VoiceIME Detection

Purpose: keep the current broadened VoiceIME fallback explicit before adding
inline dictation.

Modify:

- `LIMEUtilities.java`
  - preserve target priority:
    1. exact Google Voice Search / QuickSearchBox / TTS Speech Services voice
       IME IDs
    2. `InputMethodSubtype.getMode() == "voice"`
    3. ID contains `voice` or `speech`
    4. Gboard ID prefix
  - keep Gboard as last fallback only.
- `LIMEService.java`
  - keep switch-and-verify before `RecognizerIntent`.
  - keep Traditional-first recognizer fallback.

Tests:

- Extend `LIMEServiceTest` or add `VoiceImeDetectionTest`:
  - exact Google TTS/Speech Services ID returns true
  - legacy Voice Search IDs return true
  - heuristic `speech`/`voice` IDs return true
  - Gboard ID returns true
  - unrelated IME ID returns false
- If selection order needs stronger coverage, extract a pure helper such as
  `chooseVoiceInputMethodId(List<VoiceImeCandidate>)` and test it directly.

Automated gate:

- compile debug Java
- compile androidTest Java

### Step 2: Add Voice Routing Model

Purpose: create a testable router before touching microphone recognition.

Add:

- `LimeStudio/app/src/main/java/org/limeime/voice/VoiceInputMode.java`
  - `AUTO`
  - `LIME_INLINE`
  - `VOICE_IME`
  - `RECOGNIZER_INTENT`
- `LimeStudio/app/src/main/java/org/limeime/voice/VoicePermissionState.java`
  - `GRANTED`
  - `NOT_REQUESTED`
  - `DENIED_CAN_ASK`
  - `DENIED_DO_NOT_ASK_AGAIN`
- `LimeStudio/app/src/main/java/org/limeime/voice/VoiceInputRoute.java`
  - `INLINE_DICTATION`
  - `VOICE_IME`
  - `RECOGNIZER_INTENT`
  - `UNAVAILABLE`
- `LimeStudio/app/src/main/java/org/limeime/voice/LIMEVoiceInputRouter.java`
  - pure helper that decides route from:
    - feature enabled
    - selected mode
    - permission state
    - inline recognizer available
    - VoiceIME target available
    - recognizer fallback available

Tests:

- New `LIMEVoiceInputRouterTest`:
  - permission granted + `AUTO` + feature enabled -> inline
  - permission denied + VoiceIME available -> VoiceIME
  - permission denied + no VoiceIME + recognizer available -> recognizer
  - `VOICE_IME` mode ignores inline even when permission granted
  - `RECOGNIZER_INTENT` mode skips inline and VoiceIME
  - unavailable recognizer and no VoiceIME -> unavailable

Automated gate:

- compile debug Java
- compile androidTest Java

### Step 3: Add Permission Helpers And Manifest Permission

Purpose: add `RECORD_AUDIO` support without prompting from the keyboard on
every mic tap.

Modify:

- `LimeStudio/app/src/main/AndroidManifest.xml`
  - add `android.permission.RECORD_AUDIO`

Add:

- `LimeStudio/app/src/main/java/org/limeime/voice/VoicePermissionHelper.java`
  - `getRecordAudioPermissionState(Context, Activity/Fragment)`
  - `hasRecordAudioPermission(Context)`
  - `openAppSettings(Context)`
  - helper for denied-once/prompted state if persisted locally.

Preference/persistence:

- Add a persisted flag if needed:
  - `voice_inline_permission_prompted`
  - `voice_inline_enabled`

Tests:

- `VoicePermissionHelperTest` or reflective coverage in `LIMEServiceTest`:
  - helper class exists
  - app-settings intent action/package is formed correctly
  - permission state enum maps expected values for mocked/simple inputs where
    possible

Automated gate:

- compile debug Java
- compile androidTest Java

### Step 4: Refactor `LIMEService` Routing Without Starting Inline Dictation

Purpose: integrate the router while preserving existing VoiceIME and
`RecognizerIntent` behavior.

Modify `LIMEService.java`:

- Split `startVoiceInput()` into small methods:
  - `startVoiceInput()`
  - `startInlineDictationOrFallback()`
  - `startDelegatedVoiceInput(Intent voiceIntent)`
  - `startRecognizerFallback(Intent voiceIntent)`
- `startVoiceInput()` should:
  - read selected voice mode
  - read permission state
  - resolve VoiceIME availability
  - resolve recognizer availability
  - ask `LIMEVoiceInputRouter` for route
  - route to delegated VoiceIME or recognizer until inline controller exists
- Keep:
  - `RecognizerIntent` language as `zh-TW`/`zh-HK`, never `zh-CN`
  - switch-and-verify behavior
  - pending voice commit behavior
  - no repeated permission prompt from IME service

Tests:

- `LIMEServiceTest`:
  - existing `getVoiceIntent()` language fallback remains `zh-TW`
  - route branches can be invoked without crash in test context
  - `RecognizerIntent` fallback remains available when no VoiceIME route exists

Automated gate:

- compile debug Java
- compile androidTest Java

### Step 5: Add Setup Tab Permission Row

Purpose: provide the user-facing opt-in point for LIME microphone access.

Modify:

- `SetupFragment.java`
  - add views for optional voice permission row:
    - icon
    - title `允許語音輸入`
    - detail/status text
    - click handler
  - poll `RECORD_AUDIO` permission in `onResume()`
  - distinguish:
    - granted
    - not granted/can ask
    - denied once
    - denied permanently
  - request permission from the Settings tab when possible
  - open Android app settings for permanently denied state
- `fragment_setup.xml`
  - add optional MaterialCardView or row under activation setup steps
  - hide when inline dictation feature flag is disabled
- `strings.xml`
  - `setup_voice_permission_title`
  - `setup_voice_permission_granted`
  - `setup_voice_permission_not_granted`
  - `setup_voice_permission_denied_once`
  - `setup_voice_permission_denied_permanently`
  - `setup_voice_permission_open_settings`

Tests:

- `SetupImFragmentTest` or new `SetupFragmentVoicePermissionTest`:
  - fragment exposes permission refresh method or row binding
  - row is hidden when feature flag disabled
  - row is visible when enabled
  - click behavior routes to permission request or app settings by state
- If UI state is brittle, keep fragment tests smoke-level and cover logic in
  `VoicePermissionHelper`.

Automated gate:

- compile debug Java
- compile androidTest Java

### Step 6: Add Dictation State And Recognizer Adapter

Purpose: make `SpeechRecognizer` lifecycle testable before wiring UI.

Add:

- `LimeStudio/app/src/main/java/org/limeime/voice/DictationState.java`
  - state enum or immutable state:
    - `IDLE`
    - `LISTENING`
    - `PARTIAL`
    - `FINALIZING`
    - `ERROR`
    - `CANCELLED`
- `LimeStudio/app/src/main/java/org/limeime/voice/DictationResultListener.java`
  - partial text callback
  - final text callback
  - error callback
  - cancelled callback
- `LimeStudio/app/src/main/java/org/limeime/voice/SpeechRecognizerAdapter.java`
  - test seam over Android `SpeechRecognizer`
- `LimeStudio/app/src/main/java/org/limeime/voice/AndroidSpeechRecognizerAdapter.java`
  - concrete Android implementation

Tests:

- New fake adapter for tests.
- `LIMEDictationControllerTest` can be written in the next step using the fake.

Automated gate:

- compile debug Java
- compile androidTest Java

### Step 7: Add `LIMEDictationController`

Purpose: implement inline dictation state management and callbacks.

Add:

- `LimeStudio/app/src/main/java/org/limeime/voice/LIMEDictationController.java`
  - owns recognizer lifecycle
  - exposes:
    - `start(String languageTag)`
    - `stopAndCommit()`
    - `cancel()`
    - `destroy()`
    - `isActive()`
  - sends listener callbacks for partial/final/error/cancel
  - ignores duplicate final results
  - releases recognizer on cancel/error/destroy

Controller intent extras:

- `RecognizerIntent.EXTRA_LANGUAGE_MODEL =
  RecognizerIntent.LANGUAGE_MODEL_FREE_FORM`
- `RecognizerIntent.EXTRA_LANGUAGE =
  LIMEService.resolveVoiceRecognitionLanguageTag(...)`
- `RecognizerIntent.EXTRA_PARTIAL_RESULTS = true`
- `RecognizerIntent.EXTRA_MAX_RESULTS = 1`

Tests:

- New `LIMEDictationControllerTest`:
  - start moves `IDLE -> LISTENING`
  - partial result moves to `PARTIAL`
  - final result moves to `FINALIZING` and emits final text
  - cancel emits `CANCELLED`
  - recognizer error emits `ERROR`
  - duplicate final callback is ignored
  - no-speech/recognizer-busy errors are marked fallback-worthy

Automated gate:

- compile debug Java
- compile androidTest Java

### Step 8: Wire Inline Dictation Into `LIMEService`

Purpose: make mic tap use inline dictation when allowed, and fallback otherwise.

Modify `LIMEService.java`:

- create/destroy controller with IME lifecycle:
  - `onCreate()`
  - `onDestroy()`
  - `onFinishInputView()`
- route `startVoiceInput()`:
  - inline dictation if router returns `INLINE_DICTATION`
  - delegated VoiceIME if router returns `VOICE_IME`
  - `RecognizerIntent` if router returns `RECOGNIZER_INTENT`
- on final inline text:
  - call existing `commitVoiceTextWithRetry(...)`
- on inline error/no recognizer:
  - fall back to delegated VoiceIME, then recognizer
- clear dictation state on:
  - finish input view
  - cancel
  - final commit
  - fallback

Tests:

- `LIMEServiceTest`:
  - inline final text calls same commit path as voice result
  - `onFinishInputView()` cancels/clears controller
  - fallback path still launches recognizer when controller unavailable
  - duplicate final text commits once if service layer also sees duplicates

Automated gate:

- compile debug Java
- compile androidTest Java

### Step 9: Add Candidate Strip Inline UI

Purpose: show listening and partial recognition text inside LIME's keyboard
surface.

Modify:

- `CandidateView.java`
  - add dictation display state:
    - inactive
    - listening
    - partial text
    - error/cancel
  - add public methods:
    - `showDictationListening()`
    - `showDictationPartial(String text)`
    - `showDictationError(String message)`
    - `clearDictationState()`
  - draw partial text using existing composing/candidate paint rules
  - preserve existing candidate drawing when dictation inactive
- `CandidateViewContainer.java`
  - optionally use `embeddedComposing` for partial text
  - expose helper to show/hide partial dictation text without disturbing normal
    composing text
- `candidates.xml`
  - prefer no structural change for first pass
  - reuse `embeddedComposing` or CandidateView drawing
  - add fixed-width stop/cancel button only if required
- `CandidateInInputViewContainer.java`
  - route touch on dictation state to stop/cancel if needed
- `LIMEService.java`
  - propagate controller state to candidate UI
  - clear dictation UI on input finish, cancel, final commit, and fallback

Tests:

- `CandidateViewTest`:
  - dictation state methods do not crash without service
  - partial text state invalidates/redraws
  - clear restores normal empty candidate behavior
- `LIMEServiceTest`:
  - service updates candidate UI on listening/partial/final/error state where
    testable

Automated gate:

- compile debug Java
- compile androidTest Java

### Step 10: Final Text Conversion And Commit Hardening

Purpose: ensure inline dictation final output uses existing safe voice commit
behavior.

Modify:

- `LIMEService.java`
  - reuse `prepareVoiceTextForCommit(...)`
  - keep `commitVoiceTextWithRetry(...)` shared between `VoiceInputActivity`
    and inline dictation
  - ensure final result is committed once only
  - clear pending/partial state after commit
- `LIMEDictationController.java`
  - keep duplicate-final guard
  - stop listening after final result
  - release recognizer on cancel/error/destroy

Tests:

- `LIMEServiceTest`:
  - Simplified voice result is converted when Han conversion enabled
  - non-Chinese/English result passes through unchanged
  - duplicate final result commits once
  - null `InputConnection` stores pending text and retries
- `LIMEDictationControllerTest`:
  - duplicate final callback is ignored

Automated gate:

- compile debug Java
- compile androidTest Java

### Step 11: Add Preference Gate And User Modes

Purpose: make behavior controllable when device/provider behavior varies.

Modify:

- `res/xml/preference.xml`
- `res/xml-v17/preference.xml`
- `strings.xml` or `strings_settings.xml`
Voice routing:

- Do not add a user-facing voice input mode preference.
- `LIMEService` should always route with `AUTO`.
- The router decides between inline dictation, delegated VoiceIME, and
  `RecognizerIntent` fallback from permission and availability state.
  - `Google/系統語音輸入`
  - `Google 語音辨識視窗`

Routing:

- `AUTO`: inline if enabled/granted, then VoiceIME, then recognizer.
- `lime_inline`: inline if granted; if permission denied, show optional setup
  hint and fall back to VoiceIME.
- `voice_ime`: skip inline, skip recognizer unless VoiceIME missing/fails.
- `recognizer_intent`: skip inline and VoiceIME.

Tests:

- `LIMEVoiceInputRouterTest` covers every mode.
- preference default test confirms `AUTO`.

Automated gate:

- compile debug Java
- compile androidTest Java

### Step 12: Documentation Updates

Purpose: make repo docs match implemented behavior.

Modify:

- `docs/ANDROID_LIME_DITACTION.md`
  - update status and final class names
  - mark implemented behavior
- `docs/ANDROID_VOICE_INPUT.md`
  - note inline dictation as first path once shipped
  - keep VoiceIME/recognizer details as delegated fallback
- `docs/LIME_SETTINGS.md`
  - add Setup tab permission block from `ANDROID_LIME_DITACTION.md`
- `docs/#63_ISSUE.md`
  - update status and mention whether inline dictation addresses the third
    screenshot style

Automated gate:

- compile debug Java
- compile androidTest Java

## Phase 1 Completion Gate

Phase 1 implementation status: completed. The source changes now include
VoiceIME detection protection, route selection, permission helpers, Setup tab
permission UI, inline `SpeechRecognizer` controller, `LIMEService` integration,
candidate strip dictation status, final commit hardening, and automatic route
selection without a user-facing voice mode preference.

After Step 12, run the full automated gate:

```powershell
cd LimeStudio
.\gradlew.bat :app:compileDebugJavaWithJavac
.\gradlew.bat :app:compileDebugAndroidTestJavaWithJavac
```

If a connected Android test device/emulator is already available and stable,
run:

```powershell
cd LimeStudio
.\gradlew.bat :app:connectedDebugAndroidTest
```

`connectedDebugAndroidTest` is not a gate between implementation steps. It is a
post-implementation automated check when an environment is available.

Targeted test classes expected by the end of Phase 1:

- `LIMEServiceTest`
- `VoiceInputActivityTest`
- `LIMEDictationControllerTest`
- `LIMEVoiceInputRouterTest`
- `CandidateViewTest`
- `SetupFragmentVoicePermissionTest` or updated `SetupImFragmentTest`

## Phase 2: Human Verification After Functional Code

Run this phase only after all Phase 1 functional code and docs are complete and
automated verification passes.

Minimum devices/environments:

- Android 13 device or emulator
- Android 14/15 device or emulator
- Android 16 device if available
- Reporter-like Xiaomi device if available
- Pixel/Gboard device if available

Configurations:

- System locale English, Google voice Chinese Taiwan.
- System locale `zh-TW`, Google voice Chinese Taiwan.
- System locale `zh-CN`, Google voice Chinese Taiwan.
- LIME microphone permission granted.
- LIME microphone permission denied once.
- LIME microphone permission permanently denied.
- Google Speech Services enabled.
- Google Speech Services disabled/unavailable.
- Gboard enabled.
- Gboard absent or disabled.

Manual scenarios:

1. Mic permission not granted:
   - tap mic
   - expected: no repeated permission nag; route to VoiceIME then recognizer.
2. Permission granted:
   - tap mic
   - expected: keyboard remains visible; listening state appears.
3. Partial result:
   - speak a long phrase
   - expected: candidate/composing area updates without overlap.
4. Final result:
   - stop speaking
   - expected: final text committed once.
5. Cancel:
   - start dictation then cancel/stop
   - expected: no commit, normal keyboard restored.
6. Focus loss:
   - start dictation, switch app or close field
   - expected: recognizer stops and no crash.
7. Han conversion:
   - dictate text that Google may return Simplified
   - expected: final commit honors Han conversion setting.
8. Delegated fallback:
   - deny permission or disable inline mode
   - expected: VoiceIME/recognizer fallback still works.
9. Recognizer fallback:
   - disable/avoid usable VoiceIME target
   - expected: `RecognizerIntent` fallback opens and uses `zh-TW`.

## Implementation Risks And Guardrails

Risks:

- `SpeechRecognizer` callbacks vary by provider and Android version.
- Runtime permission request cannot be cleanly shown from every IME context.
- Chinese partial results may be sparse or delayed.
- Candidate strip layout is dense; partial text can overlap existing controls.
- IME focus can disappear while dictation is running.

Guardrails:

- Keep delegated VoiceIME and `RecognizerIntent` paths intact until inline
  dictation is verified on devices.
- Add the Setup tab permission path before relying on inline dictation.
- Route permission-denied users to Google/vendor voice input without nagging.
- Keep dictation state controller testable without full UI.
- Use current Han conversion and pending commit paths instead of duplicating
  commit logic.
- Keep first inline UI minimal: listening status plus partial text.

## Suggested Commit Sequence

1. Voice routing helpers and detection tests.
2. Manifest permission, permission helper, and Setup permission row.
3. Dictation controller with fake recognizer tests.
4. `LIMEService` integration with final-result commit.
5. Candidate strip partial UI.
6. Preference gate.
7. Docs and Phase 2 human verification notes.
