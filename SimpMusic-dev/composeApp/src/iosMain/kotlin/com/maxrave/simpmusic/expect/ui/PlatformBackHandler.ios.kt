package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable

@Composable
internal actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // iOS handles back navigation via UI back buttons and interactive pop gesture
}
