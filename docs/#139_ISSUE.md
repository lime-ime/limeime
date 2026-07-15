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

## Instrumentation results (2026-07-15) — hypothesis resolved

Instrumented on a physical iPhone 17 Pro Max (WJIP17) with a DEBUG-only geometry probe:

- **Extension side** (`GeoProbe` logging in `KeyboardViewController`): `viewWillLayoutSubviews`, `applyHeight`, `publishKeyboardHeightToUIKit`, a new `viewWillTransition(to:with:)`, plus LIME's rendered frames.
- **Host side** (`GeometryProbeHostVC`, 資料庫 tab, DEBUG only): a LINE-style composer whose bottom inset is cached **only** from `keyboardWillChangeFrame` (not `keyboardLayoutGuide`), which reproduces the coverage objectively (`overlap > 0`) without needing LINE.

### What the data shows

Portrait ⇄ landscape, keyboard held visible, LIME active, `keyboard_size` default:

| State | LIME declares | LIME renders (`barBot`/`kbBot`/`view`) | iOS reports to host (`keyboardLayoutGuide` **and** `keyboardWillChangeFrame`) |
| --- | --- | --- | --- |
| Clean portrait show | 312 | bar 58, keys→312, view 312 (exact) | keyboard top 552, height **404** (adds a ~92 pt bottom band) — correct, `overlap=-4` |
| Portrait after rotate-back | 312 (unchanged) | 312 (unchanged, exact) | keyboard top ~632, height **~324–370** — **~80 pt short**, `overlap=+13…+30` covered |

### Conclusions (each backed by the log)

1. **LIME's geometry is correct and constant through rotation.** `view.bounds`, `preferredHeight`, and the height constraint all converge to the right values (312 portrait / 232 landscape); `didTransition` confirms the settled state. **H1, H2, H3 are all ruled out** — screen bounds are always the final orientation, every rotation changes numeric height so `publish` fires, and the final rows are correct.
2. **LIME renders exactly what it declares.** `barBot=58`, `kbBot=312`, `view=312`: candidate bar + keys fill the declared height with **zero overflow and zero under-declaration**. The asymmetric candidate layout is **not** involved.
3. **The 92 pt is iOS's own bottom safe-area band**, included in the keyboard frame iOS reports to hosts. After rotation iOS drops ~80 pt of it from the *reported* frame while still rendering the keyboard in the correct place — so a host positioning from the notification (or the layout guide, which is **also** stale — an earlier "guide-based hosts are immune" reading was an artifact of comparing the guide against itself) places its field ~80 pt too low and the real keyboard covers it.
4. **Not fixable from the extension via geometry.** SEVEN post-rotation fixes were tried and **all produced the identical stale result** — the settled portrait host notification is `inset=370` every time (correct is 404), `overlap≈+30`, covered:
   1. `setNeedsUpdateConstraints` immediately after settle;
   2. a 1 pt height-constant jiggle timed to when `view.bounds` first equals the constraint;
   3. the same, delayed 350 ms clear of the rotation transaction;
   4. a render-forced (`layoutIfNeeded`) jiggle held ~50 ms;
   5. a full constraint **reinstall** (deactivate old, activate a fresh `heightAnchor` constraint) plus `invalidateIntrinsicContentSize()` on `view` and `inputView`;
   6. `inputView.allowsSelfSizing = true` (the Apple pattern for a self-sizing input view) — no effect on rotation, and it added a show-time height overshoot (keyboard momentarily reported 772 pt);
   7. resizing the keyboard **inside** the rotation coordinator's `animate(alongsideTransition:)` block — the log shows the view still stays at the landscape height (232) through the whole transition and only reaches 312 *after* `didTransition`, because iOS drives the input-view size during rotation and LIME's constraint doesn't win until it completes.

   The reported `inset` (370 / 387) never moves regardless of the constraint object, sizing mode, or timing. iOS computes the post-rotation keyboard frame **without consulting LIME's geometry** and only re-derives it on a **fresh presentation** (host restarts its input session → `keyboardWillShow` with the correct 404). The extension has no API to trigger that on the host (it doesn't own the first responder). The layout guide *does* recover to the correct value after rotation, so **guide-based hosts self-heal; only notification-based hosts (LINE) stay covered.** This is a **UIKit post-rotation keyboard-frame reporting defect**, not a LIME bug.

