# Getting started

WhatCode is a small code editor for Android. It opens and saves plain-text and source files, highlights common languages, and puts the symbols and shortcuts that are hard to use on a phone keyboard in two scrollable rows above it.

## Requirements

- Android 8.0 (API 26) or newer
- About 5 MB of free storage

The editor opens the files you pick in Android's picker, or open with the app, and the files you open in the [file explorer](./03-file-explorer.md). To show the folders on your phone's storage, the file explorer needs **All files access**. The app asks for it only the first time you open a folder that needs it, never when it starts, and the explorer still shows what it can reach if you say no. The app also asks for two permissions that only the updater uses: **Internet**, to look for and download new builds from GitHub, and **Install unknown apps**, to install a downloaded update. See [Updating the app](../03-the-app/02-updating.md).

## Install

1. Download the APK from the [download page](/download). Choose **Stable** unless you want the latest changes.
2. Open the downloaded file from your browser's downloads or your Files app.
3. If Android asks, allow your browser or Files app to **install unknown apps**.
4. Tap **Install**, then **Open**.

Builds are not published on Google Play, so Android shows an "unknown source" prompt the first time. The APK is signed; you can compare its SHA-256 checksum with the one on the download page before installing.

## Update

The app checks GitHub for a newer build of its own channel every time it opens, and asks before it downloads anything. You can also open **Settings** (in the file explorer's menu, or the editor's **⋮** menu at the top right), then **Update App**, and tap **Check now** at any time. See [Updating the app](../03-the-app/02-updating.md).

You can still update by hand: download the newer APK from the [download page](/download) and install it over the old one. Your files are untouched. The app keeps only a few settings, the list of files that were open (see [Tabs](../02-editing/06-tabs.md)) and, while an update is waiting to be installed, the downloaded APK.

Stable and nightly builds share one app ID and one signing key, so either can update the other. Android only installs an update with a higher build number, so a stable build you downloaded last week will not replace a nightly built yesterday. See [Release channels](../03-the-app/01-release-channels.md).

## Help and community

Ask questions, share ideas or just chat on the [Discord server](https://discord.gg/bJxwDj492x). Bugs and feature requests can also go in the Forums on the website.

## Next

- [File explorer](./03-file-explorer.md)
- [Using the editor](./02-using-the-editor.md)
- [The key rows](../02-editing/01-key-rows.md)
- [Find and replace](../02-editing/02-find-and-replace.md)
- [Files and folders](../02-editing/03-files-and-folders.md)
- [Languages and highlighting](../02-editing/04-languages.md)
- [Autosave](../02-editing/05-autosave.md)
- [Tabs](../02-editing/06-tabs.md)
- [Updating the app](../03-the-app/02-updating.md)
