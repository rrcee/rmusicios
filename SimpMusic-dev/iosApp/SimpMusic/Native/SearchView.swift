import SwiftUI

struct SearchView: View {
    @State private var query = ""
    @ObservedObject var player = AudioPlayerService.shared

    // Apple Music Browse categories matching screenshot 5
    let categories: [SearchCategory] = [
        SearchCategory(title: "Spatial Audio", gradient: [Color(red: 0.1, green: 0.4, blue: 0.8), Color(red: 0.05, green: 0.15, blue: 0.4)], icon: "waveform"),
        SearchCategory(title: "Charts", gradient: [Color(red: 0.9, green: 0.2, blue: 0.4), Color(red: 0.5, green: 0.05, blue: 0.2)], icon: "chart.bar.fill"),
        SearchCategory(title: "Hip-Hop", gradient: [Color(red: 0.9, green: 0.5, blue: 0.1), Color(red: 0.4, green: 0.2, blue: 0.05)], icon: "music.mic"),
        SearchCategory(title: "Pop", gradient: [Color(red: 0.85, green: 0.15, blue: 0.6), Color(red: 0.4, green: 0.05, blue: 0.3)], icon: "sparkles"),
        SearchCategory(title: "Chill", gradient: [Color(red: 0.1, green: 0.7, blue: 0.7), Color(red: 0.05, green: 0.3, blue: 0.35)], icon: "leaf.fill"),
        SearchCategory(title: "Dance & Electronic", gradient: [Color(red: 0.6, green: 0.2, blue: 0.9), Color(red: 0.25, green: 0.05, blue: 0.5)], icon: "bolt.fill"),
        SearchCategory(title: "Rock", gradient: [Color(red: 0.8, green: 0.1, blue: 0.2), Color(red: 0.3, green: 0.05, blue: 0.05)], icon: "guitars.fill"),
        SearchCategory(title: "Focus", gradient: [Color(red: 0.2, green: 0.4, blue: 0.6), Color(red: 0.1, green: 0.2, blue: 0.3)], icon: "brain.head.profile"),
        SearchCategory(title: "Workout", gradient: [Color(red: 0.95, green: 0.3, blue: 0.1), Color(red: 0.5, green: 0.1, blue: 0.05)], icon: "figure.run"),
        SearchCategory(title: "Sleep", gradient: [Color(red: 0.15, green: 0.15, blue: 0.45), Color(red: 0.05, green: 0.05, blue: 0.2)], icon: "moon.stars.fill")
    ]

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 20) {
                // Large Header Title
                Text("Search")
                    .font(.system(size: 34, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 20)
                    .padding(.top, 54)

                // Rounded Apple Music Search Field
                HStack(spacing: 12) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 18))
                        .foregroundColor(.gray)

                    TextField("Artists, Songs, Lyrics, and More", text: $query)
                        .font(.system(size: 16))
                        .foregroundColor(.white)

                    if !query.isEmpty {
                        Button(action: { query = "" }) {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(.gray)
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(Color.white.opacity(0.12))
                .cornerRadius(12)
                .padding(.horizontal, 20)

                // Browse Categories Section
                VStack(alignment: .leading, spacing: 14) {
                    Text("Browse Categories")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 20)

                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                        ForEach(categories) { category in
                            CategoryCard(category: category)
                        }
                    }
                    .padding(.horizontal, 20)
                }

                Spacer().frame(height: 120)
            }
        }
        .background(Color.black.ignoresSafeArea())
    }
}

struct SearchCategory: Identifiable {
    let id = UUID()
    let title: String
    let gradient: [Color]
    let icon: String
}

struct CategoryCard: View {
    let category: SearchCategory

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(
                colors: category.gradient,
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )

            // Category title
            Text(category.title)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.white)
                .lineLimit(2)
                .padding(14)

            // Right-aligned icon/graphic
            VStack {
                HStack {
                    Spacer()
                    Image(systemName: category.icon)
                        .font(.system(size: 28))
                        .foregroundColor(.white.opacity(0.35))
                        .rotationEffect(.degrees(12))
                        .padding(12)
                }
                Spacer()
            }
        }
        .frame(height: 104)
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color.white.opacity(0.15), lineWidth: 0.8)
        )
        .shadow(color: Color.black.opacity(0.25), radius: 6, x: 0, y: 3)
    }
}
