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

Syntax highlighting has been rewritten. The editor now knows each language by name, colors far more of the code, and copes with much bigger files.

## ✨ Newly added

- Highlighting for **CSS**, **SCSS**, **Less**, **SQL**, **Markdown**, **Dockerfile**, **Makefile**, **Gradle**, **Go modules**, **Properties** and **JSON with comments** ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Each language has its own keyword list, so **TypeScript**, **Java**, **Go**, **Rust**, **C**, **C++**, **C#**, **Swift**, **Dart**, **PHP**, **Scala**, **Ruby**, **YAML**, **TOML**, **INI**, **XML** and **SVG** are read as themselves ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- More colors: purple for `if`, `for` and `return`, teal for types and classes, yellow for functions, annotations and macros, and light blue for object keys, JSON keys, YAML keys and HTML attributes ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Regular expressions in JavaScript and TypeScript are colored red ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- `<script>` and `<style>` in HTML files are colored as JavaScript and CSS ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Markdown headings, lists, quotes, links, bold text, inline code and code blocks are colored ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- The comment key uses `--` for SQL, `;` for INI and `/* */` for CSS ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Website preview of the editor shows the new colors, the sidebar button and both key rows ([`2a40c3f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/2a40c3f5e25189388e3f9a0207b31e962473ebea))

## 🚀 Improved

- Long files are colored on a background thread, so typing stays smooth ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Strings, template strings and comments that run over several lines are colored correctly ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- A string that is not closed stops at the end of its line instead of coloring the rest of the file ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Website docs describe every language, the colors and the limits ([`2a40c3f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/2a40c3f5e25189388e3f9a0207b31e962473ebea))

## ⚙️ Changed

- The status bar shows the real language, such as **JavaScript** or **YAML**, instead of **C-like**, **Markup** or **Shell/Config** ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Highlighting is used for files up to 1,000,000 characters, up from 150,000 ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- `.md` files are highlighted as Markdown instead of shown as plain text ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))

## 🧩 Bug fixes

- Large files, such as a 248,000-character `main.js`, had no colors at all ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Ordinary names such as `data`, `open`, `init`, `go` and `match` were colored as keywords in every language; each language now has only its own keywords ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- Names after a dot, such as `obj.default`, were colored as keywords ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))

## 🧹 Removed




## 🔧 Under the hood

- Highlighter rewritten: one reader per kind of language (code, HTML, CSS, YAML and INI, Markdown) and a table of languages ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
- The comment key takes its style from the language definition ([`6b6d17f`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/6b6d17f88df16b1a358033655c4cdd1590bf038a))
