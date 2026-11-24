# Updating the app

WhatCodeEditor can find, download and install its own updates. It never downloads anything until you say so.

## How it works

The app knows whether it is a **stable** or a **nightly** build and only looks at that channel (see [Release channels](./01-release-channels.md)).

- When the app starts, it checks GitHub for a newer build, at most twice a day. If it finds one, it asks whether to download it. Choose **Later** and it will not ask about that version again at start-up.
- Open the **⋮** menu at the top right and choose **Update** to check whenever you like. The **⋮** turns blue and the entry reads **Update ●** when an update is found, or one is downloaded and waiting to be installed.
- You can turn the start-up check off with the checkbox in the Update panel. The manual check always works.

## Installing an update

1. Tap **Download** when the app offers an update. A progress bar shows the download, and **Cancel** stops it. The file is checked against the build's SHA-256 checksum.
2. Tap **Install**. The first time, Android asks you to allow this app to install updates: turn on **Allow from this source**, go back, and tap **Update > Install** again.
3. Confirm in Android's installer. When it finishes, open the app again.

## If the install is cancelled

The downloaded file is **kept until the update is confirmed**, meaning the new version is installed and running. So if you back out of Android's installer by accident, nothing is lost. Open **Update** and you will see the downloaded version with two buttons:

- **Install** starts the installer again from the file already on your phone, with no new download.
- **Delete** removes the file if you no longer want it. You can download it again later.

Once the new version is installed, the app deletes the downloaded file by itself, and says so the next time you open it.

## If the install fails

The panel shows what Android reported. The most common cause is a different signing key: an update must be signed with the same key as the installed app. If you installed a build that someone else compiled, uninstall it and install an official APK from the [download page](#/download).

## Privacy

The updater only talks to GitHub: `api.github.com` to find the newest release, and GitHub's download servers to fetch the APK. It sends no personal data and no identifier beyond what any web request includes, such as your IP address. The downloaded APK lives in the app's private storage, which other apps cannot read.
