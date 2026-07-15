# Issue #139: iOS Keyboard Frame Becomes Stale After Live Geometry Changes

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/139
- Classification: `bug` + `Usability`
- State: open, reproducible by the maintainer in LINE after rotating with LIME visible
- Platform: iOS only. Android does not use the iOS custom-keyboard extension frame lifecycle.
- Source: the issue began with private email/TestFlight evidence and now also has a maintainer reproduction in LINE. Do not expose the private reporter's identity, company app details, or private videos.
- Active scope: host content or an input field can remain partly covered when LIME's keyboard geometry changes while the keyboard stays visible. Dismissing and reopening the keyboard restores the correct host layout.

## Current conclusion

The earlier fix improved LIME's calculated keyboard height and asked UIKit to update constraints when that numeric height changed. It did not resolve every live geometry transition.

Two independent host-app observations now share the same failure pattern:

1. A private scrollable form cannot reach its true bottom with LIME visible.
2. LINE's message field becomes partly covered after device rotation while LIME remains visible.

These observations belong in one issue for now because both indicate that the host's usable area can become stale relative to LIME's visible top edge. They do **not** yet prove one identical code-level root cause. Split the LINE rotation case into a separate issue only if instrumentation shows that the private form receives a correct keyboard frame but mishandles its scroll inset while LINE receives a stale or incorrect frame from LIME/UIKit.

## Evidence

### Private form negative retest

The private reporter retested after the first #139 fix shipped:

- LIME: 6.1.28
- Device/OS: a recent large-screen iPhone on an iOS 26 beta. Exact values remain in the private support thread.
- Result: bottom content remained covered and the scrollbar could not reach the actual bottom.
- Keyboard size: reproduced from minimum through extra large.
- Comparison: the reporter said two other third-party keyboards did not reproduce the problem. Their names remain in the private support thread.
- Evidence: a private follow-up screen recording. The recording shows a custom scrollable form and an accessory toolbar above the keyboard. Keep the recording, exact date, and app details private.

The video is consistent with a stale host viewport or bottom inset, but it cannot by itself prove whether the stale geometry originates in LIME/UIKit or in the private host app.

### Maintainer LINE rotation reproduction

The maintainer reported a separate, public-app reproduction:

1. Open a LINE conversation.
2. Focus the message field with LIME visible.
3. Rotate the device while keeping the keyboard open.
4. LIME's top edge covers roughly half of LINE's message field.
5. Rotate back to the original orientation.
6. The overlap remains.
7. Dismiss and reopen the keyboard.
8. LINE positions the message field correctly again.

The recovery behavior is important: reopening the keyboard forces a fresh keyboard presentation and clears the stale geometry. This makes a live rotation/frame-publication failure more likely than a permanently incorrect static keyboard height.

Record the exact device, iOS version, LIME build, starting orientation, ending orientation, active LIME layout, and `keyboard_size` before claiming a complete reproduction matrix.

## Historical scope no longer active

The original email also discussed numeric-field routing and keyboard-size behavior for Array10. Those are not the active #139 defect:

- A June 29 simulator investigation did not reproduce the reported numeric-field routing symptom: the tested fields either kept LIME active or were replaced by the iOS system keyboard before LIME could select an internal layout. This limited simulator result does not establish universal iOS behavior; reopen the numeric-routing scope if new real-device evidence appears.
- `keyboard_size` must remain authoritative for visual row sizing.
- Do not shrink or cap tall layouts to hide host-content coverage.

The active issue is dynamic keyboard-frame publication and host adjustment after live geometry changes.

## Previous fix and why the issue remains open

Commit `f7088f2853a692dd930bba02c52bd6d99e3a2b8a` (`#139 fix real iOS keyboard height reporting`) shipped in iOS 6.1.28.

It changed `KeyboardViewController.applyHeight()` so that:

- `KeyboardView.preferredHeight` remains the source of the rendered rows' height.
- The keyboard root height constraint is updated when `totalHeight` changes.
- `publishKeyboardHeightToUIKit()` calls `setNeedsUpdateConstraints()` on `view` and `inputView` when the numeric height changes.
- The abandoned `effectiveScale` cap is not used.

