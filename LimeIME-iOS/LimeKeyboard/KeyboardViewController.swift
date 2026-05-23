import UIKit

// Full keyboard extension entry point.
// Implements IMService behavior per IM_SERVICE.md spec.

final class KeyboardViewController: UIInputViewController {

    // MARK: - Components
    private var candidateBar: CandidateBarView!
    private var keyboardView:  KeyboardView!
    private var emojiPanelView: EmojiPanelView?
    private var emojiPanelSource: EmojiPanelSource = .english

    // MARK: - SearchServer
    private var searchServer: SearchServer?
    private var lastKnownRestoreTimestamp: Double = 0

    // MARK: - Composing State (spec §3)
    private var mComposing:      String = ""  // current composing code buffer
    private var composingLength: Int    = 0   // chars inserted inline (iOS composing sim §12)
    private var mPredictionOn:   Bool   = true
    private var mCompletionOn:   Bool   = false

    // MARK: - Candidate State (spec §3)
    private var selectedCandidate:  Mapping? = nil
    private var committedCandidate: Mapping? = nil
    private var mCandidateList:     [Mapping] = []
    private var hasCandidatesShown: Bool = false
    /// True when the candidate bar is currently showing related phrases (for "More" expansion).
    private var isShowingRelatedPhrases: Bool = false

    // MARK: - Mode State (spec §3)
    private var mEnglishOnly: Bool = false
    private var mCapsLock:    Bool = false
    private var isShiftOn:    Bool = false
    private var isShiftKeyHeld: Bool = false
    private var shiftHoldModifiedCharacter: Bool = false
    private var activeIM:     String = "phonetic"
    /// Cached `imkeys` for the active IM (refreshed on every setTableName).
    /// On iPad layouts, characters whose code is NOT in this string are routed to
    /// the direct-output path so iPad dual-sliding punctuation/full-shape keys do
    /// not corrupt Chinese composition.
    private var currentImKeys: String = ""
    // Sentinel ID "__unset__" ensures initOnStartInput always loads the JSON layout on first call,
    // even when the JSON id matches the hardcoded fallback id ("lime_phonetic").
    private var currentLayout: LimeKeyLayout = LimeKeyLayout(id: "__unset__", rows: [])

    // Track the field hints we last adapted to. `viewWillAppear` runs
    // `initOnStartInput()` only when the keyboard view first appears, so
    // without these we'd miss field changes that happen while the keyboard
    // is already on screen (e.g. tapping from a Chinese-IM text field into
    // an email/number field — the previous Chinese-IM layout would stay
    // active until the keyboard is dismissed and re-popped).
    private var lastSeenKeyboardType:  UIKeyboardType   = .default
    private var lastSeenReturnKeyType: UIReturnKeyType  = .default

    // MARK: - Multi-tap State (T9-style cycling through codes[])
    private var mMultiTapCodes:    [Int]        = []
    private var mMultiTapIndex:    Int          = 0
    private var mLastTapTime:      TimeInterval = 0
    private let multiTapTimeout:   TimeInterval = LayoutMetrics.Gesture.multiTapTimeout

    // MARK: - English Prediction (spec §7 — iOS: UITextChecker replaces custom dict)
    private var tempEnglishWord: String = ""
    private let textChecker = UITextChecker()

    // MARK: - Auto-Commit (spec §3)
    private var autoCommit: Int = 0  // 0 = off; >0 = auto-commit at that composing length

    // MARK: - Settings (spec §15 — read from shared UserDefaults)
    /// Raw `keyboard_theme` value (0–5 = explicit palette; 6 = follow system appearance).
    private var currentKeyboardTheme:    Int  = 6
    private var hanConvertOption:        Int  = 0     // 0=off, 1=T→S, 2=S→T
    private var autoChineseSymbol:       Bool = true  // show Chinese punctuation after commit
    private var sortSuggestions:         Bool = false
    private var smartChineseInput:       Bool = true  // runtime phrase suggestion
    private var learnPhrase:             Bool = true  // enable LD phrase learning
    private var englishPredictionOn:     Bool = true  // enable English prediction
    private var hasVibration:            Bool = false
    private var vibrateLevel:            Int  = 40   // mapped to UIImpactFeedbackGenerator style
    private var hasSound:                Bool = false
    private var mPersistentLanguageMode: Bool = false // persist English/Chinese mode
    private var phoneticKeyboardType:    String = "phonetic"
    private var candidateSuggestion:     Bool = true  // gates RP learning (candidate_suggestion)
    private var similiarEnable:          Bool = true  // gates similar-code candidates
    private var similiarList:            Int  = 20    // max similar-code candidates
    private var numberRowInEnglish:      Bool = true  // show number row on English layout
    private var enableEmoji:             Bool = true  // mirrors Android getEmojiMode() default true
    private var enableEmojiPosition:     Int  = 5     // mirrors Android getEmojiDisplayPosition() default 5
    private var keyboardSize:            CGFloat = 1.0  // mirrors Android getKeyboardSize(); 0.8=特小 0.9=小 1.0=一般 1.1=大 1.2=特大
    private var candidateFontScale:      CGFloat = 1.0  // mirrors Android getFontSize(); scales candidate bar fonts + bar height + composing popup
    private var candidateSwitch:         Bool = true    // mirrors Android candidate_switch; true=free scroll, false=paged
    private var showArrowKey:            Int  = 0       // 0=none, 1=above, 2=below
    private var splitKeyboardMode:       Int  = 0       // 0=off, 1=on, 2=landscape-only (iPad only)

    // MARK: - Activated IM Cycling (spec §10)
    private var activatedIMs:  [ImConfig] = []
    private var activeIMIndex: Int        = 0

    // MARK: - Expanded Candidates Panel
    private var expandedCandidatesPanel: UIView?
    private var expandedScrollView: UIScrollView?
    private var expandedContentView: UIView?
    private var expandedContentHeightConstraint: NSLayoutConstraint?
    private var isExpandedCandidatesVisible = false
    private var expandedCandidates: [Mapping] = []
    /// Index into `expandedCandidates` to paint with the theme highlight, or -1 for none.
    /// Mirrors the candidate bar's selection seeding (Android CandidateView rules).
    private var expandedSelectedIndex: Int = -1
    private var expandedCollapseButton: UIButton?
    /// Live height constraint ref for the expanded panel's collapse
    /// chevron button. Height tracks `candidateBarHeight`; we update this
    /// in `applyFeedbackSettings()` whenever the bar height changes
    /// (font-scale pref). Width is a static constant
    /// (`chevronButtonWidth`) so it doesn't need a live ref.
    private var expandedCollapseHeightConstraint: NSLayoutConstraint?
    /// 1-pt vertical divider sitting just left of the expanded panel's collapse chevron,
    /// mirroring CandidateBarView.moreSep so the reserved zone matches the bar exactly.
    private var expandedMoreSep: UIView?
    /// Dismiss (✕) button at the panel's top-left — mirrors CandidateBarView.dismissButton.
    private var expandedDismissButton: UIButton?
    /// Persistent custom vertical scrollbar thumb for the expanded candidates panel.
    private var expandedScrollThumb: UIView?
    private let expandedSepWidth: CGFloat = LayoutMetrics.CandidateBar.dividerWidth
    private let expandedScrollThumbWidth: CGFloat = 3
    private let expandedScrollThumbMinHeight: CGFloat = 36
    /// Mirror of the candidate bar's keyname strip overlay. Pinned to the
    /// top of the expanded panel so when the user expands the candidate
    /// bar the first row stays pixel-identical (same composing keyname,
    /// same vertical offset for glyphs).
    private var expandedComposingLabel: UILabel?
    private var limeToastState = LimeToastState()
    private var limeToastTimer: Timer?

    // MARK: - Chinese Punctuation (spec §11)
    private var hasChineseSymbolCandidatesShown: Bool = false

    // MARK: - Symbol Keyboard (spec §10)
    private var isSymbolMode:       Bool = false
    private var symbolPageIndex:    Int  = 0
    // Current symbol layout pair (base + shift) — resolved per-IM when entering symbol mode.
    private var symbolLayouts: [String] = ["symbols1", "symbols2"]
    // Layout to restore when leaving symbol mode
    private var preSymbolLayout:   LimeKeyLayout? = nil
    private var preSymbolEnglish:  Bool           = false

    // MARK: - LD Composing Buffer (spec §5, §8)
    private var LDComposingBuffer: String = ""

    // MARK: - Search Thread Management (spec §6 Thread Interruption)
    private var currentSearchID: UInt64 = 0

    // MARK: - Self-Update Guard (spec §12)
    // Set true around our own insertText/deleteBackward calls to suppress textDidChange checks
    private var isSelfUpdate = false

    // MARK: - Key Preview
    private weak var keyPreviewView: UIView?

    /// True when the **host app** is iPad-class. iPhone-only apps running on iPad
    /// in scaled/compatibility mode report `.phone` here even though the device is
    /// an iPad — we must follow the host so the keyboard matches the host UI.
    /// (`UIDevice.current.userInterfaceIdiom` is the wrong signal here.)
    private var isOnPad: Bool { traitCollection.userInterfaceIdiom == .pad }

