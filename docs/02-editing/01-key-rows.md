# The key rows

Two rows sit above the keyboard. **Each row scrolls sideways on its own**: the main keys come first, then the extra tools and the symbols.

Keys act when you lift your finger, so you can drag along a row without pressing anything. The arrows and the line-move keys repeat while held.

Most keys are drawn icons. The modifiers (SHFT, CTRL, ALT), ESC, `//`, Go and the symbols show text.

## The default keys

- **Row 1:** SHFT, Tab, Find, Undo, Redo, Up, Save, ESC, `//`, Go, then the symbols `{ } ( ) [ ] < >`
- **Row 2:** CTRL, ALT, Move line down, Move line up, Left, Down, Right, Copy line down, Delete line, then the symbols `; : " ' / \ = + - _ * & | ! # $`

## What the keys do

| Key | What it does |
| --- | --- |
| **SHFT**, **CTRL**, **ALT** | Modifiers. Tap once to use it for the next key only (the key lights up), tap again to lock it on, and tap a third time to turn it off. |
| **Tab** (an arrow running into a bar) | Tab: inserts four spaces, or indents every selected line. With **SHFT** it removes one level of indent (a tab, or up to four spaces). |
| **Find** (a magnifying glass) | Opens find and replace. See [Find and replace](./02-find-and-replace.md). |
| **Undo** and **Redo** (curved arrows) | Undo and redo. They are dimmed when there is nothing to undo or redo. Typing and deleting in a quick run counts as one step. |
| **Up**, **Down**, **Left**, **Right** (arrows) | Move the cursor. With **SHFT** they select. With **CTRL**, left and right jump by word, and up and down jump to the start and end of the file. With **ALT**, left and right jump to the start and end of the line. |
| **Save** (a floppy disk) | Save. |
| **ESC** | Closes the find bar, or else clears the selection, and turns off any modifiers. |
| **Move line down** and **Move line up** (text lines with an arrow) | Move the current line (or every selected line) down or up. Nothing happens at the first or last line. |
| **Copy line down** (a line, a dashed copy and an arrow) | Copy the current line (or every selected line) below itself. |
| **`//`** | Comment or uncomment the current line or selected lines, using the comment style of the file's language: `//`, `#`, `--`, `;`, `/* */` or `<!-- -->` (see [Languages and highlighting](./04-languages.md)). If every line is already commented, they are all uncommented; otherwise they are all commented, at the shallowest indent, and blank lines are left alone. A file type with no comment style (plain text, JSON) shows a message instead. |
| **Go** | Go to a line number. A box asks for it and shows how many lines there are; a number outside that range goes to the nearest line. |
| **Delete line** (a bin) | Delete the current line (or every selected line). |
| Symbols | The characters themselves, such as `{` or `=>`. Each types at the cursor, and replaces the selection if there is one. |

Every line tool is a single undo step.

## Shortcuts

With **CTRL** lit, type a letter on the keyboard to run a shortcut:

| Shortcut | Action |
| --- | --- |
| **S** | Save |
| **F** or **H** | Find and replace |
| **Z** / **Y** | Undo / redo (**SHFT**+**Z** also redoes) |
| **A**, **C**, **X**, **V** | Select all, copy, cut, paste |
| **D** | Copy line down |
| **K** | Delete line |
| **/** | Toggle comment |
| **G** | Go to line |

On the on-screen **CTRL**, a letter that is not in the list shows a message saying it is not a shortcut. **ALT** has no letter shortcuts; it is used with the arrow keys.

With only **SHFT** lit, the next letter is typed as a capital. The same Ctrl shortcuts work on a hardware keyboard.

## Changing the keys

Open **⋮ > Settings** and tap **Key Editor** under **Code Editor**. The page lists the keys of both rows in order.

- **↑** and **↓** move a key one place. Long-press either to send the key to the start or the end of the row.
- **×** removes a key.
- **Add key** opens a panel under the row. Type any text or symbol, such as `=>` or `->`, and tap **Add** to get a key that types it. Or pick a built-in key from the list, including the modifiers and the tools above. New keys go at the end of the row, and the panel stays open until you tap **Close add panel**.
- **Return to default** next to **Add key** puts that one row back to its default keys. Tap it twice to confirm.
- **Return both rows to default** at the bottom of the page does the same for both rows.
- A row can hold up to 60 keys.

Every change is saved at once. **⋮ > Edit keys** in the editor's menu is a quicker version of the same thing in dialogs; it changes the same keys.

Your layout is remembered.
