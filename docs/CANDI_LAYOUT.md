# Candidate Bar and Expanded Panel Layout

This document explains the geometry of the candidate bar
([CandidateBarView.swift](../LimeIME-iOS/LimeKeyboard/CandidateBarView.swift))
and the expanded candidates panel
([KeyboardViewController.swift `reloadExpandedCandidates`](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift)).
The two surfaces share the same metrics for "expand-in-place" parity —
when the user taps the chevron, row 1 of the panel must look
pixel-identical to the bar that was there a moment ago.

For the raw constants and per-idiom values, see
[LAYOUT_PARAM.md](LAYOUT_PARAM.md). This doc focuses on *how the pieces
fit together*.

For the host-app-specific Safari / Gemini extra top-space investigation, see
[SAFARI_EXTRA_SPACE.md](SAFARI_EXTRA_SPACE.md). That issue is intentionally not
treated as candidate-bar geometry here.

## Android candidate theming

Android candidate UI is inflated with the keyboard theme context from
`LIMEService.initialViewAndSwitcher()`. The service wraps the base context with
`ContextThemeWrapper(getKeyboardTheme())`, and `CandidateView` resolves
`R.attr.LIMECandidateView`, falling back to `R.style.LIMECandidateView`.

Candidate colors and action icons therefore belong in the `LIMECandidateView`
style family, not in layout `android:src` attributes and not in ad-hoc runtime
tints. The themed attributes are declared in
[`attrs.xml`](../LimeStudio/app/src/main/res/values/attrs.xml) and assigned in
[`styles.xml`](../LimeStudio/app/src/main/res/values/styles.xml):

| Attribute | Runtime use |
|---|---|
| `suggestHighlight` | Candidate selection background. |
| `voiceInputIcon` | Right-side voice button when the candidate row is empty. |
| `emojiButtonIcon` | Left-side emoji button when the candidate row is empty. |
| `ExpandDownButtonIcon` | Right-side down chevron before expansion. |
| `ExpandUpButtonIcon` | Right-side up chevron after expansion. |
| `closeButtonIcon` | Candidate dismiss button. |
| `keyboardShowIcon` | Physical-keyboard mode button for showing the soft keyboard. |
| `candidateBackground`, `candidateNormalTextColor`, `candidateNormalTextHighlightColor`, `composingCodeColor`, `composingCodeHighlightColor`, `spacerColor`, `selKeyColor`, `selKeyShiftedColor`, `composingTextColor`, `composingBackgroundColor` | Candidate row, expanded row, composing strip, spacers, and hardware-selection text colors. |

The six Android candidate theme variants are:
`Light`, `Dark`, `Pink`, `TechBlue`, `RelaxGreen`, and `FashionPurple`.
The voice and emoji controls both have matching selector drawable families for
those six variants:

```
btn_voice_light / btn_emoji_light
btn_voice_dark / btn_emoji_dark
btn_voice_pink / btn_emoji_pink
btn_voice_tech_blue / btn_emoji_tech_blue
btn_voice_relax_green / btn_emoji_relax_green
btn_voice_fashion_purple / btn_emoji_fashion_purple
```

`CandidateInInputViewContainer` reads the resolved drawables from its
`CandidateView` owner:

- `candidate_emoji` uses `mDrawableEmojiInput`.
- `candidate_right` uses `mDrawableVoiceInput` when there are no candidates.
- `candidate_right` swaps to `mDrawableExpandDown` or `mDrawableExpandUp` when
  candidates are present.

When swapping these icons, clear any existing image color filter before setting
the themed drawable. A previously tinted `ImageButton` can otherwise tint the
next drawable and make a theme-specific asset render with the wrong color.

The expanded candidate popup must reuse the same candidate colors and action
button assets. It should cover the original candidate bar when the soft keyboard
is visible, and its first row should preserve the collapsed row's visual metrics
so the dismiss button, composing text, and candidates look expanded in place.

---

## 1. The candidate bar

The candidate bar is one fixed-height surface at the top of the keyboard.
It contains every candidate-related control: the left dismiss button, the
composing keyname / reverse-lookup strip, the horizontally-scrolling
candidate cells, the right separator, and the expand chevron.

```
Horizontal zones

 bar.leading                                                   bar.trailing
     │                                                              │
     ▼                                                              ▼
 ┌──────┬──────────────────────────────────────────────────────┬─┬─────┐
 │  X   │ composing keyname / reverse lookup strip             │ │     │
 │      │ ㄉㄚˊ   or   日=ㄇㄧˋ; ㄖˋ                             │ │     │
 │      │                                                      │ │     │
 │      │   raw    first    second    third                    │ │  ▾  │
 │      │   dj     答       打        搭       ...              │ │     │
 └──────┴──────────────────────────────────────────────────────┴─┴─────┘
   ▲      ▲                                              ▲      ▲   ▲
   │      │                                              │      │   │
   │      scrollView.leading                             │      │   moreButton
   │      composingLabel.leading                         │      │   width = Chevron.buttonWidth
   │                                                     moreSep
   dismissButton                                         width = CandidateBar.dividerWidth
   width = Chevron.buttonWidth / 2

Vertical bands

 y=0 ┌──────────────────────────────────────────────────────────────┐
    │ composingLabel: composing keyname or reverse lookup           │
    │ height = ceil(composingStripFont.lineHeight) + labelHeightPad │
    ├──────────────────── stripHeight ─────────────────────────────┤
    │ stripHeight is counted from bar.top downward:                 │
    │ y = 0 ... stripHeight                                         │
    │ candidate glyph zone                                          │
    │ CandidateButton titleLabel is shifted down by stripHeight / 2 │
    │ selection pill hugs titleLabel + pillPadX / pillPadY          │
    └──────────────────────────────────────────────────────────────┘ y=candidateBarHeight
```

### Component map

