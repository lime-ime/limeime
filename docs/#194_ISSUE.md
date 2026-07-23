# Issue #194: 哈哈倉頡 bundled candidate order regression

## Current status

Confirmed shared-data defect. GitHub issue #194 is open, labeled `bug`, `enhancement`, and `Usability`, and assigned to `jrywu`.

The ordering defect and the reporter's 20260723 table update are tracked together because the corrected downloadable database must be rebuilt from that updated source. No reporter retest should be requested until a usable Android or iOS build contains the corrected archive.

## Problem statement

LIME v6.1.36 can display broadly incorrect same-code candidate order for the downloadable 哈哈倉頡 table. The reporter's example enters code `j`: the source table requires `都`, `十`, but the app shows `十`, `都`. Source inspection reproduces that order when selection sorting is disabled.

The reporter also supplied 哈哈倉頡 source version `20260723_074910`, which adjusts `覷` and multiple phrases.

Issue: https://github.com/lime-ime/limeime/issues/194

## Reported reproduction and evidence

1. Install LIME v6.1.36 and download/select 哈哈倉頡.
2. Use the table with `啟動選取排序` disabled.
3. Enter `j`.
4. Expected source order: `都`, `十`.
5. Reported v6.1.36 order: `十`, `都`.

The issue includes a 1080 × 768 screenshot and the plaintext source `haha_20260723_074910.txt`.

## Known-good / failing boundary

| Boundary | Known-good | Failing |
|---|---|---|
| exact artifact | Android APK 6.1.16 for imported `.cin` ordering in #91 | Android/iOS v6.1.36 shared downloadable `hahacj.limedb` |
| packaged data | #91 fixture/source rows inserted in source order | archive created for #112 with `custom._id` unrelated to source order |
| install state | reporter imported a `.cin` table and disabled sorting | reporter installed 6.1.36 and used the downloadable 哈哈倉頡 table |
| user action | enter a duplicate code such as `vmi` | enter `j` and inspect `都` / `十` |
| process boundary | runtime query after `.cin` import | runtime query after `.limedb` catalog import |

The v6.1.36 release points at the same `hahacj.limedb` bytes present in its source tag. This is a source-data correctness defect, not stale artifact provenance.

## Confirmed root cause

`Database/hahacj.limedb` contains a zipped `cj4.db`. In the failing archive:

- `custom._id = 3397` is `j → 十`.
- `custom._id = 28161` is `j → 都`.
- `ORDER BY _id ASC` therefore returns `十`, `都`.

The plaintext 20260723 source places `j → 都` immediately before `j → 十`. Android `LimeDB.getMappingByCode(...)` and iOS `LimeDB.getMappingByCode(...)` intentionally use `_id ASC` as the final source-order fallback when selection sorting is disabled. The #91 code fix remains present on both platforms, but it cannot recover source order from an archive whose row IDs were already rebuilt in a different order.

This explains why #91 was reporter-confirmed fixed for direct `.cin` import in 6.1.16 while the later downloadable archive fails in 6.1.36.

## Existing coverage and gap

- Android has `cinImportPreservesDuplicateCodeOrderWhenSelectionSortDisabled`, which covers direct `.cin` import into a custom table.
- iOS has insertion-order and `.cin` import tests for the same `vmi` ordering contract.
- Neither test validates the committed downloadable `Database/hahacj.limedb` bytes against their plaintext source.
- The missing gate allowed a catalog archive with scrambled `_id` order to ship even though runtime query code was correct.

## Proposed solution

1. Keep the reporter-provided, CC BY 4.0 source in `Database/` as the reproducible source of the bundled archive.
2. Add a deterministic builder that inserts every mapping in exact source-file order and preserves the required `,.` Lime end keys.
3. Rebuild `Database/hahacj.limedb` from source version `20260723_074910`.
4. Add a regression that compares every archived `(code, word)` row ordered by `_id` with the plaintext source and explicitly checks `j → 都, 十`.
5. Update Android and iOS catalog record counts to 33,038.

## Platform impact

### Android

Confirmed affected by source and reporter evidence. Android downloads `Database/hahacj.limedb`, imports its `custom` rows, and uses `_id ASC` when selection sorting is disabled. Direct `.cin` import remains covered by #91 and is not the broken boundary in this report.

### iOS

Source-confirmed affected. iOS catalogs the same `hahacj.limedb` file and its query has the same sorting-disabled `_id ASC` fallback. The reporter did not separately identify the screenshot platform, so iPhone/iPad reporter-visible behavior remains to be verified after delivery.

## Follow-up questions

No additional information is required to fix the committed archive. If post-fix ordering still differs, ask which platform/device was used, whether `啟動選取排序` is enabled, and whether 哈哈倉頡 was re-downloaded after updating the app.

## Verification plan

1. Preserve RED output from the archive/source equality regression against the v6.1.36 archive.
2. Rebuild the archive and run the identical test GREEN.
3. Inspect the rebuilt SQLite rows and metadata, including all 33,038 mappings, source version, `limeendkey`, and `j → 都, 十` by `_id`.
4. Run relevant Python/resource tests and Android test compilation.
5. Import the exact archive and enter `j` with selection sorting disabled on Android.
6. Verify the same path on iPhone, full iPad, and narrow iPad when Xcode/device access is available.
7. Independently review the source, builder, archive test, catalog counts, issue analysis, and backlog entries.
8. Open a focused PR for Jeremy. Do not publish an APK/release or request reporter retest until a usable build contains the fix.

## Verification results on the issue branch

- RED: the archive/source equality test failed against the v6.1.36 archive at the first row and found 17 source rows absent from that archive.
- GREEN: the rebuilt archive matches all 33,038 source rows in exact `_id` order.
- SQLite `PRAGMA integrity_check`: `ok`.
- Rebuilt `j` rows: `_id 7741 → 都`, `_id 7742 → 十`, both with zero initial learned/base score.
- Archive metadata: version `20260723_074910`, `limeendkey` `,.`, and ten `cj4` metadata rows.
- Builder determinism: three consecutive builds produced the same SHA-256.
- Python regressions: 哈哈倉頡 archive tests, emoji database tests, iOS custom-IM resource contract, and iOS number-symbol layout contract passed.
- Android `testDebugUnitTest` and `compileDebugAndroidTestJavaWithJavac` passed.
- Full Android connected instrumentation passed on the Pixel 9 Pro API 36 emulator: 1,211 tests finished, zero failures, and eight skipped environment-gated tests.
- A fixture-gated Android runtime test imported the exact committed `hahacj.limedb` archive through `DBServer.importZippedDb`, selected `cj4`, disabled selection sorting, queried through `SearchServer`, and returned exact candidates `都`, `十` in that order. Xcode checks remain pending.
- Claude Code and Codex CLI review attempts were blocked by expired local OAuth sessions. A manual second-pass review checked the staged source/archive equality, metadata, platform import/query paths, catalog values, docs, and deterministic output before commit.
