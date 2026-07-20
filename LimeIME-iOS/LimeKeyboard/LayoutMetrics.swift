/*
 *
 *  *
 *  **    Copyright 2026, The LimeIME Open Source Project
 *  **
 *  **    Project Url: http://github.com/lime-ime/limeime/
 *  **
 *  **    This program is free software: you can redistribute it and/or modify
 *  **    it under the terms of the GNU General Public License as published by
 *  **    the Free Software Foundation, either version 3 of the License, or
 *  **    (at your option) any later version.
 *  *
 *  **    This program is distributed in the hope that it will be useful,
 *  **    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  **    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  **    GNU General Public License for more details.
 *  *
 *  **    You should have received a copy of the GNU General Public License
 *  **    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  *
 *
 */

import UIKit

// Single source of truth for keyboard-extension layout constants.
//
// Every magic number that affects geometry, font size, spacing, special-case
// colors, or animation timing lives here. The keyboard-extension code reads
// these values and never inlines a literal of its own.
//
// See `docs/LAYOUT_PARAM.md` for an explanation of each constant, the screen
// region it controls, and the trade-offs behind its current value.
//
// Convention:
//   - iPad-vs-iPhone variants are exposed as `func foo(isPad: Bool) -> CGFloat`.
//     Callers pass their own `isPad` / `isOnPad` flag (which is captured from
//     `UIDevice.current` for the keyboard view and from
//     `traitCollection.userInterfaceIdiom` for the controller — see the
//     respective files for why each path uses the source it does).
//   - Theme palette colors are NOT in this file — they are already
//     centralized in `KeyboardPalette` (KeyboardView.swift). Only the
//     palette-independent overlay/effect colors (touch-trap fill, dark-pill
//     override) live here.
enum IPadSizeClass: Equatable {
    case small
    case medium
    case large

    static func resolve(shortSideExtent: CGFloat) -> IPadSizeClass {
        if shortSideExtent >= 870 { return .large }
        if shortSideExtent >= 750 { return .medium }
        return .small
    }
}

enum LayoutMetrics {

    // MARK: - Touch trap (custom-keyboard hit gate)

    enum TouchTrap {
        // Custom keyboard extensions drop touches that land on fully
        // transparent pixels — see docs/IOS_CANDI_TOUCH.md §Resolution.
        // A near-invisible neutral grey fill defeats this gate without
        // tinting the shared blur backdrop.
        static let fill = UIColor(white: 0.5, alpha: 0.01)
    }

    enum Shadow {
        static let color = UIColor.black.cgColor
    }

    // MARK: - Candidate bar chrome
    //
    // Idiom-agnostic structural pieces of the candidate bar (chevron,
    // selection pill, divider, paging, theme overrides). Sizing of the
    // composing-keyname strip and the candidate cells themselves lives
    // under `ComposingPopup` below — that section holds the per-idiom
    // values together.
    enum CandidateBar {
        static func candidateHPad(isPad: Bool) -> CGFloat {
            guard isPad else { return 10 }
            switch LayoutLoader.iPadSizeClass {
            case .small: return 12
            case .medium: return 14
            case .large: return 10
            }
        }

        // Width of the thin moreSep divider just left of the chevron.
        static let dividerWidth: CGFloat = 1
        // Visible height of the moreSep divider.
        static let dividerHeight: CGFloat = 20

        // Chevron (more) button.
        //
        // The button frame width is set independently of the bar height —
        // the previous "width == height" rule made the chevron a square
        // sized to the bar, which left ~20pt of empty space on each side
        // of the glyph and grew/shrank with font scale for no good reason.
        //
        // Per-idiom values live in `Phone` / `Pad` sub-enums. The visible
        // padding around the glyph is `(chevronButtonWidth - chevronIconSize) / 2`,
        // so iPad gets a wider button (and bigger glyph) so the chevron
        // doesn't look cramped against the larger candidate text.
        enum Chevron {
            /// Sizes used on iPhone hardware / phone-class host.
            enum Phone {
                static let iconSize: CGFloat = 18
                static let buttonWidth: CGFloat = 40   // padding ≈ 11pt each side
            }
            /// Sizes used on iPad hardware / pad-class host.
            enum Pad {
                static let iconSize: CGFloat = 22
                static let buttonWidth: CGFloat = 52   // padding ≈ 15pt each side
            }
            // Per-idiom selectors used by the bar view and the controller.
            static func iconSize(isPad: Bool) -> CGFloat {
                isPad ? Pad.iconSize : Phone.iconSize
            }
            static func buttonWidth(isPad: Bool) -> CGFloat {
                isPad ? Pad.buttonWidth : Phone.buttonWidth
            }
            /// feat#N01: candidate-bar dismiss (✕) button — 1.5× the former half-chevron
            /// tap target (wider hit area only; height/color/corner style unchanged).
            static func dismissButtonWidth(isPad: Bool) -> CGFloat {
                buttonWidth(isPad: isPad) / 2 * 1.5
            }
        }

