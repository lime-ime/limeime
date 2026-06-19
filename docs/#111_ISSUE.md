# Issue #111: Fast Cangjie catalog feedback and `x`/`z` candidate data anomaly

## Problem statement

Reporter `ejmoog` opened [issue #111](https://github.com/lime-ime/limeime/issues/111) with two kinds of feedback:

1. Product/catalog feedback about the LIME IME page / IM download list ordering and whether the legacy `快倉` table should remain presented alongside current Cangjie-family tables.
2. A concrete data-quality report that, after installing/using the Android `快倉` (`scj`) table, pressing `x` or `z` can produce `1991` as the leading candidate.

The `x`/`z` report is reproducible from the legacy `scj` table data: rows where `code = 'x'` and `code = 'z'` both map to `word = '1991'` sort before the punctuation/symbol rows in a default `ORDER BY score DESC` query because all shown rows have `score = 0` and `basescore = 0`.

## Observed evidence

- Live issue: https://github.com/lime-ime/limeime/issues/111
- Current live state checked after closure: closed by `limeimetw`, labeled `bug`, `enhancement`, and `Usability`, assigned to `jrywu`.
- Reporter comment: https://github.com/lime-ime/limeime/issues/111#issuecomment-4697232328
- Maintainer acknowledgement/closure comment: https://github.com/lime-ime/limeime/issues/111#issuecomment-4697297136
- Legacy `scj` table data checked.
- Metadata rows identify the table as `快倉輸入法` with keyboard `LIME+數字列鍵盤` / `limenum`.
- Verification query run locally against the legacy `scj` table:
  - `SELECT code, word, score, basescore FROM scj WHERE code IN ('x','z') ...`
  - Result included `x -> 1991` and `z -> 1991` as the first displayed rows.
- Android IM download/catalog code currently lists `四碼倉頡` (`cj4`, with variant label `哈哈倉頡`) before `快倉` in `LimeStudio/app/src/main/java/net/toload/main/hd/ui/view/ImInstallFragment.java`; the broader catalog-order / removal request is therefore a product/catalog decision, not proven to be the same as the data-quality bug.

## Likely root cause

The most likely cause of the concrete `x`/`z` symptom is bad or obsolete source data inside the legacy `scj` table rather than a general candidate-selection regression. The database contains explicit `1991` rows for both one-letter codes.

The broader request to remove `快倉`, rename/reposition Cangjie-family entries, or replace a visible slot with `哈哈倉頡` is product/catalog curation work. It should be decided separately from the confirmed `scj` data anomaly.

## Proposed solution

### Confirmed bug/data fix scope

- Audit the legacy `scj.zip` cloud artifact and current `scj.limedb` generation for one-letter `x` and `z` rows.
- Remove or demote the anomalous `1991` rows if they are not intentional table entries.
- Rebuild the downloadable `scj` artifact(s) and verify Android import/search ordering no longer presents `1991` as the default candidate for `x` / `z`.
- If iOS uses the same `scj.limedb` catalog artifact, verify the corrected artifact is consumed the same way there.

### Product/catalog scope

- Decide separately whether `快倉` should stay listed, be renamed, be moved, or be deprecated/hidden.
- Decide whether `四碼倉頡` / `哈哈倉頡` should be presented more prominently than the legacy `快倉` family in every surface where Cangjie-family tables appear.
- Do not treat the reporter's preference for removing `快倉` as a confirmed product direction until Jeremy/maintainer confirms it.

## Follow-up questions

For the maintainer/product decision:

1. Should the immediate fix only remove/demote the `1991` rows from `scj`, or should `快倉` be deprecated/hidden from public download surfaces?
2. If `快倉` remains available, should the UI text explain that it is a legacy table distinct from `哈哈倉頡`?

The public follow-up pattern is already set by the closure comment: do not ask the reporter to retest now; update the issue only if a later table/artifact change needs reporter confirmation.

No additional reporter evidence is needed to confirm the `x`/`z` data anomaly because the current repository database already reproduces the reported candidate.

## Verification plan

- Query the fixed `scj` table and confirm `code = 'x'` and `code = 'z'` no longer return `1991` as the leading candidate.
- Import/download the rebuilt `scj` table on Android and verify pressing `x` / `z` no longer commits or highlights `1991` by default.
- If iOS exposes/downloads the same `scj` table, install/update the table through iOS Settings and verify the same query behavior.
- Confirm `哈哈倉頡` (`cj4`) catalog entries and the existing `快倉` (`scj`) entries still install successfully after any catalog/data change.

## Platform impact analysis

### Android

Confirmed impact path. Android historically downloaded `scj.zip` from the cloud catalog; the maintained artifact is now `scj.limedb`. After import it becomes the `scj` table and drives candidate lookup. The legacy `scj` data contains the reported `x`/`z` -> `1991` rows, so Android users who install/use `快倉` can see the bad candidate.

The Android install list already places the `四碼倉頡` family (`cj4`, variant label `哈哈倉頡`) before `快倉` in `ImInstallFragment.java`; any remaining ordering complaint may refer to another public page or screenshot surface and needs product/site verification before changing Android UI order.

### iOS

Potentially affected if iOS users install/use the same `scj.limedb` artifact through the iOS IM catalog. `LimeIME-iOS/LimeSettings/IMCatalog.swift` includes an `scj` family entry for `快倉`, so the table-data anomaly may carry over after iOS import even though the reporter only showed Android-style screenshots.

The issue should therefore verify both Android and iOS install/import paths if the shared `scj` artifact is corrected. No iOS-specific runtime bug is confirmed yet.

## Follow-up status

- 2026-06-13: Live issue and comment inspected. Legacy `scj` table data verified to contain `x -> 1991` and `z -> 1991`. Classified as a confirmed table-data bug plus separate catalog/product feedback. No public retest request is appropriate until a rebuilt table/APK is available.
- 2026-06-13: `limeimetw` posted a short acknowledgement that the `x`/`z` -> `1991` data issue and broader `快倉` follow-up will be discussed with the author team, then closed the issue: https://github.com/lime-ime/limeime/issues/111#issuecomment-4697297136. Treat the public issue as closed with no active reporter retest watch; keep the confirmed `scj` table-data correction in the backlog until a rebuilt table/artifact decision is made.
