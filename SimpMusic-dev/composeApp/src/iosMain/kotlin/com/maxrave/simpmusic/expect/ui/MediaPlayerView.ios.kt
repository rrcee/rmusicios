@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.maxrave.simpmusic.expect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.text.TextStyle
import com.maxrave.domain.data.model.metadata.Lyrics
import com.maxrave.domain.data.model.streams.TimeLine
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerActionAtItemEndNone
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.actionAtItemEnd
import platform.AVFoundation.currentItem
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVKit.AVPlayerViewController
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIColor
import platform.UIKit.UIView

@Composable
actual fun MediaPlayerView(
    url: String,
    modifier: Modifier,
    cropToBounds: Boolean,
    onFirstFrame: () -> Unit,
) {
    if (url.isNotEmpty()) {
        val player = remember(url) {
            val nsUrl = NSURL.URLWithString(url)
            if (nsUrl != null) {
                AVPlayer(uRL = nsUrl).apply {
                    muted = true
                    actionAtItemEnd = AVPlayerActionAtItemEndNone
                }
            } else {
                null
            }
        }
        DisposableEffect(player) {
            val observer = if (player != null) {
                NSNotificationCenter.defaultCenter.addObserverForName(
                    name = AVPlayerItemDidPlayToEndTimeNotification,
                    `object` = player.currentItem,
                    queue = NSOperationQueue.mainQueue,
                ) { _ ->
                    player.seekToTime(CMTimeMake(value = 0, timescale = 1))
                    player.play()
                }
            } else {
                null
            }
            onDispose {
                if (observer != null) {
                    NSNotificationCenter.defaultCenter.removeObserver(observer)
                }
                player?.pause()
            }
        }
        val controller = remember(url) {
            AVPlayerViewController().apply {
                this.player = player
                this.showsPlaybackControls = false
                this.updatesNowPlayingInfoCenter = false
                this.videoGravity = if (cropToBounds) {
                    AVLayerVideoGravityResizeAspectFill
                } else {
                    AVLayerVideoGravityResizeAspect
                }
                this.view.backgroundColor = UIColor.clearColor
                this.view.userInteractionEnabled = false
            }
        }
        UIKitView(
            modifier = modifier,
            factory = {
                player?.play()
                onFirstFrame()
                controller.view
            },
            update = { _ ->
                player?.play()
            },
        )
    } else {
        Box(modifier = modifier.background(Color.Black))
    }
}

@Composable
actual fun MediaPlayerViewWithSubtitle(
    modifier: Modifier,
    playerName: String,
    shouldPip: Boolean,
    shouldShowSubtitle: Boolean,
    shouldScaleDownSubtitle: Boolean,
    isInPipMode: Boolean,
    timelineState: TimeLine,
    lyricsData: Lyrics?,
    translatedLyricsData: Lyrics?,
    mainTextStyle: TextStyle,
    translatedTextStyle: TextStyle,
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black))
}

@Composable
actual fun rememberVideoAspectRatio(playerName: String): Float? = 16f / 9f
