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
        guard !isModifier, downKey?.isRepeatable != true, downKey?.isSpace != true else { return nil }
        let previousKey = currentKey
        guard let newKey = detector.keyAt(point, movingFrom: currentKey, hysteresis: hysteresis),
              newKey != currentKey,
              Self.canFlint(to: newKey) else {
            return nil
        }
        currentKey = newKey
        isSliding = true
        return previousKey
    }

    static func shouldCancelRepeat<S: Sequence>(trackers: S) -> Bool where S.Element == TouchTracker {
        var count = 0
        for tracker in trackers {
            guard let key = tracker.currentKey ?? tracker.downKey,
                  !key.isModifier || key.isRepeatable else {
                continue
            }
            count += 1
            if count >= 2 { return true }
        }
        return false
    }

    private static func canFlint(to key: KeyModel) -> Bool {
        !key.isModifier
            && !key.isRepeatable
            && !key.hasPopup
            && !key.isDualRow
            && !key.isSpace
    }
}

enum KeyboardSwipeCommand {
    case left
    case right
    case none
}

struct KeyboardSwipeClassifier {
    static func classify(delta: CGSize,
                         velocity: CGVector,
                         endingVelocity: CGVector,
                         bounds: CGSize,
                         velocityThreshold: CGFloat) -> KeyboardSwipeCommand {
        let absX = abs(velocity.dx)
        let absY = abs(velocity.dy)
        let travelX = bounds.width / 2

        if velocity.dx > velocityThreshold,
           absY < absX,
           delta.width > travelX,
           endingVelocity.dx >= velocity.dx / 4 {
            return .right
        }

        if velocity.dx < -velocityThreshold,
           absY < absX,
           delta.width < -travelX,
           endingVelocity.dx <= velocity.dx / 4 {
            return .left
        }

        return .none
    }
}
