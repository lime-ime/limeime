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

Tests will exercise lifecycle behavior through observable repeat actions rather than attempting to inspect the physical Taptic Engine in the simulator.

### Test seam and observations

UIKit does not provide a reliable way to construct synthetic `UITouch` objects in a unit test, and real-time `Timer` assertions are prone to slow or flaky tests. The production code will therefore expose the smallest internal test seam needed to drive the existing state transitions without making test-only behavior part of the app API.

The seam will allow tests to:

- begin a key interaction using a real `KeyDef`;
- advance a controllable scheduler past the repeat-start delay and repeat interval;
- finish or cancel the interaction;
- request lifecycle cancellation directly;
- observe committed key codes, release callbacks, preview dismissal, popup cancellation, shift-hold state, and requested haptic events through recording delegates/closures;
- query only coarse interaction state needed for assertions: idle, repeat pending, or repeating.

The scheduler abstraction will cover only delayed and repeating keyboard-interaction work. Production will continue to use the main run loop and the durations in `LayoutMetrics.Gesture`; tests will use a deterministic fake clock. Tests must assert externally visible behavior first. Timer/state inspection is supplemental and must not become the only proof that work stopped.

### Repeat behavior baseline

1. **Repeat starts at the configured boundary.** Begin Backspace, advance to just before `repeatStartDelay`, and assert only the initial delete and initial haptic were requested. Advance through the boundary and one `repeatInterval`; assert repeated delete and haptic requests occur at the expected cadence.
2. **A normal release stops an active repeat.** Start Backspace, advance until at least two repeat callbacks occur, release it, record counts, advance several more intervals, and assert the counts remain unchanged and state is idle.
3. **A normal release stops a pending repeat.** Begin Backspace and release before `repeatStartDelay`; advance beyond both delay and interval and assert there is no repeated delete or haptic.
4. **Touch cancellation stops active and pending repeat.** Run the same two timing arrangements using cancellation rather than release. Assert no post-cancellation actions and no lingering pressed/preview state.
5. **Every repeatable key follows the policy.** Parameterize Backspace and the four arrow keys. Confirm each repeats while held and stops through the common cleanup path, preventing a fix that only special-cases Backspace.

### Controller lifecycle coverage

6. **Disappearance stops an active repeat.** Start Backspace, enter repeating state, invoke `KeyboardViewController.viewWillDisappear`, advance the fake scheduler, and assert no further key or haptic requests.
7. **Disappearance stops a pending repeat.** Begin Backspace, invoke disappearance before the delay, then advance beyond the delay. Assert repeat never starts.
8. **Disappearance is safe with no active touch.** Invoke `viewWillDisappear` on an idle controller and assert existing popup/preview teardown still completes without extra key, release, or haptic callbacks.
9. **Repeated disappearance is idempotent.** Invoke disappearance twice around an active interaction. Assert cleanup callbacks are not duplicated and no work is rescheduled.
10. **Reappearance permits fresh input.** After disappearance cleanup, simulate appearance and begin a new Backspace interaction. Assert initial and repeated actions work normally, proving cancellation does not permanently disable feedback or scheduling.

### View/window lifecycle coverage

11. **Window detachment stops an active repeat.** Place `KeyboardView` in a test `UIWindow`, begin repeat, remove it from the window, advance time, and assert no more key or haptic requests.
12. **Window detachment stops a pending repeat.** Remove the view before `repeatStartDelay`, advance time, and assert repeat never starts.
13. **Initial construction does not count as detachment.** Construct and lay out a keyboard whose `window` is initially `nil`; assert this does not emit cleanup callbacks or prevent its first interaction.
14. **Window attachment does not cancel input state.** Attach an idle keyboard to a window, start a new interaction, and confirm normal repeat behavior. The lifecycle hook must act only on transition from a non-`nil` window to `nil`.
15. **Controller disappearance followed by window detachment is idempotent.** Exercise the order expected during ordinary dismissal and assert a single effective cleanup.
16. **Window detachment followed by controller disappearance is idempotent.** Exercise the defensive reverse order possible in a host transition and assert the same outcome.
17. **Detach and reattach permits fresh input.** Detach after cleanup, reattach the same keyboard view, begin a new interaction, and assert keys and haptics work again.

### Out-of-order event robustness

