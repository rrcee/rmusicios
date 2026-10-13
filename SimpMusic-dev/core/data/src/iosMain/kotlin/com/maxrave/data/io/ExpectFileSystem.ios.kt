package com.maxrave.data.io

import com.maxrave.data.db.documentDirectory
import okio.FileSystem
import okio.Path.Companion.toPath

actual fun fileSystem(): FileSystem = FileSystem.SYSTEM

actual fun fileDir(): String {
    val dir = documentDirectory() + "/SimpMusic"
    runCatching { FileSystem.SYSTEM.createDirectories(dir.toPath()) }
    return dir
}