# Updating the app

WhatCode can find, download and install its own updates. It never downloads anything until you say so.

## How it works

The app knows whether it is a **stable** or a **nightly** build and only looks at that channel (see [Release channels](./01-release-channels.md)).

- Every time the app opens, it checks GitHub for a newer build. If it finds one, it opens a full-screen **Update available** page showing the new version and its release notes (what was added, improved and fixed; links in the notes can be tapped). Choose **Later** and it shows the page again the next time the app opens. If you come back to the app after being away for more than ten minutes, it checks again.
- Open **Settings** (in the file explorer's menu, or the **⋮** menu at the top right of the editor), then tap **Update App** under **About App**. The page shows the installed version and what was found, and has **Check now** to check whenever you like, and **View update** to reopen the update page. The editor's **⋮** turns blue and the entry reads **Settings ●** (in the explorer's menu too) when an update is found, or one is downloaded and waiting to be installed; **Update App** gets the same dot.
- You can turn the automatic check off with **Check for updates when the app opens** on the **Update App** page. **Check now** always works.
- **About**, also under **About App** in Settings, opens the release notes of the version you have installed, with buttons for the project's GitHub page, its releases, and the website and docs, and one for the Discord server. The GitHub and website buttons only appear if their pages can be reached; the Discord button is always there.
- The release notes of every stable version are also listed on the website's [Changelog](/changelog) page.

## Installing an update

1. Tap **Download** on the update page. A progress bar shows the download, and **Cancel** stops it. Leaving the page also stops it. The file is checked against the build's SHA-256 checksum.
2. Tap **Install**. The first time, Android asks you to allow this app to install updates: turn on **Allow from this source**, go back, and tap **Install** again.
3. Confirm in Android's installer. When it finishes, open the app again.

## If the install is cancelled

The downloaded file is **kept until the update is confirmed**, meaning the new version is installed and running. So if you back out of Android's installer by accident, nothing is lost. Open **Settings > Update App** (or let the app show the update page the next time it opens) and you will see the downloaded version with these buttons:

- **Install** starts the installer again from the file already on your phone, with no new download.
- **Delete** removes the file if you no longer want it. You can download it again later.

Once the new version is installed, the app deletes the downloaded file by itself, and says so the next time you open it.

## If the install fails

**Update App** shows what Android reported. The most common cause is a different signing key: an update must be signed with the same key as the installed app. If you installed a build that someone else compiled, uninstall it and install an official APK from the [download page](/download).

## Privacy

The updater only talks to GitHub: `api.github.com` to find the newest release, and GitHub's download servers to fetch the APK. It sends no personal data and no identifier beyond what any web request includes, such as your IP address. The downloaded APK lives in the app's private storage, which other apps cannot read.
