# Preference Backup And Restore

## Purpose

This document records the cross-platform preference backup/restore contract for full LIME database backups.

It complements `docs/DB_BAK_RES.md`, which defines the ZIP layout for database files. Full backup should carry both:

- runtime database files, such as `databases/lime.db`;
- user-facing preferences, such as keyboard appearance, input behavior, learning settings, reverse lookup, and per-IM import/delete options.

The goal is to make future Android and iOS backups restore preferences across platforms while preserving restore compatibility for older Android backups.

## Current State

Full backup currently uses a shared archive entry name:

```text
shared_prefs.bak
```

The entry name is shared, but the payload format is not.

| Platform | Current backup payload | Current restore behavior |
|---|---|---|
| Android | Java `ObjectOutputStream` serialized `Map<String, ?>` from default `SharedPreferences`. | Restores `shared_prefs.bak` with `ObjectInputStream`; old Android backups depend on this behavior. |
| iOS | Binary property list containing an allowlisted subset of App Group `UserDefaults`. | Has restore helper for plist payloads, but full archive restore currently focuses on `lime.db`; preference extraction must be wired into full restore. |

Because Android and iOS use different binary formats, `shared_prefs.bak` should be treated as a legacy platform-local payload, not as the future cross-platform preference format.

## Preferred Shared Layout

Future full backups should include both the legacy platform-local preference file and a new cross-platform JSON manifest:

```text
databases/lime.db
databases/lime.db-journal
shared_prefs.bak
preferences/lime_prefs.json
```

`shared_prefs.bak` remains for same-platform backward compatibility.

`preferences/lime_prefs.json` is the preferred cross-platform preference payload.

## JSON Manifest Format

The JSON manifest should be UTF-8 encoded and small enough to read fully into memory. It must not contain arbitrary app state.

Example:

```json
{
  "schema": 1,
  "sourcePlatform": "android",
  "preferences": {
    "keyboard_theme": 6,
    "keyboard_size": "1",
    "font_size": "1",
    "smart_chinese_input": true,
    "enable_emoji_position": 5
  }
}
```

Required fields:

| Field | Type | Meaning |
|---|---|---|
| `schema` | integer | Manifest schema version. Start at `1`. |
| `preferences` | object | Allowlisted preference key/value pairs. |

Optional fields:

| Field | Type | Meaning |
|---|---|---|
| `sourcePlatform` | string | `android` or `ios`, for diagnostics only. Restore must not require it. |
| `appVersion` | string | Version string for diagnostics only. |
| `createdAt` | string | ISO-like timestamp for diagnostics only. |

Restore must ignore unknown top-level fields.

## Restore Priority

Restore should be more tolerant than export.

Recommended full-restore preference priority:

1. If `preferences/lime_prefs.json` exists and validates, restore it.
2. If JSON is missing or invalid on Android, restore legacy Android `shared_prefs.bak`.
3. If JSON is missing or invalid on iOS, restore legacy iOS plist `shared_prefs.bak`.
4. If no valid preference payload exists, restore the database and leave current/default preferences unchanged.

This order preserves old Android backup restore while allowing new backups to move between Android and iOS.

## Android Backward Compatibility

Android must continue to restore old Android backups that only contain:

```text
databases/lime.db
databases/lime.db-journal
shared_prefs.bak
```

The legacy Java-serialized `shared_prefs.bak` reader should remain available on Android.

Android should write `preferences/lime_prefs.json` in new backups, but should also keep writing legacy `shared_prefs.bak` for downgrade and same-platform compatibility.

Android should not replace the legacy payload inside `shared_prefs.bak` with JSON unless a separate migration plan is approved, because older Android builds expect Java serialization there.

## iOS Backward Compatibility

iOS should continue to restore older iOS backups whose `shared_prefs.bak` is a binary plist.

iOS should write `preferences/lime_prefs.json` in new backups. It may continue to write plist `shared_prefs.bak` for same-platform compatibility.

iOS is not required to parse Android Java serialization. Old Android backups can still restore the database on iOS, but cross-platform preference restore requires a backup that contains `preferences/lime_prefs.json`.

## Preference Scope

The JSON manifest should contain only preferences that are safe, meaningful, and portable.

Portable cross-platform keys should be sourced from `docs/PREFS_TABLE.md` and grouped as:

- keyboard appearance;
- keyboard feedback;
- input-method behavior;
- Han conversion;
- related phrases and learning;
- English keyboard;
- reverse lookup;
- per-IM detail/import options where the key pattern is stable.

