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

// KeyboardPickerView.swift
// LimeIME-iOS
//
// Soft keyboard layout selection for a given IM.
// Spec §5.2.1.

import SwiftUI

// MARK: - KeyboardPickerView

struct KeyboardPickerView: View {

    let im: IMRow
    let onSave: (() -> Void)?

    @EnvironmentObject private var manageImController: ManageImController
    @Environment(\.presentationMode) private var presentationMode
    @State private var keyboards: [KeyboardConfig] = []
    @State private var selectedCode: String = ""

    init(im: IMRow, onSave: (() -> Void)? = nil) {
        self.im = im
        self.onSave = onSave
        // Seed from the already-loaded IMRow so the checkmark shows before the async task returns.
        _selectedCode = State(initialValue: im.keyboardId)
    }

    var body: some View {
        List {
            ForEach(keyboards, id: \.code) { kb in
                HStack {
                    Text(kb.desc)
                    Spacer()
                    if kb.code == selectedCode {
                        Image(systemName: "checkmark")
                            .foregroundColor(.accentColor)
                    }
                }
                .contentShape(Rectangle())
                .onTapGesture {
                    selectKeyboard(kb)
                }
            }
            .setupMatchedSectionBlock()
        }
        .setupMatchedGroupedSurface()
        .constrainedDetailLayout("選擇鍵盤佈局")
        .task { await loadKeyboards() }
    }

    // MARK: - Helpers

    private func loadKeyboards() async {
        let result = await manageImController.loadKeyboards(forIM: im.tableNick)
        keyboards = result.keyboards
        if !result.selected.isEmpty {
            selectedCode = result.selected
        }
    }

    private func selectKeyboard(_ kb: KeyboardConfig) {
        selectedCode = kb.code
        Task {
            await manageImController.setKeyboard(forIM: im.tableNick, keyboard: kb)
            // For phonetic IM, also update the phonetic_keyboard_type preference
            if im.tableNick == "phonetic" {
                LIMEPreferenceManager.shared.phoneticKeyboardType = kb.code
            }
            onSave?()
            presentationMode.wrappedValue.dismiss()
        }
    }
}
