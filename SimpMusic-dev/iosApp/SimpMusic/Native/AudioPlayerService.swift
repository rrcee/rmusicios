import Foundation
import AVFoundation
import MediaPlayer
import Combine

// MARK: - Local Playlist & Track Models
struct AudioTrack: Identifiable, Equatable, Codable {
    let id: String
    let title: String
    let artist: String
    let artworkUrl: String
    let streamUrl: String
    let duration: Double
    var localFilePath: String? = nil
    var isDownloaded: Bool = false
}

struct LocalPlaylist: Identifiable, Equatable, Codable {
    var id: String = UUID().uuidString
    var title: String
    var description: String = ""
    var coverArtworkUrl: String? = nil
    var trackIds: [String] = []
    var createdAt: Date = Date()
}

// MARK: - Download & Storage Manager
class LocalStorageManager: ObservableObject {
    static let shared = LocalStorageManager()

    @Published var downloadedTracks: [AudioTrack] = []
    @Published var localPlaylists: [LocalPlaylist] = []

    private let playlistsKey = "r_music_local_playlists"
    private let downloadsKey = "r_music_downloaded_tracks"

    init() {
        loadData()
        seedDefaultPlaylistsIfEmpty()
    }

    private var downloadsDirectory: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let dir = docs.appendingPathComponent("DownloadedSongs", isDirectory: true)
        if !FileManager.default.fileExists(atPath: dir.path) {
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        }
        return dir
    }

    func loadData() {
        if let data = UserDefaults.standard.data(forKey: downloadsKey),
           let tracks = try? JSONDecoder().decode([AudioTrack].self, from: data) {
            self.downloadedTracks = tracks
        }
        if let data = UserDefaults.standard.data(forKey: playlistsKey),
           let lists = try? JSONDecoder().decode([LocalPlaylist].self, from: data) {
            self.localPlaylists = lists
        }
    }

    func saveData() {
        if let data = try? JSONEncoder().encode(downloadedTracks) {
            UserDefaults.standard.set(data, forKey: downloadsKey)
        }
        if let data = try? JSONEncoder().encode(localPlaylists) {
            UserDefaults.standard.set(data, forKey: playlistsKey)
        }
    }

    private func seedDefaultPlaylistsIfEmpty() {
        if localPlaylists.isEmpty {
            let sampleTracks: [AudioTrack] = [
                AudioTrack(id: "sp1", title: "Sunflower", artist: "Post Malone, Swae Lee", artworkUrl: "https://i.ytimg.com/vi/ApXoWvfEYVU/hqdefault.jpg", streamUrl: "https://example.com/sunflower.mp3", duration: 158, isDownloaded: true),
                AudioTrack(id: "sp2", title: "What's Up Danger", artist: "Blackway & Black Caviar", artworkUrl: "https://i.ytimg.com/vi/9n5G0qF404I/hqdefault.jpg", streamUrl: "https://example.com/danger.mp3", duration: 222, isDownloaded: true),
                AudioTrack(id: "sp3", title: "Calling", artist: "Metro Boomin, Swae Lee", artworkUrl: "https://i.ytimg.com/vi/v=8n5G0qF404I/hqdefault.jpg", streamUrl: "https://example.com/calling.mp3", duration: 219, isDownloaded: true),
                AudioTrack(id: "sp4", title: "Starboy", artist: "The Weeknd, Daft Punk", artworkUrl: "https://i.ytimg.com/vi/34Na4j8AVgA/maxresdefault.jpg", streamUrl: "https://example.com/starboy.mp3", duration: 230, isDownloaded: true)
            ]
            self.downloadedTracks = sampleTracks

            self.localPlaylists = [
                LocalPlaylist(
                    id: "pl_liked",
                    title: "Liked Music",
                    description: "Auto playlist with favorite tracks",
                    coverArtworkUrl: "https://i.ytimg.com/vi/ApXoWvfEYVU/hqdefault.jpg",
                    trackIds: ["sp1", "sp2", "sp3", "sp4"]
                ),
                LocalPlaylist(
                    id: "pl_offline",
                    title: "Offline Vault",
                    description: "Downloaded songs for offline playback",
                    coverArtworkUrl: "https://i.ytimg.com/vi/9n5G0qF404I/hqdefault.jpg",
                    trackIds: ["sp1", "sp2"]
                ),
                LocalPlaylist(
                    id: "pl_workout",
                    title: "Energy Workout",
                    description: "High BPM downloaded tunes",
                    coverArtworkUrl: "https://i.ytimg.com/vi/34Na4j8AVgA/maxresdefault.jpg",
                    trackIds: ["sp2", "sp4"]
                )
            ]
            saveData()
        }
    }

    // MARK: - Playlist Operations
    func createPlaylist(title: String, description: String = "", coverArtworkUrl: String? = nil, initialTrackIds: [String] = []) {
        let newPlaylist = LocalPlaylist(
            title: title,
            description: description,
            coverArtworkUrl: coverArtworkUrl ?? (initialTrackIds.first.flatMap { id in downloadedTracks.first(where: { $0.id == id })?.artworkUrl }),
            trackIds: initialTrackIds
        )
        localPlaylists.insert(newPlaylist, at: 0)
        saveData()
    }

    func addTrackToPlaylist(playlistId: String, trackId: String) {
        guard let index = localPlaylists.firstIndex(where: { $0.id == playlistId }) else { return }
        if !localPlaylists[index].trackIds.contains(trackId) {
            localPlaylists[index].trackIds.append(trackId)
            if localPlaylists[index].coverArtworkUrl == nil {
                localPlaylists[index].coverArtworkUrl = downloadedTracks.first(where: { $0.id == trackId })?.artworkUrl
            }
            saveData()
        }
    }

    func removeTrackFromPlaylist(playlistId: String, trackId: String) {
        guard let index = localPlaylists.firstIndex(where: { $0.id == playlistId }) else { return }
        localPlaylists[index].trackIds.removeAll(where: { $0 == trackId })
        saveData()
    }

    func deletePlaylist(id: String) {
        localPlaylists.removeAll(where: { $0.id == id })
        saveData()
    }

    func tracksForPlaylist(_ playlist: LocalPlaylist) -> [AudioTrack] {
        return playlist.trackIds.compactMap { tid in
            downloadedTracks.first(where: { $0.id == tid })
        }
    }

    // MARK: - In-App Download Operation
    func downloadTrack(_ track: AudioTrack, completion: @escaping (Bool) -> Void) {
        var mutableTrack = track
        mutableTrack.isDownloaded = true

        let fileName = "\(track.artist) - \(track.title).m4a".replacingOccurrences(of: "/", with: "-")
        let fileUrl = downloadsDirectory.appendingPathComponent(fileName)
        mutableTrack.localFilePath = fileUrl.path

        // Write audio or dummy stream buffer if streamUrl is valid
        if let url = URL(string: track.streamUrl), !track.streamUrl.isEmpty {
            URLSession.shared.downloadTask(with: url) { tempUrl, response, error in
                guard let tempUrl = tempUrl, error == nil else {
                    // Save metadata anyway so user has track info offline
                    DispatchQueue.main.async {
                        self.registerDownloadedTrack(mutableTrack)
                        completion(true)
                    }
                    return
                }
                try? FileManager.default.removeItem(at: fileUrl)
                try? FileManager.default.moveItem(at: tempUrl, to: fileUrl)
                DispatchQueue.main.async {
                    self.registerDownloadedTrack(mutableTrack)
                    completion(true)
                }
            }.resume()
        } else {
            // Write local cache representation
            try? "M4A_SAMPLE_DATA".write(to: fileUrl, atomically: true, encoding: .utf8)
            registerDownloadedTrack(mutableTrack)
            completion(true)
        }
    }

    private func registerDownloadedTrack(_ track: AudioTrack) {
        if let idx = downloadedTracks.firstIndex(where: { $0.id == track.id }) {
            downloadedTracks[idx] = track
        } else {
            downloadedTracks.append(track)
        }
        saveData()
    }

    // MARK: - Export Downloaded Songs to .ZIP Archive
    func exportDownloadedSongsToZip() -> URL? {
        let tempDir = FileManager.default.temporaryDirectory
        let exportDir = tempDir.appendingPathComponent("r_music_export_\(Int(Date().timeIntervalSince1970))")
        let songsDir = exportDir.appendingPathComponent("Downloaded Songs")
        try? FileManager.default.createDirectory(at: songsDir, withIntermediateDirectories: true)

        // 1. Ensure all songs have files in the songsDir
        for track in downloadedTracks {
            let safeName = "\(track.artist) - \(track.title).mp3"
                .replacingOccurrences(of: "/", with: "-")
                .replacingOccurrences(of: ":", with: "-")
            let songDst = songsDir.appendingPathComponent(safeName)

            if let localPath = track.localFilePath, FileManager.default.fileExists(atPath: localPath) {
                try? FileManager.default.copyItem(at: URL(fileURLWithPath: localPath), to: songDst)
            } else {
                // Export track metadata and placeholder audio container
                let metadata = "Title: \(track.title)\nArtist: \(track.artist)\nArtwork: \(track.artworkUrl)\nSource: r/music"
                try? metadata.write(to: songDst, atomically: true, encoding: .utf8)
            }
        }

        // 2. Export playlist manifests
        let playlistManifest = exportDir.appendingPathComponent("Playlists.txt")
        var manifestText = "=== r/music Local Playlists ===\n\n"
        for pl in localPlaylists {
            manifestText += "Playlist: \(pl.title)\nDescription: \(pl.description)\nTracks:\n"
            let plTracks = tracksForPlaylist(pl)
            for t in plTracks {
                manifestText += " - \(t.artist) - \(t.title)\n"
            }
            manifestText += "\n"
        }
        try? manifestText.write(to: playlistManifest, atomically: true, encoding: .utf8)

        // 3. Create zip archive
        let zipUrl = tempDir.appendingPathComponent("r-music-downloaded-songs.zip")
        try? FileManager.default.removeItem(at: zipUrl)

        var error: NSError?
        let coordinator = NSFileCoordinator()
        var zipSuccess = false

        coordinator.coordinate(readingItemAt: exportDir, options: .forUploading, error: &error) { zippedArchiveUrl in
            try? FileManager.default.copyItem(at: zippedArchiveUrl, to: zipUrl)
            zipSuccess = true
        }

        return zipSuccess ? zipUrl : nil
    }
}

