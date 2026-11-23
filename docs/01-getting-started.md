# Getting started

WhatCodeEditor is a small code editor for Android. It opens and saves plain-text and source files, highlights common languages, and puts the symbols that are hard to type on a phone keyboard in a row above the status bar.

## Requirements

- Android 8.0 (API 26) or newer
- About 5 MB of free storage

The app asks for no permissions. It reads and writes only files you pick in Android's file picker or open with the app, so it cannot browse your storage.

## Install

1. Download the APK from the [download page](#/). Choose **Stable** unless you want the latest changes.
2. Open the downloaded file from your browser's downloads or your Files app.
3. If Android asks, allow your browser or Files app to **install unknown apps**.
4. Tap **Install**, then **Open**.

Builds are not published on Google Play, so Android shows an "unknown source" prompt the first time. The APK is signed; you can compare its SHA-256 checksum with the one on the download page before installing.

## Update

Download the newer APK and install it over the old one. Your files are untouched, because the app stores no files or settings of its own.

Stable and nightly builds share one app ID and one signing key, so either can update the other. Android only installs an update with a higher build number, so a stable build you downloaded last week will not replace a nightly built yesterday. See [Release channels](./04-release-channels.md).

## Next

- [Using the editor](./02-using-the-editor.md)
- [Languages and highlighting](./03-languages.md)
