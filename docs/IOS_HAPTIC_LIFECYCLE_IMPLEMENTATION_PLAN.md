# iOS Haptic Lifecycle Safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop repeat-key actions and haptic requests whenever the iOS keyboard disappears or detaches, while preserving normal repeat behavior and allowing feedback after reappearance.

**Architecture:** Replace the two-stage repeat `Timer` ownership in `KeyboardView` with a tiny injected scheduler and generation guard. Route controller disappearance and view detachment through the existing centralized touch cleanup. Test virtual time deterministically through a new XCTest suite, with thin integration tests proving both lifecycle entry points call the real cleanup path.

**Tech Stack:** Swift, UIKit keyboard extension, XCTest, Xcode project build phases.

## Global Constraints

- Do not change Full Access behavior, haptic strength, repeat start delay, or repeat cadence.
- Reuse the existing active-touch cleanup instead of creating parallel lifecycle state.
- All edited `.swift` files must be UTF-8 with BOM; `project.pbxproj` remains UTF-8 without a BOM.
- Do not revert files with git commands and do not overwrite source files wholesale.
- Do not add external dependencies.
- No physical-haptic success claim may be based on the simulator.

---

### Task 1: Deterministic repeat scheduling and baseline tests

**Files:**
- Modify: `LimeIME-iOS/LimeKeyboard/KeyboardView.swift`
- Create: `LimeIME-iOS/LimeTests/KeyboardHapticLifecycleTests.swift`
- Modify: `LimeIME-iOS/LimeIME.xcodeproj/project.pbxproj`

**Interfaces:**
- Produces: `KeyboardScheduledTask`, `KeyboardRepeatScheduling`, `MainRunLoopKeyboardScheduler`, `KeyboardView.RepeatState`, scheduler injection on `KeyboardView.init`, and internal interaction test drivers.
- Consumes: `LayoutMetrics.Gesture.repeatStartDelay`, `LayoutMetrics.Gesture.repeatInterval`, `KeyboardViewDelegate`.

- [ ] **Step 1: Add the test file and target membership with failing scheduler tests**

Create a BOM-prefixed Swift test file containing `ManualKeyboardScheduler`, `RecordingLifecycleDelegate`, a layout with delete and four arrows, and these tests:

```swift
func testRepeatableKeyDoesNotRepeatBeforeStartDelay()
func testRepeatableKeyRepeatsAtConfiguredCadence()
func testReleaseBeforeStartDelayCancelsPendingRepeat()
func testReleaseWhileRepeatingStopsFurtherKeyAndHapticEvents()
func testCancelBeforeStartDelayCancelsPendingRepeat()
func testCancelWhileRepeatingStopsFurtherKeyAndHapticEvents()
func testAllRepeatableKeyCodesUseTheSameRepeatSession()
func testCancelledDequeuedRepeatCallbackDoesNothing()
func testCallbackFromOldInteractionCannotRepeatNewInteraction()
```

The manual scheduler stores `(deadline, interval, cancelled, action)` records. `advance(by:)` repeatedly executes the earliest due non-cancelled task and reschedules periodic tasks. Keep every scheduled closure in `capturedActions` for stale-callback tests.

- [ ] **Step 2: Run the focused suite and verify RED**

Run:

```sh
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME \
  -destination 'platform=iOS Simulator,id=<BOOTED_UDID>' \
  -derivedDataPath .Codex/DerivedData-haptic-lifecycle \
  -clonedSourcePackagesDirPath .Codex/SourcePackages \
  -only-testing:LimeTests/KeyboardHapticLifecycleTests test
```

Expected: compile failure because scheduler and test-driver interfaces do not exist.

- [ ] **Step 3: Add the minimal scheduler and generation-guard implementation**

Add:

```swift
protocol KeyboardScheduledTask: AnyObject { func cancel() }

protocol KeyboardRepeatScheduling {
    func schedule(after delay: TimeInterval,
                  repeating interval: TimeInterval?,
                  _ action: @escaping () -> Void) -> KeyboardScheduledTask
}

enum RepeatState: Equatable { case idle, pending, repeating }
```

`MainRunLoopKeyboardScheduler` wraps a `Timer`; a token's `cancel()` invalidates it. Store `repeatTask`, `repeatGeneration`, `repeatStateForTesting`, and injected `repeatScheduler`. Starting a repeat increments and captures the generation. Stopping increments before cancelling. Both delay and periodic closures guard the captured generation and current repeat key.

Add internal drivers that locate the layout's real button by code and invoke `beginPlainKeyTouch`, `endPlainKeyTouch`, or `cancelPlainKeyTouch`. Add `hapticDidFireForTesting` immediately after `impactOccurred()`/`selectionChanged()`.

- [ ] **Step 4: Run the focused suite and verify GREEN**

Run the Step 2 command. Expected: all nine tests pass with no real-time waits.

- [ ] **Step 5: Commit Task 1**

```sh
git add LimeIME-iOS/LimeKeyboard/KeyboardView.swift \
  LimeIME-iOS/LimeTests/KeyboardHapticLifecycleTests.swift \
  LimeIME-iOS/LimeIME.xcodeproj/project.pbxproj
git commit -m "test(ios): cover deterministic key repeat lifecycle"
```

### Task 2: Keyboard view lifecycle cleanup