        // Selection pill (drawn inside CandidateButton).
        static let pillCornerRadius: CGFloat = 6
        static let pillPadX: CGFloat = 4
        static let pillPadY: CGFloat = 2

        // Minimum pan translation that counts as a paged scroll gesture
        // (smaller drags are treated as taps).
        static let pagingDragThreshold: CGFloat = 20

        // Dark theme (theme 1) overrides the palette's candiHighlight with
        // an elevated grey pill for Android parity.
        static let darkThemePill = UIColor(white: 0.23, alpha: 1)

        // Subtle alphas applied on top of palette colours.
        static let composingCodeDimAlpha: CGFloat = 0.5
        static let separatorAlpha: CGFloat = 0.2
    }

    // MARK: - Composing popup (keyname overlay inside the candidate bar)
    //
    // The single active composing-popup surface on BOTH iPhone and iPad —
    // a keyname strip overlaid on the leading region of the candidate bar
    // (RC3 Option A, see docs/IPAD_ASSIST_BAR.md §8). Per-idiom values are
    // separated into `Phone` and `Pad` sub-enums so equivalent parameters
    // sit next to their counterpart. Layout values that happen to be the
    // same on both idioms live directly under `ComposingPopup`.
    //
    enum ComposingPopup {

        /// Sizes used on iPhone hardware / phone-class host.
        enum Phone {
            /// Reserved height of the keyname strip overlay.
            static let stripHeight: CGFloat = 18
            /// Strip-label font size (× candidateFontScale at use-site).
            static let stripFontSize: CGFloat = 16
            /// Candidate-cell font size (× candidateFontScale at use-site).
            static let candidateFontSize: CGFloat = 26
            /// Composing-code (raw English letters) cell font size.
            static let composingCodeFontSize: CGFloat = 22
            /// Candidate-bar resting height before fontScale is applied.
            static let barBaseHeight: CGFloat = 58
        }

        /// Sizes used on iPad hardware / pad-class host.
        enum PadSmall {
            static let stripHeight: CGFloat = 24
            static let stripFontSize: CGFloat = 17
            static let candidateFontSize: CGFloat = 26
            static let composingCodeFontSize: CGFloat = 19
            static let barBaseHeight: CGFloat = 50
        }

        enum PadMedium {
            static let stripHeight: CGFloat = 26
            static let stripFontSize: CGFloat = 18
            static let candidateFontSize: CGFloat = 28
            static let composingCodeFontSize: CGFloat = 21
            static let barBaseHeight: CGFloat = 54
        }

        enum PadLarge {
            static let stripHeight: CGFloat = 28
            static let stripFontSize: CGFloat = 18
            static let candidateFontSize: CGFloat = 26
            static let composingCodeFontSize: CGFloat = 22
            static let barBaseHeight: CGFloat = 74
        }

        // Shared layout (identical on iPhone and iPad).
        static let labelLeading: CGFloat = 8
        static let labelTrailingInset: CGFloat = -4
        static let labelTopInset: CGFloat = 0
        // Padding added to the strip's height beyond ceil(font.lineHeight)
        // so STHeiti tone glyphs (ˇ ˋ ˊ ˙) don't clip against the label box.
        static let labelHeightPad: CGFloat = 2
        // Alpha applied to the keyname text on top of palette.candiText.
        static let textAlpha: CGFloat = 0.75

