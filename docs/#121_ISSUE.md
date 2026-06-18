# Issue #121: iOS first cloud-installed IM switch can desync layout and input mode

## Current status

- GitHub issue: https://github.com/lime-ime/limeime/issues/121
- Reporter/source: `limeimetw` maintainer-created tracking issue
- Classification: `bug`, `Type-Defect`, `Usability`
- Current state: open and assigned to `jrywu`; source fix prepared in PR workflow
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
- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`

`IMDownloadManager.importDownloaded(...)` imports the downloaded database, calls `server.registerIM(imName:tableName:label:keyboardId:)`, then calls `LIMEPreferenceManager.shared.syncIMActivatedState(dbServer: server)` so Settings records the newly available/activated IM for the keyboard extension.

`LimeDB.registerIM(...)` returns immediately if any `im` rows already exist for the IM code. For cloud-installed IMs this preserves rows merged from the downloaded DB instead of synthesizing a fallback registration row.

`DBServer.prepareKeyboardRuntimeDatabase(...)` builds the keyboard runtime context by reading `getAllImConfigs()`, applying `keyboard_state` from the shared app-group defaults when present, and choosing the first activated IM as the initial runtime table.

### Keyboard runtime startup path

`KeyboardViewController` starts with in-memory defaults before the database setup callback completes:

- `activeIM` defaults to `"phonetic"`.
- `activatedIMs` starts empty.
- `mEnglishOnly` starts false, then `initOnStartInput()` maps the host field type and remembered language preference to the runtime mode.

`initOnStartInput()` chooses the visible layout from `mEnglishOnly`, `activatedIMs`, and `activeIM`:

- English-only mode or no activated IMs loads `lime_english` / `lime_english_number`.
- Chinese mode loads `resolvedLayoutId(for: activeIM)`.

`setupDatabase(...)` asynchronously assigns `searchServer`, `activatedIMs`, and either the saved `keyboard_list` IM or the resolved initial IM. If a saved IM is found and `mEnglishOnly` is false, it refreshes the visible layout to that IM's layout. The callback does not currently make the opposite correction: when the runtime was English because the host field or persisted language mode says English, it should ensure the visible layout is English after the cloud-installed IM state is restored.

## Likely root cause

The cloud install path correctly registers/imports the new IM and syncs activated state, but the keyboard extension can have a first-start ordering gap between:

1. Settings writing the newly installed IM / activated-state defaults, and
2. the keyboard extension asynchronously rebuilding `activatedIMs`, `activeIM`, and the visible layout.

`setupDatabase(...)` can refresh the visible layout to the saved Chinese IM when `mEnglishOnly` is false, but it does not explicitly reconcile the final visible layout with `mEnglishOnly` after the async runtime context is applied. That can leave the keyboard showing a Chinese IM layout even when the runtime input mode is English.

This is a source-backed hypothesis from code inspection. It should be validated with an iOS simulator/device reproduction because the report concerns first activation timing after a cloud install.

## Platform impact analysis

### Android

Not confirmed impacted. The reported path is iOS-specific: `IMStoreView`, app-group `UserDefaults`, `DBServer.prepareKeyboardRuntimeDatabase(...)`, and `KeyboardViewController.setupDatabase(...)` are iOS code paths. Android has a separate service/startup and IM-selection implementation, and #115 already covers the recent Android first-keyboard family.

No Android bug fix or APK retest should be inferred from #121 unless a separate Android reproduction appears.

### iOS

Confirmed plausible by code inspection. The issue sits at the boundary between Settings-side cloud IM installation/registration and keyboard-extension runtime startup. A warm or first-launched extension can draw from default/incomplete in-memory state, then asynchronously receive the resolved activated IM list and active IM. The final layout/mode reconciliation is fragile because it only refreshes the saved-IM Chinese layout path and does not centrally reapply the layout from the final `mEnglishOnly` value.

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
3. Refactor the final layout selection into a small helper shared by `initOnStartInput()`, `setupDatabase(...)`, and `switchChiEng(...)`, so the visible layout is always derived from the final runtime mode:
   - if `mEnglishOnly` is true, load the English layout;
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

No public community retest request is needed because #121 is maintainer-created. After the source fix lands, verify with iOS unit/simulator/device checks. Close the maintainer-created issue directly after maintainer/local verification or after a TestFlight/App Store build containing the targeted fix is validated.