    // MARK: - Keyboard Geometry
    private var baseCandidateBarHeight: CGFloat { LayoutMetrics.ComposingPopup.barBaseHeight(isPad: isOnPad) }
    private var candidateBarHeight: CGFloat { baseCandidateBarHeight * candidateFontScale }
    private var candidateBarHeightConstraint: NSLayoutConstraint?
    private var keyboardTopToCandidateConstraint: NSLayoutConstraint?
    private var keyboardTopToViewConstraint: NSLayoutConstraint?
    private var emojiPanelBottomConstraint: NSLayoutConstraint?
    // keyRowHeight removed — height is now driven by KeyboardView.preferredHeight,
    // which sums actual per-row heights (54 pt regular, 56 pt bottom row).
    private var keyboardHeightConstraint: NSLayoutConstraint?
    private weak var inlineMenuPanel: UIView?
    private weak var inlineMenuDismissTapGesture: UITapGestureRecognizer?
    private weak var keyboardTopCoverView: UIView?
    private var keyboardHostCoverHeight: CGFloat { isOnPad ? 0 : 12 }

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        // Tell LayoutLoader whether the **host app** is iPad-class BEFORE any
        // load() call. iPhone-only apps running on iPad must use phone layouts.
        LayoutLoader.hostIsPad = isOnPad
        LayoutLoader.clearCache()
        // Load size/font prefs from UserDefaults synchronously so candidateFontScale
        // and keyboardSize are correct when setupKeyboardUI() creates its height
        // constraints. searchServer is nil here so applyPrefsToSearchEngine() is a no-op.
        loadSettings()
        let _initLayout = numberRowInEnglish ? "lime_english_number" : "lime_english"
        if let loaded = LayoutLoader.load(_initLayout) { currentLayout = loaded }
        LayoutLoader.prefetchCommonLayouts()
        setupKeyboardUI()
        applyHeight()
        // Heartbeat for the Settings app's Setup tab. Writes current state, not a
        // one-way latch — the host app clears these on foreground and we re-assert
        // them here (and again in viewWillAppear) so the banner can reflect reality
        // across enable/disable/Full-Access toggles.
        sharedDefaults?.set(true, forKey: "keyboard_extension_loaded")
        sharedDefaults?.set(hasFullAccess, forKey: "keyboard_has_full_access")
        sharedDefaults?.set(Date().timeIntervalSince1970, forKey: "keyboard_last_seen_at")
        // Run DB setup off the main thread — avoids blocking the keyboard's view
        // lifecycle and prevents the Settings watchdog from killing the Preferences
        // app (0x8BADF00D) when it presents the keyboard extension for the first time.
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.setupDatabase()
        }
    }

    /// Called every time the keyboard becomes visible (spec §2 initOnStartInput).
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        // Reload database if Settings app performed a restore while the keyboard was inactive.
        // After restore, the keyboard's DatabaseQueue points to the old (replaced) file.
        let restoredAt = UserDefaults(suiteName: "group.net.toload.limeime")?
            .double(forKey: "lime_db_restored_at") ?? 0
        if restoredAt > lastKnownRestoreTimestamp {
            lastKnownRestoreTimestamp = restoredAt
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                self?.setupDatabase()
            }
        }
        sharedDefaults?.set(true, forKey: "keyboard_extension_loaded")
        sharedDefaults?.set(hasFullAccess, forKey: "keyboard_has_full_access")
        sharedDefaults?.set(Date().timeIntervalSince1970, forKey: "keyboard_last_seen_at")
        initOnStartInput()
    }

    /// Called every time the keyboard is dismissed — equivalent to Android postFinishInput().
    /// Triggers deferred Tier 2 learning: RP learning + LD phrase learning (spec §9, §13.5).
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        dismissPopupKeyboard()
        // Detach window-attached composing popup so it doesn't linger after
        // the keyboard is dismissed.
        hideComposingPopup()
        // postFinishInput() snapshots scorelist + ldPhraseListArray and dispatches to background
        // internally — no outer async wrapper needed.
        searchServer?.postFinishInput()
    }

    // MARK: - iOS Input Assistant Bar (iPad only)

    /// On iPad: replace the default assist bar with [Paste | composing info].
    /// On iPhone: suppress the bar entirely (it's not shown anyway).
    override func viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        // The keyboard extension's traitCollection.userInterfaceIdiom may have been
        // .unspecified during viewDidLoad (host idiom not yet propagated). Resync
        // here — viewWillLayoutSubviews fires after the host attaches the input
        // view and the trait collection reflects the host. Used by LayoutLoader
        // for `_ipad` variant gating; KeyboardView/CandidateBarView size
        // themselves from UIDevice (real iPad hardware) and are not pushed here.
        let nowPad = isOnPad
        if LayoutLoader.hostIsPad != nowPad {
            LayoutLoader.hostIsPad = nowPad
            LayoutLoader.clearCache()
            if currentLayout.id != "__unset__",
               let reloaded = LayoutLoader.load(currentLayout.id) {
                currentLayout = reloaded
                keyboardView?.setLayout(reloaded)
            }
        }
        // Use screen bounds to detect orientation — NOT view.bounds.
        // The keyboard extension view is always wider than tall (e.g. 430 × 270pt),
        // so view.bounds.width > view.bounds.height is always true and would
        // permanently force landscape row heights and horizontal label layout.
        let screen = UIScreen.main.bounds
        let landscape = screen.width > screen.height
        keyboardView?.isLandscape = landscape
        let isPad   = isOnPad
        let doSplit = isPad && (splitKeyboardMode == 1 || (splitKeyboardMode == 2 && landscape))
        keyboardView?.splitMode = doSplit
        applyHeight()
        updateGlobeKeyVisibility()
    }

    // MARK: - Trait / Theme Change (spec §2)

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        // Host idiom may flip when the user moves an iPhone-only app between
        // iPad multitasking modes; resync LayoutLoader so the next load() picks
        // the correct iPad/iPhone variant, and push the new value into the views.
        let newHostIsPad = isOnPad
        if LayoutLoader.hostIsPad != newHostIsPad {
            LayoutLoader.hostIsPad = newHostIsPad
            LayoutLoader.clearCache()
            if let reloaded = LayoutLoader.load(currentLayout.id) {
                currentLayout = reloaded
                keyboardView?.setLayout(reloaded)
            }
            updateGlobeKeyVisibility()
            applyHeight()
        }
        guard let prev = previousTraitCollection else { return }

        // Theme change (light ↔ dark): keyboard background updates automatically via system colors,
        // but we need to refresh the keyboard view to pick up any color-dependent resources.
        // When keyboard_theme == 6 (系統設定), resolvedKeyboardTheme re-evaluates here so the
        // palette switches automatically as the user toggles system appearance.
        if prev.userInterfaceStyle != traitCollection.userInterfaceStyle {
            let t = resolvedKeyboardTheme
            keyboardView?.theme = t
            candidateBar?.systemUserInterfaceStyle = traitCollection.userInterfaceStyle
            candidateBar?.theme = t
        }

        // Horizontal size class change (e.g. iPad split-screen, landscape):
        // reset composing and reload the layout.
        if prev.horizontalSizeClass != traitCollection.horizontalSizeClass {
            cancelComposing()
            updateGlobeKeyVisibility()
            applyHeight()
        }
    }

    override func textWillChange(_ textInput: UITextInput?) {
        // Nothing — detection deferred to textDidChange
    }

    override func textDidChange(_ textInput: UITextInput?) {
        // Guard: skip checks triggered by our own insertText/deleteBackward (spec §12)
        guard !isSelfUpdate else { return }

        // Field-change detection. When the user taps a new input while the
        // keyboard is still on screen, `viewWillAppear` does NOT re-fire, so
        // `initOnStartInput()` (where layout / mEnglishOnly / mPredictionOn /
        // returnKeyType adapt to the field's hints) never runs for the new
        // field. Compare the proxy's current keyboardType / returnKeyType
        // against the last-seen values and re-adapt on any change.
        let currentKB     = textDocumentProxy.keyboardType  ?? .default
        let currentReturn = textDocumentProxy.returnKeyType ?? .default
        if currentKB != lastSeenKeyboardType || currentReturn != lastSeenReturnKeyType {
            initOnStartInput()
            return
        }

        // If the cursor changed externally while composing, cancel composing
        if composingLength > 0 {
            let before = textDocumentProxy.documentContextBeforeInput ?? ""
            if !before.hasSuffix(mComposing) {
                cancelComposing()
            }
        }
        updateShiftForAutoCap()
    }

    // MARK: - Initialization (spec §2 initOnStartInput)

    private func initOnStartInput() {
        // If the container app modified IM records (score/code/word), clear the stale cache
        // so the first keystroke re-queries from the updated DB.
        if sharedDefaults?.bool(forKey: "needsKeyboardCacheReset") == true {
            searchServer?.clearAllCaches()
            sharedDefaults?.removeObject(forKey: "needsKeyboardCacheReset")
        }

        // Re-read phonetic_keyboard_type + the phonetic IM's keyboardId so Settings-app
        // changes (picker writes pref + DB row via updatePhoneticKeyboard) take effect
        // without a full extension restart. Mirrors Android's per-onStartInput re-read.
        refreshPhoneticKeyboardPrefs()

        mCompletionOn = false
        clearShiftState()

        // Map keyboard type → mEnglishOnly + mPredictionOn (spec §2 table)
        switch textDocumentProxy.keyboardType ?? .default {
        case .numberPad, .decimalPad, .asciiCapableNumberPad:
            mEnglishOnly = true; mPredictionOn = true
        case .phonePad:
            mEnglishOnly = true; mPredictionOn = false
        case .emailAddress, .URL:
            mEnglishOnly = true; mPredictionOn = false
        default:
            // Restore persisted language mode if enabled (spec §15)
            if mPersistentLanguageMode {
                mEnglishOnly = sharedDefaults?.bool(forKey: "persisted_english_mode") ?? false
            } else {
                mEnglishOnly = false
            }
            mPredictionOn = true
        }

        // Restore last-used LIME IM (mirrors Android mLIMEPref.getActiveIM(), key "keyboard_list")
        if !mEnglishOnly {
            let saved = sharedDefaults?.string(forKey: "keyboard_list") ?? ""
            if !saved.isEmpty && saved != activeIM {
                // Find the saved IM in the activated list and restore index
                if let idx = activatedIMs.firstIndex(where: { $0.tableNick == saved }) {
                    activeIM      = saved
                    activeIMIndex = idx
                    let caps = searchServer?.detectIMCapabilities(tableName: activeIM)
                        ?? (hasNumber: false, hasSymbol: false)
                    searchServer?.setTableName(activeIM,
                        hasNumberMapping: caps.hasNumber,
                        hasSymbolMapping: caps.hasSymbol)
                    searchServer?.setPhoneticKeyboardType(phoneticKeyboardType)
                    refreshImKeys()
                }
            }
        }

        // Match Apple's keyboard: adapt the Enter key icon/label to the host's
        // returnKeyType (e.g. magnifier for URL/search fields, "Go" / "Send" labels).
        // KeyboardView.didSet skips the rebuild if rowViews are empty (initial load)
        // and triggers one otherwise; the subsequent setLayout below rebuilds with the
        // already-updated returnKeyType, so there is no double-build on field focus.
        keyboardView?.returnKeyType = textDocumentProxy.returnKeyType ?? .default

        let kbType        = textDocumentProxy.keyboardType ?? .default
        let isPhonePad    = kbType == .phonePad
        let isNumericPad  = kbType == .numberPad
                         || kbType == .decimalPad
                         || kbType == .asciiCapableNumberPad
        let englishLayout = numberRowInEnglish ? "lime_english_number" : "lime_english"
        let layoutName: String
        if isPhonePad {
            layoutName = "phone_number"
        } else if isNumericPad {
            // Mirror Android's MODE_TEXT + isSymbol path: route number /
            // decimal fields to the symbols keyboard (digits + punctuation),
            // not the English-alphabet layout. `.phonePad` already has its
            // own restricted T9-style layout above.
            layoutName = "symbols1"
        } else if mEnglishOnly || activatedIMs.isEmpty {
            layoutName = englishLayout
        } else {
            layoutName = resolvedLayoutId(for: activeIM)
        }
        if let newLayout = LayoutLoader.load(layoutName) ?? LayoutLoader.load(englishLayout),
           newLayout.id != currentLayout.id {
            currentLayout = newLayout
            keyboardView?.setLayout(currentLayout)
            applyHeight()
        }

        clearComposing(force: false)
        tempEnglishWord = ""

        // Record what we just adapted to so textDidChange's field-change
        // detector doesn't re-trigger on the next keystroke.
        lastSeenKeyboardType  = textDocumentProxy.keyboardType  ?? .default
        lastSeenReturnKeyType = textDocumentProxy.returnKeyType ?? .default
    }

    // MARK: - Database Setup

    private func setupDatabase() {
        guard let context = try? DBServer.shared.prepareKeyboardRuntimeDatabase() else { return }
        let ss = context.searchServer
        let resolved = context.activatedIMs
        let resolvedIM = context.initialIM

        // Marshal all state assignments back to the main thread
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.searchServer = ss
            self.activatedIMs  = resolved

            // Restore the last-used IM from keyboard_list (written by cycleIM / switchIM).
            // setupDatabase runs once, async — this is the only reliable place to apply
            // the saved IM because activatedIMs is empty when initOnStartInput runs.
            let savedIM = UserDefaults(suiteName: "group.net.toload.limeime")?.string(forKey: "keyboard_list") ?? ""
            if !savedIM.isEmpty, let idx = resolved.firstIndex(where: { $0.tableNick == savedIM }) {
                self.activeIM      = savedIM
                self.activeIMIndex = idx
                let savedCaps = ss.detectIMCapabilities(tableName: savedIM)
                ss.setTableName(savedIM, hasNumberMapping: savedCaps.hasNumber, hasSymbolMapping: savedCaps.hasSymbol)
                // Refresh the keyboard layout if the restored IM differs from the
                // phonetic default that initOnStartInput applied before DB was ready.
                let savedImKb: String = {
                    let kbCode = resolved.first(where: { $0.tableNick == savedIM })?.keyboardId ?? ""
                    // If kbCode is already a loadable layout name (e.g. "lime_array", "phone_simple"),
                    // use it directly — same logic as resolvedLayoutId().
                    if LayoutLoader.load(kbCode) != nil {
                        if savedIM == "array10"  { return "phone_simple" }
                        return kbCode
                    }
                    if let imkb = ss.getKeyboardConfig(kbCode)?.imkb, !imkb.isEmpty { return imkb }
                    if savedIM == "array10" { return "phone_simple" }
                    return "lime_\(savedIM)"
                }()
                if !self.mEnglishOnly, let layout = LayoutLoader.load(savedImKb),
                   layout.id != self.currentLayout.id {
                    self.clearShiftState()
                    self.currentLayout = layout
                    self.keyboardView?.setLayout(layout)
                    self.applyHeight()
                }
            } else {
                self.activeIM      = resolvedIM
                self.activeIMIndex = 0
            }

            // Load settings from shared UserDefaults (spec §15)
            // loadSettings() calls applyPrefsToSearchEngine() which pushes all prefs to SearchServer.
            self.loadSettings()
            self.searchServer?.setPhoneticKeyboardType(self.phoneticKeyboardType)
            // imKeysForTable depends on phoneticKeyboardType for the phonetic family,
            // so refreshImKeys must run AFTER setPhoneticKeyboardType.
            self.refreshImKeys()
            self.applyFeedbackSettings()
            self.preloadEmojiCategoryPages()
        }
    }

    /// Returns the JSON layout file ID (e.g. "lime_array_number") for the given IM table nick.
    /// Resolves im.keyboard → keyboard.imkb so that variants like "行列 + 數字列鍵盤" load
    /// the correct JSON instead of always falling back to "lime_<tableNick>".
    /// Re-reads `phonetic_keyboard_type` from shared defaults and the phonetic IM's
    /// current `keyboard` value from the DB. If either differs from the in-memory
    /// copy, the cached `activatedIMs` entry is updated and the layout is re-applied
    /// so changes made in Settings (via `updatePhoneticKeyboard`) take effect on the
    /// next keyboard show. Mirrors Android's per-onStartInput `im.keyboard` re-read.
    private func refreshPhoneticKeyboardPrefs() {
        // 1. Pref-side (controls DB remap path inside LimeDB)
        let freshType = sharedDefaults?.string(forKey: "phonetic_keyboard_type") ?? phoneticKeyboardType
        if freshType != phoneticKeyboardType {
            phoneticKeyboardType = freshType
            searchServer?.setPhoneticKeyboardType(phoneticKeyboardType)
            // Phonetic-family imkeys depend on kbType (BPMF / ETEN26 / HSU / ETEN).
            refreshImKeys()
        }

        // 2. DB-side (controls visible layout via resolvedLayoutId → activatedIMs cache)
        guard let idx = activatedIMs.firstIndex(where: { $0.tableNick == "phonetic" }) else { return }
        let freshKb = searchServer?.getImConfig("phonetic", "keyboard") ?? ""
        guard !freshKb.isEmpty, freshKb != activatedIMs[idx].keyboardId else { return }
        let old = activatedIMs[idx]
        activatedIMs[idx] = ImConfig(
            id: old.id,
            imName: old.imName,
            tableNick: old.tableNick,
            label: old.label,
            fullName: old.fullName,
            keyboardId: freshKb,
            keyboardLandscapeId: freshKb,
            enabled: old.enabled,
            sortOrder: old.sortOrder)
        // If the phonetic IM is currently active, swap the visible layout immediately.
        if activeIM == "phonetic", !mEnglishOnly {
            let newLayoutId = resolvedLayoutId(for: "phonetic")
            if let newLayout = LayoutLoader.load(newLayoutId), newLayout.id != currentLayout.id {
                currentLayout = newLayout
                keyboardView?.setLayout(currentLayout)
                applyHeight()
            }
        }
    }

    private func resolvedLayoutId(for tableNick: String) -> String {
        // For phonetic IMs, keyboard type determines the visible layout.
        // Mirrors Android: eten26/hsu → "lime" (English QWERTY, remap transparent);
        //                  eten26_symbol → "et26" layout; hsu_symbol → "hsu" layout.
        if tableNick == "phonetic" {
            let kbType = phoneticKeyboardType
            if kbType == "eten26_symbol" || kbType == "et26" {
                if LayoutLoader.load("lime_et26") != nil { return "lime_et26" }
            } else if kbType == "hsu_symbol" {
                if LayoutLoader.load("lime_hsu") != nil { return "lime_hsu" }
            } else if kbType.hasPrefix("eten26") || kbType.hasPrefix("hsu") {
                let engLayout = numberRowInEnglish ? "lime_english_number" : "lime_english"
                if LayoutLoader.load(engLayout) != nil { return engLayout }
            }
        }
        guard let imConfig = activatedIMs.first(where: { $0.tableNick == tableNick }) else {
            return "lime_\(tableNick)"
        }
        let kbCode = imConfig.keyboardId
        // If kbCode is a directly loadable layout filename (e.g. "lime_array", "phone_simple"),
        // use it as-is. This covers the fallback list path where we store the layout name directly.
        if LayoutLoader.load(kbCode) != nil {
            // array10 was historically seeded with "lime_array" (wrong — same as regular array).
            // Redirect to the correct phone-style layout for any existing DB with this old value.
            if tableNick == "array10" && kbCode == "lime_array" { return "phone_simple" }
            return kbCode
        }
        // DB-loaded path: kbCode is the keyboard table's code; look up the imkb value.
        if let imkb = searchServer?.getKeyboardConfig(kbCode)?.imkb, !imkb.isEmpty {
            return imkb
        }
        // array10 has no "lime_array10" layout; its canonical fallback is phone_simple
        // (mirrors Android: phonenum keyboard config → imkb = "phone_simple")
        if tableNick == "array10" { return "phone_simple" }
        return "lime_\(tableNick)"
    }

    /// Shared UserDefaults for reading settings written by the container app.
    private var sharedDefaults: UserDefaults? {
        UserDefaults(suiteName: "group.net.toload.limeime")
    }

    /// Load all user preferences from shared UserDefaults (spec §15).
    /// Returns the resolved theme index (0–5), mapping value 6 (系統設定) to 0 (淺色) or 1 (深色)
    /// based on the current UITraitCollection. Callers use this when applying colour palettes.
    var resolvedKeyboardTheme: Int {
        if currentKeyboardTheme == 6 {
            return traitCollection.userInterfaceStyle == .dark ? 1 : 0
        }
        return currentKeyboardTheme
    }

    private func loadSettings() {
        let d = sharedDefaults
        // Note: d?.bool(forKey:) and d?.integer(forKey:) return false/0 when the key is
        // absent (not nil), so the ?? fallback never fires. Use object(forKey:) as? Type
        // for all settings whose default is non-false / non-zero.
        currentKeyboardTheme    = (d?.object(forKey: "keyboard_theme") != nil)
            ? (d?.integer(forKey: "keyboard_theme") ?? 6)
            : 6
        hanConvertOption        = d?.integer(forKey: "han_convert_option")                       ?? 0
        autoChineseSymbol       = d?.bool(forKey: "auto_chinese_symbol")                        ?? false
        sortSuggestions         = (d?.object(forKey: "learning_switch")          as? Bool)      ?? true
        smartChineseInput       = d?.bool(forKey: "smart_chinese_input")                        ?? false
        learnPhrase             = (d?.object(forKey: "learn_phrase")              as? Bool)     ?? true
        candidateSuggestion     = (d?.object(forKey: "candidate_suggestion")      as? Bool)     ?? true
        englishPredictionOn     = (d?.object(forKey: "english_dictionary_enable") as? Bool)     ?? true
        hasVibration            = (d?.object(forKey: "vibrate_on_keypress")       as? Bool)     ?? true
        vibrateLevel            = (d?.object(forKey: "vibrate_level")             as? Int)      ?? 40
        hasSound                = d?.bool(forKey: "sound_on_keypress")                          ?? false
        mPersistentLanguageMode = d?.bool(forKey: "persistent_language_mode")                   ?? false
        phoneticKeyboardType    = d?.string(forKey: "phonetic_keyboard_type")                   ?? "phonetic"
        autoCommit              = d?.integer(forKey: "auto_commit")                             ?? 0
        similiarEnable          = (d?.object(forKey: "similiar_enable")           as? Bool)     ?? true
        similiarList            = (d?.object(forKey: "similiar_list")             as? Int)      ?? 20
        numberRowInEnglish      = (d?.object(forKey: "number_row_in_english")     as? Bool)     ?? true
        enableEmoji             = (d?.object(forKey: "enable_emoji")              as? Bool)     ?? true
        enableEmojiPosition     = (d?.object(forKey: "enable_emoji_position")     as? Int)      ?? 5
        if let sizeStr = d?.string(forKey: "keyboard_size"), let sizeVal = Float(sizeStr) {
            keyboardSize = CGFloat(sizeVal)
        } else {
            keyboardSize = 1.0
        }
        if let fontStr = d?.string(forKey: "font_size"), let fontVal = Float(fontStr) {
            candidateFontScale = CGFloat(fontVal)
        } else {
            candidateFontScale = 1.0
        }
        // candidate_switch UI toggle removed — free-scroll is now the only mode.
        // See LIMEPreferenceManager.candidateSwitch (always true).
        candidateSwitch = true
        showArrowKey      = d?.integer(forKey: "show_arrow_key")      ?? 0
        splitKeyboardMode = d?.integer(forKey: "split_keyboard_mode") ?? 0
        applyPrefsToSearchEngine()
    }

    /// Push all engine-level prefs to SearchServer (spec §15).
    /// Call after loadSettings() and after SearchServer is initialized.
    private func applyPrefsToSearchEngine() {
        searchServer?.sortSuggestions     = sortSuggestions
        searchServer?.smartChineseInput   = smartChineseInput
        searchServer?.candidateSuggestion = candidateSuggestion
        searchServer?.learnPhrasePref     = learnPhrase
        searchServer?.similiarEnable      = similiarEnable
        searchServer?.similiarList        = similiarList
        // Sync pref-driven config to LimeDB under cacheLock (fixes threading race + ordering).
        searchServer?.applyPrefsToDatabase()
    }

    /// Push feedback and theme settings to KeyboardView, CandidateBarView, and composing popup (spec §15).
    private func applyFeedbackSettings() {
        keyboardView?.feedbackVibration = hasVibration
        keyboardView?.feedbackSound     = hasSound
        keyboardView?.vibrateLevel      = vibrateLevel
        keyboardView?.showArrowKey      = showArrowKey
        let prevScale = keyboardView?.keySizeScale
        keyboardView?.keySizeScale      = keyboardSize
        candidateBar?.feedbackVibration = hasVibration
        candidateBar?.vibrateLevel      = vibrateLevel
        // Pre-warm Taptic Engine so the very first keypress has no cold-start lag.
        keyboardView?.prepareHapticGenerator()
        candidateBar?.prepareHapticGenerator()
        rebuildHapticGenerator()
        let prevFontScale = candidateBar?.fontScale
        candidateBar?.fontScale         = candidateFontScale
        candidateBar?.candidateSwitch   = candidateSwitch
        candidateBarHeightConstraint?.constant = candidateBarHeight
        // Keep the expanded panel's collapse chevron height in lockstep with
        // the bar height. The dismiss button height is derived automatically
        // via a relative constraint off collapseBtn, so no separate update needed.
        expandedCollapseHeightConstraint?.constant = candidateBarHeight
        let t = resolvedKeyboardTheme
        let pal = KeyboardPalette.palettes[max(0, min(t, KeyboardPalette.palettes.count - 1))]
        // Candidate bar backdrop is a transparent system blur — text must contrast the
        // system backdrop, not the keyboard theme. Capture system style before
        // overrideUserInterfaceStyle locks the bar's traitCollection to the theme.
        let systemStyle = traitCollection.userInterfaceStyle
        let adaptedCandiText: UIColor
        if systemStyle == .dark {
            adaptedCandiText = KeyboardPalette.iosDark(.label)
        } else if t == 1 {
            adaptedCandiText = KeyboardPalette.iosLight(.label)
        } else {
            adaptedCandiText = pal.candiText
        }
        keyboardView?.theme  = t
        candidateBar?.systemUserInterfaceStyle = systemStyle
        candidateBar?.theme  = t
        if prevScale != keyboardSize || prevFontScale != candidateFontScale { applyHeight() }
        // Lock dynamic UIColors (.label etc. baked into palette[0]/[1]) to the
        // keyboard's chosen theme so key chrome (tintColor etc.) doesn't re-resolve
        // against the host app's appearance. Candidate text is handled separately
        // via adaptedCandiText above.
        let chromeStyle: UIUserInterfaceStyle = (t == 1) ? .dark : .light
        candidateBar?.overrideUserInterfaceStyle         = chromeStyle
        expandedCandidatesPanel?.overrideUserInterfaceStyle = chromeStyle
        expandedCandidatesPanel?.backgroundColor = .clear
        expandedCollapseButton?.tintColor = adaptedCandiText
        expandedMoreSep?.backgroundColor = adaptedCandiText.withAlphaComponent(LayoutMetrics.CandidateBar.separatorAlpha)
        expandedDismissButton?.tintColor = pal.label
        expandedDismissButton?.backgroundColor = pal.normalKey.withAlphaComponent(0.15)
        expandedComposingLabel?.font = candidateBar.composingStripFont
        expandedComposingLabel?.textColor = adaptedCandiText.withAlphaComponent(LayoutMetrics.ComposingPopup.textAlpha)
        if isExpandedCandidatesVisible { reloadExpandedCandidates() }
    }

    /// Refresh the cached `imkeys` for the active IM. Called after every
    /// SearchServer.setTableName / setPhoneticKeyboardType so handleCharacter
    /// can use it as the authoritative input-acceptance check on iPad layouts.
    /// Reads through `SearchServer.imKeysForTable` which uses hardcoded keymaps for
    /// known IMs (phonetic / cj / dayi / array) and falls back to the im
    /// table's `imkeys` field for unknown ones — the im table row may be
    /// missing for IMs that have a hardcoded keymap, so getImConfig alone
    /// returns "" for them on iOS.
    private func refreshImKeys() {
        currentImKeys = searchServer?.imKeysForTable(activeIM) ?? ""
    }

    // MARK: - UI Setup

    private func setupKeyboardUI() {
        // Transparent so the area above the candidate bar (the collapsible
        // popup strip) doesn't paint a gray rectangle next to the keyname bubble.
        view.backgroundColor = .clear
        // Initial values for composing popup / expanded panel chrome. Clamped to {0,1}
        // so coloured themes (2–5) fall back to Light/Dark chrome instead of
        // inheriting the theme's tinted candidate bar. applyFeedbackSettings() updates
        // these at runtime when the resolved theme changes.
        let t0 = resolvedKeyboardTheme
        let pal = KeyboardPalette.palettes[max(0, min(t0, 1))]
        let setupSystemStyle = traitCollection.userInterfaceStyle
        let adaptedCandiText: UIColor
        if setupSystemStyle == .dark {
            adaptedCandiText = KeyboardPalette.iosDark(.label)
        } else if t0 == 1 {
            adaptedCandiText = KeyboardPalette.iosLight(.label)
        } else {
            adaptedCandiText = pal.candiText
        }

        // Candidate bar
        candidateBar = CandidateBarView()
        candidateBar.systemUserInterfaceStyle = setupSystemStyle
        candidateBar.delegate = self
        candidateBar.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(candidateBar)

        // Keyboard view
        keyboardView = KeyboardView(layout: currentLayout)
        keyboardView.delegate = self
        keyboardView.inputModeViewController = self
        keyboardView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(keyboardView)

        NSLayoutConstraint.activate([
            candidateBar.topAnchor.constraint(equalTo: view.topAnchor),
            candidateBar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            candidateBar.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            {
                let c = candidateBar.heightAnchor.constraint(equalToConstant: candidateBarHeight)
                candidateBarHeightConstraint = c
                return c
            }(),

            {
                let c = keyboardView.topAnchor.constraint(equalTo: candidateBar.bottomAnchor)
                keyboardTopToCandidateConstraint = c
                return c
            }(),
            keyboardView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            keyboardView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            keyboardView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        keyboardTopToViewConstraint = keyboardView.topAnchor.constraint(equalTo: view.topAnchor, constant: 118)
        // Space-key gestures (swipe + long-press) are now added directly in
        // KeyboardView.makeKeyButton so they survive every setLayout() call.

        // Expanded candidates panel — hidden by default. When shown, keyboardView is hidden too
        // (see showExpandedCandidates), so the panel doesn't need to obscure keys itself. The
        // panel stays `.clear` so the parent UIInputView(.keyboard) blur shows through exactly
        // as it did for keyboardView — guaranteeing a pixel-identical backdrop.
        let panel = UIView()
        panel.backgroundColor = .clear
        panel.isHidden = true
        panel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(panel)

        let sv = UIScrollView()
        sv.backgroundColor = .clear
        sv.translatesAutoresizingMaskIntoConstraints = false
        sv.showsVerticalScrollIndicator = false
        sv.alwaysBounceVertical = false
        sv.delegate = self
        panel.addSubview(sv)

        let scrollThumb = UIView()
        scrollThumb.backgroundColor = adaptedCandiText.withAlphaComponent(0.35)
        scrollThumb.layer.cornerRadius = expandedScrollThumbWidth / 2
        scrollThumb.layer.masksToBounds = true
        scrollThumb.isHidden = true
        scrollThumb.isUserInteractionEnabled = false
        panel.addSubview(scrollThumb)

        let contentView = UIView()
        contentView.translatesAutoresizingMaskIntoConstraints = false
        sv.addSubview(contentView)

        NSLayoutConstraint.activate([
            panel.topAnchor.constraint(equalTo: candidateBar.topAnchor),
            panel.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            panel.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            panel.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            sv.topAnchor.constraint(equalTo: panel.topAnchor),
            sv.leadingAnchor.constraint(equalTo: panel.leadingAnchor),
            sv.trailingAnchor.constraint(equalTo: panel.trailingAnchor),
            sv.bottomAnchor.constraint(equalTo: panel.bottomAnchor),

            contentView.topAnchor.constraint(equalTo: sv.contentLayoutGuide.topAnchor),
            contentView.leadingAnchor.constraint(equalTo: sv.contentLayoutGuide.leadingAnchor),
            contentView.trailingAnchor.constraint(equalTo: sv.contentLayoutGuide.trailingAnchor),
            contentView.bottomAnchor.constraint(equalTo: sv.contentLayoutGuide.bottomAnchor),
            contentView.widthAnchor.constraint(equalTo: sv.frameLayoutGuide.widthAnchor),
        ])
        let hc = contentView.heightAnchor.constraint(equalToConstant: 0)
        hc.isActive = true
        expandedCandidatesPanel          = panel
        expandedScrollView               = sv
        expandedContentView              = contentView
        expandedContentHeightConstraint  = hc
        expandedScrollThumb              = scrollThumb

        // Collapse button (chevron.up) pinned to top-right, same width as candi bar chevron.
        // Mirror the collapsed bar's chevron point size so the two surfaces
        // do not drift visually when the user expands / collapses.
        let collapseBtn = UIButton(type: .system)
        let collapseChevronConfig = UIImage.SymbolConfiguration(
            pointSize: LayoutMetrics.CandidateBar.Chevron.iconSize(isPad: isOnPad), weight: .regular)
        collapseBtn.setImage(UIImage(systemName: "chevron.up", withConfiguration: collapseChevronConfig),
                             for: .normal)
        collapseBtn.tintColor = adaptedCandiText
        // Match candidate-row glyph bias so the chevron stays vertically
        // aligned with the row 1 candidates and the bar's chevron.
        // Using KVC `contentEdgeInsets` (not `UIButton.Configuration`) because
        // Configuration clamps negative insets, breaking the symmetric bias.
        let chevronBias = candidateBar.composingStripHeight / 2
        // Symmetric horizontal insets are unnecessary because the icon is
        // centered in the (now narrower) frame; only the vertical bias matters.
        collapseBtn.setValue(NSValue(uiEdgeInsets: UIEdgeInsets(top: chevronBias, left: 0,
                                                                bottom: -chevronBias, right: 0)),
                             forKey: "contentEdgeInsets")
        // 0.01-alpha touch-trap fill so taps in the chevron's padding land on
        // a non-clear pixel — keyboard extensions drop touches on transparent
        // pixels (see docs/IOS_CANDI_TOUCH.md §Resolution).
        collapseBtn.backgroundColor = LayoutMetrics.TouchTrap.fill
        collapseBtn.translatesAutoresizingMaskIntoConstraints = false
        collapseBtn.addTarget(self, action: #selector(collapseExpandedCandidates), for: .touchUpInside)
        panel.addSubview(collapseBtn)

        // Thin vertical divider just left of the chevron — mirrors
        // CandidateBarView.moreSep so the expanded panel's reserved right-hand
        // zone matches the bar and the first row fits the exact same candidate count.
        let sep = UIView()
        sep.backgroundColor = adaptedCandiText.withAlphaComponent(LayoutMetrics.CandidateBar.separatorAlpha)
        sep.translatesAutoresizingMaskIntoConstraints = false
        panel.addSubview(sep)

        // Width is a static constant — mirrors the collapsed bar's chevron
        // button width so the leading edge sits at the same X. Only the
        // height tracks the bar (so the chevron's tap target spans the
        // full first-row height when the user changes font scale).
        let collapseH = collapseBtn.heightAnchor.constraint(equalToConstant: candidateBarHeight)
        expandedCollapseHeightConstraint = collapseH
        NSLayoutConstraint.activate([
            collapseBtn.trailingAnchor.constraint(equalTo: panel.trailingAnchor),
            collapseBtn.topAnchor.constraint(equalTo: panel.topAnchor),
            collapseBtn.widthAnchor.constraint(equalToConstant: LayoutMetrics.CandidateBar.Chevron.buttonWidth(isPad: isOnPad)),
            collapseH,

            sep.trailingAnchor.constraint(equalTo: collapseBtn.leadingAnchor),
            // Bias separator down to match candidate glyphs (mirrors
            // CandidateBarView.moreSep so row 1 stays pixel-identical).
            sep.centerYAnchor.constraint(equalTo: collapseBtn.centerYAnchor, constant: candidateBar.composingStripHeight / 2),
            sep.widthAnchor.constraint(equalToConstant: expandedSepWidth),
            sep.heightAnchor.constraint(equalToConstant: LayoutMetrics.CandidateBar.dividerHeight),
        ])
        expandedCollapseButton = collapseBtn
        expandedMoreSep = sep

        // Dismiss button (✕) at the panel's top-left — mirrors CandidateBarView.dismissButton.
        let dismissBtn = UIButton(type: .system)
        let xmarkConfig = UIImage.SymbolConfiguration(
            pointSize: LayoutMetrics.CandidateBar.Chevron.iconSize(isPad: isOnPad), weight: .regular)
        dismissBtn.setImage(UIImage(systemName: "xmark", withConfiguration: xmarkConfig), for: .normal)
        dismissBtn.tintColor = pal.label
        dismissBtn.backgroundColor = pal.normalKey.withAlphaComponent(0.15)
        dismissBtn.layer.cornerRadius = 6
        dismissBtn.layer.masksToBounds = true
        dismissBtn.translatesAutoresizingMaskIntoConstraints = false
        dismissBtn.addTarget(self, action: #selector(dismissExpandedAndComposing), for: .touchUpInside)
        panel.addSubview(dismissBtn)

        // Dismiss button: half chevron width, height = barHeight − stripHeight (tracks
        // collapseBtn automatically), centered on glyph axis.  No contentEdgeInsets
        // bias — the frame is already positioned at the glyph center.
        NSLayoutConstraint.activate([
            dismissBtn.leadingAnchor.constraint(equalTo: panel.leadingAnchor),
            dismissBtn.centerYAnchor.constraint(equalTo: collapseBtn.centerYAnchor,
                                                 constant: candidateBar.composingStripHeight / 2),
            dismissBtn.heightAnchor.constraint(equalTo: collapseBtn.heightAnchor,
                                                constant: -candidateBar.composingStripHeight),
            dismissBtn.widthAnchor.constraint(equalToConstant: LayoutMetrics.CandidateBar.Chevron.buttonWidth(isPad: isOnPad) / 2),
        ])
        expandedDismissButton = dismissBtn

        // Mirror the candidate bar's keyname strip overlay so the user
        // perceives the expanded panel as the bar growing in place — first
        // row stays pixel-identical (same composing keyname above, same
        // glyph baseline below).
        let stripLabel = UILabel()
        stripLabel.font = candidateBar.composingStripFont
        stripLabel.textColor = adaptedCandiText.withAlphaComponent(LayoutMetrics.ComposingPopup.textAlpha)
        stripLabel.textAlignment = .left
        stripLabel.backgroundColor = .clear
        stripLabel.isUserInteractionEnabled = false
        // Mirror CandidateBarView.composingLabel exactly so the expanded
        // panel's first row + keyname strip is pixel-identical to the
        // collapsed bar (same top inset, same label height, same clip behavior).
        stripLabel.clipsToBounds = false
        stripLabel.translatesAutoresizingMaskIntoConstraints = false
        panel.addSubview(stripLabel)
        NSLayoutConstraint.activate([
            stripLabel.leadingAnchor.constraint(equalTo: dismissBtn.trailingAnchor,
                                                constant: LayoutMetrics.ComposingPopup.labelLeading),
            stripLabel.trailingAnchor.constraint(equalTo: sep.leadingAnchor,
                                                 constant: LayoutMetrics.ComposingPopup.labelTrailingInset),
            stripLabel.topAnchor.constraint(equalTo: panel.topAnchor, constant: 0),
            stripLabel.heightAnchor.constraint(equalToConstant: ceil(candidateBar.composingStripFont.lineHeight)
                                                + LayoutMetrics.ComposingPopup.labelHeightPad),
        ])
        panel.bringSubviewToFront(stripLabel)
        expandedComposingLabel = stripLabel
    }

    private func applyHeight() {
        // Use KeyboardView.preferredHeight so the outer extension view is sized to
        // exactly match the sum of each row's actual height (54 pt regular, 56 pt
        // bottom row), rather than a flat per-row constant that would squish keys.
        let keysHeight = keyboardView?.preferredHeight
            ?? CGFloat(currentLayout.rows.count) * LayoutMetrics.KeyboardRow.fallbackRowHeight
        let barH = candidateBarHeight
        // Keep bar height constraint in sync with the computed bar height.
        // This covers the iPad case where traitCollection.userInterfaceIdiom is
        // .unspecified at viewDidLoad and resolves to .pad only by the time
        // viewWillLayoutSubviews fires — without this, candidateBarHeightConstraint
        // would stay at the Phone value (58×scale) while totalHeight is computed
        // with the Pad value (74×scale), leaving a layout gap.
        candidateBarHeightConstraint?.constant = barH
        expandedCollapseHeightConstraint?.constant = barH
        let totalHeight = barH + keysHeight
        if let existing = keyboardHeightConstraint {
            existing.constant = totalHeight
        } else {
            let c = view.heightAnchor.constraint(equalToConstant: totalHeight)
            c.priority = UILayoutPriority(rawValue: 999)
            c.isActive = true
            keyboardHeightConstraint = c
        }
    }

    // MARK: - Key Event Dispatch (spec §4 onKey)

    private func onKey(primaryCode: Int) {
        // CapsLock pre-processing: lowercase → uppercase (spec §4)
        var code = primaryCode
        if mCapsLock && code >= 97 && code <= 122 { code -= 32 }

        switch code {
        case LimeKeyCode.delete.rawValue:      handleBackspace()
        case LimeKeyCode.shift.rawValue:       handleShift()
        case LimeKeyCode.done.rawValue:        handleClose()
        case LimeKeyCode.globe.rawValue:       advanceToNextInputMode()
        case LimeKeyCode.emojiPanel.rawValue:  showEmojiPanel()
        case LimeKeyCode.emojiABC.rawValue:    hideEmojiPanel()
        case LimeKeyCode.switchToEnglish.rawValue: switchChiEng(toEnglish: true)
        case LimeKeyCode.switchToIM.rawValue:  switchChiEng(toEnglish: false)
        case LimeKeyCode.switchToSymbol.rawValue:     switchToSymbol()
        case LimeKeyCode.switchSymbolKeyboard.rawValue: cycleSymbolPage()
        case LimeKeyCode.nextIM.rawValue:      switchToNextActivatedIM(forward: true)
        case LimeKeyCode.prevIM.rawValue:      switchToNextActivatedIM(forward: false)
        case LimeKeyCode.space.rawValue:       handleEnterOrSpace(isEnter: false)
        case LimeKeyCode.enter.rawValue:       handleEnterOrSpace(isEnter: true)
        case LimeKeyCode.arrowLeft.rawValue:
            if hasCandidatesShown, let bar = candidateBar {
                let next = bar.currentSelectedIndex - 1
                if next >= 0 { bar.setSelectedIndex(next) }
            } else {
                textDocumentProxy.adjustTextPosition(byCharacterOffset: -1)
            }
        case LimeKeyCode.arrowRight.rawValue:
            if hasCandidatesShown, let bar = candidateBar {
                let cur = bar.currentSelectedIndex
                // When no item is preselected (cur == -1), right arrow selects the first candidate.
                let next = cur < 0 ? 0 : cur + 1
                if next < bar.candidateCount { bar.setSelectedIndex(next) }
            } else {
                textDocumentProxy.adjustTextPosition(byCharacterOffset: 1)
            }
        case LimeKeyCode.arrowUp.rawValue:
            moveByLine(forward: false)
        case LimeKeyCode.arrowDown.rawValue:
            moveByLine(forward: true)
        default:
            handleCharacter(code)
            // Auto-commit check: array10 phone-numpad keyboard only.
            // Android uses currentSoftKeyboard.contains("phone") which accidentally also
            // triggers for phonetic ("phonetic" contains "phone"). The intended target is
            // array10's phone-numpad layout (Android keyboard code "phonenum").
            if autoCommit > 0, !mEnglishOnly,
               mComposing.count == autoCommit,
               activeIM == "array10" {
                commitTyped()
            }
        }
    }

    /// Move the cursor approximately one line forward or backward using newline detection.
    /// Falls back to ±10 characters when no newline is found in the proxy context.
    private func moveByLine(forward: Bool) {
        if forward {
            let after = textDocumentProxy.documentContextAfterInput ?? ""
            let offset: Int
            if let nl = after.firstIndex(of: "\n") {
                offset = after.distance(from: after.startIndex, to: nl) + 1
            } else {
                offset = min(10, after.count)
            }
            if offset > 0 { textDocumentProxy.adjustTextPosition(byCharacterOffset: offset) }
        } else {
            let before = textDocumentProxy.documentContextBeforeInput ?? ""
            let trimmed = before.hasSuffix("\n") ? String(before.dropLast()) : before
            let offset: Int
            if let nl = trimmed.lastIndex(of: "\n") {
                offset = trimmed.distance(from: nl, to: trimmed.endIndex)
            } else {
                offset = min(10, trimmed.count)
            }
            if offset > 0 { textDocumentProxy.adjustTextPosition(byCharacterOffset: -offset) }
        }
    }

    // MARK: - Space / Enter Handling (spec §4)

    /// True when the visible candidate list is an optional / browse-only suggestion list
    /// (related phrases, Chinese punctuation, or English predictions) — i.e. Space/Enter
    /// must NOT commit a candidate and Backspace must NOT swallow the delete (see
    /// docs/CANDI_FUNCTION_KEYS.md and docs/#78_ISSUE.md).
    private var isBrowseOnlySuggestionList: Bool {
        isShowingRelatedPhrases
            || hasChineseSymbolCandidatesShown
            || (mEnglishOnly && hasCandidatesShown)
    }

    /// Dismiss a stale browse-only suggestion bar (related phrases / Chinese
    /// punctuation) without touching the host document. Used after Backspace /
    /// Enter / Space when composing is empty and the bar was optional.
    /// Mirrors Android's `hideCandidateView()` in the same branch.
    private func dismissBrowseOnlySuggestionBar() {
        isShowingRelatedPhrases         = false
        hasChineseSymbolCandidatesShown = false
        hasCandidatesShown              = false
        mCandidateList                  = []
        selectedCandidate               = nil
        candidateBar.setCandidates([])
    }

    private func handleEnterOrSpace(isEnter: Bool) {
        let isPhonetic = searchServer?.isPhoneticTable ?? false

        // Associated candidate lists (related phrases, Chinese punctuation, English
        // suggestions) are "browse only" — space/enter must insert a normal space/newline
        // rather than commit the first entry. Mirrors Android's "no default selection"
        // rule for these record types (CandidateView.setSuggestions rule 3).
        let isAssociatedList = isBrowseOnlySuggestionList

        // Determine whether to pick the highlighted candidate (spec §4 conditions)
        let shouldPick: Bool
        if isAssociatedList {
            shouldPick = false
        } else if isEnter {
            shouldPick = hasCandidatesShown
        } else if mEnglishOnly {
            shouldPick = false
        } else if !isPhonetic {
            shouldPick = hasCandidatesShown
        } else {
            // Phonetic: pick if composing ends with space (tone entered) or composing is empty
            shouldPick = hasCandidatesShown && (mComposing.hasSuffix(" ") || mComposing.isEmpty)
        }

        if shouldPick {
            let picked = pickHighlightedCandidate()
            if !picked && mComposing.isEmpty {
                clearSuggestions()
                textDocumentProxy.insertText(isEnter ? "\n" : " ")
            }
            // Enter in English mode: candidate was picked (or nothing picked); either way,
            // the word boundary has been crossed — reset so next word predicts fresh.
            if mEnglishOnly {
                resetTempEnglishWord()
                clearSuggestions()
            }
        } else {
            // Not picking — for phonetic space is a tone marker
            if !isEnter, !mEnglishOnly, isPhonetic, !mComposing.isEmpty {
                handleCharacter(LimeKeyCode.space.rawValue)  // space as tone mark
            } else {
                if !isEnter,
                   mEnglishOnly,
                   LIMEPreferenceManager.shared.autoCap,
                   shouldInsertPeriodForDoubleSpace(before: textDocumentProxy.documentContextBeforeInput ?? "") {
                    textDocumentProxy.deleteBackward()
                    textDocumentProxy.insertText(". ")
                } else {
                    textDocumentProxy.insertText(isEnter ? "\n" : " ")
                }
                // Space or enter in English mode: word boundary crossed — reset prediction.
                if mEnglishOnly {
                    resetTempEnglishWord()
                    clearSuggestions()
                } else if isAssociatedList && mComposing.isEmpty {
                    // #78 Bug 3: dismiss stale browse-only bar (related phrases /
                    // Chinese punctuation) once Enter/Space has inserted its literal
                    // character. Matches Android's hideCandidateView() in the same path.
                    dismissBrowseOnlySuggestionBar()
                }
            }
        }
        updateShiftForAutoCap()
    }

    // MARK: - Character Handling (spec §5 handleCharacter / Character Acceptance Rules)

    private func handleCharacter(_ code: Int) {
        guard code > 0, let scalar = Unicode.Scalar(code) else { return }
        let char      = Character(scalar)
        let charStr   = String(char)

        if mEnglishOnly {
            handleEnglishCharacter(code: code, char: char)
            return
        }

        let hasSymbol  = searchServer?.hasSymbolMapping ?? false
        let hasNumber  = searchServer?.hasNumberMapping ?? false
        let isPhonetic = searchServer?.isPhoneticTable ?? false
        let isLetter   = (code >= 97 && code <= 122) || (code >= 65 && code <= 90)
        let isDigit    = code >= 48 && code <= 57
        let isSpace    = code == 32
        let isComma    = code == 44
        let isPeriod   = code == 46

        // Acceptance rules.
        // iPad layouts: tighter rule driven by the IM table's `imkeys` field.
        // iPad dual-sliding keys output codes that are NOT in any IM's imkeys
        // (full-shape Chinese punct 65292/12290/65306/65307, half-shape 60/62/63/58,
        // CJK brackets 12300-12303/12289, top-row symbols 33-41, etc.). With the
        // legacy hasSymbol/hasNumber heuristic those codes get accepted into
        // mComposing because hasSymbol=true on most Chinese IMs (phonetic, array,
        // dayi, et26, et_41, hsu, hs all use ASCII codes in the symbol range as
        // IM-input keys). That corrupts the composing buffer and breaks the next
        // candidate lookup. Using `imkeys` membership routes those codes to the
        // direct-output branch (commit current candidate + insertText + finishComposing).
        // Phone layouts retain the legacy heuristic to avoid behavior changes.
        let isIPadLayout = isOnPad && currentLayout.id.contains("_ipad")
        let accepted: Bool
        if isIPadLayout && !currentImKeys.isEmpty {
            // Compare both the literal char and its lowercase form so a-z and A-Z
            // both match an imkeys entry stored as lowercase (the convention).
            let inImKeys = currentImKeys.contains(charStr)
                        || currentImKeys.contains(charStr.lowercased())
            accepted = isLetter || inImKeys || (isPhonetic && isSpace)
        } else if !hasSymbol && !hasNumber {
            accepted = isLetter || (isPhonetic && isSpace) || isComma || isPeriod
        } else if !hasSymbol && hasNumber {
            accepted = isLetter || isDigit
        } else if hasSymbol && !hasNumber {
            let isSymbol = !isLetter && !isDigit && code > 32
            accepted = isLetter || isSymbol || (isPhonetic && isSpace)
        } else {
            let isSymbol = !isLetter && !isDigit && code > 32
            accepted = isLetter || isDigit || isSymbol || (isPhonetic && isSpace)
        }

        if accepted {
            let insertChar = (isShiftOn && !isSpace) ? charStr.uppercased() : charStr

            // Stroke5 (WB) 5-character limit: discard the 6th character (spec §5)
            if searchServer?.isWBTable == true && mComposing.count >= 5 { return }

            // Append to composing buffer first, then insert (so textDidChange check passes)
            mComposing += insertChar
            // iOS composing simulation: insert char inline (spec §12)
            isSelfUpdate = true
            textDocumentProxy.insertText(insertChar)
            isSelfUpdate = false
            composingLength += 1
            // WB: truncate query code to 5 characters
            updateCandidates()
        } else {
            // Not accepted: commit current candidate, then send char directly (spec §5)
            _ = pickHighlightedCandidate()
            let insertChar = isShiftOn ? charStr.uppercased() : charStr
            isSelfUpdate = true
            textDocumentProxy.insertText(insertChar)
            isSelfUpdate = false
            finishComposing()
        }

        consumeShiftAfterCharacter()
    }

    // MARK: - English Character Handling (spec §5 English Mode)

    private func handleEnglishCharacter(code: Int, char: Character) {
        let charStr    = String(char)
        let insertChar = isShiftOn ? charStr.uppercased() : charStr

        if char.isLetter {
            tempEnglishWord += insertChar
        } else {
            resetTempEnglishWord()
        }
        isSelfUpdate = true
        textDocumentProxy.insertText(insertChar)
        isSelfUpdate = false
        updateEnglishPrediction()
        consumeShiftAfterCharacter()
        updateShiftForAutoCap()
    }

    // MARK: - Backspace Handling (spec §5 handleBackspace — 6 cases)

    private func handleBackspace() {
        if mComposing.count > 1 {
            // Case 1: composing > 1 → remove last char from composing, then delete from document
            // (update mComposing BEFORE deleteBackward so textDidChange check passes)
            mComposing.removeLast()
            isSelfUpdate = true
            textDocumentProxy.deleteBackward()
            isSelfUpdate = false
            composingLength -= 1
            updateCandidates()
            showComposingPopup()

        } else if mComposing.count == 1 {
            // Case 2: composing == 1 → clear composing and force-remove from document (spec §5)
            clearComposing(force: true)

        } else if hasCandidatesShown && hasChineseSymbolCandidatesShown {
            // Case 4: Chinese punctuation list shown → hide it without deleting (spec §11,
            // intentional "cancel" gesture — same as Android).
            hasChineseSymbolCandidatesShown = false
            hasCandidatesShown  = false
            selectedCandidate   = nil
            mCandidateList      = []
            candidateBar.setCandidates([])

        } else if mEnglishOnly && !tempEnglishWord.isEmpty {
            // Case 5 (#78 Bug 1): English prediction word → delete last char and re-query.
            // Must run before the generic hasCandidatesShown branch so the English
            // delete path is actually reached when predictions are visible. Mirrors
            // Android (whose equivalent branches are gated by !mEnglishOnly).
            tempEnglishWord.removeLast()
            isSelfUpdate = true
            textDocumentProxy.deleteBackward()
            isSelfUpdate = false
            updateEnglishPrediction()

        } else if isBrowseOnlySuggestionList {
            // #78 Bug 2: composing empty, optional/browse-only list visible (related
            // phrases, or English predictions with empty tempEnglishWord). Dismiss the
            // stale bar AND perform the normal delete in one tap — do not slide into
            // the Chinese-punctuation list via clearSuggestions().
            dismissBrowseOnlySuggestionBar()
            isSelfUpdate = true
            textDocumentProxy.deleteBackward()
            isSelfUpdate = false

        } else if hasCandidatesShown {
            // Case 3 (residual): generic candidates with empty composing not matching any
            // browse-only category — use clearSuggestions so autoChineseSymbol may trigger.
            clearSuggestions()

        } else {
            // Case 6: no composing, no candidates → pass delete to text field
            textDocumentProxy.deleteBackward()
        }
    }

    // MARK: - Shift / CapsLock (spec §4 handleShift)
    // Three states matching Android: off → one-shot (on) → caps lock → off
    // Layout switching: normal ↔ _shift variant (mirrors Android toggleShift())

    private func handleShift() {
        if mCapsLock {
            // Caps lock → off
            mCapsLock = false
            isShiftOn = false
            applyShiftState()
        } else if isShiftOn {
            // One-shot → caps lock
            mCapsLock = true
            isShiftOn = true
            applyShiftState()
        } else {
            // Off → one-shot
            isShiftOn = true
            applyShiftState()
        }
    }

    /// Apply the current shift/capsLock state to the keyboard view (icon) and layout.
    private func applyShiftState() {
        // Update shift key icon (3 states)
        let state: KeyboardView.ShiftState = mCapsLock ? .capsLock : (isShiftOn ? .on : .off)
        keyboardView?.setShiftState(state)

        // Switch to shift layout variant when active, normal variant when off.
        // Mirrors Android mKeyboardSwitcher.toggleShift().
        // Only switch for layouts that have a _shift variant (abc, phonetic, etc.).
        let wantShift = isShiftOn || mCapsLock
        let base      = currentLayout.id.hasSuffix("_shift")
                        ? String(currentLayout.id.dropLast(6))  // already shifted → get base
                        : currentLayout.id
        let targetId  = wantShift ? "\(base)_shift" : base
        if targetId != currentLayout.id,
           let newLayout = LayoutLoader.load(targetId) {
            if isShiftKeyHeld {
                keyboardView?.previewLayout(newLayout)
                return
            }
            currentLayout = newLayout
            keyboardView?.setLayout(currentLayout)
            applyHeight()
        } else if !wantShift {
            keyboardView?.previewLayout(nil)
        }
    }

    private func setShift(_ on: Bool) {
        isShiftOn = on
        applyShiftState()
    }

    private func consumeShiftAfterCharacter() {
        if ShiftResetPolicy.shouldResetAfterCharacter(isShiftOn: isShiftOn,
                                                      capsLock: mCapsLock,
                                                      shiftKeyIsHeld: isShiftKeyHeld) {
            setShift(false)
        } else if isShiftOn && !mCapsLock && isShiftKeyHeld {
            shiftHoldModifiedCharacter = true
        }
    }

    private func releaseShiftKey() {
        isShiftKeyHeld = false
        if ShiftResetPolicy.shouldResetAfterShiftRelease(capsLock: mCapsLock,
                                                         holdModifiedCharacter: shiftHoldModifiedCharacter) {
            setShift(false)
        } else {
            applyShiftState()
        }
        shiftHoldModifiedCharacter = false
    }

    private func clearShiftState() {
        guard isShiftOn || mCapsLock else { return }
        isShiftOn = false
        mCapsLock = false
        isShiftKeyHeld = false
        shiftHoldModifiedCharacter = false
        applyShiftState()
    }

    private func handleClose() {
        clearComposing(force: false)
        dismissKeyboard()
    }

    private func updateShiftForAutoCap() {
        // Auto-capitalization only applies in English mode.
        // In Chinese/phonetic mode the composing codes are always lowercase —
        // auto-shifting them to uppercase breaks all DB lookups.
        guard mEnglishOnly else { return }
        guard !isShiftOn, !mCapsLock else { return }
        // User-toggleable per §8.7 英文鍵盤 → 首字自動大寫
        guard LIMEPreferenceManager.shared.autoCap else { return }
        // iOS provides autocapitalizationType directly (spec §2 iOS note)
        guard let capType = textDocumentProxy.autocapitalizationType,
              capType == .sentences || capType == .allCharacters || capType == .words else { return }
        let before = textDocumentProxy.documentContextBeforeInput ?? ""
        if shouldAutoCapitalize(before: before) {
            isShiftOn = true
            applyShiftState()
        }
    }

    /// LatinIME-style sentence-boundary detection (see docs/ENGLISH_KB.md §1).
    /// Fires at start-of-document, after a newline/paragraph, or after
    /// `. ` / `! ` / `? `, allowing trailing closing quotes/parens before the
    /// space, and skipping `(\w\.){2,}` patterns ("U.S.", "e.g.") plus a small
    /// allowlist of common single-word abbreviations ("Mr.", "Dr.", …).
    internal func shouldAutoCapitalize(before: String) -> Bool {
        EnglishKeyboardPolicy.shouldAutoCapitalize(before: before)
    }

    /// True if `beforeDot` looks like the tail of an abbreviation, i.e. the
    /// trailing `.` is part of an abbreviation rather than a sentence end.
    /// Matches LatinIME's `(\w\.){2,}` (covers "U.S.", "e.g.", "i.e.") plus a
    /// small allowlist of common single-word abbreviations.
    internal func isAbbreviationBeforeDot(_ beforeDot: Substring) -> Bool {
        !EnglishKeyboardPolicy.shouldAutoCapitalize(before: "\(beforeDot). ")
    }

    internal func shouldInsertPeriodForDoubleSpace(before: String) -> Bool {
        EnglishKeyboardPolicy.shouldInsertPeriodForDoubleSpace(before: before)
    }

    // MARK: - iOS Composing Simulation (spec §12)

    /// Clear composing state. `force=true` removes inline chars from the document.
    private func clearComposing(force: Bool) {
        if force {
            isSelfUpdate = true
            for _ in 0..<composingLength { textDocumentProxy.deleteBackward() }
            isSelfUpdate = false
        }
        mComposing      = ""
        composingLength = 0
        selectedCandidate = nil
        candidateBar.setComposingCode("")
        hideComposingPopup()
        clearSuggestions()  // mirrors Android: checks hasCandidatesShown before resetting
    }

    /// Cancel composing without touching the document (cursor moved externally).
    private func cancelComposing() {
        hideLimeToast()
        mComposing       = ""
        composingLength  = 0
        selectedCandidate = nil
        mCandidateList   = []
        hasCandidatesShown = false
        hasChineseSymbolCandidatesShown = false
        candidateBar.setComposingCode("")
        candidateBar.setIdleToolsSuppressed(false)
        candidateBar.setCandidates([])
        hideComposingPopup()
    }

    /// Cancel from the candidate-bar dismiss button, including the inline
    /// composing text inserted into the host document by the iOS simulation.
    private func cancelActiveComposingFromCandidateDismiss() {
        hideLimeToast()
        let inlineComposingLength = max(composingLength, mComposing.count)
        if inlineComposingLength > 0 {
            isSelfUpdate = true
            for _ in 0..<inlineComposingLength { textDocumentProxy.deleteBackward() }
            isSelfUpdate = false
        }
        cancelComposing()
    }

    /// Reset composing tracking after text has been committed or cleared.
    private func finishComposing() {
        mComposing       = ""
        composingLength  = 0
        selectedCandidate = nil
        hasCandidatesShown = false
        candidateBar.setIdleToolsSuppressed(false)
        hideComposingPopup()
    }

    // MARK: - Candidate Flow (spec §6 updateCandidates)

    private func updateCandidates() {
        guard mPredictionOn, let ss = searchServer, !mComposing.isEmpty else {
            clearSuggestions(); return
        }
        candidateBar.setIdleToolsSuppressed(true)
        // PROFILING: BEGIN — Stroke span (entry → stage-1 render). See docs/IOS_PROFILING.md.
        let strokeID = Prof.newID()
        Prof.begin("Stroke", id: strokeID)
        Prof.event("UpdateCandidates", "len=\(mComposing.count)")
        // PROFILING: END
        // Show composing popup IMMEDIATELY (don't wait for async DB query).
        // Mirrors Android: the popup is based on keyToKeyname(mComposing), independent of candidate results.
        // PROFILING: BEGIN — T1 composing popup latency.
        let popupID = Prof.newID()
        Prof.begin("ComposingPopup", id: popupID)
        // PROFILING: END
        showComposingPopup()
        // PROFILING: BEGIN — T1 close.
        Prof.end("ComposingPopup", id: popupID)
        // PROFILING: END
        // On composing restart (length == 1): clear runtime suggestion context (spec §6)
        if mComposing.count == 1 && smartChineseInput {
            ss.clearSuggestionContext()
        }
        // WB/Stroke5: query with at most 5 characters (spec §5)
        let code = ss.isWBTable ? String(mComposing.prefix(5)) : mComposing
        currentSearchID &+= 1
        let sid = currentSearchID

        let capturedEnableEmoji = enableEmoji
        let capturedEmojiPosition = enableEmojiPosition
        DispatchQueue.global(qos: .userInteractive).async { [weak self] in
            // Stage 1: quick fetch (INITIAL_RESULT_LIMIT). Shows candidates fast.
            // PROFILING: BEGIN — T2 stage-1 DB query (background).
            let q1ID = Prof.newID()
            Prof.begin("DBQueryStage1", id: q1ID)
            // PROFILING: END
            var results = ss.getMappingByCode(code, isSoftKeyboard: true)
            // PROFILING: BEGIN — T2 stage-1 query close.
            Prof.end("DBQueryStage1", id: q1ID)
            // PROFILING: END
            if !results.isEmpty, capturedEnableEmoji {
                results = ss.injectEmoji(into: results, insertAt: capturedEmojiPosition)
            }
            let wasTruncated = results.contains(where: { $0.isHasMoreMarkRecord })
            DispatchQueue.main.async { [weak self] in
                guard let self = self, self.currentSearchID == sid else {
                    // PROFILING: BEGIN — stale-stroke cancellation path.
                    Prof.event("StrokeCancelled")
                    Prof.end("Stroke", id: strokeID)
                    // PROFILING: END
                    return
                }
                // P2 (see docs/IOS_MISS_KEY.md): defer the heavy candidate-bar reload
                // one runloop tick so UIKit can dispatch any queued touchDown/touchUp
                // events before the reload locks the main thread. Re-checks stale in
                // case the user typed again during the hop.
                DispatchQueue.main.async { [weak self] in
                    guard let self = self, self.currentSearchID == sid else {
                        // PROFILING: BEGIN — stale-stroke cancellation path (deferred).
                        Prof.event("StrokeCancelled")
                        Prof.end("Stroke", id: strokeID)
                        // PROFILING: END
                        return
                    }
                    // PROFILING: BEGIN — T2 candidate bar reload (main thread).
                    let reloadID = Prof.newID()
                    Prof.begin("CandidateReload", id: reloadID)
                    // PROFILING: END
                    results.isEmpty ? self.clearSuggestions() : self.setSuggestions(results)
                    // PROFILING: BEGIN — T2 reload close + Stroke close.
                    Prof.end("CandidateReload", id: reloadID)
                    Prof.end("Stroke", id: strokeID)
                    // PROFILING: END
                }
            }
            // Stage 2: full fetch (FINAL_RESULT_LIMIT). Upgrades bar without scroll reset.
            // Only runs when stage 1 was truncated (see docs/TWO_STAGE_CANDI.md).
            guard wasTruncated else { return }
            // PROFILING: BEGIN — T3 stage-2 full DB query.
            let q2ID = Prof.newID()
            Prof.begin("DBQueryStage2", id: q2ID)
            // PROFILING: END
            var fullResults = ss.getMappingByCode(code, isSoftKeyboard: true, getAllRecords: true)
            // PROFILING: BEGIN — T3 stage-2 query close.
            Prof.end("DBQueryStage2", id: q2ID)
            // PROFILING: END
            if !fullResults.isEmpty, capturedEnableEmoji {
                fullResults = ss.injectEmoji(into: fullResults, insertAt: capturedEmojiPosition)
            }
            // Stage 2 must land AFTER the stage-1 bar reload. Stage 1 uses a
            // nested DispatchQueue.main.async (P2 deferral, see IOS_MISS_KEY.md);
            // if stage 2 uses a single async, M3 can fire before stage 1's inner
            // M2 — applyFullCandidateResults then bails on hasCandidatesShown and
            // the `…` sentinel is left in the bar (see docs/#77_ISSUE.md).
            // Double-dispatch stage 2 so M3 is always enqueued after M2 (FIFO).
            DispatchQueue.main.async { [weak self] in
                DispatchQueue.main.async { [weak self] in
                    guard let self = self else { return }
                    // PROFILING: BEGIN — T3 candidate swap (main thread).
                    let swapID = Prof.newID()
                    Prof.begin("CandidateSwap", id: swapID)
                    // PROFILING: END
                    self.applyFullCandidateResults(fullResults, sid: sid)
                    // PROFILING: BEGIN — T3 swap close.
                    Prof.end("CandidateSwap", id: swapID)
                    // PROFILING: END
                }
            }
        }
    }

    /// Swap the full (un-truncated) candidate list into the bar after the background
    /// follow-up fetch completes. Preserves scroll position via appendCandidates.
    ///
    /// The `hasCandidatesShown` guard is intentionally omitted: under the
    /// double-dispatch order (see updateCandidates), stage 2 normally lands
    /// after the stage-1 inner block sets `hasCandidatesShown = true`, but if
    /// it ever lands first, the full list must still be shown — stage 1 and
    /// stage 2 are by construction mutually consistent for one `sid`. We set
    /// `hasCandidatesShown` here as well so downstream code stays consistent.
    private func applyFullCandidateResults(_ full: [Mapping], sid: UInt64) {
        guard currentSearchID == sid,
              !isShowingRelatedPhrases,
              !hasChineseSymbolCandidatesShown,
              !mEnglishOnly,
              !full.isEmpty else { return }
        mCandidateList = full
        hasCandidatesShown = true
        let idx: Int
        if full.count > 1 && (full[1].isExactMatchToCodeRecord || full[1].isPartialMatchToCodeRecord) {
            idx = 1
        } else if let first = full.first,
                  first.isComposingCodeRecord || first.isRuntimeBuiltPhraseRecord {
            idx = 0
        } else {
            idx = -1
        }
        selectedCandidate = (idx >= 0) ? full[idx] : nil
        candidateBar.appendCandidates(full, selectedIndex: idx)
        // If the expanded grid is currently visible for normal candidates,
        // reload it so the truncated stage-1 list (with `…` sentinel) is
        // replaced by the full stage-2 list. Related-phrase expansion already
        // fetches with getAllRecords: true on demand and is unaffected.
        if isExpandedCandidatesVisible {
            expandedCandidates = full
            expandedSelectedIndex = idx
            reloadExpandedCandidates()
            updateExpandedScrollThumb()
        }
    }

    /// Set candidate list and default selection (spec §6 Default Candidate Selection).
    private func setSuggestions(_ list: [Mapping]) {
        hideExpandedCandidates()
        mCandidateList     = list
        hasCandidatesShown = !list.isEmpty
        isShowingRelatedPhrases = false

        // Normal-candidate selection seed (mirrors Android CandidateView.setSuggestions,
        // CandidateView.java:1182–1196). Associated lists (related phrases, punctuation,
        // English) bypass this method entirely and stay at selectedIndex = -1.
        let selectedIdx: Int
        if list.count > 1 && (list[1].isExactMatchToCodeRecord || list[1].isPartialMatchToCodeRecord) {
            selectedIdx = 1
        } else if let first = list.first,
                  first.isComposingCodeRecord || first.isRuntimeBuiltPhraseRecord {
            selectedIdx = 0
        } else {
            selectedIdx = -1
        }
        selectedCandidate = (selectedIdx >= 0) ? list[selectedIdx] : nil
        candidateBar.setIdleToolsSuppressed(false)

        // Mixed mode (spec §6, Android CandidateView.setComposingText):
        // - Show keyname popup ABOVE the candidate bar (e.g. "日土" for Dayi "dj")
        // - Keep the composing code record VISIBLE in the candidate bar at index 0
        //   so the user can tap it to commit the raw English letters.
        showComposingPopup()
        showCandidates(list, selectedIndex: selectedIdx)    // include composing code record
    }

    /// Show candidates in the bar.
    ///
    /// - Parameter selectedIndex: the initial highlighted cell. Pass `-1` (default) for
    ///   associated lists (related phrases, Chinese punctuation, English suggestions) —
    ///   matches Android's "no default selection" rule for those record types.
    private func showCandidates(_ list: [Mapping], selectedIndex: Int = -1) {
        candidateBar.setCandidates(list, selectedIndex: selectedIndex)
    }

    private func showExpandedCandidates(_ candidates: [Mapping], selectedIndex: Int = -1) {
        expandedCandidates = candidates
        expandedSelectedIndex = (selectedIndex >= 0 && selectedIndex < candidates.count) ? selectedIndex : -1
        expandedScrollView?.setContentOffset(.zero, animated: false)
        reloadExpandedCandidates()
        expandedCandidatesPanel?.isHidden = false
        // Hide both the key grid and the candidate bar while the expanded panel is shown.
        // The panel overlays from candidateBar.topAnchor; if the bar stays visible its
        // items bleed through the transparent panel (the partially-clipped last item
        // shows as a ghost on row 1 even though it is correctly on row 2 in the panel).
        keyboardView?.isHidden = true
        candidateBar.isHidden = true
        isExpandedCandidatesVisible = true
        updateExpandedScrollThumb()
    }

    private func hideExpandedCandidates() {
        guard isExpandedCandidatesVisible else { return }
        expandedCandidatesPanel?.isHidden = true
        expandedScrollThumb?.isHidden = true
        keyboardView?.isHidden = false
        candidateBar.isHidden = false
        isExpandedCandidatesVisible = false
        candidateBar.setChevronExpanded(false)
    }

    private func reloadExpandedCandidates() {
        guard let contentView = expandedContentView else { return }

        // Remove all previous subviews
        contentView.subviews.forEach { $0.removeFromSuperview() }

        let pal = KeyboardPalette.palettes[max(0, min(resolvedKeyboardTheme, KeyboardPalette.palettes.count - 1))]
        let t = resolvedKeyboardTheme
        let systemStyle = traitCollection.userInterfaceStyle
        let adaptedCandiText: UIColor
        if systemStyle == .dark {
            adaptedCandiText = KeyboardPalette.iosDark(.label)
        } else if t == 1 {
            adaptedCandiText = KeyboardPalette.iosLight(.label)
        } else {
            adaptedCandiText = pal.candiText
        }
        // Themes 0 and 1 adapt pill + text to the system backdrop; other themes use their fixed palette colour.
        let highlightColor: UIColor
        if t == 0 || t == 1 {
            highlightColor = systemStyle == .dark
                ? LayoutMetrics.CandidateBar.darkThemePill
                : KeyboardPalette.iosLight(.systemBackground)
        } else {
            highlightColor = pal.candiHighlight
        }
        let panelWidth = view.bounds.width > 0 ? view.bounds.width : UIScreen.main.bounds.width
        // Row 1 mirrors the collapsed bar exactly: full `candidateBarHeight`
        // tall, glyph biased down by `composingStripHeight/2` so it clears
        // the keyname strip overlay at the top.
        // Rows 2+ have no keyname above them, so the reserved strip area
        // would be pure whitespace. Shrink them to `candidateBarHeight -
        // composingStripHeight` and drop the bias — the glyph then sits
        // symmetrically centered in the shorter row (≈7pt iPhone / ≈10pt
        // iPad padding above and below).
        let hPad:         CGFloat = 0
        let vPad:         CGFloat = 0
        let stripH:       CGFloat = candidateBar.composingStripHeight
        let firstRowH:    CGFloat = candidateBarHeight
        let restRowH:     CGFloat = max(0, candidateBarHeight - stripH)
        var rowH:         CGFloat = firstRowH
        var rowBias:      CGFloat = stripH / 2
        // Match CandidateBarView font sizing exactly: iPad = 26/22, iPhone = 22/16.
        // Use isOnPad (traitCollection-based) so compatibility-mode iPhone apps on iPad
        // use phone metrics consistently with CandidateBarView and KeyboardView.
        // IMPORTANT: composing-code records use PingFangTC-Regular (NOT monospaced
        // system) — same font as CandidateBarView.composingCodeFont — otherwise the
        // first item's glyph width differs and shifts the whole row horizontally.
        let onPad         = isOnPad
        let baseCandSize  = LayoutMetrics.ComposingPopup.candidateFontSize(isPad: onPad)
        let baseCompSize  = LayoutMetrics.ComposingPopup.composingCodeFontSize(isPad: onPad)
        let font          = UIFont.systemFont(ofSize: baseCandSize * candidateFontScale, weight: .regular)
        let composingFont = UIFont(name: "PingFangTC-Regular", size: baseCompSize * candidateFontScale)
            ?? UIFont.systemFont(ofSize: baseCompSize * candidateFontScale, weight: .regular)

        // Reserve the same leading zone as the collapsed bar's dismiss button on every row.
        let dismissZone = LayoutMetrics.CandidateBar.Chevron.buttonWidth(isPad: isOnPad) / 2
        var x: CGFloat = dismissZone + hPad
        var y: CGFloat = vPad
        var isFirstInRow = true

        for (i, mapping) in expandedCandidates.enumerated() {
            let isComposingCode = mapping.isComposingCodeRecord
            let btnFont = isComposingCode ? composingFont : font
            let text  = mapping.word

            // Build the button first and read its real intrinsicContentSize so the
            // layout matches CandidateBarView.makeCandidateButton exactly (same font,
            // same contentEdgeInsets of 10pt). Using NSString.size drifts slightly
            // vs. UIButton's actual width and pushes the last row-1 item to row 2.
            let btn = UIButton(type: .system)
            btn.setTitle(text, for: .normal)
            btn.titleLabel?.font = btnFont
            let cellHPad = LayoutMetrics.CandidateBar.candidateHPad
            // Width is determined by horizontal insets only (vertical insets
            // don't change intrinsicContentSize.width), so set H insets
            // first to read btnW. The vertical (rowBias) insets are
            // applied after the wrap decision so row 1 vs rows 2+ get
            // their correct bias.
            btn.setValue(NSValue(uiEdgeInsets: UIEdgeInsets(top: 0, left: cellHPad,
                                                            bottom: 0, right: cellHPad)),
                         forKey: "contentEdgeInsets")
            let btnW = btn.intrinsicContentSize.width

            // Every row reserves the same right-edge zone as the collapsed
            // bar: chevron button width + moreSep divider.
            let chevronZone = LayoutMetrics.CandidateBar.Chevron.buttonWidth(isPad: isOnPad)
            let rowMaxX = panelWidth - chevronZone - expandedSepWidth

            if !isFirstInRow {
                if x + btnW > rowMaxX {
                    // Wrap to next row. Advance by the OLD row's height,
                    // then switch to the shorter rows-2+ height with no
                    // strip-bias.
                    x = dismissZone + hPad
                    y += rowH
                    rowH = restRowH
                    rowBias = 0
                    isFirstInRow = true
                }
            }

            // Now apply the row-specific vertical bias.
            btn.setValue(NSValue(uiEdgeInsets: UIEdgeInsets(top: rowBias, left: cellHPad,
                                                            bottom: -rowBias, right: cellHPad)),
                         forKey: "contentEdgeInsets")

            // Candidate styling
            let isSelected = (i == expandedSelectedIndex && expandedSelectedIndex >= 0)
            btn.setTitleColor(
                isComposingCode && !isSelected
                    ? adaptedCandiText.withAlphaComponent(LayoutMetrics.CandidateBar.composingCodeDimAlpha)
                    : adaptedCandiText,
                for: .normal)
            btn.frame = CGRect(x: x, y: y, width: btnW, height: rowH)
            btn.tag = i
            btn.addTarget(self, action: #selector(expandedCandidateTapped(_:)), for: .touchUpInside)
            contentView.addSubview(btn)

            // Match CandidateBarView's pill geometry exactly: pill hugs the title
            // label (text width + padX*2), positioned at (cellHPad - padX) so it
            // aligns to the glyph rather than the full button frame. Mirrors
            // CandidateButton.layoutSubviews (cellHPad=10, padX=4, padY=2).
            if isSelected {
                let padX = LayoutMetrics.CandidateBar.pillPadX
                let padY = LayoutMetrics.CandidateBar.pillPadY
                let textW  = btnW - 2 * cellHPad
                let pillW  = textW + 2 * padX
                // Match CandidateButton.layoutSubviews exactly: pill hugs the
                // title label which UIKit sizes to font.lineHeight (not ceiled).
                let pillH  = min(rowH, btnFont.lineHeight + 2 * padY)
                let pillX  = cellHPad - padX
                // The label's vertical center is shifted by the FULL bias
                // (insets are top:+bias, bottom:-bias → content-rect center
                // moves by bias). Use rowBias (which is `stripH/2` for row 1,
                // 0 for rows 2+) so the pill tracks the glyph in either case.
                let pillY  = max(0, (rowH - pillH) / 2) + rowBias
                let pill = UIView(frame: CGRect(x: pillX, y: pillY,
                                                width: pillW, height: pillH))
                pill.backgroundColor = highlightColor
                pill.layer.cornerRadius = LayoutMetrics.CandidateBar.pillCornerRadius
                pill.layer.masksToBounds = true
                pill.isUserInteractionEnabled = false
                btn.insertSubview(pill, at: 0)
            }

            x += btnW
            isFirstInRow = false
        }

        // Drive scroll view content height via constraint
        let totalH = expandedCandidates.isEmpty ? 0 : (y + rowH + vPad)
        expandedContentHeightConstraint?.constant = totalH
        expandedScrollView?.layoutIfNeeded()
        updateExpandedScrollThumb()
    }

    private func updateExpandedScrollThumb() {
        guard let panel = expandedCandidatesPanel,
              let scrollView = expandedScrollView,
              let thumb = expandedScrollThumb else { return }
        panel.layoutIfNeeded()
        scrollView.layoutIfNeeded()

        let viewportHeight = scrollView.bounds.height
        let contentHeight = scrollView.contentSize.height
        let hasOverflow = isExpandedCandidatesVisible && contentHeight > viewportHeight + 1
        guard hasOverflow, viewportHeight > 0 else {
            thumb.isHidden = true
            return
        }

        let trackInset: CGFloat = 2
        let trackHeight = max(0, scrollView.bounds.height - 2 * trackInset)
        let thumbHeight = min(trackHeight,
                              max(expandedScrollThumbMinHeight,
                                  trackHeight * viewportHeight / contentHeight))
        let maxOffset = max(1, contentHeight - viewportHeight)
        let clampedOffset = min(max(scrollView.contentOffset.y, 0), maxOffset)
        let progress = clampedOffset / maxOffset
        let thumbTravel = max(0, trackHeight - thumbHeight)
        let thumbY = scrollView.frame.minY + trackInset + thumbTravel * progress
        let thumbX = scrollView.frame.maxX - expandedScrollThumbWidth - 2

        thumb.frame = CGRect(x: thumbX,
                             y: thumbY,
                             width: expandedScrollThumbWidth,
                             height: thumbHeight)
        thumb.isHidden = false
    }

    @objc private func expandedCandidateTapped(_ sender: UIButton) {
        let idx = sender.tag
        guard idx < expandedCandidates.count else { return }
        let mapping = expandedCandidates[idx]
        // The `…` sentinel is a UI control, not a real candidate. Defensive
        // guard against any path that lets it slip into expandedCandidates
        // (see docs/#77_ISSUE.md). Filtering in candidateBarViewDidRequestMore
        // should keep this from triggering; treat it as a no-op if it does.
        guard !mapping.isHasMoreMarkRecord else { return }
        fireHapticIfEnabled()
        hideExpandedCandidates()
        pickCandidateManually(mapping)
    }

    @objc private func collapseExpandedCandidates() {
        fireHapticIfEnabled()
        hideExpandedCandidates()
    }

    @objc private func dismissExpandedAndComposing() {
        fireHapticIfEnabled()
        hideExpandedCandidates()
        cancelActiveComposingFromCandidateDismiss()
    }

    // Stored haptic generator for the expanded-candidate panel chrome. See KeyboardView
    // for the rationale — the previous "build a new generator each call" pattern caused
    // cold-start latency and dropped touch events under fast input.
    private var hapticGenerator: UIFeedbackGenerator?
    private var lastHapticAt: CFTimeInterval = 0
    private let minHapticInterval: CFTimeInterval = 0.025

    private func rebuildHapticGenerator() {
        guard hasVibration else { hapticGenerator = nil; return }
        hapticGenerator = KeyboardView.makeHapticGenerator(for: vibrateLevel)
        hapticGenerator?.prepare()
    }

    /// Fires an impact haptic matching the current vibrateLevel, when vibrate preference
    /// is enabled. Used by keyboard-extension UI outside KeyboardView/CandidateBarView
    /// (e.g. the expanded-candidate collapse chevron).
    private func fireHapticIfEnabled() {
        guard hasVibration else { return }
        let now = CACurrentMediaTime()
        guard now - lastHapticAt >= minHapticInterval else { return }
        lastHapticAt = now
        if hapticGenerator == nil { rebuildHapticGenerator() }
        guard let gen = hapticGenerator else { return }
        if let impact = gen as? UIImpactFeedbackGenerator {
            impact.impactOccurred()
        } else if let sel = gen as? UISelectionFeedbackGenerator {
            sel.selectionChanged()
        }
        gen.prepare()
    }

    private func clearSuggestions() {
        hideExpandedCandidates()
        // Auto Chinese Symbol: when candidates disappear in Chinese mode, show punctuation (spec §11)
        if autoChineseSymbol && !mEnglishOnly && hasCandidatesShown && !hasChineseSymbolCandidatesShown {
            let punctuation = KeyboardViewController.chinesePunctuationMappings()
            if !punctuation.isEmpty {
                mCandidateList              = punctuation
                hasCandidatesShown          = true
                hasChineseSymbolCandidatesShown = true
                isShowingRelatedPhrases     = false
                selectedCandidate           = nil
                showCandidates(punctuation)
                return
            }
        }
        hasChineseSymbolCandidatesShown = false
        isShowingRelatedPhrases = false
        mCandidateList     = []
        hasCandidatesShown = false
        selectedCandidate  = nil
        candidateBar.setIdleToolsSuppressed(!mComposing.isEmpty)
        candidateBar.setCandidates([])
        // Keep the keyname popup visible while the user is still composing.
        // clearSuggestions() runs both when composing is fully cleared AND when
        // the DB returns zero matches during an ongoing composition — only the
        // former should tear down the popup.
        if mComposing.isEmpty {
            hideComposingPopup()
        }
    }

    private func keyname(_ code: String) -> String {
        searchServer?.keyToKeyname(code) ?? code
    }

    // MARK: - Composing Popup (mirrors Android mComposingTextPopup)

    /// Show the IM keyname as a window-attached bubble floating above the
    /// candidate bar. Attaches to `view.window` like keyPreviewView so the
    /// bubble overlays the host app's content area without growing the
    /// keyboard extension's height.
    /// Show the IM keyname in the permanent strip above the candidate bar.
    /// Only the label's text changes — the strip's height is fixed so the
    /// extension never grows/shrinks between compose and commit.
    private func showComposingPopup() {
        hideLimeToast()
        let raw = mComposing
        guard !raw.isEmpty, !mEnglishOnly else { hideComposingPopup(); return }

        let name = keyname(raw)
        // Allow keyname == raw: searchServer init is async (viewDidLoad spawns
        // setupDatabase on a bg queue), so the first keypresses after activation
        // hit keyname() while searchServer is nil and fall through to the raw
        // fallback. Showing the raw code is strictly better than a blank bar —
        // the user is in CJK mode (first guard already excluded English-only).
        let display = name.trimmingCharacters(in: .whitespaces).isEmpty ? raw : name
        candidateBar.composingText = display
        if let lbl = expandedComposingLabel {
            lbl.attributedText = CandidateBarView.attributedKeyname(
                display, baseFont: candidateBar.composingStripFont,
                color: lbl.textColor ?? .label)
        }
    }

    private func hideComposingPopup() {
        guard !limeToastState.isShowing else { return }
        candidateBar.composingText = nil
        expandedComposingLabel?.attributedText = nil
        expandedComposingLabel?.text = nil
    }

    // MARK: - Candidate Selection (spec §8)

    /// Pick the highlighted candidate. Returns true if a candidate was committed.
    @discardableResult
    private func pickHighlightedCandidate() -> Bool {
        guard let candidate = selectedCandidate else { return false }
        pickCandidateManually(candidate)
        return true
    }

    private func pickCandidateManually(_ candidate: Mapping) {
        let wasComposingCodeCommit = candidate.isComposingCodeRecord
        selectedCandidate = candidate
        commitTyped()
        if wasComposingCodeCommit {
            // Mixed-mode raw-English commit: no related phrases; clear the bar
            // (updateRelatedPhrase bails for composing-code records without clearing).
            clearSuggestions()
        } else {
            updateRelatedPhrase()
        }
    }

    // MARK: - Commit Flow (spec §8 commitTyped)

    private func commitTyped() {
        guard let candidate = selectedCandidate else { return }

        // Unicode surrogate / emoji: commit directly and force-clear (spec §8)
        let isEmoji = candidate.isEmojiRecord || containsEmojiSurrogatePair(candidate.word)

        // iOS composing simulation: delete composing chars, then insert word (spec §12 step 5)
        // isSelfUpdate suppresses textDidChange composing-integrity check during our own writes
        isSelfUpdate = true
        for _ in 0..<composingLength { textDocumentProxy.deleteBackward() }
        composingLength = 0   // already deleted; clearComposing(force:true) must not delete again

        // Han conversion: iOS uses CFStringTransform (spec §8 step 3)
        var wordToCommit = candidate.word
        if hanConvertOption == 1 {
            // Traditional → Simplified
            let mutable = NSMutableString(string: wordToCommit)
            CFStringTransform(mutable, nil, "Hant-Hans" as CFString, false)
            wordToCommit = mutable as String
        } else if hanConvertOption == 2 {
            // Simplified → Traditional
            let mutable = NSMutableString(string: wordToCommit)
            CFStringTransform(mutable, nil, "Hans-Hant" as CFString, false)
            wordToCommit = mutable as String
        }

        // Commit the word (spec §8 step 4)
        textDocumentProxy.insertText(wordToCommit)
        isSelfUpdate = false

        // Continuous typing check (spec §8 step 6)
        let codeLen = searchServer?.getRealCodeLength(
            mapping: candidate, composing: mComposing) ?? min(candidate.code.count, mComposing.count)

        // Emoji or WB/punctuation: force-clear after commit (spec §8)
        let forceClearAfterCommit = isEmoji
            || candidate.isChinesePunctuationRecord
            || (searchServer?.isWBTable == true)

        if mComposing.count > candidate.code.count && !forceClearAfterCommit {
            // Remaining composing code → re-establish inline and re-query
            var remaining = String(mComposing.dropFirst(min(codeLen, mComposing.count)))
            if remaining.hasPrefix(" ") { remaining = String(remaining.dropFirst()) }
            mComposing      = remaining
            composingLength = remaining.count
            if !remaining.isEmpty {
                isSelfUpdate = true
                textDocumentProxy.insertText(remaining)
                isSelfUpdate = false
                updateCandidates()
                showComposingPopup()
                // Buffer for LD learning (spec §5 Continuous Typing)
                if learnPhrase { searchServer?.addLDPhrase(candidate, ending: false) }
                // Track LD composing buffer (spec §5)
                if LDComposingBuffer.isEmpty { LDComposingBuffer = candidate.word }
                else { LDComposingBuffer += candidate.word }
                return
            }
        }

        // Post-commit (spec §8 step 7)
        committedCandidate = candidate
        if forceClearAfterCommit {
            clearComposing(force: true)
            LDComposingBuffer = ""
            if learnPhrase { searchServer?.addLDPhrase(nil, ending: true) }
        } else {
            finishComposing()
            // Signal LD learning end if buffer had accumulated
            if !LDComposingBuffer.isEmpty {
                if learnPhrase { searchServer?.addLDPhrase(candidate, ending: true) }
                LDComposingBuffer = ""
            }
        }
        searchServer?.learnRelatedPhraseAndUpdateScore(candidate)
        // Record committed candidate + its code for runtime phrase suggestion cross-check (spec §6)
        if smartChineseInput { searchServer?.addToSuggestionContext(candidate, code: candidate.code) }

        // Reverse lookup: show committed word's codes in the configured IM strip (spec §8, §13)
        let imKey = "\(activeIM)_im_reverselookup"
        let notifyEnabled = sharedDefaults?.object(forKey: "reverse_lookup_notify") as? Bool ?? true
        if notifyEnabled,
           let lookupTable = sharedDefaults?.string(forKey: imKey),
           lookupTable != "none", !lookupTable.isEmpty,
           let ss = searchServer {
            let word = candidate.word
            DispatchQueue.global(qos: .background).async { [weak self] in
                guard let result = ss.getCodeListStringFromWord(word, usingTable: lookupTable),
                      !result.isEmpty else { return }
                DispatchQueue.main.async { self?.showLimeToast(result) }
            }
        }
    }

    // MARK: - Related Phrase Display (spec §8 updateRelatedPhrase)

    private func updateRelatedPhrase() {
        guard let committed = committedCandidate,
              !committed.word.isEmpty,
              !committed.isEmojiRecord,
              !committed.isChinesePunctuationRecord,
              !committed.isComposingCodeRecord,   // mixed-mode raw-code commit has no related phrases
              let ss = searchServer else { return }

        // Clear stale composing candidates immediately so the bar doesn't linger.
        // Only when no remaining composing; if commitTyped() left a partial buffer,
        // updateCandidates() owns the bar.
        if mComposing.isEmpty {
            mCandidateList          = []
            hasCandidatesShown      = false
            isShowingRelatedPhrases = false
            selectedCandidate       = nil
            candidateBar.setCandidates([])
            candidateBar.setComposingCode("")
        }

        let word = committed.word
        DispatchQueue.global(qos: .userInteractive).async { [weak self] in
            // Always fetch the full related list — there is no stage-2 upgrade
            // for related phrases, so a truncated fetch would leave the `…`
            // sentinel stuck in the bar (see docs/#77_ISSUE.md fix 7).
            let related = ss.getRelatedByWord(word, getAllRecords: true)
            DispatchQueue.main.async { [weak self] in
                guard let self = self, self.mComposing.isEmpty else { return }
                if related.isEmpty {
                    // spec §8 step 6: no related results → nil committedCandidate, clear bar
                    self.committedCandidate = nil
                    self.clearSuggestions()
                } else {
                    self.mCandidateList         = related
                    self.hasCandidatesShown     = true
                    self.isShowingRelatedPhrases = true
                    self.selectedCandidate      = related.first
                    self.showCandidates(related)
                }
            }
        }
    }

    // MARK: - English Prediction (spec §7 — iOS: UITextChecker)

    private func updateEnglishPrediction() {
        guard englishPredictionOn else { return }
        guard !tempEnglishWord.isEmpty else { clearSuggestions(); return }
        let word = tempEnglishWord
        // Validate cursor context (spec §7) — read documentContext on main thread
        let beforeCursor = textDocumentProxy.documentContextBeforeInput ?? ""
        guard beforeCursor.hasSuffix(word) else { return }

        DispatchQueue.global(qos: .userInteractive).async { [weak self] in
            guard let self = self else { return }
            let range = NSRange(location: 0, length: (word as NSString).length)
            let completions = self.textChecker.completions(
                forPartialWordRange: range, in: word, language: "en_US") ?? []
            var mappings: [Mapping] = completions.prefix(20).map { suggestion in
                Mapping(id: 0, code: word, word: suggestion,
                        score: 0, baseScore: 0, recordType: Mapping.RecordType.englishSuggestion)
            }
            // Emoji injection for English predictions (spec §6 step 5, §7)
            if !mappings.isEmpty, let ss = self.searchServer, self.enableEmoji {
                mappings = ss.injectEnglishEmoji(into: mappings, word: word,
                                          insertAt: self.enableEmojiPosition)
            }
            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                if mappings.isEmpty { self.clearSuggestions() }
                else {
                    self.mCandidateList    = mappings
                    self.hasCandidatesShown = true
                    self.selectedCandidate = mappings.first
                    self.showCandidates(mappings)
                }
            }
        }
    }

    private func resetTempEnglishWord() { tempEnglishWord = "" }

    /// Commit an English suggestion: insert only the untyped suffix + space (spec §8 Path 3).
    private func commitEnglishSuggestion(_ word: String) {
        let suffix = word.count > tempEnglishWord.count
            ? String(word.dropFirst(tempEnglishWord.count)) : ""
        textDocumentProxy.insertText(suffix + " ")
        resetTempEnglishWord()
        clearSuggestions()
    }

    // MARK: - Mode Switching (spec §10)

    /// Toggle Chinese ↔ English mode (spec §10 switchChiEng).
    private func switchChiEng(toEnglish: Bool) {
        dismissPopupKeyboard()
        if isSymbolMode { exitSymbolMode() }
        clearShiftState()
        if toEnglish {
            cancelActiveComposingFromCandidateDismiss()
        } else {
            clearComposing(force: false)
        }
        mEnglishOnly = toEnglish
        // Persist language mode if setting is enabled (spec §15)
        if mPersistentLanguageMode {
            sharedDefaults?.set(toEnglish, forKey: "persisted_english_mode")
        }
        clearSuggestions()
        resetTempEnglishWord()
        let layoutName = toEnglish ? (numberRowInEnglish ? "lime_english_number" : "lime_english") : resolvedLayoutId(for: activeIM)
        if let loaded = LayoutLoader.load(layoutName) { currentLayout = loaded }
        keyboardView.setLayout(currentLayout)
        applyHeight()
    }

    /// Cycle to next/previous LIME-internal IM (spec §10 switchToNextActivatedIM).
    private func switchToNextActivatedIM(forward: Bool) {
        guard !activatedIMs.isEmpty, let ss = searchServer else { return }
        dismissPopupKeyboard()
        let count = activatedIMs.count
        activeIMIndex = forward
            ? (activeIMIndex + 1) % count
            : (activeIMIndex - 1 + count) % count
        let im = activatedIMs[activeIMIndex]
        activeIM = im.tableNick.isEmpty ? "phonetic" : im.tableNick
        // Persist last-used IM (mirrors Android mLIMEPref.setActiveIM(), key "keyboard_list")
        sharedDefaults?.set(activeIM, forKey: "keyboard_list")

        // Clear composing and candidates before switching
        clearShiftState()
        clearComposing(force: false)
        LDComposingBuffer = ""

        // Reconfigure SearchServer for the new IM
        let caps = ss.detectIMCapabilities(tableName: activeIM)
        ss.setTableName(activeIM, hasNumberMapping: caps.hasNumber, hasSymbolMapping: caps.hasSymbol)
        ss.setPhoneticKeyboardType(phoneticKeyboardType)
        refreshImKeys()

        // Update keyboard layout to match the new IM if available
        let preferredLayout = resolvedLayoutId(for: activeIM)
        if let newLayout = LayoutLoader.load(preferredLayout), newLayout.id != currentLayout.id {
            currentLayout = newLayout
            keyboardView?.setLayout(currentLayout)
            applyHeight()
        }
        showLimeToast(displayName(for: im))
    }

    // MARK: - Symbol Keyboard (spec §10)

    /// Enter symbol keyboard mode (spec §10 switchToSymbol).
    private func switchToSymbol() {
        guard !isSymbolMode else { exitSymbolMode(); switchChiEng(toEnglish: true); return }
        dismissPopupKeyboard()
        clearShiftState()
        isSymbolMode       = true
        preSymbolEnglish   = mEnglishOnly
        mEnglishOnly       = true   // disable CJK composing while in symbol mode
        symbolPageIndex    = 0
        preSymbolLayout    = currentLayout
        // Resolve IM-specific symbol layout (mirrors Android KeyboardConfig.symbolkb).
        // Android stores "symbols"/"symbols_shift" as resource refs — map these to iOS JSON IDs.
        // English mode always uses the generic symbols1.
        if !preSymbolEnglish {
            let kbCode = activatedIMs.first(where: { $0.tableNick == activeIM })?.keyboardId ?? ""
            let cfg    = kbCode.isEmpty ? nil : searchServer?.getKeyboardConfig(kbCode)
            // Map Android resource names → iOS JSON layout IDs
            func resolveSymId(_ id: String) -> String {
                switch id {
                case "symbols", "symbols_shift": return "symbols1"
                default:                         return id
                }
            }
            let dbBase  = resolveSymId(cfg?.symbolkb ?? "")
            let dbShift = resolveSymId(cfg?.symbolshiftkb ?? "")
            if !dbBase.isEmpty, LayoutLoader.load(dbBase) != nil {
                // Use dbShift as page 2 only if it is distinct from dbBase and loadable.
                // Android always stores symbolshiftkb = "symbols_shift" which resolves to
                // "symbols1" (same as symbolkb), so we fall back to "symbols2" in that case.
                let page2 = (dbShift != dbBase && !dbShift.isEmpty && LayoutLoader.load(dbShift) != nil)
                    ? dbShift : "symbols2"
                symbolLayouts = [dbBase, page2, "symbols3"]
            } else {
                symbolLayouts = ["symbols1", "symbols2", "symbols3"]
            }
        } else {
            symbolLayouts = ["symbols1", "symbols2", "symbols3"]
        }
        clearComposing(force: false)
        loadSymbolLayout(page: 0)
    }

    /// Cycle through symbol keyboard pages (spec §10 KEYCODE_SWITCH_SYMBOL_KEYBOARD).
    private func cycleSymbolPage() {
        guard isSymbolMode else { switchToSymbol(); return }
        dismissPopupKeyboard()
        symbolPageIndex = (symbolPageIndex + 1) % symbolLayouts.count
        loadSymbolLayout(page: symbolPageIndex)
    }

    /// Load a symbol keyboard layout page.
    private func loadSymbolLayout(page: Int) {
        dismissPopupKeyboard()
        clearShiftState()
        let id = symbolLayouts[page]
        let layout = LayoutLoader.load(id) ?? currentLayout
        currentLayout = layout
        keyboardView?.setLayout(layout)
        applyHeight()
    }

    /// Exit symbol mode and restore the previous keyboard layout.
    private func exitSymbolMode() {
        guard isSymbolMode else { return }
        dismissPopupKeyboard()
        clearShiftState()
        isSymbolMode = false
        mEnglishOnly = preSymbolEnglish
        let restore = preSymbolLayout ?? currentLayout
        currentLayout = restore
        keyboardView?.setLayout(restore)
        applyHeight()
    }

    // MARK: - Globe Key Visibility (spec §10)

    /// The keyboard key (code -3) is always visible — it is the primary dismiss affordance
    /// and doubles as the long-press globe menu entry point (spec §10).
    /// Only legacy code-200 globe keys (hardcoded fallback layouts) are conditionally shown.
    private func updateGlobeKeyVisibility() {
        let isPad = isOnPad
        // On iPad with an _ipad layout, globe is always visible (matches Apple's stock keyboard).
        let globeVisible = (isPad && currentLayout.id.contains("_ipad")) || needsInputModeSwitchKey
        keyboardView?.setGlobeKeyVisible(globeVisible)
    }

    // MARK: - Emoji / Surrogate Pair Detection (spec §8)

    /// Returns true if the string contains a Unicode surrogate pair (emoji or extended CJK).
    private func containsEmojiSurrogatePair(_ word: String) -> Bool {
        word.unicodeScalars.contains { $0.value > 0xFFFF }
    }

    // MARK: - LIME Toast / Reverse Lookup Display (spec §8, §13)

    private func showLimeToast(_ message: String) {
        guard limeToastState.show(message), let text = limeToastState.message else { return }
        limeToastTimer?.invalidate()
        candidateBar.composingText = text
        if let lbl = expandedComposingLabel {
            lbl.attributedText = CandidateBarView.attributedKeyname(
                text, baseFont: candidateBar.composingStripFont,
                color: lbl.textColor ?? .label)
        }
        limeToastTimer = Timer.scheduledTimer(withTimeInterval: 1.5, repeats: false) { [weak self] _ in
            self?.hideLimeToast()
        }
    }

    private func hideLimeToast() {
        limeToastTimer?.invalidate()
        limeToastTimer = nil
        guard limeToastState.isShowing else { return }
        limeToastState.hide()
        candidateBar.composingText = nil
        expandedComposingLabel?.attributedText = nil
        expandedComposingLabel?.text = nil
    }

    // MARK: - Chinese Punctuation List (spec §11)

    /// Standard Chinese punctuation set shown after a commit when autoChineseSymbol is on.
    static func chinesePunctuationMappings() -> [Mapping] {
        let symbols = ["，", "。", "、", "；", "：", "？", "！",
                       "「", "」", "『", "』", "【", "】", "〔", "〕",
                       "（", "）", "《", "》", "〈", "〉",
                       "…", "——", "～", "·", "※",
                       "\u{201C}", "\u{201D}", "\u{2018}", "\u{2019}"]
        return symbols.map {
            Mapping(id: 0, code: "", word: $0, score: 0, baseScore: 0,
                    recordType: Mapping.RecordType.chinesePunctuation)
        }
    }

    // MARK: - Space Key Gestures (spec §10)
    // Space-key gestures (swipe left/right and long-press) are wired directly in
    // KeyboardView.makeKeyButton — no setup needed here.
    // MARK: - Popup Keyboard state (must be in main class, not extension)
    var currentPopupView: PopupKeyboardView?

}

// MARK: - KeyboardViewDelegate
extension KeyboardViewController: KeyboardViewDelegate {

    func keyboardView(_ view: KeyboardView, didPress keyDef: KeyDef) {
        hideLimeToast()
        if emojiPanelView?.handleSearchKey(code: keyDef.code) == true {
            return
        }
        if keyDef.code == LimeKeyCode.shift.rawValue {
            let wasShiftKeyHeld = isShiftKeyHeld
            isShiftKeyHeld = true
            guard ShiftPressPolicy.shouldHandleShiftPress(wasShiftKeyHeld: wasShiftKeyHeld) else {
                return
            }
            shiftHoldModifiedCharacter = false
        }
        if let shiftedCode = shiftedHeldPrimaryCode(for: keyDef) {
            resetMultiTap()
            onKey(primaryCode: shiftedCode)
            return
        }
        if keyDef.codes.count > 1 {
            handleMultiTap(keyDef)
        } else {
            resetMultiTap()
            onKey(primaryCode: keyDef.code)
        }
    }

    func keyboardView(_ view: KeyboardView, didRelease keyDef: KeyDef) {
        if keyDef.code == LimeKeyCode.shift.rawValue {
            releaseShiftKey()
        }
    }

    func keyboardView(_ view: KeyboardView, didUpdateShiftHoldActive active: Bool) {
        if active {
            isShiftKeyHeld = true
        } else {
            releaseShiftKey()
        }
    }

    private func shiftedHeldPrimaryCode(for keyDef: KeyDef) -> Int? {
        guard isShiftOn,
              isShiftKeyHeld,
              keyDef.longPressCode != 0,
              keyDef.longPressCode != LimeKeyCode.keyboardOptionsMenu.rawValue,
              keyDef.popupKeyboard.isEmpty,
              keyDef.code > 0 else {
            return nil
        }
        return keyDef.longPressCode
    }

    private func handleMultiTap(_ keyDef: KeyDef) {
        let now = Date().timeIntervalSinceReferenceDate
        let isSameKey = keyDef.codes == mMultiTapCodes
        let isWithinTimeout = (now - mLastTapTime) < multiTapTimeout
        if isSameKey && isWithinTimeout {
            handleBackspace()
            mMultiTapIndex = (mMultiTapIndex + 1) % keyDef.codes.count
        } else {
            mMultiTapIndex = 0
        }
        mMultiTapCodes = keyDef.codes
        mLastTapTime = now
        onKey(primaryCode: keyDef.codes[mMultiTapIndex])
    }

    private func resetMultiTap() {
        mMultiTapCodes = []
        mMultiTapIndex = 0
        mLastTapTime = 0
    }

    // MARK: Key preview (iOS callout popup above pressed key)

    func keyboardView(_ view: KeyboardView, showPreviewFor keyDef: KeyDef, keyRect: CGRect) {
        keyPreviewView?.removeFromSuperview()

        // At least label or sublabel must be non-empty
        guard !keyDef.label.isEmpty || !keyDef.sublabel.isEmpty else { return }

        // Convert key rect from KeyboardView → window coordinates so the preview
        // can float above the keyboard top edge without being clipped by self.view.
        guard let kbView = keyboardView,
              let window = self.view.window else { return }
        let keyInWindow = kbView.convert(keyRect, to: window)

        // --- Layout mode: mirror key rendering (same isTall logic as KeyboardView) ---
        let isTall = keyInWindow.height >= keyInWindow.width

        // --- Bubble geometry -------------------------------------------------
        // iOS-native callout: a balloon wider than the key, tapering down via
        // S-curved sides to a "neck" that matches the key's width.
        let isLand  = view.isLandscape
        let bubbleW = max(keyInWindow.width * LayoutMetrics.KeyPreview.widthFactor,
                          isLand ? LayoutMetrics.KeyPreview.minWidthLandscape
                                 : LayoutMetrics.KeyPreview.minWidthPortrait)
        let bubbleH = max(keyInWindow.height * LayoutMetrics.KeyPreview.heightFactor,
                          isLand ? LayoutMetrics.KeyPreview.minHeightLandscape
                                 : LayoutMetrics.KeyPreview.minHeightPortrait)
        let neckH   = LayoutMetrics.KeyPreview.neckHeight
        let totalH  = bubbleH + neckH
        let r       = LayoutMetrics.KeyPreview.cornerRadius
        let edge    = LayoutMetrics.KeyPreview.edgeMargin

        // Centre bubble above the key; clamp to window edges
        let bubbleX = max(edge, min(keyInWindow.midX - bubbleW / 2,
                                    window.bounds.width - bubbleW - edge))
        let bubbleY = max(edge, keyInWindow.minY - totalH)

        let container = UIView(frame: CGRect(x: bubbleX, y: bubbleY,
                                             width: bubbleW, height: totalH))
        container.backgroundColor = .clear
        container.isUserInteractionEnabled = false

        // --- Callout shape ---------------------------------------------------
        // Key edges in container-local coordinates (clamped so a window-edge bubble still draws).
        let keyL = max(0, min(bubbleW, keyInWindow.minX - bubbleX))
        let keyR = max(0, min(bubbleW, keyInWindow.maxX - bubbleX))

        let path = UIBezierPath()
        // Top-left rounded corner
        path.move(to: CGPoint(x: 0, y: r))
        path.addArc(withCenter: CGPoint(x: r, y: r),
                    radius: r, startAngle: .pi, endAngle: -.pi / 2, clockwise: true)
        // Top edge → top-right corner
        path.addLine(to: CGPoint(x: bubbleW - r, y: 0))
        path.addArc(withCenter: CGPoint(x: bubbleW - r, y: r),
                    radius: r, startAngle: -.pi / 2, endAngle: 0, clockwise: true)
        // Right edge of balloon down to neck start
        path.addLine(to: CGPoint(x: bubbleW, y: bubbleH))
        // S-curve from balloon bottom-right down to key right edge
        path.addCurve(to: CGPoint(x: keyR, y: bubbleH + neckH),
                      controlPoint1: CGPoint(x: bubbleW, y: bubbleH + neckH * LayoutMetrics.KeyPreview.neckCurveFar),
                      controlPoint2: CGPoint(x: keyR,    y: bubbleH + neckH * LayoutMetrics.KeyPreview.neckCurveNear))
        // Across the key top
        path.addLine(to: CGPoint(x: keyL, y: bubbleH + neckH))
        // S-curve from key left edge up to balloon bottom-left
        path.addCurve(to: CGPoint(x: 0, y: bubbleH),
                      controlPoint1: CGPoint(x: keyL, y: bubbleH + neckH * LayoutMetrics.KeyPreview.neckCurveNear),
                      controlPoint2: CGPoint(x: 0,    y: bubbleH + neckH * LayoutMetrics.KeyPreview.neckCurveFar))
        // Left edge up to top-left corner
        path.addLine(to: CGPoint(x: 0, y: r))
        path.close()

        // Mirror the key's own palette so the preview bubble matches the theme
        // exactly: same background as the key and same label/sublabel colors.
        let pal = KeyboardPalette.palettes[max(0, min(resolvedKeyboardTheme, KeyboardPalette.palettes.count - 1))]
        let keyBg    = keyDef.isModifier ? pal.modifierKey   : pal.normalKey
        let keyLabel = keyDef.isModifier ? pal.modifierLabel : pal.label

        let shapeLayer = CAShapeLayer()
        shapeLayer.path = path.cgPath
        shapeLayer.fillColor = keyBg.cgColor
        shapeLayer.shadowColor = UIColor.black.cgColor
        shapeLayer.shadowOffset = CGSize(width: 0, height: LayoutMetrics.KeyPreview.shadowOffsetY)
        shapeLayer.shadowOpacity = LayoutMetrics.KeyPreview.shadowOpacity
        shapeLayer.shadowRadius  = LayoutMetrics.KeyPreview.shadowRadius
        container.layer.addSublayer(shapeLayer)

        // --- Content: same layout as the key itself --------------------------
        let contentView: UIView
        let hasSublabel = !keyDef.sublabel.isEmpty

        if hasSublabel {
            // Dual-label layout — mirrors KeyboardView.makeDualLabelView
            let stack = UIStackView()
            stack.alignment = .center

            let primaryLbl = UILabel()
            primaryLbl.text = keyDef.label
            primaryLbl.textColor = pal.secondaryLabel
            primaryLbl.setContentHuggingPriority(.required, for: .horizontal)
            primaryLbl.setContentHuggingPriority(.required, for: .vertical)

            let subLbl = UILabel()
            subLbl.text = keyDef.sublabel
            subLbl.textColor = keyLabel
            subLbl.setContentHuggingPriority(.required, for: .horizontal)
            subLbl.setContentHuggingPriority(.required, for: .vertical)

            if isTall {
                stack.axis    = .vertical
                stack.spacing = 0
                primaryLbl.font = UIFont.systemFont(
                    ofSize: LayoutMetrics.KeyPreview.primaryFontSize(isTall: true, isLandscape: isLand),
                    weight: .regular)
                subLbl.font = UIFont.systemFont(
                    ofSize: LayoutMetrics.KeyPreview.sublabelFontSize(isTall: true, isLandscape: isLand),
                    weight: .regular)
            } else {
                stack.axis    = .horizontal
                stack.spacing = LayoutMetrics.KeyPreview.horizontalDualSpacing
                primaryLbl.font = UIFont.systemFont(
                    ofSize: LayoutMetrics.KeyPreview.primaryFontSize(isTall: false, isLandscape: isLand),
                    weight: .light)
                subLbl.font = UIFont.systemFont(
                    ofSize: LayoutMetrics.KeyPreview.sublabelFontSize(isTall: false, isLandscape: isLand),
                    weight: .regular)
            }
            stack.addArrangedSubview(primaryLbl)
            stack.addArrangedSubview(subLbl)
            contentView = stack
        } else {
            // Single label
            let lbl = UILabel()
            lbl.text          = keyDef.label
            lbl.font          = UIFont.systemFont(
                ofSize: LayoutMetrics.KeyPreview.singleFontSize(isLandscape: isLand), weight: .regular)
            lbl.textColor     = keyLabel
            lbl.textAlignment = .center
            contentView = lbl
        }

        contentView.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(contentView)
        NSLayoutConstraint.activate([
            contentView.centerXAnchor.constraint(equalTo: container.centerXAnchor),
            contentView.centerYAnchor.constraint(equalTo: container.centerYAnchor,
                                                  constant: -neckH / 2),
            contentView.widthAnchor.constraint(lessThanOrEqualToConstant: bubbleW + LayoutMetrics.KeyPreview.contentWidthInset),
        ])

        // --- Animate in -------------------------------------------------------
        container.alpha = 0
        container.transform = CGAffineTransform(scaleX: LayoutMetrics.KeyPreview.initialScale,
                                                y: LayoutMetrics.KeyPreview.initialScale)
        window.addSubview(container)   // add to window so preview clears the candidate bar
        UIView.animate(withDuration: LayoutMetrics.KeyPreview.appearDuration, delay: 0,
                       usingSpringWithDamping: LayoutMetrics.KeyPreview.springDamping,
                       initialSpringVelocity: LayoutMetrics.KeyPreview.springInitialVelocity) {
            container.alpha = 1
            container.transform = .identity
        }
        keyPreviewView = container
    }

    func keyboardView(_ view: KeyboardView, didMoveCaretBy steps: Int) {
        guard mComposing.isEmpty else { return }
        textDocumentProxy.adjustTextPosition(byCharacterOffset: steps)
    }

    func keyboardViewDismissPreview(_ view: KeyboardView) {
        guard let preview = keyPreviewView else { return }
        keyPreviewView = nil
        UIView.animate(withDuration: LayoutMetrics.KeyPreview.disappearDuration, animations: {
            preview.alpha = 0
        }, completion: { _ in preview.removeFromSuperview() })
    }

    func keyboardView(_ view: KeyboardView, didLongPress keyDef: KeyDef) {
        // Keyboard key (code -3): show the LIME options menu (spec §10).
        // Globe long-press is routed through UIInputViewController.handleInputModeList.
        // Per spec §10: briefly show globe icon preview to satisfy Apple's globe affordance requirement,
        // then display the inline options menu.
        if keyDef.code == LimeKeyCode.done.rawValue {
            showGlobeKeyPreview(for: keyDef, in: view)
            showGlobeMenu(from: view)
        }
        // Space key: show LIME-internal IM picker only (spec §10: NOT iOS keyboard switch)
        else if keyDef.code == LimeKeyCode.space.rawValue {
            showLimeIMPicker()
        }
    }

    func keyboardView(_ view: KeyboardView, didLongPressPopupKey keyDef: KeyDef, sourceRect: CGRect) {
        let srcInView = view.convert(sourceRect, to: self.view)
        showPopupKeyboard(for: keyDef, sourceRect: srcInView)
    }

    // MARK: - Popup Keyboard


    private func showPopupKeyboard(for keyDef: KeyDef, sourceRect: CGRect) {
        dismissPopupKeyboard()
        guard let popupLayout = resolvePopupLayout(for: keyDef),
              !popupLayout.rows.isEmpty else { return }

        // Single-key popup: fire the action directly without showing the popup UI.
        let allKeys = popupLayout.rows.flatMap { $0.keys }
        if allKeys.count == 1 {
            firePopupKey(allKeys[0])
            return
        }

        // Tap-outside overlay placed below the popup
        let overlay = UIControl()
        overlay.frame = view.bounds
        overlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        overlay.backgroundColor = LayoutMetrics.TouchTrap.fill
        overlay.addTarget(self, action: #selector(dismissPopupKeyboard), for: .touchUpInside)
        overlay.tag = 9877
        view.addSubview(overlay)

        let popup = PopupKeyboardView(layout: popupLayout, theme: resolvedKeyboardTheme)
        popup.delegate = self

        // Centre the popup over the key, clamped to view edges with edgeMargin.
        let pw = popup.frame.width
        let ph = popup.frame.height
        var ox = sourceRect.midX - pw / 2
        let edge = LayoutMetrics.PopupKeyboard.edgeMargin
        ox = max(edge, min(ox, view.bounds.width - pw - edge))
        let oy = max(0, sourceRect.minY - ph - LayoutMetrics.PopupKeyboard.yOffsetFromKey)
        popup.frame.origin = CGPoint(x: ox, y: oy)

        view.addSubview(popup)
        currentPopupView = popup
    }

    @objc private func dismissPopupKeyboard() {
        currentPopupView?.removeFromSuperview()
        currentPopupView = nil
        view.subviews.filter { $0.tag == 9877 }.forEach { $0.removeFromSuperview() }
    }

    // MARK: - Popup Layout Resolution

    private func resolvePopupLayout(for keyDef: KeyDef) -> LimeKeyLayout? {
        let name = keyDef.popupKeyboard   // already resolved by LayoutLoader (no @xml/ prefix)
        guard !name.isEmpty else { return nil }

        if name == "popup_template" {
            return popupCharLayout(for: keyDef.popupCharacters)
        }

        guard let layout = LayoutLoader.load(name) else { return nil }

        // Resolve @string/popular_domain_X placeholders in popup_domains
        if name == "popup_domains" {
            return resolvedDomainLayout(layout)
        }
        return layout
    }

    /// Build a popup layout from a popupCharacters string (e.g. "àáâãäåæ").
    /// Each Unicode scalar becomes one key. Returns nil if chars is empty.
    private func popupCharLayout(for chars: String) -> LimeKeyLayout? {
        guard !chars.isEmpty else { return nil }
        let keys = chars.unicodeScalars.map { scalar in
            KeyDef(code: Int(scalar.value), label: String(scalar))
        }
        return LimeKeyLayout(id: "popup_chars", rows: [KeyRow(keys: keys)])
    }

    private func resolvedDomainLayout(_ layout: LimeKeyLayout) -> LimeKeyLayout {
        let domains = [".com", ".net", ".org", ".co", ".info"]
        let rows = layout.rows.map { row -> KeyRow in
            let keys = row.keys.map { key -> KeyDef in
                var label = key.label
                if label.hasPrefix("@string/popular_domain_"),
                   let idx = Int(label.suffix(1)),
                   (1...domains.count).contains(idx) {
                    label = domains[idx - 1]
                }
                return KeyDef(code: key.code, label: label, sublabel: key.sublabel,
                              widthPercent: key.widthPercent, icon: key.icon,
                              isRepeatable: key.isRepeatable, isModifier: key.isModifier,
                              isSticky: key.isSticky)
            }
            return KeyRow(keys: keys, isBottomRow: row.isBottomRow)
        }
        return LimeKeyLayout(id: layout.id, rows: rows)
    }

    // MARK: - IM Switching Helper

    /// Switch to a LIME-internal IM by absolute index in activatedIMs.
    private func switchIM(toIndex i: Int) {
        guard i < activatedIMs.count else { return }
        dismissPopupKeyboard()
        let im = activatedIMs[i]
        activeIMIndex = i
        activeIM = im.tableNick.isEmpty ? "phonetic" : im.tableNick
        // Persist last-used IM (mirrors Android mLIMEPref.setActiveIM(), key "keyboard_list")
        sharedDefaults?.set(activeIM, forKey: "keyboard_list")
        clearShiftState()
        clearComposing(force: false)
        let caps = searchServer?.detectIMCapabilities(tableName: activeIM)
            ?? (hasNumber: false, hasSymbol: false)
        searchServer?.setTableName(activeIM, hasNumberMapping: caps.hasNumber,
                                   hasSymbolMapping: caps.hasSymbol)
        searchServer?.setPhoneticKeyboardType(phoneticKeyboardType)
        refreshImKeys()
        if let layout = LayoutLoader.load(resolvedLayoutId(for: activeIM)), layout.id != currentLayout.id {
            currentLayout = layout
            keyboardView?.setLayout(currentLayout)
            applyHeight()
        }
        showLimeToast(displayName(for: im))
    }

    private func displayName(for im: ImConfig) -> String {
        if !im.label.isEmpty { return im.label }
        if !im.tableNick.isEmpty { return im.tableNick }
        return activeIM
    }

    /// Show a globe-icon preview bubble above the keyboard key on long-press (spec §10).
    /// The globe icon satisfies Apple's "clearly visible globe affordance" requirement.
    private func showGlobeKeyPreview(for keyDef: KeyDef, in kbView: KeyboardView) {
        guard let window = self.view.window,
              let kbViewUnwrapped = keyboardView else { return }

        // Find the done key button frame in window coordinates
        // We look for the subview whose KeyDef code matches -3
        var keyRect: CGRect = .zero
        func findKeyRect(in view: UIView) -> CGRect? {
            for sub in view.subviews {
                if let btn = sub as? UIButton {
                    // Use reflection-free approach: check tag or just use kbView bounds estimate
                    // Since we can't directly access KeyButton.keyDef from here,
                    // approximate: the done key is always the first key in the bottom row.
                    _ = btn
                }
                if let found = findKeyRect(in: sub) { return found }
            }
            return nil
        }

        // Approximate position: done key is bottom-left of the keyboard
        let kbInWindow = kbViewUnwrapped.convert(kbViewUnwrapped.bounds, to: window)
        let isLand = kbView.isLandscape
        let approxKeyW: CGFloat = kbInWindow.width * LayoutMetrics.GlobePreview.approxKeyWidthFactor
        let approxKeyH: CGFloat = isLand
            ? LayoutMetrics.GlobePreview.approxKeyHeightLandscape
            : LayoutMetrics.GlobePreview.approxKeyHeightPortrait
        keyRect = CGRect(x: kbInWindow.minX,
                         y: kbInWindow.maxY - approxKeyH,
                         width: approxKeyW,
                         height: approxKeyH)

        // Build a simple globe preview bubble
        let bubbleW: CGFloat = isLand
            ? LayoutMetrics.GlobePreview.bubbleWidthLandscape
            : LayoutMetrics.GlobePreview.bubbleWidthPortrait
        let bubbleH: CGFloat = isLand
            ? LayoutMetrics.GlobePreview.bubbleHeightLandscape
            : LayoutMetrics.GlobePreview.bubbleHeightPortrait
        let tipH: CGFloat = LayoutMetrics.GlobePreview.tipHeight
        let totalH = bubbleH + tipH
        let r: CGFloat = LayoutMetrics.GlobePreview.cornerRadius
        let edge = LayoutMetrics.GlobePreview.edgeMargin
        let bubbleX = max(edge, keyRect.midX - bubbleW / 2)
        let bubbleY = max(edge, keyRect.minY - totalH)

        let container = UIView(frame: CGRect(x: bubbleX, y: bubbleY,
                                             width: bubbleW, height: totalH))
        container.backgroundColor = .clear
        container.isUserInteractionEnabled = false

        // Shape
        let tipX: CGFloat = min(bubbleW - r, max(r, keyRect.midX - bubbleX))
        let path = UIBezierPath()
        path.move(to: CGPoint(x: 0, y: r))
        path.addArc(withCenter: CGPoint(x: r, y: r), radius: r,
                    startAngle: .pi, endAngle: -.pi/2, clockwise: true)
        path.addLine(to: CGPoint(x: bubbleW - r, y: 0))
        path.addArc(withCenter: CGPoint(x: bubbleW - r, y: r), radius: r,
                    startAngle: -.pi/2, endAngle: 0, clockwise: true)
        path.addLine(to: CGPoint(x: bubbleW, y: bubbleH - r))
        path.addArc(withCenter: CGPoint(x: bubbleW - r, y: bubbleH - r), radius: r,
                    startAngle: 0, endAngle: .pi/2, clockwise: true)
        path.addLine(to: CGPoint(x: tipX + LayoutMetrics.GlobePreview.tipHorizontalRadius, y: bubbleH))
        path.addLine(to: CGPoint(x: tipX, y: bubbleH + tipH))
        path.addLine(to: CGPoint(x: tipX - LayoutMetrics.GlobePreview.tipHorizontalRadius, y: bubbleH))
        path.addLine(to: CGPoint(x: r, y: bubbleH))
        path.addArc(withCenter: CGPoint(x: r, y: bubbleH - r), radius: r,
                    startAngle: .pi/2, endAngle: .pi, clockwise: true)
        path.close()
        let t = resolvedKeyboardTheme
        let pal = KeyboardPalette.palettes[max(0, min(t, KeyboardPalette.palettes.count - 1))]
        let sl = CAShapeLayer()
        sl.path = path.cgPath; sl.fillColor = pal.modifierKey.cgColor
        sl.shadowColor = UIColor.black.cgColor
        sl.shadowOffset = CGSize(width: 0, height: LayoutMetrics.GlobePreview.shadowOffsetY)
        sl.shadowOpacity = LayoutMetrics.GlobePreview.shadowOpacity
        sl.shadowRadius = LayoutMetrics.GlobePreview.shadowRadius
        container.layer.addSublayer(sl)

        // Globe SF symbol
        let iconSize = isLand
            ? LayoutMetrics.GlobePreview.iconSizeLandscape
            : LayoutMetrics.GlobePreview.iconSizePortrait
        let img = UIImageView(image: UIImage(systemName: "globe",
            withConfiguration: UIImage.SymbolConfiguration(pointSize: iconSize)))
        img.tintColor = pal.modifierLabel
        img.contentMode = .center
        img.frame = CGRect(x: 0, y: 0, width: bubbleW, height: bubbleH)
        container.addSubview(img)

        container.alpha = 0
        window.addSubview(container)
        UIView.animate(withDuration: LayoutMetrics.GlobePreview.appearDuration) { container.alpha = 1 }

        // Auto-dismiss after menu appears (brief flash)
        DispatchQueue.main.asyncAfter(deadline: .now() + LayoutMetrics.GlobePreview.dismissDelay) {
            UIView.animate(withDuration: LayoutMetrics.GlobePreview.dismissDuration,
                           animations: { container.alpha = 0 },
                           completion: { _ in container.removeFromSuperview() })
        }
    }

    // MARK: - Inline Menu Panel
    // UIAlertController.present() in a keyboard extension causes the system to advance to the
    // next input mode in some iOS versions. Use an inline UIView panel instead.

    /// Dismiss any visible inline menu panel.
    private func dismissInlineMenu() {
        inlineMenuPanel?.removeFromSuperview()
        inlineMenuPanel = nil
        if let tap = inlineMenuDismissTapGesture {
            view?.removeGestureRecognizer(tap)
            inlineMenuDismissTapGesture = nil
        }
    }

    /// Build and show an inline menu panel overlaying the keyboard.
    private func showInlineMenu(items: [(title: String, action: () -> Void)]) {
        dismissInlineMenu()
        guard let root = view else { return }

        let panel = UIView()
        panel.backgroundColor = UIColor.systemBackground.withAlphaComponent(LayoutMetrics.InlineMenu.backgroundAlpha)
        panel.layer.cornerRadius = LayoutMetrics.InlineMenu.cornerRadius
        panel.layer.shadowColor = UIColor.black.cgColor
        panel.layer.shadowOpacity = LayoutMetrics.InlineMenu.shadowOpacity
        panel.layer.shadowRadius = LayoutMetrics.InlineMenu.shadowRadius
        panel.layer.shadowOffset = CGSize(width: 0, height: LayoutMetrics.InlineMenu.shadowOffsetY)
        panel.translatesAutoresizingMaskIntoConstraints = false
        panel.clipsToBounds = true
        root.addSubview(panel)

        // Scroll view so the panel can scroll when item count exceeds available height
        // (e.g. long IM picker list taller than keyboardView + candidate bar).
        let scroll = UIScrollView()
        scroll.translatesAutoresizingMaskIntoConstraints = false
        scroll.showsVerticalScrollIndicator = true
        scroll.alwaysBounceVertical = false
        panel.addSubview(scroll)

        // Stack of buttons
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 0
        stack.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(stack)

        for (idx, item) in items.enumerated() {
            let btn = UIButton(type: .system)
            btn.setTitle(item.title, for: .normal)
            btn.titleLabel?.font = UIFont.systemFont(ofSize: LayoutMetrics.InlineMenu.buttonFontSize)
            btn.contentHorizontalAlignment = .center
            btn.heightAnchor.constraint(equalToConstant: LayoutMetrics.InlineMenu.buttonHeight).isActive = true
            btn.tag = idx
            // Separator line (except last)
            if idx < items.count - 1 {
                let sep = UIView()
                sep.backgroundColor = UIColor.separator
                sep.heightAnchor.constraint(equalToConstant: LayoutMetrics.InlineMenu.separatorHeight).isActive = true
                stack.addArrangedSubview(btn)
                stack.addArrangedSubview(sep)
            } else {
                // Last item is Cancel — style differently
                btn.setTitleColor(.systemBlue, for: .normal)
                stack.addArrangedSubview(btn)
            }
            let capture = item.action
            btn.addAction(UIAction { [weak self] _ in
                self?.dismissInlineMenu()
                capture()
            }, for: .touchUpInside)
        }

        let menuInset = LayoutMetrics.InlineMenu.edgeInset
        NSLayoutConstraint.activate([
            scroll.topAnchor.constraint(equalTo: panel.topAnchor, constant: menuInset),
            scroll.bottomAnchor.constraint(equalTo: panel.bottomAnchor, constant: -menuInset),
            scroll.leadingAnchor.constraint(equalTo: panel.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: panel.trailingAnchor),

            stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
            stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
            stack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor),
            stack.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor),

            panel.leadingAnchor.constraint(equalTo: root.leadingAnchor, constant: menuInset),
            panel.trailingAnchor.constraint(equalTo: root.trailingAnchor, constant: -menuInset),
            panel.bottomAnchor.constraint(equalTo: root.bottomAnchor, constant: -menuInset),
            // Cap the panel height so a long IM list scrolls instead of getting
            // clipped above the keyboard view (root) bounds.
            panel.topAnchor.constraint(greaterThanOrEqualTo: root.topAnchor, constant: menuInset),
        ])

        // Prefer the panel to be just tall enough to fit content, but allow it
        // to shrink (and the scroll view to scroll) when content exceeds the
        // available root height. Use a high but non-required priority so the
        // greaterThanOrEqualTo top constraint can win.
        let preferredHeight = scroll.heightAnchor.constraint(equalTo: stack.heightAnchor)
        preferredHeight.priority = .defaultHigh
        preferredHeight.isActive = true

        inlineMenuPanel = panel

        // Arm tap-outside dismissal after the long-press touch sequence completes.
        // Adding this recognizer immediately can make the same finger-up event that
        // opened the menu dismiss it again before it is visibly usable.
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self, weak root, weak panel] in
            guard let self,
                  let root,
                  let panel,
                  self.inlineMenuPanel === panel else { return }
            let tap = UITapGestureRecognizer(target: self, action: #selector(self.dismissInlineMenuGesture))
            tap.cancelsTouchesInView = false
            root.addGestureRecognizer(tap)
            self.inlineMenuDismissTapGesture = tap
        }

        // Animate in
        panel.alpha = 0
        panel.transform = CGAffineTransform(translationX: 0, y: LayoutMetrics.InlineMenu.appearTranslationY)
        UIView.animate(withDuration: LayoutMetrics.InlineMenu.appearDuration) {
            panel.alpha = 1
            panel.transform = .identity
        }
    }

    @objc private func dismissInlineMenuGesture(_ gr: UITapGestureRecognizer) {
        guard let panel = inlineMenuPanel else { return }
        // Only dismiss if tap is outside the panel
        let loc = gr.location(in: view)
        if !panel.frame.contains(loc) {
            dismissInlineMenu()
        }
    }

    /// Long-press on keyboard key: inline options menu (mirrors Android handleOptions()).
    private func showGlobeMenu(from sourceView: UIView) {
        var items: [(title: String, action: () -> Void)] = []

        let reverseLookupValue = LIMEPreferenceManager.shared.reverseLookup(for: activeIM)
        let reverseLookupOptions = LIMEPreferenceManager.reverseLookupOptions(from: activatedIMs)
        let reverseLookupLabel = LIMEPreferenceManager.reverseLookupLabel(for: reverseLookupValue,
                                                                          options: reverseLookupOptions)
        items.append(("字根反查：\(reverseLookupLabel) ▸", { [weak self] in
            self?.showReverseLookupPicker()
        }))

        // 漢字轉換 — sub-picker (mirrors Android showHanConvertPicker())
        let hanStateLabels = ["關閉", "繁→簡", "簡→繁"]
        let hanState = hanStateLabels[max(0, min(hanConvertOption, hanStateLabels.count - 1))]
        items.append(("漢字轉換：\(hanState) ▸", { [weak self] in self?.showHanConvertPicker() }))

        // LIME 輸入法切換 — mirrors Android showIMPicker()
        items.append(("LIME 輸入法切換", { [weak self] in self?.showLimeIMPicker() }))

        // 系統輸入法切換 — only when no globe key is visible (tap-globe already handles this when visible)
        let isPadIPad = isOnPad && currentLayout.id.contains("_ipad")
        let globeIsVisible = isPadIPad || needsInputModeSwitchKey
        if !globeIsVisible {
            items.append(("系統輸入法切換", { [weak self] in self?.advanceToNextInputMode() }))
        }

        items.append(("取消", {}))
        showInlineMenu(items: items)
    }

    /// Reverse lookup source sub-picker for the current active IM.
    private func showReverseLookupPicker() {
        let current = LIMEPreferenceManager.shared.reverseLookup(for: activeIM)
        var items: [(title: String, action: () -> Void)] = []
        for option in LIMEPreferenceManager.reverseLookupOptions(from: activatedIMs) {
            let display = option.value == current ? "✓ \(option.label)" : option.label
            items.append((display, { [weak self] in
                guard let self else { return }
                LIMEPreferenceManager.shared.setReverseLookup(option.value, for: self.activeIM)
                self.showLimeToast("字根反查：\(option.label)")
            }))
        }
        items.append(("取消", {}))
        showInlineMenu(items: items)
    }

    /// Han conversion sub-picker (mirrors Android showHanConvertPicker()).
    private func showHanConvertPicker() {
        let options = ["無", "繁轉簡", "簡轉繁"]
        var items: [(title: String, action: () -> Void)] = []
        for (opt, title) in options.enumerated() {
            let display = (hanConvertOption == opt) ? "✓ \(title)" : title
            items.append((display, { [weak self] in
                self?.hanConvertOption = opt
                self?.sharedDefaults?.set(opt, forKey: "han_convert_option")
            }))
        }
        items.append(("取消", {}))
        showInlineMenu(items: items)
    }

    /// Long-press on space key: LIME-internal IM picker (spec §10).
    private func showLimeIMPicker() {
        guard !activatedIMs.isEmpty else { return }
        var items: [(title: String, action: () -> Void)] = []
        for (i, im) in activatedIMs.enumerated() {
            let label = im.label.isEmpty ? im.tableNick : im.label
            let display = (i == activeIMIndex) ? "✓ \(label)" : label
            items.append((display, { [weak self] in self?.switchIM(toIndex: i) }))
        }
        items.append(("取消", {}))
        showInlineMenu(items: items)
    }

    private func showEmojiPanel() {
        emojiPanelSource = EmojiPanelSource.source(isEnglishOnly: mEnglishOnly)
        if isExpandedCandidatesVisible { hideExpandedCandidates() }
        dismissPopupKeyboard()
        hideComposingPopup()
        view.isOpaque = false
        view.backgroundColor = .clear
        inputView?.isOpaque = false
        inputView?.backgroundColor = .clear

        let panel: EmojiPanelView
        if let existing = emojiPanelView {
            panel = existing
        } else {
            panel = EmojiPanelView()
            panel.translatesAutoresizingMaskIntoConstraints = false
            panel.delegate = self
            view.addSubview(panel)
            let bottom = panel.bottomAnchor.constraint(equalTo: view.bottomAnchor)
            emojiPanelBottomConstraint = bottom
            NSLayoutConstraint.activate([
                panel.topAnchor.constraint(equalTo: view.topAnchor),
                panel.leadingAnchor.constraint(equalTo: view.leadingAnchor),
                panel.trailingAnchor.constraint(equalTo: view.trailingAnchor),
                bottom,
            ])
            emojiPanelView = panel
        }

        emojiPanelBottomConstraint?.isActive = false
        let bottom = panel.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        bottom.isActive = true
        emojiPanelBottomConstraint = bottom
        keyboardTopToViewConstraint?.isActive = false
        keyboardTopToCandidateConstraint?.isActive = true
        candidateBarHeightConstraint?.constant = candidateBarHeight
        keyboardView.isHidden = true
        candidateBar.isHidden = true
        panel.prepareForPresentation()
        panel.setReturnKeyTitle(emojiPanelSource.returnKeyTitle)
        panel.isHidden = false
        panel.setEmojiPages(loadEmojiCategoryPages())
    }

    private func hideEmojiPanel() {
        emojiPanelView?.resignSearch()
        emojiPanelView?.isHidden = true
        emojiPanelView?.setSearchMode(false)
        emojiPanelBottomConstraint?.isActive = false
        if let panel = emojiPanelView {
            let bottom = panel.bottomAnchor.constraint(equalTo: view.bottomAnchor)
            bottom.isActive = true
            emojiPanelBottomConstraint = bottom
        }
        keyboardTopToViewConstraint?.isActive = false
        keyboardTopToCandidateConstraint?.isActive = true
        candidateBarHeightConstraint?.constant = candidateBarHeight
        candidateBar.isHidden = false
        keyboardView.isHidden = false
        keyboardView.setLayout(currentLayout)
    }

    private func showEmojiSearchKeyboard() {
        guard let panel = emojiPanelView else { return }
        emojiPanelBottomConstraint?.isActive = false
        let bottom = panel.bottomAnchor.constraint(equalTo: keyboardView.topAnchor)
        bottom.isActive = true
        emojiPanelBottomConstraint = bottom
        candidateBar.isHidden = true
        candidateBarHeightConstraint?.constant = 0
        keyboardTopToCandidateConstraint?.isActive = false
        keyboardTopToViewConstraint?.constant = 118
        keyboardTopToViewConstraint?.isActive = true
        keyboardView.isHidden = false
        let englishLayout = numberRowInEnglish ? "lime_english_number" : "lime_english"
        if currentLayout.id != englishLayout,
           let layout = LayoutLoader.load(englishLayout) {
            keyboardView.setLayout(layout)
        }
        panel.setSearchMode(true)
        view.setNeedsLayout()
    }

    private func loadEmojiCategoryPages() -> [[Mapping]] {
        let recent = searchServer?.loadRecentEmoji(32) ?? []
        var pages = EmojiPanelFallback.categories.map { emojiMappings(from: $0) }
        pages[0] = EmojiRecentSeedQueue.merged(recent: recent,
                                               fallback: pages[0],
                                               limit: 32)
        let dbCategoryPages = searchServer?.loadEmojiCategoryPages() ?? []
        for (index, dbPage) in dbCategoryPages.enumerated()
            where index + 1 < pages.count && !dbPage.isEmpty {
            pages[index + 1] = dbPage
        }
        return pages
    }

    private func preloadEmojiCategoryPages() {
        searchServer?.preloadEmojiCategoryPages()
    }

    private func loadEmojiSearchFallbackItems() -> [Mapping] {
        var seen = Set<String>()
        return EmojiPanelFallback.categories.flatMap { words in
            words.compactMap { word in
                guard seen.insert(word).inserted else { return nil }
                return emojiMapping(word)
            }
        }
    }

    private func emojiMappings(from words: [String]) -> [Mapping] {
        words.map { emojiMapping($0) }
    }

    private func emojiMapping(_ word: String) -> Mapping {
        Mapping(id: 0, code: "", word: word,
                score: 0, baseScore: 0,
                recordType: Mapping.RecordType.emoji)
    }

    private func searchEmojiPanel(query: String) {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            emojiPanelView?.setEmojis(loadEmojiSearchFallbackItems())
            return
        }
        let english = searchServer?.searchEmoji(trimmed, locale: .en, limit: 80) ?? []
        let traditional = searchServer?.searchEmoji(trimmed, locale: .tw, limit: 80) ?? []
        var seen = Set<String>()
        let results = (english + traditional).filter { seen.insert($0.word).inserted }
        emojiPanelView?.setEmojis(results)
    }
}

