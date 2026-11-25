# Files and folders

Opening, saving and closing files, and working with a whole folder.

## Working with files

**New** clears the editor and starts an untitled text file.

**Open** shows Android's file picker. Pick any file, and the editor reads it as text. The app asks Android to remember access, so the file can be saved again later.

**Save** writes the file back to where it came from. For a new file it behaves like **Save as**.

**Save as** asks where to save and what to call the file. The file name decides which language is used for highlighting.

**Close file** closes the current file and leaves an empty, untitled editor.

If you press **New**, **Open**, **Close file** or Back with unsaved changes, the editor asks whether to discard them.

## Working with a folder

**Open folder** asks Android for a folder. The app remembers it, so it is still open the next time you start, and it can only see that folder and what is inside it.

**Files** lists what is in the opened folder, folders first. Tap a folder to go into it, **..** to go back up, or a file to open it. The list stays where you left it until you open a different folder.

**Find file** searches the opened folder and every folder below it, however deep. Type part of a file name or path (several words must all match) and tap a result to open it. The first search reads the whole folder, and results appear while it is still going. Tap **Rescan** after files have been added or removed outside the app.

Files opened this way can be saved back with **Save**.

## The sidebar

Tap the three-bar button at the top left to slide the sidebar over the editor. A narrow strip of icons on its left edge switches between two panels. Tap outside the sidebar, or press Back, to close it.

**Files** shows the opened folder as a tree. Folders have a folder icon, text files a page with lines of text, and other files their own icon, such as **GO**, **JS**, **PY**, `<>` for HTML or `{}` for JSON. Tap a folder to open or close it, or a file to open it. The file that is open now is highlighted. **Refresh** reads the folder again. With no folder open, the panel has an **Open folder** button.

**Search in files** looks for text inside every file of the opened folder and the folders below it, and for file names too. Type what to look for and tap **Search** (or the keyboard's search key). The three toggles above the field are **aA** (match case), **a-z** (whole word) and **.\*** (regular expression), and they are remembered. They apply to file names as well as to text.

Results are grouped by file, with the matching lines under each file name, each with its line number and a short piece of the line around the match. Tap a file name to fold or unfold its lines, or tap a line to open the file with the match selected. A file whose name matches has a **File name matches** row that opens it, and shows **name** next to its count. A file with only a name match is listed with just that row.

The search runs in the background, so you can keep using the editor, and results appear while it is still going. While it runs the **Search** button reads **Stop**; tap it to stop and keep what was found so far. Starting a new search, or opening another folder, also stops the old one.

These are not searched: the folders `.git`, `.hg`, `.svn`, `node_modules`, `build`, `dist`, `out`, `target`, `.gradle`, `.idea`, `.next`, `.nuxt`, `__pycache__`, `.cache`, `.venv`, `venv`, `.dart_tool`, `Pods` and `DerivedData`, files larger than 1 MB, and files that are not text, such as images, archives and fonts (their names can still match). At most 1,000 matches are shown, and at most 100 lines of one file. When the limit is reached the status line says so; search for something more specific to see the rest.

## Opening files from other apps

WhatCodeEditor appears in the **Open with** list for files that Android reports as text. If a file manager does not offer it for a particular file, open the app and use **Open** instead.
