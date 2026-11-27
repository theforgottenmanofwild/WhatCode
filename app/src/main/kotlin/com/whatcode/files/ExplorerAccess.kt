package com.whatcode.files

/**
 * When the file explorer has to ask for access to all files. Only the phone's shared storage needs it: the app's own
 * folders on it, and anything outside it, can be reached without. No Android here (unit tested).
 */
object ExplorerAccess {

    /** The folders on shared storage that belong to the app itself, for the app with the id [packageName]. */
    fun appFolders(sharedRoot: String, packageName: String): List<String> = listOf(
        ExplorerPaths.child(ExplorerPaths.child(sharedRoot, "Android/data"), packageName),
        ExplorerPaths.child(ExplorerPaths.child(sharedRoot, "Android/media"), packageName),
        ExplorerPaths.child(ExplorerPaths.child(sharedRoot, "Android/obb"), packageName),
    )

    /**
     * True if reaching [path] needs the access to all files: it is on the shared storage [sharedRoot] and is not
     * the storage itself or one of the app's own [appFolders]. The storage root only has to be listed, which the
     * explorer does at the start without asking.
     */
    fun needsAllFiles(path: String, sharedRoot: String, appFolders: List<String>): Boolean {
        val p = ExplorerPaths.normalize(path)
        if (p == ExplorerPaths.normalize(sharedRoot)) return false
        if (!ExplorerPaths.isInside(p, sharedRoot)) return false
        return appFolders.none { ExplorerPaths.isInside(p, it) }
    }
}
