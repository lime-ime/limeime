# Issue #69: Candidate-bar tool icons flicker during continuous input

## Problem statement

Issue #69 tracks a visible candidate-bar flicker on both Android and iOS during fast continuous input.

Observed behavior:

- After the user completes one composition and quickly starts the next one, the candidate bar briefly returns to its idle/tool state before the next candidate list is ready.
- Android briefly exposes the emoji and voice-input icons.
- iOS briefly exposes the emoji and options/menu icons.
- The icon flash is visually perceived as candidate-bar flicker.

Expected behavior:

- During continuous typing, the candidate bar should transition from one active composition/candidate state to the next without briefly exposing idle tool buttons.
- The idle tool buttons should still appear when the keyboard is genuinely idle and no new composition is imminent.

## Current observations

Relevant Android files:

- `LimeStudio/app/src/main/java/org/limeime/candidate/CandidateInInputViewContainer.java`
- `LimeStudio/app/src/main/java/org/limeime/candidate/CandidateView.java`
- `LimeStudio/app/src/main/java/org/limeime/LIMEService.java`

`CandidateInInputViewContainer.requestLayout()` treats `mCandidateView.isEmpty()` as the immediate switch between active-candidate controls and idle tools:

- non-empty candidate view: show dismiss, hide emoji, use the right button as expand/collapse.
- empty candidate view: hide dismiss, show emoji, use the right button as voice input.

`CandidateView.doUpdateUI()` resets and invalidates candidate layout whenever suggestions change. When a composition is committed or cleared before the next lookup returns, the candidate list can become temporarily empty. That empty intermediate frame is enough for `CandidateInInputViewContainer` to show the idle emoji/voice controls even if the next key has already started a new composition.

Relevant iOS files:

- `LimeIME-iOS/LimeKeyboard/CandidateBarView.swift`
- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`

`CandidateBarView.rebuildButtons()` uses `candidates.isEmpty` as the immediate switch between active-candidate controls and idle tools:

- non-empty candidates: show more/separator/dismiss, hide emoji/options.
- empty candidates: hide active controls, show emoji/options.

`KeyboardViewController` owns composition state and calls `candidateBar.setCandidates([])` in several clear paths, including backspace/clear-suggestion flows. During fast input, there can be a short interval where the previous candidate list has been cleared and the next `updateCandidates()` result has not yet populated the bar.

## Likely root cause

Both platforms currently couple idle tool visibility directly to an empty candidate list. During continuous input, an empty candidate list can be a transient loading/state-transition frame rather than true idle state.

Because the candidate lookup/update path is asynchronous or at least split across multiple UI updates, the bar briefly renders idle controls between candidate sets. The flicker is therefore likely a UI state machine problem rather than an icon rendering issue.

## Proposed solution

1. Add an explicit candidate-bar state distinct from `candidates.isEmpty`, such as:
   - active candidates
   - composing/search pending
   - true idle
2. Suppress idle tool icons while composing text exists or a candidate lookup for a fresh composition is pending.
3. Consider a short delayed idle-tools reveal, cancelled by the next composition/candidate update. This matches the issue's initial suggestion and preserves idle tool availability.
4. Android:
   - Gate `CandidateInInputViewContainer` idle icon visibility on both `mCandidateView.isEmpty()` and service/composing/search state, or add a delayed idle reveal owned by the container/service.
   - Ensure `requestLayout()` does not expose emoji/voice buttons for transient empty candidate frames while a new composition is being processed.
5. iOS:
   - Add a similar delayed idle reveal or explicit `isComposingOrSearching` flag to `CandidateBarView`/`KeyboardViewController`.
   - Avoid showing `emojiButton` and `optionsButton` until the keyboard has been idle long enough to rule out a continuous-input transition.
6. Keep the delay short enough that real idle tools still feel responsive, and cancel the delay immediately when candidates or composing text reappear.

## Follow-up questions

- What delay should be used before revealing idle tool icons: 80 ms, 120 ms, or another value based on visual testing?
- Should the delay apply only after committing a Chinese composition, or any time the candidate list becomes empty?
- Should Android voice-input visibility and iOS options visibility use the same delay, or should each platform tune separately?

## Verification plan

- Android:
  - Type repeated Chinese compositions quickly using a table with visible candidate updates.
  - Confirm emoji/voice icons do not flash between consecutive candidate lists.
  - Pause after a commit and confirm emoji/voice icons appear normally in true idle state.
  - Test both soft-keyboard visible and keyboard-hidden/candidate-only modes.
- iOS:
  - Repeat the same fast continuous input flow.
  - Confirm emoji/options icons do not flash between consecutive candidate lists.
  - Pause after a commit and confirm emoji/options buttons appear normally.
  - Verify the candidate bar, expanded candidate panel, and composing strip do not jitter in height or layout.
- Regression checks:
  - Confirm dismiss/expand controls still appear immediately when candidates are shown.
  - Confirm emoji/options/voice tools remain tappable after the idle delay.
  - Confirm no stale candidates remain visible after a real clear/cancel operation.
