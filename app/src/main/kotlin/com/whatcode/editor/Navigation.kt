package com.whatcode.editor

/** What one open file remembers for moving about in it: its bookmarks, the places it jumped from and its folds. */
class Navigation(
    val bookmarks: Bookmarks = Bookmarks(),
    val jumps: JumpHistory = JumpHistory(),
    val folds: Folds = Folds(),
) {

    fun copy() = Navigation(bookmarks.copy(), jumps.copy(), folds.copy())

    /** [removed] characters at [start] were replaced by [added] characters. */
    fun edited(start: Int, removed: Int, added: Int) {
        bookmarks.edited(start, removed, added)
        jumps.edited(start, removed, added)
        folds.edited(start, removed, added)
    }

    fun clear() {
        bookmarks.clear()
        jumps.clear()
        folds.clear()
    }
}
