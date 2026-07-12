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

// LIMEPreferenceManagerTest.swift
// LimeIMETests
//
// Round-trip tests for every preference key in spec §9.
// Uses an isolated in-memory UserDefaults suite so no real App Group is needed.

import XCTest
@testable import LimeIME

// MARK: - LIMEPreferenceManagerTest

final class LIMEPreferenceManagerTest: XCTestCase {

    private var suiteName: String!
    private var testDefaults: UserDefaults!
    private var prefs: LIMEPreferenceManager!

    override func setUp() {
        super.setUp()
        suiteName = "test.lime.prefs.\(UUID().uuidString)"
        testDefaults = UserDefaults(suiteName: suiteName)!
        prefs = LIMEPreferenceManager(defaults: testDefaults)
    }

    override func tearDown() {
        testDefaults.removePersistentDomain(forName: suiteName)
        super.tearDown()
    }

    func testSyncIMActivatedStateKeepsKeyboardListCoherentWithEnabledIMs() throws {
        let source = try String(contentsOf: projectFileURL("Shared/Preferences/LIMEPreferenceManager.swift"),
                                encoding: .utf8)

        XCTAssertTrue(source.contains("let enabledTableNicks = Set(enabledConfigs.map { $0.element.tableNick })"))
        XCTAssertTrue(source.contains("current.isEmpty || !enabledTableNicks.contains(current)"))
        XCTAssertTrue(source.contains("activeIM = firstEnabled"))
    }

    // MARK: - Default values

    func testDefaultKeyboardTheme() {
        XCTAssertEqual(prefs.keyboardTheme, 6)
    }

    func testDefaultEnableEmojiPosition() {
        XCTAssertEqual(prefs.enableEmojiPosition, 5)
    }

    func testMigratesDisabledEnableEmojiToPositionNone() {
        testDefaults.set(false, forKey: "enable_emoji")
        XCTAssertEqual(prefs.enableEmojiPosition, 0)
        XCTAssertNil(testDefaults.object(forKey: "enable_emoji"))
    }

    func testDefaultKeyboardSize() {
        XCTAssertEqual(prefs.keyboardSize, "1")
    }

    func testDefaultFontSize() {
        XCTAssertEqual(prefs.fontSize, "1")
    }

    func testDefaultCandidateFontSize() {
        XCTAssertEqual(prefs.candidateFontSize, 18)
    }

    func testDefaultShowArrowKey() {
        XCTAssertEqual(prefs.showArrowKey, 0)
    }

    func testDefaultSplitKeyboardMode() {
        XCTAssertEqual(prefs.splitKeyboardMode, 0)
    }

    func testDefaultVibrateOnKeypress() {
        XCTAssertTrue(prefs.vibrateOnKeypress)
    }

    func testDefaultVibrateLevel() {
        XCTAssertEqual(prefs.vibrateLevel, 40)
    }

    func testDefaultSoundOnKeypress() {
        XCTAssertFalse(prefs.soundOnKeypress)
    }

    func testDefaultKeypressSoundVolume() {
        XCTAssertEqual(prefs.keypressSoundVolume, "-1")
    }

    func testDefaultNumberRowInEnglish() {
        XCTAssertTrue(prefs.numberRowInEnglish)
    }

    func testDefaultSmartChineseInput() {
        XCTAssertTrue(prefs.smartChineseInput)
    }

    func testDefaultAutoChineseSymbol() {
        XCTAssertFalse(prefs.autoChineseSymbol)
    }

    func testDefaultAutoCommit() {
        XCTAssertEqual(prefs.autoCommit, 0)
    }

    func testDefaultCandidateSwitch() {
        XCTAssertTrue(prefs.candidateSwitch)
    }

    func testDefaultPhoneticKeyboardType() {
        XCTAssertEqual(prefs.phoneticKeyboardType, "standard")
    }

    func testDefaultHanConvertOption() {
        XCTAssertEqual(prefs.hanConvertOption, 0)
    }

    func testDefaultSimiliarEnable() {
        XCTAssertTrue(prefs.similiarEnable)
    }

    func testDefaultSimiliarList() {
        XCTAssertEqual(prefs.similiarList, 20)
    }

    func testDefaultCandidateSuggestion() {
        XCTAssertTrue(prefs.candidateSuggestion)
    }

    func testDefaultLearnPhrase() {
        XCTAssertTrue(prefs.learnPhrase)
    }

    func testDefaultLearningSwitch() {
        XCTAssertTrue(prefs.learningSwitch)
    }

