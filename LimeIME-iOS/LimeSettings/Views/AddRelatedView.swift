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

// AddRelatedView.swift
// LimeIME-iOS
//
// Sheet for adding a new related phrase.
// Spec §6.2.1.

import SwiftUI

// MARK: - AddRelatedView

struct AddRelatedView: View {

    @EnvironmentObject private var manageRelatedController: ManageRelatedController
    @Environment(\.presentationMode) private var presentationMode
    @State private var parentWord: String = ""
    @State private var childWord: String = ""
    @State private var score: Int = 0
    @State private var errorMessage: String = ""

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("新增資料列")) {
                    TextField("首字（一個中文字）", text: $parentWord)
                        .disableAutocorrection(true)
                    TextField("關聯字 (related)", text: $childWord)
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
                    Button("確認新增") {
                        addPhrase()
                    }
                    .disabled(parentWord.isEmpty || childWord.isEmpty)
                }
            }
            .navigationTitle("新增資料列")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
        }
    }

    private func addPhrase() {
        Task {
            let result = await manageRelatedController.addRelated(
                parentWord: parentWord, childWord: childWord, score: score)
            switch result {
            case .success:
                presentationMode.wrappedValue.dismiss()
            case .failure(let error):
                errorMessage = error.localizedDescription
            }
        }
    }
}