private enum EmojiPanelFallback {
    static let categories: [[String]] = [
        ["😀", "😂", "😍", "🥰", "😘", "😭", "👍", "🙏", "👏", "🎉", "❤️", "✨", "🔥", "✅", "⭐", "💯"],
        ["😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "😘",
         "😋", "😛", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥳", "😏", "😒", "😔", "😢", "😭", "😤", "😱"],
        ["👋", "🤚", "🖐", "✋", "🖖", "👌", "🤌", "🤏", "✌", "🤞", "🫰", "🤟", "🤘", "🤙", "👈", "👉",
         "👆", "🖕", "👇", "☝", "🫵", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "🫶", "🙏", "💅"],
        ["🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔",
         "🐧", "🐦", "🐤", "🦆", "🦅", "🦉", "🐺", "🐗", "🐴", "🦄", "🐝", "🦋", "🐌", "🐞", "🐢", "🐍"],
        ["🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝",
         "🍅", "🥑", "🍆", "🥔", "🥕", "🌽", "🌶", "🥒", "🥬", "🥦", "🍄", "🥜", "🍞", "🧀", "🍔", "🍟"],
        ["🚗", "🚕", "🚙", "🚌", "🚎", "🏎", "🚓", "🚑", "🚒", "🚐", "🛻", "🚚", "🚛", "🚜", "🛵", "🏍",
         "🛺", "🚲", "🛴", "🚨", "🚔", "🚍", "🚘", "🚖", "✈", "🚀", "🚁", "⛵", "🚢", "🚉", "🚇", "🚆"],
        ["⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱", "🪀", "🏓", "🏸", "🏒", "🏑", "🥍",
         "🏏", "🪃", "🥅", "⛳", "🪁", "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹", "🛼", "🛷", "⛸", "🥌"],
        ["💡", "🔦", "🕯", "🪔", "📱", "💻", "⌨", "🖥", "🖨", "🖱", "🖲", "💽", "💾", "💿", "📷", "🎥",
         "📺", "📻", "🎙", "⏰", "⌚", "📚", "✏", "📌", "✂", "🔒", "🔑", "🔨", "🧰", "🧲", "🧪", "🧬"],
        ["❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣", "💕", "💞", "💓", "💗", "💖",
         "✨", "⭐", "🌟", "💫", "⚡", "🔥", "💥", "☀", "🌙", "☁", "☔", "❄", "☃", "✅", "❌", "⭕"],
        ["🏳", "🏴", "🏁", "🚩", "🇹🇼", "🇯🇵", "🇰🇷", "🇺🇸", "🇨🇦", "🇬🇧", "🇫🇷", "🇩🇪", "🇮🇹", "🇪🇸", "🇦🇺", "🇳🇿",
         "🇸🇬", "🇭🇰", "🇲🇴", "🇹🇭", "🇻🇳", "🇵🇭", "🇲🇾", "🇮🇩", "🇮🇳", "🇧🇷", "🇲🇽", "🇳🇱", "🇸🇪", "🇨🇭", "🇪🇺", "🇺🇳"]
    ]
}

// MARK: - UIScrollViewDelegate
extension KeyboardViewController: UIScrollViewDelegate {

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        guard scrollView === expandedScrollView else { return }
        updateExpandedScrollThumb()
    }
}

