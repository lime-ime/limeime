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

    func testURLAndSearchKeyboardTypesUsePersistedLanguageModeRoute() {
        XCTAssertFalse(KeyboardTypePolicy.isForcedEnglishKeyboardType(.URL))
        XCTAssertFalse(KeyboardTypePolicy.isForcedEnglishKeyboardType(.webSearch))
        XCTAssertFalse(KeyboardTypePolicy.isForcedEnglishKeyboardType(.default))

        XCTAssertTrue(KeyboardTypePolicy.isForcedEnglishKeyboardType(.emailAddress))
        XCTAssertTrue(KeyboardTypePolicy.isForcedEnglishKeyboardType(.numberPad))
        XCTAssertTrue(KeyboardTypePolicy.isForcedEnglishKeyboardType(.decimalPad))
        XCTAssertTrue(KeyboardTypePolicy.isForcedEnglishKeyboardType(.asciiCapableNumberPad))
        XCTAssertTrue(KeyboardTypePolicy.isForcedEnglishKeyboardType(.phonePad))
    }

    func testEnglishLayoutHasChineseSwitchOnBottomRow() {
        let rows = LimeKeyLayout.english.rows
        let bottomCodes = rows.last?.keys.map(\.code) ?? []

        XCTAssertTrue(bottomCodes.contains(LimeKeyCode.switchToIM.rawValue))
        XCTAssertFalse(bottomCodes.contains(LimeKeyCode.emojiPanel.rawValue))
    }

    func testContextualEnterKeyRestoreUsesSameBackgroundPolicyAsInitialRender() throws {
        let source = try String(contentsOf: projectFileURL("LimeKeyboard/KeyboardView.swift"),
                                encoding: .utf8)

        XCTAssertTrue(source.contains("private func restoredKeyBackgroundColor(for keyDef: KeyDef) -> UIColor"))
        XCTAssertTrue(source.contains("btn.backgroundColor = restoredKeyBackgroundColor(for: keyDef)"))
        XCTAssertFalse(source.contains("btn.backgroundColor = isModifier ? modifierKeyColor : normalKeyColor"))
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

    func testIPadEnglishShiftLayoutShowsShiftedKeys() throws {
        let layout = try loadKeyboardLayoutFixture("lime_english_ipad_shift")
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

    func testHSLayoutsUseLowercaseUnshiftedAndUppercaseShiftedLetterCodesAndLabels() throws {
        try assertLetterKeyCodes(in: "lime_hs", shouldBeUppercase: false)
        try assertLetterKeyCodes(in: "lime_hs_ipad", shouldBeUppercase: false)
        try assertLetterKeyCodes(in: "lime_hs_shift", shouldBeUppercase: true)
        try assertLetterKeyCodes(in: "lime_hs_ipad_shift", shouldBeUppercase: true)
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

    func testShiftedSymbolKeysDoNotShowChineseRootSubLabels() throws {
        let layoutIDs = [
            "lime_phonetic_shift",
            "lime_phonetic_ipad_shift",
            "lime_ez_shift",
            "lime_ez_ipad_shift",
            "lime_et_41_shift",
            "lime_et_41_ipad_shift",
            "lime_dayi_sym_shift",
            "lime_dayi_sym_ipad_shift",
        ]

        for layoutID in layoutIDs {
            let layout = try loadKeyboardLayoutFixture(layoutID)
            for key in layout.rows.flatMap(\.keys)
                where isPunctuationOrSymbolCode(key.code) && containsChineseRootSublabel(key.sublabel) {
                XCTFail("\(layoutID): shifted symbol key \(key.label) should not show root sublabel \(key.sublabel)")
            }
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

    // MARK: - Legacy iPhone globe mode (spec: docs/IPHONE_LEGACY_KB.md)

    private func makeKeyboardKey() -> KeyDef {
        KeyDef(code: LimeKeyCode.done.rawValue,
               widthPercent: 14,
               icon: "keyboard.chevron.compact.down",
               isModifier: true,
               longPressCode: LimeKeyCode.keyboardOptionsMenu.rawValue)
    }

    private func makeGlobeKey() -> KeyDef {
        KeyDef(code: LimeKeyCode.globe.rawValue,
               widthPercent: 8,
               icon: "globe",
               isModifier: true,
               longPressCode: LimeKeyCode.keyboardOptionsMenu.rawValue)
    }

    func testStandardModeKeyboardKeyOwnsLimeOptionsMenu() {
        let key = makeKeyboardKey()
        XCTAssertTrue(KeyboardGesturePolicy.shouldUseLimeOptionsMenuGesture(
            keyDef: key, legacyGlobeMode: false))
    }

    func testLegacyModeKeyboardKeyReleasesLimeOptionsMenuToSystemPicker() {
        let key = makeKeyboardKey()
        XCTAssertFalse(KeyboardGesturePolicy.shouldUseLimeOptionsMenuGesture(
            keyDef: key, legacyGlobeMode: true))
    }

    func testStandardModeGlobeKeyNeverGetsLimeOptionsMenu() {
        let key = makeGlobeKey()
        XCTAssertFalse(KeyboardGesturePolicy.shouldUseLimeOptionsMenuGesture(
            keyDef: key, legacyGlobeMode: false))
    }

    func testLegacyModeGlobeKeyStillBypassesLimeOptionsMenu() {
        let key = makeGlobeKey()
        XCTAssertFalse(KeyboardGesturePolicy.shouldUseLimeOptionsMenuGesture(
            keyDef: key, legacyGlobeMode: true))
    }

    func testStandardModeKeyboardKeyDoesNotWireSystemPicker() {
        let key = makeKeyboardKey()
        XCTAssertFalse(KeyboardGesturePolicy.shouldWireSystemPickerOnKeyboardKey(
            keyDef: key, legacyGlobeMode: false, hasInputModeViewController: true))
    }

    func testLegacyModeKeyboardKeyWiresSystemPickerWhenIVCPresent() {
        let key = makeKeyboardKey()
        XCTAssertTrue(KeyboardGesturePolicy.shouldWireSystemPickerOnKeyboardKey(
            keyDef: key, legacyGlobeMode: true, hasInputModeViewController: true))
    }

    func testLegacyModeWithoutIVCDoesNotWireSystemPicker() {
        let key = makeKeyboardKey()
        XCTAssertFalse(KeyboardGesturePolicy.shouldWireSystemPickerOnKeyboardKey(
            keyDef: key, legacyGlobeMode: true, hasInputModeViewController: false))
    }

    func testLegacyModeOnlyAppliesToKeyboardKey_NotShiftOrEnter() {
        let shift = KeyDef(code: LimeKeyCode.shift.rawValue, widthPercent: 14,
                           icon: "shift", isModifier: true)
        let enter = KeyDef(code: LimeKeyCode.enter.rawValue, widthPercent: 14,
                           icon: "return", isModifier: true)
        XCTAssertFalse(KeyboardGesturePolicy.shouldWireSystemPickerOnKeyboardKey(
            keyDef: shift, legacyGlobeMode: true, hasInputModeViewController: true))
        XCTAssertFalse(KeyboardGesturePolicy.shouldWireSystemPickerOnKeyboardKey(
            keyDef: enter, legacyGlobeMode: true, hasInputModeViewController: true))
    }

    func testIconForKeyboardKey_StandardModeReturnsNilSoJSONIconWins() {
        let key = makeKeyboardKey()
        XCTAssertNil(KeyboardGesturePolicy.iconForKeyboardKey(
            keyDef: key, legacyGlobeMode: false))
    }

    func testIconForKeyboardKey_LegacyModeReturnsGlobe() {
        let key = makeKeyboardKey()
        XCTAssertEqual(
            KeyboardGesturePolicy.iconForKeyboardKey(keyDef: key, legacyGlobeMode: true),
            "globe")
    }

    func testIconForKeyboardKey_LegacyModeIgnoresNonKeyboardKey() {
        let shift = KeyDef(code: LimeKeyCode.shift.rawValue, widthPercent: 14,
                           icon: "shift", isModifier: true)
        XCTAssertNil(KeyboardGesturePolicy.iconForKeyboardKey(
            keyDef: shift, legacyGlobeMode: true))
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

    func testSingleShiftTapTogglesBetweenShiftedAndUnshiftedOnly() {
        var state = ShiftTapPolicy.nextState(shifted: false, capsLock: false, doubleTap: false)
        XCTAssertTrue(state.shifted)
        XCTAssertFalse(state.capsLock)

        state = ShiftTapPolicy.nextState(shifted: state.shifted, capsLock: state.capsLock, doubleTap: false)
        XCTAssertFalse(state.shifted)
        XCTAssertFalse(state.capsLock)

        state = ShiftTapPolicy.nextState(shifted: state.shifted, capsLock: state.capsLock, doubleTap: false)
        XCTAssertTrue(state.shifted)
        XCTAssertFalse(state.capsLock)
    }

    func testDoubleShiftTapEntersShiftLockAndSingleTapUnlocks() {
        var state = ShiftTapPolicy.nextState(shifted: false, capsLock: false, doubleTap: true)
        XCTAssertTrue(state.shifted)
        XCTAssertTrue(state.capsLock)

        state = ShiftTapPolicy.nextState(shifted: state.shifted, capsLock: state.capsLock, doubleTap: false)
        XCTAssertFalse(state.shifted)
        XCTAssertFalse(state.capsLock)

        state = ShiftTapPolicy.nextState(shifted: true, capsLock: false, doubleTap: true)
        XCTAssertTrue(state.shifted)
        XCTAssertTrue(state.capsLock)
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

    func testKeyboardControllerAdvertisesInputClickFeedbackToUIKit() throws {
        let source = try String(contentsOf: projectFileURL("LimeKeyboard/KeyboardViewController.swift"),
                                encoding: .utf8)

        XCTAssertTrue(source.contains("final class KeyboardViewController: UIInputViewController, UIInputViewAudioFeedback"))
        XCTAssertTrue(source.contains("var enableInputClicksWhenVisible: Bool { true }"))
    }

    func testKeyboardSoundFeedbackBypassesSystemInputClickToggle() throws {
        let source = try String(contentsOf: projectFileURL("LimeKeyboard/KeyboardView.swift"),
                                encoding: .utf8)

        XCTAssertTrue(source.contains("import AudioToolbox"))
        XCTAssertTrue(source.contains("private func playKeyClickSound()"))
        XCTAssertTrue(source.contains("AudioServicesPlaySystemSound"))
        XCTAssertFalse(source.contains("UIDevice.current.playInputClick()"))
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

    func testDatabaseSetupAppliesResolvedInitialIMLayoutWithoutSavedKeyboardList() throws {
        let sourceURL = projectFileURL("LimeKeyboard/KeyboardViewController.swift")
        let source = try String(contentsOf: sourceURL, encoding: .utf8)

        XCTAssertTrue(source.contains("private func applyResolvedActiveIMLayout()"),
                      "setupDatabase fallback path must apply the resolved first IM layout after async DB setup")
        XCTAssertTrue(source.contains("self.applyResolvedActiveIMLayout()"))
        XCTAssertTrue(source.contains("self.activeIM      = resolvedIM"))
        XCTAssertTrue(source.contains("self.activeIMIndex = resolved.firstIndex"))
    }

    func testCandidateBarChromeUsesSystemAppearanceOnly() {
        XCTAssertTrue(CandidateBarSystemChrome.usesLightForeground(systemUserInterfaceStyle: .dark))
        XCTAssertFalse(CandidateBarSystemChrome.usesLightForeground(systemUserInterfaceStyle: .light))
        XCTAssertFalse(CandidateBarSystemChrome.usesLightForeground(systemUserInterfaceStyle: .unspecified))
    }

    func testExpandedCandidateViewUsesCandidateBarSystemChrome() throws {
        let sourceURL = projectFileURL("LimeKeyboard/KeyboardViewController.swift")
        let source = try String(contentsOf: sourceURL, encoding: .utf8)

        XCTAssertTrue(source.contains("let adaptedCandiText = CandidateBarSystemChrome.labelColor(systemUserInterfaceStyle: systemStyle)"))
        XCTAssertTrue(source.contains("expandedCollapseButton?.tintColor = adaptedCandiText"))
        XCTAssertTrue(source.contains("expandedDismissButton?.tintColor = adaptedCandiText"))
        XCTAssertTrue(source.contains("dismissBtn.tintColor = adaptedCandiText"))
        XCTAssertTrue(source.contains("systemUserInterfaceStyle: candidateBar.systemUserInterfaceStyle"))
        XCTAssertTrue(source.contains("expandedCollapseButton?.tintColor = chromeText"))
        XCTAssertTrue(source.contains("expandedDismissButton?.tintColor = chromeText"))
        XCTAssertTrue(source.contains("expandedComposingLabel?.textColor = adaptedCandiText.withAlphaComponent(LayoutMetrics.ComposingPopup.textAlpha)"))
        XCTAssertTrue(source.contains("btn.setTitleColor("))
        XCTAssertTrue(source.contains(": adaptedCandiText,"))
        XCTAssertFalse(source.contains("adaptedCandiText = pal.candiText"))
    }

    func testEmojiSearchEmptyQueryKeepsDedupeFallbackCandidates() throws {
        let sourceURL = projectFileURL("LimeKeyboard/KeyboardViewController.swift")
        let source = try String(contentsOf: sourceURL, encoding: .utf8)

        XCTAssertTrue(source.contains("showEmojiSearchCandidates(loadEmojiSearchFallbackItems())"))
        XCTAssertTrue(source.contains("self.hasEmptyEmojiSearchText"))
        XCTAssertTrue(source.contains("guard seen.insert(word).inserted else { return nil }"))
        XCTAssertTrue(source.contains("showEmojiSearchCandidates(loadEmojiSearchFallbackItems())"))
    }

    func testSpacePickerIMSwitchExitsEnglishOnlyModeBeforeLoadingChineseLayout() throws {
        let sourceURL = projectFileURL("LimeKeyboard/KeyboardViewController.swift")
        let source = try String(contentsOf: sourceURL, encoding: .utf8)
        let functionRange = try XCTUnwrap(source.range(of: #"private func switchIM\(toIndex i: Int\) \{[\s\S]*?\n    \}"#,
                                                       options: .regularExpression))
        let function = String(source[functionRange])

        let modeRange = try XCTUnwrap(function.range(of: "mEnglishOnly = false"))
        let layoutRange = try XCTUnwrap(function.range(of: "LayoutLoader.load(resolvedLayoutId(for: activeIM))"))

        XCTAssertLessThan(modeRange.lowerBound, layoutRange.lowerBound)
    }

    func testEmojiSearchKeepsTemporaryLanguageModeAndRestoresSourceLayout() throws {
        let sourceURL = projectFileURL("LimeKeyboard/KeyboardViewController.swift")
        let source = try String(contentsOf: sourceURL, encoding: .utf8)

        XCTAssertTrue(source.contains("private var emojiSearchEnglishOnly = false"))
        XCTAssertTrue(source.contains("private var emojiSearchSourceLayout: LimeKeyLayout?"))
        XCTAssertFalse(source.contains("private var emojiSearchLockedKeysHeight"))
        XCTAssertTrue(source.contains("emojiSearchEnglishOnly = emojiPanelSource == .english"))
        XCTAssertTrue(source.contains("setEmojiSearchKeyboard(toEnglish: emojiSearchEnglishOnly)"))
        XCTAssertTrue(source.contains("if let sourceLayout = emojiSearchSourceLayout"))
        XCTAssertTrue(source.contains("currentLayout = sourceLayout"))
        XCTAssertTrue(source.contains("mEnglishOnly = emojiPanelSource == .english"))
        XCTAssertTrue(source.contains("mEnglishOnly = toEnglish"))
        XCTAssertTrue(source.contains("candidateBar.setComposingStripReserved(true)"))
        XCTAssertFalse(source.contains("candidateBar.setComposingStripReserved(!toEnglish)"))
        XCTAssertTrue(source.contains("private var activeCandidateBarHeight: CGFloat"))
        XCTAssertTrue(source.contains("candidateBarHeight"))
        XCTAssertTrue(source.contains("private var emojiSearchHeaderHeight: CGFloat"))
        XCTAssertTrue(source.contains("return EmojiPanelView.searchHeaderHeight"))
        XCTAssertTrue(source.contains("private var emojiSearchHeaderView: UIView?"))
        XCTAssertTrue(source.contains("private var emojiSearchField: UISearchTextField?"))
        XCTAssertTrue(source.contains("field.heightAnchor.constraint(equalToConstant: EmojiPanelView.searchFieldHeight)"))
        XCTAssertTrue(source.contains("header.heightAnchor.constraint(equalToConstant: EmojiPanelView.searchHeaderHeight)"))
        XCTAssertTrue(source.contains("searchField.heightAnchor.constraint(equalToConstant: Self.searchFieldHeight)"))
        XCTAssertTrue(source.contains("private func resetSearchFieldHeight()"))
        XCTAssertTrue(source.contains("searchFieldHeightConstraint?.constant = Self.searchFieldHeight"))
        XCTAssertTrue(source.contains("searchFieldHeightConstraint?.isActive = true"))
        XCTAssertFalse(source.contains("searchFieldSearchBottomConstraint"))
        XCTAssertFalse(source.contains("func setSearchHeaderHeight(_ height: CGFloat)"))
        XCTAssertTrue(source.contains("private func shouldRouteKeyToEmojiSearchField(_ code: Int) -> Bool"))
        XCTAssertTrue(source.contains("if emojiSearchEnglishOnly { return true }"))
        XCTAssertTrue(source.contains("code == LimeKeyCode.delete.rawValue && mComposing.isEmpty"))
        XCTAssertTrue(source.contains("private func appendPickedCandidateToEmojiSearch(_ candidate: Mapping) -> Bool"))
        XCTAssertTrue(source.contains("!candidate.isComposingCodeRecord"))
        XCTAssertTrue(source.contains("appendEmojiSearchText(candidate.word)"))
        XCTAssertTrue(source.contains("private func handleEmojiSearchKey(code: Int) -> Bool"))
        XCTAssertTrue(source.contains("case 1...Int(UInt32.max):"))
        XCTAssertTrue(source.contains("hideEmojiPanel()"))
        XCTAssertTrue(source.contains("if isEmojiSearchMode {"))
        XCTAssertTrue(source.contains("searchEmojiPanel(query: emojiSearchField?.text ?? \"\")"))
        XCTAssertTrue(source.contains("showEmojiSearchCandidates([])"))
    }

    func testEmojiCategoryBarKeepsScrollableContentWidth() throws {
        let sourceURL = projectFileURL("LimeKeyboard/KeyboardViewController.swift")
        let source = try String(contentsOf: sourceURL, encoding: .utf8)

        XCTAssertTrue(source.contains("private let categoryScrollView = CandidateScrollView()"))
        XCTAssertTrue(source.contains("categoryScrollView.alwaysBounceHorizontal = true"))
        XCTAssertTrue(source.contains("categoryScrollView.isScrollEnabled = true"))
        XCTAssertTrue(source.contains("categoryScrollView.delaysContentTouches = false"))
        XCTAssertTrue(source.contains("categoryScrollView.canCancelContentTouches = true"))
        XCTAssertTrue(source.contains("categoryScrollView.backgroundColor = LayoutMetrics.TouchTrap.fill"))
        XCTAssertTrue(source.contains("categoryBar.backgroundColor = LayoutMetrics.TouchTrap.fill"))
        XCTAssertTrue(source.contains("private let categoryModeButton = UIButton(type: .system)"))
        XCTAssertTrue(source.contains("categoryScrollView.leadingAnchor.constraint(equalTo: categoryModeButton.trailingAnchor"))
        XCTAssertFalse(source.contains("categoryBar.addArrangedSubview(abc)"))
        XCTAssertTrue(source.contains("var preferredPanelHeight: CGFloat"))
        XCTAssertTrue(source.contains("isEmojiPanelVisible && !isEmojiSearchMode"))
        XCTAssertTrue(source.contains("max(keyboardHeight, emojiPanelView?.preferredPanelHeight ?? keyboardHeight)"))
        XCTAssertTrue(source.contains("button.backgroundColor = LayoutMetrics.TouchTrap.fill"))
        XCTAssertTrue(source.contains("UIColor.label.withAlphaComponent(0.14) : LayoutMetrics.TouchTrap.fill"))
        XCTAssertTrue(source.contains("private var categoryBarWidthConstraint"))
        XCTAssertTrue(source.contains("categoryBarWidthConstraint = categoryBar.widthAnchor.constraint(equalToConstant: 1)"))
        XCTAssertTrue(source.contains("private func updateCategoryBarContentWidth()"))
        XCTAssertTrue(source.contains("let targetWidth = contentWidth"))
        XCTAssertTrue(source.contains("categoryScrollView.contentSize = CGSize(width: targetWidth"))
        XCTAssertTrue(source.contains("categoryScrollView.contentInset.left = centerInset"))
        XCTAssertTrue(source.contains("let wasAtStart = abs(categoryScrollView.contentOffset.x + categoryScrollView.contentInset.left) < 0.5"))
        XCTAssertTrue(source.contains("categoryScrollView.setContentOffset(CGPoint(x: -centerInset, y: 0), animated: false)"))
        XCTAssertTrue(source.contains("private func resetCategoryScrollPosition()"))
        XCTAssertTrue(source.contains("private func resetEmojiScrollPosition()"))
        XCTAssertTrue(source.contains("private func normalModeHorizontalInset(pageWidth: CGFloat,"))
        XCTAssertFalse(source.contains("categoryIconSpacer"))
        XCTAssertFalse(source.contains("let centeredIconStart = max(0, (categoryScrollView.bounds.width - iconsWidth) / 2)"))
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

    // docs/AUTO_CHINESE_PUNC.md cases c/d/g (T-iOS-1): the candidate-bar dismiss (✕)
    // button hides the auto Chinese-punctuation strip when it is already showing
    // (case d, via cancelComposing() which does NOT re-enter clearSuggestions), and
    // otherwise clears the composition through clearComposing(force: true) so
    // clearSuggestions() can surface the strip (case c = related dismiss, case g =
    // dismiss during active composing — iOS aligned to Android).
    func testCandidateBarDismissSurfacesPunctuationStrip() throws {
        let sourceURL = projectFileURL("LimeKeyboard/KeyboardViewController.swift")
        let source = try String(contentsOf: sourceURL, encoding: .utf8)
        let pattern = #"func candidateBarViewDidRequestDismiss[\s\S]*?\n    \}"#
        let regex = try NSRegularExpression(pattern: pattern)
        let range = NSRange(source.startIndex..<source.endIndex, in: source)
        let match = try XCTUnwrap(regex.firstMatch(in: source, range: range))
        let method = String(source[Range(match.range, in: source)!])

        // case d: strip already showing → hide without rebuilding it
        XCTAssertTrue(method.contains("if hasChineseSymbolCandidatesShown {"))
        XCTAssertTrue(method.contains("cancelComposing()"))
        // cases c + g: otherwise clear composition and let clearSuggestions surface the strip
        XCTAssertTrue(method.contains("clearComposing(force: true)"))
        // must NOT bypass clearSuggestions for the non-strip dismiss (the old case-c/g bug)
        XCTAssertFalse(method.contains("cancelActiveComposingFromCandidateDismiss()"))
    }

    // docs/AUTO_CHINESE_PUNC.md §10.3 case (b) (T-iOS-2): after committing a word
    // with no related phrases, updateRelatedPhrase()'s empty-related branch must
    // restore hasCandidatesShown (which the commit path reset) to true BEFORE it
    // calls clearSuggestions(), so the auto Chinese-punctuation strip can surface.
    func testUpdateRelatedPhraseRestoresHasCandidatesShownForPunctuationStrip() throws {
        let sourceURL = projectFileURL("LimeKeyboard/KeyboardViewController.swift")
        let source = try String(contentsOf: sourceURL, encoding: .utf8)
        let pattern = #"func updateRelatedPhrase[\s\S]*?\n    \}"#
        let regex = try NSRegularExpression(pattern: pattern)
        let range = NSRange(source.startIndex..<source.endIndex, in: source)
        let match = try XCTUnwrap(regex.firstMatch(in: source, range: range))
        let method = String(source[Range(match.range, in: source)!])

        // the empty-related branch exists
        XCTAssertTrue(method.contains("if related.isEmpty {"))
        // and it restores hasCandidatesShown before handing off to clearSuggestions
        let branchRange = try XCTUnwrap(method.range(of: "if related.isEmpty {"))
        let branch = String(method[branchRange.lowerBound...])
        let restoreRange = try XCTUnwrap(branch.range(of: "self.hasCandidatesShown = true"))
        let clearRange = try XCTUnwrap(branch.range(of: "self.clearSuggestions()"))
        XCTAssertLessThan(restoreRange.lowerBound, clearRange.lowerBound,
                          "case (b): hasCandidatesShown must be restored before clearSuggestions()")
    }

    // docs/AUTO_CHINESE_PUNC.md §10.3 (T-iOS-3): clearSuggestions() is the single
    // strip builder. It must carry the full gate
    // (autoChineseSymbol && !mEnglishOnly && hasCandidatesShown && !hasChineseSymbolCandidatesShown)
    // and build the strip via chinesePunctuationMappings() — cases a/b/c/g all route here.
    func testClearSuggestionsHasAutoChinesePunctuationGate() throws {
        let sourceURL = projectFileURL("LimeKeyboard/KeyboardViewController.swift")
        let source = try String(contentsOf: sourceURL, encoding: .utf8)
        let pattern = #"func clearSuggestions[\s\S]*?\n    \}"#
        let regex = try NSRegularExpression(pattern: pattern)
        let range = NSRange(source.startIndex..<source.endIndex, in: source)
        let match = try XCTUnwrap(regex.firstMatch(in: source, range: range))
        let method = String(source[Range(match.range, in: source)!])

        XCTAssertTrue(method.contains("autoChineseSymbol && !mEnglishOnly && hasCandidatesShown && !hasChineseSymbolCandidatesShown"))
        XCTAssertTrue(method.contains("chinesePunctuationMappings()"))
    }

    // docs/AUTO_CHINESE_PUNC.md §10.3 cases (e)/(f) (T-iOS-4): in handleBackspace(),
    // the Case-4 branch (hasChineseSymbolCandidatesShown) hides the punctuation strip
    // by emptying the bar and must NOT delete a character (case e). The browse-only
    // branch instead dismisses the related bar AND deletes one character (case f).
    func testHandleBackspaceHidesPunctuationStripWithoutDeletingAndDismissesBrowseList() throws {
        let sourceURL = projectFileURL("LimeKeyboard/KeyboardViewController.swift")
        let source = try String(contentsOf: sourceURL, encoding: .utf8)
        let pattern = #"func handleBackspace[\s\S]*?\n    \}"#
        let regex = try NSRegularExpression(pattern: pattern)
        let range = NSRange(source.startIndex..<source.endIndex, in: source)
        let match = try XCTUnwrap(regex.firstMatch(in: source, range: range))
        let method = String(source[Range(match.range, in: source)!])

        // Case 4 (case e): strip showing → hide via empty bar. Bound the slice to the
        // NEXT else-if header so it covers only the Case-4 body (the following English
        // and browse branches legitimately call deleteBackward()).
        let case4Range = try XCTUnwrap(
            method.range(of: "else if hasCandidatesShown && hasChineseSymbolCandidatesShown {"))
        let case5Range = try XCTUnwrap(
            method.range(of: "else if mEnglishOnly && !tempEnglishWord.isEmpty {"))
        let browseRange = try XCTUnwrap(
            method.range(of: "else if isBrowseOnlySuggestionList {"))
        let case4Branch = String(method[case4Range.lowerBound..<case5Range.lowerBound])
        XCTAssertTrue(case4Branch.contains("candidateBar.setCandidates([])"))
        // case e: the strip-hide branch must NOT delete a character
        XCTAssertFalse(case4Branch.contains("deleteBackward()"))

        // case f: browse-only (related) branch dismisses the bar AND deletes one char
        let browseBranch = String(method[browseRange.lowerBound...])
        XCTAssertTrue(browseBranch.contains("dismissBrowseOnlySuggestionBar()"))
        XCTAssertTrue(browseBranch.contains("textDocumentProxy.deleteBackward()"))
    }

    // docs/AUTO_CHINESE_PUNC.md §2 / §4.4 / §10.4 (T-MODE): the strip is Chinese-mode
    // only. clearSuggestions()'s build gate must include !mEnglishOnly so the strip
    // never appears in English-prediction mode even when the pref is ON.
    func testPunctuationStripGatedToChineseMode() throws {
        let sourceURL = projectFileURL("LimeKeyboard/KeyboardViewController.swift")
        let source = try String(contentsOf: sourceURL, encoding: .utf8)
        let pattern = #"func clearSuggestions[\s\S]*?\n    \}"#
        let regex = try NSRegularExpression(pattern: pattern)
        let range = NSRange(source.startIndex..<source.endIndex, in: source)
        let match = try XCTUnwrap(regex.firstMatch(in: source, range: range))
        let method = String(source[Range(match.range, in: source)!])

        XCTAssertTrue(method.contains("!mEnglishOnly"))
        XCTAssertTrue(method.contains("autoChineseSymbol && !mEnglishOnly"))
    }

    // docs/AUTO_CHINESE_PUNC.md §4.4 / §10.4 (T-BROWSE): the punctuation strip (and
    // related/English lists) are browse-only — Space/Enter must insert a literal
    // space/newline rather than commit the first entry. handleEnterOrSpace consults
    // isBrowseOnlySuggestionList to suppress the pick.
    func testBrowseOnlyListsDoNotCommitOnSpaceOrEnter() throws {
        let sourceURL = projectFileURL("LimeKeyboard/KeyboardViewController.swift")
        let source = try String(contentsOf: sourceURL, encoding: .utf8)
        let pattern = #"func handleEnterOrSpace[\s\S]*?\n    \}"#
        let regex = try NSRegularExpression(pattern: pattern)
        let range = NSRange(source.startIndex..<source.endIndex, in: source)
        let match = try XCTUnwrap(regex.firstMatch(in: source, range: range))
        let method = String(source[Range(match.range, in: source)!])

        // it consults the browse-only gate and suppresses the pick for those lists
        XCTAssertTrue(method.contains("let isAssociatedList = isBrowseOnlySuggestionList"))
        XCTAssertTrue(method.contains("if isAssociatedList {\n            shouldPick = false"))
        // browse-only Space/Enter inserts a literal space/newline rather than committing
        XCTAssertTrue(method.contains("textDocumentProxy.insertText(isEnter ? \"\\n\" : \" \")"))
    }

    // docs/AUTO_CHINESE_PUNC.md §3 / T-SET: the iOS strip must emit the canonical
    // punctuation set — same symbols, same order as Android ChineseSymbol.chineseSymbols
    // — so both platforms show an identical strip. (Android side: the matching
    // LIMEServiceTest T-SET asserts getChineseSymoblList() returns this exact list.)
    func testChinesePunctuationStripMatchesCanonicalAndroidSet() throws {
        let sourceURL = projectFileURL("LimeKeyboard/KeyboardViewController.swift")
        let source = try String(contentsOf: sourceURL, encoding: .utf8)
        let pattern = #"static func chinesePunctuationMappings[\s\S]*?\n    \}"#
        let regex = try NSRegularExpression(pattern: pattern)
        let range = NSRange(source.startIndex..<source.endIndex, in: source)
        let match = try XCTUnwrap(regex.firstMatch(in: source, range: range))
        let method = String(source[Range(match.range, in: source)!])

        // Canonical ordered set (mirrors Android ChineseSymbol.chineseSymbols).
        let canonical = ["，", "。", "、", "？", "！", "：", "；",
                         "（", "）", "「", "」", "『", "』", "【", "】",
                         "／", "＼", "－", "＿", "＊", "＆", "︿",
                         "％", "＄", "＃", "＠", "～",
                         "｛", "｝", "［", "］", "＜", "＞", "＋", "｜", "‵", "＂"]
        // each canonical symbol present, and in this exact order
        var cursor = method.startIndex
        for s in canonical {
            guard let r = method.range(of: "\"\(s)\"", range: cursor..<method.endIndex) else {
                XCTFail("canonical symbol \(s) missing or out of order in the iOS strip")
                return
            }
            cursor = r.upperBound
        }
        // the old iOS-only symbols must be gone (strip unified to the Android set)
        for s in ["〔", "〕", "《", "》", "〈", "〉", "…", "·", "※"] {
            XCTAssertFalse(method.contains("\"\(s)\""), "removed iOS-only symbol \(s) still present")
        }
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

    func testLegacyCandidateBarOptionsHideWhenCandidatesShowExpandChevron() throws {
        let sourceURL = projectFileURL("LimeKeyboard/CandidateBarView.swift")
        let source = try String(contentsOf: sourceURL, encoding: .utf8)

        XCTAssertTrue(source.contains("shouldShowOptionsButton("))
        XCTAssertTrue(source.contains("optionsButton.isHidden = !CandidateBarView.shouldShowOptionsButton("))
        XCTAssertTrue(source.contains("if legacyGlobeMode {\n            return !hasCandidates\n        }"))
        XCTAssertTrue(source.contains("optionsButton.isHidden = !candidates.isEmpty"))
        XCTAssertFalse(source.contains("if legacyGlobeMode {\n            optionsButton.isHidden = false\n        }"))
    }

    func testIMDetailShareButtonUsesConstrainedLayoutTrailingSlot() throws {
        let sourceURL = projectFileURL("LimeSettings/Views/IMDetailView.swift")
        let source = try String(contentsOf: sourceURL, encoding: .utf8)
        let layoutSource = try String(
            contentsOf: projectFileURL("LimeSettings/LimeSettingsView.swift"),
            encoding: .utf8
        )

        XCTAssertTrue(source.contains(".constrainedDetailLayout(displayName) {"))
        XCTAssertFalse(source.contains("ToolbarItem(placement: .navigationBarTrailing)"))
        XCTAssertTrue(source.contains("showSharePicker = true"))
        XCTAssertTrue(source.contains("square.and.arrow.up"))
        XCTAssertTrue(source.contains(".font(.title2.weight(.semibold))"))
        XCTAssertTrue(source.contains(".frame(width: SettingsMetrics.detailToolbarButtonSize,"))
        XCTAssertTrue(source.contains("height: SettingsMetrics.detailToolbarButtonSize)"))
        XCTAssertTrue(layoutSource.contains("private let titleSectionHeight: CGFloat = SettingsMetrics.titleSectionHeight"))
        XCTAssertTrue(layoutSource.contains("HStack(alignment: .center, spacing: 12)"))
        XCTAssertTrue(layoutSource.contains(".frame(height: titleSectionHeight)"))
    }

    func testIMDetailViewShowsEditableLimeEndkeyField() throws {
        let sourceURL = projectFileURL("LimeSettings/Views/IMDetailView.swift")
        let source = try String(contentsOf: sourceURL, encoding: .utf8)

        XCTAssertTrue(source.contains("case endkey"))
        XCTAssertTrue(source.contains("case .endkey: return \"結束鍵\""))
        XCTAssertTrue(source.contains("case .endkey: return \"編輯結束鍵\""))
        XCTAssertTrue(source.contains("case .endkey: return \"limeendkey\""))
        XCTAssertTrue(source.contains("DBServer.shared.getImConfig(im.tableNick, \"limeendkey\")"))
        XCTAssertTrue(source.contains("beginMetadataEdit(.endkey)"))
        XCTAssertTrue(source.contains("editableMetadataRow(label: \"結束鍵\", value: displayEndkey)"))
    }

    func testLimeEndkeyPolicyMatchesAndroidTriggerRules() {
        XCTAssertTrue(LimeEndkeyPolicy.isCommitKey(
            primaryCode: Int(UnicodeScalar(",").value),
            endkey: ".,",
            englishOnly: false
        ))
        XCTAssertFalse(LimeEndkeyPolicy.isCommitKey(
            primaryCode: Int(UnicodeScalar(",").value),
            endkey: ".,",
            englishOnly: true
        ))
        XCTAssertFalse(LimeEndkeyPolicy.isCommitKey(
            primaryCode: Int(UnicodeScalar(",").value),
            endkey: "",
            englishOnly: false
        ))

        XCTAssertTrue(LimeEndkeyPolicy.isKeyInImkeys(
            primaryCode: Int(UnicodeScalar("A").value),
            imkeys: "abc"
        ))
        XCTAssertFalse(LimeEndkeyPolicy.isKeyInImkeys(
            primaryCode: Int(UnicodeScalar(",").value),
            imkeys: "abc"
        ))
    }

    // #96: general exact-match highlight rule. When the candidate after the composing echo
    // has the same code as the echo (the typed code), it is an exact match and must be
    // highlighted -- including an auto-inserted full-width punctuation candidate whose code
    // equals the typed ',' / '.'. This is NOT a punctuation-specific rule; it is driven only
    // by code equality. Locks the fix so future refactors do not re-introduce the drift.
    func testDefaultHighlightedCandidateHighlightsExactMatchAfterComposingEcho() {
        let composing = Mapping(id: 0, code: ".", word: ".", score: 0, baseScore: 0,
                                recordType: Mapping.RecordType.composingCode)
        let punctuation = Mapping(id: 1, code: ".", word: "。", score: 0, baseScore: 0,
                                  recordType: Mapping.RecordType.chinesePunctuation)

        XCTAssertEqual(CandidateSelectionPolicy.defaultHighlightedCandidateIndex([composing, punctuation]), 1)
    }

    // The exact-match rule is code-equality driven, not record-type driven: a candidate
    // whose code differs from the composing echo's code is NOT an exact match and the
    // composing echo stays highlighted.
    func testDefaultHighlightedCandidateKeepsComposingEchoWhenCodeDiffers() {
        let composing = Mapping(id: 0, code: "ab", word: "ab", score: 0, baseScore: 0,
                                recordType: Mapping.RecordType.composingCode)
        let nonExact = Mapping(id: 1, code: "abc", word: "字", score: 0, baseScore: 0,
                               recordType: Mapping.RecordType.chinesePunctuation)

        XCTAssertEqual(CandidateSelectionPolicy.defaultHighlightedCandidateIndex([composing, nonExact]), 0)
    }

    // A second candidate whose code is NOT an exact match to the typed code (and is not an
    // exact/partial code record) must not be promoted; the composing echo stays highlighted.
    func testDefaultHighlightedCandidateDoesNotPromoteArbitrarySecondCandidate() {
        let composing = Mapping(id: 0, code: ".", word: ".", score: 0, baseScore: 0,
                                recordType: Mapping.RecordType.composingCode)
        let arbitrary = Mapping(id: 1, code: "..extra", word: "not-default", score: 0, baseScore: 0,
                                recordType: Mapping.RecordType.exactMatchToWord)

        XCTAssertEqual(CandidateSelectionPolicy.defaultHighlightedCandidateIndex([composing, arbitrary]), 0)
    }

    func testDefaultHighlightedCandidateDoesNotSelectRelatedOrEnglishLists() {
        let related = Mapping(id: 0, code: "", word: "明天", score: 0, baseScore: 0,
                              recordType: Mapping.RecordType.relatedPhrase)
        let english = Mapping(id: 1, code: "", word: "tomorrow", score: 0, baseScore: 0,
                              recordType: Mapping.RecordType.englishSuggestion)

        XCTAssertEqual(CandidateSelectionPolicy.defaultHighlightedCandidateIndex([related]), -1)
        XCTAssertEqual(CandidateSelectionPolicy.defaultHighlightedCandidateIndex([english]), -1)
    }

    func testEndkeyCommitCandidateResolutionIsSeparateFromDefaultHighlighting() {
        let composing = Mapping(id: 0, code: ".", word: ".", score: 0, baseScore: 0,
                                recordType: Mapping.RecordType.composingCode)
        let punctuation = Mapping(id: 1, code: ".", word: "。", score: 0, baseScore: 0,
                                  recordType: Mapping.RecordType.chinesePunctuation)

        // For an exact-match candidate after the composing echo, the visible highlight and
        // the endkey commit target now agree on index 1. The two policies stay distinct
        // (e.g. they diverge for related/English browse-only lists), but they coincide here.
        XCTAssertEqual(CandidateSelectionPolicy.defaultHighlightedCandidateIndex([composing, punctuation]), 1)
        XCTAssertEqual(LimeEndkeyPolicy.commitCandidateIndex([composing, punctuation]), 1)
    }

    func testKeyboardControllerRoutesLimeEndkeyBeforeNormalCharacterHandling() throws {
        let source = try String(
            contentsOf: projectFileURL("LimeKeyboard/KeyboardViewController.swift"),
            encoding: .utf8
        )

        XCTAssertTrue(source.contains("if handleLimeEndkeyCommit(code)"))
        XCTAssertTrue(source.contains("searchServer?.getImConfig(activeIM, \"limeendkey\")"))
        XCTAssertTrue(source.contains("commitComposingWithAppendedEndkey(primaryCode)"))
        XCTAssertTrue(source.contains("commitFreshEndkeyOrRaw(primaryCode)"))
        XCTAssertTrue(source.contains("LimeEndkeyPolicy.commitCandidateIndex(candidates)"))
        XCTAssertTrue(source.contains("currentSearchID &+= 1"))
    }

    func testNormalCandidateSelectionDoesNotUseLimeEndkeyPolicy() throws {
        let source = try String(
            contentsOf: projectFileURL("LimeKeyboard/KeyboardViewController.swift"),
            encoding: .utf8
        )

        XCTAssertTrue(source.contains("let idx = CandidateSelectionPolicy.defaultHighlightedCandidateIndex(full)"))
        XCTAssertTrue(source.contains("let selectedIdx = CandidateSelectionPolicy.defaultHighlightedCandidateIndex(list)"))
        XCTAssertFalse(source.contains("let idx = LimeEndkeyPolicy.defaultCommitCandidateIndex(full)"))
        XCTAssertFalse(source.contains("let selectedIdx = LimeEndkeyPolicy.defaultCommitCandidateIndex(list)"))
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

    func testSettingsAndKeyboardThemeLiteralsUseCentralRoles() throws {
        let settingsTheme = try String(
            contentsOf: projectFileURL("LimeSettings/SettingsTheme.swift"),
            encoding: .utf8
        )
        let settingsMetrics = try String(
            contentsOf: projectFileURL("LimeSettings/SettingsMetrics.swift"),
            encoding: .utf8
        )
        XCTAssertTrue(settingsTheme.contains("enum SettingsTheme"))
        XCTAssertTrue(settingsTheme.contains("static let destructive"))
        XCTAssertTrue(settingsTheme.contains("static let overlayScrim"))
        XCTAssertTrue(settingsMetrics.contains("enum SettingsMetrics"))
        XCTAssertTrue(settingsMetrics.contains("static let contentMaxWidth"))
        XCTAssertTrue(settingsMetrics.contains("static let modalPadding"))

        let settingsFiles = [
            "LimeSettings/LimeSettingsView.swift",
            "LimeSettings/Views/DBManagerView.swift",
            "LimeSettings/Views/IMDetailView.swift",
            "LimeSettings/Views/IMInstallView.swift",
            "LimeSettings/Views/IMListView.swift",
            "LimeSettings/Controllers/IMStoreView.swift",
            "LimeSettings/Views/SetupTabView.swift"
        ]
        for relativePath in settingsFiles {
            let source = try String(contentsOf: projectFileURL(relativePath), encoding: .utf8)
            XCTAssertFalse(source.contains("Color.black.opacity("), relativePath)
            XCTAssertFalse(source.contains(".foregroundColor(.red)"), relativePath)
            XCTAssertFalse(source.contains(".foregroundColor(.green)"), relativePath)
            XCTAssertFalse(source.contains(".foregroundColor(.orange)"), relativePath)
            XCTAssertFalse(source.contains(".foregroundStyle(.white)"), relativePath)
            XCTAssertFalse(source.contains(".background(Color.blue"), relativePath)
        }

        for relativePath in [
            "LimeKeyboard/KeyboardViewController.swift",
            "LimeKeyboard/KeyboardView.swift",
            "LimeKeyboard/PopupKeyboardView.swift"
        ] {
            let source = try String(contentsOf: projectFileURL(relativePath), encoding: .utf8)
            XCTAssertFalse(source.contains("UIColor.black.cgColor"), relativePath)
            XCTAssertTrue(source.contains("LayoutMetrics.Shadow.color"), relativePath)
        }
    }

    private func emojiMapping(_ word: String) -> Mapping {
        Mapping(id: 0, code: "", word: word,
                score: 0, baseScore: 0,
                recordType: Mapping.RecordType.emoji)
    }

    func testLayoutResolverKeepsEnglishRuntimeOnEnglishLayoutAfterIMRefresh() {
        let layout = KeyboardViewController.layoutIdForCurrentInputField(
            keyboardType: .default,
            isEnglishOnly: true,
            hasActivatedIMs: true,
            englishLayout: "lime_english_number",
            resolvedActiveLayoutId: "lime_cj")

        XCTAssertEqual(layout, "lime_english_number")
    }

    func testLayoutResolverUsesActiveIMOnlyForChineseRuntimeWithActivatedIMs() {
        let layout = KeyboardViewController.layoutIdForCurrentInputField(
            keyboardType: .default,
            isEnglishOnly: false,
            hasActivatedIMs: true,
            englishLayout: "lime_english",
            resolvedActiveLayoutId: "lime_cj")

        XCTAssertEqual(layout, "lime_cj")
    }

    func testLayoutResolverUsesEnglishLayoutWhenActivatedIMsAreEmpty() {
        let layout = KeyboardViewController.layoutIdForCurrentInputField(
            keyboardType: .default,
            isEnglishOnly: false,
            hasActivatedIMs: false,
            englishLayout: "lime_english",
            resolvedActiveLayoutId: "lime_cj")

        XCTAssertEqual(layout, "lime_english")
    }

    func testLayoutResolverPreservesNumericAndPhoneFieldOverrides() {
        XCTAssertEqual(KeyboardViewController.layoutIdForCurrentInputField(
            keyboardType: .phonePad,
            isEnglishOnly: false,
            hasActivatedIMs: true,
            englishLayout: "lime_english",
            resolvedActiveLayoutId: "lime_cj"), "phone_number")
        XCTAssertEqual(KeyboardViewController.layoutIdForCurrentInputField(
            keyboardType: .numberPad,
            isEnglishOnly: false,
            hasActivatedIMs: true,
            englishLayout: "lime_english",
            resolvedActiveLayoutId: "lime_cj"), "symbols1")
    }

    func testDatabaseSetupReconcilesInputModeAndLayoutAfterAsyncIMRefresh() throws {
        let source = try String(contentsOf: projectFileURL("LimeKeyboard/KeyboardViewController.swift"),
                                encoding: .utf8)

        XCTAssertTrue(source.contains("private func updateInputModeForCurrentField()"))
        XCTAssertTrue(source.contains("private func applyLayoutForCurrentInputField()"))
        XCTAssertTrue(source.contains("self.updateInputModeForCurrentField()\n            self.applyLayoutForCurrentInputField()"),
                      "setupDatabase should re-apply current input mode/layout after activatedIMs refresh")
        XCTAssertTrue(source.contains("first activation after Settings/cloud install"))
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

    private func assertLetterKeyCodes(in layoutID: String, shouldBeUppercase: Bool) throws {
        let layout = try loadKeyboardLayoutFixture(layoutID)
        let letterKeys = layout.rows.flatMap(\.keys).filter {
            (65...90).contains($0.code) || (97...122).contains($0.code)
        }

        XCTAssertFalse(letterKeys.isEmpty, "\(layoutID): should contain Latin letter keys")
        for key in letterKeys {
            if shouldBeUppercase {
                XCTAssertTrue((65...90).contains(key.code),
                              "\(layoutID): \(key.label) should emit uppercase code")
                XCTAssertEqual(key.label.uppercased(), key.label,
                               "\(layoutID): \(key.label) should show uppercase label")
            } else {
                XCTAssertTrue((97...122).contains(key.code),
                              "\(layoutID): \(key.label) should emit lowercase code")
                XCTAssertEqual(key.label.lowercased(), key.label,
                               "\(layoutID): \(key.label) should show lowercase label")
            }
        }
    }

    private func isPunctuationOrSymbolCode(_ code: Int) -> Bool {
        (33...47).contains(code)
            || (58...64).contains(code)
            || (91...96).contains(code)
            || (123...126).contains(code)
    }

    private func containsChineseRootSublabel(_ sublabel: String) -> Bool {
        sublabel.unicodeScalars.contains { scalar in
            (0x3100...0x312F).contains(Int(scalar.value))
                || (0x31A0...0x31BF).contains(Int(scalar.value))
                || (0x4E00...0x9FFF).contains(Int(scalar.value))
        }
    }

    private func projectFileURL(_ relativePath: String) -> URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
    }

}
