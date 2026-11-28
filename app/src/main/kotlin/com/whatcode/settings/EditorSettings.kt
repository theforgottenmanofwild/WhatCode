package com.whatcode.settings

import android.content.Context
import com.whatcode.editor.FoldBy
import com.whatcode.editor.IndentDefault
import com.whatcode.editor.TextConvert
import com.whatcode.editor.TextTools

/**
 * How typing, the on-screen keys and open files behave, saved on the device. Changed on the Settings > Code
 * Editor pages Typing ([TypingActivity]), Tabs ([TabsActivity]) and Key Editor ([KeyEditorActivity]), and read by
 * the editor ([CodeEditText]), the key rows ([KeyBar]) and the editor screen ([MainActivity]).
 */
class EditorSettings(context: Context) {

    private val prefs = context.getSharedPreferences("editor", Context.MODE_PRIVATE)

    /** Typing ( { [ or a quote also types the closing one, and typing the closing one steps over it. */
    var autoClose: Boolean
        get() = prefs.getBoolean("auto_close", DEFAULT_AUTO_CLOSE)
        set(v) = prefs.edit().putBoolean("auto_close", v).apply()

    /** Enter after an opening bracket (or a colon in Python and YAML) indents one more level. */
    var smartIndent: Boolean
        get() = prefs.getBoolean("smart_indent", DEFAULT_SMART_INDENT)
        set(v) = prefs.edit().putBoolean("smart_indent", v).apply()

    /**
     * What one level of indentation is in a file that shows no clear pattern of its own: a tab, or two, four or eight
     * spaces. A file that is already indented keeps its own style. [IndentDefault.AUTOMATIC] is four spaces, or a tab
     * in Go and Makefiles.
     */
    var indentDefault: IndentDefault
        get() = IndentDefault.fromKey(prefs.getString("indent_default", null))
        set(v) = prefs.edit().putString("indent_default", v.key).apply()

    /** In HTML, XML and SVG, typing the `>` that ends an opening tag also types its closing tag. */
    var closeTags: Boolean
        get() = prefs.getBoolean("close_tags", DEFAULT_CLOSE_TAGS)
        set(v) = prefs.edit().putBoolean("close_tags", v).apply()

    /** In HTML, XML and SVG, Enter between an opening and a closing tag opens an empty indented line between them. */
    var splitTags: Boolean
        get() = prefs.getBoolean("split_tags", DEFAULT_SPLIT_TAGS)
        set(v) = prefs.edit().putBoolean("split_tags", v).apply()

    /** In HTML, XML and SVG, changing the name in one tag of a pair changes it in the other tag too. */
    var renameTags: Boolean
        get() = prefs.getBoolean("rename_tags", DEFAULT_RENAME_TAGS)
        set(v) = prefs.edit().putBoolean("rename_tags", v).apply()

    /** In HTML, XML and SVG, typing `=` after an attribute name adds the quotes of its value. */
    var quoteAttributes: Boolean
        get() = prefs.getBoolean("quote_attributes", DEFAULT_QUOTE_ATTRIBUTES)
        set(v) = prefs.edit().putBoolean("quote_attributes", v).apply()

    /** The bracket next to the cursor, or the tag it is in, is highlighted together with its partner. */
    var matchHighlight: Boolean
        get() = prefs.getBoolean("match_highlight", DEFAULT_MATCH_HIGHLIGHT)
        set(v) = prefs.edit().putBoolean("match_highlight", v).apply()

    /** Copy and cut with nothing selected take the whole line. */
    var wholeLineClipboard: Boolean
        get() = prefs.getBoolean("whole_line_clipboard", DEFAULT_WHOLE_LINE_CLIPBOARD)
        set(v) = prefs.edit().putBoolean("whole_line_clipboard", v).apply()

    /** Paste always pastes plain text, without the fonts and colors of the place it was copied from. */
    var plainPaste: Boolean
        get() = prefs.getBoolean("plain_paste", DEFAULT_PLAIN_PASTE)
        set(v) = prefs.edit().putBoolean("plain_paste", v).apply()

    /** The Ctrl shortcuts of a hardware keyboard work. The on-screen CTRL key always works. */
    var hardwareShortcuts: Boolean
        get() = prefs.getBoolean("hardware_shortcuts", DEFAULT_HARDWARE_SHORTCUTS)
        set(v) = prefs.edit().putBoolean("hardware_shortcuts", v).apply()

