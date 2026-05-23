import XCTest
@testable import LimeIME

final class KeyboardViewControllerTest: XCTestCase {

    private struct KeyboardLayoutFixture: Decodable {
        let rows: [KeyboardRowFixture]
    }

    private struct KeyboardRowFixture: Decodable {
        let isBottomRow: Bool
        let keys: [KeyboardKeyFixture]
    }

    private struct KeyboardKeyFixture: Decodable {
        let code: Int
        let label: String
        let sublabel: String
        let widthPercent: Double
        let longPressCode: Int?
    }

    func testEmojiKeyboardKeyCodesUseReservedCrossPlatformRange() {
        XCTAssertEqual(LimeKeyCode.emojiPanel.rawValue, -201)
        XCTAssertEqual(LimeKeyCode.emojiABC.rawValue, -202)
        XCTAssertEqual(LimeKeyCode.emojiCategoryRecent.rawValue, -203)
        XCTAssertEqual(LimeKeyCode.emojiCategoryPeople.rawValue, -205)
        XCTAssertEqual(LimeKeyCode.emojiCategoryTravel.rawValue, -208)
        XCTAssertEqual(LimeKeyCode.emojiCategoryActivities.rawValue, -209)
        XCTAssertEqual(LimeKeyCode.emojiCategoryFlags.rawValue, -212)
    }

    func testEnglishLayoutHasChineseSwitchOnBottomRow() {
        let rows = LimeKeyLayout.english.rows
        let bottomCodes = rows.last?.keys.map(\.code) ?? []

        XCTAssertTrue(bottomCodes.contains(LimeKeyCode.switchToIM.rawValue))
        XCTAssertFalse(bottomCodes.contains(LimeKeyCode.emojiPanel.rawValue))
    }

    func testIPhoneEnglishJsonLayoutsHaveChineseSwitchOnBottomRow() throws {
        for layoutID in ["lime_english", "lime_english_number"] {
            let layout = try loadKeyboardLayoutFixture(layoutID)
            let bottomCodes = layout.rows.first(where: { $0.isBottomRow })?.keys.map(\.code) ?? []
            XCTAssertTrue(bottomCodes.contains(LimeKeyCode.switchToIM.rawValue),
                          "\(layoutID) should have 中 on the bottom row")
        }
    }

    func testIPhoneEnglishJsonLayoutsHaveChineseSwitchOnBottomRowAndFullSpaceWidth() throws {
        for layoutID in ["lime_english", "lime_english_number"] {
            let layout = try loadKeyboardLayoutFixture(layoutID)
            let bottom = try XCTUnwrap(layout.rows.first(where: { $0.isBottomRow }))
            let bottomCodes = bottom.keys.map(\.code)
            let space = try XCTUnwrap(bottom.keys.first(where: { $0.code == 32 }))

            XCTAssertTrue(bottomCodes.contains(LimeKeyCode.switchToIM.rawValue),
                          "\(layoutID) should place 中 on the bottom row")
            XCTAssertFalse(bottomCodes.contains(LimeKeyCode.emojiPanel.rawValue),
                           "\(layoutID) should not have emoji launcher on the bottom row")
            XCTAssertEqual(space.widthPercent, 30.0, "\(layoutID) should keep the full-width space key")
        }
    }

    func testIPhoneEnglishJsonLayoutsHaveNoEmojiLauncherKeyEmojiAccessedViaCandidateBar() throws {
        for layoutID in ["lime_english", "lime_english_number"] {
            let layout = try loadKeyboardLayoutFixture(layoutID)
            let allCodes = layout.rows.flatMap { $0.keys.map(\.code) }
            XCTAssertFalse(allCodes.contains(LimeKeyCode.emojiPanel.rawValue),
                           "\(layoutID): emoji launcher was moved to the candidate bar")
        }
    }

    // MARK: - iPad bottom-row tests (docs/IOS_KB_GAP.md §3.4)

