# LIME Toast

## Goal

Android now has an IME-local `showLimeToast` path for keyboard feedback. iOS should use an equivalent path so IM switching and reverse lookup feedback appear in the same candidate-bar/composing-strip area instead of being silent or using a separate mechanism.

Chinese/English mode switching intentionally does not show a lime toast on either platform.

Voice input is excluded from this parity pass.

## Summary Call Sites

| Behavior | Android | iOS |
|---|---|---|
| Next/previous IM cycling | `LIMEService.switchToNextActivatedIM(boolean forward)` → `showLimeToast(activeIMName)`; message: `activeIMName`; short auto-hide. | `KeyboardViewController.switchToNextActivatedIM(forward:)` → `showLimeToast(displayName(for: im))`; message: `im.label`, else `im.tableNick`, else active IM id; short auto-hide. |
| IM picker selection | `LIMEService.handleIMSelection(int position)` → `showLimeToast(activeIMName)`; message: selected IM full name from `activatedIMFullNameList`; short auto-hide. | `KeyboardViewController.switchIM(toIndex:)` → `showLimeToast(displayName(for: im))`; message: `im.label`, else `im.tableNick`, else active IM id; short auto-hide. |
| Reverse lookup | `SearchServer.learnRelatedPhraseAndUpdateScore()` reverse lookup branch → `showReverseLookup(result)` → persistent lime toast; message: reverse lookup `result`; stays until next keystroke. | Reverse lookup callback in `KeyboardViewController` → `showLimeToast(result)`; message: reverse lookup `result`; short auto-hide. |
| Voice input unavailable | `LIMEService.launchRecognizerIntent()` → `showLimeToast(...)`; messages: `Voice recognition not available on this device`, `Voice input activity not found`, `Cannot launch voice input (security restriction)`, or `Voice input unavailable: ` + exception message; short auto-hide. | Excluded from this parity pass; no iOS lime toast. |
| Chinese/English mode switching | None; intentionally silent. | None; intentionally silent. |

## Android Current Behavior

Android defines the service entry point in:

- `LimeStudio/app/src/main/java/org/limeime/LIMEService.java`

```java
public void showLimeToast(CharSequence text) {
    if (text == null || text.length() == 0) return;
    try {
        if (Looper.myLooper() != null) {
            CandidateView toastTarget = mCandidateView;
            if (mCandidateViewInInputView != null && mCandidateViewInInputView.getWindowToken() != null) {
                toastTarget = mCandidateViewInInputView;
            }
            if (toastTarget != null) {
                toastTarget.showLimeToast(text);
            }
        }
    } catch (RuntimeException e) {
        Log.w(TAG, "Cannot show lime_toast: " + e.getMessage());
    }
}
```

The visual implementation lives in:

- `LimeStudio/app/src/main/java/org/limeime/candidate/CandidateView.java`

`CandidateView.showLimeToast()` posts through its handler, shows a popup near the composing/candidate bar, replaces any previous lime toast, and hides it after the short toast duration. `showLimeToastUntilNextKey()` uses the same visual path but leaves the toast visible until the next key press clears it.

## Android Call Sites

| Call site | File | Exact toast message | Behavior |
|---|---|---|---|
| `switchToNextActivatedIM(boolean forward)` | `LIMEService.java` | `activeIMName` | Shows the newly active IM display name after cycling to the next/previous activated IM. |
| `handleIMSelection(int position)` | `LIMEService.java` | `activeIMName` from `activatedIMFullNameList.get(position)` | Shows the selected IM display name after choosing an IM from the picker dialog. |
| `launchRecognizerIntent()` | `LIMEService.java` | `Voice recognition not available on this device` | Shows when no voice recognition activity is available. Excluded from iOS parity for this pass. |
| `launchRecognizerIntent()` | `LIMEService.java` | `Voice input activity not found` | Shows when `VoiceInputActivity` cannot be launched. Excluded from iOS parity for this pass. |
| `launchRecognizerIntent()` | `LIMEService.java` | `Cannot launch voice input (security restriction)` | Shows on `SecurityException`. Excluded from iOS parity for this pass. |
| `launchRecognizerIntent()` | `LIMEService.java` | `Voice input unavailable: ` + exception message | Shows on other launch failures. Excluded from iOS parity for this pass. |

Android reverse lookup uses the persistent lime-toast path:

| Call site | File | Exact message | Behavior |
|---|---|---|---|
| `SearchServer.learnRelatedPhraseAndUpdateScore()` reverse lookup branch | `SearchServer.java` | `result` from reverse lookup, for example an IM/code lookup string | Calls `LIMEService.showReverseLookup(result)`, which shows a persistent lime toast until the next keystroke. |

For the iOS implementation, reverse lookup should be unified into the new lime-toast path so there is one feedback mechanism.

## iOS Previous Behavior

iOS previously had a separate reverse lookup display in:

- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`

```swift
private func showReverseLookup(_ message: String) {
    candidateBar.composingText = message
    if let lbl = expandedComposingLabel {
        lbl.attributedText = CandidateBarView.attributedKeyname(
            message, baseFont: candidateBar.composingStripFont,
            color: lbl.textColor ?? .label)
    }
}
```

The reverse lookup call site is:

```swift
DispatchQueue.main.async { self?.showReverseLookup(result) }
```

iOS did not show equivalent lime toast feedback for:

- Cycling IMs in `switchToNextActivatedIM(forward:)`.
- Picking an IM from the long-press IM picker through `switchIM(toIndex:)`.

## iOS Gap

| Android behavior | Android exact message | iOS status | Required iOS change |
|---|---|---|---|
| IM cycle shows active IM name | `activeIMName` | Missing | Call `showLimeToast(im.label.isEmpty ? im.tableNick : im.label)` after `switchToNextActivatedIM(forward:)` completes. |
| IM picker selection shows active IM name | Same as IM cycle: `activeIMName` equivalent | Missing | Call `showLimeToast(im.label.isEmpty ? im.tableNick : im.label)` after `switchIM(toIndex:)` completes. |
| Reverse lookup feedback | Reverse lookup `result` string | Present, separate `showReverseLookup` path | Rewrite to use `showLimeToast(result)` so the path is Android-equivalent. |
| Voice input errors | Android voice error strings listed above | Excluded | Do not add in this pass. |

## iOS Messages To Use

| iOS call site | Exact toast message |
|---|---|
| `switchToNextActivatedIM(forward:)` | `im.label.isEmpty ? im.tableNick : im.label` |
| `switchIM(toIndex:)` | `im.label.isEmpty ? im.tableNick : im.label` |
| Reverse lookup | Reverse lookup `result` string from the existing lookup callback |
| Voice input | No iOS toast in this pass |

## iOS Implementation Plan

Add a small shared state helper in `LimeIME-iOS/Shared/Models/LimeToastState.swift`:

```swift
struct LimeToastState {
    private(set) var message: String?

    var isShowing: Bool { message != nil }

    @discardableResult
    mutating func show(_ message: String) -> Bool {
        let trimmed = message.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }
        self.message = trimmed
        return true
    }

    mutating func hide() {
        message = nil
    }
}
```

Use it from an Android-equivalent helper in `KeyboardViewController.swift`:

```swift
private var limeToastState = LimeToastState()
private var limeToastTimer: Timer?

private func showLimeToast(_ message: String) {
    guard limeToastState.show(message), let text = limeToastState.message else { return }
    limeToastTimer?.invalidate()
    candidateBar.composingText = text
    if let lbl = expandedComposingLabel {
        lbl.attributedText = CandidateBarView.attributedKeyname(
            text, baseFont: candidateBar.composingStripFont,
            color: lbl.textColor ?? .label)
    }

    limeToastTimer = Timer.scheduledTimer(withTimeInterval: 1.5, repeats: false) { [weak self] _ in
        self?.hideLimeToast()
    }
}

private func hideLimeToast() {
    limeToastTimer?.invalidate()
    limeToastTimer = nil
    guard limeToastState.isShowing else { return }
    limeToastState.hide()
    candidateBar.composingText = nil
    expandedComposingLabel?.attributedText = nil
    expandedComposingLabel?.text = nil
}
```

Then rewrite reverse lookup to call the same path directly:

```swift
DispatchQueue.main.async { self?.showLimeToast(result) }
```

Update `hideComposingPopup()` so it does not erase an active lime toast:

```swift
private func hideComposingPopup() {
    guard !limeToastState.isShowing else { return }
    candidateBar.composingText = nil
    expandedComposingLabel?.attributedText = nil
    expandedComposingLabel?.text = nil
}
```

When a new key press or explicit state change should clear transient feedback, call `hideLimeToast()` before showing composing keynames or candidates.

## iOS Call Sites To Add

### Next / Previous Activated IM

In `switchToNextActivatedIM(forward:)`, after the new layout is applied:

```swift
showLimeToast(im.label.isEmpty ? im.tableNick : im.label)
```

### IM Picker Selection

In `switchIM(toIndex:)`, after the new layout is applied:

```swift
showLimeToast(im.label.isEmpty ? im.tableNick : im.label)
```

## Tests

Add focused iOS tests where possible:

- Verify `showLimeToast` writes to the candidate-bar composing text.
- Verify a second toast replaces the first timer/text.
- Verify `hideComposingPopup()` does not clear an active lime toast.
- Verify `hideLimeToast()` clears the composing text after the timer path.
- Verify Chinese/English mode switching does not call `showLimeToast`.

If direct unit tests are hard because `KeyboardViewController` owns UIKit-private state, use a small test-only helper around the lime-toast state machine or an accessibility/UI test that triggers:

- Next/previous IM key.
- IM picker selection.

Manual visual verification should confirm the toast appears in the candidate-bar/composing-strip region and disappears automatically.
