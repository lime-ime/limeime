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

// EditRecordView.swift
// LimeIME-iOS
//
// Sheet for editing or deleting a mapping record.
// Spec §6.1.2.

import SwiftUI

// MARK: - EditRecordView

struct EditRecordView: View {

    let tableName: String
    let record: LimeRecord

    @EnvironmentObject private var manageImController: ManageImController
    @Environment(\.presentationMode) private var presentationMode
    @State private var code: String
    @State private var word: String
    @State private var score: Int
    @State private var errorMessage: String = ""
    @State private var showDeleteConfirm = false

    init(tableName: String, record: LimeRecord) {
        self.tableName = tableName
        self.record = record
        _code = State(initialValue: record.code)
        _word = State(initialValue: record.word)
        _score = State(initialValue: record.score)
    }

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("編輯資料列")) {
                    TextField("字根", text: $code)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                    TextField("文字", text: $word)
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
                        saveRecord()
                    }
                    .disabled(code.isEmpty || word.isEmpty)
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
                Button("刪除", role: .destructive) { deleteRecord() }
                Button("取消", role: .cancel) {}
            } message: {
                Text("確定要刪除「\(record.word)」(\(record.code))？")
            }
        }
    }

    private func saveRecord() {
        Task {
            let result = await manageImController.updateRecord(
                table: tableName, id: record.id, code: code, word: word, score: score)
            switch result {
            case .success:
                presentationMode.wrappedValue.dismiss()
            case .failure(let error):
                errorMessage = error.localizedDescription
            }
        }
    }

    private func deleteRecord() {
        Task {
            _ = await manageImController.deleteRecord(table: tableName, id: record.id)
            presentationMode.wrappedValue.dismiss()
        }
    }
}
