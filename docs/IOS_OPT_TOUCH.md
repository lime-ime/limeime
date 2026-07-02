# iOS Touch Miss Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:systematic-debugging` first, then `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop missed keys during high-speed tapping on the iOS Lime keyboard.

**Architecture:** Keep the current single-owner `KeyTouchLayer`; do not restart the touch rewrite. Harden touch delivery first, then remove synchronous compose/candidate work from the latency-critical touch stack. Only optimize candidate rendering after device evidence shows delivered taps are still blocked by main-thread UI work.

**Tech Stack:** UIKit custom keyboard extension, `UIInputViewController`, `UIView` raw touch handling, `os_signpost` via `Prof`, XCTest/XCUITest, Instruments/xctrace on the WJIP17 device.

---

## 1. Scope

This plan targets the clarified symptom: **high-speed tapping still misses some keys**. It does not target cosmetic lag, slow-looking candidate animation, or Android parity features that already landed in `docs/IOS_TOUCH_REWRITE.md`.

Success means:

- Every physical key tap during a fast burst produces a `touchesBegan` on `KeyTouchLayer`.
- Every valid plain-key `touchesBegan` produces one `didPress`.
- Every `didPress` for a composing key appends/inserts exactly once.
- A fast burst such as `wo3jiao4li2ming2wo3jiao4li2ming2` has zero silent misses on WJIP17.

## 2. Current Evidence

Current Lime already has the large architectural fix from `docs/IOS_TOUCH_REWRITE.md`:

- `KeyTouchLayer` owns raw `touchesBegan/Moved/Ended/Cancelled` and has `isMultipleTouchEnabled = true` in `KeyboardView.swift`.
- Plain keys commit on `touchesBegan`, not `touchesEnded`, through `beginPlainKeyTouch`.
- Per-layer `KeyDetector` contexts are cached, avoiding the earlier per-tap all-subview/frame rebuild.

So the lazy conclusion is: **do not rewrite the touch layer again**. The remaining misses are likely in one of these narrower places:

1. UIKit/window delivery delay: the tap never reaches `KeyTouchLayer`.
2. Lime routing gap: `touchesBegan` arrives but no `didPress` fires.
3. Compose stack stall: `didPress` fires, but synchronous compose/candidate work blocks the next tap long enough for UIKit to delay/drop the next begin.
4. Candidate UI stall: delivered taps are correct, but the main thread is blocked by candidate reloads between taps.

## 3. Open-Source Research

### Tasty Imitation Keyboard

