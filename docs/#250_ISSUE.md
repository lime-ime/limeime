# Issue #250: Android symbol-mode switch dismisses LIME in Pikmin search

Issue: https://github.com/lime-ime/limeime/issues/250

## Classification

Confirmed reporter-visible Android defect, pending maintainer reproduction and root-cause isolation.

The report was received privately and was reproduced on two Android devices, Pixel 10 and Pixel 5, in Pikmin's search field. The Pixel 10 runs Android 17; the Pixel 5 Android version and both LIME/Pikmin versions are not yet known. A maintainer-reviewed private recording shows the first symbol page appearing briefly before the entire IME closes and the host content expands. The recording and reporter identity must remain private.

## Problem statement

In Pikmin's Android search field, LIME is visible and accepts input, but tapping the keyboard's `123` key only shows the first symbol page briefly before the entire input method closes and the host content expands. The issue is app/field-specific so far. No equivalent behavior has been reported in another Android editor.

The visible result conflicts with LIME's accepted mode-key contract: key code `-2` changes between the current alphabet/IM layout and symbol mode. Keyboard dismissal belongs to the separate done/dismiss key and global swipe-down path.

## Architecture preflight

Authoritative current references reviewed in full:

- `docs/ANDROID_IPHONE_KEYBOARD.md`
  - **Source Map** identifies `LIMEService` as Android's final action dispatcher and `LIMEKeyboardSwitcher` as the keyboard-type switcher.
  - **Special Key Codes** defines `-2` as symbol-mode switching and `-3` as keyboard dismissal.
  - **General Swipe Gestures** separately defines Android swipe-down as keyboard dismissal.
- `docs/KEYBOARD_TYPE.md`
  - **Field-by-field status**, URL/search row, defines Android search fields as normal text and expects ordinary IM/English layout behavior rather than a restricted field layout.
  - **Return-key adaptation** limits search-specific behavior to the host-provided action key and does not redefine `123`.
- `docs/LIMEIME_ARCHITECTURE.md`
  - **Core Components / LIMEService** assigns input-method lifecycle and keyboard switching to `LIMEService`, with `LIMEKeyboardSwitcher` managing keyboard type.
  - **IME Logic Flow / Soft Keyboard Input Flow** routes soft-key actions through `LIMEService.onKey()`.
  - **Lifecycle and Initialization / LIMEService Lifecycle** leaves IME presentation lifecycle under Android's `InputMethodService` contract.

No accepted amendment, successor, or supersession note was found that changes the `-2` contract for Android search fields.

### Constraint ledger

| Item | Constraint |
| --- | --- |
| Required behavior | A tap on `123` (`KEYCODE_MODE_CHANGE`, `-2`) must switch the visible LIME layout in place to symbol mode while the editor remains focused. |
| Governing invariant | Symbol switching and IME dismissal are separate actions. `-2` must not intentionally call the keyboard-close path used by `-3` or swipe-down. |
| Platform limitation | Android or the host app can revoke editor focus or request that the IME be hidden. LIME cannot keep its window visible after a legitimate host/framework teardown, so logs must distinguish LIME-initiated hiding from host focus/input-connection loss. |
| Removable behavior | Any LIME-side mode-switch side effect that unnecessarily tears down the input view, changes focus, or sends an editor-visible action may be removed or isolated. Legitimate host-driven hide/focus lifecycle handling must remain. |
| Consequence of change | A broad workaround that suppresses normal `onFinishInput*`, focus loss, or host hide requests could leave a stale IME attached to the wrong editor. The correction must be limited to the mode-switch transaction proven by reproduction/logs. |

## Current production flow

The current Android source does not intentionally close LIME for `123`:

1. Keyboard XML assigns `codes="-2"` to the visible `123`/symbol key.
2. `LIMEService.onKey()` routes `KEYCODE_SWITCH_TO_SYMBOL_MODE` to `switchKeyboard(-2)`.
3. `switchKeyboard(-2)` clears/commits composition as applicable, hides candidate content, sets `mEnglishOnly`, and calls `mKeyboardSwitcher.toggleSymbols()`.
4. `LIMEKeyboardSwitcher.toggleSymbols()` calls `setKeyboardMode(...)` with the symbol flag inverted.
5. `setKeyboardMode(...)` resolves `symbols1`, obtains the keyboard object, and calls `mInputView.setKeyboard(...)` in place.
6. The explicit close path is `KEYCODE_DONE -> handleClose() -> requestHideSelf(0)`, not the `-2` path.

