import SwiftUI
import VLCKitSPM

/// Full-screen film/episode player. It uses VLC (VLCKit) rather than Apple's player because
/// most films from Xtream providers are .mkv, often with DTS/AC3 audio, which AVPlayer can't open.
/// Tap shows the controls; double-tap the left/right side skips 10 s back/forward.
struct VodPlayerView: View {
    let playback: VodPlayback
    let playlist: Playlist
    @Environment(PlaylistStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var model = VodPlayerModel()
    @State private var current: VodPlayback
    @State private var queue: [VodPlayback]
    @State private var controlsVisible = true
    @State private var hideTask: Task<Void, Never>?
    @State private var scrubbing: Double?

    init(playback: VodPlayback, playlist: Playlist) {
        self.playback = playback
        self.playlist = playlist
        _current = State(initialValue: playback)
        _queue = State(initialValue: playback.upNext)
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VLCVideoView(model: model).ignoresSafeArea()

            // Double-tap halves: back 10 s on the left, forward 10 s on the right; one tap toggles
            // the controls. (The double tap is declared first so a single tap waits for it.)
            HStack(spacing: 0) {
                Color.clear.contentShape(Rectangle())
                    .onTapGesture(count: 2) { model.skip(-10); showControls() }
                    .onTapGesture { toggleControls() }
                Color.clear.contentShape(Rectangle())
                    .onTapGesture(count: 2) { model.skip(10); showControls() }
                    .onTapGesture { toggleControls() }
            }

            if model.isBuffering && !model.failed {
                ProgressView().controlSize(.large).tint(.white)
            }
            if model.failed {
                Text("This video can't be played right now.")
                    .font(LumenFont.body)
                    .padding(20)
                    .glass(fill: .black.opacity(0.6))
            }
            if controlsVisible { controls.transition(.opacity) }
        }
        .statusBarHidden(!controlsVisible)
        .persistentSystemOverlays(.hidden)
        .onAppear {
            model.start(current.streamUrl, at: current.startAt)
            UIApplication.shared.isIdleTimerDisabled = true
            scheduleHide()
        }
        .onDisappear {
            saveProgress()
            model.stop()
            UIApplication.shared.isIdleTimerDisabled = false
        }
        // Save every 15 s while playing, as on the TV.
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(15))
                if model.isPlaying { saveProgress() }
            }
        }
        .onChange(of: model.ended) { _, ended in
            guard ended else { return }
            saveProgress(finished: true)
            if let next = queue.first {
                queue.removeFirst()
                current = next
                model.start(next.streamUrl, at: 0)
                showControls()
            } else {
                dismiss()
            }
        }
    }

    private var controls: some View {
        VStack {
            HStack {
                Text(current.title).font(LumenFont.headline).lineLimit(1)
                Spacer()
                TrackMenu(model: model)
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 17, weight: .semibold)).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Close")
            }
            .foregroundStyle(LumenColors.textPrimary)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .glass(fill: .black.opacity(0.5))
            .padding(16)

            Spacer()

            HStack(spacing: 44) {
                Button { model.skip(-10); showControls() } label: { Image(systemName: "gobackward.10") }
                Button { model.togglePause(); showControls() } label: {
                    Image(systemName: model.isPlaying ? "pause.fill" : "play.fill").frame(width: 44)
                }
                Button { model.skip(10); showControls() } label: { Image(systemName: "goforward.10") }
            }
            .font(.system(size: 34, weight: .semibold))
            .foregroundStyle(.white)

            Spacer()

            VStack(spacing: 6) {
                Slider(
                    value: Binding(
                        get: { scrubbing ?? model.position },
                        set: { scrubbing = $0; showControls() }
                    ),
                    in: 0...max(model.duration, 1),
                    onEditingChanged: { editing in
                        if !editing, let target = scrubbing {
                            model.seek(to: target)
                            scrubbing = nil
                        }
                    }
                )
                .tint(.white)
                HStack {
                    Text(formatTime(scrubbing ?? model.position))
                    Spacer()
                    Text("-" + formatTime(max(0, model.duration - (scrubbing ?? model.position))))
                }
                .font(LumenFont.caption)
                .foregroundStyle(LumenColors.textSecondary)
                if let next = queue.first {
                    Button {
                        saveProgress()
                        queue.removeFirst()
                        current = next
                        model.start(next.streamUrl, at: 0)
                    } label: {
                        Label("Next episode", systemImage: "forward.end.fill")
                    }
                    .buttonStyle(GlassButtonStyle())
                    .frame(maxWidth: .infinity, alignment: .trailing)
                }
            }
            .padding(16)
            .glass(fill: .black.opacity(0.5))
            .padding(16)
        }
    }

    private func saveProgress(finished: Bool = false) {
        var item = current.progress
        let duration = model.duration > 0 ? model.duration : item.duration
        guard duration > 0 || model.position > 0 else { return }
        item.duration = duration
        item.position = finished ? duration : model.position
        item.updated = .now
        store.saveProgress(item, in: playlist)
    }

    private func toggleControls() {
        withAnimation(.easeOut(duration: 0.18)) { controlsVisible.toggle() }
        if controlsVisible { scheduleHide() }
    }

    private func showControls() {
        withAnimation(.easeOut(duration: 0.18)) { controlsVisible = true }
        scheduleHide()
    }

    private func scheduleHide() {
        hideTask?.cancel()
        hideTask = Task {
            try? await Task.sleep(for: .seconds(4))
            guard !Task.isCancelled, model.isPlaying, scrubbing == nil else { return }
            withAnimation(.easeOut(duration: 0.3)) { controlsVisible = false }
        }
    }
}

