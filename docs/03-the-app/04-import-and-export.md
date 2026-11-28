# Import and export

Import and export saves your settings as one JSON file, or as text on the clipboard, and loads them again on this or another device.

## Opening it

Open **⋮ > Settings** and tap **Import and export** under **Backup**.

## What a backup holds

- The **Appearance** choices: Light, Dark or System, the accent color, true black, the editor font, size, line height and margins, the current-line highlight, syntax highlighting and every syntax color.
- The **Typing** choices: closing brackets and quotes, smart indentation, closing, splitting and renaming tags, matching, the clipboard options, the indentation, word wrap, the extra keys, the status bar and the hardware keyboard options.
- The **Tabs** choices and the **Autosave** choices.
- The **key rows**, including the rows you set for single languages.
- The **File explorer** choices: everything on its **View**, **Sorting**, **Start and navigation**, **Taps**, **Safety** and **Row menu** pages, the same View and Sort choices as the explorer's menu, and the start folder you typed.
- Whether the app checks for updates by itself.

It does not hold your files, your folder, your open tabs, the folder the explorer showed last, or anything about the last update check.

## Exporting

- **Save to a file** asks where to put `whatcode-settings.json`.
- **Copy to the clipboard** copies the same text, to paste into a message or a note.

The file is plain text. It starts with the app name, the layout of the file (`format`) and the version of the app that made it, and then lists each saved page of settings with its values. Only the settings you have changed are in it; everything else is at its default. You can open it in any text editor and change a value by hand, for example `"font_size": 18`.

## Importing

- **Open a file** picks a JSON file.
- **Paste from the clipboard** reads the text you copied.

Before anything changes, the app checks the text and asks you to confirm. Importing **replaces** the settings on the pages the backup covers: a setting the backup does not mention goes back to its default there, and pages it has nothing for are left alone. Your files and tabs are never touched.

Nothing in the file is trusted blindly. A setting this version does not have, or a value it cannot use (a font size of 500, a color name that does not exist, a key row that is not a list), is ignored, and the dialog and the result on the page say how many entries were ignored. A file that is not valid JSON, is not a WhatCode backup, is too big to be one, or was made by a newer layout of the file is refused with a message, and nothing changes.

This page takes on an imported theme and accent at once, and the editor does when you go back to it.

## Moving to another device

Export on the old device, send the file to the new one, and import it there. A backup made by one version of the app can be read by a later one; settings added since are simply at their defaults, and settings that no longer exist are ignored.
