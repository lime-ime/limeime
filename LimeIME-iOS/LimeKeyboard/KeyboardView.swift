import UIKit
import AudioToolbox
import AVFoundation

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
    /// feat#124: a key with a generic `longPressCode` (e.g. English 123 → phone_simple) was long-pressed.
    func keyboardView(_ view: KeyboardView, didLongPressKey keyDef: KeyDef)
    /// Called when a key with a non-empty `popupKeyboard` is long-pressed.
    /// `sourceRect` is the key's frame in the KeyboardView's coordinate space.
    func keyboardView(_ view: KeyboardView, didLongPressPopupKey keyDef: KeyDef, sourceRect: CGRect)
    /// Called when a single-key popup is released: dismiss the popup, and if `commit` is true, type
    /// the lone alternate (the key the panel would have fired on tap).
    func keyboardView(_ view: KeyboardView, didReleasePopupKey keyDef: KeyDef, commit: Bool)
    /// Called on touchDown for non-modifier keys — host should show a key-preview popup.
    /// `keyRect` is the key's frame in the KeyboardView's coordinate space.
    func keyboardView(_ view: KeyboardView, showPreviewFor keyDef: KeyDef, keyRect: CGRect)
    /// Called on touchUp/cancel — host should dismiss the key preview.
    func keyboardViewDismissPreview(_ view: KeyboardView)
    /// Called continuously while the user slides horizontally on the space bar.
    /// `steps` is the signed number of caret positions to move (negative = left).
    func keyboardView(_ view: KeyboardView, didMoveCaretBy steps: Int)
    func keyboardViewHasOpenPopup(_ view: KeyboardView) -> Bool
    /// True when the open mini-popup has exactly one key (e.g. the "123" symbol-mode switch,
    /// whose popup is a single-key layout with empty `popupCharacters`). Such popups fire
    /// directly on release-near-press instead of requiring a slide/flint onto the lone key.
    func keyboardViewCurrentPopupIsSingleKey(_ view: KeyboardView) -> Bool
    func keyboardView(_ view: KeyboardView, popupKeyAtKeyboardPoint point: CGPoint) -> KeyDef?
    func keyboardView(_ view: KeyboardView, highlightPopupKey keyDef: KeyDef?)
    func keyboardView(_ view: KeyboardView, didSelectPopupKey keyDef: KeyDef)
    func keyboardViewDidCancelPopupSlide(_ view: KeyboardView)
    func keyboardViewDidSwipeLeft(_ view: KeyboardView)
    func keyboardViewDidSwipeRight(_ view: KeyboardView)
}

extension KeyboardViewDelegate {
    /// Default: without a host answer, only `popupCharacters`-based single keys are treated as
    /// single-key. Layout-based single-key popups (e.g. the 123 symbol-mode switch) need the host
    /// (which owns `currentPopupView`) to return true.
    func keyboardViewCurrentPopupIsSingleKey(_ view: KeyboardView) -> Bool { false }
}

final class KeyboardView: UIView, UIInputViewAudioFeedback {
    /// Keeps the visible input view eligible for UIKit input-click feedback.
    var enableInputClicksWhenVisible: Bool { true }
    private static let keyClickSystemSoundID: SystemSoundID = 1104
    private static let keyClickWavData = makeKeyClickWavData()

    static func shouldUseDualRowGesture(isPad: Bool, layoutId: String, keyDef: KeyDef) -> Bool {
        KeyboardGesturePolicy.shouldUseDualRowGesture(isPad: isPad, layoutId: layoutId, keyDef: keyDef)
    }

    static func shouldUseLimeOptionsMenuGesture(keyDef: KeyDef,
                                                 legacyGlobeMode: Bool = false) -> Bool {
        KeyboardGesturePolicy.shouldUseLimeOptionsMenuGesture(
            keyDef: keyDef, legacyGlobeMode: legacyGlobeMode)
    }

    static func canClassifySwipe(behaviorIsPlain: Bool, keyDef: KeyDef) -> Bool {
        behaviorIsPlain && !keyDef.isRepeatable && !keyDef.isModifier
    }

    /// feat#124: the English "123" key long-presses to phone_simple and shows a "…" hint.
    /// Scoped precisely to the phone_simple code (-106) so it never touches the globe/done key
    /// (longPressCode -100) or iPad dual-row keys (which carry their secondary glyph as
    /// longPressCode) — those keep their own gestures and hint/secondary-glyph rendering.
    /// (isPad/layoutId/legacyGlobeMode kept for call-site symmetry with the other gesture policies.)
    static func shouldUseGenericLongPress(keyDef: KeyDef, isPad: Bool,
                                          layoutId: String, legacyGlobeMode: Bool) -> Bool {
        keyDef.longPressCode == LimeKeyCode.switchToPhoneSimple.rawValue
    }

    weak var delegate: KeyboardViewDelegate?

    func popupKeyboardPoint(fromLayerPoint point: CGPoint, in layer: UIView) -> CGPoint {
        layer.convert(point, to: self)
    }

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
    private var touchTrackers: [ObjectIdentifier: TouchTracker] = [:]
    private var plainTouchTargets: [ObjectIdentifier: PlainKeyTouchTarget] = [:]
    private var touchLayerIDs: [ObjectIdentifier: ObjectIdentifier] = [:]
    private var ownerTouchStates: [ObjectIdentifier: OwnerTouchState] = [:]
    private var ownerLongPressTimers: [ObjectIdentifier: Timer] = [:]
    private var swipeSamples: [ObjectIdentifier: [TouchSample]] = [:]
    // ponytail: key frames are static during a touch; cache per row layer and drop on relayout/no live touches.
    private var plainTouchContexts: [ObjectIdentifier: PlainKeyTouchContext] = [:]
    private static let flintHysteresisKeyWidthFraction: CGFloat = 0.25
    private static let popupSlideAllowance: CGFloat = 10
    private static let swipeVelocityThreshold: CGFloat = 500
    private static let swipeSampleWindow: TimeInterval = 0.2
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
    var keypressSoundVolume: String = "-1"
    private var keyClickPlayer: AVAudioPlayer?
    var vibrateLevel: Int = 40 {
        didSet {
            guard oldValue != vibrateLevel else { return }
            rebuildHapticGenerator()
        }
    }

    private func playKeyClickSound() {
        if let volume = Self.customKeyClickVolume(from: keypressSoundVolume) {
            playCustomKeyClick(volume: volume)
            return
        }
        AudioServicesPlaySystemSound(Self.keyClickSystemSoundID)
    }

    static func customKeyClickVolume(from rawValue: String) -> Float? {
        guard let volume = Float(rawValue), volume >= 0 else { return nil }
        return min(volume, 1)
    }

