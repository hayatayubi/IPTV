import Shared
import SwiftUI

enum VodKind { case movies, series }

/// The Movies and Series tabs, as on the TV: a big hero (backdrop, title, year, genre, plot,
/// Play and My List) over Continue watching and a row of posters per provider category
/// (30 each, "See all" for the rest).
struct VodTabView: View {
    let kind: VodKind
    @Environment(PlaylistStore.self) private var store
    @Environment(\.lumenWide) private var wide

    private var title: String { kind == .movies ? "Movies" : "Series" }

    var body: some View {
        NavigationStack {
            Group {
                if let playlist = store.selected {
                    content(playlist)
                        .vodDestinations(playlist)
                } else {
                    EmptyTab(title: title, message: "Films and series from your Xtream login show up here.")
                }
            }
            .toolbar(.hidden, for: .navigationBar)
        }
    }

    @ViewBuilder
    private func content(_ playlist: Playlist) -> some View {
        switch store.vodState(of: playlist) {
        case .idle, .loading:
            StatusMessage(text: kind == .movies ? "Loading films…" : "Loading series…", loading: true)
        case .unavailable:
            StatusMessage(text: "Films and series come with an Xtream login. This playlist is an M3U link.")
        case .failed(let error):
            StatusMessage(text: error) { Task { await store.loadVod(playlist) } }
        case .loaded(let index):
            // The reader gives the height of the status bar (and the iPad's tab bar), so the
            // hero's backdrop can reach up behind them.
            GeometryReader { geo in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: wide ? 36 : 28) {
                        VodHero(kind: kind, title: title, playlist: playlist, index: index, topInset: geo.safeAreaInsets.top)
                        ContinueWatchingRow(kind: kind, playlist: playlist, index: index)
                        if kind == .movies {
                            ForEach(index.movieCategories, id: \.id) { category in
                                let movies = index.moviesByCategory[category.id] ?? []
                                PosterRow(category: category, route: VodCategoryRoute(category: category, kind: .movies)) {
                                    ForEach(movies.prefix(30), id: \.id) { movie in
                                        NavigationLink(value: movie) {
                                            Poster(url: movie.posterUrl, title: movie.title, subtitle: movie.year.map { "\($0.intValue)" }, width: wide ? 160 : 116)
                                        }
                                    }
                                }
                            }
                        } else {
                            ForEach(index.seriesCategories, id: \.id) { category in
                                let series = index.seriesByCategory[category.id] ?? []
                                PosterRow(category: category, route: VodCategoryRoute(category: category, kind: .series)) {
                                    ForEach(series.prefix(30), id: \.id) { show in
                                        NavigationLink(value: show) {
                                            Poster(url: show.posterUrl, title: show.title, subtitle: show.year.map { "\($0.intValue)" }, width: wide ? 160 : 116)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .padding(.bottom, 24)
                }
                .scrollIndicators(.hidden)
                // Lets the backdrop draw above the scroll view's top edge.
                .scrollClipDisabled()
                .refreshable { await store.loadVod(playlist) }
            }
            .background(AmbientBackground())
        }
    }
}

extension View {
    /// Where film, series and "See all" links go, for any NavigationStack showing posters.
    func vodDestinations(_ playlist: Playlist) -> some View {
        navigationDestination(for: Movie.self) { MovieDetailView(movie: $0, playlist: playlist) }
            .navigationDestination(for: Series.self) { SeriesDetailView(series: $0, playlist: playlist) }
            .navigationDestination(for: VodCategoryRoute.self) { VodGridView(route: $0, playlist: playlist) }
    }
}

struct VodCategoryRoute: Hashable {
    let category: VodCategory
    let kind: VodKind
}

/// The big picture at the top of the Movies and Series tabs: the film you were last watching,
/// else the first of the provider's first category. Play starts it straight away; the heart
/// adds it to My List; the title opens its page.
struct VodHero: View {
    let kind: VodKind
    let title: String
    let playlist: Playlist
    let index: VodIndex
    var topInset: CGFloat = 0
    @Environment(PlaylistStore.self) private var store
    @Environment(\.lumenWide) private var wide
    @State private var movieInfo: MovieInfo?
    @State private var playback: VodPlayback?

    var body: some View {
        let height: CGFloat = wide ? 560 : 500
        ZStack(alignment: .bottomLeading) {
            Backdrop(url: backdropUrl, height: height + topInset, wide: wide)
            VStack(alignment: .leading, spacing: 0) {
                if !wide {
                    Text(title)
                        .font(LumenFont.inter(34, .semibold))
                        .foregroundStyle(LumenColors.textPrimary)
                        .shadow(color: .black.opacity(0.4), radius: 8)
                        .padding(.top, topInset + 8)
                }
                Spacer(minLength: 0)
                if let movie {
                    details(title: movie.title, quality: movie.quality, facts: movieFacts(movie), plot: movieInfo?.plot, link: movie)
                    buttons(
                        play: { playMovie(movie) },
                        playLabel: moviePlayLabel(movie),
                        inList: store.isInMyList(.movie, id: movie.id, in: playlist),
                        toggleList: { store.toggleMyList(movie, in: playlist) }
                    )
                } else if let series {
                    details(title: series.title, quality: nil, facts: seriesFacts(series), plot: series.plot, link: series)
                    buttons(
                        play: { Task { playback = await nextUpPlayback(series, store: store, playlist: playlist) } },
                        playLabel: "Play",
                        inList: store.isInMyList(.series, id: series.id, in: playlist),
                        toggleList: { store.toggleMyList(series, in: playlist) }
                    )
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .lumenGutter(wide)
            .padding(.bottom, 6)
        }
        .frame(height: height + topInset)
        .task(id: movie?.id) {
            guard let movie else { return }
            movieInfo = try? await store.movieInfo(movie, in: playlist)
        }
        .fullScreenCover(item: $playback) { VodPlayerView(playback: $0, playlist: playlist) }
    }

    // MARK: Which title

    private var movie: Movie? {
        guard kind == .movies else { return nil }
        if let recent = store.continueWatching(.movie, in: playlist).first,
           let id = recent.movieId, let movie = index.movieById[id] { return movie }
        return index.movieCategories.lazy.compactMap { index.moviesByCategory[$0.id]?.first }.first
    }

    private var series: Series? {
        guard kind == .series else { return nil }
        if let recent = store.continueWatching(.series, in: playlist).first,
           let id = recent.seriesId, let show = index.seriesById[id] { return show }
        return index.seriesCategories.lazy.compactMap { index.seriesByCategory[$0.id]?.first }.first
    }

    private var backdropUrl: String? {
        if let movie { return movieInfo?.backdropUrl ?? movie.posterUrl }
        return series.flatMap { $0.backdropUrl ?? $0.posterUrl }
    }

    // MARK: Pieces

    private func details<Link: Hashable>(title: String, quality: String?, facts: String, plot: String?, link: Link) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            NavigationLink(value: link) {
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Text(title)
                        .font(LumenFont.inter(wide ? 52 : 34, .semibold))
                        .foregroundStyle(LumenColors.textPrimary)
                        .lineLimit(2)
                        .minimumScaleFactor(0.6)
                        .multilineTextAlignment(.leading)
                    if let quality { QualityTag(text: quality) }
                }
            }
            .buttonStyle(.plain)
            if !facts.isEmpty {
                Text(facts).font(LumenFont.inter(wide ? 18 : 15)).foregroundStyle(LumenColors.textSecondary)
            }
            if let plot {
                Text(plot)
                    .font(LumenFont.inter(wide ? 18 : 15))
                    .foregroundStyle(LumenColors.textPrimary.opacity(0.85))
                    .lineLimit(wide ? 3 : 2)
                    .frame(maxWidth: 560, alignment: .leading)
            }
        }
    }

    private func buttons(play: @escaping () -> Void, playLabel: String, inList: Bool, toggleList: @escaping () -> Void) -> some View {
        HStack(spacing: 12) {
            Button(action: play) { Label(playLabel, systemImage: "play.fill") }
                .buttonStyle(PrimaryButtonStyle())
            MyListButton(inList: inList, action: toggleList)
        }
        .padding(.top, 14)
    }

    private func movieFacts(_ movie: Movie) -> String {
        var parts: [String] = []
        if let year = movie.year { parts.append("\(year.intValue)") }
        if let genre = movieInfo?.genre { parts.append(genre) }
        if let secs = movieInfo?.durationSecs?.doubleValue { parts.append(formatDuration(secs)) }
        return parts.joined(separator: " · ")
    }

    private func seriesFacts(_ series: Series) -> String {
        var parts: [String] = []
        if let year = series.year { parts.append("\(year.intValue)") }
        if let genre = series.genre { parts.append(genre) }
        return parts.joined(separator: " · ")
    }

    private func moviePlayLabel(_ movie: Movie) -> String {
        guard let saved = store.progress(WatchProgress.movieKey(movie.id), in: playlist), !saved.finished else { return "Play" }
        return "Resume"
    }

    private func playMovie(_ movie: Movie) {
        let saved = store.progress(WatchProgress.movieKey(movie.id), in: playlist)
        let start = saved.map { $0.finished ? 0 : $0.position } ?? 0
        playback = moviePlayback(movie, info: movieInfo, from: start)
    }
}

/// The glass heart: filled while the title is in My List.
struct MyListButton: View {
    let inList: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: inList ? "heart.fill" : "heart")
                .contentTransition(.symbolEffect(.replace))
        }
        .buttonStyle(GlassButtonStyle())
        .accessibilityLabel(inList ? "Remove from My List" : "Add to My List")
        .sensoryFeedback(.selection, trigger: inList)
    }
}