The recording confirms that the normal mode-switch path reaches a visibly attached symbol keyboard before dismissal. This makes failure to resolve or attach `symbols1`, and a direct `requestHideSelf()` call from the normal `-2` branch, less likely. The remaining fault boundary is after attachment: a host-triggered focus/input-connection transition, an exception or state inconsistency immediately after the switch, or another app-specific lifecycle interaction that static source inspection cannot identify.

## Likely root cause

Root cause is not yet established.

The leading investigation hypothesis is a post-switch interaction at the Android editor/IME lifecycle boundary that Pikmin's search field exposes after LIME replaces its keyboard layout. The observed symbol-page frame proves the replacement starts successfully, while static inspection shows no intentional hide request in the `-2` path. A code-only claim that `toggleSymbols()` directly closes LIME would still be an overstatement. A logcat reproduction must establish which component initiates dismissal and whether the LIME process logs an exception after the symbol layout is attached.

## Proposed solution direction

1. Reproduce on a current Android build with the same Pikmin search path and record the exact `EditorInfo.inputType`, variation/flags, and `imeOptions`.
2. Instrument or capture `onKey(-2)`, `switchKeyboard(-2)`, `toggleSymbols()`, `setKeyboardMode()`, `onStartInput*`, `onFinishInput*`, `onWindowHidden`, current input-connection identity, and uncaught exceptions.
3. Compare the same LIME build and active IM in:
   - Pikmin search
   - a normal Android text field
   - another app's search field
4. Add a focused RED regression at the lowest production seam that reproduces the proven trigger. The test must assert that mode change attaches `symbols1` without invoking LIME's close path or corrupting the active editor transaction.
5. Correct only the proven LIME-side trigger. If logs instead prove Pikmin or Android explicitly removes editor focus, document the upstream boundary and evaluate a safe compatibility path without suppressing legitimate lifecycle events.

Do not implement a speculative global lifecycle override from the current static evidence.

## Follow-up questions and evidence needed

- Exact Android version on the Pixel 5.
- Exact LIME version and distribution channel.
- Exact Pikmin version.
- Active LIME input method and keyboard layout.
- Whether the failure occurs every time.
- Whether tapping the input field immediately restores LIME after dismissal.
- Whether `123` works in other Pikmin text fields, other apps' search fields, and ordinary text fields.
- A filtered logcat covering the tap through dismissal, including LIME exceptions and input-method lifecycle callbacks.

## Verification plan

### Android

- Reproduce the original Pikmin search-field workflow on both reported device generations when available.
- Verify `123` changes to `symbols1` and LIME remains visible.
- Verify symbol-page cycling and return from symbols still work.
- Verify Chinese IM, English, Array10/phone-style, and number-row layouts that expose `-2`.
- Verify generic text, search, URL, email/password, and restricted number/phone fields preserve their accepted routing.
- Verify composition behavior during mode switch, including empty composition and active composition.
- Verify done-key and swipe-down dismissal still close LIME.
- Verify switching fields, app background/foreground, and host-driven focus loss still follow Android lifecycle events.
- Run focused unit/instrumentation tests plus a real-device Pikmin check on the exact fixing build.

### iOS

No iOS defect is established. Pikmin and both reported devices are Android-specific, and iOS uses a separate UIKit keyboard-extension implementation. Keep iOS source unchanged unless independent evidence reproduces the same visible contract failure. As parity coverage, verify in an iOS search field that code `-2` still switches layouts while the keyboard remains visible, but do not represent that check as reproduction of issue #250.

## Public/private handling

The public issue may state the app, generic Pixel models, maintainer-observed visible sequence, and requested version/reproduction details already present in the issue. Do not publish the private recording, attachment metadata, reporter identity, or any additional private context.