// MARK: - CandidateBarViewDelegate
extension KeyboardViewController: CandidateBarViewDelegate {

    func candidateBarView(_ view: CandidateBarView, didSelect mapping: Mapping) {
        if mapping.isEnglishSuggestionRecord {
            commitEnglishSuggestion(mapping.word)
        } else {
            pickCandidateManually(mapping)
        }
    }

    func candidateBarViewDidRequestEmoji(_ view: CandidateBarView) {
        showEmojiPanel()
    }

    func candidateBarViewDidRequestOptions(_ view: CandidateBarView) {
        showGlobeMenu(from: view)
    }

    func candidateBarViewDidRequestDismiss(_ view: CandidateBarView) {
        if isExpandedCandidatesVisible { hideExpandedCandidates() }
        cancelActiveComposingFromCandidateDismiss()
    }

    func candidateBarViewDidRequestMore(_ view: CandidateBarView) {
        // Tap again to collapse
        if isExpandedCandidatesVisible {
            hideExpandedCandidates()
            return
        }

        if isShowingRelatedPhrases {
            guard let committed = committedCandidate, let ss = searchServer else { return }
            let word = committed.word
            DispatchQueue.global(qos: .userInteractive).async { [weak self] in
                let all = ss.getRelatedByWord(word, getAllRecords: true)
                DispatchQueue.main.async { self?.showExpandedCandidates(all) }
            }
            return
        }

        guard CandidateExpansionPolicy.shouldExpand(
            hasCandidatesShown: hasCandidatesShown,
            composing: mComposing,
            hasChineseSymbolCandidatesShown: hasChineseSymbolCandidatesShown
        ) else { return }
        // mCandidateList already holds the full emoji-injected list from the two-stage
        // fetch. Use it directly so the expanded grid shows exactly the same items as
        // the candidate bar (including any injected emoji). Filter the `…`
        // (hasMoreMark) sentinel — it is a UI control, not a real candidate
        // (see docs/#77_ISSUE.md). If the user requests expansion during the
        // brief stage-1 window before stage 2 lands, the sentinel must not
        // appear in the grid.
        let all = mCandidateList.filter { !$0.isHasMoreMarkRecord }
        // Apply the Android CandidateView seeding rule so the expanded grid
        // highlights the same default entry the bar does.
        let idx: Int
        if all.count > 1 && all[1].isExactMatchToCodeRecord {
            idx = 1
        } else if let first = all.first,
                  first.isComposingCodeRecord || first.isRuntimeBuiltPhraseRecord {
            idx = 0
        } else {
            idx = -1
        }
        showExpandedCandidates(all, selectedIndex: idx)
    }
}

