// ReverseLookupSettingsView.swift
// LimeIME-iOS
//
// Per-IM reverse lookup source pickers.
// Spec §8.4.1.

import SwiftUI

// MARK: - ReverseLookupSettingsView

struct ReverseLookupSettingsView: View {

    @EnvironmentObject private var manageImController: ManageImController
    @Environment(\.dismiss) private var dismiss

    private let prefs = LIMEPreferenceManager.shared
    @State private var lookupTargets: [LIMEPreferenceManager.ReverseLookupOption] = []
    @State private var lookupOptions = LIMEPreferenceManager.fallbackReverseLookupOptions
    @State private var selections: [String: String] = [:]

    var body: some View {
        Form {
            Section(header: Text("說明")) {
                Text("輸入字根無候選字時，以其他輸入法字根標注說明。")
                    .font(.footnote)
                    .foregroundColor(.secondary)
            }
            .setupMatchedSectionBlock()

            Section(header: Text("各輸入法反查來源")) {
                if lookupTargets.isEmpty {
                    Text("尚未啟用任何輸入法")
                        .foregroundColor(.secondary)
                } else {
                    ForEach(lookupTargets, id: \.value) { target in
                        lookupPicker(target)
                    }
                }
            }
            .setupMatchedSectionBlock()
        }
        .setupMatchedGroupedSurface()
        .constrainedDetailLayout("字根反查設定")
        .task { await loadLookupOptions() }
    }

    private func lookupPicker(_ target: LIMEPreferenceManager.ReverseLookupOption) -> some View {
        Picker(target.label, selection: binding(for: target.value)) {
            ForEach(lookupOptions, id: \.value) { option in
                Text(option.label).tag(option.value)
            }
        }
        .pickerStyle(.menu)
    }

    private func binding(for tableNick: String) -> Binding<String> {
        Binding(
            get: { selections[tableNick] ?? prefs.reverseLookup(for: tableNick) },
            set: { value in
                selections[tableNick] = value
                prefs.setReverseLookup(value, for: tableNick)
            }
        )
    }

    private func loadLookupOptions() async {
        let configs = await manageImController.loadIMList()
        let options = LIMEPreferenceManager.reverseLookupOptions(from: configs)
        lookupOptions = options
        lookupTargets = LIMEPreferenceManager.reverseLookupTargets(from: configs)
        selections = Dictionary(uniqueKeysWithValues: lookupTargets.map { target in
            (target.value, prefs.reverseLookup(for: target.value))
        })
    }
}