| Component | View / owner | Parameter(s) | Geometry / role |
|---|---|---|---|
| Whole bar | `CandidateBarView` | `ComposingPopup.{Phone,Pad}.barBaseHeight * candidateFontScale` | Total candidate-bar height. The controller stores this as `candidateBarHeight` and applies it to `candidateBarHeightConstraint`. |
| Left dismiss button | `dismissButton` in `CandidateBarView` | `Chevron.buttonWidth(isPad:) / 2`; height = `candidateBarHeight - composingStripHeight`; centerY offset = `composingStripHeight / 2` | Clears composing without touching document text. It sits only in the candidate glyph zone, not in the keyname strip. |
| Keyname / reverse-lookup strip | `composingLabel` in `CandidateBarView` | `ComposingPopup.labelLeading`; `labelTrailingInset`; `labelTopInset`; `stripFontSize`; `labelHeightPad`; `textAlpha` | Overlays the top of the scroll region. During composing it shows key names such as `ㄉㄚˊ`; after commit it reuses the same label for reverse lookup such as `日=ㄇㄧˋ; ㄖˋ`. |
| Candidate viewport | `scrollView` in `CandidateBarView` | leading = `dismissButton.trailingAnchor`; trailing = `moreSep.leadingAnchor` | The visible horizontal candidate area. The keyname / reverse-lookup label is drawn above this same region. |
| Candidate row content | `stackView` inside `scrollView` | `stackView.spacing = 0`; `CandidateBar.candidateHPad`; `ComposingPopup.stripHeight` | Holds `CandidateButton`s back-to-back. There is no stack spacing; visible item spacing comes from each button's left/right `candidateHPad`. |
| Candidate cell | `CandidateButton` | `candidateHPad`; `candidateFontSize`; `composingCodeFontSize`; `CandidateBar.composingCodeDimAlpha` | Full-height tap target. Normal candidates use `candidateFont`; raw composing-code records use `composingCodeFont` and dimmed text. |
| Candidate vertical bias | `CandidateButton.contentEdgeInsets` | `ComposingPopup.stripHeight(isPad:) / 2` | Shifts title text and selection pill down so candidate glyphs clear the overlaid strip. |
| Selection pill | `CandidateButton.pillView` | `pillPadX`; `pillPadY`; `pillCornerRadius`; `darkThemePill`; `candiHighlight` | Inner highlight view that hugs the title label instead of filling the whole button. |
| Right separator | `moreSep` in `CandidateBarView` | `CandidateBar.dividerWidth`; `dividerHeight`; `separatorAlpha` | Hairline divider immediately left of the chevron. Its trailing edge is pinned to `moreButton.leadingAnchor`. |
| Expand chevron | `moreButton` in `CandidateBarView` | `Chevron.buttonWidth(isPad:)`; `Chevron.iconSize(isPad:)`; vertical content inset = `composingStripHeight / 2` | Opens the expanded candidates panel. Width is fixed per idiom and independent of bar height. |

### Main layout formulas

| Quantity | Formula | Notes |
|---|---|---|
| `candidateBarHeight` | `ComposingPopup.barBaseHeight(isPad:) * candidateFontScale` | Overall bar height. Defaults at fontScale 1.1: iPhone `58 * 1.1 ≈ 64pt`, iPad `74 * 1.1 ≈ 81pt`. |
| `composingStripHeight` | `ComposingPopup.stripHeight(isPad:)` | Reserved top strip area counted from `bar.top` downward (`y = 0 ... stripHeight`): iPhone `22pt`, iPad `28pt`. This value is not multiplied by `candidateFontScale`. |
| `composingStripFont` | `ComposingPopup.stripFontSize(isPad:) * candidateFontScale` | Font for composing keyname and reverse lookup. Uses STHeiti TC for visible Bopomofo tone marks. |
| `candidateFont` | `ComposingPopup.candidateFontSize(isPad:) * candidateFontScale` | Font for normal candidate words. |
| `composingCodeFont` | `ComposingPopup.composingCodeFontSize(isPad:) * candidateFontScale` | Font for raw composing-code records. |
| Candidate horizontal gap | `candidateHPad + candidateHPad` between adjacent titles | The stack has zero spacing. Each button contributes its own left and right padding. |
| Candidate vertical shift | `bias = composingStripHeight / 2` | Applied as `contentEdgeInsets.top = bias`, `bottom = -bias`. |
| Dismiss width | `Chevron.buttonWidth(isPad:) / 2` | iPhone `20pt`, iPad `26pt` with current constants. |
| Chevron width | `Chevron.buttonWidth(isPad:)` | iPhone `40pt`, iPad `52pt`. Also used by the expanded panel row-wrap calculation. |

### State-dependent text in the strip

The top strip is a single label with two meanings:

| State | Text source | Example | Lifetime |
|---|---|---|---|
| Composing | `showComposingPopup()` maps `mComposing` through `keyname(_:)` | `ㄉㄚˊ` | Updates on every composing-code change; clears when composing is cancelled or committed. |
| Reverse lookup | `showReverseLookup(_:)` formats the committed word's lookup codes | `日=ㄇㄧˋ; ㄖˋ` | Reuses the same `composingLabel`. Guarded by `isShowingReverseLookup` so `hideComposingPopup()` does not immediately clear it during post-commit cleanup. |

Because both states use the same view, reverse lookup inherits the exact
same leading/trailing constraints, font, color, and clipping behavior as
the composing keyname.

### Chevron button — width independent of bar height

The chevron button is *not* a bar-height square. It uses an explicit
per-idiom width so the visible left/right padding around the chevron
glyph doesn't blow up at large font scales:

| Idiom | `Chevron.iconSize` | `Chevron.buttonWidth` | Padding each side |
|---|---|---|---|
| iPhone | 18 | 40 | (40 − 18) / 2 = 11pt |
| iPad | 22 | 52 | (52 − 22) / 2 = 15pt |

The button height still tracks the bar height (so the whole bar's
trailing region is tappable), but width is constant. The reclaimed
horizontal space goes to the candidate scroll view, fitting more
cells on row 1.

### Why `moreSep` is anchored to `moreButton.leadingAnchor` (not a fixed offset)

```swift
moreSep.trailingAnchor.constraint(equalTo: moreButton.leadingAnchor)
```

Zero-gap abutment. If the chevron's button width is later tuned, the
moreSep follows automatically. The expanded panel does the same with
`sep.trailingAnchor == collapseBtn.leadingAnchor`, so the divider
position stays in sync between the two surfaces.

---

## 2. The keyname strip overlay

The keyname strip is the most subtle piece of the design. It is
rendered **inside** the bar (overlaid on top of the leading region of
the candidate scroll view) — not in a separate band above the bar.

### Why an overlay and not a separate band

Two earlier surfaces failed (see
[IPAD_ASSIST_BAR.md §8](IPAD_ASSIST_BAR.md) for the full story):

1. **In-keyboard `composingPopupLabel` strip above the bar** — wasted
   ~22pt of vertical space all the time (even when not composing). The
   constants are kept in `LayoutMetrics.Vestigial.InKeyboardComposingPopup`
   but the height is forced to 0 at runtime.
2. **iPad assist-bar `assistBarComposingLabel`** — silently ignored by
   iOS for keyboard extensions. Constants kept in
   `LayoutMetrics.Vestigial.AssistBarLabel`; never rendered.

The active solution (RC3 Option A) is the overlay strip: zero extra
vertical space above the bar, candidate glyphs are biased down to clear
the keyname text region.

### Strip metrics (per idiom)

| Idiom | `stripHeight` | `stripFontSize` | Font face |
|---|---|---|---|
| iPhone | 22 | 14 | STHeitiTC-Light |
| iPad | 28 | 18 | STHeitiTC-Light |

Font choice is documented inline on `composingStripFont`
([CandidateBarView.swift](../LimeIME-iOS/LimeKeyboard/CandidateBarView.swift)):
PingFang TC and SF render Bopomofo tone marks (`ˇ ˋ ˊ ˙`) as
near-invisible IPA-style accents. STHeiti TC is the only system font
on iOS where the tone glyphs render at a glance-readable size without
per-character scaling.

### Strip layout constraints

| Anchor | Value | Effect |
|---|---|---|
| `composingLabel.leadingAnchor` | `bar.leadingAnchor + 8` | 8pt left margin (`ComposingPopup.labelLeading`). |
| `composingLabel.trailingAnchor` | `moreSep.leadingAnchor − 4` | Strip ends 4pt before the divider (`labelTrailingInset`). |
| `composingLabel.topAnchor` | `bar.topAnchor + 0` | Top-flush. |
| `composingLabel.heightAnchor` | `ceil(font.lineHeight) + 2` | Padded by 2pt (`labelHeightPad`) so STHeiti tone glyphs at the top of the em-box don't get clipped against the input view's top boundary. |