That fix addressed stale numeric height constraints after layout changes. The private 6.1.28 negative retest and the LINE rotation reproduction show that the broader lifecycle problem is not resolved.

## Current implementation path

Relevant code is in `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`.

### Rotation/layout path

`viewWillLayoutSubviews()` currently:

1. synchronizes the layout environment from traits
2. reloads the active layout when needed
3. derives orientation from `UIScreen.main.bounds`
4. sets `keyboardView.isLandscape`
5. updates split mode
6. calls `applyHeight()`

`traitCollectionDidChange(_:)` calls `applyHeight()` for selected trait/size-class changes.

There is no explicit `viewWillTransition(to:with:)` path that records the transition target and republishes final geometry after the rotation coordinator completes.

### Height publication path

`applyHeight()` computes:

```text
keysHeight = keyboardView.preferredHeight
barHeight = activeCandidateBarHeight
keyboardHeight = emojiSearchHeaderHeight + barHeight + keysHeight
totalHeight = max(keyboardHeight, emoji panel height when applicable)
```

It updates the root height constraint and calls `publishKeyboardHeightToUIKit()` only when the numeric constraint changes by more than 0.5 points or the constraint is first created.

`publishKeyboardHeightToUIKit()` currently performs:

```swift
view.setNeedsUpdateConstraints()
inputView?.setNeedsUpdateConstraints()
```

This means a width/orientation/frame transition that requires republishing geometry but produces the same numeric height can skip publication entirely. It also means publication can occur during an intermediate rotation layout pass rather than after the final orientation/layout state. These are investigation targets, not yet confirmed causes.

## Root-cause hypotheses to test

Do not implement a fix until instrumentation selects one hypothesis.

### H1: transitional orientation is used

`viewWillLayoutSubviews()` reads `UIScreen.main.bounds`. During rotation, the keyboard extension may receive a layout pass before screen bounds represent the final target orientation. LIME may rebuild or report height for an intermediate state and never publish the final state.

### H2: publication is incorrectly gated only by numeric height

The keyboard width, row metrics, or host frame can change while `totalHeight` remains numerically equal. Because `publishKeyboardHeightToUIKit()` is gated by `didChangeHeight`, UIKit may not receive a fresh constraints update for the final frame.

### H3: preferred height is sampled before the final row rebuild

`keyboardView.isLandscape` triggers a rebuild. If `preferredHeight` or constraints are sampled before that rebuild reaches its final geometry, the root constraint can remain synchronized to the previous layout until the keyboard is recreated.

### H4: host-specific inset handling is a second problem

The private app may independently cache a keyboard inset or observe only show/hide notifications. The LINE reproduction reduces the likelihood that the entire issue is private-app-only, but instrumentation must still distinguish an extension-frame problem from a host scroll-inset problem.

## Required diagnostic harness

Add a DEBUG-only host screen to the containing LIME app. It should not ship in release UI.

### LINE-style composer probe

- Add a bottom message field with a visible border.
- Anchor one copy to `view.keyboardLayoutGuide.topAnchor`.
- Keep the field focused while rotating portrait → landscape → portrait.
- Detect and display any overlap between the field's converted frame and the keyboard layout guide.

### Scrollable-form probe

- Add a long `UIScrollView` with fields extending below the initial viewport.
- Put an editable field and a visible marker at the true bottom.
- Keep LIME visible while rotating, changing LIME keyboard size, switching between four-row and five-row layouts, and switching input modes.
- Verify the final marker remains reachable without dismissing the keyboard.

### Notification and geometry logging

Record, with timestamps and a transition reason:

```text
orientation and target orientation
UIScreen.main.bounds
keyboard extension view.bounds
inputView.bounds
keyboardView.preferredHeight
activeCandidateBarHeight
computed totalHeight
keyboardHeightConstraint constant before/after
view.bounds and inputView.bounds after the final layout pass
host keyboardLayoutGuide frame
keyboardWillChangeFrame begin/end frames in host coordinates
keyboardDidChangeFrame end frame
host scroll contentInset.bottom
host scroll adjustedContentInset.bottom
active layout id, row count, keyboard_size, split mode, emoji/search state
```

Do not log typed text, candidate contents, document context, reporter data, or other private input.

## Reproduction matrix

