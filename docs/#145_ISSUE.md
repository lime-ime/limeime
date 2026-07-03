# Issue #145: Tablet bottom keyboard row can be covered

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/145
- Classification: `bug` + `Usability`
- Reporter: `james631025`
- Live state at triage: open, no labels, no assignee, no comments before Hermes triage.
- Reporter scope: while using LIME on a tablet, the bottom keyboard row, described as the row containing the Space key, is often covered. Switching to another input method and then switching back to LIME makes the row appear again.
- Current state: plausible UI/layout bug, but the report does not yet identify Android vs iPadOS, LIME version, tablet model, orientation, split-keyboard setting, keyboard-size setting, navigation mode, or screenshots/video.

## Problem statement

The reporter says that on a tablet, the bottom row of the keyboard is often hidden or covered, and the workaround is to switch away to another input method and then switch back to LIME. The visible symptom suggests the IME view can enter a stale or incorrectly inset layout state where the keyboard container does not reserve enough visible space for the bottom row.

Because the report says only `平板` and does not name the platform, treat Android tablet as the first investigation target for the public GitHub APK line, while keeping iOS/iPadOS bottom-content coverage as an analogous but separate path until the reporter confirms the platform.

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
- #145 is not yet proven to be the same issue because the reporter has not said whether the tablet is Android or iPadOS, and the described workaround is about the LIME keyboard row itself reappearing after IME switching.

## Likely root cause / investigation hypothesis

The most plausible Android hypothesis is a stale IME window/insets or measurement state on tablets: after LIME is shown, the keyboard container may apply an incorrect bottom inset or be measured under a host/system layout state that leaves the bottom row behind the system navigation/gesture area or host app edge. Switching to another input method and back likely forces the framework to recreate or remeasure the input view, which temporarily restores the missing bottom row.

The current source has a suspicious Android clue: `onCreateInputView()` applies API 35+ bottom system-bar padding directly to the full input container, while the `mLastKnownBottomPadding` recovery variable referenced in the debug log is not functional. This does not prove the reported tablet path yet, but it is a concrete area to inspect with device logs and layout measurements.

If the reporter is on iPadOS instead, investigate it separately through the iOS custom-keyboard height/safe-area path already documented for #139, rather than assuming the Android inset path applies.

## Proposed investigation plan

1. Ask the reporter for platform, LIME version, tablet model, OS version, orientation, navigation mode, active input method/table, keyboard size setting, split-keyboard setting, and a screenshot or short screen recording showing the hidden bottom row.
2. On Android, reproduce on a tablet or emulator with gesture navigation and 3-button navigation, in both portrait and landscape, with normal and split-keyboard settings.
3. Instrument the Android IME view path around `onCreateInputView()`, `setOnApplyWindowInsetsListener(...)`, `mCandidateInInputView` measured height/padding, and `LIMEKeyboardBaseView.onMeasure()` to compare the broken state against the restored state after switching IMEs.
4. Verify whether API level, system navigation bar height, `fitsSystemWindows`, host app `adjustResize` behavior, or tablet resource key heights are causing the bottom row to be clipped.
5. If the reporter confirms iPadOS, compare with #139's iOS height/safe-area investigation but keep the public issue scopes separate unless the same source defect is proven.

## Verification plan

- Android manual verification on tablet-sized device/emulator:
  - Bottom row remains visible when LIME first appears.
  - Bottom row remains visible after switching away from and back to LIME.
  - Portrait/landscape, gesture navigation, and 3-button navigation do not cover the Space row.
  - Split keyboard and keyboard-size settings do not leave stale bottom padding or excessive height.
- Add focused regression coverage or logging-backed assertions for any Android inset/measurement helper introduced during the fix.
- If iPadOS is confirmed, verify through a TestFlight/simulator scenario matching the reporter's tablet and orientation.

## Platform impact

- Android: likely first investigation target because the public GitHub APK line and current code show an Android IME container/insets path that could plausibly affect tablet bottom-row visibility. Platform is not confirmed by the reporter yet.
- iOS/iPadOS: possible only if the reporter confirms the tablet is an iPad. Existing #139 covers a related but separate iOS bottom-content coverage class.

## Follow-up / retest condition

Post one clarification acknowledgement asking for platform/version/device/orientation/settings/evidence, label the issue as a plausible bug, and assign maintainer attention. Do not ask the reporter to retest the same APK/build. A retest request should wait until a newer Android APK, Google Play build, or TestFlight build contains a targeted fix for the confirmed platform path.

No `docs/BACKLOG.md` entry is added yet because the platform and exact fix direction are not confirmed. Add a `fix#145` backlog item after reporter details or maintainer reproduction confirms the affected platform and implementation scope.
