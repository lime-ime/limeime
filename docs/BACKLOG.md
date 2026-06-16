# LIME IME Backlog

Public backlog for confirmed pending fixes, active retest watches, and new-feature/product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; mutable automation state stays outside the repo.

Last reviewed: 2026-06-16

## Active issue follow-up

- #115 Android: active initial keyboard/layout startup bug, now pending reporter retest on APK `LIMEHD2026-6.1.20.apk` (blob SHA `cbe1ff21ab7a499eef952c702ee5eb0a40131c05`, size 14,053,640 bytes). On a fresh install, tested first mounted non-`注音` IMs (`倉頡`, `大易`, `行列`, `行列10`) can first open in a mismatched English-looking layout with the `EN` switch key; after adding a second IM, any active IM including `注音` can show the same wrong first keyboard. PR #116 / merge commit `0a03fcca34fd` fixes the stale startup snapshot cause by making IM keyboard-assignment DB writes invalidate startup config, with automated regression coverage for DB invalidation and email-first then normal-text startup refresh. Retest request: https://github.com/lime-ime/limeime/issues/115#issuecomment-4715747519. Keep the reporter's attached manually imported Array10 `.lime` default-layout path under watch during retest; audit iOS only if shared `.lime` import semantics change.
- #111 was closed by `limeimetw` after acknowledgement; the confirmed `scj` table-data correction remains tracked under pending fixes until a rebuilt table/artifact decision is made.

## Unfiled release-QA follow-up

- Unify full database backup ZIP filenames across Android and iOS. Android currently defaults to `limeBackup.zip`, while iOS creates `lime_backup_<timestamp>.zip`; choose one user-facing naming convention for DB Manager backup/restore docs, QA, and support.

## Pending fixes

- #111 Android/iOS: correct or regenerate the shared `scj` / `快倉` downloadable table data so the one-letter `x` and `z` codes no longer surface `1991` as the default/leading candidate.

