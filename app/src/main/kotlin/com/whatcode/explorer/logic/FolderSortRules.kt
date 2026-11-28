package com.whatcode.explorer.logic

/**
 * Pure per-folder sort memory rules: maps normalized folder paths to remembered `(key, descending, showHidden)` specs,
 * bounded to [MAX_ENTRIES] most-recently-updated folders. No Android here (unit tested).
 */
object FolderSortRules {

    data class Spec(
        val key: ExplorerSort.Key,
        val descending: Boolean,
        val showHidden: Boolean,
    )

    const val MAX_ENTRIES = 120

    fun parse(raw: String?): LinkedHashMap<String, Spec> {
        val out = LinkedHashMap<String, Spec>()
        if (raw.isNullOrBlank()) return out
        for (line in raw.lineSequence()) {
            val parts = line.split('\t')
            if (parts.size < 4) continue
            val path = unescape(parts[0])
            val key = ExplorerSort.Key.values().firstOrNull { it.name == parts[1] } ?: continue
            val desc = parts[2] == "1"
            val hidden = parts[3] == "1"
            if (path.isNotEmpty()) out[path] = Spec(key, desc, hidden)
        }
        return out
    }

    fun write(map: Map<String, Spec>): String =
        map.entries.take(MAX_ENTRIES).joinToString("\n") { (path, spec) ->
            "${escape(path)}\t${spec.key.name}\t${if (spec.descending) "1" else "0"}\t${if (spec.showHidden) "1" else "0"}"
        }

    fun put(map: Map<String, Spec>, folderPath: String, spec: Spec): LinkedHashMap<String, Spec> {
        val norm = ExplorerPaths.normalize(folderPath)
        val out = LinkedHashMap<String, Spec>(map)
        out.remove(norm)
        out[norm] = spec
        while (out.size > MAX_ENTRIES) {
            val firstKey = out.keys.firstOrNull() ?: break
            out.remove(firstKey)
        }
        return out
    }

    fun get(map: Map<String, Spec>, folderPath: String): Spec? = map[ExplorerPaths.normalize(folderPath)]

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")

    private fun unescape(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '\\' -> sb.append('\\')
                    't' -> sb.append('\t')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    else -> sb.append(s[i + 1])
                }
                i += 2
            } else {
                sb.append(s[i])
                i++
            }
        }
        return sb.toString()
    }
}