    private let iPadLayoutsForBottomRowAudit: [String] = [
        "lime_phonetic_ipad", "lime_phonetic_ipad_shift",
        "lime_array_ipad", "lime_array_ipad_shift",
        "lime_array_number_ipad", "lime_array_number_ipad_shift",
        "lime_cj_ipad", "lime_cj_ipad_shift",
        "lime_cj_number_ipad", "lime_cj_number_ipad_shift",
        "lime_dayi_ipad", "lime_dayi_ipad_shift",
        "lime_dayi_sym_ipad", "lime_dayi_sym_ipad_shift",
        "lime_et26_ipad", "lime_et26_ipad_shift",
        "lime_et_41_ipad", "lime_et_41_ipad_shift",
        "lime_ez_ipad", "lime_ez_ipad_shift",
        "lime_hs_ipad", "lime_hs_ipad_shift",
        "lime_hsu_ipad", "lime_hsu_ipad_shift",
        "lime_wb_ipad", "lime_wb_ipad_shift",
    ]

    func testIPadBottomRowHasEmojiLeftOfSpaceAndNoVoiceKey() throws {
        for id in iPadLayoutsForBottomRowAudit {
            let layout = try loadKeyboardLayoutFixture(id)
            let bottom = try XCTUnwrap(layout.rows.first(where: { $0.isBottomRow }),
                                       "\(id): missing isBottomRow")
            let codes = bottom.keys.map(\.code)
            let spaceIx = try XCTUnwrap(codes.firstIndex(of: 32),
                                        "\(id): no space key in bottom row")
            XCTAssertEqual(codes[spaceIx - 1], LimeKeyCode.emojiPanel.rawValue,
                           "\(id): emoji (-201) must be immediately left of space")
            XCTAssertFalse(codes.contains(LimeKeyCode.voiceInput.rawValue),
                           "\(id): iOS layouts must not expose voiceInput (-220)")
        }
    }

