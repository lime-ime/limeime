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
// Full keyboard extension entry point.
// Implements IMService behavior per IM_SERVICE.md spec.

final class KeyboardViewController: UIInputViewController, UIInputViewAudioFeedback {

    var enableInputClicksWhenVisible: Bool { true }
    // ponytail: fixed DB setup retries; make configurable only if device logs show startup flakiness.
    private static let databaseSetupAttempts = 3
    private static let databaseSetupRetryDelay: TimeInterval = 0.15

    static func isForcedEnglishKeyboardType(_ keyboardType: UIKeyboardType) -> Bool {
        KeyboardTypePolicy.isForcedEnglishKeyboardType(keyboardType)
    }

    // MARK: - Components
    private var candidateBar: CandidateBarView!
    private var keyboardView:  KeyboardView!
    private var emojiPanelView: EmojiPanelView?
    private var emojiPanelSource: EmojiPanelSource = .english
    private let syncQueue = DispatchQueue(label: "org.limeime.keyboard.sync", qos: .utility)
    private var syncScanInProgress = false
    private var tablesUpdatedObserver: SyncSignalObserver?

    // MARK: - SearchServer
    private var searchServer: SearchServer?

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
    private var lastShiftTapTime: TimeInterval = 0
    private var activeIM:     String = "phonetic" {
        didSet { imConfigCache.removeAll() }   // per-IM im.json values; re-read once after any IM change
    }
    /// Cached `imkeys` for the active IM (refreshed on every setTableName).
    /// When present, characters outside this string route to direct output; empty
    /// keeps the legacy hasSymbol/hasNumber fallback for unaudited IMs.
    var currentImKeys: String = ""
    /// Per-IM `im.json` values (imkeys/imkeynames/limeendkey) cached for the compose
    /// hot path. Reading them fresh per keystroke cost a `stat()` on im.json every
    /// stroke (see docs/IOS_PROFILING_V2.md §4). Cleared on any activeIM change.
    private var imConfigCache: [String: String] = [:]
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
    private var keypressSoundVolume:     String = "-1"
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
    /// Scroll-view top offset (= first-row height) so the candidate grid scrolls BELOW the fixed
    /// first row, which never moves — nothing slides under the composing strip / dismiss on scroll.
    private var expandedScrollTopConstraint: NSLayoutConstraint?
    /// Fixed first-row header: holds row-0 candidate buttons at the exact collapsed-bar geometry
    /// (full height + strip bias) so expanding reads as the bar growing in place.
    private var expandedFirstRowContainer: UIView?
    private var expandedFirstRowHeightConstraint: NSLayoutConstraint?
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
    private var expandedMoreSepCenterYConstraint: NSLayoutConstraint?
    /// 1-pt vertical divider sitting just left of the expanded panel's collapse chevron,
    /// mirroring CandidateBarView.moreSep so the reserved zone matches the bar exactly.
    private var expandedMoreSep: UIView?
    /// Dismiss (✕) button at the panel's top-left — mirrors CandidateBarView.dismissButton.
    private var expandedDismissButton: UIButton?
    private var expandedDismissCenterYConstraint: NSLayoutConstraint?
    private var expandedDismissHeightConstraint: NSLayoutConstraint?
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
    private let databaseQueue = DispatchQueue(label: "org.limeime.keyboard.database", qos: .userInitiated)
    private var isKeyboardVisible = false

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
    private var didAnswerRelayThisAppearance = false
    private let relayPrefStore = KeyboardRelayPrefStore()
    /// §1.8: the keyboard-owned hot store for the 3 two-writer hamburger prefs
    /// (han_convert_option / split_keyboard_mode / <im>_im_reverselookup). Backed by the
    /// extension-private `UserDefaults.standard`, NOT the App Group — so a stale cold value
    /// can never clobber a hamburger change. Fed by the hamburger (direct) and the pref
    /// inbox drain; seeded once from cold on first run. Cold is never read for these three.
    private let hotPrefs = LIMEPreferenceManager(defaults: .standard)

    // MARK: - English Pick-Space Punctuation Swap (ENGLISH_KB.md #0 / §2a)
    // After an English suggestion pick auto-appends a space (word ), typing punctuation
    // should produce "word, " not "word ,". Replicates AOSP LatinIME. Character classes
    // from donottranslate-config-spacing-and-punctuations.xml (en_US).
    private static let engSwapFollowedBySpace: Set<Character> = [".", ",", ";", ":", "!", "?", ")", "]", "}"]
    private static let engSwapPrecededBySpace: Set<Character> = ["(", "[", "{"]
    private static let engSwapStrip: Set<Character> = ["-", "/", "@", "_", "'"]
    // True only immediately after an English suggestion pick auto-appended a space.
    private var pickedAutoSpace = false

    // MARK: - Key Preview
    private enum KeyPreviewContentMode {
        case single
        case dual
    }

    private var keyPreviewView: UIView?
    private var keyPreviewShapeLayer: CAShapeLayer?
    private var keyPreviewSingleLabel: UILabel?
    private var keyPreviewDualStack: UIStackView?
    private var keyPreviewDualPrimaryLabel: UILabel?
    private var keyPreviewDualSubLabel: UILabel?
    private var keyPreviewSingleWidthConstraint: NSLayoutConstraint?
    private var keyPreviewDualWidthConstraint: NSLayoutConstraint?
    private var keyPreviewContentMode: KeyPreviewContentMode?
    private var keyPreviewAnimationID = 0

    /// True when the **host app** is iPad-class. iPhone-only apps running on iPad
    /// in scaled/compatibility mode report `.phone` here even though the device is
    /// an iPad — we must follow the host so the keyboard matches the host UI.
    /// (`UIDevice.current.userInterfaceIdiom` is the wrong signal here.)
    private var isOnPad: Bool { traitCollection.userInterfaceIdiom == .pad }

    @discardableResult
    private func syncLayoutEnvironmentFromTraits() -> Bool {
        let oldHostIsPad = LayoutLoader.hostIsPad
        let oldTier = LayoutLoader.iPadSizeClass

        LayoutLoader.hostIsPad = isOnPad
        if isOnPad {
            let screen = UIScreen.main.bounds
            LayoutLoader.iPadSizeClass = IPadSizeClass.resolve(shortSideExtent: min(screen.width, screen.height))
        } else {
            LayoutLoader.iPadSizeClass = .large
        }

        let changed = oldHostIsPad != LayoutLoader.hostIsPad || oldTier != LayoutLoader.iPadSizeClass
        if changed {
            LayoutLoader.clearCache()
        }
        return changed
    }

    private func englishLayoutId() -> String {
        isOnPad ? "lime_english" : (numberRowInEnglish ? "lime_english_number" : "lime_english")
    }

    /// True when iOS reports it cannot supply a globe key for us (legacy
    /// home-button iPhones: SE 2/3, 8). Drives the in-keyboard globe affordance
    /// (spec: docs/IPHONE_LEGACY_KB.md). Excludes iPad and any `_ipad` layout
    /// so the existing dual-key iPad story (`-200` globe + `-3` dismiss) is
    /// untouched.
    private var legacyGlobeMode: Bool {
        needsInputModeSwitchKey
            && !isOnPad
            && !currentLayout.id.contains("_ipad")
    }

    // MARK: - Keyboard Geometry
    private var baseCandidateBarHeight: CGFloat { LayoutMetrics.ComposingPopup.barBaseHeight(isPad: isOnPad) }
    private var candidateBarHeight: CGFloat { baseCandidateBarHeight * candidateFontScale }
    private var activeCandidateBarHeight: CGFloat {
        candidateBarHeight
    }
    private var emojiSearchHeaderHeight: CGFloat {
        guard isEmojiSearchMode, candidateBar != nil else { return 0 }
        return EmojiPanelView.searchHeaderHeight
    }
    private var isEmojiPanelVisible: Bool {
        emojiPanelView?.isHidden == false
    }
    private var candidateBarTopConstraint: NSLayoutConstraint?
    private var candidateBarHeightConstraint: NSLayoutConstraint?
    private var keyboardTopToCandidateConstraint: NSLayoutConstraint?
    private var keyboardTopToViewConstraint: NSLayoutConstraint?
    private var emojiPanelBottomConstraint: NSLayoutConstraint?
    private var emojiSearchHeaderView: UIView?
    private var emojiSearchField: UISearchTextField?
    private var emojiSearchFieldHeightConstraint: NSLayoutConstraint?
    private var isEmojiSearchMode = false
    private var emojiSearchEnglishOnly = false
    private var emojiSearchSourceLayout: LimeKeyLayout?
    private var emojiSearchCandidates: [Mapping] = []
    // keyRowHeight removed — height is now driven by KeyboardView.preferredHeight,
    // which sums actual per-row heights (54 pt regular, 56 pt bottom row).
    // #139: this is the CONTENT height constraint (on keyboardView, not view) —
    // the extension view's height is derived from the subview chain. An explicit
    // view.heightAnchor constant made iOS latch a stale keyboard frame on rotation.
    private var keyboardHeightConstraint: NSLayoutConstraint?
    /// #139: true from rotation start until ~0.3 s after the coordinator
    /// completes. While set, applyHeight() must NOT move the height constraint:
    /// any height change inside the rotation transaction gets baked into iOS's
    /// stale keyboard-frame notification with no later correction. The deferred
    /// applyHeight() after settling is a plain stationary height change, which
    /// hosts track correctly (same path as keyboard_size / emoji-panel resizes).
    private var rotationSettling = false
    private weak var inlineMenuPanel: UIView?
    private weak var inlineMenuDismissTapGesture: UITapGestureRecognizer?
    /// Fired once when the inline menu is dismissed (完成 / tap-outside). Used by the
    /// long-press menu to apply its inline segmented choices (漢字轉換 / 分離鍵盤) on
    /// dismiss, matching the Android keyboard menu's apply-on-dismiss model.
    private var inlineMenuOnDismiss: (() -> Void)?
    private weak var keyboardTopCoverView: UIView?
    private var keyboardHostCoverHeight: CGFloat { isOnPad ? 0 : 12 }

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        // Tell LayoutLoader whether the **host app** is iPad-class BEFORE any
        // load() call. iPhone-only apps running on iPad must use phone layouts.
        _ = syncLayoutEnvironmentFromTraits()
        LayoutLoader.clearCache()
        // Load size/font prefs from UserDefaults synchronously so candidateFontScale
        // and keyboardSize are correct when setupKeyboardUI() creates its height
        // constraints. searchServer is nil here so applyPrefsToSearchEngine() is a no-op.
        loadSettings()
        // English runtime layout is preference-driven; legacy KeyboardConfig engkb fields are DB compatibility data only.
        let _initLayout = englishLayoutId()
        if let loaded = LayoutLoader.load(_initLayout) { currentLayout = cj4SemicolonAdjusted(loaded) }
        LayoutLoader.prefetchCommonLayouts()
        setupKeyboardUI()
        applyHeight()
        reportFullAccessStatus()
        tablesUpdatedObserver = SyncSignalObserver(signal: .tablesUpdated) { [weak self] in
            self?.triggerSyncScan()
        }
        // Run DB setup off the main thread — avoids blocking the keyboard's view
        // lifecycle and prevents the Settings watchdog from killing the Preferences
        // app (0x8BADF00D) when it presents the keyboard extension for the first time.
        databaseQueue.async { [weak self] in
            self?.setupDatabase()
        }
    }

    /// Called every time the keyboard becomes visible (spec §2 initOnStartInput).
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        isKeyboardVisible = true
        didAnswerRelayThisAppearance = false
        reportFullAccessStatus()
        triggerSyncScan()
        drainPrefInbox()          // §1.8: apply app→kb pref changes BEFORE answering the relay
        scheduleRelayResponse()
        initOnStartInput()
    }

    private func triggerSyncScan() {
        // App Group container access does NOT require Full Access (confirmed: master read
        // the App Group DB + shared prefs FA-off). So cold→hot sync must run regardless of
        // FA — otherwise FA-off installs land in cold and never reach the keyboard's hot DB,
        // leaving no active IM. See IOS_DB_COLD_HOT.md §1.0.2.
        // Feedback while a restore/install sync runs: if there is no active IM yet, show
        // "同步中，請稍侯…" so the keyboard isn't just blank. Resolved when the scan ends.
        DispatchQueue.main.async { [weak self] in self?.beginSyncToastIfNeeded() }
        // Capture FA on the main thread (UIInputViewController property); the App Group
        // writers inside scanAndApply (backup / editor receipt) are FA-on only (§1.5 Fix 3).
        let fa = hasFullAccess
        syncQueue.async { [weak self] in
            guard let self else { return }
            guard !self.syncScanInProgress else { return }
            self.syncScanInProgress = true
            defer { self.syncScanInProgress = false }
            let locator = SyncDatabaseLocator.production()
            do {
                // Only ensure the hot DB file exists (no-op when it does) — do NOT rebuild
                // the SearchServer here. prepareKeyboardRuntimeDatabase() reseeds the shared
                // LimeDB.currentTableName to the first activated IM, clobbering the active
                // keyboard's query table on every appearance (§1.7 Rule 1). scanAndApply's
                // own generation/epoch check bails fast when nothing changed; the full
                // rebuild happens only when a sync applies, via setupDatabase() below.
                try DBServer.shared.ensureDatabaseFileReady()
                let applied = try TableSyncEngine(locator: locator).scanAndApply(hasFullAccess: fa)
                if applied {
                    // The sync changed hot's IM data (e.g. a newly-installed IM). Reload
                    // activatedIMs + re-apply keyboard_list so the IM shows on THIS
                    // appearance — otherwise activatedIMs (loaded once) stays stale and the
                    // installed IM never appears. setupDatabase re-prepares + reloads
                    // (and resolves the sync toast at the end of its main-thread block).
                    self.setupDatabase()
                } else if DBServer.shared.hotFileReplacedSinceOpen() {
                    // Converged, but ANOTHER keyboard process (the Settings sync-probe, or the
                    // keyboard in a different host app) ran the full-replace that landed this
                    // restore/install — moving hot lime.db to a new inode. scanAndApply used
                    // fresh connections so it saw the new file (→ converged, applied=false), but
                    // THIS warm process's long-lived datasource is still bound to the old,
                    // now-unlinked inode → zero rows → empty IM picker (#86, cross-process; the
                    // "restore then switch to Safari shows 同步中 + no active IM" bug). Rebind to
                    // the swapped-in file, then reload activatedIMs so the picker fills.
                    DBServer.shared.reopenDatabaseFromDisk()
                    self.setupDatabase()
                } else {
                    DispatchQueue.main.async { [weak self] in
                        // Resolve only once the initial setup has loaded activatedIMs. On a
                        // fresh process it hasn't yet — setupDatabase (viewDidLoad) resolves the
                        // toast when it lands; resolving here off a still-empty activatedIMs
                        // would clobber "同步中" with "no active IM" (the first-empty race).
                        guard let self, self.didCompleteInitialSetup else { return }
                        self.resolveSyncToast()
                    }
                }
            } catch {
                UserDefaults.standard.set(error.localizedDescription,
                                          forKey: "keyboard_db_last_error")
                DispatchQueue.main.async { [weak self] in self?.resolveSyncToast() }
            }
            // The cold→hot scan attempt is done (hot is synced). Ring the app so a sync probe
            // dismisses now — no fixed hold, and the sync is guaranteed complete first. Darwin
            // is name-only; the seq for GC rides the relay, not this.
            postSyncSignal(.syncScanDone)
        }
    }

    private func reportFullAccessStatus() {
        let currentHasFullAccess = hasFullAccess
        let now = Date().timeIntervalSince1970
        let lastDBError = UserDefaults.standard.string(forKey: "keyboard_db_last_error")

        let localDefaults = UserDefaults.standard
        localDefaults.set(true, forKey: "keyboard_extension_loaded")
        localDefaults.set(currentHasFullAccess, forKey: "keyboard_has_full_access")
        localDefaults.set(now, forKey: "keyboard_last_seen_at")
        if let lastDBError, !lastDBError.isEmpty {
            localDefaults.set(lastDBError, forKey: "keyboard_db_last_error")
        } else {
            localDefaults.removeObject(forKey: "keyboard_db_last_error")
        }
        localDefaults.synchronize()

        postSyncSignal(currentHasFullAccess ? .faOn : .faOff)
        guard let baseURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: LIMEPreferenceManager.suiteName),
              let data = try? JSONEncoder().encode(KeyboardHeartbeat(
                hasFullAccess: currentHasFullAccess,
                lastSeenAt: now,
                lastDBError: lastDBError))
        else { return }
        try? atomicWrite(data, to: SyncPaths.heartbeat(baseURL))
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        disableKeyboardWindowTouchDelay()
        scheduleRelayResponse()
    }

    /// The app's invisible probe *loads* the keyboard (viewWillAppear fires — that's the
    /// FA ping) but may never *present* it, so viewDidAppear can be skipped entirely.
    /// textDocumentProxy is already connected at viewWillAppear, so answer from there too.
    /// Poll because iOS populates documentContextBeforeInput asynchronously and a token set
    /// programmatically by the app fires no textDidChange; the once-per-appearance guard
    /// makes repeats no-ops.
    private func scheduleRelayResponse() {
        answerRelayRequestIfNeeded()
        for delay in [0.05, 0.15, 0.3, 0.6, 1.0, 1.5] as [Double] {
            DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
                self?.answerRelayRequestIfNeeded()
            }
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        disableKeyboardWindowTouchDelay()
    }

    private func disableKeyboardWindowTouchDelay() {
        view.window?.gestureRecognizers?.forEach { $0.delaysTouchesBegan = false }
    }

    /// Called every time the keyboard is dismissed — equivalent to Android postFinishInput().
    /// Triggers deferred Tier 2 learning: RP learning + LD phrase learning (spec §9, §13.5).
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        isKeyboardVisible = false
        keyboardView?.cancelActiveInteractions()
        dismissPopupKeyboard()
        // Detach window-attached composing popup so it doesn't linger after
        // the keyboard is dismissed.
        hideComposingPopup()
        teardownKeyPreview()
        // postFinishInput() snapshots scorelist + ldPhraseListArray and dispatches to background
        // internally — no outer async wrapper needed.
        searchServer?.postFinishInput()
    }

#if DEBUG
    func installKeyboardViewForTesting(_ keyboardView: KeyboardView) {
        self.keyboardView = keyboardView
    }
