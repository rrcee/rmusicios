package com.maxrave.data.mediaservice

import com.maxrave.domain.data.entities.NewFormatEntity
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.mediaService.SponsorSkipSegments
import com.maxrave.domain.data.player.AudioEffects
import com.maxrave.domain.data.player.AudioOutput
import com.maxrave.domain.data.player.AudioOutputKind
import com.maxrave.domain.data.player.GenericCastState
import com.maxrave.domain.data.player.GenericCommandButton
import com.maxrave.domain.data.player.GenericMediaItem
import com.maxrave.domain.data.player.GenericMediaMetadata
import com.maxrave.domain.data.player.GenericPlaybackParameters
import com.maxrave.domain.data.player.PlayerError
import com.maxrave.domain.extension.toGenericMediaItem
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.ControlState
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.NowPlayingTrackState
import com.maxrave.domain.mediaservice.handler.PlayerEvent
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.mediaservice.handler.RepeatState
import com.maxrave.domain.mediaservice.handler.SimpleMediaState
import com.maxrave.domain.mediaservice.handler.SleepTimerState
import com.maxrave.domain.mediaservice.handler.ToastType
import com.maxrave.domain.mediaservice.player.MediaPlayerInterface
import com.maxrave.domain.mediaservice.player.MediaPlayerListener
import com.maxrave.domain.repository.AnalyticsRepository
import com.maxrave.domain.repository.LocalPlaylistRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.repository.StreamRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.toSongEntity
import com.maxrave.domain.utils.toTrack
import com.maxrave.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerTimeControlStatusPaused
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.NSURL
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.UIKit.UIImage

private const val TAG = "IosMediaPlayerHandler"

/**
 * iOS implementation of [MediaPlayerHandler] and [MediaPlayerInterface].
 * Manages AVPlayer playback, queue management, background audio session,
 * MPNowPlayingInfoCenter and MPRemoteCommandCenter on iOS.
 */