The strip's *visible* height (from `composingStripHeight`) is independent
of the label's *frame* height — the label can be taller (full lineHeight
+ pad) so the glyph isn't clipped, while the bias inset on candidate
cells uses the smaller `composingStripHeight` to save vertical space.

### Reverse lookup reuse

After the user commits a candidate, the same `composingLabel` strip is
reused to show that candidate's codes in the configured lookup IM (e.g.
`日=ㄇㄧˋ; ㄖˋ`). The strip stays visible until the next keystroke or
a dismiss-button tap — there is no auto-dismiss timer.

**Implementation guard** (`KeyboardViewController`): `isShowingReverseLookup: Bool`
is set to `true` by `showReverseLookup(_:)` and cleared by `didPress` /
`cancelComposing`. While the flag is `true`, `hideComposingPopup()` is a
no-op, preventing post-commit cleanup (related-phrase fetch, `clearSuggestions`)
from racing away the result before the user can read it.

**Key naming**: the UserDefaults key is `"\(activeIM)_im_reverselookup"` where
`activeIM` equals the IM's `tableNick` from the database (e.g. `"phonetic"`,
`"dayi"`, `"array"`). The Settings UI must use matching keys — the phonetic
IM's key is `phonetic_im_reverselookup`, **not** `bpmf_im_reverselookup`.

---

## 3. Candidate cell bias — why glyphs sit "low"

Each candidate cell is a `CandidateButton` (a `UIButton(type: .custom)`
with full bar height). The candidate glyph is rendered as the button's
`titleLabel`, which by default would center vertically inside the
button — putting it right where the keyname strip is.

To clear the strip, the button uses asymmetric vertical
`contentEdgeInsets`:

```swift
let bias = composingStripHeight / 2
btn.contentEdgeInsets = UIEdgeInsets(
    top: bias,         // +bias: shift content rect down by bias
    left: cellHPad,    // (10pt)
    bottom: -bias,     // -bias: extend content rect bias pixels below button (net: rect shifts down, size unchanged)
    right: cellHPad
)
```

Because `top + bottom = 0`, the content rect has the same *size* as
the button frame, but its center is shifted down by `bias` pixels.
The centered title label follows.

### Where the glyph ends up (iPhone, fontScale 1.0)

```
y =  0  ┬─────── bar.top, strip.top
        │  ㄉㄚˊ  ← keyname text
y = 22  ┼─────── strip.bottom (composingStripHeight)
        │   7pt padding
y = 29  ┼─────── glyph.top
        │   答    ← candidate glyph (≈22pt lineHeight)
y = 51  ┼─────── glyph.bottom
        │   7pt padding
y = 58  ┴─────── bar.bottom (candidateBarHeight)
```

The glyph is **symmetrically centered in the post-strip area** (7pt
padding above and below). Total visible spread from bar.top is 29pt of
"strip + padding" above the glyph and 7pt below — looks lopsided when
the strip is empty, balanced when it shows a keyname.

### Selection pill (`CandidateButton.pillView`)

The selection pill is drawn on an inner `UIView` sized to hug the
title label, not the full button frame:

```swift
pillView.frame = label.frame.insetBy(dx: -pillPadX, dy: -pillPadY)
                                       // (-4)         (-2)
```

Why an inner view: the button frame fills the full bar height for a
comfortable tap target (any tap inside the bar registers, not just on
the glyph), but if the pill matched the button frame, it would draw a
huge highlight box covering the keyname strip area. The inner view
keeps the pill compact around the glyph regardless of the surrounding
button frame.

---

## 4. The expanded candidates panel

When the user taps the chevron, the `KeyboardView` is hidden and a
panel takes its place. The panel must:

1. Look like the bar grew in place — row 1 pixel-identical to the bar.
2. Stack additional rows below row 1 efficiently.
3. Reuse every metric from the bar so visual changes propagate.

### Panel placement

```swift
panel.topAnchor.constraint(equalTo: candidateBar.topAnchor)
panel.leading/trailing/bottomAnchor == view.leading/trailing/bottomAnchor
```

The panel covers the candidate-bar AND keyboard-view area while it's
visible. The collapse chevron (chevron.up) and a moreSep mirror sit at
the panel's top-right, geometrically identical to the bar's chevron and
moreSep.

### Row heights — the asymmetry

This is the most important geometric subtlety:

| Row | Height | Bias | Why |
|---|---|---|---|
| Row 1 | `candidateBarHeight` (e.g. 58 iPhone, 74 iPad) | `stripHeight / 2` | Mirrors the collapsed bar exactly so expand-in-place feels seamless. The keyname strip area is reserved at the top. |
| Rows 2+ | `candidateBarHeight − stripHeight` (e.g. 36 iPhone, 46 iPad) | 0 | No keyname above these rows, so reserving strip space would be permanent whitespace. Glyphs are centered in the shorter row with symmetric padding. |

The wrap loop tracks the current row's height and bias and updates
them on the first wrap:

```swift
var rowH    = firstRowH      // candidateBarHeight
var rowBias = stripH / 2

for candidate in expandedCandidates {
    // ... compute btnW (floored at minCandidateCellWidth) ...
    if needsWrap {
        x = dismissZone + hPad   // every row starts at the same left margin
        y += rowH                // advance by OLD row's height
        rowH = restRowH          // shorter from now on
        rowBias = 0              // no strip-bias from now on
    }
    // Row 0 → fixed header container; rows 2+ → scroll content (y shifted up by firstRowH).
    let inHeader = row == 0
    btn.frame = CGRect(x: x, y: inHeader ? y : y - firstRowH, width: btnW, height: rowH)
    btn.contentEdgeInsets.top    =  rowBias
    btn.contentEdgeInsets.bottom = -rowBias
    (inHeader ? firstRowContainer : contentView).addSubview(btn)
    // ...
}
```

### Where rows 2+ glyphs land (iPhone)

```
y = 58  ┬─────── row 2 top (= row 1 bottom)
        │   7pt padding
y = 65  ┼─────── glyph.top
        │   答      ← candidate glyph (≈22pt)
y = 87  ┼─────── glyph.bottom
        │   7pt padding
y = 94  ┴─────── row 2 bottom (rowH = 36pt)
```

Symmetric padding above and below the glyph, no wasted strip area.

### Fixed first row + rows-2+ scroll below (expand-in-place)

Row 1 is built into a **fixed header container** (`expandedFirstRowContainer`,
pinned at the panel top with the exact collapsed-bar geometry). Rows 2+ live in
the scroll view, whose top is offset down by the first-row height
(`expandedScrollTopConstraint = activeCandidateBarHeight`). So when the grid
scrolls, row 1 and the chrome (composing strip, dismiss, chevron) never move and
nothing slides under them — the earlier single-scroll-view design let lower rows
scroll up behind the clear chrome.

### Wrap point — chevron GLYPH on row 0, full width on rows 2+

