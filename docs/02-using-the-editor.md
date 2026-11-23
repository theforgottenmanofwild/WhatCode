# Using the editor

## The screen

From top to bottom:

| Area | What it does |
| --- | --- |
| Top bar | The current file name on the left (a dot before it means unsaved changes) and the **⋮** menu on the right |
| Editor | The text you are editing. Long lines wrap. |
| Symbol row | Keys for symbols; scroll it sideways to see all of them |
| Status bar | Line and column, character count and detected language |

## The menu

Tap **⋮** at the top right to open the menu: **New**, **Open**, **Save**, **Save as** and **Update**. The **⋮** turns blue, and the entry reads **Update ●**, when an update is waiting for you.

## Working with files

**New** clears the editor and starts an untitled text file.

**Open** shows Android's file picker. Pick any file, and the editor reads it as text. The app asks Android to remember access, so the file can be saved again later.

**Save** writes the file back to where it came from. For a new file it behaves like **Save as**.

**Save as** asks where to save and what to call the file. The file name decides which language is used for highlighting.

If you press **New**, **Open** or Back with unsaved changes, the editor asks whether to discard them.

### Opening files from other apps

WhatCodeEditor appears in the **Open with** list for files that Android reports as text. If a file manager does not offer it for a particular file, open the app and use **Open** instead.

## Writing code

**Auto-indent.** Pressing Enter starts the new line with the same leading spaces or tabs as the line above.

**Symbol row.** Tap a key to insert that character at the cursor. If text is selected, it is replaced. The row has Tab and these symbols:

`{ } ( ) [ ] ; : " ' < > / \ = + - _ * & | ! # $`

**Tab** inserts four spaces.

**Status bar.** Ln and Col show the cursor position, starting at 1. The character count covers the whole file.

## Not included yet

- Several files open at once
- Find and replace
- Line numbers
- Changing the theme or font size

The editor uses a dark theme only.
