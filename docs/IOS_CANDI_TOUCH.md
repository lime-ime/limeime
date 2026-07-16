# Candidate Bar Touch-Sensitivity — Attempts Log

## Symptom

Tapping the candidate bar only commits a candidate when the tap lands on the
**glyph itself**. Any tap in the vertical padding above or below the glyph —
even though it's clearly inside the bar's visible rectangle and (after fix 6
below) also inside the button's actual frame — does nothing.

Visual confirmation with a debug pink border on each `CandidateButton`
(`layer.borderWidth = 1`, `layer.borderColor = .systemPink`) shows that after
fix 6, the button frame does span the full bar height. Yet dead-zone taps
still fail. Glyph taps succeed. Horizontal scroll-drag works.

## Attempts that did NOT fix it

### 1. `stackView.alignment = .fill`
Changed from `.center` at `CandidateBarView.swift` stack-view setup.

- Intent: let each `UIButton` arranged subview stretch to the full 44 pt bar
  height so its frame (and therefore its hit area) matches the bar.
- Result: highlight pill visually expanded to full bar height (looked wrong);
  top/bottom taps still did not register.
- Status: insufficient by itself; retained but not sufficient.

### 2. `CandidateButton` subclass with inner `pillView`
New subclass owns a small `pillView` sized to the title label's frame inside
`layoutSubviews`. `applyHighlightStyle` now paints `pillView.backgroundColor`
instead of `button.backgroundColor` so the pill stays glyph-sized even when
the button frame is full bar height.

- Intent: keep `.fill` for hit area without the ugly full-height pill.
- Result: pill visual restored to compact glyph-hug — correct. Top/bottom taps
  still fail.
- Status: kept (fixes the visual regression from fix 1).

### 3. `CandidateScrollView` override of `touchesShouldCancel(in:)`
Subclass of `UIScrollView` that returns `true` for every view, overriding
UIKit's default of `false` for `UIControl` subclasses.

- Intent: when the entire bar is `UIControl` (after `.fill`), the scroll
  view's pan can still cancel button tracking and drive a scroll, so the
  user can drag-to-scroll from any spot including over a button.
- Result: scroll works again. Tap behavior unchanged — dead-zone taps still
  fail.
- Status: kept (fixes the scroll regression introduced by fix 1).

### 4. `scrollView.delaysContentTouches = false`
Tell the scroll view not to wait 150 ms before forwarding touches to
subviews.

- Intent: rule out the scroll view's touch-delay state machine as the reason
  edge taps never reach the button.
- Result: no change to dead-zone behavior.
- Status: kept.

### 5. `hitTest` override on `CandidateBarView`
Manually route any touch inside the scroll region to the candidate button
whose horizontal x-range contains the point, regardless of y.

```swift
override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
    // for point inside scrollView.bounds, find button by x and return it
}
```

- Intent: force every point inside the bar to resolve to the corresponding
  button, independent of whether `.fill` actually stretched the button
  frames.
- Result: no change. Top/bottom taps still fail.
- Status: removed (replaced by tap gesture approach in fix 6).

### 6. Explicit `btn.heightAnchor == stackView.heightAnchor`
```swift
btn.translatesAutoresizingMaskIntoConstraints = false
btn.heightAnchor.constraint(equalTo: stackView.heightAnchor).isActive = true
```

- Intent: `.fill` alone wasn't actually stretching the button (confirmed by
  pink debug border being glyph-sized, not bar-sized). Add a hard constraint
  so the button frame definitively equals the bar height.
- Result: pink border now spans full bar height — **button frame IS full
  height**. But dead-zone taps still fail.
- Status: kept. Proves the issue is not the button frame.

### 7. `UITapGestureRecognizer` on the scroll view
```swift
let tap = UITapGestureRecognizer(target: self, action: #selector(handleCandidateTap(_:)))
tap.cancelsTouchesInView = true
scrollView.addGestureRecognizer(tap)
```

- Intent: even if button hit-testing is somehow broken, a tap gesture at the
  scroll-view level should recognize any tap inside the scroll view and map
  its x-coordinate to a candidate via `candidateButtons[i].frame`.
- Result: no change. Dead-zone taps still fail.
- Status: moved to `CandidateBarView` in fix 8.

