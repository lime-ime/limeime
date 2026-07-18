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

// EditRelatedView.swift
// LimeIME-iOS
//
// Sheet for editing or deleting a related phrase.
// Spec §6.2.2.

import SwiftUI

// MARK: - EditRelatedView

struct EditRelatedView: View {

    let phrase: Related

    @EnvironmentObject private var manageRelatedController: ManageRelatedController
    @Environment(\.presentationMode) private var presentationMode
    @State private var parentWord: String
    @State private var childWord: String
    @State private var score: Int
    @State private var errorMessage: String = ""
    @State private var showDeleteConfirm = false

    init(phrase: Related) {
        self.phrase = phrase
        _parentWord = State(initialValue: phrase.parentWord)
        _childWord = State(initialValue: phrase.childWord)
        _score = State(initialValue: phrase.score)
    }

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("編輯資料列")) {
                    TextField("首字（一個中文字）", text: $parentWord)
                        .disableAutocorrection(true)
                    TextField("關聯字", text: $childWord)
                        .disableAutocorrection(true)
                    ScoreInputRow(score: $score)
                }

                if !errorMessage.isEmpty {
                    Section {
                        Text(errorMessage)
                            .foregroundColor(SettingsTheme.destructive)
                            .font(.footnote)
                    }
                }

                Section {
                    Button("儲存") {
                        savePhrase()
                    }
                    .disabled(parentWord.isEmpty || childWord.isEmpty)
                }

                Section {
                    Button("刪除", role: .destructive) {
                        showDeleteConfirm = true
                    }
                }
            }
            .navigationTitle("編輯資料列")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
            .alert("確認刪除", isPresented: $showDeleteConfirm) {
                Button("刪除", role: .destructive) { deletePhrase() }
                Button("取消", role: .cancel) {}
            } message: {
                Text("確定要刪除「\(phrase.parentWord) → \(phrase.childWord)」？")
            }
        }
    }

    private func savePhrase() {
        Task {
            let result = await manageRelatedController.updateRelated(
                id: phrase.id, parentWord: parentWord, childWord: childWord, score: score)
            switch result {
            case .success:
                presentationMode.wrappedValue.dismiss()
            case .failure(let error):
                errorMessage = error.localizedDescription
            }
        }
    }

    private func deletePhrase() {
        Task {
            _ = await manageRelatedController.deleteRelated(id: phrase.id)
            presentationMode.wrappedValue.dismiss()
        }
    }
}
