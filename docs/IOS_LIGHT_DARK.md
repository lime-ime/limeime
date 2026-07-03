# iOS Light / Dark Keyboard Theme — Implementation Notes

**Last updated:** 2026-04-22
**Scope:** Palette[0] (淺色) and palette[1] (深色) rewritten to use iOS system colors via `UIColor` semantic tokens. Every view that sits on top of the `UIInputView(.keyboard)` blur is made transparent so the native blur shows through. No hex literals in these two palettes.

---

## 1. Why the native look needs the UIInputView blur

`KeyboardViewController` inherits `UIInputViewController`, whose root `view` is a `UIInputView(inputViewStyle: .keyboard)`. That view paints the exact blur + tint Apple uses for the system keyboard — this is not a flat color, so no `systemGray4` / `secondarySystemBackground` / hex fill can reproduce it.

Guessing at flat colors kept looking wrong because **every flat fill painted over the blur**. The fix is to let that blur show through every layer that doesn't truly need to be opaque.

---

## 2. Color helpers

In [KeyboardView.swift](../LimeIME-iOS/LimeKeyboard/KeyboardView.swift):

```swift
/// Resolve an iOS dynamic system color to its light-mode variant so palette[0]
/// stays "Light" even when the OS is in dark mode.
static func iosLight(_ color: UIColor) -> UIColor {
    color.resolvedColor(with: UITraitCollection(userInterfaceStyle: .light))
}

/// Resolve an iOS dynamic system color to its dark-mode variant so palette[1]
/// stays "Dark" even when the OS is in light mode.
static func iosDark(_ color: UIColor) -> UIColor {
    color.resolvedColor(with: UITraitCollection(userInterfaceStyle: .dark))
}
```

These freeze each dynamic `UIColor` to a specific appearance so the palette slot — which represents a user-visible theme choice — stays consistent regardless of the system's current `userInterfaceStyle`.

`resolvedColor(with:)` is public API; it returns a static `UIColor` whose RGB equals what the dynamic color would yield under that trait collection.

---

## 3. Palette[0] — Light

All colors from `UIColor` statics resolved to light. No hex literals.

| Slot | Expression | Resolved value |
|---|---|---|
| `background` | `iosLight(.systemGray4)` | `#D1D1D6` — fallback only; UIInputView blur replaces at runtime |
| `normalKey` | `iosLight(.systemBackground)` | `#FFFFFF` |
| `modifierKey` | `iosLight(.systemGray3)` | `#C7C7CC` — split from letter keys to mimic native iOS English keyboard on a flat backdrop; reads subtly under the UIInputView blur |
| `pressedKey` | `iosLight(.systemGray5)` | `#E5E5EA` |
| `label` | `iosLight(.label)` | `#000000` |
| `modifierLabel` | `iosLight(.label)` | `#000000` |
| `secondaryLabel` | `iosLight(.secondaryLabel)` | `#3C3C43 @ 60%` |
| `candiBackground` | `iosLight(.secondarySystemBackground)` | `#F2F2F7` — fallback only; candi bar is `.clear` at runtime |
| `candiText` | `iosLight(.label)` | `#000000` |
| `candiHighlight` | `iosLight(.systemBackground)` | `#FFFFFF` |

## 4. Palette[1] — Dark