Android-only physical keyboard preferences may be exported and restored on Android. iOS should ignore them unless an iOS counterpart is later added.

iOS-only runtime or app-extension implementation details should not be exported unless they are intentionally shared user settings.

## Allowed Types

Each key must have a declared type. Restore must reject values whose JSON type does not match the key.

Supported JSON value types:

| Preference kind | JSON type |
|---|---|
| Boolean toggle | boolean |
| Numeric picker backed by integer | number, integer only |
| String picker | string |
| Reverse lookup target | string |

Do not restore arrays or nested objects as preference values.

## Key Policy

Restore must use an allowlist, not blindly apply every JSON key.

Recommended allowlist categories:

| Category | Example keys |
|---|---|
| Appearance | `keyboard_theme`, `keyboard_size`, `font_size`, `number_row_in_english`, `show_arrow_key`, `split_keyboard_mode` |
| Feedback | `vibrate_on_keypress`, `vibrate_level`, `sound_on_keypress`, `keypress_sound_volume` |
| IM behavior | `smart_chinese_input`, `auto_chinese_symbol`, `persistent_language_mode`, `enable_emoji_position`, `similiar_list` |
| Han conversion | `han_convert_option` |
| Related and learning | `similiar_enable`, `candidate_suggestion`, `learn_phrase`, `learning_switch` |
| English keyboard | `english_dictionary_enable`, `auto_cap` |
| Reverse lookup | `custom_im_reverselookup`, `cj_im_reverselookup`, `bpmf_im_reverselookup`, etc. |
| Per-IM options | `accept_number_index`, `accept_symbol_index`, `auto_commit`, `phonetic_keyboard_type`, `backup_on_delete_<table>`, `restore_on_import_<table>`, `cj4_semicolon_key` |

Do not restore payment, entitlement, license, server URL, debug, migration, or internal feature-flag keys from backups.

## Value Normalization

Android `ListPreference` values are often stored as strings, while iOS may store equivalent values as integers. The compatibility layer should normalize values by key.

Examples:

| Key | Canonical JSON value | Android restore | iOS restore |
|---|---|---|---|
| `keyboard_theme` | integer | Store as string if the Android preference API expects `"6"`. | Store as integer. |
| `keyboard_size` | string | Store as string. | Store as string. |
| `smart_chinese_input` | boolean | Store as boolean. | Store as boolean. |
| `enable_emoji_position` | integer | Store as string or integer according to existing Android accessor expectations. | Store as integer. |
| `phonetic_keyboard_type` | string | Store as string. | Store as string. |

The JSON manifest should use the canonical semantic type. Platform adapters handle storage conversion.

## Security And Robustness

Preference restore must be defensive:

- cap the JSON payload size;
- reject invalid JSON;
- reject unknown schema versions unless a migration exists;
- apply only allowlisted keys;
- type-check every value;
- clamp numeric values to valid ranges where a preference has bounded choices;
- ignore unknown keys;
- never clear existing preferences before a new payload validates;
- avoid restoring secrets, payment flags, URLs, or debug-only state;
- restore preferences independently from database restore success when practical, but report partial failures in logs.

## Export Rules

New full backups should:

1. Write the database entries using the shared layout from `docs/DB_BAK_RES.md`.
2. Write legacy `shared_prefs.bak` in the platform's existing format.
3. Write `preferences/lime_prefs.json` using the allowlisted canonical key/type map.
4. Omit default-valued preferences only if restore semantics are clear; otherwise include explicit values for user-facing settings.

Table `.limedb` exports must not include preference payloads.

## Restore Rules

Full restore should:

1. Locate and restore `lime.db` according to `docs/DB_BAK_RES.md`.
2. Locate `preferences/lime_prefs.json`.
3. If JSON exists and validates, apply it through the compatibility adapter.
4. If JSON is absent or invalid, fall back to legacy `shared_prefs.bak` on the current platform.
5. Refresh preference-backed runtime caches after restore.

Restore should not require `shared_prefs.bak` to exist if JSON exists.

Restore should not fail the database restore merely because preference restore fails.

## Implementation Plan

1. Define a shared preference schema table in code on each platform.
2. Add JSON export helpers:
   - Android: read default `SharedPreferences`, normalize allowed keys, write JSON.
   - iOS: read App Group `UserDefaults` plus any approved standard `UserDefaults` per-IM keys, normalize allowed keys, write JSON.
3. Add JSON restore helpers:
   - parse, validate, normalize, and apply through platform storage APIs;
   - do not clear unrelated preferences.
