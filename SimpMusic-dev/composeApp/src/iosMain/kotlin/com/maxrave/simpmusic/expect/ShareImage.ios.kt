package com.maxrave.simpmusic.expect

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageWriteToSavedPhotosAlbum

@OptIn(ExperimentalForeignApi::class)
actual suspend fun saveImageToDevice(
    bytes: ByteArray,
    fileName: String,
): Boolean = withContext(Dispatchers.Main) {
    runCatching {
        val nsData = bytes.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
        }
        val uiImage = UIImage.imageWithData(nsData) ?: return@runCatching false
        UIImageWriteToSavedPhotosAlbum(uiImage, null, null, null)
        true
    }.getOrDefault(false)
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun shareImage(
    bytes: ByteArray,
    fileName: String,
    chooserTitle: String,
    mimeType: String,
): Boolean = withContext(Dispatchers.Main) {
    runCatching {
        val nsData = bytes.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
        }
        val image = UIImage.imageWithData(nsData) ?: nsData
        val activityVC = UIActivityViewController(activityItems = listOf(image), applicationActivities = null)
        val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootVC?.presentViewController(activityVC, animated = true, completion = null)
        true
    }.getOrDefault(false)
}
