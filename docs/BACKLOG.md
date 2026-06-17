# LIME IME Backlog

Public backlog for confirmed pending fixes, active retest watches, and new-feature/product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; mutable automation state stays outside the repo.

Last reviewed: 2026-06-17

## Active issue follow-up

- #115 Android: reporter `gontera` gave a negative/partial retest on APK `LIMEHD2026-6.1.20.apk` (blob SHA `cbe1ff21ab7a499eef952c702ee5eb0a40131c05`, size 14053640 bytes) after PR #116 / commit `976465e8057d8ca9aa66ceb2159c8ae74945241c` addressed the first stale-snapshot path. Reporter result: https://github.com/lime-ime/limeime/issues/115#issuecomment-4716038267 says Problems 1-2 still reproduce but improved because `EN` -> `中` now restores the correct keyboard without restarting the target app; the attached manual Array10 `.lime` default-keyboard path currently looks normal. Follow-up PR #118 merged to `master` as `676f9b4d50c398126ff7489d48e7db83727a58c2`, with direct #115 fix commit `e984c4c1432ea1efd1996b69285cafe425e6b22c`. Android APK `LIMEHD2026-6.1.21.apk` (blob SHA `a8838c47b4186956536cd4c8aa4e3931d579d1da`, size 14055188 bytes) now contains PR #118, so #115 was reopened and a scoped Problems 1-2 retest request was posted at https://github.com/lime-ime/limeime/issues/115#issuecomment-4726813753. Keep Problem 3 as watch-only unless it recurs. Audit iOS only if shared `.lime` import semantics change.
- #111 was closed by `limeimetw` after acknowledgement; the confirmed `scj` table-data correction remains tracked under pending fixes until a rebuilt table/artifact decision is made.

## Unfiled release-QA follow-up

- Unify full database backup ZIP filenames across Android and iOS. Android currently defaults to `limeBackup.zip`, while iOS creates `lime_backup_<timestamp>.zip`; choose one user-facing naming convention for DB Manager backup/restore docs, QA, and support.

## Pending fixes

- #111 Android/iOS: correct or regenerate the shared `scj` / `快倉` downloadable table data so the one-letter `x` and `z` codes no longer surface `1991` as the default/leading candidate.

