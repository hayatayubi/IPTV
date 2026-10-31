import Foundation
import Observation
import Shared

/// A saved playlist. Only what's safe to keep in plain settings; the login is in the Keychain.
struct Playlist: Codable, Identifiable, Hashable {
    enum Kind: String, Codable { case xtream, m3u }

    let id: UUID
    var name: String
    var kind: Kind
    /// Just the host, for showing in Settings.
    var host: String
}

/// The secret half of a playlist, stored in the Keychain.
private struct PlaylistLogin: Codable {
    var server: String?
    var username: String?
    var password: String?
    var link: String?
}

/// A playlist's channels, loaded from the provider by the shared Kotlin code.
struct LoadedLibrary {
    let library: LiveLibrary
    let categories: [LiveCategory]
    let channelsByCategory: [String: [LiveChannel]]
    let byKey: [String: LiveChannel]

    /// Channels the provider didn't put in any category.
    static let otherKey = "\u{0}other"

    init(_ library: LiveLibrary) {
        self.library = library
        var byCategory = [String: [LiveChannel]]()
        for channel in library.channels {
            byCategory[channel.categoryName ?? Self.otherKey, default: []].append(channel)
        }
        channelsByCategory = byCategory
        byKey = Dictionary(library.channels.map { ($0.key, $0) }, uniquingKeysWith: { first, _ in first })
        categories = library.categories
    }
}

/// Saved playlists and their channels. `@Observable` is SwiftUI's own observation (iOS 17+):
/// views that read a property redraw when it changes.
@MainActor
@Observable
final class PlaylistStore {
    enum LoadState { case idle, loading, loaded(LoadedLibrary), failed(String) }

    private(set) var playlists: [Playlist] = []
    private(set) var states: [UUID: LoadState] = [:]
    var selectedID: UUID?

    // The Kotlin loader. Its `suspend` functions arrive in Swift as `async throws`; Kotlin
    // wants them called from the main thread, which this @MainActor class guarantees.
    private let loader = LumenKt.makePlaylistLoader()
    private let defaultsKey = "playlists"

    /// Favourite channels per playlist, in the user's order, as stable keys (`StableKeys`): the
    /// same idea as the TV's `favourites` table, so they survive refreshes. A favourite that's
    /// missing from the playlist is kept and comes back if the channel does.
    private(set) var favouriteKeys: [UUID: [String]] = [:]
    private let favouritesKey = "favourites"

    /// Films and series per playlist (Xtream only), loaded after the channels.
    private(set) var vodStates: [UUID: VodState] = [:]
    /// Where you stopped in films and episodes, per playlist, keyed `m:<id>` / `e:<id>`.
    private(set) var progress: [UUID: [String: WatchProgress]] = [:]
    private let progressKey = "watchProgress"
    @ObservationIgnored private var movieInfoCache: [String: MovieInfo] = [:]
    @ObservationIgnored private var seriesInfoCache: [String: SeriesInfo] = [:]

    init() {
        if let data = UserDefaults.standard.data(forKey: defaultsKey),
           let saved = try? JSONDecoder().decode([Playlist].self, from: data) {
            playlists = saved
        }
        if let data = UserDefaults.standard.data(forKey: favouritesKey),
           let saved = try? JSONDecoder().decode([UUID: [String]].self, from: data) {
            favouriteKeys = saved
        }
        if let data = UserDefaults.standard.data(forKey: progressKey),
           let saved = try? JSONDecoder().decode([UUID: [String: WatchProgress]].self, from: data) {
            progress = saved
        }
        selectedID = playlists.first?.id
    }

    var selected: Playlist? { playlists.first { $0.id == selectedID } ?? playlists.first }

    func state(of playlist: Playlist) -> LoadState { states[playlist.id] ?? .idle }

    /// Loads every playlist that hasn't been loaded yet (at launch).
    func loadAll() async {
        for playlist in playlists where isIdle(playlist) {
            await refresh(playlist)
        }
        // Films and series after every playlist's channels, so Live TV is ready first.
        for playlist in playlists where vodStates[playlist.id] == nil {
            await loadVod(playlist)
        }
    }

    private func isIdle(_ playlist: Playlist) -> Bool {
        if case .idle = state(of: playlist) { return true }
        return false
    }

