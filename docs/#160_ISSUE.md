# Issue #160: iOS `limenumsym` (LIME+數字符號鍵盤) renders the ordinary QWERTY layout

## Status

- Issue: https://github.com/lime-ime/limeime/issues/160
- Classification: bug, usability, cross-platform parity
- State: PR #162 merged to `master` as `c56593f8e3fd76b4a800b66b12ab76b7a6b96f46`, and Xcode Cloud run 16 succeeded for the PR head. The community issue was auto-closed by the merge, but release readiness remains blocked by the iPad punctuation semantic regression described below. Pending a corrective PR, device verification, and release.
- Platform: iOS only. Android is **reporter-confirmed working** and is **not** changed by this fix.
- Public acknowledgement: reporter is a community iPhone user (see privacy-safe summary below). No private account details are recorded in this repo.

## Problem statement

A user enables the shared-catalog keyboard option `limenumsym` (description `LIME+數字符號鍵盤`). On Android the dedicated number/symbol QWERTY layout is shown. On iOS the ordinary number-row QWERTY layout is shown instead — the selected `limenumsym` layout never appears.

## Reproduction from report

1. Import a custom CIN table into LIME. The reporter used a community `.cin`, but the import is incidental because the bug reproduces for the shared `limenumsym` catalog option regardless of the active table.
2. Enable / select the keyboard option `limenumsym` — `LIME+數字符號鍵盤`.
3. Open the LIME iOS keyboard.
4. **Observed:** the ordinary number-row QWERTY keyboard is shown.
5. **Expected (matches Android):** the `lime_number_symbol` layout — QWERTY with a dedicated top number row plus semicolon, apostrophe, minus, and equals keys. Its shift page `lime_number_symbol_shift` shows `!@#$%^&*()` over uppercase QWERTY with colon, quotation-mark, and `<>?_+` keys.

## Root cause (independently verified)

**Not a database problem.** The shared keyboard catalog row is present and correct, and is the *same* row Android and iOS consume. Confirmed by querying Android's source-of-truth `LimeStudio/app/src/main/res/raw/lime.db`:

```
keyboard.code = limenumsym  →  imkb = lime_number_symbol,  imshiftkb = lime_number_symbol_shift
(desc = "LIME+數字符號鍵盤", type = phone)
```

Because the catalog is shared/ported, a missing/wrong row would break Android identically — and Android works. So the DB is not the cause.

**The cause is a missing iOS layout resource, introduced by the XML→JSON converter.** LIME iOS is a port of the Android keyboard: Android keyboard XML layouts are converted to JSON by `scripts/convert_keyboard_layouts.py` and bundled into the keyboard extension, where `LimeIME-iOS/LimeKeyboard/LayoutLoader.load(id)` loads `<id>.json`.

- **Android** ships `LimeStudio/app/src/main/res/xml/lime_number_symbol.xml` and `lime_number_symbol_shift.xml`, and `LIMEKeyboardSwitcher.java` maps those names to `R.xml.lime_number_symbol{,_shift}` (lines 494–497). Working.
- **iOS** had **no** `lime_number_symbol.json` / `lime_number_symbol_shift.json`. The converter's `SKIP_FILES` set explicitly excluded both XML files with the comment *"The lime_number_symbol\*.xml files are Dayi IM layouts — NOT the symbol keyboard."* **That comment is wrong.** Inspection of the XML shows they are ordinary QWERTY + number-row / symbol layouts with the expected letter and punctuation keys. The shift page is the symbol variant. They are neither Dayi nor the 3-page symbol keyboard. Because they were skipped, no JSON was generated, so nothing was bundled.

**Resulting iOS behavior chain:** `resolvedLayoutId(for:)` returns the catalog `imkb` = `lime_number_symbol` → `LayoutLoader.load("lime_number_symbol")` finds no bundled JSON and returns `nil` → the caller falls back to a generic English/number QWERTY layout (e.g. `LayoutLoader.load(layoutName) ?? LayoutLoader.load(englishLayout)`), which is exactly the "ordinary number-row QWERTY" the user sees.

A second, compounding gap: even a freshly converted JSON would not ship, because each layout must be individually registered in `LimeIME-iOS/LimeIME.xcodeproj/project.pbxproj`. The `Layouts` group is a `<group>`, not a folder reference, and roughly 120 layouts are listed one-by-one across four sections. Both files were absent from the project (0 references).

## Cross-platform impact

- **iOS:** affected. Any user selecting `limenumsym` gets the wrong keyboard. Independent of the active input method / imported `.cin` (the imkb resolution is catalog-driven, not table-driven).
- **Android:** not affected and not changed. It served as the parity reference.
- **Shared DB:** correct on both platforms. No DB change required or made.

## Fix

1. `scripts/convert_keyboard_layouts.py` — removed `lime_number_symbol.xml` and `lime_number_symbol_shift.xml` from `SKIP_FILES` and corrected the misidentifying comment. They now convert like every other layout, so future full regenerations keep them in sync with the Android XML.
2. `LimeIME-iOS/LimeKeyboard/Layouts/lime_number_symbol.json` and `lime_number_symbol_shift.json` — generated from the Android XML via the converter, matching the repo's UTF-8-BOM + trailing-newline convention. Each layout has five rows and is structurally faithful to the source XML.
3. `scripts/build_ipad_layouts.py` and `scripts/trim_ipad_layout.py` — added the number-symbol family to the full-size and narrow iPad generators. This produces normal and Shift variants for large iPads and the small/medium `ipad_narrow` size tier instead of relying on LayoutLoader's phone-layout fallback.
4. `LimeIME-iOS/LimeIME.xcodeproj/project.pbxproj` — registered all six JSONs in all four required sections (`PBXBuildFile`, `PBXFileReference`, the `Layouts` `PBXGroup` children, and the keyboard-extension `PBXResourcesBuildPhase`) so they are copied into the extension bundle, following the established `add_ipad_layouts_to_xcodeproj.py` pattern.

