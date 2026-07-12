# Issue #68: Candidate-bar dismiss should fully cancel composition

## Problem statement

Issue #68 tracks inconsistent and incomplete composition cancellation when the user taps the dismiss button on the candidate bar.

Expected behavior:

- Tapping dismiss should fully cancel the active composition.
- The visible composing text/code should be removed from the host input field or composing area.
- Candidate rows, composing popup/strip, and platform composing state should all be cleared.

Observed behavior:

- iOS: the composition state is closed, but the inline composing text remains in the host input field.
- Android: the candidate-bar composing display is cleared, but Android's composing state remains open instead of being cancelled.

## Current observations

Relevant iOS files:

- `LimeIME-iOS/LimeKeyboard/CandidateBarView.swift`
- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`

`CandidateBarView` exposes `candidateBarViewDidRequestDismiss(_:)` through its delegate. `KeyboardViewController` owns the actual composing state: `mComposing`, `composingLength`, candidate lists, and the inline composing simulation that inserts typed composing code into `textDocumentProxy`.

`KeyboardViewController` already has separate helpers with different semantics:

- `clearComposing(force:)`: when `force` is true, deletes the tracked inline composing characters from the document before resetting state.
- `cancelComposing()`: clears internal composing/candidate state without touching the document.
- `finishComposing()`: resets internal tracking after text has already been committed or cleared.

The reported iOS behavior suggests the dismiss path is currently using a state-only clear/cancel path, or otherwise avoids `clearComposing(force: true)`, so the inline composing simulation remains visible in the host text field.

Relevant Android files:

- `LimeStudio/app/src/main/java/org/limeime/candidate/CandidateInInputViewContainer.java`
- `LimeStudio/app/src/main/java/org/limeime/candidate/CandidateView.java`
- `LimeStudio/app/src/main/java/org/limeime/LIMEService.java`

`CandidateInInputViewContainer.onClick()` calls `mCandidateView.dismissComposingFromCandidate()` when the dismiss button is tapped. `CandidateView.dismissComposingFromCandidate()` hides the candidate popup and delegates to `mService.dismissCandidateComposing()` when the service is available. The reported Android behavior suggests that this service path clears candidate UI state but does not fully finish/cancel the active `InputConnection` composing text/state.

## Likely root cause

The dismiss button currently behaves more like "hide candidate/composing UI" than "cancel composition transaction".

On iOS, the composition buffer is simulated by inserting composing characters inline and tracking `composingLength`. A dismiss action that clears only `mComposing`, candidate lists, or the popup will leave those inserted characters in the document. The dismiss action should use the same force-removal path as backspace case `mComposing.count == 1`, or a dedicated cancel helper that deletes `composingLength` characters before resetting state.

On Android, the dismiss action appears to clear the LIME candidate/composing UI but may not call the `InputConnection` APIs needed to end composing text, such as `finishComposingText()`, nor reset all service-side composing fields. This leaves Android's composing session logically open even though the candidate row no longer shows the composing text.

## Proposed solution

1. Define dismiss as a full cancel operation, not a UI-only clear.
2. iOS:
   - Route `candidateBarViewDidRequestDismiss(_:)` to a helper that removes the inline composing text when `composingLength > 0`.
   - Prefer reusing or adapting `clearComposing(force: true)` so `mComposing`, `composingLength`, candidate lists, composing popup/strip, and selected candidate state reset together.
   - Ensure this path does not commit the highlighted candidate and does not leave stale related/candidate state.
3. Android:
   - Audit `LIMEService.dismissCandidateComposing()` and make it cancel both LIME state and `InputConnection` composing state.
   - Ensure it clears `mComposing`, candidate lists/flags, composing popup, candidate view contents, and calls `finishComposingText()` on the current `InputConnection` when available.
   - Verify the service does not leave `mPredicting` or candidate flags in a state where the next key resumes the old composition.
4. Keep behavior consistent across normal candidate bar and expanded candidate popup dismiss buttons.

## Follow-up questions

- On iOS, should dismiss delete the inline composing code only, or also clear any visible related/chinese-punctuation candidate list when no active composing code exists?
- On Android, should dismiss also hide the keyboard/candidate popup if the keyboard view is currently hidden, or only cancel composition state?
- Should dismiss preserve English prediction suggestions, or should it clear all candidate-like UI consistently?

## Verification plan

- iOS:
  - Start a Chinese composition by typing several composing keys.
  - Tap candidate-bar dismiss.
  - Confirm the composing code disappears from the host text field.
  - Confirm candidate bar, composing strip/popup, and selection state are empty.
  - Type a new key and confirm no stale composition is reused.
- Android:
  - Start a Chinese composition by typing several composing keys.
  - Tap candidate-bar dismiss.
  - Confirm candidate bar and composing popup are empty.
  - Confirm the host editor no longer has active composing text/underline.
  - Confirm `InputConnection.finishComposingText()` semantics by typing after dismiss and verifying a fresh composition starts.
- Test both collapsed and expanded candidate UI paths.
