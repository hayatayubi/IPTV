import AVFoundation
import Shared
import SwiftUI

/// Full-screen live TV. Swipe up or down to change channel (like Up/Down on the TV remote),
/// tap to show or hide the channel name and buttons.
struct LivePlayerView: View {
    let channels: [LiveChannel]
    @State private var index: Int
    @State private var model = LivePlayerModel()
    @State private var chromeVisible = true
    @State private var hideTask: Task<Void, Never>?
    @Environment(\.dismiss) private var dismiss

    init(channels: [LiveChannel], startIndex: Int) {
        self.channels = channels
        _index = State(initialValue: startIndex)
    }

    private var channel: LiveChannel { channels[index] }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            PlayerLayerView(player: model.player).ignoresSafeArea()

            if model.isBuffering {
                ProgressView().controlSize(.large).tint(.white)
            }
            if let error = model.error {
                Text(error)
                    .font(LumenFont.body)
                    .foregroundStyle(LumenColors.textPrimary)
                    .multilineTextAlignment(.center)
                    .padding(20)
                    .glass(fill: Color.black.opacity(0.6))
                    .padding(32)
            }
            if chromeVisible { chrome.transition(.opacity) }
        }
        .contentShape(Rectangle())
        .onTapGesture { toggleChrome() }
        .gesture(
            DragGesture(minimumDistance: 40).onEnded { drag in
                guard abs(drag.translation.height) > abs(drag.translation.width) else { return }
                // Swiping up moves to the next channel, as scrolling a list would.
                zap(by: drag.translation.height < 0 ? 1 : -1)
            }
        )
        .statusBarHidden(!chromeVisible)
        .persistentSystemOverlays(.hidden)
        .onAppear {
            model.play(channel.streamUrl, debounce: false)
            UIApplication.shared.isIdleTimerDisabled = true
            scheduleHide()
        }
        .onDisappear {
            model.stop()
            UIApplication.shared.isIdleTimerDisabled = false
        }
    }

    private var chrome: some View {
        VStack {
            HStack(spacing: 14) {
                ChannelLogo(url: channel.logoUrl, title: channel.title, size: 40)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 8) {
                        Text(channel.title).font(LumenFont.title).lineLimit(1)
                        if let quality = channel.quality { QualityTag(text: quality) }
                    }
                    Text("\(index + 1) of \(channels.count) · swipe up or down to change")
                        .font(LumenFont.caption)
                        .foregroundStyle(LumenColors.textSecondary)
                }
                Spacer()
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 17, weight: .semibold))
                        .frame(width: 44, height: 44)
                }
                .glass(cornerRadius: 22, fill: Color.black.opacity(0.4))
                .accessibilityLabel("Close")
            }
            .foregroundStyle(LumenColors.textPrimary)
            .padding(16)
            .glass(fill: Color.black.opacity(0.55))
            .padding(.horizontal, 16)

            Spacer()

            HStack(spacing: 16) {
                Button { zap(by: -1) } label: { Label("Previous", systemImage: "chevron.up") }
                Button { zap(by: 1) } label: { Label("Next", systemImage: "chevron.down") }
            }
            .buttonStyle(GlassButtonStyle())
            .padding(.bottom, 24)
        }
    }

    private func zap(by step: Int) {
        guard channels.count > 1 else { return }
        index = (index + step + channels.count) % channels.count
        model.play(channel.streamUrl, debounce: true)
        withAnimation(.easeOut(duration: 0.18)) { chromeVisible = true }
        scheduleHide()
    }

    private func toggleChrome() {
        withAnimation(.easeOut(duration: 0.18)) { chromeVisible.toggle() }
        if chromeVisible { scheduleHide() }
    }

    private func scheduleHide() {
        hideTask?.cancel()
        hideTask = Task {
            try? await Task.sleep(for: .seconds(4))
            guard !Task.isCancelled else { return }
            withAnimation(.easeOut(duration: 0.3)) { chromeVisible = false }
        }
    }
}

/// Owns the AVPlayer. Like `PlayerHolder` on the TV: when zapping, the old stream stops at
/// once (providers often allow one connection) and the new one starts after 350 ms, so
/// flicking past several channels doesn't open each of them.
@MainActor
@Observable
final class LivePlayerModel {
    let player = AVPlayer()
    private(set) var isBuffering = true
    private(set) var error: String?
    @ObservationIgnored private var pending: Task<Void, Never>?
    @ObservationIgnored private var observation: NSKeyValueObservation?
    @ObservationIgnored private var statusObservation: NSKeyValueObservation?

    init() {
        // Sound plays even with the silent switch on, as in other video apps.
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
        try? AVAudioSession.sharedInstance().setActive(true)
        player.automaticallyWaitsToMinimizeStalling = true
        observation = player.observe(\.timeControlStatus) { [weak self] player, _ in
            let playing = player.timeControlStatus == .playing
            Task { @MainActor in
                guard let self else { return }
                self.isBuffering = !playing && self.error == nil
            }
        }
    }

    func play(_ url: String, debounce: Bool) {
        pending?.cancel()
        player.replaceCurrentItem(with: nil)
        error = nil
        isBuffering = true
        pending = Task {
            if debounce {
                try? await Task.sleep(for: .milliseconds(350))
                guard !Task.isCancelled else { return }
            }
            guard let parsed = URL(string: url) else {
                error = "This channel's address can't be played."
                return
            }
            let item = AVPlayerItem(url: parsed)
            statusObservation = item.observe(\.status) { [weak self] item, _ in
                guard item.status == .failed else { return }
                Task { @MainActor in
                    self?.error = "This channel isn't playing right now. Try another one."
                    self?.isBuffering = false
                }
            }
            player.replaceCurrentItem(with: item)
            player.play()
        }
    }

    func stop() {
        pending?.cancel()
        player.pause()
        player.replaceCurrentItem(with: nil)
    }
}

/// Shows an AVPlayer through an AVPlayerLayer. (SwiftUI's VideoPlayer brings Apple's own
/// controls and gestures, which would fight the swipe-to-zap gesture.)
struct PlayerLayerView: UIViewRepresentable {
    let player: AVPlayer

    final class LayerView: UIView {
        override class var layerClass: AnyClass { AVPlayerLayer.self }
        var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
    }

    func makeUIView(context: Context) -> LayerView {
        let view = LayerView()
        view.playerLayer.player = player
        view.playerLayer.videoGravity = .resizeAspect
        view.backgroundColor = .black
        return view
    }

    func updateUIView(_ view: LayerView, context: Context) {
        view.playerLayer.player = player
    }
}
