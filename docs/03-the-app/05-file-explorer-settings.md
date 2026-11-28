# File explorer settings

The file explorer has its own settings: how the list looks, its order, where it starts, how you move between folders and what a tap does.

## Opening them

Open **Settings** (from the explorer's menu, or **⋮ > Settings** in the editor) and tap **File explorer** under **File explorer**. It has four pages. Every choice is saved as soon as you make it, and the explorer shows it when you go back to it; nothing needs a restart. Each page has a **Return to default** button at the bottom that puts that page's choices back as they were when the app was installed, after asking.

The **View** and **Sort** choices in [the explorer's menu](../01-basics/03-file-explorer.md#the-menu) and on these pages are the same settings: change one in either place and the other shows it.

## View

- **Layout**: **Detailed** (the name with a line under it), **Compact** (the name alone, in thinner rows) or **Grid** (tiles).
- **Row height**: **Roomy**, **Normal** or **Dense**, for the Detailed and Compact layouts.
- **Tile size**: **Small**, **Medium** or **Large**, for the Grid layout. Bigger tiles mean fewer in a row.
- **Show the size** and **Show the date** choose what is in the line under a file's name in the Detailed layout.
- **Show how many items a folder holds** puts the count, such as `12 items`, under each folder's name. It is off at first, because each folder in the list has to be read for it, which can take a moment in a folder that holds many folders.
- **Show file extensions**: off shows `notes.txt` as `notes`. Folders, and names that start with a dot such as `.gitignore`, are always shown in full.
- **Show file icons**: off shows the same plain icon for every file, instead of one per type.
- **Dates**: **How long ago** (`Just now`, `5 min ago`, `3 h ago`, `Yesterday, 14:05`, `4 days ago`, and the date for anything older than a week) or **Date and time** (`12 Mar 2026, 14:05`).
- **Time**: **As the phone is set**, **12-hour** (`2:05 PM`) or **24-hour** (`14:05`).
- **Sizes**: **1 KB is 1024 bytes** (as at first) or **1 kB is 1000 bytes** (as the phone's own storage settings count).

## Sorting

- **Sort by**: **Name**, **Size**, **Date** or **Type** (the ending of the name, such as `.kt`). What the chosen order cannot tell apart is put in order by name.
- **Direction**: **Ascending** (A to Z, oldest and smallest first) or **Descending** (Z to A, newest and biggest first).
- **Folders first**: on keeps folders above files whichever way the list is turned; off mixes them in.
- **Read numbers as numbers**: on puts `file2` before `file10`; off reads digits one at a time, so `file10` comes first.
- **Ignore capital letters**: on puts `apple`, `Banana` and `cherry` in that order; off puts names that start with a capital first.
- **Show hidden files**: files and folders whose names start with a dot. It is the same switch as **Hidden** in the menu.

## Start and navigation

- **Start in**: where the explorer opens when the app starts. **Phone storage** (as at first), **The last folder** it showed, or **A folder I choose**. For the last one, type the folder's full path in the box (it starts with `/`, for example `/storage/emulated/0/Projects`) and tap **Use this folder**, or tap **Use the last folder shown**. A path that is not a folder is not accepted. If the folder is gone when the app starts, the explorer opens **Phone storage** instead.
- **Type a path in the path bar**: tapping the last step of the path, the folder you are in, turns the path into a box to type where to go. See [The list](../01-basics/03-file-explorer.md#the-list).
- **Pull down to refresh**: pulling the top of the list down and letting go reads the folder again.
- **Back goes up a folder**: on, Back goes up one folder and leaves the app only from the top of the path. Off, Back leaves the app from any folder.

## Taps

- **Show the ⋮ button on rows**: off hides the button at the end of each row. Pressing and holding a row (or a tile) still opens its menu.
- **Tapping a text file**: **Open it in the code editor** (as at first), or **Ask what to do**, which offers **In the code editor** and **With another app**.
- **Tapping a file that isn't text**, such as an image, a zip or an app: **Say that it isn't text** (as at first), or **Open it with another app**, which shows Android's list of the apps that can open it.

When a file goes to another app, that app is given the right to read that one file, and nothing else. It cannot change it. If no app on the phone can open it, a message says so.

## In a backup

All of these settings are in a settings backup (see [Import and export](04-import-and-export.md)), including the start folder you typed. The folder the explorer showed last is not, as it belongs to this phone.
