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
import UIKit

final class FABackupRestoreFlowUITest: LimeUITestSupport {

    @MainActor
    func testPhase1InstallTwoIMsAndBackup() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-limeUITestBackupColdToDocuments", "1"]
        app.launch()

        tapTab("輸入法", in: app)
        var needsInstallScreen = false
        let needsDayi = !imRow(containing: "大易", in: app).waitForExistence(timeout: 3)
        let needsPhonetic = !imRow(containing: "注音", in: app).exists
        needsInstallScreen = needsDayi || needsPhonetic

        if needsInstallScreen {
            openInstallScreen(in: app)
            if needsDayi {
                try installVariant(id: "dayi", search: "Dayi",
                                   name: "OpenVanilla 大易字根", in: app)
            }
            if needsPhonetic {
                try installVariant(id: "phonetic", search: "Phonetic",
                                   name: "OpenVanilla 注音字根", in: app)
            }
            returnToIMList(in: app)
        }

        XCTAssertTrue(imRow(containing: "大易", in: app).waitForExistence(timeout: 20),
                      "大易 should appear in the installed IM list")
        XCTAssertTrue(imRow(containing: "注音", in: app).waitForExistence(timeout: 20),
                      "注音 should appear in the installed IM list")

        tapTab("資料庫", in: app)
        XCTAssertTrue(app.buttons["備份資料庫"].waitForExistence(timeout: 10),
                      "Backup button did not appear")
        app.buttons["備份資料庫"].tap()
        XCTAssertTrue(app.staticTexts["backup_done"].waitForExistence(timeout: 120),
                      "Cold backup zip was not surfaced as Documents/lime_backup.zip")
    }

    @MainActor
    func testPhase2RestoreAndVerify() throws {
        let app = XCUIApplication()
        app.launchArguments += [
            "-limeUITestRestoreFromDocuments", "lime_backup.zip",
            "-LimeUITestKeyboardList", "phonetic",
        ]
        app.launch()

        XCTAssertTrue(app.staticTexts["restore_done"].waitForExistence(timeout: 120),
                      "Restore did not complete from Documents/lime_backup.zip")

        tapTab("輸入法", in: app)
        XCTAssertTrue(imRow(containing: "大易", in: app).waitForExistence(timeout: 20),
                      "大易 should reappear in the IM list after restore")
        XCTAssertTrue(imRow(containing: "注音", in: app).waitForExistence(timeout: 20),
                      "注音 should reappear in the IM list after restore")

        if !bestEffortTypingCheckProducedCandidate() {
            XCTAssertTrue(restoredRowsAreNonEmpty(in: app),
                          "Typing check did not produce a candidate and restored row counts were missing/empty")
        }
    }

    private func tapTab(_ label: String, in app: XCUIApplication) {
        let tab = app.tabBars.buttons[label]
        if tab.waitForExistence(timeout: 5) {
            tab.tap()
            return
        }
        app.buttons[label].tap()
    }

    private func openInstallScreen(in app: XCUIApplication) {
        let fab = app.buttons["im_install_fab"]
        if fab.waitForExistence(timeout: 2) {
            fab.tap()
        } else if app.buttons["plus"].waitForExistence(timeout: 2) {
            app.buttons["plus"].tap()
        } else {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.90, dy: 0.88)).tap()
        }
        XCTAssertTrue(app.staticTexts["下載 / 匯入輸入法"].waitForExistence(timeout: 10),
                      "Install screen did not open")
    }

    private func imRow(containing text: String, in app: XCUIApplication) -> XCUIElement {
        app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
    }

    private func installVariant(id: String, search: String, name: String,
                                in app: XCUIApplication) throws {
        let searchField = app.textFields["搜尋輸入法"]
        XCTAssertTrue(searchField.waitForExistence(timeout: 10), "Install search field missing")
        searchField.tap()
        if let value = searchField.value as? String, !value.isEmpty, value != "搜尋輸入法" {
            searchField.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: value.count))
        }
        searchField.typeText(search)

        let idButton = app.buttons["install_variant_\(id)"]
        if idButton.waitForExistence(timeout: 3) {
            idButton.tap()
        } else {
            let row = app.cells.containing(.staticText, identifier: name).firstMatch
            XCTAssertTrue(row.waitForExistence(timeout: 20), "Install row not found for \(name)")
            let installButton = row.buttons["安裝"]
            if installButton.waitForExistence(timeout: 5) {
                installButton.tap()
            } else {
                row.buttons.element(boundBy: 0).tap()
            }
        }

        XCTAssertTrue(waitUntil(timeout: 300) {
            !app.staticTexts[name].exists
        }, "\(name) did not finish installing")
    }

    private func returnToIMList(in app: XCUIApplication) {
        let back = app.buttons["detail_back_button"]
        if back.waitForExistence(timeout: 2) {
            back.tap()
        } else if app.buttons["Back"].waitForExistence(timeout: 2) {
            app.buttons["Back"].tap()
        } else {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.06, dy: 0.08)).tap()
        }
    }

    private func bestEffortTypingCheckProducedCandidate() -> Bool {
        let safari = XCUIApplication(bundleIdentifier: "com.apple.mobilesafari")
        do {
            _ = try focusSafariAddressField(in: safari)
            try switchToLimeIME(in: safari)
            keyCoordinate(in: safari, labels: ["s", "S"], fallback: CGVector(dx: 0.22, dy: 0.78)).tap()
            Thread.sleep(forTimeInterval: 1.0)
            let candidatePredicate = NSPredicate(format: "identifier CONTAINS 'candidate' OR label == '是'")
            return safari.descendants(matching: .any).matching(candidatePredicate).firstMatch.exists
        } catch {
            return false
        }
    }

    private func restoredRowsAreNonEmpty(in app: XCUIApplication) -> Bool {
        let counts = app.staticTexts["restore_table_counts"]
        guard counts.waitForExistence(timeout: 5) else { return false }
        return positiveCount("dayi", in: counts.label) && positiveCount("phonetic", in: counts.label)
    }

    private func positiveCount(_ key: String, in text: String) -> Bool {
        guard let range = text.range(of: "\(key)=") else { return false }
        let digits = text[range.upperBound...].prefix { $0.isNumber }
        return (Int(digits) ?? 0) > 0
    }

    private func waitUntil(timeout: TimeInterval, condition: () -> Bool) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() { return true }
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        }
        return condition()
    }
}
