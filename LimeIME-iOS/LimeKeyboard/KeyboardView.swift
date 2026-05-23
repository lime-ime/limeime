import UIKit

// Full keyboard view: renders keys from a LimeKeyLayout.
// Phase 2: UIButton-based; Phase 3 can switch to UICollectionView for more flexibility.

// MARK: - KeyboardPalette

struct KeyboardPalette {
    let background:      UIColor
    let normalKey:       UIColor
    let modifierKey:     UIColor
    let pressedKey:      UIColor
    /// Label color for normal (character) keys.
    let label:           UIColor
    /// Label color for modifier/function keys — may differ when modifierKey bg needs opposite contrast.
    let modifierLabel:   UIColor
    let secondaryLabel:  UIColor
    let candiBackground: UIColor
    let candiText:       UIColor
    /// Background tint drawn behind the currently-selected candidate cell.
    /// Mirrors Android `mDrawableSuggestHighlight` (drawable/ic_suggest_scroll_background_hl).
    let candiHighlight:  UIColor

    // Indices 0–5 match keyboard_theme values 0–5.
    // Theme 6 (系統設定) is resolved to 0 or 1 by KeyboardViewController.
    // Colors ported exactly from Android LimeStudio/app/src/main/res/values/colors.xml.
    static let palettes: [KeyboardPalette] = [
        // 0 淺色 (Light) — iOS system semantic colors, resolved to their light variant.
        // background is a fallback only; the real backdrop comes from UIInputView's blur
        // material — KeyboardView sets its own backgroundColor to .clear so the blur shows.
        KeyboardPalette(
            background:      iosLight(.systemGray4),
            normalKey:       iosLight(.systemBackground),
            modifierKey:     iosLight(.systemGray3),
            pressedKey:      iosLight(.systemGray5),
            label:           iosLight(.label),
            modifierLabel:   iosLight(.label),
            secondaryLabel:  iosLight(.secondaryLabel),
            candiBackground: iosLight(.secondarySystemBackground),
            candiText:       iosLight(.label),
            candiHighlight:  iosLight(.systemBackground)),
        // 1 深色 (Dark) — iOS system semantic colors, resolved to their dark variant.
        KeyboardPalette(
            background:      iosDark(.systemGray4),
            normalKey:       iosDark(.systemGray2),
            modifierKey:     iosDark(.systemGray4),
            pressedKey:      iosDark(.systemGray),
            label:           iosDark(.label),
            modifierLabel:   iosDark(.label),
            secondaryLabel:  iosDark(.secondaryLabel),
            candiBackground: iosDark(.secondarySystemBackground),
            candiText:       iosDark(.label),
            candiHighlight:  iosDark(.systemGray2)),
        // 2 粉紅 (Pink) — modifier bg #F173AC (dark pink), white label
        KeyboardPalette(
            background:      h(0xFAD5E5),
            normalKey:       h(0xF49AC1),
            modifierKey:     h(0xF173AC),
            pressedKey:      h(0xF173AC),
            label:           h(0xFFFFFF),
            modifierLabel:   h(0xFFFFFF),
            secondaryLabel:  h(0xC74A72),
            candiBackground: h(0xFEF3F7),
            candiText:       h(0x000000),
            candiHighlight:  h(0xF49AC1)),
        // 3 科技藍 (Tech Blue) — normal label #314453 (dark), modifier bg #6699CC needs white
        KeyboardPalette(
            background:      h(0xC5DBEC),
            normalKey:       h(0x9BC5E4),
            modifierKey:     h(0x6699CC),
            pressedKey:      h(0x6699CC),
            label:           h(0x314453),
            modifierLabel:   h(0xFFFFFF),
            secondaryLabel:  h(0xFFFFFF),
            candiBackground: h(0xD8E7F3),
            candiText:       h(0x000000),
            candiHighlight:  h(0x9BC5E4)),
        // 4 時尚紫 (Fashion Purple) — modifier bg #8F53A1 (dark purple), white label
        KeyboardPalette(
            background:      h(0xB0ACD5),
            normalKey:       h(0xB28ABF),
            modifierKey:     h(0x8F53A1),
            pressedKey:      h(0x8F53A1),
            label:           h(0xEEEEEE),
            modifierLabel:   h(0xFFFFFF),
            secondaryLabel:  h(0xFFFFFF),
            candiBackground: h(0xEFEDFF),
            candiText:       h(0x000000),
            candiHighlight:  h(0xB28ABF)),
        // 5 放鬆綠 (Relax Green) — modifier bg #009444 (dark green), white label
        KeyboardPalette(
            background:      h(0x8DC63F),
            normalKey:       h(0x39B54A),
            modifierKey:     h(0x009444),
            pressedKey:      h(0x009444),
            label:           h(0x003A17),
            modifierLabel:   h(0xFFFFFF),
            secondaryLabel:  h(0xFFFFFF),
            candiBackground: h(0xF2F5D5),
            candiText:       h(0x000000),
            candiHighlight:  h(0x39B54A)),
    ]

    /// Convenience: build a UIColor from a 24-bit RGB hex literal (e.g. 0xFAD5E5).
    static func h(_ rgb: UInt32) -> UIColor {
        UIColor(red:   CGFloat((rgb >> 16) & 0xFF) / 255,
                green: CGFloat((rgb >>  8) & 0xFF) / 255,
                blue:  CGFloat( rgb        & 0xFF) / 255,
                alpha: 1)
    }

    /// Resolve an iOS dynamic system color to its light-mode variant, freezing it so
    /// palette[0] renders as "Light" regardless of the current `userInterfaceStyle`.
    static func iosLight(_ color: UIColor) -> UIColor {
        color.resolvedColor(with: UITraitCollection(userInterfaceStyle: .light))
    }

    /// Resolve an iOS dynamic system color to its dark-mode variant, freezing it so
    /// palette[1] renders as "Dark" regardless of the current `userInterfaceStyle`.
    static func iosDark(_ color: UIColor) -> UIColor {
        color.resolvedColor(with: UITraitCollection(userInterfaceStyle: .dark))
    }
}

protocol KeyboardViewDelegate: AnyObject {
    func keyboardView(_ view: KeyboardView, didPress keyDef: KeyDef)
    func keyboardView(_ view: KeyboardView, didRelease keyDef: KeyDef)
    func keyboardView(_ view: KeyboardView, didUpdateShiftHoldActive active: Bool)
    func keyboardView(_ view: KeyboardView, didLongPress keyDef: KeyDef)
    /// Called when a key with a non-empty `popupKeyboard` is long-pressed.
    /// `sourceRect` is the key's frame in the KeyboardView's coordinate space.
    func keyboardView(_ view: KeyboardView, didLongPressPopupKey keyDef: KeyDef, sourceRect: CGRect)
    /// Called on touchDown for non-modifier keys — host should show a key-preview popup.
    /// `keyRect` is the key's frame in the KeyboardView's coordinate space.
    func keyboardView(_ view: KeyboardView, showPreviewFor keyDef: KeyDef, keyRect: CGRect)
    /// Called on touchUp/cancel — host should dismiss the key preview.
    func keyboardViewDismissPreview(_ view: KeyboardView)
    /// Called continuously while the user slides horizontally on the space bar.
    /// `steps` is the signed number of caret positions to move (negative = left).
    func keyboardView(_ view: KeyboardView, didMoveCaretBy steps: Int)
}

final class KeyboardView: UIView, UIInputViewAudioFeedback {
    /// Required by UIInputViewAudioFeedback so UIDevice.current.playInputClick() actually plays.
    /// The system only plays the click sound when the visible input view returns true here.
    var enableInputClicksWhenVisible: Bool { true }

    static func shouldUseDualRowGesture(isPad: Bool, layoutId: String, keyDef: KeyDef) -> Bool {
        KeyboardGesturePolicy.shouldUseDualRowGesture(isPad: isPad, layoutId: layoutId, keyDef: keyDef)
    }

    static func shouldUseLimeOptionsMenuGesture(keyDef: KeyDef,
                                                 legacyGlobeMode: Bool = false) -> Bool {
        KeyboardGesturePolicy.shouldUseLimeOptionsMenuGesture(
            keyDef: keyDef, legacyGlobeMode: legacyGlobeMode)
    }

    weak var delegate: KeyboardViewDelegate?

