# Issue #91: CIN import changes same-code candidate order

## Current status

Resolved / reporter-confirmed fixed.

Community reporter `ejmoog` confirmed that Android APK `LIMEHD2026-6.1.16.apk` fixes the reported 哈哈倉頡 `vmi` same-code candidate order: https://github.com/lime-ime/limeime/issues/91#issuecomment-4633080682

The issue was closed by the reporter on 2026-06-05, and the closing acknowledgement is: https://github.com/lime-ime/limeime/issues/91#issuecomment-4633087289

Verified Android APK used for the retest request:

- File: `LimeStudio/app/release/LIMEHD2026-6.1.16.apk`
- Direct link: https://raw.githubusercontent.com/lime-ime/limeime/master/LimeStudio/app/release/LIMEHD2026-6.1.16.apk
- GitHub Contents blob SHA: `eb99705bc3f6a2668889e89c05f7d9914c574639`
- Size: 11983378 bytes

## Problem statement

Community reporter `ejmoog` reported that importing a `.cin` table could change the order of duplicate-code candidates from the order in the source file, even when Android's candidate/selection sorting preference (`啟動選取排序`) was off or had never been enabled.

Issue: https://github.com/lime-ime/limeime/issues/91

## Reported reproduction

1. Import the 哈哈倉頡 `.cin` file.
2. Enter code `vmi`.
3. In the source `.cin`, the same-code candidates are expected in this order: `狀`, `绒`, `戕`.
4. After import/use in LIME, the reported order was: `狀`, `戕`, `绒`.

Evidence: the issue includes a screenshot showing the `vmi` duplicate-code candidate order after import.

## Classification

Confirmed Android `.cin` import / candidate-ordering bug — fixed in APK `6.1.16` and closed after reporter confirmation.

This was distinct from a product request for learning-based sorting: the reporter explicitly said the selection sorting preference was disabled, so same-code candidates should remain stable in source-file order unless another enabled feature intentionally reorders them.

## Resolution status

Resolved / reporter-confirmed fixed. Implemented and merged to `master` via PR #101 (`43aa6c887d9eebf162891549d0ef04fca9b6fe50`). The Android test APK used for reporter confirmation was `LIMEHD2026-6.1.16.apk` in `LimeStudio/app/release/` (blob SHA `eb99705bc3f6a2668889e89c05f7d9914c574639`, size 11983378 bytes).

- Added regression coverage for duplicate-code `.cin` source order when selection sorting is disabled.
- Updated Android candidate query ordering so score/base-score priority applies only when sorting is enabled; sorting-disabled same-code exact matches fall back to `_id ASC` / source insertion order.
- GitHub auto-closed the community issue during the PR merge; Hermes reopened it and posted/edited a scoped retest request: https://github.com/lime-ime/limeime/issues/91#issuecomment-4624477607
- Reporter `ejmoog` tested APK `6.1.16` and confirmed that the 哈哈倉頡 `vmi` ordering is now correct: https://github.com/lime-ime/limeime/issues/91#issuecomment-4633080682
- Reporter `ejmoog` closed the issue after the `6.1.16` confirmation; Hermes added a `+1` reaction and posted the kept closing acknowledgement: https://github.com/lime-ime/limeime/issues/91#issuecomment-4633087289
- Current follow-up: none. Treat #91 as closed/resolved for the Android `.cin` same-code ordering scope unless it is reopened or new ordering evidence appears.

## Relevant code observed

Android import path:

- `LimeStudio/app/src/main/java/org/limeime/limedb/LimeDB.java`
  - `.cin` files are detected by file extension in `importTxtTable(...)`.
  - `%chardef begin/end` lines are parsed, `code` and `word` are inserted into the IM table, and rows are inserted with `db.insert(table, null, cv)`.
  - If no explicit score/base score is provided, `score` defaults to `0`, while `basescore` is populated from `getBaseScore(word)`.

Android candidate query path:

- `LimeDB.getMappingByCode(...)` builds an `ORDER BY` clause with `_id ASC` as the final tie-breaker.
- Before the fix, even when the `sort` preference was false, fixed score/base-score priority terms could still run before `_id ASC` for exact single-character candidates.
- For the reported `vmi` same-code / single-character candidates, those score/base-score terms could differentiate candidates before `_id ASC`, causing the displayed order to diverge from source-file order even though selection sorting was disabled.

## Root cause / implementation notes

PR #101 resolved the Android ordering path by ensuring score/base-score priority is applied only when candidate sorting is enabled. With sorting disabled, same-code exact matches now fall back to `_id ASC` / source insertion order, preserving the `.cin` file order for the reported case.

The fix added focused regression coverage for duplicate-code `.cin` import order using the reported `vmi` / `狀`, `绒`, `戕` pattern. Learned/user score behavior remains intended only when sorting or explicit selection/learning paths apply.

## Platform scope

- Verified: Android `.cin` import/candidate ordering for the reporter's 哈哈倉頡 `vmi` same-code case on APK `6.1.16`.
- Not separately verified by this issue: unrelated `.cin` files, existing tables with learned records, or iOS import/query behavior.

## Remaining non-watch QA

No routine follow-up is needed after the reporter-confirmed APK `6.1.16` fix. If the issue is reopened or a new ordering failure is reported, ask for:

- the exact 哈哈倉頡 `.cin` source file/version they imported;
- whether the table was imported into a clean custom IM or over an existing table with learned records;
- the Android APK version used for the new screenshot/evidence.

## Verification result

- Issue state on GitHub: closed by reporter `ejmoog` after the APK `6.1.16` confirmation.
- Android regression coverage was added for `.cin` import source order with duplicate-code candidates and sorting disabled.
- Reporter manually verified the 哈哈倉頡 `vmi` / `狀 绒 戕` case on APK `6.1.16` and confirmed the ordering is correct.
- Verified scope: Android `.cin` import/candidate ordering for the reported same-code case with sorting disabled. This does not separately verify unrelated `.cin` files, existing tables with learned records, or iOS behavior.
- The confirming APK was `LIMEHD2026-6.1.16.apk`; verified blob SHA `eb99705bc3f6a2668889e89c05f7d9914c574639`, size 11983378 bytes.
