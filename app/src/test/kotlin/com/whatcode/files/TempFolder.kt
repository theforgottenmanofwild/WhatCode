package com.whatcode.files

import java.io.File

/** A fresh empty folder for a test, made under the build folder (the system's temp folder is not always writable). */
class TempFolder {
    val root: File = File(System.getProperty("user.dir"), "build/test-folders/${System.nanoTime()}").also { it.mkdirs() }

    fun file(path: String, text: String = ""): File = File(root, path).also {
        it.parentFile?.mkdirs()
        it.writeText(text)
    }

    fun folder(path: String): File = File(root, path).also { it.mkdirs() }

    fun delete() {
        root.deleteRecursively()
    }
}
