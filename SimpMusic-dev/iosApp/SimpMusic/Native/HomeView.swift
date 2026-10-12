import SwiftUI

struct HomeView: View {
    @ObservedObject var player = AudioPlayerService.shared
    @State private var selectedFilter: String = "All"

    let filterChips = ["All", "Relax", "Sleep", "Energize", "Sad", "Romance"]

    // Listen Again (large horizontal rectangular cards)
    let listenAgainItems: [ListenAgainItem] = [
        ListenAgainItem(id: "1", title: "Spider-Man: Into the Spider-Verse", subtitle: "Various Artists • Soundtrack", artworkUrl: "https://i.ytimg.com/vi/34Na4j8AVgA/maxresdefault.jpg", gradientColors: [Color(red: 0.85, green: 0.15, blue: 0.25), Color(red: 0.12, green: 0.12, blue: 0.35)]),
        ListenAgainItem(id: "2", title: "After Hours (Deluxe)", subtitle: "The Weeknd • Album", artworkUrl: "https://i.ytimg.com/vi/4NRXx6U8ABQ/maxresdefault.jpg", gradientColors: [Color(red: 0.75, green: 0.05, blue: 0.1), Color(red: 0.15, green: 0.02, blue: 0.05)]),
        ListenAgainItem(id: "3", title: "Starboy", subtitle: "The Weeknd • Hit tracks", artworkUrl: "https://i.ytimg.com/vi/XXYlFuWEuKI/maxresdefault.jpg", gradientColors: [Color(red: 0.95, green: 0.65, blue: 0.1), Color(red: 0.2, green: 0.05, blue: 0.15)]),
        ListenAgainItem(id: "4", title: "Heroes & Villains", subtitle: "Metro Boomin • Hip-Hop", artworkUrl: "https://i.ytimg.com/vi/uPD0QOGUxuo/maxresdefault.jpg", gradientColors: [Color(red: 0.2, green: 0.5, blue: 0.9), Color(red: 0.05, green: 0.1, blue: 0.2)])
    ]

