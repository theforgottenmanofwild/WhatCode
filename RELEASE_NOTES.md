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

- Six accent colors, chosen in **Settings > Appearance** ([`d3dccdd`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/d3dccddf5440ebdc4dbe42d9bb442037484df576))
- **True black** for the dark theme ([`d3dccdd`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/d3dccddf5440ebdc4dbe42d9bb442037484df576))
- Choose the editor font: **Monospace**, **Serif monospace** or **Sans-serif** ([`d3dccdd`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/d3dccddf5440ebdc4dbe42d9bb442037484df576))
- Set the editor font size and line height ([`d3dccdd`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/d3dccddf5440ebdc4dbe42d9bb442037484df576))
- **Compact**, **Normal** or **Comfortable** margins round the code ([`d3dccdd`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/d3dccddf5440ebdc4dbe42d9bb442037484df576))
- **Highlight the current line** turns on a soft background for the cursor's line ([`d3dccdd`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/d3dccddf5440ebdc4dbe42d9bb442037484df576))
- A live editor preview at the top of **Settings > Appearance** ([`d3dccdd`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/d3dccddf5440ebdc4dbe42d9bb442037484df576))
- A **Syntax colors** page with your own colors for keywords, strings, comments and more ([`d3dccdd`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/d3dccddf5440ebdc4dbe42d9bb442037484df576))
- Syntax colors are kept separately for the light and dark theme ([`d3dccdd`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/d3dccddf5440ebdc4dbe42d9bb442037484df576))
- Pick a color with sliders or a `#RRGGBB` code ([`d3dccdd`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/d3dccddf5440ebdc4dbe42d9bb442037484df576))
- A color that is hard to read is shown in a readable shade ([`d3dccdd`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/d3dccddf5440ebdc4dbe42d9bb442037484df576))
- **Return to default** on the **Appearance** page puts every choice back ([`d3dccdd`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/d3dccddf5440ebdc4dbe42d9bb442037484df576))
- An **Appearance** page in the website docs ([`d3dccdd`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/d3dccddf5440ebdc4dbe42d9bb442037484df576))

## 🚀 Improved

- Text on buttons and the status bar stays readable on every accent ([`d3dccdd`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/d3dccddf5440ebdc4dbe42d9bb442037484df576))

## ⚙️ Changed

- The editor's cursor and selection follow the accent color ([`d3dccdd`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/d3dccddf5440ebdc4dbe42d9bb442037484df576))

## 🧩 Bug fixes

Nothing to show here.

## 🧹 Removed

Nothing to show here.

## 🔧 Under the hood

- `./gradlew test` now runs, with JUnit added to the build ([`d3dccdd`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/d3dccddf5440ebdc4dbe42d9bb442037484df576))
- Tests check the contrast of every theme, accent and syntax color ([`d3dccdd`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/d3dccddf5440ebdc4dbe42d9bb442037484df576))
- Appearance choices live in `AppearanceSettings`, apart from the screens ([`d3dccdd`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/d3dccddf5440ebdc4dbe42d9bb442037484df576))
- Syntax colors are applied through `SyntaxScheme` instead of fixed values ([`d3dccdd`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/d3dccddf5440ebdc4dbe42d9bb442037484df576))
- Contrast and accent code lives in `Contrast` and `AccentColor` ([`d3dccdd`](https://github.com/theforgottenmanofwild/WhatCodeEditor/commit/d3dccddf5440ebdc4dbe42d9bb442037484df576))