### External research + the encapsulated-constraint measurement (closes the case)

Web research (Apple DTS forum thread 799003, the archagon 3rd-party-keyboard writeup, iOS-9 height-constraint threads) converges on one named culprit: iOS installs its own **required (priority 1000)** height constraint, `UIView-Encapsulated-Layout-Height`, and the system reports the keyboard frame from **that**, not from a third-party 999-priority constraint. Community fixes (Bitmoji/Wispr "offset trick") read/reconcile against it. The research also independently **confirmed two dead ends**: Apple DTS states `allowsSelfSizing` is for input *accessory* views, not keyboards (attempt 6), and the whole community documents custom-height-constraint + rotation as a known landmine.

This produced a concrete, falsifiable hypothesis — *the stale value lives in `UIView-Encapsulated-Layout-Height`, which none of the seven attempts touched* — so the probe was extended to log that constraint's constant (`enc=`). **Measurement disproves it:** after rotating back to portrait, `constraint=312 enc=312 view=440x312` — LIME's constraint, iOS's encapsulated constraint, and the view are **all correct at 312** — yet the host notification still reports `inset=387` (`overlap=+13`, covered). The stale value is in **none** of the accessible constraints; it lives solely in UIKit's keyboard-frame *notification* computation, which reads the constraints correctly but publishes an independent stale frame and never re-fires. There is no constraint to rewrite. The case is closed as a UIKit defect with every accessible lever measured.

### RESOLVED — deferred post-rotation height application (attempt 11, probe-verified 2026-07-15)

The "accept as UIKit limitation" disposition was **wrong** — the reporter's evidence that two other third-party keyboards and the built-in keyboard survive rotation meant the failure was LIME-specific and fixable. Three more attempts followed:

8. Writing iOS's `UIView-Encapsulated-Layout-Height` (`enc`) constant inside `applyHeight()` — **crashed the extension** (mutating it during the layout pass → infinite relayout → watchdog kill). Never mutate `enc` from a layout pass.
9. The same `enc` write, done once in the rotation-completion callback — landed before the notification fired (`enc=312` at `didTransition`), **iOS still published the stale frame**: the notification value is not read from `enc` at fire time.
10. Content-driven height (explicit height constraint moved from `view` to `keyboardView`; view height derived from the subview chain) + required (1000) priority — still stale; during rotation AutoLayout resolves the required-vs-`enc` conflict in the system's favor and the view stays at the old height past the last notification.

**Root cause (final):** every failing variant changed the keyboard height *during* the rotation transaction (from `viewWillLayoutSubviews` mid-rotation). iOS emits its keyboard-frame notifications inside that transaction using the view's current/interpolated size, then silently applies LIME's new height afterward with **no further notification**. Stationary (out-of-band) height changes — keyboard_size, emoji panel — notify hosts correctly; only transaction-internal changes are swallowed.

**Fix (attempt 11):** `rotationSettling` flag — `viewWillTransition(to:with:)` sets it; `applyHeight()` holds the existing height constant while it is set; 0.3 s after the rotation coordinator completes, the flag clears and `applyHeight()` runs once, applying the new orientation's height as a plain stationary change. The probe log confirms the previously-missing notification now fires with the correct settled frame in both orientations (portrait `inset=404`, landscape `inset=253`, final `overlap=-4`).

Cosmetic trade-off: the keyboard keeps the previous orientation's height for ~0.3 s after rotation, then snaps to the correct height — that snap *is* the host notification. A rapid double-rotation inside the 0.3 s window can momentarily apply mid-rotation, but the second rotation's own deferred apply self-heals it.

**LINE rotation retest: PASSED** (maintainer, WJIP17, 2026-07-15) — message field stays fully visible through portrait ↔ landscape ↔ portrait without dismissing the keyboard. Remaining before closing: the reproduction-matrix stationary cases (keyboard_size, four/five-row, candidate/emoji transitions — expected unaffected) and the private reporter's bottom-reachability retest on the next shipped build.

- The DEBUG probe (`GeoProbe`, `geoDump`, `GeometryProbeHostVC` + 資料庫-tab viewer) was **stripped before commit** — it never entered git history. Restoration snippets: `.claude/txt/139-geometry-probe-restoration.md`. Re-add them if the private reporter's no-rotation case reproduces on the fixed build.

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
