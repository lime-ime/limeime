import UIKit

// Horizontal scrolling candidate bar above the keyboard.
// Mirrors Android's CandidateView.java (horizontal ListView).

protocol CandidateBarViewDelegate: AnyObject {
    func candidateBarView(_ view: CandidateBarView, didSelect mapping: Mapping)
    func candidateBarViewDidRequestMore(_ view: CandidateBarView)
    func candidateBarViewDidRequestDismiss(_ view: CandidateBarView)
    func candidateBarViewDidRequestEmoji(_ view: CandidateBarView)
    func candidateBarViewDidRequestOptions(_ view: CandidateBarView)
    /// Legacy iPhone globe mode: short tap on the candidate-bar chevron
    /// asks the host to dismiss the keyboard outright (spec:
    /// docs/IPHONE_LEGACY_KB.md). Distinct from `…RequestDismiss` which
    /// only hides expanded candidates + cancels composing.
    func candidateBarViewDidRequestKeyboardDismiss(_ view: CandidateBarView)
}

final class CandidateBarView: UIView {

    weak var delegate: CandidateBarViewDelegate?

    // MARK: - Subviews
    private let scrollView    = CandidateScrollView()
    private let stackView     = UIStackView()
    private let moreButton    = UIButton(type: .system)
    private let moreSep       = UIView()          // fixed separator left of chevron
    private let dismissButton = UIButton(type: .system)
    private let emojiButton   = UIButton(type: .system)
    private let optionsButton = UIButton(type: .system)

    /// Set by KeyboardViewController. When true, `optionsButton` paints as
    /// `keyboard.chevron.compact.down`, taps dismiss the keyboard, long-press
    /// shows the LIME options menu, and it stays visible regardless of bar
    /// state (spec: docs/IPHONE_LEGACY_KB.md).
    var legacyGlobeMode: Bool = false {
        didSet {
            guard oldValue != legacyGlobeMode else { return }
            applyLegacyOptionsBinding()
        }
    }

    /// Long-press recognizer attached to `optionsButton` in legacy mode so a
    /// long press routes to the LIME options menu while a short tap dismisses.
    /// Kept as a weak property so `applyLegacyOptionsBinding()` can remove it
    /// when leaving legacy mode.
    private weak var legacyOptionsLongPress: UILongPressGestureRecognizer?
    /// Leading region that displays the composing keyname. iPad uses this
    /// in lieu of the in-keyboard composingPopupLabel strip (which wastes
    /// vertical space). iPhone keeps the strip and leaves this collapsed.
    /// Width is 0 when `composingText` is nil/empty; intrinsic otherwise.
    private let composingLabel = UILabel()
    /// Reserved height of the top keyname strip overlaid on the candidate bar.
    /// Candidates get this much extra top padding so their glyphs sit below
    /// the keyname instead of overlapping it.
    /// Public so the expanded-candidates panel (rendered by
    /// `KeyboardViewController`) can mirror the same metrics and remain
    /// visually pixel-identical to the unexpanded bar's first row.
    ///
    /// Sized to comfortably contain a Bopomofo tone glyph from STHeiti TC at
    /// `composingStripFont`'s point size (see that property's comment for
    /// font choice rationale).
    var composingStripHeight: CGFloat { LayoutMetrics.ComposingPopup.stripHeight(isPad: isPad) }
    /// Font for the small top-strip keyname overlay. Deliberately smaller
    /// than the main composing/candidate font so it stays a subtle hint.
    /// Public for the same reason as `composingStripHeight`.
    ///
    /// NOTE: Uses **STHeiti TC** rather than PingFang. CoreText measurements
    /// at 14 pt show:
    ///   PingFangTC-Regular  ˇ = 3×1  pt   (invisible accent)
    ///   STHeitiTC-Light     ˇ = 5×3  pt   (≈10x larger area)
    ///   STSongti-TC         ˇ = 3×2  pt
    /// SF / SF Mono are even worse — they treat ˇ ˋ ˊ ˙ as Latin
    /// "spacing modifier letters" and draw them as near-invisible IPA
    /// accents. STHeiti TC is the only system font on iOS where the
    /// Bopomofo tone marks render at a glance-readable size without any
    /// per-character scaling tricks.
    var composingStripFont: UIFont {
        let size = LayoutMetrics.ComposingPopup.stripFontSize(isPad: isPad) * fontScale
        return UIFont(name: "STHeitiTC-Light", size: size)
            ?? UIFont(name: "PingFangTC-Regular", size: size)
            ?? UIFont.systemFont(ofSize: size, weight: .regular)
    }

    /// Text shown in the top keyname strip (RC3 Option A — vertical stack).
    /// When nil/empty the strip is hidden but the reserved padding above
    /// candidate glyphs is preserved so the bar height never jitters.
    var composingText: String? {
        didSet { applyComposingText() }
    }

    // MARK: - Theme
    var theme: Int = 0 {
        didSet { guard oldValue != theme else { return }; applyTheme() }
    }
    private var palette: KeyboardPalette {
        KeyboardPalette.palettes[max(0, min(theme, KeyboardPalette.palettes.count - 1))]
    }
    // Set by KeyboardViewController before assigning `theme` so effectiveCandiText
    // can read the real system appearance independently of overrideUserInterfaceStyle.
    var systemUserInterfaceStyle: UIUserInterfaceStyle = .light {
        didSet { guard oldValue != systemUserInterfaceStyle else { return }; applyTheme() }
    }

    // The candidate bar backdrop is a transparent system blur, so chrome here
    // follows only the host light/dark appearance, not the selected keyboard theme.
    private var effectiveCandiText: UIColor {
        CandidateBarSystemChrome.labelColor(systemUserInterfaceStyle: systemUserInterfaceStyle)
    }

    // MARK: - Feedback
    var feedbackVibration: Bool = false {
        didSet {
            guard oldValue != feedbackVibration else { return }
            if feedbackVibration { ensureHapticGenerator() } else { hapticGenerator = nil }
        }
    }
    var vibrateLevel: Int = 40 {
        didSet {
            guard oldValue != vibrateLevel else { return }
            rebuildHapticGenerator()
        }
    }

