# Issue #139: iOS Keyboard Height Reporting

## Status

- GitHub issue: https://github.com/lime-ime/limeime/issues/139
- Classification: `bug` + `Usability`
- Source: maintainer-created issue from private email/TestFlight evidence. Do not expose reporter identity, private app details, or private videos in public comments.
- Current state: reopened after a private reporter retested LIME 6.1.28 on iPhone 17 Pro Max / iOS 26.6 beta 4 and confirmed the bottom content is still covered. The scrollbar cannot reach the bottom with LIME keyboard size set anywhere from minimum to extra large. The reporter supplied a new private `.mov` recording and said Okidokey and 元書輸入法 do not reproduce the behavior.
- Attempted fix commit `f7088f2853a692dd930bba02c52bd6d99e3a2b8a` (`#139 fix real iOS keyboard height reporting`) is included in LIME 6.1.28, but the real-device negative retest shows the active defect remains unresolved. Do not treat the existing height-constraint regression tests as sufficient device verification.
- No Android retest applies. Keep the issue open pending renewed iOS investigation and a later TestFlight/App Store retest.
- Historical symptom: iOS TestFlight 6.1.27 could leave host-app bottom content behind the LIME keyboard. The reporter said native iOS and other third-party keyboards did not cover the same bottom content. Follow-up evidence showed this was not Array10-only: Dayi also showed it, and Dayi covered a larger range.
- Numeric-field routing is no longer the active defect. Simulator investigation on 2026-06-29 could not reproduce the reported numeric-field switch for tested web fields; iOS often replaces third-party keyboards entirely for numeric/inputmode fields.
- The attempted `effectiveScale` / fixed-height cap is abandoned. It is not native iOS behavior, not Android behavior, and it breaks the user's `keyboard_size` preference. Do not reintroduce it.

## Hard Rules

- Do not change layout design for this issue.
- Do not shrink, cap, normalize, or otherwise redesign tall layouts.
- Do not mutate `KeyboardView.keySizeScale` inside height reporting.
- `keyboard_size` remains authoritative for row scale.
- The only valid #139 fix is to report the real current keyboard height to iOS.

## Problem

LIME layouts have legitimately different heights.

Examples at iPhone portrait normal size, excluding emoji/search/expanded modes:

| Layout shape | Keys height | Candidate bar | Total |
| --- | ---: | ---: | ---: |
| Four-row layout: `3 * 50 + 54` | `204 pt` | `58 pt` | `262 pt` |
| Five-row layout: `4 * 50 + 54` | `254 pt` | `58 pt` | `312 pt` |

Both totals are valid. A five-row Dayi layout should report a taller keyboard than a four-row Array10/CJ-style layout. A shorter layout should report a shorter keyboard.

Therefore stale reporting can fail in either direction:

- reported height < real rendered height: host content is hidden behind the keyboard
- reported height > real rendered height: host content is pushed too high or leaves a gap

#139's reported symptom is the first case.

## Current Height Flow

Source points:

- `KeyboardViewController.loadSettings()` reads `keyboard_size` into `keyboardSize`.
- `KeyboardViewController.applyFeedbackSettings()` assigns `keyboardView?.keySizeScale = keyboardSize`.
- `KeyboardView.keySizeScale` rebuilds rows.
- `KeyboardView.preferredHeight` sums current layout rows using scaled row heights:
  - regular row: `LayoutMetrics.KeyboardRow.rowHeight(...) * keySizeScale`
  - bottom row: `LayoutMetrics.KeyboardRow.bottomRowHeight(...) * keySizeScale`
- `KeyboardViewController.applyHeight()` computes:

```text
totalHeight =
  emojiSearchHeaderHeight
  + activeCandidateBarHeight
  + keyboardView.preferredHeight
```

For normal keyboard mode, `applyHeight()` reports that height by setting the keyboard extension root view height constraint:

```swift
view.heightAnchor.constraint(equalToConstant: totalHeight)
```

There is no separate iOS API for "report keyboard height". This root view height constraint is the report UIKit uses to derive the custom keyboard frame.

## Real Root Cause

The height math is not the core bug. The core bug is that the height reported to UIKit can become stale relative to the real current rendered keyboard.

Current code can change the visible keyboard shape after an earlier height has already been reported:

- `viewDidLoad()` starts from an English/preference layout, builds UI, and calls `applyHeight()`.
- `viewWillAppear()` / `initOnStartInput()` can restore the active IM and switch to the real field/layout.
- async database setup can later resolve activated IMs, active IM, layout ID, keyboard prefs, and call layout/height code again.
- field type, orientation, arrow row, candidate bar font scale, emoji/search state, and layout switches can all change the real height.

