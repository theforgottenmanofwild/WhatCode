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

**Line endings.** The last part of the status bar shows how the lines of the file end: **LF** (Linux, macOS and Android), **CRLF** (Windows) or **CR** (very old Mac files). **Mixed** means the file uses more than one of them. A file with no line break yet shows **LF**, which is what a new line gets. Saving never converts anything: the file is written back with the line endings it has in the editor, and a file that was **Mixed** stays exactly as mixed. To change them, tap the line ending in the status bar and pick **Line endings to LF** or **Line endings to CRLF** (see [Text tools](#text-tools)). Lines you add by pressing Enter end with LF, so adding a line to a CRLF file makes it **Mixed**; the lines that were already there are left as they are, and pasted text keeps the line breaks it came with. In a very big file the ending appears a moment after the file opens.

**Status bar.** The bar at the bottom can be hidden with **Settings > Typing > Show the status bar**. It is on by default. Ln and Col show the cursor position, starting at 1. The character count covers the whole file. The language shown is the one picked from the file name, for example **JavaScript** for `main.js` (see [Languages and highlighting](../02-editing/04-languages.md)). After it comes the line ending (see above), then the current scope (see above). In a big file the bar ends with **Large file mode**, and tapping the bar says what was turned off (see [Large files](../03-the-app/06-large-files.md)).

## Text tools

The **Txt** key (or **Ctrl+P**) lists the tools below. Tap one to run it. Each use is a single undo step, and a message says so if there is nothing to change. Each tool also has a key of its own that you can add in the Key Editor.

| Tool | What it does |
| --- | --- |
| **Sort lines, A to Z** and **Sort lines, Z to A** | Sorts the lines. Equal lines keep their order. Capitals are ignored and numbers are read by value (`item2` before `item10`) unless you turn that off. **Ctrl+R** sorts A to Z, **Ctrl+Shift+R** Z to A. |
| **Reverse lines** | Puts the lines in the opposite order. |
| **Remove duplicate lines** | Removes each line that is the same as an earlier one. Lines that differ in capitals or spaces are different, unless you choose to ignore capitals. |
| **Upper case**, **Lower case**, **Title case** | Changes the selection, or the word at the cursor when nothing is selected. **Ctrl+U** is upper case, **Ctrl+Shift+U** lower case. |
| **Swap characters** | Swaps the character before the cursor with the one after it, or the last two at the end of a line. An emoji moves as one character. **Ctrl+I**. |
| **Indent with tabs** and **Indent with spaces** | Rewrites the spaces and tabs at the start of every line of the file, using the tab width from the settings. Whitespace inside a line, and lines with nothing else on them, are left alone. Text inside a string that spans lines counts as the start of a line too. |
| **Line endings to LF** and **Line endings to CRLF** | Changes every line break of the file, including lone CR ones. |
| **Format JSON** and **Minify JSON** | Format puts each name and each item of a list on its own line, indented. Minify takes out all the white space. They work on the selection, or on the whole file when nothing is selected. **Ctrl+Shift+J** formats. |

The line tools work on the lines you have selected when the selection touches two or more lines, and on the whole file otherwise. Every line keeps its ending, except that removing and reordering lines never adds or takes away the break at the end of the file. The two indentation tools and the two line ending tools always work on the whole file, and bookmarks stay on their lines. The JSON tools work on the selection, or on the whole file when nothing is selected. They leave the white space around the JSON alone, keep the line breaks the text already uses, copy every name, string and number exactly as it was written, and keep the names in their order. A formatted selection keeps the indentation of the line it starts on, and stays selected. The JSON must be strict: a comment, a trailing comma, single quotes or two values in a row are refused with a message that gives the line and column, and nothing is changed. Lists and objects can be nested 512 levels deep. In a text of 200,000 characters or more a tool runs on another thread, with a box that can cancel it.

**Settings > Text tools** turns the tools off, and chooses **Ignore capitals when sorting** (on), **Sort numbers by value** (on), **Ignore capitals when comparing** for duplicates (off), the **Tab width** (1 to 16, default 4) and the **JSON indentation** (a tab, or 1 to 8 spaces, default 2). **Return to default** puts these back.

## Snippets

A snippet is a short word that stands for a piece of code. Type the word, such as `fn` or `for`, and press **Tab**: the word is replaced by the code, and the first place to fill in is selected. Type over it, then press **Tab** to go to the next place, or **Shift+Tab** to go back. After the last place, **Tab** puts the cursor where the snippet ends. **ESC**, or moving the cursor out of the snippet, stops. A place that is used twice in a snippet starts with the same text in both. The lines of a snippet take the indentation of the line it is put in, and a tab in a snippet is one level of the file's indentation. Putting a snippet in is one undo step.

The word must end at the cursor and not run on after it, and a word right after `.`, `-`, `$`, `@`, `#` or `\` (a member, a property or a variable) is never expanded, so **Tab** indents there as usual. Words are matched with their capitals.

The **Snip** key (or **Ctrl+.**) lists the snippets of the open file's language; tap one to put it in, in place of the selection. The editor comes with snippets for JavaScript, TypeScript, Python, Kotlin, Java, Go, Rust, C, C++, C#, Swift, Ruby, shell, HTML, CSS, SCSS, Less, Markdown and SQL.

**Your own snippets.** **Settings > Snippets** lists the snippets you wrote. **Add a snippet** asks for the word, a name for the list, the language (or every language) and the code. In the code, `$1`, `$2` and so on are the places **Tab** goes to, in the order of their numbers; `${1:text}` starts with `text` there; `$0` is where the cursor ends up; and `\$` is a dollar sign. Tap a snippet to change or delete it. One of yours with the same word as a built-in one takes its place. You can have up to 200 snippets, with up to 4,000 characters of code each and 100,000 in all.

**Export to a file** writes your snippets to `whatcode-snippets.json`, and **Import a file** reads such a file and adds its snippets to yours: one with the word and language of one of yours replaces it, and the app says how many were added, replaced, did not fit or could not be read before anything changes. [Import and export](../03-the-app/04-import-and-export.md) keeps your snippets too.

**HTML and CSS abbreviations.** In an HTML file, **Tab** after an abbreviation writes the tags it stands for, in the style of Emmet. `div#main.box` is a div with that id and class, `a[href=x]` sets an attribute, `p{Hello}` puts text in, `>` puts the next tag inside, `+` puts it after, `^` goes back up a level, `*3` makes three, `$` in a name or text is the number of the copy (`$$` gives 01, 02…), and brackets group tags, as in `(dt+dd)*2`. With no tag name you get a div, or an li in a list, a tr in a table, a td in a row and an option in a select. `!` writes a whole page. Some tags get the attributes they need, such as `href` on a link and `src` and `alt` on an image. Each empty tag and attribute is a place to fill in, and **Tab** goes from one to the next as in a snippet. Only tag names HTML has, and names with a hyphen, are taken, so **Tab** after a plain word in your text still indents. In a CSS, SCSS or Less file, **Tab** at the start of a declaration inside `{ }` expands `m10` to `margin: 10px;`, `p10-20` to `padding: 10px 20px;`, `w50p` to `width: 50%;` (also `e` em, `r` rem, `v` vh, `w` vw), `c#f` to `color: #ffffff;`, `df` to `display: flex;` and `jcsb` to `justify-content: space-between;`; a property name of two letters or more alone, such as `bgc`, leaves a place for its value. Turn this off with **Settings > Snippets > Abbreviations**.

**Settings > Snippets** also turns snippets off, turns off the **Built-in snippets**, and turns off **Expand with Tab**, so that only the Snip key puts one in and **Tab** always indents. **Return to default** puts these three switches and **Abbreviations** back, and keeps your snippets.

## Word completion

While you type, a short list under the cursor offers the words that start with the letters you have typed. They come from the file you are in, nearest to the cursor first, and then from your other open tabs, the most common first. **Tab** takes the chosen word (the first one at the start), the up and down keys of a hardware keyboard choose another, **Esc** closes the list, and tapping a word takes it. Taking one is a single undo step, and the word replaces what you typed, so the capitals of the suggestion are kept. Typing on, moving the cursor, scrolling or leaving the editor closes the list.

The **Cmp** key (or **Ctrl+Space**) asks for suggestions at once, after any number of letters. The key is not in the default rows; add it in the Key Editor. After the word of a snippet, or an HTML or CSS abbreviation, **Tab** still puts the snippet or the tags in; the list goes with it. A number is never completed.

Only 100,000 characters on each side of the cursor are read in the file you are in, and the first 300,000 characters of at most 10 other tabs, so suggestions come as fast in a big file as in a small one. **Settings > Completion** turns it off, sets how many letters it waits for (1 to 8, 3 at first) and how long after the last letter (0 to 1,500 ms, 250 at first), turns the other tabs off, and lets **Enter** take the suggestion (off at first, so Enter always starts a new line). **Return to default** puts these back.

## Multiple cursors

Put the cursor in a word and press **+Nx** (or **Ctrl+Shift+D**): the word is selected. Press it again and a cursor is added at the next place with the same text, going on from the end of the selection and round to the start of the file. The new one is the main cursor, and the others are drawn as thin bars (or as a coloured band, where something is selected). **+All** (or **Ctrl+Shift+L**) puts a cursor at every place at once. The two keys are not in the default rows; add them in the Key Editor. A message says so when there is no more of the text, when too much text is selected, or when there are already as many cursors as **Settings > Multiple cursors** allows.

With several cursors, typing, **Enter** (which keeps the indentation of each line), **Tab**, backspace, forward delete, cut and paste all happen at every cursor. A paste with as many lines as there are cursors gives one line to each cursor; any other paste goes in whole at every cursor. Copy puts the selected text of every cursor, one under the other, on the clipboard. The left and right arrows (on the key row, with **CTRL** for words and **ALT** for the line ends, or on a hardware keyboard) move every cursor. Brackets and quotes are not closed by themselves while there are several cursors.

Everything you do at the cursors together is one change to the text, so one undo takes it all back; a word that the keyboard composes letter by letter is one undo step too, but each key of a hardware keyboard is a step of its own. **Esc**, a touch on the text, the up and down arrows, and anything else that changes the text (undo, redo, a text tool, switching tabs) drop the extra cursors. The cursors must be within 5,000,000 characters of each other, so that the change stays one undo step.

**Settings > Multiple cursors** turns the feature off, chooses whether capitals and whole words matter when finding the next place (capitals do at first, whole words do not), sets the most cursors (200 at first, 2 to 1,000) and turns off splitting a paste by lines. **Return to default** puts these back.

## Navigation

**Go to symbol.** The **Fn** key (or **Ctrl+O**) lists the functions, classes and headings of the open file. Type in the box to keep the names that hold your letters, then tap one to put the cursor at its name. Each row shows a mark (**ƒ** for a function, **◆** for a class or a CSS rule, **§** for a heading), the name, and its line number. Names are indented to show what holds what. The list is found line by line with a pattern for each language, not by reading the program, so a rare form (an arrow function kept in a variable, say) can be missing. Markdown and HTML list their headings, CSS its rules, YAML its top keys and INI its sections. At most 5,000 names are listed, and a text of 200,000 characters or more is read on another thread, with a box that can stop it. A plain text file shows a message. The key is in the default keys of row 1; if you changed your keys earlier, add it from the list of built-in keys in the Key Editor.

**Bookmarks.** The **Mk** key (or **Ctrl+B**) marks the line the cursor is on, and pressing it again on that line takes the mark away. A bar at the left edge of the editor, in the accent color, shows a marked line, with or without line numbers. **Mk>** and **Mk<** (or **Ctrl+N** and **Ctrl+Shift+N**) go to the next and the previous marked line, and round to the other end of the file after the last or the first. A mark stays with its line when lines are added or removed above it. Each tab has its own bookmarks, which are kept while the tab is open and are gone when it is closed or the app is closed. Turn them off with **Settings > Navigation > Bookmarks**.

**Back and forward.** Go to line, Go to symbol, the bookmark keys and each search hit remember where the cursor was. The **<-** key (or **Ctrl+-**) goes back to that place, and **->** (or **Ctrl+Shift+-**) goes forward again, as in a web browser. A jump after going back drops the places you could have gone forward to. Stepping through the hits of one search counts as a single jump. The last 100 places are kept for each tab. Turn this off with **Settings > Navigation > Back and forward**.

**Current scope.** The status bar names what holds the cursor, such as `Foo › run` for a function in a class, or the Markdown heading above it. It is found from the indentation of the lines, a quarter of a second after the last change. It is not shown in a plain text file, in a text of over 200,000 characters or in large file mode. Turn it off in **Settings > Navigation > Show the current scope**.

**Code folding.** Blocks inside `{ }`, `[ ]`, `( )` or indented blocks can be folded so only their first line stays visible, with a `…` mark at the end of the line. Tap the `▾` or `▸` arrow next to the line numbers (or tap `…`) to fold or unfold a block, use the **Fld** key to toggle the block at the cursor (**Ctrl+Shift+[** folds, **Ctrl+Shift+]** unfolds), or press **Ctrl+9** and **Ctrl+0** (**Fld-** and **Fld+**) to fold and unfold every block. Folded blocks stay with their tab until it is closed. Moving the cursor with arrow keys steps over folded blocks, and searching, Go to line, Go to symbol and Back/Forward open any fold that hides where they jump. Folding is turned off in a file with very long lines or over 200,000 characters. Change **Code folding**, **Gutter markers**, **Find blocks by** (**Brackets and indentation**, **Indentation**, or **Brackets**) and **Minimum hidden lines** (1 to 20, default 1) under **Settings > Navigation**.

## Hardware keyboard

With a physical keyboard connected, the two rows of extra keys are hidden, because the keyboard has those keys itself. They come back when it is removed. To keep them showing, turn off **Settings > Typing > Hide the extra keys**. To never show them, turn off **Settings > Typing > Show the extra keys**; they come back when you turn it on again. The Ctrl shortcuts listed in [The key rows](../02-editing/01-key-rows.md#shortcuts) work on a hardware keyboard, and **Settings > Typing** lists them too. If you would rather leave Ctrl and a letter to Android, turn off **Settings > Typing > Keyboard shortcuts**; the on-screen **CTRL** key keeps working.

## Backing up your settings

**Settings > Import and export** saves your settings as a JSON file and reads them back. See [Import and export](../03-the-app/04-import-and-export.md).

## Appearance

The theme, accent color, font, font size, spacing, line numbers and whitespace marks are changed in **Settings > Appearance** (see [Appearance](../03-the-app/03-appearance.md)).
