package com.whatcode.explorer

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import com.whatcode.explorer.logic.ExplorerPaths
import java.io.File

/**
 * Discovers mounted storage volumes (phone internal storage, SD cards, USB drives) cleanly using [StorageManager]
 * on API 30+ and external storage paths on older APIs.
 */
object StorageRoots {

    data class Root(val label: String, val path: String, val removable: Boolean)

    fun list(context: Context): List<Root> {
        val out = ArrayList<Root>()
        val seen = HashSet<String>()
        val primary = ExplorerPaths.normalize(Environment.getExternalStorageDirectory().path)
        seen.add(primary)
        out.add(Root("Phone storage", primary, removable = false))

        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        if (sm != null) {
            for (vol in sm.storageVolumes) {
                val dir = if (Build.VERSION.SDK_INT >= 30) vol.directory else null
                if (dir != null) {
                    val norm = ExplorerPaths.normalize(dir.path)
                    if (seen.add(norm) && dir.exists()) {
                        val label = vol.getDescription(context).takeIf { it.isNotBlank() }
                            ?: if (vol.isRemovable) "SD card / USB" else dir.name
                        out.add(Root(label, norm, vol.isRemovable))
                    }
                }
            }
        }
        // Fallback discovery for external volume roots on Android 8–10.
        for (ext in context.getExternalFilesDirs(null)) {
            if (ext == null) continue
            val p = ext.absolutePath
            val marker = "/Android/data/"
            val idx = p.indexOf(marker)
            if (idx > 0) {
                val rootPath = ExplorerPaths.normalize(p.substring(0, idx))
                if (seen.add(rootPath) && File(rootPath).exists()) {
                    out.add(Root(ExplorerPaths.name(rootPath).ifEmpty { "External storage" }, rootPath, removable = true))
                }
            }
        }
        return out
    }
}
