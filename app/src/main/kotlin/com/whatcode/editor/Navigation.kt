package com.whatcode.editor

/** What one open file remembers for moving about in it: its bookmarks and the places it jumped from. */
class Navigation(val bookmarks: Bookmarks = Bookmarks(), val jumps: JumpHistory = JumpHistory()) {

    fun copy() = Navigation(bookmarks.copy(), jumps.copy())

    /** [removed] characters at [start] were replaced by [added] characters. */
    fun edited(start: Int, removed: Int, added: Int) {
        bookmarks.edited(start, removed, added)
        jumps.edited(start, removed, added)
    }

    fun clear() {
        bookmarks.clear()
        jumps.clear()
    }
}
