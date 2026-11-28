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
- A copy keeps the date of the original ([`9b09caa`](https://github.com/theforgottenmanofwild/WhatCode/commit/9b09caa74fda24f8fd927e4bb8690824df8ab215))
- A copied folder is numbered as a whole, as in `photos (2)` ([`9b09caa`](https://github.com/theforgottenmanofwild/WhatCode/commit/9b09caa74fda24f8fd927e4bb8690824df8ab215))
- Deleting a folder removes links inside it, never what they point to ([`9b09caa`](https://github.com/theforgottenmanofwild/WhatCode/commit/9b09caa74fda24f8fd927e4bb8690824df8ab215))
- A copy or move on hold follows the file if you rename it ([`a90c20e`](https://github.com/theforgottenmanofwild/WhatCode/commit/a90c20ecef4983ddc26019e8e600b1626cdbe47b))
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
- Only a file opened from another app (Open with, Share) starts in the editor; reopened tabs wait in the editor behind the explorer ([`7e67b40`](https://github.com/theforgottenmanofwild/WhatCode/commit/7e67b40c2102ccc1a378edc9430bd3e31ffbd22f)) ([`d820369`](https://github.com/theforgottenmanofwild/WhatCode/commit/d82036939179fb5945b2d93728a997c06e0a71c0))
- **Open folder** in the explorer asks whether to open the code editor now; **Not now** stays in the explorer ([`d820369`](https://github.com/theforgottenmanofwild/WhatCode/commit/d82036939179fb5945b2d93728a997c06e0a71c0))
- **Back** in the editor goes to the explorer, and in the explorer leaves the app ([`7e67b40`](https://github.com/theforgottenmanofwild/WhatCode/commit/7e67b40c2102ccc1a378edc9430bd3e31ffbd22f))

## 🧩 Bug fixes

- A file that isn't text, such as an image or a zip, is refused instead of opened and saved over with garbage ([`d945a44`](https://github.com/theforgottenmanofwild/WhatCode/commit/d945a44bcf5a58ece25ec8a0469f9f579299dae1))

## 🧹 Removed

Nothing to show here.

## 🔧 Under the hood

- Explorer rules for paths, sorting, access, find and files, with tests ([`804e5aa`](https://github.com/theforgottenmanofwild/WhatCode/commit/804e5aa77defae33b375e4244b65c587b929427c))