    /** Long lines wrap at the right edge of the editor. When off, they run on and the text scrolls sideways. */
    var wordWrap: Boolean
        get() = prefs.getBoolean("word_wrap", DEFAULT_WORD_WRAP)
        set(v) = prefs.edit().putBoolean("word_wrap", v).apply()

    /** The two rows of extra keys are shown above the keyboard. When off, they are never shown. */
    var showKeyRows: Boolean
        get() = prefs.getBoolean("show_keys", DEFAULT_SHOW_KEY_ROWS)
        set(v) = prefs.edit().putBoolean("show_keys", v).apply()

    /** The bar under the editor shows the line and column, the character count, the language and the line ending. */
    var showStatusBar: Boolean
        get() = prefs.getBoolean("show_status_bar", DEFAULT_SHOW_STATUS_BAR)
        set(v) = prefs.edit().putBoolean("show_status_bar", v).apply()

    /** The two rows of extra keys are hidden while a physical keyboard is connected. */
    var hideKeysWithKeyboard: Boolean
        get() = prefs.getBoolean("hide_keys", DEFAULT_HIDE_KEYS)
        set(v) = prefs.edit().putBoolean("hide_keys", v).apply()

    /** The key rows change to the layout of the open file's language (see [KeyLayout.rowsFor]). */
    var keysByLanguage: Boolean
        get() = prefs.getBoolean("keys_by_language", DEFAULT_KEYS_BY_LANGUAGE)
        set(v) = prefs.edit().putBoolean("keys_by_language", v).apply()

    /** Opening a file adds a tab for it. When off, opening a file replaces the one in the editor. */
    var openInTabs: Boolean
        get() = prefs.getBoolean("open_in_tabs", DEFAULT_OPEN_IN_TABS)
        set(v) = prefs.edit().putBoolean("open_in_tabs", v).apply()

    /** The files that were open when the app was left are opened again on the next start. */
    var reopenLastFiles: Boolean
        get() = prefs.getBoolean("reopen_last", DEFAULT_REOPEN_LAST)
        set(v) = prefs.edit().putBoolean("reopen_last", v).apply()

    /** Saving by hand first removes the spaces and tabs at the ends of lines, except in Markdown. Autosave never does. */
    var trimOnSave: Boolean
        get() = prefs.getBoolean("trim_on_save", DEFAULT_TRIM_ON_SAVE)
        set(v) = prefs.edit().putBoolean("trim_on_save", v).apply()

    /** The status bar shows what contains the cursor: the class and function it is in, or the heading above it. */
    var showScope: Boolean
        get() = prefs.getBoolean("show_scope", DEFAULT_SHOW_SCOPE)
        set(v) = prefs.edit().putBoolean("show_scope", v).apply()

    /** Lines can carry a bookmark, and Next and Previous bookmark go between them. */
    var bookmarks: Boolean
        get() = prefs.getBoolean("bookmarks", DEFAULT_BOOKMARKS)
        set(v) = prefs.edit().putBoolean("bookmarks", v).apply()

    /** Go to line, Go to symbol, a bookmark and a search hit remember where the cursor was, for Back and Forward. */
    var jumpHistory: Boolean
        get() = prefs.getBoolean("jump_history", DEFAULT_JUMP_HISTORY)
        set(v) = prefs.edit().putBoolean("jump_history", v).apply()

    /** Blocks of code can be folded out of sight so only their first line shows. */
    var codeFolding: Boolean
        get() = prefs.getBoolean("code_folding", DEFAULT_CODE_FOLDING)
        set(v) = prefs.edit().putBoolean("code_folding", v).apply()

    /** Whether foldable blocks are found from brackets, indentation, or both. */
    var foldBy: FoldBy
        get() = FoldBy.fromKey(prefs.getString("fold_by", null))
        set(v) = prefs.edit().putString("fold_by", v.key).apply()

    /** How many lines a block must hide before it can be folded. */
    var foldMinLines: Int
        get() = prefs.getInt("fold_min_lines", DEFAULT_FOLD_MIN_LINES).coerceIn(MIN_FOLD_MIN_LINES, MAX_FOLD_MIN_LINES)
        set(v) = prefs.edit().putInt("fold_min_lines", v.coerceIn(MIN_FOLD_MIN_LINES, MAX_FOLD_MIN_LINES)).apply()