/// A category's title (with its 4K tag) and "See all", over a sideways row of posters.
struct PosterRow<Content: View>: View {
    let category: VodCategory
    let route: VodCategoryRoute
    @ViewBuilder let content: () -> Content
    @Environment(\.lumenWide) private var wide

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(category.title).font(LumenFont.inter(wide ? 22 : 19, .semibold)).foregroundStyle(LumenColors.textPrimary)
                    .lineLimit(1)
                if let quality = category.quality { QualityTag(text: quality) }
                Spacer()
                NavigationLink("See all", value: route)
                    .font(LumenFont.inter(15, .medium))
                    .foregroundStyle(LumenColors.textSecondary)
            }
            .lumenGutter(wide)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: wide ? 18 : 12) { content() }
                    .lumenGutter(wide)
            }
            .buttonStyle(TileButtonStyle())
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
                    ProgressLine(fraction: progress, track: .white.opacity(0.3)).padding(8)
                }
            }
            .frame(width: width, height: width * 1.5)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            Text(title)
                .font(LumenFont.caption)
                .foregroundStyle(LumenColors.textPrimary)
                .lineLimit(1)
            if let subtitle {
                Text(subtitle).font(LumenFont.caption).foregroundStyle(LumenColors.textTertiary)
            }
        }
        .frame(width: width, alignment: .leading)
        .contentShape(Rectangle())
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
                        NameTile(title: title, height: min(geo.size.width * 0.6, geo.size.height))
                    }
                }
            } else {
                NameTile(title: title, height: min(geo.size.width * 0.6, geo.size.height))
            }
        }
    }
}

