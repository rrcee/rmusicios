package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable

// iOS controls system volume via hardware buttons or system MPVolumeView.
@Composable
actual fun rememberDeviceVolumeController(): DeviceVolumeController? = null
