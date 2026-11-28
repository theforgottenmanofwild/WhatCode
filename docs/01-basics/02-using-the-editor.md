# Using the editor

How the editor screen is laid out, what the menu holds, and the small helpers for writing code.

## The screen

From top to bottom:

| Area | What it does |
| --- | --- |
| Top bar | The sidebar button on the left, then the current file name (a dot before it means unsaved changes), and the **⋮** menu on the right. See [Files and folders](../02-editing/03-files-and-folders.md#the-sidebar). The editor is reached from the [file explorer](./03-file-explorer.md), and Back goes back to it. |
| Tab bar | Appears under the top bar once two or more files are open, with one tab for each. See [Tabs](../02-editing/06-tabs.md). |
| Editor | The text you are editing. Long lines wrap unless you turn word wrap off (see [Word wrap](#writing-code)). Line numbers can be shown on its left (see [Appearance](../03-the-app/03-appearance.md#editor-text)). |
| Find and replace bar | Appears above the key rows when you open find. See [Find and replace](../02-editing/02-find-and-replace.md). |
| Key rows | Two rows of extra keys: modifiers, arrows, undo, redo, save, line tools and symbols. Each row scrolls sideways on its own. See [The key rows](../02-editing/01-key-rows.md). |
| Status bar | Line and column, character count, language (such as JavaScript or YAML) and line ending (LF, CRLF, CR or Mixed) |

## The menu

Tap **⋮** at the top right to open the menu: **New**, **Open**, **Open folder**, **Files**, **Find file**, **Save**, **Save as**, **Close file**, **Edit keys** and **Settings**.

- The file entries are covered in [Files and folders](../02-editing/03-files-and-folders.md).
- **Edit keys** is a quick version of **Settings > Code Editor > Key Editor**, and both are covered in [The key rows](../02-editing/01-key-rows.md#changing-the-keys).
- The **⋮** turns blue, and the entry reads **Settings ●**, when an update is waiting for you. See [Updating the app](../03-the-app/02-updating.md).

## Writing code

**Auto-indent.** Pressing Enter starts the new line with the same leading spaces or tabs as the line above. With smart indentation on (**Settings > Typing > Smart indentation**), Enter after an opening bracket indents one more level, and Enter between a bracket and its closing partner opens an empty indented line between them (and so does Enter between two tags, see [Closing tags](#writing-code) below). Backspace in the spaces that start a line removes back to the previous level.

**Closing brackets and quotes.** With **Settings > Typing > Close brackets and quotes** on (the default), typing `(`, `[`, `{` or a quote also types the closing one, and typing the closing one steps over it instead of adding another. Typing a bracket or quote over selected text wraps the selection, and Backspace between an empty pair removes both. Quotes are paired only in languages that use them, so not in plain text or Markdown.

**Closing tags.** In HTML, XML and SVG files, typing the `>` that ends an opening tag also types its closing tag and leaves the cursor between the two. Nothing is added if the tag already has one, for self-closing tags, for HTML tags that have no closing tag such as `<br>` and `<img>`, or inside a script, a style or a quoted attribute value. Turn it off with **Settings > Typing > Close tags**; it is on by default.

**Splitting a tag pair.** In the same files, pressing Enter between an opening and a closing tag, as in `<div>|</div>`, puts the cursor on an empty, indented line between them and the closing tag on the line below. It needs **Smart indentation** to be on, and has its own switch, **Settings > Typing > Split a tag pair on Enter**, on by default.

**Renaming a tag pair.** Changing the name in an opening tag changes it in the closing tag too, and the other way round, so `<div>` and `</div>` stay a pair as you edit one of them. Undo takes back both in one step. Nothing is changed in the partner once a name has been deleted completely, in a self-closing tag, or in a tag without a partner; to replace a name, select it and type over it. Turn it off with **Settings > Typing > Rename both tags**; it is on by default.

**Attribute quotes.** In the same files, typing `=` after an attribute name inside an opening tag adds `""` and leaves the cursor between the quotes, and typing the closing quote steps over it. Nothing is added in text, comments, scripts or styles, inside a value that is already there, or when a quote or a value already follows. It works for typing on a keyboard, like the other typing helpers; the symbol keys of the key rows insert only the character. Turn it off with **Settings > Typing > Quote attribute values**; it is on by default.

**Tag matching.** With the cursor in a tag in HTML, XML or SVG, that tag and its partner are highlighted. Hovering a mouse or stylus over a tag does the same. Bracket matching below follows the same switch, **Settings > Typing > Highlight matching brackets and tags**, on by default.

**Jump to matching.** The **Jump to matching** key moves the cursor to the partner of the tag it is in, or of the bracket next to it. In a tag it goes to the start of the other tag; next to a bracket it goes next to the partner on the same side. Press it again to go back, or light **SHFT** first to select from where you were to the partner. It also works as **Ctrl+M**. It is in the default keys of row 1; if you changed your keys earlier, add it from the list of built-in keys in the Key Editor. The jump works whether or not the highlight is on.

**Bracket matching.** With the cursor next to a bracket, that bracket and its partner are highlighted, so you can see where a block starts and ends. The bracket just before the cursor is used first, then the one just after it. Nothing is highlighted while text is selected.

- `( )`, `[ ]` and `{ }` are paired in every language.
- `< >` are paired only where they mean something: as the two ends of a tag in HTML, XML and SVG, and as the brackets of generics such as `List<String>` in TypeScript, Kotlin, Java, C#, C++, Rust, Swift, Dart and Scala. Comparisons and arrows such as `a < b`, `a >= b` and `->` are not brackets.
- A bracket inside text that is colored as a string, a comment or a regular expression is never highlighted and is not counted while looking for a partner. In `f("(", x)` the first `(` is paired with the last `)`.
- The partner is looked for up to 10,000 characters away, so a huge file or one very long line stays quick. A bracket whose partner is farther away is not highlighted. The same goes for a bracket that has no partner.

**Expand selection.** The **Expand selection** key grows the selection one step each time you press it. A step is the next bigger of these: the word, the line, the inside of the brackets around the selection, those brackets included, the inside of the tag around it, the whole element, and at last the whole file. Brackets and tags inside one line therefore come before the line itself. Light **SHFT** first to shrink back through exactly the steps you took. Moving the cursor, selecting by hand or editing the text starts afresh. Brackets in text that is colored as a string, a comment or a regular expression do not count, tags are used in HTML, XML and SVG, and brackets and tags are looked for up to 200,000 characters away. It also works as **Ctrl+E**, with **Shift** to shrink. It is in the default keys of row 1; if you changed your keys earlier, add it from the list of built-in keys in the Key Editor.

**Wrap in tag.** In HTML, XML and SVG, the **Wrap in tag** key asks for a tag name, with attributes if you want them, such as `a href="/"`; the closing tag uses the first word. The tag goes around the selection, or around the word at the cursor, and with neither an empty pair is added with the cursor inside. When the selection covers whole lines, the tags go on lines of their own and the lines are indented one level. A tag that can hold nothing, such as `<br>` in HTML, is refused. It also works as **Ctrl+T**.

**Remove surrounding tag.** Light **SHFT** and press **Wrap in tag** (or press **Ctrl+Shift+T**) to remove the innermost tag pair around the cursor and keep its content. If the tags and the content were on lines of their own, the tag lines are removed and the content moves back to the indentation of the tags. Wrapping and removing are each undone in one step. In other files both show a message instead. The key is in the default keys of row 1; if you changed your keys earlier, add it from the list of built-in keys in the Key Editor.

**Indentation.** One level of indentation is what the file already uses: a tab, or two to four spaces. When a file shows no such pattern, such as a new, empty or unindented file, the choice in **Settings > Typing > Indentation** is used: **Automatic**, **Tab**, or **2**, **4** or **8 spaces**. **Automatic** is the default and means four spaces, or a tab in Go and Makefiles. The choice is used by Tab and Shift+Tab, Enter after an opening bracket, a colon in Python and YAML or between two tags, the closing bracket stepping back a level, the indent keys (**]** and **[**), and Backspace in the spaces that start a line (with smart indentation on). A file that is already indented keeps its own style, whatever is chosen. **Return to default** puts it back to **Automatic**.

**Word wrap.** Long lines wrap at the right edge of the screen and continue on the next row. To keep every line on one row, turn off **Settings > Typing > Word wrap**; the text then scrolls sideways. The setting is on by default and applies to every file at once. The line and column in the status bar count lines of the file, not rows on the screen, so they are the same in both modes. Turning it on or off keeps the cursor in view. **Return to default** on the same page turns word wrap back on. A big file or a very long line is not wrapped, whatever this says (see [Large files](../03-the-app/06-large-files.md)).

**Clipboard.** Paste always pastes plain text, so text copied from a web page or a document does not bring its fonts and colors into the editor. Copy and cut with nothing selected take the whole line, with its line break. Each has a switch under **Settings > Typing > Clipboard**: **Paste as plain text** and **Copy and cut the whole line**. Both are on by default, and with them off the normal Android behavior is used.

**Trailing whitespace.** With **Settings > Typing > Trim trailing whitespace on save** on, saving by hand first removes the spaces and tabs at the ends of lines. That covers **Save**, the Save key, **Ctrl+S**, **Save as** and **Save** in the question asked when you close a changed tab. Autosave never trims. Every line keeps its ending (LF, CRLF or CR), the cursor stays where it was (or moves to the new end of its line, if it was in the removed spaces), and undo brings the spaces back in one step. Markdown files are skipped, because two spaces at the end of a line there mean a line break. The setting is off by default. To see spaces and tabs, turn on [Show whitespace](../03-the-app/03-appearance.md#editor-text).

**Symbols and Tab.** Tap a symbol key in the key rows to insert that character at the cursor. If text is selected, it is replaced. The **Tab** key inserts one level of indentation (a tab, or the number of spaces the file already uses), or indents the selected lines. You can change which keys there are with **Edit keys**.

**Line endings.** The last part of the status bar shows how the lines of the file end: **LF** (Linux, macOS and Android), **CRLF** (Windows) or **CR** (very old Mac files). **Mixed** means the file uses more than one of them. A file with no line break yet shows **LF**, which is what a new line gets. Saving never converts anything: the file is written back with the same line endings it was opened with, and a file that was **Mixed** stays exactly as mixed. There is no setting to change them. Lines you add by pressing Enter end with LF, so adding a line to a CRLF file makes it **Mixed**; the lines that were already there are left as they are, and pasted text keeps the line breaks it came with. In a very big file the ending appears a moment after the file opens.

**Status bar.** The bar at the bottom can be hidden with **Settings > Typing > Show the status bar**. It is on by default. Ln and Col show the cursor position, starting at 1. The character count covers the whole file. The language shown is the one picked from the file name, for example **JavaScript** for `main.js` (see [Languages and highlighting](../02-editing/04-languages.md)). After it comes the line ending (see above). In a big file the bar ends with **Large file mode**, and tapping the bar says what was turned off (see [Large files](../03-the-app/06-large-files.md)).

## Hardware keyboard

With a physical keyboard connected, the two rows of extra keys are hidden, because the keyboard has those keys itself. They come back when it is removed. To keep them showing, turn off **Settings > Typing > Hide the extra keys**. To never show them, turn off **Settings > Typing > Show the extra keys**; they come back when you turn it on again. The Ctrl shortcuts listed in [The key rows](../02-editing/01-key-rows.md#shortcuts) work on a hardware keyboard, and **Settings > Typing** lists them too. If you would rather leave Ctrl and a letter to Android, turn off **Settings > Typing > Keyboard shortcuts**; the on-screen **CTRL** key keeps working.

## Backing up your settings

**Settings > Import and export** saves your settings as a JSON file and reads them back. See [Import and export](../03-the-app/04-import-and-export.md).

## Appearance

The theme, accent color, font, font size, spacing, line numbers and whitespace marks are changed in **Settings > Appearance** (see [Appearance](../03-the-app/03-appearance.md)).
