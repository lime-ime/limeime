# iOS Haptic Lifecycle Safety Design

Date: 2026-07-10

## Problem

On an iPhone 17 using LimeIME in Reminders, one interaction can leave haptic feedback firing continuously. After sustained firing, later key taps no longer produce haptics. Full Access was enabled during the report, but the feedback and repeat-timer code does not depend on Full Access.

The only unbounded periodic haptic source in the keyboard is the repeat-key timer used by Backspace and arrow keys. Normal touch completion stops it, but keyboard disappearance does not currently cancel the keyboard view's active touches and timers. A host transition that omits or delays `touchesEnded`/`touchesCancelled` can therefore leave repeat work running.

## Scope

Make active keyboard interaction cleanup reliable across normal and abnormal lifecycle transitions without changing haptic strength, repeat cadence, Full Access behavior, or ordinary key semantics.

## Design

`KeyboardView` will expose a narrow lifecycle cleanup operation that reuses its existing centralized active-touch cancellation logic. Cleanup will:

- invalidate the pending repeat-start timer or active repeating timer;
- clear the repeat key;
- invalidate pending owner long-press timers;
- cancel pressed-key visual and preview state;
- cancel popup-slide state;
- clear touch trackers, targets, samples, and shift-hold state;
- tolerate duplicate calls and later delivery of touch completion events.

`KeyboardViewController.viewWillDisappear` will invoke this operation before completing the controller's existing popup and preview teardown. This is the primary input-session lifecycle boundary.

`KeyboardView` will also invoke cleanup when it detaches from a window. This is the defensive boundary for host behavior that removes or replaces the input view without a dependable controller disappearance callback. Attachment to a new window must not trigger cancellation.

The implementation will not add a maximum repeat duration or disable haptic feedback during repeat. Those approaches would mask the leaked timer while changing legitimate long-hold behavior.

## Regression Tests

The automated coverage will use real XCTest methods and production objects. It will not use source-text assertions for lifecycle behavior and will not sleep on real timers.

### Production seams required by the tests

Add these internal types to `KeyboardView.swift` rather than creating a general scheduling framework:

```swift
protocol KeyboardScheduledTask: AnyObject {
    func cancel()
}

protocol KeyboardRepeatScheduling {
    func schedule(after delay: TimeInterval,
                  repeating interval: TimeInterval?,
                  _ action: @escaping () -> Void) -> KeyboardScheduledTask
}
```

The production implementation wraps `Timer`. `KeyboardView.init` receives a scheduler with a default production value, so all existing call sites remain unchanged:

```swift
init(layout: LimeKeyLayout,
     repeatScheduler: KeyboardRepeatScheduling = MainRunLoopKeyboardScheduler())
```

Replace `repeatTimer` with `repeatTask`. Both the initial delay and periodic repeat use the injected scheduler. A monotonically increasing `repeatGeneration` is captured by callbacks. `stopRepeating()` increments the generation before cancelling, so a callback already dequeued on the run loop becomes a no-op.

Expose only these internal testable operations:

```swift
func cancelActiveInteractions()
var repeatStateForTesting: RepeatState { get }
var hapticDidFireForTesting: (() -> Void)?

func beginKeyInteractionForTesting(code: Int)
func endKeyInteractionForTesting(code: Int)
func cancelKeyInteractionForTesting(code: Int)

var activeTrackerCountForTesting: Int { get }
var activeTargetCountForTesting: Int { get }
var pendingOwnerLongPressCountForTesting: Int { get }
```

`cancelActiveInteractions()` is production lifecycle API. The other members remain internal to the module and are used only by `LimeTests`. The three interaction drivers locate the real `KeyButton` by code and call the same existing begin/end/cancel functions used by `KeyTouchLayer`; they must not contain a second implementation of key behavior. If the code is absent from the installed test layout, they fail through a precondition so a malformed fixture cannot produce a false pass. The haptic observer runs only after a UIKit generator is actually asked to fire; it does not replace or suppress production feedback.

Popup/Shift state tests additionally use one internal helper:

