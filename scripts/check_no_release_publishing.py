#!/usr/bin/env python3
"""Fails when a GitHub Actions workflow would publish, change or delete a GitHub Release.

APK releases of Seema AI are made by hand by the owner. Workflows may build, test, lint, scan and keep
artifacts, but must not create, upload to, edit or delete releases, and must not ask for write access to
repository contents. Run: python3 scripts/check_no_release_publishing.py
"""
import pathlib
import re
import sys

FORBIDDEN = [
    (re.compile(r"\bgh\s+release\s+(create|upload|edit|delete|delete-asset)\b"), "gh release command"),
    (re.compile(r"\bcontents:\s*write\b"), "contents: write permission"),
    (re.compile(r"softprops/action-gh-release|actions/create-release|actions/upload-release-asset|ncipollo/release-action"),
     "release-publishing action"),
    (re.compile(r"/releases(/[0-9]+)?/assets|api\.github\.com/repos/[^\s]+/releases"), "Releases REST API call"),
]


def problems(root: pathlib.Path):
    found = []
    for wf in sorted((root / ".github" / "workflows").glob("*.y*ml")):
        for no, line in enumerate(wf.read_text(encoding="utf-8").splitlines(), 1):
            code = line.split("#", 1)[0]  # comments may mention releases
            for pattern, what in FORBIDDEN:
                if pattern.search(code):
                    found.append(f"{wf.relative_to(root)}:{no}: {what}: {line.strip()}")
    return found


def main() -> int:
    root = pathlib.Path(__file__).resolve().parents[1]
    found = problems(root)
    for f in found:
        print(f"::error::{f}")
    if found:
        print("Automatic release publishing is not allowed; the owner publishes APKs by hand.")
        return 1
    print("OK: no workflow publishes, edits or deletes GitHub Releases.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