// MARK: - PopupKeyboardViewDelegate

extension KeyboardViewController: PopupKeyboardViewDelegate {

    func popupKeyboardView(_ popup: PopupKeyboardView, didSelect keyDef: KeyDef) {
        dismissPopupKeyboard()
        firePopupKey(keyDef)
    }

    /// Fire the action for a popup key (shared by popup selection and single-key direct dispatch).
    private func firePopupKey(_ keyDef: KeyDef) {
        // Special action codes (negative): route through the normal key handler
        if keyDef.code < 0 {
            onKey(primaryCode: keyDef.code)
            return
        }

        // Determine the character to insert
        let char: String
        if keyDef.code > 0, let scalar = Unicode.Scalar(keyDef.code) {
            char = String(scalar)
        } else if !keyDef.label.isEmpty {
            // Strip Android escape prefix if present (e.g. \' → ')
            let label = keyDef.label
            char = (label.hasPrefix("\\") && label.count > 1)
                ? String(label.dropFirst())
                : label
        } else {
            return
        }

        // Clear any inline composing first (popup commits bypass the IM engine)
        if composingLength > 0 { clearComposing(force: true) }
        isSelfUpdate = true
        textDocumentProxy.insertText(char)
        isSelfUpdate = false
    }
}

// MARK: - EmojiPanelViewDelegate

