import SwiftUI

struct NowPlayingView: View {
    @Binding var isPresented: Bool
    @ObservedObject var player = AudioPlayerService.shared
    @State private var showLyrics = false
    @State private var showQueue = false
    @State private var isDraggingSlider = false
    @State private var dragPosition: Double = 0
    @State private var isEndlessQueueEnabled = true

    var body: some View {
        ZStack {
            // Dynamic colorful mesh gradient background
            MeshBackdropView(artworkUrl: player.currentTrack?.artworkUrl)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // MARK: - Grabber & Top Controls
                Capsule()
                    .fill(Color.white.opacity(0.3))
                    .frame(width: 38, height: 5)
                    .padding(.top, 12)
                    .padding(.bottom, 16)

                // Top Header Row
                HStack {
                    Button(action: {
                        withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                            isPresented = false
                        }
                    }) {
                        Image(systemName: "chevron.down")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(.white.opacity(0.85))
                            .frame(width: 44, height: 44)
                    }

                    Spacer()

                    // Segmented Pills: Now Playing vs Queue
                    HStack(spacing: 4) {
                        Button(action: { withAnimation { showQueue = false } }) {
                            Text("Now Playing")
                                .font(.system(size: 13, weight: !showQueue ? .bold : .medium))
                                .foregroundColor(!showQueue ? .white : .white.opacity(0.6))
                                .padding(.horizontal, 14)
                                .padding(.vertical, 6)
                                .background(Capsule().fill(!showQueue ? Color.white.opacity(0.18) : Color.clear))
                        }

                        Button(action: { withAnimation { showQueue = true } }) {
                            HStack(spacing: 6) {
                                Text("Queue")
                                    .font(.system(size: 13, weight: showQueue ? .bold : .medium))
                                    .foregroundColor(showQueue ? .white : .white.opacity(0.6))

                                // Equalizer indicator if playing
                                EqualizerBarMini()
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 6)
                            .background(Capsule().fill(showQueue ? Color.white.opacity(0.18) : Color.clear))
                        }
                    }
                    .padding(4)
                    .background(Capsule().fill(Color.black.opacity(0.25)))
                    .overlay(Capsule().stroke(Color.white.opacity(0.12), lineWidth: 0.8))

                    Spacer()

                    Button(action: {
                        withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                            showLyrics.toggle()
                        }
                    }) {
                        Image(systemName: showLyrics ? "quote.bubble.fill" : "quote.bubble")
                            .font(.system(size: 19, weight: .semibold))
                            .foregroundColor(showLyrics ? Color(red: 1.0, green: 0.25, blue: 0.35) : .white.opacity(0.85))
                            .frame(width: 44, height: 44)
                    }
                }
                .padding(.horizontal, 18)