    static func makeKeyClickWavData() -> Data {
        let sampleRate: UInt32 = 44_100
        let channelCount: UInt16 = 1
        let bitsPerSample: UInt16 = 16
        let sampleCount = Int(Double(sampleRate) * 0.014)

        var samples = Data()
        samples.reserveCapacity(sampleCount * 2)
        for i in 0..<sampleCount {
            let t = Double(i) / Double(sampleRate)
            let attack = min(1, Double(i) / 6)
            let decay = exp(-Double(i) / 120)
            let tone = sin(2 * Double.pi * 3_600 * t) * 0.85
                + sin(2 * Double.pi * 7_200 * t) * 0.15
            let clipped = max(-1, min(1, tone * attack * decay))
            appendLittleEndian(Int16(clipped * Double(Int16.max)), to: &samples)
        }

        let byteRate = sampleRate * UInt32(channelCount) * UInt32(bitsPerSample / 8)
        let blockAlign = channelCount * (bitsPerSample / 8)
        var data = Data()
        data.reserveCapacity(44 + samples.count)
        data.append(Data("RIFF".utf8))
        appendLittleEndian(UInt32(36 + samples.count), to: &data)
        data.append(Data("WAVE".utf8))
        data.append(Data("fmt ".utf8))
        appendLittleEndian(UInt32(16), to: &data)
        appendLittleEndian(UInt16(1), to: &data)
        appendLittleEndian(channelCount, to: &data)
        appendLittleEndian(sampleRate, to: &data)
        appendLittleEndian(byteRate, to: &data)
        appendLittleEndian(blockAlign, to: &data)
        appendLittleEndian(bitsPerSample, to: &data)
        data.append(Data("data".utf8))
        appendLittleEndian(UInt32(samples.count), to: &data)
        data.append(samples)
        return data
    }

    private func playCustomKeyClick(volume: Float) {
        guard let player = keyClickPlayer ?? makeCustomKeyClickPlayer() else { return }
        player.volume = volume
        player.currentTime = 0
        player.play()
    }

    private func makeCustomKeyClickPlayer() -> AVAudioPlayer? {
        do {
            let player = try AVAudioPlayer(data: Self.keyClickWavData)
            player.prepareToPlay()
            keyClickPlayer = player
            return player
        } catch {
            return nil
        }
    }

