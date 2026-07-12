# Issue #153: iOS Array30 `w` + digit symbol codes show no candidates

## Status

- Issue: https://github.com/lime-ime/limeime/issues/153
- Classification: bug, usability
- State: open
- Assignee: `jrywu`
- Platform: iOS confirmed by the maintainer report; Android comparison path is working and already contains a dedicated exception

## Problem statement

With Array30 active on iOS, entering `w` followed by a digit (`w0` through `w9`) does not show the expected Array symbol candidates. The same code family works on Android.

The current Array30 database confirms that digits are end keys but are not ordinary Array roots:

- `imkeys`: `abcdefghijklmnopqrstuvwxyz./;,`
- `endkey`: `1234567890`

This means a digit following `w` needs a narrowly scoped Array30 symbol-code exception rather than being accepted as a general Array root.

## Source evidence

### iOS

`LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift` caches the active table's published/fallback `imkeys` in `currentImKeys` through `refreshImKeys()` and `composingImKeys(...)`.

`acceptsIntoComposing(...)` treats non-empty `currentImKeys` as authoritative. Because Array30's `imkeys` excludes digits, a digit is rejected even when `hasNumberMapping` is true. `handleCharacter(...)` then follows the non-accepted branch, commits the current candidate or code, inserts the digit directly, and finishes composition. No `w#` lookup is performed.

There is currently no iOS equivalent of Android's Array30-specific `w[0-9]*` handling.

### Android comparison

`LimeStudio/app/src/main/java/org/limeime/LIMEService.java` explicitly sets both `hasNumberMapping` and `hasSymbolMapping` for `LIME.IM_ARRAY`. More importantly, `handleCharacter(...)` contains a dedicated fallback after normal `acceptsIntoComposing(...)` rejects the digit:

- active IM must be Array30
- current composition must match `w[0-9]*`
- the new key must be a digit
- Chinese input mode must be active

That branch appends the digit and calls `updateCandidates()`. Its comments identify the exact metadata interaction: digits are not Array roots, but they are valid after `w` for the Array30 symbol lookup.

## Existing test coverage and gap

`LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift` covers layout key codes, number-field routing, composing acceptance, candidate behavior, and several source-level regressions. The inspected tests do not cover Array30 `w` + digit composition or require parity with Android's dedicated exception.

The missing regression gate should exercise the acceptance/dispatch decision for at least:

- `w0` and `w9` accepted into Array30 composition
- `w` followed by a letter continuing normal Array composition
- a bare digit not becoming a general Array30 root
- a digit after a non-`w` Array composition not triggering the exception
- non-Array input methods remaining unchanged

A focused pure policy/helper test is preferable if key dispatch cannot be exercised without a live `UIInputViewController`.

## Likely root cause

High confidence: the iOS composing path made stored `imkeys` authoritative but did not carry forward Android's narrow Array30 symbol-code exception. Array30 metadata correctly excludes digits as ordinary roots, so the general `hasNumberMapping` fallback cannot run once `currentImKeys` is non-empty.

## Proposed fix

Add an iOS Array30-specific helper or branch equivalent to Android's guarded behavior. When all of the following are true, append the digit to composition and update candidates instead of committing/directly inserting it:

1. `activeIM == "array"`
2. Chinese input mode is active
3. the existing composition matches `w[0-9]*`
4. the incoming character is an ASCII digit

Keep digits excluded from general Array30 root acceptance. Avoid changing the table's published `imkeys`, because adding all digits there would broaden composition behavior beyond the `w#` symbol-code syntax.

## Verification plan

### iOS

1. Add focused regression tests for the guarded Array30 `w` + digit decision and negative cases listed above.
2. Run the iOS unit tests on an Xcode-capable environment.
3. In an iOS simulator/device build with Array30 active, verify `w0` through `w9` show the expected symbol candidates.
4. Verify a bare digit and digits after unrelated Array compositions keep their previous behavior.
5. Verify ordinary Array30 letter codes, candidate selection, end-key handling, and English mode are unchanged.

### Android

No Android source change is indicated. The Android path already has the dedicated exception at `LIMEService.handleCharacter(...)`. Keep its existing behavior covered during regression testing if Android code is touched incidentally.

## Follow-up condition

This maintainer-created issue does not need a routine public acknowledgement or community APK retest request. After an iOS fix is merged, verify it in a newer TestFlight/App Store build before marking binary delivery complete.
