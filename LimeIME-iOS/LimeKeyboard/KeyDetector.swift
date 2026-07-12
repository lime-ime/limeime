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

import CoreGraphics

struct KeyDetector {
    let keys: [KeyModel]
    let proximityThreshold: CGFloat
    let defaultHysteresis: CGFloat

    init(keys: [KeyModel],
         proximityThreshold: CGFloat? = nil,
         defaultHysteresis: CGFloat? = nil) {
        let fallbackKeyWidth = keys.first(where: { $0.frame.width > 0 })?.frame.width ?? 44
        self.keys = keys
        self.proximityThreshold = proximityThreshold ?? fallbackKeyWidth
        self.defaultHysteresis = defaultHysteresis ?? fallbackKeyWidth * 0.25
    }

    func keyAt(_ point: CGPoint) -> KeyModel? {
        if let directHit = keys.first(where: { $0.frame.standardized.contains(point) }) {
            return directHit
        }

        let thresholdSquared = proximityThreshold * proximityThreshold
        guard let nearest = keys.min(by: {
            distanceSquared(from: point, toCenterOf: $0.frame) < distanceSquared(from: point, toCenterOf: $1.frame)
        }) else {
            return nil
        }

        return distanceSquared(from: point, toCenterOf: nearest.frame) <= thresholdSquared ? nearest : nil
    }

    func keyAt(_ point: CGPoint, movingFrom current: KeyModel?) -> KeyModel? {
        keyAt(point, movingFrom: current, hysteresis: defaultHysteresis)
    }

    func keyAt(_ point: CGPoint, movingFrom current: KeyModel?, hysteresis: CGFloat) -> KeyModel? {
        if let current,
           squareDistanceToKeyEdge(from: point, frame: current.frame) <= hysteresis * hysteresis {
            return current
        }

        return keyAt(point)
    }

    private func distanceSquared(from point: CGPoint, toCenterOf frame: CGRect) -> CGFloat {
        let rect = frame.standardized
        let dx = point.x - rect.midX
        let dy = point.y - rect.midY
        return dx * dx + dy * dy
    }

    private func squareDistanceToKeyEdge(from point: CGPoint, frame: CGRect) -> CGFloat {
        let rect = frame.standardized
        let dx: CGFloat
        if point.x < rect.minX {
            dx = rect.minX - point.x
        } else if point.x > rect.maxX {
            dx = point.x - rect.maxX
        } else {
            dx = 0
        }

        let dy: CGFloat
        if point.y < rect.minY {
            dy = rect.minY - point.y
        } else if point.y > rect.maxY {
            dy = point.y - rect.maxY
        } else {
            dy = 0
        }

        return dx * dx + dy * dy
    }
}
