import SwiftUI

/// Liquid Glass Style Modifier & View
/// Inspired by deepika-builds/liquid-glass:
/// Multi-layer frosted glass, inner and outer specular reflections, chromatic border gradient,
/// dynamic light refraction, and active tab radial glow.
struct LiquidGlassBackground: ViewModifier {
    var cornerRadius: CGFloat = 36
    var interactiveGlow: Bool = true
    var glowColor: Color = Color(red: 0.95, green: 0.22, blue: 0.28) // Apple Music vibrant red/crimson
    var isDark: Bool = true

    func body(content: Content) -> some View {
        content
            .background(
                ZStack {
                    // 1. Ultra-thin blur material background with deep tint
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .fill(.ultraThinMaterial)

                    // 2. Base dark tint to preserve contrast and prevent washing out ("đục đen")
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color.black.opacity(0.45),
                                    Color.black.opacity(0.65)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )

                    // 3. Specular highlight inner sheen (light hitting top curvature)
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color.white.opacity(0.18),
                                    Color.white.opacity(0.04),
                                    Color.clear
                                ],
                                startPoint: .top,
                                endPoint: .center
                            )
                        )

                    // 4. Subtle chromatic refraction rim gradient
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .strokeBorder(
                            LinearGradient(
                                stops: [
                                    .init(color: Color.white.opacity(0.60), location: 0.0),
                                    .init(color: Color.white.opacity(0.20), location: 0.2),
                                    .init(color: Color.cyan.opacity(0.15), location: 0.4),
                                    .init(color: Color.purple.opacity(0.15), location: 0.6),
                                    .init(color: Color.white.opacity(0.10), location: 0.8),
                                    .init(color: Color.white.opacity(0.35), location: 1.0)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 1.2
                        )
                }
            )
            .shadow(color: Color.black.opacity(0.35), radius: 24, x: 0, y: 12)
            .shadow(color: Color.black.opacity(0.15), radius: 6, x: 0, y: 3)
    }
}

extension View {
    func liquidGlass(cornerRadius: CGFloat = 36, glowColor: Color = Color(red: 0.95, green: 0.22, blue: 0.28)) -> some View {
        self.modifier(LiquidGlassBackground(cornerRadius: cornerRadius, glowColor: glowColor))
    }
}
