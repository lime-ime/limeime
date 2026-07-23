# Issue #194: 哈哈倉頡 downloadable-table order regression

## Current status

Confirmed shared-data and import/export ordering defect. GitHub issue #194 is open, labeled `bug`, `enhancement`, and `Usability`, and assigned to `jrywu`.

The ordering defect and the reporter's final 20260723 table update stay in one scope because the corrected downloadable database must be rebuilt from that exact source. Do not request reporter retest until a usable build contains the fix.

Issue: https://github.com/lime-ime/limeime/issues/194

## Authoritative source

The reporter explicitly superseded the first attachment with:

- filename: `haha_20260723_082459.txt`
- source version: `20260723_082459`
- mappings: 33,044
- SHA-256: `c1ce0ddd185873597afa469a5156d76d71418867d2f8c8db4ab946839267abd9`

The checked-in `Database/hahacj-20260723.txt` must remain byte-identical to that public issue attachment. The earlier `20260723_074910` attachment is stale and must not be used.

## Reported reproduction

1. Install LIME v6.1.36 and download/select 哈哈倉頡.
2. Disable `啟動選取排序`.
3. Enter `j`.
4. Expected source order: `都`, `十`.
5. Reported v6.1.36 order: `十`, `都`.

The screenshot visually shows a landscape Android keyboard, but the reporter did not explicitly identify the device. Android is directly affected; iOS shares the same archive and equivalent query contract.

## Known-good / failing boundary

| Boundary | Known-good | Failing |
|---|---|---|
| direct text import | #91: `.cin` rows inserted in source order | not the failing path in #194 |
| downloadable archive | rebuilt archive whose `custom._id` follows source order | #112/v6.1.36 archive whose IDs and contents diverge from source |
| sorting disabled | runtime query falls back to `_id ASC` | malformed archive IDs expose the wrong order |
| platform | Android and iOS query code both preserve `_id` order | both consume the shared malformed `hahacj.limedb` |

## Rebuild timeline

| Date / release | Event | Archive consequence |
|---|---|---|
| 2026-05-24 | #84 added the first downloadable `cj4.limedb` | Predates the #91 ordering fix. |
| 2026-06-03 | #91 changed sorting-disabled duplicate candidates to follow source insertion/`_id` order | Reporter verified direct `.cin` order in Android 6.1.16 on 2026-06-05. |
| 2026-06-17 / 6.1.21 | #112 rebuilt/replaced the downloadable table as `hahacj.limedb` for the June table and Lime end keys | This post-#91 rebuild produced the malformed IDs/content later exposed by #194. |
| 6.1.21–6.1.36 | Every release tag carrying `hahacj.limedb` contains byte-identical archive content | The table was not rebuilt again before #194; 6.1.36 still carries the 6.1.21 artifact. |
| 2026-07-23 / PR #195 | Rebuilt from the final `20260723_082459` source with deterministic source-order IDs | Pending merge and platform delivery verification. |

Therefore, the regression is specifically tied to the **#112 database rebuild after #91**, not to a later query-code regression between 6.1.21 and 6.1.36.

## Deeper causal chain: #91 → #112 → #194

### #91 correctly fixed direct `.cin` import behavior

When selection sorting is disabled, candidate lookup must ignore learned/base scores and use source insertion order (`_id ASC`) for duplicate codes. #91 implemented and tested that contract on Android and iOS.

### #112 shipped an archive whose order lived in scores, not IDs

The v6.1.36 archive contains:

- `_id 3397`: `j → 十`, score `25280`
- `_id 28161`: `j → 都`, score `25281`

Sorting by score descending happens to return `都`, `十`, but sorting-disabled lookup correctly ignores those scores and returns IDs ascending: `十`, `都`. #91 therefore exposed, rather than caused, the malformed #112 archive.

The old archive is also not a faithful serialization of the June source:

- June source mappings: 33,038
- old archive mappings: 33,021
- source pairs absent from archive: 172
- archive-only pairs: 155
- net row difference: 17