/// Audio and subtitle choice, the iOS way: one menu.
struct TrackMenu: View {
    let model: VodPlayerModel

    var body: some View {
        Menu {
            let audio = model.audioTracks()
            if !audio.isEmpty {
                Section("Audio") {
                    ForEach(audio, id: \.id) { track in
                        Button { model.selectAudio(track.id) } label: {
                            if track.selected { Label(track.name, systemImage: "checkmark") } else { Text(track.name) }
                        }
                    }
                }
            }
            let subtitles = model.subtitleTracks()
            if !subtitles.isEmpty {
                Section("Subtitles") {
                    ForEach(subtitles, id: \.id) { track in
                        Button { model.selectSubtitle(track.id) } label: {
                            if track.selected { Label(track.name, systemImage: "checkmark") } else { Text(track.name) }
                        }
                    }
                }
            }
        } label: {
            Image(systemName: "captions.bubble").font(.system(size: 18, weight: .semibold)).frame(width: 44, height: 44)
        }
        .accessibilityLabel("Audio and subtitles")
    }
}

struct Track {
    let id: Int32
    let name: String
    let selected: Bool
}

/// Wraps VLCMediaPlayer. Its state is read twice a second (simpler and sturdier than VLC's
/// delegate callbacks) and published to SwiftUI.
@MainActor
@Observable
final class VodPlayerModel {
    @ObservationIgnored let player = VLCMediaPlayer()
    private(set) var position: Double = 0
    private(set) var duration: Double = 0
    private(set) var isPlaying = false
    private(set) var isBuffering = true
    private(set) var failed = false
    private(set) var ended = false
    @ObservationIgnored private var timer: Timer?

    init() {
        try? AVAudioSessionHelper.activate()
    }

    func start(_ url: String, at seconds: Double) {
        player.stop()
        guard let parsed = URL(string: url) else { failed = true; return }
        let media = VLCMedia(url: parsed)
        if seconds > 1 { media.addOption(":start-time=\(Int(seconds))") }
        // A bigger network buffer (ms) smooths over slow provider servers.
        media.addOption(":network-caching=3000")
        player.media = media
        position = seconds
        duration = 0
        failed = false
        ended = false
        isBuffering = true
        player.play()
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.poll() }
        }
    }

    private func poll() {
        let time = Double(player.time.intValue) / 1000
        if time > 0 { position = time }
        if let length = player.media?.length.intValue, length > 0 { duration = Double(length) / 1000 }
        isPlaying = player.isPlaying
        switch player.state {
        case .error: failed = true
        case .ended: if !ended { ended = true }
        case .buffering, .opening: isBuffering = !player.isPlaying
        default: isBuffering = false
        }
    }

    func togglePause() {
        if player.isPlaying { player.pause() } else { player.play() }
        isPlaying.toggle()
    }

    func skip(_ seconds: Int32) {
        if seconds > 0 { player.jumpForward(seconds) } else { player.jumpBackward(-seconds) }
        position = max(0, position + Double(seconds))
    }

    func seek(to seconds: Double) {
        player.time = VLCTime(int: Int32(seconds * 1000))
        position = seconds
    }

    func stop() {
        timer?.invalidate()
        timer = nil
        player.stop()
    }

    func audioTracks() -> [Track] {
        tracks(names: player.audioTrackNames, ids: player.audioTrackIndexes, current: player.currentAudioTrackIndex)
    }

    func subtitleTracks() -> [Track] {
        tracks(names: player.videoSubTitlesNames, ids: player.videoSubTitlesIndexes, current: player.currentVideoSubTitleIndex)
    }

    func selectAudio(_ id: Int32) { player.currentAudioTrackIndex = id }
    func selectSubtitle(_ id: Int32) { player.currentVideoSubTitleIndex = id }

    private func tracks(names: [Any], ids: [Any], current: Int32) -> [Track] {
        zip(names, ids).compactMap { name, id in
            guard let id = (id as? NSNumber)?.int32Value else { return nil }
            // VLC lists "Disable" as track -1.
            let label = id == -1 ? "Off" : (name as? String ?? "Track \(id)")
            return Track(id: id, name: label, selected: id == current)
        }
    }
}

/// The view VLC draws the video into.
struct VLCVideoView: UIViewRepresentable {
    let model: VodPlayerModel

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .black
        model.player.drawable = view
        return view
    }

    func updateUIView(_ view: UIView, context: Context) {}
}