extension KeyboardViewController: EmojiPanelViewDelegate {

    func emojiPanelView(_ view: EmojiPanelView, didSelect mapping: Mapping) {
        if composingLength > 0 { clearComposing(force: true) }
        isSelfUpdate = true
        textDocumentProxy.insertText(mapping.word)
        isSelfUpdate = false
        searchServer?.recordEmojiUsage(mapping.word)
    }

    func emojiPanelViewDidRequestABC(_ view: EmojiPanelView) {
        hideEmojiPanel()
    }

    func emojiPanelViewDidRequestBackspace(_ view: EmojiPanelView) {
        textDocumentProxy.deleteBackward()
    }

    func emojiPanelViewDidBeginSearch(_ view: EmojiPanelView) {
        showEmojiSearchKeyboard()
    }

    func emojiPanelView(_ view: EmojiPanelView, didChangeSearchQuery query: String) {
        searchEmojiPanel(query: query)
    }
}

protocol EmojiPanelViewDelegate: AnyObject {
    func emojiPanelView(_ view: EmojiPanelView, didSelect mapping: Mapping)
    func emojiPanelViewDidRequestABC(_ view: EmojiPanelView)
    func emojiPanelViewDidRequestBackspace(_ view: EmojiPanelView)
    func emojiPanelViewDidBeginSearch(_ view: EmojiPanelView)
    func emojiPanelView(_ view: EmojiPanelView, didChangeSearchQuery query: String)
}

