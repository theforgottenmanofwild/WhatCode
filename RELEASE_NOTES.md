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

- **File explorer** as the first screen, with folders first and the path on top ([`7e67b40`](https://github.com/theforgottenmanofwild/WhatCode/commit/7e67b40c2102ccc1a378edc9430bd3e31ffbd22f), [`804e5aa`](https://github.com/theforgottenmanofwild/WhatCode/commit/804e5aa77defae33b375e4244b65c587b929427c))
- Tap a step of the path to jump to that folder ([`7e67b40`](https://github.com/theforgottenmanofwild/WhatCode/commit/7e67b40c2102ccc1a378edc9430bd3e31ffbd22f))
- Tapping a file in the explorer opens it in the editor ([`7e67b40`](https://github.com/theforgottenmanofwild/WhatCode/commit/7e67b40c2102ccc1a378edc9430bd3e31ffbd22f))
- Menu with **File explorer** and **Code editor** to switch screens ([`7e67b40`](https://github.com/theforgottenmanofwild/WhatCode/commit/7e67b40c2102ccc1a378edc9430bd3e31ffbd22f))
- **<** at the top of the editor's menu strip brings those two options back ([`7e67b40`](https://github.com/theforgottenmanofwild/WhatCode/commit/7e67b40c2102ccc1a378edc9430bd3e31ffbd22f))
- Open tabs and unsaved changes stay when switching screens ([`7e67b40`](https://github.com/theforgottenmanofwild/WhatCode/commit/7e67b40c2102ccc1a378edc9430bd3e31ffbd22f))
- **New file** and **New folder** on the explorer's **+** button ([`372859a`](https://github.com/theforgottenmanofwild/WhatCode/commit/372859a033c45610f775ffbc1ca4efc501b7a0dc), [`804e5aa`](https://github.com/theforgottenmanofwild/WhatCode/commit/804e5aa77defae33b375e4244b65c587b929427c))
- Round **+** button at the bottom right of the explorer opens **New folder** and **New file** ([`b3107f9`](https://github.com/theforgottenmanofwild/WhatCode/commit/b3107f95113b254e2a0be8fe480eb1080343a5a1))
- **Rename**, **Copy**, **Move** and **Delete** from the **⋮** on each row ([`372859a`](https://github.com/theforgottenmanofwild/WhatCode/commit/372859a033c45610f775ffbc1ca4efc501b7a0dc), [`804e5aa`](https://github.com/theforgottenmanofwild/WhatCode/commit/804e5aa77defae33b375e4244b65c587b929427c))
- **View** options in the explorer's menu: **Detailed**, **Compact** and **Grid** layouts, and **Hidden** to show or hide dotfiles ([`b3107f9`](https://github.com/theforgottenmanofwild/WhatCode/commit/b3107f95113b254e2a0be8fe480eb1080343a5a1))
- **Sort** options in the explorer's menu: **Name**, **Size**, **Date**, **Type** and **Desc**; folders stay first, and the choices are remembered ([`b3107f9`](https://github.com/theforgottenmanofwild/WhatCode/commit/b3107f95113b254e2a0be8fe480eb1080343a5a1))
- **Find file** in the explorer's menu searches names below the folder ([`372859a`](https://github.com/theforgottenmanofwild/WhatCode/commit/372859a033c45610f775ffbc1ca4efc501b7a0dc), [`804e5aa`](https://github.com/theforgottenmanofwild/WhatCode/commit/804e5aa77defae33b375e4244b65c587b929427c))
- Tabs follow files that are renamed or moved in the explorer ([`372859a`](https://github.com/theforgottenmanofwild/WhatCode/commit/372859a033c45610f775ffbc1ca4efc501b7a0dc), [`804e5aa`](https://github.com/theforgottenmanofwild/WhatCode/commit/804e5aa77defae33b375e4244b65c587b929427c))
- A deleted file's tab stays open as changes not saved yet ([`372859a`](https://github.com/theforgottenmanofwild/WhatCode/commit/372859a033c45610f775ffbc1ca4efc501b7a0dc))
- **All files access** asked only when a folder needs it, never at start ([`7e67b40`](https://github.com/theforgottenmanofwild/WhatCode/commit/7e67b40c2102ccc1a378edc9430bd3e31ffbd22f), [`804e5aa`](https://github.com/theforgottenmanofwild/WhatCode/commit/804e5aa77defae33b375e4244b65c587b929427c))

## 🚀 Improved

Nothing to show here.

## ⚙️ Changed

- The app starts on the file explorer instead of the editor ([`7e67b40`](https://github.com/theforgottenmanofwild/WhatCode/commit/7e67b40c2102ccc1a378edc9430bd3e31ffbd22f))
- Opening a file from another app, or reopening tabs, still starts in the editor ([`7e67b40`](https://github.com/theforgottenmanofwild/WhatCode/commit/7e67b40c2102ccc1a378edc9430bd3e31ffbd22f))
- **Back** in the editor goes to the explorer, and in the explorer leaves the app ([`7e67b40`](https://github.com/theforgottenmanofwild/WhatCode/commit/7e67b40c2102ccc1a378edc9430bd3e31ffbd22f))

## 🧩 Bug fixes

Nothing to show here.

## 🧹 Removed

Nothing to show here.

## 🔧 Under the hood

- Explorer rules for paths, sorting, access, find and files, with tests ([`804e5aa`](https://github.com/theforgottenmanofwild/WhatCode/commit/804e5aa77defae33b375e4244b65c587b929427c))
