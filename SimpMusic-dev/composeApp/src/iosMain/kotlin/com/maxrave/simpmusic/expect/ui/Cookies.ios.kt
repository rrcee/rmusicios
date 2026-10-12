package com.maxrave.simpmusic.expect.ui

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
import platform.darwin.NSObject

actual fun createWebViewCookieManager(): WebViewCookieManager =
    object : WebViewCookieManager {
        override fun getCookie(url: String): String {
            val nsUrl = NSURL.URLWithString(url) ?: return ""
            val storage = NSHTTPCookieStorage.sharedHTTPCookieStorage
            val cookies = storage.cookiesForURL(nsUrl) as? List<NSHTTPCookie> ?: emptyList()
            return cookies.joinToString("; ") { "${it.name}=${it.value}" }
        }

        override fun removeAllCookies() {
            val storage = NSHTTPCookieStorage.sharedHTTPCookieStorage
            val cookies = storage.cookies as? List<NSHTTPCookie> ?: emptyList()
            cookies.forEach { storage.deleteCookie(it) }
        }
    }

private class NavigationDelegate(
    private val onPageFinished: (String) -> Unit,
    private val onProgress: (Int) -> Unit,
) : NSObject(), WKNavigationDelegateProtocol {
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        val currentUrl = webView.URL?.absoluteString ?: ""
        onProgress(100)
        onPageFinished(currentUrl)
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
                val webView = WKWebView(frame = platform.CoreGraphics.CGRectZero, configuration = config)
                val delegate = NavigationDelegate(
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