/// Unfinished films (or series) of this tab, most recent first, as wide cards with the title on
/// the picture and a progress line (as on the TV since 0.8.0). Hold to remove.
struct ContinueWatchingRow: View {
    let kind: VodKind
    let playlist: Playlist
    let index: VodIndex
    @Environment(PlaylistStore.self) private var store
    @Environment(\.lumenWide) private var wide

    var body: some View {
        let items = store.continueWatching(kind == .movies ? .movie : .series, in: playlist)
        if !items.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                Text("Continue watching")
                    .font(LumenFont.inter(wide ? 22 : 19, .semibold))
                    .foregroundStyle(LumenColors.textPrimary)
                    .lumenGutter(wide)
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(alignment: .top, spacing: wide ? 18 : 12) {
                        ForEach(items, id: \.key) { item in
                            link(for: item)
                                .contextMenu {
                                    Button("Remove from Continue watching", systemImage: "xmark", role: .destructive) {
                                        store.removeProgress(item.key, in: playlist)
                                    }
                                }
                        }
                    }
                    .lumenGutter(wide)
                }
                .buttonStyle(TileButtonStyle())
            }
        }
    }

    @ViewBuilder
    private func link(for item: WatchProgress) -> some View {
        let card = WideCard(url: backdrop(for: item), title: item.title, progress: item.fraction, width: wide ? 300 : 230)
        if let id = item.movieId, let movie = index.movieById[id] {
            NavigationLink(value: movie) { card }
        } else if let id = item.seriesId, let series = index.seriesById[id] {
            NavigationLink(value: series) { card }
        }
    }

    private func backdrop(for item: WatchProgress) -> String? {
        if let id = item.seriesId, let series = index.seriesById[id] { return series.backdropUrl ?? item.posterUrl }
        return item.posterUrl
    }
}

