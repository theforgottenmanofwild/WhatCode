package com.whatcode.settings

import android.content.Context
import com.whatcode.editor.IndentDefault

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

    /** Puts the choices of the Typing page back to their defaults. */
    fun resetTyping() {
        prefs.edit().remove("auto_close").remove("smart_indent").remove("hide_keys").remove("word_wrap").remove("indent_default")
            .remove("close_tags").remove("split_tags").remove("rename_tags").remove("match_highlight")
            .remove("whole_line_clipboard").remove("plain_paste").remove("hardware_shortcuts")
            .remove("show_keys").remove("show_status_bar").remove("quote_attributes").remove("trim_on_save").apply()
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