                if showQueue {
                    // Queue Screen View
                    QueueListView(isEndlessEnabled: $isEndlessQueueEnabled)
                        .transition(.opacity)
                } else {
                    // Main Now Playing View
                    VStack(spacing: 20) {
                        Spacer()

                        // Large Centered Artwork Card
                        ZStack {
                            AsyncImage(url: URL(string: player.currentTrack?.artworkUrl ?? "")) { image in
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                            } placeholder: {
                                RoundedRectangle(cornerRadius: 22)
                                    .fill(LinearGradient(colors: [Color.red.opacity(0.8), Color.purple.opacity(0.7)], startPoint: .topLeading, endPoint: .bottomTrailing))
                            }
                            .frame(width: min(UIScreen.main.bounds.width - 64, 340), height: min(UIScreen.main.bounds.width - 64, 340))
                            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                            .shadow(color: .black.opacity(0.45), radius: 28, x: 0, y: 14)
                            .overlay(
                                RoundedRectangle(cornerRadius: 22, style: .continuous)
                                    .stroke(Color.white.opacity(0.18), lineWidth: 1)
                            )
                        }

                        // Lyrics Snippet (2-line animated preview)
                        VStack(spacing: 4) {
                            Text("I feel like I'm invincible, nothing in my way")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(.white)
                                .multilineTextAlignment(.center)
                            Text("Ain't nobody stoppin' me now")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(.white.opacity(0.65))
                                .multilineTextAlignment(.center)
                        }
                        .padding(.horizontal, 28)
                        .padding(.vertical, 8)

                        Spacer()

                        // Track Title, Artist & Animated Equalizer
                        HStack(alignment: .center) {
                            VStack(alignment: .leading, spacing: 4) {
                                HStack(spacing: 8) {
                                    Text(player.currentTrack?.title ?? "Starboy")
                                        .font(.system(size: 22, weight: .bold))
                                        .foregroundColor(.white)
                                        .lineLimit(1)

                                    if player.isPlaying {
                                        EqualizerWaveformMini()
                                    }
                                }

                                Text(player.currentTrack?.artist ?? "The Weeknd, Daft Punk")
                                    .font(.system(size: 16, weight: .medium))
                                    .foregroundColor(.white.opacity(0.72))
                                    .lineLimit(1)
                            }

                            Spacer()

                            Button(action: {}) {
                                Image(systemName: "ellipsis.circle.fill")
                                    .font(.system(size: 26))
                                    .foregroundColor(.white.opacity(0.8))
                            }
                        }
                        .padding(.horizontal, 28)

                        // Custom Waveform Seeker
                        VStack(spacing: 8) {
                            WaveformSeekBarView(
                                progress: isDraggingSlider ? dragPosition : (player.duration > 0 ? player.currentTime / player.duration : 0),
                                onSeek: { newProgress in
                                    player.seek(to: newProgress * player.duration)
                                }
                            )

                            // Time Labels
                            HStack {
                                Text(formatTime(player.currentTime))
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundColor(.white.opacity(0.6))
                                Spacer()
                                Text(formatTime(player.duration))
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundColor(.white.opacity(0.6))
                            }
                            .padding(.horizontal, 28)
                        }

                        // Playback Controls
                        HStack(spacing: 44) {
                            Button(action: { player.previous() }) {
                                Image(systemName: "backward.fill")
                                    .font(.system(size: 28))
                                    .foregroundColor(.white)
                            }

                            Button(action: { player.togglePlayPause() }) {
                                ZStack {
                                    Circle()
                                        .fill(Color.white)
                                        .frame(width: 72, height: 72)
                                        .shadow(color: Color.black.opacity(0.3), radius: 12, x: 0, y: 6)

                                    Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                                        .font(.system(size: 30))
                                        .foregroundColor(.black)
                                        .offset(x: player.isPlaying ? 0 : 2)
                                }
                            }

                            Button(action: { player.next() }) {
                                Image(systemName: "forward.fill")
                                    .font(.system(size: 28))
                                    .foregroundColor(.white)
                            }
                        }
                        .padding(.vertical, 12)

                        // Bottom Action Icons
                        HStack(spacing: 48) {
                            Button(action: {}) {
                                Image(systemName: "speaker.wave.3.fill")
                                    .font(.system(size: 18))
                                    .foregroundColor(.white.opacity(0.7))
                            }

                            Button(action: {}) {
                                Image(systemName: "shuffle")
                                    .font(.system(size: 18))
                                    .foregroundColor(.white.opacity(0.7))
                            }

                            Button(action: {}) {
                                Image(systemName: "repeat")
                                    .font(.system(size: 18))
                                    .foregroundColor(.white.opacity(0.7))
                            }
                        }
                        .padding(.bottom, 28)
                    }
                }
            }
        }
    }

    private func formatTime(_ time: Double) -> String {
        let mins = Int(time) / 60
        let secs = Int(time) % 60
        return String(format: "%d:%02d", mins, secs)
    }
}

// MARK: - Queue List View
struct QueueListView: View {
    @Binding var isEndlessEnabled: Bool
    @ObservedObject var player = AudioPlayerService.shared

    let upcomingTracks: [AudioTrack] = [
        AudioTrack(id: "q1", title: "Sunflower", artist: "Post Malone, Swae Lee", artworkUrl: "https://i.ytimg.com/vi/ApXoWvfEYVU/hqdefault.jpg", streamUrl: "", duration: 158),
        AudioTrack(id: "q2", title: "What's Up Danger", artist: "Blackway & Black Caviar", artworkUrl: "https://i.ytimg.com/vi/9n5G0qF404I/hqdefault.jpg", streamUrl: "", duration: 222),
        AudioTrack(id: "q3", title: "Calling", artist: "Metro Boomin, Swae Lee", artworkUrl: "https://i.ytimg.com/vi/v=8n5G0qF404I/hqdefault.jpg", streamUrl: "", duration: 219),
        AudioTrack(id: "q4", title: "Blinding Lights", artist: "The Weeknd", artworkUrl: "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg", streamUrl: "", duration: 200)
    ]

