# #54 - Extra white band above English URL keyboard in Chrome/Brave

Issue: https://github.com/lime-ime/limeime/issues/54

## Confirmed problem statement
The issue is in browser URL/address-bar input contexts, specifically Chrome and Brave URL bars.

When the URL bar is focused, LIME switches to the English URL keyboard. The keyboard itself is shown, and the empty embedded toolbar row with emoji/mic actions is visible, but an extra blank white band appears above the keyboard view.

This is not primarily the older “Chinese candidates covered by Brave toolbar after EN->ZH switch” symptom. The screenshot confirms the visible defect is:
- Context: Chrome/Brave address bar.
- Input mode: English-only URL keyboard.
- Symptom: a white strip/band above the LIME keyboard area, before the embedded emoji/mic toolbar and key rows.

## Code path
Android:
- `LimeStudio/app/src/main/java/org/limeime/LIMEService.java`
  - `onStartInputView()`
    - `EditorInfo.TYPE_TEXT_VARIATION_URI` sets:
      - `mPredictionOn = false`
      - `mEnglishOnly = true`
      - keyboard mode `LIMEKeyboardSwitcher.MODE_URL`
    - For `mEnglishOnly && !mPredictionOn`, calls `showEmptyCandidateToolbar()`.
  - `showEmptyCandidateToolbar()`
    - clears composing/candidates,
    - sets embedded candidates to empty,
    - calls `mCandidateViewHandler.showCandidateView()`.
  - `CandidateViewHandler.showCandidateView()` eventually calls `setCandidatesViewShown(true)`.
  - `setCandidatesViewShown(true)` currently delegates to `super.setCandidatesViewShown(true)`.
  - `onCreateCandidatesView()` still inflates the separate framework candidates view from `R.layout.candidates`.

## Likely root cause
LIME now uses the embedded candidate/toolbar view inside `R.layout.inputcandidate`, but the old framework candidates-view path is still being shown.

In the URL English no-prediction path, `showEmptyCandidateToolbar()` should expose only the embedded toolbar row. Instead, the call chain can also show Android's separate candidates view above the input view. Because that separate candidate view uses the standalone candidates layout/theme, it can appear as a blank light/white band above the actual keyboard.

## What to inspect/fix
Likely fix area:
- `LIMEService.setCandidatesViewShown(boolean shown)`
- `LIMEService.onCreateCandidatesView()`
- `LIMEService.showEmptyCandidateToolbar()`

Since candidates are embedded in `inputcandidate.xml`, avoid showing the framework candidates view for this path. The embedded candidate container should be updated directly without creating the separate `onCreateCandidatesView()` band.

## Fix applied
- `onCreateCandidatesView()` now returns `null` because LIME no longer uses a separate framework candidates view.
- `setCandidatesViewShown(boolean shown)` now always calls `super.setCandidatesViewShown(false)` so Android does not show the separate candidates window above the embedded keyboard/candidate container.

The embedded toolbar/candidate row remains controlled by `mCandidateViewInInputView.setSuggestions(...)`, `clear()`, and `forceHide()`.

## Verification plan
1. Open Chrome address bar.
2. Focus the URL field with LIME selected.
3. Confirm English URL keyboard appears with `.com`, `/`, `Go`, emoji, and mic controls.
4. Confirm there is no white strip above the LIME keyboard.
5. Repeat in Brave address bar.
6. Regression-check normal Chinese input candidates and EN->ZH switching.

## Verification result
- `.\gradlew.bat :app:assembleDebug` - pass.
- `.\gradlew.bat :app:installDebug` - pass on Pixel 9 Pro Android 16 emulator.
- Chrome URL bar visual check - pass. The English URL keyboard shows the embedded emoji/mic toolbar directly above the keys with no extra white band.
- Brave URL bar visual check - pass after force-stopping stale browser/IME state. The English URL keyboard shows the embedded emoji/mic toolbar directly above the keys with no extra white band.
