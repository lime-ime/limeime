# LIME IME Backlog

Public backlog for confirmed pending fixes, active retest watches, and new-feature/product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; mutable automation state stays outside the repo.

Last reviewed: 2026-06-19

## Active issue follow-up

- #119 is a maintainer-created Android/iOS text-import keyboard-layout bug. Source fix is on `master` in commit `66c2b88aede9c1d988a3f76d94af3586c0d8eec3` after PR #120 was closed unmerged because branch `fix/119-import-default-keyboards` was merged directly. No public acknowledgement or community retest request is needed; close after maintainer/local verification or after a build containing the fix is available and verified.

## Source fixed / awaiting build or maintainer verification

- #119 Android/iOS: `.lime` / `.cin` text import now has explicit intended keyboard layouts for known IMs on `master`, with iOS writing a keyboard config row after text import and Android making `scj`/`pinyin` mapping intent explicit. No newer Android APK has been observed after `LIMEHD2026-6.1.21.apk`, so Android APK verification and iOS TestFlight/App Store delivery remain pending.
- #121 iOS: cloud/download IM first-switch sync fix landed on `master` in merge commit `e3aef89cca52b08fd48d68105dce2fe0042f0f19` and the maintainer-created issue is closed. Remaining validation is iOS unit/simulator/device and TestFlight/App Store release QA; no Android APK retest applies.

## Unfiled release-QA follow-up

- Unify full database backup ZIP filenames across Android and iOS. Android currently defaults to `limeBackup.zip`, while iOS creates `lime_backup_<timestamp>.zip`; choose one user-facing naming convention for DB Manager backup/restore docs, QA, and support.

## Pending fixes

- None currently tracked.