    // Stored haptic generator. See KeyboardView for the rationale — the previous
    // computed-property pattern caused cold-start latency and dropped keystrokes.
    private var hapticGenerator: UIFeedbackGenerator?
    private var lastHapticAt: CFTimeInterval = 0
    private let minHapticInterval: CFTimeInterval = 0.025

    private func ensureHapticGenerator() {
        if hapticGenerator == nil { rebuildHapticGenerator() }
    }

    private func rebuildHapticGenerator() {
        guard feedbackVibration else { hapticGenerator = nil; return }
        hapticGenerator = KeyboardView.makeHapticGenerator(for: vibrateLevel)
        hapticGenerator?.prepare()
    }

    @inline(__always)
    fileprivate func fireHaptic() {
        guard feedbackVibration else { return }
        let now = CACurrentMediaTime()
        guard now - lastHapticAt >= minHapticInterval else { return }
        lastHapticAt = now
        ensureHapticGenerator()
        guard let gen = hapticGenerator else { return }
        if let impact = gen as? UIImpactFeedbackGenerator {
            impact.impactOccurred()
        } else if let sel = gen as? UISelectionFeedbackGenerator {
            sel.selectionChanged()
        }
        gen.prepare()
    }

    func prepareHapticGenerator() {
        ensureHapticGenerator()
        hapticGenerator?.prepare()
    }

    // MARK: - State
    private var candidates:  [Mapping] = []
    private static let idleToolsRevealDelay: TimeInterval = 0.12
    private var idleToolsRevealWorkItem: DispatchWorkItem?
    private var idleToolsRevealReady = true
    private var idleToolsSuppressed = false {
        didSet {
            guard oldValue != idleToolsSuppressed else { return }
            if idleToolsSuppressed {
                cancelIdleToolsReveal()
                idleToolsRevealReady = false
            } else if candidates.isEmpty && !idleToolsRevealReady {
                scheduleIdleToolsReveal()
            }
            rebuildButtons()
        }
    }


    /// Currently-highlighted candidate index, or -1 when no cell should be drawn highlighted.
    /// Mirrors Android `CandidateView.mSelectedIndex` — associated lists (related phrases,
    /// Chinese punctuation, English suggestions) always leave this at -1.
    private var selectedIndex: Int = -1
    /// Read-only access to the current selection index for arrow-key navigation.
    var currentSelectedIndex: Int { selectedIndex }
    /// Number of candidates currently displayed.
    var candidateCount: Int { candidates.count }
    /// Button for each entry in `candidates`, in order. Used by `setSelectedIndex` to re-style
    /// individual cells without rebuilding the whole stack (preserves scroll offset).
    private var candidateButtons: [CandidateButton] = []

    /// Tags used to locate the two labels inside a selkey-prefixed button so
    /// `applyHighlightStyle` can update their colors on a selection change.


    // MARK: - Layout constants
    /// Mirrors Android `font_size` scaler from LIMEPreferenceManager.getFontSize().
    /// Applied to candidate/selkey/composing fonts. Set by KeyboardViewController.
    var fontScale: CGFloat = 1.0 {
        didSet { guard oldValue != fontScale else { return }; rebuildButtons() }
    }
    /// Mirrors LayoutLoader.hostIsPad (set from traitCollection by the controller)
    /// so compatibility-mode iPhone apps on iPad use phone metrics consistently.
    private var isPad: Bool { LayoutLoader.hostIsPad }
    private var baseCandidateFontSize: CGFloat     { LayoutMetrics.ComposingPopup.candidateFontSize(isPad: isPad) }
    private var baseComposingCodeFontSize: CGFloat { LayoutMetrics.ComposingPopup.composingCodeFontSize(isPad: isPad) }
    private var candidateFont: UIFont     { UIFont.systemFont(ofSize: baseCandidateFontSize * fontScale, weight: .regular) }
    // Per-candidate composing-code label font.
    // Uses PingFang TC for the same reason documented on `composingStripFont`
    // above: Bopomofo tone marks (ˇ ˋ ˊ ˙) would otherwise render as tiny
    // IPA accents under SF / SF Mono.
    private var composingCodeFont: UIFont {
        let size = baseComposingCodeFontSize * fontScale
        return UIFont(name: "PingFangTC-Regular", size: size)
            ?? UIFont.systemFont(ofSize: size, weight: .regular)
    }
    private let candidateHPad: CGFloat = LayoutMetrics.CandidateBar.candidateHPad
    private let dividerWidth:  CGFloat = LayoutMetrics.CandidateBar.dividerWidth

