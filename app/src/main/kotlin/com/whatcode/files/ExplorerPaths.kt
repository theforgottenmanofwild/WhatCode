package com.whatcode.files

/**
 * The path rules of the file explorer: tidying a path, going up, the breadcrumb at the top of the explorer, what
 * becomes of a path when a folder is renamed or moved, and the folder on the phone that a folder picked in
 * Android's picker stands for. Paths are plain strings with `/` between the names. No Android here (unit tested).
 */
object ExplorerPaths {

    /** One step of the breadcrumb: the name to show and the folder it goes to. */
    class Crumb(val label: String, val path: String)

    /** [path] with doubled and trailing slashes removed. The root stays `/`. */
    fun normalize(path: String): String {
        if (path.isEmpty()) return path
        val absolute = path.startsWith("/")
        val parts = path.split('/').filter { it.isNotEmpty() }
        val joined = parts.joinToString("/")
        return if (absolute) "/$joined" else joined
    }

    /** The folder [name] in [folder]. */
    fun child(folder: String, name: String): String {
        val base = normalize(folder)
        return if (base == "/") "/$name" else "$base/$name"
    }

    /** The last name in [path], or the path itself for the root. */
    fun name(path: String): String {
        val p = normalize(path)
        return p.substringAfterLast('/').ifEmpty { p }
    }

    /** True if [path] is [folder] itself or anything below it. `/a/bc` is not inside `/a/b`. */
    fun isInside(path: String, folder: String): Boolean {
        val p = normalize(path)
        val f = normalize(folder)
        if (f == "/") return p.startsWith("/")
        return p == f || p.startsWith("$f/")
    }

    /** The folder above [path], or null when [path] is [root] (or not inside it), so the explorer can't leave it. */
    fun parent(path: String, root: String): String? {
        val p = normalize(path)
        val r = normalize(root)
        if (p == r || !isInside(p, r)) return null
        val up = p.substringBeforeLast('/', "")
        return if (up.isEmpty()) "/" else up
    }

    /**
     * The breadcrumb for [path]: [rootLabel] for [root], then one step per folder below it. A path that is not
     * inside [root] gets the root step alone.
     */
    fun crumbs(path: String, root: String, rootLabel: String): List<Crumb> {
        val p = normalize(path)
        val r = normalize(root)
        val list = arrayListOf(Crumb(rootLabel, r))
        if (p == r || !isInside(p, r)) return list
        val rest = if (r == "/") p.removePrefix("/") else p.removePrefix("$r/")
        var at = r
        for (part in rest.split('/')) {
            at = child(at, part)
            list.add(Crumb(part, at))
        }
        return list
    }

    /**
     * Where [path] is after [from] was renamed or moved to [to]: the same place below [to]. Null when [path] is not
     * [from] or inside it, so nothing about it changed.
     */
    fun rebase(path: String, from: String, to: String): String? {
        val p = normalize(path)
        val f = normalize(from)
        if (!isInside(p, f)) return null
        val t = normalize(to)
        return if (p == f) t else t + p.substring(f.length)
    }

    /**
     * The folder on the phone that a folder picked in Android's picker stands for, from the picker's document id
     * (`primary:Documents/site`) and the path of the phone's own storage. Null for an SD card, a cloud folder and
     * anything else that has no path the explorer can show.
     */
    fun fromTreeId(treeId: String, primaryRoot: String): String? {
        if (!treeId.startsWith("primary:")) return null
        val rest = treeId.removePrefix("primary:").trim('/')
        return if (rest.isEmpty()) normalize(primaryRoot) else child(primaryRoot, rest)
    }
}