Source: [archagon/tasty-imitation-keyboard `ForwardingView.swift`](https://github.com/archagon/tasty-imitation-keyboard/blob/58ee779d79ff5dffdc170545ab4bb0afdd0e2caa/Keyboard/ForwardingView.swift)

Relevant pattern:

- Uses one `ForwardingView` with `isMultipleTouchEnabled = true`.
- Overrides `hitTest` to return itself for any point inside bounds.
- Adds an empty `draw(_:)` because clear keyboard areas can fail touch recognition.
- Tracks `UITouch -> UIView` and forwards `.touchDown`, drag-enter/exit, `.touchUpInside`, and `.touchCancel`.
- Resolves nearest view instead of relying only on rectangular per-control hit tests.

Takeaway for Lime: our `KeyTouchLayer` already owns touches, but it is clear and has no `hitTest`/`draw` hardening. Add Tasty's touch-surface hardening before adding heavier logic.

### azooKey

Sources:

- [TouchDownAndTouchUpGesture.swift](https://github.com/azooKey/azooKey/blob/b3559131ddfe2f25575cfb23b57539a225989607/AzooKeyCore/Sources/SwiftUIUtils/TouchDownAndTouchUpGesture.swift)
- [UnifiedGenericKeyView.swift](https://github.com/azooKey/azooKey/blob/b3559131ddfe2f25575cfb23b57539a225989607/AzooKeyCore/Sources/KeyboardViews/View/UnifiedKey/UnifiedGenericKeyView.swift)
- [KeyboardViewController.swift](https://github.com/azooKey/azooKey/blob/b3559131ddfe2f25575cfb23b57539a225989607/Keyboard/Display/KeyboardViewController.swift#L263-L268)

Relevant pattern:

- Uses a custom `UIGestureRecognizer` to expose touch down, move, and up into SwiftUI.
- Uses `DragGesture(minimumDistance: .zero)` for key gestures, so the key lifecycle starts immediately.
- Allows simultaneous recognition.
- In `viewDidAppear`, sets window gesture recognizers' `delaysTouchesBegan = false`.

Takeaway for Lime: when a keyboard is missing high-speed begins, the smallest researched fix is not a new queue; it is to ensure UIKit does not delay key begins and to make down/move/up explicit.

### Apple Custom Keyboard Guidance

Source: [Apple App Extension Programming Guide - Custom Keyboard](https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/CustomKeyboard.html)

Relevant constraints:

- A custom keyboard's core job is responding to taps/gestures and inserting/deleting text through `textDocumentProxy`.
- Apple explicitly frames the system keyboard as fast and responsive; custom keyboards must match that expectation.
- The keyboard draws only within the `UIInputViewController` primary view, so touch and preview behavior must be implemented inside that view hierarchy.

## 4. Root-Cause Probe

Before changing behavior, bucket each missed tap. Use existing `Prof` signposts; if xctrace is unavailable on device, use the file-logger recipe already documented in `docs/IOS_MISS_KEY.md`.

### Task 1: Add Touch Delivery Signposts

**Files:**

- Modify: `LimeIME-iOS/LimeKeyboard/KeyboardView.swift`
- Modify: `LimeIME-iOS/LimeKeyboard/Profiling.swift` only if `Prof.event` needs a string overload already missing

- [ ] **Step 1: Add events at the four decision points**

Add signposts in `KeyboardView.swift`:

```swift
Prof.event("TouchBegan", "touch=\(touchID) code=\(key?.codes.first ?? 0)")
Prof.event("PlainPress", "touch=\(touchID) code=\(target.keyDef.code)")
Prof.event("PlainCommit", "touch=\(touchID) code=\(keyDef.code)")
Prof.event("TouchCancel", "touch=\(touchID)")
```

Place them at:

- `keyTouchLayer(_:touchesBegan:with:)`, immediately after key detection.
- `beginPlainKeyTouch`, just before any feedback or delegate call.
- `beginPlainKeyTouch`, immediately before `delegate?.keyboardView(self, didPress: keyDef)`.
- `keyTouchLayer(_:touchesCancelled:with:)`.

- [ ] **Step 2: Capture WJIP17 burst**

Run the LimeIME scheme on the WJIP17 device, force-refresh the extension as in `docs/IOS_MISS_KEY.md`, then record a fixed burst:

```text
wo3jiao4li2ming2wo3jiao4li2ming2
```

Expected classification:

| Observation | Bucket | Next phase |
|---|---|---|
| `TouchBegan` count < physical taps | UIKit/window delivery or transparent hit gate | Phase 1 |
| `TouchBegan` count == taps, `PlainCommit` lower | Lime touch routing | Phase 1 plus code audit around `ownerTouchBehavior` |
| `PlainCommit` count == taps, document missing chars | compose/proxy path | Phase 2 |
| All counts match, but next begin arrives late during `CandidateReload` | candidate UI block | Phase 3 |

Do not attempt a fourth blind fix if three probe/fix loops fail on the same bucket. Stop and inspect a fresh Time Profiler trace.

## 5. Phase 1 - Harden Touch Delivery

This phase handles the case where `TouchBegan` is missing or delayed.

### Task 2: Make `KeyTouchLayer` a Definite Hit Surface

**Files:**

- Modify: `LimeIME-iOS/LimeKeyboard/KeyboardView.swift`
- Test: `LimeIME-iOS/LimeTests/TouchLayerGestureTests.swift`

- [ ] **Step 1: Add Tasty-style hit-test hardening**

Patch `KeyTouchLayer`:

```swift
fileprivate final class KeyTouchLayer: UIView {
    private weak var owner: KeyboardView?

    init(owner: KeyboardView) {
        self.owner = owner
        super.init(frame: .zero)
        isAccessibilityElement = false
        isMultipleTouchEnabled = true
        isOpaque = false
        contentMode = .redraw
    }

    required init?(coder: NSCoder) { fatalError("not used") }

    override func draw(_ rect: CGRect) {}

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        guard !isHidden, alpha > 0, isUserInteractionEnabled, bounds.contains(point) else {
            return nil
        }
        return self
    }
}
```

This keeps one owner and avoids a visible row fill. If the probe still shows missing `TouchBegan` in transparent gaps, switch the initializer to:

```swift
backgroundColor = LayoutMetrics.TouchTrap.fill
```

Only take that fallback after the probe proves the empty draw is not enough.

- [ ] **Step 2: Add a source-level guard test**

Extend `TouchLayerGestureTests` with:

```swift
func testKeyTouchLayerOwnsHitTesting() throws {
    let source = try String(contentsOf: projectFileURL("LimeKeyboard/KeyboardView.swift"),
                            encoding: .utf8)
    XCTAssertTrue(source.contains("override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView?"))
    XCTAssertTrue(source.contains("return self"))
    XCTAssertTrue(source.contains("override func draw(_ rect: CGRect) {}"))
}
```

- [ ] **Step 3: Run the focused tests**

```bash
xcodebuild test -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.5' -only-testing:LimeTests/TouchLayerGestureTests
```

Expected: `TouchLayerGestureTests` passes.

### Task 3: Disable Window Touch-Began Delay

**Files:**

- Modify: `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`
- Test: `LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift`

- [ ] **Step 1: Add a tiny helper**

Add this helper inside `KeyboardViewController`:

```swift
private func disableKeyboardWindowTouchDelay() {
    view.window?.gestureRecognizers?.forEach { recognizer in
        recognizer.delaysTouchesBegan = false
    }
}
```

Call it from `viewDidAppear(_:)` and `viewDidLayoutSubviews()` after `super`, because the extension window can appear late:

```swift
override func viewDidAppear(_ animated: Bool) {
    super.viewDidAppear(animated)
    disableKeyboardWindowTouchDelay()
}
```

If these overrides already exist, add only the helper call.

- [ ] **Step 2: Add a source-level guard test**

Add to `KeyboardViewControllerTest`:

```swift
func testKeyboardWindowTouchDelayIsDisabled() throws {
    let source = try String(contentsOf: projectFileURL("LimeKeyboard/KeyboardViewController.swift"),
                            encoding: .utf8)
    XCTAssertTrue(source.contains("private func disableKeyboardWindowTouchDelay()"))
    XCTAssertTrue(source.contains("recognizer.delaysTouchesBegan = false"))
    XCTAssertTrue(source.contains("disableKeyboardWindowTouchDelay()"))
}
```

- [ ] **Step 3: Re-run the WJIP17 burst**

Expected:

- `TouchBegan` count matches physical taps.
- Misses disappear if the root cause was UIKit/window delivery delay.

If misses disappear here, stop. Do not implement Phases 2-3.

## 6. Phase 2 - Move Compose UI Work Out of the Touch Stack

This phase handles the case where `TouchBegan` and `PlainCommit` happen, but fast taps still miss because the next begin is delayed while `didPress -> handleCharacter -> updateCandidates -> showComposingPopup` runs synchronously.

### Task 4: Coalesce Candidate Updates One Runloop Later

**Files:**

- Modify: `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`
- Test: `LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift`

- [ ] **Step 1: Add one pending flag**

Near candidate-flow state:

```swift
private var candidateUpdatePending = false
```

- [ ] **Step 2: Add the coalescing helper**

```swift
private func requestCandidateUpdate() {
    guard !candidateUpdatePending else { return }
    candidateUpdatePending = true
    DispatchQueue.main.async { [weak self] in
        guard let self else { return }
        self.candidateUpdatePending = false
        self.updateCandidates()
    }
}
```

- [ ] **Step 3: Replace only latency-sensitive normal compose calls**

In `handleCharacter(_:)`, replace the accepted-compose path's direct call:

```swift
updateCandidates()
```

with:

```swift
if autoCommit == 0 {
    requestCandidateUpdate()
} else {
    updateCandidates()
}
```

Keep synchronous `updateCandidates()` in paths that immediately need current candidates to commit, such as end-key resolution and active auto-commit.

In `handleBackspace()`, for `mComposing.count > 1`, replace:

```swift
updateCandidates()
showComposingPopup()
```

with:

```swift
requestCandidateUpdate()
```

- [ ] **Step 4: Add a source-level guard test**

```swift
func testComposePathRequestsDeferredCandidateUpdate() throws {
    let source = try String(contentsOf: projectFileURL("LimeKeyboard/KeyboardViewController.swift"),
                            encoding: .utf8)
    XCTAssertTrue(source.contains("private var candidateUpdatePending = false"))
    XCTAssertTrue(source.contains("private func requestCandidateUpdate()"))
    XCTAssertTrue(source.contains("DispatchQueue.main.async { [weak self]"))
    XCTAssertTrue(source.contains("if autoCommit == 0"))
}
```

- [ ] **Step 5: Re-run device burst**

Expected:

- `TouchBegan` and `PlainCommit` count match taps.
- High-speed typing no longer misses keys.
- Candidate bar may update one frame later during bursts; that is acceptable if text input is correct.

If this fixes misses, stop. Phase 3 is performance polish, not required.

### Task 5: Skip Duplicate Composing Popup Work

**Files:**

- Modify: `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`

- [ ] **Step 1: Cache the rendered composing display**

Add:

```swift
private var lastComposingPopupDisplay: String?
```

Patch `showComposingPopup()` after computing `display`:

```swift
guard lastComposingPopupDisplay != display else { return }
lastComposingPopupDisplay = display
candidateBar.composingText = display
```

Patch `hideComposingPopup()`:

```swift
lastComposingPopupDisplay = nil
```

Expected: the second `showComposingPopup()` inside `setSuggestions` becomes a no-op when DB results do not change `mComposing`.

## 7. Phase 3 - Reduce Candidate Main-Thread Blocks

This phase handles the case where all tap/commit counters match but `CandidateReload` p95 overlaps the next tap window.

### Task 6: Reuse Candidate Buttons Instead of Rebuilding All

**Files:**

- Modify: `LimeIME-iOS/LimeKeyboard/CandidateBarView.swift`
- Test: `LimeIME-iOS/LimeTests/KeyboardViewControllerTest.swift`

- [ ] **Step 1: Let candidate buttons keep their width constraint**

Patch `CandidateButton`:

```swift
final class CandidateButton: UIButton {
    let pillView = UIView()
    var minWidthConstraint: NSLayoutConstraint?
}
```

In `makeCandidateButton`, replace the anonymous width constraint activation with:

```swift
let minWidth = btn.widthAnchor.constraint(
    greaterThanOrEqualToConstant:
        CandidateBarView.minCandidateCellWidth(fontPointSize: cellFont.pointSize,
                                                hPad: candidateHPad)
)
minWidth.isActive = true
btn.minWidthConstraint = minWidth
```

- [ ] **Step 2: Add a configure method for existing buttons**

```swift
private func configureCandidateButton(_ btn: CandidateButton, mapping: Mapping, index: Int) {
    btn.tag = index
    let isComposingCode = mapping.isComposingCodeRecord
    let cellFont = isComposingCode ? composingCodeFont : candidateFont
    btn.titleLabel?.font = cellFont
    btn.setTitle(mapping.word, for: .normal)
    btn.setTitleColor(
        isComposingCode
            ? effectiveCandiText.withAlphaComponent(LayoutMetrics.CandidateBar.composingCodeDimAlpha)
            : effectiveCandiText,
        for: .normal
    )
    btn.minWidthConstraint?.constant = CandidateBarView.minCandidateCellWidth(
        fontPointSize: cellFont.pointSize,
        hPad: candidateHPad)
    applyHighlightStyle(button: btn, index: index, mapping: mapping)
}
```

- [ ] **Step 3: Change `rebuildButtons()` to grow/shrink/update**

Replace teardown-first behavior with:

```swift
private func rebuildButtons() {
    while candidateButtons.count < candidates.count {
        let index = candidateButtons.count
        let btn = makeCandidateButton(mapping: candidates[index], index: index)
        stackView.addArrangedSubview(btn)
        btn.heightAnchor.constraint(equalTo: stackView.heightAnchor).isActive = true
        candidateButtons.append(btn)
    }

    while candidateButtons.count > candidates.count {
        let btn = candidateButtons.removeLast()
        btn.removeFromSuperview()
    }

    for (index, mapping) in candidates.enumerated() {
        configureCandidateButton(candidateButtons[index], mapping: mapping, index: index)
    }

    updateChromeVisibility(hasCandidates: !candidates.isEmpty)
}
```

Move the existing chrome show/hide code into:

```swift
private func updateChromeVisibility(hasCandidates: Bool) {
    let showEmptyDismissChrome = emptyDismissChromeEnabled && !hasCandidates
    let allowEmoji = !isPad
    let allowOptions = true
    let showIdleTools = CandidateBarView.shouldShowIdleTools(
        hasCandidates: hasCandidates,
        idleRevealReady: idleToolsRevealReady,
        idleToolsSuppressed: idleToolsSuppressed,
        allowTool: !showEmptyDismissChrome)
    let showActiveChrome = CandidateBarView.shouldShowActiveChrome(
        hasCandidates: hasCandidates,
        showIdleTools: showIdleTools,
        idleRevealReady: idleToolsRevealReady)
    let showMoreChrome = showActiveChrome && hasCandidates
    moreButton.isHidden = !showMoreChrome
    moreSep.isHidden = !showMoreChrome
    dismissButton.isHidden = !(showActiveChrome || showEmptyDismissChrome)
    emojiButton.isHidden = !showIdleTools || !allowEmoji
    optionsButton.isHidden = !CandidateBarView.shouldShowOptionsButton(
        legacyGlobeMode: legacyGlobeMode,
        hasCandidates: hasCandidates,
        showIdleTools: showIdleTools,
        allowOptions: allowOptions)
}
```

- [ ] **Step 4: Run existing candidate tests plus burst trace**

```bash
xcodebuild test -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.5' -only-testing:LimeTests/KeyboardViewControllerTest
```

Expected:

- Candidate behavior tests pass.
- `CandidateReload` p95 drops compared with the pre-change WJIP17 trace.

### Task 7: Remove Synchronous `layoutIfNeeded()` From Fresh Candidate Reload

**Files:**

- Modify: `LimeIME-iOS/LimeKeyboard/CandidateBarView.swift`

- [ ] **Step 1: Guard the layout flush behind a real need**

Current `setCandidates` always calls:

```swift
scrollView.layoutIfNeeded()
scrollView.setContentOffset(.zero, animated: false)
```

Replace with:

```swift
if scrollView.contentOffset.x != 0 {
    scrollView.setContentOffset(.zero, animated: false)
}
```

Only restore `layoutIfNeeded()` if a regression proves UIScrollView overwrites zero offset without it.

Expected: less main-thread layout work immediately after stage-1 candidate reload.

## 8. Phase 4 - Measure, Then Stop

### Task 8: Run Final Verification

**Files:**

- No source edits unless a gate fails.

- [ ] **Step 1: Simulator build**

```bash
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.5' build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 2: Focused tests**

```bash
xcodebuild test -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME -destination 'platform=iOS Simulator,name=iPhone 17 Pro,OS=26.5' -only-testing:LimeTests/KeyDetectorTests -only-testing:LimeTests/TouchLayerGestureTests -only-testing:LimeTests/KeyboardViewControllerTest
```

Expected: all pass.

- [ ] **Step 3: WJIP17 physical-device burst**

Use the LimeIME scheme, force-refresh the extension binary, then type:

```text
wo3jiao4li2ming2wo3jiao4li2ming2
```

Expected: zero silent misses.

- [ ] **Step 4: Compare with controls**

Run the same burst with:

- iOS built-in keyboard.
- Android LIME keyboard.
- iOS Lime with haptic off.
- iOS Lime with haptic on.

Expected: iOS Lime is no longer observably worse for missed taps. Haptic setting must not change correctness.

## 9. Non-Goals

- Do not add another touch queue. UIKit already queues touches; the fix is making delivery immediate and keeping the main thread clear.
- Do not replace `KeyDetector` with a grid. Row sizes are tiny; O(n) detection is not the bottleneck unless a trace proves otherwise.
- Do not rewrite `KeyboardView.swift`. All edits above are targeted.
- Do not optimize candidate rendering before the touch/commit counters prove candidate rendering is still involved.

## 10. Stop Conditions

Stop early when the WJIP17 burst has zero silent misses.

Stop and re-research when the same bucket fails three times. The next research target is then Apple's current UIKit gesture-delivery behavior for keyboard extensions on iOS 26.x, plus a fresh comparison against azooKey's latest touch stack.

---

## 11. Results — what shipped (2026-07-02, branch `ios-touch-rewrite`)

**Outcome: high-speed misses resolved (device-confirmed on WJIP17 — "much better", then confirmed fixed).** Phase 1 delivery hardening did most of the work; Phase 3 removed the residual candidate-bar block. Phase 2 was **not needed** and left unimplemented. Full headless suite green (130/0).

### Phase 0 — signposts (Task 1) — DONE, commit `49845bba`
`Prof.event` probes added at the four sites: `TouchBegan` (after `KeyDetector.keyAt`), `PlainPress` + `PlainCommit` (in `beginPlainKeyTouch`), `TouchCancel`. Left in place for future device bucketing.

### Phase 1 — delivery hardening (Task 2 + 3) — DONE, commit `49845bba` (the biggest win)
- **Task 3 `delaysTouchesBegan = false`** on the input window's gesture recognizers (azooKey pattern), from `viewDidAppear` + `viewDidLayoutSubviews`. This + Task 2 is what fixed the bulk of the misses — the root cause was **UIKit delaying/dropping `touchesBegan`**, not candidate work.
- **Task 2 `KeyTouchLayer` hit-surface hardening** (Tasty pattern): `isOpaque=false`, `contentMode=.redraw`, empty `draw(_:)`, and a `hitTest`. **⚠️ Correction to the plan:** the plan's `hitTest` returning `self` for *every* in-bounds point would swallow touches meant for the still-interactive **system-globe / legacy `-3`** keys (they are `isUserInteractionEnabled=true` subviews) and break the keyboard switcher. Shipped version returns an interactive subview hit from `super.hitTest` first, and only falls back to `return self` for plain/render-only keys + transparent gaps. The `TouchTrap.fill` fallback was **not** needed.

### Phase 2 — compose coalescing (Task 4 + 5) — NOT IMPLEMENTED, and assessed NOT WORTH DOING

**Verdict (2026-07-02): skip it.** Rationale:

1. **It targets a cause that turned out to be wrong.** Phase 2's premise (§6) is that the synchronous `didPress → updateCandidates → showComposingPopup` chain blocks the next begin. The real cause was **UIKit touch-delivery delay**, fixed in Phase 1 (`delaysTouchesBegan=false` + hit-test hardening). With delivery fixed and misses gone on device, Phase 2's premise no longer holds — and the plan's own §5 stop-condition says *"if misses disappear in Phase 1, stop; do not implement Phases 2-3."*
2. **The deferral idea was already tried on device and did not help.** `docs/IOS_MISS_KEY.md` (2026-05-21) shipped a runloop-deferred reload and logged *"drops still occur"* — that result is what redirected the whole investigation toward delivery. Re-doing a heavier version of a disproven fix is not warranted.
3. **Task 4 is the riskiest change in this plan for zero measured benefit.** Coalescing candidate updates one runloop later makes the candidate bar visibly lag one frame and adds edge cases (a candidate pick or end-key landing between the defer and the update; the `autoCommit == 0` gating must be exact). Trading correctness risk for latency that can no longer be measured is a bad deal — YAGNI.

**Exception — Task 5 is a safe, optional micro-opt (unlike Task 4).** Caching `lastComposingPopupDisplay` to skip the duplicate `showComposingPopup()` when the display is unchanged is a *pure* optimization: no timing/behavior change, just skips redundant per-stroke work. Fine to land on its own as battery/smoothness polish, but not needed — do it only if residual jank is observed.

**When to revisit Task 4:** only if a future WJIP17 signpost trace shows all counts match (`TouchBegan` = `PlainCommit` = physical taps) **yet the next begin still arrives late during compose** — i.e. the compose path is provably blocking even after today's fixes. The Task-1 signposts (`49845bba`) are in place to detect exactly that. Absent that evidence, leave Phase 2 out.

### Phase 3 — diffable candidate rebuild (Task 6 + 7) — DONE, commit `4daae397`
- **Task 6:** `rebuildButtons()` no longer tears down + reallocates every cell. It grows (append only missing `CandidateButton`s), shrinks (remove only the tail), then `configureCandidateButton()` each visible cell in place (identical rendering); chrome logic moved verbatim to `updateChromeVisibility(hasCandidates:)`; `CandidateButton` keeps a reusable `minWidthConstraint`.
- **Task 7 (conservative):** in the *fresh-reset* path of `setCandidates` only, dropped the synchronous `layoutIfNeeded()` and zero the offset only when `contentOffset.x != 0`; the offset-preservation path is unchanged. **Watch-item:** confirm the candidate bar still starts at the left edge after typing; restore `layoutIfNeeded()` if not.

### Profiling finding (validates Non-Goal #2)
A sim micro-benchmark (iPhone 17 Pro, Debug, 26-key layout) measured `KeyDetector` **build+resolve ≈ 6.4 µs/tap**, **resolve-only ≈ 5.4 µs/tap** — ~0.04 % of a 16.7 ms frame. **Detection is not the bottleneck; do not optimize it.** The cost is UIKit touch delivery + per-keystroke view work.

### Adjacent fixes on the same branch (not part of this plan but part of the same effort)
- `6ed2c884` reuse the key-preview view instead of rebuilding it per keystroke.
- `10bdcdf8` cache `KeyDetector` across taps (was rebuilt on every touch-down).
- `d97d52f3` commit plain keys on `touchesBegan` not release, so a dropped `touchesEnded` can't swallow the char (flint preserved via delete-then-commit on slide).
- `69b9b132` flint onto a popup key opens its mini-keyboard (was stopping one key short).
