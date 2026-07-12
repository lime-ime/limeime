# Issue #155: Array30 `hg#` digit symbol lists are unreachable

## Status

- Issue: https://github.com/lime-ime/limeime/issues/155
- Classification: bug, usability
- State: open
- Assignee: `jrywu`
- Fix commit: `81d8dcdc225a117b1664231e135c555f5abfd9ef`
- Android delivery: included in the corrected GitHub testing-track v6.1.30 Release asset (`LIMEHD2026-6.1.30.apk`), pending runtime verification.
- iOS delivery: source fix is on `master`, but TestFlight/App Store delivery was not verified during the GitHub Release closeout.
- Platforms: Android and iOS shared the same reachability gap and were changed by the fix commit.

## Problem statement

Array30's current database contains symbol lists under codes formed by `hg` plus a digit, but both platform input pipelines only make a narrow exception for the separate `w` plus digit symbol-code family. Since digits are intentionally absent from Array30's ordinary `imkeys`, entering the digit after `hg` is rejected before the database can query the corresponding list.

## Verified table evidence

`Database/array.limedb` is a ZIP containing `array.db`. Its `custom` table contains 437 `hg#` mappings:

| Code | Count | Content family / examples |
| --- | ---: | --- |
| `hg0` | 330 | Kangxi radicals and related radical forms, beginning `⼀⼁⼂…` |
| `hg1` | 18 | Computer/office symbols such as `☐☑☒⚙︎💾📞…` |
| `hg2` | 33 | Keyboard and media-control symbols such as `↹⇧⇪⌘⌥⌦⌫…` |
| `hg8` | 18 | Ideographic description characters such as `⿰⿱⿲⿳…` |
| `hg9` | 38 | CJK stroke symbols such as `㇀㇁㇂㇃…` |

The older `Database/array.zip` table does not contain these `hg#` rows. The issue applies to the current LIMEDB table data that does.

## Source evidence

### Android

`LIMEService.handleCharacter(...)` has an Array30-specific fallback only when the current composition matches `w[0-9]*` and the incoming key is a digit. It appends the digit and refreshes candidates. There is no equivalent `hg[0-9]*` path.

### iOS

`KeyboardViewController.isArraySymbolDigit(...)` similarly requires Array30, a composition beginning with `w` whose remaining characters are digits, and an incoming ASCII digit. It does not accept a digit after `hg`.

For both platforms, the normal composing acceptance path correctly rejects digits as general Array30 roots because the table's published `imkeys` excludes them. The missing behavior is therefore a guarded symbol-prefix exception, not a table metadata change.

## Likely root cause

High confidence: the `w#` compatibility logic was implemented as a hard-coded single-prefix rule and did not account for the newer `hg#` symbol families present in `array.limedb`.

## Proposed fix

Create one explicit Array30 digit-symbol policy on each platform that accepts an incoming digit only when:

1. Array30 is active.
2. Chinese input mode is active.
3. The current composition is a verified digit-bearing symbol prefix:
   - `w[0-9]*`
   - `hg` before its first digit, and `hg[0-9]*` if multi-digit continuation is supported by table data/prefix lookup.
4. The incoming key is an ASCII digit.

Prefer a shared helper/policy that is table-driven or clearly lists the verified prefixes, rather than adding unrelated branches. Do not add digits to Array30's general `imkeys`.

## Verification plan

### Shared table verification

- Confirm candidate lookup returns every row for `hg0`, `hg1`, `hg2`, `hg8`, and `hg9`.
- Confirm codes without table rows, including currently unused `hg3`–`hg7`, fail cleanly without committing an unintended digit.

### Android

- Add focused policy and service tests for `hg0`, `hg1`, `hg2`, `hg8`, and `hg9`.
- Verify existing `w0`–`w9` behavior remains unchanged.
- Verify bare digits and digits after unrelated Array30 compositions remain direct numeric input rather than general roots.
- Verify non-Array input methods are unchanged.

### iOS

- Add focused `isArraySymbolDigit`/dispatch tests for all populated `hg#` groups and negative cases.
- Verify on simulator/device that each list appears and candidates commit correctly.
- Verify existing `w#`, ordinary Array30 input, numeric input, and other input methods remain unchanged.

## Platform impact

- **Android:** the fix commit extends the guarded Array30 digit-symbol path to the verified `hg#` codes. The corrected GitHub testing-track v6.1.30 APK includes the change, but runtime verification remains pending.
- **iOS:** the same fix commit extends the iOS guarded digit-symbol path. Source is on `master`; runtime verification and TestFlight/App Store delivery remain pending and are not established by the Android GitHub Release.
