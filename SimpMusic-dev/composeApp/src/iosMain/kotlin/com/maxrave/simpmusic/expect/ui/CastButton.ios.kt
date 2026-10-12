package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVKit.AVRoutePickerView
import platform.UIKit.UIColor

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformCastButton(
    modifier: Modifier,
    tint: Color,
) {
    UIKitView(
        factory = {
            AVRoutePickerView().apply {
                tintColor = UIColor.colorWithRed(
                    red = tint.red.toDouble(),
                    green = tint.green.toDouble(),
                    blue = tint.blue.toDouble(),
                    alpha = tint.alpha.toDouble(),
                )
            }
        },
        modifier = modifier,
        update = { view ->
            view.tintColor = UIColor.colorWithRed(
                red = tint.red.toDouble(),
                green = tint.green.toDouble(),
                blue = tint.blue.toDouble(),
                alpha = tint.alpha.toDouble(),
            )
        },
    )
}

actual fun isPlatformCastAvailable(): Boolean = true

private val NoCastReceivers = CastReceivers(
    receivers = emptyList(),
    connect = {},
    disconnect = {},
)

@Composable
actual fun rememberCastReceivers(discover: Boolean): CastReceivers = NoCastReceivers