    func testDefaultEnglishDictionaryEnable() {
        XCTAssertTrue(prefs.englishDictionaryEnable)
    }

    func testDefaultAcceptNumberIndex() {
        XCTAssertFalse(prefs.acceptNumberIndex)
    }

    func testDefaultAcceptSymbolIndex() {
        XCTAssertFalse(prefs.acceptSymbolIndex)
    }

    func testDefaultPersistentLanguageMode() {
        XCTAssertFalse(prefs.persistentLanguageMode)
    }

    // Reverse lookup defaults
    func testDefaultReverseLookupKeys() {
        XCTAssertEqual(prefs.customImReverselookup, "none")
        XCTAssertEqual(prefs.cjImReverselookup,     "none")
        XCTAssertEqual(prefs.scjImReverselookup,    "none")
        XCTAssertEqual(prefs.cj5ImReverselookup,    "none")
        XCTAssertEqual(prefs.ecjImReverselookup,    "none")
        XCTAssertEqual(prefs.dayiImReverselookup,   "none")
        XCTAssertEqual(prefs.phoneticImReverselookup,   "none")
        XCTAssertEqual(prefs.ezImReverselookup,     "none")
        XCTAssertEqual(prefs.arrayImReverselookup,  "none")
        XCTAssertEqual(prefs.array10ImReverselookup,"none")
        XCTAssertEqual(prefs.wbImReverselookup,     "none")
        XCTAssertEqual(prefs.hsImReverselookup,     "none")
        XCTAssertEqual(prefs.pinyinImReverselookup, "none")
    }

    // MARK: - Round-trip setters

    func testKeyboardThemeSystemValue() {
        // Default should be 6 (系統設定)
        XCTAssertEqual(prefs.keyboardTheme, 6)
        // Value 6 (系統設定) must round-trip correctly
        prefs.keyboardTheme = 6
        XCTAssertEqual(prefs.keyboardTheme, 6)
    }

    func testRoundTripKeyboardTheme() {
        prefs.keyboardTheme = 3
        XCTAssertEqual(prefs.keyboardTheme, 3)
    }

    func testRoundTripKeyboardSize() {
        prefs.keyboardSize = "0.9"
        XCTAssertEqual(prefs.keyboardSize, "0.9")
    }

    func testRoundTripFontSize() {
        prefs.fontSize = "1.2"
        XCTAssertEqual(prefs.fontSize, "1.2")
    }

    func testRoundTripCandidateFontSize() {
        prefs.candidateFontSize = 22.0
        XCTAssertEqual(prefs.candidateFontSize, 22.0)
    }

    func testRoundTripVibrateLevel() {
        prefs.vibrateLevel = 60
        XCTAssertEqual(prefs.vibrateLevel, 60)
    }

    func testRoundTripKeypressSoundVolume() {
        prefs.keypressSoundVolume = "0.75"
        XCTAssertEqual(prefs.keypressSoundVolume, "0.75")
    }

    func testRoundTripPhoneticKeyboardType() {
        prefs.phoneticKeyboardType = "hsu"
        XCTAssertEqual(prefs.phoneticKeyboardType, "hsu")
    }

    func testRoundTripHanConvertOption() {
        prefs.hanConvertOption = 2
        XCTAssertEqual(prefs.hanConvertOption, 2)
    }

    func testRoundTripSimiliarList() {
        prefs.similiarList = 30
        XCTAssertEqual(prefs.similiarList, 30)
    }

    func testRoundTripReverseLookupCustom() {
        prefs.customImReverselookup = "cj"
        XCTAssertEqual(prefs.customImReverselookup, "cj")
    }

    func testRoundTripReverseLookupBpmf() {
        prefs.phoneticImReverselookup = "phonetic"
        XCTAssertEqual(prefs.phoneticImReverselookup, "phonetic")
    }

    func testRoundTripReverseLookupByTableNick() {
        prefs.setReverseLookup("cj", for: "phonetic")
        prefs.setReverseLookup("array", for: "dayi")

        XCTAssertEqual(prefs.reverseLookup(for: "phonetic"), "cj")
        XCTAssertEqual(prefs.reverseLookup(for: "dayi"), "array")
    }