```swift
func configureInteractionStateForTesting(_ state: InteractionTestState)
```

`InteractionTestState` is a small enum with only `.openPopup(primaryCode:selectedCode:)` and `.shiftHeldWithLetter(shiftCode:letterCode:)`. The helper populates the same dictionaries used by production cleanup and is confined to `#if DEBUG`; it does not add runtime behavior to release builds.

The existing private `cancelAllActiveTouches()` becomes the implementation behind `cancelActiveInteractions()`. `viewWillDisappear` calls it through the live `keyboardView`. `KeyboardView.didMoveToWindow()` calls it only when the view previously had a non-`nil` window and now has `nil`.

### New test file and Xcode membership

Create `LimeIME-iOS/LimeTests/KeyboardHapticLifecycleTests.swift`, saved as UTF-8 with BOM. Add its file reference and Sources build-phase entry to the `LimeTests` target in `LimeIME.xcodeproj/project.pbxproj`.

The file contains three fixtures:

```swift
private final class ManualKeyboardScheduler: KeyboardRepeatScheduling
private final class RecordingLifecycleDelegate: KeyboardViewDelegate
private struct KeyboardLifecycleHarness
```

`ManualKeyboardScheduler` stores tasks ordered by deadline and insertion order. `advance(by:)` executes all due callbacks, reschedules repeating tasks, and skips cancelled tasks. It also has `capturedActions` so a test can deliberately invoke a stale callback after cancellation. No test uses `RunLoop`, `asyncAfter`, or wall-clock waits.

`RecordingLifecycleDelegate` records `pressedCodes`, `releasedCodes`, `previewDismissCount`, `popupCancelCount`, `popupHighlights`, `shiftHoldStates`, `longPressCodes`, and `caretMoves`.

`KeyboardLifecycleHarness` constructs the real, final `KeyboardViewController`, forces `loadViewIfNeeded()`, and replaces its live keyboard through an internal `installKeyboardViewForTesting(_:)` method. It must not subclass or mock the controller and the test calls the real `viewWillDisappear` override.

### Exact `KeyboardRepeatSessionTests`

These tests exercise the real scheduler-backed repeat implementation through `KeyboardView` with a one-row layout containing Backspace and the four arrows.

1. `testRepeatableKeyDoesNotRepeatBeforeStartDelay`
   - Call an internal interaction driver that uses the same `beginPlainKeyTouch` path as the touch layer for Backspace.
   - Assert `pressedCodes == [delete]`, haptic count is 1, and state is `.pending`.
   - Advance by `repeatStartDelay - 0.001`.
   - Assert counts and state are unchanged.

2. `testRepeatableKeyRepeatsAtConfiguredCadence`
   - Begin Backspace and advance by `repeatStartDelay + 3 * repeatInterval`.
   - Assert one initial delete plus three repeated deletes, four haptic requests total, and state `.repeating`.
   - Use exact counts; the manual scheduler removes timing tolerance.

3. `testReleaseBeforeStartDelayCancelsPendingRepeat`
   - Begin Backspace, invoke the real release path, and advance by `repeatStartDelay + 3 * repeatInterval`.
   - Assert one delete only, one haptic only, one release callback, and state `.idle`.

4. `testReleaseWhileRepeatingStopsFurtherKeyAndHapticEvents`
   - Advance through two repeats, snapshot key/haptic counts, release, then advance five intervals.
   - Assert counts remain equal to the snapshot and state is `.idle`.

5. `testCancelBeforeStartDelayCancelsPendingRepeat`
   - Same arrangement as test 3, using the real cancel path.
   - Assert no release callback is emitted and no repeat occurs.

6. `testCancelWhileRepeatingStopsFurtherKeyAndHapticEvents`
   - Same arrangement as test 4, using cancellation.
   - Assert counts freeze and state is `.idle`.

7. `testAllRepeatableKeyCodesUseTheSameRepeatSession`
   - Loop over delete, arrow-left, arrow-up, arrow-down, and arrow-right using `XCTContext.runActivity` with the code in the activity name.
   - For each key, advance through two intervals and assert three matching presses total, then cancel and assert counts freeze.