    private var layout: LimeKeyLayout
    private var isShiftOn: Bool = false
    private var rowViews: [UIView] = []
    private var repeatTimer: Timer?
    private var repeatKeyDef: KeyDef?
    private weak var globeButton: UIButton?
    /// Weak ref to the bottom-row `-3` (LimeKeyCode.done) button — needed in legacy
    /// iPhone globe mode so we can swap its SF Symbol image when the flag flips.
    private weak var keyboardDoneButton: UIButton?

    /// Set by KeyboardViewController. When true, the `-3` key paints as a globe and
    /// hands tap + long-press to iOS' input-mode picker (spec: docs/IPHONE_LEGACY_KB.md).
    /// Changing this triggers a full layout rebuild because the bottom-row gesture
    /// wiring is determined at button-construction time.
    var legacyGlobeMode: Bool = false {
        didSet {
            guard oldValue != legacyGlobeMode else { return }
            setLayout(layout)
        }
    }
    private var shiftHoldTrackingActive = false
    private static let styledContentTag = 92731
    /// Set by KeyboardViewController so globe button uses the system keyboard picker.
    weak var inputModeViewController: UIInputViewController? {
        didSet { configureGlobeButtonForSystemPicker() }
    }

    // MARK: - Feedback settings (spec §15)
    var feedbackVibration: Bool = false {
        didSet {
            guard oldValue != feedbackVibration else { return }
            if feedbackVibration { ensureHapticGenerator() } else { hapticGenerator = nil }
        }
    }
    var feedbackSound:     Bool = false
    var vibrateLevel: Int = 40 {
        didSet {
            guard oldValue != vibrateLevel else { return }
            rebuildHapticGenerator()
        }
    }

    // Stored haptic generator. Held across keystrokes and re-prepared after each fire
    // so the Taptic Engine stays warm. Rebuilding/preparing on every keypress (the old
    // computed-property pattern) caused two bugs:
    //   1. cold-start latency → the pulse arrived after the visible press, reading as
    //      "haptic feels longer than the iOS system keyboard";
    //   2. main-thread + haptic-subsystem load during rapid typing → UIKit dropped
    //      intermediate .touchDown events, so middle keys in a fast burst were missed.
    private var hapticGenerator: UIFeedbackGenerator?
    private var lastHapticAt: CFTimeInterval = 0
    private let minHapticInterval: CFTimeInterval = 0.025   // 40 Hz ceiling

    private func ensureHapticGenerator() {
        if hapticGenerator == nil { rebuildHapticGenerator() }
    }

    private func rebuildHapticGenerator() {
        guard feedbackVibration else { hapticGenerator = nil; return }
        hapticGenerator = Self.makeHapticGenerator(for: vibrateLevel)
        hapticGenerator?.prepare()
    }

    /// 5 distinct intensities so each "震動強度" setting actually feels different.
    /// Lowest level uses UISelectionFeedbackGenerator — the subtlest public-API tick,
    /// closest in feel to Apple's stock keyboard. UIImpactFeedbackGenerator(.light)
    /// is heavier/longer than the system keyboard tick, so it is not the floor.
    static func makeHapticGenerator(for level: Int) -> UIFeedbackGenerator {
        switch level {
        case ..<15:  return UISelectionFeedbackGenerator()              // 10 特弱
        case ..<30:  return UIImpactFeedbackGenerator(style: .soft)     // 20 弱
        case ..<50:  return UIImpactFeedbackGenerator(style: .light)    // 40 中
        case ..<70:  return UIImpactFeedbackGenerator(style: .medium)   // 60 強
        default:     return UIImpactFeedbackGenerator(style: .heavy)    // 80 特強
        }
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
        gen.prepare()   // re-warm engine for the next press
    }

    /// Pre-warm the Taptic Engine so the very first keypress is not cold.
    /// Called by KeyboardViewController after applyFeedbackSettings().
    func prepareHapticGenerator() {
        ensureHapticGenerator()
        hapticGenerator?.prepare()
    }

    // isPad: trait-collection-based (false in iPhone compat mode on iPad).
    // Controls layout JSON selection, fonts, gaps, corner radius, and 1/3-split logic.
    private var isPad: Bool { LayoutLoader.hostIsPad }
    // isPadHardware: UIDevice-based (true on any iPad hardware, including compat mode).
    // Controls row heights only — compat mode gets iPad-sized rows for ergonomics
    // even though it loads the phone layout JSON with phone fonts/gaps.
    private let isPadHardware = UIDevice.current.userInterfaceIdiom == .pad
    // isPadCompat: true when running an iPhone app on iPad hardware (compat mode).
    // Controls the PadCompat font tier — taller than phone to fill the extra row
    // height, but not as wide as iPad since key columns stay phone-narrow.
    private var isPadCompat: Bool { isPadHardware && !isPad }
    private let rowHeightPortrait:              CGFloat = LayoutMetrics.KeyboardRow.Phone.portraitRow
    private let bottomRowHeightPortrait:        CGFloat = LayoutMetrics.KeyboardRow.Phone.portraitBottomRow
    private let rowHeightLandscape:             CGFloat = LayoutMetrics.KeyboardRow.Phone.landscapeRow
    private let bottomRowHeightLandscape:       CGFloat = LayoutMetrics.KeyboardRow.Phone.landscapeBottomRow
    private let rowHeightPortraitIPad:          CGFloat = LayoutMetrics.KeyboardRow.Pad.portraitRow
    private let bottomRowHeightPortraitIPad:    CGFloat = LayoutMetrics.KeyboardRow.Pad.portraitBottomRow
    private let rowHeightLandscapeIPad:         CGFloat = LayoutMetrics.KeyboardRow.Pad.landscapeRow
    private let bottomRowHeightLandscapeIPad:   CGFloat = LayoutMetrics.KeyboardRow.Pad.landscapeBottomRow
    private let rowHeightPortraitCompat:        CGFloat = LayoutMetrics.KeyboardRow.PadCompat.portraitRow
    private let bottomRowHeightPortraitCompat:  CGFloat = LayoutMetrics.KeyboardRow.PadCompat.portraitBottomRow
    private let rowHeightLandscapeCompat:       CGFloat = LayoutMetrics.KeyboardRow.PadCompat.landscapeRow
    private let bottomRowHeightLandscapeCompat: CGFloat = LayoutMetrics.KeyboardRow.PadCompat.landscapeBottomRow
    private let keyShadowOpacity: Float = LayoutMetrics.Key.shadowOpacity
    private var keyHGap:         CGFloat { LayoutMetrics.KeyboardRow.keyHGap(isPad: isPad) }
    private var keyVGap:         CGFloat { LayoutMetrics.KeyboardRow.keyVGap(isPad: isPad) }
    private var keyCornerRadius: CGFloat { LayoutMetrics.KeyboardRow.keyCornerRadius(isPad: isPad) }

    /// Set by KeyboardViewController in initOnStartInput from textDocumentProxy.returnKeyType.
    /// Drives the Enter-key icon/label substitution applied in styleKeyContent — e.g. URL/search
    /// fields render a magnifier instead of the JSON's "return" icon, matching Apple's keyboard.
    var returnKeyType: UIReturnKeyType = .default {
        didSet {
            guard returnKeyType != oldValue else { return }
            guard !rowViews.isEmpty else { return }
            rowViews.forEach { $0.removeFromSuperview() }
            rowViews.removeAll()
            globeButton = nil
            keyboardDoneButton = nil
            shiftKeyButtons.removeAll()
            buildKeys()
            updateShiftKeyIcon()
        }
    }

    /// Set by KeyboardViewController in viewWillLayoutSubviews; triggers a full rebuild.
    var isLandscape: Bool = false {
        didSet {
            guard isLandscape != oldValue else { return }
            rowViews.forEach { $0.removeFromSuperview() }
            rowViews.removeAll()
            globeButton = nil
            keyboardDoneButton = nil
            buildKeys()
        }
    }

    /// Multiplier on row height (mirrors Android keySizeScale from getKeyboardSize()).
    /// Values: 0.8=特小 0.9=小 1.0=一般 1.1=大 1.2=特大. Set by KeyboardViewController from keyboard_size pref.
    var keySizeScale: CGFloat = 1.0 {
        didSet {
            guard keySizeScale != oldValue else { return }
            rowViews.forEach { $0.removeFromSuperview() }
            rowViews.removeAll()
            globeButton = nil
            keyboardDoneButton = nil
            shiftKeyButtons.removeAll()
            buildKeys()
            updateShiftKeyIcon()
        }
    }

