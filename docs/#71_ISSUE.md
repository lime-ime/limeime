# Issue #71: Switching to English keyboard leaves stale composing code as output

## Problem statement

Issue #71 was reported as a request for a faster way to clear the current composing code, specifically by clearing composition when switching from Chinese input to English input.

When the user is composing code and switches to the English keyboard, LIME IME should cancel the active composition and leave no text to output. The previous behavior could leave the stale composing code as output after switching to English.

This is analogous to the candidate-bar dismiss behavior fixed in 6.1.7: dismissing/cancelling the current composition should clear the composing buffer, not commit or leak the raw code.

## Expected behavior

- If there is active composing code and the user switches to English keyboard mode, cancel the composition.
- Do not commit the stale composing code to the editor.
- Leave no output from the cancelled composition.
- The behavior should be consistent with the candidate-bar dismiss action introduced/fixed around 6.1.7.

## Actual behavior

- During active composition, switching to English keyboard mode left the stale composing code as output.
- The user then had to manually delete the leaked code, which defeated the purpose of using the switch key as a quick cancel path.

## Current fix status

The relevant Android fix is included in bulk commit `3d5d9c5` (`refactor: keyboard quality pass, prefs cleanup, emoji default 5 (#71)`) and is present in test APK `LIMEHD2026-6.1.8.apk`.

Relevant Android file:

- `LimeStudio/app/src/main/java/org/limeime/LIMEService.java`

Android changed `switchKeyboard(int primaryCode)` so `KEYCODE_SWITCH_TO_ENGLISH_MODE` cancels active composition instead of committing stale raw code:

```java
if (primaryCode == KEYCODE_SWITCH_TO_ENGLISH_MODE) {
    if (mComposing != null && mComposing.length() > 0) {
        clearComposing(true);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.finishComposingText();
    } else {
        clearComposing(false);
    }
} else if (mComposing != null && mComposing.length() > 0) {
    getCurrentInputConnection().commitText(mComposing, 1);
    finishComposing();
    clearComposing(false);
} else {
    clearComposing(false);
}
```

Relevant iOS file:

- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`

iOS changed `switchChiEng(toEnglish:)` so switching to English uses the candidate-dismiss cancel path:

```swift
if toEnglish {
    cancelActiveComposingFromCandidateDismiss()
} else {
    clearComposing(force: false)
}
```

This keeps legacy auto-commit behavior for other switch paths while making the explicit Chinese-to-English switch behave as a cancel operation.

Reporter `ejmoog` confirmed on 2026-05-22 that Android APK `6.1.8` implements the requested Chinese/English switch composition-cancel behavior, and the issue was closed as completed. The iOS code path is implemented but not reporter-verified by this Android APK confirmation.

## Root cause addressed

The Chinese/English switch path previously did not consistently treat active composition as a cancel operation. For this issue, switching from Chinese composition to English should cancel active composition and remove inline composing text, not preserve or commit it.

The 6.1.8 fix addresses the two known soft-keyboard paths:

1. Android `KEYCODE_SWITCH_TO_ENGLISH_MODE` now calls `clearComposing(true)` and `finishComposingText()` when composition is active, instead of committing `mComposing`.
2. iOS `switchChiEng(toEnglish: true)` now calls `cancelActiveComposingFromCandidateDismiss()` instead of only `clearComposing(force: false)`.

## Implemented solution

The implemented behavior routes explicit Chinese-to-English switching through safe composition-cancel semantics while leaving other switch modes on the legacy commit/clear path.

Remaining engineering review items, if future reports appear:

- Verify whether any physical-keyboard Chinese/English switch shortcut needs parity with the soft-keyboard behavior.
- Verify the iOS `abc` / Chinese toggle behavior on device or simulator.

## Follow-up questions

- Should switching from Chinese to symbol/emoji modes also cancel instead of commit active composing code, or is this issue limited to the explicit Chinese/English switch key?
- Should language switching always cancel active composition, or should this become a setting if some users rely on the previous auto-commit behavior?

## Verification result

Reporter `ejmoog` tested Android APK `6.1.8` and confirmed: 「安裝了6.1.8，功能已實現，感謝！」

The internal verification plan was:

1. Start composing a code sequence that has not been committed yet.
2. Tap the Chinese/English switch key while composition is active.
3. Verify that the composing/candidate state is cleared.
4. Verify that no raw code or stale text is inserted into the target editor.
5. Repeat with the candidate-bar dismiss button and confirm both paths behave consistently.
6. Repeat with at least one table where the typed code has valid candidates and one where it does not.
7. Repeat on iOS with the `abc` / Chinese toggle while composing, including iPhone and iPad layouts if possible.
8. Repeat with a physical keyboard Chinese/English switch shortcut, if supported, to confirm parity.

Only the core Android APK behavior was reporter-verified in the public comment. Items 5-8 remain internal regression checks if related reports appear.

Follow-up status: reporter-confirmed fixed on Android APK `6.1.8`; issue is closed as completed. No active GitHub watch is needed unless the reporter reopens or reports a new regression.