4. Add JSON entry to full backup export.
5. Wire full restore to prefer JSON and fall back to legacy payloads.
6. Keep Android legacy `ObjectInputStream` restore path for old Android backups.
7. Keep iOS legacy plist restore path for old iOS backups.
8. Update `docs/DB_BAK_RES.md` after implementation to reference this preference contract.

## Implementation Status

Implemented compatibility layer:

- Android now writes `preferences/lime_prefs.json` in full backups while keeping legacy `shared_prefs.bak` for old Android restore compatibility.
- Android restore prefers the JSON manifest when present and falls back to the legacy Java-serialized `shared_prefs.bak` path when JSON is missing or invalid.
- iOS has a matching `PreferenceBackupAdapter` for the canonical JSON manifest and ignores Android-only keys such as `physical_keyboard_sort`.
- Both adapters cap/validate the manifest, use allowlisted keys, reject wrong value types, and avoid payment/internal keys.

Verified results:

- Android adapter instrumentation tests passed.
- Android full backup/restore E2E passed for the real ZIP flow, including `databases/lime.db`, legacy `shared_prefs.bak`, and `preferences/lime_prefs.json`.
- iOS adapter XCTest passed on the booted iPhone 17 Pro Max simulator, including Android-style manifest restore.
- Cross-platform fixture E2E passed in both directions:
  - Android restored an iOS-style preference backup through `IntegrationTestBackupRestore#test_5_6_11_RestoreIosStylePreferenceFixtureThroughAndroidAdapter`.
  - iOS restored an Android-style preference backup through `DBServerTest.testDBServerRestoresAndroidStylePreferenceFixture`.
- iOS simulator build passed.

Remaining follow-up:

- Non-macOS CI should skip only the iOS/Xcode half of cross-platform fixture E2E while still running Android adapter and Android fixture restore checks.

## Required Tests

Preference compatibility must be covered at three levels:

- adapter unit tests for schema, validation, and type conversion;
- same-platform full-backup integration tests;
- cross-platform fixture E2E tests for Android-to-iOS and iOS-to-Android restore.

### Adapter Tests

Add focused adapter tests on both platforms.

Required Android adapter tests:

- JSON `keyboard_theme: 4` restores to Android `SharedPreferences` as `"4"` if the Android accessor expects a `ListPreference` string.
- JSON `smart_chinese_input: false` restores as a boolean.
- JSON `keyboard_size: "1"` restores as a string.
- Unknown keys are ignored.
- Wrong-type values are ignored.
- Out-of-range numeric values are ignored or clamped according to the key schema.
- Android-only keys restore on Android.
- iOS-only or unsupported keys are ignored on Android.
- Payment, entitlement, URL, debug, and internal migration keys are never restored.

Required iOS adapter tests:

- JSON `keyboard_theme: 4` restores to App Group `UserDefaults` as an integer.
- JSON `smart_chinese_input: false` restores as a boolean.
- JSON `keyboard_size: "1"` restores as a string.
- Unknown keys are ignored.
- Wrong-type values are ignored.
- Out-of-range numeric values are ignored or clamped according to the key schema.
- Android-only keys are ignored on iOS.
- iOS-only supported keys restore on iOS.
- Payment, entitlement, URL, debug, and internal migration keys are never restored.

### Android Same-Platform E2E

Rewrite/extend the existing Android integrated backup test in:

```text
LimeStudio/app/src/androidTest/java/net/toload/main/hd/IntegrationTestBackupRestore.java
```

The Android same-platform E2E test should:

1. Set representative Android preferences before backup:
   - shared cross-platform keys, such as `keyboard_theme`, `keyboard_size`, and `smart_chinese_input`;
   - at least one Android-only key, such as `physical_keyboard_sort`.
2. Run the real full-backup path through `SetupImController.performBackup(Uri)` or the nearest production controller path.
3. Assert the backup archive contains:
   - `databases/lime.db`;
   - legacy `shared_prefs.bak`;
   - `preferences/lime_prefs.json`.
4. Read `preferences/lime_prefs.json` from the archive and assert canonical JSON values:
   - numeric semantic prefs are JSON integers;
   - boolean prefs are JSON booleans;
   - string prefs are JSON strings.
5. Mutate or clear those preferences.
6. Run the real full-restore path through `SetupImController.performRestore(Uri)`.
7. Assert Android preferences were restored through the compatibility adapter.
8. Assert the existing database checks still pass, including IM list and record-count restoration.

This test must preserve old Android backup behavior by keeping legacy `shared_prefs.bak` in the archive.

