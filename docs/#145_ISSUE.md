# Issue #145: Tablet bottom keyboard row can be covered

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/145
- Classification: `bug` + `Usability`
- Reporter: `james631025`
- Live state: closed as completed on 2026-07-18 after the reporter said the bottom-row clipping had not recurred during several days of testing. Labels remain `bug` + `Usability`, and `jrywu` remains assigned.
- Public acknowledgement / clarification request: https://github.com/lime-ime/limeime/issues/145#issuecomment-4874848760 asked for platform, version/device details, active layout, app scope, whether other actions besides switching IMEs restore the row, and screenshot evidence.
- Reporter follow-up: https://github.com/lime-ime/limeime/issues/145#issuecomment-4880870984 confirms Android tablet, LIME 6.1.27, Android 10, iPlay 30, 嘸蝦米, all apps, rotation also restores the row, and includes a screenshot.
- Maintainer/project-account follow-up: https://github.com/lime-ime/limeime/issues/145#issuecomment-4884643621 asked which Boshiamy keyboard layout is active, such as standard keyboard, phone keyboard, or another custom/special layout.
- Latest reporter reply: https://github.com/lime-ime/limeime/issues/145#issuecomment-4884708378 says the active Boshiamy layout is the standard keyboard and the reporter has not changed special settings. The attached settings screenshots show keyboard style set to system setting, keyboard size `一般`, font size `一般`, direction keys `無`, split keyboard `關閉`, and physical-keyboard auto-hide enabled.
- Local confirmation: API 29 Android Studio emulator looked fine with 3-button navigation, while the bottom-row coverage reproduced with gesture navigation.
- Release / retest request: v6.1.28 includes the Android tablet / gesture-navigation bottom-row clipping fix. Earlier retest comment URLs recorded during overlapping release closeout are no longer live; the retained reporter retest request is https://github.com/lime-ime/limeime/issues/145#issuecomment-4917044757.
- Reporter confirmation: https://github.com/lime-ime/limeime/issues/145#issuecomment-5011056009 says the problem did not recur during several days of testing after the v6.1.28 retest request. The comment does not restate the installed version or distribution channel, so verification is scoped to the reporter's Android tablet path rather than broad release coverage.
- Closing acknowledgement: https://github.com/lime-ime/limeime/issues/145#issuecomment-5011058938. The issue was closed as completed on 2026-07-18.
- Current state: the Android 10 / API 29 gesture-navigation fix shipped in GitHub APK v6.1.28, and the reporter subsequently observed no recurrence on the reported tablet path. The 2026-07-08 source fix gates LIME's forced IME edge-to-edge opt-in to API 35+ only, so pre-35 gesture navigation keeps the system-managed nav-bar fit behavior.

## Problem statement

The reporter says that on an Android tablet, the bottom row of the keyboard is often hidden or covered, and the workaround is to switch away to another input method and then switch back to LIME. A later comment confirms that rotating the tablet also restores the row. Local testing narrowed this to gesture navigation: Android API 29 with 3-button navigation is fine, while gesture navigation can cover the bottom row before the fix.

The reporter has now identified the platform as Android tablet, specifically iPlay 30 / Android 10 / LIME 6.1.27 while using 嘸蝦米. Keep iOS/iPadOS bottom-content coverage as related context only, not the active #145 platform path, unless a separate iPad report appears.

## Reporter evidence

- Initial report: the lowest keyboard row containing Space is often covered on a tablet, and switching to another input method and back makes the row appear again.
- Follow-up details: Android tablet, LIME 6.1.27, Android 10, iPlay 30, 嘸蝦米, all apps, rotation restores the row.
- Screenshot evidence: the first attached image shows the LIME keyboard with the bottom functional row visibly at the lower screen edge and partially cut off/covered. Do not infer the exact Boshiamy layout from that screenshot alone.
- Latest layout/settings evidence: the reporter says the Boshiamy layout is standard keyboard and that they did not change special settings. The attached settings screenshots visibly show normal/general keyboard and font size, no direction keys, split keyboard disabled, and physical-keyboard auto-hide enabled.
- Local emulator evidence: Android API 29 with 3-button navigation is fine; gesture navigation reproduces the bottom-row coverage.

## Source evidence inspected

### Android input view and bottom inset path

