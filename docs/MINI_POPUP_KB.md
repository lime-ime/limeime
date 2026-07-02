# Mini Popup Keyboard (iOS)

Reference for the long-press "mini-keyboard" that shows a key's alternate
characters (the `popupKeyboard` / hint of a `KeyDef`). Covers the two supported
usage models, the touch architecture, the behaviours restored/added on
2026-07-02, and where each piece lives.

Status: **shipped** — commits `8a418927` (behaviour) + `747c18ef` (test plan) on
`master`; device-confirmed on WJIP17.

---

## 1. Two usage models

Both open the mini-keyboard by long-pressing a key that has a hint/popup, then
select an alternate. Either gesture may be used interchangeably.

1. **Hold + slide (slide-select).**
   Long-press the key → keep holding → slide up into the mini-keyboard onto the
   desired alternate → **release on it** → the alternate is committed and the
   mini-keyboard dismisses. Flinting (sliding from a neighbouring key straight
   into a popup key) enters this same mode.

2. **Release + tap (sticky).**
   Long-press the key → **release** → the mini-keyboard **stays on screen** →
   **tap** the desired alternate → it is committed and the mini-keyboard
   dismisses. Tapping outside the popup dismisses it without committing.

A **single-alternate** popup additionally commits on a plain release near the
start point (a convenience shortcut — no slide/tap required).

---

## 2. Architecture

The mini-keyboard lives inside the single-owner touch model introduced by the
touch rewrite (see [IOS_TOUCH_REWRITE.md](IOS_TOUCH_REWRITE.md)). There is no
per-button gesture recogniser; one `KeyTouchLayer` owns every touch and routes
it through a per-`UITouch` `TouchTracker` / `OwnerTouchState`.

**Opening** (`KeyboardView.openPopup(touchID:state:)`) — one shared path used by:
- **Long-press:** a `.popup` `OwnerTouchState` schedules a hold timer
  (`popupKeyboardHoldDuration`); on fire, `fireOwnerLongPress` → `openPopup`.
- **Flint:** `touchesMoved` detects a slide onto a `hasPopup` key
  (`landing.hasPopup`), calls `beginFlintPopup`, which installs a `.popup`
  state for the same touch and calls `openPopup` immediately.

`openPopup` sets `popupOpen = true`, fires haptic, and asks the controller to
present the mini-keyboard via `didLongPressPopupKey(_:sourceRect:)`.

**Presentation** (`KeyboardViewController.showPopupKeyboard(for:sourceRect:)`):
- A full-bounds tap-outside **overlay** (`UIControl`, `tag == 9877`,
  `.touchUpInside → dismissPopupKeyboard`) is added first.
- A **`PopupKeyboardView`** (the mini-keyboard) is added on top, centred over the
  source key and clamped to the view edges. `currentPopupView` holds it.

**PopupKeyboardView** builds one `UIButton` per alternate and supports **both**
selection paths:
- Slide: `key(at:slideAllowance:)` + `setHighlightedKey(_:)` driven by the owner
  layer while the finger is down.
- Tap: each button's `.touchUpInside → keyTapped → didSelect` (sticky mode,
  after the finger is released), `.touchDown → keyHighlight`.

**Dismissal** (`dismissPopupKeyboard`): removes the popup + overlay and hides the
key preview. Triggered by: a committed selection (`didSelect` /
`didSelectPopupKey` / single-key `didReleasePopupKey`) or a tap on the overlay.

---

## 3. Behaviours restored / added (2026-07-02)

The touch rewrite had reduced the popup to hold-and-slide only and dismissed it
on release. Three gaps were closed:

### 3.1 Sticky release (usage model 2)
`KeyboardView.endPopupTouch` previously called `keyboardViewDidCancelPopupSlide`
(dismiss) whenever a release did not land on an alternate. It now **does
nothing** in that branch — the mini-keyboard stays up and its own buttons handle
tap-to-select; the overlay handles dismiss. Slide-select on release
(`didSelectPopupKey`) and the single-key commit shortcut are unchanged.

`cleanupOwnerTouch` only clears touch bookkeeping, so leaving the popup open on
release is safe.

