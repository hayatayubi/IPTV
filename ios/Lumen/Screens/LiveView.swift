import Shared
import SwiftUI

/// Live TV: the playlist's categories, then a category's channels. Tapping a channel plays it
/// full screen, and swiping up/down there zaps through that category.
struct LiveView: View {
    @Environment(PlaylistStore.self) private var store
    @State private var search = ""
    @State private var playing: PlayerRoute?

    var body: some View {
        if let playlist = store.selected {
            NavigationStack {
                content(for: playlist)
                    .scrollContentBackground(.hidden)
                    .background(AmbientBackground())
                    .navigationTitle("Live TV")
                    .toolbar { playlistMenu }
                    .navigationDestination(for: CategoryRoute.self) { route in
                        ChannelListView(title: route.title, channels: route.channels)
                    }
            }
        } else {
            EmptyTab(title: "Live TV", message: "Add your playlist or Xtream login to see your channels.")
        }
    }

    @ViewBuilder
    private func content(for playlist: Playlist) -> some View {
        switch store.state(of: playlist) {
        case .idle, .loading:
            ProgressView("Loading channels…")
                .font(LumenFont.body)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .failed(let message):
            VStack(spacing: 16) {
                Text(message)
                    .font(LumenFont.body)
                    .foregroundStyle(LumenColors.textSecondary)
                    .multilineTextAlignment(.center)
                Button("Try again") { Task { await store.refresh(playlist) } }
                    .buttonStyle(GlassButtonStyle())
            }
            .padding(24)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .loaded(let loaded):
            List {
                if search.isEmpty {
                    ForEach(loaded.categories, id: \.name) { category in
                        NavigationLink(value: CategoryRoute(
                            title: category.title,
                            channels: loaded.channelsByCategory[category.name] ?? []
                        )) {
                            LabeledContent(category.title, value: "\(category.channelCount)")
                        }
                    }
                    if let others = loaded.channelsByCategory[LoadedLibrary.otherKey] {
                        NavigationLink(value: CategoryRoute(title: "Other channels", channels: others)) {
                            LabeledContent("Other channels", value: "\(others.count)")
                        }
                    }
                } else {
                    let matches = Array(loaded.library.channels.filter {
                        $0.title.localizedCaseInsensitiveContains(search)
                    }.prefix(200))
                    ChannelRows(channels: matches, playing: $playing)
                }
            }
            .font(LumenFont.body)
            .playerCover($playing)
            .searchable(text: $search, prompt: "Search channels")
            .refreshable { await store.refresh(playlist) }
        }
    }

    private var playlistMenu: some ToolbarContent {
        ToolbarItem(placement: .topBarTrailing) {
            // Only worth showing with more than one playlist.
            if store.playlists.count > 1 {
                Menu {
                    Picker("Playlist", selection: Binding(
                        get: { store.selected?.id },
                        set: { store.selectedID = $0 }
                    )) {
                        ForEach(store.playlists) { Text($0.name).tag(Optional($0.id)) }
                    }
                } label: {
                    Label(store.selected?.name ?? "Playlist", systemImage: "list.bullet")
                }
            }
        }
    }
}

/// Navigation value for a category's channel list. (NavigationLink(value:) needs Hashable;
/// Kotlin objects are compared by identity, which is fine here.)
struct CategoryRoute: Hashable {
    let title: String
    let channels: [LiveChannel]
}

struct ChannelListView: View {
    let title: String
    let channels: [LiveChannel]
    @State private var playing: PlayerRoute?

    var body: some View {
        List { ChannelRows(channels: channels, playing: $playing) }
            .font(LumenFont.body)
            .playerCover($playing)
            .scrollContentBackground(.hidden)
            .background(AmbientBackground())
            .navigationTitle(title)
    }
}

/// Channel rows; tapping one plays it full screen, zapping within [channels].
struct ChannelRows: View {
    let channels: [LiveChannel]
    @Binding var playing: PlayerRoute?

    var body: some View {
        ForEach(Array(channels.enumerated()), id: \.element.key) { index, channel in
            Button {
                playing = PlayerRoute(channels: channels, index: index)
            } label: {
                HStack(spacing: 14) {
                    ChannelLogo(url: channel.logoUrl, title: channel.title)
                    Text(channel.title)
                        .foregroundStyle(LumenColors.textPrimary)
                        .lineLimit(1)
                    Spacer()
                    if let quality = channel.quality {
                        QualityTag(text: quality)
                    }
                }
            }
        }
    }
}

extension View {
    /// Presents the full-screen player for [route] (set by tapping a channel row).
    func playerCover(_ route: Binding<PlayerRoute?>) -> some View {
        fullScreenCover(item: route) { route in
            LivePlayerView(channels: route.channels, startIndex: route.index)
        }
    }
}

struct PlayerRoute: Identifiable {
    let id = UUID()
    let channels: [LiveChannel]
    let index: Int
}

/// The provider's logo on a small glass tile, or the first letter when there's none.
struct ChannelLogo: View {
    let url: String?
    let title: String
    var size: CGFloat = 44

    var body: some View {
        ZStack {
            if let url, let parsed = URL(string: url) {
                AsyncImage(url: parsed) { image in
                    image.resizable().scaledToFit().padding(5)
                } placeholder: {
                    monogram
                }
            } else {
                monogram
            }
        }
        .frame(width: size * 16 / 9, height: size)
        .glass(cornerRadius: 10)
    }

    private var monogram: some View {
        Text(title.prefix(1).uppercased())
            .font(LumenFont.headline)
            .foregroundStyle(LumenColors.textSecondary)
    }
}

struct QualityTag: View {
    let text: String

    var body: some View {
        Text(text)
            .font(LumenFont.caption)
            .foregroundStyle(LumenColors.textSecondary)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .overlay(RoundedRectangle(cornerRadius: 4).strokeBorder(LumenColors.textTertiary, lineWidth: 1))
    }
}