- `LimeStudio/app/src/main/java/org/limeime/LIMEService.java`
  - `onCreateInputView()` inflates/returns the fixed candidate + keyboard container `mCandidateInInputView`.
  - For API 35+ (`VANILLA_ICE_CREAM`), it installs a `ViewCompat.setOnApplyWindowInsetsListener(...)` on `mCandidateInInputView` and applies `systemBars().bottom` as the container bottom padding.
  - `applyNavigationBarTheme()` previously called `WindowCompat.setDecorFitsSystemWindows(window, false)` on every API level, which forced Android 10 gesture navigation into an edge-to-edge IME window without the matching pre-35 bottom inset padding.
  - 2026-07-08 source fix: `shouldForceImeEdgeToEdge(...)` keeps both the forced edge-to-edge opt-in and the explicit bottom-inset listener on API 35+ only.
- `LimeStudio/app/src/main/res/layout/inputcandidate.xml`
  - The IME view is a vertical `CandidateInInputViewContainer` with `wrap_content` height, `fitsSystemWindows="true"`, an embedded candidate strip, and a `LIMEKeyboardView` with `wrap_content` height and `layout_alignParentBottom="true"`.
- `LimeStudio/app/src/main/java/org/limeime/keyboard/LIMEKeyboardBaseView.java`
  - `onMeasure()` sets the keyboard view height to `mKeyboard.getHeight() + paddingTop + paddingBottom`.
- `LimeStudio/app/src/main/java/org/limeime/keyboard/LIMEBaseKeyboard.java`
  - Keyboard total height is computed from the XML row heights and vertical gaps.
  - Large/tablet resources use larger key heights, for example `values-large/dimens.xml` sets `key_height` to `60dip`, and `values-xlarge/dimens.xml` sets `key_height` to `84dip`.

### iOS/iPadOS comparison

- Existing issue #139 tracks an iOS TestFlight bottom-content coverage issue from a private reporter. Its active scope is host-app bottom content being covered by LIME's custom keyboard height/safe-area behavior.
- #145 is not the same active platform path as #139: the reporter confirmed Android tablet, and the described workaround is about the LIME keyboard row itself reappearing after IME switching or rotation.

## Root cause

#46's navigation-bar theming path forced the IME dialog window into edge-to-edge layout on every API level, but the matching explicit bottom inset padding only existed for API 35+. On Android 10 gesture navigation, that can place the bottom row behind the gesture bar. 3-button navigation is fine because it does not expose the same gesture-bar overlap.

The reporter is on Android 10, so the iOS custom-keyboard height/safe-area path is not the active #145 investigation path.

## Current fix

- `LIMEService.applyNavigationBarTheme()` now calls `WindowCompat.setDecorFitsSystemWindows(window, false)` only when `shouldForceImeEdgeToEdge(Build.VERSION.SDK_INT)` is true.
- `LIMEService.onCreateInputView()` uses the same predicate for the explicit bottom-inset listener, keeping edge-to-edge opt-in and inset compensation paired.
- `ImeEdgeToEdgePolicyTest` locks the policy: API 29 does not force IME edge-to-edge; API 35+ does.
- Local retest: Android API 29 gesture navigation no longer covers the bottom row after the fix.

## Verification plan

- Android manual verification on tablet-sized device/emulator:
  - Bottom row remains visible when LIME first appears.
  - Bottom row remains visible after switching away from and back to LIME.
  - Portrait/landscape, gesture navigation, and 3-button navigation do not cover the Space row.
  - Split keyboard and keyboard-size settings do not introduce bottom padding or height regressions.
- Automated regression: `./gradlew :app:testDebugUnitTest --tests net.toload.main.hd.ImeEdgeToEdgePolicyTest`.
- iOS/iPadOS: no #145 retest path unless separate matching iPad evidence appears.

## Platform impact

- Android: confirmed reporter platform. The bug is scoped to Android 10 tablet / iPlay 30 / LIME 6.1.27 / 嘸蝦米 standard keyboard, normal keyboard size, split keyboard off, and gesture navigation, with screenshot evidence and rotation/IME switching restoring the row.
- iOS/iPadOS: not implicated by #145. Existing #139 covers a related but separate iOS bottom-content coverage class.

## Follow-up / retest condition

- The reporter supplied Android tablet details, screenshot evidence, and the active Boshiamy standard-keyboard layout/settings. v6.1.28 was the first reporter-testable build containing this targeted gesture-navigation fix.
- After the v6.1.28 retest request, the reporter said the problem had not recurred during several days of testing. This closes the active reporter watch for the reported iPlay 30 / Android 10 / Boshiamy standard-keyboard path. The exact installed version and distribution channel were not restated in the confirmation comment.

`docs/BACKLOG.md` already stopped tracking `fix#145 Android` after the fix shipped in v6.1.28, so no backlog edit is needed for this closure. Reopen or start a new focused investigation only if the reporter or another user supplies recurrence evidence.
