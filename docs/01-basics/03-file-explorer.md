# File explorer

The file explorer is the first screen of the app: a list of the folders and files on your phone, where you pick the file to open in the editor.

## Starting the app

The app starts on the file explorer. It goes straight to the editor instead when you open it with a file from another app (**Open with** or **Share**), or when files were open the last time you used it (see [Tabs](../02-editing/06-tabs.md#settings)).

The explorer and the editor are two views of the same screen, so going from one to the other closes nothing: your tabs, the cursor in each and any unsaved changes stay as they are.

Press Back in the editor to go to the explorer. Press Back in the explorer to leave the app, after the usual question if an open file has unsaved changes.

## The list

The path of the folder you are in is at the top, starting with **Phone storage**. Tap a step of the path to go to that folder.

- Folders come first, then files, each in alphabetical order. Capital letters do not count, and numbers are read as numbers, so `file2` comes before `file10`.
- Under each file name are its size and the date it was last changed.
- Tap a folder to go into it, or **..** at the top of the list to go up one level.
- Tap a file to open it in the editor, in a new tab. A file that is already open is shown instead of being opened twice.

The names use the font from **Settings > Appearance**, and the colors are those of the theme.

## The menu

Tap the three-bar button at the top left to open the menu. The strip of icons on its left edge has two options, **File explorer** and **Code editor**; see [Going to the editor and back](#going-to-the-editor-and-back). Next to it are the explorer's options:

- **Open** shows Android's file picker and opens the file you pick in the editor.
- **Open folder** asks Android for a folder, as in the editor (see [Files and folders](../02-editing/03-files-and-folders.md#working-with-a-folder)). If the folder is on the phone's own storage, the explorer goes to it as well.
- **Settings** opens the settings. It reads **Settings ●** when an update is waiting for you.

Tap outside the menu, or press Back, to close it.

## Going to the editor and back

In the menu, **Code editor** shows the editor. The menu stays open and its strip now has the editor's own icons, **Files** and **Search in files**, where the two options were. Tap outside it to close it.

Tap the **<** at the top of that strip to bring the two options back. **File explorer** then shows the explorer. In the explorer, **File explorer** does nothing, as you are already there; **Code editor** shows the editor again, and tapped in the editor it brings the editor's own icons back.

## Access to all files

On Android, an app may only read and write files in the folders on your phone's storage if you allow **All files access**. The explorer needs it to show those folders. It never asks when the app starts. The first time you open a folder that needs it, a message says why in one line and has a button to the settings page where you turn it on. When you come back, the folder opens.

If you tap **Not now**, nothing is lost: the explorer shows what it can reach without it, and a line under the path says that access is off, with **Allow** to ask again. Opening a file with **Open**, or opening a folder with **Open folder** in the editor, never needs it.

What the app can reach without it: the top level of **Phone storage**, and the folders Android gives the app for its own files (`Android/data/com.whatcode`, `Android/media/com.whatcode`).
