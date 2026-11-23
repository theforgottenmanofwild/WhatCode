# Getting started

WhatCodeEditor is a small code editor for Android. It opens and saves plain-text and source files, highlights common languages, and puts the symbols that are hard to type on a phone keyboard in a row above the status bar.

## Requirements

- Android 8.0 (API 26) or newer
- About 5 MB of free storage

The app reads and writes only files you pick in Android's file picker or open with the app, so it cannot browse your storage. It asks for two permissions, both used only by the updater: **Internet**, to look for and download new builds from GitHub, and **Install unknown apps**, to install a downloaded update. See [Updating the app](./05-updating.md).

## Install

1. Download the APK from the [download page](#/). Choose **Stable** unless you want the latest changes.
2. Open the downloaded file from your browser's downloads or your Files app.
3. If Android asks, allow your browser or Files app to **install unknown apps**.
4. Tap **Install**, then **Open**.

Builds are not published on Google Play, so Android shows an "unknown source" prompt the first time. The APK is signed; you can compare its SHA-256 checksum with the one on the download page before installing.

## Update

The app checks GitHub for a newer build of its own channel when it starts, and asks before it downloads anything. You can also tap **Update** in the action bar at any time. See [Updating the app](./05-updating.md).

You can still update by hand: download the newer APK from the [download page](#/) and install it over the old one. Your files are untouched. The app keeps only a few settings and, while an update is waiting to be installed, the downloaded APK.

Stable and nightly builds share one app ID and one signing key, so either can update the other. Android only installs an update with a higher build number, so a stable build you downloaded last week will not replace a nightly built yesterday. See [Release channels](./04-release-channels.md).

## Next

- [Using the editor](./02-using-the-editor.md)
- [Languages and highlighting](./03-languages.md)
- [Updating the app](./05-updating.md)
