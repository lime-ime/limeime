# LIME IME Backlog

Public backlog for confirmed pending fixes, active retest watches, and new-feature/product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; mutable automation state stays outside the repo.

Last reviewed: 2026-06-18

## Active issue follow-up

- #111 was closed by `limeimetw` after acknowledgement; the confirmed `scj` table-data correction remains tracked under pending fixes until a rebuilt table/artifact decision is made.
- #119 is a maintainer-created Android/iOS text-import keyboard-layout bug. Analysis doc `docs/#119_ISSUE.md` tracks the source-backed investigation. No public acknowledgement or community retest request is needed until a fix reaches a build.
- #121 is a maintainer-created iOS cloud-installed IM first-switch layout/mode sync bug. Analysis doc `docs/#121_ISSUE.md` tracks the source-backed investigation. No public acknowledgement or community retest request is needed until a TestFlight/App Store build contains a targeted fix or maintainer verification closes it.

## Unfiled release-QA follow-up

- Unify full database backup ZIP filenames across Android and iOS. Android currently defaults to `limeBackup.zip`, while iOS creates `lime_backup_<timestamp>.zip`; choose one user-facing naming convention for DB Manager backup/restore docs, QA, and support.

## Pending fixes

- #111 Android/iOS: correct or regenerate the shared `scj` / `快倉` downloadable table data so the one-letter `x` and `z` codes no longer surface `1991` as the default/leading candidate.
- #119 Android/iOS: make `.lime` / `.cin` text import assign explicit intended keyboard layouts for known IMs, with iOS writing a keyboard config row after text import and Android making `scj`/`pinyin` mapping intent explicit.
- #121 iOS: fix first activation after cloud/download IM install so runtime language mode, active IM, and visible layout stay synchronized; no Android APK retest applies.
