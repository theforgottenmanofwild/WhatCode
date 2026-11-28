package com.whatcode.apk

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.util.LruCache
import java.io.File
import java.util.concurrent.Executors

/** Bounded in-memory LRU cache of app names and icons read from `.apk` files for explorer rows. */
object ApkRowCache {

    data class Meta(val label: String?, val icon: Bitmap?, val bytes: Int)

    private const val DEFAULT_LIMIT = 4 * 1024 * 1024
    private const val MAX_ICON_PX = 96

    private val lock = Any()
    private val cache = object : LruCache<String, Meta>(DEFAULT_LIMIT) {
        override fun sizeOf(key: String, value: Meta): Int = value.bytes.coerceAtLeast(64)
    }
    private val pool = Executors.newFixedThreadPool(2)
    private val inFlight = HashSet<String>()

    fun configure(limitMb: Int) {
        val bytes = (limitMb.coerceAtLeast(1)) * 1024 * 1024
        synchronized(lock) {
            cache.resize(bytes)
        }
    }

    fun clear() {
        synchronized(lock) {
            cache.evictAll()
            inFlight.clear()
        }
    }

    fun keyFor(path: String, modified: Long, size: Long): String = "$path:$modified:$size"

    fun peek(path: String, modified: Long, size: Long): Meta? {
        val k = keyFor(path, modified, size)
        synchronized(lock) {
            return cache.get(k)
        }
    }

    fun request(
        context: Context,
        path: String,
        modified: Long,
        size: Long,
        limitMb: Int,
        ui: Handler,
        onReady: () -> Unit,
    ) {
        configure(limitMb)
        val k = keyFor(path, modified, size)
        synchronized(lock) {
            if (cache.get(k) != null || !inFlight.add(k)) return
        }
        val app = context.applicationContext
        pool.execute {
            val meta = try {
                loadMeta(app, File(path))
            } catch (_: Exception) {
                Meta(null, null, 64)
            }
            synchronized(lock) {
                inFlight.remove(k)
                cache.put(k, meta)
            }
            ui.post(onReady)
        }
    }

    private fun loadMeta(context: Context, file: File): Meta {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val pkg = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return Meta(null, null, 64)
        val info = pkg.applicationInfo ?: return Meta(null, null, 64)
        info.sourceDir = file.absolutePath
        info.publicSourceDir = file.absolutePath
        val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() && it != pkg.packageName }
            ?: info.loadLabel(pm)?.toString()
        val drawable = try {
            info.loadIcon(pm)
        } catch (_: Exception) {
            null
        }
        val bmp = drawable?.let(::toThumbnail)
        val bytes = (bmp?.allocationByteCount ?: 0) + (label?.length ?: 0) * 2 + 128
        return Meta(label, bmp, bytes)
    }

    private fun toThumbnail(d: Drawable): Bitmap? {
        if (d is BitmapDrawable && d.bitmap != null) {
            val b = d.bitmap
            if (b.width <= MAX_ICON_PX && b.height <= MAX_ICON_PX) return b
            val scale = minOf(MAX_ICON_PX.toFloat() / b.width, MAX_ICON_PX.toFloat() / b.height)
            return Bitmap.createScaledBitmap(b, (b.width * scale).toInt().coerceAtLeast(1), (b.height * scale).toInt().coerceAtLeast(1), true)
        }
        val w = d.intrinsicWidth.coerceIn(1, MAX_ICON_PX)
        val h = d.intrinsicHeight.coerceIn(1, MAX_ICON_PX)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, w, h)
        d.draw(canvas)
        return bmp
    }
}
