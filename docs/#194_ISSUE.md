# Issue #194: 哈哈倉頡 downloadable-table order regression

## Current status

Confirmed shared-data archive-generation defect. GitHub issue #194 is open, labeled `bug`, `enhancement`, and `Usability`, and assigned to `jrywu`.

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

The reporter's June `.lime` source itself has the correct order:

- line 7749: `j|都|25298|25298`
- line 7750: `j|十|25297|25297`

The rebuilt v6.1.36 archive instead contains:

- `_id 3397`: `j → 十`, score `25280`
- `_id 28161`: `j → 都`, score `25281`

This is a systematic transformation, not random row drift:

- The entire archive `_id` sequence is exactly `ORDER BY word ASC`; its first rows are punctuation ordered by output word.
- `score` is a complete unique sequence from 33,021 down to 1 and retains the source rank after the 17-row loss.
- Therefore `ORDER BY score DESC` reconstructs source order, while `ORDER BY _id ASC` reconstructs word order.

The historical rebuild path materialized a word-sorted record set into SQLite IDs while carrying source rank in `score`, instead of assigning IDs directly from the `.cin`/`.lime` line sequence. The exact external generation command was not checked into the repository, but the archive ordering proves this transformation occurred before the artifact was committed.

Sorting by score descending therefore happened to return `都`, `十`. Sorting-disabled lookup correctly ignores scores and returns IDs ascending: `十`, `都`. #91 exposed, rather than caused, the malformed #112 archive.

The old archive is also not a faithful serialization of the June source:

- June source mappings: 33,038
- old archive mappings: 33,021
- source pairs absent from archive: 172
- archive-only pairs: 155
- net row difference: 17

Many differences are `z`/難-key phrase mappings, but ordinary mappings differ too. This proves the old artifact came from a transformed or different table state, not merely a reordered copy of the submitted source.

### Archive index convention

All other downloadable mapping archives carry a single-column `code` index. The first PR #195 rebuild omitted that established index. The corrected builder creates `custom_idx_code ON custom(code)` after inserting mappings. For equal codes, that index retains rowid order, so the source-order candidate contract remains intact without platform import changes.

## Fix

1. Pin the exact final `20260723_082459` public attachment by SHA-256, version, and row count.
2. Rebuild `Database/hahacj.limedb` deterministically from that plaintext source.
3. Insert mappings with zero initial learned/base scores and source-file order as archive `_id` order.
4. Preserve `,.` Lime end keys from the previously approved #112 behavior.
5. Create the standard `custom_idx_code ON custom(code)` archive index.
6. Update Android/iOS catalog metadata to 33,044 mappings and 496 KB.
7. Keep the exact committed-archive runtime regression.
8. Gate every current and future `Database/*.limedb` update through the repository-wide source-order contract.

## Existing-install migration

Updating the app alone does not replace a 哈哈倉頡 table that the user already downloaded. Current Android and iOS catalog UIs treat an installed family as installed and do not automatically apply newer table bytes.

For #194 verification, existing users must remove/reinstall or re-download 哈哈倉頡 after installing the fixed build. The confirmed cross-platform one-step `更新碼表` details-page flow is tracked separately as `feat#N06`; it is not part of this defect.

Reporter-facing test instructions must explicitly include re-downloading 哈哈倉頡; otherwise an old local `cj4` table can make the fix appear ineffective.

## Repository-wide prevention gate

- `Database/limedb-order-contracts.json` registers every current `.limedb` archive.
- Existing archives without committed source material are grandfathered at their current hash. That hash is immutable across a PR/release baseline; changing it requires conversion to a source-backed contract.
- New archives may not be grandfathered. They must declare a committed source, source format, and mapping-table contract from their first revision.
- Source-backed archives are compared row-for-row as `(code, word)` in SQLite `_id ASC` order, in addition to ZIP and SQLite integrity checks.
- Pull requests and pushes touching `Database/**` or table builders/tests run the all-table gate against a fetched base commit.
- The release workflow is restricted to `master` and reruns the gate against the previous release tag before publishing.
- Invalid or unavailable base refs fail closed, and a source-backed contract cannot be removed or downgraded to a hash-only contract.

## Platform impact

### Android

Directly reproduced. The archive install path is `DBServer.importZippedDb` → `LimeDB.importDb`; candidate lookup uses `_id ASC` when sorting is disabled. The exact rebuilt archive regression verifies `j → 都, 十` through that runtime path.

### iOS

iOS downloads the same corrected archive and already uses `_id ASC` when sorting is disabled. No iOS database code changes are required. XCTest and iPhone/full-iPad/narrow-iPad runtime verification remain required on macOS/Xcode.

## Verification results

- Authoritative source hash/version/count test: passed.
- Archive/source exact row equality: 33,044 rows passed.
- SQLite `PRAGMA integrity_check`: `ok`.
- Archive index: `custom_idx_code ON custom(code)`.
- Rebuilt archive version: `20260723_082459`.
- Rebuilt `j` rows: `_id 7741 → 都`, `_id 7742 → 十`, both with zero score/base score.
- Archive size: 508,390 bytes (496.475 KiB).
- Exact rebuilt archive imported through `DBServer.importZippedDb` and queried through `SearchServer` with sorting disabled: `都`, `十` passed on Pixel 9 Pro API 36.
- Python 哈哈倉頡 regression suite: passed.
- Android unit tests and Android-test compilation: passed.
- Ponytail review removed speculative platform SQL/tests and aligned the archive with the existing `code` index and DEFLATE conventions.
- Claude Code review found one stale catalog-size blocker from an intermediate snapshot; both catalogs now match the final 508,390-byte archive at 496 KB. CI correctly retains semantic archive validation rather than cross-toolchain byte equality.
- macOS/Xcode XCTest and device-tier runtime checks remain before merge/reporter retest.

## Release/retest gate

Before asking the reporter to verify:

1. Produce a usable Android and/or iOS build containing the corrected archive.
2. On a clean install, download 哈哈倉頡 and verify `j → 都, 十` with sorting disabled.
3. On an existing installation, remove/re-download 哈哈倉頡, then repeat the same check.
4. Verify sorting-enabled learning still promotes selected candidates from the zero-score baseline.
5. State the required re-download step in the public retest request.