    /// 0=none, 1=above keyboard, 2=below keyboard.
    var showArrowKey: Int = 0 {
        didSet {
            guard showArrowKey != oldValue else { return }
            rowViews.forEach { $0.removeFromSuperview() }
            rowViews.removeAll()
            globeButton = nil
            keyboardDoneButton = nil
            shiftKeyButtons.removeAll()
            buildKeys()
            updateShiftKeyIcon()
        }
    }

    /// When true (iPad only), each key row is split into left and right halves with a gap.
    var splitMode: Bool = false {
        didSet {
            guard splitMode != oldValue else { return }
            rowViews.forEach { $0.removeFromSuperview() }
            rowViews.removeAll()
            globeButton = nil
            keyboardDoneButton = nil
            shiftKeyButtons.removeAll()
            buildKeys()
            updateShiftKeyIcon()
        }
    }

    private var rowHeight: CGFloat {
        let base: CGFloat
        switch (isPadHardware, isPad) {
        case (true, false): base = isLandscape ? rowHeightLandscapeCompat : rowHeightPortraitCompat
        case (true, true):  base = isLandscape ? rowHeightLandscapeIPad   : rowHeightPortraitIPad
        default:            base = isLandscape ? rowHeightLandscape        : rowHeightPortrait
        }
        return base * keySizeScale
    }
    private var bottomRowHeight: CGFloat {
        let base: CGFloat
        switch (isPadHardware, isPad) {
        case (true, false): base = isLandscape ? bottomRowHeightLandscapeCompat : bottomRowHeightPortraitCompat
        case (true, true):  base = isLandscape ? bottomRowHeightLandscapeIPad   : bottomRowHeightPortraitIPad
        default:            base = isLandscape ? bottomRowHeightLandscape        : bottomRowHeightPortrait
        }
        return base * keySizeScale
    }

    // MARK: - Theme
    /// Resolved theme index 0–5. Set by KeyboardViewController from resolvedKeyboardTheme.
    var theme: Int = 0 {
        didSet { guard oldValue != theme else { return }; applyTheme() }
    }
    private var palette: KeyboardPalette {
        KeyboardPalette.palettes[max(0, min(theme, KeyboardPalette.palettes.count - 1))]
    }
    private var normalKeyColor:   UIColor { palette.normalKey }
    private var modifierKeyColor: UIColor { palette.modifierKey }
    private var pressedKeyColor:  UIColor { palette.pressedKey }

    private var keySingleLabelFont: UIFont {
        UIFont.systemFont(ofSize: LayoutMetrics.Key.singleLabelFontSize(isPad: isPad, isPadCompat: isPadCompat), weight: .regular)
    }

    private var keyLabelFont: UIFont {
        UIFont.systemFont(ofSize: LayoutMetrics.Key.primaryLabelFontSize(isPad: isPad, isPadCompat: isPadCompat), weight: .light)
    }
    private var keySublabelFont: UIFont {
        UIFont.systemFont(ofSize: LayoutMetrics.Key.sublabelFontSize(isPad: isPad, isPadCompat: isPadCompat), weight: .regular)
    }
    private var keyDualSlidingFont: UIFont {
        UIFont.systemFont(ofSize: LayoutMetrics.Key.primaryLabelFontSize(isPad: isPad, isPadCompat: isPadCompat), weight: .regular)
    }
    private var keyLabelFontLand: UIFont {
        UIFont.systemFont(ofSize: LayoutMetrics.Key.primaryLabelFontSize(isPad: isPad, isPadCompat: isPadCompat), weight: .light)
    }
    private var keySublabelFontLand: UIFont {
        UIFont.systemFont(ofSize: LayoutMetrics.Key.sublabelFontSize(isPad: isPad, isPadCompat: isPadCompat), weight: .regular)
    }

    // MARK: - Init
    init(layout: LimeKeyLayout) {
        self.layout = layout
        super.init(frame: .zero)
        backgroundColor = .clear
        buildKeys()
    }
    required init?(coder: NSCoder) { fatalError("not used") }

    // MARK: - Layout switch
    func setLayout(_ newLayout: LimeKeyLayout) {
        layout = newLayout
        rowViews.forEach { $0.removeFromSuperview() }
        rowViews.removeAll()
        globeButton = nil
        keyboardDoneButton = nil
        shiftKeyButtons.removeAll()
        buildKeys()
        updateShiftKeyIcon()
    }

    func previewLayout(_ previewLayout: LimeKeyLayout?) {
        let buttons = renderedKeyButtons()
        let previewKeys = renderedKeys(for: previewLayout ?? layout)
        guard buttons.count == previewKeys.count else { return }
        for (btn, keyDef) in zip(buttons, previewKeys) {
            applyButtonStyle(btn, keyDef: keyDef, rowHeight: rowHeight, totalPercent: 100)
        }
        updateShiftKeyIcon()
    }

    /// Apply the current theme palette: update background and rebuild all key buttons.
    func applyTheme() {
        backgroundColor = .clear
        rowViews.forEach { $0.removeFromSuperview() }
        rowViews.removeAll()
        globeButton = nil
        keyboardDoneButton = nil
        shiftKeyButtons.removeAll()
        buildKeys()
        updateShiftKeyIcon()
    }

    /// Sum of all row heights for the current layout, including the arrow row if shown.
    /// Use this in KeyboardViewController.applyHeight() instead of a flat constant.
    var preferredHeight: CGFloat {
        let base = layout.rows.reduce(0) { $0 + ($1.isBottomRow ? bottomRowHeight : rowHeight) }
        return base + (showArrowKey != 0 ? rowHeight : 0)
    }

    // MARK: - Shift state

    /// Three-state shift: off / one-shot / caps-lock (mirrors Android mCapsLock + isShifted).
    enum ShiftState { case off, on, capsLock }

    private(set) var shiftState: ShiftState = .off
    /// Weak ref to the shift key button — stored during buildKeys for icon updates.
    private var shiftKeyButtons: [UIButton] = []

    /// Update shift state and refresh the shift key icon.
    /// Call from KeyboardViewController.setShift(_:capsLock:).
    func setShiftState(_ state: ShiftState) {
        guard state != shiftState else { return }
        shiftState = state
        isShiftOn  = state != .off
        updateShiftKeyIcon()
    }

    private func updateShiftKeyIcon() {
        guard !shiftKeyButtons.isEmpty else { return }
        let iconName: String
        switch shiftState {
        case .off:      iconName = "shift"
        case .on:       iconName = "shift.fill"
        case .capsLock: iconName = "capslock.fill"
        }
        let cfg = UIImage.SymbolConfiguration(pointSize: LayoutMetrics.Key.shiftIconSize, weight: .regular)
        let tint = shiftState == .off ? palette.modifierLabel : UIColor.systemBlue
        for btn in shiftKeyButtons {
            btn.setImage(UIImage(systemName: iconName, withConfiguration: cfg), for: .normal)
            btn.tintColor = tint
        }
    }

    func setShift(_ on: Bool) {
        isShiftOn = on
    }

    /// Show or hide the globe key based on needsInputModeSwitchKey (spec §10).
    func setGlobeKeyVisible(_ visible: Bool) {
        globeButton?.isHidden = !visible
    }

