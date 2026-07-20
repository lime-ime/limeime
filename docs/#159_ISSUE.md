# Issue #159: Android 三碼 installation registers an unusable IM configuration

## Status

Open. The reporter's Android v6.1.35 retest failed on two phones. The catalog entry and mapping import appear to complete, but the installed 三碼 input method opens with the ordinary keyboard and accepts English only. Android needs a confirmed fix and a new reporter-testable build. The iOS catalog remains separate unverified product work.

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

## Likely root cause

The exact cause is not yet proven. The strongest shared suspect is the automatic registration/assignment boundary for the dedicated `tricode` table:

- Catalog `.limedb` import may import the metadata row but leave the runtime-selected IM or keyboard state stale.
- Dedicated-table text import may resolve `limenumsym2` too early or fail to persist the assignment, then silently use the generic `lime` fallback.
- IM activation may not refresh table-specific configuration after either import completes.

The fact that manual 自建 plus explicit keyboard selection works argues against a missing layout resource or invalid mapping database. A focused failing test should identify which registration value differs among catalog import, dedicated-table text import, and the working 自建 path before source behavior is changed.

## Proposed solution

1. Add an Android regression test that imports the shipped `tricode.limedb` into the `tricode` table and verifies persisted IM metadata, especially the keyboard assignment `limenumsym2`, `imkeys`, and installed full-name row.
2. Add a corresponding text-import test for the dedicated `tricode` slot.
3. Compare both results with the working 自建 plus explicit-keyboard path, including the active table and keyboard configuration read by the IM service after import.
4. Reproduce the stale/fallback behavior RED before modifying production code.
5. Fix the narrow registration, assignment, or refresh boundary proven by the test. Do not special-case the keyboard renderer when the reporter has already shown that the renderer works.
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
- Produce a newer Android test APK and request a targeted retest on the reporter's Nokia 3.4 and Redmi 13C.

### iOS

The reporter supplied no iOS retest evidence. iOS has a separate implementation of the `tricode` catalog, default-keyboard mapping, metadata import, and `limenumsym2` registration. Before release, add equivalent import/registration assertions and verify phone, full-iPad, and narrow-iPad activation. Treat cross-platform impact as possible until an iOS test proves the automatic paths select the intended keyboard and enable Chinese composition.

## Privacy-safe reporter summary

A community reporter tested the Android v6.1.35 GitHub build on two phone models. The dedicated 三碼 catalog and coding-file import paths installed an entry but did not activate Chinese input or the intended keyboard. The same mapping worked through 自建 after manually selecting `LIME+數字符號鍵盤2`. No private support context or personal information is included here.
