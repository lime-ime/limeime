// IntentHandler.swift
// LimeIME-iOS
//
// Handles incoming files shared from Files / other apps (.lime, .cin, .limedb, .zip).
// Mirrors Android IntentHandler. Wire into AppDelegate / LimeSettingsApp.onOpenURL.
// Spec §3.3.

import Foundation

// MARK: - IntentHandler

@MainActor
final class IntentHandler {

    static let shared = IntentHandler()

    private let setupController: SetupImController

    private init() {
        setupController = SetupImController(progress: ProgressManager())
    }

    // MARK: - Handle incoming URL

    /// Route an incoming file URL to the appropriate import path.
    /// - Parameters:
    ///   - url: The file URL received from the system (share sheet / document picker).
    ///   - view: Optional SetupImView to receive error callbacks.
    func handle(url: URL, view: (any SetupImView)?) async {
        let ext = url.pathExtension.lowercased()
        // Sanitise: only accept names that pass the DB identifier allowlist.
        let rawName = url.deletingPathExtension().lastPathComponent
            .components(separatedBy: .init(charactersIn: "-_")).first ?? "custom"
        let tableName = DBServer.shared.isValidTableName(rawName) ? rawName : "custom"

        switch ext {
        case "limedb", "zip":
            let result = await setupController.importDBFile(url: url, tableName: tableName)
            if case .failure(let error) = result {
                view?.onError(error.localizedDescription)
            }
        case "lime", "cin":
            let result = await setupController.importTxtFile(url: url, tableName: tableName)
            if case .failure(let error) = result {
                view?.onError(error.localizedDescription)
            }
        default:
            view?.onError("不支援的檔案格式：.\(ext)")
        }
    }
}
