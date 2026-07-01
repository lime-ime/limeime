import CoreGraphics

struct KeyModel: Equatable, Identifiable {
    var id: String {
        let codeID = codes.map(String.init).joined(separator: ",")
        return "\(frame.minX),\(frame.minY),\(frame.width),\(frame.height):\(codeID):\(primaryLabel):\(secondaryLabel)"
    }

    let frame: CGRect
    let codes: [Int]
    let primaryLabel: String
    let secondaryLabel: String
    let isRepeatable: Bool
    let isModifier: Bool
    let hasPopup: Bool
    let isDualRow: Bool
    let isSpace: Bool
}
