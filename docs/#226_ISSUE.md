# Issue #226: iOS email fields lose Chinese composing after the 中 switch

## Current status

- Issue: https://github.com/lime-ime/limeime/issues/226
- State: fixed, device-confirmed on iPhone
- Classification: confirmed iOS defect, LIME-owned — prediction flag disabled the candidate pipeline in Chinese mode
- Reported environment: LIME 6.1.37, iOS 26.6, Outlook 5.2629.0
- Reporter-confirmed platform split: iOS affected; Android works normally

## Problem statement

The reporter cannot use Chinese input in Outlook's recipient and sender fields on iOS. The behavior occurs with both Cangjie and Array, while Android works normally. The report includes a portrait screenshot and exact LIME, iOS, and Outlook versions.

The expected behavior is that address fields which accept contact names should allow the active Chinese input method, while still allowing the user to switch to English for a literal email address.

## Reported reproduction

1. Use LIME 6.1.37 on iOS 26.6 with Outlook 5.2629.0.
2. Focus an Outlook recipient or sender field.
3. Select Cangjie or Array in LIME.
4. Attempt Chinese input.

Actual result: Chinese input is unavailable in both fields.

Expected result: the fields allow Chinese names to be entered with the selected LIME input method.

## Evidence and source assessment

Outlook recipient and sender controls advertise `.emailAddress`, which LIME classified as forced-English.

Forcing English is **not** itself the defect, and the design intent is English-first. Android does the same thing, deliberately:

- `LIMEService.isForcedEnglishTextVariation()` groups `TYPE_TEXT_VARIATION_EMAIL_ADDRESS` and `WEB_EMAIL_ADDRESS` with the three password variations.
- `LIMEService` line ~1007 sets `mEnglishOnly = true`, `mPredictionOn = false`, and switches to `LIMEKeyboardSwitcher.MODE_EMAIL`.
- The `MODE_EMAIL` row in `res/xml/lime_english.xml` carries `done`, **`中` (code -10)**, `@`, and a popular-domain key. English-first, with the Chinese IM one tap away.

Every iOS English layout carries the same `中` key (`LimeKeyCode.switchToIM` = -10) — verified across all twelve `lime_english*.json` variants plus `symbols1.json`. So the Chinese switch was present in the Outlook field all along, and `switchChiEng()` sets `mEnglishOnly = false` directly.

So the switch worked, and the reported failure was downstream of it. Device testing confirmed the sequence: the field opens in English (correct), `中` switches to the Chinese IM keyboard (correct), and then composing produces bare English letters.

## Root cause

`.emailAddress` set `mPredictionOn = false` alongside `mEnglishOnly = true`, and **that survived the switch into Chinese**. `mPredictionOn` does far more than its name suggests on iOS — it guards `updateCandidates()`, the entire Chinese candidate pipeline.

The failure is invisible rather than obvious because iOS keyboard extensions have no `setComposingText`. LIME **simulates** composing by inserting the raw letter into the host field (`handleCharacter`, spec §12) and showing the composing popup plus candidate bar. With prediction off:

- `handleCharacter` correctly took the Chinese branch, and `mComposing` accumulated correctly;
- the raw Latin letter was inserted inline, as designed;
- `updateCandidates()` returned immediately at its `guard mPredictionOn` — no candidate bar, no composing popup, nothing to commit.

The result is bare Latin letters accumulating in the field, which is indistinguishable from "still in English mode" — which is how the issue was reported. Composing was running the whole time; it just had no UI and no way to commit.

This was the shipped behaviour in 6.1.37. It affected `.phonePad` identically.

**Android parity trap.** Android sets `mPredictionOn = false` for the same variations and is fine, because `setComposingText()` underlines the composing text in the host field independently of the candidate view. iOS has no such affordance, so the same value is benign on one platform and fatal on the other. Copying the Android line here without accounting for that is precisely how this was reintroduced mid-investigation.

**Secondary defect:** the forced-English set existed in two places, and the only test asserted against the copy the keyboard never called — so the helper could be corrected while runtime behaviour stayed broken, with the suite green.

