import SwiftUI
import QuickLook

struct LibraryView: View {
    @ObservedObject var player = AudioPlayerService.shared
    @ObservedObject var storage = LocalStorageManager.shared

    @State private var selectedChip: String = "Your library"
    let chips = ["Your library", "SimpMusic Charts", "Your YouTube"]

    @State private var isCreatingPlaylist = false
    @State private var newPlaylistTitle = ""
    @State private var newPlaylistDesc = ""

    @State private var selectedPlaylist: LocalPlaylist?
    @State private var zipExportUrl: URL?
    @State private var showShareSheet = false
    @State private var isExportingZip = false
    @State private var exportSuccessAlert = false

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 20) {
                // MARK: - Header matching screenshot 7
                HStack(alignment: .center, spacing: 14) {
                    // Profile avatar circle
                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [Color.yellow, Color.purple, Color.blue],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 36, height: 36)
                        .overlay(
                            Image(systemName: "person.fill")
                                .font(.system(size: 16))
                                .foregroundColor(.white)
                        )

                    Text("Library")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.white)

                    Spacer()

                    // ZIP Export Button ("Save All as .ZIP")
                    Button(action: exportZip) {
                        HStack(spacing: 6) {
                            if isExportingZip {
                                ProgressView()
                                    .tint(.white)
                                    .scaleEffect(0.7)
                            } else {
                                Image(systemName: "square.and.arrow.down.fill")
                                    .font(.system(size: 14, weight: .semibold))
                            }
                            Text("ZIP Export")
                                .font(.system(size: 12, weight: .bold))
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(
                            Capsule().fill(Color(red: 1.0, green: 0.22, blue: 0.35))
                        )
                        .shadow(color: Color.red.opacity(0.3), radius: 6, x: 0, y: 3)
                    }

                    // Create Local Playlist (+)
                    Button(action: { isCreatingPlaylist = true }) {
                        Image(systemName: "plus")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(width: 36, height: 36)
                            .background(Circle().fill(Color.white.opacity(0.14)))
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 54)

                // MARK: - Filter Pills Row matching screenshot 7
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(chips, id: \.self) { chip in
                            let isSelected = selectedChip == chip
                            Button(action: {
                                withAnimation(.spring(response: 0.3, dampingFraction: 0.75)) {
                                    selectedChip = chip
                                }
                            }) {
                                Text(chip)
                                    .font(.system(size: 14, weight: isSelected ? .semibold : .medium))
                                    .foregroundColor(isSelected ? .white : .white.opacity(0.8))
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 9)
                                    .background(
                                        Capsule()
                                            .fill(isSelected ? Color.white.opacity(0.22) : Color.white.opacity(0.08))
                                    )
                                    .overlay(
                                        Capsule()
                                            .stroke(
                                                isSelected ? Color(red: 1.0, green: 0.22, blue: 0.35).opacity(0.8) : Color.white.opacity(0.12),
                                                lineWidth: 1
                                            )
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 20)
                }

                // MARK: - Custom Local Playlists 3-Column Grid matching screenshot 7
                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        Text("Custom Local Playlists")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)

                        Spacer()

                        Text("\(storage.localPlaylists.count) playlists")
                            .font(.system(size: 13))
                            .foregroundColor(.gray)
                    }
                    .padding(.horizontal, 20)

                    LazyVGrid(columns: [
                        GridItem(.flexible(), spacing: 14),
                        GridItem(.flexible(), spacing: 14),
                        GridItem(.flexible(), spacing: 14)
                    ], spacing: 20) {
                        ForEach(storage.localPlaylists) { playlist in
                            Button(action: {
                                selectedPlaylist = playlist
                            }) {
                                VStack(alignment: .leading, spacing: 6) {
                                    // Playlist 3-column square cover
                                    ZStack {
                                        if let cover = playlist.coverArtworkUrl, let url = URL(string: cover) {
                                            AsyncImage(url: url) { img in
                                                img.resizable().aspectRatio(contentMode: .fill)
                                            } placeholder: {
                                                Color.gray.opacity(0.2)
                                            }
                                        } else {
                                            LinearGradient(
                                                colors: [Color.purple.opacity(0.8), Color.pink.opacity(0.7)],
                                                startPoint: .topLeading,
                                                endPoint: .bottomTrailing
                                            )
                                            .overlay(
                                                Image(systemName: "music.note.list")
                                                    .font(.system(size: 28))
                                                    .foregroundColor(.white.opacity(0.7))
                                            )
                                        }
                                    }
                                    .frame(width: (UIScreen.main.bounds.width - 68) / 3, height: (UIScreen.main.bounds.width - 68) / 3)
                                    .cornerRadius(14)
                                    .clipped()
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 14)
                                            .stroke(Color.white.opacity(0.14), lineWidth: 0.8)
                                    )

                                    Text(playlist.title)
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(.white)
                                        .lineLimit(1)
                                        .frame(maxWidth: .infinity, alignment: .leading)

                                    Text(playlist.description.isEmpty ? "\(playlist.trackIds.count) tracks" : playlist.description)
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
                }

                // MARK: - Downloaded Songs in App Section
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Downloaded Music")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(.white)
                            Text("Stored locally in app storage • Available offline")
                                .font(.system(size: 12))
                                .foregroundColor(.gray)
                        }
                        Spacer()

                        Button(action: exportZip) {
                            Text("Save as .ZIP")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(Color(red: 1.0, green: 0.25, blue: 0.35))
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 12)

                    VStack(spacing: 8) {
                        ForEach(storage.downloadedTracks) { track in
                            HStack(spacing: 12) {
                                AsyncImage(url: URL(string: track.artworkUrl)) { img in
                                    img.resizable().aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    Color.gray.opacity(0.2)
                                }
                                .frame(width: 48, height: 48)
                                .cornerRadius(10)

                                VStack(alignment: .leading, spacing: 3) {
                                    Text(track.title)
                                        .font(.system(size: 15, weight: .semibold))
                                        .foregroundColor(.white)
                                        .lineLimit(1)

                                    HStack(spacing: 6) {
                                        Image(systemName: "arrow.down.circle.fill")
                                            .font(.system(size: 11))
                                            .foregroundColor(Color(red: 1.0, green: 0.25, blue: 0.35))

                                        Text(track.artist)
                                            .font(.system(size: 12))
                                            .foregroundColor(.gray)
                                            .lineLimit(1)
                                    }
                                }

                                Spacer()

                                Button(action: {
                                    player.playTrack(track)
                                }) {
                                    Image(systemName: "play.circle.fill")
                                        .font(.system(size: 26))
                                        .foregroundColor(.white.opacity(0.85))
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(Color.white.opacity(0.05))
                            )
                            .padding(.horizontal, 20)
                        }
                    }
                }

                Spacer().frame(height: 120)
            }
        }
        .background(Color.black.ignoresSafeArea())
        // Sheet for New Playlist
        .sheet(isPresented: $isCreatingPlaylist) {
            NewPlaylistModal(isPresented: $isCreatingPlaylist)
        }
        // Sheet for Playlist Detail
        .sheet(item: $selectedPlaylist) { playlist in
            PlaylistDetailView(playlist: playlist)
        }
        // Share sheet for ZIP
        .sheet(isPresented: $showShareSheet) {
            if let url = zipExportUrl {
                ShareActivityView(activityItems: [url])
            }
        }
        .alert("ZIP Ready", isPresented: $exportSuccessAlert) {
            Button("Share / Save to Files", action: { showShareSheet = true })
            Button("OK", role: .cancel) {}
        } message: {
            Text("All downloaded songs and playlists have been packaged into r-music-downloaded-songs.zip! You can save it to the iOS Files app or share it anywhere.")
        }
    }

    private func exportZip() {
        isExportingZip = true
        DispatchQueue.global(qos: .userInitiated).async {
            let zipUrl = storage.exportDownloadedSongsToZip()
            DispatchQueue.main.async {
                self.isExportingZip = false
                if let url = zipUrl {
                    self.zipExportUrl = url
                    self.exportSuccessAlert = true
                }
            }
        }
    }
}

