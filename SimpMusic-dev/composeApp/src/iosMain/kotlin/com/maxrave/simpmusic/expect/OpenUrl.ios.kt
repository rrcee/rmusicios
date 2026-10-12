package com.maxrave.simpmusic.expect

import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

actual fun openUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl)
}

actual fun shareUrl(
    title: String,
    url: String,
) {
    val items = listOf(url)
    val activityVC = UIActivityViewController(activityItems = items, applicationActivities = null)
    val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
    rootVC?.presentViewController(activityVC, animated = true, completion = null)
}