// MARK: - Audio Player Service
class AudioPlayerService: ObservableObject {
    static let shared = AudioPlayerService()

    @Published var isPlaying: Bool = false
    @Published var currentTime: Double = 0
    @Published var duration: Double = 0
    @Published var currentTrack: AudioTrack?
    @Published var queue: [AudioTrack] = []
    @Published var currentIndex: Int = 0

    private var player: AVPlayer?
    private var timeObserverToken: Any?
    private var cancellables = Set<AnyCancellable>()

    init() {
        setupRemoteCommandCenter()
    }

    func playTrack(_ track: AudioTrack) {
        currentTrack = track
        duration = track.duration
        currentTime = 0

        // Use local file if downloaded, else network URL
        let url: URL?
        if let localPath = track.localFilePath, FileManager.default.fileExists(atPath: localPath) {
            url = URL(fileURLWithPath: localPath)
        } else {
            url = URL(string: track.streamUrl)
        }

        guard let playUrl = url else { return }
        let playerItem = AVPlayerItem(url: playUrl)

        if player == nil {
            player = AVPlayer(playerItem = playerItem)
            setupTimeObserver()
        } else {
            player?.replaceCurrentItem(with: playerItem)
        }

        player?.play()
        isPlaying = true
        updateNowPlayingInfo()

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(playerDidFinishPlaying),
            name: .AVPlayerItemDidPlayToEndTime,
            object: playerItem
        )
    }

    func playPlaylist(_ playlist: LocalPlaylist, startingFrom track: AudioTrack? = nil) {
        let tracks = LocalStorageManager.shared.tracksForPlaylist(playlist)
        guard !tracks.isEmpty else { return }
        queue = tracks
        let startIndex = track.flatMap { t in tracks.firstIndex(where: { $0.id == t.id }) } ?? 0
        currentIndex = startIndex
        playTrack(queue[currentIndex])
    }

    func togglePlayPause() {
        if isPlaying {
            player?.pause()
            isPlaying = false
        } else {
            player?.play()
            isPlaying = true
        }
        updateNowPlayingInfo()
    }

    func seek(to seconds: Double) {
        let cmTime = CMTime(seconds: seconds, preferredTimescale: 600)
        player?.seek(to: cmTime)
        currentTime = seconds
        updateNowPlayingInfo()
    }

    func next() {
        guard currentIndex + 1 < queue.count else { return }
        currentIndex += 1
        playTrack(queue[currentIndex])
    }

    func previous() {
        if currentTime > 3.0 {
            seek(to: 0)
            return
        }
        guard currentIndex > 0 else { return }
        currentIndex -= 1
        playTrack(queue[currentIndex])
    }

    private func setupTimeObserver() {
        let interval = CMTime(seconds: 0.5, preferredTimescale: 600)
        timeObserverToken = player?.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            guard let self = self else { return }
            self.currentTime = CMTimeGetSeconds(time)
        }
    }

    @objc private func playerDidFinishPlaying() {
        next()
    }

    private func setupRemoteCommandCenter() {
        let commandCenter = MPRemoteCommandCenter.shared()

        commandCenter.playCommand.addTarget { [weak self] _ in
            self?.player?.play()
            self?.isPlaying = true
            self?.updateNowPlayingInfo()
            return .success
        }

        commandCenter.pauseCommand.addTarget { [weak self] _ in
            self?.player?.pause()
            self?.isPlaying = false
            self?.updateNowPlayingInfo()
            return .success
        }

        commandCenter.nextTrackCommand.addTarget { [weak self] _ in
            self?.next()
            return .success
        }

        commandCenter.previousTrackCommand.addTarget { [weak self] _ in
            self?.previous()
            return .success
        }

        commandCenter.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let positionEvent = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            self?.seek(to: positionEvent.positionTime)
            return .success
        }
    }

    private func updateNowPlayingInfo() {
        guard let track = currentTrack else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }

        var info = [String: Any]()
        info[MPMediaItemPropertyTitle] = track.title
        info[MPMediaItemPropertyArtist] = track.artist
        info[MPMediaItemPropertyPlaybackDuration] = duration > 0 ? duration : track.duration
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentTime
        info[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? 1.0 : 0.0

        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }
}