final class EmojiPanelView: UIView, UITextFieldDelegate, UIScrollViewDelegate {
    weak var delegate: EmojiPanelViewDelegate?

    private let searchField = UISearchTextField()
    private let emojiViewport = UIScrollView()
    private let emojiContentView = UIView()
    private let categoryScrollView = UIScrollView()
    private let categoryBar = UIStackView()
    private let categoryIconSpacer = UIView()
    private let categoryBackspaceButton = UIButton(type: .system)
    private var emojiPages: [[Mapping]] = []
    private var buttonMappings: [Mapping] = []
    private let emojiFallbackButtonSize: CGFloat = 54
    private let phoneEmojiMinButtonSize: CGFloat = 46
    private let phoneEmojiMaxButtonSize: CGFloat = 54
    private let phoneEmojiMinFontSize: CGFloat = 30
    private let phoneEmojiMaxFontSize: CGFloat = 36
    private let padEmojiMinButtonSize: CGFloat = 48
    private let padEmojiMaxButtonSize: CGFloat = 72
    private let padEmojiMinFontSize: CGFloat = 40
    private let padEmojiMaxFontSize: CGFloat = 56
    private let categoryIconSize: CGFloat = 32
    private let categoryTextWidth: CGFloat = 48
    private let categorySymbolSize: CGFloat = 22
    private let categorySpacing: CGFloat = 4
    private var categoryIconSpacerWidth: NSLayoutConstraint?
    private var visibleRows = 4
    private var emojiBottomToCategoryConstraint: NSLayoutConstraint?
    private var emojiBottomToPanelConstraint: NSLayoutConstraint?
    private var isSearchMode = false
    private var categoryButtons: [UIButton] = []
    private var returnKeyboardButton: UIButton?
    private var returnKeyTitle = EmojiPanelSource.english.returnKeyTitle
    private var activeCategoryIndex = 1
    private var lastRenderedWidth: CGFloat = 0
    private var lastRenderedViewportHeight: CGFloat = 0
    private var emojiContentOffsetX: CGFloat = 0
    private var emojiContentWidth: CGFloat = 0
    private var categoryStartDisplayPageIndexes: [Int] = []
    private var displayPageOffsets: [CGFloat] = []
    private var categoryStartDisplayOffsets: [CGFloat] = []
    private var displayEmojiPages: [[Mapping]] = []
    private var displayPageSourceIndexes: [Int] = []
    private var displayPageColumnCounts: [Int] = []
    private var renderedDisplayPageIndexes: Set<Int> = []
    private var cachedPagination: EmojiPanelPaginationResult?
    private var cachedPaginationCellsPerPage: Int = 0
    private var cachedPaginationRowsPerPage: Int = 0
    private var cachedPaginationCategoryButtonCount: Int = 0
    private var reusableEmojiLabels: [UILabel] = []

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    func setEmojis(_ emojis: [Mapping]) {
        self.emojiPages = [emojis]
        invalidatePaginationCache()
        rebuildEmojiButtons()
    }

    func setEmojiPages(_ pages: [[Mapping]]) {
        self.emojiPages = pages
        invalidatePaginationCache()
        if !isSearchMode {
            setEmojiContentOffset(0, animated: false)
            activeCategoryIndex = 1
        }
        rebuildEmojiButtons()
    }

    func setReturnKeyTitle(_ title: String) {
        returnKeyTitle = title
        returnKeyboardButton?.setTitle(title, for: .normal)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let width = bounds.width
        let viewportHeight = emojiViewport.bounds.height
        if abs(width - lastRenderedWidth) > 0.5 || abs(viewportHeight - lastRenderedViewportHeight) > 0.5 {
            rebuildEmojiButtons()
        }
        updateCategoryIconSpacer()
    }

    private func invalidatePaginationCache() {
        cachedPagination = nil
        cachedPaginationCellsPerPage = 0
        cachedPaginationCategoryButtonCount = 0
    }

