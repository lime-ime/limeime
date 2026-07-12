# Issue #143: Replace Cangjie semicolon preference with selectable layouts

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/143
- Classification: `enhancement` + `Usability`
- Reporter / creator: `limeimetw`
- Created: 2026-07-02
- Current live state checked 2026-07-09: closed as completed with maintainer/project-account comment https://github.com/lime-ime/limeime/issues/143#issuecomment-4916962552.
- Backlog entry: removed from `docs/BACKLOG.md` after v6.1.28 delivery because the implementation is complete and the issue is closed.
- Parent context: issue #140 follow-up. The old cj4-specific semicolon preference shipped in v6.1.27, but the next direction is explicit selectable Cangjie semicolon layouts so custom/self-built tables can choose the semicolon-capable keyboard directly.

## Implementation result

GitHub Release/APK v6.1.28 contains the Android implementation for selectable Cangjie semicolon layouts. iOS source implementation is also present on `master` and will be delivered through the normal TestFlight/App Store path.

Retained closure comment: https://github.com/lime-ime/limeime/issues/143#issuecomment-4916962552.

## Request

Replace the old Cangjie-family semicolon behavior preference with selectable keyboard layouts:

- `cj_semi`: standard Cangjie keyboard with semicolon behavior.
- `cj_num_semi`: Cangjie numeric keyboard with semicolon behavior.

The existing non-semicolon Cangjie choices must remain unchanged:

- `cj` / standard Cangjie layout keeps today's no-semicolon behavior.
- `cjnum` / Cangjie numeric layout keeps today's no-semicolon behavior.

## Required behavior

The new semicolon layouts must behave exactly like the current Cangjie keyboard when the old `cj4_semicolon_key` preference is on.

### Phone / Android / iPhone

- Standard semicolon layout is based on `lime_cj`.
- Numeric semicolon layout is based on `lime_cj_number`.
- Shifted variants are required:
  - `lime_cj_semi_shift`
  - `lime_cj_number_semi_shift`
- The home/asdf row gains ASCII `;` at the right end.
- The row must match the old pref-on transform:
  - 9 existing home-row keys plus `;`.
  - 10 equal-width keys.
  - no leftover leading/trailing spacer from the no-semicolon row.
  - `;` tap output, `'` long-press/slide output.
  - label convention stays old pref-on convention: `'` on top, `;` on bottom.

### iPad / iPad narrow

iPad variants are required for both standard and numeric semicolon layouts:

- `lime_cj_semi_ipad`
- `lime_cj_semi_ipad_shift`
- `lime_cj_semi_ipad_narrow`
- `lime_cj_semi_ipad_narrow_shift`
- `lime_cj_number_semi_ipad`
- `lime_cj_number_semi_ipad_shift`
- `lime_cj_number_semi_ipad_narrow`
- `lime_cj_number_semi_ipad_narrow_shift`

These variants must match the old pref-on iPad behavior:

- Do not append a new key to iPad rows.
- Rewrite the existing `；|：` dual key to ASCII semicolon behavior.
- Tap output is `;`.
- Long-press/slide output is `'`.
- Width, row placement, and surrounding keys stay identical to the current pref-on transform.

## Keyboard table requirement

Add keyboard-table entries for the new selectable choices, following the existing `computernum` seed pattern.

Expected rows:

- `keyboard.code = 'cj_semi'`
  - standard Cangjie semicolon keyboard.
  - `imkb` points to the standard semicolon layout.
  - `imshiftkb` points to the shifted standard semicolon layout.
  - `extendedkb` / `extendedshiftkb` should point to the numeric semicolon layouts, mirroring how `cj` points to `lime_cj_number`.
- `keyboard.code = 'cj_num_semi'`
  - numeric Cangjie semicolon keyboard.
  - `imkb` points to the numeric semicolon layout.
  - `imshiftkb` points to the shifted numeric semicolon layout.

The exact runtime layout IDs can be aliases to existing resources, but they must resolve as stable semicolon layout IDs in all paths that reload by layout ID, especially iOS trait / iPad-size changes.

## Database init / repair paths

Both platforms already seed `computernum` from the current database check path:

- Android: `LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java`
  - `ensureCurrentDatabase()`
  - `ensureComputerNumKeyboard(...)`
- iOS: `LimeIME-iOS/Shared/Database/LimeDB.swift`
  - `ensureCurrentDatabase()`
  - `ensureComputerNumKeyboard(...)`

#143 should add the Cangjie semicolon keyboard rows from the same current-database check path so existing installs receive the rows without requiring a full bundled DB replacement.

Also check any bundled-DB repair/import path that refreshes keyboard rows so the new keyboard codes are not lost after restore/reset.

## Current implementation to replace

### Android

Current old preference path:

- `LimeStudio/app/src/main/res/layout/fragment_im_detail.xml`
  - `section_cj4_semicolon`
  - `switch_cj4_semicolon`
- `LimeStudio/app/src/main/res/values/strings_settings.xml`
  - `cj4_semicolon_section_title`
  - `cj4_semicolon_switch_label`
  - `cj4_semicolon_summary`
- `LimeStudio/app/src/main/java/org/limeime/ui/view/ImDetailFragment.java`
  - shows the switch only for `cj4`.
  - stores `cj4_semicolon_key`.
