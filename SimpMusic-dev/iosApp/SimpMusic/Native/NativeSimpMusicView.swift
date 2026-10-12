import SwiftUI
import Combine

struct NativeSimpMusicView: View {
    @StateObject private var player = AudioPlayerService.shared
    @State private var selectedTab: Tab = .home
    @State private var isNowPlayingExpanded = false

    enum Tab: String, CaseIterable, Identifiable {
        case home = "Home"
        case mix = "Mix"
        case library = "Library"
        case search = "Search"

        var id: String { rawValue }

        var icon: String {
            switch self {
            case .home: return "house.fill"
            case .mix: return "sparkles"
            case .library: return "square.stack.fill"
            case .search: return "magnifyingglass"
            }
        }
    }

    var body: some View {
        GeometryReader { proxy in
            let bottomSafeArea = proxy.safeAreaInsets.bottom
            ZStack(alignment: .bottom) {
                Color.black.ignoresSafeArea()

                // Main Tab Content
                TabView(selection: $selectedTab) {
                    HomeView()
                        .tag(Tab.home)

                    MixView()
                        .tag(Tab.mix)

                    LibraryView()
                        .tag(Tab.library)

                    SearchView()
                        .tag(Tab.search)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .ignoresSafeArea(edges: .bottom)

                // Floating Dock & MiniPlayer Container
                VStack(spacing: 8) {
                    if player.currentTrack != nil {
                        MiniPlayerBar(onTap: {
                            withAnimation(.spring(response: 0.38, dampingFraction: 0.82)) {
                                isNowPlayingExpanded = true
                            }
                        })
                        .transition(.asymmetric(
                            insertion: .move(edge: .bottom).combined(with: .opacity),
                            removal: .move(edge: .bottom).combined(with: .opacity)
                        ))
                    }

                    // Floating Liquid Glass Navigation Dock
                    LiquidGlassFloatingDock(selectedTab: $selectedTab)
                        .padding(.horizontal, 20)
                        .padding(.bottom, max(bottomSafeArea > 0 ? bottomSafeArea - 6 : 14, 12))
                }
            }
        }
        .fullScreenCover(isPresented: $isNowPlayingExpanded) {
            NowPlayingView(isPresented: $isNowPlayingExpanded)
        }
    }
}

// MARK: - Floating Liquid Glass Dock
struct LiquidGlassFloatingDock: View {
    @Binding var selectedTab: NativeSimpMusicView.Tab
    @Namespace private var animationNamespace

    var body: some View {
        HStack(spacing: 0) {
            ForEach(NativeSimpMusicView.Tab.allCases) { tab in
                let isSelected = selectedTab == tab
                Button(action: {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.75)) {
                        selectedTab = tab
                    }
                }) {
                    VStack(spacing: 4) {
                        ZStack {
                            if isSelected {
                                // Active tab soft radiant spotlight
                                Circle()
                                    .fill(
                                        RadialGradient(
                                            gradient: Gradient(colors: [
                                                Color(red: 0.98, green: 0.22, blue: 0.35).opacity(0.32),
                                                Color.clear
                                            ]),
                                            center: .center,
                                            startRadius: 2,
                                            endRadius: 24
                                        )
                                    )
                                    .frame(width: 44, height: 44)
                                    .matchedGeometryEffect(id: "activeGlow", in: animationNamespace)
                            }

                            Image(systemName: tab.icon)
                                .font(.system(size: 21, weight: isSelected ? .bold : .medium))
                                .foregroundColor(isSelected ? Color(red: 1.0, green: 0.24, blue: 0.35) : Color.white.opacity(0.55))
                                .scaleEffect(isSelected ? 1.08 : 1.0)
                        }

                        Text(tab.rawValue)
                            .font(.system(size: 11, weight: isSelected ? .semibold : .medium))
                            .foregroundColor(isSelected ? .white : Color.white.opacity(0.55))
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 60)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .liquidGlass(cornerRadius: 34)
    }
}

// MARK: - Mini Player
struct MiniPlayerBar: View {
    @ObservedObject var player = AudioPlayerService.shared
    var onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                AsyncImage(url: URL(string: player.currentTrack?.artworkUrl ?? "")) { image in
                    image.resizable().aspectRatio(contentMode: .fill)
                } placeholder: {
                    Color.gray.opacity(0.3)
                }
                .frame(width: 44, height: 44)
                .cornerRadius(10)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Color.white.opacity(0.15), lineWidth: 0.8)
                )

                VStack(alignment: .leading, spacing: 2) {
                    Text(player.currentTrack?.title ?? "Not Playing")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.white)
                        .lineLimit(1)

                    Text(player.currentTrack?.artist ?? "")
                        .font(.system(size: 12))
                        .foregroundColor(.gray)
                        .lineLimit(1)
                }

                Spacer()

                Button(action: {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        player.togglePlayPause()
                    }
                }) {
                    Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 19))
                        .foregroundColor(.white)
                        .frame(width: 36, height: 36)
                        .contentShape(Rectangle())
                }

                Button(action: {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        player.next()
                    }
                }) {
                    Image(systemName: "forward.fill")
                        .font(.system(size: 17))
                        .foregroundColor(.white)
                        .frame(width: 36, height: 36)
                        .contentShape(Rectangle())
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .liquidGlass(cornerRadius: 18)
            .padding(.horizontal, 20)
        }
        .buttonStyle(.plain)
    }
}


