# WhatCode

A lightweight code editor for Android, written in Kotlin.

Builds and documentation are on the project website (`site/`): a **stable**
release published by hand, and a **nightly** build from every commit to `main`.

## Community
Questions, ideas and help: join the [Discord server](https://discord.gg/bJxwDj492x).
Want to contribute? Read [CONTRIBUTING.md](CONTRIBUTING.md).

## Structure
```
app/src/main/kotlin/com/whatcode/
  MainActivity.kt      the editor screen: tabs, menu, opening and saving files
  editor/              the text view and what it does with text
    CodeEditText.kt      the editor view (undo, shortcuts, smart typing hooks)
    TypingLogic.kt       auto-closing brackets and smart indentation (plain logic, unit tested)
    IndentDefault.kt     the indentation chosen for files that show no pattern (plain logic, unit tested)
    BracketMatch.kt      the bracket next to the cursor and its partner, for the highlight (plain logic, unit tested)
    TagMatch.kt          closing tags, renaming a tag pair and the pair to highlight in HTML, XML and SVG (plain logic, unit tested)
    AttributeQuotes.kt   the quotes of attribute values in HTML, XML and SVG (plain logic, unit tested)
    MatchJump.kt         where the "jump to matching" key goes, for tags and brackets (plain logic, unit tested)
    Highlighter.kt       syntax highlighting and the language list
    EditOps.kt           line tools: duplicate, move, join, comment, indent (plain logic, unit tested)
    EditHistory.kt       undo and redo steps and when edits join into one step (plain logic, unit tested)
    LineCounter.kt       line and column for the status bar without counting a big file each time
    LineNumbers.kt       which rows the line number gutter numbers and how wide it is (plain logic, unit tested)
    EditorAppearance.kt  puts the font, colors, margins and line numbers on the editor
    SyntaxScheme.kt      the syntax colors of a theme
    LineEndings.kt       LF, CRLF or mixed line endings for the status bar (plain logic, unit tested)
    FindBar.kt           find and replace
  keys/                the extra keys above the keyboard
    KeyBar.kt            the key rows
    KeyEditor.kt         the saved key layout and the quick key dialogs
    KeyEditorActivity.kt the Key Editor page
  files/               files, folders and what was open
    Workspace.kt         the open folder and its file list
    Sidebar.kt           the sliding file browser
    FileSearch.kt        Find file / search in folder
    SearchLogic.kt       which files and folders are searched, patterns and matching lines (plain logic, unit tested)
    TextFile.kt          reads and writes file text without changing any line ending (plain logic, unit tested)
    FileIcons.kt         file type icons and colors
    SessionStore.kt      remembers the open files for the next start
  settings/            the Settings screens and the saved choices
    SettingsActivity.kt  the settings hub (SettingsPage.kt is the base for its sub-pages)
    AppearanceActivity.kt, SyntaxColorsActivity.kt, TypingActivity.kt, TabsActivity.kt, AutosaveActivity.kt
    BackupActivity.kt    the Import and export page
    SettingsBackup.kt    what a settings backup holds and how it is read and checked (plain logic, unit tested)
    SettingsBackupStore.kt   reads and writes the saved settings for a backup
    EditorSettings.kt, Autosave.kt, AppearanceSettings.kt   the saved choices
    SettingsGlyphs.kt    the small pictures on the settings rows
  update/              in-app updates and the About page
    Updater.kt           reads GitHub releases and downloads builds
    UpdateController.kt, UpdateAppActivity.kt, ReleaseActivity.kt, Receivers.kt
    Markdown.kt          shows release notes
  ui/                  shared look
    AppTheme.kt          colors, light/dark mode and drawing helpers
    Accents.kt, Contrast.kt   accent colors and the readability checks
    Icons.kt             the drawn icons
app/src/test/            unit tests, in the same folders as the code they test
docs/                  user documentation, shown on the website and readable here
site/                  React website (Vite): downloads, docs, Forums and Source
.github/               workflows: nightly.yml (every push to main), release.yml (stable, manual),
                       site.yml, and the shared build-apk action
scripts/release-notes.py   turns RELEASE_NOTES.md into the release description
RELEASE_NOTES.md       the notes for the next stable release, written by hand
RELEASING.md           how releases, signing keys and the site are set up
VERSION                base version for builds
```

## Build
Needs JDK 17 or newer and the Android SDK (platform 35).

```sh
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # signed with a debug key unless KEYSTORE is set (see RELEASING.md)
```
Override the version with `-PversionName=1.2.3 -PversionCode=42`.