8. `testCancelledDequeuedRepeatCallbackDoesNothing`
   - Begin Backspace and retain the initial-delay action from `capturedActions`.
   - Cancel the interaction, invoke the retained action manually, and advance time.
   - Assert no new key or haptic event and state remains `.idle`.

9. `testCallbackFromOldInteractionCannotRepeatNewInteraction`
   - Capture interaction A's delay callback, cancel A, begin arrow-left as interaction B, then invoke A's callback.
   - Assert A produces no delete, B remains `.pending`, and B repeats arrow-left normally after its own delay.

### Exact `KeyboardViewLifecycleTests`

These tests verify the public cleanup boundary rather than calling `stopRepeating()` directly.

10. `testCancelActiveInteractionsStopsPendingRepeat`
    - Begin Backspace, call `cancelActiveInteractions()`, advance beyond the delay.
    - Assert no repeat and state `.idle`.

11. `testCancelActiveInteractionsStopsActiveRepeat`
    - Enter repeating state, call cleanup, snapshot counts, advance five intervals.
    - Assert key and haptic counts freeze.

12. `testCancelActiveInteractionsIsIdempotent`
    - Begin Backspace and call cleanup three times.
    - Assert state is idle, scheduled tasks are cancelled, preview dismissal is not multiplied for the same tracked key, and no later callback fires.

13. `testLateReleaseAfterLifecycleCleanupDoesNotRestartOrDuplicateRepeat`
    - Begin Backspace, call lifecycle cleanup, then invoke the interaction release driver.
    - Assert no crash, no new press/haptic, and state remains idle.

14. `testLifecycleCleanupAllowsNextInteractionToRepeatNormally`
    - Clean up interaction A, begin interaction B, and advance through two repeats.
    - Assert B receives one initial and two repeated presses and three haptics.

15. `testLifecycleCleanupDoesNotFireHapticByItself`
    - Record haptic count before cleanup in pending and repeating subcases.
    - Assert cleanup never increases the count.

16. `testLifecycleCleanupWithVibrationDisabledKeepsKeyRepeatBehavior`
    - Set `feedbackVibration = false`, begin Backspace, verify repeat presses occur with zero haptics, clean up, and verify presses stop.

17. `testDidMoveToWindowInitialNilStateDoesNotCancelInteractionCapability`
    - Construct the view without a window, explicitly trigger layout, then begin Backspace.
    - Assert repeat works; this catches an unconditional `window == nil` cleanup implementation.

18. `testWindowDetachmentStopsPendingRepeat`
    - Add the keyboard to a retained `UIWindow`, begin Backspace, remove it before the delay, and advance time.
    - Assert state is idle and no repeat occurs.

19. `testWindowDetachmentStopsActiveRepeat`
    - Attach to a window, enter repeating state, remove from the superview, snapshot counts, and advance five intervals.
    - Assert counts freeze.

20. `testDetachThenReattachAllowsFreshRepeatInteraction`
    - Attach, begin/cancel through detachment, reattach the same instance, begin arrow-right, and advance two intervals.
    - Assert arrow-right repeats normally.

21. `testDuplicateDetachAndExplicitCleanupAreIdempotent`
    - Detach the view, then call `cancelActiveInteractions()` twice.
    - Invoke captured stale callbacks and assert no action or haptic.

### Exact `KeyboardViewControllerLifecycleTests`

22. `testViewWillDisappearStopsPendingKeyboardRepeat`
    - Inject a keyboard with a pending Backspace repeat into a real controller instance.
    - Call `viewWillDisappear(false)`, advance beyond the delay, and assert no repeat.

23. `testViewWillDisappearStopsActiveKeyboardRepeat`
    - Inject a keyboard already repeating, call the real disappearance override, snapshot counts, and advance five intervals.
    - Assert counts freeze and repeat state is idle.

24. `testRepeatedViewWillDisappearIsSafe`
    - Call the real override twice for the same active interaction.
    - Assert no duplicated key/haptic callbacks and no live scheduled task.

