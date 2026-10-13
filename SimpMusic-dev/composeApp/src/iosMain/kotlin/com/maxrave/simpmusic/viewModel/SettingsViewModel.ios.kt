@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.maxrave.simpmusic.viewModel

import com.eygraber.uri.Uri
import com.maxrave.common.Config
import com.maxrave.domain.repository.CacheRepository
import com.maxrave.domain.repository.CommonRepository
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSFileSystemSize
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUserDefaults
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.getBytes
import platform.Foundation.NSError
import platform.Foundation.writeToFile
import platform.UIKit.*

@OptIn(ExperimentalForeignApi::class)
actual suspend fun calculateDataFraction(cacheRepository: CacheRepository): SettingsStorageSectionFraction? {
    return withContext(Dispatchers.Default) {
        runCatching {
            val playerCache = cacheRepository.getCacheSize(Config.PLAYER_CACHE)
            val downloadCache = cacheRepository.getCacheSize(Config.DOWNLOAD_CACHE)
            val canvasCache = cacheRepository.getCacheSize(Config.CANVAS_CACHE)

            val home = NSHomeDirectory()
            val attrs = NSFileManager.defaultManager.attributesOfFileSystemForPath(home, error = null)
            val totalBytes = (attrs?.get(NSFileSystemSize) as? NSNumber)?.longLongValue ?: 0L
            val freeBytes = (attrs?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue ?: 0L

            if (totalBytes > 0L) {
                val totalFloat = totalBytes.toFloat()
                val playerFraction = (playerCache.toFloat() / totalFloat).coerceIn(0f, 1f)
                val downloadFraction = (downloadCache.toFloat() / totalFloat).coerceIn(0f, 1f)
                val canvasFraction = (canvasCache.toFloat() / totalFloat).coerceIn(0f, 1f)
                val freeFraction = (freeBytes.toFloat() / totalFloat).coerceIn(0f, 1f)
                val usedOther = (totalBytes - freeBytes - playerCache - downloadCache - canvasCache).coerceAtLeast(0L)
                val otherFraction = (usedOther.toFloat() / totalFloat).coerceIn(0f, 1f)

                SettingsStorageSectionFraction(
                    otherApp = otherFraction,
                    downloadCache = downloadFraction,
                    playerCache = playerFraction,
                    canvasCache = canvasFraction,
                    thumbCache = 0f,
                    freeSpace = freeFraction,
                    appDatabase = 0.001f,
                )
            } else {
                null
            }
        }.getOrNull()
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun readPickedFile(uri: Uri): ByteArray = withContext(Dispatchers.Default) {
    runCatching {
        val path = uri.path ?: uri.toString()
        val data = NSData.dataWithContentsOfFile(path)
            ?: NSData.dataWithContentsOfURL(NSURL.fileURLWithPath(path))
            ?: return@runCatching ByteArray(0)
        val length = data.length.toInt()
        if (length == 0) return@runCatching ByteArray(0)
        val array = ByteArray(length)
        array.usePinned { pinned ->
            data.getBytes(pinned.addressOf(0), length.toULong())
        }
        array
    }.getOrDefault(ByteArray(0))
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun restoreNative(
    commonRepository: CommonRepository,
    uri: Uri,
    getData: () -> Unit,
) {
    withContext(Dispatchers.Default) {
        runCatching {
            val sourcePath = uri.path ?: uri.toString()
            val sourceData = NSData.dataWithContentsOfFile(sourcePath)
                ?: NSData.dataWithContentsOfURL(NSURL.fileURLWithPath(sourcePath))
                ?: return@runCatching
            commonRepository.databaseDaoCheckpoint()
            commonRepository.closeDatabase()
            val dbPath = commonRepository.getDatabasePath() ?: return@runCatching
            sourceData.writeToFile(dbPath, true)
            withContext(Dispatchers.Main) {
                getData()
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun backupNative(
    commonRepository: CommonRepository,
    uri: Uri,
    backupDownloaded: Boolean,
) {
    withContext(Dispatchers.Default) {
        runCatching {
            commonRepository.databaseDaoCheckpoint()
            val dbPath = commonRepository.getDatabasePath() ?: return@runCatching
            val dbData = NSData.dataWithContentsOfFile(dbPath) ?: return@runCatching
            val targetPath = uri.path ?: uri.toString()
            dbData.writeToFile(targetPath, true)
        }
    }
}

actual fun getPackageName(): String =
    NSBundle.mainBundle.bundleIdentifier ?: "com.maxrave.simpmusic"

actual fun getFileDir(): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
    return (paths.firstOrNull() as? String) ?: NSHomeDirectory()
}

actual fun changeLanguageNative(code: String) {
    NSUserDefaults.standardUserDefaults.setObject(listOf(code), forKey = "AppleLanguages")
    NSUserDefaults.standardUserDefaults.synchronize()
}

actual fun setAppIconNative(iconKey: String?) {
    platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
        runCatching {
            val app = UIApplication.sharedApplication
            if (app.supportsAlternateIcons) {
                val target = if (iconKey.isNullOrEmpty() || iconKey == "default") null else iconKey
                app.setAlternateIconName(target) { _: NSError? -> }
            }
        }
    }
}

actual fun getAppIconNative(): String =
    runCatching {
        UIApplication.sharedApplication.alternateIconName ?: "default"
    }.getOrDefault("default")
