# Issue #145: Tablet bottom keyboard row can be covered

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/145
- Classification: `bug` + `Usability`
- Reporter: `james631025`
- Live state after the 2026-07-05 reporter reply: open, labeled `bug` + `Usability`, assigned to `jrywu`.
- Public acknowledgement / clarification request: https://github.com/lime-ime/limeime/issues/145#issuecomment-4874848760 asked for platform, version/device details, active layout, app scope, whether other actions besides switching IMEs restore the row, and screenshot evidence.
- Reporter follow-up: https://github.com/lime-ime/limeime/issues/145#issuecomment-4880870984 confirms Android tablet, LIME 6.1.27, Android 10, iPlay 30, 嘸蝦米, all apps, rotation also restores the row, and includes a screenshot.
- Maintainer/project-account follow-up: https://github.com/lime-ime/limeime/issues/145#issuecomment-4884643621 asked which Boshiamy keyboard layout is active, such as standard keyboard, phone keyboard, or another custom/special layout.
- Latest reporter reply: https://github.com/lime-ime/limeime/issues/145#issuecomment-4884708378 says the active Boshiamy layout is the standard keyboard and the reporter has not changed special settings. The attached settings screenshots show keyboard style set to system setting, keyboard size `一般`, font size `一般`, direction keys `無`, split keyboard `關閉`, and physical-keyboard auto-hide enabled.
- Current state: Android tablet UI/layout bug with screenshot and settings evidence. The report is now scoped to LIME 6.1.27 on Android 10 / iPlay 30 using Boshiamy standard keyboard with normal keyboard size and split keyboard off. Orientation/navigation mode and reproduction timing still need confirmation during implementation, but enough layout detail exists to track the Android fix in `docs/BACKLOG.md`.

## Problem statement

The reporter says that on an Android tablet, the bottom row of the keyboard is often hidden or covered, and the workaround is to switch away to another input method and then switch back to LIME. A later comment confirms that rotating the tablet also restores the row. The visible symptom suggests the IME view can enter a stale or incorrectly inset layout state where the keyboard container does not reserve enough visible space for the bottom row.

The reporter has now identified the platform as Android tablet, specifically iPlay 30 / Android 10 / LIME 6.1.27 while using 嘸蝦米. Keep iOS/iPadOS bottom-content coverage as related context only, not the active #145 platform path, unless a separate iPad report appears.

## Reporter evidence

- Initial report: the lowest keyboard row containing Space is often covered on a tablet, and switching to another input method and back makes the row appear again.
- Follow-up details: Android tablet, LIME 6.1.27, Android 10, iPlay 30, 嘸蝦米, all apps, rotation restores the row.
- Screenshot evidence: the first attached image shows the LIME keyboard with the bottom functional row visibly at the lower screen edge and partially cut off/covered. Do not infer the exact Boshiamy layout from that screenshot alone.
- Latest layout/settings evidence: the reporter says the Boshiamy layout is standard keyboard and that they did not change special settings. The attached settings screenshots visibly show normal/general keyboard and font size, no direction keys, split keyboard disabled, and physical-keyboard auto-hide enabled.

## Source evidence inspected

### Android input view and bottom inset path

- `LimeStudio/app/src/main/java/net/toload/main/hd/LIMEService.java`
  - `onCreateInputView()` inflates/returns the fixed candidate + keyboard container `mCandidateInInputView`.
  - For API 35+ (`VANILLA_ICE_CREAM`), it installs a `ViewCompat.setOnApplyWindowInsetsListener(...)` on `mCandidateInInputView` and applies `systemBars().bottom` as the container bottom padding.
  - The surrounding comment says this is meant to prevent overlap with the system gesture navigation bar.
  - The debug log mentions `mLastKnownBottomPadding`, but the field is currently a final `-1` and is not actually updated. That makes any intended stale-padding recovery ineffective today.
- `LimeStudio/app/src/main/res/layout/inputcandidate.xml`
  - The IME view is a vertical `CandidateInInputViewContainer` with `wrap_content` height, `fitsSystemWindows="true"`, an embedded candidate strip, and a `LIMEKeyboardView` with `wrap_content` height and `layout_alignParentBottom="true"`.
- `LimeStudio/app/src/main/java/net/toload/main/hd/keyboard/LIMEKeyboardBaseView.java`
  - `onMeasure()` sets the keyboard view height to `mKeyboard.getHeight() + paddingTop + paddingBottom`.
