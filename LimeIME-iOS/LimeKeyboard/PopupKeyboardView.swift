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

import UIKit

// Floating mini-keyboard that appears above a key when the user long-presses.
// Mirrors Android's MiniKeyboardPopup / PopupKeyboardView behaviour.

protocol PopupKeyboardViewDelegate: AnyObject {
    func popupKeyboardView(_ popup: PopupKeyboardView, didSelect keyDef: KeyDef)
    /// A key became highlighted (slide-over or tap-down) — `nil` clears it. Drives the key preview.
    func popupKeyboardView(_ popup: PopupKeyboardView, didHighlight keyDef: KeyDef?)
}

final class PopupKeyboardView: UIView {

    weak var delegate: PopupKeyboardViewDelegate?

    private let layout:   LimeKeyLayout
    private let palette:  KeyboardPalette
    private let baseKeySize: CGSize

    // Layout constants — sourced from LayoutMetrics.PopupKeyboard
    private let hPad:    CGFloat = LayoutMetrics.PopupKeyboard.hPad
    private let vPad:    CGFloat = LayoutMetrics.PopupKeyboard.vPad
    private let spacing: CGFloat = LayoutMetrics.PopupKeyboard.spacing

    // MARK: - Init

    init(layout: LimeKeyLayout, theme: Int = 0, baseKeySize: CGSize) {
        self.layout  = layout
        self.palette = KeyboardPalette.palettes[max(0, min(theme, KeyboardPalette.palettes.count - 1))]
        self.baseKeySize = baseKeySize
        super.init(frame: .zero)
        buildUI()
    }

    required init?(coder: NSCoder) { fatalError() }

    // MARK: - Build

    private func buildUI() {
        backgroundColor = palette.background
        layer.cornerRadius  = LayoutMetrics.PopupKeyboard.panelCornerRadius
        layer.masksToBounds = false
        layer.shadowColor   = LayoutMetrics.Shadow.color
        layer.shadowOpacity = LayoutMetrics.PopupKeyboard.panelShadowOpacity
        layer.shadowOffset  = CGSize(width: 0, height: LayoutMetrics.PopupKeyboard.panelShadowOffsetY)
        layer.shadowRadius  = LayoutMetrics.PopupKeyboard.panelShadowRadius

        var yOff: CGFloat = vPad
        for (ri, row) in layout.rows.enumerated() {
            var xOff: CGFloat = hPad
            for (ki, kd) in row.keys.enumerated() {
                let kw  = keyWidth(for: kd)
                let btn = makeKeyButton(kd, row: ri, col: ki)
                btn.frame = CGRect(x: xOff, y: yOff, width: kw, height: baseKeySize.height)
                addSubview(btn)
                xOff += kw + spacing
            }
            let isLast = ri == layout.rows.count - 1
            yOff += baseKeySize.height + (isLast ? 0 : spacing)
        }

        // Size the view to fit its content
        let totalW = layout.rows.map { contentWidth(of: $0) }.max() ?? 0
        let nRows  = CGFloat(layout.rows.count)
        let totalH = vPad + nRows * baseKeySize.height + max(0, nRows - 1) * spacing + vPad
        frame.size = CGSize(width: totalW, height: totalH)
    }

    private func keyWidth(for kd: KeyDef) -> CGFloat {
        let text = cleanLabel(kd.label.isEmpty ? kd.sublabel : kd.label)
        guard text.count > 1 else { return baseKeySize.width }
        let font = UIFont.systemFont(ofSize: LayoutMetrics.PopupKeyboard.keyFontSize)
        let w    = (text as NSString).size(withAttributes: [.font: font]).width
        return max(baseKeySize.width, ceil(w) + LayoutMetrics.PopupKeyboard.keyExtraWidth)
    }

    private func contentWidth(of row: KeyRow) -> CGFloat {
        let keysW = row.keys.reduce(0.0) { $0 + keyWidth(for: $1) }
        let gapW  = CGFloat(max(0, row.keys.count - 1)) * spacing
        return hPad + keysW + gapW + hPad
    }

