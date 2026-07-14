# Issue #157: iOS hamburger reverse lookup selection is not persistent or immediate

## Summary

Maintainer-created iOS bug: changing the reverse-lookup source from the iOS keyboard hamburger menu can update the menu UI/hot preference path, but the committed-candidate reverse-lookup path still reads the App Group/cold preference, so the change may not take effect immediately in the current keyboard session and may appear unreliable after keyboard reopen/switch cycles.

Live issue: https://github.com/lime-ime/limeime/issues/157

## Current classification

- Labels: `bug`, `Usability`
- Assignee: `jrywu`
- Reporter/source: maintainer-created tracking issue from `limeimetw`
- Public acknowledgement: not needed because this is an internal maintainer-created tracking issue.

## Reported behavior

### Steps from the report

1. Open the LIME iOS keyboard.
2. Tap the hamburger/menu button.
3. Select or change the reverse lookup option.
4. Continue using the keyboard immediately.
5. Close and reopen the keyboard, or switch away and back.

### Actual behavior

The reverse lookup selection does not take effect immediately, and the selected value is not persisted reliably after the keyboard is reopened or switched.

### Expected behavior

The selected reverse lookup source should be effective immediately in the current keyboard session and should remain selected for future keyboard sessions.

## Source evidence inspected

### iOS keyboard hamburger path

- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`
  - Lines 201-208 define `hotPrefs` as the keyboard-owned extension-private hot store for hamburger two-writer preferences, including `<im>_im_reverselookup`.
  - Lines 1065-1073 define `hotReverseLookup(for:)`, which seeds the hot reverse-lookup value once from shared/cold defaults and then reads the hot store.
  - Lines 4336-4352 implement the hamburger reverse-lookup sub-picker. On selection, line 4344 writes `hotPrefs.setReverseLookup(option.value, for: self.activeIM)`, and lines 4346-4347 update the keyboard-to-app relay state.
  - Lines 4280-4284 build the parent hamburger menu label using `LIMEPreferenceManager.shared.reverseLookup(for: activeIM)`, not `hotReverseLookup(for:)`, so the visible label can lag the hot picker selection.

### iOS committed-candidate reverse lookup path

- `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`
  - Lines 2960-2973 show reverse lookup after committing a candidate.
  - Lines 2961-2965 build `<activeIM>_im_reverselookup` and read `sharedDefaults?.string(forKey: imKey)` before calling `searchServer.getCodeListStringFromWord(...)`.
  - This contradicts the hot-store ownership comments at lines 203-207 and the `hotReverseLookup(for:)` helper. A hamburger change can be stored in the extension-private hot store but the actual lookup display can still read a stale App Group/cold value.

### iOS Settings app reverse-lookup path

- `LimeIME-iOS/LimeSettings/Views/ReverseLookupSettingsView.swift`
  - Lines 79-95 bind the Settings-side reverse lookup picker.
  - Lines 83-84 update the shared preference manager, and lines 87-91 write a `PrefInbox` record so the keyboard can consume the app-side change.

### App-to-keyboard and keyboard-to-app sync paths

- `LimeIME-iOS/Shared/Database/SyncContract.swift`
  - Lines 165-181 define `PrefInboxRecord` / `PrefInbox` for app-to-keyboard delivery of two-writer hamburger preferences.
  - Lines 187-206 merge app-side reverse-lookup changes into the inbox.
  - Lines 236-262 define `RelayPrefSync.apply(...)`, which writes a keyboard-reported reverse-lookup value back into shared/cold defaults for the Settings app.
- `LimeIME-iOS/LimeSettings/LimeSettingsView.swift`
  - Lines 287-291 apply the keyboard relay payload to shared defaults when the Settings app receives it.

### Existing test coverage

- `LimeIME-iOS/LimeTests/RelayPrefSyncTest.swift`
  - Lines 88-116 cover relay payload round-trip and stale timestamp rejection for reverse lookup.
- `LimeIME-iOS/LimeTests/LIMEPreferenceManagerTest.swift`
  - Lines 260-265 cover per-table reverse-lookup preference round trip.
  - Lines 268-285 cover reverse-lookup option/target construction from enabled IM configs.

No inspected test directly gates that `KeyboardViewController` uses the hot reverse-lookup value for the committed-candidate lookup path immediately after a hamburger selection, or that the hamburger parent label reads the same hot source as the sub-picker.

## Likely root cause

The iOS keyboard has a split preference model for hamburger-owned settings:

- The hamburger reverse-lookup picker writes the extension-private hot store (`hotPrefs`) immediately.
- The committed-candidate reverse-lookup code still reads the App Group/cold store (`sharedDefaults`) through `sharedDefaults?.string(forKey: imKey)`.
- The hamburger parent label also reads `LIMEPreferenceManager.shared`, which is the shared/default manager rather than the extension-private hot source used by the picker.

Because of that mismatch, a new selection can be accepted by the picker but not used by the actual reverse-lookup display until a later relay/app-sync path updates the shared defaults. If relay application does not happen before the next keyboard use, the user sees the selection as non-immediate or unreliable.

## Proposed fix / investigation plan

1. Make the iOS keyboard's committed-candidate reverse-lookup lookup table come from the hot store, likely via `hotReverseLookup(for: activeIM)`, instead of reading `sharedDefaults?.string(forKey: imKey)` directly.
2. Make the hamburger parent menu label use the same hot lookup source as the sub-picker so UI feedback matches the actual keyboard behavior.
3. Preserve the existing relay behavior so keyboard-owned changes are still reported back to the app-side Settings UI when the relay path is available.
4. Add focused tests or source-level regression coverage around the two failure modes:
   - selecting reverse lookup through the hamburger immediately changes the lookup table used after candidate commit,
   - reopening/rebuilding the hamburger menu shows the hot selected value rather than a stale shared/default value.

## Platform impact

### iOS

Affected. The inspected iOS keyboard code has a concrete hot-store versus shared-defaults mismatch in the hamburger reverse-lookup and committed-candidate reverse-lookup paths.

### Android

Not reported and likely not affected by this specific iOS two-writer/hot-store issue. Android reverse lookup uses different Java service/settings paths, for example `SearchServer.getCodeListStringFromWord(...)` and `LIMEService.showReverseLookup(...)`, and does not share the inspected iOS `PrefInbox` / `KeyboardRelayPrefStore` / extension-private `UserDefaults.standard` split.

## Verification plan

### iOS

- In an iOS simulator or device with at least two enabled IM tables, open the keyboard and choose a reverse-lookup source from the hamburger menu for the active IM.
- Without closing the keyboard, commit a candidate and verify the reverse-lookup toast/strip uses the newly selected source.
- Reopen the hamburger menu and verify the selected reverse-lookup label/checkmark reflects the new value.
- Switch away from and back to the keyboard, then verify the selected reverse-lookup source persists and is still used after candidate commit.
- Verify Settings-side reverse-lookup changes still reach the keyboard through `PrefInbox`.

### Android

- No Android APK retest is needed for this iOS-only source path unless separate Android reverse-lookup preference evidence appears.

## Follow-up / release state

- Keep issue #157 open until the iOS source fix is implemented and verified in a TestFlight/App Store build.
- No public retest request is needed now because the issue is maintainer-created.
- `docs/BACKLOG.md` should track `fix#157 iOS` while implementation remains pending.
