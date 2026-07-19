# Issue #169 — Integrated phone portrait keyboard model (no width gate)

Status: Android-delivered in GitHub Release v6.1.34 after PR #171 (`9667c82db800a899b12e13b9211c77dbda7c26fb`).
The community issue was reopened for reporter confirmation after maintainer close-on-merge.
GitHub Release v6.1.34 targets `d45aa437b6356bfef5079ceebbfcd8d295a300b8` and contains the
Android fix. Its verified GitHub testing-track APK is `LIMEHD2026-6.1.34.apk` (7,112,576 bytes,
SHA-256 `d16d7fde5d634d655148396c657e8ffab5f3868f434f705f9568855da4e3e84f`). The retained
retest request is https://github.com/lime-ime/limeime/issues/169#issuecomment-5016727812.
The iOS changes are source-fixed and simulator-validated, but verified TestFlight/App Store
delivery remains separate.
Last updated: 2026-07-19

## Problem

v6.1.33 exposed `split_keyboard_mode` and `one_hand_mode` as two independent phone settings and
made phone split landscape-only. That broke the legacy portrait-split contract and allowed
contradictory stored choices (split + one-hand both "on"). An earlier redesign also gated the
one-hand option behind a screen-width threshold (`ReachGeometry.oneHandAvailable`, ≈5.5"+), so the
control silently disappeared on narrower phones.

## Authoritative requirement (supersedes the earlier ≈5.5"+ gate)

- **Remove ALL phone-width / physical-size gating** for the integrated phone preference and
  one-hand application.
- The integrated phone preferences apply to **every Android phone and every iPhone**, regardless
  of screen width. The phone controls are always shown on phone-class devices.
- Android devices classified as tablets (`smallestScreenWidthDp >= 600`, ≈7"+) use the existing
  tablet mode only — never the phone controls.
- Every iPad uses the existing iPad split / numpad model only — never the phone controls.
- `ReachGeometry.oneHandWidth` keeps clamping to the available width. The old
  `ReachGeometry.oneHandAvailable` gate is removed rather than retained as dormant policy.

## Canonical shared keys/values (Android ↔ iOS identical)

| Key | Type / values | Scope |
|---|---|---|
| `phone_portrait_keyboard_mode` | int: 0 standard, 1 split, 2 one-hand left, 3 one-hand right | every phone |
| `phone_landscape_split` | bool | every phone |
| `split_keyboard_mode` | int: 0 off, 1 always, 2 landscape-only | Android tablet ≥600dp / iPad only |
| `numpad_anchor` | int: 0 fit, 1 left, 2 right, 3 center | Android tablet ≥600dp / iPad only |

`split_keyboard_mode` / `numpad_anchor` are the tablet/iPad profile keys and are **never**
rewritten by phone changes. `one_hand_mode` / legacy `split_keyboard_mode` remain migration input
only.

## Shared policy

`PhoneKeyboardModePolicy` is implemented twice with identical semantics so phone behaviour and key
values align across platforms:

- Android: `LimeStudio/app/src/main/java/org/limeime/keyboard/PhoneKeyboardModePolicy.java`
- iOS: `LimeIME-iOS/Shared/Models/PhoneKeyboardModePolicy.swift` (pure logic, no UIKit; added to
  the LimeIME app, LimeKeyboard extension, and LimeTests targets)

Functions: `migratePortraitMode`, `migrateLandscapeSplit`, `splitActive`, `oneHandAnchor`,
`phoneControlsApply(isTablet/isPad)`.

Migration precedence (both platforms): legacy one-hand left/right wins → else legacy split
`always` (Android only) → else standard. `legacyPhoneSplitSupported` is **true** on Android
(legacy portrait split existed) and **false** on iOS (iPhone split was never shipped, so migration
preserves one-hand but never invents a legacy iPhone split).

## Delivery

### Android
- `PhoneKeyboardModePolicy.java` + one-time migration accessors in `LIMEPreferenceManager`
  (`getPhonePortraitKeyboardMode` / `getPhoneLandscapeSplit`, writing canonical keys on first read).
- Rendering: `LIMEKeyboardSwitcher.getKeyboard` resolves phone vs tablet; phone split + one-hand
  anchor come from the policy. `LIMEBaseKeyboard` gained a `phoneSplitForced` constructor param;
  tablets keep the legacy value-based split. **The `oneHandAvailable` helper and render gate were removed.**
- In-keyboard menu (`LIMEService.handleOptions`): 直向鍵盤模式 (4-seg, 分離 hidden for numpad) in
  phone portrait; 橫向分離鍵盤 (binary) in phone landscape; tablet split / numpad anchor unchanged.
  **No width gate.** Chevron restore sets `phone_portrait_keyboard_mode = 0`.
- Settings: `preference.xml` replaces the one-hand ListPreference with a 直向鍵盤模式 ListPreference
  + a 橫向分離鍵盤 switch; `LIMEPreference` shows phone controls on every phone (no width gate) and
  gates `split_keyboard_mode` / `numpad_anchor` to tablets. Strings/arrays in `strings_settings.xml`.
- Backup: `PreferenceBackupAdapter` adds `phone_portrait_keyboard_mode` + `phone_landscape_split`.
- Tests: `PhoneKeyboardModePolicyTest` (10 unit tests incl. narrow-phone no-gate),
  `PhoneKeyboardPreferenceMigrationTest` (instrumentation), `ReachGeometryTest` (6).

### iOS
- `PhoneKeyboardModePolicy.swift` + cold accessors in `LIMEPreferenceManager.swift`.
- Hot store: `seededHotInt(_:cold:migrate:)` / `seededHotBool(_:cold:migrate:)` seed + migrate the
  two prefs in `loadSettings()`.
- Rendering (`KeyboardViewController` layout): iPhone split from `PhoneKeyboardModePolicy.splitActive`;
  one-hand anchor from `oneHandAnchor`. **`oneHandAvailable` gate removed** — `oneHandWidth` still
  clamps. Chevron restore resets the portrait mode to standard. Phone split key width follows
  Android's reserved-column model (2 portrait / 3 landscape); the existing iPad 66 mm reach cap
  is unchanged.
- Globe menu: 直向鍵盤模式 (portrait) / 橫向分離鍵盤 (landscape) on every iPhone; iPad split / numpad
  anchor unchanged. Segmented rows render horizontally on iPhone landscape so the short keyboard
  can show the complete menu; portrait and iPad retain stacked rows.
- Dual-label split keys choose top/bottom versus left/right from their actual rendered half width,
  so tall narrow phone keys keep the Latin hint above the Chinese label.
- Transport: `PrefInbox` + `RelayPrefState` carry `phonePortraitMode` / `phoneLandscapeSplit`; the
  FA-off text relay carries `pp=` / `pls=` and `RelayPrefSync.apply` writes them cold so a globe-menu
  change reaches the settings app with Full Access off.
- Settings (`PreferencesTabView`): 直向鍵盤模式 Picker + 橫向分離鍵盤 Toggle gated `!= .pad` only
  (no width gate); cold migration in `migrateRemovedPreferences`.
- Backup/restore: `PreferenceBackupAdapter` specs, hot→cold flush (`TableSyncEngine`), restore push
  (`DBManagerView.pushRestoredPrefsToInbox`).
- Tests: `ReachGeometryTests` (policy contract + narrow-phone no-gate + `phoneControlsApply`),
  `RelayPayloadTest` + `SyncContractTest` (phone-pref round-trips + `RelayPrefSync.apply`).

## Verification status

- Android full gate passed on the Pixel 9 Pro AVD:
  - `:app:testDebugUnitTest`
  - `:app:lintDebug`
  - `:app:connectedDebugAndroidTest` (1,207 executed test cases, 8 skipped, 0 failed)
- Focused phone migration + backup/restore instrumentation: 14 tests passed, including legacy
  Android/iOS migration, startup-cache invalidation, and tablet profile isolation.
- Phone and simulated `smallestScreenWidthDp >= 600` settings visibility were verified from live
  emulator UI dumps.
- iOS focused XCTest passed on the LimeTest-iPhone16 simulator: 12 `ReachGeometryTests` plus the
  landscape-menu and split dual-label regression tests (14 total, 0 failed). Device layout was
  manually verified by the user; automated visual verification was intentionally skipped.

## Non-goals

No emoji-panel width change, no per-orientation memory, no new iPad email/URL layouts. Tablet split
reach-cap and numpad anchoring (Features A/C) are unchanged by this issue.