```swift
let chevronZone      = LayoutMetrics.CandidateBar.Chevron.buttonWidth(isPad: isOnPad)
let chevronGlyphLeft = panelWidth - chevronZone/2
                     - LayoutMetrics.CandidateBar.Chevron.iconSize(isPad: isOnPad)/2
func expandedRowMaxX(row: Int) -> CGFloat {
    // Row 0 shares the chevron → stop at the chevron glyph (its side padding is
    // empty). Rows 2+ scroll below the header (no chevron) → full screen width.
    return row == 0 ? chevronGlyphLeft - expandedSepWidth : panelWidth
}

// Wrap on the item's NATURAL width (glyph + pads), not the floored btnW.
// Row 0 may overflow ~10% into the chevron-glyph margin (on-screen); rows 2+
// wrap cleanly at the screen edge so nothing clips off-screen.
let wrapExtent = row == 0 ? intrinsicW * 0.9 : intrinsicW
if x + wrapExtent > expandedRowMaxX(row: row) { /* wrap */ }
```

The wrap must never use `candidateBarHeight` (the historical pre-refactor
mistake). Row 0 stops at the chevron **glyph** rather than the full 40pt button
so the last cell fills the row instead of wrapping early against the button's
empty side padding; rows 2+ have no chevron beneath them and fill the full
width. Each cell is floored at `minCandidateCellWidth` (`fontPointSize + 2·hPad`,
the bar's rule) so narrow glyphs (composing echo, single bopomofo) get a
Han-sized cell — the wrap check uses the *natural* width so the floored padding
of a narrow cell may spill into the reserved margin without forcing a wrap.

### Selection pill in the panel

Computed manually (the panel doesn't use `CandidateButton`; it builds
plain `UIButton(type: .system)` cells). The pill must hug the *centered*
glyph — because cells are floored at `minCandidateCellWidth`, a narrow glyph's
cell is wider than the glyph and UIButton centers the title, so a left-aligned
pill would miss it:

```swift
let textW = intrinsicW − 2 × cellHPad                // real glyph width (unfloored)
let pillW = textW + 2 × pillPadX                     // hug glyph horizontally
let pillH = min(rowH, btnFont.lineHeight + 2 × pillPadY)
let pillX = (btnW − pillW) / 2                        // centered in the (possibly floored) cell
let pillY = max(0, (rowH − pillH) / 2) + rowBias     // center vertically + apply row bias
```

This mirrors `CandidateButton.layoutSubviews` (`pill = label.frame.insetBy(-padX,
-padY)`), where `label.frame` is the centered title. `rowBias` is `stripH/2` for
row 1 (so the pill matches row-1's biased glyph) and 0 for rows 2+ (centered with
the unbiased glyph).

### Collapse chevron — width is static, height tracks the bar

| Property | Source | Reason |
|---|---|---|
| `collapseBtn.widthAnchor.equalToConstant` | `Chevron.buttonWidth(isPad:)` | Same width as the bar's `moreButton`, so the collapsed/expanded chevrons sit at the same X. Doesn't change with font scale. |
| `collapseBtn.heightAnchor.equalToConstant` | `candidateBarHeight` (live ref `expandedCollapseHeightConstraint`) | Tracks the bar height so the chevron tap target spans the panel's first row. Updated in `applyFeedbackSettings` whenever the user changes `font_size`. |

Without the live height ref, font-scale changes would resize the bar
but not the chevron button — its leading edge (and the sep that
follows it) would drift relative to the bar's chevron, breaking
expand-in-place. Bug history is preserved in the comments around
[KeyboardViewController.swift:932](../LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift#L932).

---

## 5. Quick "what knob to turn" table

| Want to change | Knob in `LayoutMetrics` | Effect on bar | Effect on panel |
|---|---|---|---|
| Bar overall height | `ComposingPopup.{Phone,Pad}.barBaseHeight` | Bar grows/shrinks | Row 1 grows/shrinks; rows 2+ grow/shrink by same delta (since `restRowH = barBaseHeight − stripHeight`) |
| Reserved keyname-strip space | `ComposingPopup.{Phone,Pad}.stripHeight` | Glyph bias changes; padding around glyph in row 1 changes | Rows 2+ height changes (bigger strip → shorter rows 2+, same total bar) |
| Keyname text size | `ComposingPopup.{Phone,Pad}.stripFontSize` | Strip text glyph size | Same — panel uses bar's `composingStripFont` directly |
| Candidate cell glyph size | `ComposingPopup.{Phone,Pad}.candidateFontSize` | Candidate text glyph size | Same — panel computes from the same source |
| Chevron icon size | `CandidateBar.Chevron.{Phone,Pad}.iconSize` | Bar's chevron glyph size | Panel's collapse chevron glyph size (same constant) |
| Chevron button width / padding | `CandidateBar.Chevron.{Phone,Pad}.buttonWidth` | Bar chevron's tap target width and L/R padding around glyph | Panel chevron's tap target width AND row-wrap point on every row |
| Selection pill rounding | `CandidateBar.pillCornerRadius` | Bar pill rounding | Panel pill rounding |
| Selection pill text padding | `CandidateBar.pillPadX`, `pillPadY` | Bar pill X/Y inset around glyph | Panel pill X/Y inset around glyph |

---

## 6. Invariants the layout maintains

1. **Row 1 of the panel is pixel-identical to the collapsed bar.** Same
   height, same bias, same glyph baseline, same chevron position, same
   moreSep position.
2. **Rows 2+ have no wasted strip space.** The shorter `restRowH`
   gives back `stripHeight` pixels per row.
3. **The chevron leading edge is at the same X on both surfaces.**
   Both use `Chevron.buttonWidth(isPad:)` for the button's width.
4. **Row 1 never scrolls.** It is a fixed header container; only rows 2+
   scroll, below it — so the composing strip, dismiss, and chevron stay
   put and nothing scrolls under the clear chrome. (Row-0 wrap reserves the
   chevron *glyph*; rows 2+ use the full width — see §4 Wrap point.)
5. **Font-scale changes propagate live.** The bar's
   `candidateBarHeightConstraint` and the panel's
   `expandedCollapseHeightConstraint` are both updated in
   `applyFeedbackSettings`. Anything else that depends on
   `candidateBarHeight` (like row 1 / rows 2+ heights inside
   `reloadExpandedCandidates`) is recomputed on the next reload —
   which `applyFeedbackSettings` triggers via
   `reloadExpandedCandidates()` when the panel is visible.

If a future change breaks one of these invariants, expand-in-place
will visibly judder. Test by:

1. Open a host app, start composing.
2. Tap the chevron to expand. Row 1 should not move.
3. Change `font_size` in Settings while the panel is open. Row 1 should
   resize in step with the bar; rows 2+ should resize proportionally.
4. Collapse the panel. The bar should be where it was before expansion.

---

## 7. Dismiss button — left-end clear control

### Purpose

A dismiss (✕) button at the **leading edge** of the candidate bar lets
the user cancel the current composing session in one tap — clearing
`mComposing` without modifying the document and hiding the candidate
list. The same button appears on the expanded panel's top-left corner.

### Bar layout

```
 ┌──┬───────────────────────────────────────────────────────┬─┬─────┐
 │  │ ㄉㄚˊ                                                │ │     │   ← keyname strip (top)
 │✕ │ ──────────────────────────────────────────────────── │ │  ▾  │
 │  │   答    打    搭    達    大    ...                   │ │     │
 └──┴───────────────────────────────────────────────────────┴─┴─────┘
   ↑                                                        ↑ ↑     ↑
dismiss btn                                            moreSep │  trailing
(no sep after)                                          chevron btn
```

The dismiss button is **narrower** than the chevron and does **not**
span the full bar height — it sits in the glyph zone only. There is no
separator between the dismiss button and the scroll view.

### Geometry

| Property | Value | Rationale |
|---|---|---|
| Width | `Chevron.buttonWidth(isPad:) / 2` (20pt iPhone, 26pt iPad) | Compact; less horizontal space consumed |
| Height | `barHeight − stripHeight = restRowH` (36pt iPhone, 46pt iPad) | Covers glyph + top/bottom padding; excludes strip zone |
| Center Y | `barCenterY + stripHeight / 2` | Aligned with the candidate glyph axis |
| Icon | `xmark` SF Symbol, `iconSize` = same as chevron | Reuses existing constant |
| `contentEdgeInsets` bias | **none** | Frame is already positioned at glyph center; no shift needed |
| Background | `candiText` at 10 % alpha, `cornerRadius` 6, `masksToBounds` | Makes button extent visible; no separator needed |

#### Where the dismiss button sits (iPhone, fontScale 1.0)

```
y =  0  ┬── bar.top / strip.top
        │   ㄉㄚˊ  (keyname strip, 22pt)
y = 22  ┼── strip.bottom    ← dismissButton.top
        │   7pt top padding   │
y = 29  ┼── glyph.top         │  height = restRowH = 36pt
        │   答  (~22pt)        │
y = 51  ┼── glyph.bottom      │
        │   7pt bot padding   │
y = 58  ┴── bar.bottom      ← dismissButton.bottom
```

The button is symmetric around the glyph center (y = 40), covering
both padding bands but stopping at the strip boundary above.

### Constraints in `CandidateBarView`

```swift
dismissButton.leadingAnchor.constraint(equalTo: leadingAnchor)
dismissButton.centerYAnchor.constraint(equalTo: centerYAnchor,
    constant: composingStripHeight / 2)
dismissButton.heightAnchor.constraint(equalTo: heightAnchor,
    constant: -composingStripHeight)
dismissButton.widthAnchor.constraint(equalToConstant:
    Chevron.buttonWidth(isPad:) / 2)
```

Downstream anchors that moved:

| Anchor | Old value | New value |
|---|---|---|
| `scrollView.leadingAnchor` | `bar.leadingAnchor` | `dismissButton.trailingAnchor` |
| `composingLabel.leadingAnchor` | `bar.leadingAnchor + labelLeading` | `dismissButton.trailingAnchor + labelLeading` |

### Visibility lifecycle

`dismissButton.isHidden` is toggled with `moreButton` / `moreSep` in
`rebuildButtons()` — hidden when there are no candidates.

### Delegate

```swift
protocol CandidateBarViewDelegate: AnyObject {
    func candidateBarView(_ view: CandidateBarView, didSelect mapping: Mapping)
    func candidateBarViewDidRequestMore(_ view: CandidateBarView)
    func candidateBarViewDidRequestDismiss(_ view: CandidateBarView)
}
```

`dismissTapped()` fires `delegate?.candidateBarViewDidRequestDismiss(self)`.

### Action in `KeyboardViewController`

```swift
func candidateBarViewDidRequestDismiss(_ view: CandidateBarView) {
    if isExpandedCandidatesVisible { hideExpandedCandidates() }
    cancelComposing()
}
```

`cancelComposing()` clears `mComposing`, empties the candidate list,
and hides the composing popup — no new clearing logic needed.

### Expanded panel — dismiss button at top-left

The panel's dismiss button uses **relative constraints off `collapseBtn`**
so it tracks font-scale changes automatically without a live constraint ref:

```swift
dismissBtn.centerYAnchor.constraint(equalTo: collapseBtn.centerYAnchor,
    constant: candidateBar.composingStripHeight / 2)
dismissBtn.heightAnchor.constraint(equalTo: collapseBtn.heightAnchor,
    constant: -candidateBar.composingStripHeight)
dismissBtn.widthAnchor.constraint(equalToConstant:
    Chevron.buttonWidth(isPad:) / 2)
```

When `expandedCollapseHeightConstraint.constant` is updated (font-scale
change), the dismiss button height and center follow automatically —
no separate `expandedDismissHeightConstraint` is needed.

### Row lead offset

The `dismissZone` uses half the chevron width (matching the actual
button) so panel row 1 starts at the same X as the collapsed bar:

```swift
let dismissZone = Chevron.buttonWidth(isPad: isOnPad) / 2   // = actual button width
var x: CGFloat  = dismissZone + hPad   // every row, including wraps
// Right edge: row 0 stops at the chevron glyph; rows 2+ use full width (see §4 Wrap point).
```

### Invariants preserved

+ **Row 1 pixel-identical to bar**: `dismissZone` = dismiss button width (no sep),
  matching the bar's leading region exactly.
+ **Rows 2+ no wasted strip space**: `restRowH` unchanged; only `rowLeadX`
  shifts the start X.
+ **Font-scale live**: dismiss button height auto-derives from
  `collapseBtn` via relative constraints; no additional update call needed.

### Files modified

| File | Change summary |
| --- | --- |
| `LimeIME-iOS/LimeKeyboard/CandidateBarView.swift` | Add `dismissButton`; new delegate method; updated leading constraints; visibility toggled with `moreButton` |
| `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift` | Implement `candidateBarViewDidRequestDismiss`; add dismiss btn to panel with relative constraints off `collapseBtn`; `dismissZone` in `reloadExpandedCandidates` |

No new `LayoutMetrics` constants — `Chevron.buttonWidth`, `Chevron.iconSize`,
`CandidateBar.dividerWidth`, and `CandidateBar.dividerHeight` are reused.

### Verification

1. Start composing → bar shows dismiss (✕) at left, chevron (▾) at right.
2. Tap ✕ → composing cleared, candidate bar empties.
3. Tap ▾ to expand → panel row 1 pixel-identical to bar (same leading zone).
4. Tap ✕ in expanded panel → panel collapses AND composing clears.
5. Change `font_size` in Settings while panel open → dismiss button height
   tracks bar height with no layout drift.
6. iPhone 20 × 36 pt; iPad 26 × 46 pt (at fontScale 1.0).

---

## 8. Android backport target

Android should backport the iOS candidate-bar dismiss control and the
candidate-local notification surface, but it does **not** need the iOS
expanded-panel asymmetry. Android can already show popups above the
candidate bar through `PopupWindow`, so the expanded candidate popup may
continue to grow above the bar instead of becoming an iOS-style
full-keyboard replacement.

### Dismiss key parity

Add the dismiss key to both Android candidate surfaces:

| Surface | Layout / owner | Required change |
|---|---|---|
| Fixed/floating candidate view | `res/layout/candidates.xml`, `CandidateViewContainer` | Add a leading `ImageButton` before `CandidateView` and wire it to clear composing. |
| In-input keyboard candidate view | `res/layout/inputcandidate.xml`, `CandidateInInputViewContainer` | Add the same leading `ImageButton` before `candidatesView` and wire it through `LIMEService`. |
| Expanded popup | `res/layout/candidatepopup.xml`, `CandidateView` / `CandidateExpandedView` | Keep the existing close/collapse button for collapsing the popup. Add a separate leading dismiss action only if row-1 parity is implemented for the popup later. |

The visual contract should match iOS exactly in relative terms:

| Property | Android target |
|---|---|
| Width | `candidate_expand_button_width / 2` (`21sp` with current `42sp` dimen). Use a named dimen such as `candidate_dismiss_button_width`, not an inline magic number. |
| Height | Candidate glyph-row height only: `CandidateView.mHeight` in Java, or the same measured height as the candidate row in XML. It must not include any composing/keyname strip height. |
| Vertical alignment | Center on the candidate glyph axis. If the Android composing/keyname view is embedded above the row, the dismiss button starts below that embedded strip. |
| Keyname X anchor | The composing/keyname popup or embedded label starts at the dismiss button's trailing edge, matching the candidate viewport leading edge. It must not start at absolute screen X=0 once the dismiss button exists. |
| Icon | Programmatic cross glyph using `candidateNormalTextColor` / `candiText`; do not use `btn_close` because that drawable carries its own solid/selector background. The apparent glyph size should match the iOS dismiss xmark. |
| Background | Same candidate background as the row, plus the iOS-style visible touch extent: `candidateNormalTextColor`/`candiText` at about 10% alpha with 6dp corner radius. Pressed/focused state may reuse the existing close-button selector. |
| Separator | None between dismiss and candidates. The candidate text begins immediately after the dismiss zone. |
| Visibility | Show when the candidate row has composing/candidates/reverse-lookup content; hide when the candidate row is empty. Do not replace the voice/mic button or expand button on the right. |

Behavior must also match iOS:

1. Tapping dismiss clears the current composing buffer and candidate list.
2. It does **not** delete committed document text.
3. If the expanded candidate popup is open, dismiss hides/collapses it before
   clearing composing.
4. It clears the composing keyname/reverse-lookup popup or embedded label.
5. It should reuse the existing service clearing path (`clearComposing(false)`
   / `clearSuggestions()` or the local equivalent) rather than duplicating
   candidate-state cleanup.

Android width accounting must reserve the dismiss zone at the leading edge:

```java
int dismissWidth = getResources()
        .getDimensionPixelSize(R.dimen.candidate_dismiss_button_width);
int rightWidth = visibleRightButtons * candidateExpandButtonWidth;
int maxCandidateWidth = containerWidth - dismissWidth - rightWidth;
```

The row should still use the existing right-side logic:

- empty candidate row: right button remains voice input;
- non-empty row: right button remains expand/collapse;
- keyboard-hidden row: `candidate_keyboard` remains the restore-keyboard
  button.

### Existing Android microphone key

Android already has a microphone/voice-input action in the candidate row.
It is not a standalone key in the candidate text strip; it reuses the
right-side candidate action button when there is no candidate content.

| Surface | Layout / owner | Location |
|---|---|---|
| In-input keyboard candidate view | `res/layout/inputcandidate.xml`, `CandidateInInputViewContainer` | `candidate_right` inside `candidate_right_parent`, at the far trailing edge of the candidate row. It sits to the right of `candidatesView`; when the soft keyboard is hidden, `candidate_keyboard` may appear immediately to its left as the restore-keyboard button. |
| Fixed/floating candidate view | `res/layout/candidates.xml`, `CandidateViewContainer` | The existing right-side button is the expand button only. Current code does not swap this fixed/floating surface to the microphone icon when empty. |
| Options menu | `LIMEService` menu item `voice_input` | Secondary entry point; not part of candidate-bar geometry. |

The in-input row chooses the right button icon in
`CandidateInInputViewContainer.requestLayout()`:

```java
if (mCandidateView.isEmpty()) {
    mRightButton.setImageDrawable(mCandidateView.mDrawableVoiceInput);
} else {
    mRightButton.setImageDrawable(isKeyboardHidden
            ? mCandidateView.mDrawableExpandUpButton
            : mCandidateView.mDrawableExpandDownButton);
}
```

Its click behavior mirrors the same state split:

| Candidate row state | Right button visual | Tap behavior |
|---|---|---|
| Empty (`mCandidateView.isEmpty() == true`) | Theme `voiceInputIcon` (`btn_voice_*` / `sym_keyboard_voice_*`) | Calls `CandidateView.startVoiceInput()`, which delegates to `LIMEService.startVoiceInput()`. |
| Non-empty, keyboard visible | Expand-down icon | Opens the expanded candidate popup through `CandidateView.showCandidatePopup()`. |
| Non-empty, keyboard hidden | Expand-up icon | Opens the expanded candidate popup upward; `candidate_keyboard` remains the separate restore-keyboard action. |

The microphone key should not clear composing, replace existing candidates,
or delete document text by itself; it only starts voice input. Any text commit
happens later when the recognizer returns a non-empty result.

`LIMEService.startVoiceInput()` first tries to switch to Google's voice IME
when `LIMEUtilities.isVoiceSearchServiceExist(...)` finds one. If that
switch does not take, or no voice IME is available, it falls back to a
`RecognizerIntent.ACTION_RECOGNIZE_SPEECH` launched through
`VoiceInputActivity`. The recognizer uses the system locale as
`RecognizerIntent.EXTRA_LANGUAGE`, free-form language model, and one result.

Recognized text returns to the service through two paths:

1. `VoiceInputActivity` stores the result in `sPendingVoiceText`; when LIME's
   input view starts again, `onStartInputView()` consumes and commits it.
2. As a backup, `VoiceInputActivity` broadcasts
   `org.limeime.VOICE_INPUT_RESULT` with `recognized_text`;
   `LIMEService` receives it and calls `commitVoiceTextWithRetry(...)`.

Both paths commit the recognized text through `InputConnection.commitText`.

### Replace system IME toasts with `lime_toast`

Android's IME-mode toasts should stop using `Toast.makeText(...)` for
keyboard-local feedback. Add a small candidate-surface popup named
`lime_toast` and route keyboard/IME feedback through it.

Scope: replace the IME-facing system toasts in `LIMEService`, especially:

- active IM switch feedback (`activeIMName`);
- Chinese/English mode feedback (`typing_mode_english`,
  `typing_mode_mixed`).

Reverse lookup is **not** a `lime_toast`. When
`SearchServer.getCodeListStringFromWord(...)` is running inside
`LIMEService`, successful reverse lookup should reuse the composing
keyname/reverse-lookup strip, matching iOS behavior. It should remain visible
until the next keystroke, dismiss tap, or other normal composing/candidate
clear. Empty/no-match reverse lookup results should stay silent in the IME and
must not fall back to Android `Toast.makeText(...)`.

Do **not** replace settings/import/activity toasts in this pass; those are
normal app UI feedback, not keyboard-surface feedback.

`lime_toast` placement aligns with the composing keyname popup:

| State | Placement |
|---|---|
| No composing keyname visible | Same anchor and baseline as the composing keyname popup/embedded composing label: horizontally aligned to the dismiss button's trailing edge / candidate viewport leading edge. |
| Composing keyname visible | Same Y as keyname, but shifted right of the keyname by measured keyname width + an 8dp gap. Clamp the right edge inside the candidate viewport. |
| Candidate popup expanded above the bar | Popup may remain above the candidate bar; no iOS asymmetric candidate-bar layout is needed. |

The styling should read as the same family as the composing keyname surface:

| Property | Target |
|---|---|
| View | `TextView` inside a reusable `PopupWindow`, owned by `CandidateView` or a small helper owned by the candidate container. |
| Font size | Same as `@dimen/composing_text_size` multiplied by `mLIMEPref.getFontSize()`. |
| Text color | `mColorComposingText`. |
| Background | `mColorComposingBackground`, same corner treatment as the composing popup/embedded label. |
| Padding | Horizontal 8dp, vertical 2-4dp so short labels do not look cramped. |
| Duration | Match Android short-toast feel, about 1200-1500 ms, but cancel and replace immediately when a newer `lime_toast` arrives. |
| Touch | Not touchable; never steals candidate/key presses. |

Implementation shape:

```java
candidateView.showLimeToast(text);
```

`showLimeToast` should:

1. lazily create/reuse a `PopupWindow` and `TextView`;
2. measure the current composing keyname view if it is visible;
3. place the toast at composing-popup Y, or just above the candidate row when
   using the popup path;
4. apply the right offset only when keyname text is visible;
5. auto-dismiss through the existing `UIHandler` so show/hide ordering matches
   `showComposing()` / `hideComposing()`.

### Android verification

1. Start composing → leading dismiss appears; right expand remains on the right.
2. Tap dismiss → composing text, candidates, keyname/reverse lookup, and expanded
   popup all disappear; document text is unchanged.
3. Switch IMs → `lime_toast` appears where the composing keyname would appear.
4. Start composing, then switch Chinese/English → keyname remains at the left and
   `lime_toast` appears to its right without overlap.
5. Expand candidates while keyboard is hidden → popup may render above the
   candidate row; no extra iOS-style asymmetric row math is required.
6. Confirm settings/import/activity screens still use normal Android toasts.

---

## 9. Emoji icon in the candidate bar

### Design rationale

The emoji panel launcher is surfaced from the phone candidate bar's existing left-end zone rather than from inside the English keyboard layout. This avoids any keyboard layout changes:

- iPhone English layout keeps `中` in the bottom row — no home-row reflow.
- iPad English layout keeps the full-width space key — no trimming.
- Android English layout is unchanged.

The left-end zone already belongs to the dismiss (✕) button when candidates are present. When the bar is empty the dismiss button is already hidden, leaving that zone free. The emoji icon fills it.

The iOS matching right-end zone uses an options hamburger when the row is empty and the expand chevron when candidates are present. The earlier iOS microphone plan is removed from the candidate-bar slot because iOS cannot record audio or launch system dictation from a custom keyboard extension. Android keeps its existing right-side microphone icon.

### Left-end zone state machine

| Candidate bar state | Left zone |
|---|---|
| Empty | 😀 emoji button |
| Candidates present | ✕ dismiss button |

Only one of {emojiButton, dismissButton} is ever visible at a time. The `scrollView.leadingAnchor` stays pinned to `dismissButton.trailingAnchor` unchanged — this works because both buttons occupy the same zone with the same width.

### iOS geometry

`emojiButton` is a new `UIButton(type: .system)` sibling of `dismissButton` in `CandidateBarView`. It is iPhone-only; iPad keeps the keyboard-level emoji key.

**Constraints** — centered in the first key column guide:

```swift
emojiButton.centerXAnchor.constraint(equalTo: firstColumnGuide.centerXAnchor),
emojiButton.centerYAnchor.constraint(equalTo: centerYAnchor),
emojiButton.heightAnchor.constraint(equalTo: heightAnchor,
    constant: -composingStripHeight),
emojiButton.widthAnchor.constraint(equalTo: firstColumnGuide.widthAnchor,
    multiplier: 0.80),
```

**Dimensions** (at fontScale 1.0):

| Idiom | Width | Height | Center offset |
| --- | --- | --- | --- |
| iPhone | 20 pt | 36 pt | +11 pt from bar center |

**Glyph**: SF Symbol `face.smiling` at the same configuration as the `xmark` glyph used by dismiss. Literal `😀` label as a fallback if the symbol is unavailable in the extension target.

**Background**: transparent.

**Visibility toggle in `rebuildButtons()`**:

```swift
let hasCandidates = !candidates.isEmpty
dismissButton.isHidden = !hasCandidates
moreButton.isHidden    = !hasCandidates
moreSep.isHidden       = !hasCandidates
let allowEmoji = !isPad
emojiButton.isHidden   = hasCandidates || !allowEmoji
```
The launcher is always enabled; there is no preference gate.

**Delegate**:

```swift
protocol CandidateBarViewDelegate: AnyObject {
    // ... existing methods ...
    func candidateBarViewDidRequestEmoji(_ view: CandidateBarView)
    func candidateBarViewDidRequestOptions(_ view: CandidateBarView)
}
```

`emojiTapped()` fires `delegate?.candidateBarViewDidRequestEmoji(self)`.
`optionsTapped()` fires `delegate?.candidateBarViewDidRequestOptions(self)`.

**Action in `KeyboardViewController`**:

```swift
func candidateBarViewDidRequestEmoji(_ view: CandidateBarView) {
    showEmojiPanel()
}

func candidateBarViewDidRequestOptions(_ view: CandidateBarView) {
    showGlobeMenu(from: view)
}
```

**Invariants preserved**:

- `scrollView.leadingAnchor == dismissButton.trailingAnchor` — unchanged. Works because emoji and dismiss buttons share the same zone.
- Row-1 parity with the expanded panel — unchanged; the left zone width is the same whether emoji or dismiss is showing.
- Font-scale live tracking — `rebuildButtons()` already responds to `fontScale` changes.

### iOS right-end options zone

The empty-row right-end button is `optionsButton`, a `UIButton(type: .system)` sibling of `moreButton`. It uses SF Symbol `line.3.horizontal` with fallback text `☰`. It is shown on both iPhone and iPad.

The glyph color follows `effectiveCandiText`, so it is dark on the light system candidate-bar backdrop and light on the dark backdrop. The button background remains the same near-transparent touch-trap fill used by candidate buttons; there is no visible key cap behind the hamburger.

Geometry is idiom-specific:

| Idiom | Horizontal target | Touch target |
|---|---|---|
| iPhone | trailing 10% column, centered | Full candidate-bar height |
| iPad | right-edge/backspace zone, but only 7% normal-key width, centered in that normal-width frame | Full candidate-bar height |

The iPad target intentionally does **not** use the top-row backspace width or center, because backspace is wider than a normal key. The button still sits at the right edge over the backspace area, but its frame is the normal key width. The button frame spans the full candidate-bar height, and its content uses the same `composingStripHeight / 2` vertical inset bias as `moreButton`, so taps in the top/bottom padding fire while the glyph still sits on the candidate glyph axis.

**State machine**:

| Candidate bar state | Right zone |
|---|---|
| Empty | Hamburger/options button |
| Candidates present | Expand chevron |

Only one of {optionsButton, moreButton} is ever visible at a time on iOS. The hamburger opens the same inline menu as long-pressing the keyboard/dismiss key:

- Reverse lookup source for the current active IM.
- Han conversion picker.
- LIME IM picker.
- System input-mode switch only when no visible globe key already handles it.
- Cancel.

### Android geometry

**Layout** (`res/layout/inputcandidate.xml`): Add an `ImageButton` for the emoji launcher immediately before `candidatesView`, in the same left-side position as the existing dismiss button:

```xml
<ImageButton
    android:id="@+id/candidate_emoji"
    android:layout_width="@dimen/candidate_dismiss_button_width"
    android:layout_height="fill_parent"
    android:visibility="gone"
    android:contentDescription="@string/emoji_panel" />
```

Width matches `candidate_dismiss_button_width` (21 sp) so the two left-zone buttons are interchangeable in the layout calculation.

**Visibility logic in `CandidateInInputViewContainer.requestLayout()`**:

```java
boolean isEmpty = mCandidateView.isEmpty();

if (mDismissButton != null) {
    mDismissButton.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
}
if (mEmojiButton != null) {
    mEmojiButton.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
}
```

**Width accounting in `updateCandidateViewWidthConstraint()`**: Include emoji button width when `VISIBLE` — same calculation already done for the dismiss button.

**Click handler**: emoji button click dispatches key code `-201` through `LIMEService`, the same emoji-panel open code used internally by the emoji panel spec.

### Preference gating

There is no preference gate. The emoji launcher is always visible when the candidate bar is empty.

### Emoji button file changes

| File | Change |
|---|---|
| `LimeIME-iOS/LimeKeyboard/CandidateBarView.swift` | Add `emojiButton` and right-side `optionsButton`; delegate methods for emoji/options; visibility in `rebuildButtons()` |
| `LimeIME-iOS/LimeKeyboard/KeyboardViewController.swift` | Implement `candidateBarViewDidRequestEmoji` and `candidateBarViewDidRequestOptions`; route options to `showGlobeMenu(from:)` |
| `LimeStudio/app/src/main/res/layout/inputcandidate.xml` | Add `candidate_emoji` ImageButton (21 sp, GONE) before `candidatesView` |
| `LimeStudio/app/src/main/java/org/limeime/candidate/CandidateInInputViewContainer.java` | Emoji/dismiss visibility toggle; width accounting; existing empty-row microphone button unchanged |
| `LimeStudio/app/src/main/java/org/limeime/candidate/CandidateView.java` | Programmatic dismiss glyph |
| `LimeStudio/app/src/main/java/org/limeime/LIMEService.java` | Dispatch `-201` on emoji click |
| `docs/EMOJI_KEYBOARD.md` | Update English keyboard launcher section; remove keyboard-layout changes; update shared contract and verification |

English keyboard layout files (`lime_abc.json`, `lime_abc_shift.json`, `lime_abc_ipad.json`, `lime_abc_ipad_shift.json`, Android English layout XML) **require no changes**.

### Emoji button verification

1. Open any text field with LimeIME active, no composing → candidate bar empty → 😀 appears at left on iPhone and Android.
2. Start composing → candidates appear → left zone switches to ✕; 😀 is gone.
3. Tap ✕ → composing cleared, bar empties → 😀 reappears.
4. Tap 😀 → emoji panel opens; candidate bar remains anchored above the panel.
5. On iPhone, empty-row right hamburger opens the same options menu as long-pressing keyboard/dismiss.
6. iPhone English keyboard: `中` is in the bottom row (unchanged). iPad keeps its keyboard-level emoji key and also shows the candidate-bar hamburger.
7. Android English keyboard: bottom row unchanged; no extra key added.

---

## TODO

### iOS

+ [ ] **`CandidateBarView.swift`**: Add iPhone-only `emojiButton: UIButton(type: .system)` centered in the first key column guide, width around 80% of that guide, transparent background.
+ [ ] **`CandidateBarView.swift`**: Add iOS `optionsButton: UIButton(type: .system)` centered in the right-side guide, transparent background, SF Symbol `line.3.horizontal` with `☰` fallback.
+ [ ] **`CandidateBarView.swift`**: For iPad options geometry, keep the button on the right/backspace edge but size it to a 7% normal-key frame; use `effectiveCandiText` for light/dark contrast and a full-height touch target with vertical content bias.
+ [ ] **`CandidateBarView.swift`**: In `rebuildButtons()`, add `emojiButton.isHidden = hasCandidates` alongside the existing dismiss/more/moreSep toggles.
+ [ ] **`CandidateBarView.swift`**: Add `candidateBarViewDidRequestEmoji(_ view: CandidateBarView)` to `CandidateBarViewDelegate`; wire `emojiButton` tap to fire it.
+ [ ] **`CandidateBarView.swift`**: Add `candidateBarViewDidRequestOptions(_ view: CandidateBarView)` to `CandidateBarViewDelegate`; wire `optionsButton` tap to fire it.
+ [ ] **`KeyboardViewController.swift`**: Implement `candidateBarViewDidRequestEmoji` to call `showEmojiPanel()`.
+ [ ] **`KeyboardViewController.swift`**: Implement `candidateBarViewDidRequestOptions` to call `showGlobeMenu(from:)`.
+ [ ] No changes to any keyboard layout JSON files.

### Android

+ [ ] **`inputcandidate.xml`**: Add `ImageButton` id=`candidate_emoji`, width=`@dimen/candidate_dismiss_button_width` (21 sp), height=`fill_parent`, visibility=`gone`, before `candidatesView`.
+ [ ] **`CandidateInInputViewContainer.java`**: In `requestLayout()`, toggle `mEmojiButton` visibility: `VISIBLE` when `isEmpty`, `GONE` otherwise.
+ [ ] **`CandidateInInputViewContainer.java`**: In `updateCandidateViewWidthConstraint()`, add emoji button width to `buttonsWidth` when emoji button is `VISIBLE`.
+ [ ] **`CandidateInInputViewContainer.java`**: Wire `mEmojiButton.setOnClickListener` to dispatch key code `-201` through `LIMEService`.
+ [ ] No changes to Android English keyboard layout XML.

### Shared

+ [ ] **`docs/EMOJI_KEYBOARD.md`**: Update "English keyboard launcher placement" section — remove keyboard-layout changes; replace with candidate-bar approach (reference §9 of this document).
+ [ ] **`docs/EMOJI_KEYBOARD.md`**: Update "Shared contract" table — remove bottom-row position rows for iPhone/iPad/Android; add candidate-bar emoji button row.
+ [ ] **`docs/EMOJI_KEYBOARD.md`**: Update verification step 1 (keyboard layout check) to match new design; remove step 9 iPhone home-row `中` check.
+ [ ] Verify all TODO items above with manual test on WJIP17 (iPhone), iPad, and Android emulator.