// MARK: - New Playlist Creation Modal
struct NewPlaylistModal: View {
    @Binding var isPresented: Bool
    @ObservedObject var storage = LocalStorageManager.shared
    @State private var title: String = ""
    @State private var desc: String = ""
    @State private var selectedTrackIds: Set<String> = []

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Playlist Details")) {
                    TextField("Playlist Name", text: $title)
                    TextField("Description (optional)", text: $desc)
                }

                Section(header: Text("Add Downloaded Songs")) {
                    ForEach(storage.downloadedTracks) { track in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(track.title).font(.system(size: 15, weight: .semibold))
                                Text(track.artist).font(.system(size: 12)).foregroundColor(.gray)
                            }
                            Spacer()
                            if selectedTrackIds.contains(track.id) {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundColor(.red)
                            } else {
                                Image(systemName: "circle")
                                    .foregroundColor(.gray)
                            }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture {
                            if selectedTrackIds.contains(track.id) {
                                selectedTrackIds.remove(track.id)
                            } else {
                                selectedTrackIds.insert(track.id)
                            }
                        }
                    }
                }
            }
            .navigationTitle("New Playlist")
            .navigationBarItems(
                leading: Button("Cancel") { isPresented = false },
                trailing: Button("Create") {
                    guard !title.trimmingCharacters(in: .whitespaces).isEmpty else { return }
                    storage.createPlaylist(
                        title: title,
                        description: desc,
                        initialTrackIds: Array(selectedTrackIds)
                    )
                    isPresented = false
                }
                .disabled(title.trimmingCharacters(in: .whitespaces).isEmpty)
            )
        }
    }
}

