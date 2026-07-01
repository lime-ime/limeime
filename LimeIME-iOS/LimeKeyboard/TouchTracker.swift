import CoreGraphics

struct TouchTracker: Equatable {
    let downKey: KeyModel?
    var currentKey: KeyModel?
    let isModifier: Bool
    var isSliding: Bool

    init(downKey: KeyModel?, currentKey: KeyModel? = nil, isSliding: Bool = false) {
        self.downKey = downKey
        self.currentKey = currentKey ?? downKey
        self.isModifier = downKey?.isModifier ?? false
        self.isSliding = isSliding
    }

    @discardableResult
    mutating func move(to point: CGPoint,
                       detector: KeyDetector,
                       hysteresis: CGFloat) -> KeyModel? {
        guard !isModifier, downKey?.isRepeatable != true else { return nil }
        let previousKey = currentKey
        guard let newKey = detector.keyAt(point, movingFrom: currentKey, hysteresis: hysteresis),
              newKey != currentKey else {
            return nil
        }
        currentKey = newKey
        isSliding = true
        return previousKey
    }
}