No production Swift change is needed. The existing `resolvedLayoutId → LayoutLoader.load` path already requests `lime_number_symbol`, but it lacked the resource. No Android files changed.

## Test plan / coverage added

`scripts/test_number_symbol_layout_ios.py` — a Linux-runnable regression test guarding the iOS layout/resource contract for #160 (Swift/Xcode tests cannot run on Linux):

- `test_ios_layout_json_files_exist` — both JSONs exist, parse, and carry the right `id` and non-empty rows.
- `test_ios_layout_json_matches_android_xml` — parity: each committed JSON equals a fresh conversion of its Android XML source (via `convert_keyboard_layouts.py`), so drift is caught.
- `test_ipad_layout_variants_match_generators` — full-size and narrow iPad normal/Shift layouts exist and exactly match fresh output from the iPad generators.
- `test_layouts_registered_in_xcodeproj` — all six phone/iPad JSONs have a `PBXFileReference` and membership in the LimeKeyboard target's Resources build phase, i.e. they will actually be bundled.

RED (before the phone fix): all three original tests failed because the base JSONs were absent and unregistered. A follow-up iPad contract test then failed in two places because the four `ipad` / `ipad_narrow` variants were absent and unregistered. GREEN: all four contract tests pass for all six resources.

## Verification

### Linux (done)

- `python3 scripts/test_number_symbol_layout_ios.py -v` → 4 passed. The original phone RED was 3 failed, and the follow-up iPad RED was 2 failed.
- Full JSON/XML parity sweep over all converter-produced layouts: 43 checked, 0 mismatches, 0 missing.
- The iPad builder generated 22 full-size layouts including both number-symbol variants. The trimmer generated 40 narrow layouts including both number-symbol variants without changing existing committed layouts.
- All six new JSONs validate as JSON. Each file is referenced four times in the Xcode project (build file, file reference, group child, and LimeKeyboard resources).
- Existing suite `scripts/test_build_emoji_db.py` → 6 passed (no regression). Android tree untouched.

### iOS / Xcode (residual — cannot run on Linux)

Xcode Cloud run 16 succeeded for exact PR head `f7730083ae355eb3f5aedb78c5b307d06486ba58` (required test and archive actions both succeeded). This proves the project builds and archives, but it does not validate the generated keyboard's punctuation semantics.

An independent post-generation semantic audit found that the generator-relative test is insufficient: it compares committed iPad layouts to output from the same generator. The merged generated resources therefore reproduce a generator defect instead of detecting it:

- Phone Shift preserves `_` (code `95`) and `+` (code `43`).
- Full iPad Shift contains `…` (code `8230`) and `+`, losing `_`.
- Narrow iPad Shift retains `…` and loses both `_` and `+`.
- Normal narrow iPad drops `=` (code `61`).

Before release, add source-independent semantic assertions for these required punctuation keys, correct the iPad generator/trimmer behavior, regenerate the affected layouts, and rerun the focused checks plus Xcode/device verification.

1. Build the keyboard extension and confirm all six phone/iPad JSONs are copied into the bundle.
2. On iPhone, select `limenumsym` and confirm the `lime_number_symbol` layout renders the number row plus semicolon, apostrophe, minus, and equals keys. Confirm Shift shows `lime_number_symbol_shift` with the expected symbols, uppercase letters, and punctuation.
3. On a large iPad, confirm resolution uses `lime_number_symbol_ipad` and `lime_number_symbol_ipad_shift`.
4. On a small/medium iPad size tier, confirm resolution uses `lime_number_symbol_ipad_narrow` and `lime_number_symbol_ipad_narrow_shift`.
5. Confirm the period and greater-than keys open their expected punctuation popups.
6. Regression: confirm other keyboards (e.g. `limenum` → `lime_number`) still render correctly.

### Android

No change and no retest indicated. Android is confirmed unaffected and used as the parity reference.

## Privacy-safe reporter summary

A community iPhone user reported that selecting the `LIME+數字符號鍵盤` (`limenumsym`) option shows the normal QWERTY keyboard instead of the number/symbol layout, while the same option works on Android. The report arrived alongside a custom `.cin` import, which is incidental to the bug. No personal account identifiers, email addresses, `.cin` contents, screenshots, or other private details are stored in this repository.

## Fix status

- [x] Root cause independently verified (DB query + XML inspection + iOS loader trace).
- [x] RED regression test written and observed failing.
- [x] Converter fixed, phone/iPad/iPad-narrow JSONs generated, and Xcode project updated.
- [x] GREEN: focused test + parity/inclusion validations pass on Linux.
- [x] Xcode Cloud build/test/archive for PR head `f7730083ae355eb3f5aedb78c5b307d06486ba58`.
- [ ] Correct the merged iPad punctuation semantic regression with source-independent RED assertions and a focused follow-up PR.
- [ ] Simulator/device verification of phone, full iPad, and narrow iPad normal/Shift layouts.
- [ ] Release + maintainer close-out. Remove `fix#160 iOS` from `docs/BACKLOG.md` once shipped.
