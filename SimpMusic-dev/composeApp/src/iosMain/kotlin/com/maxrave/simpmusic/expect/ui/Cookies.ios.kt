@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.maxrave.simpmusic.expect.ui

import kotlinx.cinterop.readValue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieStorage
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject

object IosCookieStore {
    var lastCookies: String = ""
}

actual fun createWebViewCookieManager(): WebViewCookieManager =
    object : WebViewCookieManager {
        override fun getCookie(url: String): String {
            if (IosCookieStore.lastCookies.isNotEmpty()) {
                return IosCookieStore.lastCookies
            }
            val nsUrl = NSURL.URLWithString(url) ?: return ""
            val storage = NSHTTPCookieStorage.sharedHTTPCookieStorage
            val cookies = storage.cookiesForURL(nsUrl) as? List<NSHTTPCookie> ?: emptyList()
            return cookies.joinToString("; ") { "${it.name}=${it.value}" }
        }

        override fun removeAllCookies() {
            IosCookieStore.lastCookies = ""
            val storage = NSHTTPCookieStorage.sharedHTTPCookieStorage
            val cookies = storage.cookies as? List<NSHTTPCookie> ?: emptyList()
            cookies.forEach { storage.deleteCookie(it) }
            WKWebsiteDataStore.defaultDataStore().httpCookieStore.getAllCookies { list ->
                list?.filterIsInstance<NSHTTPCookie>()?.forEach {
                    WKWebsiteDataStore.defaultDataStore().httpCookieStore.deleteCookie(it, null)
                }
            }
        }
    }

private class NavigationDelegate(
    private val webView: WKWebView,
    private val onPageFinished: (String) -> Unit,
    private val onProgress: (Int) -> Unit,
) : NSObject(), WKNavigationDelegateProtocol {
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        val currentUrl = webView.URL?.absoluteString ?: ""
        onProgress(100)
        webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { list ->
            val cookieList = list?.filterIsInstance<NSHTTPCookie>() ?: emptyList()
            if (cookieList.isNotEmpty()) {
                val cookieStr = cookieList.joinToString("; ") { "${it.name}=${it.value}" }
                IosCookieStore.lastCookies = cookieStr
            }
            onPageFinished(currentUrl)
        }
    }
}

@Composable
actual fun PlatformWebView(
    state: MutableState<WebViewState>,
    initUrl: String,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onPageFinished: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        UIKitView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                val config = WKWebViewConfiguration()
                config.websiteDataStore = WKWebsiteDataStore.defaultDataStore()
                val webView = WKWebView(frame = platform.CoreGraphics.CGRectZero.readValue(), configuration = config)
                // Set standard mobile Safari user agent so Google login is not blocked
                webView.customUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
                val delegate = NavigationDelegate(
                    webView = webView,
                    onPageFinished = { url ->
                        state.value = WebViewState.Finished
                        onPageFinished(url)
                    },
                    onProgress = { p ->
                        if (p >= 100) state.value = WebViewState.Finished
                        else state.value = WebViewState.Loading(p)
                    }
                )
                webView.navigationDelegate = delegate
                val request = NSURLRequest(uRL = NSURL.URLWithString(initUrl)!!)
                webView.loadRequest(request)
                webView
            }
        )
        aboveContent()
    }
}

@Composable
actual fun DiscordWebView(
    state: MutableState<WebViewState>,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onLoginDone: (token: String) -> Unit,
) {
    PlatformWebView(
        state = state,
        initUrl = "https://discord.com/login",
        aboveContent = aboveContent,
        onPageFinished = { url ->
            // Check for login redirection
        }
    )
}
