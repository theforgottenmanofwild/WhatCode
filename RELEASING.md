# Releasing

Two channels, both published as GitHub Releases and listed on the website:

| Channel | Workflow | Runs | Release |
| --- | --- | --- | --- |
| Nightly | `.github/workflows/nightly.yml` | on every push to `main` | one pre-release tagged `nightly`, deleted and recreated each time |
| Stable | `.github/workflows/release.yml` | by hand only | one release per version, tagged `vX.Y.Z`, marked "latest" |

Both release workflows build with Gradle (`./gradlew :app:assembleRelease`), passing the version and build number as `-PversionName` and `-PversionCode`. The release key reaches Gradle through the `KEYSTORE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` environment variables.

## What the in-app updater relies on

The app updates itself from these same releases (see `app/src/main/kotlin/com/whatcodeeditor/update/Updater.kt`), so keep these conventions:

- The repository is passed to the build as `-PgithubRepo=owner/name` and read by the app from its manifest.
- A **nightly** app reads the release tagged `nightly`. The build number comes from the release title, `Nightly X.Y.Z-nightly.N`, and is compared with the installed build number.
- A **stable** app reads the latest non-pre-release. The version comes from its `vX.Y.Z` tag and is compared with the installed version name.
- Each release carries one `.apk` and, next to it, a `.apk.sha256` file in `sha256sum` format. The app checks the download against it.
- The release description (Markdown) is what the app shows as the release notes. Plain Markdown is fine; the app renders headings, lists, bold, code and links.

The website (`site/`) is deployed by `.github/workflows/site.yml` whenever `site/` or `docs/` change. It reads the releases from the GitHub API in the visitor's browser, so publishing a release needs no site rebuild.

### Forums

The **Forums** pages show the repository's GitHub Discussions inside the site: categories, discussions, comments and replies, answers, polls, labels, reactions and people. GitHub only serves Discussions through its GraphQL API, which needs a token even for a public repository, so a visitor's browser cannot ask for them. Instead `npm run discussions` (`site/scripts/fetch-discussions.mjs`) copies them into `site/public/discussions.json` while the site is built, and the pages read that file.

- Turn on **Discussions** in the repository settings (Settings > General > Features). With it off, the Forums page says there is nothing to show.
- The workflow already has the permission it needs (`discussions: read`) and uses its own `GITHUB_TOKEN`; there is nothing to set up.
- It rebuilds the site when a discussion or comment is created, edited, deleted, labelled, pinned, locked or answered, and every six hours (reactions and upvotes send no event). A new reply shows on the site a minute or two after it is posted.
- Posting still happens on GitHub: the pages link to it ("Start a discussion", "Reply on GitHub"). A static site cannot write to GitHub.
- If GitHub cannot be reached while building, the build fails and the published site stays as it was; it never publishes an empty forum by mistake.
- Locally, `GH_TOKEN=... npm run discussions` fills in the file (a token that can read the repository, and a `github.com` `origin` remote or `VITE_GITHUB_REPO`). `discussions.json` is not committed.

## One-time setup

**1. Signing key.** Android only installs an update if it is signed with the same key as the installed app, so create one key and keep it.

```sh
keytool -genkeypair -keystore release.keystore -alias whatcodeeditor \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.keystore
```

Use the same password for the keystore and the key (PKCS12 keystores ignore a separate key password). Back up `release.keystore` and the password somewhere safe. If they are lost, every user has to uninstall before they can update.

Add four repository secrets (Settings > Secrets and variables > Actions):

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | output of the `base64` command above |
| `ANDROID_KEYSTORE_PASSWORD` | the keystore password |
| `ANDROID_KEY_ALIAS` | `whatcodeeditor` |
| `ANDROID_KEY_PASSWORD` | the same password |

Without these the workflows still run, but sign with the debug key and print a warning. Each CI run generates a new debug key, so such a build cannot update the previous one, and the in-app updater cannot install it either.

**2. GitHub Pages.** Settings > Pages > Source: **GitHub Actions**. The site is then at `https://<owner>.github.io/<repo>/`.

## Release notes

`RELEASE_NOTES.md` (repository root) holds the notes for the next stable release. **You write them by hand**; nothing fills them in from commits. It has these sections: **Newly added**, **Improved**, **Changed**, **Bug fixes**, **Removed** and **Under the hood**.

- Add bullet points under the sections that apply and leave the others empty; empty sections are left out of the release.
- Markdown works: **bold**, `code` and links. `@user`, `#123` and bare GitHub links become links in the app.
- `scripts/release-notes.py` is only a helper: `body --version X` prints the release description made from the file, and `reset` puts the empty template back.

The stable release uses this file as the release description, and the app shows the same text on its update screen and its **What's new** page, and the website lists it on its **Changelog** page. Nightly releases only say which commit they were built from.

## Publishing a stable release

1. Write the notes in `RELEASE_NOTES.md` on `main`.
2. Actions > **Stable release** > Run workflow, on `main`, and enter the version, for example `0.2.0`.
3. The workflow builds and publishes the release with your notes as its description (empty sections left out, and a link to the full changelog added). If the file is empty it warns and publishes a one-line placeholder.
4. It then resets `RELEASE_NOTES.md` to the empty template and pushes that to `main`. (If `main` is protected against pushes by the workflow, run `python3 scripts/release-notes.py reset` yourself.)
5. When it finishes, the release appears on the website's Stable card.
6. Edit `VERSION` to the next planned version, so nightly builds are labelled `0.3.0-nightly.N`.

The workflow stops if the version is not `X.Y.Z` or the tag already exists.

## Version numbers

- `VERSION` holds the base version for nightlies.
- The Android build number (`versionCode`) is the number of commits on the branch. It always rises, which is what lets nightlies and stable releases update each other.
- A stable build made from an older commit than the latest nightly has a lower build number. Android will not install it over that nightly, and users must uninstall first.

## Trying the site locally

```sh
cd site
npm install
cp .env.example .env     # set VITE_GITHUB_REPO=owner/repo
npm run dev
npm run smoke            # renders the pages with sample data
```
