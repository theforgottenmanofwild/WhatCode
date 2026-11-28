# WhatCode

A lightweight dev tool app for Android, written in Kotlin.

- **File explorer**: browse your phone's folders; make, rename, copy, move,
  delete and find files; list, compact or grid view, with sorting; its own
  settings for the view, the order, the start folder and what taps do.
- **Code editor**: tabs, syntax highlighting for more than 30 languages and file
  types, find and replace, search in files, and extra key rows above the keyboard.

Builds and documentation are on the project website (`site/`): a **stable**
release published by hand, and a **nightly** build from every commit to `main`.

## Community
Questions, ideas and help: join the [Discord server](https://discord.gg/bJxwDj492x).
Want to contribute? Read [CONTRIBUTING.md](CONTRIBUTING.md).

## Structure
```
app/src/main/kotlin/com/whatcode/
  MainActivity.kt      the editor screen and the file explorer screen it switches with: tabs, menu, opening and saving files
  EditorTab.kt         what an open file keeps while another tab is showing
  TabStrip.kt          the tab bar
  EditorStatus.kt      the status bar
  AutoIndentFilter.kt  copies the indentation onto a new line
  ErrorLog.kt          writes down failures that must not stop the app
  editor/              the text view and what it does with text
    CodeEditText.kt      the editor view (undo, shortcuts, smart typing hooks)
    TypingLogic.kt       auto-closing brackets and smart indentation (plain logic, unit tested)
    IndentDefault.kt     the indentation chosen for files that show no pattern (plain logic, unit tested)
    BracketMatch.kt      the bracket next to the cursor and its partner, for the highlight (plain logic, unit tested)
    TagMatch.kt          closing tags, renaming a tag pair and the pair to highlight in HTML, XML and SVG (plain logic, unit tested)
    AttributeQuotes.kt   the quotes of attribute values in HTML, XML and SVG (plain logic, unit tested)
    MatchJump.kt         where the "jump to matching" key goes, for tags and brackets (plain logic, unit tested)
    MarkupElements.kt    where the elements of HTML, XML and SVG start and end (plain logic, unit tested)
    SelectionExpander.kt the steps of the "expand selection" key (plain logic, unit tested)
    TagWrap.kt           wrapping in a tag and removing the tag around the cursor (plain logic, unit tested)
    Highlighter.kt       syntax highlighting and the language list
    Lang.kt              a language the editor knows
    highlight/           the language list and the scanners that find what to color
    LineGutter.kt        draws the line numbers
    WhitespacePainter.kt draws the marks for spaces and tabs
    ViewClip.kt          the part of the editor that is on screen
    EditOps.kt           line tools: duplicate, move, join, comment, indent (plain logic, unit tested)
    EditHistory.kt       undo and redo steps and when edits join into one step (plain logic, unit tested)
    LineCounter.kt       line and column for the status bar without counting a big file each time
    LineNumbers.kt       which rows the line number gutter numbers and how wide it is (plain logic, unit tested)
    EditorAppearance.kt  puts the font, colors, margins and line numbers on the editor
    SyntaxScheme.kt      the syntax colors of a theme
    LineEndings.kt       LF, CRLF or mixed line endings for the status bar (plain logic, unit tested)
    TrimWhitespace.kt    trims the ends of lines when saving, keeping every line ending (plain logic, unit tested)
    WhitespaceMarks.kt   which spaces and tabs get a mark (plain logic, unit tested)
    FindBar.kt           find and replace
  keys/                the extra keys above the keyboard
    KeyBar.kt            the key rows
    KeyEditor.kt         the saved key layout and the quick key dialogs
    KeyEditorActivity.kt the Key Editor page
  explorer/            the file explorer
    FileExplorer.kt      the file explorer screen: the path, the list, opening a file in the editor, the row menu, Find file
    Sidebar.kt           the sliding file browser
    FilesPanel.kt        the sidebar's file list
    SearchPanel.kt       the sidebar's search
    ExplorerRows.kt      the rows of the explorer's list
    ExplorerAddButton.kt the + button
    ExplorerOptionsPanel.kt the View and Sort options
    NameDialog.kt        asks for a file or folder name
    FindFileDialog.kt    Find file
    ExplorerOptions.kt   how the explorer lists things (layout, what rows show, hidden files, order), saved for the next start
    ExplorerSettings.kt  where the explorer starts, how it moves between folders and what taps do
    PullToRefresh.kt     pull the list down to read the folder again
    FileIcons.kt         file type icons and colors
    logic/               the explorer's rules, all plain logic and unit tested
      ExplorerPaths.kt     path rules: the path bar, going up, where a renamed path goes
      ExplorerSort.kt      the order of the explorer's list and the short size label
      ExplorerFormat.kt    the dates, item counts and names shown in the explorer's rows
      ExplorerAccess.kt    which folders need the access to all files
      ExplorerFind.kt      Find file in the explorer: names below a folder
  search/              search in files
    FileSearch.kt        Find file / search in folder
    SearchLogic.kt       which files and folders are searched, patterns and matching lines (plain logic, unit tested)
  files/               files, folders and what was open
    Workspace.kt         the open folder and its file list
    FileOps.kt           new file and folder, rename, delete, copy and move, and the rules for names (plain logic, unit tested)
    TextFile.kt          reads and writes file text without changing any line ending (plain logic, unit tested)
    SharedFiles.kt       hands one file, read only, to the app picked in Open with
    DocumentIo.kt        reads and writes the opened documents by address
    StorageAccess.kt     the access to all files: whether it is given, and the settings page for it
    SessionStore.kt      remembers the open files for the next start
  settings/            the Settings screens and the saved choices
    SettingsActivity.kt  the settings hub (every settings screen is built on ui/SettingsPage.kt)
    SettingsHubPage.kt   a settings page that lists other pages (the hub, File explorer)
    explorer/            Settings > File explorer and its pages (View, Sorting, Start and navigation, Taps)
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
    ThemedPopup.kt       pop-up menus drawn in the current theme's colors
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
