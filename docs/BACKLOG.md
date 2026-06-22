# LIME IME Backlog

Public backlog for confirmed pending fixes, active retest watches, and new-feature/product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; mutable automation state stays outside the repo.

Last reviewed: 2026-06-22

## Active issue follow-up

- #124 Android: v6.1.23 includes the targeted LINE/WeChat/Instagram bottom-composer composing/root-key and reverse-lookup popup placement fixes. Retained comment https://github.com/lime-ime/limeime/issues/124#issuecomment-4761898236 tells the Google Play closed-test reporter to update from Google Play, and the issue was reopened with https://github.com/lime-ime/limeime/issues/124#issuecomment-4761963945. After the reporter uploaded videos, `limeimetw` posted https://github.com/lime-ime/limeime/issues/124#issuecomment-4766516641 asking whether the temporary reverse-lookup hint duration and placement are acceptable, or whether a shorter duration / further inward keyboard placement is preferred; the public comment describes disappearance as roughly five seconds, but source/tests still pin the timed lime-toast timeout to `1400` ms, so any longer observed duration should be clarified against the exact build/path. Keep #124 open pending that reply.

## Source fixed / awaiting build verification

- #128 Android: PR #132 merged to `master` as `e0659dac3670e42b0970cae54fdc7fd299c2a19e` and auto-closed the community issue. The fix replaces predefined keypress haptic effects with `VibrationEffect.createOneShot(...)` after Samsung Android 16 hardware showed predefined effects were dropped at the HAL despite framework success. Current APK metadata still points to `LIMEHD2026-6.1.23.apk` (blob SHA `7315b2d88bf13327d2f16343ddd2c8d1f843be84`, size 7,406,598 bytes), which predates PR #132; ask the reporter to retest only after a newer APK / Google Play build contains this merge.

- #119 iOS: `.lime` / `.cin` text import now has explicit intended keyboard layouts for known IMs, with iOS writing a keyboard config row after text import. Android source delivery is covered by newer APKs through v6.1.23; remaining release QA is iOS TestFlight/App Store delivery unless new Android evidence appears.
- #121 iOS: cloud/download IM first-switch sync fix landed on `master` in merge commit `e3aef89cca52b08fd48d68105dce2fe0042f0f19` and the maintainer-created issue is closed. Remaining validation is iOS unit/simulator/device and TestFlight/App Store release QA; no Android APK retest applies.

## Unfiled release-QA follow-up

- Unify full database backup ZIP filenames across Android and iOS. Android currently defaults to `limeBackup.zip`, while iOS creates `lime_backup_<timestamp>.zip`; choose one user-facing naming convention for DB Manager backup/restore docs, QA, and support.

## Pending fixes

- None currently tracked.
