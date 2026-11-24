package com.whatcodeeditor.settings

/**
 * The "Typing" page, opened from Settings > Code Editor. It has the switches for closing brackets and quotes as
 * you type, for smart indentation, and for hiding the rows of extra keys while a physical keyboard is
 * connected, and lists the keyboard shortcuts. Each choice is saved at once; the editor screen reads them when
 * it is shown again.
 */
class TypingActivity : SettingsPage("Typing") {

    private val settings by lazy { EditorSettings(this) }

    override fun render() {
        content.removeAllViews()

        content.addView(toggle("Close brackets and quotes", settings.autoClose) { settings.autoClose = it })
        content.addView(
            muted(
                "Typing ( [ { or a quote also types the closing one, typing the closing one steps over it, " +
                    "and typing a bracket over selected text wraps the selection. Backspace between an empty pair " +
                    "removes both. Quotes are paired only in languages that use them."
            )
        )

        content.addView(toggle("Smart indentation", settings.smartIndent) { settings.smartIndent = it })
        content.addView(
            muted(
                "Enter after an opening bracket (or a colon in Python and YAML) indents one more level, Enter " +
                    "between a bracket and its partner opens an empty indented line, and a closing bracket typed at " +
                    "the start of a line moves back one level. With this off, a new line only copies the indentation above."
            )
        )

        content.addView(sectionTitle("HARDWARE KEYBOARD"))
        content.addView(
            toggle("Hide the extra keys", settings.hideKeysWithKeyboard) {
                settings.hideKeysWithKeyboard = it
            }
        )
        content.addView(muted("Hides the two rows of keys above the keyboard while a physical keyboard is connected, and shows them again when it is removed."))
        content.addView(sectionTitle("SHORTCUTS"))
        content.addView(
            muted(
                "Ctrl+S  save\n" +
                    "Ctrl+F  find and replace\n" +
                    "Ctrl+/  toggle comment\n" +
                    "Ctrl+Z  undo   Ctrl+Y or Ctrl+Shift+Z  redo\n" +
                    "Ctrl+D  copy line down   Ctrl+K  delete line\n" +
                    "Ctrl+G  go to line\n" +
                    "Ctrl+W  close tab   Ctrl+Tab  next tab (add Shift for the previous one)"
            )
        )

        defaultsSection(
            "Turn on closing brackets and smart indentation, and hide the extra keys with a keyboard?",
            "Typing returned to default"
        ) { settings.resetTyping() }
    }
}
