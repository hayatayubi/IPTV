import SwiftUI

/// The same palette as the TV app (`LumenColors` in the Android code): dark, a softly coloured
/// ambient background, frosted glass for bars and panels, blue only as a small accent.
enum LumenColors {
    static let background = Color(hex: 0x0D0F14)
    static let surface = Color(hex: 0x1E2129)

    static let glass = Color.white.opacity(0.07)
    static let glassStrong = Color.white.opacity(0.13)
    static let glassEdge = Color.white.opacity(0.10)

    static let ambientCool = Color(hex: 0x26406E)
    static let ambientWarm = Color(hex: 0x5C3B1F)

    static let textPrimary = Color(hex: 0xF5F5F7)
    static let textSecondary = Color(hex: 0xA1A1A6)
    static let textTertiary = Color(hex: 0x6E6E73)

    static let accent = Color(hex: 0x0A84FF)
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

/// Inter, bundled in Resources/Fonts (OFL, see third_party/inter), as on the TV.
enum LumenFont {
    static func inter(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .custom("Inter", size: size).weight(weight)
    }

    static let largeTitle = inter(32, .semibold)
    static let title = inter(22, .semibold)
    static let headline = inter(17, .semibold)
    static let body = inter(16)
    static let caption = inter(13, .medium)
}

/// The dark background with two big soft glows (cool top left, warm bottom right), so screens
/// aren't flat black. Same idea as `Ambient` in the TV app.
struct AmbientBackground: View {
    var tint: Color = LumenColors.ambientCool

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            ZStack {
                LumenColors.background
                RadialGradient(
                    colors: [tint.opacity(0.55), tint.opacity(0.18), .clear],
                    center: UnitPoint(x: 0.15, y: 0),
                    startRadius: 0, endRadius: max(w, 400) * 0.8
                )
                RadialGradient(
                    colors: [LumenColors.ambientWarm.opacity(0.40), LumenColors.ambientWarm.opacity(0.12), .clear],
                    center: UnitPoint(x: 0.9, y: 1),
                    startRadius: 0, endRadius: max(w, 400) * 0.7
                )
            }
        }
        .ignoresSafeArea()
        .animation(.easeOut(duration: 0.7), value: tint)
    }
}

extension View {
    /// Frosted glass, like `Modifier.glass()` on the TV: a light translucent fill and a faint edge.
    /// (On iPhone a real `.ultraThinMaterial` blur would be affordable too; the flat fill keeps
    /// both apps looking the same.)
    func glass(cornerRadius: CGFloat = 20, fill: Color = LumenColors.glass) -> some View {
        background(fill, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .strokeBorder(LumenColors.glassEdge, lineWidth: 1)
            )
    }
}

/// The white pill (Play, Add playlist): white with dark text, as focus looks on the TV.
struct PrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(LumenFont.headline)
            .foregroundStyle(LumenColors.background)
            .padding(.horizontal, 24)
            .padding(.vertical, 12)
            .background(.white, in: Capsule())
            .opacity(configuration.isPressed ? 0.75 : 1)
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.easeOut(duration: 0.18), value: configuration.isPressed)
    }
}

/// The glass pill for everything else.
struct GlassButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(LumenFont.headline)
            .foregroundStyle(LumenColors.textPrimary)
            .padding(.horizontal, 22)
            .padding(.vertical, 12)
            .glass(cornerRadius: 999, fill: configuration.isPressed ? LumenColors.glassStrong : LumenColors.glass)
    }
}
