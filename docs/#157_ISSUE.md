# Issue #157: iOS hamburger reverse lookup selection is not persistent or immediate

## Status

- Issue: https://github.com/lime-ime/limeime/issues/157
- Classification: bug, usability
- State: fixed in iOS v6.1.31; maintainer tracker closed
- Assignee: `jrywu`
- Source: maintainer-created iOS tracking issue
- Platform: iOS affected by the maintainer report. Android has a separate SharedPreferences-backed picker path and is not reported affected.
- Current follow-up: none. The fix is included in iOS v6.1.31, and no community retest request is needed because this is a maintainer-created tracking issue.

## Problem statement

On iOS, selecting the reverse lookup source from the keyboard hamburger/options menu can fail in two ways:

1. The selected reverse-lookup table does not take effect immediately for the current keyboard session.
2. The selected value is not reliably reflected after the keyboard is reopened or switched.

Expected behavior: changing the reverse-lookup source from the hamburger menu should immediately affect later candidate commits in the same keyboard session and should persist across later keyboard sessions.

## Source evidence

### iOS keyboard hot-store path

`LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift` intentionally treats hamburger-owned preferences as keyboard-owned hot preferences:

- `hotPrefs` is backed by extension-private `UserDefaults.standard` and is meant to avoid stale App Group values clobbering keyboard-side changes.
- `hotReverseLookup(for:)` seeds `<im>_im_reverselookup` from the App Group only once, then reads from `hotPrefs`.
- `showReverseLookupPicker()` reads the current value through `hotReverseLookup(for: activeIM)` and writes the selected value to `hotPrefs.setReverseLookup(...)`.
- The picker also calls `relayPrefStore.update(reverseLookupIM:reverseLookupValue:)` so the Settings app can learn the keyboard-side change through the root relay.

This is the right persistence direction for a keyboard-owned hamburger preference, but two nearby call sites still bypass the hot value.

### iOS stale immediate-use path

`KeyboardViewController.commitCandidate(...)` shows reverse lookup after a candidate is committed. It builds the active key as `<activeIM>_im_reverselookup`, but then reads the lookup table from `sharedDefaults`:

```swift
let imKey = "\(activeIM)_im_reverselookup"
let notifyEnabled = sharedDefaults?.object(forKey: "reverse_lookup_notify") as? Bool ?? true
if notifyEnabled,
   let lookupTable = sharedDefaults?.string(forKey: imKey),
   lookupTable != "none", !lookupTable.isEmpty,
   let ss = searchServer {
    ...
}
```

That means a selection made inside the keyboard hamburger menu is written to the hot keyboard store, but the actual reverse-lookup display path can keep using the older App Group value until the app-side relay catches up or the keyboard restarts in a way that reseeds/updates state. This directly explains the "not immediate" symptom.

`showGlobeMenu(...)` has a similar display-only mismatch: it builds the top-level `字根反查` row label using `LIMEPreferenceManager.shared.reverseLookup(for: activeIM)`, which reads the App Group value instead of the keyboard hot value. After a hamburger-menu change, reopening the top-level menu can therefore show the previous label even if `showReverseLookupPicker()` itself wrote the new hot value.

### iOS persistence / app relay path

The Settings side has relay support:

- `KeyboardRelayPrefStore.update(...)` stores the last reverse-lookup `im/value` pair in `relay-prefs.json`.
- `encodeRelayPayload(...)` includes `rlim` and `rlval` when present.
- `LimeSettingsView.handleRootRelayTextChange()` calls `RelayPrefSync.apply(...)`, which writes the relay value into the App Group key `<im>_im_reverselookup`.

This means persistence depends on the keyboard-to-app relay being received by the Settings app. The current code does not immediately mirror the hamburger selection into the App Group from the keyboard, and the runtime commit path is still reading the App Group instead of the hot store. The fix should preserve the intended hot-store ownership while ensuring runtime use and user-visible labels read the hot value.

### App-side reverse-lookup settings path

`LimeIME-iOS/LimeSettings/Views/ReverseLookupSettingsView.swift` writes app-side reverse-lookup selections to the App Group with `prefs.setReverseLookup(...)` and also writes a `PrefInbox` record so the keyboard can drain the change into the hot store on its next appearance. That app-to-keyboard path is separate from the hamburger keyboard-to-app relay path and should continue to work.

### Android comparison

Android uses one preference store for this path:

