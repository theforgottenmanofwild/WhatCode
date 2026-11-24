<!--
  Release notes for the next stable release. Write them by hand.

  - Add bullet points under the sections that apply, for example:
      - Added a Time Format setting (Settings > Appearance) (@name)
    Markdown works: **bold**, `code`, [links](https://example.com);
    @user, #123 and bare GitHub links become links in the app.
  - You can write a sentence or two above the first heading; it is kept as an introduction.
  - Sections you leave empty are left out of the release.
  - When the "Stable release" workflow runs, everything below becomes the GitHub release
    description, and the app shows the same text on its update screen.
  - After publishing, this file is reset to the empty template.
-->

Typing is smarter, the key rows follow the language, and files open in tabs. Syntax highlighting was rewritten too.

## ✨ Newly added

- Brackets and quotes close themselves as you type ([`f82f926`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/f82f92642e8a19a52f4d1a3e073cb5d261db8cdf))
- Enter indents after an opening bracket, or a colon in Python and YAML ([`f82f926`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/f82f92642e8a19a52f4d1a3e073cb5d261db8cdf))
- Key rows change with the file's language, such as `:=` for Go ([`f82f926`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/f82f92642e8a19a52f4d1a3e073cb5d261db8cdf))
- Edit each language's keys in **Settings > Code Editor > Key Editor** ([`f82f926`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/f82f92642e8a19a52f4d1a3e073cb5d261db8cdf))
- Open files get tabs, with a dot on unsaved ones ([`f82f926`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/f82f92642e8a19a52f4d1a3e073cb5d261db8cdf))
- Long-press a tab to close it, the others or all ([`f82f926`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/f82f92642e8a19a52f4d1a3e073cb5d261db8cdf))
- **Ctrl+W** closes a tab and **Ctrl+Tab** switches tabs ([`f82f926`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/f82f92642e8a19a52f4d1a3e073cb5d261db8cdf))
- **Typing** and **Tabs** pages in **Settings > Code Editor** ([`f82f926`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/f82f92642e8a19a52f4d1a3e073cb5d261db8cdf))
- The extra keys hide while a physical keyboard is connected ([`f82f926`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/f82f92642e8a19a52f4d1a3e073cb5d261db8cdf))
- Highlighting for **CSS**, **SCSS**, **Less**, **SQL** and **Markdown** ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Highlighting for **Dockerfile**, **Makefile**, **Gradle**, **Go modules** and **Properties** ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Highlighting for **JSON with comments** ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- The app reopens the files you had open, with the cursor where you left it (**Settings > Code Editor > Tabs > Reopen last files**)
- Files made with **Save as** stay reachable after the app is closed
- A **Discord** link on the About page and the website
- Each language has its own keywords, so **Go**, **Rust**, **Java** and others read right ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- More colors for control words, types, functions, annotations and keys ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Regular expressions in JavaScript and TypeScript are colored red ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- `<script>` and `<style>` in HTML are colored as JavaScript and CSS ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Markdown headings, lists, links and code blocks are colored ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- The comment key uses `--` for SQL, `;` for INI and `/* */` for CSS ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Website preview shows the new colors, sidebar button and key rows ([`2a40c3f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/2a40c3f5e25189388e3f9a0207b31e962473ebea))
- **Light**, **Dark** and **System** color modes, chosen in **Settings > Appearance** ([`917d791`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/917d791f1303f157f5be36d088edd2be7f7d7fd3))
- System follows your device's light or dark setting ([`917d791`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/917d791f1303f157f5be36d088edd2be7f7d7fd3))
- Code colors have a light version that stays easy to read on a bright editor ([`917d791`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/917d791f1303f157f5be36d088edd2be7f7d7fd3))

## 🚀 Improved

- Long files are colored in the background, so typing stays smooth ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Strings and comments that run over several lines are colored correctly ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- An unclosed string stops at the end of its line ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Each tab keeps its own cursor, scroll position and undo history ([`f82f926`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/f82f92642e8a19a52f4d1a3e073cb5d261db8cdf))
- Website docs describe every language and color ([`2a40c3f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/2a40c3f5e25189388e3f9a0207b31e962473ebea))

## ⚙️ Changed

- The status bar shows the real language, such as **JavaScript** or **YAML** ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Highlighting works up to 1,000,000 characters, up from 150,000 ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- `.md` files are highlighted as Markdown ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Opening a file adds a tab; turn this off in **Settings > Code Editor > Tabs** ([`f82f926`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/f82f92642e8a19a52f4d1a3e073cb5d261db8cdf))
- **Close file** closes the tab and offers to save it ([`f82f926`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/f82f92642e8a19a52f4d1a3e073cb5d261db8cdf))
- **Edit keys** changes the keys of the open file's language ([`f82f926`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/f82f92642e8a19a52f4d1a3e073cb5d261db8cdf))
- Leaving the app asks about unsaved changes in every tab ([`f82f926`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/f82f92642e8a19a52f4d1a3e073cb5d261db8cdf))
- The app has a new look: the editor, sidebar, key row, find bar, settings, release and update screens share one design ([`917d791`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/917d791f1303f157f5be36d088edd2be7f7d7fd3))
- The app used to be dark only; it now follows your device's setting by default ([`917d791`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/917d791f1303f157f5be36d088edd2be7f7d7fd3))
- Settings pages are grouped, each with a short description ([`917d791`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/917d791f1303f157f5be36d088edd2be7f7d7fd3))

## 🧩 Bug fixes

- Large files had no colors at all ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Names like `data`, `open` and `go` were colored as keywords everywhere ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Names after a dot, such as `obj.default`, were colored as keywords ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Opening or saving a file no longer crashes if it can't be opened ([`b009898`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/b00989847e0a04fa73dc53fd6bb8db3a494338fc))
- Files too large to read show a message instead of closing the app ([`b009898`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/b00989847e0a04fa73dc53fd6bb8db3a494338fc))
- Save and open errors say what went wrong, never just "null" ([`b009898`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/b00989847e0a04fa73dc53fd6bb8db3a494338fc))
- Discarding changes on exit no longer saves them anyway when autosave is on ([`b009898`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/b00989847e0a04fa73dc53fd6bb8db3a494338fc))
- Switching dark mode, font size or language no longer resets the open file ([`b009898`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/b00989847e0a04fa73dc53fd6bb8db3a494338fc))

## 🧹 Removed

Nothing to show here.

## 🔧 Under the hood

- Highlighter rewritten with one reader per kind of language ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- The comment key takes its style from the language definition ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Typing rules live in `TypingLogic`, apart from the editor view ([`f82f926`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/f82f92642e8a19a52f4d1a3e073cb5d261db8cdf))
- Settings pages with switches share one base class ([`f82f926`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/f82f92642e8a19a52f4d1a3e073cb5d261db8cdf))
- Waiting highlight and autosave work is cancelled when the editor closes ([`b009898`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/b00989847e0a04fa73dc53fd6bb8db3a494338fc))
- Unit tests cover the highlighter and the typing rules ([`b009898`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/b00989847e0a04fa73dc53fd6bb8db3a494338fc))
- Shared colors and drawing helpers live in `AppTheme` ([`917d791`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/917d791f1303f157f5be36d088edd2be7f7d7fd3))
