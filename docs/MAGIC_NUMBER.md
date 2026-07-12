# Magic Number And Theme Literal Audit

## Goal

Prevent repeated UI regressions caused by scattered hardcoded colors and layout metrics, especially:

- light text/icons on light backgrounds
- dark text/icons on dark backgrounds
- keyboard/candidate controls that ignore the selected keyboard theme
- settings screens that use one-off spacing, corner radius, icon size, or overlay values

The target is not "zero literals everywhere". Keyboard themes, vector geometry, and shared layout metrics need literal values. The target is: unavoidable literals live in one source of truth, and new one-off UI literals are gated.

## Current Guard

The repo now has a scanner:

```sh
python3 scripts/check_ui_theme_literals.py --limit 0
```

Current baseline result:

```text
high  color: 39
info  color: 73
info metric: 133
medium  color: 5
medium metric: 597
```

Meaning:

- `high color`: likely non-theme-aware UI color outside an approved central place.
- `medium color`: raw hex or less direct color literal outside an approved central place.
- `medium metric`: hardcoded layout metric outside an approved central place.
- `info`: literal appears in a central-ish place such as `LayoutMetrics.swift`, Android `res/values/*`, or keyboard theme resources.

The baseline file is `scripts/ui_theme_literal_baseline.json`. The intended CI check is:

```sh
python3 scripts/check_ui_theme_literals.py --limit 0
```

For debt reduction work, inspect everything without baseline filtering:

```sh
python3 scripts/check_ui_theme_literals.py --no-baseline --limit 200
```

After centralizing a group of literals, update the baseline:

```sh
python3 scripts/check_ui_theme_literals.py --write-baseline scripts/ui_theme_literal_baseline.json --limit 0
```

## Scan Scope

The guard scans production UI source under:

- `LimeIME-iOS`
- `LimeStudio/app/src/main`

It skips generated/build/test/vendor paths. A broader manual grep was also run over iOS and Android source to identify examples that may need human classification.

## Highest Risk Findings

### iOS Settings Colors

Original examples, now migrated to `SettingsTheme` roles:

- `LimeIME-iOS/LimeSettings/Views/AddRecordView.swift`: `.foregroundColor(.red)`
- `LimeIME-iOS/LimeSettings/Views/DBManagerView.swift`: `.foregroundColor(.red)`
- `LimeIME-iOS/LimeSettings/Views/IMListView.swift`: `.foregroundStyle(.white)` with `.background(Color.blue, in: Circle())`
- `LimeIME-iOS/LimeSettings/Views/SetupTabView.swift`: `.fill(Color.green)`, `.foregroundColor(.green/.orange/.red)`
- `LimeIME-iOS/LimeSettings/Controllers/IMStoreView.swift`: `.foregroundColor(.green/.red)`

Risk:

These usually look fine in the current default theme, but they are not expressed as semantic roles. They can fail in dark mode, high contrast, future iOS appearance changes, or local overlay states.

Centralize into:

- `SettingsTheme.swift`
  - `destructive`
  - `success`
  - `warning`
  - `info`
  - `floatingActionForeground`
  - `floatingActionBackground`
  - `overlayScrim`
  - `overlayCardBackground`
  - `progressAccent`

### iOS Settings Layout Metrics

Original examples, now migrated to `SettingsMetrics` roles:

- repeated `maxWidth: 560`
- repeated `.padding(24)`
- repeated `.cornerRadius(12)`
- fixed widths such as `64`, `80`, `180`
- fixed icon sizes such as `44`, `48`, `60`, `80`

Risk:

These make pages drift from each other and make iPad/large text tuning harder.

Centralize into:

- `SettingsMetrics.swift`
  - `contentMaxWidth`
  - `sectionHorizontalPadding`
  - `modalPadding`
  - `cardCornerRadius`
  - `rowVerticalPadding`
  - `iconSizeSmall/Medium/Large`
  - `buttonWidthSmall/Medium/Large`

### iOS Keyboard Shadows And Raw UIColors

Original examples, now routed through `LayoutMetrics.Shadow.color`:

- `LimeIME-iOS/LimeKeyboard/PopupKeyboardView.swift`: `UIColor.black.cgColor`
- `LimeIME-iOS/LimeKeyboard/KeyboardView.swift`: `UIColor.black.cgColor`
- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`: `UIColor.black.cgColor`
- `LimeIME-iOS/LimeKeyboard/KeyboardView.swift`: literal keyboard theme color conversion

Risk:

Keyboard shadows are probably visually acceptable because shadows are intentionally dark, but the values are copied in several places. Theme palette literals are valid only if they stay inside the keyboard theme source.

Centralize into:

- existing `LayoutMetrics.swift`
- existing keyboard theme palette code
- a small keyboard shadow role such as `LayoutMetrics.Shadow.color`, `opacity`, `radius`, and `offset`

### Android Candidate Emoji Icon

Example:

- `LimeStudio/app/src/main/res/drawable/ic_candidate_emoji_face.xml`: `#FF000000`

Risk:

This is exactly the class of bug we have seen before: an icon stays black/white while the candidate bar or keyboard theme changes. It should not own its final color.

Centralize by making the vector tintable:

- use a tint from the caller, or
- use a theme/resource role such as `@color/candidate_icon_tint`, or
- use `?attr/colorControlNormal` / `?attr/colorOnSurface` if the Material theme path is appropriate.

### Android Layout XML Metrics

Top noisy files from the guard:

- `LimeStudio/app/src/main/res/layout/fragment_im_detail.xml`
- `LimeStudio/app/src/main/res/layout/fragment_setup.xml`
- `LimeStudio/app/src/main/res/layout/kbsetting.xml`
- `LimeStudio/app/src/main/res/layout/fragment_db_manager.xml`
- `LimeStudio/app/src/main/res/layout/fragment_manage_im.xml`
- `LimeStudio/app/src/main/res/layout/fragment_manage_related.xml`
- `LimeStudio/app/src/main/res/layout/sheet_manage_im_add.xml`
- `LimeStudio/app/src/main/res/layout/sheet_manage_im_edit.xml`
- `LimeStudio/app/src/main/res/layout/sheet_manage_related_add.xml`
- `LimeStudio/app/src/main/res/layout/sheet_manage_related_edit.xml`

Risk:

Spacing, button sizes, and sheet padding are copy-pasted. This does not directly cause light-on-light bugs, but it causes layout drift and makes future visual fixes harder.

Centralize into:

- `LimeStudio/app/src/main/res/values/dimens.xml`
- `LimeStudio/app/src/main/res/values/styles.xml`

Suggested roles:

- `settings_content_padding_horizontal`
- `settings_content_padding_vertical`
- `settings_card_padding`
- `settings_card_radius`
- `settings_row_gap`
- `settings_icon_size`
- `settings_bottom_sheet_padding`
- `settings_counter_button_size`
- `candidate_bar_height`
- `candidate_item_padding_horizontal`

### Android Candidate View Code Metrics And Colors

Top noisy files:

- `LimeStudio/app/src/main/java/org/limeime/candidate/CandidateInInputViewContainer.java`
- `LimeStudio/app/src/main/java/org/limeime/candidate/CandidateView.java`
- `LimeStudio/app/src/main/java/org/limeime/candidate/CandidateViewContainer.java`
- `LimeStudio/app/src/main/java/org/limeime/LIMEService.java`

Risk:

Programmatic candidate UI is easy to miss in XML/resource-only checks. This is where theme-aware behavior must be explicit.

Centralize into:

- Android keyboard/candidate theme helper
- Android resource roles in `colors.xml` and `dimens.xml`
- one candidate layout/metric model used by normal composing, emoji search, and expanded candidate views

## Single Source Of Truth Proposal

### iOS

Keep keyboard-specific values in keyboard-specific code:

- `LimeIME-iOS/LimeKeyboard/LayoutMetrics.swift`
- `LimeIME-iOS/LimeKeyboard/KeyboardView.swift` theme palette
- candidate bar constants in the candidate bar implementation, or move them into `LayoutMetrics.swift` when shared

Add settings-specific sources:

- `LimeIME-iOS/LimeSettings/SettingsTheme.swift`
- `LimeIME-iOS/LimeSettings/SettingsMetrics.swift`

Rules:

- Settings views should not directly use `.red`, `.green`, `.orange`, `.white`, `.black`, `.blue`, or raw `Color.black.opacity(...)`.
- Settings views should not directly repeat modal/card/page constants.
- Keyboard views may use theme colors only through keyboard palette/metrics roles.
- Shadows and transparent touch traps should live in metrics/theme roles, not inline.

### Android

Use Android resources for static roles:

- `LimeStudio/app/src/main/res/values/colors.xml`
- `LimeStudio/app/src/main/res/values/dimens.xml`
- `LimeStudio/app/src/main/res/values/styles.xml`

Use Java/Kotlin helpers for dynamic keyboard state:

- keyboard theme palette
- candidate theme/metrics helper
- emoji search candidate state should reuse normal candidate bar metrics

Rules:

- XML layout files should use `@dimen/...` for repeated spacing and sizes.
- XML drawables should avoid hardcoded final black/white icon colors unless the drawable is a central theme asset.
- Java candidate UI should resolve colors/metrics from resources or the candidate theme helper.

## What The Guard Can And Cannot Prove

The guard can catch:

- new hardcoded foreground/background/icon colors
- new raw hex colors outside central files
- new hardcoded UI spacing/sizing values outside central files
- regressions above the current baseline

The guard cannot fully prove:

- visual contrast after blur/transparency
- whether a color is acceptable on every keyboard theme
- whether an icon tint is correct after runtime state changes
- whether iPad layout is visually balanced

For those, keep visual verification for high-risk surfaces:

- iOS and Android candidate bar collapsed/expanded
- emoji search panel in English and Chinese mode
- backup/restore/progress overlays in light and dark mode
- keyboard themes with colored candidate bars

## TODO

1. Done: add `SettingsTheme.swift` and migrate direct Settings colors.
2. Done: add `SettingsMetrics.swift` and migrate repeated Settings layout metrics.
3. Done: move `ic_candidate_emoji_face.xml` hardcoded black into `@color/candidate_emoji_icon_default`.
4. Move Android settings layout constants into `res/values/dimens.xml` and shared styles.
5. Move Android candidate programmatic colors/metrics into one candidate theme/metrics helper.
6. Add the guard command to CI or the release checklist:

```sh
python3 scripts/check_ui_theme_literals.py --limit 0
```

7. After each centralization pass, regenerate the baseline so new debt remains gated.

## iOS Pass Verification

- `python3 scripts/check_ui_theme_literals.py --limit 0`
- `xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'id=58BCFA46-0EE8-4631-B2A0-6331EF124A75' -derivedDataPath .Codex/DerivedData-ios-theme-literals -only-testing:LimeTests/KeyboardViewControllerTest/testSettingsAndKeyboardThemeLiteralsUseCentralRoles test`
- Simulator smoke screenshots captured in `.Codex/txt/ios_theme_literal_light.png` and `.Codex/txt/ios_theme_literal_dark.png`.

## Android Pass Verification

- `python3 scripts/check_ui_theme_literals.py --limit 0`
- `python3 scripts/check_ui_theme_literals.py --no-baseline --limit 260 | rg "ic_candidate_emoji_face|HIGH   color"` now reports only the vector dimensions for `ic_candidate_emoji_face.xml`, not hardcoded icon color.
