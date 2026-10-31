import SwiftUI

struct SettingsView: View {
    @Environment(PlaylistStore.self) private var store
    @State private var addingPlaylist = false

    var body: some View {
        NavigationStack {
            List {
                Section("Playlists") {
                    ForEach(store.playlists) { playlist in
                        PlaylistRow(playlist: playlist)
                            .swipeActions {
                                Button("Remove", role: .destructive) { store.remove(playlist) }
                            }
                            .contextMenu {
                                Button("Refresh", systemImage: "arrow.clockwise") {
                                    Task { await store.refresh(playlist) }
                                }
                                Button("Remove", systemImage: "trash", role: .destructive) { store.remove(playlist) }
                            }
                    }
                    Button("Add playlist", systemImage: "plus") { addingPlaylist = true }
                }
                Section("Check this device") {
                    NavigationLink("Test video playback") { TestPlayerView() }
                }
                Section("About") {
                    LabeledContent("Version", value: Bundle.main.version)
                    Text("Lumen is a player for your own TV subscription. It doesn't provide any channels or content.")
                        .foregroundStyle(LumenColors.textSecondary)
                    NavigationLink("Licences") { LicencesView() }
                }
            }
            .font(LumenFont.body)
            .scrollContentBackground(.hidden)
            .background(AmbientBackground())
            .navigationTitle("Settings")
            .sheet(isPresented: $addingPlaylist) { AddPlaylistView() }
        }
    }
}

private struct PlaylistRow: View {
    @Environment(PlaylistStore.self) private var store
    let playlist: Playlist

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(playlist.name).foregroundStyle(LumenColors.textPrimary)
            Text(status)
                .font(LumenFont.caption)
                .foregroundStyle(LumenColors.textSecondary)
        }
    }

    private var status: String {
        let kind = playlist.kind == .xtream ? "Xtream" : "M3U"
        switch store.state(of: playlist) {
        case .idle, .loading: return "\(kind) · \(playlist.host) · loading…"
        case .loaded(let loaded): return "\(kind) · \(playlist.host) · \(loaded.library.channels.count) channels"
        case .failed(let message): return message
        }
    }
}

struct LicencesView: View {
    var body: some View {
        List {
            Section("Inter") {
                Text("Typeface by Rasmus Andersson, under the SIL Open Font License 1.1.")
            }
        }
        .font(LumenFont.body)
        .scrollContentBackground(.hidden)
        .background(AmbientBackground())
        .navigationTitle("Licences")
    }
}

extension Bundle {
    /// "0.1.0 (1)".
    var version: String {
        let short = infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let build = infoDictionary?["CFBundleVersion"] as? String ?? "?"
        return "\(short) (\(build))"
    }
}