        // Per-idiom selectors used by the controller and the bar view.
        static func stripHeight(isPad: Bool) -> CGFloat {
            guard isPad else { return Phone.stripHeight }
            switch LayoutLoader.iPadSizeClass {
            case .small: return PadSmall.stripHeight
            case .medium: return PadMedium.stripHeight
            case .large: return PadLarge.stripHeight
            }
        }
        static func stripFontSize(isPad: Bool) -> CGFloat {
            guard isPad else { return Phone.stripFontSize }
            switch LayoutLoader.iPadSizeClass {
            case .small: return PadSmall.stripFontSize
            case .medium: return PadMedium.stripFontSize
            case .large: return PadLarge.stripFontSize
            }
        }
        static func candidateFontSize(isPad: Bool) -> CGFloat {
            guard isPad else { return Phone.candidateFontSize }
            switch LayoutLoader.iPadSizeClass {
            case .small: return PadSmall.candidateFontSize
            case .medium: return PadMedium.candidateFontSize
            case .large: return PadLarge.candidateFontSize
            }
        }
        static func composingCodeFontSize(isPad: Bool) -> CGFloat {
            guard isPad else { return Phone.composingCodeFontSize }
            switch LayoutLoader.iPadSizeClass {
            case .small: return PadSmall.composingCodeFontSize
            case .medium: return PadMedium.composingCodeFontSize
            case .large: return PadLarge.composingCodeFontSize
            }
        }
        static func barBaseHeight(isPad: Bool) -> CGFloat {
            guard isPad else { return Phone.barBaseHeight }
            switch LayoutLoader.iPadSizeClass {
            case .small: return PadSmall.barBaseHeight
            case .medium: return PadMedium.barBaseHeight
            case .large: return PadLarge.barBaseHeight
            }
        }
    }

    // MARK: - Keyboard rows
    //
    // Row heights and per-key geometry. Per-idiom values live under
    // `Phone` and `Pad` sub-enums so equivalent parameters sit together,
    // mirroring the `ComposingPopup` layout. Idiom-agnostic constants
    // (split-keyboard gap fraction, applyHeight fallback) live directly
    // under this enum.
    enum KeyboardRow {

        /// Sizes used on iPhone hardware / phone-class host.
        enum Phone {
            // Row heights (× keySizeScale at use-site).
            static let portraitRow: CGFloat = 50
            static let portraitBottomRow: CGFloat = 54
            static let landscapeRow: CGFloat = 34   // matches Android 36 dip
            static let landscapeBottomRow: CGFloat = 38
            // Per-key gaps / shape.
            static let keyHGap: CGFloat = 5
            static let keyVGap: CGFloat = 2
            static let keyCornerRadius: CGFloat = 6
        }

        /// Sizes used on iPad hardware / pad-class host (native iPad app).
        enum PadSmall {
            static let portraitRow: CGFloat = 52
            static let portraitBottomRow: CGFloat = 56
            static let landscapeRow: CGFloat = 52
            static let landscapeBottomRow: CGFloat = 56
            static let keyHGap: CGFloat = 5
            static let keyVGap: CGFloat = 3
            static let keyCornerRadius: CGFloat = 6
        }

        enum PadMedium {
            static let portraitRow: CGFloat = 58
            static let portraitBottomRow: CGFloat = 62
            static let landscapeRow: CGFloat = 58
            static let landscapeBottomRow: CGFloat = 62
            static let keyHGap: CGFloat = 6
            static let keyVGap: CGFloat = 3
            static let keyCornerRadius: CGFloat = 7
        }

        enum PadLarge {
            static let portraitRow: CGFloat = 64
            static let portraitBottomRow: CGFloat = 68
            static let landscapeRow: CGFloat = 60
            static let landscapeBottomRow: CGFloat = 64
            static let keyHGap: CGFloat = 7
            static let keyVGap: CGFloat = 4
            static let keyCornerRadius: CGFloat = 8
        }

        /// Row heights for iPad hardware running an iPhone app in compatibility
        /// mode (hostIsPad=false but UIDevice==.pad). Keys use iPad-scale heights
        /// for ergonomics; fonts, gaps, and corner radius stay phone-sized because
        /// the phone layout JSON is loaded (see KeyboardView.isPadHardware vs isPad).
        enum PadCompat {
            static let portraitRow: CGFloat = 54
            static let portraitBottomRow: CGFloat = 58
            static let landscapeRow: CGFloat = 34
            static let landscapeBottomRow: CGFloat = 38
        }

