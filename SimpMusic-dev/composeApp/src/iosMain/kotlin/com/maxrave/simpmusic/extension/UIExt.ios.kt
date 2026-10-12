package com.maxrave.simpmusic.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.maxrave.domain.data.model.ui.ScreenSizeInfo
import kotlin.math.roundToInt
import platform.UIKit.UIApplication

@Composable
actual fun getScreenSizeInfo(): ScreenSizeInfo {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current
    val hPx = window.containerSize.height
    val wPx = window.containerSize.width
    return ScreenSizeInfo(
        hDP = with(density) { hPx.toDp().value.roundToInt() },
        wDP = with(density) { wPx.toDp().value.roundToInt() },
        hPX = hPx,
        wPX = wPx
    )
}

@Composable
actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        UIApplication.sharedApplication.idleTimerDisabled = true
        onDispose {
            UIApplication.sharedApplication.idleTimerDisabled = false
        }
    }
}

@Composable
actual fun rememberIsInPipMode(): Boolean = false
