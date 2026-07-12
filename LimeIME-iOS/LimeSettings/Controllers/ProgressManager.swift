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

// ProgressManager.swift
// LimeIME-iOS
//
// Observable progress overlay state.
// Mirrors Android's ProgressDialogManager.
// All mutations must occur on MainActor.

import Foundation
import Combine

// MARK: - ProgressManager

@MainActor
final class ProgressManager: ObservableObject {

    @Published var isVisible: Bool = false
    @Published var status: String = ""
    @Published var percent: Int = 0

    // MARK: - Public API

    func show(status: String = "", percent: Int = 0) {
        self.status = status
        self.percent = percent
        self.isVisible = true
    }

    func update(status: String, percent: Int = -1) {
        self.status = status
        if percent >= 0 {
            self.percent = percent
        }
    }

    func dismiss() {
        self.isVisible = false
        self.status = ""
        self.percent = 0
    }
}