        // Idiom-agnostic.
        /// Width fraction of the central gap in iPad split-keyboard mode.
        static let splitGapFraction: CGFloat = 0.06
        /// Default per-row height assumed when a layout hasn't been measured
        /// yet (used as the fallback inside applyHeight).
        static let fallbackRowHeight: CGFloat = 54

        // Per-idiom selectors used by the keyboard view.
        static func rowHeight(isPadHardware: Bool, isPad: Bool, isLandscape: Bool) -> CGFloat {
            if isPadHardware && !isPad {
                return isLandscape ? PadCompat.landscapeRow : PadCompat.portraitRow
            }
            guard isPad else {
                return isLandscape ? Phone.landscapeRow : Phone.portraitRow
            }
            switch LayoutLoader.iPadSizeClass {
            case .small: return isLandscape ? PadSmall.landscapeRow : PadSmall.portraitRow
            case .medium: return isLandscape ? PadMedium.landscapeRow : PadMedium.portraitRow
            case .large: return isLandscape ? PadLarge.landscapeRow : PadLarge.portraitRow
            }
        }

        static func bottomRowHeight(isPadHardware: Bool, isPad: Bool, isLandscape: Bool) -> CGFloat {
            if isPadHardware && !isPad {
                return isLandscape ? PadCompat.landscapeBottomRow : PadCompat.portraitBottomRow
            }
            guard isPad else {
                return isLandscape ? Phone.landscapeBottomRow : Phone.portraitBottomRow
            }
            switch LayoutLoader.iPadSizeClass {
            case .small: return isLandscape ? PadSmall.landscapeBottomRow : PadSmall.portraitBottomRow
            case .medium: return isLandscape ? PadMedium.landscapeBottomRow : PadMedium.portraitBottomRow
            case .large: return isLandscape ? PadLarge.landscapeBottomRow : PadLarge.portraitBottomRow
            }
        }

        static func keyHGap(isPad: Bool) -> CGFloat {
            guard isPad else { return Phone.keyHGap }
            switch LayoutLoader.iPadSizeClass {
            case .small: return PadSmall.keyHGap
            case .medium: return PadMedium.keyHGap
            case .large: return PadLarge.keyHGap
            }
        }
        static func keyVGap(isPad: Bool) -> CGFloat {
            guard isPad else { return Phone.keyVGap }
            switch LayoutLoader.iPadSizeClass {
            case .small: return PadSmall.keyVGap
            case .medium: return PadMedium.keyVGap
            case .large: return PadLarge.keyVGap
            }
        }
        static func keyCornerRadius(isPad: Bool) -> CGFloat {
            guard isPad else { return Phone.keyCornerRadius }
            switch LayoutLoader.iPadSizeClass {
            case .small: return PadSmall.keyCornerRadius
            case .medium: return PadMedium.keyCornerRadius
            case .large: return PadLarge.keyCornerRadius
            }
        }
    }

    // MARK: - Key content (labels, icons, shadow)
    //
    // Per-key chrome (label fonts, icon sizes, shadow). Per-idiom values
    // are grouped under `Phone` and `Pad`; chrome that doesn't differ by
    // idiom (shadow, popup indicator, shift icon) lives directly under
    // `Key`.
    enum Key {

        /// Sizes used on iPhone hardware / phone-class host.
        enum Phone {
            static let singleLabelFontSize: CGFloat = 22
            /// Small primary label in dual-label keys (the letter sitting
            /// above the bopomofo sublabel).
            static let primaryLabelFontSize: CGFloat = 18
            static let sublabelFontSize: CGFloat = 22
            /// SF Symbol point size for icon keys (excluding the dismiss key).
            static let iconSize: CGFloat = 20
        }

        /// Sizes used on iPad hardware / pad-class host (native iPad app).
        enum PadSmall {
            static let singleLabelFontSize: CGFloat = 21
            static let primaryLabelFontSize: CGFloat = 18
            static let sublabelFontSize: CGFloat = 21
            static let iconSize: CGFloat = 23
        }

        enum PadMedium {
            static let singleLabelFontSize: CGFloat = 22
            static let primaryLabelFontSize: CGFloat = 19
            static let sublabelFontSize: CGFloat = 22
            static let iconSize: CGFloat = 24
        }

        enum PadLarge {
            static let singleLabelFontSize: CGFloat = 24
            static let primaryLabelFontSize: CGFloat = 20
            static let sublabelFontSize: CGFloat = 24
            static let iconSize: CGFloat = 26
        }

