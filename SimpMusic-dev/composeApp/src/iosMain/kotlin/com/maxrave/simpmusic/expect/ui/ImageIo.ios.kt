package com.maxrave.simpmusic.expect.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.maxrave.logger.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToFile

private const val TAG = "ImageIo"
private const val COVER_DIR = "playlist_covers"

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.onFailure { Logger.w(TAG, "Could not decode image: ${it.message}") }.getOrNull()

@OptIn(ExperimentalForeignApi::class)
actual suspend fun persistPickedImage(
    bytes: ByteArray,
    fileName: String,
): String? = withContext(Dispatchers.Default) {
    runCatching {
        val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        val docDir = paths.firstOrNull() as? String ?: return@runCatching null
        val coverDir = "$docDir/$COVER_DIR"
        NSFileManager.defaultManager.createDirectoryAtPath(coverDir, true, null, null)
        val filePath = "$coverDir/$fileName"
        val nsData = bytes.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
        }
        nsData.writeToFile(filePath, true)
        NSURL.fileURLWithPath(filePath).absoluteString
    }.onFailure { Logger.w(TAG, "Could not persist image: ${it.message}") }.getOrNull()
}
