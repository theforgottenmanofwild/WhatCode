# Autosave

Autosave saves the file you are editing by itself, so you do not have to tap Save. It is off until you turn it on.

## Turning it on

Open **⋮ > Settings** and tap **Autosave** under **Code Editor**, then turn on **Autosave**.

A file is saved a little while after you stop typing. Each key you type starts the wait again, so the file is saved when you pause, not in the middle of a sentence. The dot before the file name goes away once it is saved, and nothing pops up.

## Settings

- **Save after you stop typing for** is how long to wait: 1, 2, 5, 10 or 30 seconds, or 1 or 5 minutes. The default is 5 seconds.
- **Also save when I leave the app** saves straight away, without waiting, when you switch to another app, open another screen of the app or close it. With several files open in [tabs](./06-tabs.md), it saves every tab that has unsaved changes and a file to save to, not only the one on screen. It is on by default, and it only applies while **Autosave** is on.
- **Return to default** turns autosave off, sets the wait to 5 seconds and turns on saving when you leave the app. It asks you to confirm first.

Every choice is saved at once.

## Good to know

- **A new file with no name is not autosaved.** Use **Save as** once to give it a name and a place, and autosave takes over from there.
- Autosave writes over the file each time, exactly as **Save** does. Undo still works in the editor, but the earlier version of the file on your device is replaced.
- Autosave never trims trailing whitespace, even with **Settings > Typing > Trim trailing whitespace on save** on. See [Using the editor](../01-basics/02-using-the-editor.md#writing-code).
- If a save fails, for example because the file was moved, a short message says so.