    func testAllIPadJsonLayoutsUseEmojiInsteadOfMic() throws {
        let layoutsURL = projectFileURL("LimeKeyboard/Layouts")
        let urls = try FileManager.default.contentsOfDirectory(
            at: layoutsURL,
            includingPropertiesForKeys: nil
        ).filter { $0.lastPathComponent.contains("_ipad") && $0.pathExtension == "json" }

        XCTAssertFalse(urls.isEmpty)
        for url in urls {
            let source = try String(contentsOf: url, encoding: .utf8)
            XCTAssertFalse(source.contains(#""icon": "mic""#), url.lastPathComponent)
            XCTAssertFalse(source.contains(#""code": -99"#), url.lastPathComponent)
        }
    }

    func testIPadBottomRowSumsToHundredPercent() throws {
        for id in iPadLayoutsForBottomRowAudit {
            let layout = try loadKeyboardLayoutFixture(id)
            let bottom = try XCTUnwrap(layout.rows.first(where: { $0.isBottomRow }))
            let sum = bottom.keys.map(\.widthPercent).reduce(0, +)
            XCTAssertEqual(sum, 100.0, accuracy: 0.01,
                           "\(id): bottom row widthPercent should sum to 100")
        }
    }

    func testIPadBottomRowGlobeAndKeyboardKeysExposeOptionsMenuLongPress() throws {
        for id in iPadLayoutsForBottomRowAudit {
            let layout = try loadKeyboardLayoutFixture(id)
            let bottom = try XCTUnwrap(layout.rows.first(where: { $0.isBottomRow }))
            let globe = try XCTUnwrap(bottom.keys.first(where: { $0.code == LimeKeyCode.globe.rawValue }),
                                      "\(id): missing globe key")
            let keyboard = try XCTUnwrap(bottom.keys.first(where: { $0.code == LimeKeyCode.done.rawValue }),
                                         "\(id): missing keyboard dismiss/options key")

            XCTAssertEqual(globe.longPressCode, LimeKeyCode.keyboardOptionsMenu.rawValue,
                           "\(id): globe carries the legacy options sentinel but must route to the iOS picker")
            XCTAssertEqual(keyboard.longPressCode, LimeKeyCode.keyboardOptionsMenu.rawValue,
                           "\(id): keyboard key long press should open the keyboard options menu")
        }
    }

    func testIPadEnglishNumberShiftLayoutShowsShiftedKeys() throws {
        let layout = try loadKeyboardLayoutFixture("lime_english_number_ipad_shift")
        let keys = layout.rows.flatMap(\.keys)
        let tilde = try XCTUnwrap(keys.first { $0.code == 126 })
        let q = try XCTUnwrap(keys.first { $0.code == 113 })
        let leftBrace = try XCTUnwrap(keys.first { $0.code == 123 })
        let lessThan = try XCTUnwrap(keys.first { $0.code == 60 })

        XCTAssertEqual(tilde.label, "~")
        XCTAssertEqual(q.label, "Q")
        XCTAssertEqual(leftBrace.label, "{")
        XCTAssertEqual(lessThan.label, "<")
    }

    func testIPadOptionsMenuKeysAreNotTreatedAsDualRowSecondaryGlyphKeys() {
        let keyboardKey = KeyDef(code: LimeKeyCode.done.rawValue,
                                 widthPercent: 8,
                                 icon: "keyboard.chevron.compact.down",
                                 isModifier: true,
                                 longPressCode: LimeKeyCode.keyboardOptionsMenu.rawValue)
        let globeKey = KeyDef(code: LimeKeyCode.globe.rawValue,
                              widthPercent: 8,
                              icon: "globe",
                              isModifier: true,
                              longPressCode: LimeKeyCode.keyboardOptionsMenu.rawValue)
        let dualGlyphKey = KeyDef(code: 49,
                                  label: "!\n1",
                                  widthPercent: 6.6,
                                  longPressCode: 33)

        XCTAssertFalse(KeyboardGesturePolicy.shouldUseDualRowGesture(isPad: true,
                                                                      layoutId: "lime_english_ipad",
                                                                      keyDef: keyboardKey))
        XCTAssertFalse(KeyboardGesturePolicy.shouldUseDualRowGesture(isPad: true,
                                                                      layoutId: "lime_english_ipad",
                                                                      keyDef: globeKey))
        XCTAssertTrue(KeyboardGesturePolicy.shouldUseDualRowGesture(isPad: true,
                                                                    layoutId: "lime_english_ipad",
                                                                    keyDef: dualGlyphKey))
        XCTAssertTrue(KeyboardGesturePolicy.shouldUseDualRowGesture(isPad: true,
                                                                    layoutId: "lime_english_ipad_shift",
                                                                    keyDef: dualGlyphKey))
    }

    func testDayiSymbolIPadShiftKeepsShiftedRootPunctuation() throws {
        let layout = try loadKeyboardLayoutFixture("lime_dayi_sym_ipad_shift")
        let keys = layout.rows.flatMap(\.keys)
        let expectedPunctuationByRoot: [String: (code: Int, label: String)] = [
            "虫": (58, ":"),
            "力": (60, "<"),
            "舟": (62, ">"),
            "竹": (63, "?")
        ]

        for (root, expected) in expectedPunctuationByRoot {
            let key = try XCTUnwrap(keys.first { $0.sublabel == root },
                                    "Dayi symbol iPad shift layout should keep shifted punctuation for \(root)")
            XCTAssertEqual(key.code, expected.code)
            XCTAssertEqual(key.label, expected.label)
        }
    }

    func testGlobeRoutesToSystemPickerWhileKeyboardKeyRoutesToLimeOptionsMenu() {
        let keyboardKey = KeyDef(code: LimeKeyCode.done.rawValue,
                                 widthPercent: 8,
                                 icon: "keyboard.chevron.compact.down",
                                 isModifier: true,
                                 longPressCode: LimeKeyCode.keyboardOptionsMenu.rawValue)
        let globeKey = KeyDef(code: LimeKeyCode.globe.rawValue,
                              widthPercent: 8,
                              icon: "globe",
                              isModifier: true,
                              longPressCode: LimeKeyCode.keyboardOptionsMenu.rawValue)

        XCTAssertTrue(KeyboardGesturePolicy.shouldUseLimeOptionsMenuGesture(keyDef: keyboardKey))
        XCTAssertFalse(KeyboardGesturePolicy.shouldUseLimeOptionsMenuGesture(keyDef: globeKey))
    }

    func testKeyLayoutHasVoiceInputCode() {
        XCTAssertEqual(LimeKeyCode.voiceInput.rawValue, -220)
    }

    func testMomentaryShiftDoesNotResetAfterCharacterWhileShiftKeyIsHeld() {
        XCTAssertFalse(ShiftResetPolicy.shouldResetAfterCharacter(isShiftOn: true,
                                                                  capsLock: false,
                                                                  shiftKeyIsHeld: true))
        XCTAssertTrue(ShiftResetPolicy.shouldResetAfterCharacter(isShiftOn: true,
                                                                 capsLock: false,
                                                                 shiftKeyIsHeld: false))
    }

    func testHeldShiftOnlyResetsOnReleaseAfterItModifiedACharacter() {
        XCTAssertTrue(ShiftResetPolicy.shouldResetAfterShiftRelease(capsLock: false,
                                                                    holdModifiedCharacter: true))
        XCTAssertFalse(ShiftResetPolicy.shouldResetAfterShiftRelease(capsLock: false,
                                                                     holdModifiedCharacter: false))
        XCTAssertFalse(ShiftResetPolicy.shouldResetAfterShiftRelease(capsLock: true,
                                                                     holdModifiedCharacter: true))
    }

    func testShiftPressPolicyIgnoresRepeatedPressDuringSamePhysicalHold() {
        XCTAssertTrue(ShiftPressPolicy.shouldHandleShiftPress(wasShiftKeyHeld: false))
        XCTAssertFalse(ShiftPressPolicy.shouldHandleShiftPress(wasShiftKeyHeld: true))
    }

    func testShiftHoldTouchPolicyRequiresAnotherActiveTouch() {
        XCTAssertTrue(ShiftHoldTouchPolicy.isShiftStillHeld(activeTouchCount: 2))
        XCTAssertFalse(ShiftHoldTouchPolicy.isShiftStillHeld(activeTouchCount: 1))
        XCTAssertFalse(ShiftHoldTouchPolicy.isShiftStillHeld(activeTouchCount: 0))
    }

    func testShiftHoldTouchPolicyKeepsExistingHoldWhenCharacterTouchReportsOnlyItself() {
        XCTAssertTrue(ShiftHoldTouchPolicy.isShiftStillHeld(activeTouchCount: 1,
                                                           wasShiftAlreadyHeld: true))
        XCTAssertFalse(ShiftHoldTouchPolicy.isShiftStillHeld(activeTouchCount: 1,
                                                            wasShiftAlreadyHeld: false))
        XCTAssertFalse(ShiftHoldTouchPolicy.isShiftStillHeld(activeTouchCount: 0,
                                                            wasShiftAlreadyHeld: true))
    }

    func testEnglishAutoCapRecognizesNewlinesQuotesAndAbbreviations() {
        XCTAssertTrue(EnglishKeyboardPolicy.shouldAutoCapitalize(before: "Hello.\n"))
        XCTAssertTrue(EnglishKeyboardPolicy.shouldAutoCapitalize(before: "She said \"Hello.\" "))
        XCTAssertTrue(EnglishKeyboardPolicy.shouldAutoCapitalize(before: "Ready?) "))
        XCTAssertFalse(EnglishKeyboardPolicy.shouldAutoCapitalize(before: "e."))
        XCTAssertFalse(EnglishKeyboardPolicy.shouldAutoCapitalize(before: "Mr. "))
        XCTAssertFalse(EnglishKeyboardPolicy.shouldAutoCapitalize(before: "U.S. "))
    }

    func testEnglishDoubleSpacePeriodOnlyAfterWordLikeContext() {
        XCTAssertTrue(EnglishKeyboardPolicy.shouldInsertPeriodForDoubleSpace(before: "hello "))
        XCTAssertTrue(EnglishKeyboardPolicy.shouldInsertPeriodForDoubleSpace(before: "Go2 "))
        XCTAssertTrue(EnglishKeyboardPolicy.shouldInsertPeriodForDoubleSpace(before: "done) "))
        XCTAssertFalse(EnglishKeyboardPolicy.shouldInsertPeriodForDoubleSpace(before: "hello. "))
        XCTAssertFalse(EnglishKeyboardPolicy.shouldInsertPeriodForDoubleSpace(before: "http://lime-ime.github.io "))
    }

    func testIOSBundlesDoNotDeclareVoiceInputPrivacyUsageDescriptions() throws {
        for plistURL in [
            projectFileURL("LimeKeyboard/Info.plist"),
            projectFileURL("LimeSettings/Info.plist"),
        ] {
            let data = try Data(contentsOf: plistURL)
            let plist = try XCTUnwrap(
                PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any]
            )

            XCTAssertNil(plist["NSMicrophoneUsageDescription"],
                         "\(plistURL.lastPathComponent) should not request microphone privacy usage")
            XCTAssertNil(plist["NSSpeechRecognitionUsageDescription"],
                         "\(plistURL.lastPathComponent) should not request speech-recognition privacy usage")
        }
    }

    func testCandidateChevronExpansionAllowsEnglishSuggestionsWithoutComposingBuffer() {
        XCTAssertTrue(CandidateExpansionPolicy.shouldExpand(
            hasCandidatesShown: true,
            composing: "",
            hasChineseSymbolCandidatesShown: false
        ))
    }

    func testCandidateChevronExpansionStillRequiresVisibleCandidates() {
        XCTAssertFalse(CandidateExpansionPolicy.shouldExpand(
            hasCandidatesShown: false,
            composing: "",
            hasChineseSymbolCandidatesShown: false
        ))
    }

    func testEmojiPanelPaginatorKeepsCategoryCompactAndColumnPacked() {
        let category = (0..<75).map { index in
            Mapping(id: index, code: "", word: "e\(index)",
                    score: 0, baseScore: 0,
                    recordType: Mapping.RecordType.emoji)
        }

        let result = EmojiPanelPaginator.displayPages(sourcePages: [[], category],
                                                      cellsPerPage: 28,
                                                      rowsPerPage: 4,
                                                      categoryButtonCount: 3)

        XCTAssertEqual(result.pages.map { $0.map(\.word) }, [
            [],
            Array(0..<75).map { "e\($0)" },
        ])
        XCTAssertEqual(result.categoryStartDisplayPageIndexes, [0, 0, 1])
        XCTAssertEqual(result.sourcePageIndexes, [0, 1])
        XCTAssertEqual(result.columnCounts, [7, 19])
        XCTAssertEqual(EmojiPanelScrollLayout.cellPosition(index: 5, rows: 4).column, 1)
        XCTAssertEqual(EmojiPanelScrollLayout.cellPosition(index: 5, rows: 4).row, 1)
    }

    func testEmojiRecentSeedQueueKeepsFallbackBehindRealRecent() {
        let recent = ["🎯", "😀"].map { emojiMapping($0) }
        let fallback = ["😀", "😂", "😍"].map { emojiMapping($0) }

        let merged = EmojiRecentSeedQueue.merged(recent: recent,
                                                 fallback: fallback,
                                                 limit: 4)

        XCTAssertEqual(merged.map(\.word), ["🎯", "😀", "😂", "😍"])
    }

    func testEmojiRecentSeedQueueLetsRealRecentKickOutFallbackByLimit() {
        let recent = ["1", "2", "3"].map { emojiMapping($0) }
        let fallback = ["4", "5", "6"].map { emojiMapping($0) }

        let merged = EmojiRecentSeedQueue.merged(recent: recent,
                                                 fallback: fallback,
                                                 limit: 4)

        XCTAssertEqual(merged.map(\.word), ["1", "2", "3", "4"])
    }

    func testEmojiPanelScrollLayoutKeepsContentCoordinatesStableWhileScrolling() {
        let contentFrame = EmojiPanelScrollLayout.contentFrame(viewportWidth: 390,
                                                               contentWidth: 1400,
                                                               contentHeight: 180)
        let firstCellX = EmojiPanelScrollLayout.cellX(pageOffsetX: 390,
                                                      column: 0,
                                                      cellWidth: 52,
                                                      horizontalInset: 12)
        let laterCellX = EmojiPanelScrollLayout.cellX(pageOffsetX: 390,
                                                      column: 3,
                                                      cellWidth: 52,
                                                      horizontalInset: 12)

        XCTAssertEqual(contentFrame.origin.x, 0)
        XCTAssertEqual(contentFrame.width, 1400)
        XCTAssertEqual(firstCellX, 402)
        XCTAssertEqual(laterCellX, 558)
    }

    func testEmojiPanelSourceReturnKeyTitlesMatchSourceKeyboard() {
        XCTAssertEqual(EmojiPanelSource.english.returnKeyTitle, "ABC")
        XCTAssertEqual(EmojiPanelSource.chineseIM.returnKeyTitle, "中")
    }

    func testEmojiPanelSourceCapturesCurrentLanguageMode() {
        XCTAssertEqual(EmojiPanelSource.source(isEnglishOnly: true), .english)
        XCTAssertEqual(EmojiPanelSource.source(isEnglishOnly: false), .chineseIM)
    }

    func testCandidateBarChromeUsesSystemAppearanceOnly() {
        XCTAssertTrue(CandidateBarSystemChrome.usesLightForeground(systemUserInterfaceStyle: .dark))
        XCTAssertFalse(CandidateBarSystemChrome.usesLightForeground(systemUserInterfaceStyle: .light))
        XCTAssertFalse(CandidateBarSystemChrome.usesLightForeground(systemUserInterfaceStyle: .unspecified))
    }

    func testPopupKeyboardOutsideTapOverlayUsesTouchTrapFill() throws {
        let sourceURL = projectFileURL("LimeKeyboard/KeyboardViewController.swift")
        let source = try String(contentsOf: sourceURL, encoding: .utf8)
        let pattern = #"private func showPopupKeyboard[\s\S]*?\n    \}"#
        let regex = try NSRegularExpression(pattern: pattern)
        let range = NSRange(source.startIndex..<source.endIndex, in: source)
        let match = try XCTUnwrap(regex.firstMatch(in: source, range: range))
        let method = String(source[Range(match.range, in: source)!])

        XCTAssertTrue(method.contains("overlay.backgroundColor = LayoutMetrics.TouchTrap.fill"))
    }

    func testCandidateBarDismissRoutesThroughForcedComposingClear() throws {
        let sourceURL = projectFileURL("LimeKeyboard/KeyboardViewController.swift")
        let source = try String(contentsOf: sourceURL, encoding: .utf8)
        let pattern = #"func candidateBarViewDidRequestDismiss[\s\S]*?\n    \}"#
        let regex = try NSRegularExpression(pattern: pattern)
        let range = NSRange(source.startIndex..<source.endIndex, in: source)
        let match = try XCTUnwrap(regex.firstMatch(in: source, range: range))
        let method = String(source[Range(match.range, in: source)!])

        XCTAssertTrue(method.contains("cancelActiveComposingFromCandidateDismiss()"))
        XCTAssertFalse(method.contains("cancelComposing()"))

        let helperPattern = #"func cancelActiveComposingFromCandidateDismiss[\s\S]*?\n    \}"#
        let helperRegex = try NSRegularExpression(pattern: helperPattern)
        let helperMatch = try XCTUnwrap(helperRegex.firstMatch(in: source, range: range))
        let helper = String(source[Range(helperMatch.range, in: source)!])
        XCTAssertTrue(helper.contains("max(composingLength, mComposing.count)"))
    }

    func testCandidateBarIdleToolVisibilityIsDelayedAndSuppressible() throws {
        let sourceURL = projectFileURL("LimeKeyboard/CandidateBarView.swift")
        let source = try String(contentsOf: sourceURL, encoding: .utf8)

        XCTAssertTrue(source.contains("idleToolsRevealDelay: TimeInterval = 0.12"))
        XCTAssertTrue(source.contains("idleToolsSuppressed"))
        XCTAssertTrue(source.contains("scheduleIdleToolsReveal()"))
        XCTAssertTrue(source.contains("return !hasCandidates && idleRevealReady && !idleToolsSuppressed && allowTool"))
        XCTAssertTrue(source.contains("shouldShowActiveChrome"))
        XCTAssertTrue(source.contains("return hasCandidates || (!showIdleTools && !idleRevealReady)"))
    }

    func testIMDetailShareButtonUsesConstrainedLayoutTrailingSlot() throws {
        let sourceURL = projectFileURL("LimeSettings/Views/IMDetailView.swift")
        let source = try String(contentsOf: sourceURL, encoding: .utf8)
        let layoutSource = try String(
            contentsOf: projectFileURL("LimeSettings/LimeSettingsView.swift"),
            encoding: .utf8
        )

        XCTAssertTrue(source.contains(".constrainedDetailLayout(im.label) {"))
        XCTAssertFalse(source.contains("ToolbarItem(placement: .navigationBarTrailing)"))
        XCTAssertTrue(source.contains("showSharePicker = true"))
        XCTAssertTrue(source.contains("square.and.arrow.up"))
        XCTAssertTrue(source.contains(".font(.title2.weight(.semibold))"))
        XCTAssertTrue(source.contains(".frame(width: 44, height: 44)"))
        XCTAssertTrue(layoutSource.contains("private let titleSectionHeight: CGFloat = 60"))
        XCTAssertTrue(layoutSource.contains("HStack(alignment: .center, spacing: 12)"))
        XCTAssertTrue(layoutSource.contains(".frame(height: titleSectionHeight)"))
    }

    func testSettingsGroupedSurfacesMatchSetupTabColors() throws {
        let settingsSource = try String(
            contentsOf: projectFileURL("LimeSettings/LimeSettingsView.swift"),
            encoding: .utf8
        )
        XCTAssertTrue(settingsSource.contains("func setupMatchedGroupedSurface() -> some View"))
        XCTAssertTrue(settingsSource.contains(".scrollContentBackground(.hidden)"))
        XCTAssertTrue(settingsSource.contains(".background(Color(.systemBackground))"))
        XCTAssertTrue(settingsSource.contains(".listRowBackground(Color(.secondarySystemBackground))"))

        for relativePath in [
            "LimeSettings/Views/IMListView.swift",
            "LimeSettings/Views/IMDetailView.swift",
            "LimeSettings/Views/IMInstallView.swift",
            "LimeSettings/Views/KeyboardPickerView.swift",
            "LimeSettings/Views/PreferencesTabView.swift",
            "LimeSettings/Views/ReverseLookupSettingsView.swift"
        ] {
            let source = try String(contentsOf: projectFileURL(relativePath), encoding: .utf8)
            XCTAssertTrue(source.contains(".setupMatchedGroupedSurface()"), relativePath)
        }
    }

    private func emojiMapping(_ word: String) -> Mapping {
        Mapping(id: 0, code: "", word: word,
                score: 0, baseScore: 0,
                recordType: Mapping.RecordType.emoji)
    }

    func testLimeToastStateShowsTrimmedNonEmptyMessage() {
        var state = LimeToastState()

        XCTAssertTrue(state.show("  大易  "))

        XCTAssertEqual(state.message, "大易")
        XCTAssertTrue(state.isShowing)
    }

    func testLimeToastStateRejectsEmptyMessageWithoutReplacingExistingToast() {
        var state = LimeToastState()
        XCTAssertTrue(state.show("大易"))

        XCTAssertFalse(state.show("   "))

        XCTAssertEqual(state.message, "大易")
        XCTAssertTrue(state.isShowing)
    }

    func testLimeToastStateHideClearsMessage() {
        var state = LimeToastState()
        XCTAssertTrue(state.show("反查結果"))

        state.hide()

        XCTAssertNil(state.message)
        XCTAssertFalse(state.isShowing)
    }

    private func loadKeyboardLayoutFixture(_ layoutID: String) throws -> KeyboardLayoutFixture {
        let url = projectFileURL("LimeKeyboard/Layouts/\(layoutID).json")
        let data = try Data(contentsOf: url)
        return try JSONDecoder().decode(KeyboardLayoutFixture.self, from: data)
    }

    private func projectFileURL(_ relativePath: String) -> URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
    }

}
