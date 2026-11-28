# File explorer

The file explorer is the first screen of the app: a list of the folders and files on your phone, where you pick the file to open in the editor.

## Starting the app

The app always starts on the file explorer, in **Phone storage** unless you pick another start folder in [the explorer's settings](../03-the-app/05-file-explorer-settings.md#start-and-navigation). It goes straight to the editor instead only when you open it with a file from another app (**Open with** or **Share**). The files that were open the last time you used it are brought back in the editor's tabs (see [Tabs](../02-editing/06-tabs.md#settings)), and wait there until you switch to the editor.

The explorer and the editor are two views of the same screen, so going from one to the other closes nothing: your tabs, the cursor in each and any unsaved changes stay as they are.

Press Back in the editor to go to the explorer. In the explorer, Back goes up one folder, and leaves the app from the top of **Phone storage** (or of `/`), after the usual question if an open file has unsaved changes. To leave from any folder instead, turn off **Back goes up a folder** in **Settings > File explorer > Start and navigation**.

## The list

The path of the folder you are in is at the top, starting with **Phone storage**. Tap a step of the path to go to that folder. Tap the last step, the folder you are in, to type a path instead: the path turns into a box, and **Go** on the keyboard goes there. A path that does not start with `/` is read from the folder you are in, and `..` goes up. A file's path opens the file. Back, or tapping elsewhere, closes the box.

Pull the top of the list down and let go to read the folder again.

- Folders come first, then files, each in alphabetical order unless you pick another order in [the menu](#the-menu). Capital letters do not count, and numbers are read as numbers, so `file2` comes before `file10`. All three can be changed in [the explorer's settings](../03-the-app/05-file-explorer-settings.md#sorting).
- Under each file name are its size and the date and time it was last changed. What the rows show can be changed in [the explorer's settings](../03-the-app/05-file-explorer-settings.md#view).
- Tap a folder to go into it, or **..** at the top of the list to go up one level.
- Tap a file to open it in the editor, in a new tab. A file that is already open is shown instead of being opened twice. A file that is not text, such as an image, a zip or an app, is not opened: a message says so, and the file is left as it is. Both can be changed in [the explorer's settings](../03-the-app/05-file-explorer-settings.md#taps): a text file can ask whether to open it in the editor or with another app, and a file that isn't text can open with another app.

The names use the font from **Settings > Appearance**, and the colors are those of the theme.

## The menu

Tap the three-bar button at the top left to open the menu. The strip of icons on its left edge has two options, **File explorer** and **Code editor**; see [Going to the editor and back](#going-to-the-editor-and-back). Next to it are the explorer's options:

- **View** sets how the list looks: **Detailed** (the name with its size and date), **Compact** (the name alone, in thinner rows) or **Grid** (tiles, with a press and hold for the menu of a file). **Hidden** shows or hides the files and folders whose names start with a dot; it is on until you turn it off.
- **Sort** sets the order: **Name**, **Size**, **Date** or **Type** (the ending of the name, such as `.kt`). **Desc** turns the order round, so Z comes first, and the newest and biggest do. Folders stay before files. The choices are kept for the next time you open the app, and are the same as on the explorer's settings pages.
- **Open** shows Android's file picker and opens the file you pick in the editor.
- **Open folder** asks Android for a folder, as in the editor (see [Files and folders](../02-editing/03-files-and-folders.md#working-with-a-folder)). If the folder is on the phone's own storage, the explorer goes to it as well. A box then asks **Open the code editor?** Tap **Open code editor** to go to the editor now, or **Not now** to stay in the explorer; the folder stays open in the editor's sidebar either way.
- **Find file** looks for files and folders by name; see [Finding a file](#finding-a-file).
- **Settings** opens the settings. It reads **Settings ●** when an update is waiting for you.

Tap outside the menu, or press Back, to close it.

## Making a file or a folder

Tap the round **+** at the bottom right. It opens two smaller buttons above it, **New folder** and **New file**. Each asks for a name and makes an empty folder or file in the folder you are in. Tap the **+** again (it has turned into an x) to close them without making anything.

## Working with files and folders

Tap the **⋮** at the end of a row, or press and hold the row, for its menu (the **⋮** can be hidden in **Settings > File explorer > Taps**):

- **Rename** asks for a new name. The same name again changes nothing. A name that is empty or has a `/` in it is explained under the field, and a name that is already taken is reported in a message.
- **Copy** and **Move** put the file or folder on hold. A line under the path says what is waiting, with **Paste here** and **Cancel**. Go to the folder you want, as far as you like, and tap **Paste here**. A folder is copied with everything in it, and a name that is already used there gets a number, as in `notes (2).txt` or, for a folder, `photos (2)`. A copy keeps the date of the original. If you rename what is on hold, it is still the one that gets pasted. A folder cannot be copied or moved into itself.
- **Delete** asks first, and deletes a folder with everything in it. It cannot be undone. A link inside the folder is removed, never what it points to.

If something goes wrong, for example a folder that can only be changed with access to all files, a message says what could not be done and the list is read again.

Open tabs keep working when you change their files here. Rename or move a file, or a folder with files in it, and the tabs that show them follow it to the new name. Delete a file and its tab stays, with its text, as changes that are not saved yet: **Save** asks where to put the file, and closing the tab asks first.

## Finding a file

**Find file** in the menu asks for part of a name. It searches the folder you are in and every folder below it, and lists what matches as you type, with the folder it is in. Several words match anything that has all of them in its name or in the names of the folders above it. Tap a result to open the file in the editor, or to go to the folder.

## Going to the editor and back

In the menu, **Code editor** shows the editor. The menu stays open and its strip now has the editor's own icons, **Files** and **Search in files**, where the two options were. Tap outside it to close it.

Tap the **<** at the top of that strip to bring the two options back. **File explorer** then shows the explorer. In the explorer, **File explorer** does nothing, as you are already there; **Code editor** shows the editor again, and tapped in the editor it brings the editor's own icons back.

## Access to all files

On Android, an app may only read and write files in the folders on your phone's storage if you allow **All files access**. The explorer needs it to show those folders. It never asks when the app starts. The first time you open a folder that needs it, a message says why in one line and has a button to the settings page where you turn it on. When you come back, the folder opens.

If you tap **Not now**, nothing is lost: the explorer shows what it can reach without it, and a line under the path says that access is off, with **Allow** to ask again. Opening a file with **Open**, or opening a folder with **Open folder** in the editor, never needs it.

What the app can reach without it: the top level of **Phone storage**, and the folders Android gives the app for its own files (`Android/data/com.whatcode`, `Android/media/com.whatcode`).