        /// Sizes used on iPad hardware running an iPhone app in compatibility
        /// mode. Keys are iPad-height but phone-width, so fonts are between
        /// phone and iPad — slightly larger than phone to fill the taller key,
        /// but not as wide as iPad since the key columns stay phone-narrow.
        enum PadCompat {
            static let singleLabelFontSize: CGFloat = 22
            static let primaryLabelFontSize: CGFloat = 18
            static let sublabelFontSize: CGFloat = 22
            static let iconSize: CGFloat = 20
        }

        // Idiom-agnostic chrome.
        static let shadowOpacity: Float = 0.3
        static let shadowOffsetY: CGFloat = 1
        /// SF Symbol point size for the keyboard-dismiss key (deliberately
        /// larger than other icons for legibility).
        static let dismissIconSize: CGFloat = 28
        static let shiftIconSize: CGFloat = 20
        // Popup ("…") indicator pinned to bottom-right of a key.
        static let popupIndicatorFontSize: CGFloat = 11
        static let popupIndicatorTrailingInset: CGFloat = -3
        static let popupIndicatorBottomInset: CGFloat = -2
        /// Negative inset applied to a dual-label container so it never
        /// touches the button's edges.
        static let dualLabelWidthMargin: CGFloat = -4

        // Per-idiom selectors used by the keyboard view.
        // isPadCompat takes priority over isPad when both are provided.
        static func singleLabelFontSize(isPad: Bool, isPadCompat: Bool = false) -> CGFloat {
            if isPadCompat && !isPad { return PadCompat.singleLabelFontSize }
            guard isPad else { return Phone.singleLabelFontSize }
            switch LayoutLoader.iPadSizeClass {
            case .small: return PadSmall.singleLabelFontSize
            case .medium: return PadMedium.singleLabelFontSize
            case .large: return PadLarge.singleLabelFontSize
            }
        }
        static func primaryLabelFontSize(isPad: Bool, isPadCompat: Bool = false) -> CGFloat {
            if isPadCompat && !isPad { return PadCompat.primaryLabelFontSize }
            guard isPad else { return Phone.primaryLabelFontSize }
            switch LayoutLoader.iPadSizeClass {
            case .small: return PadSmall.primaryLabelFontSize
            case .medium: return PadMedium.primaryLabelFontSize
            case .large: return PadLarge.primaryLabelFontSize
            }
        }
        static func sublabelFontSize(isPad: Bool, isPadCompat: Bool = false) -> CGFloat {
            if isPadCompat && !isPad { return PadCompat.sublabelFontSize }
            guard isPad else { return Phone.sublabelFontSize }
            switch LayoutLoader.iPadSizeClass {
            case .small: return PadSmall.sublabelFontSize
            case .medium: return PadMedium.sublabelFontSize
            case .large: return PadLarge.sublabelFontSize
            }
        }
        static func iconSize(isPad: Bool, isPadCompat: Bool = false) -> CGFloat {
            if isPadCompat && !isPad { return PadCompat.iconSize }
            guard isPad else { return Phone.iconSize }
            switch LayoutLoader.iPadSizeClass {
            case .small: return PadSmall.iconSize
            case .medium: return PadMedium.iconSize
            case .large: return PadLarge.iconSize
            }
        }
    }

    // MARK: - Gestures and timings

    enum Gesture {
        // Long-press hold thresholds.
        static let popupKeyboardHoldDuration: TimeInterval = 0.4
        static let dualRowHoldDuration: TimeInterval = 0.4
        static let specialKeyHoldDuration: TimeInterval = 0.5
        static let spaceLongPressDuration: TimeInterval = 0.5

        // Space-key horizontal slide: pixels per caret step; initial movement dead zone.
        static let spaceCaretStepPx: CGFloat = 7
        static let spaceSwipeThreshold: CGFloat = 12

        // iPad dual-row top-key downward slide that commits the secondary
        // glyph instead of the primary.
        static func dualRowSwipeThreshold(landscape: Bool) -> CGFloat {
            landscape ? 16 : 24
        }

        // Repeating-key (backspace etc.) cadence.
        static let repeatStartDelay: TimeInterval = 0.4
        static let repeatInterval: TimeInterval = 0.1