class IosMediaPlayerHandlerImpl(
    private val dataStoreManager: DataStoreManager,
    private val songRepository: SongRepository,
    private val streamRepository: StreamRepository,
    private val localPlaylistRepository: LocalPlaylistRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val coroutineScope: CoroutineScope,
) : MediaPlayerHandler, MediaPlayerInterface {

    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var avPlayer: AVPlayer? = null
    private var currentUrl: String? = null
    private var progressJob: Job? = null
    private var sleepJob: Job? = null

    // State flows
    private val _simpleMediaState = MutableStateFlow(SimpleMediaState())
    override val simpleMediaState: StateFlow<SimpleMediaState> = _simpleMediaState.asStateFlow()

    private val _nowPlaying = MutableStateFlow<GenericMediaItem?>(null)
    override val nowPlaying: StateFlow<GenericMediaItem?> = _nowPlaying.asStateFlow()

    private val _queueData = MutableStateFlow<QueueData?>(null)
    override val queueData: StateFlow<QueueData?> = _queueData.asStateFlow()

    private val _controlState = MutableStateFlow(ControlState())
    override val controlState: StateFlow<ControlState> = _controlState.asStateFlow()

    private val _nowPlayingState = MutableStateFlow(NowPlayingTrackState())
    override val nowPlayingState: StateFlow<NowPlayingTrackState> = _nowPlayingState.asStateFlow()

    private val _sleepTimerState = MutableStateFlow(SleepTimerState(false, 0))
    override val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState.asStateFlow()

    private val _skipSegments = MutableStateFlow<List<SponsorSkipSegments>?>(null)
    override val skipSegments: StateFlow<List<SponsorSkipSegments>?> = _skipSegments.asStateFlow()

    private val _format = MutableStateFlow<NewFormatEntity?>(null)
    override val format: StateFlow<NewFormatEntity?> = _format.asStateFlow()

    private val _currentSongIndex = MutableStateFlow(0)
    override val currentSongIndex: StateFlow<Int> = _currentSongIndex.asStateFlow()

    private val _castState = MutableStateFlow(GenericCastState())
    override val castState: StateFlow<GenericCastState> = _castState.asStateFlow()

    override val player: MediaPlayerInterface get() = this

    override var onUpdateNotification: (List<GenericCommandButton>) -> Unit = {}
    override var pushPlayerError: (PlayerError) -> Unit = {}
    override var showToast: (ToastType) -> Unit = {}

    // Internal queue state
    private val mediaQueue = mutableListOf<GenericMediaItem>()
    private var originalQueue = mutableListOf<GenericMediaItem>()
    private val listeners = mutableListOf<MediaPlayerListener>()

    init {
        setupAudioSession()
        setupRemoteCommands()
        startProgressMonitoring()
    }

    private fun setupAudioSession() {
        runCatching {
            val audioSession = AVAudioSession.sharedInstance()
            audioSession.setCategory(AVAudioSessionCategoryPlayback, error = null)
            audioSession.setActive(true, error = null)
        }.onFailure {
            Logger.e(TAG, "Failed to configure audio session: ${it.message}")
        }
    }

    private fun setupRemoteCommands() {
        val commandCenter = MPRemoteCommandCenter.sharedCommandCenter()
        commandCenter.playCommand.addTargetWithHandler {
            play()
            MPRemoteCommandHandlerStatusSuccess
        }
        commandCenter.pauseCommand.addTargetWithHandler {
            pause()
            MPRemoteCommandHandlerStatusSuccess
        }
        commandCenter.togglePlayPauseCommand.addTargetWithHandler {
            if (isPlaying) pause() else play()
            MPRemoteCommandHandlerStatusSuccess
        }
        commandCenter.nextTrackCommand.addTargetWithHandler {
            seekToNext()
            MPRemoteCommandHandlerStatusSuccess
        }
        commandCenter.previousTrackCommand.addTargetWithHandler {
            seekToPrevious()
            MPRemoteCommandHandlerStatusSuccess
        }
        commandCenter.changePlaybackPositionCommand.addTargetWithHandler { event ->
            val positionEvent = event as? platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
            val targetTime = positionEvent?.positionTime ?: 0.0
            seekTo((targetTime * 1000).toLong())
            MPRemoteCommandHandlerStatusSuccess
        }
    }

    private var currentArtwork: MPMediaItemArtwork? = null
    private var currentArtworkUrl: String? = null

    @OptIn(ExperimentalForeignApi::class)
    private fun loadArtwork(urlStr: String?) {
        if (urlStr.isNullOrEmpty()) {
            currentArtwork = null
            currentArtworkUrl = null
            return
        }
        if (urlStr == currentArtworkUrl && currentArtwork != null) return
        currentArtworkUrl = urlStr
        playerScope.launch(Dispatchers.Default) {
            runCatching {
                val nsUrl = NSURL.URLWithString(urlStr) ?: return@runCatching
                val data = NSData.dataWithContentsOfURL(nsUrl) ?: return@runCatching
                val image = UIImage.imageWithData(data) ?: return@runCatching
                val artwork = runCatching {
                    MPMediaItemArtwork(boundsSize = image.size) { _ -> image }
                }.getOrElse {
                    MPMediaItemArtwork(image = image)
                }
                withContext(Dispatchers.Main) {
                    currentArtwork = artwork
                    val p = avPlayer
                    val cur = if (p != null) CMTimeGetSeconds(p.currentTime()) else 0.0
                    val dur = if (p?.currentItem != null) CMTimeGetSeconds(p.currentItem!!.duration()) else 0.0
                    updateNowPlayingInfo(_nowPlaying.value, if (dur.isNaN()) 0.0 else dur, if (cur.isNaN()) 0.0 else cur)
                }
            }
        }
    }

    private fun updateNowPlayingInfo(item: GenericMediaItem?, durationSeconds: Double, elapsedSeconds: Double) {
        if (item == null) {
            MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
            currentArtwork = null
            currentArtworkUrl = null
            return
        }
        val info = mutableMapOf<Any?, Any?>()
        info[MPMediaItemPropertyTitle] = item.metadata.title ?: item.title
        info[MPMediaItemPropertyArtist] = item.metadata.artist ?: item.artist
        item.metadata.albumTitle?.let { info[MPMediaItemPropertyAlbumTitle] = it }
        info[MPMediaItemPropertyPlaybackDuration] = durationSeconds
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = elapsedSeconds
        info[MPNowPlayingInfoPropertyPlaybackRate] = if (isPlaying) 1.0 else 0.0
        currentArtwork?.let {
            info[MPMediaItemPropertyArtwork] = it
        }
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = info
    }

    private fun startProgressMonitoring() {
        progressJob?.cancel()
        progressJob = playerScope.launch {
            while (isActive) {
                delay(500)
                val p = avPlayer ?: continue
                val currentTimeSec = CMTimeGetSeconds(p.currentTime())
                val durationSec = CMTimeGetSeconds(p.currentItem?.duration() ?: p.currentTime())

                if (!currentTimeSec.isNaN()) {
                    val posMs = (currentTimeSec * 1000).toLong()
                    val durMs = if (!durationSec.isNaN() && durationSec > 0) (durationSec * 1000).toLong() else 0L

                    _simpleMediaState.update {
                        it.copy(
                            progress = posMs,
                            duration = durMs,
                        )
                    }

                    // Check if song finished (within 500ms of end)
                    if (durMs > 1000 && posMs >= durMs - 500 && isPlaying) {
                        onTrackEnded()
                    }
                }
            }
        }
    }

    private fun onTrackEnded() {
        when (_controlState.value.repeatState) {
            RepeatState.One -> {
                seekTo(0L)
                play()
            }
            RepeatState.All -> {
                if (_currentSongIndex.value < mediaQueue.size - 1) {
                    seekToNext()
                } else if (mediaQueue.isNotEmpty()) {
                    seekTo(0, 0L)
                }
            }
            RepeatState.None -> {
                if (_currentSongIndex.value < mediaQueue.size - 1) {
                    seekToNext()
                } else {
                    pause()
                }
            }
        }
    }

    // --- MediaPlayerInterface Implementation ---

    override fun play() {
        avPlayer?.play()
        _controlState.update { it.copy(isPlaying = true) }
        _nowPlaying.value?.let { item ->
            val p = avPlayer
            val cur = if (p != null) CMTimeGetSeconds(p.currentTime()) else 0.0
            val dur = if (p?.currentItem != null) CMTimeGetSeconds(p.currentItem!!.duration()) else 0.0
            updateNowPlayingInfo(item, dur, cur)
        }
        listeners.forEach { it.onIsPlayingChanged(true) }
    }

    override fun pause() {
        avPlayer?.pause()
        _controlState.update { it.copy(isPlaying = false) }
        _nowPlaying.value?.let { item ->
            val p = avPlayer
            val cur = if (p != null) CMTimeGetSeconds(p.currentTime()) else 0.0
            val dur = if (p?.currentItem != null) CMTimeGetSeconds(p.currentItem!!.duration()) else 0.0
            updateNowPlayingInfo(item, dur, cur)
        }
        listeners.forEach { it.onIsPlayingChanged(false) }
    }

    override fun stop() {
        pause()
        seekTo(0L)
    }

    override fun seekTo(positionMs: Long) {
        val seconds = positionMs / 1000.0
        val cmTime = CMTimeMakeWithSeconds(seconds, 600)
        avPlayer?.seekToTime(cmTime)
        _simpleMediaState.update { it.copy(progress = positionMs) }
        listeners.forEach { it.onPositionDiscontinuity(positionMs) }
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex in mediaQueue.indices) {
            _currentSongIndex.value = mediaItemIndex
            loadAndPlayCurrentItem(positionMs)
        }
    }

    override fun seekBack() {
        val current = _simpleMediaState.value.progress
        seekTo((current - 10000).coerceAtLeast(0L))
    }

    override fun seekForward() {
        val current = _simpleMediaState.value.progress
        val duration = _simpleMediaState.value.duration
        seekTo((current + 10000).coerceAtMost(duration))
    }

    override fun seekToNext() {
        val nextIndex = _currentSongIndex.value + 1
        if (nextIndex in mediaQueue.indices) {
            _currentSongIndex.value = nextIndex
            loadAndPlayCurrentItem(0L)
        }
    }

    override fun seekToPrevious() {
        if (_simpleMediaState.value.progress > 3000) {
            seekTo(0L)
            return
        }
        val prevIndex = _currentSongIndex.value - 1
        if (prevIndex in mediaQueue.indices) {
            _currentSongIndex.value = prevIndex
            loadAndPlayCurrentItem(0L)
        } else {
            seekTo(0L)
        }
    }

    override fun seekToPreviousMediaItem() {
        val prevIndex = _currentSongIndex.value - 1
        if (prevIndex in mediaQueue.indices) {
            _currentSongIndex.value = prevIndex
            loadAndPlayCurrentItem(0L)
        }
    }

    override fun prepare() {
        // Ready
    }

    override fun setMediaItem(mediaItem: GenericMediaItem) {
        mediaQueue.clear()
        mediaQueue.add(mediaItem)
        originalQueue.clear()
        originalQueue.add(mediaItem)
        _currentSongIndex.value = 0
        loadAndPlayCurrentItem(0L)
    }

    override fun addMediaItem(mediaItem: GenericMediaItem) {
        mediaQueue.add(mediaItem)
        originalQueue.add(mediaItem)
        syncQueueData()
    }

    override fun addMediaItem(index: Int, mediaItem: GenericMediaItem) {
        val safeIndex = index.coerceIn(0, mediaQueue.size)
        mediaQueue.add(safeIndex, mediaItem)
        originalQueue.add(mediaItem)
        syncQueueData()
    }

    override fun removeMediaItem(index: Int) {
        if (index in mediaQueue.indices) {
            mediaQueue.removeAt(index)
            if (index < _currentSongIndex.value) {
                _currentSongIndex.value -= 1
            }
            syncQueueData()
        }
    }

    override fun moveMediaItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex in mediaQueue.indices && toIndex in mediaQueue.indices) {
            val item = mediaQueue.removeAt(fromIndex)
            mediaQueue.add(toIndex, item)
            syncQueueData()
        }
    }

    override fun clearMediaItems() {
        mediaQueue.clear()
        originalQueue.clear()
        avPlayer?.pause()
        avPlayer = null
        currentUrl = null
        _nowPlaying.value = null
        _nowPlayingState.value = NowPlayingTrackState()
        syncQueueData()
    }

    override val isPlaying: Boolean
        get() = avPlayer?.timeControlStatus == AVPlayerTimeControlStatusPlaying

    override val currentPosition: Long
        get() = _simpleMediaState.value.progress

    override val duration: Long
        get() = _simpleMediaState.value.duration

    override val currentMediaItemIndex: Int
        get() = _currentSongIndex.value

    override val mediaItemCount: Int
        get() = mediaQueue.size

    override val currentMediaItem: GenericMediaItem?
        get() = mediaQueue.getOrNull(_currentSongIndex.value)

    override fun getMediaItemAt(index: Int): GenericMediaItem? = mediaQueue.getOrNull(index)

    override val playbackParameters: GenericPlaybackParameters
        get() = GenericPlaybackParameters(speed = 1.0f, pitch = 1.0f)

    override fun setPlaybackParameters(playbackParameters: GenericPlaybackParameters) {
        avPlayer?.rate = playbackParameters.speed
    }

    override fun addListener(listener: MediaPlayerListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    override fun removeListener(listener: MediaPlayerListener) {
        listeners.remove(listener)
    }

    override fun release() {
        progressJob?.cancel()
        sleepJob?.cancel()
        avPlayer?.pause()
        avPlayer = null
    }

    override fun setVolume(volume: Float) {
        avPlayer?.volume = volume.coerceIn(0f, 1f)
    }

    override fun getVolume(): Float = avPlayer?.volume ?: 1f

    override fun setAudioEffects(effects: AudioEffects) {}
    override fun getAudioEffects(): AudioEffects = AudioEffects()
    override fun getAudioOutputs(): List<AudioOutput> = listOf(AudioOutput("Default", AudioOutputKind.SPEAKER, true))
    override fun setAudioOutput(output: AudioOutput) {}

    // --- Media Loading Logic ---

    private fun loadAndPlayCurrentItem(startPositionMs: Long) {
        val item = currentMediaItem ?: return
        _nowPlaying.value = item
        loadArtwork(item.metadata.artworkUri)
        _nowPlayingState.update {
            it.copy(
                title = item.title,
                artist = item.artist,
                song = item.toTrack().toSongEntity(),
            )
        }

        playerScope.launch {
            resolveAndPlayStream(item, startPositionMs)
        }
    }

    private suspend fun resolveAndPlayStream(item: GenericMediaItem, startPositionMs: Long) {
        val videoId = item.mediaId
        try {
            val streamRes = streamRepository.getStream(videoId)
            val playableUrl = when (streamRes) {
                is Resource.Success -> streamRes.data?.firstOrNull()?.url
                else -> null
            }

            if (playableUrl.isNullOrEmpty()) {
                Logger.e(TAG, "Failed to resolve stream for $videoId")
                pushPlayerError(PlayerError(videoId, "No stream url available"))
                return
            }

            currentUrl = playableUrl
            val nsUrl = NSURL.URLWithString(playableUrl) ?: return
            val playerItem = AVPlayerItem(uRL = nsUrl)
            
            if (avPlayer == null) {
                avPlayer = AVPlayer(playerItem = playerItem)
            } else {
                avPlayer?.replaceCurrentItemWithPlayerItem(playerItem)
            }

            if (startPositionMs > 0) {
                val cmTime = CMTimeMakeWithSeconds(startPositionMs / 1000.0, 600)
                avPlayer?.seekToTime(cmTime)
            }

            play()
            listeners.forEach { it.onMediaItemTransition(item, _currentSongIndex.value) }
            syncQueueData()
        } catch (e: Exception) {
            Logger.e(TAG, "Error playing track: ${e.message}")
            pushPlayerError(PlayerError(videoId, e.message ?: "Playback error"))
        }
    }

    private fun syncQueueData() {
        val currentQueue = mediaQueue.map { it.toTrack() }
        _queueData.value = QueueData(
            listTrack = ArrayList(currentQueue),
            currentIndex = _currentSongIndex.value,
            playlistType = PlaylistType.PLAYLIST,
            playlistId = "",
            playlistName = "Now Playing"
        )
    }

    // --- MediaPlayerHandler Implementation ---

    override suspend fun onPlayerEvent(playerEvent: PlayerEvent) {
        when (playerEvent) {
            PlayerEvent.PlayPause -> if (isPlaying) pause() else play()
            PlayerEvent.Next -> seekToNext()
            PlayerEvent.Previous -> seekToPrevious()
            PlayerEvent.Forward -> seekForward()
            PlayerEvent.Backward -> seekBack()
            PlayerEvent.Stop -> stop()
            PlayerEvent.Repeat -> {
                val nextState = when (_controlState.value.repeatState) {
                    RepeatState.None -> RepeatState.All
                    RepeatState.All -> RepeatState.One
                    RepeatState.One -> RepeatState.None
                }
                _controlState.update { it.copy(repeatState = nextState) }
            }
            PlayerEvent.Shuffle -> {
                val isShuffled = !_controlState.value.isShuffle
                _controlState.update { it.copy(isShuffle = isShuffled) }
                if (isShuffled) {
                    originalQueue = ArrayList(mediaQueue)
                    val current = currentMediaItem
                    val remaining = mediaQueue.filter { it.mediaId != current?.mediaId }.shuffled()
                    mediaQueue.clear()
                    if (current != null) mediaQueue.add(current)
                    mediaQueue.addAll(remaining)
                    _currentSongIndex.value = 0
                } else {
                    val current = currentMediaItem
                    mediaQueue.clear()
                    mediaQueue.addAll(originalQueue)
                    _currentSongIndex.value = mediaQueue.indexOfFirst { it.mediaId == current?.mediaId }.coerceAtLeast(0)
                }
                syncQueueData()
            }
            PlayerEvent.ToggleLike -> toggleLike()
            is PlayerEvent.UpdateVolume -> setVolume(playerEvent.volume)
            is PlayerEvent.SeekToPosition -> seekTo(playerEvent.position)
            is PlayerEvent.SeekTo -> seekTo(playerEvent.index, playerEvent.position)
            else -> {}
        }
    }

    override fun toggleRadio() {}

    override fun toggleLike() {
        val current = currentMediaItem ?: return
        coroutineScope.launch {
            val song = songRepository.getSong(current.mediaId).firstOrNull()
            if (song != null) {
                val newLike = song.liked == 0
                songRepository.updateLike(newLike, current.mediaId)
                _nowPlayingState.update { it.copy(liked = newLike) }
            }
        }
    }

    override fun like(liked: Boolean) {
        val current = currentMediaItem ?: return
        coroutineScope.launch {
            songRepository.updateLike(liked, current.mediaId)
            _nowPlayingState.update { it.copy(liked = liked) }
        }
    }

    override fun resetSongAndQueue() {
        clearMediaItems()
    }

    override fun sleepStart(minutes: Int) {
        sleepJob?.cancel()
        _sleepTimerState.value = SleepTimerState(true, minutes * 60)
        sleepJob = coroutineScope.launch {
            var remaining = minutes * 60
            while (remaining > 0 && isActive) {
                delay(1000)
                remaining -= 1
                _sleepTimerState.value = SleepTimerState(true, remaining)
            }
            pause()
            _sleepTimerState.value = SleepTimerState(false, 0)
        }
    }

    override fun sleepStop() {
        sleepJob?.cancel()
        _sleepTimerState.value = SleepTimerState(false, 0)
    }

    override fun addMediaItem(mediaItem: GenericMediaItem, playWhenReady: Boolean) {
        addMediaItem(mediaItem)
        if (playWhenReady && mediaQueue.size == 1) {
            loadAndPlayCurrentItem(0L)
        }
    }

    override fun playNext(mediaItem: GenericMediaItem) {
        val insertIndex = (_currentSongIndex.value + 1).coerceAtMost(mediaQueue.size)
        addMediaItem(insertIndex, mediaItem)
    }

    override fun playNext(track: Track) {
        playNext(track.toGenericMediaItem())
    }

    override fun loadMediaItem(mediaItem: GenericMediaItem, playWhenReady: Boolean) {
        setMediaItem(mediaItem)
    }

    override fun loadPlaylistOrAlbum(index: Int) {
        if (index in mediaQueue.indices) {
            seekTo(index, 0L)
        }
    }

    override fun loadMoreCatalog(list: List<Track>) {
        list.forEach { addMediaItem(it.toGenericMediaItem()) }
    }

    override fun setQueueData(queueData: QueueData) {
        _queueData.value = queueData
        mediaQueue.clear()
        mediaQueue.addAll(queueData.listTrack.map { it.toGenericMediaItem() })
        originalQueue = ArrayList(mediaQueue)
        _currentSongIndex.value = queueData.currentIndex.coerceIn(0, (mediaQueue.size - 1).coerceAtLeast(0))
        loadAndPlayCurrentItem(0L)
    }

    override fun getRelated(videoId: String) {
        coroutineScope.launch {
            // Can fetch up next suggestions
        }
    }

    override fun shufflePlaylist(index: Int) {
        if (mediaQueue.isEmpty()) return
        val chosen = mediaQueue.getOrNull(index) ?: mediaQueue.first()
        val rest = mediaQueue.filter { it.mediaId != chosen.mediaId }.shuffled()
        mediaQueue.clear()
        mediaQueue.add(chosen)
        mediaQueue.addAll(rest)
        _currentSongIndex.value = 0
        syncQueueData()
        loadAndPlayCurrentItem(0L)
    }

    override fun changeAudioOutput(output: AudioOutput) {
        setAudioOutput(output)
    }

    override fun getAvailableAudioOutputs(): List<AudioOutput> = getAudioOutputs()

    override fun seekToPosition(positionMs: Long) {
        seekTo(positionMs)
    }

    override fun getProgress(): Long = _simpleMediaState.value.progress

    override fun getPlayerDuration(): Long = _simpleMediaState.value.duration

    override fun reset() {
        stop()
        clearMediaItems()
    }
}
