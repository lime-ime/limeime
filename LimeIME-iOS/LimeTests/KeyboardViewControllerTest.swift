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

import XCTest
import AVFoundation
import UIKit
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
        let popupKeyboard: String
        let popupCharacters: String?   // absent in simple layouts (computer_simple/phone_simple); app defaults to "" too
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

    func testSyncTriggerWiringIsOffMainThreadAndNotFAGated() throws {
        let source = try String(contentsOf: projectFileURL("LimeKeyboard/KeyboardViewController.swift"),
                                encoding: .utf8)

        XCTAssertTrue(source.contains("SyncSignalObserver(signal: .tablesUpdated"))
        XCTAssertTrue(source.contains("syncScanInProgress"))
        // App Group access does NOT require Full Access, so the cold→hot sync must run
        // regardless of FA — else FA-off installs never reach the keyboard's hot DB and
        // there is no active IM. `hasFullAccess` is passed only to gate the App Group
        // WRITERS inside scanAndApply (backup / editor receipt, §1.5 Fix 3); the sync itself
        // is never FA-gated. See IOS_DB_COLD_HOT.md §1.0.2 / §1.5.
        XCTAssertTrue(source.contains("private func triggerSyncScan()"))
        XCTAssertFalse(source.contains("guard hasFullAccess else { return }"))
        XCTAssertTrue(source.contains("syncQueue.async"))
        XCTAssertTrue(source.contains(".scanAndApply(hasFullAccess: fa)"))
        XCTAssertFalse(source.contains("UITextInputMode.activeInputModes"))
        XCTAssertFalse(source.contains("value(forKey: \"activeInputMode\")"))
    }

    // §1.7 Rule 2: after a cold→hot sync, keep the current active IM when it survives in
    // the freshly-activated list, else the first available; empty request → first available.
    func testReconciledActiveIMKeepsSurvivorElseFallsToFirstAvailable() {
        XCTAssertEqual(
            KeyboardViewController.reconciledActiveIM(
                requested: "dayi", activated: ["cj4", "dayi", "phonetic"], firstAvailable: "cj4"),
            "dayi", "current IM still activated → keep it, query table follows the active keyboard")
        XCTAssertEqual(
            KeyboardViewController.reconciledActiveIM(
                requested: "dayi", activated: ["cj4", "phonetic"], firstAvailable: "cj4"),
            "cj4", "current IM removed by the sync → first available (original active keyboard gone)")
        XCTAssertEqual(
            KeyboardViewController.reconciledActiveIM(
                requested: "", activated: ["cj4", "phonetic"], firstAvailable: "cj4"),
            "cj4", "cold start with no saved IM → first available")
    }

    // §1.7 Rule 1: a no-op keyboard appearance must NOT rebuild the SearchServer. The scan
    // only ensures the hot DB file exists; prepareKeyboardRuntimeDatabase (which reseeds the
    // shared LimeDB.currentTableName to firstNick) must run only when a sync applies, via
    // setupDatabase() — never on every triggerSyncScan, or Dayi re-opens query cj4.
    func testSyncScanNoOpAppearanceDoesNotRebuildSearchServer() throws {
        let source = try String(contentsOf: projectFileURL("LimeKeyboard/KeyboardViewController.swift"),
                                encoding: .utf8)
        guard let scanRange = source.range(of: "private func triggerSyncScan()"),
              let scanEnd = source.range(of: "private func reportFullAccessStatus()", range: scanRange.upperBound..<source.endIndex) else {
            return XCTFail("could not isolate triggerSyncScan body")
        }
        let body = String(source[scanRange.upperBound..<scanEnd.lowerBound])
        XCTAssertTrue(body.contains("DBServer.shared.ensureDatabaseFileReady()"),
                      "no-op appearance should only ensure the hot DB file, not rebuild the runtime")
        XCTAssertFalse(body.contains("DBServer.shared.prepareKeyboardRuntimeDatabase("),
                       "triggerSyncScan must not call prepareKeyboardRuntimeDatabase — it reseeds the shared query table")
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

    // feat#N02: computer_simple is phone_simple with the digit grid in computer-numpad
    // order — 7 8 9 on top, 4 5 6, 1 2 3, then 0 on the bottom row. The framing modifier
    // keys (123, ABC, ⌫, +-*/=, space, ↵) stay put; only the digit keys move.
    func testComputerSimpleLayoutUsesComputerNumpadDigitOrder() throws {
        let layout = try loadKeyboardLayoutFixture("computer_simple")

        func digitLabels(_ rowIndex: Int) -> [String] {
            layout.rows[rowIndex].keys.filter { (48...57).contains($0.code) }.map(\.label)
        }

        XCTAssertEqual(digitLabels(0), ["7", "8", "9"], "top row should read 7 8 9")
        XCTAssertEqual(digitLabels(1), ["4", "5", "6"], "middle row unchanged: 4 5 6")
        XCTAssertEqual(digitLabels(2), ["1", "2", "3"], "third row should read 1 2 3")
        let bottom = try XCTUnwrap(layout.rows.first(where: { $0.isBottomRow }))
        XCTAssertEqual(bottom.keys.filter { (48...57).contains($0.code) }.map(\.label), ["0"],
                       "0 stays on the bottom row")

        // Every digit key must emit the ASCII code matching its label (label "7" → code 55),
        // otherwise the key would type the wrong number.
        for key in layout.rows.flatMap(\.keys) where (48...57).contains(key.code) {
            XCTAssertEqual(key.code, (Int(key.label) ?? -1) + 48,
                           "digit key labelled \(key.label) must emit its matching ASCII code")
        }
    }

    func testET41PopupDigitsShowOnPhoneLongPressKeyLabels() throws {
        let layout = try loadKeyboardLayoutFixture("lime_et_41")
        let keys = layout.rows.flatMap(\.keys)
        let minus = try XCTUnwrap(keys.first { $0.code == 45 })
        let equals = try XCTUnwrap(keys.first { $0.code == 61 })

        XCTAssertEqual(minus.label, "- 5")
        XCTAssertEqual(minus.sublabel, "ㄥ")
        XCTAssertEqual(minus.popupCharacters, "5")
        XCTAssertEqual(minus.popupKeyboard, "@xml/popup_template")

        XCTAssertEqual(equals.label, "= 6")
        XCTAssertEqual(equals.sublabel, "ㄦ")
        XCTAssertEqual(equals.popupCharacters, "6")
        XCTAssertEqual(equals.popupKeyboard, "@xml/popup_template")
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
            "lime_phonetic_ipad_narrow_shift",
            "lime_ez_shift",
            "lime_ez_ipad_shift",
            "lime_ez_ipad_narrow_shift",
            "lime_et_41_shift",
            "lime_et_41_ipad_shift",
            "lime_et_41_ipad_narrow_shift",
            "lime_dayi_sym_shift",
            "lime_dayi_sym_ipad_shift",
            "lime_dayi_sym_ipad_narrow_shift",
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

    func testShiftDoubleTapWindowNonShiftCancelsPendingShiftTap() {
        let firstShiftTime: TimeInterval = 1.0
        let afterLetter = ShiftDoubleTapPolicy.lastTapTimeAfterKey(primaryCode: 97,
                                                                   lastShiftTapTime: firstShiftTime)

        XCTAssertFalse(ShiftDoubleTapPolicy.isDoubleTap(lastShiftTapTime: afterLetter,
                                                        now: firstShiftTime + 0.1,
                                                        timeout: 0.3))
    }

    func testShiftDoubleTapWindowShiftKeepsPendingShiftTap() {
        let firstShiftTime: TimeInterval = 1.0
        let afterShift = ShiftDoubleTapPolicy.lastTapTimeAfterKey(primaryCode: LimeKeyCode.shift.rawValue,
                                                                  lastShiftTapTime: firstShiftTime)

        XCTAssertTrue(ShiftDoubleTapPolicy.isDoubleTap(lastShiftTapTime: afterShift,
                                                       now: firstShiftTime + 0.1,
                                                       timeout: 0.3))
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

    func testKeyboardWindowTouchDelayIsDisabled() throws {
        let source = try String(contentsOf: projectFileURL("LimeKeyboard/KeyboardViewController.swift"),
                                encoding: .utf8)

        XCTAssertTrue(source.contains("private func disableKeyboardWindowTouchDelay()"))
        XCTAssertTrue(source.contains("$0.delaysTouchesBegan = false"))
        XCTAssertTrue(source.contains("disableKeyboardWindowTouchDelay()"))
    }

    func testKeyPreviewIsPersistentAndHiddenInsteadOfRebuiltPerKeypress() throws {
        let source = try String(contentsOf: projectFileURL("LimeKeyboard/KeyboardViewController.swift"),
                                encoding: .utf8)

        XCTAssertTrue(source.contains("private var keyPreviewView: UIView?"))
        XCTAssertFalse(source.contains("private weak var keyPreviewView: UIView?"))
        XCTAssertTrue(source.contains("private var keyPreviewShapeLayer: CAShapeLayer?"))
        XCTAssertTrue(source.contains("private var keyPreviewSingleLabel: UILabel?"))
        XCTAssertTrue(source.contains("private var keyPreviewDualStack: UIStackView?"))
        XCTAssertTrue(source.contains("private func ensureKeyPreviewView() -> UIView"))
        XCTAssertTrue(source.contains("private func hideKeyPreview(animated: Bool)"))
        XCTAssertTrue(source.contains("private func teardownKeyPreview()"))

        let showRange = try XCTUnwrap(source.range(
            of: #"func keyboardView\(_ view: KeyboardView, showPreviewFor keyDef: KeyDef, keyRect: CGRect\) \{[\s\S]*?\n    \}\n\n    func keyboardView\(_ view: KeyboardView, didMoveCaretBy"#,
            options: .regularExpression
        ))
        let showSource = String(source[showRange])
        XCTAssertFalse(showSource.contains("removeFromSuperview()"))
        XCTAssertTrue(showSource.contains("ensureKeyPreviewView()"))

        let dismissRange = try XCTUnwrap(source.range(
            of: #"func keyboardViewDismissPreview\(_ view: KeyboardView\) \{[\s\S]*?\n    \}"#,
            options: .regularExpression
        ))
        let dismissSource = String(source[dismissRange])
        XCTAssertFalse(dismissSource.contains("removeFromSuperview()"))
        XCTAssertTrue(dismissSource.contains("hideKeyPreview(animated: true)"))
    }

    func testKeyboardSoundFeedbackBypassesSystemInputClickToggle() throws {
        let source = try String(contentsOf: projectFileURL("LimeKeyboard/KeyboardView.swift"),
                                encoding: .utf8)

        XCTAssertTrue(source.contains("import AudioToolbox"))
        XCTAssertTrue(source.contains("private func playKeyClickSound()"))
        XCTAssertTrue(source.contains("AudioServicesPlaySystemSound"))
        XCTAssertFalse(source.contains("UIDevice.current.playInputClick()"))
    }

    func testKeypressSoundVolumePreferenceMirrorsAndroidOptionsOnIOS() throws {
        let settings = try String(contentsOf: projectFileURL("LimeSettings/Views/PreferencesTabView.swift"),
                                  encoding: .utf8)
        let controller = try String(contentsOf: projectFileURL("LimeKeyboard/KeyboardViewController.swift"),
                                    encoding: .utf8)
        let keyboard = try String(contentsOf: projectFileURL("LimeKeyboard/KeyboardView.swift"),
                                  encoding: .utf8)

        XCTAssertTrue(settings.contains("@AppStorage(\"keypress_sound_volume\""))
        XCTAssertTrue(settings.contains("Picker(\"打字音量\", selection: $keypressSoundVolume)"))
        XCTAssertTrue(settings.contains(".disabled(!soundOnKeypress)"))
        XCTAssertTrue(controller.contains("keypressSoundVolume"))
        XCTAssertTrue(controller.contains("d?.string(forKey: \"keypress_sound_volume\") ?? \"-1\""))
        XCTAssertTrue(keyboard.contains("var keypressSoundVolume: String = \"-1\""))
        XCTAssertTrue(keyboard.contains("AVAudioPlayer"))
    }

    func testCustomKeyClickVolumeParsingAndGeneratedSoundData() throws {
        XCTAssertNil(KeyboardView.customKeyClickVolume(from: "-1"))
        XCTAssertNil(KeyboardView.customKeyClickVolume(from: "bad"))
        XCTAssertEqual(try XCTUnwrap(KeyboardView.customKeyClickVolume(from: "0.10")),
                       Float(0.10),
                       accuracy: Float(0.001))
        XCTAssertEqual(try XCTUnwrap(KeyboardView.customKeyClickVolume(from: "1.00")),
                       Float(1.00),
                       accuracy: Float(0.001))

        let data = KeyboardView.makeKeyClickWavData()
        XCTAssertEqual(String(data: data.prefix(4), encoding: .ascii), "RIFF")
        XCTAssertGreaterThan(data.count, 44)
        XCTAssertNotNil(try AVAudioPlayer(data: data))
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
        // §1.7 Rule 2: setupDatabase reconciles via reconciledActiveIM — a missing/absent
        // active IM falls back to the first available (resolvedIM).
        XCTAssertTrue(source.contains("Self.reconciledActiveIM("),
                      "setupDatabase reconciles the active IM (keep survivor, else first available)")
        XCTAssertTrue(source.contains("firstAvailable: resolvedIM"),
                      "the fallback (removed/absent active IM) resolves to the first available IM")
        XCTAssertTrue(source.contains("let requestedIM = self.didCompleteInitialSetup\n                ? self.activeIM\n                : hotActiveIM()"),
                      "cold-start setup must restore keyboard-owned active_im, even when opening in English mode")
        XCTAssertTrue(source.contains("self.activeIM      = survivingIM"))
        XCTAssertTrue(source.contains("self.activeIMIndex = resolved.firstIndex"))
    }

    func testCandidateBarChromeUsesSystemAppearanceOnly() {
        XCTAssertTrue(CandidateBarSystemChrome.usesLightForeground(systemUserInterfaceStyle: .dark))
        XCTAssertFalse(CandidateBarSystemChrome.usesLightForeground(systemUserInterfaceStyle: .light))
        XCTAssertFalse(CandidateBarSystemChrome.usesLightForeground(systemUserInterfaceStyle: .unspecified))
    }

    // feat#N01: the candidate-bar dismiss (✕) button is 1.5× the former half-chevron tap
    // target — wider hit area only; height/color/corner style unchanged.
    func testDismissButtonWidthIs1_5xHalfChevron() {
        XCTAssertEqual(LayoutMetrics.CandidateBar.Chevron.dismissButtonWidth(isPad: false), 30, accuracy: 0.001)
        XCTAssertEqual(LayoutMetrics.CandidateBar.Chevron.dismissButtonWidth(isPad: true), 39, accuracy: 0.001)
        XCTAssertEqual(LayoutMetrics.CandidateBar.Chevron.dismissButtonWidth(isPad: false),
                       LayoutMetrics.CandidateBar.Chevron.buttonWidth(isPad: false) / 2 * 1.5, accuracy: 0.001)
    }

    // #139: pure-number fields (.numberPad/.decimalPad) route to the strict, mode-key-free
    // `phone_number` keypad (matching Android), while `.asciiCapableNumberPad` keeps `symbols1`
    // so its EN/中 keys can still reach the letter layouts. (Aligns iOS routing with Android;
    // not observable on iOS because iOS system-replaces numeric fields.)
    func testNumberFieldRoutingSplitsPureNumberFromAsciiCapable() {
        func layout(_ kt: UIKeyboardType) -> String {
            KeyboardViewController.layoutIdForCurrentInputField(
                keyboardType: kt,
                isEnglishOnly: true,
                hasActivatedIMs: true,
                englishLayout: "lime_english",
                resolvedActiveLayoutId: "lime_phonetic")
        }
        XCTAssertEqual(layout(.phonePad), "phone_number")
        XCTAssertEqual(layout(.numberPad), "phone_number")
        XCTAssertEqual(layout(.decimalPad), "phone_number")
        XCTAssertEqual(layout(.asciiCapableNumberPad), "symbols1")
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

    func testApplyHeightDoesNotOverrideKeyboardSizePreference() throws {
        let source = try String(contentsOf: projectFileURL("LimeKeyboard/KeyboardViewController.swift"),
                                encoding: .utf8)

        XCTAssertTrue(source.contains("keyboardView?.keySizeScale      = keyboardSize"))
        XCTAssertTrue(source.contains("let keysHeight = keyboardView?.preferredHeight"))
        // #139: height is content-driven — the explicit constraint lives on
        // keyboardView; view height derives from the subview chain. An explicit
        // view.heightAnchor constant made iOS latch stale rotation frames.
        XCTAssertTrue(source.contains("kbView.heightAnchor.constraint(equalToConstant: kbTarget)"))
        XCTAssertFalse(source.contains("view.heightAnchor.constraint(equalToConstant: totalHeight)"))
        // #139: the height constant must never move inside a rotation
        // transaction; it is applied once after the coordinator settles.
        XCTAssertTrue(source.contains("rotationSettling = true"))
        XCTAssertTrue(source.contains("if rotationSettling"))
        XCTAssertTrue(source.contains("self.rotationSettling = false"))
        // #139 switch-in: the attach overshoot must fire at viewDidAppear
        // (mid-attach) targeting kbTarget + delta, and be restored by the
        // rendered-settle gate. Removing it re-breaks in-place keyboard
        // switches (host never told LIME's real frame).
        XCTAssertTrue(source.contains("attachOvershoot = true"))
        XCTAssertTrue(source.contains("if attachOvershoot { kbTarget += Self.attachOvershootDelta }"))
        XCTAssertTrue(source.contains("attachOvershoot = false"))
        XCTAssertFalse(source.contains("applyEffectiveKeySizeScaleForHeight()"))
        XCTAssertFalse(source.contains("private func effectiveKeySizeScaleForHeight()"))
        XCTAssertTrue(source.contains("publishKeyboardHeightToUIKit()"))
        guard let helperStart = source.range(of: "private func publishKeyboardHeightToUIKit()"),
              let helperEnd = source.range(of: "\n    }\n\n    // MARK: - Key Event Dispatch",
                                           range: helperStart.upperBound..<source.endIndex) else {
            return XCTFail("could not isolate publishKeyboardHeightToUIKit")
        }
        let helper = String(source[helperStart.upperBound..<helperEnd.lowerBound])
        XCTAssertTrue(helper.contains("view.setNeedsUpdateConstraints()"))
        XCTAssertTrue(source.contains("if didChangeHeight"))
        XCTAssertFalse(helper.contains("setNeedsLayout()"))
        XCTAssertFalse(helper.contains("layoutIfNeeded()"))
    }

    func testKeyboardPreferredHeightTracksLayoutRowsAndScale() {
        let fourRow = KeyboardView(layout: testLayout(id: "four_row", regularRows: 3))
        let fiveRow = KeyboardView(layout: testLayout(id: "five_row", regularRows: 4))
        let row = LayoutMetrics.KeyboardRow.rowHeight(
            isPadHardware: UIDevice.current.userInterfaceIdiom == .pad,
            isPad: LayoutLoader.hostIsPad,
            isLandscape: false)
        let bottom = LayoutMetrics.KeyboardRow.bottomRowHeight(
            isPadHardware: UIDevice.current.userInterfaceIdiom == .pad,
            isPad: LayoutLoader.hostIsPad,
            isLandscape: false)

        XCTAssertEqual(fourRow.preferredHeight, 3.0 * row + bottom, accuracy: 0.001)
        XCTAssertEqual(fiveRow.preferredHeight, 4.0 * row + bottom, accuracy: 0.001)
        XCTAssertEqual(fiveRow.preferredHeight - fourRow.preferredHeight, row, accuracy: 0.001)

        fiveRow.keySizeScale = 1.2
        XCTAssertEqual(fiveRow.preferredHeight, (4.0 * row + bottom) * 1.2, accuracy: 0.001)

        fiveRow.showArrowKey = 1
        XCTAssertEqual(fiveRow.preferredHeight, (5.0 * row + bottom) * 1.2, accuracy: 0.001)
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

    func testPopupKeyboardTapHighlightFiresHapticBeforePreview() throws {
        let sourceURL = projectFileURL("LimeKeyboard/KeyboardViewController.swift")
        let source = try String(contentsOf: sourceURL, encoding: .utf8)
        let pattern = #"func popupKeyboardView\(_ popup: PopupKeyboardView, didHighlight keyDef: KeyDef\?\) \{[\s\S]*?\n    \}"#
        let regex = try NSRegularExpression(pattern: pattern)
        let range = NSRange(source.startIndex..<source.endIndex, in: source)
        let match = try XCTUnwrap(regex.firstMatch(in: source, range: range))
        let method = String(source[Range(match.range, in: source)!])

        let hapticRange = try XCTUnwrap(method.range(of: "if keyDef != nil { fireHapticIfEnabled() }"))
        let previewRange = try XCTUnwrap(method.range(of: "showPopupKeyPreview(for: keyDef)"))
        XCTAssertLessThan(hapticRange.lowerBound, previewRange.lowerBound)
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
        XCTAssertTrue(source.contains("cachedImConfig(\"limeendkey\")"))
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
            resolvedActiveLayoutId: "lime_cj"), "phone_number")
        XCTAssertEqual(KeyboardViewController.layoutIdForCurrentInputField(
            keyboardType: .decimalPad,
            isEnglishOnly: false,
            hasActivatedIMs: true,
            englishLayout: "lime_english",
            resolvedActiveLayoutId: "lime_cj"), "phone_number")
        XCTAssertEqual(KeyboardViewController.layoutIdForCurrentInputField(
            keyboardType: .asciiCapableNumberPad,
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

    func testKeyboardHeartbeatUsesOutboxAndLocalMirrorOnly() throws {
        let source = try String(contentsOf: projectFileURL("LimeKeyboard/KeyboardViewController.swift"),
                                encoding: .utf8)

        XCTAssertFalse(source.contains("sharedDefaults?.set(true, forKey: \"keyboard_extension_loaded\")"))
        XCTAssertFalse(source.contains("sharedDefaults?.set(currentHasFullAccess, forKey: \"keyboard_has_full_access\")"))
        XCTAssertFalse(source.contains("sharedDefaults?.set(now, forKey: \"keyboard_last_seen_at\")"))
        XCTAssertFalse(source.contains("sharedDefaults?.set(message, forKey: \"keyboard_db_last_error\")"))
        XCTAssertFalse(source.contains("sharedDefaults?.removeObject(forKey: \"keyboard_db_last_error\")"))
        XCTAssertTrue(source.contains("localDefaults.set(true, forKey: \"keyboard_extension_loaded\")"))
        XCTAssertTrue(source.contains("localDefaults.set(currentHasFullAccess, forKey: \"keyboard_has_full_access\")"))
        XCTAssertTrue(source.contains("localDefaults.set(now, forKey: \"keyboard_last_seen_at\")"))
        XCTAssertTrue(source.contains("UserDefaults.standard.set(message, forKey: \"keyboard_db_last_error\")"))
        XCTAssertTrue(source.contains("KeyboardHeartbeat("))
        XCTAssertTrue(source.contains("atomicWrite(data, to: SyncPaths.heartbeat(baseURL))"))
        XCTAssertTrue(source.contains("postSyncSignal(currentHasFullAccess ? .faOn : .faOff)"))
        XCTAssertTrue(source.contains("databaseSetupAttempts = 3"))
        XCTAssertTrue(source.contains("prepareKeyboardRuntimeDatabaseWithRetry"))
    }

    func testKeyboardLegacyGenerationSignalsAreRemoved() throws {
        let source = try String(contentsOf: projectFileURL("LimeKeyboard/KeyboardViewController.swift"),
                                encoding: .utf8)

        XCTAssertFalse(source.contains("DBServer.databaseGenerationKey"))
        XCTAssertFalse(source.contains("LIMEPreferenceManager.keyboardRuntimeGenerationKey"))
        XCTAssertFalse(source.contains("lastKnownDatabaseGeneration"))
        XCTAssertFalse(source.contains("lastKnownKeyboardRuntimeGeneration"))
        XCTAssertFalse(source.contains("databaseWasReplaced"))
        XCTAssertFalse(source.contains("keyboardRuntimeChanged"))
        XCTAssertFalse(source.contains("setupDatabase(forceReopen:"))
        XCTAssertFalse(source.contains("prepareKeyboardRuntimeDatabase(forceReopen:"))
    }

    func testFAProbeI0BlocksAreRemoved() throws {
        let removedProbeMarker = "FA-PROBE" + "-I0"
        for relativePath in [
            "LimeKeyboard/KeyboardViewController.swift",
            "LimeSettings/Views/SetupTabView.swift"
        ] {
            let source = try String(contentsOf: projectFileURL(relativePath),
                                    encoding: .utf8)
            XCTAssertFalse(source.contains(removedProbeMarker), relativePath)
            XCTAssertFalse(source.contains("runFAProbe"), relativePath)
            XCTAssertFalse(source.contains("faProbeSetup"), relativePath)
            XCTAssertFalse(source.contains("probe_fixture.limedb"), relativePath)
        }
    }

    func testSetupRelayUsesOnlyRootProbeField() throws {
        let setupSource = try String(contentsOf: projectFileURL("LimeSettings/Views/SetupTabView.swift"),
                                     encoding: .utf8)
        let rootSource = try String(contentsOf: projectFileURL("LimeSettings/LimeSettingsView.swift"),
                                    encoding: .utf8)
        let syncSource = try String(contentsOf: projectFileURL("Shared/Database/SyncContract.swift"),
                                    encoding: .utf8)

        XCTAssertFalse(setupSource.contains("@FocusState private var probeFocused"))
        XCTAssertFalse(setupSource.contains("TextField(\"\", text: $probeText)"))
        XCTAssertFalse(setupSource.contains("probeFocused = true"))
        XCTAssertTrue(setupSource.contains("NotificationCenter.default.post(name: .limeTriggerRelay"))
        XCTAssertTrue(rootSource.contains("RelayProbeField(text: $rootRelayText, isFocused: $rootRelayFocused)"))
        XCTAssertTrue(rootSource.contains("NotificationCenter.default.publisher(for: .limeTriggerRelay)"))
        XCTAssertTrue(syncSource.contains("static let limeRelayResolvedNotActive"))
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
            let value = Int(scalar.value)
            return (0x02CA...0x02CB).contains(value)
                || value == 0x02C7
                || value == 0x02D9
                || (0x3100...0x312F).contains(value)
                || (0x31A0...0x31BF).contains(value)
                || (0x4E00...0x9FFF).contains(value)
        }
    }

    // MARK: - feat#124 English "123" long-press → phone_simple

    private func phoneSimpleSymbolKey(_ longPress: Int) -> KeyDef {
        KeyDef(code: -2, label: "123", longPressCode: longPress)
    }

    func testPhoneSimpleKeyCodeMirrorsAndroid() {
        XCTAssertEqual(LimeKeyCode.switchToPhoneSimple.rawValue, -106)
    }

    func testIPhoneEnglishSymbolKeyKeeps123AndLongPressesToPhoneSimple() throws {
        for id in ["lime_english", "lime_english_shift",
                   "lime_english_number", "lime_english_number_shift"] {
            let key = try XCTUnwrap(
                loadKeyboardLayoutFixture(id).rows.flatMap { $0.keys }.first { $0.code == -2 },
                "\(id) should have a -2 symbol key")
            XCTAssertEqual(key.label, "@string/label_symbol_key", "\(id): keep the 123 face")
            XCTAssertEqual(key.longPressCode, -106, "\(id): -2 must long-press to phone_simple")
        }
    }

    func testPhoneSimpleOwnSymbolKeyHasNoPhoneSimpleLongPress() throws {
        let key = try XCTUnwrap(
            loadKeyboardLayoutFixture("phone_simple").rows.flatMap { $0.keys }.first { $0.code == -2 })
        XCTAssertNotEqual(key.longPressCode, -106,
                          "phone_simple's own 123 key must not re-trigger phone_simple")
    }

    func testGenericLongPressFiresForEnglishSymbolKeyOnIPhone() {
        XCTAssertTrue(KeyboardView.shouldUseGenericLongPress(
            keyDef: phoneSimpleSymbolKey(-106), isPad: false,
            layoutId: "lime_english", legacyGlobeMode: false))
    }

    func testGenericLongPressIgnoresKeysWithoutLongPressCode() {
        XCTAssertFalse(KeyboardView.shouldUseGenericLongPress(
            keyDef: phoneSimpleSymbolKey(0), isPad: false,
            layoutId: "lime_english", legacyGlobeMode: false))
    }

    func testGenericLongPressIgnoresGlobeAndDualRowKeys() {
        // globe/done carries longPressCode -100; iPad dual-row keys carry a glyph code.
        // Neither is the phone_simple code (-106), so neither gets the generic hint/gesture.
        XCTAssertFalse(KeyboardView.shouldUseGenericLongPress(
            keyDef: KeyDef(code: -200, longPressCode: -100), isPad: false,
            layoutId: "lime_english", legacyGlobeMode: false))
        XCTAssertFalse(KeyboardView.shouldUseGenericLongPress(
            keyDef: KeyDef(code: 97, longPressCode: 65), isPad: true,
            layoutId: "lime_english_ipad", legacyGlobeMode: false))
    }

    func testKeyboardViewWiresGenericLongPressAndHint() throws {
        let src = try String(contentsOf: projectFileURL("LimeKeyboard/KeyboardView.swift"), encoding: .utf8)
        XCTAssertTrue(src.contains("func shouldUseGenericLongPress"))
        XCTAssertTrue(src.contains("shouldStartDeferredLongPressTimer"))
        XCTAssertTrue(src.contains("didLongPressKey"))
    }

    func testControllerLoadsPhoneSimpleAndRoutesLongPress() throws {
        let src = try String(contentsOf: projectFileURL("LimeKeyboard/KeyboardViewController.swift"), encoding: .utf8)
        XCTAssertTrue(src.contains("case LimeKeyCode.switchToPhoneSimple.rawValue"))
        XCTAssertTrue(src.contains("func switchToPhoneSimple"))
        XCTAssertTrue(src.contains("LayoutLoader.load(\"phone_simple\")"))
        XCTAssertTrue(src.contains("didLongPressKey keyDef"))
    }

    func testPhoneSimpleLayoutReturnsToEnglishViaAbc() throws {
        let codes = try loadKeyboardLayoutFixture("phone_simple").rows.flatMap { $0.keys.map(\.code) }
        XCTAssertTrue(codes.contains(LimeKeyCode.switchToEnglish.rawValue), "phone_simple needs ABC (-9)")
        XCTAssertTrue(codes.contains(LimeKeyCode.switchToSymbol.rawValue), "phone_simple needs 123 (-2)")
    }

    // MARK: - isKeyInImkeys unification characterization

    @MainActor
    func testAcceptsIntoComposingCharacterizesCangjiePhoneAcceptance() {
        let controller = KeyboardViewController()
        controller.currentImKeys = "qwertyuiopasdfghjklzxcvbnm"
        func accepts(_ code: Int) -> Bool {
            controller.acceptsIntoComposing(code: code,
                                            hasSymbol: false,
                                            hasNumber: false,
                                            isPhonetic: false)
        }

        XCTAssertTrue(accepts(97))  // a
        XCTAssertTrue(accepts(65))  // A
        XCTAssertTrue(accepts(44))  // ,
        XCTAssertTrue(accepts(46))  // .
        XCTAssertFalse(accepts(59)) // ;
        XCTAssertFalse(accepts(47)) // /
    }

    func testComposingImKeysPreferPublishedMetadataForImportedTables() {
        XCTAssertEqual(
            KeyboardViewController.composingImKeys(
                activeIM: "freenewcj",
                publishedImKeys: "qwertyuiopasdfghjkl;'zxcvbnm",
                fallbackImKeys: "qwertyuiopasdfghjklzxcvbnm"),
            "qwertyuiopasdfghjkl;'zxcvbnm")
    }

    func testComposingImKeysKeepPhoneticKeyboardTypeFallback() {
        XCTAssertEqual(
            KeyboardViewController.composingImKeys(
                activeIM: "phonetic",
                publishedImKeys: ",-./0123456789;abcdefghijklmnopqrstuvwxyz'",
                fallbackImKeys: "1234567890"),
            "1234567890")
    }

    func testPublishedKeynameUsesImJsonMetadataForImportedTables() {
        XCTAssertEqual(
            KeyboardViewController.publishedKeyname(
                ";'",
                activeIM: "freenewcj",
                imkeys: "qwertyuiopasdfghjkl;'zxcvbnm",
                imkeynames: "手|田|水|口|廿|卜|山|戈|人|心|日|尸|木|火|土|竹|十|大|中|分|撇|重|難|金|女|月|弓|一"),
            "分撇")
    }

    func testPublishedKeynameSkipsPhoneticSoKeyboardTypeMappingWins() {
        XCTAssertNil(
            KeyboardViewController.publishedKeyname(
                "1",
                activeIM: "phonetic",
                imkeys: "1234567890",
                imkeynames: "ㄅ|ㄉ|ˇ|ˋ|ㄓ|ˊ|˙|ㄚ|ㄞ|ㄢ"))
    }

    @MainActor
    func testAcceptsIntoComposingUsesImportedSymbolRootsFromImkeys() {
        let controller = KeyboardViewController()
        controller.currentImKeys = "qwertyuiopasdfghjkl;'zxcvbnm"
        func accepts(_ code: Int) -> Bool {
            controller.acceptsIntoComposing(code: code,
                                            hasSymbol: false,
                                            hasNumber: false,
                                            isPhonetic: false)
        }

        XCTAssertTrue(accepts(59)) // ;
        XCTAssertTrue(accepts(39)) // '
    }

    @MainActor
    func testAcceptsIntoComposingCharacterizesDayiPhoneAcceptance() {
        let controller = KeyboardViewController()
        controller.currentImKeys = "1234567890qwertyuiopasdfghjkl;zxcvbnm,./"
        func accepts(_ code: Int) -> Bool {
            controller.acceptsIntoComposing(code: code,
                                            hasSymbol: true,
                                            hasNumber: true,
                                            isPhonetic: false)
        }

        for code in [97, 65, 48, 57, 44, 46, 59, 47] {
            XCTAssertTrue(accepts(code), "code \(code)")
        }
        XCTAssertFalse(accepts(64)) // @
    }

    @MainActor
    func testAcceptsIntoComposingCharacterizesArray10PhoneAcceptance() {
        let controller = KeyboardViewController()
        controller.currentImKeys = "1234567890"
        func accepts(_ code: Int) -> Bool {
            controller.acceptsIntoComposing(code: code,
                                            hasSymbol: false,
                                            hasNumber: true,
                                            isPhonetic: false)
        }

        for code in [48, 57, 44, 46] {
            XCTAssertTrue(accepts(code), "code \(code)")
        }
        XCTAssertFalse(accepts(97)) // a
        XCTAssertFalse(accepts(59)) // ;
    }

    func testArray30SymbolDigitsOnlyContinueWOrHgNumberComposition() {
        for composing in ["w", "w1", "w123", "hg", "hg1"] {
            XCTAssertTrue(KeyboardViewController.isArraySymbolDigit(
                activeIM: "array", composing: composing, code: 48))
            XCTAssertTrue(KeyboardViewController.isArraySymbolDigit(
                activeIM: "array", composing: composing, code: 57))
        }

        XCTAssertFalse(KeyboardViewController.isArraySymbolDigit(
            activeIM: "array", composing: "", code: 49))
        XCTAssertFalse(KeyboardViewController.isArraySymbolDigit(
            activeIM: "array", composing: "wa", code: 49))
        XCTAssertFalse(KeyboardViewController.isArraySymbolDigit(
            activeIM: "array", composing: "h", code: 49))
        XCTAssertFalse(KeyboardViewController.isArraySymbolDigit(
            activeIM: "array", composing: "hga", code: 49))
        XCTAssertFalse(KeyboardViewController.isArraySymbolDigit(
            activeIM: "array10", composing: "w", code: 49))
        XCTAssertFalse(KeyboardViewController.isArraySymbolDigit(
            activeIM: "array", composing: "w", code: 97))
    }

    @MainActor
    func testAcceptsIntoComposingCharacterizesIPadCangjieAcceptance() {
        let controller = KeyboardViewController()
        controller.currentImKeys = "qwertyuiopasdfghjklzxcvbnm"
        func accepts(_ code: Int) -> Bool {
            controller.acceptsIntoComposing(code: code,
                                            hasSymbol: false,
                                            hasNumber: false,
                                            isPhonetic: false)
        }

        XCTAssertTrue(accepts(97))   // a
        XCTAssertTrue(accepts(122))  // z
        XCTAssertTrue(accepts(65))   // A
        XCTAssertFalse(accepts(48))  // 0
        XCTAssertTrue(accepts(44))   // ,
        XCTAssertTrue(accepts(46))   // .
        XCTAssertFalse(accepts(59))  // ;
    }

    // MARK: - feat#140 cj4 semicolon key

    func testCj4SemicolonTransformAddsPhoneKeyAndRewritesIPadDualKey() throws {
        let phoneLayout = LayoutLoader.applyingCj4Semicolon(to: try loadKeyLayoutFixture("lime_cj"))
        let cangjieHomeRowCodes = [97, 115, 100, 102, 103, 104, 106, 107, 108]
        let phoneHomeRow = try XCTUnwrap(phoneLayout.rows.first {
            Array($0.keys.prefix(cangjieHomeRowCodes.count).map(\.code)) == cangjieHomeRowCodes
        })

        XCTAssertEqual(phoneHomeRow.keys.map(\.code), cangjieHomeRowCodes + [59])
        XCTAssertEqual(phoneHomeRow.keys.last?.label, "'")
        XCTAssertEqual(phoneHomeRow.keys.last?.sublabel, ";")
        XCTAssertEqual(phoneHomeRow.keys.last?.longPressCode, 39)
        XCTAssertEqual(phoneHomeRow.keys.last?.widthPercent, 10)

        let iPadLayout = LayoutLoader.applyingCj4Semicolon(to: try loadKeyLayoutFixture("lime_cj_ipad"))
        let iPadKeys = iPadLayout.rows.flatMap(\.keys)
        let rewritten = try XCTUnwrap(iPadKeys.first {
            $0.code == 59 && $0.longPressCode == 39 && $0.widthPercent == 7
        })

        XCTAssertFalse(iPadKeys.contains { $0.code == 65306 })
        XCTAssertEqual(rewritten.label, "'")
        XCTAssertEqual(rewritten.sublabel, ";")
        XCTAssertEqual(rewritten.longPressCode, 39)
        XCTAssertEqual(rewritten.widthPercent, 7)
    }

    func testCangjieSemicolonLayoutIdsLoadThroughCurrentLayoutsAcrossVariants() throws {
        func semicolonLayout(id: String, sourceId: String) throws -> LimeKeyLayout {
            XCTAssertEqual(LayoutLoader.cangjieSemicolonSourceLayoutId(for: id), sourceId)
            return LayoutLoader.applyingCjSemicolon(
                to: try loadKeyLayoutFixture(sourceId),
                preservingId: id)
        }

        try assertPhoneCangjieSemicolon(
            try semicolonLayout(id: "lime_cj_semi", sourceId: "lime_cj"),
            expectedId: "lime_cj_semi")
        try assertPhoneCangjieSemicolon(
            try semicolonLayout(id: "lime_cj_semi_shift", sourceId: "lime_cj_shift"),
            expectedId: "lime_cj_semi_shift")
        try assertPhoneCangjieSemicolon(
            try semicolonLayout(id: "lime_cj_number_semi", sourceId: "lime_cj_number"),
            expectedId: "lime_cj_number_semi")
        try assertPhoneCangjieSemicolon(
            try semicolonLayout(id: "lime_cj_number_semi_shift", sourceId: "lime_cj_number_shift"),
            expectedId: "lime_cj_number_semi_shift")

        try assertIPadCangjieSemicolon(
            try semicolonLayout(id: "lime_cj_semi_ipad", sourceId: "lime_cj_ipad"),
            expectedId: "lime_cj_semi_ipad")
        try assertIPadCangjieSemicolon(
            try semicolonLayout(id: "lime_cj_semi_ipad_narrow", sourceId: "lime_cj_ipad_narrow"),
            expectedId: "lime_cj_semi_ipad_narrow")
        try assertIPadCangjieSemicolon(
            try semicolonLayout(id: "lime_cj_number_semi_ipad_shift", sourceId: "lime_cj_number_ipad_shift"),
            expectedId: "lime_cj_number_semi_ipad_shift")
    }

    private func assertPhoneCangjieSemicolon(_ layout: LimeKeyLayout,
                                             expectedId: String,
                                             file: StaticString = #filePath,
                                             line: UInt = #line) throws {
        let cangjieHomeRowCodes = expectedId.contains("_shift")
            ? [65, 83, 68, 70, 71, 72, 74, 75, 76]
            : [97, 115, 100, 102, 103, 104, 106, 107, 108]
        let phoneHomeRow = try XCTUnwrap(layout.rows.first {
            Array($0.keys.prefix(cangjieHomeRowCodes.count).map(\.code)) == cangjieHomeRowCodes
        }, file: file, line: line)

        XCTAssertEqual(layout.id, expectedId, file: file, line: line)
        XCTAssertEqual(phoneHomeRow.keys.map(\.code), cangjieHomeRowCodes + [59], file: file, line: line)
        XCTAssertEqual(phoneHomeRow.keys.last?.label, "'", file: file, line: line)
        XCTAssertEqual(phoneHomeRow.keys.last?.sublabel, ";", file: file, line: line)
        XCTAssertEqual(phoneHomeRow.keys.last?.longPressCode, 39, file: file, line: line)
        XCTAssertEqual(phoneHomeRow.keys.last?.widthPercent, 10, file: file, line: line)
    }

    private func assertIPadCangjieSemicolon(_ layout: LimeKeyLayout,
                                            expectedId: String,
                                            file: StaticString = #filePath,
                                            line: UInt = #line) throws {
        let keys = layout.rows.flatMap(\.keys)
        let rewritten = try XCTUnwrap(keys.first {
            $0.code == 59 && $0.longPressCode == 39
        }, file: file, line: line)

        XCTAssertEqual(layout.id, expectedId, file: file, line: line)
        XCTAssertFalse(keys.contains { $0.code == 65306 || $0.code == 65307 }, file: file, line: line)
        XCTAssertEqual(rewritten.label, "'", file: file, line: line)
        XCTAssertEqual(rewritten.sublabel, ";", file: file, line: line)
    }

    func testCangjieSemicolonKeyboardCodesForceSymbolMapping() {
        XCTAssertTrue(KeyboardViewController.hasSymbolMappingForKeyboard(false, keyboardId: "cj_semi"))
        XCTAssertTrue(KeyboardViewController.hasSymbolMappingForKeyboard(false, keyboardId: "cj_num_semi"))
        XCTAssertTrue(KeyboardViewController.hasSymbolMappingForKeyboard(false, keyboardId: "lime_cj_semi"))
        XCTAssertFalse(KeyboardViewController.hasSymbolMappingForKeyboard(false, keyboardId: "cjnum"))
        XCTAssertTrue(KeyboardViewController.hasSymbolMappingForKeyboard(true, keyboardId: "cjnum"))
    }

    // A fullwidth-punctuation cell (slim advance) must still get a Han-sized
    // tap target: one em (font point size) + both side pads. Regression guard
    // for the auto-Chinese-punc strip touch-area bug (docs/AUTO_CHINESE_PUNC.md,
    // docs/CANDI_LAYOUT.md §1).
    func testCandidateCellWidthFlooredAtOneEmPlusSidePads() {
        XCTAssertEqual(
            CandidateBarView.minCandidateCellWidth(fontPointSize: 26, hPad: 10),
            46, accuracy: 0.001,
            "26pt em + 2×10pt pad — narrow punctuation must tap as wide as a Han char")
        // Floor scales with font size (font_size pref) and idiom hPad.
        XCTAssertEqual(
            CandidateBarView.minCandidateCellWidth(fontPointSize: 28, hPad: 14),
            56, accuracy: 0.001)
    }

    func testCandidateBarReusesButtonsAndRefreshesTitlesAndHighlight() {
        let bar = CandidateBarView(frame: CGRect(x: 0, y: 0, width: 390, height: 48))
        let first = [
            Mapping(id: 0, code: "ab", word: "ab", score: 0, baseScore: 0,
                    recordType: Mapping.RecordType.composingCode),
            Mapping(id: 1, code: "ab", word: "明", score: 0, baseScore: 0),
            Mapping(id: 2, code: "li", word: "力", score: 0, baseScore: 0),
        ]
        bar.setCandidates(first, selectedIndex: 0)
        let originalButtons = candidateButtons(in: bar)

        XCTAssertEqual(originalButtons.map { $0.title(for: .normal) }, ["ab", "明", "力"])
        XCTAssertFalse(originalButtons[0].pillView.backgroundColor?.isEqual(UIColor.clear) ?? true)

        let shorter = [
            Mapping(id: 3, code: "xy", word: "xy", score: 0, baseScore: 0,
                    recordType: Mapping.RecordType.composingCode),
            Mapping(id: 4, code: "xy", word: "新", score: 0, baseScore: 0),
        ]
        bar.setCandidates(shorter, selectedIndex: 1)
        let shorterButtons = candidateButtons(in: bar)

        XCTAssertEqual(shorterButtons.count, 2)
        XCTAssertTrue(shorterButtons[0] === originalButtons[0])
        XCTAssertTrue(shorterButtons[1] === originalButtons[1])
        XCTAssertNil(originalButtons[2].superview)
        XCTAssertEqual(shorterButtons.map { $0.title(for: .normal) }, ["xy", "新"])
        XCTAssertEqual(shorterButtons.map(\.tag), [0, 1])
        XCTAssertTrue(shorterButtons[0].pillView.backgroundColor?.isEqual(UIColor.clear) ?? false)
        XCTAssertFalse(shorterButtons[1].pillView.backgroundColor?.isEqual(UIColor.clear) ?? true)

        let longer = shorter + [
            Mapping(id: 5, code: "za", word: "再", score: 0, baseScore: 0),
            Mapping(id: 6, code: "jian", word: "見", score: 0, baseScore: 0),
        ]
        bar.setCandidates(longer, selectedIndex: 2)
        let longerButtons = candidateButtons(in: bar)

        XCTAssertEqual(longerButtons.count, 4)
        XCTAssertTrue(longerButtons[0] === shorterButtons[0])
        XCTAssertTrue(longerButtons[1] === shorterButtons[1])
        XCTAssertEqual(longerButtons.map { $0.title(for: .normal) }, ["xy", "新", "再", "見"])
        XCTAssertEqual(longerButtons.map(\.tag), [0, 1, 2, 3])
        XCTAssertTrue(longerButtons[1].pillView.backgroundColor?.isEqual(UIColor.clear) ?? false)
        XCTAssertFalse(longerButtons[2].pillView.backgroundColor?.isEqual(UIColor.clear) ?? true)
    }

    // #157: hamburger reverse-lookup selection must be immediate and persistent. Every keyboard
    // consumer (runtime commit, menu label, picker) reads hotReverseLookup(for:), which must let a
    // hot-store value win over a stale cold App Group value, and seed once from cold when hot absent.
    func testHotReverseLookupWinsOverStaleColdAndSeedsOnceWhenAbsent() {
        let controller = KeyboardViewController()
        let im = "rl157test"                       // distinctive nick so no real IM key collides
        let key = "\(im)_im_reverselookup"
        let hot = UserDefaults.standard
        let cold = UserDefaults(suiteName: LIMEPreferenceManager.suiteName)
        defer { hot.removeObject(forKey: key); cold?.removeObject(forKey: key) }

        // Hot value present → wins over an older cold value (the #157 "not immediate" case).
        cold?.set("cangjie", forKey: key)
        hot.set("dayi", forKey: key)
        XCTAssertEqual(controller.hotReverseLookup(for: im), "dayi")

        // Hot absent → seed once from cold, then cold is never consulted again for this key.
        hot.removeObject(forKey: key)
        cold?.set("array30", forKey: key)
        XCTAssertEqual(controller.hotReverseLookup(for: im), "array30")   // seeded from cold
        cold?.set("phonetic", forKey: key)                               // later cold change
        XCTAssertEqual(controller.hotReverseLookup(for: im), "array30")   // hot still wins
    }

    // #156: Phonetic et41 must resolve to the ETEN 41-key layout, not standard. The visible
    // layout is driven by the phonetic_keyboard_type pref (Android parity), so et_41/eten map to
    // lime_et_41 like eten26→lime_et26 and hsu→lime_hsu; standard has no special layout (nil →
    // resolved via the keyboard-config path).
    func testPhoneticSpecialLayoutIdMapsEt41AndSiblingsFromPref() {
        XCTAssertEqual(KeyboardViewController.phoneticSpecialLayoutId(for: "et_41"), "lime_et_41")
        XCTAssertEqual(KeyboardViewController.phoneticSpecialLayoutId(for: "eten"), "lime_et_41")
        XCTAssertEqual(KeyboardViewController.phoneticSpecialLayoutId(for: "eten26"), "lime_et26")
        XCTAssertEqual(KeyboardViewController.phoneticSpecialLayoutId(for: "eten26_symbol"), "lime_et26")
        XCTAssertEqual(KeyboardViewController.phoneticSpecialLayoutId(for: "hsu"), "lime_hsu")
        XCTAssertEqual(KeyboardViewController.phoneticSpecialLayoutId(for: "hsu_symbol"), "lime_hsu")
        XCTAssertNil(KeyboardViewController.phoneticSpecialLayoutId(for: "standard"))   // not et41
    }

    private func projectFileURL(_ relativePath: String) -> URL {
        // Prefer the copy bundled into the test target — on Xcode Cloud the source
        // checkout is absent at test runtime, so #filePath resolves to a missing path.
        if let bundled = Bundle(for: type(of: self)).resourceURL?.appendingPathComponent(relativePath),
           FileManager.default.fileExists(atPath: bundled.path) {
            return bundled
        }
        return URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
    }

    private func testLayout(id: String, regularRows: Int) -> LimeKeyLayout {
        let regular = KeyRow(keys: [KeyDef(code: 97, label: "a", widthPercent: 100)])
        let bottom = KeyRow(keys: [KeyDef(code: LimeKeyCode.space.rawValue, label: "space", widthPercent: 100)],
                            isBottomRow: true)
        return LimeKeyLayout(id: id, rows: Array(repeating: regular, count: regularRows) + [bottom])
    }

    private func candidateButtons(in view: UIView) -> [CandidateButton] {
        var result: [CandidateButton] = []
        func walk(_ node: UIView) {
            if let button = node as? CandidateButton {
                result.append(button)
            }
            node.subviews.forEach(walk)
        }
        walk(view)
        return result
    }

    private func loadKeyLayoutFixture(_ layoutID: String) throws -> LimeKeyLayout {
        let fixture = try loadKeyboardLayoutFixture(layoutID)
        let rows = fixture.rows.map { row in
            KeyRow(keys: row.keys.map { key in
                KeyDef(code: key.code,
                       label: key.label,
                       sublabel: key.sublabel,
                       widthPercent: CGFloat(key.widthPercent),
                       longPressCode: key.longPressCode ?? 0)
            }, isBottomRow: row.isBottomRow)
        }
        return LimeKeyLayout(id: layoutID, rows: rows)
    }

}
