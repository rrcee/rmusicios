package com.maxrave.simpmusic

import androidx.compose.ui.window.ComposeUIViewController
import com.maxrave.common.AppIdentity
import com.maxrave.data.di.loader.loadAllModules
import com.maxrave.simpmusic.di.viewModelModule
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform

fun initKoin() {
    if (KoinPlatform.getKoinOrNull() == null) {
        startKoin {
            loadAllModules(
                AppIdentity(
                    applicationId = "com.maxrave.simpmusic",
                    versionName = "1.2.1",
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