    // MARK: - Init
    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }
    required init?(coder: NSCoder) { super.init(coder: coder); setup() }

    /// Tracks current chevron direction (used by `setChevronExpanded`).
    private var chevronExpanded: Bool = false

    // MARK: - Setup
    private func applyTheme() {
        backgroundColor = .clear
        moreButton.tintColor = effectiveCandiText
        moreSep.backgroundColor = effectiveCandiText.withAlphaComponent(LayoutMetrics.CandidateBar.separatorAlpha)
        dismissButton.tintColor = effectiveCandiText
        dismissButton.backgroundColor = palette.normalKey.withAlphaComponent(0.15)
        emojiButton.tintColor = effectiveCandiText
        emojiButton.backgroundColor = LayoutMetrics.TouchTrap.fill
        optionsButton.tintColor = effectiveCandiText
        optionsButton.setTitleColor(effectiveCandiText, for: .normal)
        optionsButton.backgroundColor = LayoutMetrics.TouchTrap.fill
        composingLabel.font = composingStripFont
        composingLabel.textColor = effectiveCandiText.withAlphaComponent(LayoutMetrics.ComposingPopup.textAlpha)
        applyComposingText()
        rebuildButtons()
    }

    private func setup() {
        // Bar itself stays .clear so it blends with the shared keyboard blur
        // backdrop. The touch-trap fill lives on the candidate buttons only
        // (see makeCandidateButton) — those fill the bar edge-to-edge when
        // candidates are shown, so every visible bar pixel is a button
        // pixel at 0.01-alpha grey. Empty bar = pure blur, matching the
        // surrounding keys exactly.
        backgroundColor = .clear

        // Fixed chevron pinned to the right edge of the bar
        let chevronConfig = UIImage.SymbolConfiguration(
            pointSize: LayoutMetrics.CandidateBar.Chevron.iconSize(isPad: isPad), weight: .regular)
        moreButton.setImage(UIImage(systemName: "chevron.down", withConfiguration: chevronConfig), for: .normal)
        moreButton.tintColor = effectiveCandiText
        // KVC sets the same backing storage as `contentEdgeInsets` without
        // tripping the iOS 15 deprecation warning. The non-Configuration
        // button path is intentional — see makeCandidateButton for the
        // reason (UIButton.Configuration.plain() inflates spacing).
        // Match candidate buttons: bias down by half the strip height so the
        // chevron icon sits at the same vertical center as the glyphs.
        // Symmetric horizontal insets are unnecessary because the icon is
        // centered in the (now narrower) frame; only the vertical bias matters.
        let chevronBias = composingStripHeight / 2
        moreButton.setValue(NSValue(uiEdgeInsets: UIEdgeInsets(top: chevronBias, left: 0,
                                                               bottom: -chevronBias, right: 0)),
                            forKey: "contentEdgeInsets")
        moreButton.isHidden = true
        moreButton.addTarget(self, action: #selector(moreTapped), for: .touchUpInside)
        // 0.01-alpha touch trap so taps in the chevron's padding also fire —
        // same rationale as the candidate buttons below.
        moreButton.backgroundColor = LayoutMetrics.TouchTrap.fill
        moreButton.translatesAutoresizingMaskIntoConstraints = false
        addSubview(moreButton)

        moreSep.backgroundColor = effectiveCandiText.withAlphaComponent(LayoutMetrics.CandidateBar.separatorAlpha)
        moreSep.isHidden = true
        moreSep.translatesAutoresizingMaskIntoConstraints = false
        addSubview(moreSep)

        // Dismiss button pinned to the leading edge — mirror of the trailing chevron.
        let dismissConfig = UIImage.SymbolConfiguration(
            pointSize: LayoutMetrics.CandidateBar.Chevron.iconSize(isPad: isPad), weight: .regular)
        dismissButton.setImage(UIImage(systemName: "xmark", withConfiguration: dismissConfig), for: .normal)
        dismissButton.tintColor = effectiveCandiText
        dismissButton.isHidden = true
        dismissButton.addTarget(self, action: #selector(dismissTapped), for: .touchUpInside)
        // Visible rounded background so the full button extent (restRowH) is apparent.
        // Colour is applied in applyTheme(); corner radius is permanent.
        dismissButton.layer.cornerRadius = 6
        dismissButton.layer.masksToBounds = true
        dismissButton.backgroundColor = palette.normalKey.withAlphaComponent(0.15)
        dismissButton.translatesAutoresizingMaskIntoConstraints = false
        addSubview(dismissButton)

        let emojiConfig = UIImage.SymbolConfiguration(
            pointSize: LayoutMetrics.CandidateBar.Chevron.iconSize(isPad: isPad) * 1.35, weight: .regular)
        if let image = UIImage(systemName: "face.smiling", withConfiguration: emojiConfig) {
            emojiButton.setImage(image, for: .normal)
        } else {
            emojiButton.setTitle("😀", for: .normal)
            emojiButton.titleLabel?.font = UIFont.systemFont(
                ofSize: LayoutMetrics.CandidateBar.Chevron.iconSize(isPad: isPad) * 1.35, weight: .regular)
        }
        emojiButton.tintColor = effectiveCandiText
        emojiButton.imageView?.contentMode = .scaleAspectFit
        emojiButton.isHidden = true
        emojiButton.addTarget(self, action: #selector(emojiTapped), for: .touchUpInside)
        emojiButton.backgroundColor = LayoutMetrics.TouchTrap.fill
        emojiButton.translatesAutoresizingMaskIntoConstraints = false
        addSubview(emojiButton)

        // Right-edge options button (trailing column mirror of emojiButton).
        // Empty candidate bars use this to expose the same menu as a long press
        // on the keyboard/dismiss key.
        let optionsConfig = UIImage.SymbolConfiguration(
            pointSize: LayoutMetrics.CandidateBar.Chevron.iconSize(isPad: isPad) * 1.10, weight: .regular)
        if let image = UIImage(systemName: "line.3.horizontal", withConfiguration: optionsConfig) {
            optionsButton.setImage(image, for: .normal)
        } else {
            optionsButton.setTitle("☰", for: .normal)
            optionsButton.titleLabel?.font = UIFont.systemFont(
                ofSize: LayoutMetrics.CandidateBar.Chevron.iconSize(isPad: isPad) * 1.10, weight: .regular)
        }
        optionsButton.tintColor = effectiveCandiText
        optionsButton.setTitleColor(effectiveCandiText, for: .normal)
        optionsButton.imageView?.contentMode = .scaleAspectFit
        optionsButton.contentHorizontalAlignment = .center
        optionsButton.contentVerticalAlignment = .center
        optionsButton.setValue(NSValue(uiEdgeInsets: UIEdgeInsets(top: chevronBias, left: 0,
                                                                  bottom: -chevronBias, right: 0)),
                               forKey: "contentEdgeInsets")
        optionsButton.isHidden = true
        optionsButton.addTarget(self, action: #selector(optionsTapped), for: .touchUpInside)
        optionsButton.backgroundColor = LayoutMetrics.TouchTrap.fill
        optionsButton.translatesAutoresizingMaskIntoConstraints = false
        addSubview(optionsButton)

        let firstColumnGuide = UILayoutGuide()
        addLayoutGuide(firstColumnGuide)
        let lastColumnGuide = UILayoutGuide()
        addLayoutGuide(lastColumnGuide)
        let optionsColumnWidth: CGFloat = isPad ? 0.07 : 0.10

        composingLabel.font = composingStripFont
        composingLabel.textColor = effectiveCandiText.withAlphaComponent(LayoutMetrics.ComposingPopup.textAlpha)
        composingLabel.textAlignment = .left
        composingLabel.backgroundColor = .clear
        composingLabel.translatesAutoresizingMaskIntoConstraints = false
        composingLabel.isUserInteractionEnabled = false
        // Tone marks ˇ ˋ ˊ ˙ are scaled up (see attributedKeyname) so the
        // glyph extent can briefly exceed the strip's reserved height. Allow
        // the label to draw outside its frame instead of clipping the tones.
        composingLabel.clipsToBounds = false
        addSubview(composingLabel)

        // Scroll view occupies the bar to the left of the fixed chevron.
        // delaysContentTouches=false routes taps to candidate buttons immediately;
        // canCancelContentTouches=true lets the scroll view cancel button tracking
        // when a drag gesture is detected, so horizontal scrolling still works.
        // CandidateScrollView.touchesShouldCancel returns true for UIControl
        // subclasses so drags are never blocked by the buttons.
        // No additional UITapGestureRecognizer is needed: buttons are full bar
        // height (alignment=.fill + explicit height constraint), so touchUpInside
        // fires for any tap within the bar regardless of vertical position.
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.delaysContentTouches = false
        scrollView.canCancelContentTouches = true
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(scrollView)

        stackView.axis = .horizontal
        // .fill makes each candidate button stretch to the full bar height so
        // taps anywhere in the bar register, not just on the glyph itself.
        // The highlight pill is drawn on an inner view sized to the text
        // (see CandidateButton) so the visual pill stays glyph-sized.
        stackView.alignment = .fill
        stackView.spacing = 0
        stackView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(stackView)

        NSLayoutConstraint.activate([
            // dismiss button: half chevron width, height = barHeight − stripHeight,
            // centered on the glyph axis (biased down by stripHeight/2 from bar center).
            // No contentEdgeInsets bias needed — the frame itself sits at glyph center.
            dismissButton.leadingAnchor.constraint(equalTo: leadingAnchor),
            dismissButton.centerYAnchor.constraint(equalTo: centerYAnchor, constant: composingStripHeight / 2),
            dismissButton.heightAnchor.constraint(equalTo: heightAnchor, constant: -composingStripHeight),
            dismissButton.widthAnchor.constraint(equalToConstant: LayoutMetrics.CandidateBar.Chevron.buttonWidth(isPad: isPad) / 2),

            firstColumnGuide.leadingAnchor.constraint(equalTo: leadingAnchor),
            firstColumnGuide.widthAnchor.constraint(equalTo: widthAnchor, multiplier: 0.10),
            emojiButton.centerXAnchor.constraint(equalTo: firstColumnGuide.centerXAnchor),
            emojiButton.centerYAnchor.constraint(equalTo: centerYAnchor, constant: composingStripHeight / 2),
            emojiButton.heightAnchor.constraint(equalTo: heightAnchor, constant: -composingStripHeight),
            emojiButton.widthAnchor.constraint(equalTo: firstColumnGuide.widthAnchor, multiplier: 0.80),

            // Options column. On iPhone this mirrors the trailing column.
            // On iPad, the button still sits on the right/backspace edge,
            // but its frame is normal-key width instead of backspace width.
            lastColumnGuide.trailingAnchor.constraint(equalTo: trailingAnchor),
            lastColumnGuide.widthAnchor.constraint(equalTo: widthAnchor, multiplier: optionsColumnWidth),
            optionsButton.centerXAnchor.constraint(equalTo: lastColumnGuide.centerXAnchor),
            optionsButton.topAnchor.constraint(equalTo: topAnchor),
            optionsButton.bottomAnchor.constraint(equalTo: bottomAnchor),
            optionsButton.widthAnchor.constraint(equalTo: lastColumnGuide.widthAnchor),

            // chevron flush to trailing edge. Width is an explicit constant
            // (chevronButtonWidth) — independent of bar height — so the
            // chevron's left/right padding stays sensible across font scales
            // and idioms. The expanded panel mirrors the same width.
            moreButton.trailingAnchor.constraint(equalTo: trailingAnchor),
            moreButton.topAnchor.constraint(equalTo: topAnchor),
            moreButton.bottomAnchor.constraint(equalTo: bottomAnchor),
            moreButton.widthAnchor.constraint(equalToConstant: LayoutMetrics.CandidateBar.Chevron.buttonWidth(isPad: isPad)),

            // thin separator just left of the chevron, biased down by the
            // same amount as the candidate glyphs so it stays centered with
            // the visible row content under the keyname overlay.
            moreSep.trailingAnchor.constraint(equalTo: moreButton.leadingAnchor),
            moreSep.centerYAnchor.constraint(equalTo: centerYAnchor, constant: composingStripHeight / 2),
            moreSep.widthAnchor.constraint(equalToConstant: dividerWidth),
            moreSep.heightAnchor.constraint(equalToConstant: LayoutMetrics.CandidateBar.dividerHeight),

            // composing keyname strip pinned to the top edge, starts after the
            // dismiss button. Sits on top of the candidate scroll view
            // (added later in subview order); does not affect scrollView frame.
            //
            // CLIP NOTE: The bar's topAnchor is the input view's top edge,
            // which is the system's clip boundary. STHeiti TC's Bopomofo tone
            // glyphs ˇ ˋ ˊ ˙ render at the TOP of the em-box and would
            // otherwise be sliced off by that boundary. Two adjustments:
            //   - topAnchor constant +1 pushes the label baseline down by 1 pt
            //     so the glyph top lands strictly inside the bar.
            //   - heightAnchor uses the font's full lineHeight (with a small
            //     pad) instead of composingStripHeight, so the label's own
            //     frame is large enough that no glyph is clipped by the
            //     label even though composingStripHeight (which drives the
            //     candidate `bias` inset) stays tight to save vertical space.
            composingLabel.leadingAnchor.constraint(equalTo: dismissButton.trailingAnchor, constant: LayoutMetrics.ComposingPopup.labelLeading),
            composingLabel.trailingAnchor.constraint(equalTo: moreSep.leadingAnchor, constant: LayoutMetrics.ComposingPopup.labelTrailingInset),
            composingLabel.topAnchor.constraint(equalTo: topAnchor, constant: LayoutMetrics.ComposingPopup.labelTopInset),
            composingLabel.heightAnchor.constraint(equalToConstant: ceil(composingStripFont.lineHeight) + LayoutMetrics.ComposingPopup.labelHeightPad),

            // scroll view fills the bar between dismiss button and moreSep
            // (composing label overlays its top region)
            scrollView.leadingAnchor.constraint(equalTo: dismissButton.trailingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: moreSep.leadingAnchor),
            scrollView.topAnchor.constraint(equalTo: topAnchor),
            scrollView.bottomAnchor.constraint(equalTo: bottomAnchor),

            stackView.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            stackView.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            stackView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            stackView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            stackView.heightAnchor.constraint(equalTo: scrollView.frameLayoutGuide.heightAnchor),
        ])

        // The scroll view is added after the emoji button and spans the same
        // leading/trailing regions when the candidate list is empty. Keep
        // the launchers above it so their full-height touch traps receive taps.
        bringSubviewToFront(emojiButton)
        bringSubviewToFront(optionsButton)

        // Seed the optionsButton role from the current legacyGlobeMode flag.
        // No-op when not in legacy mode (default state matches the constructor).
        applyLegacyOptionsBinding()
    }

    // MARK: - Public API

    /// Rotate the fixed chevron to indicate expanded (↑) or collapsed (↓) state.
    func setChevronExpanded(_ expanded: Bool) {
        chevronExpanded = expanded
        let name = expanded ? "chevron.up" : "chevron.down"
        let chevronConfig = UIImage.SymbolConfiguration(
            pointSize: LayoutMetrics.CandidateBar.Chevron.iconSize(isPad: isPad), weight: .regular)
        moreButton.setImage(UIImage(systemName: name, withConfiguration: chevronConfig), for: .normal)
    }

    /// Retained for API compatibility. The composing code is now rendered
    /// as the first candidate entry in the bar, so there is no dedicated
    /// left-edge label to update.
    func setComposingCode(_ code: String) { _ = code }

    func setIdleToolsSuppressed(_ suppressed: Bool) {
        idleToolsSuppressed = suppressed
    }

    // MARK: - Composing region (RC3 Option A)

    /// Recompute the composing label's text. Width/height are static;
    /// the strip simply hides when empty so the candidate area still has
    /// the same reserved top padding (no bar-height jitter).
    private func applyComposingText() {
        let raw = composingText?.trimmingCharacters(in: .whitespaces) ?? ""
        composingLabel.font = composingStripFont
        composingLabel.attributedText = nil
        composingLabel.text = raw.isEmpty ? nil : raw
        // Ensure the strip floats above the candidate scroll view.
        bringSubviewToFront(composingLabel)
    }

    /// Build a plain attributed string for the keyname strip.
    ///
    /// Kept as an entry point for callers (expanded panel, toast) so they
    /// can share the same typography with the in-bar overlay. We deliberately
    /// **don't** bump the size of the Bopomofo tone marks `ˇ ˋ ˊ ˙` —
    /// `composingStripFont` already uses STHeiti TC, which renders those
    /// tone glyphs at a glance-readable size without per-character scaling.
    /// Earlier scaling experiments caused UILabel to clip the bumped glyphs
    /// against the line box computed from the base font.
    static func attributedKeyname(_ text: String, baseFont: UIFont,
                                  color: UIColor) -> NSAttributedString {
        return NSAttributedString(string: text, attributes: [
            .font: baseFont,
            .foregroundColor: color
        ])
    }

    /// Replace the candidate list with new results.
    ///
    /// - Parameters:
    ///   - mappings: the new candidate list.
    ///   - selectedIndex: the initial highlighted index. Pass `-1` (default) for associated
    ///     lists (related phrases, Chinese punctuation, English suggestions) so no cell is
    ///     drawn highlighted — matches Android `CandidateView.setSuggestions` rule.
    func setCandidates(_ mappings: [Mapping], selectedIndex: Int = -1) {
        let hadCandidates = !candidates.isEmpty
        candidates = mappings
        self.selectedIndex = (selectedIndex >= 0 && selectedIndex < mappings.count) ? selectedIndex : -1
        updateIdleToolsRevealState(hadCandidates: hadCandidates)
        rebuildButtons()
        // layoutIfNeeded must come BEFORE setContentOffset(.zero).
        // During the layout pass UIScrollView internally adjusts contentOffset to
        // account for contentSize changes; calling setContentOffset after ensures
        // our zero value always wins and is not overwritten by UIScrollView's
        // internal adjustment. scrollSelectedIntoView is intentionally omitted
        // here — a fresh candidate list always starts at offset 0.
        scrollView.layoutIfNeeded()
        scrollView.setContentOffset(.zero, animated: false)
    }

    /// Append additional candidates to the existing list WITHOUT clearing buttons or
    /// resetting scroll. Used by background follow-up fetches so the user’s scroll
    /// position is undisturbed.
    ///
    /// If `mappings` starts with the same items already displayed the method only
    /// adds the net-new tail. If `mappings` is not a superset of the current list
    /// (e.g. the composing code changed) it falls back to a full `setCandidates`.
    func appendCandidates(_ mappings: [Mapping], selectedIndex: Int = -1) {
        // Preserve scroll position across the stage-2 upgrade.
        // setCandidates now calls layoutIfNeeded() BEFORE setContentOffset(.zero),
        // so when it returns layout is fully settled and there are no pending async
        // scroll adjustments. Overriding the .zero immediately after is safe.
        let preservedX = scrollView.contentOffset.x
        setCandidates(mappings, selectedIndex: selectedIndex)
        guard preservedX > 0 else { return }
        let maxX = max(0, scrollView.contentSize.width - scrollView.bounds.width)
        guard maxX > 0 else { return }
        scrollView.setContentOffset(CGPoint(x: min(preservedX, maxX), y: 0), animated: false)
    }

    /// Update the highlighted index without rebuilding buttons (preserves scroll offset).
    /// Pass `-1` to clear the highlight.
    func setSelectedIndex(_ index: Int) {
        let clamped = (index >= 0 && index < candidates.count) ? index : -1
        guard clamped != selectedIndex else { return }
        let old = selectedIndex
        selectedIndex = clamped
        if old >= 0 && old < candidateButtons.count {
            applyHighlightStyle(button: candidateButtons[old], index: old, mapping: candidates[old])
        }
        if clamped >= 0 && clamped < candidateButtons.count {
            applyHighlightStyle(button: candidateButtons[clamped], index: clamped, mapping: candidates[clamped])
        }
        scrollSelectedIntoView(animated: true)
    }

    /// Mirrors Android `candidate_switch` pref (CandidateView.java:314).
    /// true  = free drag-scroll (scrollView follows finger continuously)
    /// false = paged: drag is suppressed, on release snap to next/prev candidate page
    var candidateSwitch: Bool = true {
        didSet {
            guard oldValue != candidateSwitch else { return }
            scrollView.isScrollEnabled = candidateSwitch
            if candidateSwitch {
                if let gr = pagingPanGesture { scrollView.removeGestureRecognizer(gr) }
                pagingPanGesture = nil
                pagingStartOffsetX = 0
            } else {
                let pan = UIPanGestureRecognizer(target: self, action: #selector(handlePagingPan(_:)))
                scrollView.addGestureRecognizer(pan)
                pagingPanGesture = pan
            }
        }
    }
    private weak var pagingPanGesture: UIPanGestureRecognizer?
    private var pagingStartOffsetX: CGFloat = 0

    @objc private func handlePagingPan(_ gr: UIPanGestureRecognizer) {
        switch gr.state {
        case .began:
            pagingStartOffsetX = scrollView.contentOffset.x
        case .ended, .cancelled:
            let dx = gr.translation(in: scrollView).x
            // Ignore tiny drags — tap/select will handle them.
            guard abs(dx) > LayoutMetrics.CandidateBar.pagingDragThreshold else { return }
            if dx < 0 { scrollNextPage() } else { scrollPrevPage() }
        default:
            break
        }
    }

    /// Scroll right by one visible-width page, aligned to the first candidate
    /// that would fall at/beyond the left edge of the new viewport.
    private func scrollNextPage() {
        let w = scrollView.bounds.width
        let maxX = max(0, scrollView.contentSize.width - w)
        let target = min(pagingStartOffsetX + w, maxX)
        scrollView.setContentOffset(CGPoint(x: alignedOffset(for: target, preferLeft: true), y: 0), animated: true)
    }

    /// Scroll left by one visible-width page, aligned to a candidate boundary.
    private func scrollPrevPage() {
        let w = scrollView.bounds.width
        let target = max(0, pagingStartOffsetX - w)
        scrollView.setContentOffset(CGPoint(x: alignedOffset(for: target, preferLeft: false), y: 0), animated: true)
    }

    /// Snap a scroll offset to the nearest candidate button x-position so pages
    /// align on candidate boundaries (mirrors Android scrollPrev/scrollNext which
    /// use mWordX[] to snap to word boundaries).
    private func alignedOffset(for target: CGFloat, preferLeft: Bool) -> CGFloat {
        guard !candidateButtons.isEmpty else { return target }
        var best: CGFloat = 0
        var bestDist: CGFloat = .greatestFiniteMagnitude
        for btn in candidateButtons {
            let x = btn.frame.minX
            let d = abs(x - target)
            if d < bestDist { bestDist = d; best = x }
        }
        return best
    }

    // MARK: - Private

    private func rebuildButtons() {
        stackView.arrangedSubviews.forEach { $0.removeFromSuperview() }
        candidateButtons.removeAll(keepingCapacity: true)

        for (index, mapping) in candidates.enumerated() {
            let btn = makeCandidateButton(mapping: mapping, index: index)
            stackView.addArrangedSubview(btn)
            // Activate AFTER addArrangedSubview so btn and stackView share a
            // common ancestor — activating before would crash with
            // "no common ancestor" AutoLayout assertion.
            btn.heightAnchor.constraint(equalTo: stackView.heightAnchor).isActive = true
            candidateButtons.append(btn)
            applyHighlightStyle(button: btn, index: index, mapping: mapping)
        }

        // Show/hide the fixed chevron and dismiss button with the candidate list.
        // Left zone: emoji ↔ dismiss swap (per CANDI_LAYOUT.md §9). Right zone:
        // options ↔ chevron swap.
        let hasCandidates = !candidates.isEmpty
        let allowEmoji    = !isPad
        let allowOptions  = true
        let showIdleTools = CandidateBarView.shouldShowIdleTools(
            hasCandidates: hasCandidates,
            idleRevealReady: idleToolsRevealReady,
            idleToolsSuppressed: idleToolsSuppressed,
            allowTool: true)
        let showActiveChrome = CandidateBarView.shouldShowActiveChrome(
            hasCandidates: hasCandidates,
            showIdleTools: showIdleTools,
            idleRevealReady: idleToolsRevealReady)
        moreButton.isHidden    = !showActiveChrome
        moreSep.isHidden       = !showActiveChrome
        dismissButton.isHidden = !showActiveChrome
        emojiButton.isHidden   = !showIdleTools || !allowEmoji
        // Legacy iPhone globe mode: optionsButton owns dismiss + LIME menu and
        // must remain reachable regardless of bar state (spec: docs/IPHONE_LEGACY_KB.md).
        optionsButton.isHidden = legacyGlobeMode ? false : (!showIdleTools || !allowOptions)
    }

    static func shouldShowIdleTools(
        hasCandidates: Bool,
        idleRevealReady: Bool,
        idleToolsSuppressed: Bool,
        allowTool: Bool
    ) -> Bool {
        return !hasCandidates && idleRevealReady && !idleToolsSuppressed && allowTool
    }

    static func shouldShowActiveChrome(
        hasCandidates: Bool,
        showIdleTools: Bool,
        idleRevealReady: Bool
    ) -> Bool {
        return hasCandidates || (!showIdleTools && !idleRevealReady)
    }

    private func updateIdleToolsRevealState(hadCandidates: Bool) {
        if !candidates.isEmpty {
            cancelIdleToolsReveal()
            idleToolsRevealReady = false
        } else if idleToolsSuppressed {
            cancelIdleToolsReveal()
            idleToolsRevealReady = false
        } else if hadCandidates {
            idleToolsRevealReady = false
            scheduleIdleToolsReveal()
        } else {
            idleToolsRevealReady = true
        }
    }

    private func scheduleIdleToolsReveal() {
        cancelIdleToolsReveal()
        let workItem = DispatchWorkItem { [weak self] in
            guard let self else { return }
            self.idleToolsRevealReady = true
            self.rebuildButtons()
        }
        idleToolsRevealWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + CandidateBarView.idleToolsRevealDelay, execute: workItem)
    }

    private func cancelIdleToolsReveal() {
        idleToolsRevealWorkItem?.cancel()
        idleToolsRevealWorkItem = nil
    }

    private func makeCandidateButton(mapping: Mapping, index: Int) -> CandidateButton {
        // .custom avoids UIKit's system-button tint overrides and ensures the
        // touch area is exactly the button frame (no hidden restrictions).
        let btn = CandidateButton(type: .custom)
        btn.tag = index
        // Use contentEdgeInsets (not UIButton.Configuration) so the button has
        // exactly `candidateHPad` on each side. UIButton.Configuration.plain()
        // adds its own internal padding on top of contentInsets, which made
        // the candi-bar spacing visibly larger than the expanded panel.
        // KVC bypasses the iOS 15 deprecation warning while writing the same
        // backing storage — the property is still functional, just deprecated
        // for source code that opts into Configuration (we explicitly do not).
        // Balanced vertical insets: shift the glyph down by half the strip
        // height (top +, bottom −) so it visually clears the keyname overlay
        // without changing the button's overall height. Bar height stays
        // identical whether composing is active or not.
        let bias = composingStripHeight / 2
        btn.setValue(NSValue(uiEdgeInsets: UIEdgeInsets(top: bias,
                                                        left: candidateHPad,
                                                        bottom: -bias,
                                                        right: candidateHPad)),
                     forKey: "contentEdgeInsets")
        btn.addTarget(self, action: #selector(candidateTapped(_:)), for: .touchUpInside)
        // Same 0.01-alpha neutral fill as the bar — see setup() comment.
        // Without this, taps in the vertical padding above/below the glyph
        // land on clear pixels and are dropped by the keyboard-extension
        // touch gate before touchUpInside can fire.
        btn.backgroundColor = LayoutMetrics.TouchTrap.fill
        btn.translatesAutoresizingMaskIntoConstraints = false
        // Height constraint is added in rebuildButtons() after addArrangedSubview.

        // Composing-code record (mixed-mode raw-code entry): styled grey/monospace
        // so the user can visually distinguish it as "commit the raw English letters".
        // Mirrors Android mColorComposingCode.
        let isComposingCode = mapping.isComposingCodeRecord

        btn.setTitle(mapping.word, for: .normal)
        if isComposingCode {
            btn.titleLabel?.font = composingCodeFont
            btn.setTitleColor(effectiveCandiText.withAlphaComponent(LayoutMetrics.CandidateBar.composingCodeDimAlpha), for: .normal)
        } else {
            btn.titleLabel?.font = candidateFont
            btn.setTitleColor(effectiveCandiText, for: .normal)
        }
        return btn
    }

    /// Paint selection-dependent background and text color on a single candidate button.
    /// Mirrors Android `CandidateView.doDraw` highlight branch + per-record-type color switch.
    private func applyHighlightStyle(button: CandidateButton, index: Int, mapping: Mapping) {
        let isSelected = (index == selectedIndex && selectedIndex >= 0)
        let isComposingCode = mapping.isComposingCodeRecord
        // Theme 1 (Dark) overrides to an elevated gray pill for Android parity;
        // all other themes (including Light) use the palette's own highlight colour.
        let highlightColor: UIColor
        if theme == 0 || theme == 1 {
            highlightColor = systemUserInterfaceStyle == .dark
                ? LayoutMetrics.CandidateBar.darkThemePill
                : KeyboardPalette.iosLight(.systemBackground)
        } else {
            highlightColor = palette.candiHighlight
        }

        // The pill is drawn on an inner view sized to the text glyph only,
        // so the highlight remains visually compact even though the button
        // frame now spans the full bar height for a comfortable tap target.
        button.pillView.backgroundColor = isSelected ? highlightColor : .clear

        if isComposingCode {
            // Selected composing-code gets full opacity (mirrors mColorComposingCodeHighlight).
            let color = isSelected
                ? effectiveCandiText
                : effectiveCandiText.withAlphaComponent(LayoutMetrics.CandidateBar.composingCodeDimAlpha)
            button.setTitleColor(color, for: .normal)
        } else {
            button.setTitleColor(effectiveCandiText, for: .normal)
        }
    }

    /// Scroll so the highlighted cell is visible. No-op when selection is cleared.
    /// Mirrors Android's scrollNext/scrollPrev behavior on selection change.
    private func scrollSelectedIntoView(animated: Bool) {
        guard selectedIndex >= 0, selectedIndex < candidateButtons.count else { return }
        let btn = candidateButtons[selectedIndex]
        // Force a layout so btn.frame is valid immediately after rebuildButtons.
        stackView.layoutIfNeeded()
        let rect = btn.convert(btn.bounds, to: scrollView)
        if !scrollView.bounds.contains(rect) {
            scrollView.scrollRectToVisible(rect, animated: animated)
        }
    }

    @objc private func candidateTapped(_ sender: UIButton) {
        let index = sender.tag
        guard index < candidates.count else { return }
        // The `…` (hasMoreMark) cell is a UI control, not a real candidate
        // (see docs/#77_ISSUE.md). Re-route taps to the "more" delegate so the
        // expanded panel opens instead of committing literal `…`.
        if candidates[index].isHasMoreMarkRecord {
            fireHaptic()
            delegate?.candidateBarViewDidRequestMore(self)
            return
        }
        fireHaptic()
        // Flash the highlight on the tapped cell before the commit animates.
        setSelectedIndex(index)
        delegate?.candidateBarView(self, didSelect: candidates[index])
    }

    @objc private func dismissTapped() {
        fireHaptic()
        delegate?.candidateBarViewDidRequestDismiss(self)
    }

    @objc private func emojiTapped() {
        fireHaptic()
        delegate?.candidateBarViewDidRequestEmoji(self)
    }

    /// Swap the right-edge `optionsButton`'s role based on `legacyGlobeMode`.
    /// Legacy mode: keyboard-down chevron, tap dismisses, long-press → LIME menu,
    /// always visible. Standard mode: hamburger ☰, tap → LIME menu, visibility
    /// driven by composing state (existing behavior).
    private func applyLegacyOptionsBinding() {
        // Tap target swap. Remove both possible targets defensively so repeated
        // flips never accumulate handlers.
        optionsButton.removeTarget(self, action: #selector(optionsTapped),
                                    for: .touchUpInside)
        optionsButton.removeTarget(self, action: #selector(legacyDismissTapped),
                                    for: .touchUpInside)

        // Long-press recognizer: remove any prior instance before re-adding.
        if let lp = legacyOptionsLongPress {
            optionsButton.removeGestureRecognizer(lp)
            legacyOptionsLongPress = nil
        }

        let iconName: String
        let iconScale: CGFloat = 1.10
        if legacyGlobeMode {
            iconName = "keyboard.chevron.compact.down"
            optionsButton.addTarget(self, action: #selector(legacyDismissTapped),
                                     for: .touchUpInside)
            let lp = UILongPressGestureRecognizer(target: self,
                                                   action: #selector(legacyOptionsLongPressed(_:)))
            lp.minimumPressDuration = LayoutMetrics.Gesture.specialKeyHoldDuration
            optionsButton.addGestureRecognizer(lp)
            legacyOptionsLongPress = lp
        } else {
            iconName = "line.3.horizontal"
            optionsButton.addTarget(self, action: #selector(optionsTapped),
                                     for: .touchUpInside)
        }

        let cfg = UIImage.SymbolConfiguration(
            pointSize: LayoutMetrics.CandidateBar.Chevron.iconSize(isPad: isPad) * iconScale,
            weight: .regular)
        if let image = UIImage(systemName: iconName, withConfiguration: cfg) {
            optionsButton.setImage(image, for: .normal)
            optionsButton.setTitle(nil, for: .normal)
        } else {
            optionsButton.setImage(nil, for: .normal)
            optionsButton.setTitle(legacyGlobeMode ? "⌄" : "☰", for: .normal)
        }

        // Force visibility in legacy mode so the dismiss/menu surface is
        // always reachable, even when no candidates are composing. The
        // standard-mode visibility rule (hidden when not composing) is owned
        // by other call sites and only takes effect when this function does
        // NOT override.
        if legacyGlobeMode {
            optionsButton.isHidden = false
        }
    }

    @objc private func legacyDismissTapped() {
        fireHaptic()
        delegate?.candidateBarViewDidRequestKeyboardDismiss(self)
    }

    @objc private func legacyOptionsLongPressed(_ gr: UILongPressGestureRecognizer) {
        guard gr.state == .began else { return }
        fireHaptic()
        delegate?.candidateBarViewDidRequestOptions(self)
    }

    @objc private func optionsTapped() {
        fireHaptic()
        delegate?.candidateBarViewDidRequestOptions(self)
    }

    @objc private func moreTapped() {
        guard !candidates.isEmpty else { return }
        fireHaptic()
        // Reset scroll on expand so when the expanded panel dismisses the
        // user lands back on the first row instead of wherever they had
        // scrolled. Skip on collapse — chevronExpanded reflects the *current*
        // state, so `false` means this tap is about to expand.
        if !chevronExpanded && scrollView.contentOffset.x > 0 {
            scrollView.setContentOffset(.zero, animated: true)
        }
        delegate?.candidateBarViewDidRequestMore(self)
    }
}

/// UIButton subclass that draws its selection "pill" on an inner subview
/// sized to the text glyph, independent of the button's own frame. Combined
/// with `UIStackView.alignment = .fill`, this lets the tappable area extend
/// to the full bar height while keeping the highlight visually compact.
final class CandidateButton: UIButton {
    let pillView = UIView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        setupPillView()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupPillView()
    }

    private func setupPillView() {
        pillView.isUserInteractionEnabled = false
        pillView.backgroundColor = .clear
        pillView.layer.cornerRadius = LayoutMetrics.CandidateBar.pillCornerRadius
        pillView.layer.masksToBounds = true
        insertSubview(pillView, at: 0)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        guard let label = titleLabel else {
            pillView.frame = .zero
            return
        }
        // Hug the title label with a small pad so the pill matches the
        // glyph bounds rather than the full button frame.
        pillView.frame = label.frame.insetBy(dx: -LayoutMetrics.CandidateBar.pillPadX,
                                             dy: -LayoutMetrics.CandidateBar.pillPadY)
    }
}

/// UIScrollView subclass whose `touchesShouldCancel(in:)` returns `true` for
/// every subview, including `UIControl` descendants. The default
/// implementation returns `false` for UIControl, which would otherwise
/// prevent the candidate list from scrolling once the candidate buttons
/// stretch to the full bar height (UIStackView `alignment = .fill`).
final class CandidateScrollView: UIScrollView {
    override func touchesShouldCancel(in view: UIView) -> Bool {
        return true
    }
}
