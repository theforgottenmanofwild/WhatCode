package com.whatcodeeditor.editor

import com.whatcodeeditor.ui.Contrast

/**
 * The kinds of piece the highlighter colours, as the person sees them in Settings > Appearance > Syntax colors.
 * [darkDefault] is also the marker colour that [Highlighter] puts on a piece while it is scanning; a
 * [SyntaxScheme] turns that marker into the colour that is really shown.
 */
enum class SyntaxRole(
    val key: String,
    val label: String,
    val sample: String,
    val darkDefault: Int,
    val lightDefault: Int,
) {
    KEYWORD("keyword", "Keyword", "fun", 0xFF569CD6.toInt(), 0xFF0451A5.toInt()),
    CONTROL("control", "Control flow", "return", 0xFFC586C0.toInt(), 0xFFAF00DB.toInt()),
    TYPE("type", "Type", "String", 0xFF4EC9B0.toInt(), 0xFF267F99.toInt()),
    FUNCTION("function", "Function", "println", 0xFFDCDCAA.toInt(), 0xFF795E26.toInt()),
    CONSTANT("constant", "Constant", "MAX_SIZE", 0xFF4FC1FF.toInt(), 0xFF0070C1.toInt()),
    PROPERTY("property", "Property or key", "name", 0xFF9CDCFE.toInt(), 0xFF001080.toInt()),
    STRING("string", "String", "\"Hello\"", 0xFFCE9178.toInt(), 0xFFA31515.toInt()),
    NUMBER("number", "Number", "42", 0xFFB5CEA8.toInt(), 0xFF098658.toInt()),
    COMMENT("comment", "Comment", "// a note", 0xFF6A9955.toInt(), 0xFF008000.toInt()),
    REGEX("regex", "Regular expression", "/ab+c/g", 0xFFD16969.toInt(), 0xFF811F3F.toInt()),
    SELECTOR("selector", "CSS selector", ".button", 0xFFD7BA7D.toInt(), 0xFF795E26.toInt());

    fun defaultColor(light: Boolean): Int = if (light) lightDefault else darkDefault

    companion object {
        fun from(key: String): SyntaxRole? = values().firstOrNull { it.key == key }
    }
}

/**
 * The colour of every [SyntaxRole] for one theme. Built from the defaults plus the colours the person chose; a
 * chosen colour that would be hard to read on [editorBackground] is moved to the nearest one that is not (see
 * [Contrast.MINIMUM]), for example after the editor background changed to true black.
 */
class SyntaxScheme private constructor(private val colors: Map<SyntaxRole, Int>) {

    private val bySource: Map<Int, Int> = colors.entries.associate { it.key.darkDefault to it.value }

    fun color(role: SyntaxRole): Int = colors.getValue(role)

    /** The colour to show for a piece the highlighter marked with [source]. Anything else is left as it is. */
    fun resolve(source: Int): Int = bySource[source] ?: source

    companion object {
        fun of(light: Boolean, editorBackground: Int, overrides: Map<SyntaxRole, Int> = emptyMap()): SyntaxScheme =
            SyntaxScheme(
                SyntaxRole.values().associateWith { role ->
                    val chosen = overrides[role]
                    if (chosen == null) role.defaultColor(light) else Contrast.ensure(chosen, editorBackground, Contrast.MINIMUM)
                }
            )
    }
}
