# Using the editor

How the editor screen is laid out, what the menu holds, and the small helpers for writing code.

## The screen

From top to bottom:

| Area | What it does |
| --- | --- |
| Top bar | The sidebar button on the left, then the current file name (a dot before it means unsaved changes), and the **⋮** menu on the right. See [Files and folders](../02-editing/03-files-and-folders.md#the-sidebar). |
| Editor | The text you are editing. Long lines wrap. |
| Find and replace bar | Appears above the key rows when you open find. See [Find and replace](../02-editing/02-find-and-replace.md). |
| Key rows | Two rows of extra keys: modifiers, arrows, undo, redo, save, line tools and symbols. Each row scrolls sideways on its own. See [The key rows](../02-editing/01-key-rows.md). |
| Status bar | Line and column, character count and language, such as JavaScript or YAML |

## The menu

Tap **⋮** at the top right to open the menu: **New**, **Open**, **Open folder**, **Files**, **Find file**, **Save**, **Save as**, **Close file**, **Edit keys** and **Settings**.

- The file entries are covered in [Files and folders](../02-editing/03-files-and-folders.md).
- **Edit keys** is a quick version of **Settings > Code Editor > Key Editor**, and both are covered in [The key rows](../02-editing/01-key-rows.md#changing-the-keys).
- The **⋮** turns blue, and the entry reads **Settings ●**, when an update is waiting for you. See [Updating the app](../03-the-app/02-updating.md).

## Writing code

**Auto-indent.** Pressing Enter starts the new line with the same leading spaces or tabs as the line above. With smart indentation on (**Settings > Typing > Smart indentation**), Enter after an opening bracket indents one more level, and Enter between a bracket and its closing partner, or between an opening and a closing HTML tag, opens an empty indented line between them. Backspace in the spaces that start a line removes back to the previous level.

**Bracket matching.** With the cursor next to a bracket, that bracket and its partner are highlighted, so you can see where a block starts and ends. The bracket just before the cursor is used first, then the one just after it. Nothing is highlighted while text is selected.

- `( )`, `[ ]` and `{ }` are paired in every language.
- `< >` are paired only where they mean something: as the two ends of a tag in HTML, XML and SVG, and as the brackets of generics such as `List<String>` in TypeScript, Kotlin, Java, C#, C++, Rust, Swift, Dart and Scala. Comparisons and arrows such as `a < b`, `a >= b` and `->` are not brackets.
- A bracket inside text that is colored as a string, a comment or a regular expression is never highlighted and is not counted while looking for a partner. In `f("(", x)` the first `(` is paired with the last `)`.
- The partner is looked for up to 10,000 characters away, so a huge file or one very long line stays quick. A bracket whose partner is farther away is not highlighted. The same goes for a bracket that has no partner.

**Symbols and Tab.** Tap a symbol key in the key rows to insert that character at the cursor. If text is selected, it is replaced. The **Tab** key inserts one level of indentation (a tab, or the number of spaces the file already uses), or indents the selected lines. You can change which keys there are with **Edit keys**.

**Status bar.** Ln and Col show the cursor position, starting at 1. The character count covers the whole file. The language shown is the one picked from the file name, for example **JavaScript** for `main.js` (see [Languages and highlighting](../02-editing/04-languages.md)).

## Not included yet

- Several files open at once
- Line numbers

The theme, accent color, font, font size and spacing are changed in **Settings > Appearance** (see [Appearance](../03-the-app/03-appearance.md)).
