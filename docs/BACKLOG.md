# LIME IME Backlog

Public backlog for confirmed pending fixes, active retest watches, and new-feature/product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; mutable automation state stays outside the repo.

Last reviewed: 2026-06-20

## Active issue follow-up

- #124 Android: composing/root-key and reverse-lookup floating popups can cover bottom chat-app message input fields when using Array input. PR #125 merged the scoped reverse-lookup timeout/alignment fix to `master` as commit `c7a0959fbe316cb432629bb181ca6ef700ca6983`; keep this active because broader composing/root-key safe-area placement remains pending in PR #126, no newer Android build is available for reporter retest, and manual LINE/WeChat/Instagram device verification is still needed.

## Source fixed / awaiting build verification

- #119 Android/iOS: `.lime` / `.cin` text import now has explicit intended keyboard layouts for known IMs on `master`, with iOS writing a keyboard config row after text import and Android making `scj`/`pinyin` mapping intent explicit. Issue #119 is closed; no newer Android APK has been observed after `LIMEHD2026-6.1.21.apk`, so Android APK verification and iOS TestFlight/App Store delivery remain pending as release QA.
- #121 iOS: cloud/download IM first-switch sync fix landed on `master` in merge commit `e3aef89cca52b08fd48d68105dce2fe0042f0f19` and the maintainer-created issue is closed. Remaining validation is iOS unit/simulator/device and TestFlight/App Store release QA; no Android APK retest applies.

## Unfiled release-QA follow-up

- Unify full database backup ZIP filenames across Android and iOS. Android currently defaults to `limeBackup.zip`, while iOS creates `lime_backup_<timestamp>.zip`; choose one user-facing naming convention for DB Manager backup/restore docs, QA, and support.

## Pending fixes

- None currently tracked.
