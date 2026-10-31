import SwiftUI

/// The Add playlist form, laid out like the TV's. Nothing is saved yet: the import code comes
/// from the shared Kotlin module in a later phase.
struct AddPlaylistView: View {
    enum Kind: String, CaseIterable, Identifiable {
        case xtream = "Xtream login"
        case m3u = "M3U link"
        var id: Self { self }
    }

    @Environment(\.dismiss) private var dismiss
    @State private var kind = Kind.xtream
    @State private var name = ""
    @State private var server = ""
    @State private var username = ""
    @State private var password = ""
    @State private var link = ""

    var body: some View {
        NavigationStack {
            Form {
                Picker("Type", selection: $kind) {
                    ForEach(Kind.allCases) { Text($0.rawValue).tag($0) }
                }
                .pickerStyle(.segmented)
                .listRowBackground(Color.clear)

                Section {
                    TextField("Name (optional)", text: $name)
                    switch kind {
                    case .xtream:
                        TextField("Server, like http://example.com:8080", text: $server)
                            .keyboardType(.URL)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        TextField("Username", text: $username)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        SecureField("Password", text: $password)
                    case .m3u:
                        TextField("Playlist link", text: $link)
                            .keyboardType(.URL)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    }
                } footer: {
                    Text("Lumen doesn't provide any channels or content. Use the login or link from your own TV provider. It stays on this device.")
                }

                Section {
                    Text("Saving playlists comes in the next update of this test version.")
                        .foregroundStyle(LumenColors.textSecondary)
                }
            }
            .font(LumenFont.body)
            .scrollContentBackground(.hidden)
            .background(AmbientBackground())
            .navigationTitle("Add playlist")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") {}
                        .disabled(true)
                }
            }
        }
    }
}
