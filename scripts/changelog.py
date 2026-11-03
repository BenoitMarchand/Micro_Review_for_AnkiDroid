"""Release notes from CHANGELOG.md.

Usage: python scripts/changelog.py [notes|release VERSION]

- "notes": prints the release notes of the coming release: the "## Unreleased" section, then the same lines in a
  block ready to paste into the Play Console (<en-US>, 500 characters max: a warning beyond). Used by release.yml
  for the GitHub release, for a release candidate as for the final release.
- "release VERSION": the final release, the "## Unreleased" section becomes "## VERSION (date)" and a new empty
  "## Unreleased" goes above it.
"""
import datetime
import re
import sys
from pathlib import Path

CHANGELOG = Path(__file__).resolve().parent.parent / "CHANGELOG.md"
UNRELEASED = "## Unreleased"
PLAY_MAX_CHARS = 500


def unreleased_lines(text):
    """The lines of the Unreleased section, without the blank lines around them."""
    match = re.search(rf"^{UNRELEASED}\n(.*?)(?=^## |\Z)", text, re.MULTILINE | re.DOTALL)
    if not match:
        sys.exit(f"No '{UNRELEASED}' section in {CHANGELOG.name}.")
    return match.group(1).strip()


def play_text(lines):
    """The lines as Play Console release notes: one entry per bullet, wrapped lines joined."""
    entries = []
    for line in lines.splitlines():
        if line.startswith("- "):
            entries.append("• " + line[2:].strip())
        elif line.strip() and entries:
            entries[-1] += " " + line.strip()
    return "\n".join(entries)


def notes(text):
    lines = unreleased_lines(text)
    if not lines:
        print(f"::warning::{CHANGELOG.name} has nothing under '{UNRELEASED}': the release has no user-facing notes.",
              file=sys.stderr)
        return ""
    play = play_text(lines)
    if len(play) > PLAY_MAX_CHARS:
        print(f"::warning::The Play Store release notes are {len(play)} characters long (max {PLAY_MAX_CHARS}).",
              file=sys.stderr)
    return (f"## What's new\n\n{lines}\n\n"
            f"<details><summary>Play Store release notes ({len(play)}/{PLAY_MAX_CHARS} characters)</summary>\n\n"
            f"```\n<en-US>\n{play}\n</en-US>\n```\n\n</details>\n")


def release(text, version):
    date = datetime.date.today().isoformat()
    return text.replace(f"{UNRELEASED}\n", f"{UNRELEASED}\n\n## {version} ({date})\n", 1)


def main():
    action = sys.argv[1] if len(sys.argv) > 1 else ""
    text = CHANGELOG.read_text(encoding="utf-8")
    if action == "notes":
        sys.stdout.reconfigure(encoding="utf-8")  # the bullets, also on a Windows console
        sys.stdout.write(notes(text))
    elif action == "release" and len(sys.argv) == 3:
        # Nothing user-facing: no empty section for the version.
        if unreleased_lines(text):
            CHANGELOG.write_text(release(text, sys.argv[2]), encoding="utf-8")
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
