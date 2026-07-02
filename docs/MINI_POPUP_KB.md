# Mini Popup Keyboard

Reference for the long-press "mini-keyboard" that shows a key's alternate
characters (the `popupKeyboard` / hint of a `KeyDef`). Sections 1–7 describe the
**iOS** implementation (the two usage models, touch architecture, and behaviours
restored/added on 2026-07-02); section 8 covers the matching **Android** work.

Status: **shipped** on `master`, device-confirmed. iOS: `8a418927` (sticky +
haptic + preview), `22b9fdd3` (single-key layout popups fire on release, e.g. the
123 key). Android: `a54265ed` (multi-key slide-select, iOS parity). Test plan:
`747c18ef`.

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
start point — no slide/tap required. This covers both `popupCharacters`-based
single keys and single-key `popupKeyboard` **layouts** such as the "123" key's
`popup_symbol_mode` (mode switch); see §3.4.

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

### 3.4 Single-key layout popups fire on release — the 123 key (`22b9fdd3`)

The "123" mode key's popup is `popup_symbol_mode`: a single-key **layout**
(`code -2` symbol switch) with empty `popupCharacters`. So `endPopupTouch`'s
single-key shortcut (`popupCharacters.count == 1`) never matched, and the lone key
required a flint/slide to fire. `endPopupTouch` now also fires directly on
release-near-press when the **open popup layout has one key**, via a defaulted
`keyboardViewCurrentPopupIsSingleKey` delegate query answered from
`PopupKeyboardView.isSingleKey`. This covers any single-key `popupKeyboard`
layout, not just 123, and matches Android's single-key model.

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
| `LimeKeyboard/KeyboardViewController.swift` | `showPopupKeyboard`, `dismissPopupKeyboard`, `highlightPopupKey`, `showPopupKeyPreview`, `popupKeyAtKeyboardPoint`, `didSelectPopupKey`, `didReleasePopupKey`, `keyboardViewCurrentPopupIsSingleKey`, `keyboardViewDidCancelPopupSlide`, `PopupKeyboardViewDelegate` (`didSelect`, `didHighlight`) |
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

---

## 8. Android parity

Android's mini-keyboard is the AOSP-derived `LIMEKeyboardBaseView`
(`LimeStudio/app/src/main/java/net/toload/main/hd/keyboard/`). It selects between
two models via three `isLargeScreen` checks; `isLargeScreen` is force-set true
(`//Force turn off fling selection now`), giving:

| Popup | Selection | Preview |
|-------|-----------|---------|
| Multi-key | **Sticky + slide** (see below) | yes (`setPreviewEnabled(size > 1)`) |
| Single-key | Fling: DOWN injected at open, release commits | no |

**Multi-key both-models (`a54265ed`, iOS parity).** Classic AOSP fling injects a
DOWN at open so *every* release commits — incompatible with sticky. To get both
(like iOS) the initial DOWN is skipped for multi-key:

- `onLongPress` shows the popup and resets `mMiniKeyboardSlideEntered = false`; no
  DOWN is injected for multi-key.
- In `onModifiedTouchEvent`, the popup-owning finger (`mMiniKeyboardTrackerId`) is
  intercepted. While it stays outside the mini-keyboard bounds
  (`isTouchOutsideMiniKeyboard`) nothing is forwarded, so a plain release leaves
  the popup **sticky** (tap-to-select via the popup window).
- The first time the finger enters the bounds, a synthetic DOWN is injected there
  and `mMiniKeyboardSlideEntered` is set; subsequent events forward, so a
  **slide-and-release commits** (enter-and-lift in one motion sends DOWN+UP). This
  mirrors iOS `beginFlintPopup`.

Single-key fling, tap-outside dismiss (`dismissPopupKeyboardOnOutsideTouch`), and
the preview popup are unchanged.

Key symbols: `LIMEKeyboardBaseView.onLongPress`, `onModifiedTouchEvent`,
`isTouchOutsideMiniKeyboard`, `generateMiniKeyboardMotionEvent`,
`dismissPopupKeyboard`, `mMiniKeyboardSlideEntered`, `mMiniKeyboardTrackerId`.

### iOS vs Android

| Aspect | iOS | Android |
|--------|-----|---------|
| Multi-key sticky tap | ✅ | ✅ |
| Multi-key hold→slide→release | ✅ | ✅ (`a54265ed`) |
| Single-key: no preview, commit on release | ✅ | ✅ |
| Multi-key preview | ✅ | ✅ |
| Tap-outside dismiss | ✅ (overlay) | ✅ (`dismissPopupKeyboardOnOutsideTouch`) |