    private func setup() {
        isOpaque = false
        backgroundColor = .clear

        searchField.placeholder = "搜尋表情符號"
        searchField.delegate = self
        searchField.autocorrectionType = .no
        searchField.autocapitalizationType = .none
        searchField.returnKeyType = .done
        searchField.translatesAutoresizingMaskIntoConstraints = false
        searchField.addTarget(self, action: #selector(searchChanged), for: .editingChanged)
        addSubview(searchField)

        emojiViewport.clipsToBounds = true
        emojiViewport.backgroundColor = .clear
        emojiViewport.alwaysBounceHorizontal = true
        emojiViewport.alwaysBounceVertical = false
        emojiViewport.showsHorizontalScrollIndicator = false
        emojiViewport.showsVerticalScrollIndicator = false
        emojiViewport.delaysContentTouches = false
        emojiViewport.delegate = self
        emojiViewport.translatesAutoresizingMaskIntoConstraints = false
        addSubview(emojiViewport)

        emojiContentView.backgroundColor = .clear
        emojiViewport.addSubview(emojiContentView)

        categoryScrollView.alwaysBounceHorizontal = true
        categoryScrollView.alwaysBounceVertical = false
        categoryScrollView.showsHorizontalScrollIndicator = false
        categoryScrollView.backgroundColor = .clear
        categoryScrollView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(categoryScrollView)

        let deleteConfig = UIImage.SymbolConfiguration(pointSize: categorySymbolSize, weight: .regular)
        categoryBackspaceButton.setImage(UIImage(systemName: "delete.backward", withConfiguration: deleteConfig), for: .normal)
        categoryBackspaceButton.tintColor = .label
        categoryBackspaceButton.imageView?.contentMode = .scaleAspectFit
        categoryBackspaceButton.layer.cornerRadius = categoryIconSize / 2
        categoryBackspaceButton.addTarget(self, action: #selector(tapBackspace), for: .touchUpInside)
        categoryBackspaceButton.translatesAutoresizingMaskIntoConstraints = false
        addSubview(categoryBackspaceButton)

        categoryBar.axis = .horizontal
        categoryBar.alignment = .center
        categoryBar.distribution = .fill
        categoryBar.spacing = categorySpacing
        categoryBar.translatesAutoresizingMaskIntoConstraints = false
        categoryScrollView.addSubview(categoryBar)
        categoryIconSpacer.translatesAutoresizingMaskIntoConstraints = false
        categoryIconSpacerWidth = categoryIconSpacer.widthAnchor.constraint(equalToConstant: 0)
        categoryIconSpacerWidth?.isActive = true

        let emojiBottomToCategory = emojiViewport.bottomAnchor.constraint(equalTo: categoryScrollView.topAnchor, constant: -8)
        let emojiBottomToPanel = emojiViewport.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -8)
        emojiBottomToCategoryConstraint = emojiBottomToCategory
        emojiBottomToPanelConstraint = emojiBottomToPanel

        NSLayoutConstraint.activate([
            searchField.topAnchor.constraint(equalTo: topAnchor, constant: 10),
            searchField.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            searchField.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),
            searchField.heightAnchor.constraint(equalToConstant: 44),

            categoryScrollView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 12),
            categoryScrollView.trailingAnchor.constraint(equalTo: categoryBackspaceButton.leadingAnchor, constant: -4),
            categoryScrollView.bottomAnchor.constraint(equalTo: safeAreaLayoutGuide.bottomAnchor, constant: -10),
            categoryScrollView.heightAnchor.constraint(equalToConstant: 48),

            categoryBackspaceButton.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -8),
            categoryBackspaceButton.centerYAnchor.constraint(equalTo: categoryScrollView.centerYAnchor),
            categoryBackspaceButton.widthAnchor.constraint(equalToConstant: categoryIconSize),
            categoryBackspaceButton.heightAnchor.constraint(equalToConstant: categoryIconSize),

            categoryBar.leadingAnchor.constraint(equalTo: categoryScrollView.contentLayoutGuide.leadingAnchor),
            categoryBar.trailingAnchor.constraint(equalTo: categoryScrollView.contentLayoutGuide.trailingAnchor),
            categoryBar.topAnchor.constraint(equalTo: categoryScrollView.contentLayoutGuide.topAnchor),
            categoryBar.bottomAnchor.constraint(equalTo: categoryScrollView.contentLayoutGuide.bottomAnchor),
            categoryBar.heightAnchor.constraint(equalTo: categoryScrollView.frameLayoutGuide.heightAnchor),

            emojiViewport.topAnchor.constraint(equalTo: searchField.bottomAnchor, constant: 10),
            emojiViewport.leadingAnchor.constraint(equalTo: leadingAnchor),
            emojiViewport.trailingAnchor.constraint(equalTo: trailingAnchor),
            emojiBottomToCategory,
        ])

        buildCategoryBar()
    }

    private func buildCategoryBar() {
        categoryBar.arrangedSubviews.forEach { $0.removeFromSuperview() }
        categoryButtons = []
        let abc = textButton(returnKeyTitle, action: #selector(tapABC))
        abc.tag = 0
        returnKeyboardButton = abc
        categoryButtons.append(abc)
        categoryBar.addArrangedSubview(abc)
        categoryBar.addArrangedSubview(categoryIconSpacer)
        ["clock", "face.smiling", "person.crop.circle", "pawprint", "apple.logo", "car",
         "soccerball", "lightbulb", "heart", "flag"].enumerated().forEach { index, symbol in
            let button = iconButton(symbol, action: #selector(tapCategory))
            button.tag = index + 1
            categoryButtons.append(button)
            categoryBar.addArrangedSubview(button)
        }
        updateCategoryHighlight()
    }

    func setSearchMode(_ enabled: Bool) {
        isSearchMode = enabled
        visibleRows = currentVisibleRows()
        categoryScrollView.isHidden = enabled
        categoryBackspaceButton.isHidden = enabled
        emojiBottomToCategoryConstraint?.isActive = !enabled
        emojiBottomToPanelConstraint?.isActive = enabled
        rebuildEmojiButtons()
    }

    func prepareForPresentation() {
        endEditing(true)
        setSearchMode(false)
        setEmojiContentOffset(0, animated: false)
        activeCategoryIndex = 1
        updateCategoryHighlight()
    }

    func resignSearch() {
        searchField.resignFirstResponder()
    }

    func handleSearchKey(code: Int) -> Bool {
        guard isSearchMode, searchField.isFirstResponder else { return false }
        switch code {
        case LimeKeyCode.delete.rawValue:
            guard !(searchField.text ?? "").isEmpty else { return true }
            searchField.text = String((searchField.text ?? "").dropLast())
        case LimeKeyCode.enter.rawValue, LimeKeyCode.done.rawValue:
            searchField.resignFirstResponder()
            return true
        case 32:
            searchField.text = (searchField.text ?? "") + " "
        case 33...126:
            guard let scalar = Unicode.Scalar(code) else { return false }
            searchField.text = (searchField.text ?? "") + String(scalar)
        default:
            return false
        }
        searchChanged()
        return true
    }

    private func rebuildEmojiButtons() {
        lastRenderedWidth = bounds.width
        lastRenderedViewportHeight = emojiViewport.bounds.height
        reusableEmojiLabels.append(contentsOf: emojiContentView.subviews.compactMap { $0 as? UILabel })
        emojiContentView.subviews.forEach { $0.removeFromSuperview() }
        buttonMappings = []
        visibleRows = currentVisibleRows()
        let rows = max(1, visibleRows)
        let pageWidth = emojiPageWidth()
        let pageHeight = emojiPageHeight()
        let buttonSize = emojiButtonSize()
        let columnsPerPage = isSearchMode ? 0 : normalModeColumnsPerPage(pageWidth: pageWidth)
        let horizontalInset = emojiHorizontalInset()
        let cellWidth = isSearchMode ? buttonSize : (pageWidth - horizontalInset * 2) / CGFloat(columnsPerPage)
        let cellsPerPage = max(1, columnsPerPage * rows)
        let pages: [[Mapping]]
        let pageSourceIndexes: [Int]
        let pageColumnCounts: [Int]
        if isSearchMode {
            pages = [emojiPages.flatMap { $0 }]
            categoryStartDisplayPageIndexes = []
            pageSourceIndexes = [0]
            pageColumnCounts = [Int(ceil(Double(pages.first?.count ?? 0) / Double(rows)))]
        } else {
            let pagination = pagination(cellsPerPage: cellsPerPage, rowsPerPage: rows)
            pages = pagination.pages
            categoryStartDisplayPageIndexes = pagination.categoryStartDisplayPageIndexes
            pageSourceIndexes = pagination.sourcePageIndexes
            pageColumnCounts = pagination.columnCounts
        }
        displayEmojiPages = pages
        displayPageSourceIndexes = pageSourceIndexes
        displayPageColumnCounts = pageColumnCounts
        displayPageOffsets = EmojiPanelScrollLayout.unitOffsets(columnCounts: pageColumnCounts,
                                                                cellWidth: cellWidth)
        categoryStartDisplayOffsets = categoryStartDisplayPageIndexes.map { pageIndex in
            pageIndex < displayPageOffsets.count ? displayPageOffsets[pageIndex] : 0
        }
        if isSearchMode {
            let columns = Int(ceil(Double(pages.first?.count ?? 0) / Double(rows)))
            emojiContentWidth = max(pageWidth, CGFloat(columns) * buttonSize + 24)
        } else {
            emojiContentWidth = EmojiPanelScrollLayout.contentWidth(unitOffsets: displayPageOffsets,
                                                                    columnCounts: pageColumnCounts,
                                                                    cellWidth: cellWidth,
                                                                    horizontalInset: horizontalInset,
                                                                    viewportWidth: pageWidth)
        }
        emojiContentOffsetX = min(max(emojiContentOffsetX, 0), maxEmojiContentOffsetX())
        let pageIndexes = renderPageIndexes(pageWidth: pageWidth)
        renderedDisplayPageIndexes = Set(pageIndexes)
        for pageIndex in pageIndexes {
            let page = pages[pageIndex]
            let pageOffsetX = pageIndex < displayPageOffsets.count
                ? displayPageOffsets[pageIndex]
                : CGFloat(pageIndex) * pageWidth
            let pageRows: Int
            let pageButtonSize: CGFloat
            if isSearchMode {
                pageRows = rows
                pageButtonSize = buttonSize
            } else if pageIndex == 0 {
                pageRows = max(rows, Int(ceil(pageHeight / buttonSize)))
                pageButtonSize = buttonSize
            } else {
                pageRows = rows
                pageButtonSize = emojiButtonSize(forRows: pageRows)
            }
            let columnsForPage = pageIndex < pageColumnCounts.count
                ? max(pageColumnCounts[pageIndex], 1)
                : max(1, Int(ceil(Double(page.count) / Double(max(pageRows, 1)))))
            let visibleCellCount = isSearchMode
                ? page.count
                : max(page.count, columnsForPage * pageRows)
            for index in 0..<visibleCellCount {
                let column: Int
                let row: Int
                if isSearchMode {
                    column = index / rows
                    row = index % rows
                } else {
                    let position = EmojiPanelScrollLayout.cellPosition(index: index, rows: pageRows)
                    column = position.column
                    row = position.row
                }
                let button = dequeueEmojiLabel()
                let isRealEmoji = index < page.count
                button.text = isRealEmoji ? page[index].word : "\u{25CF}"
                button.font = UIFont.systemFont(ofSize: emojiFontSize(for: pageButtonSize))
                button.textAlignment = .center
                if isRealEmoji {
                    button.tag = buttonMappings.count
                    button.textColor = .label
                    button.backgroundColor = .clear
                    buttonMappings.append(page[index])
                } else {
                    button.tag = -1
                    button.textColor = UIColor.label.withAlphaComponent(0.001)
                    button.backgroundColor = UIColor.label.withAlphaComponent(0.001)
                }
                button.frame = CGRect(x: EmojiPanelScrollLayout.cellX(pageOffsetX: pageOffsetX,
                                                                       column: column,
                                                                       cellWidth: cellWidth,
                                                                       horizontalInset: horizontalInset),
                                      y: CGFloat(row) * pageButtonSize,
                                      width: cellWidth,
                                      height: pageButtonSize)
                emojiContentView.addSubview(button)
            }
        }
        if reusableEmojiLabels.count > 160 {
            reusableEmojiLabels.removeFirst(reusableEmojiLabels.count - 160)
        }
        layoutEmojiContent(height: pageHeight)
        updateCategoryHighlightForScroll()
    }

    private func dequeueEmojiLabel() -> UILabel {
        if let label = reusableEmojiLabels.popLast() {
            return label
        }
        let label = UILabel()
        label.isUserInteractionEnabled = true
        label.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(tapEmojiLabel(_:))))
        return label
    }

    private func pagination(cellsPerPage: Int, rowsPerPage: Int) -> EmojiPanelPaginationResult {
        if let cachedPagination,
           cachedPaginationCellsPerPage == cellsPerPage,
           cachedPaginationRowsPerPage == rowsPerPage,
           cachedPaginationCategoryButtonCount == categoryButtons.count {
            return cachedPagination
        }
        let pagination = EmojiPanelPaginator.displayPages(sourcePages: emojiPages,
                                                          cellsPerPage: cellsPerPage,
                                                          rowsPerPage: rowsPerPage,
                                                          categoryButtonCount: categoryButtons.count)
        cachedPagination = pagination
        cachedPaginationCellsPerPage = cellsPerPage
        cachedPaginationRowsPerPage = rowsPerPage
        cachedPaginationCategoryButtonCount = categoryButtons.count
        return pagination
    }

    private func renderPageIndexes(pageWidth: CGFloat) -> [Int] {
        guard !displayEmojiPages.isEmpty else { return [] }
        guard !isSearchMode else { return [0] }
        let buffer = pageWidth
        let visibleStart = emojiContentOffsetX - buffer
        let visibleEnd = emojiContentOffsetX + pageWidth + buffer
        return displayEmojiPages.indices.filter { index in
            let start = index < displayPageOffsets.count ? displayPageOffsets[index] : CGFloat(index) * pageWidth
            let columnCount = index < displayPageColumnCounts.count ? max(displayPageColumnCounts[index], 1) : 1
            let cellWidth = normalModeCellWidth(pageWidth: pageWidth)
            let end = start + CGFloat(columnCount) * cellWidth + emojiHorizontalInset() * 2
            return end >= visibleStart && start <= visibleEnd
        }
    }

    private func needsRenderForCurrentOffset() -> Bool {
        guard !isSearchMode else { return false }
        return Set(renderPageIndexes(pageWidth: emojiPageWidth())) != renderedDisplayPageIndexes
    }

    private func emojiPageWidth() -> CGFloat {
        max(emojiViewport.bounds.width, bounds.width, 1)
    }

    private func emojiPageHeight() -> CGFloat {
        max(emojiViewport.bounds.height, CGFloat(max(1, currentVisibleRows())) * emojiButtonSize())
    }

    private func usesPadEmojiLayout() -> Bool {
        traitCollection.userInterfaceIdiom == .pad || bounds.width >= 700
    }

    private func currentVisibleRows() -> Int {
        guard !isSearchMode else { return 1 }
        let availableHeight = emojiViewport.bounds.height
        let preferredRows = usesPadEmojiLayout() ? 5 : 4
        guard availableHeight > 1 else { return preferredRows }
        let minimumButtonSize = usesPadEmojiLayout() ? padEmojiMinButtonSize : phoneEmojiMinButtonSize
        return availableHeight >= minimumButtonSize * CGFloat(preferredRows) ? preferredRows : max(preferredRows - 1, 3)
    }

    private func emojiButtonSize() -> CGFloat {
        emojiButtonSize(forRows: currentVisibleRows())
    }

    private func emojiButtonSize(forRows rows: Int) -> CGFloat {
        guard !isSearchMode else { return emojiFallbackButtonSize }
        let availableHeight = emojiViewport.bounds.height
        guard availableHeight > 1 else { return emojiFallbackButtonSize }
        let rows = CGFloat(max(rows, 1))
        let minButtonSize = usesPadEmojiLayout() ? padEmojiMinButtonSize : phoneEmojiMinButtonSize
        let maxButtonSize = usesPadEmojiLayout() ? padEmojiMaxButtonSize : phoneEmojiMaxButtonSize
        return clamped(floor(availableHeight / rows),
                       lower: minButtonSize,
                       upper: maxButtonSize)
    }

    private func emojiFontSize() -> CGFloat {
        emojiFontSize(for: emojiButtonSize())
    }

    private func emojiFontSize(for buttonSize: CGFloat) -> CGFloat {
        if usesPadEmojiLayout() && !isSearchMode {
            let fontSize = buttonSize * 0.78
            return clamped(fontSize, lower: padEmojiMinFontSize, upper: padEmojiMaxFontSize)
        }
        let fontSize = buttonSize * 0.9
        return clamped(fontSize, lower: phoneEmojiMinFontSize, upper: phoneEmojiMaxFontSize)
    }

    private func emojiHorizontalInset() -> CGFloat {
        guard usesPadEmojiLayout() && !isSearchMode else { return 12 }
        return clamped(emojiPageWidth() * 0.025, lower: 24, upper: 48)
    }

    private func normalModeColumnsPerPage(pageWidth: CGFloat) -> Int {
        let targetCellWidth = usesPadEmojiLayout() ? max(emojiButtonSize() * 1.95, 116) : emojiButtonSize()
        let fitted = Int((pageWidth - emojiHorizontalInset() * 2) / targetCellWidth)
        return usesPadEmojiLayout() ? min(10, max(8, fitted)) : min(10, max(7, fitted))
    }

    private func normalModeCellWidth(pageWidth: CGFloat) -> CGFloat {
        let horizontalInset = emojiHorizontalInset()
        let columnsPerPage = normalModeColumnsPerPage(pageWidth: pageWidth)
        return (pageWidth - horizontalInset * 2) / CGFloat(columnsPerPage)
    }

    private func clamped(_ value: CGFloat, lower: CGFloat, upper: CGFloat) -> CGFloat {
        min(max(value, lower), upper)
    }

    private func updateCategoryIconSpacer() {
        guard let widthConstraint = categoryIconSpacerWidth else { return }
        let iconCount = max(categoryButtons.count - 1, 0)
        let iconsWidth = CGFloat(iconCount) * categoryIconSize
            + CGFloat(max(iconCount - 1, 0)) * categorySpacing
        let centeredIconStart = max(0, (categoryScrollView.bounds.width - iconsWidth) / 2)
        let abcAndSpacingWidth = categoryTextWidth + categorySpacing * 2
        let targetSpacer = max(0, centeredIconStart - abcAndSpacingWidth)
        if abs(widthConstraint.constant - targetSpacer) > 0.5 {
            widthConstraint.constant = targetSpacer
            categoryBar.setNeedsLayout()
        }
    }

    private func maxEmojiContentOffsetX() -> CGFloat {
        max(0, emojiContentWidth - max(emojiViewport.bounds.width, 1))
    }

    private func layoutEmojiContent(height: CGFloat? = nil) {
        let contentHeight = height ?? emojiContentView.frame.height
        emojiContentView.frame = EmojiPanelScrollLayout.contentFrame(
            viewportWidth: max(emojiViewport.bounds.width, 1),
            contentWidth: emojiContentWidth,
            contentHeight: contentHeight)
        emojiViewport.contentSize = CGSize(width: emojiContentWidth, height: contentHeight)
        let clampedX = min(emojiViewport.contentOffset.x, maxEmojiContentOffsetX())
        if abs(emojiViewport.contentOffset.x - clampedX) > 0.5 {
            emojiViewport.setContentOffset(CGPoint(x: clampedX, y: 0), animated: false)
        }
    }

    private func setEmojiContentOffset(_ offsetX: CGFloat, animated: Bool) {
        let clampedX = min(max(offsetX, 0), maxEmojiContentOffsetX())
        emojiContentOffsetX = clampedX
        emojiViewport.setContentOffset(CGPoint(x: clampedX, y: 0), animated: animated)
        if needsRenderForCurrentOffset() {
            rebuildEmojiButtons()
            return
        }
        updateCategoryHighlightForScroll()
    }

    private func textButton(_ title: String, action: Selector) -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(title, for: .normal)
        button.titleLabel?.font = UIFont.systemFont(ofSize: 22, weight: .regular)
        button.tintColor = .label
        button.addTarget(self, action: action, for: .touchUpInside)
        button.layer.cornerRadius = categoryIconSize / 2
        button.widthAnchor.constraint(equalToConstant: categoryTextWidth).isActive = true
        button.heightAnchor.constraint(equalToConstant: categoryIconSize).isActive = true
        return button
    }

    private func iconButton(_ symbol: String, action: Selector) -> UIButton {
        let button = UIButton(type: .system)
        let config = UIImage.SymbolConfiguration(pointSize: categorySymbolSize, weight: .regular)
        button.setImage(UIImage(systemName: symbol, withConfiguration: config), for: .normal)
        button.tintColor = .label
        button.imageView?.contentMode = .scaleAspectFit
        button.addTarget(self, action: action, for: .touchUpInside)
        button.layer.cornerRadius = categoryIconSize / 2
        button.widthAnchor.constraint(equalToConstant: categoryIconSize).isActive = true
        button.heightAnchor.constraint(equalToConstant: categoryIconSize).isActive = true
        return button
    }

    @objc private func searchChanged() {
        delegate?.emojiPanelView(self, didChangeSearchQuery: searchField.text ?? "")
    }

    @objc private func tapEmojiLabel(_ sender: UITapGestureRecognizer) {
        guard let hitView = sender.view,
              hitView.tag >= 0,
              hitView.tag < buttonMappings.count else { return }
        delegate?.emojiPanelView(self, didSelect: buttonMappings[hitView.tag])
    }

    @objc private func tapABC() {
        delegate?.emojiPanelViewDidRequestABC(self)
    }

    @objc private func tapBackspace() {
        delegate?.emojiPanelViewDidRequestBackspace(self)
    }

    @objc private func tapCategory(_ sender: UIButton) {
        activeCategoryIndex = sender.tag
        updateCategoryHighlight()
        let targetX = sender.tag < categoryStartDisplayOffsets.count
            ? categoryStartDisplayOffsets[sender.tag]
            : CGFloat(max(sender.tag - 1, 0)) * emojiPageWidth()
        setEmojiContentOffset(targetX, animated: true)
    }

    private func updateCategoryHighlightForScroll() {
        guard !isSearchMode else { return }
        var active = 1
        for (index, startOffset) in categoryStartDisplayOffsets.enumerated()
            where index > 0 && startOffset <= emojiContentOffsetX + 0.5 {
            active = index
        }
        let nextActive = min(max(active, 1), max(categoryButtons.count - 1, 1))
        guard nextActive != activeCategoryIndex else { return }
        activeCategoryIndex = nextActive
        updateCategoryHighlight()
    }

    private func updateCategoryHighlight() {
        for button in categoryButtons {
            let active = button.tag == activeCategoryIndex
            button.backgroundColor = active ? UIColor.label.withAlphaComponent(0.14) : .clear
            button.tintColor = .label
        }
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        guard scrollView === emojiViewport else { return }
        emojiContentOffsetX = min(max(scrollView.contentOffset.x, 0), maxEmojiContentOffsetX())
        if needsRenderForCurrentOffset() {
            rebuildEmojiButtons()
            return
        }
        updateCategoryHighlightForScroll()
    }

    func textFieldDidBeginEditing(_ textField: UITextField) {
        delegate?.emojiPanelViewDidBeginSearch(self)
    }

    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        textField.resignFirstResponder()
        return true
    }
}
