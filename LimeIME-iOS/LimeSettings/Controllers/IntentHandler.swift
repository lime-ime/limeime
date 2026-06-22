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

    private init() {}

    // MARK: - Handle incoming URL

    /// Route an incoming file URL to the appropriate import path.
    /// External open-in/share URLs do not carry a user-selected destination IM.
    /// Copy the source into app-local temporary storage, then route to the
    /// import UI so the user chooses the target IM explicitly.
    /// - Parameters:
    ///   - url: The file URL received from the system (share sheet / document picker).
    ///   - view: Optional SetupImView to receive error callbacks.
    @MainActor
    func handle(url: URL, view: (any SetupImView)?) async {
        let ext = url.pathExtension.lowercased()

        switch ext {
        case "limedb", "zip", "lime", "cin":
            let importURL = FileManager.default.temporaryDirectory
                .appendingPathComponent("\(UUID().uuidString)_\(url.lastPathComponent)")
            do {
                try? FileManager.default.removeItem(at: importURL)
                try FileManager.default.copyItem(at: url, to: importURL)
                if let oldPendingURL = pendingLimeExternalImportURL {
                    try? FileManager.default.removeItem(at: oldPendingURL)
                }
                pendingLimeExternalImportURL = importURL
                NotificationCenter.default.post(name: .limeExternalImport, object: importURL)
            } catch {
                view?.onError("匯入失敗：\(error.localizedDescription)")
            }
        default:
            view?.onError("不支援的檔案格式：.\(ext)")
        }
    }
}
