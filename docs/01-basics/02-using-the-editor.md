# Using the editor

How the editor screen is laid out, what the menu holds, and the small helpers for writing code.

## The screen

From top to bottom:

| Area | What it does |
| --- | --- |
| Top bar | The current file name on the left (a dot before it means unsaved changes) and the **⋮** menu on the right |
| Editor | The text you are editing. Long lines wrap. |
| Find and replace bar | Appears above the key rows when you open find. See [Find and replace](../02-editing/02-find-and-replace.md). |
| Key rows | Two rows of extra keys: modifiers, arrows, undo, redo, save, line tools and symbols. Each row scrolls sideways on its own. See [The key rows](../02-editing/01-key-rows.md). |
| Status bar | Line and column, character count and detected language |

## The menu

Tap **⋮** at the top right to open the menu: **New**, **Open**, **Open folder**, **Files**, **Find file**, **Save**, **Save as**, **Close file**, **Edit keys** and **Update**.

- The file entries are covered in [Files and folders](../02-editing/03-files-and-folders.md).
- **Edit keys** is covered in [The key rows](../02-editing/01-key-rows.md#changing-the-keys).
- The **⋮** turns blue, and the entry reads **Update ●**, when an update is waiting for you. See [Updating the app](../03-the-app/02-updating.md).

## Writing code

**Auto-indent.** Pressing Enter starts the new line with the same leading spaces or tabs as the line above.

**Symbols and Tab.** Tap a symbol key in the key rows to insert that character at the cursor. If text is selected, it is replaced. The **Tab** key inserts four spaces, or indents the selected lines. You can change which keys there are with **Edit keys**.

**Status bar.** Ln and Col show the cursor position, starting at 1. The character count covers the whole file. The language shown is the one picked from the file name (see [Languages and highlighting](../02-editing/04-languages.md)).

## Not included yet

- Several files open at once
- Line numbers
- Changing the theme or font size

The editor uses a dark theme only.
