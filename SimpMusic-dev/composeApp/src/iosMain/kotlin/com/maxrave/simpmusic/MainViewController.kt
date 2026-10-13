package com.maxrave.simpmusic

import androidx.compose.ui.window.ComposeUIViewController
import com.maxrave.common.AppIdentity
import com.maxrave.data.di.loader.loadAllModules
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.di.viewModelModule
import com.maxrave.simpmusic.utils.VersionManager
import com.maxrave.simpmusic.viewModel.getFileDir
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import org.simpmusic.lastfm.configLastfm

fun initKoin() {
    if (KoinPlatform.getKoinOrNull() == null) {
        runCatching {
            Logger.enableFileLogging("${getFileDir()}/logs")
        }
        VersionManager.initialize()
        runCatching {
            configLastfm(BuildKonfig.lastfmApiKey, BuildKonfig.lastfmSecret)
        }
        startKoin {
            loadAllModules(
                AppIdentity(
                    applicationId = "com.rmusic.app",
                    versionName = BuildKonfig.versionName,
                    platform = "iOS",
                    isDevBuild = false,
                ),
            )
            loadKoinModules(viewModelModule)
        }
    }
}

fun MainViewController() = ComposeUIViewController {
    initKoin()
    App()
}