| Slot | Expression | Resolved value |
|---|---|---|
| `background` | `iosDark(.systemGray4)` | `#3A3A3C` — fallback only |
| `normalKey` | `iosDark(.systemGray2)` | `#636366` — letter keys are the *lighter* shade, matching native iOS dark keyboard (letters lighter than modifiers, mirroring Light palette's contrast direction) |
| `modifierKey` | `iosDark(.systemGray4)` | `#3A3A3C` — modifier/function keys are the *darker* shade |
| `pressedKey` | `iosDark(.systemGray)` | `#8E8E93` — still lighter than `normalKey` so press feedback reads as a lift |
| `label` | `iosDark(.label)` | `#FFFFFF` |
| `modifierLabel` | `iosDark(.label)` | `#FFFFFF` |
| `secondaryLabel` | `iosDark(.secondaryLabel)` | `#EBEBF5 @ 60%` |
| `candiBackground` | `iosDark(.secondarySystemBackground)` | `#1C1C1E` — fallback only |
| `candiText` | `iosDark(.label)` | `#FFFFFF` |
| `candiHighlight` | `iosDark(.systemGray2)` | `#636366` — matches `normalKey` so the selected pill reads as a letter-key card |

Palettes 2–5 (Android-ported coloured themes) are unchanged.

---

## 5. Transparency changes

For the native UIInputView blur to be visible under the keys and candidate bar, every subview that used to paint a flat fill is now `.clear`:

| File | Site | Change |
|---|---|---|
| [KeyboardView.swift:264, 282](../LimeIME-iOS/LimeKeyboard/KeyboardView.swift#L264) | root `backgroundColor = palette.background` (init + `applyTheme()`) | → `.clear` |
| [CandidateBarView.swift:85, 92](../LimeIME-iOS/LimeKeyboard/CandidateBarView.swift#L85) | root `backgroundColor = palette.candiBackground` (`applyTheme()` + `setup()`) | → `.clear` |
| [KeyboardViewController.swift:227](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L227), [682, 688](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L682) | `composingPopupLabel.backgroundColor = …candiBackground` | → `.clear` |
| [KeyboardViewController.swift:735](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L735) (init) and the refresh path | `panel.backgroundColor = pal.candiBackground` | → `.clear` (expanded panel) |

The keys themselves (UIButton backgrounds) stay opaque so they read as individual cards on top of the blur.

---

## 6. Expanded candidates panel — the tricky case

The expanded candidates panel overlays the keyboard. If we make it `.clear`, keys show through it. If we paint a flat color, it clearly differs from the keyboard backdrop. Nesting a `UIInputView(.keyboard)` inside the root one composes lighter (double blur). Swapping in a `UIVisualEffectView(effect: UIBlurEffect(style: .systemThickMaterial/.systemChromeMaterial))` produces a different tint from Apple's keyboard.

**Solution:** keep the panel `.clear`, and hide `keyboardView` while the panel is visible. The same UIInputView(.keyboard) backdrop that was painting the keyboard now paints the panel — by construction, pixel-identical.

In [showExpandedCandidates / hideExpandedCandidates](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L1300-L1317):

```swift
private func showExpandedCandidates(_ candidates: [Mapping], selectedIndex: Int = -1) {
    …
    expandedCandidatesPanel?.isHidden = false
    keyboardView?.isHidden = true        // key grid gone — UIInputView blur fills the gap
    …
}

private func hideExpandedCandidates() {
    …
    expandedCandidatesPanel?.isHidden = true
    keyboardView?.isHidden = false
    …
}
```

Side-effects of hiding `keyboardView`: it stops receiving touches while the panel is up (desired — tapping through to the keys is ambiguous UX); any cached layout/shift state persists because the view isn't deallocated.

---

## 7. `resolvedKeyboardTheme`-aware chrome

The composing popup, expanded collapse chevron, and expanded separator used to hardcode `KeyboardPalette.palettes[0]` (always Light). They now take a local `pal = KeyboardPalette.palettes[max(0, min(resolvedKeyboardTheme, 1))]` at [setupKeyboardUI](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L679) so they honor the resolved Light/Dark choice. Coloured themes (2–5) still fall back to Light/Dark chrome via the `{0,1}` clamp so they don't inherit a tinted candidate bar.

---

## 8. CandidateBarView highlight override

[CandidateBarView.swift:325-349](../LimeIME-iOS/LimeKeyboard/CandidateBarView.swift#L325) previously hardcoded the selected-candidate pill to `UIColor.white` for both theme 0 and 1. That's now relaxed: only theme == 1 (Dark) overrides to an elevated gray pill; every other theme (including 0) uses `palette.candiHighlight`. The equivalent override for the expanded panel in [KeyboardViewController.swift:1327](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L1327) mirrors this.

---

## 9. Out of scope / known gaps

- **PopupKeyboardView** (long-press popup) still sets `backgroundColor = palette.background` flat; not converted to a blurred container. Acceptable because the popup lives *above* the keyboard, outside the UIInputView blur area.
- **English-QWERTY modifier split.** Now implemented for palettes[0]/[1] by setting `modifierKey` to `systemGray3` (Light) / `systemGray2` (Dark). Always-on: native iOS English keyboard matches exactly on a flat backdrop, and the split reads subtly under the UIInputView blur for Chinese layouts too. See [IOS_FN_KEY_SPLIT.md](IOS_FN_KEY_SPLIT.md).
- **Liquid Glass / iOS 26** aesthetics.
- **Dark theme review** against a native dark bopomofo screenshot is still pending — palette[1] values are from research agent recommendation, not visually verified.

---

## 10. Verification checklist

1. Build `LimeKeyboard` on iPhone 15 simulator (iOS 17/18).
2. Light system, palette[0]: keyboard backdrop matches native bopomofo kb; all keys white; candi bar flush with keyboard (no flat strip); expanded panel backdrop identical to keyboard backdrop; selected candidate is a white pill.
3. Dark system, palette[1]: dark blur backdrop; charcoal keys; candi bar flush; expanded panel matches.
4. Force palette[0] while OS is Dark → keyboard stays Light; force palette[1] while OS is Light → keyboard stays Dark (proves `iosLight` / `iosDark` freeze).
5. Switch to palettes 2–5 → Android-ported colours unchanged.
6. `LimeTests` scheme passes.
