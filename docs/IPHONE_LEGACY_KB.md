# Legacy iPhone Globe / Keyboard Key Plan (iOS)

## Scope

Adds an in-keyboard globe affordance for **home-button iPhones** (SE, 8, and any
iPhone where iOS reports `needsInputModeSwitchKey == true`), as required by App
Store guideline 4.1 / 4.5 (a third-party keyboard must provide a way to switch
keyboards when the system does not). iPad keeps its existing dual-key
`*_ipad.json` layouts (`-200` globe + `-3` keyboard-down side by side). Modern
iPhones (X and newer) are unchanged — iOS surfaces the globe in the system
action bar there and `needsInputModeSwitchKey == false`.

## Behavior matrix

| Device                          | Bottom-row `-3` key                           | Candidate-bar `optionsButton` (right edge) |
| ------------------------------- | --------------------------------------------- | ------------------------------------------ |
| iPad (`*_ipad` layout)          | unchanged — `keyboard.chevron.compact.down`, tap dismiss, long-press LIME menu | unchanged — `line.3.horizontal`, tap = LIME menu |
| iPhone X+ (no switch-key need)  | unchanged                                     | unchanged                                  |
| **iPhone SE / 8 (legacy)**      | **icon `globe`; tap → `advanceToNextInputMode()`; long-press → iOS system input-mode picker; LIME menu long-press cleared** | **icon `keyboard.chevron.compact.down`; tap → `dismissKeyboard()`; long-press → LIME options menu** |

The legacy mode swaps the *roles* of two existing buttons. No JSON changes; no
new keys; nothing visible on iPad or modern iPhones.

## Gate

Single computed flag:

```swift
private var legacyGlobeMode: Bool {
    needsInputModeSwitchKey
        && !isOnPad
        && !currentLayout.id.contains("_ipad")
}
```

`needsInputModeSwitchKey` is the authoritative signal (iOS updates it on
`textWillChange` / `textDidChange`, external keyboard attach, split-view,
etc.). `isOnPad` already uses `traitCollection.userInterfaceIdiom` (the
`UIDevice.current` form is wrong inside an extension; see existing comment at
[KeyboardViewController.swift:172](LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L172)).

## Files touched

All Swift — no JSON, no layout duplication.

### 1. [KeyboardView.swift](LimeIME-iOS/LimeKeyboard/KeyboardView.swift) — bottom-row `-3` rebinding