### iOS Same-Platform E2E

Port the Android E2E shape to iOS in:

```text
LimeIME-iOS/LimeTests/DBServerTest.swift
```

The iOS same-platform E2E test should:

1. Set representative iOS preferences before backup:
   - shared cross-platform keys, such as `keyboard_theme`, `keyboard_size`, and `smart_chinese_input`;
   - at least one iOS-supported per-IM key if included in the schema, such as `restore_on_import_phonetic`.
2. Run the real full-backup path through `DBServer.backupDatabase(uri:)` using an injected temp `LimeDB` where possible.
3. Assert the backup archive contains:
   - `databases/lime.db` after the database layout alignment is implemented;
   - legacy iOS `shared_prefs.bak` if same-platform fallback remains enabled;
   - `preferences/lime_prefs.json`.
4. Read `preferences/lime_prefs.json` from the archive and assert canonical JSON values.
5. Mutate or clear those preferences.
6. Run `DBServer.restoreDatabase(srcFilePath:)` or `DBServer.restoreDatabase(uri:)`.
7. Assert iOS preferences were restored through the compatibility adapter.
8. Assert database restore still succeeds and upgrade/repair hooks still run where applicable.

The iOS E2E test should be a real assertion test, not a smoke test that only verifies no crash.

### Cross-Platform Fixture E2E

Cross-platform restore should be tested with checked-in or generated fixture ZIP archives. The two platforms do not need to invoke each other's test runners.

Recommended fixture location:

```text
.Codex/txt/pref_backup_restore_fixtures/
```

If fixtures become stable long-term regression assets, move them to an existing test-resource location for each platform instead of keeping them under `.Codex/txt/`.

Required Android-to-iOS fixture test:

1. Build an Android-style full backup ZIP containing:
   - `databases/lime.db`;
   - legacy Android Java-serialized `shared_prefs.bak`;
   - `preferences/lime_prefs.json`.
2. Include shared prefs in JSON, such as:
   - `keyboard_theme: 4`;
   - `keyboard_size: "1"`;
   - `smart_chinese_input: false`.
3. Include at least one Android-only key, such as `physical_keyboard_sort`.
4. Restore the fixture on iOS.
5. Assert shared keys restore into iOS `UserDefaults`.
6. Assert Android-only keys are ignored without failing restore.
7. Assert iOS does not need to parse the Android Java-serialized `shared_prefs.bak`.

This test must be skipped, not failed, when the test runner is not macOS or when `xcodebuild` is unavailable. Linux/Windows CI should still run Android-side adapter tests and any pure fixture-generation checks that do not require Xcode.

Required iOS-to-Android fixture test:

1. Build an iOS-style full backup ZIP containing:
   - `databases/lime.db`;
   - legacy iOS plist `shared_prefs.bak`;
   - `preferences/lime_prefs.json`.
2. Include shared prefs in JSON, such as:
   - `keyboard_theme: 4`;
   - `keyboard_size: "1"`;
   - `smart_chinese_input: false`.
3. Include at least one iOS-only or unsupported key if the schema defines one.
4. Restore the fixture on Android.
5. Assert shared keys restore into Android `SharedPreferences`.
6. Assert canonical JSON integer values are converted to Android storage strings where required.
7. Assert iOS-only or unsupported keys are ignored without failing restore.
8. Assert Android does not need to parse the iOS plist `shared_prefs.bak` when valid JSON exists.

The fixture itself may be generated on macOS, but Android restore verification must not require Xcode at runtime. On non-macOS hosts, run this as an Android-only restore test using a checked-in or generated JSON manifest fixture; skip only the iOS/Xcode-producing half.

### Legacy Fallback E2E

Required legacy fallback tests:

- Android restores an old Android backup with only legacy Java `shared_prefs.bak` and no `preferences/lime_prefs.json`.
- iOS restores an old iOS backup with only plist `shared_prefs.bak` and no `preferences/lime_prefs.json`.
- Android ignores malformed JSON and falls back to legacy Java `shared_prefs.bak`.
- iOS ignores malformed JSON and falls back to legacy plist `shared_prefs.bak`.
- If preference restore fails, database restore still completes.

## Non-Goals

- Do not put preferences in `.limedb` table exports.
- Do not make iOS parse Android Java serialization unless a later explicit requirement is approved.
- Do not change the legacy `shared_prefs.bak` payload format in-place.
- Do not restore arbitrary SharedPreferences/UserDefaults keys.
- Do not use preference backup for licenses, payments, secrets, server configuration, or debug state.
