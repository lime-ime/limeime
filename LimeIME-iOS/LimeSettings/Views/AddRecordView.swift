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

// AddRecordView.swift
// LimeIME-iOS
//
// Sheet for adding a new mapping record.
// Spec §6.1.1.

import SwiftUI

// MARK: - AddRecordView

struct AddRecordView: View {

    let tableName: String

    @EnvironmentObject private var manageImController: ManageImController
    @Environment(\.presentationMode) private var presentationMode
    @State private var code: String = ""
    @State private var word: String = ""
    @State private var score: Int = 0
    @State private var errorMessage: String = ""

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("新增資料列")) {
                    TextField("字根 (code)", text: $code)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                    TextField("文字 (word)", text: $word)
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
                        addRecord()
                    }
                    .disabled(code.isEmpty || word.isEmpty)
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

    private func addRecord() {
        Task {
            let result = await manageImController.addRecord(
                table: tableName, code: code, word: word, score: score)
            switch result {
            case .success:
                presentationMode.wrappedValue.dismiss()
            case .failure(let error):
                errorMessage = error.localizedDescription
            }
        }
    }
}

struct ScoreInputRow: View {
    @Binding var score: Int

    var title: String = "分數"
    var range: ClosedRange<Int> = 0...9999

    var body: some View {
        HStack {
            Text(title)
            Spacer()
            Button {
                score = max(range.lowerBound, score - 1)
            } label: {
                Image(systemName: "minus.circle")
            }
            .buttonStyle(.plain)
            .disabled(score <= range.lowerBound)

            TextField("", value: $score, format: .number)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.center)
                .frame(width: SettingsMetrics.scoreFieldWidth)
                .textFieldStyle(.roundedBorder)
                .onChange(of: score) { newValue in
                    score = min(max(newValue, range.lowerBound), range.upperBound)
                }

            Button {
                score = min(range.upperBound, score + 1)
            } label: {
                Image(systemName: "plus.circle")
            }
            .buttonStyle(.plain)
            .disabled(score >= range.upperBound)
        }
    }
}