    func refresh(_ playlist: Playlist) async {
        guard let login = login(for: playlist) else {
            states[playlist.id] = .failed("The login for this playlist is missing. Remove it and add it again.")
            return
        }
        states[playlist.id] = .loading
        do {
            let library = try await load(login)
            states[playlist.id] = .loaded(LoadedLibrary(library))
        } catch {
            states[playlist.id] = .failed(Self.message(for: error))
        }
    }

    /// Checks the login by loading the channels, and saves the playlist only if that works.
    /// Throws an error whose message is worded for the user.
    func addXtream(name: String, server: String, username: String, password: String) async throws {
        let login = PlaylistLogin(server: server, username: username, password: password)
        try await add(name: name, kind: .xtream, login: login)
    }

    func addM3u(name: String, link: String) async throws {
        // A get.php link holds an Xtream login: store it as one (the provider may block get.php).
        if let parts = loader.xtreamLogin(link: link) {
            let login = PlaylistLogin(server: parts.server, username: parts.username, password: parts.password)
            try await add(name: name, kind: .xtream, login: login)
        } else {
            try await add(name: name, kind: .m3u, login: PlaylistLogin(link: link))
        }
    }

    private func add(name: String, kind: Playlist.Kind, login: PlaylistLogin) async throws {
        let library: LiveLibrary
        do {
            library = try await load(login)
        } catch {
            throw PlaylistError(message: Self.message(for: error))
        }
        let host = URL(string: login.server.map { $0.contains("://") ? $0 : "http://\($0)" } ?? login.link ?? "")?.host() ?? "Playlist"
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        let playlist = Playlist(id: UUID(), name: trimmed.isEmpty ? host : trimmed, kind: kind, host: host)
        if let data = try? JSONEncoder().encode(login) {
            Keychain.save(data, for: playlist.id.uuidString)
        }
        playlists.append(playlist)
        states[playlist.id] = .loaded(LoadedLibrary(library))
        selectedID = playlist.id
        save()
        Task { await loadVod(playlist) }
    }

    func remove(_ playlist: Playlist) {
        Keychain.delete(playlist.id.uuidString)
        favouriteKeys[playlist.id] = nil
        saveFavourites()
        vodStates[playlist.id] = nil
        progress[playlist.id] = nil
        saveProgress()
        playlists.removeAll { $0.id == playlist.id }
        states[playlist.id] = nil
        if selectedID == playlist.id { selectedID = playlists.first?.id }
        save()
    }

    // MARK: Favourites

    func isFavourite(_ channel: LiveChannel, in playlist: Playlist) -> Bool {
        favouriteKeys[playlist.id]?.contains(channel.key) == true
    }

    func toggleFavourite(_ channel: LiveChannel, in playlist: Playlist) {
        var keys = favouriteKeys[playlist.id] ?? []
        if let index = keys.firstIndex(of: channel.key) {
            keys.remove(at: index)
        } else {
            keys.append(channel.key)
        }
        favouriteKeys[playlist.id] = keys
        saveFavourites()
    }

    /// The favourites that are in the playlist right now, in the user's order.
    func favourites(in playlist: Playlist) -> [LiveChannel] {
        guard case .loaded(let loaded) = state(of: playlist) else { return [] }
        return (favouriteKeys[playlist.id] ?? []).compactMap { loaded.byKey[$0] }
    }

    /// Saves a new order (or a removal) made on the shown favourites. Favourites that are
    /// missing from the playlist right now keep their place at the end.
    func setFavourites(_ shown: [LiveChannel], in playlist: Playlist) {
        let shownKeys = shown.map(\.key)
        let present = Set(favourites(in: playlist).map(\.key))
        let missing = (favouriteKeys[playlist.id] ?? []).filter { !present.contains($0) }
        favouriteKeys[playlist.id] = shownKeys + missing
        saveFavourites()
    }

    private func saveFavourites() {
        if let data = try? JSONEncoder().encode(favouriteKeys) {
            UserDefaults.standard.set(data, forKey: favouritesKey)
        }
    }

    // MARK: Films and series

    func vodState(of playlist: Playlist) -> VodState { vodStates[playlist.id] ?? .idle }

