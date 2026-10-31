import Shared
import SwiftUI

/// Live TV: the playlist's categories, then a category's channels. Tapping a channel plays it
/// full screen, and swiping up/down there zaps through that category.
struct LiveView: View {
    @Environment(PlaylistStore.self) private var store
    @State private var search = ""
    @State private var playing: PlayerRoute?
    @State private var editingFavourites = false

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
                    let favourites = store.favourites(in: playlist)
                    if !favourites.isEmpty {
                        Section {
                            FavouritesRow(channels: favourites, playing: $playing)
                        } header: {
                            HStack {
                                Text("Favourites")
                                Spacer()
                                Button("Edit") { editingFavourites = true }
                                    .textCase(nil)
                            }
                        }
                    }
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
            .sheet(isPresented: $editingFavourites) { FavouritesEditView(playlist: playlist) }
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
    @Environment(PlaylistStore.self) private var store

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
                    if isFavourite(channel) {
                        Image(systemName: "star.fill")
                            .foregroundStyle(LumenColors.accent)
                            .accessibilityLabel("Favourite")
                    }
                }
            }
            // Hold a channel (or swipe right) to add or remove it from favourites.
            .contextMenu { favouriteButton(channel) }
            .swipeActions(edge: .leading) {
                favouriteButton(channel).tint(LumenColors.accent)
            }
        }
    }
}

private extension ChannelRows {
    func isFavourite(_ channel: LiveChannel) -> Bool {
        store.selected.map { store.isFavourite(channel, in: $0) } ?? false
    }

    @ViewBuilder
    func favouriteButton(_ channel: LiveChannel) -> some View {
        if let playlist = store.selected {
            if store.isFavourite(channel, in: playlist) {
                Button("Remove from favourites", systemImage: "star.slash") {
                    store.toggleFavourite(channel, in: playlist)
                }
            } else {
                Button("Add to favourites", systemImage: "star") {
                    store.toggleFavourite(channel, in: playlist)
                }
            }
        }
    }
}

/// Favourites as big 16:9 cards in a row you scroll sideways. Tap plays (zapping through
/// favourites); hold for Remove.
struct FavouritesRow: View {
    let channels: [LiveChannel]
    @Binding var playing: PlayerRoute?
    @Environment(PlaylistStore.self) private var store

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(alignment: .top, spacing: 14) {
                ForEach(Array(channels.enumerated()), id: \.element.key) { index, channel in
                    Button {
                        playing = PlayerRoute(channels: channels, index: index)
                    } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            ChannelArt(logoUrl: channel.logoUrl, title: channel.title, cornerRadius: 14)
                                .frame(width: 200, height: 112.5)
                            Text(channel.title)
                                .font(LumenFont.caption)
                                .foregroundStyle(LumenColors.textSecondary)
                                .lineLimit(1)
                                .frame(width: 200, alignment: .leading)
                        }
                    }
                    .buttonStyle(.plain)
                    .contextMenu {
                        if let playlist = store.selected {
                            Button("Remove from favourites", systemImage: "star.slash", role: .destructive) {
                                store.toggleFavourite(channel, in: playlist)
                            }
                        }
                    }
                }
            }
            .padding(.vertical, 4)
        }
        .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
        .listRowBackground(Color.clear)
    }
}

/// Reorder (drag the handles) or remove favourites, the iOS way.
struct FavouritesEditView: View {
    let playlist: Playlist
    @Environment(PlaylistStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var channels: [LiveChannel] = []

    var body: some View {
        NavigationStack {
            List {
                ForEach(channels, id: \.key) { channel in
                    HStack(spacing: 14) {
                        ChannelLogo(url: channel.logoUrl, title: channel.title, size: 36)
                        Text(channel.title).lineLimit(1)
                    }
                }
                .onMove { from, to in
                    channels.move(fromOffsets: from, toOffset: to)
                    store.setFavourites(channels, in: playlist)
                }
                .onDelete { offsets in
                    channels.remove(atOffsets: offsets)
                    store.setFavourites(channels, in: playlist)
                }
            }
            .font(LumenFont.body)
            // Always in edit mode: drag handles and delete buttons show straight away.
            .environment(\.editMode, .constant(.active))
            .scrollContentBackground(.hidden)
            .background(AmbientBackground())
            .navigationTitle("Favourites")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .onAppear { channels = store.favourites(in: playlist) }
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

/// A small 16:9 channel picture for rows and the player bar.
struct ChannelLogo: View {
    let url: String?
    let title: String
    var size: CGFloat = 44

    var body: some View {
        ChannelArt(logoUrl: url, title: title, cornerRadius: 8)
            .frame(width: size * 16 / 9, height: size)
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