    func testReverseLookupOptionsUseEnabledIMLabelsButKeepTableNickValues() {
        let configs = [
            ImConfig(id: 1, imName: "cj", tableNick: "cj", label: "倉頡輸入法",
                     fullName: "", keyboardId: "", keyboardLandscapeId: "",
                     enabled: true, sortOrder: 0),
            ImConfig(id: 2, imName: "dayi", tableNick: "dayi", label: "大易輸入法",
                     fullName: "", keyboardId: "", keyboardLandscapeId: "",
                     enabled: true, sortOrder: 1),
            ImConfig(id: 3, imName: "array", tableNick: "array", label: "行列輸入法",
                     fullName: "", keyboardId: "", keyboardLandscapeId: "",
                     enabled: false, sortOrder: 2)
        ]

        let options = LIMEPreferenceManager.reverseLookupOptions(from: configs)

        XCTAssertEqual(options.map(\.label), ["無", "倉頡輸入法", "大易輸入法"])
        XCTAssertEqual(options.map(\.value), ["none", "cj", "dayi"])
        XCTAssertEqual(LIMEPreferenceManager.reverseLookupTargets(from: configs).map(\.value), ["cj", "dayi"])
    }

    func testReverseLookupOptionsUseIMListLabelFallback() {
        let configs = [
            ImConfig(id: 1, imName: "phonetic", tableNick: "phonetic", label: "",
                     fullName: "注音輸入法", keyboardId: "", keyboardLandscapeId: "",
                     enabled: true, sortOrder: 0),
            ImConfig(id: 2, imName: "custom", tableNick: "", label: "",
                     fullName: "", keyboardId: "", keyboardLandscapeId: "",
                     enabled: true, sortOrder: 1)
        ]

        let options = LIMEPreferenceManager.reverseLookupOptions(from: configs)

        XCTAssertEqual(options.map(\.label), ["無", "phonetic", "custom"])
        XCTAssertEqual(options.map(\.value), ["none", "phonetic", "custom"])
    }

    func testRoundTripActiveIM() {
        prefs.activeIM = "cj"
        XCTAssertEqual(prefs.activeIM, "cj")
    }

    func testRoundTripKeyboardState() {
        prefs.keyboardState = "0;1;2"
        XCTAssertEqual(prefs.keyboardState, "0;1;2")
    }

    // MARK: - Suite isolation

    func testSuiteIsolation() {
        // Two managers with different suites should not share values
        let suite2 = "test.lime.prefs.b.\(UUID().uuidString)"
        let defaults2 = UserDefaults(suiteName: suite2)!
        let prefs2 = LIMEPreferenceManager(defaults: defaults2)

        prefs.keyboardTheme = 5
        XCTAssertEqual(prefs.keyboardTheme, 5)
        XCTAssertEqual(prefs2.keyboardTheme, 6) // default, not 5

        defaults2.removePersistentDomain(forName: suite2)
    }

    // MARK: - syncIMActivatedState

    func testSyncIMActivatedStateEmpty() throws {
        let tempURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".db")
        defer { try? FileManager.default.removeItem(at: tempURL) }
        let db = try LimeDB(path: tempURL.path)
        prefs.syncIMActivatedState(dbServer: DBServer(_testDatasource: db))
        let state = prefs.keyboardState
        XCTAssertNotNil(state)
    }

    func testSyncIMActivatedStateDoesNotWriteLegacyKeyboardRuntimeGeneration() throws {
        let tempURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".db")
        defer { try? FileManager.default.removeItem(at: tempURL) }
        let db = try LimeDB(path: tempURL.path)
        let key = "lime_keyboard_runtime_generation"
        testDefaults.set(11, forKey: key)

        prefs.syncIMActivatedState(dbServer: DBServer(_testDatasource: db))

        XCTAssertEqual(testDefaults.integer(forKey: key), 11)
    }

    func testSyncIMActivatedStateWithIMs() throws {
        let tempURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString + ".db")
        defer { try? FileManager.default.removeItem(at: tempURL) }
        let db = try LimeDB(path: tempURL.path)
        _ = db.openDBConnection(false)
        prefs.syncIMActivatedState(dbServer: DBServer(_testDatasource: db))
        let state = prefs.keyboardState
        XCTAssertNotNil(state)
    }

    private func projectFileURL(_ relativePath: String) -> URL {
        // Prefer the copy bundled into the test target — on Xcode Cloud the source
        // checkout is absent at test runtime, so #filePath resolves to a missing path.
        if let bundled = Bundle(for: type(of: self)).resourceURL?.appendingPathComponent(relativePath),
           FileManager.default.fileExists(atPath: bundled.path) {
            return bundled
        }
        return URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // LimeTests
            .deletingLastPathComponent() // LimeIME-iOS
            .appendingPathComponent(relativePath)
    }
}