### 3.2 Haptic
- **On open:** `openPopup` calls `fireHaptic(force: true)`. The `force` flag
  bypasses the 40 Hz throttle (`minHapticInterval = 0.025`) — otherwise the
  open tick was eaten when the popup opened mid-flint, right after a per-key
  flint tick.
- **While sliding:** `updatePopupSelection` fires a (throttled) tick each time
  the finger moves onto a new alternate, matching main-key flint feel.

### 3.3 Key preview
The main keyboard's callout bubble is reused for popup alternates:
- **Slide:** `highlightPopupKey(_:)` → `showPopupKeyPreview(for:)`.
- **Tap:** `PopupKeyboardView` reports `didHighlight` on `.touchDown` /
  `.touchUpInside`; the controller shows/hides the bubble.
- **Exception:** **single-key mini-popups show no preview** (guarded by
  `PopupKeyboardView.isSingleKey`).

`showPopupKeyPreview` computes the alternate's button rect in KeyboardView space
(`PopupKeyboardView.keyRect(for:in:)`) and calls the existing
`keyboardView(_:showPreviewFor:keyRect:)`; `dismissPopupKeyboard` hides it.

---

## 4. Coordinate chain (hit-testing)

A finger point is resolved to a popup alternate through:

```
touch.location(in: layer)                    // KeyTouchLayer space
  → popupKeyboardPoint(fromLayerPoint:in:)   // layer.convert → KeyboardView space
  → popupKeyAtKeyboardPoint(_:)              // view.convert → controllerView, then
                                             //   popup.convert → PopupKeyboardView space
  → PopupKeyboardView.key(at:slideAllowance:)
  → PopupKeyDetector.key(at:)                // direct-contains, else nearest within allowance
```

`slideAllowance` is `PopupKeyboard.keyHeight * 0.25`, doubled above a key's top
edge so sliding up into the popup is forgiving.

---

## 5. Key files & symbols

| File | Symbols |
|------|---------|
| `LimeKeyboard/KeyboardView.swift` | `openPopup`, `beginFlintPopup`, `fireOwnerLongPress`, `endPopupTouch`, `updatePopupSelection`, `popupKeyboardPoint`, `fireHaptic(force:)`, touch lifecycle (`touchesMoved/Ended/Cancelled`) |
| `LimeKeyboard/KeyboardViewController.swift` | `showPopupKeyboard`, `dismissPopupKeyboard`, `highlightPopupKey`, `showPopupKeyPreview`, `popupKeyAtKeyboardPoint`, `didSelectPopupKey`, `didReleasePopupKey`, `keyboardViewDidCancelPopupSlide`, `PopupKeyboardViewDelegate` (`didSelect`, `didHighlight`) |
| `LimeKeyboard/PopupKeyboardView.swift` | `key(at:slideAllowance:)`, `setHighlightedKey`, `keyRect(for:in:)`, `isSingleKey`, `keyTapped`, `keyHighlight`, `PopupKeyDetector` |

---

## 6. Debugging note — the "flash-vanish" report

The bug was reported as "the mini keyboard is dead, won't stay on screen". The
first hypothesis (a spurious `touchesCancelled` cancelling the touch, since the
I3 review fix dismisses popups on cancel) was **wrong**. On-device `NSLog`
instrumentation showed the dismiss stack was:

```
dismissPopupKeyboard ← keyboardViewDidCancelPopupSlide ← endPopupTouch ← touchesEnded
```

i.e. the popup was being dismissed on the **normal release**, via
`endPopupTouch`'s cancel branch — there was no missing "sticky" behaviour at all.
The real gap was the release-to-stay model (§3.1). Lesson: instrument the actual
dismiss path before theorising about touch cancellation.

---

## 7. Tests

Popup behaviour is UI/gesture-driven and verified on device; the unit target
covers it structurally (`LimeTests/TouchLayerGestureTests` — e.g. the
`openPopup` / `beginFlintPopup` shape and call sites). The Safari-driven
`LimeUITests` (which include popup-slide flows) are excluded from the default
test plan and run only on a prepared simulator — see
[IOS_TOUCH_REWRITE.md](IOS_TOUCH_REWRITE.md) and the `ios-visual-verify` setup.