    private func configureGlobeButtonForSystemPicker() {
        guard let btn = globeButton,
              let ivc = inputModeViewController else { return }

        btn.removeTarget(self, action: #selector(keyboardKeyTapped(_:)), for: .touchUpInside)
        btn.removeTarget(nil, action: #selector(UIInputViewController.handleInputModeList(from:with:)),
                         for: .allTouchEvents)
        btn.gestureRecognizers?
            .filter { $0 is UILongPressGestureRecognizer }
            .forEach { btn.removeGestureRecognizer($0) }
        btn.addTarget(ivc, action: #selector(UIInputViewController.handleInputModeList(from:with:)),
                      for: .allTouchEvents)
    }

    // MARK: - Build
    private func buildKeys() {
        var prevRow: UIView? = nil

        // Collect the rows to render, injecting the arrow row at position 0 (above) or at the end (below).
        var renderRows: [(row: KeyRow, index: Int, isArrow: Bool)] = []
        if showArrowKey == 1 {
            renderRows.append((arrowKeyRow, -1, true))
        }
        for (i, row) in layout.rows.enumerated() {
            renderRows.append((row, i, false))
        }
        if showArrowKey == 2 {
            renderRows.append((arrowKeyRow, -1, true))
        }

        for entry in renderRows {
            let rh = (!entry.isArrow && entry.row.isBottomRow) ? bottomRowHeight : rowHeight
            let rowView = splitMode
                ? makeSplitRow(row: entry.row, rowHeight: rh)
                : makeRow(row: entry.row, rowIndex: entry.index, rowHeight: rh)
            addSubview(rowView)
            rowViews.append(rowView)

            rowView.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([
                rowView.leadingAnchor.constraint(equalTo: leadingAnchor),
                rowView.trailingAnchor.constraint(equalTo: trailingAnchor),
                rowView.heightAnchor.constraint(equalToConstant: rh),
            ])

            if let prev = prevRow {
                rowView.topAnchor.constraint(equalTo: prev.bottomAnchor).isActive = true
            } else {
                rowView.topAnchor.constraint(equalTo: topAnchor).isActive = true
            }
            prevRow = rowView
        }

        if let last = prevRow {
            last.bottomAnchor.constraint(equalTo: bottomAnchor).isActive = true
        }
    }

    /// A row of four arrow keys used when showArrowKey != 0.
    private var arrowKeyRow: KeyRow {
        KeyRow(keys: [
            KeyDef(code: LimeKeyCode.arrowLeft.rawValue,  widthPercent: 25, icon: "arrow.left",  isRepeatable: true, isModifier: true),
            KeyDef(code: LimeKeyCode.arrowUp.rawValue,    widthPercent: 25, icon: "arrow.up",    isRepeatable: true, isModifier: true),
            KeyDef(code: LimeKeyCode.arrowDown.rawValue,  widthPercent: 25, icon: "arrow.down",  isRepeatable: true, isModifier: true),
            KeyDef(code: LimeKeyCode.arrowRight.rawValue, widthPercent: 25, icon: "arrow.right", isRepeatable: true, isModifier: true),
        ], isBottomRow: false)
    }

    /// Renders a row split into left and right halves with a gap — iPad split-keyboard mode.
    private func makeSplitRow(row: KeyRow, rowHeight: CGFloat) -> UIView {
        let rowView = UIView()
        rowView.backgroundColor = .clear

        let keys = row.keys
        guard !keys.isEmpty else { return rowView }

        // Find split index: first key where cumulative widthPercent >= 50% of total
        let total = keys.reduce(0) { $0 + $1.widthPercent }
        var cumulative: CGFloat = 0
        var splitIndex = keys.count / 2
        for (i, k) in keys.enumerated() {
            cumulative += k.widthPercent
            if cumulative >= total / 2 {
                splitIndex = i + 1
                break
            }
        }

        let leftKeys  = Array(keys[..<splitIndex])
        let rightKeys = Array(keys[splitIndex...])
        let splitGapFraction = LayoutMetrics.KeyboardRow.splitGapFraction

        func addHalf(_ halfKeys: [KeyDef], leading: Bool) {
            guard !halfKeys.isEmpty else { return }
            let halfPercent = halfKeys.reduce(0) { $0 + $1.widthPercent }
            let halfFraction = (halfPercent / total) * (1 - splitGapFraction)

            let contentView = UIView()
            contentView.backgroundColor = .clear
            contentView.translatesAutoresizingMaskIntoConstraints = false
            rowView.addSubview(contentView)
            NSLayoutConstraint.activate([
                contentView.topAnchor.constraint(equalTo: rowView.topAnchor),
                contentView.bottomAnchor.constraint(equalTo: rowView.bottomAnchor),
                contentView.widthAnchor.constraint(equalTo: rowView.widthAnchor, multiplier: halfFraction),
            ])
            if leading {
                contentView.leadingAnchor.constraint(equalTo: rowView.leadingAnchor).isActive = true
            } else {
                contentView.trailingAnchor.constraint(equalTo: rowView.trailingAnchor).isActive = true
            }

            var prevBtn: UIButton? = nil
            for keyDef in halfKeys {
                let btn = makeKeyButton(keyDef: keyDef, rowHeight: rowHeight, totalPercent: halfPercent)
                contentView.addSubview(btn)
                btn.translatesAutoresizingMaskIntoConstraints = false
                NSLayoutConstraint.activate([
                    btn.topAnchor.constraint(equalTo: contentView.topAnchor, constant: keyVGap),
                    btn.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -keyVGap),
                    btn.widthAnchor.constraint(equalTo: contentView.widthAnchor,
                                               multiplier: keyDef.widthPercent / halfPercent,
                                               constant: -keyHGap),
                ])
                if let prev = prevBtn {
                    btn.leadingAnchor.constraint(equalTo: prev.trailingAnchor, constant: keyHGap).isActive = true
                } else {
                    btn.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: keyHGap / 2).isActive = true
                }
                prevBtn = btn
            }
        }

