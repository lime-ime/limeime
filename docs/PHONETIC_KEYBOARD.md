# Phonetic Keyboard — Android Parity Plan

## Context

The `phonetic_keyboard_type` preference controls three related behaviours on Android:

1. **Letter → phonetic symbol remap** applied when the user types on an English-label keyboard (e.g. ETEN 26-key or HSU variants) — `LimeDB.preProcessingRemappingCode()`.
2. **Dual-code query expansion** for keys that map to two possible phonetic codes (initial vs final) — `LimeDB.preProcessingForExtraQueryConditions()`.
3. **Visible keyboard layout swap** — on pref change, the DB's `im.keyboard` column for the `phonetic` row is rewritten, and the next `onStartInput` re-reads it and loads the appropriate XML.

The iOS port has all three data paths present, but each has a defect that makes the `_symbol` variants broken and the layout-swap non-live. The pref also sits in the global 喜好設定 tab even though it only affects the phonetic IM.

This plan covers four tasks:

- **T1** — fix remap to handle `eten26_symbol` / `hsu_symbol`
- **T2** — fix dual-map to handle `eten26_symbol` / `hsu_symbol`
- **T3** — make the layout switch live when the pref changes
- **T4** — move the picker UI from 喜好設定 into the phonetic IM details page

(A fifth gap — providing an English-label layout variant for ETEN26/HSU non-`_symbol` modes — is noted but deferred to a separate task; it requires new JSON layouts and is outside this plan's scope.)

## Current Gaps

### T1 — Remap misses `_symbol` variants

[LimeDB.swift:1516-1558](../LimeIME-iOS/Shared/Database/LimeDB.swift#L1516) uses exact string matches in a `switch`:

```swift
switch kbType {
case "et_41", "eten":      …
case "et26", "eten26":     …   // does NOT match "eten26_symbol"
case "hsu":                …   // does NOT match "hsu_symbol"
default:                   …   // falls through, no remap applied
}
```

Android uses `startsWith(IM_PHONETIC_KEYBOARD_TYPE_ETEN26)` and `startsWith(IM_PHONETIC_KEYBOARD_HSU)` at [LimeDB.java:1988-1996](../LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java#L1988), so `eten26`, `eten26_symbol`, `hsu`, `hsu_symbol` all land in the same branch. iOS does not, so the `_symbol` variants silently skip remap and produce wrong codes.

### T2 — Dual map misses `_symbol` variants (same pattern)

[LimeDB.swift:1703-1713](../LimeIME-iOS/Shared/Database/LimeDB.swift#L1703) uses the same exact-match `switch` and leaves `keysDualMap` empty for `eten26_symbol` / `hsu_symbol`. Query expansion then returns `nil` and no extra SQL clauses are added, so single-key candidates that exist only under the alternate code don't surface.

### T3 — Layout switch is not live

Settings writes the new keyboard code to the DB via [PreferencesTabView.updatePhoneticKeyboard()](../LimeIME-iOS/LimeSettings/Views/PreferencesTabView.swift#L226) calling `DBServer.setImConfigKeyboard("phonetic", kb)`.

The running keyboard extension caches `activatedIMs` at `setupDatabase()` time and never re-queries the DB in [initOnStartInput()](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L245) when the keyboard returns to foreground. `resolvedLayoutId(for: "phonetic")` at [KeyboardViewController.swift:437](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L437) therefore returns the stale cached layout id. The swap only takes effect on full extension tear-down.

Android's equivalent `onStartInput` re-reads the `im.keyboard` column every time the keyboard is shown, so changes made in Settings are picked up on the next text field focus.

### T4 — Pref lives in the wrong settings page

[PreferencesTabView.swift:159-169](../LimeIME-iOS/LimeSettings/Views/PreferencesTabView.swift#L159) hosts the "注音鍵盤 → 鍵盤類型" picker in the global 喜好設定 tab. Since it only affects the phonetic IM, it belongs on the phonetic IM's details page next to the existing `軟鍵盤配置 → 鍵盤佈局` row at [IMDetailView.swift:62-68](../LimeIME-iOS/LimeSettings/Views/IMDetailView.swift#L62).

## Plan

### T1 — Normalise the remap matcher

Rewrite the `switch` in `preProcessingRemappingCode` as an `if` chain that uses `hasPrefix`, matching Android's semantics. Preserve the existing `et_41` / `eten` exact match (those don't have `_symbol` variants).

**File**: [LimeIME-iOS/Shared/Database/LimeDB.swift:1516-1558](../LimeIME-iOS/Shared/Database/LimeDB.swift#L1516)

```swift
if kbType == "et_41" || kbType == "eten" {
    // single-map ETEN 41-key
} else if kbType.hasPrefix("eten26") || kbType == "et26" {
    // dual-map ETEN 26-key (covers eten26, eten26_symbol, et26)
} else if kbType.hasPrefix("hsu") {
    // dual-map HSU (covers hsu, hsu_symbol)
} else {
    // standard — shifted-only
}
```

No new data tables needed — the existing ETEN26/HSU remap constants are shared across `_symbol` and non-symbol variants.

### T2 — Normalise the dual-map matcher

Apply the same matcher change in `preProcessingForExtraQueryConditions`.

**File**: [LimeIME-iOS/Shared/Database/LimeDB.swift:1703-1713](../LimeIME-iOS/Shared/Database/LimeDB.swift#L1703)

```swift
if table == "phonetic" {
    if kbType.hasPrefix("eten26") || kbType == "et26" {
        dualKey = LimeDB.ETEN26_DUALKEY
        dualKeyRemap = LimeDB.ETEN26_DUALKEY_REMAP
    } else if kbType.hasPrefix("hsu") {
        dualKey = LimeDB.HSU_DUALKEY
        dualKeyRemap = LimeDB.HSU_DUALKEY_REMAP
    }
}
```

### T3 — Live layout switch on keyboard show

At the top of `initOnStartInput()` (after the `mEnglishOnly` branch resolves `activeIM`), re-query the DB for the current IM's keyboard id and refresh the relevant slot in the in-memory `activatedIMs` list. Then proceed with the existing `resolvedLayoutId` / `LayoutLoader.load` swap.

**File**: [LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift:245-314](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L245)

Sketch:

```swift
// At the top of initOnStartInput(), before resolvedLayoutId is called:
if !mEnglishOnly, !activeIM.isEmpty, let db = self.db {
    if let row = db.getImConfigRow(tableNick: activeIM),
       let idx = activatedIMs.firstIndex(where: { $0.tableNick == activeIM }),
       row.keyboardId != activatedIMs[idx].keyboardId {
        activatedIMs[idx].keyboardId = row.keyboardId
    }
}
```

Reuse the existing DB accessor used during `setupDatabase()` — `LimeDB.getImConfigRow(tableNick:)` or whichever helper already populates `activatedIMs`. Prefer a targeted re-read over reloading the full list to keep the viewWillAppear path fast.

Then let the existing layout-swap block at [KeyboardViewController.swift:295-310](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L295) pick up the change through `resolvedLayoutId(for: activeIM)`.

Also push the fresh `phoneticKeyboardType` to `searchServer` in the same block so the DB-layer remap stays in sync with the visual layout.

### T4 — Move picker to phonetic IM details page

1. **Remove** the "注音鍵盤" section and related state in [PreferencesTabView.swift](../LimeIME-iOS/LimeSettings/Views/PreferencesTabView.swift):
   - `@AppStorage("phonetic_keyboard_type") …` (line 37)
   - `phoneticOptions` / `phoneticLabels` (lines 72-73)
   - the `Section(header: Text("注音鍵盤"))` block (lines 159-169)
   - the `updatePhoneticKeyboard(type:)` helper (lines 226-234)

2. **Add** an IM-specific section to [IMDetailView.swift](../LimeIME-iOS/LimeSettings/Views/IMDetailView.swift), shown only when `im.tableNick == "phonetic"`. Mirror the structure of the existing array10 / custom conditional sections at lines 71-105:

   ```swift
   if im.tableNick == "phonetic" {
       Section(header: Text("注音鍵盤類型")) {
           Picker("鍵盤類型", selection: $phoneticKeyboardType) {
               ForEach(0..<phoneticOptions.count, id: \.self) { i in
                   Text(phoneticLabels[i]).tag(phoneticOptions[i])
               }
           }
           .onChange(of: phoneticKeyboardType) { newType in
               updatePhoneticKeyboard(type: newType)
           }
       }
   }
   ```

   Move the `phoneticOptions` / `phoneticLabels` constants and `updatePhoneticKeyboard(type:)` helper into `IMDetailView`. The helper can retain its existing implementation that calls `DBServer.shared.setImConfigKeyboard("phonetic", kb)`.

3. **Update** [docs/LIME_SETTINGS.md](LIME_SETTINGS.md#L649) to move the "鍵盤類型" table out of §8.5 and into the phonetic IM section (wherever IM-specific settings are documented), so the doc reflects the new location.

4. **Verify** [KeyboardPickerView.swift:63-65](../LimeIME-iOS/LimeSettings/Views/KeyboardPickerView.swift#L63) continues to write `phoneticKeyboardType` correctly — no change needed, but confirm behaviour during verification.

## Files Modified

| File | Task | Change |
|---|---|---|
| `LimeIME-iOS/Shared/Database/LimeDB.swift` | T1, T2 | Replace two `switch` blocks with `hasPrefix`-based `if` chains |
| `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift` | T3 | Re-read phonetic IM's `keyboardId` from DB at the top of `initOnStartInput()` |
| `LimeIME-iOS/LimeSettings/Views/PreferencesTabView.swift` | T4 | Remove "注音鍵盤" section + associated state and helper |
| `LimeIME-iOS/LimeSettings/Views/IMDetailView.swift` | T4 | Add conditional "注音鍵盤類型" section gated on `im.tableNick == "phonetic"` |
| `docs/LIME_SETTINGS.md` | T4 | Move "鍵盤類型" documentation to the IM-specific section |

## Reusable Helpers (no new code needed)

- **Remap data**: `LimeDB.ETEN_KEY`, `ETEN26_KEY`, `HSU_KEY`, `*_REMAP_INITIAL/FINAL`, `ETEN26_DUALKEY`, `HSU_DUALKEY` at [LimeDB.swift:1480-1501](../LimeIME-iOS/Shared/Database/LimeDB.swift#L1480) — all already defined and identical to Android's constants.
- **Dual-map builder**: `buildOrGetDualMap(…)` at [LimeDB.swift:1603](../LimeIME-iOS/Shared/Database/LimeDB.swift#L1603).
- **Cache reset**: `resetCache()` at [LimeDB.swift:1652](../LimeIME-iOS/Shared/Database/LimeDB.swift#L1652) is already called on IM / phoneticKeyboardType change via the `didSet` at [LimeDB.swift:97-104](../LimeIME-iOS/Shared/Database/LimeDB.swift#L97); T3's re-read will benefit from this existing invalidation.
- **DB write**: `DBServer.setImConfigKeyboard(_:_:)` is the existing write path used by both [PreferencesTabView](../LimeIME-iOS/LimeSettings/Views/PreferencesTabView.swift#L231) and [KeyboardPickerView](../LimeIME-iOS/LimeSettings/Views/KeyboardPickerView.swift#L63) — keep using it unchanged.
- **DB read for layout id**: whatever accessor `setupDatabase()` already uses to populate `activatedIMs` — reuse in T3.

## Verification

End-to-end tests with the keyboard extension running on a device or simulator. The SourceKit "No such module 'UIKit'" warnings seen in the IDE are not real build errors.

1. **T1 remap (`eten26_symbol`)**
   - Settings → 輸入法 → 注音 → 注音鍵盤類型 → pick `倚天 26 鍵 (符號鍵盤)`.
   - In any text field, activate LIME phonetic IM and type the key sequence `q` (maps to `ㄗ` initial under ETEN26) followed by a final and tone.
   - Expect a valid candidate list. Before T1 fix, no candidates would appear because the code wasn't remapped.
   - Repeat with `hsu_symbol`.

2. **T2 dual-map (`eten26_symbol`, `hsu_symbol`)**
   - With `eten26_symbol` active, type a single key that has two possible codes (e.g. a letter in `ETEN26_DUALKEY`) and confirm the candidate list includes entries from both code variants.
   - Verify `LimeDB.isCodeDualMapped()` returns true after `preProcessingForExtraQueryConditions` builds the dual map (spot-check via `LimeDBTest` if needed).

3. **T3 live layout switch**
   - While a text field is focused with the LIME keyboard visible, background the app → open LimeSettings → change 鍵盤類型 → return to the text field.
   - Expect the keyboard view to show the newly picked layout on next appearance (without killing the extension).
   - Confirm `phoneticKeyboardType` is pushed to `searchServer` so the first keystroke on the new layout produces correctly-remapped codes.

4. **T4 UI move**
   - Open the Settings app → 喜好設定: confirm the "注音鍵盤" section is gone.
   - Navigate 輸入法 → 注音 detail page: confirm a "注音鍵盤類型" section with the same picker appears.
   - Change the picker value; verify via the existing `DBServer.setImConfigKeyboard` write (reuse KeyboardPickerView's existing handler or the migrated `updatePhoneticKeyboard`) that the DB is updated and T3 picks it up live.
   - Confirm the picker is absent for non-phonetic IMs (e.g. cangjie detail page).

5. **Regression**
   - Verify standard phonetic (`standard`) still works — no remap applied, candidates show as before.
   - Verify `et_41` / `eten` still works — single-map remap still applied.
   - Run `LIMEPreferenceManagerTest` and `LimeDBTest` to confirm no test regressions. Existing tests at `LIMEPreferenceManagerTest.swift:210-211` cover the `phoneticKeyboardType` round-trip.
