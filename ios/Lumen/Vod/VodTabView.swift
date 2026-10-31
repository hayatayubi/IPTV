import Shared
import SwiftUI

enum VodKind { case movies, series }

/// The Movies and Series tabs: Continue watching, then a row of posters per provider category
/// (30 each, "See all" for the rest), as on the TV.
struct VodTabView: View {
    let kind: VodKind
    @Environment(PlaylistStore.self) private var store

    private var title: String { kind == .movies ? "Movies" : "Series" }

    var body: some View {
        if let playlist = store.selected {
            NavigationStack {
                content(playlist)
                    .background(AmbientBackground())
                    .navigationTitle(title)
                    .navigationDestination(for: Movie.self) { MovieDetailView(movie: $0, playlist: playlist) }
                    .navigationDestination(for: Series.self) { SeriesDetailView(series: $0, playlist: playlist) }
                    .navigationDestination(for: VodCategoryRoute.self) { route in
                        VodGridView(route: route, playlist: playlist)
                    }
            }
        } else {
            EmptyTab(title: title, message: "Films and series from your Xtream login show up here.")
        }
    }

    @ViewBuilder
    private func content(_ playlist: Playlist) -> some View {
        switch store.vodState(of: playlist) {
        case .idle, .loading:
            ProgressView(kind == .movies ? "Loading films…" : "Loading series…")
                .font(LumenFont.body)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .unavailable:
            message("Films and series come with an Xtream login. This playlist is an M3U link.")
        case .failed(let error):
            VStack(spacing: 16) {
                message(error)
                Button("Try again") { Task { await store.loadVod(playlist) } }
                    .buttonStyle(GlassButtonStyle())
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .loaded(let index):
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 26) {
                    ContinueWatchingRow(kind: kind, playlist: playlist, index: index)
                    if kind == .movies {
                        ForEach(index.movieCategories, id: \.id) { category in
                            let movies = index.moviesByCategory[category.id] ?? []
                            PosterRow(title: category.title, route: VodCategoryRoute(category: category, kind: .movies)) {
                                ForEach(movies.prefix(30), id: \.id) { movie in
                                    NavigationLink(value: movie) {
                                        Poster(url: movie.posterUrl, title: movie.title, subtitle: movie.year.map { "\($0.intValue)" })
                                    }
                                }
                            }
                        }
                    } else {
                        ForEach(index.seriesCategories, id: \.id) { category in
                            let series = index.seriesByCategory[category.id] ?? []
                            PosterRow(title: category.title, route: VodCategoryRoute(category: category, kind: .series)) {
                                ForEach(series.prefix(30), id: \.id) { show in
                                    NavigationLink(value: show) {
                                        Poster(url: show.posterUrl, title: show.title, subtitle: show.year.map { "\($0.intValue)" })
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(.vertical, 12)
            }
            .refreshable { await store.loadVod(playlist) }
        }
    }

    private func message(_ text: String) -> some View {
        Text(text)
            .font(LumenFont.body)
            .foregroundStyle(LumenColors.textSecondary)
            .multilineTextAlignment(.center)
            .padding(24)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct VodCategoryRoute: Hashable {
    let category: VodCategory
    let kind: VodKind
}

/// A category's title with "See all", over a sideways row of posters.
struct PosterRow<Content: View>: View {
    let title: String
    let route: VodCategoryRoute
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(title).font(LumenFont.headline).foregroundStyle(LumenColors.textPrimary)
                Spacer()
                NavigationLink("See all", value: route)
                    .font(LumenFont.caption)
                    .foregroundStyle(LumenColors.textSecondary)
            }
            .padding(.horizontal, 16)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: 12) { content() }
                    .padding(.horizontal, 16)
            }
            .buttonStyle(.plain)
        }
    }
}

/// A 2:3 poster with the title underneath. Without a poster (or while it loads): a name tile.
struct Poster: View {
    let url: String?
    let title: String
    var subtitle: String?
    var width: CGFloat = 116
    var progress: Double?

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            ZStack(alignment: .bottom) {
                PosterImage(url: url, title: title)
                if let progress {
                    GeometryReader { geo in
                        Capsule().fill(.white.opacity(0.3))
                            .overlay(alignment: .leading) {
                                Capsule().fill(.white).frame(width: geo.size.width * progress)
                            }
                    }
                    .frame(height: 4)
                    .padding(8)
                }
            }
            .frame(width: width, height: width * 1.5)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            Text(title)
                .font(LumenFont.caption)
                .foregroundStyle(LumenColors.textPrimary)
                .lineLimit(1)
            if let subtitle {
                Text(subtitle).font(LumenFont.caption).foregroundStyle(LumenColors.textTertiary)
            }
        }
        .frame(width: width, alignment: .leading)
    }
}