    func loadVod(_ playlist: Playlist) async {
        guard let creds = xtreamLogin(for: playlist) else {
            vodStates[playlist.id] = .unavailable
            return
        }
        vodStates[playlist.id] = .loading
        do {
            let vod = try await loader.loadVod(server: creds.server, username: creds.username, password: creds.password)
            vodStates[playlist.id] = .loaded(VodIndex(vod))
        } catch {
            vodStates[playlist.id] = .failed(Self.message(for: error))
        }
    }

    func movieInfo(_ movie: Movie, in playlist: Playlist) async throws -> MovieInfo {
        if let cached = movieInfoCache[movie.id] { return cached }
        guard let creds = xtreamLogin(for: playlist) else { throw PlaylistError(message: "No login.") }
        do {
            let info = try await loader.movieInfo(server: creds.server, username: creds.username, password: creds.password, movieId: movie.id)
            movieInfoCache[movie.id] = info
            return info
        } catch {
            throw PlaylistError(message: Self.message(for: error))
        }
    }

    func seriesInfo(_ series: Series, in playlist: Playlist) async throws -> SeriesInfo {
        if let cached = seriesInfoCache[series.id] { return cached }
        guard let creds = xtreamLogin(for: playlist) else { throw PlaylistError(message: "No login.") }
        do {
            let info = try await loader.seriesInfo(server: creds.server, username: creds.username, password: creds.password, seriesId: series.id)
            seriesInfoCache[series.id] = info
            return info
        } catch {
            throw PlaylistError(message: Self.message(for: error))
        }
    }

    private struct XtreamCredentials {
        let server: String
        let username: String
        let password: String
    }

    private func xtreamLogin(for playlist: Playlist) -> XtreamCredentials? {
        guard playlist.kind == .xtream, let login = login(for: playlist),
              let server = login.server, let username = login.username, let password = login.password else { return nil }
        return XtreamCredentials(server: server, username: username, password: password)
    }

    // MARK: Watch progress

    func progress(_ key: String, in playlist: Playlist) -> WatchProgress? { progress[playlist.id]?[key] }

    /// Saves where playback is. Like the TV: under 30 s isn't worth keeping.
    func saveProgress(_ item: WatchProgress, in playlist: Playlist) {
        guard item.position >= 30 || progress[playlist.id]?[item.key] != nil else { return }
        progress[playlist.id, default: [:]][item.key] = item
        saveProgress()
    }

    func removeProgress(_ key: String, in playlist: Playlist) {
        progress[playlist.id]?[key] = nil
        saveProgress()
    }

    /// Unfinished films (or series, one entry each), most recent first.
    func continueWatching(_ kind: WatchProgress.Kind, in playlist: Playlist) -> [WatchProgress] {
        let items = (progress[playlist.id] ?? [:]).values.filter { $0.kind == kind && !$0.finished }
        var seenSeries = Set<String>()
        return items.sorted { $0.updated > $1.updated }.filter { item in
            guard let series = item.seriesId else { return true }
            return seenSeries.insert(series).inserted
        }
    }

    private func saveProgress() {
        if let data = try? JSONEncoder().encode(progress) {
            UserDefaults.standard.set(data, forKey: progressKey)
        }
    }

    private func load(_ login: PlaylistLogin) async throws -> LiveLibrary {
        if let link = login.link {
            return try await loader.loadM3u(link: link)
        }
        return try await loader.loadXtream(
            server: login.server ?? "", username: login.username ?? "", password: login.password ?? ""
        )
    }

    private func login(for playlist: Playlist) -> PlaylistLogin? {
        Keychain.load(playlist.id.uuidString).flatMap { try? JSONDecoder().decode(PlaylistLogin.self, from: $0) }
    }

    private func save() {
        if let data = try? JSONEncoder().encode(playlists) {
            UserDefaults.standard.set(data, forKey: defaultsKey)
        }
    }

    /// A Kotlin exception comes through as an NSError carrying the original exception;
    /// its message is already worded for the user (`LoadException` in the shared code).
    static func message(for error: Error) -> String {
        let nsError = error as NSError
        if let kotlin = nsError.userInfo["KotlinException"] as? KotlinThrowable, let message = kotlin.message {
            return message
        }
        return error.localizedDescription
    }
}

struct PlaylistError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}