/// A 16:9 card with the title on the picture and an optional progress line.
struct WideCard: View {
    let url: String?
    let title: String
    var progress: Double?
    var width: CGFloat = 230

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            PosterImage(url: url, title: title)
            LinearGradient(colors: [.clear, .black.opacity(0.65)], startPoint: .center, endPoint: .bottom)
            VStack(alignment: .leading, spacing: 6) {
                Text(title)
                    .font(LumenFont.inter(15, .semibold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .shadow(color: .black.opacity(0.5), radius: 4)
                if let progress { ProgressLine(fraction: progress, height: 3, track: .white.opacity(0.3)) }
            }
            .padding(10)
        }
        .frame(width: width, height: width * 9 / 16)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .contentShape(Rectangle())
    }
}

/// "See all": every film or series in a category, as a grid.
struct VodGridView: View {
    let route: VodCategoryRoute
    let playlist: Playlist
    @Environment(PlaylistStore.self) private var store
    @State private var search = ""
    @Environment(\.lumenWide) private var wide

    var body: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: wide ? 150 : 108), spacing: 12, alignment: .top)], spacing: 18) {
                if case .loaded(let index) = store.vodState(of: playlist) {
                    if route.kind == .movies {
                        ForEach(filter(index.moviesByCategory[route.category.id] ?? [], \.title), id: \.id) { movie in
                            NavigationLink(value: movie) {
                                Poster(url: movie.posterUrl, title: movie.title, subtitle: movie.year.map { "\($0.intValue)" }, width: wide ? 150 : 108)
                            }
                        }
                    } else {
                        ForEach(filter(index.seriesByCategory[route.category.id] ?? [], \.title), id: \.id) { show in
                            NavigationLink(value: show) {
                                Poster(url: show.posterUrl, title: show.title, subtitle: show.year.map { "\($0.intValue)" }, width: wide ? 150 : 108)
                            }
                        }
                    }
                }
            }
            .buttonStyle(.plain)
            .padding(wide ? 48 : 16)
        }
        .background(AmbientBackground())
        .toolbar(.visible, for: .navigationBar)
        .toolbarBackground(.hidden, for: .navigationBar)
        .navigationTitle(route.category.title)
        .searchable(text: $search, prompt: "Search this category")
    }

    private func filter<T>(_ items: [T], _ title: KeyPath<T, String>) -> [T] {
        search.isEmpty ? items : items.filter { $0[keyPath: title].localizedCaseInsensitiveContains(search) }
    }
}
