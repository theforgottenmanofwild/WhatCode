# Release channels

There are two kinds of build.

## Stable

A stable build is published by hand when the maintainers consider it ready. It has a version number such as `0.2.0`, release notes, and stays available after newer versions appear. Choose stable if you just want the editor to work.

## Nightly

A nightly build is made automatically from every change to the main branch, so it contains the newest features and fixes but has had no manual testing. There is always exactly one nightly build, and each new one replaces the last. Its version looks like `0.0.1-nightly.57`, where the last number counts the changes made so far.

Despite the name, nightlies are not made on a timer. Expect one within a few minutes of each change.

## Which should I use?

| | Stable | Nightly |
| --- | --- | --- |
| Updated | When a maintainer publishes | After every change |
| Tested by hand | Yes | No |
| Old versions kept | Yes | No, only the latest |
| Best for | Everyday use | Trying fixes, reporting bugs |

## Switching and updating

Both channels use the same app ID, so installing one replaces the other. Android only accepts an update with a higher build number, and a nightly build always has a higher number than the stable release before it. That means you can install a nightly over stable at any time, but going from nightly back to stable needs one extra step: uninstall the app, then install the stable APK. The app stores no files or settings of its own, so your files are unaffected.

Both channels must be signed with the same key. If an update fails with "App not installed", the signing key has changed; uninstall and install again.

## Checking a download

Each build has a SHA-256 checksum on the download page. To verify on a computer, run `sha256sum WhatCodeEditor.apk` and compare the result.