    private static func appendLittleEndian<T: FixedWidthInteger>(_ value: T, to data: inout Data) {
        var littleEndian = value.littleEndian
        withUnsafeBytes(of: &littleEndian) { data.append(contentsOf: $0) }
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
    fileprivate func fireHaptic(force: Bool = false) {
        guard feedbackVibration else { return }
        let now = CACurrentMediaTime()
        // `force` bypasses the 40 Hz throttle for distinct one-off events (e.g. a popup
        // opening mid-flint, whose tick would otherwise be eaten by the just-fired key haptic).
        if !force, now - lastHapticAt < minHapticInterval { return }
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
        LayoutMetrics.KeyboardRow.rowHeight(
            isPadHardware: isPadHardware,
            isPad: isPad,
            isLandscape: isLandscape
        ) * keySizeScale
    }
    private var bottomRowHeight: CGFloat {
        LayoutMetrics.KeyboardRow.bottomRowHeight(
            isPadHardware: isPadHardware,
            isPad: isPad,
            isLandscape: isLandscape
        ) * keySizeScale
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

    override func layoutSubviews() {
        super.layoutSubviews()
        invalidatePlainTouchContexts()
    }

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
        refreshAccessibilityElements(containing: globeButton)
    }

    private func configureGlobeButtonForSystemPicker() {
        guard let ivc = inputModeViewController else { return }

        if let btn = globeButton {
            btn.isUserInteractionEnabled = true
            btn.removeTarget(self, action: #selector(keyboardKeyTapped(_:)), for: .touchUpInside)
            btn.removeTarget(nil, action: #selector(UIInputViewController.handleInputModeList(from:with:)),
                             for: .allTouchEvents)
            btn.gestureRecognizers?
                .filter { $0 is UILongPressGestureRecognizer }
                .forEach { btn.removeGestureRecognizer($0) }
            btn.addTarget(ivc, action: #selector(UIInputViewController.handleInputModeList(from:with:)),
                          for: .allTouchEvents)
        }

        if let btn = keyboardDoneButton as? KeyButton,
           KeyboardGesturePolicy.shouldWireSystemPickerOnKeyboardKey(
            keyDef: btn.keyDef,
            legacyGlobeMode: legacyGlobeMode,
            hasInputModeViewController: true) {
            btn.isUserInteractionEnabled = true
            btn.removeTarget(self, action: #selector(keyboardKeyTapped(_:)), for: .touchUpInside)
            btn.removeTarget(nil, action: #selector(UIInputViewController.handleInputModeList(from:with:)),
                             for: .allTouchEvents)
            btn.addTarget(ivc, action: #selector(UIInputViewController.handleInputModeList(from:with:)),
                          for: .allTouchEvents)
        }

        invalidatePlainTouchContexts()
    }

    // MARK: - Build
    private func buildKeys() {
        cancelAllActiveTouches()
        invalidatePlainTouchContexts()
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

            let contentView = KeyTouchLayer(owner: self)
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

        let contentView = KeyTouchLayer(owner: self)
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

        let btn = KeyButton(keyDef: keyDef)
        btn.accessibilityOwner = self

        let isKeyboardOptionsKey = Self.shouldUseLimeOptionsMenuGesture(
            keyDef: keyDef, legacyGlobeMode: legacyGlobeMode)
        let isSystemGlobe = keyDef.code == LimeKeyCode.globe.rawValue
            && inputModeViewController != nil
        let isDualRowIPadKey = Self.shouldUseDualRowGesture(isPad: isPad,
                                                             layoutId: layout.id,
                                                             keyDef: keyDef)
        let usesGenericLongPress = Self.shouldUseGenericLongPress(keyDef: keyDef,
                                                                  isPad: isPad,
                                                                  layoutId: layout.id,
                                                                  legacyGlobeMode: legacyGlobeMode)
        let legacyOwnedByIVC = keyDef.code == LimeKeyCode.done.rawValue
            && legacyGlobeMode
            && inputModeViewController != nil
        let routeBasicTapThroughOwner = shouldRouteBasicTapThroughOwner(
            keyDef: keyDef,
            isKeyboardOptionsKey: isKeyboardOptionsKey,
            isDualRowIPadKey: isDualRowIPadKey,
            usesGenericLongPress: usesGenericLongPress,
            legacyOwnedByIVC: legacyOwnedByIVC)

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

        // Edge-touch fix (SO 61227987 / blog.kulman.sk): in the screen-edge strip iOS delays the
        // button's touchDown on a held press, so keyDown's press feedback (preview + haptic) is eaten
        // there. A gesture recognizer still gets .began with NO edge delay, so we drive the press
        // feedback from this 0-duration long-press instead of keyDown. cancelsTouchesInView=false and
        // simultaneous recognition so it never interferes with the button's tracking, the popup
        // long-press, or any other key gesture.
        if !routeBasicTapThroughOwner {
            let pressFeedback = UILongPressGestureRecognizer(target: self, action: #selector(pressFeedbackGesture(_:)))
            pressFeedback.minimumPressDuration = 0
            pressFeedback.cancelsTouchesInView = false
            pressFeedback.delegate = self
            btn.addGestureRecognizer(pressFeedback)
        }

        applyButtonStyle(btn, keyDef: keyDef, rowHeight: rowHeight, totalPercent: totalPercent)

        btn.isUserInteractionEnabled = !routeBasicTapThroughOwner
        if !routeBasicTapThroughOwner {
            btn.addTarget(self, action: #selector(keyDown(_:event:)), for: .touchDown)
            btn.addTarget(self, action: #selector(keyUp(_:)), for: [.touchUpInside, .touchUpOutside])
            btn.addTarget(self, action: #selector(keyCancel(_:)), for: .touchCancel)
        }
        // Done, globe, and popup keys fire didPress on touchUpInside (deferred so long-press can intercept)
        // In legacy iPhone globe mode the `-3` key is owned by iOS' input-mode
        // picker — we must not also fire our own touchUpInside dismiss/menu.
        if !routeBasicTapThroughOwner && !legacyOwnedByIVC && (
            keyDef.code == LimeKeyCode.done.rawValue
                || isKeyboardOptionsKey
                || (keyDef.code == LimeKeyCode.globe.rawValue && !isSystemGlobe)
                || !keyDef.popupKeyboard.isEmpty || isDualRowIPadKey
                || usesGenericLongPress) {
            btn.addTarget(self, action: #selector(keyboardKeyTapped(_:)), for: .touchUpInside)
        }
        return btn
    }

    private func shouldRouteBasicTapThroughOwner(keyDef: KeyDef,
                                                 isKeyboardOptionsKey: Bool,
                                                 isDualRowIPadKey: Bool,
                                                 usesGenericLongPress: Bool,
                                                 legacyOwnedByIVC: Bool) -> Bool {
        guard !legacyOwnedByIVC,
              !(keyDef.code == LimeKeyCode.globe.rawValue && inputModeViewController != nil) else {
            return false
        }
        return true
    }

    // Drives the press feedback (haptic / sound / key preview) off a 0-duration long-press recognizer
    // so it still fires in the screen-edge strip, where iOS eats the button's touchDown on a held press.
    @objc private func pressFeedbackGesture(_ gr: UILongPressGestureRecognizer) {
        guard let keyBtn = gr.view as? KeyButton else { return }
        let keyDef = keyBtn.keyDef
        switch gr.state {
        case .began:
            fireHaptic()
            if feedbackSound { playKeyClickSound() }
            // Same gating as the old keyDown preview: phone only, non-modifier, non-space, no icon.
            if keyDef.icon.isEmpty && !keyDef.isModifier
                && keyDef.code != LimeKeyCode.space.rawValue
                && !isPad {
                let keyRect = keyBtn.convert(keyBtn.bounds, to: self)
                delegate?.keyboardView(self, showPreviewFor: keyDef, keyRect: keyRect)
            }
        case .ended, .cancelled, .failed:
            delegate?.keyboardViewDismissPreview(self)
        default:
            break
        }
    }

    /// Apply background color, corner radius and shadow to any key button.
    private func applyButtonStyle(_ btn: UIButton, keyDef: KeyDef,
                                  rowHeight: CGFloat, totalPercent: CGFloat) {
        btn.backgroundColor = restoredKeyBackgroundColor(for: keyDef)
        btn.layer.cornerRadius = keyCornerRadius
        btn.layer.masksToBounds = false
        btn.layer.shadowColor = LayoutMetrics.Shadow.color
        btn.layer.shadowOffset = CGSize(width: 0, height: LayoutMetrics.Key.shadowOffsetY)
        btn.layer.shadowOpacity = keyShadowOpacity
        btn.layer.shadowRadius = 0
        styleKeyContent(btn: btn, keyDef: keyDef, rowHeight: rowHeight, totalPercent: totalPercent)
    }

    private func restoredKeyBackgroundColor(for keyDef: KeyDef) -> UIColor {
        // Apple-style accent: when the Enter key represents a non-default
        // primary action (.search / .go / .send / .next / .join / .done /
        // .route / .continue), render and restore the key with the system-blue
        // tint so its white icon/label remains readable after touch release.
        if enterKeyOverride(for: keyDef) != nil {
            return .systemBlue
        }
        return keyDef.isModifier ? modifierKeyColor : normalKeyColor
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
        btn.isAccessibilityElement = true
        btn.accessibilityLabel = accessibilityLabel(for: keyDef,
                                                    renderIcon: renderIcon,
                                                    renderLabel: renderLabel)
        btn.accessibilityTraits = .keyboardKey
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

        // Popup-keyboard / generic-long-press indicator: small "…" pinned to bottom-right corner.
        // feat#124: the English 123 key keeps its "123" label and shows this hint (longPressCode set).
        if !keyDef.popupKeyboard.isEmpty
            || Self.shouldUseGenericLongPress(keyDef: keyDef, isPad: isPad,
                                              layoutId: layout.id, legacyGlobeMode: legacyGlobeMode) {
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

    private func accessibilityLabel(for keyDef: KeyDef,
                                    renderIcon: String,
                                    renderLabel: String) -> String {
        if !keyDef.sublabel.isEmpty {
            return [keyDef.label, keyDef.sublabel]
                .filter { !$0.isEmpty }
                .joined(separator: " ")
        }
        if !renderLabel.isEmpty { return renderLabel }
        switch renderIcon {
        case "delete.left": return "delete"
        case "shift", "shift.fill", "capslock.fill": return "shift"
        case "return": return "return"
        case "globe": return "next keyboard"
        case "keyboard.chevron.compact.down": return "dismiss keyboard"
        case "arrow.left": return "left arrow"
        case "arrow.right": return "right arrow"
        case "arrow.up": return "up arrow"
        case "arrow.down": return "down arrow"
        case "magnifyingglass": return "search"
        default: return renderIcon
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

    fileprivate func refreshAccessibilityElements(for layer: KeyTouchLayer) {
        layer.accessibilityElements = allSubviews(of: layer).compactMap { view in
            guard let button = view as? KeyButton,
                  !button.isHidden,
                  button.bounds.width > 0,
                  button.bounds.height > 0 else {
                return nil
            }
            return button
        }
    }

    private func refreshAccessibilityElements(containing button: UIButton?) {
        guard let layer = button?.superview as? KeyTouchLayer else { return }
        refreshAccessibilityElements(for: layer)
    }

    fileprivate func accessibilityActivateKey(_ button: KeyButton) -> Bool {
        guard button.isDescendant(of: self),
              !button.isHidden,
              let layer = button.superview as? KeyTouchLayer else {
            return false
        }
        let token = NSObject()
        let touchID = ObjectIdentifier(token)
        let point = button.convert(CGPoint(x: button.bounds.midX, y: button.bounds.midY), to: layer)
        let target = PlainKeyTouchTarget(button: button, keyDef: button.keyDef)
        let state = OwnerTouchState(behavior: ownerTouchBehavior(for: button.keyDef),
                                    target: target,
                                    layer: layer,
                                    startPoint: point,
                                    lastPoint: point,
                                    startTime: CACurrentMediaTime())
        ownerTouchStates[touchID] = state
        beginOwnerKeyTouch(touchID: touchID, state: state)
        endOwnerTouch(touchID: touchID, state: ownerTouchStates[touchID] ?? state)
        cleanupOwnerTouch(touchID)
        return true
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

    fileprivate func keyTouchLayer(_ layer: KeyTouchLayer,
                                   touchesBegan touches: Set<UITouch>,
                                   with event: UIEvent?) {
        let layerID = ObjectIdentifier(layer)
        let context = plainTouchContext(in: layer)

        for touch in touches {
            let touchID = ObjectIdentifier(touch)
            let point = touch.location(in: layer)
            touchLayerIDs[touchID] = layerID
            appendSwipeSample(touchID: touchID, point: point)
            let key = context.detector.keyAt(point)
            Prof.event("TouchBegan", "touch=\(touchID) code=\(key?.codes.first ?? 0)")
            touchTrackers[touchID] = TouchTracker(downKey: key)
            guard let key,
                  let target = plainTouchTarget(for: key, in: context) else {
                continue
            }
            plainTouchTargets[touchID] = target
            let behavior = ownerTouchBehavior(for: target.keyDef)
            let state = OwnerTouchState(behavior: behavior,
                                        target: target,
                                        layer: layer,
                                        startPoint: point,
                                        lastPoint: point,
                                        startTime: CACurrentMediaTime())
            ownerTouchStates[touchID] = state
            beginOwnerKeyTouch(touchID: touchID, state: state)
            cancelRepeatIfNeeded()
        }
    }

    fileprivate func keyTouchLayer(_ layer: KeyTouchLayer,
                                   touchesMoved touches: Set<UITouch>,
                                   with event: UIEvent?) {
        guard layer.isDescendant(of: self) else { return }
        let context = plainTouchContext(in: layer)

        for touch in touches {
            let touchID = ObjectIdentifier(touch)
            let point = touch.location(in: layer)
            appendSwipeSample(touchID: touchID, point: point)
            if var state = ownerTouchStates[touchID], state.behavior != .plain {
                state.lastPoint = point
                handleMovedOwnerTouch(touchID: touchID, state: &state)
                ownerTouchStates[touchID] = state
                continue
            }

            guard var tracker = touchTrackers[touchID] else { continue }
            if let landing = context.detector.keyAt(point, movingFrom: tracker.currentKey, hysteresis: context.hysteresis),
               landing.hasPopup, landing != tracker.currentKey,
               let popupTarget = plainTouchTarget(for: landing, in: context) {
                beginFlintPopup(touchID: touchID, point: point, layer: layer,
                                previousTarget: plainTouchTargets[touchID], popupTarget: popupTarget)
                continue
            }

            let previousKey = tracker.currentKey
            _ = tracker.move(to: point,
                             detector: context.detector,
                             hysteresis: context.hysteresis)
            guard tracker.currentKey != previousKey else {
                touchTrackers[touchID] = tracker
                continue
            }

            if let oldTarget = plainTouchTargets[touchID] {
                releasePlainKeyTouch(button: oldTarget.button, keyDef: oldTarget.keyDef)
            } else if let previousKey,
                      let oldTarget = plainTouchTarget(for: previousKey, in: context) {
                releasePlainKeyTouch(button: oldTarget.button, keyDef: oldTarget.keyDef)
            }

            guard let newKey = tracker.currentKey,
                  let newTarget = plainTouchTarget(for: newKey, in: context) else {
                touchTrackers[touchID] = tracker
                plainTouchTargets.removeValue(forKey: touchID)
                continue
            }
            plainTouchTargets[touchID] = newTarget
            if tracker.isSliding {
                delegate?.keyboardView(self, didPress: flintUndoKeyDef())
            }
            beginPlainKeyTouch(button: newTarget.button, keyDef: newTarget.keyDef)
            touchTrackers[touchID] = tracker
            cancelRepeatIfNeeded()
        }
    }

    fileprivate func keyTouchLayer(_ layer: KeyTouchLayer,
                                   touchesEnded touches: Set<UITouch>,
                                   with event: UIEvent?) {
        for touch in touches {
            let touchID = ObjectIdentifier(touch)
            let point = touch.location(in: layer)
            appendSwipeSample(touchID: touchID, point: point)
            let tracker = touchTrackers.removeValue(forKey: touchID)
            let target = plainTouchTargets.removeValue(forKey: touchID)
                ?? plainTouchTarget(for: tracker?.currentKey, in: layer)
            if let command = swipeCommand(for: touchID, in: layer),
               command != .none,
               delegate?.keyboardViewHasOpenPopup(self) != true {
                if let target {
                    cancelPlainKeyTouch(button: target.button, keyDef: target.keyDef)
                }
                dispatchSwipeCommand(command)
            } else if var state = ownerTouchStates.removeValue(forKey: touchID) {
                state.lastPoint = point
                if state.behavior == .plain, let target {
                    endPlainKeyTouch(button: target.button, keyDef: target.keyDef)
                } else {
                    endOwnerTouch(touchID: touchID, state: state)
                }
            } else if let target {
                endPlainKeyTouch(button: target.button, keyDef: target.keyDef)
            }
            finishPlainTouch(touchID)
            cleanupOwnerTouch(touchID)
        }
    }

    fileprivate func keyTouchLayer(_ layer: KeyTouchLayer,
                                   touchesCancelled touches: Set<UITouch>,
                                   with event: UIEvent?) {
        for touch in touches {
            let touchID = ObjectIdentifier(touch)
            Prof.event("TouchCancel", "touch=\(touchID)")
            touchTrackers.removeValue(forKey: touchID)
            let state = ownerTouchStates.removeValue(forKey: touchID)
            if let target = plainTouchTargets.removeValue(forKey: touchID) {
                cancelPlainKeyTouch(button: target.button, keyDef: target.keyDef)
            }
            if state?.popupOpen == true {
                delegate?.keyboardViewDidCancelPopupSlide(self)
            }
            finishPlainTouch(touchID)
            delegate?.keyboardView(self, highlightPopupKey: nil)
            cleanupOwnerTouch(touchID)
        }
    }

    private func ownerTouchBehavior(for keyDef: KeyDef) -> OwnerTouchBehavior {
        if keyDef.code == LimeKeyCode.space.rawValue { return .space }
        if !keyDef.popupKeyboard.isEmpty { return .popup }
        if Self.shouldUseDualRowGesture(isPad: isPad, layoutId: layout.id, keyDef: keyDef) {
            return .dualRow
        }
        if shouldDeferOwnerKeyTap(keyDef) { return .deferredLongPress }
        return .plain
    }

    private func shouldDeferOwnerKeyTap(_ keyDef: KeyDef) -> Bool {
        keyDef.code == LimeKeyCode.done.rawValue
            || keyDef.code == LimeKeyCode.globe.rawValue
            || Self.shouldUseLimeOptionsMenuGesture(keyDef: keyDef, legacyGlobeMode: legacyGlobeMode)
            || Self.shouldUseGenericLongPress(keyDef: keyDef, isPad: isPad,
                                              layoutId: layout.id, legacyGlobeMode: legacyGlobeMode)
    }

    private func beginFlintPopup(touchID: ObjectIdentifier, point: CGPoint, layer: KeyTouchLayer,
                                 previousTarget: PlainKeyTouchTarget?, popupTarget: PlainKeyTouchTarget) {
        delegate?.keyboardView(self, didPress: flintUndoKeyDef())
        if let previousTarget {
            releasePlainKeyTouch(button: previousTarget.button, keyDef: previousTarget.keyDef)
        }
        touchTrackers.removeValue(forKey: touchID)
        plainTouchTargets.removeValue(forKey: touchID)
        invalidateOwnerLongPress(touchID)

        var state = OwnerTouchState(behavior: ownerTouchBehavior(for: popupTarget.keyDef),
                                    target: popupTarget,
                                    layer: layer,
                                    startPoint: point,
                                    lastPoint: point,
                                    startTime: CACurrentMediaTime())
        openPopup(touchID: touchID, state: &state)
        ownerTouchStates[touchID] = state
    }

    private func beginOwnerKeyTouch(touchID: ObjectIdentifier, state: OwnerTouchState) {
        if state.behavior == .space {
            state.target.button.wasLongPressed = false
            state.target.button.backgroundColor = pressedKeyColor
        } else {
            beginPlainKeyTouch(button: state.target.button, keyDef: state.target.keyDef)
        }
        scheduleOwnerLongPressIfNeeded(touchID: touchID, state: state)
    }

    private func handleMovedOwnerTouch(touchID: ObjectIdentifier, state: inout OwnerTouchState) {
        switch state.behavior {
        case .popup:
            if state.popupOpen { updatePopupSelection(state: &state) }
        case .dualRow:
            updateDualRowSlide(touchID: touchID, state: &state)
        case .space:
            updateSpaceTouch(touchID: touchID, state: &state)
        case .deferredLongPress, .plain:
            break
        }
    }

    private func endOwnerTouch(touchID: ObjectIdentifier, state: OwnerTouchState) {
        invalidateOwnerLongPress(touchID)
        switch state.behavior {
        case .popup:
            endPopupTouch(state: state)
        case .dualRow:
            endDualRowTouch(state: state)
        case .space:
            endSpaceTouch(state: state)
        case .deferredLongPress:
            if state.longPressFired {
                cancelPlainKeyTouch(button: state.target.button, keyDef: state.target.keyDef)
            } else {
                commitKeyTouchOnEnded(button: state.target.button, keyDef: state.target.keyDef)
            }
        case .plain:
            endPlainKeyTouch(button: state.target.button, keyDef: state.target.keyDef)
        }
    }

    private func scheduleOwnerLongPressIfNeeded(touchID: ObjectIdentifier, state: OwnerTouchState) {
        let duration: TimeInterval?
        switch state.behavior {
        case .popup:
            duration = LayoutMetrics.Gesture.popupKeyboardHoldDuration
        case .dualRow:
            duration = LayoutMetrics.Gesture.dualRowHoldDuration
        case .space:
            duration = LayoutMetrics.Gesture.spaceLongPressDuration
        case .deferredLongPress:
            duration = shouldStartDeferredLongPressTimer(for: state.target.keyDef)
                ? LayoutMetrics.Gesture.specialKeyHoldDuration
                : nil
        case .plain:
            duration = nil
        }
        guard let duration else { return }
        ownerLongPressTimers[touchID] = Timer.scheduledTimer(withTimeInterval: duration,
                                                             repeats: false) { [weak self] _ in
            self?.fireOwnerLongPress(touchID: touchID)
        }
    }

    private func shouldStartDeferredLongPressTimer(for keyDef: KeyDef) -> Bool {
        Self.shouldUseLimeOptionsMenuGesture(keyDef: keyDef, legacyGlobeMode: legacyGlobeMode)
            || Self.shouldUseGenericLongPress(keyDef: keyDef, isPad: isPad,
                                              layoutId: layout.id, legacyGlobeMode: legacyGlobeMode)
    }

    private func fireOwnerLongPress(touchID: ObjectIdentifier) {
        guard var state = ownerTouchStates[touchID] else { return }
        state.longPressFired = true
        state.target.button.wasLongPressed = true

        switch state.behavior {
        case .popup:
            openPopup(touchID: touchID, state: &state)
        case .dualRow:
            guard state.target.keyDef.longPressCode != 0 else { break }
            state.dualPreviewShown = true
            let keyRect = state.target.button.convert(state.target.button.bounds, to: self)
            delegate?.keyboardView(self,
                                   showPreviewFor: secondaryKeyDef(for: state.target.keyDef),
                                   keyRect: keyRect)
        case .space:
            guard shouldFireSpaceLongPress(state: state) else { break }
            state.spaceTapSuppressed = true
            state.target.button.backgroundColor = restoredKeyBackgroundColor(for: state.target.keyDef)
            delegate?.keyboardView(self, didLongPress: state.target.keyDef)
        case .deferredLongPress:
            fireHaptic()
            if Self.shouldUseGenericLongPress(keyDef: state.target.keyDef, isPad: isPad,
                                              layoutId: layout.id, legacyGlobeMode: legacyGlobeMode) {
                delegate?.keyboardView(self, didLongPressKey: state.target.keyDef)
            } else {
                delegate?.keyboardView(self, didLongPress: state.target.keyDef)
            }
        case .plain:
            break
        }

        ownerTouchStates[touchID] = state
    }

    private func openPopup(touchID: ObjectIdentifier, state: inout OwnerTouchState) {
        state.longPressFired = true
        state.target.button.wasLongPressed = true
        state.target.button.backgroundColor = pressedKeyColor
        state.popupOpen = true
        fireHaptic(force: true)   // guaranteed tick even when opened mid-flint
        delegate?.keyboardViewDismissPreview(self)
        let keyRect = state.target.button.convert(state.target.button.bounds, to: self)
        delegate?.keyboardView(self, didLongPressPopupKey: state.target.keyDef, sourceRect: keyRect)
        updatePopupSelection(state: &state)
    }

    private func endPopupTouch(state: OwnerTouchState) {
        guard state.popupOpen else {
            commitKeyTouchOnEnded(button: state.target.button, keyDef: state.target.keyDef)
            return
        }

        defer {
            delegate?.keyboardView(self, highlightPopupKey: nil)
            cancelPlainKeyTouch(button: state.target.button, keyDef: state.target.keyDef)
        }

        let keyboardPoint = popupKeyboardPoint(fromLayerPoint: state.lastPoint, in: state.layer)
        if let selected = delegate?.keyboardView(self, popupKeyAtKeyboardPoint: keyboardPoint) {
            // Usage 1: held + slid onto an alternate → commit it and dismiss.
            delegate?.keyboardView(self, didSelectPopupKey: selected)
        } else if (state.target.keyDef.popupCharacters.count == 1
                   || delegate?.keyboardViewCurrentPopupIsSingleKey(self) == true),
                  squareDistance(state.startPoint, state.lastPoint) <= state.target.button.bounds.height * state.target.button.bounds.height / 4 {
            // Single-key popup (char-based OR a single-key layout like the 123 symbol-mode
            // switch): release without flinting fires the lone key directly.
            delegate?.keyboardView(self, didReleasePopupKey: state.target.keyDef, commit: true)
        }
        // Usage 2 (else): released without landing on an alternate — leave the
        // mini-keyboard on screen (sticky). Its own buttons handle tap-to-select;
        // the tap-outside overlay dismisses it. Do NOT dismiss here.
    }

    private func updatePopupSelection(state: inout OwnerTouchState) {
        let keyboardPoint = popupKeyboardPoint(fromLayerPoint: state.lastPoint, in: state.layer)
        let selected = delegate?.keyboardView(self, popupKeyAtKeyboardPoint: keyboardPoint)
        guard selected != state.popupSelection else { return }
        state.popupSelection = selected
        delegate?.keyboardView(self, highlightPopupKey: selected)
        if selected != nil { fireHaptic() }   // per-alternate tick while sliding (matches main-key flint)
    }

    private func updateDualRowSlide(touchID: ObjectIdentifier, state: inout OwnerTouchState) {
        guard state.target.keyDef.longPressCode != 0,
              state.target.keyDef.popupKeyboard.isEmpty else { return }
        let threshold = LayoutMetrics.Gesture.dualRowSwipeThreshold(landscape: isLandscape)
        let isSlideDown = state.lastPoint.y - state.startPoint.y > threshold
        if isSlideDown && !state.target.button.wasSlideDown {
            invalidateOwnerLongPress(touchID)
            state.target.button.wasSlideDown = true
            setDualRowLabelSecondaryOnly(state.target.button, secondaryOnly: true)
        } else if !isSlideDown && state.target.button.wasSlideDown {
            state.target.button.wasSlideDown = false
            setDualRowLabelSecondaryOnly(state.target.button, secondaryOnly: false)
        }
    }

    private func endDualRowTouch(state: OwnerTouchState) {
        if state.target.button.wasSlideDown || state.dualPreviewShown {
            setDualRowLabelSecondaryOnly(state.target.button, secondaryOnly: false)
            state.target.button.wasSlideDown = false
            delegate?.keyboardView(self, didPress: secondaryKeyDef(for: state.target.keyDef))
            delegate?.keyboardViewDismissPreview(self)
            cancelPlainKeyTouch(button: state.target.button, keyDef: state.target.keyDef)
        } else if state.longPressFired {
            cancelPlainKeyTouch(button: state.target.button, keyDef: state.target.keyDef)
        } else {
            commitKeyTouchOnEnded(button: state.target.button, keyDef: state.target.keyDef)
        }
    }

    private func secondaryKeyDef(for keyDef: KeyDef) -> KeyDef {
        KeyDef(code: keyDef.longPressCode,
               codes: [keyDef.longPressCode],
               label: keyDef.label, sublabel: "",
               widthPercent: keyDef.widthPercent,
               isRepeatable: false, isModifier: false, isSticky: false,
               longPressCode: 0)
    }

    private func updateSpaceTouch(touchID: ObjectIdentifier, state: inout OwnerTouchState) {
        guard !state.spaceTapSuppressed || state.spaceCaretFired else { return }
        let dx = state.lastPoint.x - state.startPoint.x
        guard abs(dx) >= LayoutMetrics.Gesture.spaceSwipeThreshold else { return }

        if !state.spaceCaretFired {
            state.spaceCaretFired = true
            invalidateOwnerLongPress(touchID)
            state.target.button.backgroundColor = restoredKeyBackgroundColor(for: state.target.keyDef)
        }

        let sign = dx < 0 ? -1 : 1
        let step = sign * spaceCaretSteps(for: abs(dx))
        let delta = step - state.lastSpaceCaretStep
        guard delta != 0 else { return }
        state.lastSpaceCaretStep = step
        state.spaceTapSuppressed = true
        delegate?.keyboardView(self, didMoveCaretBy: delta)
    }

    private func endSpaceTouch(state: OwnerTouchState) {
        state.target.button.backgroundColor = restoredKeyBackgroundColor(for: state.target.keyDef)
        if !state.spaceTapSuppressed {
            fireHaptic()
            if feedbackSound { playKeyClickSound() }
            delegate?.keyboardView(self, didPress: state.target.keyDef)
        }
    }

    private func shouldFireSpaceLongPress(state: OwnerTouchState) -> Bool {
        let limit = state.target.button.bounds.height / 5
        return hypot(state.lastPoint.x - state.startPoint.x,
                     state.lastPoint.y - state.startPoint.y) < limit
    }

    private func spaceCaretSteps(for absDx: CGFloat) -> Int {
        let travel = absDx - LayoutMetrics.Gesture.spaceSwipeThreshold
        guard travel > 0 else { return 0 }
        let t1: CGFloat = 60
        let t2: CGFloat = 140
        let stepPx = LayoutMetrics.Gesture.spaceCaretStepPx
        if travel <= t1 {
            return Int(travel / stepPx)
        }
        if travel <= t2 {
            return Int(t1 / stepPx + (travel - t1) / (stepPx / 2))
        }
        return Int(t1 / stepPx + (t2 - t1) / (stepPx / 2) + (travel - t2) / (stepPx / 4))
    }

    private func appendSwipeSample(touchID: ObjectIdentifier, point: CGPoint) {
        let now = CACurrentMediaTime()
        var samples = swipeSamples[touchID] ?? []
        samples.append(TouchSample(point: point, time: now))
        samples = samples.suffix(4).filter { now - $0.time <= Self.swipeSampleWindow }
        swipeSamples[touchID] = samples
    }

    private func swipeCommand(for touchID: ObjectIdentifier, in layer: KeyTouchLayer) -> KeyboardSwipeCommand? {
        guard let state = ownerTouchStates[touchID],
              Self.canClassifySwipe(behaviorIsPlain: state.behavior == .plain,
                                    keyDef: state.target.keyDef),
              !state.longPressFired,
              !state.popupOpen,
              let samples = swipeSamples[touchID],
              let newest = samples.last else {
            return nil
        }
        let oldest = samples.first ?? newest
        let totalDuration = max(newest.time - state.startTime, 0.001)
        let endingDuration = max(newest.time - oldest.time, 0.001)
        let totalVelocity = CGVector(dx: (newest.point.x - state.startPoint.x) / totalDuration,
                                     dy: (newest.point.y - state.startPoint.y) / totalDuration)
        let endingVelocity = CGVector(dx: (newest.point.x - oldest.point.x) / endingDuration,
                                      dy: (newest.point.y - oldest.point.y) / endingDuration)
        return KeyboardSwipeClassifier.classify(delta: CGSize(width: newest.point.x - state.startPoint.x,
                                                              height: newest.point.y - state.startPoint.y),
                                                velocity: totalVelocity,
                                                endingVelocity: endingVelocity,
                                                bounds: layer.bounds.size,
                                                velocityThreshold: Self.swipeVelocityThreshold)
    }

    private func dispatchSwipeCommand(_ command: KeyboardSwipeCommand) {
        switch command {
        case .left:
            delegate?.keyboardViewDidSwipeLeft(self)
        case .right:
            delegate?.keyboardViewDidSwipeRight(self)
        case .none:
            break
        }
    }

    private func cancelRepeatIfNeeded() {
        if TouchTracker.shouldCancelRepeat(trackers: touchTrackers.values) {
            stopRepeating()
        }
    }

    private func cancelAllActiveTouches() {
        ownerLongPressTimers.values.forEach { $0.invalidate() }
        ownerLongPressTimers.removeAll()
        stopRepeating()

        let hadOpenPopup = ownerTouchStates.values.contains { $0.popupOpen }
            || delegate?.keyboardViewHasOpenPopup(self) == true

        var targets: [ObjectIdentifier: PlainKeyTouchTarget] = [:]
        for target in plainTouchTargets.values {
            targets[ObjectIdentifier(target.button)] = target
        }
        for state in ownerTouchStates.values {
            targets[ObjectIdentifier(state.target.button)] = state.target
        }
        for target in targets.values {
            cancelPlainKeyTouch(button: target.button, keyDef: target.keyDef)
        }
        shiftHoldTrackingActive = false

        if hadOpenPopup {
            delegate?.keyboardViewDidCancelPopupSlide(self)
        }
        delegate?.keyboardView(self, highlightPopupKey: nil)

        touchTrackers.removeAll()
        plainTouchTargets.removeAll()
        touchLayerIDs.removeAll()
        ownerTouchStates.removeAll()
        swipeSamples.removeAll()
    }

    private func invalidateOwnerLongPress(_ touchID: ObjectIdentifier) {
        ownerLongPressTimers[touchID]?.invalidate()
        ownerLongPressTimers.removeValue(forKey: touchID)
    }

    private func cleanupOwnerTouch(_ touchID: ObjectIdentifier) {
        invalidateOwnerLongPress(touchID)
        ownerTouchStates.removeValue(forKey: touchID)
        swipeSamples.removeValue(forKey: touchID)
    }

    private func squareDistance(_ a: CGPoint, _ b: CGPoint) -> CGFloat {
        let dx = a.x - b.x
        let dy = a.y - b.y
        return dx * dx + dy * dy
    }

    private func beginPlainKeyTouch(button: KeyButton, keyDef: KeyDef) {
        Prof.event("PlainPress", "code=\(keyDef.code)")
        button.wasLongPressed = false
        button.backgroundColor = pressedKeyColor
        updateShiftHoldTrackingFromTrackers(for: keyDef)
        fireHaptic()
        if feedbackSound { playKeyClickSound() }
        if keyDef.icon.isEmpty && !keyDef.isModifier
            && keyDef.code != LimeKeyCode.space.rawValue
            && !isPad {
            let keyRect = button.convert(button.bounds, to: self)
            delegate?.keyboardView(self, showPreviewFor: keyDef, keyRect: keyRect)
        }
        if shouldCommitPlainKeyOnBegan(keyDef) {
            Prof.event("PlainCommit", "code=\(keyDef.code)")
            delegate?.keyboardView(self, didPress: keyDef)
        }

        if keyDef.isRepeatable {
            repeatKeyDef = keyDef
            repeatTimer = Timer.scheduledTimer(withTimeInterval: LayoutMetrics.Gesture.repeatStartDelay,
                                               repeats: false) { [weak self] _ in
                self?.startRepeating()
            }
        }
    }

    private func endPlainKeyTouch(button: KeyButton, keyDef: KeyDef) {
        releasePlainKeyTouch(button: button, keyDef: keyDef)
    }

    private func commitKeyTouchOnEnded(button: KeyButton, keyDef: KeyDef) {
        delegate?.keyboardView(self, didPress: keyDef)
        releasePlainKeyTouch(button: button, keyDef: keyDef)
    }

    private func releasePlainKeyTouch(button: KeyButton, keyDef: KeyDef) {
        if keyDef.code == LimeKeyCode.shift.rawValue {
            shiftHoldTrackingActive = false
        }
        button.backgroundColor = restoredKeyBackgroundColor(for: keyDef)
        delegate?.keyboardViewDismissPreview(self)
        delegate?.keyboardView(self, didRelease: keyDef)
        stopRepeating()
        button.wasSlideDown = false
    }

    private func shouldCommitPlainKeyOnBegan(_ keyDef: KeyDef) -> Bool {
        ownerTouchBehavior(for: keyDef) == .plain
    }

    private func flintUndoKeyDef() -> KeyDef {
        KeyDef(code: LimeKeyCode.delete.rawValue,
               codes: [LimeKeyCode.delete.rawValue])
    }

    private func cancelPlainKeyTouch(button: KeyButton, keyDef: KeyDef) {
        if keyDef.code == LimeKeyCode.shift.rawValue {
            shiftHoldTrackingActive = false
        }
        button.backgroundColor = restoredKeyBackgroundColor(for: keyDef)
        delegate?.keyboardViewDismissPreview(self)
        stopRepeating()
        button.wasSlideDown = false
    }

    private func updateShiftHoldTrackingFromTrackers(for keyDef: KeyDef) {
        if keyDef.code == LimeKeyCode.shift.rawValue {
            shiftHoldTrackingActive = true
            return
        }

        guard shiftHoldTrackingActive else { return }
        let active = ShiftHoldTouchPolicy.isShiftStillHeld(activeTouchCount: touchTrackers.count,
                                                           wasShiftAlreadyHeld: shiftHoldTrackingActive)
        if !active {
            shiftHoldTrackingActive = false
        }
        delegate?.keyboardView(self, didUpdateShiftHoldActive: active)
    }

    private func plainTouchContext(in layer: KeyTouchLayer) -> PlainKeyTouchContext {
        let layerID = ObjectIdentifier(layer)
        if let cached = plainTouchContexts[layerID] {
            return cached
        }

        let entries = plainKeyEntries(in: layer)
        let detector = KeyDetector(keys: entries.map(\.model))
        let keyWidth = entries.first(where: { $0.model.frame.width > 0 })?.model.frame.width ?? 44
        let context = PlainKeyTouchContext(entries: entries,
                                           detector: detector,
                                           hysteresis: keyWidth * Self.flintHysteresisKeyWidthFraction)
        plainTouchContexts[layerID] = context
        return context
    }

    private func plainTouchTarget(for key: KeyModel?, in layer: KeyTouchLayer) -> PlainKeyTouchTarget? {
        guard layer.isDescendant(of: self),
              let key else {
            return nil
        }
        return plainTouchTarget(for: key, in: plainTouchContext(in: layer))
    }

    private func plainTouchTarget(for key: KeyModel, in context: PlainKeyTouchContext) -> PlainKeyTouchTarget? {
        guard let entry = context.entries.first(where: { $0.model == key }) else { return nil }
        return PlainKeyTouchTarget(button: entry.button, keyDef: entry.keyDef)
    }

    private func finishPlainTouch(_ touchID: ObjectIdentifier) {
        // ponytail: keep the per-layer KeyDetector context cached across taps (built
        // once, reused). Dropping it whenever a layer's touches drained rebuilt the
        // detector — allSubviews walk + per-key frame conversions — on EVERY
        // touchesBegan, adding work to the latency-critical press path and dropping
        // keys under fast typing. It is still invalidated on real layout change via
        // invalidatePlainTouchContexts() in layoutSubviews()/buildKeys().
        touchLayerIDs.removeValue(forKey: touchID)
    }

    private func invalidatePlainTouchContexts() {
        plainTouchContexts.removeAll()
    }

    private func plainKeyEntries(in layer: KeyTouchLayer) -> [PlainKeyEntry] {
        allSubviews(of: layer).compactMap { view in
            guard let button = view as? KeyButton,
                  !button.isUserInteractionEnabled,
                  !button.isHidden else {
                return nil
            }
            let keyDef = button.keyDef
            let frame = button.convert(button.bounds, to: layer)
            guard frame.width > 0, frame.height > 0 else { return nil }
            return PlainKeyEntry(model: keyModel(for: keyDef, frame: frame),
                                 button: button,
                                 keyDef: keyDef)
        }
    }

    private func keyModel(for keyDef: KeyDef, frame: CGRect) -> KeyModel {
        KeyModel(frame: frame,
                 codes: keyDef.codes,
                 primaryLabel: keyDef.label,
                 secondaryLabel: keyDef.sublabel,
                 isRepeatable: keyDef.isRepeatable,
                 isModifier: keyDef.isModifier,
                 hasPopup: !keyDef.popupKeyboard.isEmpty,
                 isDualRow: Self.shouldUseDualRowGesture(isPad: isPad,
                                                          layoutId: layout.id,
                                                          keyDef: keyDef),
                 isSpace: keyDef.code == LimeKeyCode.space.rawValue)
    }

    // MARK: - Touch handling
    @objc private func keyDown(_ btn: UIButton, event: UIEvent) {
        guard let keyBtn = btn as? KeyButton else { return }
        keyBtn.wasLongPressed = false   // reset each new touch cycle
        btn.backgroundColor = pressedKeyColor

        let keyDef = keyBtn.keyDef
        updateShiftHoldTracking(for: keyDef, event: event)

        // Press feedback (haptic / sound / key preview) is driven by pressFeedbackGesture, NOT here —
        // a gesture recognizer still fires in the screen-edge strip where iOS eats this touchDown on a
        // held press, so the edge keys get the same feedback as inner keys.

        // Button-owned legacy/system keys defer didPress to touchUpInside so their
        // native picker/long-press arbitration can complete before the primary action.
        let isDualRowIPad = Self.shouldUseDualRowGesture(isPad: isPad,
                                                          layoutId: layout.id,
                                                          keyDef: keyDef)
        let deferToTouchUp = keyDef.code == LimeKeyCode.done.rawValue
                          || keyDef.code == LimeKeyCode.globe.rawValue
                          || !keyDef.popupKeyboard.isEmpty
                          || isDualRowIPad
                          || Self.shouldUseGenericLongPress(keyDef: keyDef, isPad: isPad,
                                                            layoutId: layout.id, legacyGlobeMode: legacyGlobeMode)
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

    /// Fires `didPress` for button-owned legacy/system keys on touchUpInside.
    @objc private func keyboardKeyTapped(_ btn: UIButton) {
        guard let keyBtn = btn as? KeyButton, !keyBtn.wasLongPressed else { return }
        delegate?.keyboardView(self, didPress: keyBtn.keyDef)
    }

    @objc private func keyUp(_ btn: UIButton) {
        guard let keyBtn = btn as? KeyButton else { return }
        if keyBtn.keyDef.code == LimeKeyCode.shift.rawValue {
            shiftHoldTrackingActive = false
        }
        let keyDef = keyBtn.keyDef
        btn.backgroundColor = restoredKeyBackgroundColor(for: keyDef)
        delegate?.keyboardViewDismissPreview(self)
        delegate?.keyboardView(self, didRelease: keyDef)
        stopRepeating()
        keyBtn.wasSlideDown = false
    }

    @objc private func keyCancel(_ btn: UIButton) {
        guard let keyBtn = btn as? KeyButton else { return }
        btn.backgroundColor = restoredKeyBackgroundColor(for: keyBtn.keyDef)
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

private struct PlainKeyEntry {
    let model: KeyModel
    let button: KeyButton
    let keyDef: KeyDef
}

private struct PlainKeyTouchContext {
    let entries: [PlainKeyEntry]
    let detector: KeyDetector
    let hysteresis: CGFloat
}

private struct PlainKeyTouchTarget {
    let button: KeyButton
    let keyDef: KeyDef
}

private enum OwnerTouchBehavior {
    case plain
    case popup
    case dualRow
    case space
    case deferredLongPress
}

private struct OwnerTouchState {
    let behavior: OwnerTouchBehavior
    let target: PlainKeyTouchTarget
    let layer: KeyTouchLayer
    let startPoint: CGPoint
    var lastPoint: CGPoint
    let startTime: TimeInterval
    var longPressFired = false
    var popupOpen = false
    var popupSelection: KeyDef?
    var dualPreviewShown = false
    var spaceCaretFired = false
    var spaceTapSuppressed = false
    var lastSpaceCaretStep = 0
}

private struct TouchSample {
    let point: CGPoint
    let time: TimeInterval
}

fileprivate final class KeyTouchLayer: UIView {
    private weak var owner: KeyboardView?

    init(owner: KeyboardView) {
        self.owner = owner
        super.init(frame: .zero)
        isAccessibilityElement = false
        isMultipleTouchEnabled = true
        isOpaque = false
        contentMode = .redraw
    }

    required init?(coder: NSCoder) { fatalError("not used") }

    override func draw(_ rect: CGRect) {}

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        guard !isHidden, alpha > 0.01, isUserInteractionEnabled, bounds.contains(point) else {
            return nil
        }
        // Interactive subviews (system globe / legacy picker key) must handle their own touches.
        if let hit = super.hitTest(point, with: event), hit !== self, hit.isUserInteractionEnabled {
            return hit
        }
        // Everything else (plain render-only keys + transparent gaps) is owned by the layer.
        return self
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        owner?.refreshAccessibilityElements(for: self)
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        owner?.keyTouchLayer(self, touchesBegan: touches, with: event)
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        owner?.keyTouchLayer(self, touchesMoved: touches, with: event)
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        owner?.keyTouchLayer(self, touchesEnded: touches, with: event)
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        owner?.keyTouchLayer(self, touchesCancelled: touches, with: event)
    }
}

// MARK: - KeyButton: stores its KeyDef
private class KeyButton: UIButton {
    let keyDef: KeyDef
    weak var accessibilityOwner: KeyboardView?
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

    override func accessibilityActivate() -> Bool {
        if isUserInteractionEnabled {
            return super.accessibilityActivate()
        }
        return accessibilityOwner?.accessibilityActivateKey(self) ?? false
    }
}

extension KeyboardView: UIGestureRecognizerDelegate {
    // The pressFeedback recognizer is the only one with this delegate set, so returning true here lets
    // it coexist with the button's own touch tracking and every other key gesture (popup long-press,
    // dual-row, generic long-press) without altering their mutual behavior.
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
        true
    }
}