    /** Show ▾ and ▸ fold markers next to the line numbers on foldable lines. */
    var foldMarkers: Boolean
        get() = prefs.getBoolean("fold_markers", DEFAULT_FOLD_MARKERS)
        set(v) = prefs.edit().putBoolean("fold_markers", v).apply()

    /** The text tools (sorting lines, changing case, converting indentation and line endings) can be used. */
    var textTools: Boolean
        get() = prefs.getBoolean("text_tools", DEFAULT_TEXT_TOOLS)
        set(v) = prefs.edit().putBoolean("text_tools", v).apply()

    /** Sorting lines reads capitals and small letters as the same. */
    var sortIgnoreCase: Boolean
        get() = prefs.getBoolean("sort_ignore_case", DEFAULT_SORT_IGNORE_CASE)
        set(v) = prefs.edit().putBoolean("sort_ignore_case", v).apply()

    /** Sorting lines compares runs of digits as numbers, so 2 comes before 10. */
    var sortNatural: Boolean
        get() = prefs.getBoolean("sort_natural", DEFAULT_SORT_NATURAL)
        set(v) = prefs.edit().putBoolean("sort_natural", v).apply()

    /** Removing duplicate lines treats lines that differ only in capitals as the same. */
    var uniqueIgnoreCase: Boolean
        get() = prefs.getBoolean("unique_ignore_case", DEFAULT_UNIQUE_IGNORE_CASE)
        set(v) = prefs.edit().putBoolean("unique_ignore_case", v).apply()

    /** How many columns a tab is when the indentation is converted between tabs and spaces. */
    var tabWidth: Int
        get() = prefs.getInt("tab_width", DEFAULT_TAB_WIDTH).coerceIn(MIN_TAB_WIDTH, MAX_TAB_WIDTH)
        set(v) = prefs.edit().putInt("tab_width", v.coerceIn(MIN_TAB_WIDTH, MAX_TAB_WIDTH)).apply()

    /** How many spaces one level of formatted JSON is, or 0 for a tab. */
    var jsonIndent: Int
        get() = prefs.getInt("json_indent", TextTools.DEFAULT_JSON_INDENT).coerceIn(0, TextTools.MAX_JSON_INDENT)
        set(v) = prefs.edit().putInt("json_indent", v.coerceIn(0, TextTools.MAX_JSON_INDENT)).apply()

    /** Snippets can be put in: Tab after a prefix, and the Snip key. */
    var snippets: Boolean
        get() = prefs.getBoolean("snippets", DEFAULT_SNIPPETS)
        set(v) = prefs.edit().putBoolean("snippets", v).apply()

    /** Tab after the prefix of a snippet puts the snippet in. When off, only the Snip key puts one in. */
    var snippetsTab: Boolean
        get() = prefs.getBoolean("snippets_tab", DEFAULT_SNIPPETS_TAB)
        set(v) = prefs.edit().putBoolean("snippets_tab", v).apply()

    /** The snippets that come with the editor can be used, as well as the ones that were written. */
    var snippetsBuiltIn: Boolean
        get() = prefs.getBoolean("snippets_builtin", DEFAULT_SNIPPETS_BUILTIN)
        set(v) = prefs.edit().putBoolean("snippets_builtin", v).apply()

    /** Tab after an HTML or CSS abbreviation, such as ul>li*3 or m10, expands it. */
    var abbreviations: Boolean
        get() = prefs.getBoolean("abbreviations", DEFAULT_ABBREVIATIONS)
        set(v) = prefs.edit().putBoolean("abbreviations", v).apply()

    /** Puts the choices of the Snippets page back to their defaults. */
    fun resetSnippets() {
        prefs.edit().remove("snippets").remove("snippets_tab").remove("snippets_builtin").remove("abbreviations").apply()
    }

    /** Words from the open files are suggested under the cursor while you type. */
    var completion: Boolean
        get() = prefs.getBoolean("completion", DEFAULT_COMPLETION)
        set(v) = prefs.edit().putBoolean("completion", v).apply()