Many differences are `z`/難-key phrase mappings, but ordinary mappings differ too. This proves the old artifact came from a transformed or different table state, not merely a reordered copy of the submitted source.

### Import/export also relied on implicit SQLite scan order

Both platforms copied mapping rows with `INSERT … SELECT` or `SELECT` statements lacking `ORDER BY`. SQLite often scans rowid tables by rowid, but SQL does not guarantee that. A covering index reproduces the latent failure: source IDs `10 → 都`, `20 → 十` are read as `十`, `都` unless `_id ASC` is explicit.

The Android emulator regression failed before the fix with expected `都` but actual `十`.

## Fix

1. Pin the exact final `20260723_082459` public attachment by SHA-256, version, and row count.
2. Rebuild `Database/hahacj.limedb` deterministically from that plaintext source.
3. Insert mappings with zero initial learned/base scores and source-file order as archive `_id` order.
4. Preserve `,.` Lime end keys from the previously approved #112 behavior.
5. Explicitly order mapping rows by source `_id ASC` on Android import/export and both iOS import paths/export.
6. Update Android/iOS catalog metadata to 33,044 mappings and 720 KB.
7. Keep runtime regressions for direct `.cin` import, indexed `.limedb` import, and the committed archive.

## Existing-install migration

Updating the app alone does not replace a 哈哈倉頡 table that the user already downloaded. Current Android and iOS catalog UIs treat an installed family as installed and do not automatically apply newer table bytes.

For #194 verification, existing users must remove/reinstall or re-download 哈哈倉頡 after installing the fixed build. Any automatic cloud-IM updater with learned-score backup/restore is separate product work and should not be added to this defect without its own migration design and tests.

Reporter-facing test instructions must explicitly include re-downloading 哈哈倉頡; otherwise an old local `cj4` table can make the fix appear ineffective.

## Platform impact

### Android

Directly reproduced. The archive install path is `DBServer.importZippedDb` → `LimeDB.importDb`; candidate lookup uses `_id ASC` when sorting is disabled. The indexed-source regression now proves import order explicitly.

### iOS

iOS downloads the same archive. `importFromAttachedDB`/`importDb` now order source rows by `_id`, and `getMappingByCode` uses `_id ASC` when sorting is disabled. A DB-level covering-index regression was added. XCTest and iPhone/full-iPad/narrow-iPad runtime verification remain required on macOS/Xcode.

## Verification results

- Authoritative source hash/version/count test: passed.
- Archive/source exact row equality: 33,044 rows passed.
- SQLite `PRAGMA integrity_check`: `ok`.
- Rebuilt archive version: `20260723_082459`.
- Rebuilt `j` rows: `_id 7741 → 都`, `_id 7742 → 十`, both with zero score/base score.
- Archive size: 737,390 bytes (720.107 KiB).
- Android covering-index import regression:
  - RED: expected `都`, actual `十`.
  - GREEN: Pixel 9 Pro API 36 targeted instrumentation passed after explicit `_id ASC` import.
- Exact rebuilt archive imported through `DBServer.importZippedDb` and queried through `SearchServer` with sorting disabled: `都`, `十` passed on Pixel 9 Pro API 36.
- Python 哈哈倉頡 regression suite: passed.
- Android unit tests and Android-test compilation: passed.
- Full Android connected instrumentation: 1,214 tests finished, zero failures, nine environment/fixture skips; the self-contained indexed-import regression ran and passed.
- Independent Hermes/Codex review found the stale source, existing-install gap, implicit-order gap, and missing iOS regression; those findings were incorporated.
- macOS/Xcode XCTest and device-tier runtime checks remain before merge/reporter retest.

## Release/retest gate

Before asking the reporter to verify:

1. Produce a usable Android and/or iOS build containing the corrected archive and import changes.
2. On a clean install, download 哈哈倉頡 and verify `j → 都, 十` with sorting disabled.
3. On an existing installation, remove/re-download 哈哈倉頡, then repeat the same check.
4. Verify sorting-enabled learning still promotes selected candidates from the zero-score baseline.
5. State the required re-download step in the public retest request.
