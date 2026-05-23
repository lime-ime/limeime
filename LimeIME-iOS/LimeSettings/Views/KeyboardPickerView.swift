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
