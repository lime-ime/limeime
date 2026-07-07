import Foundation

/// An input method configuration row from the `im` table in lime.db.
/// Mirrors Android's ImConfig.java.
struct ImConfig: Codable {
    let id: Int64
    let imName: String
    let tableNick: String
    let label: String
    /// Full name from the `title="name"` config entry (mirrors Android LIME.IM_FULL_NAME / sidebar desc).
    let fullName: String
    let keyboardId: String
    let keyboardLandscapeId: String
    var enabled: Bool
    var sortOrder: Int
}