    /** How many letters of a word must be typed before words are suggested. */
    var completionMinLetters: Int
        get() = prefs.getInt("completion_min", DEFAULT_COMPLETION_MIN).coerceIn(MIN_COMPLETION_MIN, MAX_COMPLETION_MIN)
        set(v) = prefs.edit().putInt("completion_min", v.coerceIn(MIN_COMPLETION_MIN, MAX_COMPLETION_MIN)).apply()

    /** How many milliseconds to wait after the last typed letter before the suggestions are looked for. */
    var completionDelay: Int
        get() = prefs.getInt("completion_delay", DEFAULT_COMPLETION_DELAY).coerceIn(0, MAX_COMPLETION_DELAY)
        set(v) = prefs.edit().putInt("completion_delay", v.coerceIn(0, MAX_COMPLETION_DELAY)).apply()

    /** Words of the other open tabs are suggested as well as the words of the file being edited. */
    var completionOtherFiles: Boolean
        get() = prefs.getBoolean("completion_other", DEFAULT_COMPLETION_OTHER)
        set(v) = prefs.edit().putBoolean("completion_other", v).apply()

    /** Enter takes the chosen suggestion, as Tab does. When off, Enter always starts a new line. */
    var completionEnter: Boolean
        get() = prefs.getBoolean("completion_enter", DEFAULT_COMPLETION_ENTER)
        set(v) = prefs.edit().putBoolean("completion_enter", v).apply()

    /** Puts the choices of the Completion page back to their defaults. */
    fun resetCompletion() {
        prefs.edit().remove("completion").remove("completion_min").remove("completion_delay").remove("completion_other")
            .remove("completion_enter").apply()
    }

    /** Several cursors can be used: add the next place, add every place, and type in all of them. */
    var multiCursor: Boolean
        get() = prefs.getBoolean("multi_cursor", DEFAULT_MULTI_CURSOR)
        set(v) = prefs.edit().putBoolean("multi_cursor", v).apply()

    /** The most cursors at once, the first one included. */
    var multiCursorMax: Int
        get() = prefs.getInt("multi_cursor_max", DEFAULT_MULTI_CURSOR_MAX).coerceIn(MIN_MULTI_CURSOR_MAX, MAX_MULTI_CURSOR_MAX)
        set(v) = prefs.edit().putInt("multi_cursor_max", v.coerceIn(MIN_MULTI_CURSOR_MAX, MAX_MULTI_CURSOR_MAX)).apply()

    /** Looking for the next or every place with the same text tells capitals from small letters. */
    var multiCursorMatchCase: Boolean
        get() = prefs.getBoolean("multi_cursor_case", DEFAULT_MULTI_CURSOR_MATCH_CASE)
        set(v) = prefs.edit().putBoolean("multi_cursor_case", v).apply()

    /** Looking for the next or every place with the same text skips a word that is part of a longer one. */
    var multiCursorWholeWord: Boolean
        get() = prefs.getBoolean("multi_cursor_word", DEFAULT_MULTI_CURSOR_WHOLE_WORD)
        set(v) = prefs.edit().putBoolean("multi_cursor_word", v).apply()

    /** Pasting text with as many lines as there are cursors gives one line to each cursor. */
    var multiCursorPasteLines: Boolean
        get() = prefs.getBoolean("multi_cursor_lines", DEFAULT_MULTI_CURSOR_PASTE_LINES)
        set(v) = prefs.edit().putBoolean("multi_cursor_lines", v).apply()

    /** Puts the choices of the Multiple cursors page back to their defaults. */
    fun resetMultiCursor() {
        prefs.edit().remove("multi_cursor").remove("multi_cursor_max").remove("multi_cursor_case").remove("multi_cursor_word")
            .remove("multi_cursor_lines").apply()
    }

    fun textToolOptions() = TextTools.Options(sortIgnoreCase, sortNatural, uniqueIgnoreCase, tabWidth, jsonIndent)

    /** Puts the choices of the Text tools page back to their defaults. */
    fun resetTextTools() {
        prefs.edit().remove("text_tools").remove("sort_ignore_case").remove("sort_natural").remove("unique_ignore_case")
            .remove("tab_width").remove("json_indent").apply()
    }