#endif

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
        if syncLayoutEnvironmentFromTraits() {
            if currentLayout.id != "__unset__",
               let reloaded = LayoutLoader.load(currentLayout.id) {
                let adjusted = cj4SemicolonAdjusted(reloaded)
                currentLayout = adjusted
                keyboardView?.setLayout(adjusted)
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
        updateGlobeAndDismissBindings()
    }

    // #139: hold the current keyboard height through the entire rotation
    // transaction, then apply the new orientation's height as a plain
    // stationary change once iOS has settled. Height changes made DURING the
    // transaction get baked into iOS's keyboard-frame notification at a stale/
    // interpolated value with no later correction (probe-verified across ten
    // variants); stationary changes go through the normal publish path that
    // hosts track correctly. See docs/#139_ISSUE.md.
    override func viewWillTransition(to size: CGSize,
                                     with coordinator: UIViewControllerTransitionCoordinator) {
        super.viewWillTransition(to: size, with: coordinator)
        rotationSettling = true
        coordinator.animate(alongsideTransition: nil) { [weak self] _ in
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                guard let self else { return }
                self.rotationSettling = false
                self.applyHeight()
            }
        }
    }

    // MARK: - Trait / Theme Change (spec §2)

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        // Host idiom may flip when the user moves an iPhone-only app between
        // iPad multitasking modes; resync LayoutLoader so the next load() picks
        // the correct iPad/iPhone variant, and push the new value into the views.
        if syncLayoutEnvironmentFromTraits() {
            if let reloaded = LayoutLoader.load(currentLayout.id) {
                let adjusted = cj4SemicolonAdjusted(reloaded)
                currentLayout = adjusted
                keyboardView?.setLayout(adjusted)
            }
            updateGlobeAndDismissBindings()
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
            updateGlobeAndDismissBindings()
            applyHeight()
        }
    }

    override func textWillChange(_ textInput: UITextInput?) {
        // Nothing — detection deferred to textDidChange
    }

    override func textDidChange(_ textInput: UITextInput?) {
        // Guard: skip checks triggered by our own insertText/deleteBackward (spec §12)
        guard !isSelfUpdate else { return }
        if answerRelayRequestIfNeeded() { return }

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
            updateGlobeAndDismissBindings()
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

        // Re-evaluate legacyGlobeMode on every text-input change. iOS toggles
        // `needsInputModeSwitchKey` on hardware-keyboard attach/detach and on
        // some field transitions; without this call the legacy globe binding
        // would stick until the next layout pass (spec: docs/IPHONE_LEGACY_KB.md
        // § Risks/pitfalls — first-tap latency).
        updateGlobeAndDismissBindings()
    }

    @discardableResult
    private func answerRelayRequestIfNeeded() -> Bool {
        guard !didAnswerRelayThisAppearance,
              isRelayRequestContext(before: textDocumentProxy.documentContextBeforeInput,
                                    after: textDocumentProxy.documentContextAfterInput)
        else { return false }

        didAnswerRelayThisAppearance = true
        isSelfUpdate = true
        defer { isSelfUpdate = false }
        let prefs = (try? relayPrefStore.read())
            ?? RelayPrefState(hanConvert: hanConvertOption,
                              splitKeyboard: splitKeyboardMode,
                              updatedAt: 0)
        textDocumentProxy.insertText(encodeRelayPayload(faOn: hasFullAccess,
                                                        ts: Date().timeIntervalSince1970,
                                                        prefs: prefs))
        return true
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

        // Re-read keyboard_theme so a theme change made in Settings while the keyboard
        // extension stayed warm takes effect on the next keyboard show. viewDidLoad reads
        // it only once per process; without this, the keyboard keeps the theme it loaded
        // with. applyFeedbackSettings() re-applies the resolved palette to the live views.
        if let d = sharedDefaults {
            let savedTheme = (d.object(forKey: "keyboard_theme") != nil)
                ? d.integer(forKey: "keyboard_theme")
                : 6
            if savedTheme != currentKeyboardTheme {
                currentKeyboardTheme = savedTheme
                applyFeedbackSettings()
            }
        }

        mCompletionOn = false
        clearShiftState()

        updateInputModeForCurrentField()

        // Restore last-used LIME IM from the keyboard-owned hot store (§1.8 active_im)
        if !mEnglishOnly {
            let saved = hotActiveIM()
            if !saved.isEmpty && saved != activeIM {
                // Find the saved IM in the activated list and restore index
                if let idx = activatedIMs.firstIndex(where: { $0.tableNick == saved }) {
                    activeIM      = saved
                    activeIMIndex = idx
                    let caps = searchServer?.detectIMCapabilities(tableName: activeIM)
                        ?? (hasNumber: false, hasSymbol: false)
                    let symbolMapping = Self.hasSymbolMappingForKeyboard(
                        caps.hasSymbol,
                        keyboardId: activatedIMs[idx].keyboardId)
                    searchServer?.setTableName(activeIM,
                        hasNumberMapping: caps.hasNumber,
                        hasSymbolMapping: symbolMapping)
                    searchServer?.setPhoneticKeyboardType(phoneticKeyboardType)
                    refreshImKeys()
                }
            }
        }

        applyLayoutForCurrentInputField()

        clearComposing(force: false)
        tempEnglishWord = ""

    }

    private func updateInputModeForCurrentField() {
        // Map keyboard type → mEnglishOnly + mPredictionOn (spec §2 table)
        switch textDocumentProxy.keyboardType ?? .default {
        case .numberPad, .decimalPad, .asciiCapableNumberPad:
            mEnglishOnly = true; mPredictionOn = true
        case .phonePad:
            mEnglishOnly = true; mPredictionOn = false
        case .emailAddress:
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
    }

    static func layoutIdForCurrentInputField(keyboardType: UIKeyboardType,
                                             isEnglishOnly: Bool,
                                             hasActivatedIMs: Bool,
                                             englishLayout: String,
                                             resolvedActiveLayoutId: String) -> String {
        if keyboardType == .phonePad {
            return "phone_number"
        }
        if keyboardType == .numberPad || keyboardType == .decimalPad {
            // #139: pure-number fields get the strict, mode-key-free numeric keypad,
            // matching Android's TYPE_CLASS_NUMBER → phone_number. `.phonePad` already
            // returns phone_number above. (Not observable on iOS — iOS system-replaces
            // numeric fields — but kept for parity/consistency with Android.)
            return "phone_number"
        }
        if keyboardType == .asciiCapableNumberPad {
            // #139: ASCII-capable number fields may need letters, so keep symbols1 —
            // its EN / 中 keys reach the letter layouts (it is not letter-trapped).
            return "symbols1"
        }
        if isEnglishOnly || !hasActivatedIMs {
            return englishLayout
        }
        return resolvedActiveLayoutId
    }

    private func applyLayoutForCurrentInputField() {
        // Match Apple's keyboard: adapt the Enter key icon/label to the host's
        // returnKeyType (e.g. magnifier for URL/search fields, "Go" / "Send" labels).
        // KeyboardView.didSet skips the rebuild if rowViews are empty (initial load)
        // and triggers one otherwise; the subsequent setLayout below rebuilds with the
        // already-updated returnKeyType, so there is no double-build on field focus.
        keyboardView?.returnKeyType = textDocumentProxy.returnKeyType ?? .default

        let kbType = textDocumentProxy.keyboardType ?? .default
        // English runtime layout is preference-driven; legacy KeyboardConfig engkb fields are DB compatibility data only.
        let englishLayout = englishLayoutId()
        let resolvedActiveLayout = (!mEnglishOnly && !activatedIMs.isEmpty)
            ? resolvedLayoutId(for: activeIM)
            : ""
        let layoutName = Self.layoutIdForCurrentInputField(
            keyboardType: kbType,
            isEnglishOnly: mEnglishOnly,
            hasActivatedIMs: !activatedIMs.isEmpty,
            englishLayout: englishLayout,
            resolvedActiveLayoutId: resolvedActiveLayout)
        if let newLayout = LayoutLoader.load(layoutName) ?? LayoutLoader.load(englishLayout) {
            let adjusted = cj4SemicolonAdjusted(newLayout)
            if adjusted != currentLayout {
                currentLayout = adjusted
                keyboardView?.setLayout(currentLayout)
                applyHeight()
            }
        }

        // Record what we just adapted to so textDidChange's field-change
        // detector doesn't re-trigger on the next keystroke.
        lastSeenKeyboardType  = textDocumentProxy.keyboardType  ?? .default
        lastSeenReturnKeyType = textDocumentProxy.returnKeyType ?? .default

        // Surface the persistent no-active-IM hint whenever the keyboard resolves to no
        // IM (and clear it once one is available). Runs on every appearance / field change.
        refreshNoActiveIMHint()
    }

    // MARK: - Database Setup

    /// §1.7 Rule 2: after a cold→hot sync, keep the requested active IM when it survived in
    /// the freshly-activated list; otherwise fall to the first available IM. Empty
    /// `requested` (cold start with no saved IM) also falls through to `firstAvailable`.
    static func reconciledActiveIM(requested: String, activated: [String], firstAvailable: String) -> String {
        activated.contains(requested) ? requested : firstAvailable
    }

    static func hasSymbolMappingForKeyboard(_ baseHasSymbolMapping: Bool, keyboardId: String) -> Bool {
        baseHasSymbolMapping
            || keyboardId == "cj_semi"
            || keyboardId == "cj_num_semi"
            || LayoutLoader.isCjSemicolonLayoutId(keyboardId)
    }

    // Ordering guard for the two concurrent setupDatabase paths (viewDidLoad's databaseQueue
    // and the sync apply's syncQueue). `nextSetupToken()` is thread-safe; `lastAppliedSetupToken`
    // is touched only on the main thread inside setupDatabase's assignment block.
    private let setupTokenLock = NSLock()
    private var setupTokenCounter = 0
    private var lastAppliedSetupToken = 0
    /// True once the first setupDatabase has loaded activatedIMs on this process. Until then a
    /// no-op (converged) scan must NOT resolve the sync toast — its read of the still-empty
    /// activatedIMs would clobber the "同步中" with "no active IM" before setupDatabase lands
    /// (the "first empty, second good" race). setupDatabase resolves the toast itself. Main-thread.
    private var didCompleteInitialSetup = false

    private func nextSetupToken() -> Int {
        setupTokenLock.lock()
        defer { setupTokenLock.unlock() }
        setupTokenCounter += 1
        return setupTokenCounter
    }

    private func setupDatabase() {
        // setupDatabase runs from BOTH viewDidLoad (databaseQueue) and a sync apply
        // (syncQueue) — two serial queues that run concurrently. After a restore, the
        // viewDidLoad read sees the PRE-restore (empty) hot while the sync's read sees the
        // freshly-replaced hot; if the stale main-thread assignment lands last it clobbers
        // the fresh one, leaving an empty picker + "同步中"/no-active-IM until the next
        // appearance re-reads the applied hot (the "double copy / first empty, second good"
        // report). Guard the assignment with a monotonic token captured at read start: a
        // read that STARTED later saw a fresher hot (sync only moves hot forward), so only
        // the newest read may apply — a stale earlier read is dropped.
        let setupToken = nextSetupToken()
        let context: DBServer.KeyboardRuntimeContext
        do {
            context = try prepareKeyboardRuntimeDatabaseWithRetry()
        } catch {
            recordDatabaseSetupFailure(error)
            return
        }
        clearDatabaseSetupFailure()
        let ss = context.searchServer
        let resolved = context.activatedIMs
        let resolvedIM = context.initialIM

        // Marshal all state assignments back to the main thread
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            guard setupToken > self.lastAppliedSetupToken else { return }
            self.lastAppliedSetupToken = setupToken
            self.searchServer = ss
            self.activatedIMs  = resolved

            // Wire the Chinese-IM-overflow English fallback to UITextChecker (the same
            // source as keyboard English auto-completion), replacing the legacy FTS
            // `dictionary` table which no longer exists once the scored-dictionary seed
            // ships. Keeps SearchServer UIKit-free via a plain (String) -> [String] closure.
            // See docs/ENG_AUTO_COMPLETION.md "iOS impact of the seed change".
            ss.englishCompletionProvider = { [weak self] word in
                guard let self, !word.isEmpty else { return [] }
                let range = NSRange(location: 0, length: (word as NSString).length)
                return self.textChecker.completions(
                    forPartialWordRange: range, in: word, language: "en_US") ?? []
            }

            // Reconcile the active IM after a (re)prepare (§1.7 Rule 2). First setup has
            // no trustworthy live IM yet (a fresh process may open in English mode), so
            // restore the keyboard-owned hot active_im. Later sync reloads prefer the live
            // active keyboard, keeping the query table aligned with the current IM.
            let requestedIM = self.didCompleteInitialSetup
                ? self.activeIM
                : hotActiveIM()
            let survivingIM = Self.reconciledActiveIM(
                requested: requestedIM,
                activated: resolved.map { $0.tableNick },
                firstAvailable: resolvedIM)
            // If the LIVE active keyboard was removed by this sync, the controller has just
            // switched it to the first available — remember the switch as last-used.
            if !self.activeIM.isEmpty, self.activeIM != survivingIM {
                self.persistHotActiveIM(survivingIM)
            }
            self.activeIM      = survivingIM
            self.activeIMIndex = resolved.firstIndex { $0.tableNick == survivingIM } ?? 0
            let survivingCaps = ss.detectIMCapabilities(tableName: survivingIM)
            let survivingKeyboardId = resolved.first { $0.tableNick == survivingIM }?.keyboardId ?? ""
            ss.setTableName(survivingIM,
                            hasNumberMapping: survivingCaps.hasNumber,
                            hasSymbolMapping: Self.hasSymbolMappingForKeyboard(
                                survivingCaps.hasSymbol,
                                keyboardId: survivingKeyboardId))
            self.applyResolvedActiveIMLayout()

            // Load settings from shared UserDefaults (spec §15)
            // loadSettings() calls applyPrefsToSearchEngine() which pushes all prefs to SearchServer.
            self.loadSettings()
            self.searchServer?.setPhoneticKeyboardType(self.phoneticKeyboardType)
            // imKeysForTable depends on phoneticKeyboardType for the phonetic family,
            // so refreshImKeys must run AFTER setPhoneticKeyboardType.
            self.refreshImKeys()
            self.applyFeedbackSettings()
            self.preloadEmojiCategoryPages()

            // setupDatabase() runs asynchronously and can complete after viewWillAppear()
            // already chose a layout from stale/empty activatedIMs. Re-read the current
            // field mode and re-apply the visible layout from the freshly resolved IM
            // list so first activation after Settings/cloud install does not split
            // English runtime state from a Chinese IM layout (#121).
            self.updateInputModeForCurrentField()
            self.applyLayoutForCurrentInputField()
            self.updateGlobeAndDismissBindings()
            // If this reload was driven by a sync that put up the "同步中…" toast, resolve
            // it now — confirm the active IM, or fall back to the no-active-IM hint.
            self.resolveSyncToast()
            self.didCompleteInitialSetup = true
        }
    }

    private func prepareKeyboardRuntimeDatabaseWithRetry() throws -> DBServer.KeyboardRuntimeContext {
        var lastError: Error?
        for attempt in 1...Self.databaseSetupAttempts {
            do {
                return try DBServer.shared.prepareKeyboardRuntimeDatabase()
            } catch {
                lastError = error
                NSLog("[Keyboard] DB setup attempt \(attempt) failed: \(error)")
                if attempt < Self.databaseSetupAttempts {
                    Thread.sleep(forTimeInterval: Self.databaseSetupRetryDelay * Double(attempt))
                }
            }
        }
        throw lastError ?? DBServerError.datasourceUnavailable
    }

    private func recordDatabaseSetupFailure(_ error: Error) {
        let message = String(describing: error)
        NSLog("[Keyboard] DB setup failed after \(Self.databaseSetupAttempts) attempts: \(message)")
        UserDefaults.standard.set(message, forKey: "keyboard_db_last_error")
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: "keyboard_db_last_error_at")
        UserDefaults.standard.synchronize()
    }

    private func clearDatabaseSetupFailure() {
        UserDefaults.standard.removeObject(forKey: "keyboard_db_last_error")
        UserDefaults.standard.removeObject(forKey: "keyboard_db_last_error_at")
        UserDefaults.standard.synchronize()
    }

    private func applyResolvedActiveIMLayout() {
        guard !mEnglishOnly else { return }
        let layoutName = resolvedLayoutId(for: activeIM)
        guard let layout = LayoutLoader.load(layoutName) else { return }
        let adjusted = cj4SemicolonAdjusted(layout)
        guard adjusted != currentLayout else { return }
        clearShiftState()
        currentLayout = adjusted
        keyboardView?.setLayout(adjusted)
        applyHeight()
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
        // §1.5: read `im` config from `im.json` (via DBServer), not SearchServer's hot LimeDB.
        let freshKb = DBServer.shared.getImConfig("phonetic", "keyboard")
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
            if let newLayout = LayoutLoader.load(newLayoutId) {
                let adjusted = cj4SemicolonAdjusted(newLayout)
                guard adjusted != currentLayout else { return }
                currentLayout = adjusted
                keyboardView?.setLayout(currentLayout)
                applyHeight()
            }
        }
    }

    /// Maps the `phonetic_keyboard_type` pref to its dedicated visible layout id, or nil when the
    /// type has no special layout (standard → resolved via the keyboard-config path below).
    /// This is the single source of truth for the phonetic visible layout, mirroring Android's
    /// `resolvePhoneticKeyboardCode()` (#156): the pref drives the layout directly, so et_41 no
    /// longer depends on the persisted `im.keyboard` config round-trip that its siblings bypass.
    static func phoneticSpecialLayoutId(for kbType: String) -> String? {
        if kbType == "et_41" || kbType == "eten" { return "lime_et_41" }   // #156 ETEN 41-key
        if kbType.hasPrefix("eten26") || kbType == "et26" { return "lime_et26" }
        if kbType.hasPrefix("hsu") { return "lime_hsu" }
        return nil
    }

    private func resolvedLayoutId(for tableNick: String) -> String {
        // For phonetic IMs, keyboard type determines the visible layout.
        // Mirrors Android: eten/eten26/hsu use QWERTY-shaped Chinese layouts so the
        // mode key changes to abc while composing stays in the active IM.
        if tableNick == "phonetic",
           let special = KeyboardViewController.phoneticSpecialLayoutId(for: phoneticKeyboardType),
           LayoutLoader.load(special) != nil {
            return special
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
        UserDefaults(suiteName: LIMEPreferenceManager.suiteName)
    }

    private func cj4SemicolonAdjusted(_ layout: LimeKeyLayout) -> LimeKeyLayout {
        layout
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

    /// §1.8: read a two-writer hamburger pref from the hot store (extension-private
    /// `UserDefaults.standard`), seeding it once from cold (App Group) when the hot value is
    /// absent (first run / reinstall). Cold is never read again for this key after the seed.
    private func seededHotInt(_ key: String, cold: UserDefaults?) -> Int {
        let hot = UserDefaults.standard
        if hot.object(forKey: key) == nil, let seed = cold?.object(forKey: key) as? Int {
            hot.set(seed, forKey: key)
        }
        return hot.integer(forKey: key)
    }

    /// §1.8: reverse-lookup (per-IM) from the hot store, seeded once from cold on first read.
    /// This is the single effective reverse-lookup reader — hamburger picker, runtime commit,
    /// and menu label all route through it so the hot value always wins over stale cold (#157).
    /// `internal` (not `private`) so the read policy is unit-testable via `@testable import`.
    func hotReverseLookup(for im: String) -> String {
        let key = "\(im)_im_reverselookup"
        if UserDefaults.standard.object(forKey: key) == nil,
           let seed = sharedDefaults?.string(forKey: key) {
            UserDefaults.standard.set(seed, forKey: key)
        }
        return hotPrefs.reverseLookup(for: im)
    }

    /// §1.8: drain the app→keyboard pref inbox into the hot store (one-time). Runs on
    /// appearance BEFORE the relay so the relay reports the post-drain value. Layout is
    /// re-applied by `initOnStartInput()` right after, so a split-mode change takes effect.
    @discardableResult
    private func drainPrefInbox() -> Bool {
        let base = SyncDatabaseLocator.production().appGroupDirectory
        let lastConsumed = UserDefaults.standard.integer(forKey: "last_consumed_pref_seq")
        // seq guard: apply a record only once, even if the file lingers because the keyboard
        // cannot delete it FA-off. The consumed marker lives in the keyboard's own container.
        guard let rec = PrefInbox.read(base: base), rec.seq > lastConsumed else { return false }
        if let han = rec.hanConvert {
            UserDefaults.standard.set(han, forKey: "han_convert_option")
            hanConvertOption = han
        }
        if let split = rec.splitKeyboard {
            UserDefaults.standard.set(split, forKey: "split_keyboard_mode")
            splitKeyboardMode = split
        }
        if let reverse = rec.reverseLookup {
            for (im, value) in reverse { hotPrefs.setReverseLookup(value, for: im) }
        }
        if let restoredIM = rec.activeIM, !restoredIM.isEmpty {
            // Only a wholesale restore sets this — it overrides the live active IM (§1.2:
            // the backup's state wins). §1.7's reconcile then validates it against the
            // freshly-restored enabled list.
            persistHotActiveIM(restoredIM)
            activeIM = restoredIM
        }
        UserDefaults.standard.set(rec.seq, forKey: "last_consumed_pref_seq")
        PrefInbox.clearBestEffort(base: base)   // FA-on cleanup; FA-off relies on the seq guard
        // Mirror the post-drain value into relay-prefs.json so the kb→app relay reports it.
        let firstReverse = rec.reverseLookup?.first
        _ = try? relayPrefStore.update(hanConvert: hanConvertOption,
                                       splitKeyboard: splitKeyboardMode,
                                       reverseLookupIM: firstReverse?.key,
                                       reverseLookupValue: firstReverse?.value)
        return true
    }

    /// §1.8: the active IM (`active_im`) is keyboard-owned in the hot store. Read from there,
    /// seeding once from the legacy cold `keyboard_list` on first run (a read — FA-off OK).
    /// Empty → §1.7's reconcile falls to the first enabled IM. Cold is never read again.
    private func hotActiveIM() -> String {
        if UserDefaults.standard.object(forKey: "active_im") == nil {
            // seed from cold active_im, falling back to the legacy keyboard_list key
            let seed = sharedDefaults?.string(forKey: "active_im")
                ?? sharedDefaults?.string(forKey: "keyboard_list") ?? ""
            if !seed.isEmpty { UserDefaults.standard.set(seed, forKey: "active_im") }
        }
        return UserDefaults.standard.string(forKey: "active_im") ?? ""
    }

    /// §1.8: persist the active IM into the keyboard's own container (FA-off-safe; a cold
    /// write would be dropped FA-off). Cold only ever gets it at backup time (FA-on).
    private func persistHotActiveIM(_ im: String) {
        UserDefaults.standard.set(im, forKey: "active_im")
    }

    private func loadSettings() {
        let d = sharedDefaults
        // Note: d?.bool(forKey:) and d?.integer(forKey:) return false/0 when the key is
        // absent (not nil), so the ?? fallback never fires. Use object(forKey:) as? Type
        // for all settings whose default is non-false / non-zero.
        currentKeyboardTheme    = (d?.object(forKey: "keyboard_theme") != nil)
            ? (d?.integer(forKey: "keyboard_theme") ?? 6)
            : 6
        hanConvertOption        = seededHotInt("han_convert_option", cold: d)   // §1.8 hot store
        autoChineseSymbol       = d?.bool(forKey: "auto_chinese_symbol")                        ?? false
        sortSuggestions         = (d?.object(forKey: "learning_switch")          as? Bool)      ?? true
        smartChineseInput       = d?.bool(forKey: "smart_chinese_input")                        ?? false
        learnPhrase             = (d?.object(forKey: "learn_phrase")              as? Bool)     ?? true
        candidateSuggestion     = (d?.object(forKey: "candidate_suggestion")      as? Bool)     ?? true
        englishPredictionOn     = (d?.object(forKey: "english_dictionary_enable") as? Bool)     ?? true
        hasVibration            = (d?.object(forKey: "vibrate_on_keypress")       as? Bool)     ?? true
        vibrateLevel            = (d?.object(forKey: "vibrate_level")             as? Int)      ?? 40
        hasSound                = d?.bool(forKey: "sound_on_keypress")                          ?? false
        keypressSoundVolume     = d?.string(forKey: "keypress_sound_volume") ?? "-1"
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
        splitKeyboardMode = seededHotInt("split_keyboard_mode", cold: d)   // §1.8 hot store
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
        keyboardView?.keypressSoundVolume = keypressSoundVolume
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
        candidateBarHeightConstraint?.constant = activeCandidateBarHeight
        // Keep the expanded panel's collapse chevron height in lockstep with
        // the bar height. The dismiss button height is derived automatically
        // via a relative constraint off collapseBtn, so no separate update needed.
        expandedCollapseHeightConstraint?.constant = activeCandidateBarHeight
        let t = resolvedKeyboardTheme
        let pal = KeyboardPalette.palettes[max(0, min(t, KeyboardPalette.palettes.count - 1))]
        // Candidate bar backdrop is a transparent system blur — text must contrast the
        // system backdrop, not the keyboard theme. Capture system style before
        // overrideUserInterfaceStyle locks the bar's traitCollection to the theme.
        let systemStyle = traitCollection.userInterfaceStyle
        let adaptedCandiText = CandidateBarSystemChrome.labelColor(systemUserInterfaceStyle: systemStyle)
        keyboardView?.theme  = t
        candidateBar?.systemUserInterfaceStyle = systemStyle
        candidateBar?.theme  = t
        emojiPanelView?.setTheme(t, systemUserInterfaceStyle: systemStyle)
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
        expandedDismissButton?.tintColor = adaptedCandiText
        expandedDismissButton?.backgroundColor = pal.normalKey.withAlphaComponent(0.15)
        expandedComposingLabel?.font = candidateBar.composingStripFont
        expandedComposingLabel?.textColor = adaptedCandiText.withAlphaComponent(LayoutMetrics.ComposingPopup.textAlpha)
        if isExpandedCandidatesVisible { reloadExpandedCandidates() }
    }

    /// Refresh the cached `imkeys` for the active IM. Called after every
    /// SearchServer.setTableName / setPhoneticKeyboardType so handleCharacter
    /// can use it as the authoritative input-acceptance check on iPad layouts.
    /// Reads through `SearchServer.imKeysForTable`, which prefers stored table
    /// metadata and uses hardcoded keymaps only as fallback.
    private func refreshImKeys() {
        currentImKeys = Self.composingImKeys(
            activeIM: activeIM,
            publishedImKeys: cachedImConfig("imkeys"),
            fallbackImKeys: searchServer?.imKeysForTable(activeIM) ?? "")
    }

    /// Read a per-IM `im.json` value (imkeys/imkeynames/limeendkey) through a per-IM
    /// cache so the compose hot path doesn't `stat()` im.json + scan the im array
    /// every keystroke (docs/IOS_PROFILING_V2.md §4). Cache is cleared on IM change.
    private func cachedImConfig(_ field: String) -> String {
        if let v = imConfigCache[field] { return v }
        let v = DBServer.shared.getImConfig(activeIM, field)
        imConfigCache[field] = v
        return v
    }

    static func composingImKeys(activeIM: String,
                                publishedImKeys: String,
                                fallbackImKeys: String) -> String {
        switch activeIM {
        case "phonetic", "et41", "et_41", "eten":
            return fallbackImKeys
        default:
            return publishedImKeys.isEmpty ? fallbackImKeys : publishedImKeys
        }
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
        let adaptedCandiText = CandidateBarSystemChrome.labelColor(systemUserInterfaceStyle: setupSystemStyle)

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
            {
                let c = candidateBar.topAnchor.constraint(equalTo: view.topAnchor)
                candidateBarTopConstraint = c
                return c
            }(),
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
        // Keyboard extensions drop touches on fully transparent pixels (IOS_CANDI_TOUCH.md
        // §Resolution): a .clear content view leaves the gaps between candidate cells (and the
        // ragged row-ends) dead to both tap and scroll. The near-invisible touch-trap fill lets
        // the scroll view receive those touches.
        contentView.backgroundColor = LayoutMetrics.TouchTrap.fill
        contentView.translatesAutoresizingMaskIntoConstraints = false
        sv.addSubview(contentView)

        // Scroll top starts at the dismiss-button top (offset = active composing-strip height,
        // set per-show in updateExpandedCandidateChromeMetrics) so the grid scrolls below the
        // fixed composing strip instead of under it.
        let svTop = sv.topAnchor.constraint(equalTo: panel.topAnchor)
        expandedScrollTopConstraint = svTop
        NSLayoutConstraint.activate([
            panel.topAnchor.constraint(equalTo: candidateBar.topAnchor),
            panel.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            panel.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            panel.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            svTop,
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

        // Fixed first-row header (row-0 candidates) pinned at the panel top, in front of the
        // scroll view but behind the chrome. Height tracks the bar per-show. Rows 2+ scroll
        // below it, so nothing slides under the composing strip / dismiss button on scroll.
        let firstRowContainer = UIView()
        firstRowContainer.backgroundColor = .clear
        firstRowContainer.translatesAutoresizingMaskIntoConstraints = false
        panel.addSubview(firstRowContainer)
        let firstRowH = firstRowContainer.heightAnchor.constraint(equalToConstant: 0)
        NSLayoutConstraint.activate([
            firstRowContainer.topAnchor.constraint(equalTo: panel.topAnchor),
            firstRowContainer.leadingAnchor.constraint(equalTo: panel.leadingAnchor),
            firstRowContainer.trailingAnchor.constraint(equalTo: panel.trailingAnchor),
            firstRowH,
        ])
        expandedFirstRowContainer = firstRowContainer
        expandedFirstRowHeightConstraint = firstRowH

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
        // 0.01-alpha touch-trap fill so taps in the chevron's padding land on a non-clear pixel —
        // keyboard extensions drop touches on transparent pixels (docs/IOS_CANDI_TOUCH.md §Resolution).
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
        let sepCenterY = sep.centerYAnchor.constraint(equalTo: collapseBtn.centerYAnchor,
                                                      constant: candidateBar.composingStripHeight / 2)
        NSLayoutConstraint.activate([
            collapseBtn.trailingAnchor.constraint(equalTo: panel.trailingAnchor),
            collapseBtn.topAnchor.constraint(equalTo: panel.topAnchor),
            collapseBtn.widthAnchor.constraint(equalToConstant: LayoutMetrics.CandidateBar.Chevron.buttonWidth(isPad: isOnPad)),
            collapseH,

            sep.trailingAnchor.constraint(equalTo: collapseBtn.leadingAnchor),
            // Bias separator down to match candidate glyphs (mirrors
            // CandidateBarView.moreSep so row 1 stays pixel-identical).
            sepCenterY,
            sep.widthAnchor.constraint(equalToConstant: expandedSepWidth),
            sep.heightAnchor.constraint(equalToConstant: LayoutMetrics.CandidateBar.dividerHeight),
        ])
        expandedMoreSepCenterYConstraint = sepCenterY
        expandedCollapseButton = collapseBtn
        expandedMoreSep = sep

        // Dismiss button (✕) at the panel's top-left — mirrors CandidateBarView.dismissButton.
        let dismissBtn = UIButton(type: .system)
        let xmarkConfig = UIImage.SymbolConfiguration(
            pointSize: LayoutMetrics.CandidateBar.Chevron.iconSize(isPad: isOnPad), weight: .regular)
        dismissBtn.setImage(UIImage(systemName: "xmark", withConfiguration: xmarkConfig), for: .normal)
        dismissBtn.tintColor = adaptedCandiText
        dismissBtn.backgroundColor = pal.normalKey.withAlphaComponent(0.15)
        dismissBtn.layer.cornerRadius = 6
        dismissBtn.layer.masksToBounds = true
        dismissBtn.translatesAutoresizingMaskIntoConstraints = false
        dismissBtn.addTarget(self, action: #selector(dismissExpandedAndComposing), for: .touchUpInside)
        panel.addSubview(dismissBtn)

        // Dismiss button: half chevron width, height = barHeight − stripHeight (tracks
        // collapseBtn automatically), centered on glyph axis.  No contentEdgeInsets
        // bias — the frame is already positioned at the glyph center.
        let dismissCenterY = dismissBtn.centerYAnchor.constraint(equalTo: collapseBtn.centerYAnchor,
                                                                 constant: candidateBar.composingStripHeight / 2)
        let dismissHeight = dismissBtn.heightAnchor.constraint(equalTo: collapseBtn.heightAnchor,
                                                               constant: -candidateBar.composingStripHeight)
        NSLayoutConstraint.activate([
            dismissBtn.leadingAnchor.constraint(equalTo: panel.leadingAnchor),
            dismissCenterY,
            dismissHeight,
            dismissBtn.widthAnchor.constraint(equalToConstant: LayoutMetrics.CandidateBar.Chevron.dismissButtonWidth(isPad: isOnPad)),
        ])
        expandedDismissButton = dismissBtn
        expandedDismissCenterYConstraint = dismissCenterY
        expandedDismissHeightConstraint = dismissHeight

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
        // match the current row-height policy rather than a flat per-row constant.
        let keysHeight = keyboardView?.preferredHeight
            ?? CGFloat(currentLayout.rows.count) * LayoutMetrics.KeyboardRow.fallbackRowHeight
        let barH = activeCandidateBarHeight
        // Keep bar height constraint in sync with the computed bar height.
        // This covers the iPad case where traitCollection.userInterfaceIdiom is
        // .unspecified at viewDidLoad and resolves to .pad only by the time
        // viewWillLayoutSubviews fires — without this, candidateBarHeightConstraint
        // would stay at the Phone value (58×scale) while totalHeight is computed
        // with the Pad value (74×scale), leaving a layout gap.
        candidateBarHeightConstraint?.constant = barH
        expandedCollapseHeightConstraint?.constant = barH
        let keyboardHeight = emojiSearchHeaderHeight + barH + keysHeight
        let totalHeight = isEmojiPanelVisible && !isEmojiSearchMode
            ? max(keyboardHeight, emojiPanelView?.preferredPanelHeight ?? keyboardHeight)
            : keyboardHeight
        // #139: drive the extension view's height FROM CONTENT (keyboardView gets
        // the explicit height; the bar-top/bar-height/kb-top/kb-bottom chain then
        // defines view height) instead of an explicit view.heightAnchor constant.
        // With the explicit view-height constant, iOS latched a stale keyboard
        // frame during rotation and reported it to notification-based hosts
        // (LINE) with no way to correct it — peers without that constraint don't
        // reproduce. Content-driven height is the structural difference.
        let kbTarget = totalHeight - emojiSearchHeaderHeight - barH
        let didChangeHeight: Bool
        if rotationSettling, keyboardHeightConstraint != nil {
            // #139: hold height until the rotation settles (see viewWillTransition).
            didChangeHeight = false
        } else if let existing = keyboardHeightConstraint {
            didChangeHeight = abs(existing.constant - kbTarget) > 0.5
            if didChangeHeight { existing.constant = kbTarget }
        } else if let kbView = keyboardView {
            let c = kbView.heightAnchor.constraint(equalToConstant: kbTarget)
            // Required, not 999: during rotation iOS's own required
            // UIView-Encapsulated-Layout-Height pins the view at the OLD height
            // until after the last keyboard-frame notification fires, so a
            // 999-priority height loses the race and hosts get a stale frame
            // (#139). At required priority the view snaps to the final height
            // the moment applyHeight runs mid-rotation, before the notification.
            c.priority = .required
            c.isActive = true
            keyboardHeightConstraint = c
            didChangeHeight = true
        } else {
            didChangeHeight = false
        }
        if didChangeHeight {
            publishKeyboardHeightToUIKit()
        }
    }

    private func publishKeyboardHeightToUIKit() {
        view.setNeedsUpdateConstraints()
        inputView?.setNeedsUpdateConstraints()
    }

    // MARK: - Key Event Dispatch (spec §4 onKey)

    private func onKey(primaryCode: Int) {
        // CapsLock pre-processing: lowercase → uppercase (spec §4)
        var code = primaryCode
        if mCapsLock && code >= 97 && code <= 122 { code -= 32 }
        lastShiftTapTime = ShiftDoubleTapPolicy.lastTapTimeAfterKey(primaryCode: code,
                                                                    lastShiftTapTime: lastShiftTapTime)

        switch code {
        case LimeKeyCode.delete.rawValue:      handleBackspace()
        case LimeKeyCode.shift.rawValue:       handleShift()
        case LimeKeyCode.done.rawValue:        handleClose()
        case LimeKeyCode.globe.rawValue:       advanceToNextInputMode()
        case LimeKeyCode.emojiPanel.rawValue:  showEmojiPanel()
        case LimeKeyCode.emojiABC.rawValue:
            if isEmojiSearchMode {
                setEmojiSearchKeyboard(toEnglish: !emojiSearchEnglishOnly)
            } else {
                hideEmojiPanel()
            }
        case LimeKeyCode.switchToEnglish.rawValue:
            if isEmojiSearchMode {
                setEmojiSearchKeyboard(toEnglish: true)
            } else {
                switchChiEng(toEnglish: true)
            }
        case LimeKeyCode.switchToIM.rawValue:
            if isEmojiSearchMode {
                setEmojiSearchKeyboard(toEnglish: false)
            } else {
                switchChiEng(toEnglish: false)
            }
        case LimeKeyCode.switchToSymbol.rawValue:     switchToSymbol()
        case LimeKeyCode.switchSymbolKeyboard.rawValue: cycleSymbolPage()
        case LimeKeyCode.switchToPhoneSimple.rawValue: switchToPhoneSimple()
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
            if handleLimeEndkeyCommit(code) {
                consumeShiftAfterCharacter()
                return
            }
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

    func isKeyInImkeys(_ code: Int) -> Bool {
        if code == 44 || code == 46 { return true }
        guard let scalar = Unicode.Scalar(code) else { return false }
        let s = String(Character(scalar))
        return currentImKeys.contains(s) || currentImKeys.contains(s.lowercased())
    }

    func acceptsIntoComposing(code: Int,
                              hasSymbol: Bool,
                              hasNumber: Bool,
                              isPhonetic: Bool) -> Bool {
        guard code > 0, Unicode.Scalar(code) != nil else { return false }
        let isLetter = (code >= 97 && code <= 122) || (code >= 65 && code <= 90)
        let isDigit = code >= 48 && code <= 57
        let isSpace = code == 32
        let isComma = code == 44
        let isPeriod = code == 46

        if !currentImKeys.isEmpty {
            return isKeyInImkeys(code) || (isPhonetic && isSpace)
        } else if !hasSymbol && (isComma || isPeriod) {
            // Chinese , and . processing (mirrors Android LIMEService.handleCharacter
            // line 5354): whenever the IM has no symbol mapping, ',' and '.' are
            // accepted into composing so the full-width 「，」/「。」 candidate is
            // auto-inserted. This is a general rule independent of hasNumber, so it
            // also covers Array10 / Pinyin (hasNumber=true, hasSymbol=false), which
            // the hasNumber branch below would otherwise route to direct output.
            return true
        } else if !hasSymbol && !hasNumber {
            return isLetter || (isPhonetic && isSpace)
        } else if !hasSymbol && hasNumber {
            return isLetter || isDigit
        } else if hasSymbol && !hasNumber {
            let isSymbol = !isLetter && !isDigit && code > 32
            return isLetter || isSymbol || (isPhonetic && isSpace)
        } else {
            let isSymbol = !isLetter && !isDigit && code > 32
            return isLetter || isDigit || isSymbol || (isPhonetic && isSpace)
        }
    }

    static func isArraySymbolDigit(activeIM: String, composing: String, code: Int) -> Bool {
        activeIM == "array"
            && ((composing.first == "w"
                    && composing.dropFirst().allSatisfy { $0 >= "0" && $0 <= "9" })
                || (composing.hasPrefix("hg")
                    && composing.dropFirst(2).allSatisfy { $0 >= "0" && $0 <= "9" }))
            && (48...57).contains(code)
    }

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
        let isSpace    = code == 32

        let accepted = acceptsIntoComposing(
            code: code,
            hasSymbol: hasSymbol,
            hasNumber: hasNumber,
            isPhonetic: isPhonetic)

        if accepted || Self.isArraySymbolDigit(activeIM: activeIM, composing: mComposing, code: code) {
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

    // MARK: - LIME Endkey Commit

    private func activeImkeysForEndkey() -> String {
        let configured = cachedImConfig("imkeys")   // §1.5: im.json (cached per IM)
        return configured.isEmpty ? currentImKeys : configured
    }

    private func handleLimeEndkeyCommit(_ primaryCode: Int) -> Bool {
        let limeendkey = cachedImConfig("limeendkey")   // §1.5: im.json (cached per IM)
        guard LimeEndkeyPolicy.isCommitKey(primaryCode: primaryCode,
                                           endkey: limeendkey,
                                           englishOnly: mEnglishOnly) else {
            return false
        }

        if LimeEndkeyPolicy.isKeyInImkeys(primaryCode: primaryCode, imkeys: activeImkeysForEndkey()) {
            return commitComposingWithAppendedEndkey(primaryCode)
        }

        if !mComposing.isEmpty && !commitCurrentEndkeyComposing() {
            return false
        }
        return commitFreshEndkeyOrRaw(primaryCode)
    }

    private func commitComposingWithAppendedEndkey(_ primaryCode: Int) -> Bool {
        guard appendEndkeyToComposing(primaryCode) else { return false }
        return commitResolvedEndkeyComposing()
    }

    private func commitCurrentEndkeyComposing() -> Bool {
        if hasCurrentEndkeySelectedCandidate() {
            commitSelectedEndkeyCandidate()
            return true
        }
        return commitResolvedEndkeyComposing()
    }

    private func commitFreshEndkeyOrRaw(_ primaryCode: Int) -> Bool {
        guard appendEndkeyToComposing(primaryCode) else { return false }
        if commitResolvedEndkeyComposing() {
            return true
        }
        clearComposing(force: false)
        return true
    }

    private func appendEndkeyToComposing(_ primaryCode: Int) -> Bool {
        guard primaryCode > 0, let scalar = UnicodeScalar(primaryCode) else { return false }
        let char = String(Character(scalar))
        let insertChar = (isShiftOn && primaryCode != LimeKeyCode.space.rawValue) ? char.uppercased() : char
        mComposing += insertChar
        isSelfUpdate = true
        textDocumentProxy.insertText(insertChar)
        isSelfUpdate = false
        composingLength += 1
        candidateBar.setIdleToolsSuppressed(true)
        showComposingPopup()
        return true
    }

    private func commitResolvedEndkeyComposing() -> Bool {
        guard resolveEndkeySelectedCandidate() != nil else { return false }
        commitSelectedEndkeyCandidate()
        return true
    }

    private func commitSelectedEndkeyCandidate() {
        commitTyped()
        clearSuggestions()
    }

    private func resolveEndkeySelectedCandidate() -> Mapping? {
        if hasCurrentEndkeySelectedCandidate() {
            return selectedCandidate
        }
        guard !mComposing.isEmpty,
              let ss = searchServer else { return nil }

        currentSearchID &+= 1
        let candidates = ss.getMappingByCode(mComposing, isSoftKeyboard: true)
        guard !candidates.isEmpty else { return nil }

        let idx = LimeEndkeyPolicy.commitCandidateIndex(candidates)
        guard idx >= 0, idx < candidates.count else { return nil }
        mCandidateList = candidates
        selectedCandidate = candidates[idx]
        hasCandidatesShown = true
        isShowingRelatedPhrases = false
        hasChineseSymbolCandidatesShown = false
        return selectedCandidate
    }

    private func hasCurrentEndkeySelectedCandidate() -> Bool {
        guard let candidate = selectedCandidate else { return false }
        return !candidate.isComposingCodeRecord
            && !candidate.code.isEmpty
            && mComposing == candidate.code
    }

    // MARK: - English Character Handling (spec §5 English Mode)

    private func handleEnglishCharacter(code: Int, char: Character) {
        let charStr    = String(char)
        let insertChar = isShiftOn ? charStr.uppercased() : charStr

        // ENGLISH_KB.md #0 / §2a: read+clear the pick-auto-space flag up front so every
        // path clears it; only the punctuation-swap branch below acts on it.
        let wasPickedAutoSpace = pickedAutoSpace
        pickedAutoSpace = false

        if char.isLetter {
            tempEnglishWord += insertChar
        } else {
            resetTempEnglishWord()
            // Try the LatinIME-style space↔punctuation swap. If it handled the commit,
            // we are done (skip the normal insert + prediction refresh).
            if commitEnglishPunctuationWithSwap(insertChar, wasPickedAutoSpace) {
                consumeShiftAfterCharacter()
                updateShiftForAutoCap()
                return
            }
        }
        isSelfUpdate = true
        textDocumentProxy.insertText(insertChar)
        isSelfUpdate = false
        updateEnglishPrediction()
        consumeShiftAfterCharacter()
        updateShiftForAutoCap()
    }

    /// ENGLISH_KB.md #0 / §2a — replicate LatinIME's pick-space punctuation swap.
    ///
    /// When an English suggestion pick auto-appended a trailing space (`word `), typing
    /// punctuation should produce `word, ` rather than `word ,`. Returns `true` if this
    /// method fully handled the commit (caller must skip its own insert); `false` to let
    /// the caller insert normally.
    ///
    /// Character classes mirror AOSP `donottranslate-config-spacing-and-punctuations.xml`:
    /// followed-by-space (`. , ; : ! ? ) ] }`) → delete space, insert `punct + " "`;
    /// preceded-by-space (`( [ {`) → keep space, insert the bracket normally (returns false);
    /// strip (`- / @ _ '`) → delete space, insert the punctuation bare.
    private func commitEnglishPunctuationWithSwap(_ insertChar: String,
                                                  _ wasPickedAutoSpace: Bool) -> Bool {
        guard wasPickedAutoSpace, let c = insertChar.first else { return false }

        let followed = KeyboardViewController.engSwapFollowedBySpace.contains(c)
        let strip    = KeyboardViewController.engSwapStrip.contains(c)
        // Preceded-by-space brackets keep the existing space → let caller insert normally.
        guard followed || strip else { return false }

        // Cursor-move safety: only delete if the auto-space is actually still there.
        guard textDocumentProxy.documentContextBeforeInput?.hasSuffix(" ") == true else {
            return false
        }

        isSelfUpdate = true
        textDocumentProxy.deleteBackward()
        if followed {
            textDocumentProxy.insertText(insertChar + " ") // word,  (space after punct)
        } else {
            textDocumentProxy.insertText(insertChar)       // word-  (no space)
        }
        isSelfUpdate = false
        return true
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
    // Single tap toggles one-shot shift; double tap enters Shift Lock.
    // Layout switching: normal ↔ _shift variant (mirrors Android toggleShift())

    private func handleShift() {
        let next = ShiftTapPolicy.nextState(shifted: isShiftOn,
                                            capsLock: mCapsLock,
                                            doubleTap: isShiftDoubleTap())
        isShiftOn = next.shifted
        mCapsLock = next.capsLock
        applyShiftState()
    }

    private func isShiftDoubleTap(now: TimeInterval = Date().timeIntervalSinceReferenceDate) -> Bool {
        defer { lastShiftTapTime = now }
        return ShiftDoubleTapPolicy.isDoubleTap(lastShiftTapTime: lastShiftTapTime,
                                                now: now,
                                                timeout: LayoutMetrics.Gesture.shiftDoubleTapTimeout)
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
            let adjusted = cj4SemicolonAdjusted(newLayout)
            if isShiftKeyHeld {
                keyboardView?.previewLayout(adjusted)
                return
            }
            currentLayout = adjusted
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
        lastShiftTapTime = 0
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
            // Stage 1: quick fetch (INITIAL_RESULT_LIMIT).
            let q1ID = Prof.newID()
            Prof.begin("DBQueryStage1", id: q1ID)
            var results = ss.getMappingByCode(code, isSoftKeyboard: true)
            Prof.end("DBQueryStage1", id: q1ID)
            if !results.isEmpty, capturedEnableEmoji {
                results = ss.injectEmoji(into: results, insertAt: capturedEmojiPosition)
            }
            let wasTruncated = results.contains(where: { $0.isHasMoreMarkRecord })
            DispatchQueue.main.async { [weak self] in
                guard let self = self, self.currentSearchID == sid else {
                    Prof.event("StrokeCancelled")
                    Prof.end("Stroke", id: strokeID)
                    return
                }
                DispatchQueue.main.async { [weak self] in
                    guard let self = self, self.currentSearchID == sid else {
                        Prof.event("StrokeCancelled")
                        Prof.end("Stroke", id: strokeID)
                        return
                    }
                    let reloadID = Prof.newID()
                    Prof.begin("CandidateReload", id: reloadID)
                    results.isEmpty ? self.clearSuggestions() : self.setSuggestions(results)
                    Prof.end("CandidateReload", id: reloadID)
                    Prof.end("Stroke", id: strokeID)
                }
            }
            // Stage 2: full fetch, auto-fired when stage 1 was truncated. Upgrades the bar to the
            // full set without a scroll (see docs/TWO_STAGE_CANDI.md).
            guard wasTruncated else { return }
            let q2ID = Prof.newID()
            Prof.begin("DBQueryStage2", id: q2ID)
            var fullResults = ss.getMappingByCode(code, isSoftKeyboard: true, getAllRecords: true)
            Prof.end("DBQueryStage2", id: q2ID)
            if !fullResults.isEmpty, capturedEnableEmoji {
                fullResults = ss.injectEmoji(into: fullResults, insertAt: capturedEmojiPosition)
            }
            DispatchQueue.main.async { [weak self] in
                DispatchQueue.main.async { [weak self] in
                    guard let self = self else { return }
                    self.applyFullCandidateResults(fullResults, sid: sid)
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
        let idx = CandidateSelectionPolicy.defaultHighlightedCandidateIndex(full)
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
        let selectedIdx = CandidateSelectionPolicy.defaultHighlightedCandidateIndex(list)
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
        updateExpandedCandidateChromeMetrics()
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

    private func updateExpandedCandidateChromeMetrics() {
        let stripH = candidateBar.activeComposingStripHeight
        let bias = stripH / 2
        let chromeText = CandidateBarSystemChrome.labelColor(
            systemUserInterfaceStyle: candidateBar.systemUserInterfaceStyle)
        let pal = KeyboardPalette.palettes[max(0, min(resolvedKeyboardTheme, KeyboardPalette.palettes.count - 1))]
        expandedCollapseHeightConstraint?.constant = activeCandidateBarHeight
        expandedMoreSepCenterYConstraint?.constant = bias
        expandedDismissCenterYConstraint?.constant = bias
        expandedDismissHeightConstraint?.constant = -stripH
        // Grid scrolls below the fixed first row (which stays put, identical to the collapsed bar).
        expandedScrollTopConstraint?.constant = activeCandidateBarHeight
        expandedFirstRowHeightConstraint?.constant = activeCandidateBarHeight
        expandedCollapseButton?.tintColor = chromeText
        expandedMoreSep?.backgroundColor = chromeText.withAlphaComponent(LayoutMetrics.CandidateBar.separatorAlpha)
        expandedDismissButton?.tintColor = chromeText
        expandedDismissButton?.backgroundColor = pal.normalKey.withAlphaComponent(0.15)
        expandedComposingLabel?.textColor = chromeText.withAlphaComponent(LayoutMetrics.ComposingPopup.textAlpha)
        expandedComposingLabel?.isHidden = isEmojiSearchMode

        let chevronInsets = UIEdgeInsets(top: bias, left: 0, bottom: -bias, right: 0)
        expandedCollapseButton?.setValue(NSValue(uiEdgeInsets: chevronInsets), forKey: "contentEdgeInsets")
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

        // Remove all previous subviews (scroll body + fixed first-row header)
        contentView.subviews.forEach { $0.removeFromSuperview() }
        expandedFirstRowContainer?.subviews.forEach { $0.removeFromSuperview() }

        let pal = KeyboardPalette.palettes[max(0, min(resolvedKeyboardTheme, KeyboardPalette.palettes.count - 1))]
        let t = resolvedKeyboardTheme
        let systemStyle = candidateBar.systemUserInterfaceStyle
        let adaptedCandiText = CandidateBarSystemChrome.labelColor(systemUserInterfaceStyle: systemStyle)
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
        // Row 1 mirrors the collapsed bar exactly. Chinese composing keeps
        // the top keyname strip; emoji search disables it, so the expanded
        // row must read the active strip metrics from CandidateBarView instead
        // of assuming the Chinese composing layout.
        let hPad:         CGFloat = 0
        let vPad:         CGFloat = 0
        let activeStripH = candidateBar.activeComposingStripHeight
        // Row 0 is the fixed header — identical to the collapsed bar (full height + strip bias).
        // Rows 2+ scroll below it at the shorter no-strip height.
        let firstRowH:    CGFloat = activeCandidateBarHeight
        let restRowH:     CGFloat = max(0, activeCandidateBarHeight - activeStripH)
        var rowH:         CGFloat = firstRowH
        var rowBias:      CGFloat = activeStripH / 2
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

        let dismissZone = LayoutMetrics.CandidateBar.Chevron.dismissButtonWidth(isPad: isOnPad)
        let chevronZone = LayoutMetrics.CandidateBar.Chevron.buttonWidth(isPad: isOnPad)
        // Left edge of the chevron's centered glyph: row 0 items may fill up to here (the
        // button's side padding is empty), so the last item doesn't wrap early against the
        // full 40pt button.
        let chevronGlyphLeft = panelWidth - chevronZone / 2
            - LayoutMetrics.CandidateBar.Chevron.iconSize(isPad: isOnPad) / 2
        // All rows start at the same left (dismiss-width) margin so columns align.
        func expandedRowStartX(row: Int) -> CGFloat {
            return dismissZone + hPad
        }
        func expandedRowMaxX(row: Int) -> CGFloat {
            // Row 0 shares the chevron → stop at the chevron glyph. Rows 2+ scroll below the
            // header (no chevron) → full screen width.
            return row == 0 ? chevronGlyphLeft - expandedSepWidth : panelWidth
        }

        var row = 0
        var x: CGFloat = expandedRowStartX(row: row)
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
            btn.backgroundColor = LayoutMetrics.TouchTrap.fill   // commit taps on a glyph's transparent padding (IOS_CANDI_TOUCH.md §Resolution), mirroring makeCandidateButton
            btn.setTitle(text, for: .normal)
            btn.titleLabel?.font = btnFont
            let cellHPad = LayoutMetrics.CandidateBar.candidateHPad(isPad: onPad)
            // Width is determined by horizontal insets only (vertical insets
            // don't change intrinsicContentSize.width), so set H insets
            // first to read btnW. The vertical (rowBias) insets are
            // applied after the wrap decision so row 1 vs rows 2+ get
            // their correct bias.
            btn.setValue(NSValue(uiEdgeInsets: UIEdgeInsets(top: 0, left: cellHPad,
                                                            bottom: 0, right: cellHPad)),
                         forKey: "contentEdgeInsets")
            // Floor cell width at the bar's minimum (one em + side pads) so narrow glyphs
            // (composing echo, single bopomofo) get the same Han-sized cell as the collapsed
            // bar — otherwise row 0 packs tighter and every candidate shifts left vs the bar.
            let intrinsicW = btn.intrinsicContentSize.width
            let btnW = max(intrinsicW,
                           CandidateBarView.minCandidateCellWidth(fontPointSize: btnFont.pointSize, hPad: cellHPad))

            if !isFirstInRow {
                // Wrap on the item's NATURAL width (glyph + side pads), not the floored btnW.
                // Row 0 may overflow ~10% into the chevron glyph area (on-screen, empty-ish);
                // rows 2+ end at the screen edge so they wrap cleanly — nothing clips off-screen.
                let wrapExtent = row == 0 ? intrinsicW * 0.9 : intrinsicW
                if x + wrapExtent > expandedRowMaxX(row: row) {
                    // Wrap to next row. Advance by the OLD row's height,
                    // then switch to the shorter rows-2+ height with no
                    // strip-bias.
                    y += rowH
                    row += 1
                    x = expandedRowStartX(row: row)
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
            // Row 0 → fixed header container (panel-top coords). Rows 2+ → scroll content,
            // shifted up by the first-row height so the first scrolling row sits at content y=0.
            let inHeader = row == 0
            btn.frame = CGRect(x: x, y: inHeader ? y : y - firstRowH, width: btnW, height: rowH)
            btn.tag = i
            btn.addTarget(self, action: #selector(expandedCandidateTapped(_:)), for: .touchUpInside)
            (inHeader ? (expandedFirstRowContainer ?? contentView) : contentView).addSubview(btn)

            // Match CandidateButton.layoutSubviews exactly: pill = label.frame inset by
            // (-padX, -padY). UIButton centers the title in the cell, so the pill hugs the
            // ACTUAL glyph width centered in btnW — NOT (btnW - 2·cellHPad), which is wrong
            // once the cell is floored wider than the glyph (narrow glyphs / composing echo).
            if isSelected {
                let padX = LayoutMetrics.CandidateBar.pillPadX
                let padY = LayoutMetrics.CandidateBar.pillPadY
                let textW  = intrinsicW - 2 * cellHPad
                let pillW  = textW + 2 * padX
                // Match CandidateButton.layoutSubviews exactly: pill hugs the
                // title label which UIKit sizes to font.lineHeight (not ceiled).
                let pillH  = min(rowH, btnFont.lineHeight + 2 * padY)
                let pillX  = (btnW - pillW) / 2
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

        // Scroll content holds only rows 2+ (row 0 is the fixed header), so subtract the first row.
        let totalH = expandedCandidates.isEmpty ? 0 : (y + rowH + vPad)
        expandedContentHeightConstraint?.constant = max(0, totalH - firstRowH)
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
        if isEmojiSearchMode && mapping.isEmojiRecord {
            commitEmoji(mapping)
            return
        }
        pickCandidateManually(mapping)
    }

    @objc private func collapseExpandedCandidates() {
        fireHapticIfEnabled()
        hideExpandedCandidates()
    }

    @objc private func dismissExpandedAndComposing() {
        fireHapticIfEnabled()
        hideExpandedCandidates()
        if isEmojiSearchMode {
            hideEmojiPanel()
            return
        }
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
        if isEmojiSearchMode {
            hasChineseSymbolCandidatesShown = false
            isShowingRelatedPhrases = false
            mCandidateList     = []
            hasCandidatesShown = false
            selectedCandidate  = nil
            candidateBar.setIdleToolsSuppressed(!mComposing.isEmpty)
            if mComposing.isEmpty {
                hideComposingPopup()
                searchEmojiPanel(query: emojiSearchField?.text ?? "")
            } else {
                showEmojiSearchCandidates([])
            }
            return
        }
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
        if let published = Self.publishedKeyname(
            code,
            activeIM: activeIM,
            imkeys: cachedImConfig("imkeys"),
            imkeynames: cachedImConfig("imkeynames")) {
            return published
        }
        return searchServer?.keyToKeyname(code) ?? code
    }

    static func publishedKeyname(_ code: String,
                                 activeIM: String,
                                 imkeys: String,
                                 imkeynames: String) -> String? {
        switch activeIM {
        case "phonetic", "et41", "et_41", "eten":
            return nil
        default:
            guard !imkeys.isEmpty, !imkeynames.isEmpty else { return nil }
            let names = imkeynames.components(separatedBy: "|")
            let keys = Array(imkeys)
            var result = ""
            for ch in code {
                if let idx = keys.firstIndex(of: ch) {
                    let offset = keys.distance(from: keys.startIndex, to: idx)
                    result += offset < names.count ? names[offset] : String(ch)
                } else {
                    result.append(ch)
                }
            }
            return result
        }
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
        if isEmojiSearchMode {
            if candidate.isEmojiRecord {
                commitEmoji(candidate)
            } else if appendPickedCandidateToEmojiSearch(candidate) {
                return
            }
            return
        }
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

    private func appendPickedCandidateToEmojiSearch(_ candidate: Mapping) -> Bool {
        guard !candidate.word.isEmpty,
              !candidate.isEmojiRecord,
              !candidate.isComposingCodeRecord else { return false }
        clearComposing(force: true)
        selectedCandidate = nil
        appendEmojiSearchText(candidate.word)
        return true
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

        // Reverse lookup: show committed word's codes in the configured IM strip (spec §8, §13).
        // §1.8: read the hot store so a hamburger-menu change takes effect immediately, without
        // waiting for the keyboard→app relay or a keyboard restart (#157).
        let notifyEnabled = sharedDefaults?.object(forKey: "reverse_lookup_notify") as? Bool ?? true
        let lookupTable = hotReverseLookup(for: activeIM)
        if notifyEnabled,
           lookupTable != "none", !lookupTable.isEmpty,
           let ss = searchServer {
            let word = candidate.word
            DispatchQueue.global(qos: .background).async { [weak self] in
                guard let result = ss.getCodeListStringFromWord(word, usingTable: lookupTable),
                      !result.isEmpty else { return }
                DispatchQueue.main.async {
                    // Persist the reverse-lookup strip until the next key tap / new composing
                    // (didPress → hideLimeToast) — mirrors Android's dedicated reverse-lookup
                    // space, no auto-dismiss timer. Skip if a new stroke already started so a
                    // late async result can't clobber live composing and stick.
                    guard let self = self, self.mComposing.isEmpty else { return }
                    self.showLimeToast(result, persistent: true)
                }
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
                    // spec §11 case (b): the commit path already reset hasCandidatesShown
                    // (finishComposing + the mComposing.isEmpty block above), so restore it
                    // here so clearSuggestions() can surface the auto Chinese-punctuation
                    // strip. clearSuggestions() still gates on the autoChineseSymbol pref,
                    // so this is a no-op when the feature is off.
                    self.hasCandidatesShown = true
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
        // ENGLISH_KB.md #0 / §2a: arm the punctuation swap — we just appended a space.
        pickedAutoSpace = true
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
        // English runtime layout is preference-driven; legacy KeyboardConfig engkb fields are DB compatibility data only.
        let layoutName = toEnglish ? englishLayoutId() : resolvedLayoutId(for: activeIM)
        if let loaded = LayoutLoader.load(layoutName) { currentLayout = cj4SemicolonAdjusted(loaded) }
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
        // Persist last-used IM to the keyboard-owned hot store (§1.8 active_im)
        persistHotActiveIM(activeIM)

        // Clear composing and candidates before switching
        clearShiftState()
        clearComposing(force: false)
        LDComposingBuffer = ""

        // Reconfigure SearchServer for the new IM
        let caps = ss.detectIMCapabilities(tableName: activeIM)
        ss.setTableName(activeIM,
                        hasNumberMapping: caps.hasNumber,
                        hasSymbolMapping: Self.hasSymbolMappingForKeyboard(
                            caps.hasSymbol,
                            keyboardId: im.keyboardId))
        ss.setPhoneticKeyboardType(phoneticKeyboardType)
        refreshImKeys()

        // Update keyboard layout to match the new IM if available
        let preferredLayout = resolvedLayoutId(for: activeIM)
        if let newLayout = LayoutLoader.load(preferredLayout) {
            let adjusted = cj4SemicolonAdjusted(newLayout)
            if adjusted != currentLayout {
                currentLayout = adjusted
                keyboardView?.setLayout(currentLayout)
                applyHeight()
            }
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
        if isOnPad {
            symbolLayouts = ["symbols1", "symbols2", "symbols3"]
            clearComposing(force: false)
            loadSymbolLayout(page: 0)
            return
        }
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
        let layout = cj4SemicolonAdjusted(LayoutLoader.load(id) ?? currentLayout)
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

    /// feat#124: load the phone_simple numeric keypad (English 123 long-press).
    /// Direct layout swap (no persistent mode). Return to English uses phone_simple's
    /// existing ABC (-9 → switchChiEng(toEnglish:true), already idempotent on iOS).
    private func switchToPhoneSimple() {
        dismissPopupKeyboard()
        clearShiftState()
        mEnglishOnly = true
        let layout = cj4SemicolonAdjusted(LayoutLoader.load("phone_simple") ?? currentLayout)
        currentLayout = layout
        keyboardView?.setLayout(layout)
        applyHeight()
    }

    // MARK: - Globe Key Visibility (spec §10) and Legacy iPhone Dismiss Bindings

    /// Single refresh point for all globe-related view state (spec §10 and
    /// docs/IPHONE_LEGACY_KB.md). Must be called whenever `needsInputModeSwitchKey`
    /// could have changed (textWillChange/textDidChange) or after any layout
    /// rebuild (setLayout, shift toggle, symbol mode, IM switch).
    private func updateGlobeAndDismissBindings() {
        let isPad = isOnPad
        // On iPad with an _ipad layout, globe is always visible (matches Apple's stock keyboard).
        let globeVisible = (isPad && currentLayout.id.contains("_ipad")) || needsInputModeSwitchKey
        keyboardView?.setGlobeKeyVisible(globeVisible)

        let legacy = legacyGlobeMode
        keyboardView?.legacyGlobeMode = legacy
        candidateBar.legacyGlobeMode = legacy
    }

    // MARK: - Emoji / Surrogate Pair Detection (spec §8)

    /// Returns true if the string contains a Unicode surrogate pair (emoji or extended CJK).
    private func containsEmojiSurrogatePair(_ word: String) -> Bool {
        word.unicodeScalars.contains { $0.value > 0xFFFF }
    }

    // MARK: - LIME Toast / Reverse Lookup Display (spec §8, §13)

    /// Guides the user back to the Settings app when the keyboard has no IM to activate.
    static let noActiveIMHintText = "請回到LIME設定程式安裝並啓用輸入法"
    /// Shown while a restore/install cold→hot sync runs and no IM is active yet.
    static let syncingHintText = "同步中，請稍侯…"
    /// True while the "同步中…" toast is up (main-thread only): keeps the no-active-IM hint
    /// from clobbering it and gates the sync-done confirmation.
    private var syncToastActive = false

    private func showLimeToast(_ message: String, persistent: Bool = false) {
        guard limeToastState.show(message), let text = limeToastState.message else { return }
        limeToastTimer?.invalidate()
        limeToastTimer = nil
        candidateBar.composingText = text
        if let lbl = expandedComposingLabel {
            lbl.attributedText = CandidateBarView.attributedKeyname(
                text, baseFont: candidateBar.composingStripFont,
                color: lbl.textColor ?? .label)
        }
        // A persistent toast (the no-active-IM hint) stays until an IM becomes
        // available or another toast replaces it — no auto-dismiss timer.
        guard !persistent else { return }
        limeToastTimer = Timer.scheduledTimer(withTimeInterval: 1.5, repeats: false) { [weak self] _ in
            self?.hideLimeToast()
        }
    }

    /// Show/refresh the persistent "no active IM" hint: shown whenever the keyboard has
    /// no installed+enabled IM to activate, cleared as soon as one becomes available.
    private func refreshNoActiveIMHint() {
        // While a sync is running, leave the "同步中…" toast up — a restore/install sync
        // may be about to load an IM.
        if syncToastActive { return }
        if activatedIMs.isEmpty {
            showLimeToast(Self.noActiveIMHintText, persistent: true)
        } else if limeToastState.message == Self.noActiveIMHintText {
            hideLimeToast()
        }
    }

    /// Put up the "同步中，請稍侯…" toast while a scan runs and no IM is active yet.
    private func beginSyncToastIfNeeded() {
        guard activatedIMs.isEmpty, !syncToastActive else { return }
        syncToastActive = true
        showLimeToast(Self.syncingHintText, persistent: true)
    }

    /// Resolve the "同步中…" toast when a scan ends: brief confirmation of the now-active
    /// IM, or the no-active-IM hint if still none. No-op unless the toast was shown.
    private func resolveSyncToast() {
        guard syncToastActive else { return }
        syncToastActive = false
        if let config = activatedIMs.first(where: { $0.tableNick == activeIM }) ?? activatedIMs.first {
            showLimeToast(displayName(for: config))
        } else {
            refreshNoActiveIMHint()
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
    /// Canonical set — must stay identical (same symbols, same order) to Android's
    /// `ChineseSymbol.chineseSymbols` so both platforms show the same strip
    /// (docs/AUTO_CHINESE_PUNC.md §3; locked by the T-SET parity tests).
    static func chinesePunctuationMappings() -> [Mapping] {
        let symbols = ["，", "。", "、", "？", "！", "：", "；",
                       "（", "）", "「", "」", "『", "』", "【", "】",
                       "／", "＼", "－", "＿", "＊", "＆", "︿",
                       "％", "＄", "＃", "＠", "～",
                       "｛", "｝", "［", "］", "＜", "＞", "＋", "｜", "‵", "＂"]
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
        if shouldRouteKeyToEmojiSearchField(keyDef.code),
           handleEmojiSearchKey(code: keyDef.code) {
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

    private func shouldRouteKeyToEmojiSearchField(_ code: Int) -> Bool {
        guard isEmojiSearchMode else { return false }
        if emojiSearchEnglishOnly { return true }
        if code == LimeKeyCode.enter.rawValue || code == LimeKeyCode.done.rawValue {
            return true
        }
        if code == LimeKeyCode.delete.rawValue && mComposing.isEmpty {
            return true
        }
        return false
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

    private func ensureKeyPreviewView() -> UIView {
        if let keyPreviewView {
            return keyPreviewView
        }

        let container = UIView(frame: .zero)
        container.backgroundColor = .clear
        container.isUserInteractionEnabled = false
        container.alpha = 0
        container.isHidden = true

        let shapeLayer = CAShapeLayer()
        shapeLayer.shadowColor = LayoutMetrics.Shadow.color
        shapeLayer.shadowOffset = CGSize(width: 0, height: LayoutMetrics.KeyPreview.shadowOffsetY)
        shapeLayer.shadowOpacity = LayoutMetrics.KeyPreview.shadowOpacity
        shapeLayer.shadowRadius = LayoutMetrics.KeyPreview.shadowRadius
        container.layer.addSublayer(shapeLayer)

        let singleLabel = UILabel()
        singleLabel.textAlignment = .center
        singleLabel.translatesAutoresizingMaskIntoConstraints = false
        singleLabel.isHidden = true
        container.addSubview(singleLabel)

        let dualStack = UIStackView()
        dualStack.alignment = .center
        dualStack.translatesAutoresizingMaskIntoConstraints = false
        dualStack.isHidden = true

        let primaryLabel = UILabel()
        primaryLabel.setContentHuggingPriority(.required, for: .horizontal)
        primaryLabel.setContentHuggingPriority(.required, for: .vertical)

        let subLabel = UILabel()
        subLabel.setContentHuggingPriority(.required, for: .horizontal)
        subLabel.setContentHuggingPriority(.required, for: .vertical)

        dualStack.addArrangedSubview(primaryLabel)
        dualStack.addArrangedSubview(subLabel)
        container.addSubview(dualStack)

        let singleWidthConstraint = singleLabel.widthAnchor.constraint(lessThanOrEqualToConstant: 0)
        let dualWidthConstraint = dualStack.widthAnchor.constraint(lessThanOrEqualToConstant: 0)
        NSLayoutConstraint.activate([
            singleLabel.centerXAnchor.constraint(equalTo: container.centerXAnchor),
            singleLabel.centerYAnchor.constraint(equalTo: container.centerYAnchor,
                                                 constant: -LayoutMetrics.KeyPreview.neckHeight / 2),
            singleWidthConstraint,
            dualStack.centerXAnchor.constraint(equalTo: container.centerXAnchor),
            dualStack.centerYAnchor.constraint(equalTo: container.centerYAnchor,
                                               constant: -LayoutMetrics.KeyPreview.neckHeight / 2),
            dualWidthConstraint,
        ])

        keyPreviewView = container
        keyPreviewShapeLayer = shapeLayer
        keyPreviewSingleLabel = singleLabel
        keyPreviewDualStack = dualStack
        keyPreviewDualPrimaryLabel = primaryLabel
        keyPreviewDualSubLabel = subLabel
        keyPreviewSingleWidthConstraint = singleWidthConstraint
        keyPreviewDualWidthConstraint = dualWidthConstraint
        return container
    }

    private func attachKeyPreview(_ preview: UIView, to window: UIWindow) {
        guard preview.superview !== window else { return }
        preview.removeFromSuperview()
        window.addSubview(preview)
    }

    private func setKeyPreviewContentMode(_ mode: KeyPreviewContentMode) {
        guard keyPreviewContentMode != mode else { return }
        keyPreviewContentMode = mode
        keyPreviewSingleLabel?.isHidden = mode != .single
        keyPreviewDualStack?.isHidden = mode != .dual
    }

    private func hideKeyPreview(animated: Bool) {
        guard let preview = keyPreviewView else { return }
        keyPreviewAnimationID += 1
        let animationID = keyPreviewAnimationID
        preview.layer.removeAllAnimations()
        keyPreviewShapeLayer?.removeAllAnimations()

        let finish: () -> Void = { [weak self, weak preview] in
            guard let self = self,
                  let preview = preview,
                  animationID == self.keyPreviewAnimationID else {
                return
            }
            preview.alpha = 0
            preview.transform = .identity
            preview.isHidden = true
        }

        if animated {
            UIView.animate(withDuration: LayoutMetrics.KeyPreview.disappearDuration,
                           animations: {
                preview.alpha = 0
            }, completion: { _ in finish() })
        } else {
            finish()
        }
    }

    private func teardownKeyPreview() {
        keyPreviewAnimationID += 1
        keyPreviewView?.layer.removeAllAnimations()
        keyPreviewShapeLayer?.removeAllAnimations()
        keyPreviewView?.removeFromSuperview()
        keyPreviewView = nil
        keyPreviewShapeLayer = nil
        keyPreviewSingleLabel = nil
        keyPreviewDualStack = nil
        keyPreviewDualPrimaryLabel = nil
        keyPreviewDualSubLabel = nil
        keyPreviewSingleWidthConstraint = nil
        keyPreviewDualWidthConstraint = nil
        keyPreviewContentMode = nil
    }

    func keyboardView(_ view: KeyboardView, showPreviewFor keyDef: KeyDef, keyRect: CGRect) {
        // At least label or sublabel must be non-empty
        guard !keyDef.label.isEmpty || !keyDef.sublabel.isEmpty else {
            hideKeyPreview(animated: false)
            return
        }

        // Convert key rect from KeyboardView → window coordinates so the preview
        // can float above the keyboard top edge without being clipped by self.view.
        guard let kbView = keyboardView,
              let window = self.view.window else {
            hideKeyPreview(animated: false)
            return
        }
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

        let container = ensureKeyPreviewView()
        attachKeyPreview(container, to: window)
        container.layer.removeAllAnimations()
        keyPreviewShapeLayer?.removeAllAnimations()
        container.frame = CGRect(x: bubbleX, y: bubbleY, width: bubbleW, height: totalH)

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

        CATransaction.begin()
        CATransaction.setDisableActions(true)
        keyPreviewShapeLayer?.path = path.cgPath
        keyPreviewShapeLayer?.fillColor = keyBg.cgColor
        CATransaction.commit()

        // --- Content: same layout as the key itself --------------------------
        let hasSublabel = !keyDef.sublabel.isEmpty

        if hasSublabel {
            // Dual-label layout — mirrors KeyboardView.makeDualLabelView
            setKeyPreviewContentMode(.dual)
            guard let stack = keyPreviewDualStack,
                  let primaryLbl = keyPreviewDualPrimaryLabel,
                  let subLbl = keyPreviewDualSubLabel else {
                return
            }
            stack.alignment = .center

            primaryLbl.text = keyDef.label
            primaryLbl.textColor = pal.secondaryLabel

            subLbl.text = keyDef.sublabel
            subLbl.textColor = keyLabel

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
            keyPreviewDualWidthConstraint?.constant = bubbleW + LayoutMetrics.KeyPreview.contentWidthInset
        } else {
            // Single label
            setKeyPreviewContentMode(.single)
            guard let lbl = keyPreviewSingleLabel else { return }
            lbl.text          = keyDef.label
            lbl.font          = UIFont.systemFont(
                ofSize: LayoutMetrics.KeyPreview.singleFontSize(isLandscape: isLand), weight: .regular)
            lbl.textColor     = keyLabel
            lbl.textAlignment = .center
            keyPreviewSingleWidthConstraint?.constant = bubbleW + LayoutMetrics.KeyPreview.contentWidthInset
        }

        // --- Animate in -------------------------------------------------------
        keyPreviewAnimationID += 1
        container.layoutIfNeeded()
        container.isHidden = false
        container.alpha = 0
        container.transform = CGAffineTransform(scaleX: LayoutMetrics.KeyPreview.initialScale,
                                                y: LayoutMetrics.KeyPreview.initialScale)
        UIView.animate(withDuration: LayoutMetrics.KeyPreview.appearDuration, delay: 0,
                       usingSpringWithDamping: LayoutMetrics.KeyPreview.springDamping,
                       initialSpringVelocity: LayoutMetrics.KeyPreview.springInitialVelocity) {
            container.alpha = 1
            container.transform = .identity
        }
    }

    func keyboardView(_ view: KeyboardView, didMoveCaretBy steps: Int) {
        guard mComposing.isEmpty else { return }
        textDocumentProxy.adjustTextPosition(byCharacterOffset: steps)
    }

    func keyboardViewHasOpenPopup(_ view: KeyboardView) -> Bool {
        currentPopupView != nil
    }

    func keyboardViewCurrentPopupIsSingleKey(_ view: KeyboardView) -> Bool {
        currentPopupView?.isSingleKey ?? false
    }

    func keyboardView(_ view: KeyboardView, popupKeyAtKeyboardPoint point: CGPoint) -> KeyDef? {
        guard let popup = currentPopupView else { return nil }
        let pointInView = view.convert(point, to: self.view)
        let pointInPopup = popup.convert(pointInView, from: self.view)
        return popup.key(at: pointInPopup, slideAllowance: popup.normalKeyHeight * 0.25)
    }

    func keyboardView(_ view: KeyboardView, highlightPopupKey keyDef: KeyDef?) {
        currentPopupView?.setHighlightedKey(keyDef)
        showPopupKeyPreview(for: keyDef)
    }

    /// Show the callout key-preview above a popup alternate (slide-over or tap-down); `nil` hides it.
    /// Suppressed for single-key mini-popups per spec.
    private func showPopupKeyPreview(for keyDef: KeyDef?) {
        guard let keyDef,
              let popup = currentPopupView, !popup.isSingleKey,
              let kbView = keyboardView,
              let rect = popup.keyRect(for: keyDef, in: kbView) else {
            hideKeyPreview(animated: false)
            return
        }
        keyboardView(kbView, showPreviewFor: keyDef, keyRect: rect)
    }

    func keyboardView(_ view: KeyboardView, didSelectPopupKey keyDef: KeyDef) {
        dismissPopupKeyboard()
        firePopupKey(keyDef)
    }

    func keyboardViewDidCancelPopupSlide(_ view: KeyboardView) {
        dismissPopupKeyboard()
    }

    func keyboardViewDidSwipeLeft(_ view: KeyboardView) {
        handleBackspace()
    }

    func keyboardViewDidSwipeRight(_ view: KeyboardView) {
        _ = pickHighlightedCandidate()
    }

    func keyboardViewDismissPreview(_ view: KeyboardView) {
        hideKeyPreview(animated: true)
    }

    func keyboardView(_ view: KeyboardView, didLongPress keyDef: KeyDef) {
        // Keyboard key (code -3): show the LIME options menu (spec §10).
        // Globe long-press is routed through UIInputViewController.handleInputModeList.
        // The globe-icon preview popup that used to flash before the menu is
        // removed — modern iPhones, iPad, and legacy iPhones each have a
        // dedicated globe affordance elsewhere (system action bar, in-keyboard
        // globe key, or candidate-bar ☰), so the preview is redundant.
        if keyDef.code == LimeKeyCode.done.rawValue {
            showGlobeMenu(from: view)
        }
        // Space key: show LIME-internal IM picker only (spec §10: NOT iOS keyboard switch)
        else if keyDef.code == LimeKeyCode.space.rawValue {
            showLimeIMPicker()
        }
    }

    // feat#124: generic long-press → run its secondary key code (English 123 → phone_simple).
    func keyboardView(_ view: KeyboardView, didLongPressKey keyDef: KeyDef) {
        onKey(primaryCode: keyDef.longPressCode)
    }

    func keyboardView(_ view: KeyboardView, didLongPressPopupKey keyDef: KeyDef, sourceRect: CGRect) {
        let srcInView = view.convert(sourceRect, to: self.view)
        showPopupKeyboard(for: keyDef, sourceRect: srcInView)
    }

    /// Single-key popup released: dismiss the panel, and on commit type the lone alternate — the
    /// same key the panel would have fired on tap.
    func keyboardView(_ view: KeyboardView, didReleasePopupKey keyDef: KeyDef, commit: Bool) {
        dismissPopupKeyboard()
        guard commit,
              let popupLayout = resolvePopupLayout(for: keyDef),
              let popupKey = popupLayout.rows.first?.keys.first else { return }
        firePopupKey(popupKey)
    }

    // MARK: - Popup Keyboard


    private func showPopupKeyboard(for keyDef: KeyDef, sourceRect: CGRect) {
        dismissPopupKeyboard()
        guard let popupLayout = resolvePopupLayout(for: keyDef),
              !popupLayout.rows.isEmpty else { return }

        // Single-key popups show the mini-keyboard too (like multi-key): popupKeyLongPressed opens it
        // on hold and, on release, dismisses it + sends the lone key via didReleasePopupKey.

        // Tap-outside overlay placed below the popup
        let overlay = UIControl()
        overlay.frame = view.bounds
        overlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        overlay.backgroundColor = LayoutMetrics.TouchTrap.fill
        overlay.addTarget(self, action: #selector(dismissPopupKeyboard), for: .touchUpInside)
        overlay.tag = 9877
        view.addSubview(overlay)

        let normalKeySize = keyboardView?.normalKeySize ?? .zero
        let popup = PopupKeyboardView(layout: popupLayout, theme: resolvedKeyboardTheme,
                                      baseKeySize: normalKeySize == .zero ? sourceRect.size : normalKeySize)
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
        hideKeyPreview(animated: false)
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
        // Persist last-used IM to the keyboard-owned hot store (§1.8 active_im)
        persistHotActiveIM(activeIM)
        clearShiftState()
        clearComposing(force: false)
        mEnglishOnly = false
        if mPersistentLanguageMode {
            sharedDefaults?.set(false, forKey: "persisted_english_mode")
        }
        clearSuggestions()
        resetTempEnglishWord()
        let caps = searchServer?.detectIMCapabilities(tableName: activeIM)
            ?? (hasNumber: false, hasSymbol: false)
        let keyboardId = activatedIMs.first { $0.tableNick == activeIM }?.keyboardId ?? ""
        searchServer?.setTableName(activeIM, hasNumberMapping: caps.hasNumber,
                                   hasSymbolMapping: Self.hasSymbolMappingForKeyboard(
                                       caps.hasSymbol,
                                       keyboardId: keyboardId))
        searchServer?.setPhoneticKeyboardType(phoneticKeyboardType)
        refreshImKeys()
        if let layout = LayoutLoader.load(resolvedLayoutId(for: activeIM)) {
            let adjusted = cj4SemicolonAdjusted(layout)
            if adjusted != currentLayout {
                currentLayout = adjusted
                keyboardView?.setLayout(currentLayout)
                applyHeight()
            }
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
        sl.shadowColor = LayoutMetrics.Shadow.color
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
        let onDismiss = inlineMenuOnDismiss
        inlineMenuOnDismiss = nil
        onDismiss?()
    }

    /// One row in the inline menu: either a tappable action row (optional leading
    /// icon) or an inline segmented control (Android-parity 漢字轉換 / 分離鍵盤).
    private enum InlineMenuEntry {
        case action(title: String, icon: String?, action: () -> Void)
        case segmented(title: String, icon: String?, labels: [String], selected: Int, onSelect: (Int) -> Void)
    }

    /// Build and show an inline menu panel overlaying the keyboard.
    /// Back-compat: items without icons (sub-pickers). Delegates to the icon-aware form.
    private func showInlineMenu(items: [(title: String, action: () -> Void)]) {
        showInlineMenu(items: items.map { (title: $0.title, icon: nil, action: $0.action) })
    }

    private func showInlineMenu(items: [(title: String, icon: String?, action: () -> Void)]) {
        showInlineMenu(entries: items.map { .action(title: $0.title, icon: $0.icon, action: $0.action) })
    }

    private func showInlineMenu(entries: [InlineMenuEntry], onDismiss: (() -> Void)? = nil) {
        dismissInlineMenu()
        inlineMenuOnDismiss = onDismiss
        guard let root = view else { return }

        let panel = UIView()
        panel.backgroundColor = UIColor.systemBackground.withAlphaComponent(LayoutMetrics.InlineMenu.backgroundAlpha)
        panel.layer.cornerRadius = LayoutMetrics.InlineMenu.cornerRadius
        panel.layer.shadowColor = LayoutMetrics.Shadow.color
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

        let iconPointSize = LayoutMetrics.InlineMenu.buttonFontSize
        func addSeparator() {
            let sep = UIView()
            sep.backgroundColor = UIColor.separator
            sep.heightAnchor.constraint(equalToConstant: LayoutMetrics.InlineMenu.separatorHeight).isActive = true
            stack.addArrangedSubview(sep)
        }
        for (idx, entry) in entries.enumerated() {
            let isLast = idx == entries.count - 1
            switch entry {
            case let .action(title, icon, action):
                let btn = UIButton(type: .system)
                btn.setTitle(title, for: .normal)
                btn.titleLabel?.font = UIFont.systemFont(ofSize: LayoutMetrics.InlineMenu.buttonFontSize)
                btn.titleLabel?.adjustsFontForContentSizeCategory = true
                btn.heightAnchor.constraint(greaterThanOrEqualToConstant: LayoutMetrics.InlineMenu.buttonHeight).isActive = true
                if let iconName = icon, !isLast {
                    // Android-style row: leading icon + left-aligned title.
                    let cfg = UIImage.SymbolConfiguration(pointSize: iconPointSize, weight: .regular)
                    var config = UIButton.Configuration.plain()
                    var titleAttributes = AttributeContainer()
                    titleAttributes.font = UIFont.systemFont(ofSize: LayoutMetrics.InlineMenu.buttonFontSize)
                    config.attributedTitle = AttributedString(title, attributes: titleAttributes)
                    config.image = UIImage(systemName: iconName, withConfiguration: cfg)
                    config.imagePlacement = .leading
                    config.imagePadding = 12
                    config.contentInsets = NSDirectionalEdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16)
                    config.baseForegroundColor = .label
                    btn.configuration = config
                    btn.contentHorizontalAlignment = .leading
                } else {
                    btn.contentHorizontalAlignment = .center
                    if isLast { btn.setTitleColor(.systemBlue, for: .normal) }  // 完成
                }
                stack.addArrangedSubview(btn)
                if !isLast { addSeparator() }
                btn.addAction(UIAction { [weak self] _ in
                    self?.dismissInlineMenu()
                    action()
                }, for: .touchUpInside)

            case let .segmented(title, icon, labels, selected, onSelect):
                // Inline segmented row: leading icon + title, then a UISegmentedControl
                // (Android-parity 漢字轉換 / 分離鍵盤). Selection is recorded immediately;
                // the menu's onDismiss applies it.
                let rowFont = UIFont.systemFont(ofSize: LayoutMetrics.InlineMenu.buttonFontSize)
                let header = UILabel()
                header.font = rowFont
                header.adjustsFontForContentSizeCategory = true
                header.textColor = .label
                header.text = title
                if let iconName = icon {
                    let cfg = UIImage.SymbolConfiguration(pointSize: iconPointSize, weight: .regular)
                    let iv = UIImageView(image: UIImage(systemName: iconName, withConfiguration: cfg))
                    iv.tintColor = .label
                    iv.setContentHuggingPriority(.required, for: .horizontal)
                    let hRow = UIStackView(arrangedSubviews: [iv, header])
                    hRow.axis = .horizontal
                    hRow.spacing = 12
                    hRow.alignment = .center
                    let seg = UISegmentedControl(items: labels)
                    seg.selectedSegmentIndex = max(0, min(selected, labels.count - 1))
                    seg.addAction(UIAction { _ in onSelect(seg.selectedSegmentIndex) }, for: .valueChanged)
                    let col = UIStackView(arrangedSubviews: [hRow, seg])
                    col.axis = .vertical
                    col.spacing = 8
                    col.isLayoutMarginsRelativeArrangement = true
                    col.layoutMargins = UIEdgeInsets(top: 10, left: 16, bottom: 10, right: 16)
                    stack.addArrangedSubview(col)
                } else {
                    let seg = UISegmentedControl(items: labels)
                    seg.selectedSegmentIndex = max(0, min(selected, labels.count - 1))
                    seg.addAction(UIAction { _ in onSelect(seg.selectedSegmentIndex) }, for: .valueChanged)
                    let col = UIStackView(arrangedSubviews: [header, seg])
                    col.axis = .vertical
                    col.spacing = 8
                    col.isLayoutMarginsRelativeArrangement = true
                    col.layoutMargins = UIEdgeInsets(top: 10, left: 16, bottom: 10, right: 16)
                    stack.addArrangedSubview(col)
                }
                if !isLast { addSeparator() }
            }
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
        var entries: [InlineMenuEntry] = []

        // 字根反查 — drill-down sub-picker. §1.8: read the hot store so the row label reflects a
        // prior hamburger-menu change instead of a stale App Group value (#157).
        let reverseLookupValue = hotReverseLookup(for: activeIM)
        let reverseLookupOptions = LIMEPreferenceManager.reverseLookupOptions(from: activatedIMs)
        let reverseLookupLabel = LIMEPreferenceManager.reverseLookupLabel(for: reverseLookupValue,
                                                                          options: reverseLookupOptions)
        entries.append(.action(title: "字根反查：\(reverseLookupLabel) ▸", icon: "magnifyingglass", action: { [weak self] in
            self?.showReverseLookupPicker()
        }))

        // 漢字轉換 — inline segmented control (無 / 繁→簡 / 簡→繁), Android-parity.
        // Choice is recorded as pending and applied on dismiss.
        var pendingHan = max(0, min(hanConvertOption, 2))
        let hanStart = pendingHan
        entries.append(.segmented(title: "漢字轉換", icon: "arrow.left.arrow.right",
                                  labels: ["無", "繁→簡", "簡→繁"], selected: pendingHan,
                                  onSelect: { pendingHan = $0 }))

        // 分離鍵盤 — iPad only: inline segmented control (關閉 / 開啟 / 僅橫向).
        var pendingSplit = max(0, min(splitKeyboardMode, 2))
        let splitStart = pendingSplit
        if isOnPad {
            entries.append(.segmented(title: "分離鍵盤", icon: "rectangle.split.2x1",
                                      labels: ["關閉", "開啟", "僅橫向"], selected: pendingSplit,
                                      onSelect: { pendingSplit = $0 }))
        }

        // LIME 輸入法切換 — mirrors Android showIMPicker()
        entries.append(.action(title: "LIME 輸入法切換", icon: "list.bullet", action: { [weak self] in self?.showLimeIMPicker() }))

        // 系統輸入法切換 was removed: the globe key is always present (App Store policy),
        // so the system-keyboard switcher is already reachable from the keyboard itself.

        entries.append(.action(title: "完成", icon: nil, action: {}))

        showInlineMenu(entries: entries, onDismiss: { [weak self] in
            guard let self else { return }
            var changed = false
            if pendingHan != hanStart {
                self.hanConvertOption = pendingHan
                self.hotPrefs.hanConvertOption = pendingHan   // §1.8 hot store, not cold
                changed = true
            }
            if pendingSplit != splitStart {
                self.splitKeyboardMode = pendingSplit
                self.hotPrefs.splitKeyboardMode = pendingSplit   // §1.8 hot store, not cold
                self.view.setNeedsLayout()   // re-applies splitMode in viewWillLayoutSubviews
                changed = true
            }
            if changed {
                try? self.relayPrefStore.write(RelayPrefState(hanConvert: self.hanConvertOption,
                                                              splitKeyboard: self.splitKeyboardMode,
                                                              updatedAt: Date().timeIntervalSince1970))
            }
        })
    }

    /// Reverse lookup source sub-picker for the current active IM.
    private func showReverseLookupPicker() {
        let current = hotReverseLookup(for: activeIM)   // §1.8 hot store
        var items: [(title: String, action: () -> Void)] = []
        for option in LIMEPreferenceManager.reverseLookupOptions(from: activatedIMs) {
            let display = option.value == current ? "✓ \(option.label)" : option.label
            items.append((display, { [weak self] in
                guard let self else { return }
                self.hotPrefs.setReverseLookup(option.value, for: self.activeIM)   // §1.8 hot store
                // relay delivery back to the app's Preferences tab.
                _ = try? self.relayPrefStore.update(reverseLookupIM: self.activeIM,
                                                    reverseLookupValue: option.value)
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
                guard let self else { return }
                self.hanConvertOption = opt
                self.hotPrefs.hanConvertOption = opt   // §1.8 hot store, not cold
                _ = try? self.relayPrefStore.update(hanConvert: opt,
                                                    splitKeyboard: self.splitKeyboardMode)
            }))
        }
        items.append(("取消", {}))
        showInlineMenu(items: items)
    }

    /// Long-press on space key: LIME-internal IM picker (spec §10).
    private func showLimeIMPicker() {
        // Long-press space and the ≡ menu both route here. With no IM there is nothing
        // to switch to — re-surface the install hint instead of doing nothing.
        guard !activatedIMs.isEmpty else {
            showLimeToast(Self.noActiveIMHintText, persistent: true)
            return
        }
        var items: [(title: String, action: () -> Void)] = []
        for (i, im) in activatedIMs.enumerated() {
            let label = im.label
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
        isEmojiSearchMode = false
        emojiSearchHeaderView?.isHidden = true
        emojiSearchEnglishOnly = false
        emojiSearchSourceLayout = nil
        emojiSearchCandidates = []
        candidateBarTopConstraint?.constant = 0
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
        candidateBar.setComposingStripReserved(true)
        candidateBar.setEmptyDismissChromeEnabled(false)
        keyboardTopToViewConstraint?.isActive = false
        keyboardTopToCandidateConstraint?.isActive = true
        candidateBarHeightConstraint?.constant = activeCandidateBarHeight
        keyboardView.isHidden = true
        candidateBar.isHidden = true
        panel.setTheme(resolvedKeyboardTheme, systemUserInterfaceStyle: traitCollection.userInterfaceStyle)
        panel.setKeyboardSizeScale(keyboardSize)
        panel.prepareForPresentation()
        panel.setReturnKeyTitle(emojiPanelSource.returnKeyTitle)
        panel.isHidden = false
        panel.setEmojiPages(loadEmojiCategoryPages())
        applyHeight()
    }

    private func hideEmojiPanel() {
        emojiPanelView?.resignSearch()
        emojiPanelView?.clearSearchText()
        emojiPanelView?.isHidden = true
        emojiPanelView?.setSearchMode(false)
        emojiSearchField?.resignFirstResponder()
        emojiSearchField?.text = ""
        emojiSearchHeaderView?.isHidden = true
        isEmojiSearchMode = false
        if let sourceLayout = emojiSearchSourceLayout {
            currentLayout = sourceLayout
        }
        mEnglishOnly = emojiPanelSource == .english
        emojiSearchSourceLayout = nil
        emojiSearchCandidates = []
        emojiPanelBottomConstraint?.isActive = false
        if let panel = emojiPanelView {
            let bottom = panel.bottomAnchor.constraint(equalTo: view.bottomAnchor)
            bottom.isActive = true
            emojiPanelBottomConstraint = bottom
        }
        candidateBarTopConstraint?.constant = 0
        candidateBar.setComposingStripReserved(true)
        candidateBar.setEmptyDismissChromeEnabled(false)
        keyboardTopToViewConstraint?.isActive = false
        keyboardTopToCandidateConstraint?.isActive = true
        candidateBarHeightConstraint?.constant = activeCandidateBarHeight
        candidateBar.isHidden = false
        keyboardView.isHidden = false
        candidateBar.setCandidates([])
        keyboardView.setLayout(currentLayout)
        applyHeight()
    }

    private func showEmojiSearchKeyboard() {
        guard let panel = emojiPanelView else { return }
        if !isEmojiSearchMode {
            emojiSearchSourceLayout = currentLayout
            emojiSearchEnglishOnly = emojiPanelSource == .english
        }
        isEmojiSearchMode = true
        let searchField = ensureEmojiSearchHeader()
        searchField.text = panel.searchText
        emojiPanelBottomConstraint?.isActive = false
        let bottom = panel.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        bottom.isActive = true
        emojiPanelBottomConstraint = bottom
        panel.isHidden = true
        candidateBar.setEmptyDismissChromeEnabled(true)
        updateEmojiSearchHeaderMetrics()
        candidateBar.isHidden = false
        candidateBarHeightConstraint?.constant = activeCandidateBarHeight
        keyboardTopToViewConstraint?.isActive = false
        keyboardTopToCandidateConstraint?.isActive = true
        keyboardView.isHidden = false
        setEmojiSearchKeyboard(toEnglish: emojiSearchEnglishOnly)
        emojiSearchHeaderView?.isHidden = false
        showEmojiSearchCandidates(loadEmojiSearchFallbackItems())
        searchField.becomeFirstResponder()
        DispatchQueue.main.async { [weak self] in
            guard let self,
                  self.isEmojiSearchMode,
                  self.hasEmptyEmojiSearchText else { return }
            self.showEmojiSearchCandidates(self.loadEmojiSearchFallbackItems())
        }
        applyHeight()
        view.setNeedsLayout()
    }

    private func setEmojiSearchKeyboard(toEnglish: Bool) {
        guard isEmojiSearchMode else { return }
        emojiSearchEnglishOnly = toEnglish
        if toEnglish {
            cancelActiveComposingFromCandidateDismiss()
        } else {
            clearComposing(force: false)
        }
        candidateBar.setComposingStripReserved(true)
        mEnglishOnly = toEnglish
        clearShiftState()
        let layoutName = toEnglish
            ? englishLayoutId()
            : resolvedLayoutId(for: activeIM)
        guard let layout = LayoutLoader.load(layoutName) else { return }
        let adjusted = cj4SemicolonAdjusted(layout)
        currentLayout = adjusted
        keyboardView.setLayout(adjusted)
        updateEmojiSearchHeaderMetrics()
        candidateBarHeightConstraint?.constant = activeCandidateBarHeight
        expandedCollapseHeightConstraint?.constant = activeCandidateBarHeight
        applyHeight()
    }

    private func updateEmojiSearchHeaderMetrics() {
        guard isEmojiSearchMode else { return }
        let headerHeight = emojiSearchHeaderHeight
        candidateBarTopConstraint?.constant = headerHeight
    }

    @discardableResult
    private func ensureEmojiSearchHeader() -> UISearchTextField {
        if let field = emojiSearchField { return field }

        let header = UIView()
        header.isOpaque = false
        header.backgroundColor = .clear
        header.translatesAutoresizingMaskIntoConstraints = false
        header.isHidden = true
        view.addSubview(header)

        let field = UISearchTextField()
        field.placeholder = "搜尋表情符號"
        field.accessibilityIdentifier = "lime_emoji_search_field"
        field.delegate = self
        field.autocorrectionType = .no
        field.autocapitalizationType = .none
        field.returnKeyType = .done
        field.translatesAutoresizingMaskIntoConstraints = false
        field.addTarget(self, action: #selector(emojiSearchTextChanged), for: .editingChanged)
        header.addSubview(field)

        let fieldHeight = field.heightAnchor.constraint(equalToConstant: EmojiPanelView.searchFieldHeight)
        NSLayoutConstraint.activate([
            header.topAnchor.constraint(equalTo: view.topAnchor),
            header.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            header.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            header.heightAnchor.constraint(equalToConstant: EmojiPanelView.searchHeaderHeight),

            field.topAnchor.constraint(equalTo: header.topAnchor, constant: EmojiPanelView.searchFieldTopInset),
            field.leadingAnchor.constraint(equalTo: header.leadingAnchor, constant: 16),
            field.trailingAnchor.constraint(equalTo: header.trailingAnchor, constant: -16),
            fieldHeight,
        ])

        emojiSearchHeaderView = header
        emojiSearchField = field
        emojiSearchFieldHeightConstraint = fieldHeight
        return field
    }

    private var hasEmptyEmojiSearchText: Bool {
        (emojiSearchField?.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func appendEmojiSearchText(_ text: String) {
        guard !text.isEmpty else { return }
        let field = ensureEmojiSearchHeader()
        field.text = (field.text ?? "") + text
        searchEmojiPanel(query: field.text ?? "")
    }

    private func handleEmojiSearchKey(code: Int) -> Bool {
        guard isEmojiSearchMode else { return false }
        let field = ensureEmojiSearchHeader()
        switch code {
        case LimeKeyCode.delete.rawValue:
            guard !(field.text ?? "").isEmpty else { return true }
            field.text = String((field.text ?? "").dropLast())
        case LimeKeyCode.enter.rawValue, LimeKeyCode.done.rawValue:
            hideEmojiPanel()
            return true
        case 32:
            field.text = (field.text ?? "") + " "
        case 1...Int(UInt32.max):
            guard let scalar = Unicode.Scalar(code) else { return false }
            field.text = (field.text ?? "") + String(scalar)
        default:
            return false
        }
        searchEmojiPanel(query: field.text ?? "")
        return true
    }

    @objc private func emojiSearchTextChanged() {
        searchEmojiPanel(query: emojiSearchField?.text ?? "")
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

    private func commitEmoji(_ mapping: Mapping) {
        if composingLength > 0 { clearComposing(force: true) }
        isSelfUpdate = true
        textDocumentProxy.insertText(mapping.word)
        isSelfUpdate = false
        searchServer?.recordEmojiUsage(mapping.word)
    }

    private func showEmojiSearchCandidates(_ candidates: [Mapping]) {
        emojiSearchCandidates = candidates
        candidateBar.setCandidates(candidates, selectedIndex: -1)
        candidateBar.setChevronExpanded(false)
    }

    private func searchEmojiPanel(query: String) {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            showEmojiSearchCandidates(loadEmojiSearchFallbackItems())
            return
        }
        let english = searchServer?.searchEmoji(trimmed, locale: .en, limit: 80) ?? []
        let traditional = searchServer?.searchEmoji(trimmed, locale: .tw, limit: 80) ?? []
        var seen = Set<String>()
        let results = (english + traditional).filter { seen.insert($0.word).inserted }
        showEmojiSearchCandidates(results)
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
        if isEmojiSearchMode {
            pickCandidateManually(mapping)
        } else if mapping.isEnglishSuggestionRecord {
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
        if isEmojiSearchMode {
            hideEmojiPanel()
            return
        }
        if isExpandedCandidatesVisible { hideExpandedCandidates() }
        // Candidate-bar dismiss (✕) button — see docs/AUTO_CHINESE_PUNC.md.
        //  • Punctuation strip already showing → put it away WITHOUT rebuilding it
        //    (case d). cancelComposing() empties the bar via a direct setCandidates([])
        //    and does not route through clearSuggestions(), so it cannot re-surface.
        //  • Otherwise — active composing (case g) OR a related-phrase browse list
        //    (case c) — clear the composition and route through clearSuggestions() so
        //    the auto Chinese-punctuation strip surfaces, matching Android's
        //    dismissCandidateComposing → clearComposing(true). clearComposing(force:
        //    true) deletes any inline composing text, keeps hasCandidatesShown, then
        //    calls clearSuggestions(), which gates on the autoChineseSymbol pref (so
        //    the bar simply clears when the feature is off).
        if hasChineseSymbolCandidatesShown {
            cancelComposing()
            return
        }
        clearComposing(force: true)
    }

    func candidateBarViewDidRequestKeyboardDismiss(_ view: CandidateBarView) {
        if isExpandedCandidatesVisible { hideExpandedCandidates() }
        cancelActiveComposingFromCandidateDismiss()
        dismissKeyboard()
    }

    func candidateBarViewDidRequestMore(_ view: CandidateBarView) {
        // Tap again to collapse
        if isExpandedCandidatesVisible {
            hideExpandedCandidates()
            return
        }

        if isEmojiSearchMode {
            showExpandedCandidates(emojiSearchCandidates, selectedIndex: -1)
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
        // Reuse the single highlight policy so the expanded grid highlights the same default
        // entry the bar does. Keeping this in one place avoids the recurring drift where the
        // grid and the bar disagree (e.g. on an exact-match `，`/`。`). See docs/#96_ISSUE.md.
        let idx = CandidateSelectionPolicy.defaultHighlightedCandidateIndex(all)
        showExpandedCandidates(all, selectedIndex: idx)
    }
}

// MARK: - PopupKeyboardViewDelegate

extension KeyboardViewController: PopupKeyboardViewDelegate {

    func popupKeyboardView(_ popup: PopupKeyboardView, didSelect keyDef: KeyDef) {
        dismissPopupKeyboard()
        firePopupKey(keyDef)
    }

    func popupKeyboardView(_ popup: PopupKeyboardView, didHighlight keyDef: KeyDef?) {
        if keyDef != nil { fireHapticIfEnabled() }
        showPopupKeyPreview(for: keyDef)
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
        commitEmoji(mapping)
    }

    func emojiPanelViewDidRequestABC(_ view: EmojiPanelView) {
        hideEmojiPanel()
    }

    func emojiPanelViewDidRequestBackspace(_ view: EmojiPanelView) {
        textDocumentProxy.deleteBackward()
    }

    func emojiPanelViewDidRequestDismiss(_ view: EmojiPanelView) {
        hideEmojiPanel()
    }

    func emojiPanelViewDidBeginSearch(_ view: EmojiPanelView) {
        showEmojiSearchKeyboard()
    }

    func emojiPanelView(_ view: EmojiPanelView, didChangeSearchQuery query: String) {
        searchEmojiPanel(query: query)
    }
}

extension KeyboardViewController: UITextFieldDelegate {
    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        guard textField === emojiSearchField else { return true }
        hideEmojiPanel()
        return true
    }
}

protocol EmojiPanelViewDelegate: AnyObject {
    func emojiPanelView(_ view: EmojiPanelView, didSelect mapping: Mapping)
    func emojiPanelViewDidRequestABC(_ view: EmojiPanelView)
    func emojiPanelViewDidRequestBackspace(_ view: EmojiPanelView)
    func emojiPanelViewDidRequestDismiss(_ view: EmojiPanelView)
    func emojiPanelViewDidBeginSearch(_ view: EmojiPanelView)
    func emojiPanelView(_ view: EmojiPanelView, didChangeSearchQuery query: String)
}

final class EmojiPanelView: UIView, UITextFieldDelegate, UIScrollViewDelegate {
    static let searchFieldTopInset: CGFloat = 10
    static let searchFieldHeight: CGFloat = 44
    static let searchHeaderHeight: CGFloat = searchFieldTopInset + searchFieldHeight

    weak var delegate: EmojiPanelViewDelegate?

    private let searchField = UISearchTextField()
    private let emojiViewport = UIScrollView()
    private let emojiContentView = UIView()
    private let categoryScrollView = CandidateScrollView()
    private let categoryBar = UIStackView()
    private let categoryModeButton = UIButton(type: .system)
    private let categoryBackspaceButton = UIButton(type: .system)
    private let searchDismissButton = UIButton(type: .system)
    private var emojiPages: [[Mapping]] = []
    private var buttonMappings: [Mapping] = []
    private var keyboardSizeScale: CGFloat = 1.0
    private var categoryBarWidthConstraint: NSLayoutConstraint?
    private var categoryScrollHeightConstraint: NSLayoutConstraint?
    private var categoryModeWidthConstraint: NSLayoutConstraint?
    private var categoryModeHeightConstraint: NSLayoutConstraint?
    private var categoryBackspaceWidthConstraint: NSLayoutConstraint?
    private var categoryBackspaceHeightConstraint: NSLayoutConstraint?
    private var searchFieldHeightConstraint: NSLayoutConstraint?
    private var searchDismissWidthConstraint: NSLayoutConstraint?
    private var searchDismissHeightConstraint: NSLayoutConstraint?
    private var emojiViewportNormalLeadingConstraint: NSLayoutConstraint?
    private var emojiViewportSearchLeadingConstraint: NSLayoutConstraint?
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
    private var theme: Int = 0
    private var systemUserInterfaceStyle: UIUserInterfaceStyle = .light
    private var palette: KeyboardPalette {
        KeyboardPalette.palettes[max(0, min(theme, KeyboardPalette.palettes.count - 1))]
    }
    private var effectiveCandiText: UIColor {
        CandidateBarSystemChrome.labelColor(systemUserInterfaceStyle: systemUserInterfaceStyle)
    }
    var preferredPanelHeight: CGFloat {
        Self.searchHeaderHeight
            + 10
            + CGFloat(EmojiPanelSizing.visibleRows(isSearchMode: false)) * EmojiPanelSizing.buttonSize(keyboardSizeScale: keyboardSizeScale)
            + 8
            + categoryRowHeight()
            + 10
    }

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

    func setKeyboardSizeScale(_ scale: CGFloat) {
        let normalized = EmojiPanelSizing.normalizedKeyboardSizeScale(scale)
        guard abs(normalized - keyboardSizeScale) > 0.001 else { return }
        keyboardSizeScale = normalized
        invalidatePaginationCache()
        applyCategorySizing()
        buildCategoryBar()
        rebuildEmojiButtons()
    }

    func setTheme(_ theme: Int, systemUserInterfaceStyle: UIUserInterfaceStyle) {
        self.theme = theme
        self.systemUserInterfaceStyle = systemUserInterfaceStyle
        applySearchDismissStyle()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let width = bounds.width
        let viewportHeight = emojiViewport.bounds.height
        if abs(width - lastRenderedWidth) > 0.5 || abs(viewportHeight - lastRenderedViewportHeight) > 0.5 {
            rebuildEmojiButtons()
        }
        updateCategoryBarContentWidth()
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
        // Distinct id from the in-search-mode header field (also
        // "lime_emoji_search_field"): this panel field is the one whose tap
        // begins search mode (textFieldDidBeginEditing →
        // emojiPanelViewDidBeginSearch), so UI tests must target it
        // unambiguously to enter search and reveal the dismiss button.
        searchField.accessibilityIdentifier = "lime_emoji_panel_search_field"
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
        categoryScrollView.isScrollEnabled = true
        categoryScrollView.delaysContentTouches = false
        categoryScrollView.canCancelContentTouches = true
        categoryScrollView.showsHorizontalScrollIndicator = false
        categoryScrollView.backgroundColor = LayoutMetrics.TouchTrap.fill
        categoryScrollView.clipsToBounds = true
        categoryScrollView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(categoryScrollView)

        configureTextButton(categoryModeButton, title: returnKeyTitle, action: #selector(tapABC))
        categoryModeButton.tag = 0
        returnKeyboardButton = categoryModeButton
        categoryModeButton.translatesAutoresizingMaskIntoConstraints = false
        addSubview(categoryModeButton)

        let deleteConfig = UIImage.SymbolConfiguration(pointSize: backspaceGlyphSize(), weight: .regular)
        categoryBackspaceButton.setImage(UIImage(systemName: "delete.backward", withConfiguration: deleteConfig), for: .normal)
        categoryBackspaceButton.tintColor = .label
        categoryBackspaceButton.imageView?.contentMode = .scaleAspectFit
        categoryBackspaceButton.layer.cornerRadius = categoryButtonSize() / 2
        categoryBackspaceButton.addTarget(self, action: #selector(tapBackspace), for: .touchUpInside)
        categoryBackspaceButton.translatesAutoresizingMaskIntoConstraints = false
        addSubview(categoryBackspaceButton)

        let dismissConfig = UIImage.SymbolConfiguration(
            pointSize: LayoutMetrics.CandidateBar.Chevron.iconSize(isPad: LayoutLoader.hostIsPad),
            weight: .regular)
        searchDismissButton.setImage(UIImage(systemName: "xmark", withConfiguration: dismissConfig), for: .normal)
        searchDismissButton.accessibilityIdentifier = "lime_emoji_search_dismiss_button"
        searchDismissButton.accessibilityLabel = "Dismiss emoji search"
        searchDismissButton.tintColor = effectiveCandiText
        searchDismissButton.backgroundColor = palette.normalKey.withAlphaComponent(0.15)
        searchDismissButton.imageView?.contentMode = .scaleAspectFit
        searchDismissButton.layer.cornerRadius = 6
        searchDismissButton.layer.masksToBounds = true
        searchDismissButton.addTarget(self, action: #selector(tapDismiss), for: .touchUpInside)
        searchDismissButton.translatesAutoresizingMaskIntoConstraints = false
        searchDismissButton.isHidden = true
        addSubview(searchDismissButton)

        categoryBar.axis = .horizontal
        categoryBar.alignment = .center
        categoryBar.backgroundColor = LayoutMetrics.TouchTrap.fill
        categoryBar.distribution = .fill
        categoryBar.spacing = categorySpacing()
        categoryBar.translatesAutoresizingMaskIntoConstraints = false
        categoryScrollView.addSubview(categoryBar)
        categoryBarWidthConstraint = categoryBar.widthAnchor.constraint(equalToConstant: 1)
        categoryBarWidthConstraint?.isActive = true

        let emojiBottomToCategory = emojiViewport.bottomAnchor.constraint(equalTo: categoryScrollView.topAnchor, constant: -8)
        let emojiBottomToPanel = emojiViewport.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -8)
        emojiBottomToCategoryConstraint = emojiBottomToCategory
        emojiBottomToPanelConstraint = emojiBottomToPanel
        categoryScrollHeightConstraint = categoryScrollView.heightAnchor.constraint(equalToConstant: categoryRowHeight())
        categoryModeWidthConstraint = categoryModeButton.widthAnchor.constraint(equalToConstant: categoryModeKeyWidth())
        categoryModeHeightConstraint = categoryModeButton.heightAnchor.constraint(equalToConstant: categoryButtonSize())
        categoryBackspaceWidthConstraint = categoryBackspaceButton.widthAnchor.constraint(equalToConstant: categoryButtonSize())
        categoryBackspaceHeightConstraint = categoryBackspaceButton.heightAnchor.constraint(equalToConstant: categoryButtonSize())
        searchFieldHeightConstraint = searchField.heightAnchor.constraint(equalToConstant: Self.searchFieldHeight)
        searchDismissWidthConstraint = searchDismissButton.widthAnchor.constraint(equalToConstant: LayoutMetrics.CandidateBar.Chevron.buttonWidth(isPad: LayoutLoader.hostIsPad) / 2)
        searchDismissHeightConstraint = searchDismissButton.heightAnchor.constraint(equalToConstant: emojiButtonSize())
        emojiViewportNormalLeadingConstraint = emojiViewport.leadingAnchor.constraint(equalTo: leadingAnchor)
        emojiViewportSearchLeadingConstraint = emojiViewport.leadingAnchor.constraint(equalTo: searchDismissButton.trailingAnchor)
        emojiViewportSearchLeadingConstraint?.isActive = false

        NSLayoutConstraint.activate([
            searchField.topAnchor.constraint(equalTo: topAnchor, constant: Self.searchFieldTopInset),
            searchField.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            searchField.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),
            searchFieldHeightConstraint!,

            categoryModeButton.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 12),
            categoryModeButton.centerYAnchor.constraint(equalTo: categoryScrollView.centerYAnchor),
            categoryModeWidthConstraint!,
            categoryModeHeightConstraint!,

            categoryScrollView.leadingAnchor.constraint(equalTo: categoryModeButton.trailingAnchor, constant: 4),
            categoryScrollView.trailingAnchor.constraint(equalTo: categoryBackspaceButton.leadingAnchor, constant: -4),
            categoryScrollView.bottomAnchor.constraint(equalTo: safeAreaLayoutGuide.bottomAnchor, constant: -10),
            categoryScrollHeightConstraint!,

            categoryBackspaceButton.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -8),
            categoryBackspaceButton.centerYAnchor.constraint(equalTo: categoryScrollView.centerYAnchor),
            categoryBackspaceWidthConstraint!,
            categoryBackspaceHeightConstraint!,

            searchDismissButton.leadingAnchor.constraint(equalTo: leadingAnchor),
            searchDismissButton.topAnchor.constraint(equalTo: emojiViewport.topAnchor),
            searchDismissWidthConstraint!,
            searchDismissHeightConstraint!,

            categoryBar.leadingAnchor.constraint(equalTo: categoryScrollView.contentLayoutGuide.leadingAnchor),
            categoryBar.trailingAnchor.constraint(equalTo: categoryScrollView.contentLayoutGuide.trailingAnchor),
            categoryBar.topAnchor.constraint(equalTo: categoryScrollView.contentLayoutGuide.topAnchor),
            categoryBar.bottomAnchor.constraint(equalTo: categoryScrollView.contentLayoutGuide.bottomAnchor),
            categoryBar.heightAnchor.constraint(equalTo: categoryScrollView.frameLayoutGuide.heightAnchor),

            emojiViewport.topAnchor.constraint(equalTo: searchField.bottomAnchor, constant: 10),
            emojiViewportNormalLeadingConstraint!,
            emojiViewport.trailingAnchor.constraint(equalTo: trailingAnchor),
            emojiBottomToCategory,
        ])

        buildCategoryBar()
    }

    private func buildCategoryBar() {
        categoryBar.arrangedSubviews.forEach { $0.removeFromSuperview() }
        categoryButtons = []
        categoryBar.spacing = categorySpacing()
        configureTextButton(categoryModeButton, title: returnKeyTitle, action: #selector(tapABC))
        ["clock", "face.smiling", "person.crop.circle", "pawprint", "apple.logo", "car",
         "soccerball", "lightbulb", "heart", "flag"].enumerated().forEach { index, symbol in
            let button = iconButton(symbol, action: #selector(tapCategory))
            button.tag = index + 1
            categoryButtons.append(button)
            categoryBar.addArrangedSubview(button)
        }
        updateCategoryHighlight()
        updateCategoryBarContentWidth()
        resetCategoryScrollPosition()
    }

    private func applyCategorySizing() {
        let deleteConfig = UIImage.SymbolConfiguration(pointSize: backspaceGlyphSize(), weight: .regular)
        categoryBackspaceButton.setImage(UIImage(systemName: "delete.backward", withConfiguration: deleteConfig), for: .normal)
        categoryBackspaceButton.layer.cornerRadius = categoryButtonSize() / 2
        let dismissConfig = UIImage.SymbolConfiguration(
            pointSize: LayoutMetrics.CandidateBar.Chevron.iconSize(isPad: LayoutLoader.hostIsPad),
            weight: .regular)
        searchDismissButton.setImage(UIImage(systemName: "xmark", withConfiguration: dismissConfig), for: .normal)
        configureTextButton(categoryModeButton, title: returnKeyTitle, action: #selector(tapABC))
        categoryScrollHeightConstraint?.constant = categoryRowHeight()
        categoryModeWidthConstraint?.constant = categoryModeKeyWidth()
        categoryModeHeightConstraint?.constant = categoryButtonSize()
        categoryBackspaceWidthConstraint?.constant = categoryButtonSize()
        categoryBackspaceHeightConstraint?.constant = categoryButtonSize()
        searchDismissWidthConstraint?.constant = LayoutMetrics.CandidateBar.Chevron.buttonWidth(isPad: LayoutLoader.hostIsPad) / 2
        searchDismissHeightConstraint?.constant = emojiButtonSize()
        categoryBar.spacing = categorySpacing()
        updateCategoryBarContentWidth()
    }

    func setSearchMode(_ enabled: Bool) {
        isSearchMode = enabled
        resetSearchFieldHeight()
        visibleRows = currentVisibleRows()
        categoryModeButton.isHidden = enabled
        categoryScrollView.isHidden = enabled
        categoryBackspaceButton.isHidden = enabled
        searchDismissButton.isHidden = true
        emojiViewport.isHidden = enabled
        emojiViewportNormalLeadingConstraint?.isActive = !enabled
        emojiViewportSearchLeadingConstraint?.isActive = enabled
        emojiBottomToCategoryConstraint?.isActive = !enabled
        emojiBottomToPanelConstraint?.isActive = enabled
        rebuildEmojiButtons()
    }

    private func applySearchDismissStyle() {
        searchDismissButton.tintColor = effectiveCandiText
        searchDismissButton.backgroundColor = palette.normalKey.withAlphaComponent(0.15)
    }

    func prepareForPresentation() {
        endEditing(true)
        resetSearchFieldHeight()
        setSearchMode(false)
        resetEmojiScrollPosition()
        resetCategoryScrollPosition()
        activeCategoryIndex = 1
        updateCategoryHighlight()
    }

    func resignSearch() {
        searchField.resignFirstResponder()
    }

    func clearSearchText() {
        searchField.text = ""
    }

    private func resetSearchFieldHeight() {
        searchFieldHeightConstraint?.constant = Self.searchFieldHeight
        searchFieldHeightConstraint?.isActive = true
    }

    func appendSearchText(_ text: String) {
        guard !text.isEmpty else { return }
        searchField.text = (searchField.text ?? "") + text
        searchChanged()
    }

    var hasEmptySearchText: Bool {
        (searchField.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var searchText: String {
        searchField.text ?? ""
    }

    func handleSearchKey(code: Int) -> Bool {
        guard isSearchMode, searchField.isFirstResponder else { return false }
        switch code {
        case LimeKeyCode.delete.rawValue:
            guard !(searchField.text ?? "").isEmpty else { return true }
            searchField.text = String((searchField.text ?? "").dropLast())
        case LimeKeyCode.enter.rawValue, LimeKeyCode.done.rawValue:
            delegate?.emojiPanelViewDidRequestDismiss(self)
            return true
        case 32:
            searchField.text = (searchField.text ?? "") + " "
        case 1...Int(UInt32.max):
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
        let cellWidth = isSearchMode
            ? buttonSize
            : normalModeCellWidth(pageWidth: pageWidth, columnsPerPage: columnsPerPage)
        let horizontalInset = isSearchMode
            ? 12
            : normalModeHorizontalInset(pageWidth: pageWidth,
                                        columnsPerPage: columnsPerPage,
                                        cellWidth: cellWidth)
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
            let horizontalInset = normalModeHorizontalInset(pageWidth: pageWidth,
                                                            columnsPerPage: normalModeColumnsPerPage(pageWidth: pageWidth),
                                                            cellWidth: cellWidth)
            let end = start + CGFloat(columnCount) * cellWidth + horizontalInset * 2
            return end >= visibleStart && start <= visibleEnd
        }
    }

    private func needsRenderForCurrentOffset() -> Bool {
        guard !isSearchMode else { return false }
        return Set(renderPageIndexes(pageWidth: emojiPageWidth())) != renderedDisplayPageIndexes
    }

    private func emojiPageWidth() -> CGFloat {
        max(emojiViewport.bounds.width, 1)
    }

    private func emojiPageHeight() -> CGFloat {
        max(emojiViewport.bounds.height, CGFloat(max(1, currentVisibleRows())) * emojiButtonSize())
    }

    private func usesPadEmojiLayout() -> Bool {
        traitCollection.userInterfaceIdiom == .pad || bounds.width >= 700
    }

    private func currentVisibleRows() -> Int {
        EmojiPanelSizing.visibleRows(isSearchMode: isSearchMode)
    }

    private func emojiButtonSize() -> CGFloat {
        emojiButtonSize(forRows: currentVisibleRows())
    }

    private func emojiButtonSize(forRows rows: Int) -> CGFloat {
        let scaledButtonSize = EmojiPanelSizing.buttonSize(keyboardSizeScale: keyboardSizeScale)
        guard !isSearchMode else { return scaledButtonSize }
        let availableHeight = emojiViewport.bounds.height
        guard availableHeight > 1 else { return scaledButtonSize }
        let rows = CGFloat(max(rows, 1))
        return max(floor(availableHeight / rows), 1)
    }

    private func emojiFontSize() -> CGFloat {
        emojiFontSize(for: emojiButtonSize())
    }

    private func emojiFontSize(for buttonSize: CGFloat) -> CGFloat {
        EmojiPanelSizing.emojiGlyphSize(keyboardSizeScale: keyboardSizeScale)
    }

    private func normalModeOuterInset(pageWidth: CGFloat) -> CGFloat {
        guard usesPadEmojiLayout() else { return 12 }
        return clamped(pageWidth * 0.025, lower: 24, upper: 48)
    }

    private func normalModeTargetCellWidth() -> CGFloat {
        max(emojiButtonSize(), emojiFontSize() + 32)
    }

    private func normalModeColumnsPerPage(pageWidth: CGFloat) -> Int {
        let targetCellWidth = usesPadEmojiLayout() ? normalModeTargetCellWidth() : emojiButtonSize()
        let fitted = Int((pageWidth - normalModeOuterInset(pageWidth: pageWidth) * 2) / targetCellWidth)
        return usesPadEmojiLayout() ? min(10, max(8, fitted)) : min(10, max(7, fitted))
    }

    private func normalModeCellWidth(pageWidth: CGFloat, columnsPerPage: Int) -> CGFloat {
        guard usesPadEmojiLayout() else {
            let horizontalInset = normalModeOuterInset(pageWidth: pageWidth)
            return (pageWidth - horizontalInset * 2) / CGFloat(columnsPerPage)
        }
        return normalModeTargetCellWidth()
    }

    private func normalModeHorizontalInset(pageWidth: CGFloat,
                                           columnsPerPage: Int,
                                           cellWidth: CGFloat) -> CGFloat {
        let outerInset = normalModeOuterInset(pageWidth: pageWidth)
        guard usesPadEmojiLayout() else { return outerInset }
        return outerInset
    }

    private func normalModeCellWidth(pageWidth: CGFloat) -> CGFloat {
        let columnsPerPage = normalModeColumnsPerPage(pageWidth: pageWidth)
        return normalModeCellWidth(pageWidth: pageWidth, columnsPerPage: columnsPerPage)
    }

    private func clamped(_ value: CGFloat, lower: CGFloat, upper: CGFloat) -> CGFloat {
        min(max(value, lower), upper)
    }

    private func updateCategoryBarContentWidth() {
        guard let widthConstraint = categoryBarWidthConstraint else { return }
        let iconCount = categoryButtons.count
        let arrangedCount = categoryButtons.count
        let spacingWidth = CGFloat(max(arrangedCount - 1, 0)) * categorySpacing()
        let contentWidth = CGFloat(iconCount) * categoryButtonSize()
            + spacingWidth
        let targetWidth = contentWidth
        if abs(widthConstraint.constant - targetWidth) > 0.5 {
            widthConstraint.constant = targetWidth
            categoryScrollView.contentSize = CGSize(width: targetWidth,
                                                    height: categoryRowHeight())
        }
        let centerInset = max(0, (categoryScrollView.bounds.width - contentWidth) / 2)
        if abs(categoryScrollView.contentInset.left - centerInset) > 0.5 {
            let wasAtStart = abs(categoryScrollView.contentOffset.x + categoryScrollView.contentInset.left) < 0.5
            categoryScrollView.contentInset.left = centerInset
            categoryScrollView.contentInset.right = centerInset
            if wasAtStart {
                categoryScrollView.setContentOffset(CGPoint(x: -centerInset, y: 0), animated: false)
            }
        }
        clampCategoryScrollPosition()
    }

    private func categoryButtonSize() -> CGFloat {
        EmojiPanelSizing.categoryButtonSize(keyboardSizeScale: keyboardSizeScale)
    }

    private func categoryModeKeyWidth() -> CGFloat {
        EmojiPanelSizing.modeKeyWidth(keyboardSizeScale: keyboardSizeScale)
    }

    private func categoryRowHeight() -> CGFloat {
        EmojiPanelSizing.categoryRowHeight(keyboardSizeScale: keyboardSizeScale)
    }

    private func categoryGlyphSize() -> CGFloat {
        EmojiPanelSizing.categoryGlyphSize(keyboardSizeScale: keyboardSizeScale)
    }

    private func backspaceGlyphSize() -> CGFloat {
        EmojiPanelSizing.backspaceGlyphSize(keyboardSizeScale: keyboardSizeScale)
    }

    private func modeKeyGlyphSize() -> CGFloat {
        EmojiPanelSizing.modeKeyGlyphSize(keyboardSizeScale: keyboardSizeScale)
    }

    private func categorySpacing() -> CGFloat {
        EmojiPanelSizing.categorySpacing(keyboardSizeScale: keyboardSizeScale)
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

    private func resetEmojiScrollPosition() {
        emojiContentOffsetX = 0
        emojiViewport.setContentOffset(.zero, animated: false)
    }

    private func resetCategoryScrollPosition() {
        categoryScrollView.setContentOffset(CGPoint(x: -categoryScrollView.contentInset.left, y: 0),
                                            animated: false)
    }

    private func clampCategoryScrollPosition() {
        let minOffsetX = -categoryScrollView.contentInset.left
        let maxOffsetX = max(minOffsetX,
                             categoryScrollView.contentSize.width
                             - categoryScrollView.bounds.width
                             + categoryScrollView.contentInset.right)
        let clampedX = min(max(categoryScrollView.contentOffset.x, minOffsetX), maxOffsetX)
        if abs(categoryScrollView.contentOffset.x - clampedX) > 0.5 {
            categoryScrollView.setContentOffset(CGPoint(x: clampedX, y: 0), animated: false)
        }
    }

    private func configureTextButton(_ button: UIButton, title: String, action: Selector) {
        button.setTitle(title, for: .normal)
        button.titleLabel?.font = UIFont.systemFont(ofSize: modeKeyGlyphSize(), weight: .regular)
        button.tintColor = .label
        button.backgroundColor = LayoutMetrics.TouchTrap.fill
        button.removeTarget(nil, action: nil, for: .touchUpInside)
        button.addTarget(self, action: action, for: .touchUpInside)
        button.layer.cornerRadius = categoryButtonSize() / 2
    }

    private func iconButton(_ symbol: String, action: Selector) -> UIButton {
        let button = UIButton(type: .system)
        let config = UIImage.SymbolConfiguration(pointSize: categoryGlyphSize(), weight: .regular)
        button.setImage(UIImage(systemName: symbol, withConfiguration: config), for: .normal)
        button.tintColor = .label
        button.backgroundColor = LayoutMetrics.TouchTrap.fill
        button.imageView?.contentMode = .scaleAspectFit
        button.addTarget(self, action: action, for: .touchUpInside)
        button.layer.cornerRadius = categoryButtonSize() / 2
        button.widthAnchor.constraint(equalToConstant: categoryButtonSize()).isActive = true
        button.heightAnchor.constraint(equalToConstant: categoryButtonSize()).isActive = true
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

    @objc private func tapDismiss() {
        delegate?.emojiPanelViewDidRequestDismiss(self)
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
        let nextActive = min(max(active, 1), max(categoryButtons.count, 1))
        guard nextActive != activeCategoryIndex else { return }
        activeCategoryIndex = nextActive
        updateCategoryHighlight()
    }

    private func updateCategoryHighlight() {
        for button in categoryButtons {
            let active = button.tag == activeCategoryIndex
            button.backgroundColor = active ? UIColor.label.withAlphaComponent(0.14) : LayoutMetrics.TouchTrap.fill
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
        delegate?.emojiPanelViewDidRequestDismiss(self)
        return true
    }
}