- `LimeStudio/app/src/main/java/org/limeime/LIMEKeyboardSwitcher.java`
  - reads `cj4_semicolon_key`.
  - calls `LIMEKeyboard.addCj4SemicolonKey()` only for cj4 + Cangjie XML.
- `LimeStudio/app/src/main/java/org/limeime/LIMEService.java`
  - reads `cj4_semicolon_key`.
  - forces `SearchSrv.setSymbolMapping(true)` for cj4 when enabled.
- `LimeStudio/app/src/main/java/org/limeime/keyboard/LIMEKeyboard.java`
  - `addCj4SemicolonKey()` contains the existing phone/Android row transform. Reuse or rename this behavior; do not duplicate geometry.

### iOS

Current old preference path:

- `LimeIME-iOS/LimeSettings/Views/IMDetailView.swift`
  - `@AppStorage("cj4_semicolon_key")`
  - cj4-only settings toggle.
- `LimeIME-iOS/LimeKeyboard/LayoutLoader.swift`
  - `applyingCj4Semicolon(to:)` contains the existing iPhone/iPad transform.
- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`
  - checks `activeIM == "cj4"` and `cj4_semicolon_key`.
  - applies the transform.
  - forces symbol mapping for cj4 when enabled.

## Implementation direction

Keep the implementation boring:

1. Seed `cj_semi` and `cj_num_semi` keyboard rows in the same current-database check path as `computernum`.
2. Register/list the new keyboard rows so both Android and iOS keyboard pickers can select them.
3. Resolve the new semicolon layout IDs to the existing Cangjie resources plus the existing semicolon transform.
4. Preserve a semicolon layout ID after aliasing, so reload paths do not silently become plain `lime_cj` / `lime_cj_number`.
5. Drive semicolon behavior and symbol mapping from selected keyboard layout/code, not from `cj4_semicolon_key`.
6. Hide/remove the old cj4-only semicolon setting from settings UI.
7. Keep `cj` and `cjnum` unchanged.

No new static layout files are required if resolver aliases prove all variants load correctly. If generated JSON/XML files are preferred later, generate them from the same source transform rather than hand-maintaining duplicate geometry.

## Migration / fallback

Old installs may still have `cj4_semicolon_key = true`.

Recommended minimal migration:

- If the old pref is true and cj4 currently uses `cj`, move cj4 to `cj_semi`.
- If the old pref is true and cj4 currently uses `cjnum`, move cj4 to `cj_num_semi`.
- Then ignore or clear the old pref.
- Do not change normal `cj` / `cjnum` behavior when the old pref is false.

Open decision:

- Whether to migrate only cj4, or every Cangjie-family/custom IM currently using `cj` / `cjnum`. Since the old pref was cj4-only, the safer default is cj4-only migration.

## Verification plan

### Android automated

- Add/adjust `LimeDBTest` coverage:
  - `cj_semi` row is seeded.
  - `cj_num_semi` row is seeded.
  - rows point to semicolon runtime layout IDs and shifted IDs.
- Add/adjust keyboard-switcher coverage:
  - semicolon layout IDs resolve to existing `lime_cj` / `lime_cj_number` XML resources.
  - selected semicolon layouts request the existing semicolon key transform.
  - plain `cj` / `cjnum` do not request the transform.
- Add/adjust service policy coverage:
  - selected `cj_semi` / `cj_num_semi` forces symbol mapping.
  - old `cj4_semicolon_key` is not required for symbol mapping.

### iOS automated

- Add/adjust `LimeDBTest` coverage:
  - `cj_semi` and `cj_num_semi` rows are seeded from current DB init.
- Add/adjust `KeyboardViewControllerTest` / `LayoutLoader` coverage:
  - `lime_cj_semi` matches current `lime_cj` + old pref-on transform.
  - `lime_cj_semi_shift` matches shifted pref-on transform.
  - `lime_cj_semi_ipad` and `lime_cj_semi_ipad_narrow` match old iPad pref-on transform.
  - numeric equivalents match `lime_cj_number` pref-on behavior.
  - returned layout IDs remain semicolon IDs after aliasing.
- Add/adjust search/runtime coverage:
  - selected semicolon keyboard forces symbol mapping for `;`.
  - plain Cangjie layouts keep existing symbol-mapping behavior.

### Manual

- Android:
  - Select `cj_semi`: standard Cangjie shows `;` on the home row.
  - Select `cj_num_semi`: numeric Cangjie shows `;` on the home row.
  - Select `cj` / `cjnum`: old no-semicolon layouts remain unchanged.
- iPhone:
  - Same standard/numeric checks as Android.
- iPad / iPad narrow:
  - Select standard/numeric semicolon layouts.
  - Confirm `；|：` becomes `;` / `'` behavior without row geometry changes.
- Custom/self-built table:
  - Assign `cj_semi` or `cj_num_semi`.
  - Confirm `;` can compose as a root when the table has semicolon in `imkeys`.

## Public response note

Public completion note was posted in https://github.com/lime-ime/limeime/issues/143#issuecomment-4916962552. Do not post duplicate completion comments unless a future build supersedes v6.1.28 or a reporter asks about the delivered layouts.