        addHalf(leftKeys,  leading: true)
        addHalf(rightKeys, leading: false)
        return rowView
    }

    private func makeRow(row: KeyRow, rowIndex: Int, rowHeight: CGFloat) -> UIView {
        let rowView = UIView()
        rowView.backgroundColor = .clear

        // Total width percent for this row. When < 100, the keys are narrower than the
        // full row — center them with equal left/right whitespace via a content container.
        let totalPercent = row.keys.reduce(0) { $0 + $1.widthPercent }
        let widthMultiplier = min(1.0, totalPercent / 100.0)

        let contentView = UIView()
        contentView.backgroundColor = .clear
        contentView.translatesAutoresizingMaskIntoConstraints = false
        rowView.addSubview(contentView)
        NSLayoutConstraint.activate([
            contentView.centerXAnchor.constraint(equalTo: rowView.centerXAnchor),
            contentView.topAnchor.constraint(equalTo: rowView.topAnchor),
            contentView.bottomAnchor.constraint(equalTo: rowView.bottomAnchor),
            contentView.widthAnchor.constraint(equalTo: rowView.widthAnchor, multiplier: widthMultiplier),
        ])

        var prevButton: UIButton? = nil

        for (_, keyDef) in row.keys.enumerated() {
            let btn = makeKeyButton(keyDef: keyDef, rowHeight: rowHeight, totalPercent: totalPercent)
            contentView.addSubview(btn)

            btn.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([
                btn.topAnchor.constraint(equalTo: contentView.topAnchor, constant: keyVGap),
                btn.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -keyVGap),
                // Each key spans its proportional share of the content view width minus keyHGap,
                // so adjacent keys are separated by keyHGap pt of background.
                btn.widthAnchor.constraint(equalTo: contentView.widthAnchor,
                                           multiplier: keyDef.widthPercent / totalPercent,
                                           constant: -keyHGap),
            ])

            if let prev = prevButton {
                btn.leadingAnchor.constraint(equalTo: prev.trailingAnchor, constant: keyHGap).isActive = true
            } else {
                btn.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: keyHGap / 2).isActive = true
            }
            prevButton = btn
        }

        return rowView
    }

    private func makeKeyButton(keyDef: KeyDef, rowHeight: CGFloat, totalPercent: CGFloat) -> UIButton {
        // Transparent spacer key: no background, no shadow, no touch.
        if keyDef.code == 0 && keyDef.label.isEmpty && keyDef.icon.isEmpty {
            let spacer = UIButton()
            spacer.backgroundColor = .clear
            spacer.isUserInteractionEnabled = false
            return spacer
        }

        // Space key: custom touch tracking avoids UISwipeGestureRecognizer conflicts and
        // prevents keyDown from firing didPress(space) before swipe/long-press is resolved.
        if keyDef.code == LimeKeyCode.space.rawValue {
            return makeSpaceButton(keyDef: keyDef, rowHeight: rowHeight, totalPercent: totalPercent)
        }

        let btn = KeyButton(keyDef: keyDef)

        let isKeyboardOptionsKey = Self.shouldUseLimeOptionsMenuGesture(
            keyDef: keyDef, legacyGlobeMode: legacyGlobeMode)
        let isSystemGlobe = keyDef.code == LimeKeyCode.globe.rawValue
            && inputModeViewController != nil

        // Keyboard options keys:
        //   - single tap (touchUpInside): primary key action
        //   - long press: show LIME options menu (globe preview, spec §10)
        // MUST use touchUpInside so the long-press GR can fire before the primary action runs.
        if isKeyboardOptionsKey {
            let lp = UILongPressGestureRecognizer(target: self, action: #selector(specialLongPressed(_:)))
            lp.minimumPressDuration = LayoutMetrics.Gesture.specialKeyHoldDuration
            btn.addGestureRecognizer(lp)
        }

        // Shift key: store reference for icon updates (multiple shift keys on iPad layouts).
        if keyDef.code == LimeKeyCode.shift.rawValue {
            shiftKeyButtons.append(btn)
        }

        // Globe key (code -200): iPad JSONs still carry longPressCode=-100 for
        // compatibility, but globe long-press belongs to iOS' input-mode picker.
        if keyDef.code == LimeKeyCode.globe.rawValue {
            globeButton = btn
            if isSystemGlobe, let ivc = inputModeViewController {
                btn.addTarget(ivc, action: #selector(UIInputViewController.handleInputModeList(from:with:)),
                              for: .allTouchEvents)
            }
        }

        // Keyboard key (code -3): in legacy iPhone globe mode it takes over the
        // role of the missing system-bar globe (spec: docs/IPHONE_LEGACY_KB.md).
        // We always track it so the icon can be repainted; we wire the system
        // picker only when the policy says so.
        if keyDef.code == LimeKeyCode.done.rawValue {
            keyboardDoneButton = btn
            let wireSystemPicker = KeyboardGesturePolicy.shouldWireSystemPickerOnKeyboardKey(
                keyDef: keyDef,
                legacyGlobeMode: legacyGlobeMode,
                hasInputModeViewController: inputModeViewController != nil)
            if wireSystemPicker, let ivc = inputModeViewController {
                btn.addTarget(ivc, action: #selector(UIInputViewController.handleInputModeList(from:with:)),
                              for: .allTouchEvents)
            }
        }

        // Popup keyboard: long-press shows a mini keyboard panel (e.g. accent variants, punctuation)
        if !keyDef.popupKeyboard.isEmpty {
            let lp = UILongPressGestureRecognizer(target: self, action: #selector(popupKeyLongPressed(_:)))
            lp.minimumPressDuration = LayoutMetrics.Gesture.popupKeyboardHoldDuration
            btn.addGestureRecognizer(lp)
        }

        applyButtonStyle(btn, keyDef: keyDef, rowHeight: rowHeight, totalPercent: totalPercent)

        btn.addTarget(self, action: #selector(keyDown(_:event:)), for: .touchDown)
        btn.addTarget(self, action: #selector(keyUp(_:)), for: [.touchUpInside, .touchUpOutside])
        btn.addTarget(self, action: #selector(keyCancel(_:)), for: .touchCancel)
        // Done, globe, and popup keys fire didPress on touchUpInside (deferred so long-press can intercept)
        let isDualRowIPadKey = Self.shouldUseDualRowGesture(isPad: isPad,
                                                             layoutId: layout.id,
                                                             keyDef: keyDef)
        // In legacy iPhone globe mode the `-3` key is owned by iOS' input-mode
        // picker — we must not also fire our own touchUpInside dismiss/menu.
        let legacyOwnedByIVC = keyDef.code == LimeKeyCode.done.rawValue
            && legacyGlobeMode
            && inputModeViewController != nil
        if !legacyOwnedByIVC && (
            keyDef.code == LimeKeyCode.done.rawValue
                || isKeyboardOptionsKey
                || (keyDef.code == LimeKeyCode.globe.rawValue && !isSystemGlobe)
                || !keyDef.popupKeyboard.isEmpty || isDualRowIPadKey) {
            btn.addTarget(self, action: #selector(keyboardKeyTapped(_:)), for: .touchUpInside)
        }
        // iPad dual-row keys: pan gesture for slide-down → secondary glyph; long-press → preview secondary.
        // cancelsTouchesInView=false lets the button's touchUpInside still fire for normal taps.
        if isDualRowIPadKey {
            let pan = UIPanGestureRecognizer(target: self, action: #selector(dualRowPanned(_:)))
            pan.cancelsTouchesInView = false
            btn.addGestureRecognizer(pan)

            let lp = UILongPressGestureRecognizer(target: self, action: #selector(dualRowLongPressed(_:)))
            lp.minimumPressDuration = LayoutMetrics.Gesture.dualRowHoldDuration
            // Don't cancel the underlying touch — otherwise UIKit fires touchCancel
            // on the button when the long-press begins, which dismisses the preview
            // immediately. We need keyUp to fire only on actual finger release.
            lp.cancelsTouchesInView = false
            btn.addGestureRecognizer(lp)
        }

        return btn
    }

    @objc private func popupKeyLongPressed(_ gr: UILongPressGestureRecognizer) {
        guard gr.state == .began, let keyBtn = gr.view as? KeyButton else { return }
        keyBtn.wasLongPressed = true
        let keyRect = keyBtn.convert(keyBtn.bounds, to: self)
        fireHaptic()
        delegate?.keyboardView(self, didLongPressPopupKey: keyBtn.keyDef, sourceRect: keyRect)
    }

    /// Build the space key as a SpaceKeyButton so tap/swipe/long-press are mutually exclusive.
    private func makeSpaceButton(keyDef: KeyDef, rowHeight: CGFloat, totalPercent: CGFloat) -> UIButton {
        let btn = SpaceKeyButton(keyDef: keyDef)
        btn.restoreColor = normalKeyColor
        applyButtonStyle(btn, keyDef: keyDef, rowHeight: rowHeight, totalPercent: totalPercent)

        btn.onTap = { [weak self] in
            guard let self else { return }
            self.fireHaptic()
            if self.feedbackSound     { UIDevice.current.playInputClick() }
            self.delegate?.keyboardView(self, didPress: keyDef)
        }
        btn.onLongPress = { [weak self] in
            guard let self else { return }
            self.delegate?.keyboardView(self, didLongPress: keyDef)
        }
        btn.onCaretMove = { [weak self] steps in
            guard let self else { return }
            self.delegate?.keyboardView(self, didMoveCaretBy: steps)
        }
        return btn
    }

    /// Apply background color, corner radius and shadow to any key button.
    private func applyButtonStyle(_ btn: UIButton, keyDef: KeyDef,
                                  rowHeight: CGFloat, totalPercent: CGFloat) {
        // Apple-style accent: when the Enter key represents a non-default
        // primary action (.search / .go / .send / .next / .join / .done /
        // .route / .continue), render the key with the system-blue tint to
        // signal it submits the field. Default-return (.default) keeps the
        // standard modifier-key background.
        if enterKeyOverride(for: keyDef) != nil {
            btn.backgroundColor = .systemBlue
        } else {
            btn.backgroundColor = keyDef.isModifier ? modifierKeyColor : normalKeyColor
        }
        btn.layer.cornerRadius = keyCornerRadius
        btn.layer.masksToBounds = false
        btn.layer.shadowColor = UIColor.black.cgColor
        btn.layer.shadowOffset = CGSize(width: 0, height: LayoutMetrics.Key.shadowOffsetY)
        btn.layer.shadowOpacity = keyShadowOpacity
        btn.layer.shadowRadius = 0
        styleKeyContent(btn: btn, keyDef: keyDef, rowHeight: rowHeight, totalPercent: totalPercent)
    }

    /// Apple-style Enter-key adaptation: if `keyDef` is the Enter key (code 10) and the host's
    /// `returnKeyType` is non-default, substitute the appropriate icon (`magnifyingglass` for
    /// `.search` / `.google` / `.yahoo`, `arrow.right` for `.go`) or text label (`Send` / `Next`
    /// / `Join` / `Done` / `Route` / `Continue`). Returns nil for non-Enter keys or `.default`,
    /// in which case the JSON's `return` icon is used unchanged.
    private func enterKeyOverride(for keyDef: KeyDef) -> (icon: String, label: String)? {
        guard keyDef.code == 10 else { return nil }
        switch returnKeyType {
        case .search, .google, .yahoo:
            return (icon: "magnifyingglass", label: "")
        case .go:       return (icon: "arrow.right", label: "")
        case .send:     return (icon: "", label: "Send")
        case .next:     return (icon: "", label: "Next")
        case .join:     return (icon: "", label: "Join")
        case .route:    return (icon: "", label: "Route")
        case .done:     return (icon: "", label: "Done")
        case .continue: return (icon: "", label: "Continue")
        case .default, .emergencyCall:
            return nil
        @unknown default:
            return nil
        }
    }

    ///   • Tall key  (height ≥ width): label small top,  sublabel large bottom — vertical stack
    ///   • Wide key  (width  > height): label small left, sublabel large right  — horizontal stack
    private func styleKeyContent(btn: UIButton, keyDef: KeyDef,
                                 rowHeight: CGFloat, totalPercent: CGFloat) {
        clearStyledKeyContent(from: btn)
        let override    = enterKeyOverride(for: keyDef)
        // Accent (blue) Enter keys use white foreground so the icon/label
        // reads against the system-blue background applied in applyButtonStyle.
        let keyLabel: UIColor = (override != nil)
            ? .white
            : (keyDef.isModifier ? palette.modifierLabel : palette.label)
        // Legacy iPhone globe mode: the `-3` key paints as a globe glyph instead
        // of the keyboard-down chevron (spec: docs/IPHONE_LEGACY_KB.md). Policy
        // returns nil for every other key/mode, so the JSON icon wins.
        let policyIcon = KeyboardGesturePolicy.iconForKeyboardKey(
            keyDef: keyDef, legacyGlobeMode: legacyGlobeMode)
        let renderIcon  = policyIcon ?? override?.icon  ?? keyDef.icon
        let renderLabel = policyIcon == nil ? (override?.label ?? keyDef.label) : ""
        if !renderIcon.isEmpty {
            // SF Symbol icon key — dismiss key uses a larger point size for legibility
            let iconSize: CGFloat = renderIcon == "keyboard.chevron.compact.down"
                ? LayoutMetrics.Key.dismissIconSize
                : LayoutMetrics.Key.iconSize(isPad: isPad, isPadCompat: isPadCompat)
            let config = UIImage.SymbolConfiguration(pointSize: iconSize, weight: .regular)
            let img = UIImage(systemName: renderIcon, withConfiguration: config)
            btn.setImage(img, for: .normal)
            btn.tintColor = keyLabel
        } else if !keyDef.sublabel.isEmpty {
            let displayLabel = keyDef.label
            let container: UIView
            if keyDef.longPressCode != 0 {
                // Sliding key: label=hint (top), sublabel=tap-primary (bottom) — equal size+color.
                container = makeDualSlidingLabelView(top: displayLabel, bottom: keyDef.sublabel,
                                                     labelColor: keyLabel)
            } else {
                // iPad native: always vertical (primary small top, sublabel large bottom).
                // Phone / compat: vertical when key height ≥ width (portrait),
                // horizontal 1/3–2/3 split when key width > height (landscape or compat-on-iPad).
                let isTall: Bool
                if isPadHardware {
                    // Any iPad hardware (native or compat): always vertical.
                    // UIScreen.main.bounds.width returns full iPad width even in compat
                    // mode, making estimatedWidth > usableHeight and incorrectly
                    // triggering horizontal layout — so skip the dimension check entirely.
                    isTall = true
                } else {
                    let estimatedWidth = UIScreen.main.bounds.width
                        * (keyDef.widthPercent / totalPercent) - keyHGap
                    let usableHeight = rowHeight - 2 * keyVGap
                    isTall = usableHeight >= estimatedWidth
                }
                container = makeDualLabelView(primary: displayLabel, sub: keyDef.sublabel,
                                              isTall: isTall, labelColor: keyLabel)
            }
            container.isUserInteractionEnabled = false
            container.tag = Self.styledContentTag
            container.translatesAutoresizingMaskIntoConstraints = false
            container.clipsToBounds = true
            btn.addSubview(container)
            let wConstraint = isPad
                ? container.widthAnchor.constraint(lessThanOrEqualTo: btn.widthAnchor,
                                                    constant: LayoutMetrics.Key.dualLabelWidthMargin)
                : container.widthAnchor.constraint(equalTo: btn.widthAnchor,
                                                    constant: LayoutMetrics.Key.dualLabelWidthMargin)
            NSLayoutConstraint.activate([
                container.centerXAnchor.constraint(equalTo: btn.centerXAnchor),
                container.centerYAnchor.constraint(equalTo: btn.centerYAnchor),
                wConstraint,
                container.heightAnchor.constraint(lessThanOrEqualTo: btn.heightAnchor),
            ])
        } else {
            // Single label key
            btn.setTitle(renderLabel, for: .normal)
            btn.titleLabel?.font = keySingleLabelFont
            btn.titleLabel?.adjustsFontSizeToFitWidth = true
            btn.titleLabel?.minimumScaleFactor = 0.5
            btn.titleLabel?.lineBreakMode = .byClipping
            btn.setTitleColor(keyLabel, for: .normal)
        }

        // Popup-keyboard indicator: small "…" pinned to bottom-right corner
        if !keyDef.popupKeyboard.isEmpty {
            let dot = UILabel()
            dot.tag = Self.styledContentTag
            dot.text = "…"
            dot.font = UIFont.systemFont(ofSize: LayoutMetrics.Key.popupIndicatorFontSize, weight: .medium)
            dot.textColor = palette.secondaryLabel
            dot.isUserInteractionEnabled = false
            dot.translatesAutoresizingMaskIntoConstraints = false
            btn.addSubview(dot)
            NSLayoutConstraint.activate([
                dot.trailingAnchor.constraint(equalTo: btn.trailingAnchor,
                                              constant: LayoutMetrics.Key.popupIndicatorTrailingInset),
                dot.bottomAnchor.constraint(equalTo: btn.bottomAnchor,
                                            constant: LayoutMetrics.Key.popupIndicatorBottomInset),
            ])
        }
    }

    private func clearStyledKeyContent(from btn: UIButton) {
        btn.subviews
            .filter { $0.tag == Self.styledContentTag }
            .forEach { $0.removeFromSuperview() }
        btn.setTitle(nil, for: .normal)
        btn.setImage(nil, for: .normal)
    }

    private func renderedKeyButtons() -> [UIButton] {
        rowViews.flatMap { rowView in
            allSubviews(of: rowView).compactMap { $0 as? KeyButton }
        }
    }

    private func allSubviews(of view: UIView) -> [UIView] {
        view.subviews + view.subviews.flatMap { allSubviews(of: $0) }
    }

    private func renderedKeys(for sourceLayout: LimeKeyLayout) -> [KeyDef] {
        var rows: [KeyRow] = []
        if showArrowKey == 1 { rows.append(arrowKeyRow) }
        rows.append(contentsOf: sourceLayout.rows)
        if showArrowKey == 2 { rows.append(arrowKeyRow) }
        return rows.flatMap(\.keys).filter { !($0.code == 0 && $0.label.isEmpty && $0.icon.isEmpty) }
    }

    /// Builds a two-part label view for keys that have both a primary label and a sublabel.
    /// - `isTall` (iPad): vertical stack — primary small top, sublabel large bottom.
    /// - `!isTall` (phone/compat): horizontal 1/3–2/3 split — letter left, code right.
    private func makeDualLabelView(primary: String, sub: String,
                                   isTall: Bool, labelColor: UIColor) -> UIView {
        if isTall {
            let stack = UIStackView()
            stack.alignment = .fill   // labels fill stack width so adjustsFontSizeToFitWidth works
            stack.axis = .vertical
            stack.spacing = 0

            let primaryLbl = UILabel()
            primaryLbl.text = primary
            primaryLbl.font = keyLabelFont
            primaryLbl.textColor = palette.secondaryLabel
            primaryLbl.textAlignment = .center
            primaryLbl.adjustsFontSizeToFitWidth = true
            primaryLbl.minimumScaleFactor = 0.6
            primaryLbl.setContentHuggingPriority(.required, for: .vertical)

            let subLbl = UILabel()
            subLbl.text = sub
            subLbl.font = keySublabelFont
            subLbl.textColor = labelColor
            subLbl.textAlignment = .center
            subLbl.adjustsFontSizeToFitWidth = true
            subLbl.minimumScaleFactor = 0.6
            subLbl.setContentHuggingPriority(.required, for: .vertical)

            stack.addArrangedSubview(primaryLbl)
            stack.addArrangedSubview(subLbl)
            return stack
        } else {
            let container = UIView()

            let primaryLbl = UILabel()
            primaryLbl.text = primary
            primaryLbl.font = keyLabelFont
            primaryLbl.textColor = palette.secondaryLabel
            primaryLbl.textAlignment = .center
            primaryLbl.adjustsFontSizeToFitWidth = true
            primaryLbl.minimumScaleFactor = 0.6
            primaryLbl.translatesAutoresizingMaskIntoConstraints = false

            let subLbl = UILabel()
            subLbl.text = sub
            subLbl.font = keySublabelFont
            subLbl.textColor = labelColor
            subLbl.textAlignment = .center
            subLbl.adjustsFontSizeToFitWidth = true
            subLbl.minimumScaleFactor = 0.6
            subLbl.translatesAutoresizingMaskIntoConstraints = false

            container.addSubview(primaryLbl)
            container.addSubview(subLbl)

            NSLayoutConstraint.activate([
                primaryLbl.leadingAnchor.constraint(equalTo: container.leadingAnchor),
                primaryLbl.widthAnchor.constraint(equalTo: container.widthAnchor, multiplier: 1.0/3.0),
                primaryLbl.topAnchor.constraint(equalTo: container.topAnchor),
                primaryLbl.bottomAnchor.constraint(equalTo: container.bottomAnchor),

                subLbl.leadingAnchor.constraint(equalTo: primaryLbl.trailingAnchor),
                subLbl.trailingAnchor.constraint(equalTo: container.trailingAnchor),
                subLbl.topAnchor.constraint(equalTo: container.topAnchor),
                subLbl.bottomAnchor.constraint(equalTo: container.bottomAnchor),
            ])
            return container
        }
    }

    /// Builds a two-part label view for dual-sliding keys (hint\nprimary).
    /// Both labels use primary color — distinguishes from sublabel keys where the
    /// primary letter is rendered in secondary color.
    private func makeDualSlidingLabelView(top: String, bottom: String,
                                          labelColor: UIColor) -> UIView {
        let stack = UIStackView()
        stack.alignment = .center
        stack.axis = .vertical
        stack.spacing = 0

        let topLbl = UILabel()
        topLbl.text = top
        topLbl.font = keyDualSlidingFont
        topLbl.textColor = labelColor
        topLbl.setContentHuggingPriority(.required, for: .horizontal)
        topLbl.setContentHuggingPriority(.required, for: .vertical)

        let bottomLbl = UILabel()
        bottomLbl.text = bottom
        bottomLbl.font = keyDualSlidingFont
        bottomLbl.textColor = labelColor
        bottomLbl.setContentHuggingPriority(.required, for: .horizontal)
        bottomLbl.setContentHuggingPriority(.required, for: .vertical)

        stack.addArrangedSubview(topLbl)
        stack.addArrangedSubview(bottomLbl)
        return stack
    }

    // MARK: - Touch handling
    @objc private func keyDown(_ btn: UIButton, event: UIEvent) {
        guard let keyBtn = btn as? KeyButton else { return }
        keyBtn.wasLongPressed = false   // reset each new touch cycle
        btn.backgroundColor = pressedKeyColor

        let keyDef = keyBtn.keyDef
        updateShiftHoldTracking(for: keyDef, event: event)

        // Haptic / audio feedback (spec §15)
        fireHaptic()
        if feedbackSound     { UIDevice.current.playInputClick() }

        // Show key preview — phone only; iPad keys are large enough that press-state
        // color change is sufficient feedback (matches Apple's stock iPad keyboard).
        if keyDef.icon.isEmpty && !keyDef.isModifier
            && keyDef.code != LimeKeyCode.space.rawValue
            && !isPad {
            let keyRect = btn.convert(btn.bounds, to: self)
            delegate?.keyboardView(self, showPreviewFor: keyDef, keyRect: keyRect)
        }

        // Keyboard dismiss key (code -3) and globe key (code -200): defer didPress to
        // touchUpInside so the long-press GR can fire before the action runs (spec §10).
        // Globe key must not fire advanceToNextInputMode() immediately on touchDown or the
        // keyboard switches before the long-press menu can appear.
        // All popup-keyboard keys: also deferred so the long-press popup can appear
        // before the primary action fires (prevents double-insert on non-modifier popup keys).
        // iPad dual-row top keys: deferred so slide-down gesture can intercept and commit
        // the secondary glyph (longPressCode) instead of the primary.
        let isDualRowIPad = Self.shouldUseDualRowGesture(isPad: isPad,
                                                          layoutId: layout.id,
                                                          keyDef: keyDef)
        let deferToTouchUp = keyDef.code == LimeKeyCode.done.rawValue
                          || keyDef.code == LimeKeyCode.globe.rawValue
                          || !keyDef.popupKeyboard.isEmpty
                          || isDualRowIPad
        if !deferToTouchUp {
            delegate?.keyboardView(self, didPress: keyDef)
        }

        // Start repeat timer for repeatable keys
        if keyDef.isRepeatable {
            repeatKeyDef = keyDef
            repeatTimer = Timer.scheduledTimer(withTimeInterval: LayoutMetrics.Gesture.repeatStartDelay,
                                               repeats: false) { [weak self] _ in
                self?.startRepeating()
            }
        }
    }

    /// Fires `didPress` for the keyboard dismiss key on touchUpInside (see keyDown comment).
    /// Suppressed if the key was long-pressed (wasLongPressed flag) to prevent dismissing
    /// the keyboard immediately after the long-press options menu appears.
    /// For iPad dual-row keys with a completed slide-down, wasLongPressed is set by
    /// dualRowPanned so this handler is suppressed (secondary was already committed there).
    @objc private func keyboardKeyTapped(_ btn: UIButton) {
        guard let keyBtn = btn as? KeyButton, !keyBtn.wasLongPressed else { return }
        delegate?.keyboardView(self, didPress: keyBtn.keyDef)
    }

    @objc private func keyUp(_ btn: UIButton) {
        guard let keyBtn = btn as? KeyButton else { return }
        if keyBtn.keyDef.code == LimeKeyCode.shift.rawValue {
            shiftHoldTrackingActive = false
        }
        let isModifier = keyBtn.keyDef.isModifier
        btn.backgroundColor = isModifier ? modifierKeyColor : normalKeyColor
        delegate?.keyboardViewDismissPreview(self)
        delegate?.keyboardView(self, didRelease: keyBtn.keyDef)
        stopRepeating()
        keyBtn.wasSlideDown = false
    }

    @objc private func keyCancel(_ btn: UIButton) {
        guard let keyBtn = btn as? KeyButton else { return }
        let isModifier = keyBtn.keyDef.isModifier
        btn.backgroundColor = isModifier ? modifierKeyColor : normalKeyColor
        delegate?.keyboardViewDismissPreview(self)
        stopRepeating()
        keyBtn.wasSlideDown = false
    }

    private func updateShiftHoldTracking(for keyDef: KeyDef, event: UIEvent) {
        if keyDef.code == LimeKeyCode.shift.rawValue {
            shiftHoldTrackingActive = true
            return
        }

        guard shiftHoldTrackingActive else { return }
        let activeTouchCount = event.allTouches?
            .filter { $0.phase != .ended && $0.phase != .cancelled }
            .count ?? 1
        let active = ShiftHoldTouchPolicy.isShiftStillHeld(activeTouchCount: activeTouchCount,
                                                           wasShiftAlreadyHeld: shiftHoldTrackingActive)
        if !active {
            shiftHoldTrackingActive = false
        }
        delegate?.keyboardView(self, didUpdateShiftHoldActive: active)
    }

    private func startRepeating() {
        repeatTimer = Timer.scheduledTimer(withTimeInterval: LayoutMetrics.Gesture.repeatInterval,
                                           repeats: true) { [weak self] _ in
            guard let self = self, let keyDef = self.repeatKeyDef else { return }
            // One haptic tick per repeated character, matching the iOS system keyboard
            // (backspace and arrow keys). Throttled by fireHaptic()'s minHapticInterval.
            self.fireHaptic()
            self.delegate?.keyboardView(self, didPress: keyDef)
        }
    }

    private func stopRepeating() {
        repeatTimer?.invalidate()
        repeatTimer = nil
        repeatKeyDef = nil
    }

    /// Long-press handler for keyboard dismiss key and legacy globe key.
    @objc private func specialLongPressed(_ gr: UILongPressGestureRecognizer) {
        guard gr.state == .began, let keyBtn = gr.view as? KeyButton else { return }
        // Mark so touchUpInside (keyboardKeyTapped) does NOT fire dismiss/action after long press.
        keyBtn.wasLongPressed = true
        delegate?.keyboardView(self, didLongPress: keyBtn.keyDef)
    }

    /// Long-press handler for iPad dual-row keys: shows a key preview with the sliding char.
    @objc private func dualRowLongPressed(_ gr: UILongPressGestureRecognizer) {
        guard isPad, let keyBtn = gr.view as? KeyButton else { return }
        let keyDef = keyBtn.keyDef
        guard keyDef.longPressCode != 0 else { return }
        let slidingDef = KeyDef(code: keyDef.longPressCode,
                                codes: [keyDef.longPressCode],
                                label: keyDef.label, sublabel: "",
                                widthPercent: keyDef.widthPercent,
                                isRepeatable: false, isModifier: false, isSticky: false,
                                longPressCode: 0)
        switch gr.state {
        case .began:
            keyBtn.wasLongPressed = true
            let keyRect = keyBtn.convert(keyBtn.bounds, to: self)
            delegate?.keyboardView(self, showPreviewFor: slidingDef, keyRect: keyRect)
        case .ended:
            delegate?.keyboardView(self, didPress: slidingDef)
            delegate?.keyboardViewDismissPreview(self)
        case .cancelled, .failed:
            delegate?.keyboardViewDismissPreview(self)
        default:
            break
        }
    }

    /// Pan handler for iPad dual-row top keys.
    /// A downward slide past the threshold commits the secondary glyph (longPressCode)
    /// instead of the primary (code). The key label morphs to show only the secondary
    /// while the slide is active — matches Apple's stock iPad keyboard behavior.
    @objc private func dualRowPanned(_ gr: UIPanGestureRecognizer) {
        guard isPad, let keyBtn = gr.view as? KeyButton else { return }
        let keyDef = keyBtn.keyDef
        guard keyDef.longPressCode != 0, keyDef.popupKeyboard.isEmpty else { return }

        let translation = gr.translation(in: self)
        let threshold = LayoutMetrics.Gesture.dualRowSwipeThreshold(landscape: isLandscape)

        switch gr.state {
        case .changed:
            if translation.y > threshold && !keyBtn.wasSlideDown {
                keyBtn.wasSlideDown = true
                setDualRowLabelSecondaryOnly(keyBtn, secondaryOnly: true)
            } else if translation.y <= threshold && keyBtn.wasSlideDown {
                // User slid back up — revert to primary glyph display.
                keyBtn.wasSlideDown = false
                setDualRowLabelSecondaryOnly(keyBtn, secondaryOnly: false)
            }
        case .ended, .cancelled:
            if keyBtn.wasSlideDown {
                setDualRowLabelSecondaryOnly(keyBtn, secondaryOnly: false)
                keyBtn.wasSlideDown = false
                // Commit secondary directly here and suppress keyboardKeyTapped via wasLongPressed.
                let secondaryDef = KeyDef(code: keyDef.longPressCode,
                                          codes: [keyDef.longPressCode],
                                          label: keyDef.label, sublabel: "",
                                          widthPercent: keyDef.widthPercent,
                                          isRepeatable: false, isModifier: false, isSticky: false,
                                          longPressCode: 0)
                delegate?.keyboardView(self, didPress: secondaryDef)
                keyBtn.wasLongPressed = true
            }
        default:
            break
        }
    }

    /// Morphs a dual-row key's label to show only the secondary glyph (or restores original).
    private func setDualRowLabelSecondaryOnly(_ keyBtn: KeyButton, secondaryOnly: Bool) {
        // Find the UIStackView added by makeDualLabelView or makeDualSlidingLabelView.
        guard let stack = keyBtn.subviews.first(where: { $0 is UIStackView }) as? UIStackView,
              stack.arrangedSubviews.count == 2,
              let primaryLbl = stack.arrangedSubviews[0] as? UILabel,
              let secondaryLbl = stack.arrangedSubviews[1] as? UILabel
        else { return }
        if secondaryOnly {
            secondaryLbl.isHidden = true
            primaryLbl.font = keySingleLabelFont
        } else {
            secondaryLbl.isHidden = false
            let kd = keyBtn.keyDef
            primaryLbl.font = (kd.longPressCode != 0 && !kd.sublabel.isEmpty)
                ? keyDualSlidingFont : keyLabelFont
        }
    }
}

// MARK: - SpaceKeyButton
// Handles tap / slide / long-press internally using raw touch tracking.
// This avoids the UISwipeGestureRecognizer + UIButton.touchDown conflict where a space
// character fires on touchDown before UIKit has a chance to recognise the swipe direction.
// Horizontal sliding moves the text cursor one step per spaceCaretStepPx pixels; the initial
// dead zone is spaceSwipeThreshold so accidental micro-drags don't move the cursor.
private final class SpaceKeyButton: KeyButton {
    var onTap:       (() -> Void)?
    var onLongPress: (() -> Void)?
    /// Called with a signed step count each time the finger crosses a caret step boundary.
    var onCaretMove: ((Int) -> Void)?
    /// Normal (unpressed) background color — set from the active palette when the button is created.
    var restoreColor: UIColor = .white

    private var touchBeganPoint: CGPoint = .zero
    private var longPressTimer:  Timer?
    private var caretFired = false   // true once cursor movement has started
    private var tapSuppressed = false // true once any action (long-press or caret) fired
    private var lastCaretStep = 0    // last discrete step emitted

    private static let stepPx:           CGFloat      = LayoutMetrics.Gesture.spaceCaretStepPx
    private static let deadZone:         CGFloat      = LayoutMetrics.Gesture.spaceSwipeThreshold
    private static let longPressDuration: TimeInterval = LayoutMetrics.Gesture.spaceLongPressDuration

    // Override all four touch methods WITHOUT calling super so that UIKit never sends
    // the .touchDown / .touchUpInside control events → keyDown/keyUp never fire for space.
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        touchBeganPoint = touch.location(in: self)
        caretFired    = false
        tapSuppressed = false
        lastCaretStep = 0
        backgroundColor = UIColor.systemGray5
        longPressTimer?.invalidate()
        longPressTimer = Timer.scheduledTimer(
            withTimeInterval: SpaceKeyButton.longPressDuration, repeats: false
        ) { [weak self] _ in
            guard let self, !self.tapSuppressed else { return }
            self.tapSuppressed = true
            self.resetBg()
            self.onLongPress?()
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first, (!tapSuppressed || caretFired) else { return }
        let dx = touch.location(in: self).x - touchBeganPoint.x
        guard abs(dx) >= SpaceKeyButton.deadZone else { return }

        // First crossing: cancel long-press and start caret tracking
        if !caretFired {
            caretFired = true
            longPressTimer?.invalidate(); longPressTimer = nil
            resetBg()
        }
        // Emit delta steps since last move event, with acceleration for longer slides
        let sign = dx < 0 ? -1 : 1
        let step = sign * SpaceKeyButton.stepsForDisplacement(abs(dx))
        let delta = step - lastCaretStep
        if delta != 0 {
            lastCaretStep = step
            tapSuppressed = true
            onCaretMove?(delta)
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        longPressTimer?.invalidate(); longPressTimer = nil
        resetBg()
        if !tapSuppressed { onTap?() }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        longPressTimer?.invalidate(); longPressTimer = nil
        resetBg()
    }

    /// Maps absolute horizontal displacement to a total signed step count.
    /// Three tiers give progressively faster caret movement for longer slides.
    private static func stepsForDisplacement(_ absDx: CGFloat) -> Int {
        let travel = absDx - deadZone
        guard travel > 0 else { return 0 }
        // Tier boundaries (pt beyond dead zone) and step sizes per tier
        let t1: CGFloat = 60            // slow zone: 7pt / step
        let t2: CGFloat = 140           // medium zone: 3.5pt / step → fast zone: 1.75pt / step
        let steps: CGFloat
        if travel <= t1 {
            steps = travel / stepPx
        } else if travel <= t2 {
            steps = t1 / stepPx + (travel - t1) / (stepPx / 2)
        } else {
            steps = t1 / stepPx + (t2 - t1) / (stepPx / 2) + (travel - t2) / (stepPx / 4)
        }
        return Int(steps)
    }

    private func resetBg() {
        backgroundColor = restoreColor
    }
}

// MARK: - KeyButton: stores its KeyDef
private class KeyButton: UIButton {
    let keyDef: KeyDef
    /// Set to true when a UILongPressGestureRecognizer fires on this button.
    /// Used to suppress the subsequent touchUpInside (e.g. done key dismissing keyboard after long press).
    var wasLongPressed = false
    /// Set to true on iPad when the user slides a dual-row top key downward past the threshold.
    var wasSlideDown = false
    init(keyDef: KeyDef) {
        self.keyDef = keyDef
        super.init(frame: .zero)
    }
    required init?(coder: NSCoder) { fatalError("not used") }
}