Existing helper [`configureGlobeButtonForSystemPicker()`](LimeIME-iOS/LimeKeyboard/KeyboardView.swift#L481)
already wires `UIInputViewController.handleInputModeList(from:with:)` to a
button for `.allTouchEvents`, which gives iOS ownership of both the tap
(advance) and long-press (picker). Today it only targets the `code: -200`
button (iPad only).

Changes:

- Introduce `var legacyGlobeMode: Bool = false { didSet { applyLegacyGlobeBinding() } }`
  on `KeyboardView`, set by `KeyboardViewController` whenever the gate may have
  changed (see §3).
- Track the `-3` button in a new `weak var keyboardDoneButton: UIButton?`
  (mirrors the existing `globeButton`). Set/clear it in `makeKeyButton` and the
  paths that null out `globeButton` (lines 281, 294, 306, 319, 332, 404, 425).
- New `applyLegacyGlobeBinding()`:
  - If `legacyGlobeMode == true` **and** `keyboardDoneButton != nil`: swap its
    SF Symbol image to `"globe"`; remove its tap target
    (`keyboardKeyTapped(_:)`); remove its long-press recognizer for
    `keyboardOptionsMenu`; call `configureGlobeButtonForSystemPicker()`
    against `keyboardDoneButton` so iOS owns tap + long-press.
  - Else: restore the SF Symbol from the `KeyDef.icon`
    (`"keyboard.chevron.compact.down"`); re-add `keyboardKeyTapped(_:)` and
    the `keyboardOptionsMenu` long-press recognizer.
- In `makeKeyButton`, when building a `-3` key, branch on `legacyGlobeMode` so
  the initial wiring matches without a follow-up re-bind pass.

The existing `setGlobeKeyVisible(_:)` and `updateGlobeKeyVisibility()` paths
remain — they only govern the (possibly-absent) `-200` key.

### 2. [CandidateBarView.swift](LimeIME-iOS/LimeKeyboard/CandidateBarView.swift) — `optionsButton` rebinding

The right-edge `optionsButton` (`line.3.horizontal`, line 25 declaration,
constructed around line 301) currently fires `optionsTapped` on tap (LIME
options menu).

Changes:

- Add `var legacyGlobeMode: Bool = false { didSet { applyLegacyGlobeMode() } }`.
- In `applyLegacyGlobeMode()`:
  - If `true`: swap the SF Symbol to `"keyboard.chevron.compact.down"`;
    replace the tap target — remove `optionsTapped`, add a new
    `legacyDismissTapped` that calls
    `keyboardViewController?.dismissKeyboard()` (or fires a new delegate
    callback `candidateBarRequestedDismiss(_:)`); attach a
    `UILongPressGestureRecognizer` whose `.began` calls the same handler that
    `optionsTapped` already calls (LIME options menu).
  - Else: restore `"line.3.horizontal"`, re-add `optionsTapped`, remove the
    long-press recognizer.
- `optionsButton.isHidden` logic unchanged — the button is already shown when
  the bar is empty. **Visibility in legacy mode**: we still want the dismiss
  button reachable even when candidates are present, so the legacy-mode path
  forces `optionsButton.isHidden = false` whenever `legacyGlobeMode == true`
  (and reserves the same `optionsColumnWidth` it already uses). Verify in QA
  that this doesn't crowd the candidate strip on the narrowest iPhone SE
  width; if it does, fall back to mirroring `moreButton`'s visibility.

### 3. [KeyboardViewController.swift](LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift) — refresh hook

[`updateGlobeKeyVisibility()`](LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L2397)
is the existing single point that re-evaluates globe state on every relevant
event (`viewWillLayoutSubviews`, `textWillChange`, `textDidChange`, `setLayout`,
shift toggle, symbol mode enter/exit, IM switch). Rename it to
`updateGlobeAndDismissBindings()` and append:

```swift
let legacy = legacyGlobeMode  // computed property defined here, not on subviews
keyboardView?.legacyGlobeMode = legacy
candidateBar.legacyGlobeMode = legacy
```

Audit every call site of the old name (grep already shows three: lines 287,
305, 325) plus the rebind paths that follow `setLayout` and shift changes —
all must call the new entry point.

The `legacyGlobeMode` boolean is the **only** new piece of mutable state on
the controller. No new layout objects, no JSON, no XIB.

## Why programmatic and not new JSON layouts

1. **Maintenance:** the `-3` key lives in ~20 iPhone JSON files
   (`lime_english.json`, `lime_dayi.json`, `lime_array.json`, every
   `lime_*_number.json`, every `lime_*_shift.json`, etc.). A "legacy" JSON
   fork doubles every iPhone layout and any future per-IM edit becomes a
   two-file patch.
2. **Dynamic signal:** `needsInputModeSwitchKey` can flip mid-session (split
   keyboard on iPad, external keyboard attach/detach, app brings a
   hardware-keyboard text field on screen). JSON selection is a one-shot at
   layout load — it can't track this.
3. **Existing scaffolding:** `configureGlobeButtonForSystemPicker` (the
   only nontrivial piece) is already written and battle-tested on iPad. We're
   pointing it at a different button, not writing new code.
4. **iPad symmetry stays clean:** the dual-key iPad story is preserved by
   *not* touching `_ipad` layouts at all.

## Risks / pitfalls

- **Recycling regressions.** Any code path that rebuilds the bottom row (shift
  toggle, symbol mode enter/exit, orientation change, IM switch, popup
  dismiss) must re-apply `legacyGlobeMode` on the freshly-created `-3`
  button. The single-entry refresh (§3) is what makes this tractable — every
  rebuild path must funnel through `updateGlobeAndDismissBindings()`. Search
  for `setLayout(` / `keyboardView?.setLayout` and confirm coverage.
- **Long-press conflict on `-3`.** Today the `-3` key has a
  `keyboardOptionsMenu` long-press recognizer. In legacy mode iOS' system
  picker must own long-press exclusively, so we must `removeGestureRecognizer`
  for the LIME menu recognizer — not just swap the icon. The `wasLongPressed`
  flag path in [KeyboardView.swift:1172](LimeIME-iOS/LimeKeyboard/KeyboardView.swift#L1172)
  must not fire after the system picker engages.
- **App Store reviewer test rig.** Apple's review fleet still includes iPhone
  SE 2/3. Verify on a real / simulated iPhone SE that the globe is visible
  *and* that long-press shows the system picker (a missing entitlement here
  has historically caused 4.1 rejections).
- **Candidate-bar width on iPhone SE.** Forcing `optionsButton` always-visible
  in legacy mode eats `optionsColumnWidth` (0.10 on iPhone). On the narrowest
  device this drops one candidate slot. QA visual diff on iPhone SE 2 (4.7"
  width).
- **First-tap latency.** The system picker only appears if the button is
  registered with `inputModeViewController` *before* the first tap. The
  binding swap must run during initial layout construction, not on the first
  `viewWillLayoutSubviews`.

## Verification checklist

- [ ] iPhone SE 2 simulator: `-3` key renders as globe; short tap cycles IMs;
      long-press shows iOS picker; LIME options menu **not** shown from the
      `-3` key.
- [ ] iPhone SE 2 simulator: candidate-bar right edge shows
      `keyboard.chevron.compact.down`; short tap dismisses keyboard; long
      press shows LIME options menu.
- [ ] iPhone 15 simulator: no visible change to either button; LIME options
      menu still fires from candidate-bar hamburger; `-3` still
      dismisses + long-press shows LIME menu.
- [ ] iPad simulator (any `_ipad` layout): no change. Globe and keyboard-down
      both visible; both behaviors as before.
- [ ] iPhone SE: orientation change, shift toggle, symbol mode in/out, IM
      switch, mode swap to/from emoji panel — globe-on-`-3` survives every
      rebuild.
- [ ] iPhone SE: hardware keyboard attach via simulator → `needsInputModeSwitchKey`
      flips; both bindings revert live; detach restores legacy mode.
