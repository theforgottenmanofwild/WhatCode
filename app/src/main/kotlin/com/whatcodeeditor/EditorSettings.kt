package com.whatcodeeditor

import android.content.Context

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

    /** Puts the three choices of the Typing page back to their defaults. */
    fun resetTyping() {
        prefs.edit().remove("auto_close").remove("smart_indent").remove("hide_keys").apply()
    }

    /** Puts the choice of the Tabs page back to its default. */
    fun resetTabs() {
        prefs.edit().remove("open_in_tabs").apply()
    }

    companion object {
        const val DEFAULT_AUTO_CLOSE = true
        const val DEFAULT_SMART_INDENT = true
        const val DEFAULT_HIDE_KEYS = true
        const val DEFAULT_KEYS_BY_LANGUAGE = true
        const val DEFAULT_OPEN_IN_TABS = true
    }
}
