import Shared
import SwiftUI

/// What the VOD player gets: the stream and where to save progress.
struct VodPlayback: Identifiable {
    let id = UUID()
    let streamUrl: String
    let title: String
    let startAt: Double
    /// Template for the saved progress (position and duration are filled in while playing).
    let progress: WatchProgress
    /// For a series: the episodes after this one, for "Next episode".
    var upNext: [VodPlayback] = []
}

/// The big backdrop at the top of film and series pages, fading into the background.
struct Backdrop: View {
    let url: String?

    var body: some View {
        ZStack(alignment: .bottom) {
            if let url, let parsed = URL(string: url) {
                AsyncImage(url: parsed) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Color.clear
                }
            }
            LinearGradient(colors: [.clear, LumenColors.background], startPoint: .center, endPoint: .bottom)
        }
        .frame(height: 260)
        .frame(maxWidth: .infinity)
        .clipped()
    }
}

struct MovieDetailView: View {
    let movie: Movie
    let playlist: Playlist
    @Environment(PlaylistStore.self) private var store
    @State private var info: MovieInfo?
    @State private var playback: VodPlayback?

    private var key: String { WatchProgress.movieKey(movie.id) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Backdrop(url: info?.backdropUrl ?? movie.posterUrl)
                VStack(alignment: .leading, spacing: 14) {
                    HStack(spacing: 8) {
                        Text(movie.title).font(LumenFont.largeTitle).foregroundStyle(LumenColors.textPrimary)
                        if let quality = movie.quality { QualityTag(text: quality) }
                    }
                    Text(facts).font(LumenFont.caption).foregroundStyle(LumenColors.textSecondary)
                    buttons
                    if let plot = info?.plot {
                        Text(plot).font(LumenFont.body).foregroundStyle(LumenColors.textPrimary.opacity(0.9))
                    }
                    if let cast = info?.cast {
                        Text("Cast: \(cast)").font(LumenFont.caption).foregroundStyle(LumenColors.textSecondary)
                    }
                    if let director = info?.director {
                        Text("Director: \(director)").font(LumenFont.caption).foregroundStyle(LumenColors.textSecondary)
                    }
                }
                .padding(.horizontal, 20)
            }
            .padding(.bottom, 30)
        }
        .ignoresSafeArea(edges: .top)
        .background(AmbientBackground())
        .navigationBarTitleDisplayMode(.inline)
        .task { info = try? await store.movieInfo(movie, in: playlist) }
        .fullScreenCover(item: $playback) { VodPlayerView(playback: $0, playlist: playlist) }
    }

    private var facts: String {
        var parts: [String] = []
        if let year = movie.year { parts.append("\(year.intValue)") }
        if let genre = info?.genre { parts.append(genre) }
        if let secs = info?.durationSecs?.doubleValue { parts.append(formatDuration(secs)) }
        if let age = info?.ageRating { parts.append(age) }
        if let rating = movie.rating?.doubleValue { parts.append(String(format: "★ %.1f", rating)) }
        return parts.joined(separator: " · ")
    }

    @ViewBuilder
    private var buttons: some View {
        let saved = store.progress(key, in: playlist)
        HStack(spacing: 12) {
            if let saved, !saved.finished {
                Button { play(from: saved.position) } label: {
                    Label("Resume from \(formatTime(saved.position))", systemImage: "play.fill")
                }
                .buttonStyle(PrimaryButtonStyle())
                Button { play(from: 0) } label: { Label("Start over", systemImage: "arrow.counterclockwise") }
                    .buttonStyle(GlassButtonStyle())
            } else {
                Button { play(from: 0) } label: { Label("Play", systemImage: "play.fill") }
                    .buttonStyle(PrimaryButtonStyle())
            }
        }
    }

    private func play(from start: Double) {
        playback = VodPlayback(
            streamUrl: movie.streamUrl,
            title: movie.title,
            startAt: start,
            progress: WatchProgress(
                key: key, kind: .movie, seriesId: nil, movieId: movie.id, title: movie.title,
                posterUrl: movie.posterUrl, position: start,
                duration: info?.durationSecs?.doubleValue ?? 0, updated: .now
            )
        )
    }
}