    // Recently Played 3-column square items
    let recentlyPlayed: [AudioTrack] = [
        AudioTrack(id: "r1", title: "Sunflower", artist: "Post Malone, Swae Lee", artworkUrl: "https://i.ytimg.com/vi/ApXoWvfEYVU/hqdefault.jpg", streamUrl: "", duration: 158),
        AudioTrack(id: "r2", title: "What's Up Danger", artist: "Blackway & Black Caviar", artworkUrl: "https://i.ytimg.com/vi/9n5G0qF404I/hqdefault.jpg", streamUrl: "", duration: 222),
        AudioTrack(id: "r3", title: "Calling", artist: "Metro Boomin, Swae Lee", artworkUrl: "https://i.ytimg.com/vi/v=8n5G0qF404I/hqdefault.jpg", streamUrl: "", duration: 219),
        AudioTrack(id: "r4", title: "Blinding Lights", artist: "The Weeknd", artworkUrl: "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg", streamUrl: "", duration: 200),
        AudioTrack(id: "r5", title: "Save Your Tears", artist: "The Weeknd", artworkUrl: "https://i.ytimg.com/vi/XXYlFuWEuKI/hqdefault.jpg", streamUrl: "", duration: 215),
        AudioTrack(id: "r6", title: "Die For You", artist: "The Weeknd", artworkUrl: "https://i.ytimg.com/vi/uPD0QOGUxuo/hqdefault.jpg", streamUrl: "", duration: 260)
    ]

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 24) {
                // MARK: - Apple Music Top Header
                HStack(alignment: .center) {
                    Text("Listen Now")
                        .font(.system(size: 32, weight: .bold))
                        .foregroundColor(.white)

                    Spacer()

                    HStack(spacing: 18) {
                        Button(action: {}) {
                            Image(systemName: "bell")
                                .font(.system(size: 19, weight: .medium))
                                .foregroundColor(.white.opacity(0.85))
                        }

                        Button(action: {}) {
                            Image(systemName: "clock.arrow.circlepath")
                                .font(.system(size: 19, weight: .medium))
                                .foregroundColor(.white.opacity(0.85))
                        }

                        Button(action: {}) {
                            Image(systemName: "gearshape")
                                .font(.system(size: 19, weight: .medium))
                                .foregroundColor(.white.opacity(0.85))
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 54)

                // MARK: - Category Filter Chips Row (All, Relax, Sleep, Energize, Sad, Romance)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(filterChips, id: \.self) { chip in
                            let isSelected = selectedFilter == chip
                            Button(action: {
                                withAnimation(.spring(response: 0.3, dampingFraction: 0.75)) {
                                    selectedFilter = chip
                                }
                            }) {
                                Text(chip)
                                    .font(.system(size: 14, weight: isSelected ? .semibold : .medium))
                                    .foregroundColor(isSelected ? .black : .white.opacity(0.9))
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 8)
                                    .background(
                                        Capsule()
                                            .fill(isSelected ? Color.white : Color.white.opacity(0.12))
                                    )
                                    .overlay(
                                        Capsule()
                                            .stroke(Color.white.opacity(isSelected ? 0.0 : 0.15), lineWidth: 0.8)
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 20)
                }

                // MARK: - Listen Again (Wide Featured Cards)
                VStack(alignment: .leading, spacing: 14) {
                    Text("Listen again")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 20)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 16) {
                            ForEach(listenAgainItems) { item in
                                ListenAgainCard(item: item, onTap: {
                                    player.playTrack(AudioTrack(
                                        id: item.id,
                                        title: item.title,
                                        artist: item.subtitle,
                                        artworkUrl: item.artworkUrl,
                                        streamUrl: "",
                                        duration: 210
                                    ))
                                })
                            }
                        }
                        .padding(.horizontal, 20)
                    }
                }

                // MARK: - Recently Played (3-column layout matching screenshot)
                VStack(alignment: .leading, spacing: 14) {
                    Text("Recently Played")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 20)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 14) {
                            ForEach(recentlyPlayed) { track in
                                Button(action: { player.playTrack(track) }) {
                                    VStack(alignment: .leading, spacing: 6) {
                                        AsyncImage(url: URL(string: track.artworkUrl)) { img in
                                            img.resizable().aspectRatio(contentMode: .fill)
                                        } placeholder: {
                                            Color.gray.opacity(0.25)
                                        }
                                        .frame(width: 110, height: 110)
                                        .cornerRadius(12)
                                        .clipped()

                                        Text(track.title)
                                            .font(.system(size: 13, weight: .semibold))
                                            .foregroundColor(.white)
                                            .lineLimit(1)
                                            .frame(width: 110, alignment: .leading)

                                        Text(track.artist)
                                            .font(.system(size: 11))
                                            .foregroundColor(.gray)
                                            .lineLimit(1)
                                            .frame(width: 110, alignment: .leading)
                                    }
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 20)
                    }
                }

                // MARK: - Made For You Row
                VStack(alignment: .leading, spacing: 14) {
                    Text("Made For You")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 20)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 14) {
                            ForEach(1...5, id: \.self) { idx in
                                VStack(alignment: .leading, spacing: 8) {
                                    ZStack(alignment: .bottomLeading) {
                                        LinearGradient(
                                            colors: [
                                                Color(red: 0.2 + Double(idx) * 0.1, green: 0.1, blue: 0.4),
                                                Color(red: 0.9, green: 0.2, blue: 0.3)
                                            ],
                                            startPoint: .topLeading,
                                            endPoint: .bottomTrailing
                                        )
                                        .frame(width: 140, height: 140)
                                        .cornerRadius(14)

                                        Image(systemName: "music.note.list")
                                            .font(.system(size: 24))
                                            .foregroundColor(.white.opacity(0.8))
                                            .padding(12)
                                    }

                                    Text("Daily Mix \(idx)")
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundColor(.white)
                                        .lineLimit(1)

                                    Text("Tailored for you")
                                        .font(.system(size: 12))
                                        .foregroundColor(.gray)
                                        .lineLimit(1)
                                }
                                .frame(width: 140)
                            }
                        }
                        .padding(.horizontal, 20)
                    }
                }

                // Padding to allow scrolling above floating dock and miniplayer
                Spacer().frame(height: 120)
            }
        }
        .background(Color.black.ignoresSafeArea())
    }
}

// MARK: - Data Models & Supporting Views
struct ListenAgainItem: Identifiable {
    let id: String
    let title: String
    let subtitle: String
    let artworkUrl: String
    let gradientColors: [Color]
}

struct ListenAgainCard: View {
    let item: ListenAgainItem
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            ZStack(alignment: .bottomLeading) {
                // Background artwork / gradient
                AsyncImage(url: URL(string: item.artworkUrl)) { img in
                    img.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    LinearGradient(colors: item.gradientColors, startPoint: .topLeading, endPoint: .bottomTrailing)
                }
                .frame(width: 250, height: 155)
                .clipped()

                // Dark vignette overlay
                LinearGradient(
                    colors: [Color.clear, Color.black.opacity(0.85)],
                    startPoint: .top,
                    endPoint: .bottom
                )

                // Info overlay
                VStack(alignment: .leading, spacing: 3) {
                    Text(item.title)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)

                    Text(item.subtitle)
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.8))
                        .lineLimit(1)
                }
                .padding(14)
            }
            .frame(width: 250, height: 155)
            .cornerRadius(16)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color.white.opacity(0.12), lineWidth: 0.8)
            )
            .shadow(color: Color.black.opacity(0.3), radius: 10, x: 0, y: 6)
        }
        .buttonStyle(.plain)
    }
}
