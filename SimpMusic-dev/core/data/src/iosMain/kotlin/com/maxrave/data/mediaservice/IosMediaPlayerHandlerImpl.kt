@file:OptIn(ExperimentalForeignApi::class)

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
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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
import kotlinx.coroutines.withContext
import platform.AVFAudio.*
import platform.AVFoundation.*
import platform.CoreMedia.*
import platform.Foundation.*
import platform.MediaPlayer.*
import platform.UIKit.*

private const val TAG = "IosMediaPlayerHandler"

/**
 * iOS implementation of [MediaPlayerHandler] and [MediaPlayerInterface].
 * Manages AVPlayer playback, queue management, background audio session,
 * interruptions, headphone route changes, MPNowPlayingInfoCenter,
 * MPRemoteCommandCenter, and full-fidelity lock screen artwork.
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
    private var wasPlayingBeforeInterruption = false

    // State flows
    private val _simpleMediaState = MutableStateFlow<SimpleMediaState>(SimpleMediaState.Initial)
    override val simpleMediaState: StateFlow<SimpleMediaState> = _simpleMediaState.asStateFlow()

    private val _nowPlaying = MutableStateFlow<GenericMediaItem?>(null)
    override val nowPlaying: StateFlow<GenericMediaItem?> = _nowPlaying.asStateFlow()

    private val _queueData = MutableStateFlow<QueueData?>(
        QueueData(
            queueState = QueueData.StateSource.STATE_CREATED,
            data = QueueData.Data(),
        ),
    )
    override val queueData: StateFlow<QueueData?> = _queueData.asStateFlow()

    private val _controlState = MutableStateFlow(
        ControlState(
            isPlaying = false,
            isShuffle = false,
            repeatState = RepeatState.None,
            isLiked = false,
            isNextAvailable = false,
            isPreviousAvailable = false,
            isCrossfading = false,
            volume = 1.0f,
        ),
    )
    override val controlState: StateFlow<ControlState> = _controlState.asStateFlow()

    private val _nowPlayingState = MutableStateFlow(NowPlayingTrackState.initial())
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

    private val _audioOutputs = MutableStateFlow<List<AudioOutput>>(
        listOf(AudioOutput(id = "default", name = "Speaker", kind = AudioOutputKind.DEVICE_SPEAKER, isActive = true)),
    )
    override val audioOutputs: StateFlow<List<AudioOutput>> = _audioOutputs.asStateFlow()

    override val player: MediaPlayerInterface get() = this

    override var onUpdateNotification: (List<GenericCommandButton>) -> Unit = {}
    override var pushPlayerError: (PlayerError) -> Unit = {}
    override var showToast: (ToastType) -> Unit = {}

    // Internal queue state
    private val mediaQueue = mutableListOf<GenericMediaItem>()
    private var originalQueue = mutableListOf<GenericMediaItem>()
    private val listeners = mutableListOf<MediaPlayerListener>()

    // MediaPlayerInterface properties
    override var shuffleModeEnabled: Boolean
        get() = _controlState.value.isShuffle
        set(value) {
            _controlState.update { it.copy(isShuffle = value) }
        }

    override var repeatMode: Int
        get() = when (_controlState.value.repeatState) {
            RepeatState.None -> 0
            RepeatState.One -> 1
            RepeatState.All -> 2
        }
        set(value) {
            val state = when (value) {
                1 -> RepeatState.One
                2 -> RepeatState.All
                else -> RepeatState.None
            }
            _controlState.update { it.copy(repeatState = state) }
        }

    override var playWhenReady: Boolean = true
    override var playbackParameters: GenericPlaybackParameters = GenericPlaybackParameters(1.0f, 1.0f)
        set(value) {
            field = value
            avPlayer?.rate = value.speed
        }
    override val audioSessionId: Int = 0
    override var volume: Float
        get() = avPlayer?.volume ?: 1f
        set(value) {
            setVolume(value)
        }
    override var sleepFadeFactor: Float = 1.0f
    override var crossfadeSuppressed: Boolean = false
    override var albumTrackIds: Set<String> = emptySet()
    override var skipSilenceEnabled: Boolean = false

    private val currentProgressMs: Long
        get() = (_simpleMediaState.value as? SimpleMediaState.Progress)?.progress ?: 0L

    private val currentDurationMs: Long
        get() {
            val p = avPlayer ?: return 0L
            val durSec = CMTimeGetSeconds(p.currentItem?.duration() ?: p.currentTime())
            return if (!durSec.isNaN() && durSec > 0) (durSec * 1000).toLong() else 0L
        }

    override val bufferedPosition: Long get() = currentProgressMs
    override val bufferedPercentage: Int get() = 100
    override val contentPosition: Long get() = currentProgressMs
    override val playbackState: Int
        get() = if (isPlaying) 3 else 2 // 3 = READY, 2 = BUFFERING/PAUSED

    init {
        setupAudioSession()
        setupNotificationsAndInterruptions()
        setupRemoteCommands()
        startProgressMonitoring()
        refreshAudioOutputs()
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

    private fun setupNotificationsAndInterruptions() {
        runCatching {
            val center = NSNotificationCenter.defaultCenter

            // 1. Audio session interruptions (phone calls, FaceTime, alarms)
            center.addObserverForName(
                name = AVAudioSessionInterruptionNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { notification ->
                val userInfo = notification?.userInfo ?: return@addObserverForName
                val type = (userInfo[AVAudioSessionInterruptionTypeKey] as? NSNumber)?.unsignedIntegerValue
                if (type == AVAudioSessionInterruptionTypeBegan.toULong()) {
                    wasPlayingBeforeInterruption = isPlaying
                    pause()
                } else if (type == AVAudioSessionInterruptionTypeEnded.toULong()) {
                    val options = (userInfo[AVAudioSessionInterruptionOptionKey] as? NSNumber)?.unsignedIntegerValue
                    if (options != null && (options and AVAudioSessionInterruptionOptionShouldResume.toULong()) != 0UL) {
                        if (wasPlayingBeforeInterruption) {
                            play()
                        }
                    }
                }
            }

            // 2. Audio route changes (headphones unplugged / AirPods disconnected -> auto-pause)
            center.addObserverForName(
                name = AVAudioSessionRouteChangeNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { notification ->
                refreshAudioOutputs()
                val userInfo = notification?.userInfo ?: return@addObserverForName
                val reason = (userInfo[AVAudioSessionRouteChangeReasonKey] as? NSNumber)?.unsignedIntegerValue
                if (reason == AVAudioSessionRouteChangeReasonOldDeviceUnavailable.toULong()) {
                    pause()
                }
            }

            // 3. Track finished playing notification
            center.addObserverForName(
                name = AVPlayerItemDidPlayToEndTimeNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { notification ->
                val finishedItem = notification?.`object` as? AVPlayerItem
                if (finishedItem != null && finishedItem == avPlayer?.currentItem) {
                    onTrackEnded()
                }
            }
        }.onFailure {
            Logger.e(TAG, "Failed to register audio observers: ${it.message}")
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
            val positionEvent = event as? MPChangePlaybackPositionCommandEvent
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
        item.metadata.title?.let { info[MPMediaItemPropertyTitle] = it }
        item.metadata.artist?.let { info[MPMediaItemPropertyArtist] = it }
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

                    _simpleMediaState.value = SimpleMediaState.Progress(posMs)

                    if (durMs > 1000 && posMs >= durMs - 300 && isPlaying) {
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
        runCatching {
            AVAudioSession.sharedInstance().setActive(true, error = null)
        }
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
        _simpleMediaState.value = SimpleMediaState.Progress(positionMs)
        listeners.forEach { it.onSeeked(positionMs) }
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex in mediaQueue.indices) {
            _currentSongIndex.value = mediaItemIndex
            loadAndPlayCurrentItem(positionMs)
        }
    }

    override fun seekBack() {
        val current = currentProgressMs
        seekTo((current - 10000).coerceAtLeast(0L))
    }

    override fun seekForward() {
        val current = currentProgressMs
        val duration = currentDurationMs
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
        if (currentProgressMs > 3000) {
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

    override fun prepare() {}

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

    override fun removeMediaItems(fromIndex: Int, toIndex: Int, onRemoved: (removedIds: List<String>) -> Unit) {
        val safeFrom = fromIndex.coerceIn(0, mediaQueue.size)
        val safeTo = toIndex.coerceIn(safeFrom, mediaQueue.size)
        val removed = mediaQueue.subList(safeFrom, safeTo).map { it.mediaId }
        mediaQueue.subList(safeFrom, safeTo).clear()
        syncQueueData()
        onRemoved(removed)
    }

    override fun moveMediaItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex in mediaQueue.indices && toIndex in mediaQueue.indices) {
            val item = mediaQueue.removeAt(fromIndex)
            mediaQueue.add(toIndex, item)
            syncQueueData()
        }
    }

    override fun moveShuffledItem(fromShuffledIndex: Int, toShuffledIndex: Int) {
        moveMediaItem(fromShuffledIndex, toShuffledIndex)
    }

    override fun clearMediaItems() {
        mediaQueue.clear()
        originalQueue.clear()
        avPlayer?.pause()
        avPlayer = null
        currentUrl = null
        _nowPlaying.value = null
        _nowPlayingState.value = NowPlayingTrackState.initial()
        syncQueueData()
    }

    override fun replaceMediaItem(index: Int, mediaItem: GenericMediaItem) {
        if (index in mediaQueue.indices) {
            mediaQueue[index] = mediaItem
            syncQueueData()
        }
    }

    override val isPlaying: Boolean
        get() = avPlayer?.timeControlStatus == AVPlayerTimeControlStatusPlaying

    override val currentPosition: Long
        get() = currentProgressMs

    override val duration: Long
        get() = currentDurationMs

    override val currentMediaItemIndex: Int
        get() = _currentSongIndex.value

    override val mediaItemCount: Int
        get() = mediaQueue.size

    override val currentMediaItem: GenericMediaItem?
        get() = mediaQueue.getOrNull(_currentSongIndex.value)

    override fun getMediaItemAt(index: Int): GenericMediaItem? = mediaQueue.getOrNull(index)

    override fun getCurrentMediaTimeLine(): List<GenericMediaItem> = ArrayList(mediaQueue)

    override fun getUnshuffledIndex(shuffledIndex: Int): Int = shuffledIndex

    override fun hasNextMediaItem(): Boolean = _currentSongIndex.value < mediaQueue.size - 1

    override fun hasPreviousMediaItem(): Boolean = _currentSongIndex.value > 0

    fun setPlaybackParameters(playbackParameters: GenericPlaybackParameters) {
        this.playbackParameters = playbackParameters
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

    fun setVolume(volume: Float) {
        val safeVol = volume.coerceIn(0f, 1f)
        avPlayer?.volume = safeVol
        _controlState.update { it.copy(volume = safeVol) }
    }

    fun getVolume(): Float = avPlayer?.volume ?: 1f

    override fun setEqualizer(bandsDb: List<Float>, preampDb: Float) {}
    override fun setAudioEffects(effects: AudioEffects) {}

    override fun selectAudioOutput(id: String?) {
        refreshAudioOutputs()
    }

    override fun refreshAudioOutputs() {
        runCatching {
            val session = AVAudioSession.sharedInstance()
            val currentOutputs = session.currentRoute.outputs as? List<*> ?: emptyList<Any>()
            val list = mutableListOf<AudioOutput>()
            for (port in currentOutputs) {
                val portDesc = port as? AVAudioSessionPortDescription ?: continue
                val kind = when (portDesc.portType) {
                    AVAudioSessionPortHeadphones -> AudioOutputKind.WIRED
                    AVAudioSessionPortBluetoothHFP,
                    AVAudioSessionPortBluetoothA2DP,
                    AVAudioSessionPortBluetoothLE -> AudioOutputKind.BLUETOOTH
                    else -> AudioOutputKind.DEVICE_SPEAKER
                }
                list.add(AudioOutput(id = portDesc.UID, name = portDesc.portName, kind = kind, isActive = true))
            }
            if (list.isEmpty()) {
                list.add(AudioOutput(id = "default", name = "Speaker", kind = AudioOutputKind.DEVICE_SPEAKER, isActive = true))
            }
            _audioOutputs.value = list
        }
    }

    // --- Media Loading Logic ---

    private fun loadAndPlayCurrentItem(startPositionMs: Long) {
        val item = currentMediaItem ?: return
        _nowPlaying.value = item
        loadArtwork(item.metadata.artworkUri)
        val track = _queueData.value?.data?.listTracks?.find { it.videoId == item.mediaId }
            ?: Track(
                album = null,
                artists = item.metadata.artist?.let { listOf(com.maxrave.domain.data.model.searchResult.songs.Artist(name = it, id = null)) },
                duration = null,
                durationSeconds = null,
                isAvailable = true,
                isExplicit = false,
                likeStatus = null,
                thumbnails = item.metadata.artworkUri?.let { listOf(com.maxrave.domain.data.model.searchResult.songs.Thumbnail(height = 544, width = 544, url = it)) },
                title = item.metadata.title ?: "",
                videoId = item.mediaId,
                videoType = null,
                category = null,
                feedbackTokens = null,
                resultType = null,
            )
        _nowPlayingState.update {
            it.copy(
                mediaItem = item,
                track = track,
                songEntity = track.toSongEntity(),
            )
        }

        playerScope.launch {
            resolveAndPlayStream(item, startPositionMs)
        }
    }

    private suspend fun resolveAndPlayStream(item: GenericMediaItem, startPositionMs: Long) {
        val videoId = item.mediaId
        try {
            val playableUrl: String? = streamRepository.getStream(
                dataStoreManager = dataStoreManager,
                videoId = videoId,
                isDownloading = false,
                isVideo = false,
            ).firstOrNull()

            if (playableUrl.isNullOrEmpty()) {
                Logger.e(TAG, "Failed to resolve stream for $videoId")
                pushPlayerError(PlayerError(-1, "STREAM_ERROR", "No stream url available for $videoId"))
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

            mayBeNormalizeVolume()

            if (startPositionMs > 0) {
                val cmTime = CMTimeMakeWithSeconds(startPositionMs / 1000.0, 600)
                avPlayer?.seekToTime(cmTime)
            }

            play()
            listeners.forEach { it.onMediaItemTransition(item, _currentSongIndex.value) }
            syncQueueData()
        } catch (e: Exception) {
            Logger.e(TAG, "Error playing track: ${e.message}")
            pushPlayerError(PlayerError(-1, "PLAYBACK_ERROR", e.message ?: "Playback error"))
        }
    }

    private fun syncQueueData() {
        val existingTracks = _queueData.value?.data?.listTracks ?: emptyList()
        val trackMap = existingTracks.associateBy { it.videoId }
        val currentQueue = mediaQueue.map { item ->
            trackMap[item.mediaId] ?: Track(
                album = null,
                artists = item.metadata.artist?.let { listOf(com.maxrave.domain.data.model.searchResult.songs.Artist(name = it, id = null)) },
                duration = null,
                durationSeconds = null,
                isAvailable = true,
                isExplicit = false,
                likeStatus = null,
                thumbnails = item.metadata.artworkUri?.let { listOf(com.maxrave.domain.data.model.searchResult.songs.Thumbnail(height = 544, width = 544, url = it)) },
                title = item.metadata.title ?: "",
                videoId = item.mediaId,
                videoType = null,
                category = null,
                feedbackTokens = null,
                resultType = null,
            )
        }
        _queueData.update {
            it?.copy(
                queueState = QueueData.StateSource.STATE_INITIALIZED,
                data = it.data.copy(
                    listTracks = ArrayList(currentQueue),
                    playlistType = PlaylistType.PLAYLIST,
                    playlistName = "Now Playing",
                ),
            ) ?: QueueData(
                queueState = QueueData.StateSource.STATE_INITIALIZED,
                data = QueueData.Data(
                    listTracks = ArrayList(currentQueue),
                    playlistType = PlaylistType.PLAYLIST,
                    playlistName = "Now Playing",
                ),
            )
        }
    }

    // --- MediaPlayerHandler Implementation ---

    override suspend fun onPlayerEvent(playerEvent: PlayerEvent) {
        when (playerEvent) {
            PlayerEvent.PlayPause -> if (isPlaying) pause() else play()
            PlayerEvent.Next -> seekToNext()
            PlayerEvent.Previous -> seekToPrevious()
            PlayerEvent.SkipToPrevious -> seekToPreviousMediaItem()
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
            is PlayerEvent.UpdateVolume -> setVolume(playerEvent.newVolume)
            is PlayerEvent.UpdateProgress -> seekTo((playerEvent.newProgress * currentDurationMs).toLong())
        }
    }

    override fun toggleRadio() {}

    override fun toggleLike() {
        val current = currentMediaItem ?: return
        coroutineScope.launch {
            val song = songRepository.getSongById(current.mediaId).firstOrNull()
            val newLike = if (song != null) !song.liked else !_controlState.value.isLiked
            songRepository.updateLikeStatus(current.mediaId, if (newLike) 1 else 0)
            _controlState.update { it.copy(isLiked = newLike) }
            _nowPlayingState.update { it.copy(songEntity = it.songEntity?.copy(liked = newLike)) }
        }
    }

    override fun like(liked: Boolean) {
        val current = currentMediaItem ?: return
        coroutineScope.launch {
            songRepository.updateLikeStatus(current.mediaId, if (liked) 1 else 0)
            _controlState.update { it.copy(isLiked = liked) }
            _nowPlayingState.update { it.copy(songEntity = it.songEntity?.copy(liked = liked)) }
        }
    }

    override fun resetSongAndQueue() {
        clearMediaItems()
    }

    override fun sleepStart(minutes: Int) {
        sleepJob?.cancel()
        _sleepTimerState.value = SleepTimerState(false, minutes * 60)
        sleepJob = coroutineScope.launch {
            var remaining = minutes * 60
            while (remaining > 0 && isActive) {
                delay(1000)
                remaining -= 1
                _sleepTimerState.value = SleepTimerState(false, remaining)
            }
            pause()
            _sleepTimerState.value = SleepTimerState(true, 0)
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

    override fun addMediaItemList(mediaItemList: List<GenericMediaItem>) {
        mediaQueue.addAll(mediaItemList)
        originalQueue.addAll(mediaItemList)
        syncQueueData()
    }

    override fun playMediaItemInMediaSource(index: Int) {
        if (index in mediaQueue.indices) {
            _currentSongIndex.value = index
            loadAndPlayCurrentItem(0L)
        }
    }

    override fun currentSongIndex(): Int = _currentSongIndex.value

    override fun currentOrderIndex(): Int = _currentSongIndex.value

    override suspend fun swap(from: Int, to: Int) {
        moveMediaItem(from, to)
    }

    override fun resetCrossfade() {}

    override fun shufflePlaylist(randomTrackIndex: Int) {
        if (mediaQueue.isEmpty()) return
        val chosen = mediaQueue.getOrNull(randomTrackIndex) ?: mediaQueue.first()
        val rest = mediaQueue.filter { it.mediaId != chosen.mediaId }.shuffled()
        mediaQueue.clear()
        mediaQueue.add(chosen)
        mediaQueue.addAll(rest)
        _currentSongIndex.value = 0
        syncQueueData()
        loadAndPlayCurrentItem(0L)
    }

    override fun loadMore() {}

    override fun getRelated(videoId: String) {}

    override fun setQueueData(queueData: QueueData.Data) {
        _queueData.update {
            it?.copy(
                queueState = QueueData.StateSource.STATE_INITIALIZED,
                data = queueData,
            ) ?: QueueData(
                queueState = QueueData.StateSource.STATE_INITIALIZED,
                data = queueData,
            )
        }
        mediaQueue.clear()
        mediaQueue.addAll(queueData.listTracks.map { it.toGenericMediaItem() })
        originalQueue = ArrayList(mediaQueue)
        _currentSongIndex.value = 0
        if (mediaQueue.isNotEmpty()) {
            loadAndPlayCurrentItem(0L)
        }
    }

    override fun getCurrentMediaItem(): GenericMediaItem? = currentMediaItem

    override suspend fun moveItemUp(position: Int) {
        if (position > 0 && position < mediaQueue.size) {
            moveMediaItem(position, position - 1)
        }
    }

    override suspend fun moveItemDown(position: Int) {
        if (position >= 0 && position < mediaQueue.size - 1) {
            moveMediaItem(position, position + 1)
        }
    }

    override suspend fun moveItemToPlayNext(position: Int) {
        val targetIndex = (_currentSongIndex.value + 1).coerceAtMost(mediaQueue.size - 1)
        if (position in mediaQueue.indices && targetIndex in mediaQueue.indices) {
            moveMediaItem(position, targetIndex)
        }
    }

    override fun addFirstMediaItemToIndex(mediaItem: GenericMediaItem?, index: Int) {
        if (mediaItem != null) {
            addMediaItem(index, mediaItem)
        }
    }

    override fun reset() {
        stop()
        clearMediaItems()
    }

    override suspend fun load(downloaded: Int, index: Int?) {
        val targetIndex = index ?: 0
        if (targetIndex in mediaQueue.indices) {
            seekTo(targetIndex, 0L)
        }
    }

    override suspend fun loadMoreCatalog(listTrack: ArrayList<Track>, isAddToQueue: Boolean) {
        val items = listTrack.map { it.toGenericMediaItem() }
        if (isAddToQueue) {
            addMediaItemList(items)
        } else {
            items.forEach { addMediaItem(it) }
        }
    }

    override suspend fun updateCatalog(downloaded: Int, index: Int?): Boolean = true

    override fun addQueueToPlayer() {}

    override fun loadPlaylistOrAlbum(index: Int?) {
        val targetIndex = index ?: 0
        if (targetIndex in mediaQueue.indices) {
            seekTo(targetIndex, 0L)
        }
    }

    override fun setCurrentSongIndex(index: Int) {
        _currentSongIndex.value = index.coerceIn(0, (mediaQueue.size - 1).coerceAtLeast(0))
    }

    override suspend fun playNext(track: Track) {
        val insertIndex = (_currentSongIndex.value + 1).coerceAtMost(mediaQueue.size)
        addMediaItem(insertIndex, track.toGenericMediaItem())
    }

    override suspend fun <T> loadMediaItem(anyTrack: T, type: String, index: Int?) {
        val track = when (anyTrack) {
            is Track -> anyTrack
            is SongEntity -> anyTrack.toTrack()
            else -> null
        }
        if (track != null) {
            val item = track.toGenericMediaItem()
            val targetIndex = index ?: mediaQueue.size
            _queueData.update { q ->
                val currentList = ArrayList(q?.data?.listTracks ?: emptyList())
                if (targetIndex in 0..currentList.size) {
                    currentList.add(targetIndex, track)
                } else {
                    currentList.add(track)
                }
                q?.copy(
                    queueState = QueueData.StateSource.STATE_INITIALIZED,
                    data = q.data.copy(listTracks = currentList),
                ) ?: QueueData(
                    queueState = QueueData.StateSource.STATE_INITIALIZED,
                    data = QueueData.Data(listTracks = currentList),
                )
            }
            addMediaItem(targetIndex, item)
            _currentSongIndex.value = targetIndex
            loadAndPlayCurrentItem(0L)
        }
    }

    override fun getPlayerDuration(): Long = currentDurationMs

    override fun getProgress(): Long = currentProgressMs

    override fun startProgressUpdate() {
        startProgressMonitoring()
    }

    override fun stopProgressUpdate() {
        progressJob?.cancel()
    }

    override fun startBufferedUpdate() {}

    override fun stopBufferedUpdate() {}

    override fun mayBeNormalizeVolume() {
        playerScope.launch {
            val isNormalize = dataStoreManager.normalizeVolume.firstOrNull() == "true"
            if (isNormalize) {
                avPlayer?.volume = 0.85f
            } else {
                avPlayer?.volume = 1.0f
            }
        }
    }

    override fun mayBeSaveRecentSong(runBlocking: Boolean) {
        val song = _nowPlayingState.value.songEntity ?: return
        playerScope.launch {
            songRepository.insertSong(song).firstOrNull()
        }
    }

    override fun mayBeSavePlaybackState() {}

    override fun mayBeRestoreQueue() {}

    override suspend fun restoreQueueAndPlay(): Boolean {
        if (mediaQueue.isNotEmpty()) {
            play()
            return true
        }
        return false
    }

    override fun shouldReleaseOnTaskRemoved(): Boolean = false
}
