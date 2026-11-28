package com.whatcode.settings

import android.view.ViewGroup
import android.widget.LinearLayout
import com.whatcode.editor.IndentDefault
import com.whatcode.ui.SettingsPage

/**
 * The "Typing" page, opened from Settings > Code Editor. It has the switches for closing brackets and quotes as
 * you type and for smart indentation, the choice of indentation for files that show no pattern of their own, the
 * switches for tags in HTML, XML and SVG (closing, splitting on Enter, renaming a pair, quoting attribute values), for highlighting matching
 * brackets and tags, and for the clipboard, the switch for wrapping long lines, the switch for trimming whitespace on save, the switches for the hardware
 * keyboard (hiding the rows of extra keys, and its shortcuts), and it lists the keyboard shortcuts. Each choice is
 * saved at once; the editor screen reads them when it is shown again.
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

        content.addView(sectionTitle("HTML, XML AND SVG"))
        content.addView(toggle("Close tags", settings.closeTags) { settings.closeTags = it })
        content.addView(
            muted(
                "Typing the > that ends an opening tag also types its closing tag and leaves the cursor between " +
                    "them. Nothing is added if the tag already has one, for self-closing tags, for tags such as <br> " +
                    "that have no closing tag, or inside a script, a style or a quoted attribute value."
            )
        )
        content.addView(toggle("Split a tag pair on Enter", settings.splitTags) { settings.splitTags = it })
        content.addView(
            muted(
                "Enter between an opening and a closing tag, as in <div>|</div>, puts the cursor on an empty " +
                    "indented line between them and the closing tag on the line below. It needs Smart indentation to be on."
            )
        )
        content.addView(toggle("Rename both tags", settings.renameTags) { settings.renameTags = it })
        content.addView(
            muted(
                "Changing the name in an opening tag changes it in the closing tag too, and the other way round. " +
                    "Undo takes both back in one step. The partner is not changed once a name has been deleted " +
                    "completely; select the name and type over it instead."
            )
        )

        content.addView(toggle("Quote attribute values", settings.quoteAttributes) { settings.quoteAttributes = it })
        content.addView(
            muted(
                "Typing = after an attribute name in an opening tag adds \"\" and puts the cursor between the quotes, " +
                    "and typing the closing quote steps over it. Nothing is added in text, comments, scripts, styles " +
                    "or inside a value, or if a quote already follows."
            )
        )

        content.addView(sectionTitle("MATCHING"))
        content.addView(toggle("Highlight matching brackets and tags", settings.matchHighlight) { settings.matchHighlight = it })
        content.addView(
            muted(
                "Marks the bracket next to the cursor and its partner, or the tag the cursor is in and its partner. " +
                    "Add the Jump to matching key to the key rows (Settings > Key Editor), or press Ctrl+M, to move " +
                    "the cursor to the partner. That works whether or not the highlight is on."
            )
        )

        content.addView(sectionTitle("CLIPBOARD"))
        content.addView(toggle("Copy and cut the whole line", settings.wholeLineClipboard) { settings.wholeLineClipboard = it })
        content.addView(muted("Copy and cut with nothing selected take the line the cursor is on, with its line break."))
        content.addView(toggle("Paste as plain text", settings.plainPaste) { settings.plainPaste = it })
        content.addView(muted("Pasted text loses the fonts and colors of the place it was copied from, which would fight the syntax colors."))

        content.addView(sectionTitle("INDENTATION"))
        content.addView(
            muted(
                "What one level of indentation is in a file that shows no pattern of its own, such as a new or " +
                    "unindented file. A file that is already indented with tabs, or with two to four spaces, keeps " +
                    "its own style. Used by Tab, Shift+Tab, Enter, Backspace and the indent keys."
            )
        )
        for (option in IndentDefault.values()) {
            content.addView(
                choice(option.label, option == settings.indentDefault) { settings.indentDefault = option },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { setMargins(0, dp(4), 0, 0) }
            )
        }
        content.addView(
            muted(
                "Automatic uses four spaces, and a tab in Go and Makefiles. Pick Tab or a number of spaces to use " +
                    "it everywhere instead."
            )
        )

        content.addView(sectionTitle("LONG LINES"))
        content.addView(toggle("Word wrap", settings.wordWrap) { settings.wordWrap = it })
        content.addView(
            muted(
                "On, a line that is longer than the screen continues on the next row. Off, it stays on one row and " +
                    "you scroll sideways to read the rest. The line and column in the status bar, the cursor keys " +
                    "and find work the same either way."
            )
        )

        content.addView(sectionTitle("SAVING"))
        content.addView(toggle("Trim trailing whitespace on save", settings.trimOnSave) { settings.trimOnSave = it })
        content.addView(
            muted(
                "Saving by hand removes the spaces and tabs at the ends of lines first. Line endings and the cursor " +
                    "stay as they are. Autosave never trims, and Markdown files are left alone because two spaces " +
                    "there break a line."
            )
        )

        content.addView(sectionTitle("EDITOR SCREEN"))
        content.addView(toggle("Show the extra keys", settings.showKeyRows) { settings.showKeyRows = it })
        content.addView(muted("The two rows of keys above the keyboard. Off, they are hidden whatever keyboard is used; choose which keys they hold in Settings > Key Editor."))
        content.addView(toggle("Show the status bar", settings.showStatusBar) { settings.showStatusBar = it })
        content.addView(muted("The bar at the bottom with the line and column, the number of characters, the language and the line ending."))

        content.addView(sectionTitle("HARDWARE KEYBOARD"))
        content.addView(
            toggle("Hide the extra keys", settings.hideKeysWithKeyboard) {
                settings.hideKeysWithKeyboard = it
            }
        )
        content.addView(muted("Hides the two rows of keys above the keyboard while a physical keyboard is connected, and shows them again when it is removed."))
        content.addView(toggle("Keyboard shortcuts", settings.hardwareShortcuts) { settings.hardwareShortcuts = it })
        content.addView(
            muted(
                "The Ctrl shortcuts listed below work on a physical keyboard. With this off, Ctrl and a letter " +
                    "are left to Android. The on-screen CTRL key always works."
            )
        )
        content.addView(sectionTitle("SHORTCUTS"))
        content.addView(
            muted(
                "Ctrl+S  save\n" +
                    "Ctrl+F  find and replace\n" +
                    "Ctrl+/  toggle comment\n" +
                    "Ctrl+Z  undo   Ctrl+Y or Ctrl+Shift+Z  redo\n" +
                    "Ctrl+D  copy line down   Ctrl+K  delete line\n" +
                    "Ctrl+G  go to line   Ctrl+O  go to symbol\n" +
                    "Ctrl+Shift+G  indent guides   Ctrl+Q  column ruler\n" +
                    "Ctrl+B  bookmark the line   Ctrl+N  next bookmark (add Shift for the previous one)\n" +
                    "Ctrl+-  back to where you jumped from (add Shift to go forward)\n" +
                    "Ctrl+M  jump to the matching tag or bracket\n" +
                    "Ctrl+E  expand the selection (add Shift to shrink it)\n" +
                    "Ctrl+T  wrap in a tag (add Shift to remove the tag around the cursor)\n" +
                    "Ctrl+P  text tools   Ctrl+I  swap characters   Ctrl+.  snippets\n" +
                    "Ctrl+Space  suggest words\n" +
                    "Ctrl+Shift+D  add a cursor at the next place with the same text (Ctrl+Shift+L  at every place)\n" +
                    "Ctrl+U  upper case (add Shift for lower case)\n" +
                    "Ctrl+R  sort lines (add Shift for Z to A)\n" +
                    "Ctrl+W  close tab   Ctrl+Tab  next tab (add Shift for the previous one)"
            )
        )

        defaultsSection(
            "Turn on closing brackets and tags, smart indentation, tag renaming, attribute quotes, matching, the clipboard options, word wrap, " +
                "the extra keys, the status bar and keyboard shortcuts, indent with Automatic, hide the extra keys with a keyboard, and stop trimming whitespace on save?",
            "Typing returned to default"
        ) { settings.resetTyping() }
    }
}
