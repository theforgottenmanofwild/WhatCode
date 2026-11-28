# Large files

The editor keeps working in a big file by doing less of the work: it colors only the lines you can see, and it leaves a very long line alone.

## Opening the settings

Open **Settings** and tap **Large files** under **Code Editor**. Every choice is saved as soon as you make it, and the editor uses it the next time it colors the text. **Return to default** at the bottom puts all three back, after asking.

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

## Saving the settings

The three choices are part of [Import and export](04-import-and-export.md).