**Files:**
- Modify: `LimeIME-iOS/LimeKeyboard/KeyboardView.swift`
- Modify: `LimeIME-iOS/LimeTests/KeyboardHapticLifecycleTests.swift`
- Modify: `LimeIME-iOS/LimeTests/TouchLayerGestureTests.swift`

**Interfaces:**
- Consumes: scheduler and drivers from Task 1.
- Produces: `cancelActiveInteractions()` and detachment cleanup.

- [ ] **Step 1: Write failing view lifecycle tests**

Add tests 10–21 from the design with exact names, especially active/pending cleanup, duplicate cleanup, stale callbacks, detach/reattach, and vibration-disabled repeat. Add the four direct cleanup-state tests to `TouchLayerGestureTests`.

Assertions must snapshot delegate press counts and haptic counts, advance five virtual intervals, and prove they remain unchanged. A `UIWindow` must be strongly retained for detachment tests.

- [ ] **Step 2: Run both focused suites and verify RED**

Run the Step 2 command plus `-only-testing:LimeTests/TouchLayerGestureTests`. Expected: failures because lifecycle API and detachment hook are absent.

- [ ] **Step 3: Implement public cleanup and detachment hook**

Expose:

```swift
func cancelActiveInteractions() {
    cancelAllActiveTouches()
}
```

Track whether the view was previously attached:

```swift
private var wasAttachedToWindow = false

override func didMoveToWindow() {
    super.didMoveToWindow()
    if window != nil {
        wasAttachedToWindow = true
    } else if wasAttachedToWindow {
        wasAttachedToWindow = false
        cancelActiveInteractions()
    }
}
```

Keep cleanup idempotent. Add only narrowly scoped DEBUG state helpers required by the four touch-state tests.

- [ ] **Step 4: Run both suites and verify GREEN**

Expected: all new lifecycle and existing touch-layer tests pass.

- [ ] **Step 5: Commit Task 2**

```sh
git add LimeIME-iOS/LimeKeyboard/KeyboardView.swift \
  LimeIME-iOS/LimeTests/KeyboardHapticLifecycleTests.swift \
  LimeIME-iOS/LimeTests/TouchLayerGestureTests.swift
git commit -m "fix(ios): cancel active keys when keyboard detaches"
```

### Task 3: Controller disappearance integration

**Files:**
- Modify: `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift`
- Modify: `LimeIME-iOS/LimeTests/KeyboardHapticLifecycleTests.swift`

**Interfaces:**
- Consumes: `KeyboardView.cancelActiveInteractions()`.
- Produces: internal `installKeyboardViewForTesting(_:)` and disappearance cleanup.

- [ ] **Step 1: Write failing controller lifecycle tests**

Add tests 22–27 from the design. Construct the real final controller, call `loadViewIfNeeded()`, inject the deterministic keyboard, and call the real `viewWillDisappear(false)`. Test both lifecycle orderings and Full Access values.

- [ ] **Step 2: Run the controller subset and verify RED**

Expected: active/pending repeat counts continue because `viewWillDisappear` does not yet call keyboard cleanup.

- [ ] **Step 3: Implement controller cleanup**

At the start of the existing teardown after `isKeyboardVisible = false`, call:

```swift
keyboardView?.cancelActiveInteractions()
```

Add an internal DEBUG injection method that assigns the supplied keyboard to the controller's private property and installs it in the controller view without starting database work.

- [ ] **Step 4: Run the controller and new lifecycle suites**

Expected: tests 1–27 pass; existing controller lifecycle behavior remains green.

- [ ] **Step 5: Commit Task 3**

```sh
git add LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift \
  LimeIME-iOS/LimeTests/KeyboardHapticLifecycleTests.swift
git commit -m "fix(ios): stop active keys when keyboard disappears"
```

### Task 4: Full verification and device handoff

**Files:**
- Modify only if verification reveals an in-scope defect in files already listed.

**Interfaces:**
- Consumes: completed lifecycle fix and tests.
- Produces: verified simulator result and explicit physical-device checklist.

- [ ] **Step 1: Run focused and related tests**

Run the new lifecycle suite, `TouchLayerGestureTests`, and `KeyboardViewControllerTest` on the booted simulator with repository-local DerivedData and SourcePackages.

- [ ] **Step 2: Run all LimeTests**

Expected: zero failures. If an unrelated pre-existing failure occurs, record its exact test and output without weakening the new assertions.

- [ ] **Step 3: Build the simulator target**

```sh
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj -scheme LimeIME \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath .Codex/DerivedData-haptic-lifecycle \
  -clonedSourcePackagesDirPath .Codex/SourcePackages build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 4: Check formatting and encodings**

Run `git diff --check`. Verify the first three bytes of every edited `.swift` file are `EF BB BF`; ensure `project.pbxproj` does not gain a BOM.

- [ ] **Step 5: Review the final diff against the spec**

Confirm there is no Full Access gate, cadence change, haptic-strength change, maximum repeat duration, unrelated refactor, or test relying on sleep.

- [ ] **Step 6: Commit any final verification-only correction**

Commit only if Task 4 required a code correction; otherwise leave the three focused commits unchanged.

- [ ] **Step 7: Hand off real-device acceptance**

Run or ask the user to run the six Reminders steps in the design on the iPhone 17 with Full Access on and off. Report simulator lifecycle verification separately from physical haptic verification.