    /** Puts the choices of the Typing page back to their defaults. */
    fun resetTyping() {
        prefs.edit().remove("auto_close").remove("smart_indent").remove("hide_keys").remove("word_wrap").remove("indent_default")
            .remove("close_tags").remove("split_tags").remove("rename_tags").remove("match_highlight")
            .remove("whole_line_clipboard").remove("plain_paste").remove("hardware_shortcuts")
            .remove("show_keys").remove("show_status_bar").remove("quote_attributes").remove("trim_on_save").apply()
    }

    /** Puts the choices of the Navigation page back to their defaults. */
    fun resetNavigation() {
        prefs.edit()
            .remove("show_scope").remove("bookmarks").remove("jump_history")
            .remove("code_folding").remove("fold_by").remove("fold_min_lines").remove("fold_markers")
            .apply()
    }

    /** Puts the choices of the Tabs page back to its default. */
    fun resetTabs() {
        prefs.edit().remove("open_in_tabs").remove("reopen_last").apply()
    }

    companion object {
        const val DEFAULT_AUTO_CLOSE = true
        const val DEFAULT_SMART_INDENT = true
        const val DEFAULT_CLOSE_TAGS = true
        const val DEFAULT_SPLIT_TAGS = true
        const val DEFAULT_RENAME_TAGS = true
        const val DEFAULT_QUOTE_ATTRIBUTES = true
        const val DEFAULT_TRIM_ON_SAVE = false
        const val DEFAULT_MATCH_HIGHLIGHT = true
        const val DEFAULT_WHOLE_LINE_CLIPBOARD = true
        const val DEFAULT_PLAIN_PASTE = true
        const val DEFAULT_HARDWARE_SHORTCUTS = true
        const val DEFAULT_SHOW_KEY_ROWS = true
        const val DEFAULT_SHOW_STATUS_BAR = true
        const val DEFAULT_SHOW_SCOPE = true
        const val DEFAULT_BOOKMARKS = true
        const val DEFAULT_JUMP_HISTORY = true
        const val DEFAULT_CODE_FOLDING = true
        val DEFAULT_FOLD_BY = FoldBy.BOTH
        const val DEFAULT_FOLD_MIN_LINES = 1
        const val MIN_FOLD_MIN_LINES = 1
        const val MAX_FOLD_MIN_LINES = 20
        const val DEFAULT_FOLD_MARKERS = true
        const val DEFAULT_TEXT_TOOLS = true
        const val DEFAULT_SNIPPETS = true
        const val DEFAULT_SNIPPETS_TAB = true
        const val DEFAULT_SNIPPETS_BUILTIN = true
        const val DEFAULT_ABBREVIATIONS = true
        const val DEFAULT_MULTI_CURSOR = true
        const val DEFAULT_MULTI_CURSOR_MAX = 200
        const val MIN_MULTI_CURSOR_MAX = 2
        const val MAX_MULTI_CURSOR_MAX = 1000
        const val DEFAULT_MULTI_CURSOR_MATCH_CASE = true
        const val DEFAULT_MULTI_CURSOR_WHOLE_WORD = false
        const val DEFAULT_MULTI_CURSOR_PASTE_LINES = true
        const val DEFAULT_COMPLETION = true
        const val DEFAULT_COMPLETION_OTHER = true
        const val DEFAULT_COMPLETION_ENTER = false
        const val DEFAULT_COMPLETION_MIN = 3
        const val MIN_COMPLETION_MIN = 1
        const val MAX_COMPLETION_MIN = 8
        const val DEFAULT_COMPLETION_DELAY = 250
        const val MAX_COMPLETION_DELAY = 1500
        const val DEFAULT_SORT_IGNORE_CASE = true
        const val DEFAULT_SORT_NATURAL = true
        const val DEFAULT_UNIQUE_IGNORE_CASE = false
        const val DEFAULT_TAB_WIDTH = TextConvert.DEFAULT_TAB
        const val MIN_TAB_WIDTH = 1
        const val MAX_TAB_WIDTH = 16

        /** Keeps how the editor always indented: four spaces, or a tab in Go and Makefiles. */
        val DEFAULT_INDENT = IndentDefault.AUTOMATIC

        /** On, because the editor has always wrapped long lines. */
        const val DEFAULT_WORD_WRAP = true
        const val DEFAULT_HIDE_KEYS = true
        const val DEFAULT_KEYS_BY_LANGUAGE = true
        const val DEFAULT_OPEN_IN_TABS = true
        const val DEFAULT_REOPEN_LAST = true
    }
}