// MARK: - Playlist Detail View
struct PlaylistDetailView: View {
    let playlist: LocalPlaylist
    @ObservedObject var storage = LocalStorageManager.shared
    @ObservedObject var player = AudioPlayerService.shared
    @Environment(\.presentationMode) var presentationMode

    var body: some View {
        let tracks = storage.tracksForPlaylist(playlist)

        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    // Artwork
                    ZStack {
                        if let cover = playlist.coverArtworkUrl, let url = URL(string: cover) {
                            AsyncImage(url: url) { img in
                                img.resizable().aspectRatio(contentMode: .fill)
                            } placeholder: {
                                Color.gray.opacity(0.3)
                            }
                        } else {
                            LinearGradient(colors: [Color.red, Color.purple], startPoint: .topLeading, endPoint: .bottomTrailing)
                        }
                    }
                    .frame(width: 200, height: 200)
                    .cornerRadius(18)
                    .shadow(radius: 12)
                    .padding(.top, 20)

                    VStack(spacing: 4) {
                        Text(playlist.title)
                            .font(.system(size: 24, weight: .bold))
                        Text(playlist.description)
                            .font(.system(size: 14))
                            .foregroundColor(.gray)
                        Text("\(tracks.count) downloaded songs")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                    }

                    // Play Button
                    Button(action: {
                        player.playPlaylist(playlist)
                    }) {
                        HStack {
                            Image(systemName: "play.fill")
                            Text("Play All")
                        }
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Capsule().fill(Color(red: 1.0, green: 0.22, blue: 0.35)))
                        .padding(.horizontal, 36)
                    }

                    // Track List
                    VStack(spacing: 4) {
                        ForEach(tracks) { track in
                            HStack(spacing: 12) {
                                AsyncImage(url: URL(string: track.artworkUrl)) { img in
                                    img.resizable().aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    Color.gray.opacity(0.2)
                                }
                                .frame(width: 44, height: 44)
                                .cornerRadius(8)

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(track.title)
                                        .font(.system(size: 15, weight: .semibold))
                                    Text(track.artist)
                                        .font(.system(size: 12))
                                        .foregroundColor(.gray)
                                }

                                Spacer()

                                Button(action: {
                                    player.playPlaylist(playlist, startingFrom: track)
                                }) {
                                    Image(systemName: "play.fill")
                                        .foregroundColor(.red)
                                }
                            }
                            .padding(.horizontal, 20)
                            .padding(.vertical, 8)
                        }
                    }
                }
            }
            .navigationBarItems(
                trailing: Button("Done") {
                    presentationMode.wrappedValue.dismiss()
                }
            )
        }
    }
}

// MARK: - UIActivityViewController for Exporting ZIP to Files
struct ShareActivityView: UIViewControllerRepresentable {
    let activityItems: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
        return controller
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
