# LIME IME Backlog

Public backlog for confirmed pending fixes, active retest watches, and new-feature/product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; mutable automation state stays outside the repo.

Last reviewed: 2026-06-21

## Active issue follow-up

- #127 Android: `快倉` root installation fails because the Android cloud catalog still points to missing `Database/scj.zip` while the maintained artifact is `Database/scj.limedb`. The source tree now restores `Database/scj.zip`, but the v6.1.23 APK was built before that artifact was merged and does not contain `scj.zip`; ask the reporter to retest only after a newer APK contains the restored artifact or a catalog/download fix.
- #128 Android: Samsung A55 report says `喜好設定 / 打字震動` is enabled but soft-key presses produce no vibration. Inspect the Android 12+ haptic feedback path and Samsung/One UI system-vibration gating, then ask the reporter to retest only after a newer APK contains a relevant haptic-feedback change.

## Source fixed / awaiting build verification

- #119 iOS: `.lime` / `.cin` text import now has explicit intended keyboard layouts for known IMs, with iOS writing a keyboard config row after text import. Android source delivery is covered by newer APKs through v6.1.23; remaining release QA is iOS TestFlight/App Store delivery unless new Android evidence appears.
- #121 iOS: cloud/download IM first-switch sync fix landed on `master` in merge commit `e3aef89cca52b08fd48d68105dce2fe0042f0f19` and the maintainer-created issue is closed. Remaining validation is iOS unit/simulator/device and TestFlight/App Store release QA; no Android APK retest applies.

## Unfiled release-QA follow-up

- Unify full database backup ZIP filenames across Android and iOS. Android currently defaults to `limeBackup.zip`, while iOS creates `lime_backup_<timestamp>.zip`; choose one user-facing naming convention for DB Manager backup/restore docs, QA, and support.

## Pending fixes

- None currently tracked.
