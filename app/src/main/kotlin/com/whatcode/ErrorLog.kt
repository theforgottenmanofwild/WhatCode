package com.whatcode

import android.content.Context

/** Where failures that must not stop the app are written down. */
internal object ErrorLog {
    /** The kind of error, and the line of this app that it came from, for a message. */
    fun describe(t: Throwable): String {
        val frame = t.stackTrace.firstOrNull { it.className.startsWith("com.whatcode") }
        return t.javaClass.simpleName + (frame?.let { " at ${it.fileName}:${it.lineNumber}" } ?: "")
    }

    /** Writes a failure to the log (and to files/error_log.txt) so it can be found later. Never throws. */
    fun log(context: Context, what: String, t: Throwable) {
        runCatching { android.util.Log.e("WhatCode", what, t) }
        runCatching {
            val f = java.io.File(context.filesDir, "error_log.txt")
            if (f.length() > 64 * 1024) f.delete()
            f.appendText("[$what] ${android.util.Log.getStackTraceString(t)}\n")
        }
    }
}
