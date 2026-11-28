# The key rows

Two rows sit above the keyboard. **Each row scrolls sideways on its own**: the main keys come first, then the extra tools and the symbols.

Keys act when you lift your finger, so you can drag along a row without pressing anything. The arrows and the line-move keys repeat while held.

Most keys are drawn icons. The modifiers (SHFT, CTRL, ALT), ESC, `//`, Go and the symbols show text.

## The default keys

These are the keys for all languages. The tools are the same in every file, but with **Change keys by language** on (it is on by default, see [Keys by language](#keys-by-language)) the symbols change to suit the language of the open file.

- **Row 1:** SHFT, Tab, Find, Undo, Redo, Up, Save, ESC, `//`, Go, Fn, Jump to matching, Expand selection, Wrap in tag, Txt, then the symbols `{ } ( ) [ ] < >`
- **Row 2:** CTRL, ALT, Move line down, Move line up, Left, Down, Right, Copy line down, Delete line, Mk, Mk<, Mk>, <-, ->, then the symbols `; : " ' / \ = + - _ * & | ! # $`

## What the keys do

| Key | What it does |
| --- | --- |
| **SHFT**, **CTRL**, **ALT** | Modifiers. Tap once to use it for the next key only (the key lights up), tap again to lock it on, and tap a third time to turn it off. |
| **Tab** (an arrow running into a bar) | Tab: inserts one level of indentation, or indents every selected line. A level is what the file already uses: a tab, or two to four spaces (if the file shows no pattern, the choice in **Settings > Typing > Indentation**, which is four spaces, or a tab in Go and Makefiles, unless you change it). With **SHFT** it removes one level of indent. |
| **Find** (a magnifying glass) | Opens find and replace. See [Find and replace](./02-find-and-replace.md). |
| **Undo** and **Redo** (curved arrows) | Undo and redo. They are dimmed when there is nothing to undo or redo. Typing, deleting, and a keyboard correcting the word you are typing, in a quick run, count as one step. |
| **Up**, **Down**, **Left**, **Right** (arrows) | Move the cursor. With **SHFT** they select. With **CTRL**, left and right jump by word, and up and down jump to the start and end of the file. With **ALT**, left and right jump to the start and end of the line; on an indented line the start key stops at the first character before the true start of the line. |
| **Save** (a floppy disk) | Save. |
| **ESC** | Closes the find bar, or else clears the selection, and turns off any modifiers. |
| **Move line down** and **Move line up** (text lines with an arrow) | Move the current line (or every selected line) down or up. Nothing happens at the first or last line. |
| **Copy line down** (a line, a dashed copy and an arrow) | Copy the current line (or every selected line) below itself. |
| **`//`** | Comment or uncomment the current line or selected lines, using the comment style of the file's language: `//`, `#`, `--`, `;`, `/* */` or `<!-- -->` (see [Languages and highlighting](./04-languages.md)). If every line is already commented, they are all uncommented; otherwise they are all commented, at the shallowest indent, and blank lines are left alone. A file type with no comment style (plain text, JSON) shows a message instead. |
| **Go** | Go to a line number. A box asks for it and shows how many lines there are; a number outside that range goes to the nearest line. |
| **Fn** | Go to symbol: lists the functions, classes and headings of the file, with a box to filter them, and goes to the one you tap. It also works as **Ctrl+O**. A plain text file shows a message instead. See [Using the editor](../01-basics/02-using-the-editor.md#navigation). |
| **Delete line** (a bin) | Delete the current line (or every selected line). |
| **Mk** | Put a bookmark on the cursor's line, or take it away. A bar at the left edge of the editor shows a marked line. See [Using the editor](../01-basics/02-using-the-editor.md#navigation). |
| **Mk>** and **Mk<** | Go to the next or the previous bookmarked line, and round to the first or last one after the end. A message says so if there are no bookmarks. |
| **<-** and **->** | Go back to where the cursor was before the last jump, and forward again. Go to line, Go to symbol, the bookmark keys and a search hit count as jumps. A run of search hits is one jump. |
| **Jump to matching** (two square brackets with an arrow between them) | Move the cursor to the partner of the tag it is in (HTML, XML and SVG), or of the bracket next to it. Press it again to go back. With **SHFT** lit, the selection stretches to the partner instead. A message says so if there is nothing to jump to. See [Using the editor](../01-basics/02-using-the-editor.md#writing-code). |
| **Expand selection** (a dashed box with a corner pushing out on each side) | Select more, one step at a time: the word, the line, the inside of the brackets around it, those brackets, the inside of the tag around it (HTML, XML and SVG), the whole element, and so on out to the whole file. With **SHFT** lit, it goes back through the same steps. See [Using the editor](../01-basics/02-using-the-editor.md#writing-code). |
| **Wrap in tag** (two angle brackets with a line between them) | In HTML, XML and SVG, asks for a tag and puts it around the selection or the word at the cursor. With **SHFT** lit, removes the tag pair around the cursor and keeps its content. A message says so in other files. See [Using the editor](../01-basics/02-using-the-editor.md#writing-code). |
| **Ind** and **Rul** | Show or hide the indent guides and the column ruler (see [Appearance](../03-the-app/03-appearance.md#editor-text)). They are not in the default rows; add them from the list of built-in keys in the Key Editor. |
| **Txt** | Text tools: lists the tools below, and runs the one you tap. It also works as **Ctrl+P**. See [Using the editor](../01-basics/02-using-the-editor.md#text-tools). |
| **A-Z**, **Z-A**, **Rev**, **Uniq** | Sort the lines up or down, reverse them, or remove duplicate lines. These and the keys in the next three rows are not in the default rows; add them from the list of built-in keys in the Key Editor. |
| **ABC**, **abc**, **Abc**, **Swap** | Upper case, lower case, title case, and swapping the two characters round the cursor. |
| **⇥T** and **⇥S** | Indent with tabs, or with spaces. |
| **LF** and **CRLF** | Convert the line endings of the whole file. |
| Symbols | The characters themselves, such as `{` or `=>`. Each types at the cursor, and replaces the selection if there is one. |

Every line tool is a single undo step.

## Shortcuts

With **CTRL** lit, type a letter on the keyboard to run a shortcut:

| Shortcut | Action |
| --- | --- |
| **S** | Save |
| **F** or **H** | Find and replace |
| **Z** / **Y** | Undo / redo (**SHFT**+**Z** also redoes) |
| **A**, **C**, **X**, **V** | Select all, copy, cut, paste. Paste is always plain text. With nothing selected, copy and cut take the whole line |
| **D** | Copy line down |
| **K** | Delete line |
| **L** | Select the line (or every selected line), with its line break |
| **J** | Join the line with the next one (or join all selected lines) |
| **Enter** | Open a new line below and move to it. With **SHFT**, open it above |
| **]** / **[** | Indent / outdent the line or selected lines |
| **/** | Toggle comment |
| **G** | Go to line. With **SHFT**, show or hide the indent guides |
| **Q** | Show or hide the column ruler |
| **O** | Go to symbol |
| **B** | Add or remove a bookmark |
| **N** | Next bookmark. With **SHFT**, the previous one |
| **-** | Back to where the cursor jumped from. With **SHFT**, forward again |
| **M** | Jump to the matching tag or bracket |
| **E** | Expand the selection. With **SHFT**, shrink it |
| **T** | Wrap in a tag. With **SHFT**, remove the tag pair around the cursor |
| **P** | Text tools |
| **U** | Upper case. With **SHFT**, lower case |
| **R** | Sort lines A to Z. With **SHFT**, Z to A |
| **I** | Swap the two characters round the cursor |
| **W** | Close the tab (see [Tabs](./06-tabs.md)) |
| **Tab** | Next tab. With **SHFT**, the previous tab |

On the on-screen **CTRL**, a letter that is not in the list shows a message saying it is not a shortcut. **ALT** has no letter shortcuts; it is used with the arrow keys.

With only **SHFT** lit, the next letter is typed as a capital. The same Ctrl shortcuts work on a hardware keyboard.

## Changing the keys

Open **⋮ > Settings** and tap **Key Editor** under **Code Editor**. The page lists the keys of both rows in order.

- **↑** and **↓** move a key one place. Long-press either to send the key to the start or the end of the row.
- **×** removes a key.
- **Add key** opens a panel under the row. Type any text or symbol, such as `=>` or `->`, and tap **Add** to get a key that types it. Or pick a built-in key from the list, including the modifiers and the tools above. New keys go at the end of the row, and the panel stays open until you tap **Close add panel**.
- **Return to default** next to **Add key** puts that one row back to its default keys. Tap it twice to confirm.
- **Return both rows to default** at the bottom of the page does the same for both rows. It asks you to confirm first.
- A row can hold up to 60 keys.

Every change is saved at once. **⋮ > Edit keys** in the editor's menu is a quicker version of the same thing in dialogs; it changes the same keys. With **Change keys by language** on, it edits the keys of the language of the open file.

Your layout is remembered, and it is part of what **Settings > Import and export** saves (see [Import and export](../03-the-app/04-import-and-export.md)). Layouts you saved before the bookmark and back keys, **Go to symbol**, **Jump to matching**, **Expand selection**, **Wrap in tag** or **Txt** were added do not have them; add them from the list of built-in keys.

### Keys by language

At the top of the Key Editor, **Change keys by language** is on by default. While it is on, the rows switch to symbols that suit the open file, such as `:=` and `<-` for Go, `=>` and `===` for JavaScript and TypeScript, `->` for Python, `</>` for HTML, or `true` and `false` for JSON. The tool keys (SHFT, Tab, Find, arrows and so on) stay where they are; only the symbol keys change.

These languages have symbols of their own: Go, Python, JavaScript, TypeScript, Kotlin, Rust, Shell, SQL, JSON, JSON with comments and Makefile, plus the kinds HTML, XML and SVG, CSS, SCSS and Less, YAML, TOML, INI and Properties, and Markdown. Every other language, such as Java or C, uses the same keys as all languages.

To change the keys of one language, pick it under **Editing keys for** (the list marks languages with built-in keys and the ones you changed), then edit the rows as above. The other languages keep their keys. **Return to default** on a language gives back its built-in keys, and **All languages** edits the layout that languages without keys of their own use. Turn the switch off and every file shows the all-languages layout.