    private func makeKeyButton(_ kd: KeyDef, row: Int, col: Int) -> UIButton {
        let btn = UIButton(type: .system)
        let text = cleanLabel(kd.label.isEmpty ? kd.sublabel : kd.label)
        btn.setTitle(text, for: .normal)
        btn.titleLabel?.font = UIFont.systemFont(ofSize: LayoutMetrics.PopupKeyboard.keyFontSize)
        btn.setTitleColor(palette.label, for: .normal)
        btn.backgroundColor = palette.normalKey
        btn.layer.cornerRadius  = LayoutMetrics.PopupKeyboard.keyCornerRadius
        btn.layer.masksToBounds = false
        btn.layer.shadowColor   = LayoutMetrics.Shadow.color
        btn.layer.shadowOpacity = LayoutMetrics.PopupKeyboard.keyShadowOpacity
        btn.layer.shadowOffset  = CGSize(width: 0, height: LayoutMetrics.PopupKeyboard.keyShadowOffsetY)
        btn.layer.shadowRadius  = LayoutMetrics.PopupKeyboard.keyShadowRadius

        // Tag encodes row/col so we can recover the KeyDef on tap
        btn.tag = row * 1000 + col
        btn.addTarget(self, action: #selector(keyTapped(_:)), for: .touchUpInside)

        // Highlight on press
        btn.addTarget(self, action: #selector(keyHighlight(_:)), for: .touchDown)
        btn.addTarget(self, action: #selector(keyUnhighlight(_:)), for: [.touchUpInside, .touchUpOutside, .touchCancel])
        return btn
    }

    // MARK: - Actions

    @objc private func keyTapped(_ sender: UIButton) {
        let ri = sender.tag / 1000
        let ki = sender.tag % 1000
        guard ri < layout.rows.count, ki < layout.rows[ri].keys.count else { return }
        delegate?.popupKeyboardView(self, didSelect: layout.rows[ri].keys[ki])
    }

    @objc private func keyHighlight(_ sender: UIButton) {
        sender.backgroundColor = palette.pressedKey
        delegate?.popupKeyboardView(self, didHighlight: keyDef(for: sender))
    }

    @objc private func keyUnhighlight(_ sender: UIButton) {
        sender.backgroundColor = palette.normalKey
        delegate?.popupKeyboardView(self, didHighlight: nil)
    }

    func key(at point: CGPoint, slideAllowance: CGFloat) -> KeyDef? {
        PopupKeyDetector(targets: keyHitTargets(), slideAllowance: slideAllowance).key(at: point)
    }

    /// True when the popup has a single alternate — callers suppress the key preview for these.
    var isSingleKey: Bool { layout.rows.reduce(0) { $0 + $1.keys.count } <= 1 }
    var normalKeyHeight: CGFloat { baseKeySize.height }

    /// Frame of the button matching `keyDef`, in `target`'s coordinate space (for the key preview).
    func keyRect(for keyDef: KeyDef, in target: UIView) -> CGRect? {
        for button in popupKeyButtons() where self.keyDef(for: button) == keyDef {
            return button.convert(button.bounds, to: target)
        }
        return nil
    }

    func setHighlightedKey(_ highlightedKey: KeyDef?) {
        for button in popupKeyButtons() {
            guard let buttonKey = keyDef(for: button) else { continue }
            button.backgroundColor = buttonKey == highlightedKey ? palette.pressedKey : palette.normalKey
        }
    }

    // MARK: - Helpers

    private func keyHitTargets() -> [PopupKeyHitTarget] {
        popupKeyButtons().compactMap { button in
            guard let keyDef = keyDef(for: button) else { return nil }
            return PopupKeyHitTarget(keyDef: keyDef, frame: button.frame)
        }
    }

    private func popupKeyButtons() -> [UIButton] {
        subviews.compactMap { $0 as? UIButton }
    }

    private func keyDef(for button: UIButton) -> KeyDef? {
        let ri = button.tag / 1000
        let ki = button.tag % 1000
        guard ri < layout.rows.count, ki < layout.rows[ri].keys.count else { return nil }
        return layout.rows[ri].keys[ki]
    }

    /// Strip Android XML escape prefixes: \' → ', \? → ?, \@ → @, \\ → \
    private func cleanLabel(_ label: String) -> String {
        guard label.hasPrefix("\\"), label.count > 1 else { return label }
        let rest = String(label.dropFirst())
        return rest == "\\" ? "\\" : rest
    }
}

struct PopupKeyHitTarget: Equatable {
    let keyDef: KeyDef
    let frame: CGRect
}

struct PopupKeyDetector {
    let targets: [PopupKeyHitTarget]
    let slideAllowance: CGFloat

    func key(at point: CGPoint) -> KeyDef? {
        if let direct = targets.first(where: { $0.frame.standardized.contains(point) }) {
            return direct.keyDef
        }

        var nearest: PopupKeyHitTarget?
        var nearestDistance = CGFloat.greatestFiniteMagnitude
        for target in targets {
            let frame = target.frame.standardized
            let distance = squareDistance(from: point, to: frame)
            let allowance = point.y < frame.minY
                ? slideAllowance * slideAllowance * 2
                : slideAllowance * slideAllowance
            guard distance <= allowance, distance < nearestDistance else { continue }
            nearest = target
            nearestDistance = distance
        }
        return nearest?.keyDef
    }

    private func squareDistance(from point: CGPoint, to rect: CGRect) -> CGFloat {
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
