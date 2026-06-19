# Issue #121: iOS first cloud-installed IM switch can desync layout and input mode

## Current status

- GitHub issue: https://github.com/lime-ime/limeime/issues/121
- Reporter/source: `limeimetw` maintainer-created tracking issue
- Classification: `bug`, `Type-Defect`, `Usability`
- Current state: closed by maintainer after the source fix landed on `master` in merge commit `e3aef89cca52b08fd48d68105dce2fe0042f0f19` via PR #122
- Public acknowledgement: none needed because this is maintainer-created internal tracking

## Problem statement

On iOS, after an input method is first installed from the cloud/downloadable table source, the first switch to that newly installed IM can show the Chinese IM keyboard layout while the keyboard runtime is actually in English input mode.

The visible layout and runtime mode should agree on first activation:

- Chinese mode should show and use the selected IM layout.
- English mode should show and use the English layout.

This is separate from #119. #119 tracks text-import `.lime` / `.cin` default-keyboard assignment. #121 tracks the iOS cloud/download-source install path and the first keyboard-extension activation after Settings registers the new IM.

## Source evidence inspected

### Cloud/download IM install path

- `LimeIME-iOS/LimeSettings/Controllers/IMStoreView.swift`
- `LimeIME-iOS/Shared/Database/DBServer.swift`
- `LimeIME-iOS/Shared/Database/LimeDB.swift`
- `LimeIME-iOS/Shared/Preferences/LIMEPreferenceManager.swift`
- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`

`IMStoreView`'s private `importDownloaded(...)` helper imports the downloaded database, calls `server.registerIM(imName:tableName:label:keyboardId:)`, then calls `LIMEPreferenceManager.shared.syncIMActivatedState(dbServer: server)` so Settings records enabled IM rows for the keyboard extension.

`LimeDB.registerIM(...)` returns immediately if any `im` rows already exist for the IM code. For cloud-installed IMs this preserves rows merged from the downloaded DB instead of synthesizing a fallback registration row.

`LIMEPreferenceManager.syncIMActivatedState(...)` rebuilds `keyboard_state` from `getAllImConfigs()` entries whose `enabled` flag is true. Therefore the cloud-install path only makes the new IM part of the runtime activated list if the imported/registered `im` row is enabled.

`DBServer.prepareKeyboardRuntimeDatabase(...)` builds the keyboard runtime context by reading `getAllImConfigs()`, applying `keyboard_state` from the shared app-group defaults when present, and choosing an initial runtime table from `activated.first?.tableNick`, then the first enabled IM, then a `"phonetic"` fallback.

### Keyboard runtime startup path

`KeyboardViewController` starts with in-memory defaults before the database setup callback completes:

- `activeIM` defaults to `"phonetic"`.
- `activatedIMs` starts empty.
- `mEnglishOnly` starts false, then `initOnStartInput()` maps the host field type and remembered language preference to the runtime mode.

`initOnStartInput()` chooses the visible layout from the host field type, `mEnglishOnly`, `activatedIMs`, and `activeIM`:

- Phone fields load `phone_number`.
- Number, decimal, and ASCII-capable number fields load `symbols1`.
- Other English-only paths, such as remembered English mode or email-style fields, load `lime_english` / `lime_english_number`.
- Chinese mode with activated IMs loads `resolvedLayoutId(for: activeIM)`.

`setupDatabase(...)` asynchronously assigns `searchServer`, `activatedIMs`, and either the saved `keyboard_list` IM or the resolved initial IM. If a saved IM is found, `mEnglishOnly` is false, the saved IM layout can be loaded, and that layout differs from the current one, it refreshes the visible layout to that IM's layout. The source does not show a single final layout reconciliation helper that is applied after the async runtime context is restored across all host-field and language-mode cases.

## Likely root cause

The cloud install path correctly registers/imports the new IM and syncs activated state, but the keyboard extension can have a first-start ordering gap between:

1. Settings writing the newly installed IM / activated-state defaults, and
2. the keyboard extension asynchronously rebuilding `activatedIMs`, `activeIM`, and the visible layout.

`setupDatabase(...)` gates its saved-IM layout refresh on `!mEnglishOnly`, so this triage should not assume that callback directly installs a Chinese layout while English mode is true. The safer suspected mechanism is a first-start sequencing gap among `initOnStartInput()`, the async database setup callback, field-type adaptation, and later language/field-change hooks, where the final visible layout is not centrally derived from the final field type plus `mEnglishOnly`/active-IM state. That can leave the keyboard showing a Chinese IM layout even when the runtime input mode is English.

This is a source-backed hypothesis from code inspection. It should be validated with an iOS simulator/device reproduction because the report concerns first activation timing after a cloud install.

## Platform impact analysis

### Android

Not confirmed impacted. The reported path is iOS-specific: `IMStoreView`, app-group `UserDefaults`, `DBServer.prepareKeyboardRuntimeDatabase(...)`, and `KeyboardViewController.setupDatabase(...)` are iOS code paths. Android has a separate service/startup and IM-selection implementation, and #115 already covers the recent Android first-keyboard family.

No Android bug fix or APK retest should be inferred from #121 unless a separate Android reproduction appears.

### iOS

Confirmed plausible by code inspection. The issue sits at the boundary between Settings-side cloud IM installation/registration and keyboard-extension runtime startup. A warm or first-launched extension can draw from default/incomplete in-memory state, then asynchronously receive the resolved activated IM list and active IM. The final layout/mode reconciliation is fragile because startup, async DB setup, host-field routing, and language switching do not appear to share one final layout resolver derived from field type plus the final `mEnglishOnly` value.

## Existing test coverage assessment

Existing iOS tests cover database/import and controller behavior in `LimeDBTest.swift`, `DBServerTest.swift`, `SetupImControllerTest.swift`, `ManageImControllerTest.swift`, `LIMEPreferenceManagerTest.swift`, and `KeyboardViewControllerTest.swift`.

This triage did not find a focused regression test for the cloud-download install flow that simulates:

1. install/register a cloud IM,
2. sync activated state / `keyboard_state`,
3. launch or re-enter the keyboard extension with remembered English mode or an English-forcing field, and
4. assert that the final visible layout matches the runtime `mEnglishOnly` mode after `setupDatabase(...)` applies the async runtime context.

## Code fragility assessment

The iOS startup path is fragile because mode selection, active IM restoration, layout resolution, and async database context application are spread across `initOnStartInput()`, `setupDatabase(...)`, shared defaults, and `DBServer.prepareKeyboardRuntimeDatabase(...)`.

The specific risk is not that the IM is missing from the installed list. It is that after the IM becomes visible to the keyboard extension, the final layout update can be based on saved/active IM state without a single helper that says: “given the final runtime mode and active IM list, choose the one correct layout.”

## Proposed fix / investigation plan

1. Reproduce on iOS by installing a cloud/downloadable IM into a fresh or reset app-group database, then first-switching to it from both normal text and English-mode contexts.
2. Log or inspect, around first activation:
   - `mEnglishOnly`
   - `mPersistentLanguageMode`
   - `persisted_english_mode`
   - `keyboard_list`
   - `keyboard_state`
   - resolved `activatedIMs`
   - current visible layout ID before and after `setupDatabase(...)`
3. Refactor the final layout selection into a small helper shared by `initOnStartInput()`, `setupDatabase(...)`, field-change handling, and `switchChiEng(...)`, so the visible layout is always derived from the final field type and runtime mode:
   - if the host field is phone, load `phone_number`;
   - else if the host field is number/decimal/ascii-capable number, load `symbols1`;
   - else if `mEnglishOnly` is true, load the English layout;
   - else if no activated IMs exist, load the English layout;
   - else load `resolvedLayoutId(for: activeIM)`.
4. After `setupDatabase(...)` applies `activatedIMs` / `activeIM`, call that helper regardless of whether the saved IM path ran. This should prevent an async database callback from leaving an English runtime with a Chinese visible layout.
5. Confirm the fix does not regress #119 text-import layout mapping, #115-style Android behavior, or iOS normal Chinese/English switching.

## Suggested regression coverage

Add focused iOS coverage around the layout/mode resolver if it can be factored out of UIKit-heavy controller code:

- English runtime mode with a non-empty activated IM list should resolve to `lime_english` / `lime_english_number`, not the active IM layout.
- Chinese runtime mode with an activated cloud IM should resolve to the active IM layout.
- Empty activated IM list should resolve to English layout.
- `setupDatabase(...)` / startup synchronization should reapply the correct layout after a cloud-installed IM appears in `activatedIMs`.

If direct `KeyboardViewController` unit coverage is too coupled to UIKit extension state, add a small pure helper and cover it from `KeyboardViewControllerTest.swift`.

## Verification plan

1. Fresh iOS install / reset app-group data.
2. Install at least one IM through the cloud/downloadable table store.
3. First-switch to the newly installed IM in a normal text field and confirm the visible layout and runtime mode are both Chinese IM mode.
4. Repeat with remembered English mode enabled and persisted English state set, and confirm the first visible layout is English if runtime mode is English.
5. Repeat in email/number/phone-style fields and confirm their existing field-specific English/numeric routing still wins.
6. Switch Chinese ↔ English after the first activation and confirm both the visible layout and composing behavior stay synchronized.
7. Reopen the keyboard after backgrounding Settings/host app to confirm warm-extension state does not reuse a stale layout.

## Implemented source fix

The focused fix keeps the iOS Settings-side preferences and keyboard-extension runtime snapshot coherent after a cloud/download install:

1. `LIMEPreferenceManager.syncIMActivatedState(...)` still rebuilds `keyboard_state` from enabled `im` rows, and now also repairs `keyboard_list` when the saved/current IM is empty or no longer among the enabled IMs. This prevents a warm keyboard extension from restoring a stale/default active IM while the enabled list points elsewhere.
2. `KeyboardViewController.initOnStartInput()` now shares its input-mode and layout-selection logic through `updateInputModeForCurrentField()` and `applyLayoutForCurrentInputField()`.
3. After async `setupDatabase()` refreshes `SearchServer`, `activatedIMs`, settings, and IM keys, it re-reads the current field mode and re-applies the visible layout from the freshly resolved activated IM list. This closes the first-switch race where an earlier `viewWillAppear()` pass selected a layout from stale or empty `activatedIMs`.

The fix is iOS-only and deliberately leaves #119 text-import layout mapping untouched.

## Verification coverage

- Added iOS regression coverage for the pure layout resolver, including English runtime with active IMs, Chinese runtime with active IMs, empty activated IMs, and numeric/phone field overrides.
- Added iOS source-guard coverage that asserts async DB setup re-applies current input mode/layout after activated IM refresh.
- Added iOS preference regression/source-guard coverage that asserts `syncIMActivatedState(...)` keeps `keyboard_list` coherent with enabled IMs.
- Manual/device verification is still needed on iOS for warm-extension and cold-start first activation after cloud/download IM install.

## Follow-up / retest condition

No public community retest request is needed because #121 is maintainer-created and now closed. Remaining validation is iOS release QA: run unit/simulator/device checks and confirm a future TestFlight/App Store build containing merge commit `e3aef89cca52b08fd48d68105dce2fe0042f0f19` preserves synchronized runtime mode, active IM, and visible layout. No Android APK retest applies unless separate Android evidence appears.
