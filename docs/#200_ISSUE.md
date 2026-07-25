# Issue #200 Analysis: LINE ID Search Duplicates Typed Characters on Android

## Live issue state

- Issue: https://github.com/lime-ime/limeime/issues/200
- Status: root-caused on-device and fixed in PR #207 (branch `fix/200-null-class-forced-english`); reporter retest on a release build pending.
- Reporter: physical Samsung reproduction by the maintainer; original reporter on `ASUS_AI2202`.
- Reported version: Android LIME 6.1.36, LINE 26.11.0.

## Problem statement

On Android, typing one Latin character into LINE's Add Friends → ID-search field inserts the character twice (one `j` tap produces `jj`). The defect reproduces when entering Latin characters **from the Chinese keyboard** and does **not** reproduce from LIME's dedicated English keyboard.

The reporter also recalled the same symptom in another infrequently used app or input context but could not identify it, so the scope may be broader than LINE; that second context is not yet a reproducible environment.

## Reproduction

- Original reporter: `ASUS_AI2202`, Android 14, LINE Add Friends → ID search.
- Maintainer: independently reproduced the same symptom on a physical Samsung `SM-A1760`.
- The Samsung reproduction was used to capture the runtime `EditorInfo`/dispatch trace below.

## Root cause (LIME side confirmed by on-device trace; host duplication inferred)

A privacy-safe trace (field type + dispatch/commit counts, no field contents) was captured on the Samsung `SM-A1760` while typing one Latin key into the reproducing field.

The field reports:

```
onStartInput inputType=0x90  class=0x0 (TYPE_NULL)  variation=0x90 (VISIBLE_PASSWORD)  imeOptions=SEARCH
```

It declares a **VISIBLE_PASSWORD variation with a null input class** (`0x90`) instead of the well-formed `0x91` (`TYPE_CLASS_TEXT | VISIBLE_PASSWORD`).

`LIMEService.initOnStartInput()` classifies forced-English fields (password / visible-password / web-password / email / web-email, via `isForcedEnglishTextVariation`) inside `case TYPE_CLASS_TEXT`. Because this field's class is `TYPE_NULL`, the switch skips the TEXT case and falls through to `default:` → `mEnglishOnly = false`, Chinese IM keyboard, prediction on. A Latin letter is a root key of essentially every Chinese IM (`acceptsIntoComposing` returns true), so the key takes the **composing path** and is placed in a composing region (`setComposingText`) instead of being committed. The host editor then duplicates the composed character, producing `jj`.

The trace captures LIME's side of the mechanism:

- Chinese keyboard: each tap logs exactly one `onKey` and one `handleCharacter COMPOSE setComposingText` — LIME dispatches once and composes once. There is **no** LIME-side double dispatch and **no** double commit.
- English keyboard: each tap logs `handleCharacter ENGLISH commitText` — an atomic commit, no composing region — and does not duplicate.

The only per-tap difference between the two keyboards is **compose (`setComposingText`) vs commit (`commitText`)**. LINE is a closed app, so its editor's internal duplication of the composing region was not directly instrumented; that step is inferred — but it is the sole remaining variable (LIME emits one character either way), and the fix confirms it: forcing the commit path removes the `jj`. The duplication is therefore tied to the composing region, which only the Chinese keyboard creates.

### Hypotheses refuted by the trace

- **`TYPE_TEXT_VARIATION_URI` / issue #74** — the field is `0x90` (VISIBLE_PASSWORD), not URI (`0x10`). #74 (which moved URI fields out of forced-English) is unrelated to this field.
- **Double key dispatch (onKeyDown + onKey, key repeat)** — exactly one `onKey` per soft tap.
- **LIME-internal double commit / auto-commit** — the composing branch commits nothing per tap; the auto-commit path is gated to numeric/array pads.

## Fix

Add `LIMEService.effectiveInputClass(int inputType)`: a field whose input class is `TYPE_NULL` **and** whose variation is a forced-English text variation (password / visible-password / web-password / email / web-email) is reinterpreted as `TYPE_CLASS_TEXT`, so the existing commit-only handling applies. Every other input type is returned unchanged, so ordinary Chinese text fields still compose. `initOnStartInput()` switches on `effectiveInputClass(attribute.inputType)` instead of the raw class.

This does not introduce new behavior: LIME already forces English (commit-only, no composing) for these same variations when the class is `TYPE_CLASS_TEXT`. The fix only extends that existing policy to the malformed null-class shape LINE sends.

The duplication is a host-editor reaction to the composing region, which LIME cannot change on LINE's side. Suppressing the composing region (commit-only) is therefore the only LIME-side remedy, and it is the correct default for an alphanumeric ID/search field — the same behavior as the English keyboard, which already works.

## Test coverage

`test_200_NullClassForcedEnglishVariationResolvesToText` in `LIMEServiceTest`:

- `effectiveInputClass(0x90)` resolves to `TYPE_CLASS_TEXT` — RED against the pre-fix code (`expected:<1> but was:<0>`), GREEN with the fix.
- The rest of the forced-English set (password/email) with null class also resolves to text.
- Well-formed fields (`TYPE_CLASS_TEXT | VISIBLE_PASSWORD`, plain text, number) are returned unchanged — the fix must not over-force.
- A true null field (no forced-English variation) stays null, so ordinary Chinese composing is preserved.

## Verification

- `effectiveInputClass` unit test: RED→GREEN on a Pixel 9 Pro API 37 emulator and the Samsung `SM-A1760`.
- On-device behavioral confirmation on the Samsung: the reproducing field (`inputType=0x90`) now takes `handleCharacter ENGLISH commitText`, and the `jj` duplication is gone; the ID field commits Latin directly like the English keyboard.

## Scope and limits

- The fix keys on the **variation**, so it resolves fields shaped like LINE's (a forced-English variation with a null class). If another app duplicates in a plain **normal-text** field (variation NORMAL), that composing is legitimate for Chinese and is out of scope here — a separate investigation, consistent with the reporter's unidentified second context.
- Behavior note: in the forced-English field a user can still tap `中` to switch to Chinese; because forced-English leaves `mPredictionOn=false`, that yields a candidates-only (no inline composing) state. This is optional polish, not the reported bug.

## Platform impact

### Android

Confirmed affected and fixed as above. Delivery and reporter-visible confirmation remain pending because the public LIME 6.1.36 predates this change and requires a newer reporter-testable build.

### iOS

No iOS defect is reported. The iOS keyboard extension uses a separate input path, and no independent iOS reproduction exists. No iOS change is planned unless iOS runtime evidence reproduces the same behavior.

## Verification plan

Done:

1. Captured the reproducing field's `EditorInfo` and dispatch/commit counts on the Samsung `SM-A1760`.
2. Added `effectiveInputClass` and the RED→GREEN regression; ran it on the Pixel 9 Pro API 37 emulator and the Samsung.
3. Confirmed on-device that the field now commits Latin (no composing) and the duplication is gone.

Pending:

4. Deliver a newer reporter-testable Android build containing the fix.
5. Ask the original reporter to retest the exact `j` → single-`j` behavior in LINE Add Friends → ID search on that build before closing the issue.
6. If the unidentified second app/field becomes reproducible, capture its `EditorInfo` and evaluate whether it is the same null-class shape or a separate normal-text composing case.
