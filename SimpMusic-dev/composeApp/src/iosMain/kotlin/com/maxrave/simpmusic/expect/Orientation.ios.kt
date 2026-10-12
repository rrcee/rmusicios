package com.maxrave.simpmusic.expect

import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation

actual fun currentOrientation(): Orientation {
    val orientation = UIDevice.currentDevice.orientation
    return when (orientation) {
        UIDeviceOrientation.UIDeviceOrientationPortrait,
        UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown -> Orientation.PORTRAIT
        UIDeviceOrientation.UIDeviceOrientationLandscapeLeft,
        UIDeviceOrientation.UIDeviceOrientationLandscapeRight -> Orientation.LANDSCAPE
        else -> Orientation.PORTRAIT
    }
}