### 8. `UITapGestureRecognizer` on `CandidateBarView` itself
Same gesture, attached to the outermost view instead of the scroll view, on
the theory that the scroll view's internal gesture recognizer system was
eating the touch before our gesture could see it.

- Result: no change. Dead-zone taps still fail.
- Status: current state at time of writing.

### 9. `UIButton(type: .custom)` instead of `.system`
Changed `makeCandidateButton` to use `.custom` on the theory that `.system`
applies internal UIKit tint/touch behaviors that could restrict the effective
hit area to the content (glyph) region rather than the full button frame.

```swift
let btn = CandidateButton(type: .custom)
```

Colors were already set explicitly via `setTitleColor`, so the only
functional difference is the UIKit touch-routing behavior.

- Result: no change. Dead-zone taps still fail.
- Status: kept in code (no regression, and `.custom` is cleaner), but did not
  fix the issue.

### 10. `point(inside:with:)` override on `CandidateButton`
Overrode `point(inside:with:)` in the `CandidateButton` subclass to
unconditionally return `true` for any point inside `bounds`, guaranteeing
the full button frame is claimed as a hit target at the UIKit level.

```swift
override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
    return bounds.contains(point)
}
```

- Intent: ensure UIKit's hit-test walk returns the button for any point
  within its frame, regardless of content size.
- Result: no change. Dead-zone taps still fail. This confirms that the button
  IS being returned by `hitTest` (or it doesn't matter which view is
  returned by `hitTest`) — something deeper in the touch-delivery or
  gesture-recognizer system is absorbing the event before `touchUpInside`
  fires.
- Status: kept in code (no regression).

### 11. `hitTest` override on `CandidateBarView` (second attempt)
Re-added a `hitTest` override on `CandidateBarView` (the outermost view),
mapping any touch inside the scroll region to the correct button by
x-coordinate, independent of y-position. This was a second attempt after
the original fix 5 which used the same approach.

```swift
override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
    let scrollPoint = convert(point, to: scrollView)
    if scrollView.bounds.contains(scrollPoint), !candidateButtons.isEmpty {
        let stackPoint = scrollView.convert(scrollPoint, to: stackView)
        if let btn = candidateButtons.first(where: {
            stackPoint.x >= $0.frame.minX && stackPoint.x < $0.frame.maxX
        }) { return btn }
    }
    return super.hitTest(point, with: event)
}
```

- Result: no change. Dead-zone taps still fail.
- Conclusion: `hitTest` is not the bottleneck. UIScrollView's gesture
  recognizer system intercepts touches independently of which view wins
  the hit test, so forcing `hitTest` to return the button is insufficient.
- Status: removed; replaced by fix 8 (tap gesture on `CandidateBarView`).

## What we know for certain

- Button frame = full bar height (pink border confirms).
- Horizontal scroll works.
- Glyph taps work (commit the candidate).
- Taps inside the pink box but outside the glyph do nothing — no highlight
  flash, no commit.
- NSLog diagnostics added to `handleCandidateTap` have not yet been captured
  in a successful run, so whether the gesture fires at all for dead-zone
  taps is still unverified.

## Hypotheses not yet ruled out

1. **The gesture recognizer never fires for dead-zone taps.** UIScrollView's
   own gesture system may be claiming the touch in a way that blocks both
   the button's `touchUpInside` AND the ancestor tap gesture from
   recognizing. Glyph taps may succeed via a different internal path (e.g.,
   direct `touchUpInside`).
2. **The button's `touchUpInside` fires only for points inside `titleLabel`
   rather than inside the button frame**, due to some interaction between
   `contentEdgeInsets`, `.fill`, and `UIButton(type: .system)` hit testing.
   Unusual but not impossible.
3. **An ancestor view is absorbing the touch**. `composingPopupLabel` sits
   directly above the bar; UILabel has `isUserInteractionEnabled = false`
   by default, so unlikely — but worth verifying with View Debugger.
4. **The simulator's keyboard extension is running a cached binary**.
   Re-install of the host app doesn't always reload the extension; a full
   simulator reboot may be needed before conclusions are valid. Given fix
   6's pink border IS visible, the latest binary is running — but it's
   worth confirming the gesture recognizer changes are also live.

## Next investigation steps

