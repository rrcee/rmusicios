package com.maxrave.simpmusic.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSDate
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerImageURL
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject

private class ImagePickerDelegate(
    private val onResult: (String?) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val imageUri = didFinishPickingMediaWithInfo[UIImagePickerControllerImageURL] as? NSURL
        if (imageUri?.path != null) {
            onResult(imageUri.path)
            return
        }
        val uiImage = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        if (uiImage != null) {
            val data = UIImageJPEGRepresentation(uiImage, 0.9)
            val time = NSDate().timeIntervalSince1970
            val tempPath = "${NSTemporaryDirectory()}/picked_photo_$time.jpg"
            data?.writeToFile(tempPath, true)
            onResult(tempPath)
        } else {
            onResult(null)
        }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onResult(null)
    }
}

@Composable
actual fun photoPickerResult(onResultUri: (String?) -> Unit): PhotoPickerLauncher =
    remember {
        object : PhotoPickerLauncher {
            override fun launch() {
                val delegate = ImagePickerDelegate(onResultUri)
                val picker = UIImagePickerController().apply {
                    this.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
                    this.delegate = delegate
                }
                var topController = UIApplication.sharedApplication.keyWindow?.rootViewController
                while (topController?.presentedViewController != null) {
                    topController = topController.presentedViewController
                }
                topController?.presentViewController(picker, animated = true, completion = null)
            }
        }
    }