        // T9-style multi-tap window — same key within this window cycles
        // through `codes[]` instead of starting a new selection.
        static let multiTapTimeout: TimeInterval = 0.8

        // Shift Lock gesture window. Single taps only toggle one-shot shift;
        // a second tap inside this window enters Shift Lock.
        static let shiftDoubleTapTimeout: TimeInterval = 0.3
    }

    // MARK: - Long-press popup keyboard (mini keyboard above a key)

    enum PopupKeyboard {
        static let hPad: CGFloat = 8
        static let vPad: CGFloat = 8
        static let spacing: CGFloat = 4
        static let panelCornerRadius: CGFloat = 12
        static let panelShadowOpacity: Float = 0.28
        static let panelShadowOffsetY: CGFloat = 3
        static let panelShadowRadius: CGFloat = 8
        static let keyCornerRadius: CGFloat = 6
        static let keyFontSize: CGFloat = 22
        static let keyShadowOpacity: Float = 0.22
        static let keyShadowOffsetY: CGFloat = 1
        static let keyShadowRadius: CGFloat = 1
        // Extra width added to a popup key's text width to size the button.
        static let keyExtraWidth: CGFloat = 20
        // Edge margin from the keyboard view when positioning the popup.
        static let edgeMargin: CGFloat = 4
        // Vertical gap between popup and source key.
        static let yOffsetFromKey: CGFloat = 6
    }

    // MARK: - Key preview callout (iOS-native bubble above pressed key)

    enum KeyPreview {
        static let widthFactor: CGFloat = 1.45
        static let heightFactor: CGFloat = 1.4
        static let minWidthLandscape: CGFloat = 56
        static let minWidthPortrait: CGFloat = 64
        static let minHeightLandscape: CGFloat = 50
        static let minHeightPortrait: CGFloat = 64
        static let neckHeight: CGFloat = 12
        static let cornerRadius: CGFloat = 10
        static let edgeMargin: CGFloat = 4
        static let shadowOpacity: Float = 0.22
        static let shadowOffsetY: CGFloat = 1
        static let shadowRadius: CGFloat = 3

        // Animation
        static let initialScale: CGFloat = 0.88
        static let appearDuration: TimeInterval = 0   // instant pop like the system preview (was 0.08 fade — read as sluggish)
        static let disappearDuration: TimeInterval = 0.08
        static let springDamping: CGFloat = 0.7
        static let springInitialVelocity: CGFloat = 0.5

        // Inset from container width applied to the centred content.
        static let contentWidthInset: CGFloat = -8

        // S-curve neck control-point factors (relative to neckHeight).
        static let neckCurveFar: CGFloat = 0.55
        static let neckCurveNear: CGFloat = 0.45

        // Dual-label fonts inside the preview bubble.
        static func primaryFontSize(isTall: Bool, isLandscape: Bool) -> CGFloat {
            isTall ? (isLandscape ? 12 : 13) : (isLandscape ? 11 : 12)
        }
        static func sublabelFontSize(isTall: Bool, isLandscape: Bool) -> CGFloat {
            isTall ? (isLandscape ? 22 : 28) : (isLandscape ? 16 : 20)
        }
        static func singleFontSize(isLandscape: Bool) -> CGFloat { isLandscape ? 20 : 26 }

        // Spacing between primary and sublabel in the horizontal preview.
        static let horizontalDualSpacing: CGFloat = 3
    }

    // MARK: - Globe / dismiss key preview

    enum GlobePreview {
        // Done-key position estimate (we can't read the actual button frame
        // from the controller without poking through KeyButton).
        static let approxKeyWidthFactor: CGFloat = 0.15
        static let approxKeyHeightLandscape: CGFloat = 38
        static let approxKeyHeightPortrait: CGFloat = 56

        static let bubbleWidthLandscape: CGFloat = 44
        static let bubbleWidthPortrait: CGFloat = 52
        static let bubbleHeightLandscape: CGFloat = 50
        static let bubbleHeightPortrait: CGFloat = 64

        static let tipHeight: CGFloat = 8
        // Half-width of the triangular tip at the bubble bottom.
        static let tipHorizontalRadius: CGFloat = 6
        static let cornerRadius: CGFloat = 10
        static let edgeMargin: CGFloat = 4

        static let iconSizeLandscape: CGFloat = 22
        static let iconSizePortrait: CGFloat = 28