18. **Late release after lifecycle cleanup is harmless.** Cancel lifecycle state first, then deliver the interaction's release path. Assert no crash, duplicate release action, repeat restart, or negative state transition.
19. **Late touch cancellation after lifecycle cleanup is harmless.** Repeat the case with cancellation and the same assertions.
20. **A stale repeat callback is harmless.** Capture a scheduled repeat callback, perform lifecycle cleanup, then deliberately execute the stale callback as a hostile scheduler would. It must verify the current interaction generation/state and produce neither a key action nor a haptic.
21. **A stale long-press callback is harmless.** Capture a pending popup/menu/space long-press callback, clean up, then execute it. Assert no popup, menu, caret, key, or haptic callback occurs.
22. **An old callback cannot affect a new interaction.** Clean up interaction A, start interaction B, then execute A's stale callback. Assert B remains active and A produces no action. This guards against a stale timer cancelling or repeating the wrong key after reappearance.

### Other active interaction state

23. **Pending long presses are cancelled by disappearance and detachment.** Parameterize popup, generic-long-press, Lime options, dual-row, and space-bar long-press behaviors. Begin each, clean up before its threshold, advance time, and assert its long-press delegate method never fires.
24. **Pressed visuals and previews are cleared.** Begin a preview-producing letter key, perform lifecycle cleanup, and assert its background returns to the restored color and preview dismissal is requested exactly once.
25. **Popup slide state is cleared.** Open a popup, select/highlight an alternate, clean up, and assert popup-slide cancellation and highlight clearing occur, with no alternate committed.
26. **Shift-hold state is cleared.** Begin Shift as part of a multi-touch sequence, clean up, and assert the delegate observes inactive shift-hold state and a later letter is not treated as part of the stale hold.
27. **Space caret mode is cleared.** Enter space-bar caret movement, clean up, then execute late movement/end events. Assert no further caret movement or space insertion occurs.

### Non-repeat and feedback safeguards

28. **Ordinary letters remain one-shot.** Parameterize lifecycle cleanup before and after release for a non-repeatable key. Assert exactly one committed character and no delayed action.
29. **Lifecycle cleanup itself emits no haptic.** Record haptic requests while cleaning pending and active interactions. The count must freeze at cleanup rather than adding a cancellation pulse.
30. **Haptics resume on the next valid press.** After every cleanup entry point, begin a fresh ordinary key press and assert exactly one new haptic request. This is the unit-level analogue of the reported “no more haptic feedback” symptom.
31. **Vibration-disabled behavior remains disabled.** Repeat representative disappearance and detach cases with `feedbackVibration == false`; assert no haptic request before, during, or after cleanup while key behavior remains correct.
32. **Full Access is irrelevant.** Controller-level tests run representative active and pending repeat cleanup with the controller's Full Access state both true and false and assert identical results.

### Timing and flake controls

- Deterministic scheduler tests will use exact virtual-time boundaries and never sleep.
- Any UIKit integration test that must use the main run loop will wait only for a bounded expectation and will assert a stable count after cancellation; it will not depend on physical haptic delivery.
- Tests will avoid asserting private timer object identity. They will assert callback counts, delegate output, and idle/pending/repeating state.
- Parameterized cases will report the key or lifecycle path in assertion messages so a failure identifies the broken boundary.
- Cleanup tests will advance by at least three repeat intervals after cancellation, enough to expose a surviving repeating callback without slowing the suite under a fake clock.

### Suite-level and device verification

Existing `TouchLayerGestureTests`, `KeyboardViewControllerTest`, popup/gesture tests, and preference tests will run after the focused tests. A simulator smoke test will open a normal text field, hold Backspace, dismiss the keyboard during the hold, reopen it, and confirm later keys still respond. The simulator verifies event and lifecycle behavior but not physical vibration.

Final real-device verification on the iPhone 17 will use Reminders with Full Access both on and off:

1. Type an ordinary key and confirm one haptic.
2. Hold and release Backspace; confirm repeat and immediate stop.
3. Hold Backspace while dismissing the keyboard or leaving the editing field; confirm vibration and deletion stop immediately.
4. Reopen the keyboard and type at least ten ordinary keys; confirm one haptic per press with no continuous firing or lost feedback.
5. Repeat dismissal/reopening ten times to exercise lifecycle ordering.
6. Repeat with an arrow row enabled, if configured, to cover the other repeatable-key family.

## Success Criteria

- No key action or haptic request continues after keyboard disappearance or window detachment.
- Holding Backspace or an arrow key still repeats normally while the keyboard remains active.
- Normal release and cancellation behavior is unchanged.
- Reopening the keyboard restores normal interaction without recreating the controller.
- Full Access on and off behave identically for this lifecycle fix.

## Risks and Controls

Cancelling on attachment rather than detachment could discard a legitimate new touch, so the window hook will act only when the new window is `nil`. Cleanup will reuse the existing cancellation path to avoid parallel teardown implementations. Tests will cover duplicate and out-of-order lifecycle events because UIKit host applications may deliver them differently.