Calling `applyHeight()` after these transitions is necessary but not sufficient unless UIKit receives the final resolved root height. The missing invariant is:

```text
after any visible layout-affecting change:
  computed totalHeight == root view bounds height published to UIKit
```

If this invariant fails, the host app adjusts to the wrong keyboard frame.

## Fix Direction

Keep `applyHeight()` as the single reporting path, but make it authoritative:

1. Ensure current layout, `keySizeScale`, candidate bar height, emoji/search state, arrow row, split/orientation state, and row constraints are already updated.
2. Compute `totalHeight` from the current real layout.
3. Update the root height constraint to exactly `totalHeight`.
4. If the root height changed, mark constraints dirty so UIKit consumes the new height on its own keyboard presentation/layout pass:

```swift
keyboardHeightConstraint?.constant = totalHeight
view.setNeedsUpdateConstraints()
inputView?.setNeedsUpdateConstraints()
```

Do not call `setNeedsLayout()` or `layoutIfNeeded()` from this path. `applyHeight()` runs from `viewWillLayoutSubviews`, so forcing/requesting another layout every pass can block keyboard presentation.

5. In debug/instrumented builds, verify and log if the reported height and actual root bounds diverge after layout.

This is not a cap. It does not alter layout design. It only makes iOS receive the height LIME already renders.

## Required Instrumentation

Before claiming a fix, capture this on real device/TestFlight for Array10 and Dayi:

```text
reason
layout id
keyboard_size / keySizeScale
regular row count
bottom row count
showArrowKey
isLandscape
isOnPad
emoji/search/expanded state
keyboardView.preferredHeight
activeCandidateBarHeight
emojiSearchHeaderHeight
computed totalHeight
height constraint constant before/after
view.bounds.height before layout
view.bounds.height after UIKit layout pass
inputView.bounds.height after UIKit layout pass
```

Expected result:

```text
computed totalHeight == view.bounds.height after layout
```

If the values differ, that mismatch is #139. If they match, then LIME is reporting the real layout height and the remaining issue is host-app content adjustment outside LIME.

## Verification Plan

- Add/keep a regression guard that `applyHeight()` does not override `KeyboardView.keySizeScale`.
- Add coverage for the height-reporting path:
  - four-row layout reports `3 regular + 1 bottom + candidate bar`
  - five-row layout reports `4 regular + 1 bottom + candidate bar`
  - changing layout from taller to shorter updates the reported height downward
  - changing layout from shorter to taller updates the reported height upward
- Manual device verification:
  - reproduce or instrument the 6.1.28 failure on iPhone 17 Pro Max / iOS 26.6 beta 4 using the private video as reference.
  - verify that host-app scrolling can reach the true bottom with LIME keyboard size settings across the full range from minimum through extra large.
  - compare the same host view with Okidokey and 元書輸入法, which the reporter says do not reproduce the coverage.
  - Array10 and Dayi report different heights matching their real rows.
  - Switching between layouts does not leave stale hidden content or stale gaps.
  - changing `keyboard_size` changes the reported height and visual row height together.
  - candidate bar, emoji/search, orientation, and arrow row changes do not leave stale root height.

## Non-Fix: Abandoned Effective Scale

The abandoned approach computed a cap like:

```text
effectiveScale = min(requestedScale, capScale)
```

and wrote it back into:

```swift
keyboardView.keySizeScale
```

That is wrong because it changes the visual keyboard size instead of reporting the real one. On real device it made the keyboard-size preference ineffective for tall layouts. Remove this path completely and do not use it as #139 evidence.

## Numeric-Field Findings

The original numeric-keyboard report is separate from the active bottom-coverage symptom.

Simulator investigation on 2026-06-29 with Safari/WebView fields found:

| Field shape | Observed keyboard |
| --- | --- |
| `type=text` | LIME keyboard |
| invalid `type="num"` | LIME keyboard |
| bare `type="number"` | LIME keyboard |
| `type="number" inputmode="numeric"` | iOS system numeric pad |
| text field with `inputmode="numeric"` | iOS system numeric pad |
| `pattern="[0-9]*"` | iOS system numeric pad |

Conclusion: tested numeric/inputmode fields either keep LIME active or are system-replaced by iOS before LIME can route them. Do not treat numeric routing as the active #139 defect unless new private evidence appears.

## Public / Private Communication

- Public issue notes should say only that private follow-up evidence expanded the active iOS bottom-coverage symptom beyond Array10 to Dayi.
- Do not expose reporter identity, private app details, or screenshots/videos.
- If asking privately for more data, ask for iOS version, device, orientation, keyboard-size setting, active layout, candidate/emoji/search state, and a non-sensitive frame showing the covered bottom area.
