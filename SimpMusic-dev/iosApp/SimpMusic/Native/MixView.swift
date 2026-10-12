import SwiftUI

struct MixView: View {
    @ObservedObject var player = AudioPlayerService.shared

    // Mix items matching screenshot 6 with 3-column layout
    let mixes: [MixCardData] = [
        MixCardData(title: "Replay Mix", subtitle: "NafeesIsboujee, Ju...", topPills: ["Replay", "Mix"], gradient: [Color(red: 0.95, green: 0.25, blue: 0.35), Color(red: 0.2, green: 0.1, blue: 0.4)]),
        MixCardData(title: "Archive Mix", subtitle: "Roddy Ricch, Centr...", topPills: ["Archive", "Mix"], gradient: [Color(red: 0.15, green: 0.6, blue: 0.45), Color(red: 0.05, green: 0.2, blue: 0.15)]),
        MixCardData(title: "My Mix 1", subtitle: "Juice WRLD, Kee Nc...", topPills: ["My Mix", "01"], gradient: [Color(red: 0.8, green: 0.5, blue: 0.2), Color(red: 0.2, green: 0.1, blue: 0.05)]),
        MixCardData(title: "My Mix 2", subtitle: "King Von, Travis Sc...", topPills: ["My Mix", "02"], gradient: [Color(red: 0.3, green: 0.2, blue: 0.5), Color(red: 0.1, green: 0.05, blue: 0.2)]),
        MixCardData(title: "My Mix 3", subtitle: "NafeesIsboujee, SU...", topPills: ["My Mix", "03"], gradient: [Color(red: 0.8, green: 0.7, blue: 0.2), Color(red: 0.2, green: 0.2, blue: 0.05)]),
        MixCardData(title: "My Mix 4", subtitle: "Post Malone, The Ki...", topPills: ["My Mix", "04"], gradient: [Color(red: 0.2, green: 0.2, blue: 0.25), Color(red: 0.05, green: 0.05, blue: 0.08)]),
        MixCardData(title: "My Mix 5", subtitle: "Dwele, 2Pac, EsDee...", topPills: ["My Mix", "05"], gradient: [Color(red: 0.3, green: 0.3, blue: 0.35), Color(red: 0.1, green: 0.1, blue: 0.12)]),
        MixCardData(title: "My Mix 6", subtitle: "A Boogie wit da Ho...", topPills: ["My Mix", "06"], gradient: [Color(red: 0.6, green: 0.4, blue: 0.2), Color(red: 0.15, green: 0.1, blue: 0.05)]),
        MixCardData(title: "My Mix 7", subtitle: "Brian Tyler, Ludwig...", topPills: ["My Mix", "07"], gradient: [Color(red: 0.4, green: 0.3, blue: 0.5), Color(red: 0.1, green: 0.05, blue: 0.15)])
    ]

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 20) {
                // Large Header Title
                Text("Mix for you")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 20)
                    .padding(.top, 54)

                // 3-Column Mix Grid exactly like Screenshot 6
                LazyVGrid(columns: [
                    GridItem(.flexible(), spacing: 14),
                    GridItem(.flexible(), spacing: 14),
                    GridItem(.flexible(), spacing: 14)
                ], spacing: 20) {
                    ForEach(mixes) { mix in
                        Button(action: {
                            player.playTrack(AudioTrack(
                                id: mix.id.uuidString,
                                title: mix.title,
                                artist: mix.subtitle,
                                artworkUrl: "https://i.ytimg.com/vi/34Na4j8AVgA/maxresdefault.jpg",
                                streamUrl: "",
                                duration: 210
                            ))
                        }) {
                            VStack(alignment: .leading, spacing: 6) {
                                // Mix Card Tile with typography overlay
                                ZStack(alignment: .topLeading) {
                                    LinearGradient(
                                        colors: mix.gradient,
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )

                                    // Top text banner
                                    VStack(alignment: .leading, spacing: 2) {
                                        HStack {
                                            Text(mix.topPills[0])
                                                .font(.system(size: 13, weight: .bold))
                                                .foregroundColor(.white)
                                            Spacer()
                                            Text(mix.topPills[1])
                                                .font(.system(size: 13, weight: .bold))
                                                .foregroundColor(.white.opacity(0.85))
                                        }
                                        .padding(.horizontal, 8)
                                        .padding(.top, 8)

                                        Spacer()

                                        // Subtle grid lines / bottom tag
                                        HStack {
                                            Spacer()
                                            Text("Mix")
                                                .font(.system(size: 12, weight: .semibold))
                                                .foregroundColor(.white.opacity(0.6))
                                                .padding(6)
                                        }
                                    }
                                }
                                .frame(width: (UIScreen.main.bounds.width - 68) / 3, height: (UIScreen.main.bounds.width - 68) / 3)
                                .cornerRadius(14)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14)
                                        .stroke(Color.white.opacity(0.12), lineWidth: 0.8)
                                )

                                Text(mix.title)
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.white)
                                    .lineLimit(1)
                                    .frame(maxWidth: .infinity, alignment: .leading)

                                Text(mix.subtitle)
                                    .font(.system(size: 11))
                                    .foregroundColor(.gray)
                                    .lineLimit(1)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 20)

                Spacer().frame(height: 120)
            }
        }
        .background(Color.black.ignoresSafeArea())
    }
}

struct MixCardData: Identifiable {
    let id = UUID()
    let title: String
    let subtitle: String
    let topPills: [String]
    let gradient: [Color]
}
