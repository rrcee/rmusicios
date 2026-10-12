package com.maxrave.simpmusic.viewModel

import com.eygraber.uri.Uri
import com.maxrave.domain.repository.CacheRepository
import com.maxrave.domain.repository.CommonRepository
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDefaults
import com.maxrave.common.Config
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSFileSystemSize
import platform.Foundation.NSNumber

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

actual suspend fun readPickedFile(uri: Uri): ByteArray = ByteArray(0)

actual suspend fun restoreNative(
    commonRepository: CommonRepository,
    uri: Uri,
    getData: () -> Unit,
) {
    getData()
}

actual suspend fun backupNative(
    commonRepository: CommonRepository,
    uri: Uri,
    backupDownloaded: Boolean,
) {
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
    if (platform.UIKit.UIApplication.sharedApplication.supportsAlternateIcons) {
        val iconName = if (iconKey == "default" || iconKey.isNullOrEmpty()) null else iconKey
        platform.UIKit.UIApplication.sharedApplication.setAlternateIconName(iconName, completionHandler = null)
    }
}

actual fun getAppIconNative(): String =
    platform.UIKit.UIApplication.sharedApplication.alternateIconName ?: "default"
