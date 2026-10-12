package com.maxrave.simpmusic.expect.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import platform.UIKit.UIApplication
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.setStatusBarStyle

@Composable
actual fun platformDynamicColorScheme(isDark: Boolean): ColorScheme? = null

actual fun isWallpaperDynamicColorSupported(): Boolean = false

@Composable
actual fun SystemBarAppearanceEffect(isDark: Boolean) {
    runCatching {
        UIApplication.sharedApplication.setStatusBarStyle(
            if (isDark) UIStatusBarStyleLightContent else UIStatusBarStyleDarkContent,
            animated = true
        )
    }
}
