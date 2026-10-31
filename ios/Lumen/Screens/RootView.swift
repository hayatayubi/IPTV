import SwiftUI

/// The four tabs the TV app has in its top bar, as an iOS tab bar (a sidebar on iPad, where
/// `.sidebarAdaptable` lets people switch).
struct RootView: View {
    var body: some View {
        TabView {
            Tab("Live TV", systemImage: "tv") {
                EmptyTab(
                    title: "Live TV",
                    message: "Add your playlist or Xtream login to see your channels."
                )
            }
            Tab("Movies", systemImage: "film") {
                EmptyTab(title: "Movies", message: "Films from your Xtream login show up here.")
            }
            Tab("Series", systemImage: "play.rectangle.on.rectangle") {
                EmptyTab(title: "Series", message: "Series from your Xtream login show up here.")
            }
            Tab("Settings", systemImage: "gearshape") {
                SettingsView()
            }
        }
        .tabViewStyle(.sidebarAdaptable)
        .tint(.white)
    }
}

/// A tab with nothing in it yet: Lumen ships with no channels (an App Store rule), so this is
/// what everyone sees first.
struct EmptyTab: View {
    let title: String
    let message: String
    @State private var addingPlaylist = false

    var body: some View {
        NavigationStack {
            ZStack {
                AmbientBackground()
                VStack(spacing: 16) {
                    Image(systemName: "sparkles.tv")
                        .font(.system(size: 56, weight: .light))
                        .foregroundStyle(LumenColors.textSecondary)
                    Text("Nothing here yet")
                        .font(LumenFont.title)
                        .foregroundStyle(LumenColors.textPrimary)
                    Text(message)
                        .font(LumenFont.body)
                        .foregroundStyle(LumenColors.textSecondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 360)
                    Button("Add playlist") { addingPlaylist = true }
                        .buttonStyle(PrimaryButtonStyle())
                        .padding(.top, 8)
                }
                .padding(24)
            }
            .navigationTitle(title)
            .sheet(isPresented: $addingPlaylist) { AddPlaylistView() }
        }
    }
}
