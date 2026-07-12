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

// BaseController.swift
// LimeIME-iOS
//
// Base class for all controllers.
// Mirrors Android BaseController — provides @MainActor dispatch helpers
// for error and progress callbacks to the view layer.

import Foundation

// MARK: - BaseController

@MainActor
class BaseController: ObservableObject {

    // MARK: - Dependencies

    let dbServer: DBServer
    let prefs: LIMEPreferenceManager

    // MARK: - Init

    init(dbServer: DBServer = .shared, prefs: LIMEPreferenceManager = .shared) {
        self.dbServer = dbServer
        self.prefs = prefs
    }

    // MARK: - Error dispatch

    /// Deliver an error message to the view on the main actor.
    func onError(_ message: String, to view: ViewUpdateListener?) {
        view?.onError(message)
    }

    /// Deliver a progress update to the view on the main actor.
    func onProgress(_ percentage: Int, status: String, to view: ViewUpdateListener?) {
        view?.onProgress(percentage, status: status)
    }

    // MARK: - Background task helpers

    /// Run a throwing closure on a background task; deliver errors to the view on main actor.
    nonisolated func runBackground(
        view: (any ViewUpdateListener)?,
        operation: @escaping @Sendable () throws -> Void
    ) {
        Task.detached(priority: .userInitiated) {
            do {
                try operation()
            } catch {
                let message = error.localizedDescription
                await MainActor.run {
                    view?.onError(message)
                }
            }
        }
    }
}
