#!/usr/bin/env python3
"""Helper for RELEASE_NOTES.md, which is written by hand.

  release-notes.py body --version X   print the release description made from the file
  release-notes.py reset              put the empty template back (done after a release is published)

The "Stable release" workflow publishes the output of `body` as the GitHub release description,
and the app shows that same text on its update screen. Nothing here writes notes for you.
"""
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
NOTES = ROOT / "RELEASE_NOTES.md"

SECTIONS = [
    "\u2728 Newly added",
    "\U0001F680 Improved",
    "\u2699\ufe0f Changed",
    "\U0001F9E9 Bug fixes",
    "\U0001F9F9 Removed",
    "\U0001F527 Under the hood",
]

INTRO = """<!--
  Release notes for the next stable release. Write them by hand.

  - Add bullet points under the sections that apply, for example:
      - Added a Time Format setting (Settings > Appearance) (@name)
    Markdown works: **bold**, `code`, [links](https://example.com);
    @user, #123 and bare GitHub links become links in the app.
  - You can write a sentence or two above the first heading; it is kept as an introduction.
  - Sections you leave empty are left out of the release.
  - When the "Stable release" workflow runs, everything below becomes the GitHub release
    description, and the app shows the same text on its update screen.
  - After publishing, this file is reset to the empty template.
-->
"""


def template():
    return INTRO + "".join(f"\n## {heading}\n\n\n" for heading in SECTIONS)


def git(*args):
    return subprocess.run(["git", *args], cwd=ROOT, check=True, capture_output=True, text=True).stdout


def repo_slug():
    slug = os.environ.get("GITHUB_REPOSITORY")
    if slug:
        return slug
    try:
        url = git("remote", "get-url", "origin").strip()
    except Exception:
        return None
    m = re.search(r"github\.com[:/]([\w.-]+/[\w.-]+?)(?:\.git)?$", url)
    return m.group(1) if m else None


def previous_stable_tag(version):
    for tag in git("tag", "--list", "v[0-9]*", "--sort=-v:refname").split():
        if re.fullmatch(r"v\d+(\.\d+)*", tag) and tag != f"v{version}":
            return tag
    return None


def cmd_body(version):
    text = re.sub(r"<!--.*?-->", "", NOTES.read_text(encoding="utf-8"), flags=re.S)
    # keep a short introduction (text before the first heading), and only the sections
    # that have something under their heading
    kept = []
    for chunk in re.split(r"(?m)^(?=## )", text):
        if not chunk.startswith("## "):
            if chunk.strip():
                kept.append(chunk.strip())
            continue
        lines = chunk.strip().splitlines()
        if any(l.strip() for l in lines[1:]):
            kept.append("\n".join(lines).strip())
    body = re.sub(r"\n{3,}", "\n\n", "\n\n".join(kept))
    if not body:
        print("::warning::RELEASE_NOTES.md has no notes written in it.", file=sys.stderr)
        body = "No release notes were written for this release."
    slug = repo_slug()
    prev = previous_stable_tag(version) if version else None
    if slug and prev and version:
        body += f"\n\n**Full changelog:** https://github.com/{slug}/compare/{prev}...v{version}"
    print(body)


def cmd_reset():
    NOTES.write_text(template(), encoding="utf-8")
    print("RELEASE_NOTES.md reset")


def main(argv):
    if not argv or argv[0] not in ("body", "reset"):
        print(__doc__)
        return 2
    if argv[0] == "reset":
        cmd_reset()
    else:
        cmd_body(argv[argv.index("--version") + 1] if "--version" in argv else "")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
