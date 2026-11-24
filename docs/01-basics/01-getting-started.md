# Getting started

WhatCodeEditor is a small code editor for Android. It opens and saves plain-text and source files, highlights common languages, and puts the symbols and shortcuts that are hard to use on a phone keyboard in two scrollable rows above it.

## Requirements

- Android 8.0 (API 26) or newer
- About 5 MB of free storage

The app reads and writes only the files and the one folder you pick in Android's picker, or open with the app, so it cannot browse the rest of your storage. It asks for two permissions, both used only by the updater: **Internet**, to look for and download new builds from GitHub, and **Install unknown apps**, to install a downloaded update. See [Updating the app](../03-the-app/02-updating.md).

## Install

1. Download the APK from the [download page](#/download). Choose **Stable** unless you want the latest changes.
2. Open the downloaded file from your browser's downloads or your Files app.
3. If Android asks, allow your browser or Files app to **install unknown apps**.
4. Tap **Install**, then **Open**.

Builds are not published on Google Play, so Android shows an "unknown source" prompt the first time. The APK is signed; you can compare its SHA-256 checksum with the one on the download page before installing.

## Update

The app checks GitHub for a newer build of its own channel every time it opens, and asks before it downloads anything. You can also open the **⋮** menu at the top right, choose **Settings** and tap **Check now** at any time. See [Updating the app](../03-the-app/02-updating.md).

You can still update by hand: download the newer APK from the [download page](#/download) and install it over the old one. Your files are untouched. The app keeps only a few settings and, while an update is waiting to be installed, the downloaded APK.

Stable and nightly builds share one app ID and one signing key, so either can update the other. Android only installs an update with a higher build number, so a stable build you downloaded last week will not replace a nightly built yesterday. See [Release channels](../03-the-app/01-release-channels.md).

## Next

- [Using the editor](./02-using-the-editor.md)
- [The key rows](../02-editing/01-key-rows.md)
- [Find and replace](../02-editing/02-find-and-replace.md)
- [Files and folders](../02-editing/03-files-and-folders.md)
- [Languages and highlighting](../02-editing/04-languages.md)
- [Updating the app](../03-the-app/02-updating.md)
