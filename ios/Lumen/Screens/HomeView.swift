import Shared
import SwiftUI

/// The Live TV tab's first screen, as on the TV: the chosen favourite as a big hero over a wash
/// of its colour, then the favourites as 16:9 tiles (2 across on iPhone, 4 on iPad).
/// Tap a tile to watch (zapping through favourites); hold it to move or remove it.
/// "All channels" opens every category.
struct HomeView: View {
    @Environment(PlaylistStore.self) private var store
    @Environment(\.lumenWide) private var wide
    @State private var playing: PlayerRoute?
    /// The favourite shown in the hero: the last one watched (the TV uses the focused card;
    /// a touch screen has no focus, so it follows what you tap).
    @AppStorage("homeHeroKey") private var heroKey = ""
    @State private var editingFavourites = false

    var body: some View {
        NavigationStack {
            Group {
                if let playlist = store.selected {
                    content(playlist)
                } else {
                    EmptyTab(title: "Live TV", message: "Add your playlist or Xtream login to see your channels.")
                }
            }
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: HomeRoute.self) { _ in LiveChannelsView() }
        }
        .playerCover($playing)
    }

    @ViewBuilder
    private func content(_ playlist: Playlist) -> some View {
        switch store.state(of: playlist) {
        case .idle, .loading:
            StatusMessage(text: "Loading channels…", loading: true)
        case .failed(let message):
            StatusMessage(text: message) { Task { await store.refresh(playlist) } }
        case .loaded(let loaded):
            let favourites = store.favourites(in: playlist)
            let hero = favourites.first { $0.key == heroKey } ?? favourites.first
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    if !wide { phoneHeader }
                    if let hero {
                        heroView(hero, loaded: loaded, favourites: favourites)
                        favouritesGrid(favourites, hero: hero, playlist: playlist)
                    } else {
                        noFavourites
                    }
                }
                .lumenGutter(wide)
                .padding(.bottom, 24)
            }
            .scrollIndicators(.hidden)
            .refreshable { await store.refresh(playlist) }
            .background(AmbientBackground(tint: hero.map { NameTile.colour(for: $0.title) } ?? LumenColors.ambientCool))
            .sheet(isPresented: $editingFavourites) { FavouritesEditView(playlist: playlist) }
        }
    }

    /// iPhone only (the iPad has the clock in its top bar): today's date.
    private var phoneHeader: some View {
        Text(Date.now, format: .dateTime.weekday(.wide).day().month(.wide))
            .font(LumenFont.body)
            .foregroundStyle(LumenColors.textSecondary)
            .padding(.top, 8)
    }

    private func heroView(_ channel: LiveChannel, loaded: LoadedLibrary, favourites: [LiveChannel]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Text(channel.title)
                    .font(LumenFont.inter(wide ? 56 : 38, .semibold))
                    .foregroundStyle(LumenColors.textPrimary)
                    .lineLimit(2)
                    .minimumScaleFactor(0.6)
                if let quality = channel.quality { QualityTag(text: quality) }
            }
            .contentTransition(.opacity)
            if let category = categoryTitle(channel, loaded) {
                Text(category)
                    .font(LumenFont.inter(wide ? 20 : 17, .medium))
                    .foregroundStyle(LumenColors.textSecondary)
            }
            HStack(spacing: 12) {
                Button {
                    play(channel, in: favourites)
                } label: {
                    Label("Watch", systemImage: "play.fill")
                }
                .buttonStyle(PrimaryButtonStyle())
                NavigationLink(value: HomeRoute.allChannels) {
                    Text("All channels")
                }
                .buttonStyle(GlassButtonStyle())
            }
            .padding(.top, 10)
        }
        .padding(.top, wide ? 40 : 36)
        .padding(.bottom, wide ? 44 : 32)
        .animation(.easeOut(duration: 0.25), value: channel.key)
    }

    private func favouritesGrid(_ favourites: [LiveChannel], hero: LiveChannel, playlist: Playlist) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            SectionTitle(title: "Favourites", hint: "Hold to move or remove")
            LazyVGrid(
                columns: Array(repeating: GridItem(.flexible(), spacing: wide ? 24 : 14), count: wide ? 4 : 2),
                spacing: wide ? 24 : 14
            ) {
                ForEach(favourites, id: \.key) { channel in
                    Button {
                        play(channel, in: favourites)
                    } label: {
                        ChannelArt(logoUrl: channel.logoUrl, title: channel.title, cornerRadius: 16)
                            .aspectRatio(16 / 9, contentMode: .fit)
                            .overlay {
                                if channel.key == hero.key {
                                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                                        .strokeBorder(.white, lineWidth: 3)
                                        .padding(-5)
                                }
                            }
                    }
                    .buttonStyle(TileButtonStyle())
                    .contextMenu {
                        Button("Watch", systemImage: "play.fill") { play(channel, in: favourites) }
                        Button("Move…", systemImage: "arrow.up.arrow.down") { editingFavourites = true }
                        Button("Remove from favourites", systemImage: "star.slash", role: .destructive) {
                            store.toggleFavourite(channel, in: playlist)
                        }
                    }
                    .accessibilityLabel(channel.title)
                }
            }
            .padding(5) // room for the outline round the hero's tile
            .padding(.horizontal, -5)
        }
    }

    private var noFavourites: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Favourites")
                .font(LumenFont.inter(wide ? 56 : 38, .semibold))
                .foregroundStyle(LumenColors.textPrimary)
            Text("Your favourite channels show here as big tiles. Open All channels and hold a channel to add it.")
                .font(LumenFont.body)
                .foregroundStyle(LumenColors.textSecondary)
                .frame(maxWidth: 460, alignment: .leading)
            NavigationLink(value: HomeRoute.allChannels) { Text("All channels") }
                .buttonStyle(PrimaryButtonStyle())
                .padding(.top, 6)
        }
        .padding(.top, wide ? 40 : 36)
    }

    private func play(_ channel: LiveChannel, in favourites: [LiveChannel]) {
        heroKey = channel.key
        playing = PlayerRoute(channels: favourites, index: favourites.firstIndex { $0.key == channel.key } ?? 0)
    }

    private func categoryTitle(_ channel: LiveChannel, _ loaded: LoadedLibrary) -> String? {
        guard let name = channel.categoryName else { return nil }
        return loaded.categories.first { $0.name == name }?.title
    }
}

enum HomeRoute: Hashable { case allChannels }

/// Tiles shrink a little while pressed (the touch version of the TV's focus lift).
struct TileButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .brightness(configuration.isPressed ? -0.05 : 0)
            .animation(.easeOut(duration: 0.18), value: configuration.isPressed)
    }
}
