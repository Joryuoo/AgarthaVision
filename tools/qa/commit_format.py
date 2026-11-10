"""Single source of truth for the commit subject format (C9 in docs/constraints.md).

Used by .husky/commit-msg (--file) and CI (--range). Stdlib only, Python 3.11+.
"""
import argparse
import re
import subprocess
import sys

TYPES = {
    "feat": "a new capability",
    "enhancements": "an improvement to something that already ships",
    "fix": "a defect repair",
    "security": "a vulnerability or hardening change",
    "docs": "documentation only",
    "ui": "visual or layout change",
    "ux": "interaction or flow change",
    "uiux": "both, when the change is not cleanly one",
    "refactor": "behaviour-preserving restructuring",
    "test": "test coverage only",
    "ci": "build, hooks, or pipeline",
    "chore": "dependencies and housekeeping",
}
PATTERN = re.compile(r"^\[(" + "|".join(TYPES) + r")\]\[[A-Za-z0-9_-]+\]\[[A-Za-z0-9_-]+\]: .+")
# Git generates these itself.
EXEMPT = ("Merge ", "Revert ", "fixup!", "squash!")
FORMAT = "[type][ClickUp-ID][Lastname]: Task title"
EXAMPLE = "[feat][86d4a6mpt][Ledon]: Add the settings screen"


def check(subject):
    subject = subject.rstrip("\r\n")
    return subject.startswith(EXEMPT) or bool(PATTERN.match(subject))


def format_help():
    lines = [f"  Required: {FORMAT}", f"  Example:  {EXAMPLE}", "", "  Types:"]
    lines += [f"    {name:<13} {desc}" for name, desc in TYPES.items()]
    return "\n".join(lines)


def help_text(subject):
    bar = "-" * 56
    return "\n".join(["Invalid commit message format.", bar, format_help(), "", "  Yours:", f"    {subject}", bar])


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--file", help="commit message file (checks the first line)")
    group.add_argument("--range", help="<base>..<head> revision range (checks every non-merge commit)")
    args = parser.parse_args(argv)
    if args.file:
        with open(args.file, encoding="utf-8", errors="replace") as stream:
            subject = stream.readline().rstrip("\r\n")
        if check(subject):
            return 0
        print(help_text(subject))
        return 1
    log = subprocess.run(["git", "log", "--no-merges", "--format=%H%x00%s", args.range],
                         capture_output=True, text=True, encoding="utf-8", errors="replace")
    if log.returncode != 0:
        print(log.stderr.strip(), file=sys.stderr)
        return 2
    bad = []
    for line in log.stdout.splitlines():
        sha, _, subject = line.partition("\0")
        if sha and not check(subject):
            bad.append((sha[:8], subject))
    if not bad:
        return 0
    print(f"{len(bad)} commit(s) with an invalid message format:")
    for sha, subject in bad:
        print(f"  {sha}  {subject}")
    print()
    print(format_help())
    return 1


if __name__ == "__main__":
    sys.exit(main())