25. `testViewWillDisappearThenWindowDetachIsSafe`
    - Call controller disappearance, then remove its keyboard view from a window.
    - Invoke captured stale callbacks and assert no activity.

26. `testWindowDetachThenViewWillDisappearIsSafe`
    - Perform the reverse order and make the same assertions.

27. `testViewWillDisappearCleanupDoesNotDependOnFullAccess`
    - Run pending and active subcases with the controller's test Full Access value false and true.
    - Assert identical press/haptic counts and idle final state. This prevents accidental gating of cleanup on `hasFullAccess`.

### Long-press and visual-state tests retained in `TouchLayerGestureTests.swift`

The root-cause fix does not require replacing every long-press `Timer` with the repeat scheduler. The following tests use direct interaction-driver hooks and cleanup assertions; they do not wait for timers.

28. `testLifecycleCleanupInvalidatesAllOwnerLongPressTimers`
    - Begin popup, dual-row, space, generic-long-press, and Lime-options interactions one at a time.
    - Assert the internal pending-owner-timer count is 1 before cleanup and 0 afterward.

29. `testLifecycleCleanupClearsPressedPreviewAndTrackingState`
    - Begin a preview-producing letter, call cleanup, and assert preview dismissal, restored button color, zero active trackers/targets, and idle repeat state.

30. `testLifecycleCleanupCancelsOpenPopupSlideWithoutCommittingSelection`
    - Seed an owner touch with `popupOpen` and a highlighted alternate, call cleanup, and assert one popup-cancel callback, final `nil` highlight, and no selected key press.

31. `testLifecycleCleanupClearsShiftHoldState`
    - Seed active Shift plus letter tracking, call cleanup, and assert tracking is empty and the next letter interaction is not marked as shift-held.

These direct state hooks will be internal and narrowly named for interaction testing; they will call production begin/end/cancel functions rather than duplicate their logic.

### Build and test commands

First run only the new suite on an already booted simulator:

```sh
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj \
  -scheme LimeIME \
  -destination 'platform=iOS Simulator,id=<BOOTED_UDID>' \
  -only-testing:LimeTests/KeyboardHapticLifecycleTests \
  test
```

Then run the related suites:

```sh
xcodebuild -project LimeIME-iOS/LimeIME.xcodeproj \
  -scheme LimeIME \
  -destination 'platform=iOS Simulator,id=<BOOTED_UDID>' \
  -only-testing:LimeTests/TouchLayerGestureTests \
  -only-testing:LimeTests/KeyboardViewControllerTest \
  test
```

Finally run all `LimeTests`, followed by the generic simulator build already used by the project. `git diff --check` and a BOM check will verify edited non-Java Swift files remain UTF-8 with BOM.

### Real-device acceptance test

Automated tests prove that no repeat or haptic request survives lifecycle cleanup. Physical Taptic Engine recovery still requires this iPhone 17 Reminders check with Full Access on and off:

1. In a new reminder, type ten ordinary keys; expect ten distinct haptics.
2. Hold Backspace for at least two seconds, release, and wait three seconds; deletion and vibration must stop immediately on release.
3. Hold Backspace and dismiss the keyboard while it repeats; wait three seconds; deletion and vibration must stop immediately on dismissal.
4. Reopen LimeIME and type ten keys; expect ten distinct haptics and no continuous vibration.
5. Repeat steps 3–4 ten times.
6. Enable the arrow row and repeat the dismissal case with each arrow key.

## Success Criteria

- No key action or haptic request continues after keyboard disappearance or window detachment.
- Holding Backspace or an arrow key still repeats normally while the keyboard remains active.
- Normal release and cancellation behavior is unchanged.
- Reopening the keyboard restores normal interaction without recreating the controller.
- Full Access on and off behave identically for this lifecycle fix.

## Risks and Controls

Cancelling on attachment rather than detachment could discard a legitimate new touch, so the window hook will act only when the new window is `nil`. Cleanup will reuse the existing cancellation path to avoid parallel teardown implementations. Tests will cover duplicate and out-of-order lifecycle events because UIKit host applications may deliver them differently.
