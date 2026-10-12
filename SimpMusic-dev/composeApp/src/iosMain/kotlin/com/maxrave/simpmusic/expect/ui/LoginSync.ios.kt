package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable

@Composable
actual fun LoginSyncDialog(onDismiss: () -> Unit) {
    // iOS does not run local server or camera scanner for desktop sync
}