**Tertiary defect:** `initOnStartInput()` re-runs `updateInputModeForCurrentField()` whenever the keyboard re-appears or the field's traits change, which unconditionally re-forced English. Outlook re-inits its recipient field after every committed chip. Not proven to have fired in this report, but it would discard the user's manual switch, so it is fixed alongside.

## Applied fix

Design: **English-first, not English-only** — matching Android's `MODE_EMAIL`.

`LimeIME-iOS/Shared/Models/KeyboardTypePolicy.swift`

- `isForcedEnglishKeyboardType(...)` keeps `.emailAddress`; the set still mirrors Android's `isForcedEnglishTextVariation()`.
- New `forcesEnglish(keyboardType:userSwitchedToChineseInField:)` — the host hint applies only until the user switches into Chinese inside that field.

`LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`

- **New `predictionOnForField(keyboardType:isEnglishOnly:)` — the actual fix.** Prediction is off only while the field is *both* English-first *and* still in English; Chinese mode always keeps it on, because composing cannot work without the candidate pipeline. English autocomplete is still suppressed for English typing in address/phone fields, which is what the original `false` was for.
- `switchChiEng(toEnglish: false)` sets `mPredictionOn = true` directly, so the `中` key works immediately rather than waiting for a re-init.
- New `userSwitchedToChineseInField` flag, assigned `!toEnglish` in `switchChiEng` and cleared in `updateInputModeForCurrentField()` on any keyboard-type or return-key-type change (i.e. a genuinely different field). It tracks **both** directions deliberately: leaving it set after a switch back to English would flip the field to Chinese on the next re-init, against the user's last choice.
- `updateInputModeForCurrentField()` no longer duplicates the forced-English set — it calls the policy helper.

`LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift`

- The existing policy test keeps its `.emailAddress` expectation and now documents that the helper is the production decision point rather than a parallel copy.
- New `testForcedEnglishFieldKeepsManualChineseSwitch()` covers the English-first-then-switchable contract, that ordinary fields are unaffected, and that switching back to English clears the flag.
- New `testChineseModeAlwaysKeepsPredictionInEnglishFirstField()` covers the root cause directly: prediction off for email/phone while in English, on for numeric, and on in Chinese mode regardless of field type.

No change was needed in `layoutIdForCurrentInputField(...)`: it has no `.emailAddress` branch, so once `mEnglishOnly` is false the field resolves to `resolvedActiveLayoutId`, the active Chinese layout. Numeric, phone, URL and search routing are untouched.

No Outlook-specific branch was introduced. The fix is keyed on the keyboard type alone.

### Known gap versus the built-in keyboard

iOS has no email-specific layout, so `@` and the popular-domain shortcuts that Android's `MODE_EMAIL` row provides are only on the symbols layer. Apple's keyboard puts `@` on the letters row for `.emailAddress` fields. This predates the issue and is unchanged by the fix; treat it as separate work if iOS/Android layout parity is wanted.

## Platform impact

### iOS

Fixed. `.phonePad` had the identical defect and is fixed by the same change: switching to Chinese in a phone field now restores the candidate pipeline too.

### Android

Not affected, no change. The reporter confirms Android works normally, and Android's `MODE_EMAIL` row is the reference design for the English-first half of this behaviour. Do **not** port `predictionOnForField` to Android — Android's `mPredictionOn = false` is correct there because `setComposingText` provides composing feedback independently of the candidate view.

## Verification

Completed:

- Full `LimeTests` unit suite, run locally — **PASS, 1183/1183 cases, 0 failures**
- `testChineseModeAlwaysKeepsPredictionInEnglishFirstField()` — PASS (covers the root cause)
- `testForcedEnglishFieldKeepsManualChineseSwitch()` — PASS
- Physical iPhone, Outlook recipient field: opens in English, `中` switches to the Chinese IM, **Chinese composition and candidates work** — maintainer-confirmed

Remaining, on device:

1. Confirm the mode survives a committed recipient chip (host re-init path).
2. Verify the same in the 寄件者 field, and with both Cangjie and Array.
3. Spot-check an ordinary email form field, plus URL, search, numeric and phone fields, for regressions.
4. Confirm switching back to English inside the field sticks (flag-clears-on-English path).
4. Ask the reporter to retest after the fix ships in a build.