- `LimeStudio/app/src/main/java/org/limeime/global/LIMEPreferenceManager.java` reads and writes reverse lookup through `SharedPreferences` in `getReverseLookupTable(...)` and `setReverseLookupTable(...)`.
- `LimeStudio/app/src/main/java/org/limeime/LIMEService.java` uses `mLIMEPref.getReverseLookupTable(activeIM)` for both the hamburger row label and `showReverseLookupPicker()` current selection, and writes the selected value with `mLIMEPref.setReverseLookupTable(activeIM, values[which])`.

No Android issue is indicated by the current report. Android is useful as a parity reference: the menu label, picker selected state, and runtime lookup should all read the same effective value.

## Existing test coverage and gap

Current iOS tests cover these lower-level pieces:

- `LIMEPreferenceManagerTest.testRoundTripReverseLookupByTableNick()` verifies per-table reverse-lookup storage keys.
- `RelayPrefSyncTest.testReverseLookupRoundTripsAndApplies()` verifies reverse-lookup values survive keyboard-to-app relay payload encoding/decoding and App Group application.
- `SyncContractTest` covers the app-to-keyboard `PrefInbox` sequencing path.

The missing coverage is the keyboard runtime integration:

- after `showReverseLookupPicker()` or an equivalent helper writes a hot reverse-lookup value, `commitCandidate(...)` should read the same hot value immediately
- the top-level hamburger row label should display the same effective hot value
- app-side `ReverseLookupSettingsView` changes should still reach the keyboard through `PrefInbox` and update the same effective value
- relay payloads should still let Settings persist the keyboard-side choice later

## Likely root cause

High confidence: the iOS keyboard has split reverse-lookup preference ownership between a keyboard hot store and an App Group cold store, but two keyboard-side consumers still read the cold App Group value. The hamburger picker writes the hot store, while `commitCandidate(...)` and the top-level menu label still read the cold store. That produces stale runtime behavior and stale menu labels until a relay or later lifecycle event catches up.

Persistence may also appear unreliable if the keyboard-to-app relay is not delivered before the keyboard process is killed, because the current hamburger path does not directly update the App Group cold value. The implementation should decide whether persistence should rely only on the relay or whether a safe App Group mirror is needed when full access is available, but immediate runtime behavior should not depend on that relay.

## Fix (landed in v6.1.31)

Commit `fd35e184` made `hotReverseLookup(for:)` the keyboard's single effective reverse-lookup reader:

- `commitCandidate(...)` now reads the hot value, so a hamburger-menu selection takes effect immediately.
- `showGlobeMenu(...)` now uses the same hot value, so the displayed row label no longer shows stale App Group state.
- `showReverseLookupPicker()` continues to read and write the hot store and preserve the keyboard-to-app relay path.
- `testHotReverseLookupWinsOverStaleColdAndSeedsOnceWhenAbsent()` verifies that a current hot value wins over stale cold state and that cold state is used only to seed an absent hot value.

## Verification plan

### iOS

1. Add unit coverage for the effective reverse-lookup policy: a hot-store value should win over an older App Group value for keyboard runtime reads and menu labels.
2. Add or update relay tests to verify a hamburger-side reverse-lookup change still appears in the encoded relay payload and is applied by `RelayPrefSync` to the App Group.
3. Add or keep `PrefInbox` coverage proving app-side Settings changes can still be delivered into the keyboard hot store.
4. On an iOS simulator/device with at least two enabled IMs, change `字根反查` from the hamburger menu, commit a candidate immediately, and verify the reverse-lookup toast uses the newly selected table without closing/reopening the keyboard.
5. Reopen the hamburger menu and verify the displayed selected label is current.
6. Close/reopen or switch away/back to the keyboard and verify the selected reverse lookup remains effective.
7. Verify changing the reverse-lookup setting from the Settings app still reaches the keyboard on the next activation.

### Android

No Android source change is indicated. If shared reverse-lookup metadata or documentation changes, run Android regression checks around the hamburger reverse-lookup picker, but this issue's active fix scope is iOS.

## Resolution / release state

- Fix commit: `fd35e184` (`#157 fix iOS hamburger reverse-lookup to read hot store`).
- Included in the published iOS-only GitHub release v6.1.31 and App Store build 10.
- Closed as a maintainer-created tracker after the maintainer accepted the v6.1.31 fix.
- Removed `fix#157 iOS` from `docs/BACKLOG.md`.
