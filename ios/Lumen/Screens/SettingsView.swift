import SwiftUI

struct SettingsView: View {
    var body: some View {
        NavigationStack {
            List {
                Section("Check this device") {
                    NavigationLink("Test video playback") { TestPlayerView() }
                }
                Section("About") {
                    LabeledContent("Version", value: Bundle.main.version)
                    Text("Lumen is a player for your own TV subscription. It doesn't provide any channels or content.")
                        .foregroundStyle(LumenColors.textSecondary)
                    NavigationLink("Licences") { LicencesView() }
                }
            }
            .font(LumenFont.body)
            .scrollContentBackground(.hidden)
            .background(AmbientBackground())
            .navigationTitle("Settings")
        }
    }
}

struct LicencesView: View {
    var body: some View {
        List {
            Section("Inter") {
                Text("Typeface by Rasmus Andersson, under the SIL Open Font License 1.1.")
            }
        }
        .font(LumenFont.body)
        .scrollContentBackground(.hidden)
        .background(AmbientBackground())
        .navigationTitle("Licences")
    }
}

extension Bundle {
    /// "0.1.0 (1)".
    var version: String {
        let short = infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let build = infoDictionary?["CFBundleVersion"] as? String ?? "?"
        return "\(short) (\(build))"
    }
}