    var body: some View {
        VStack(spacing: 16) {
            // Endless Queue Switch Row
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Endless Queue")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                    Text("Automatically add similar tracks")
                        .font(.system(size: 12))
                        .foregroundColor(.gray)
                }

                Spacer()

                Toggle("", isOn: $isEndlessEnabled)
                    .labelsHidden()
                    .tint(Color(red: 1.0, green: 0.22, blue: 0.35))
            }
            .padding(.horizontal, 20)
            .padding(.top, 14)

            Divider().background(Color.white.opacity(0.15))

            // Track list
            ScrollView(showsIndicators: false) {
                VStack(spacing: 8) {
                    ForEach(upcomingTracks) { track in
                        HStack(spacing: 12) {
                            AsyncImage(url: URL(string: track.artworkUrl)) { img in
                                img.resizable().aspectRatio(contentMode: .fill)
                            } placeholder: {
                                Color.gray.opacity(0.2)
                            }
                            .frame(width: 48, height: 48)
                            .cornerRadius(8)

                            VStack(alignment: .leading, spacing: 3) {
                                Text(track.title)
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundColor(.white)
                                    .lineLimit(1)
                                Text(track.artist)
                                    .font(.system(size: 12))
                                    .foregroundColor(.gray)
                                    .lineLimit(1)
                            }

                            Spacer()

                            Image(systemName: "line.3.horizontal")
                                .font(.system(size: 18))
                                .foregroundColor(.white.opacity(0.4))
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 6)
                    }
                }
            }
        }
    }
}

// MARK: - Equalizer Mini Animations
struct EqualizerBarMini: View {
    @State private var animating = false

    var body: some View {
        HStack(spacing: 2) {
            ForEach(0..<3) { idx in
                RoundedRectangle(cornerRadius: 1)
                    .fill(Color(red: 1.0, green: 0.25, blue: 0.35))
                    .frame(width: 2.5, height: animating ? CGFloat(4 + (idx * 4)) : CGFloat(12 - (idx * 3)))
            }
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 0.5).repeatForever(autoreverses: true)) {
                animating = true
            }
        }
    }
}

struct EqualizerWaveformMini: View {
    @State private var animating = false

    var body: some View {
        HStack(spacing: 2) {
            ForEach(0..<4) { idx in
                RoundedRectangle(cornerRadius: 1.5)
                    .fill(Color(red: 1.0, green: 0.25, blue: 0.35))
                    .frame(width: 3, height: animating ? CGFloat(6 + ((idx % 3) * 5)) : CGFloat(16 - ((idx % 3) * 4)))
            }
        }
        .frame(height: 18)
        .onAppear {
            withAnimation(.easeInOut(duration: 0.45).repeatForever(autoreverses: true)) {
                animating = true
            }
        }
    }
}

// MARK: - Waveform Seek Bar
struct WaveformSeekBarView: View {
    var progress: Double
    var onSeek: (Double) -> Void

    var body: some View {
        GeometryReader { geo in
            let barCount = 42
            HStack(spacing: 3) {
                ForEach(0..<barCount, id: \.self) { i in
                    let barProgress = Double(i) / Double(barCount)
                    let isPlayed = barProgress <= progress
                    let heightFactor = sin(Double(i) * 0.4) * 0.4 + 0.6

                    RoundedRectangle(cornerRadius: 2)
                        .fill(isPlayed ? Color.white : Color.white.opacity(0.25))
                        .frame(
                            width: (geo.size.width - CGFloat(barCount * 3)) / CGFloat(barCount),
                            height: CGFloat(14 + heightFactor * 18)
                        )
                }
            }
            .frame(height: 36)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let newProgress = min(max(value.location.x / geo.size.width, 0), 1)
                        onSeek(Double(newProgress))
                    }
            )
        }
        .frame(height: 36)
        .padding(.horizontal, 28)
    }
}

// MARK: - Mesh Gradient Backdrop
struct MeshBackdropView: View {
    let artworkUrl: String?

    var body: some View {
        ZStack {
            Color.black

            LinearGradient(
                colors: [
                    Color(red: 0.8, green: 0.15, blue: 0.25).opacity(0.7),
                    Color(red: 0.15, green: 0.05, blue: 0.35).opacity(0.8),
                    Color(red: 0.02, green: 0.02, blue: 0.08)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )

            Circle()
                .fill(Color(red: 0.9, green: 0.2, blue: 0.35).opacity(0.45))
                .blur(radius: 80)
                .offset(x: -80, y: -120)

            Circle()
                .fill(Color(red: 0.3, green: 0.1, blue: 0.8).opacity(0.4))
                .blur(radius: 90)
                .offset(x: 100, y: 140)
        }
    }
}
