# Find and replace

Find and replace is a bar above the key rows, not a dialog, so the matches stay highlighted in the text while you step through them and edit.

## Opening and closing

Tap the **Find** key, the magnifying glass (or press **CTRL** then **F**, or **H**), to open the bar. If you had a short piece of text selected on one line, it is filled in for you.

**✕**, the **ESC** key or Back closes the bar and removes the highlighting.

## The bar

| | Search side | Buttons |
| --- | --- | --- |
| Top row | **Search** field | left arrow: previous match, right arrow: next match, cog: options, **✕**: close |
| Bottom row | **Replace** field | replace this match, replace all matches, and the match counter |

The replace buttons are drawn as a dashed selection box with an arrow to one box (replace this match) or to a row of three boxes (replace all).

## Searching

- Type in **Search**: every match is highlighted in the text, the current one in orange, and the counter shows which one you are on, such as **3 of 12**. The counter reads **0 of 0** when nothing matches.
- The first match at or after the cursor is selected for you. The list is kept up to date while you edit the text with the bar open.
- The left and right arrows (or the keyboard's search key) step to the previous and next match, wrapping around at the ends.
- If there are more than 3,000 matches, only the first 3,000 are highlighted in the text, but every match still counts and can be stepped to, up to 100,000. Beyond that, the rest are not counted, and the counter ends with a plus, such as **3 of 100000+**.

## The options

Tap the cog to open a menu with three checkboxes. The cog is blue while any of them is on.

| Option | Effect |
| --- | --- |
| **Case sensitive** | Off by default, so `Name` also finds `name`. |
| **Whole word** | A match must not touch a letter, digit or underscore on either side, so `cat` does not find `concat`. |
| **Regular expression** | What you type is a regular expression (Java syntax). `^` and `$` match at the start and end of every line. The field says "Search (regex)", and the counter says **bad regex** if what you typed is not valid. Empty matches are ignored. |

## Replacing

- Replace this match: replaces the current match and moves to the next one.
- Replace all: replaces every match in one step, so one undo brings them all back. A message says how many were replaced.
- In plain mode the replacement is inserted exactly as you typed it.
- In regex mode the replacement can use groups such as `$1`, and `\n` and `\t` for a new line and a tab. A backslash before any other character inserts that character, so `\\` gives one backslash.

## In a long text

A text of 200,000 characters or more is searched on another thread, so typing and scrolling stay smooth. So is a search in a shorter text that takes more than a fifth of a second, for example a slow regular expression.

- The counter shows how far the search is, such as **42%**. Touch it to stop the search; it then reads **stopped**.
- Typing in the text clears the matches and searches again a moment after you stop. The result of a search that was overtaken by an edit is thrown away.
- **Replace all** also runs on another thread, with the same percentage. The text is changed only when it is done, in one step, so one undo brings it all back. If the text changed meanwhile, nothing is replaced and a message says so.
- **Replace this match** in a long text moves on to the next match without searching the whole text again. After a change of the text, the list is made again.
