# WhatCodeEditor

A lightweight code editor for Android, written in Kotlin.

Builds and documentation are on the project website (`site/`): a **stable**
release published by hand, and a **nightly** build from every commit to `main`.

## Structure
- `app/` — the Android app (Kotlin: `MainActivity.kt` the screen, `KeyBar.kt` key rows, `FindBar.kt` find and replace, `EditOps.kt` line tools, `CodeEditText.kt` the editor view, `Icons.kt` drawn icons, `Workspace.kt` folders, `SettingsActivity.kt` the settings page, `ReleaseActivity.kt` the update and What's new page, `Markdown.kt` for the release notes, `Highlighter.kt`, and the updater)
- `docs/` — user documentation, shown on the website and readable here
- `site/` — React website (Vite) for downloads and docs
- `.github/` — workflows: `nightly.yml` (every push to `main`), `release.yml` (stable, manual only), `site.yml`, and the shared `build-apk` action
- `RELEASE_NOTES.md` — the notes for the next stable release, written by hand and published as the release description
- `scripts/release-notes.py` — turns that file into the release description, and resets it after a release
- `VERSION` — base version for builds
- `RELEASING.md` — how releases, signing keys and the site are set up

## Build
Needs JDK 17 or newer and the Android SDK (platform 35).

```sh
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # signed with a debug key unless KEYSTORE is set (see RELEASING.md)
```
Override the version with `-PversionName=1.2.3 -PversionCode=42`.
