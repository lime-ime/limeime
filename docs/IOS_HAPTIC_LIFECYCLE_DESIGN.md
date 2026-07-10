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

Required coverage:

1. A repeatable key begins repeating after the configured delay.
2. Normal touch release stops repeat actions.
3. Touch cancellation stops repeat actions.
4. Controller disappearance stops a repeat already in progress.
5. Controller disappearance stops a pending repeat before its initial delay expires.
6. Keyboard window detachment stops a repeat already in progress.
7. Window detachment stops a pending repeat.
8. Calling lifecycle cleanup more than once is harmless.
9. A late touch-end or touch-cancel event after lifecycle cleanup is harmless and does not restart work.
10. Reattaching/reappearing permits a new key interaction and normal repeat behavior.
11. Lifecycle cleanup cancels pending long-press work so no delayed popup/menu action fires afterward.
12. Lifecycle cleanup clears pressed, preview, popup, and shift-hold state through existing cancellation paths.
13. A non-repeatable key remains a one-shot action across the same lifecycle transitions.

Where timing makes direct run-loop tests fragile, repeat scheduling and lifecycle state will be separated behind the smallest testable internal seam. Production cadence remains defined by `LayoutMetrics.Gesture`.

Existing touch-layer, gesture, popup, keyboard-controller, and preference tests will also be run to detect regressions. A simulator can verify event and lifecycle behavior, but final validation of physical haptic recovery requires the reported iPhone 17 scenario in Reminders.

## Success Criteria

- No key action or haptic request continues after keyboard disappearance or window detachment.
- Holding Backspace or an arrow key still repeats normally while the keyboard remains active.
- Normal release and cancellation behavior is unchanged.
- Reopening the keyboard restores normal interaction without recreating the controller.
- Full Access on and off behave identically for this lifecycle fix.

## Risks and Controls

Cancelling on attachment rather than detachment could discard a legitimate new touch, so the window hook will act only when the new window is `nil`. Cleanup will reuse the existing cancellation path to avoid parallel teardown implementations. Tests will cover duplicate and out-of-order lifecycle events because UIKit host applications may deliver them differently.
