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
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.play
import platform.Foundation.NSURL
import platform.UIKit.UIView

@Composable
actual fun MediaPlayerView(
    url: String,
    modifier: Modifier,
    cropToBounds: Boolean,
    onFirstFrame: () -> Unit,
) {
    if (url.isNotEmpty()) {
        UIKitView(
            modifier = modifier,
            factory = {
                val containerView = UIView()
                val nsUrl = NSURL.URLWithString(url)
                if (nsUrl != null) {
                    val player = AVPlayer(uRL = nsUrl)
                    val layer = AVPlayerLayer.playerLayerWithPlayer(player)
                    layer.frame = containerView.bounds
                    containerView.layer.addSublayer(layer)
                    player.play()
                    onFirstFrame()
                }
                containerView
            }
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
