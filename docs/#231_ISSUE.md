# Issue #231: iOS Easy Input phone popup roots emit the wrong code

## Status

- Issue: https://github.com/lime-ime/limeime/issues/231
- Classification: confirmed iOS bug
- State: PR #245 merged as squash commit `9a38d34de8782bae7c617c1aba82965f9833abc1` on 2026-08-18. The reporter-visible iPhone path is source-corrected and maintainer-verified on hardware; generated-layout durability, direct `code == 0` route coverage, and public iOS delivery remain pending
- Scope: iPhone Easy Input (`lime_ez`) long-press popups. iPad behavior and Android production behavior are unchanged
- Supersedes: PR #240 (`fix/231-ios-ez-popup`), closed — see **Rejected approach** below

## Problem statement

On the iPhone Easy Input layout, the number-row keys `1`–`6` carry long-press popup roots whose Easy Input table codes differ from the root shown. Selecting a root emitted the wrong character and did not enter the composing buffer, so the root never reached candidate lookup.

## Reproduction

1. Enable Easy Input on an iPhone and enter Chinese mode.
2. Long-press `1` and select the popup root.
3. Before the fix: the popup offered four items (`-`, `\`, `n`, `儿`), and selecting any of them committed that character to the host editor without entering the composing buffer.
4. After the fix: the popup offers one item, and selecting it puts `-` into composing and updates candidates.
5. Repeat for `2`–`6` — see the table below for each key's table code.

## Root cause

Two independent defects. Neither is repairable from layout data alone.

### 1. `popupCharacters` was never decoded

Android has parsed `popupCharacters` as `codes\ndisplay` since 2012 — `LIMEBaseKeyboard.java:877`, in the constructor that builds a keyboard from a character string:

```java
CharSequence labels = null;
if (characters.toString().contains("\n")) {
    String[] charactersAndLabel = characters.toString().split("\n");
    characters = charactersAndLabel[0];   // → key.codes
    labels     = charactersAndLabel[1];
}
```

The first half supplies `key.codes`; without a newline every character is its own key and supplies both. `lime_ez.xml` uses this for all six phone roots.

iOS implemented neither half:

- **Data.** `lime_ez.json` carried the *raw XML text* rather than the AAPT-processed value, so the separator arrived as a literal backslash-`n` (`"-\\n儿"`) instead of a newline. The converter copied the attribute verbatim; note that key `6` shows the tell — `\\` is only written that way for a consumer that processes escapes.
- **Parser.** `popupCharLayout(for:)` mapped every Unicode scalar to its own key with `code == scalar`. Key `1` therefore produced four keys emitting `-`, `\`, `n`, and `儿`.

### 2. Popup dispatch bypassed the input engine

`firePopupKey(_:)` routed negative codes to `onKey` but inserted every positive code directly:

```swift
if composingLength > 0 { clearComposing(force: true) }
textDocumentProxy.insertText(char)
```

So even a correctly decoded code 45 would have been committed as text rather than looked up. Android has no such branch: the mini-keyboard listener installed in `inflateMiniKeyboardContainer` forwards `primaryCode` straight to `mKeyboardActionListener.onKey(...)` for every popup key.

## Affected keys

| Phone key | Table code | Root |
|---|---:|---|
| `1` | `-` (45) | 儿 |
| `2` | `=` (61) | 母 |
| `3` | `[` (91) | 匚 |
| `4` | `]` (93) | ] |
| `5` | `'` (39) | Ｌ |
| `6` | `\` (92) | ㄏ |

`lime_ez` is the only layout using the newline form — verified across all 60 layout JSONs.

## Fix

Three changes, mirroring Android.

**Data** — `lime_ez.json` ×6 now carries the processed value (real newline), matching what AAPT hands Android.

**Parser** — `PopupCharacterLayoutPolicy.keys(from:)` in `Shared/Models/KeyLayout.swift` is the Java split transcribed:

```swift
let parts  = chars.split(separator: "\n", maxSplits: 1, omittingEmptySubsequences: false)
let labels = parts.count > 1 ? parts[1].unicodeScalars : parts[0].unicodeScalars
return zip(parts[0].unicodeScalars, labels).map { KeyDef(code: Int($0.value), label: String($1)) }
```

`labels = codes` when there is no newline reproduces Android's `labels == null` branch. `zip` truncates a mismatched pair where Android throws `StringIndexOutOfBounds`.

**Dispatch** — `firePopupKey(_:)` forwards any real code to `onKey`, matching the mini-keyboard listener. The `code == 0` branch is retained for `popup_domains`, whose keys carry their output in the label — that is Android's `keyOutputText`/`onText` path, and a bare `onKey` would drop it (`handleCharacter` guards `code > 0`).

No new `KeyDef` field. Files touched: `lime_ez.json`, `KeyLayout.swift`, `KeyboardViewController.swift`, plus tests.

## Rejected approach (PR #240)

PR #240 identified the same defect but added a `routesThroughInputEngine` flag to `KeyDef` — a per-key opt-in for behavior that has no opt-out on Android — and threaded it through `LayoutLoader` and `resolvedDomainLayout`, where nothing sets it. It also taught the Swift parser to match the literal backslash-`n` in the JSON rather than correcting the data, which would have locked the conversion error in as the iOS format. Its Xcode Cloud run failed and device verification was outstanding.

An alternative using iOS's `longPressCode` (feat#124) was considered and rejected: `longPressCode` has no counterpart in Android's `LIMEBaseKeyboard_Key` styleable, so `lime_ez.json` would have stopped mirroring `lime_ez.xml` structurally. `popupCharacters` is the attribute both platforms have.

## Verification

### Automated — passing

All five focused `xcodebuild test` methods passed on an iOS simulator at PR head `d286af4621dfc3fd51d771fb31724069d3d6b301`. Its relevant implementation and test files are byte-identical to final head `7b5026f584a68c0b771d405509bd65a5d9424e11`, whose complete tree exactly matches squash commit `9a38d34de8782bae7c617c1aba82965f9833abc1`:

- `testEasyInputPhoneRootsDecodeToOneKeyEmittingTheTableCode` — all six roots decode from the real fixture to one key with the table code; no stray separator keys
- `testOrdinaryPopupCharactersRemainOneKeyPerCharacter` — `àáâãäåæ` stays seven independent keys
- `testEncodedPopupAlternativesDecodePairwise` — `123\nＡＢＣ` decodes pairwise
- `testPopupRootDispatchEntersComposingInsteadOfCommittingText` — firing a decoded root through the production `firePopupKey` leaves `-` in the composing buffer instead of committing text
- `testET41PopupDigitsShowOnPhoneLongPressKeyLabels` — pre-existing single-char popup unchanged

### Runtime

Maintainer-verified on iPhone hardware, 2026-08-17:

- [x] iPhone: long-press `1`–`6`, roots emit their Easy Input table codes through composing and candidate lookup

Not separately exercised:

- [ ] Full and narrow iPad: Easy Input direct root keys unchanged (iPad uses direct keys, not this popup path)
- [ ] Android: Easy Input long-press smoke check (the only Android edit removes a stale comment)

## Platform impact

**iOS iPhone:** fixed. **iOS iPad:** the iPad Easy Input layouts expose `-` and `=` as direct keys and do not use this popup path; the shared parser change is covered by the ordinary-popup regression test. **Android:** no behavior change. The only Android source edit removed the stale `onLongPress` TODO in `LIMEKeyboardBaseView` — single-char popups have been handled since 2012 by injecting a synthetic `ACTION_DOWN` so the lone key is preselected and commits on release.

## Regeneration follow-up

The checked-in `lime_ez.json` values are corrected and covered by the fixture regression, but `scripts/convert_keyboard_layouts.py` still copies raw Android XML `popupCharacters` values verbatim. Running the converter therefore recreates the old literal backslash-`n` representation. Before any future iOS keyboard-layout regeneration, update the converter to emit AAPT-equivalent `codes\ndisplay` values and add a focused generator contract for all six Easy Input roots.

## Acceptance criteria

- [x] Each of the six roots resolves to exactly one popup key emitting its Easy Input table code
- [x] No separator characters appear as selectable popup keys
- [x] Popup root selection enters composing and candidate lookup instead of committing text
- [x] Ordinary one-key-per-character popups unchanged
- [ ] Direct regression for the retained `code == 0` `popup_domains` text-output route. Source inspection supports the retained behavior, but no direct regression currently exists in the repository
- [x] Device verification on all six roots
- [ ] The layout converter preserves the AAPT-equivalent values for all six roots and has a focused regeneration/anti-reversion contract
