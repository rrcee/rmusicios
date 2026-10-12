package com.maxrave.simpmusic.expect

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToFile
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

        val tempDir = NSTemporaryDirectory()
        val tempFilePath = "$tempDir/$fileName"
        nsData.writeToFile(tempFilePath, true)
        val fileUrl = NSURL.fileURLWithPath(tempFilePath)

        val activityItems = if (mimeType.startsWith("image/")) {
            val image = UIImage.imageWithData(nsData)
            if (image != null) listOf(image, fileUrl) else listOf(fileUrl)
        } else {
            listOf(fileUrl)
        }

        val activityVC = UIActivityViewController(activityItems = activityItems, applicationActivities = null)
        var topController = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (topController?.presentedViewController != null) {
            topController = topController.presentedViewController
        }
        topController?.presentViewController(activityVC, animated = true, completion = null)
        true
    }.getOrDefault(false)
}