struct PosterImage: View {
    let url: String?
    let title: String

    var body: some View {
        GeometryReader { geo in
            if let url, let parsed = URL(string: url) {
                AsyncImage(url: parsed) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                            .frame(width: geo.size.width, height: geo.size.height)
                            .clipped()
                    } else {
                        NameTile(title: title, height: geo.size.width * 0.6)
                    }
                }
            } else {
                NameTile(title: title, height: geo.size.width * 0.6)
            }
        }
    }
}

/// Unfinished films (or series) of this tab, most recent first. Hold to remove.
struct ContinueWatchingRow: View {
    let kind: VodKind
    let playlist: Playlist
    let index: VodIndex
    @Environment(PlaylistStore.self) private var store

    var body: some View {
        let items = store.continueWatching(kind == .movies ? .movie : .series, in: playlist)
        if !items.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Text("Continue watching")
                    .font(LumenFont.headline)
                    .foregroundStyle(LumenColors.textPrimary)
                    .padding(.horizontal, 16)
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(alignment: .top, spacing: 12) {
                        ForEach(items, id: \.key) { item in
                            link(for: item)
                                .contextMenu {
                                    Button("Remove from Continue watching", systemImage: "xmark", role: .destructive) {
                                        store.removeProgress(item.key, in: playlist)
                                    }
                                }
                        }
                    }
                    .padding(.horizontal, 16)
                }
                .buttonStyle(.plain)
            }
        }
    }

    @ViewBuilder
    private func link(for item: WatchProgress) -> some View {
        let poster = Poster(url: item.posterUrl, title: item.title, progress: item.fraction)
        if let id = item.movieId, let movie = index.movieById[id] {
            NavigationLink(value: movie) { poster }
        } else if let id = item.seriesId, let series = index.seriesById[id] {
            NavigationLink(value: series) { poster }
        }
    }
}

/// "See all": every film or series in a category, as a grid.
struct VodGridView: View {
    let route: VodCategoryRoute
    let playlist: Playlist
    @Environment(PlaylistStore.self) private var store
    @State private var search = ""

    var body: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 108), spacing: 12, alignment: .top)], spacing: 18) {
                if case .loaded(let index) = store.vodState(of: playlist) {
                    if route.kind == .movies {
                        ForEach(filter(index.moviesByCategory[route.category.id] ?? [], \.title), id: \.id) { movie in
                            NavigationLink(value: movie) {
                                Poster(url: movie.posterUrl, title: movie.title, subtitle: movie.year.map { "\($0.intValue)" }, width: 108)
                            }
                        }
                    } else {
                        ForEach(filter(index.seriesByCategory[route.category.id] ?? [], \.title), id: \.id) { show in
                            NavigationLink(value: show) {
                                Poster(url: show.posterUrl, title: show.title, subtitle: show.year.map { "\($0.intValue)" }, width: 108)
                            }
                        }
                    }
                }
            }
            .buttonStyle(.plain)
            .padding(16)
        }
        .background(AmbientBackground())
        .navigationTitle(route.category.title)
        .searchable(text: $search, prompt: "Search this category")
    }

    private func filter<T>(_ items: [T], _ title: KeyPath<T, String>) -> [T] {
        search.isEmpty ? items : items.filter { $0[keyPath: title].localizedCaseInsensitiveContains(search) }
    }
}
