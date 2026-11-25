package com.whatcode.editor

/**
 * What one level of indentation is in a file that shows no clear pattern (see [TypingLogic.indentUnit]), chosen in
 * Settings > Typing. A file that is already indented with tabs or with two to four spaces keeps its own style
 * whatever is chosen here. [AUTOMATIC] is how the editor always behaved: four spaces, or a tab in Go and Makefiles.
 */
enum class IndentDefault(val key: String, val label: String, val unit: String?) {
    AUTOMATIC("auto", "Automatic", null),
    TAB("tab", "Tab", "\t"),
    SPACES_2("2", "2 spaces", "  "),
    SPACES_4("4", "4 spaces", "    "),
    SPACES_8("8", "8 spaces", "        ");

    companion object {
        /** The choice saved under [key]; anything unknown, or nothing saved, is [AUTOMATIC]. */
        fun fromKey(key: String?): IndentDefault = values().firstOrNull { it.key == key } ?: AUTOMATIC
    }
}
