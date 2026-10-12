package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.AVFAudio.AVAudioSession

@Composable
actual fun rememberDeviceVolumeController(): DeviceVolumeController? =
    remember {
        object : DeviceVolumeController {
            override val volumeFraction: Float
                get() = runCatching { AVAudioSession.sharedInstance().outputVolume }.getOrDefault(1.0f)

            override fun setVolumeFraction(fraction: Float) {
                // iOS controls volume via hardware buttons or MPVolumeView
            }
        }
    }
