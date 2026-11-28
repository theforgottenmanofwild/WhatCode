# Large files

The editor keeps working in a big file by doing less of the work: it turns off a few things, colors only the lines you can see, and leaves a very long line alone.

## Opening the settings

Open **Settings** and tap **Large files** under **Code Editor**. Every choice is saved as soon as you make it, and the editor uses it at the next change to the text. **Return to default** at the bottom puts every choice on the page back, after asking.

## Large file mode

A text with more characters than a size you can set is a *large file*, and one with more than a second size is a *very large file*. A file of plain English is about one byte for each character. The sizes, when nothing was chosen, are:

- **Large files start at** 300,000 characters.
- **Very large files start at** 2,000,000 characters.

A file of exactly that many characters is not yet large. Type or delete and the editor changes mode by itself when the text crosses a size. Choose a size from the list or type one from 20,000 to 100,000,000. A very large size below the large one is read as the same size.

In a large file the editor, at first:

- turns off **word wrap**, because laying out every wrapped line takes too long;
- marks the bracket or tag that goes with the one at the cursor only when both are on the screen.

A very large file turns off those two and also:

- **whitespace marks**, the dots and arrows for spaces and tabs;
- most of the **undo** history: it keeps the last 200 steps instead of 1,000.

Each of these is a switch under **Turned off in a large file** and **Also turned off in a very large file**, so you can keep any of them on. **Undo steps kept** sets the 200, from 10 to 1,000, and is used when the undo switch is on. **Large file mode** at the top turns all of it off; the editor then keeps everything on, whatever the size.

The status bar ends with **Large file mode** while something is turned off. Tap the bar to read what, and why, and to open these settings.

## Opening and saving a big file

A file of 1,000,000 bytes or more is read on its own thread, so the screen keeps working. If it takes more than a quarter of a second, a box shows how much has been read, with **Cancel**. Cancelling leaves the editor as it was and opens nothing. A file that is not text is refused, as before.

**Save** works the same way for a text of 1,000,000 characters or more, and the box shows how much has been written. It has no **Cancel**: saving empties the file first, so stopping halfway would leave it cut short. Text typed before the box shows stays unsaved, and the file is marked as changed. **Autosave** of a text that long is written on another thread too, with no box, so you can keep typing; text typed meanwhile is saved by the next autosave. Autosave when you leave the app, and saving from the close-tab question, still finish before anything else.

## Very long lines

A minified script or a JSON file written on one line can be a single line of millions of characters. Wrapping it or coloring it would freeze the editor, so a line with more characters than **Longest line** is handled differently:

- It is not colored. The lines around it are colored as usual.
- The text is not wrapped, whatever **Word wrap** says, and scrolls sideways instead. Wrapping comes back by itself when no line is that long any more.
- The status bar adds **Long lines: not coloured or wrapped** (**Long lines: not wrapped** in a plain text file, which has no colors).

**Longest line** is 20,000 characters at first. Choose 5,000, 10,000, 20,000, 50,000, 100,000 or 500,000, or type a number from 1,000 to 1,000,000. A line of exactly that many characters is still colored.

## Colors in a long text

A text longer than 20,000 characters is colored only where you see it, and a number of lines above and below.

- **Wait after typing** is how long you must stop typing before the colors are worked out again: 200 ms at first, from 50 ms to 5 seconds.
- **Lines colored around the screen** is how many lines above and below the screen are colored too: 60 at first, from 10 to 1,000. A bigger number means less color popping in as you scroll, and more work at each pause.

Files longer than 8,000,000 characters are not colored at all (see [Limits](../02-editing/04-languages.md#limits)).

## The large-file viewer

Files bigger than **Open in viewer above** (8,000,000 bytes by default) open in the dedicated **large-file viewer** (or ask first, choosing between the viewer and the editor). You can also open any saved text file in it from the explorer's row menu (**Open in large-file viewer**) or from the editor's **⋮** menu.

- **Streams from disk**: reads windows on demand instead of loading the entire file, and indexes line breaks in the background while showing `counting…`.
- **Go to & Tail**: jump to a line number or a percentage (`50%`) even while counting, or turn on **Tail** to follow growing log files automatically.
- **Streaming search**: tap **Find** for case-sensitive, whole-word or regular-expression search with live counts, next/previous hits and **Stop**.
- **Long lines, wrap & zoom**: pinch to zoom font size, toggle **Wrap**, or tap **+ more** on a long line to expand it. Drag or long-press text and tap **Copy** to copy up to 1,000,000 characters.
- **Piece-table editing & atomic save**: tap **Edit** to enter edit mode, tap any line to modify or delete it with **Undo**/**Redo** without loading the whole file into memory, and tap **Save** to stream changes to a temporary file and atomically replace the file on disk.

Under **Settings > Large files**, choose **When opening a file this large** (**Ask**, **Always open in the large-file viewer**, or **Always open in the editor**), **Turn off word wrap in the viewer**, **Follow the end of the file (tail)**, **Line index checkpoint interval** (32 to 4,096 lines) and **Search match cap** (100 to 50,000).

## Saving the settings

Every choice on the page is part of [Import and export](04-import-and-export.md).
