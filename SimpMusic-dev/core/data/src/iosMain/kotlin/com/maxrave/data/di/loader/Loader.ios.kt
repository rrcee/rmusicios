package com.maxrave.data.di.loader

import com.maxrave.common.Config.DOWNLOAD_CACHE
import com.maxrave.common.Config.SERVICE_SCOPE
import com.maxrave.common.MERGING_DATA_TYPE
import com.maxrave.data.io.fileDir
import com.maxrave.data.io.fileSystem
import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.player.MediaPlayerInterface
import com.maxrave.domain.repository.CacheRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.toTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import org.koin.core.context.loadKoinModules
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val iosMediaServiceModule =
    module {
        single<CoroutineScope>(qualifier = named(SERVICE_SCOPE)) {
            CoroutineScope(Dispatchers.Main + SupervisorJob())
        }

        single<MediaPlayerInterface> {
            get<MediaPlayerHandler>().player
        }

        single<CacheRepository> {
            object : CacheRepository {
                override suspend fun getCacheSize(cacheName: String): Long =
                    runCatching {
                        if (cacheName == DOWNLOAD_CACHE) {
                            val dir = (fileDir() + "/downloads").toPath()
                            val fs = fileSystem()
                            if (!fs.exists(dir)) return@runCatching 0L
                            fs.list(dir).sumOf { file ->
                                fs.metadataOrNull(file)?.size ?: 0L
                            }
                        } else {
                            0L
                        }
                    }.getOrDefault(0L)

                override fun clearCache(cacheName: String) {
                    if (cacheName == DOWNLOAD_CACHE) {
                        runCatching {
                            val dir = (fileDir() + "/downloads").toPath()
                            val fs = fileSystem()
                            if (fs.exists(dir)) {
                                fs.list(dir).forEach { fs.delete(it) }
                            }
                        }
                    }
                }

                override suspend fun getAllCacheKeys(cacheName: String): List<String> =
                    runCatching {
                        if (cacheName == DOWNLOAD_CACHE) {
                            val dir = (fileDir() + "/downloads").toPath()
                            val fs = fileSystem()
                            if (!fs.exists(dir)) return@runCatching emptyList()
                            fs.list(dir).map { it.name.substringBeforeLast('.') }
                        } else {
                            emptyList()
                        }
                    }.getOrDefault(emptyList())
            }
        }

        single<DownloadHandler> {
            IosDownloadHandler(
                songRepository = get(),
            )
        }
    }

private class IosDownloadHandler(
    private val songRepository: SongRepository,
) : DownloadHandler {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _downloads =
        MutableStateFlow<Map<String, Pair<DownloadHandler.Download?, DownloadHandler.Download?>>>(emptyMap())
    override val downloads: StateFlow<Map<String, Pair<DownloadHandler.Download?, DownloadHandler.Download?>>>
        get() = _downloads

    private val _downloadTask = MutableStateFlow<Map<String, Int>>(emptyMap())
    override val downloadTask: StateFlow<Map<String, Int>>
        get() = _downloadTask

    private fun publishDownloadTask(
        videoId: String,
        state: Int?,
    ) {
        _downloadTask.update { current ->
            if (state == null) current - videoId else current + (videoId to state)
        }
    }

    private fun getDownloadDir(): String {
        val dir = fileDir() + "/downloads"
        runCatching { fileSystem().createDirectories(dir.toPath()) }
        return dir
    }

    override suspend fun downloadTrack(
        videoId: String,
        title: String,
        thumbnail: String,
    ) {
        val song = songRepository.getSongById(videoId).lastOrNull() ?: return
        songRepository.updateDownloadState(videoId, DownloadState.STATE_DOWNLOADING)
        publishDownloadTask(videoId, DownloadState.STATE_DOWNLOADING)
        val downloadDir = getDownloadDir()
        songRepository
            .downloadToFile(
                track = song.toTrack(),
                path = "$downloadDir/$videoId",
                videoId = videoId,
                isVideo = false,
            ).collect { state ->
                if (state.isError) {
                    songRepository.updateDownloadState(videoId, DownloadState.STATE_NOT_DOWNLOADED)
                    publishDownloadTask(videoId, DownloadState.STATE_NOT_DOWNLOADED)
                } else if (state.isDone) {
                    songRepository.updateDownloadState(videoId, DownloadState.STATE_DOWNLOADED)
                    publishDownloadTask(videoId, DownloadState.STATE_DOWNLOADED)
                }
            }
    }

    override fun removeDownload(videoId: String) {
        runCatching {
            val dir = getDownloadDir().toPath()
            val fs = fileSystem()
            if (fs.exists(dir)) {
                fs.list(dir).filter { it.name.contains(videoId) }.forEach { file ->
                    fs.delete(file)
                }
            }
            publishDownloadTask(videoId, null)
            coroutineScope.launch {
                songRepository.updateDownloadState(videoId, DownloadState.STATE_NOT_DOWNLOADED)
            }
        }
    }

    override fun removeAllDownloads() {
        _downloadTask.value = emptyMap()
        runCatching {
            val dir = getDownloadDir().toPath()
            val fs = fileSystem()
            if (fs.exists(dir)) {
                fs.list(dir).forEach { file ->
                    fs.delete(file)
                    val id = file.name.substringBefore('.').removePrefix(MERGING_DATA_TYPE.VIDEO)
                    coroutineScope.launch {
                        songRepository.updateDownloadState(id, DownloadState.STATE_NOT_DOWNLOADED)
                    }
                }
            }
        }
    }
}

actual fun loadMediaService() {
    loadKoinModules(iosMediaServiceModule)
}

actual fun loadLoginSyncModule() {
}
