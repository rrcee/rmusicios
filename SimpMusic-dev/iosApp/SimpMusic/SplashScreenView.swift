import SwiftUI

struct SplashScreenView: View {
    @State private var logoScale: CGFloat = 0.85
    @State private var logoOpacity: Double = 0.0
    @State private var splashBounce: CGFloat = 1.0
    @State private var shimmerOffset: CGFloat = -200
    @State private var isFinished: Bool = false
    var onFinish: () -> Void

    // Classic random yellow splash lines
    private let splashTexts = [
        "Now with synced lyrics!",
        "Pure vibes, zero ads!",
        "Powered by YouTube Music!",
        "100% Free & Open Source!",
        "Dynamic Island ready!"
    ]
    @State private var selectedSplashText: String = "Now with synced lyrics!"

    var body: some View {
        ZStack {
            // Iconic Mojang Studios Deep Red / Obsidian Background
            Color(red: 0.89, green: 0.15, blue: 0.21) // Mojang Red (#E32636)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer()

                // Mojang-style blocky geometric "r/music" logo
                ZStack {
                    HStack(spacing: 6) {
                        // "r/"
                        Text("r/")
                            .font(.system(size: 58, weight: .black, design: .monospaced))
                            .foregroundColor(.white)
                            .tracking(2)

                        // "music"
                        Text("music")
                            .font(.system(size: 58, weight: .black, design: .rounded))
                            .foregroundColor(.white)
                            .tracking(1)
                    }
                    .overlay(
                        // Shimmer sweep across the letters
                        Rectangle()
                            .fill(
                                LinearGradient(
                                    colors: [.clear, .white.opacity(0.6), .clear],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .frame(width: 60, height: 80)
                            .rotationEffect(.degrees(20))
                            .offset(x: shimmerOffset)
                            .mask(
                                HStack(spacing: 6) {
                                    Text("r/")
                                        .font(.system(size: 58, weight: .black, design: .monospaced))
                                    Text("music")
                                        .font(.system(size: 58, weight: .black, design: .rounded))
                                }
                            )
                    )

                    // Bouncing yellow Minecraft-style splash text
                    VStack {
                        Spacer()
                        HStack {
                            Spacer()
                            Text(selectedSplashText)
                                .font(.system(size: 15, weight: .heavy, design: .monospaced))
                                .foregroundColor(Color(red: 1.0, green: 1.0, blue: 0.2)) // Minecraft Yellow (#FFFF33)
                                .shadow(color: .black.opacity(0.8), radius: 2, x: 2, y: 2)
                                .rotationEffect(.degrees(-18))
                                .scaleEffect(splashBounce)
                                .offset(x: 20, y: 35)
                        }
                    }
                    .frame(width: 320, height: 100)
                }
                .scaleEffect(logoScale)
                .opacity(logoOpacity)

                Spacer()

                // Studios tagline
                Text("STUDIOS")
                    .font(.system(size: 13, weight: .bold, design: .monospaced))
                    .foregroundColor(.white.opacity(0.8))
                    .tracking(6)
                    .opacity(logoOpacity)
                    .padding(.bottom, 48)
            }
        }
        .onAppear {
            selectedSplashText = splashTexts.randomElement() ?? "Now with synced lyrics!"

            // 1. Zoom punch in
            withAnimation(.spring(response: 0.6, dampingFraction: 0.65)) {
                logoScale = 1.0
                logoOpacity = 1.0
            }

            // 2. Light shimmer sweep
            withAnimation(.easeInOut(duration: 1.0).delay(0.3)) {
                shimmerOffset = 220
            }

            // 3. Continuous yellow splash bounce
            withAnimation(.easeInOut(duration: 0.35).repeatForever(autoreverses: true)) {
                splashBounce = 1.15
            }

            // 4. Smooth dismissal after 2.4 seconds
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.4) {
                withAnimation(.easeOut(duration: 0.4)) {
                    onFinish()
                }
            }
        }
    }
}
