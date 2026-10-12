package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject

private class DocumentPickerDelegate(
    private val onResult: (String?) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val firstUrl = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        onResult(firstUrl?.path)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onResult(null)
    }
}

@Composable
actual fun filePickerResult(onResultUri: (String?) -> Unit): FilePickerLauncher =
    remember {
        object : FilePickerLauncher {
            override fun launch() {
                val delegate = DocumentPickerDelegate(onResultUri)
                val picker = UIDocumentPickerViewController(
                    documentTypes = listOf("public.item", "public.data", "public.content"),
                    inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
                )
                picker.delegate = delegate
                var topController = UIApplication.sharedApplication.keyWindow?.rootViewController
                while (topController?.presentedViewController != null) {
                    topController = topController.presentedViewController
                }
                topController?.presentViewController(picker, animated = true, completion = null)
            }
        }
    }

@Composable
actual fun fileSaverResult(
    fileName: String,
    mimeType: String,
    onResultUri: (String?) -> Unit,
): FilePickerLauncher =
    remember {
        object : FilePickerLauncher {
            override fun launch() {
                val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
                val docDir = (paths.firstOrNull() as? String) ?: NSTemporaryDirectory()
                val targetPath = "$docDir/$fileName"
                onResultUri(targetPath)
            }
        }
    }
