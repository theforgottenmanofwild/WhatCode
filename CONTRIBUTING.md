# Contributing to WhatCode

Thanks for helping! Come say hello on the [Discord server](https://discord.gg/bJxwDj492x) if you want to ask
something first or talk an idea through before you start.

## Where things are

The app's code lives in `app/src/main/kotlin/com/whatcode/`, one folder per area. The full list is in the
[README](README.md#structure). The short version:

| You want to change...                          | Look in      |
| ---------------------------------------------- | ------------ |
| typing, brackets, indentation, highlighting    | `editor/`    |
| the extra keys above the keyboard              | `keys/`      |
| explorer, folders, sidebar, reopening files    | `files/`     |
| a Settings screen or a saved setting           | `settings/`  |
| updates, the About page, release notes display | `update/`    |
| colors, light and dark mode, icons             | `ui/`        |
| the main editor screen, tabs, saving           | `MainActivity.kt` |
| a help page                                    | `docs/` (see `docs/README.md`) |
| the website                                    | `site/`      |

A new file goes in the folder of the area it belongs to, with the matching `package` line
(`package com.whatcode.editor` for a file in `editor/`). A new screen must also be listed in
`app/src/main/AndroidManifest.xml`, using its folder, for example `.settings.TabsActivity`.

## Build and test

Needs JDK 17 or newer and the Android SDK (platform 35).

```sh
./gradlew assembleDebug   # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew test            # runs the unit tests in app/src/test
```

Rules that can be tested without a device (typing, highlighting) belong in plain Kotlin classes with a test next to
them, as `TypingLogic` and `Highlighter` do.

## Pull requests

- Keep a change focused on one thing.
- Add a bullet under the right heading in `RELEASE_NOTES.md` for anything a user would notice.
- Describe what you changed and how you checked it.
