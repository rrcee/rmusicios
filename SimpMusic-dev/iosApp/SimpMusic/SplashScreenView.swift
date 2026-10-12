import SwiftUI

struct SplashScreenView: View {
    @State private var logoScale: CGFloat = 0.92
    @State private var logoOpacity: Double = 0.0
    @State private var shimmerOffset: CGFloat = -180
    @State private var isFinished: Bool = false
    var onFinish: () -> Void

    var body: some View {
        ZStack {
            // Pure AMOLED Black matching reference screenshot media_1791796045105_9acb2ed1.png
            Color.black
                .ignoresSafeArea()

            VStack(spacing: 16) {
                // "r/music" clean bold title
                ZStack {
                    Text("r/music")
                        .font(.system(size: 48, weight: .bold, design: .default))
                        .foregroundColor(.white)
                        .tracking(-0.5)

                    // Subtle liquid glass shimmer sweep
                    Rectangle()
                        .fill(
                            LinearGradient(
                                colors: [.clear, .white.opacity(0.5), .clear],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: 60, height: 60)
                        .rotationEffect(.degrees(20))
                        .offset(x: shimmerOffset)
                        .mask(
                            Text("r/music")
                                .font(.system(size: 48, weight: .bold, design: .default))
                                .tracking(-0.5)
                        )
                }

                // Subtle minimal accent rule as shown in screenshot
                RoundedRectangle(cornerRadius: 1)
                    .fill(Color.white.opacity(0.2))
                    .frame(width: 90, height: 2)
            }
            .scaleEffect(logoScale)
            .opacity(logoOpacity)
        }
        .onAppear {
            // Smooth entrance animation
            withAnimation(.spring(response: 0.55, dampingFraction: 0.75)) {
                logoScale = 1.0
                logoOpacity = 1.0
            }

            // Glassmorphic specular shimmer
            withAnimation(.easeInOut(duration: 0.8).delay(0.25)) {
                shimmerOffset = 180
            }

            // Quick transition to main app after 1.1s
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.1) {
                withAnimation(.easeOut(duration: 0.35)) {
                    onFinish()
                }
            }
        }
    }
}
