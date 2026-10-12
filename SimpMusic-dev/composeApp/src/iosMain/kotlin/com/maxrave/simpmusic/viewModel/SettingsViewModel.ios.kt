package com.maxrave.simpmusic.viewModel

import com.eygraber.uri.Uri
import com.maxrave.domain.repository.CacheRepository
import com.maxrave.domain.repository.CommonRepository
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask

actual suspend fun calculateDataFraction(cacheRepository: CacheRepository): SettingsStorageSectionFraction? = null

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
