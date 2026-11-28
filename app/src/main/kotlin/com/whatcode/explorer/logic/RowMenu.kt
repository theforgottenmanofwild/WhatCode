package com.whatcode.explorer.logic

/**
 * The actions of the menu on a row of the explorer, which of them show and in what order. The choice is saved as a
 * line of text such as `rename,copy,-share,move`: the ids in their order, with a `-` before the ones that are hidden.
 * No Android here (unit tested).
 */
object RowMenu {

    /** One action, with its id in the saved text and its name in the menu. */
    enum class Action(val id: String, val label: String, val forFolders: Boolean = true, val forFiles: Boolean = true) {
        RENAME("rename", "Rename"),
        COPY("copy", "Copy"),
        MOVE("move", "Move"),
        DELETE("delete", "Delete"),
        SHARE("share", "Share", forFolders = false),
        PROPERTIES("properties", "Properties"),
        COPY_PATH("copy_path", "Copy path"),
        OPEN_WITH("open_with", "Open with", forFolders = false),
        ;

        /** True when the action can be done on a selection of more than one thing. */
        val forMany: Boolean get() = this == COPY || this == MOVE || this == DELETE || this == SHARE
    }

    /** One line of the menu's setting: an action and whether it shows. */
    data class Item(val action: Action, val shown: Boolean)

    /** Every action, shown, in the order of [Action]. */
    val DEFAULT: List<Item> = Action.values().map { Item(it, true) }

    /** The longest saved text that is read back; a real one is under 100 characters. */
    const val MAX_TEXT = 400

    /**
     * The menu as [saved] says, or [DEFAULT] for nothing saved. Ids that are not known are dropped, each action counts
     * once (the first time), and actions the text does not name (added by a later version) go at the end, shown.
     */
    fun parse(saved: String?): List<Item> {
        if (saved.isNullOrEmpty() || saved.length > MAX_TEXT) return DEFAULT
        val out = ArrayList<Item>()
        for (part in saved.split(',')) {
            val hidden = part.startsWith("-")
            val id = part.removePrefix("-").trim()
            val action = Action.values().firstOrNull { it.id == id } ?: continue
            if (out.none { it.action == action }) out.add(Item(action, !hidden))
        }
        for (action in Action.values()) if (out.none { it.action == action }) out.add(Item(action, true))
        return out
    }

    /** The text that [parse] reads back as [items]. */
    fun write(items: List<Item>): String = items.joinToString(",") { (if (it.shown) "" else "-") + it.action.id }

    /** True for text that [parse] reads without dropping anything: known ids, each once, nothing too long. */
    fun isValid(text: String): Boolean {
        if (text.isEmpty() || text.length > MAX_TEXT) return false
        val ids = text.split(',').map { it.removePrefix("-") }
        return ids.all { id -> Action.values().any { it.id == id } } && ids.toSet().size == ids.size
    }

    /** [items] with the one at [index] moved by [by] places (-1 up, 1 down); unchanged at either end. */
    fun moved(items: List<Item>, index: Int, by: Int): List<Item> {
        val to = index + by
        if (index !in items.indices || to !in items.indices) return items
        val out = items.toMutableList()
        out.add(to, out.removeAt(index))
        return out
    }

    /**
     * The actions to show for a selection of [count] things, of which [folders] are folders, in the order [items]
     * gives. For one thing, every shown action that fits a file or a folder; for more, only those that work on many
     * (Share only when no folder is in it).
     */
    fun actionsFor(items: List<Item>, count: Int, folders: Int): List<Action> = items.filter { it.shown }.map { it.action }.filter {
        when {
            count <= 0 -> false
            count == 1 -> if (folders == 1) it.forFolders else it.forFiles
            else -> it.forMany && (it != Action.SHARE || folders == 0)
        }
    }
}
