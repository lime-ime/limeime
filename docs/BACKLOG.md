# LIME IME Backlog

Public backlog for confirmed pending fixes, active retest watches, and new-feature/product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; mutable automation state stays outside the repo.

Last reviewed: 2026-06-16

## Active issue follow-up

- #115 Android: reporter retest requested for APK `LIMEHD2026-6.1.20.apk` (blob SHA `cbe1ff21ab7a499eef952c702ee5eb0a40131c05`, size 14053640 bytes) after PR #116 / commit `976465e8057d8ca9aa66ceb2159c8ae74945241c` addressed the stale initial IM keyboard snapshot path by invalidating startup config when IM keyboard assignments change. Retest request: https://github.com/lime-ime/limeime/issues/115#issuecomment-4715747519. Watch for `gontera` confirmation on directly targeted first-mounted `倉頡` / `大易` / `行列` / `行列10` and adding-a-second-IM paths; the attached manual Array10 `.lime` default-keyboard path remains in the retest checklist but may need a separate import-target/default-keyboard fix if still negative. Audit iOS only if shared `.lime` import semantics change. Do not close until reporter or maintainer confirmation.
- #111 was closed by `limeimetw` after acknowledgement; the confirmed `scj` table-data correction remains tracked under pending fixes until a rebuilt table/artifact decision is made.

## Unfiled release-QA follow-up

- Unify full database backup ZIP filenames across Android and iOS. Android currently defaults to `limeBackup.zip`, while iOS creates `lime_backup_<timestamp>.zip`; choose one user-facing naming convention for DB Manager backup/restore docs, QA, and support.

## Pending fixes

- #111 Android/iOS: correct or regenerate the shared `scj` / `快倉` downloadable table data so the one-letter `x` and `z` codes no longer surface `1991` as the default/leading candidate.

