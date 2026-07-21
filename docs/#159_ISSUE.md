# Issue #159: Android 三碼 installation registers an unusable IM configuration

## Status

Open. The reporter's Android v6.1.35 retest failed on two phones. A focused catalog-import instrumentation test reproduced the stale startup configuration: the `limenumsym2` assignment is persisted, but `importDb(...)` does not invalidate the IME startup snapshot. The narrow Android fix now invalidates that snapshot after every valid table-import attempt, including partial/failure paths that may already have mutated the target, and passes RED/GREEN coverage. A complete candidate and reporter retest are still required. The iOS catalog remains separate unverified product work.

## Problem statement

Android v6.1.35 exposes 三碼 v.20260720.3 in the downloadable input-method catalog. On both tested phones, installing that catalog entry makes 三碼 appear in the installed IM list, but selecting it does not activate the intended `limenumsym2` keyboard or Chinese composition. The reporter sees an ordinary keyboard and can type only English.

The failure also occurs when the reporter removes the catalog-installed copy and imports the coding table into the dedicated 三碼 slot. In contrast, loading the same input method through the 自建 path and manually selecting `LIME+數字符號鍵盤2` displays the intended layout and permits Chinese input.

Source: reporter retest https://github.com/lime-ime/limeime/issues/159#issuecomment-5027623841 after the v6.1.35 request https://github.com/lime-ime/limeime/issues/159#issuecomment-5024835238.

## Confirmed reproduction evidence

Reporter-tested devices:

- Nokia 3.4
- Redmi 13C

Reported paths:

1. Download 三碼 from the built-in catalog, install it, and select the resulting 三碼 IM.
2. Observe that the ordinary keyboard appears and only English input works.
3. Remove the built-in 三碼 IM, import the coding file into the dedicated 三碼 slot, and observe the same failure.
4. Load it through 自建 instead, manually select `LIME+數字符號鍵盤2`, and observe the intended layout and working Chinese input.

The reporter did not include Android versions in this comment.

## Current implementation facts

- The v6.1.35 asset contains table version `20260720.3`.
- `ImInstallFragment` routes the catalog download to table code `tricode`.
- `Database/tricode.limedb` contains the expected `im` property rows, including `imkeys`, `imkeynames`, and a `keyboard` row whose keyboard code is `limenumsym2`.
- Android startup schema maintenance creates the `tricode` mapping table and seeds the global `limenumsym2` keyboard row.
- Text import maps `tricode` to `limenumsym2` through `getDefaultKeyboardCodeForImportedIM()` and falls back to `lime` if the requested keyboard row cannot be resolved.
- Manual 自建 selection proving that `LIME+數字符號鍵盤2` can render and type narrows the defect away from the layout XML and basic mapping content. The failed automatic paths instead point toward table-specific IM registration, keyboard assignment, or activation state.

## Root cause

The catalog `.limedb` path imports the `tricode` rows and the `keyboard = limenumsym2` assignment
correctly, but `LimeDB.importDb(...)` previously left `startup_config_version` unchanged. The IME
therefore continued using the pre-import activated-IM/keyboard snapshot. This matches the screenshots:
Settings shows 三碼 installed and enabled, while LIME's internal switcher still lists only 注音.

A second import-path defect appeared when the failure-path regression ran immediately before the
success case: `tableColumns(...)` used `SELECT * FROM sourceDB.custom LIMIT 0`. Android's compiled
statement cache retained the previous attached database's column metadata after detach/reattach, so
the next catalog could be evaluated against stale schema columns. This is relevant to the reporter's
remove/reinstall sequence. Schema inspection now uses `PRAGMA sourceDB.table_info(...)`, which reads
the currently attached database.

The focused instrumentation test initializes a clean startup snapshot, imports a catalog-shaped
database into `tricode`, verifies the persisted `limenumsym2` row, and then fails on v6.1.35 because
the snapshot version remains nonzero. Resetting the startup configuration version after every valid
table-import attempt makes the same test pass and also covers failures after overwrite or partial
mutation. Running the forced-failure import followed by the valid catalog import also proves that
reattaching a different database no longer reuses stale schema metadata. Manual 自建 plus explicit keyboard selection
working remains consistent with this root cause and continues to argue against a layout or table-data
defect.

## Proposed solution

1. Keep the RED/GREEN catalog-import regression covering persisted `limenumsym2` plus startup-snapshot invalidation.
2. Add/retain the corresponding text-import coverage for the dedicated `tricode` slot, whose existing path already resets the startup snapshot after keyboard assignment.
3. Verify both paths against the working 自建 plus explicit-keyboard path, including the active table and keyboard configuration read by the IM service after import.
4. Keep the production change narrow: invalidate the startup snapshot after every non-empty valid-table `.limedb` import attempt, including failures after overwrite or partial mutation. Do not special-case the keyboard renderer.
5. Inspect attached schemas through `PRAGMA ... table_info(...)`, not cached `SELECT *` column metadata, and keep the failure-then-success sequence under test.
6. Verify removal followed by reinstall/import so stale rows from the failed v6.1.35 path cannot preserve the defect.

## Follow-up questions

If local instrumentation cannot reproduce the failure, ask the reporter only for the missing Android versions and a screen recording showing selection of 三碼 through the installed IM list. Do not ask them to repeat the same generic v6.1.35 test.

## Verification plan

### Android

- Install a clean v6.1.35-equivalent database state and download the built-in 三碼 v.20260720.3 asset.
- Confirm the installed IM is `tricode`, its keyboard assignment is `limenumsym2`, and its `imkeys` include letters plus `'`, `,`, `.`, `/`, and `;`.
- Activate 三碼 and verify the `lime_num_sym2` layout appears rather than the ordinary English keyboard.
- Verify `lh` produces the expected 三碼 candidate and each of the five symbol roots enters composition.
- Remove 三碼, import the coding file into the dedicated 三碼 slot, and repeat the same checks.
- Verify the existing 自建/manual-selection path remains working.
- Upgrade from a database that previously installed the broken v6.1.35 configuration and verify stale metadata is repaired or replaced.
- Confirm the focused catalog-import test passes after previously failing with a nonzero startup snapshot.
- Produce a newer Android test APK and request a targeted retest on the reporter's Nokia 3.4 and Redmi 13C.

### iOS

The reporter supplied no iOS retest evidence. iOS has a separate implementation of the `tricode` catalog, default-keyboard mapping, metadata import, and `limenumsym2` registration. Before release, add equivalent import/registration assertions and verify phone, full-iPad, and narrow-iPad activation. Treat cross-platform impact as possible until an iOS test proves the automatic paths select the intended keyboard and enable Chinese composition.

## Privacy-safe reporter summary

A community reporter tested the Android v6.1.35 GitHub build on two phone models. The dedicated 三碼 catalog and coding-file import paths installed an entry but did not activate Chinese input or the intended keyboard. The same mapping worked through 自建 after manually selecting `LIME+數字符號鍵盤2`. No private support context or personal information is included here.