- `LimeStudio/app/src/main/java/net/toload/main/hd/keyboard/LIMEBaseKeyboard.java`
  - Keyboard total height is computed from the XML row heights and vertical gaps.
  - Large/tablet resources use larger key heights, for example `values-large/dimens.xml` sets `key_height` to `60dip`, and `values-xlarge/dimens.xml` sets `key_height` to `84dip`.

### iOS/iPadOS comparison

- Existing issue #139 tracks an iOS TestFlight bottom-content coverage issue from a private reporter. Its active scope is host-app bottom content being covered by LIME's custom keyboard height/safe-area behavior.
- #145 is not the same active platform path as #139: the reporter confirmed Android tablet, and the described workaround is about the LIME keyboard row itself reappearing after IME switching or rotation.

## Likely root cause / investigation hypothesis

The most plausible Android hypothesis is a stale IME window/insets or measurement state on tablets: after LIME is shown, the keyboard container may apply an incorrect bottom inset or be measured under a host/system layout state that leaves the bottom row behind the system navigation/gesture area or host app edge. Switching to another input method and back likely forces the framework to recreate or remeasure the input view, which temporarily restores the missing bottom row.

The current source has a suspicious Android clue: `onCreateInputView()` applies API 35+ bottom system-bar padding directly to the full input container, while the `mLastKnownBottomPadding` recovery variable referenced in the debug log is not functional. This does not prove the reported tablet path yet, but it is a concrete area to inspect with device logs and layout measurements.

The reporter is on Android 10, so the iOS custom-keyboard height/safe-area path is not the active #145 investigation path.

## Proposed investigation plan

1. Reproduce with Boshiamy standard keyboard on Android 10 tablet settings matching the reporter's screenshots: normal/general keyboard size, split keyboard off, no direction keys, and physical-keyboard auto-hide enabled.
2. On Android, reproduce on a tablet or emulator with gesture navigation and 3-button navigation, in both portrait and landscape, with normal and split-keyboard settings.
3. Instrument the Android IME view path around `onCreateInputView()`, `setOnApplyWindowInsetsListener(...)`, `mCandidateInInputView` measured height/padding, and `LIMEKeyboardBaseView.onMeasure()` to compare the broken state against the restored state after switching IMEs.
4. Verify whether API level, system navigation bar height, `fitsSystemWindows`, host app `adjustResize` behavior, or tablet resource key heights are causing the bottom row to be clipped.
5. Rotation is now confirmed to restore the row. During debugging, compare the broken state against the restored state after rotation and after switching IMEs.
6. If maintainers cannot reproduce, ask the reporter only for the remaining targeted details: navigation mode, orientation, and whether the symptom happens every time, first open only, or intermittently.

## Verification plan

- Android manual verification on tablet-sized device/emulator:
  - Bottom row remains visible when LIME first appears.
  - Bottom row remains visible after switching away from and back to LIME.
  - Portrait/landscape, gesture navigation, and 3-button navigation do not cover the Space row.
  - Split keyboard and keyboard-size settings do not leave stale bottom padding or excessive height.
- Add focused regression coverage or logging-backed assertions for any Android inset/measurement helper introduced during the fix.
- iOS/iPadOS: no #145 retest path unless separate matching iPad evidence appears.

## Platform impact

- Android: confirmed reporter platform. The bug is currently scoped to Android 10 tablet / iPlay 30 / LIME 6.1.27 / 嘸蝦米 standard keyboard, normal keyboard size, split keyboard off, with screenshot evidence and rotation/IME switching restoring the row.
- iOS/iPadOS: not implicated by #145. Existing #139 covers a related but separate iOS bottom-content coverage class.

## Follow-up / retest condition

The initial clarification acknowledgement has been posted, and the reporter supplied Android tablet details, screenshot evidence, and the active Boshiamy standard-keyboard layout/settings. Do not ask the reporter to retest the same APK/build. A retest request should wait until a newer Android APK or Google Play build contains a targeted fix for this Android tablet path.

`docs/BACKLOG.md` now tracks `fix#145 Android` because the reporter supplied the remaining layout detail requested by the project-account follow-up. Keep the backlog item scoped to Android tablet bottom-row clipping until a source fix or separate platform evidence broadens it.