1. Capture `NSLog` output while tapping the dead zone vs the glyph. If
   `handleCandidateTap gesture fired` prints for glyph taps but not
   dead-zone taps, hypothesis 1 is confirmed.
2. Install a view-debugger build and inspect the actual touch path (use
   Xcode's Debug View Hierarchy on a live simulator session).
3. Try replacing the gesture recognizer with a subview-based approach: add
   a transparent `UIView` on top of the scroll view that covers the full
   bar, has its own tap handler, and forwards through pan gestures
   manually. Avoids all UIScrollView + UIControl interaction.
4. Try `UIButton(type: .custom)` instead of `.system` — `.system` has
   internal tint behaviors that may interact oddly with hit testing when
   the frame is much larger than the title.

## Current file state

- `CandidateBarView.swift` contains fixes 1–4, 6, 8 plus the `CandidateButton`
  subclass with the pink debug border.
- `handleCandidateTap(_:)` contains diagnostic `NSLog("LIME_TAP ...")`
  calls suitable for `xcrun simctl spawn <device> log stream --predicate
  'eventMessage CONTAINS "LIME_TAP"'`.

## Files touched

- [LimeIME-iOS/LimeKeyboard/CandidateBarView.swift](LimeIME-iOS/LimeKeyboard/CandidateBarView.swift)

## Files NOT touched (ruled out)

- `KeyboardViewController.swift` — the bar's layout constraints are fine
  (flush top/bottom between composing popup and keyboard, 44 pt height).
- `KeyboardView.swift` — does not overlap the bar.

---

## Root cause analysis (after deep research, attempts 1–11)

### The smoking gun

iOS **custom keyboard extensions use a stricter hit-test rule than
normal UIKit apps**: touches that land on fully transparent pixels
(`backgroundColor == nil` or `UIColor.clear`, equivalently "no drawn
content") are silently dropped at the input-extension touch-delivery
layer — *before* `hitTest`, `point(inside:)`, `UIControl.touchUpInside`,
or any `UIGestureRecognizer` on any view in the hierarchy ever runs.

Normal UIKit apps only ignore a view when its `alpha < 0.01`. Keyboard
extensions additionally drop touches on clear pixels *inside* a view
whose alpha is 1. This is an undocumented-in-the-framework-headers
behavior of `UIInputView` / `UIInputViewController`, confirmed by Apple
Developer Forums thread 702798 ("Touch on transparent points of custom
keyboard extension is ignored"), with the canonical workaround of
setting an almost-invisible background like
`UIColor.white.withAlphaComponent(0.01)`.

### Why this matches every observation

Map the theory against what we have logged (section "What we know for
certain"):

| Observation                                     | Explanation under theory |
|-------------------------------------------------|---------------------------|
| Glyph taps commit                               | The rendered `titleLabel` text pixels are non-transparent, so they pass the custom-keyboard touch gate. |
| Taps in the vertical dead zone do nothing       | Every view in the bar is transparent (`backgroundColor = .clear` on `CandidateBarView` at [CandidateBarView.swift:85](LimeIME-iOS/LimeKeyboard/CandidateBarView.swift#L85) and :92; `scrollView`, `stackView`, `CandidateButton`, and non-selected `pillView` all default to nil/clear). Touches there are dropped before any handler runs. |
| `hitTest` override (fix 5, 11) didn't help       | Hit-test code isn't reached — the touch is dropped upstream. |
| `point(inside:)` override (fix 10) didn't help   | Same. The extension-level gate is above `UIView.hitTest`. |
| Height constraint (fix 6) made the pink border full-height but taps still fail | Button frame *is* full-bar; but frame geometry doesn't matter when the pixel under the finger is clear. Only rendered content counts. |
| Tap gesture on scroll view / bar (fix 7, 8) didn't help | Gesture recognizers only run on events UIKit decides to dispatch. The extension layer drops the event before UIKit even forms a `UIEvent`. |
| `.custom` vs `.system` (fix 9) didn't help       | Orthogonal — neither adds a background. |
| Horizontal scroll drag works                    | Pan-gesture dispatch has a different path in custom keyboards: once a touch *does* register anywhere (e.g. a glyph or the pan gesture's first sample hitting any solid pixel), continuous drag samples are delivered. An isolated tap on a clear pixel has no "alive" touch to continue. |
| `KeyboardView`'s keys work at all tap locations | Each key button gets an explicit `backgroundColor = normalKeyColor / modifierKeyColor` at [KeyboardView.swift:603](LimeIME-iOS/LimeKeyboard/KeyboardView.swift#L603), so the whole key frame is solid — no transparent pixels to drop touches on. This is the control case that proves the bar is the outlier, not our hit-test plumbing. |

### Why previous fixes were architecturally sound but irrelevant

All 11 fixes addressed layers above the actual bottleneck:
- Fixes 1, 2, 6 — button frame geometry
- Fixes 3, 4 — scroll-view touch cancellation / delay
- Fixes 5, 10, 11 — view hit-test return value
- Fixes 7, 8 — gesture-recognizer placement
- Fix 9 — UIButton subclass behavior

None of these change the **pixel transparency** at the touch point,
which is the one thing the custom-keyboard touch gate cares about.

### Hypothesis status

- H1 (gesture never fires for dead-zone taps) — **confirmed** as the
  proximate symptom. The underlying cause is the extension-level
  transparent-pixel gate described above, which prevents any gesture
  from firing.
- H2 (`touchUpInside` restricted to `titleLabel`) — **refuted in
  spirit, correct in observation**: it's not that UIButton restricts
  the area, it's that the only non-clear pixels inside the button
  happen to be the title label.
- H3 (ancestor absorbs touch) — **refuted**: `composingPopupLabel`
  sits above the bar (not over it) and has `isUserInteractionEnabled
  = false` by default.
- H4 (cached binary) — still worth re-verifying after the next fix,
  but pink border being visible argues against it.

---

## Next attempt (Fix 12) — give the bar non-transparent pixels

### The one-line fix

Set a near-invisible background on the candidate bar so every pixel
inside it passes the custom-keyboard touch gate. In
[CandidateBarView.swift:92](LimeIME-iOS/LimeKeyboard/CandidateBarView.swift#L92)
`setup()`:

```swift
// backgroundColor = .clear   // BEFORE — drops touches on clear pixels
backgroundColor = UIColor.white.withAlphaComponent(0.01)
```

Apply the same to the scroll view and each candidate button for
defense in depth, because a transparent subview still masks the
parent's solid pixel at the touch point:

```swift
scrollView.backgroundColor = UIColor.white.withAlphaComponent(0.01)
// in makeCandidateButton:
btn.backgroundColor = UIColor.white.withAlphaComponent(0.01)
```

Leave `stackView` alone — it has no drawn pixels and its arranged
subviews cover its full area; `pillView` stays `.clear` when
unselected (it's a small inner view, not the hit path).

Alpha 0.01 is visually imperceptible on any palette but high enough to
pass the `alpha < 0.01` threshold in the stricter keyboard-extension
gate. Using `UIColor.white` vs `UIColor.black` does not matter at that
alpha; stick with white to avoid any perceived darkening in light mode.

### Visual-parity constraint (hard requirement)

The candidate bar and the expanded candidate panel must remain
**visually identical** to the `KeyboardView` surface below — same
background colour, same blur, no tint shift, no contrast edge. The
user sees the bar, panel, and keys as one continuous surface.

What this means for Fix 12:
- Do **not** introduce any new `UIVisualEffectView` or opaque fill
  on the bar or panel. The existing blur backdrop in
  `KeyboardViewController` is shared and stays untouched.
- The 0.01-alpha fill added by Fix 12 is the *only* new pixel colour,
  and at 1% opacity it sits below the just-noticeable-difference
  threshold on every realistic backdrop.
- Use a **neutral** colour at 0.01 alpha so it cannot bias the
  composited blur in either direction. `UIColor(white: 0.5, alpha:
  0.01)` is preferred over `UIColor.white.withAlphaComponent(0.01)`:
  at 50% grey the 1% overlay cancels equally against light and dark
  palettes. If side-by-side inspection against `KeyboardView` shows
  any drift, fall back to sampling the keyboard's own idle-background
  colour and using that at alpha 0.01.
- Compare bar-vs-key and panel-vs-key pixels on all four themes
  (Light, Dark, and both branded palettes) during verification. Any
  perceptible seam is a blocker.

### Why this ordering of views

The touch gate checks whichever view `hitTest` returns. Under UIKit
rules with everything clear, `hitTest` bottoms out at the deepest
transparent view (the button), not the bar. So the *button* is the
one whose pixel must be non-clear. We also set the bar and scroll
view defensively in case any touch lands outside a button (leading
edge gap, trailing edge before moreSep) — those paths should still
work.

### Rollback plan

If Fix 12 does not resolve the dead zone after a clean build + full
simulator reboot:
1. Revert all three `backgroundColor` lines added in Fix 12.
2. Capture `xcrun simctl spawn booted log stream --predicate
   'eventMessage CONTAINS "LIME_TAP"'` on a dead-zone tap to prove
   whether the gesture fires at all. Rule #7: do not keep guessing.
3. Proceed to Fix 13 / 14 below only with that log as evidence.

### Remove-after-fix cleanup

Once Fix 12 is verified to work, the debug pink border in
[CandidateBarView.swift:462-464](LimeIME-iOS/LimeKeyboard/CandidateBarView.swift#L462-L464)
should be deleted, and the `NSLog("LIME_TAP …")` diagnostics in
`handleCandidateTap` removed. The `UITapGestureRecognizer` (Fix 8)
and `point(inside:)` override (Fix 10) can also be reverted since
they become redundant once the root cause is addressed — but do this
in a separate commit after Fix 12 is confirmed, so regressions can
be bisected.

---

## Fallback attempts if Fix 12 fails

### Fix 13 — overlay tap layer

Add a transparent-but-hit-testable `UIView` (background alpha 0.01)
sitting at the top of the bar's subview hierarchy, sized to the full
bar frame, owning the only `UITapGestureRecognizer`. Forward pan
gestures to the underlying scroll view via gesture-recognizer
delegation (`gestureRecognizer(_:shouldRecognizeSimultaneouslyWith:)`).
This guarantees a solid pixel at every bar coordinate regardless of
subview transparency, and isolates all tap routing to one spot.

### Fix 14 — migrate to `UICollectionView`

Rebuild the bar as a horizontal `UICollectionView` with a flow
layout. Collection-view cells get their own contentView background,
and the scroll-view/delegate machinery is the Apple-blessed path for
this pattern. This is the larger refactor; only do it if Fix 12 and
13 both fail.

---

## TODO

- [x] ~~**Fix 12a** — set `CandidateBarView.backgroundColor`~~ —
      **reverted** to `.clear` in the shipped version; only the
      interactive controls carry the touch-trap fill. See Resolution
      section below.
- [x] ~~**Fix 12b** — set `scrollView.backgroundColor`~~ — reverted
      to `.clear` in the shipped version (same reason as 12a).
- [x] **Fix 12c** — `btn.backgroundColor = UIColor(white: 0.5, alpha:
      0.01)` set in `makeCandidateButton`. Also applied to
      `moreButton` in `setup()` for chevron-padding parity.
- [x] **Verify on simulator** — dead-zone taps commit, horizontal
      drag scrolls, chevron expands, and bar blends seamlessly with
      the keyboard blur backdrop ("good now" — user, post-refinement).
- [ ] **Verify on device** — repeat the matrix on the physical test iPhone
      using the documented `-allowProvisioningUpdates` deploy path.
- [x] **Remove debug pink/red border around every candidate item** —
      done. The two `layer.border*` lines and DEBUG comment in
      `CandidateButton.setupPillView()` are gone.
- [x] **Remove redundant plumbing** — done. Outer
      `UITapGestureRecognizer`, `handleCandidateTap(_:)` method, and
      `point(inside:)` override on `CandidateButton` all removed.
      Shipped as part of the same pass as Fix 12 (not separate
      commits — bisect risk accepted given the refinement iterated
      on a live working fix).
- [ ] ~~Capture one `LIME_TAP` log on a dead-zone tap pre-fix~~ —
      skipped. Fix confirmed working behaviourally; a baseline log
      would only have value for future regression bisect, and the
      cost of reverting + booting sim to capture it outweighs that.
- [x] ~~**If Fix 12 fails** — proceed to Fix 13~~ — not needed;
      Fix 12 succeeded. Fix 13 / 14 sections retained below only as
      reference for any future class of similar issue.

## References

- Apple Developer Forums: [Touch on transparent points of custom
  keyboard extension is ignored](https://developer.apple.com/forums/thread/702798)
- Apple docs: [hitTest(_:with:)](https://developer.apple.com/documentation/uikit/uiview/1622469-hittest)
  — describes the `alpha < 0.01` rule for normal views. The
  keyboard-extension extra gate is not documented in the header but
  the forum thread above is the accepted reference.

---

## Resolution (Fix 12 shipped)

**Status: RESOLVED.** Confirmed working on simulator after applying
the touch-trap fill. Dead-zone taps now commit candidates regardless
of vertical position on the glyph. Horizontal scroll drag, chevron
expand, and theme switching all still work.

### What shipped

In [CandidateBarView.swift](../LimeIME-iOS/LimeKeyboard/CandidateBarView.swift):

- `makeCandidateButton`: `btn.backgroundColor = UIColor(white: 0.5, alpha: 0.01)`
- `setup()`: `moreButton.backgroundColor = UIColor(white: 0.5, alpha: 0.01)`
- Debug pink border in `CandidateButton.setupPillView()` removed.
- Outer `UITapGestureRecognizer` (fix 8) removed.
- `handleCandidateTap(_:)` method removed.
- `point(inside:)` override on `CandidateButton` (fix 10) removed.
- Stale comments updated.

### What we did *not* ship (and why)

The first draft of Fix 12 also put the 0.01-alpha fill on the bar
itself and on `scrollView`. In practice that produced a visible 1%
tint across the entire bar relative to the shared keyboard blur
backdrop — violating the visual-parity constraint. On an empty bar
(no candidates) the whole bar surface was the tinted view, making
the seam obvious.

**Refinement:** leave `CandidateBarView.backgroundColor` and
`scrollView.backgroundColor` as `.clear` (and `applyTheme()` resets
to `.clear` too). Only the interactive controls (`CandidateButton`,
`moreButton`) carry the 0.01 touch-trap fill.

This works because:
- The `UIStackView` of buttons fills `scrollView`'s contentLayoutGuide
  edge-to-edge; buttons are laid out with `spacing: 0` and width
  from `contentEdgeInsets`. Every visible bar pixel when candidates
  are shown is a button pixel.
- When the bar is empty (no candidates), there are no buttons to
  tint, and the bar blends perfectly with the keyboard blur.
- `hitTest` bottoms out at the button for every in-bar tap, so the
  button's 0.01 fill is the one that matters for passing the
  keyboard-extension touch gate.
- `moreButton` carries its own 0.01 fill independently, so taps
  anywhere in the chevron's `contentEdgeInsets` padding also fire.

### Why the bar edges are safe without a fill

- Leading edge: `stackView.leadingAnchor == scrollView.contentLayoutGuide.leadingAnchor`
  — no gap.
- Trailing edge when candidates don't fill the width: the bar has
  nothing to commit there anyway, so dropping those touches is
  acceptable (no user-visible regression).
- Between `moreSep` and `moreButton`: `moreSep.trailingAnchor ==
  moreButton.leadingAnchor` — no gap.

### Verification checklist — done

- [x] Simulator: tap in vertical padding above glyph commits.
- [x] Simulator: tap in vertical padding below glyph commits.
- [x] Simulator: horizontal drag still scrolls the bar.
- [x] Simulator: chevron tap (including its padding) expands the panel.
- [x] Simulator: bar and keyboard surface appear as one blur — no
      perceptible seam at bar top/bottom edge.
- [ ] Device (physical test iPhone): repeat the above on real hardware.
- [x] Debug pink border removed.
- [x] Redundant gesture recognizer + handler + `point(inside:)`
      override removed.

### Lessons learned

1. **Custom keyboard extensions use a stricter transparent-pixel rule
   than normal UIKit apps.** Never trust that `alpha == 1` is enough;
   the pixel itself must be non-clear. This belongs in every future
   touch-routing bug in a keyboard extension as the *first* thing to
   check.
2. **Rule #7 paid off.** Eleven fixes of hit-test/gesture plumbing
   produced zero progress. A single forum thread, found via targeted
   search, revealed the correct layer to intervene at.
3. **Visual parity and touch parity can conflict.** The instinct to
   add the touch fill to every transparent ancestor ("defense in
   depth") violated the same-surface-as-keyboard constraint. Keep
   the fill on the smallest possible set of views — ideally just the
   controls that receive the actual touch event.