Run each case without dismissing the keyboard between geometry changes:

| Host | Transition | Expected |
| --- | --- | --- |
| DEBUG LINE-style probe | portrait → landscape | message field remains completely above LIME |
| DEBUG LINE-style probe | landscape → portrait | message field remains completely above LIME |
| DEBUG scroll probe | portrait → landscape → portrait | true bottom remains reachable |
| DEBUG probes | minimum → extra-large `keyboard_size` | host guide/inset follows every final keyboard height |
| DEBUG probes | four-row ↔ five-row layout | no covered content and no stale gap |
| DEBUG probes | candidate bar ↔ expanded candidates | host guide/inset follows the final panel height |
| DEBUG probes | keyboard ↔ emoji/search modes | no covered content and no stale gap |
| DEBUG probes | arrow row off ↔ on | host guide/inset follows the added or removed row |
| DEBUG probes | active input mode and split-mode changes | final keyboard frame remains synchronized with visible rows |
| LINE | portrait → landscape → portrait | message field is never covered |
| LINE | rotate, then dismiss/reopen control | reopening causes no geometry correction because geometry was already correct |
| Private app, if available | same transitions | reported final form content remains reachable and the scrollbar reaches its true bottom |
| Apple keyboard / another third-party keyboard | same transitions | collect comparison frames and notifications |

The most useful control is the difference between rotating while the keyboard remains visible and dismissing/reopening after rotation.

## Verification criteria

A fix is acceptable only when all applicable conditions hold:

1. After every live transition, LIME's visible top edge matches the host's keyboard layout guide/final keyboard frame.
2. LINE's message field remains completely visible through portrait ↔ landscape rotations.
3. Rotating back does not preserve stale overlap.
4. Dismissing and reopening the keyboard does not alter host geometry because the live geometry was already correct.
5. The DEBUG scroll probe can reach its true bottom at all supported keyboard sizes and representative four-row/five-row layouts.
6. `keyboard_size` continues to change both visual row height and the published keyboard height together.
7. No fixed cap, forced shrink, or layout redesign is introduced.
8. Candidate, emoji/search, arrow-row, split/orientation, and input-mode changes do not leave covered content or stale gaps.
9. Real-device verification covers the maintainer's LINE reproduction. Simulator/source-inspection tests alone are insufficient.

## Test coverage gaps

Existing tests verify static height math and source structure:

- four-row and five-row `KeyboardView.preferredHeight`
- `keyboard_size` scaling
- arrow-row contribution
- presence of `publishKeyboardHeightToUIKit()`
- absence of the abandoned effective-size cap

They do not verify UIKit's final keyboard frame after rotation or the host app's keyboard layout guide/inset. Add a focused policy/unit test only after the publication rule is defined, and add a real UI/device rotation test or instrumented manual gate for the lifecycle behavior.

XCUITest may automate orientation with `XCUIDevice.shared.orientation`, but selecting and retaining a third-party keyboard can be environment-dependent. Keep a documented real-device manual gate even if simulator automation is added.

## Non-fixes

Do not:

- shrink or cap LIME's keyboard to match another keyboard
- mutate `KeyboardView.keySizeScale` inside height publication
- hardcode a universal keyboard height
- blame the private host app without comparing final keyboard frames
- call `layoutIfNeeded()` recursively from `viewWillLayoutSubviews()`
- force repeated layout passes without first identifying which final geometry signal is missing
- treat dismissal/reopen as an acceptable workaround for release closure

## Platform impact

### iOS

Confirmed scope. The issue concerns `UIInputViewController`, UIKit keyboard-frame publication, rotation, and host-app viewport adjustment.

### Android

Not affected by this iOS lifecycle path. Android uses its own IME window/insets model. No Android source change or retest is required unless a future fix touches shared layout metrics.

## Public and private communication

- Publicly describe the private report only as a bottom-content reachability problem reproduced after 6.1.28.
- Do not publish the reporter identity, company app, email address, or private recordings.
- The LINE reproduction may be documented publicly without private conversation content.
- Ask the private reporter which app/framework hosts the affected form and whether a public reproduction exists. That request has been sent by email.
- Do not claim resolution until the LINE rotation case and the bottom-reachability probe pass without dismissing/reopening LIME.
