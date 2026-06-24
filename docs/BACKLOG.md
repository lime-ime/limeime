# LIME IME Backlog

Public backlog for confirmed pending fixes, active retest watches, and new-feature/product work. Issue-specific investigation details stay in `docs/#NN_ISSUE.md`; mutable automation state stays outside the repo.

Last reviewed: 2026-06-24

## Active issue follow-up

- #124 Android: v6.1.23 includes the targeted LINE/WeChat/Instagram bottom-composer composing/root-key and reverse-lookup popup placement fixes. Retained comment https://github.com/lime-ime/limeime/issues/124#issuecomment-4761898236 tells the Google Play closed-test reporter to update from Google Play, and the issue was reopened with https://github.com/lime-ime/limeime/issues/124#issuecomment-4761963945. After the reporter uploaded videos, `limeimetw` posted https://github.com/lime-ime/limeime/issues/124#issuecomment-4766516641 asking whether the temporary reverse-lookup hint duration and placement are acceptable, or whether a shorter duration / further inward keyboard placement is preferred; the public comment describes disappearance as roughly five seconds, but source/tests still pin the timed lime-toast timeout to `1400` ms, so any longer observed duration should be clarified against the exact build/path. The original reporter then proposed UX adjustments in https://github.com/lime-ime/limeime/issues/124#issuecomment-4779129986: place the reverse-lookup hint above the root display, show only lookup roots without repeating the committed character, and limit displayed lookup choices to the first or second option. Keep #124 open for maintainer/product decision on this remaining popup behavior. A later `01disney` comment reports a likely separate Android 16 / POCO F6 Pro / Boshiamy first-input IME auto-close symptom; handle that as a separate issue/follow-up unless maintainer evidence connects it to #124.

## Source fixed / awaiting build or release verification

- #128 Android: GitHub APK `LIMEHD2026-6.1.25.apk` now contains PR #133 (`6791f14a06047047c39dc53875ce2ebaebcf1327`), which restores the Google/Pixel API 31+ system keyboard-tap haptic path while preserving Samsung/raw-pulse routing, adds the Samsung keypress-sound volume preference, and maps stored vibration levels to stronger runtime pulses. Verified GitHub Contents blob SHA `11476f674bfb859704f834ba159caae8c137e19e`, size 7,407,192 bytes, downloaded SHA-256 `d89cfffaf2f252a4eb4570fa3ad95866ccb4018e921fe9aaae0c465e8a94e66f`. Remaining release QA: verify Pixel / Android 17 keypress vibration and sound on v6.1.25, and retest Samsung A17 stronger vibration levels plus the new `keypress_sound_volume` preference. The original Samsung A55 community reporter already confirmed the original issue fixed on v6.1.24, so no duplicate public reporter retest is needed unless new evidence appears.
- #119 iOS: `.lime` / `.cin` text import now has explicit intended keyboard layouts for known IMs, with iOS writing a keyboard config row after text import. Android source delivery is covered through GitHub APK v6.1.24, including the PR #131 metadata-preservation follow-up; remaining release QA is iOS TestFlight/App Store delivery unless new Android evidence appears.
- #121 iOS: cloud/download IM first-switch sync fix landed on `master` in merge commit `e3aef89cca52b08fd48d68105dce2fe0042f0f19` and the maintainer-created issue is closed. Remaining validation is iOS unit/simulator/device and TestFlight/App Store release QA; no Android APK retest applies.

## Unfiled release-QA follow-up

- Unify full database backup ZIP filenames across Android and iOS. Android currently defaults to `limeBackup.zip`, while iOS creates `lime_backup_<timestamp>.zip`; choose one user-facing naming convention for DB Manager backup/restore docs, QA, and support.

## Pending fixes

- None currently tracked.