        static let shadowOpacity: Float = 0.22
        static let shadowOffsetY: CGFloat = 1
        static let shadowRadius: CGFloat = 3

        static let appearDuration: TimeInterval = 0.08
        // How long the brief globe flash stays visible before fading out.
        static let dismissDelay: TimeInterval = 0.4
        static let dismissDuration: TimeInterval = 0.1
    }

    // MARK: - Inline menu panel (replaces UIAlertController in the extension)

    enum InlineMenu {
        static let cornerRadius: CGFloat = 12
        static let backgroundAlpha: CGFloat = 0.97
        static let shadowOpacity: Float = 0.2
        static let shadowRadius: CGFloat = 8
        static let shadowOffsetY: CGFloat = -2
        static let buttonHeight: CGFloat = 50
        static let buttonFontSize: CGFloat = 17
        static let separatorHeight: CGFloat = 0.5
        static let edgeInset: CGFloat = 8
        static let appearDuration: TimeInterval = 0.2
        // Translation offset applied at start of the slide-in animation.
        static let appearTranslationY: CGFloat = 20

        static func segmentedAxis(isPad: Bool, isLandscape: Bool) -> NSLayoutConstraint.Axis {
            !isPad && isLandscape ? .horizontal : .vertical
        }
    }

    // The expanded candidates panel (rendered by the controller) reuses
    // every metric from `CandidateBar` and `ComposingPopup` directly so
    // its first row stays pixel-identical to the collapsed bar
    // ("expand-in-place"). No `ExpandedPanel` enum exists — that would
    // invite drift. See KeyboardViewController.setupKeyboardUI() for
    // the exact mapping.
}

/// SPLIT_ONE_HAND_KB: pure reach-geometry math (docs/SPLIT_ONE_HAND_KB.md).
/// mm → pt uses a per-device-class table; every constant is a calibration knob.
enum ReachGeometry {
    static let splitHalfMaxMM: CGFloat = 66
    static let splitRowMaxMM: CGFloat = 12
    static let oneHandMaxWidthMM: CGFloat = 60
    static let numpadKeyMM: CGFloat = 14
    static let numpadAnchorMaxFraction: CGFloat = 0.40

    /// iPad mini class = 163 pt/in, regular iPads = 132 pt/in, iPhone ≈ 153 pt/in.
    static func ptPerMM(isPad: Bool, sizeClass: IPadSizeClass) -> CGFloat {
        guard isPad else { return 6.0 }
        return sizeClass == .small ? 6.42 : 5.20
    }

    /// Per-half width fraction for split mode: the legacy (1 − gap)/2 half,
    /// capped by the two-hand thumb reach. Small iPads keep legacy behavior.
    static func splitHalfMaxFraction(viewWidth: CGFloat, sizeClass: IPadSizeClass) -> CGFloat {
        let legacy = (1 - LayoutMetrics.KeyboardRow.splitGapFraction) / 2
        guard viewWidth > 0 else { return legacy }
        let capPt = splitHalfMaxMM * ptPerMM(isPad: true, sizeClass: sizeClass)
        return min(legacy, capPt / viewWidth)
    }

    /// Android phone parity: portrait reserves two key columns in the centre,
    /// landscape reserves three. iPad continues to use splitHalfMaxFraction.
    static func phoneSplitContentFraction(keysInRow: Int, isLandscape: Bool) -> CGFloat {
        guard keysInRow > 0 else { return 1 }
        return CGFloat(keysInRow) / CGFloat(keysInRow + (isLandscape ? 3 : 2))
    }

    /// Vertical thumb-sweep cap on split-mode row height.
    static func splitRowHeightCap(sizeClass: IPadSizeClass) -> CGFloat {
        splitRowMaxMM * ptPerMM(isPad: true, sizeClass: sizeClass)
    }

    static func oneHandWidth(viewWidth: CGFloat) -> CGFloat {
        min(viewWidth, oneHandMaxWidthMM * ptPerMM(isPad: false, sizeClass: .small))
    }

    static func numpadAnchorWidth(viewWidth: CGFloat, columns: Int, sizeClass: IPadSizeClass) -> CGFloat {
        let byKeys = CGFloat(columns) * numpadKeyMM * ptPerMM(isPad: true, sizeClass: sizeClass)
        return min(byKeys, viewWidth * numpadAnchorMaxFraction)
    }
}
