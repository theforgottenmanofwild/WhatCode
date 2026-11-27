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

## ✨ Newly added

- **Show line numbers** switch in **Settings > Appearance** ([`81f3ab4`](https://github.com/theforgottenmanofwild/WhatCode/commit/81f3ab46a60a99aa8307ee5287e474303e522cfb))
- **Show whitespace** switch in **Settings > Appearance** marks spaces and tabs ([`76e35c1`](https://github.com/theforgottenmanofwild/WhatCode/commit/76e35c1e101fdac338c23189b0b5fccfa7808974))
- **Trim trailing whitespace on save** switch in **Settings > Typing** ([`76e35c1`](https://github.com/theforgottenmanofwild/WhatCode/commit/76e35c1e101fdac338c23189b0b5fccfa7808974))
- Quotes added after `=` in HTML, XML and SVG attributes ([`7337d26`](https://github.com/theforgottenmanofwild/WhatCode/commit/7337d2603b433a101ceb26cd0bb7bb2f901faa38))
- Typing an attribute's closing quote steps over it ([`7337d26`](https://github.com/theforgottenmanofwild/WhatCode/commit/7337d2603b433a101ceb26cd0bb7bb2f901faa38))
- **Quote attribute values** switch in **Settings > Typing** ([`7337d26`](https://github.com/theforgottenmanofwild/WhatCode/commit/7337d2603b433a101ceb26cd0bb7bb2f901faa38))
- **Expand selection** key grows the selection up to the whole file ([`9719273`](https://github.com/theforgottenmanofwild/WhatCode/commit/97192739e823da005e63a513af9b079b15b817c1))
- **SHFT** with **Expand selection** shrinks it back step by step ([`9719273`](https://github.com/theforgottenmanofwild/WhatCode/commit/97192739e823da005e63a513af9b079b15b817c1))
- **Ctrl+E** expands the selection, **Ctrl+Shift+E** shrinks it ([`9719273`](https://github.com/theforgottenmanofwild/WhatCode/commit/97192739e823da005e63a513af9b079b15b817c1))
- **Wrap in tag** key puts a tag around a selection or word ([`f001b0f`](https://github.com/theforgottenmanofwild/WhatCode/commit/f001b0f411fdedb0044377f0fcb5a6c69d399fbd))
- **SHFT** with **Wrap in tag** removes the surrounding tag pair ([`f001b0f`](https://github.com/theforgottenmanofwild/WhatCode/commit/f001b0f411fdedb0044377f0fcb5a6c69d399fbd))
- **Ctrl+T** wraps in a tag, **Ctrl+Shift+T** removes it ([`f001b0f`](https://github.com/theforgottenmanofwild/WhatCode/commit/f001b0f411fdedb0044377f0fcb5a6c69d399fbd))
- Closing tags added as you type in HTML, XML and SVG ([`cc9b59a`](https://github.com/theforgottenmanofwild/WhatCode/commit/cc9b59ae324c8025c3177d9bdbee90ab5d6890f0))
- Matching tags highlighted with the cursor in either one ([`cc9b59a`](https://github.com/theforgottenmanofwild/WhatCode/commit/cc9b59ae324c8025c3177d9bdbee90ab5d6890f0))
- Mouse or stylus hover highlights a tag and its partner ([`cc9b59a`](https://github.com/theforgottenmanofwild/WhatCode/commit/cc9b59ae324c8025c3177d9bdbee90ab5d6890f0))
- Enter between two tags opens an indented line between them ([`01688f4`](https://github.com/theforgottenmanofwild/WhatCode/commit/01688f4780877662906149a137c1c2e528e06b5f))
- Renaming a tag also renames its partner ([`01688f4`](https://github.com/theforgottenmanofwild/WhatCode/commit/01688f4780877662906149a137c1c2e528e06b5f))
- Undo takes back a renamed tag pair in one step ([`01688f4`](https://github.com/theforgottenmanofwild/WhatCode/commit/01688f4780877662906149a137c1c2e528e06b5f))
- **Jump to matching** key moves the cursor to the partner tag or bracket ([`01688f4`](https://github.com/theforgottenmanofwild/WhatCode/commit/01688f4780877662906149a137c1c2e528e06b5f))
- **Ctrl+M** jumps to the matching tag or bracket ([`01688f4`](https://github.com/theforgottenmanofwild/WhatCode/commit/01688f4780877662906149a137c1c2e528e06b5f))
- **Import and export** page in **Settings** saves all settings as JSON ([`246cd49`](https://github.com/theforgottenmanofwild/WhatCode/commit/246cd49d17a34e5779d1144b279d72a10fb29066))
- Settings restored from a file or the clipboard, after every value is checked ([`246cd49`](https://github.com/theforgottenmanofwild/WhatCode/commit/246cd49d17a34e5779d1144b279d72a10fb29066))
- Switches for closing, splitting and renaming tags in **Settings > Typing** ([`0722c75`](https://github.com/theforgottenmanofwild/WhatCode/commit/0722c75b3aeb58b33f38d547beef250df8f7daf9))
- Switch for highlighting matching brackets and tags in **Settings > Typing** ([`0722c75`](https://github.com/theforgottenmanofwild/WhatCode/commit/0722c75b3aeb58b33f38d547beef250df8f7daf9))
- Switches for whole-line copy and cut and plain paste in **Settings > Typing** ([`0722c75`](https://github.com/theforgottenmanofwild/WhatCode/commit/0722c75b3aeb58b33f38d547beef250df8f7daf9))
- Switches for keyboard shortcuts, the extra keys and the status bar in **Settings > Typing** ([`0722c75`](https://github.com/theforgottenmanofwild/WhatCode/commit/0722c75b3aeb58b33f38d547beef250df8f7daf9))
- **Syntax highlighting** switch in **Settings > Appearance** ([`0722c75`](https://github.com/theforgottenmanofwild/WhatCode/commit/0722c75b3aeb58b33f38d547beef250df8f7daf9))

## 🚀 Improved

Nothing to show here.

## ⚙️ Changed

Nothing to show here.

## 🧩 Bug fixes

- Stray `<` and `>` in HTML text no longer highlighted as a bracket pair ([`015eb40`](https://github.com/theforgottenmanofwild/WhatCode/commit/015eb40b560d671917b6326ec211ec3bdfbfa079))

## 🧹 Removed

Nothing to show here.

## 🔧 Under the hood

Nothing to show here.
