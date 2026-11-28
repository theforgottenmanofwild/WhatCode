# APK files

Inspect `.apk` files, compare them with what is already installed, and install or share them from the file explorer.

## Tapping an APK

Tapping a `.apk` file in the file explorer opens a sheet with **APK info**, **Install** and **Share** (or goes straight to info or install, depending on **Settings > File explorer > APK files**).

## APK info

**APK info** reads the package without opening or installing it:

- App name, icon and package name.
- Version name and version code.
- Minimum, target and compile SDK levels.
- Native ABIs found under `lib/`.
- File size, plus SHA-256 and MD5 checksums.
- Signing certificate SHA-256 fingerprint, subject, issuer and validity dates.
- Requested permissions (with dangerous permissions marked) and listed activities, services, receivers and providers.

## Comparing before install

When the installed package is visible to WhatCode, **APK info** and the install prompt compare it with the APK:

- Whether this file is an **Update**, the **Same** version, or a **Downgrade**.
- A warning when the signing certificate differs from the installed app (updating in place would fail) or when installing a downgrade.

On Android 11 and newer, Android hides most other installed packages unless granted visibility; in that case, Android's installer checks conflicts and downgrades when you tap **Install**.

## Installing

**Install** uses the same `PackageInstaller` flow as WhatCode's own updater:

- If **Allow installing unknown apps** is not enabled for WhatCode yet, a prompt explains why in one line and opens Android's permission screen.
- When installing fails, the message names the cause (signature mismatch, downgrade, low storage, damaged package, blocked or cancelled).
- After a successful install, the explorer can ask whether to delete the `.apk` file, delete it automatically, or always keep it (see [File explorer settings](../03-the-app/05-file-explorer-settings.md#apk-files)).