struct SeriesDetailView: View {
    let series: Series
    let playlist: Playlist
    @Environment(PlaylistStore.self) private var store
    @State private var info: SeriesInfo?
    @State private var error: String?
    @State private var season: Int32 = 1
    @State private var playback: VodPlayback?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Backdrop(url: series.backdropUrl ?? series.posterUrl)
                VStack(alignment: .leading, spacing: 14) {
                    Text(series.title).font(LumenFont.largeTitle).foregroundStyle(LumenColors.textPrimary)
                    Text(facts).font(LumenFont.caption).foregroundStyle(LumenColors.textSecondary)
                    if let pick = nextUp {
                        Button { play(pick.episode, from: pick.resume) } label: {
                            Label(pick.label, systemImage: "play.fill")
                        }
                        .buttonStyle(PrimaryButtonStyle())
                    }
                    if let plot = series.plot {
                        Text(plot).font(LumenFont.body).foregroundStyle(LumenColors.textPrimary.opacity(0.9))
                    }
                    episodes
                }
                .padding(.horizontal, 20)
            }
            .padding(.bottom, 30)
        }
        .ignoresSafeArea(edges: .top)
        .background(AmbientBackground())
        .navigationBarTitleDisplayMode(.inline)
        .task {
            do {
                let loaded = try await store.seriesInfo(series, in: playlist)
                info = loaded
                season = nextUp.map { $0.episode.season } ?? loaded.seasons.first?.int32Value ?? 1
            } catch {
                self.error = error.localizedDescription
            }
        }
        .fullScreenCover(item: $playback) { VodPlayerView(playback: $0, playlist: playlist) }
    }

    private var facts: String {
        var parts: [String] = []
        if let year = series.year { parts.append("\(year.intValue)") }
        if let genre = series.genre { parts.append(genre) }
        if let rating = series.rating?.doubleValue { parts.append(String(format: "★ %.1f", rating)) }
        return parts.joined(separator: " · ")
    }

    @ViewBuilder
    private var episodes: some View {
        if let error {
            Text(error).font(LumenFont.body).foregroundStyle(LumenColors.textSecondary)
        } else if let info {
            if info.seasons.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(info.seasons.map(\.int32Value), id: \.self) { number in
                            Button("Season \(number)") { season = number }
                                .font(LumenFont.caption)
                                .foregroundStyle(number == season ? LumenColors.background : LumenColors.textPrimary)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(number == season ? Color.white : LumenColors.glass, in: Capsule())
                        }
                    }
                }
            }
            ForEach(info.episodes.filter { $0.season == season }, id: \.id) { episode in
                Button { play(episode, from: resumePosition(episode)) } label: { EpisodeRow(episode: episode, progress: progress(episode)) }
                    .buttonStyle(.plain)
            }
        } else {
            ProgressView().frame(maxWidth: .infinity)
        }
    }

    private func progress(_ episode: Episode) -> WatchProgress? {
        store.progress(WatchProgress.episodeKey(episode.id), in: playlist)
    }

    private func resumePosition(_ episode: Episode) -> Double {
        guard let saved = progress(episode), !saved.finished else { return 0 }
        return saved.position
    }

    /// Which episode Play starts, like `NextUp.pick` on the TV: the one you were last watching if
    /// unfinished, else the one after it, else the first.
    private var nextUp: (episode: Episode, resume: Double, label: String)? {
        guard let episodes = info?.episodes, let first = episodes.first else { return nil }
        let watched = episodes.compactMap { e in progress(e).map { (e, $0) } }
        guard let latest = watched.max(by: { $0.1.updated < $1.1.updated }) else {
            return (first, 0, "Play S\(first.season) · E\(first.episode)")
        }
        let (last, saved) = latest
        if !saved.finished {
            return (last, saved.position, "Resume S\(last.season) · E\(last.episode)")
        }
        let index = episodes.firstIndex(of: last) ?? 0
        let next = index + 1 < episodes.count ? episodes[index + 1] : first
        return (next, resumePosition(next), "Play S\(next.season) · E\(next.episode)")
    }

    private func play(_ episode: Episode, from start: Double) {
        playback = playbackFor(episode, from: start, withUpNext: true)
    }

    private func playbackFor(_ episode: Episode, from start: Double, withUpNext: Bool) -> VodPlayback {
        let title = "\(series.title) · S\(episode.season) E\(episode.episode)"
        var playback = VodPlayback(
            streamUrl: episode.streamUrl,
            title: episode.title.map { "\(title) · \($0)" } ?? title,
            startAt: start,
            progress: WatchProgress(
                key: WatchProgress.episodeKey(episode.id), kind: .series, seriesId: series.id, movieId: nil,
                title: title, posterUrl: series.posterUrl, position: start,
                duration: episode.durationSecs?.doubleValue ?? 0, updated: .now
            )
        )
        if withUpNext, let episodes = info?.episodes, let index = episodes.firstIndex(of: episode) {
            playback.upNext = episodes.dropFirst(index + 1).prefix(20).map { playbackFor($0, from: 0, withUpNext: false) }
        }
        return playback
    }
}

struct EpisodeRow: View {
    let episode: Episode
    let progress: WatchProgress?

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            ZStack(alignment: .bottom) {
                PosterImage(url: episode.imageUrl, title: "E\(episode.episode)")
                if let progress {
                    GeometryReader { geo in
                        Capsule().fill(.white.opacity(0.3)).overlay(alignment: .leading) {
                            Capsule().fill(.white).frame(width: geo.size.width * progress.fraction)
                        }
                    }
                    .frame(height: 3)
                    .padding(6)
                }
            }
            .frame(width: 140, height: 79)
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            VStack(alignment: .leading, spacing: 4) {
                Text("\(episode.episode). \(episode.title ?? "Episode \(episode.episode)")")
                    .font(LumenFont.headline)
                    .foregroundStyle(LumenColors.textPrimary)
                    .lineLimit(2)
                if let secs = episode.durationSecs?.doubleValue {
                    Text(formatDuration(secs)).font(LumenFont.caption).foregroundStyle(LumenColors.textSecondary)
                }
                if let plot = episode.plot {
                    Text(plot).font(LumenFont.caption).foregroundStyle(LumenColors.textSecondary).lineLimit(2)
                }
            }
            Spacer(minLength: 0)
        }
        .contentShape(Rectangle())
    }
}

/// "1 h 52 min" / "48 min".
func formatDuration(_ seconds: Double) -> String {
    let minutes = Int(seconds / 60)
    return minutes >= 60 ? "\(minutes / 60) h \(minutes % 60) min" : "\(minutes) min"
}
