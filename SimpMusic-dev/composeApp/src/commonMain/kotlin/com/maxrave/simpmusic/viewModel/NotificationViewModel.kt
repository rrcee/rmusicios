package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.domain.data.entities.ArtistEntity
import com.maxrave.domain.data.entities.FollowedArtistSingleAndAlbum
import com.maxrave.domain.data.entities.NotificationEntity
import com.maxrave.domain.data.model.searchResult.albums.AlbumsResult
import com.maxrave.domain.extension.now
import com.maxrave.domain.repository.AlbumRepository
import com.maxrave.domain.repository.ArtistRepository
import com.maxrave.domain.repository.CommonRepository
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.extension.isNewRelease
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val commonRepository: CommonRepository,
    private val artistRepository: ArtistRepository,
    private val albumRepository: AlbumRepository,
) : BaseViewModel() {
    private var _listNotification: MutableStateFlow<List<NotificationEntity>?> =
        MutableStateFlow(null)
    val listNotification: StateFlow<List<NotificationEntity>?> = _listNotification

    init {
        viewModelScope.launch {
            commonRepository.getAllNotifications().collect { notificationEntities ->
                _listNotification.value =
                    notificationEntities?.sortedByDescending {
                        it.time
                    }
            }
        }
        checkNewReleases()
    }

    fun checkNewReleases() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val artistList: List<ArtistEntity> = artistRepository.getFollowedArtists().firstOrNull() ?: emptyList()
                val listFollowedArtistSingleAndAlbum =
                    albumRepository.getAllFollowedArtistSingleAndAlbums().firstOrNull() ?: emptyList()

                artistList.forEach { art ->
                    try {
                        combine(
                            albumRepository.getAlbumMore("MPAD${art.channelId}", MoreAlbumsViewModel.ALBUM_PARAM),
                            albumRepository.getAlbumMore("MPAD${art.channelId}", MoreAlbumsViewModel.SINGLE_PARAM),
                        ) { album, single ->
                            Pair(album, single)
                        }.firstOrNull()?.let { pair ->
                            val albumItem = pair.first?.second.orEmpty()
                            val singleItem = pair.second?.second.orEmpty()
                            val saved = listFollowedArtistSingleAndAlbum.find { it.channelId == art.channelId }
                            val known = saved?.let { it.album + it.single }.orEmpty()
                                .mapNotNull { it["browseId"] }
                                .toSet()

                            if (known.isNotEmpty()) {
                                val newAlbums = albumItem.filter { isNewRelease(it.browseId, it.year, known) }
                                val newSingles = singleItem.filter { isNewRelease(it.browseId, it.year, known) }

                                if (newAlbums.isNotEmpty() || newSingles.isNotEmpty()) {
                                    commonRepository.insertNotification(
                                        NotificationEntity(
                                            channelId = art.channelId,
                                            thumbnail = art.thumbnails,
                                            name = art.name,
                                            single = newSingles.toNotificationMap(),
                                            album = newAlbums.toNotificationMap(),
                                            time = now(),
                                            type = NotificationEntity.TYPE_ARTIST,
                                        ),
                                    )
                                }
                            }

                            albumRepository.insertFollowedArtistSingleAndAlbum(
                                FollowedArtistSingleAndAlbum(
                                    channelId = art.channelId,
                                    name = art.name,
                                    single = (saved?.single.orEmpty() + singleItem.toNotificationMap()).distinctBy { it["browseId"] },
                                    album = (saved?.album.orEmpty() + albumItem.toNotificationMap()).distinctBy { it["browseId"] },
                                ),
                            )
                        }
                    } catch (e: Exception) {
                        Logger.w("NotificationViewModel", "Failed checking updates for artist ${art.name}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Logger.w("NotificationViewModel", "Failed checking new releases: ${e.message}")
            }
        }
    }
}

private fun List<AlbumsResult>.toNotificationMap(): List<Map<String, String>> =
    map { item ->
        mapOf(
            "browseId" to item.browseId,
            "title" to item.title,
            "thumbnails" to (item.thumbnails.lastOrNull()?.url ?: ""),
        )
    }