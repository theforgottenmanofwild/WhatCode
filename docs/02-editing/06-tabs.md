# Tabs

Tabs let you keep several files open at once and switch between them without losing your place, and the app can bring them back the next time you start it.

## The tab bar

The tab bar sits under the top bar and shows once **two or more files** are open. Each tab has the file name and a **×** to close it.

- Tap a tab to show that file. Every tab keeps its own cursor, scroll position and undo history, so you pick up exactly where you left off.
- A dot before the name means the file has unsaved changes.
- Long-press a tab to open a small menu: **Close**, **Close others** (only when more than one file is open) and **Close all**.

## Opening and closing files

- **Open**, **Find file**, and tapping a file in the sidebar add a tab for that file. A file that is already open is not opened twice: its tab is shown instead.
- **New** opens a blank, untitled file in a new tab. If the file you are looking at is already blank and untouched, nothing happens.
- A blank, untouched file is replaced by the file you open, so you do not end up with an empty tab beside it.
- **Close file** in the **⋮** menu closes the tab you are looking at. The last tab to close leaves a blank, untitled file.
- If a tab you are closing has unsaved changes, the editor asks first. For one file that has been saved before, you can **Save**, **Discard** or **Cancel**. In the other cases (a new file, or several tabs at once) it asks whether to discard the changes.
- Leaving the app with Back asks too if any open file has unsaved changes, and names them.

## Shortcuts

With **CTRL** lit, or on a hardware keyboard:

| Shortcut | Action |
| --- | --- |
| **W** | Close the tab |
| **Tab** | Show the next tab, wrapping round at the end |
| **SHFT**+**Tab** | Show the previous tab |

## Settings

Open **⋮ > Settings** and tap **Tabs** under **Code Editor**. Every choice is saved at once.

- **Open files in tabs** is on by default. Turn it off and opening a file replaces the one in the editor, after asking about unsaved changes, and **New** does the same. Tabs that are already open stay in the bar until you close them.
- **Reopen last files** is on by default. When you open the app, the files that were open when you left it come back in their tabs, with the cursor and scroll position where you left them. The app still starts on the [file explorer](../01-basics/03-file-explorer.md); switch to the code editor to see the tabs. Files that were moved or deleted are skipped, and a message names them. With it off, the app starts on the explorer with no tabs.
- **Return to default** at the bottom of the page turns both back on. It asks you to confirm first.

## Good to know

- Only files that have a place on your device are remembered. A new file that was never saved with **Save as** is not reopened.
- If the app crashed while reopening your files, it does not try again on the next start, so it cannot get stuck in a loop. It starts on the file explorer and says so. Your files themselves are not affected.
- With **Autosave** on and **Also save when I leave the app** on, leaving the app saves every tab that has unsaved changes and a file to save to, not only the one on screen. See [Autosave](./05-autosave.md).
