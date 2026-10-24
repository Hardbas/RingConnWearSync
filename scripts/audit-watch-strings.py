#!/usr/bin/env python3
"""Audit watch-surface strings against the room their layout actually leaves.

A Wear OS card is 192dp wide and a card body leaves about 164dp after padding.
At the 10sp caption size used for micro-labels that is roughly 28 characters; a
quarter-row label gets about 7. The screen cannot measure text at build time, so
this script uses a character budget per surface as a proxy.

What it flags, and why:

  * A string used by a `maxLines = 1` Text that is longer than the budget for
    that surface. That is the defect this exists to catch: curated copy that can
    only render as "…".
  * Ellipsis is legitimate for dynamic text (strings containing a format
    placeholder). Those are reported as INFO, not as failures.

Exit codes: 0 clean, 1 offenders found, 2 usage or parse problem.

Usage: scripts/audit-watch-strings.py [--verbose]
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
WEAR_SRC = REPO / "wear" / "src" / "main" / "java"
STRINGS = [
    REPO / "wear" / "src" / "main" / "res" / "values" / "strings.xml",
    REPO / "wear" / "src" / "main" / "res" / "values-es" / "strings.xml",
]

# Characters that fit, at 10sp, in the width the surface gives the text.
BUDGETS = {
    "quarter_row": 8,   # one of four labels sharing a row
    "label": 26,        # a full card row
    "title": 26,        # a card title
    "body": 10_000,     # multi-line paragraph surfaces: not budgeted here
}

BODY_MARKERS = ("_desc", "_note", "_explanation", "_insight", "_body")


def load_strings() -> dict[str, dict[str, str]]:
    strings: dict[str, dict[str, str]] = {}
    for path in STRINGS:
        if not path.exists():
            continue
        locale = "ES" if "values-es" in str(path) else "EN"
        text = path.read_text(encoding="utf-8")
        for match in re.finditer(r'<string name="([^"]+)">(.*?)</string>', text, re.S):
            strings.setdefault(match.group(1), {})[locale] = match.group(2)
    return strings


def surface_of(name: str) -> str:
    # Titles win over body markers: `temp_delta_explanation_title` is a heading,
    # not a paragraph, even though its name mentions an explanation.
    if name.startswith("stress_window_"):
        return "quarter_row"
    if name.endswith("_title"):
        return "title"
    if any(marker in name for marker in BODY_MARKERS):
        return "body"
    return "label"


def find_constrained_texts() -> list[tuple[str, str, int]]:
    """Return (file, string_name, line) for every maxLines = 1 Text with a resource."""
    found = []
    for path in sorted(WEAR_SRC.rglob("*.kt")):
        lines = path.read_text(encoding="utf-8").splitlines()
        for index, line in enumerate(lines):
            if "maxLines = 1" not in line:
                continue
            # The Text that owns this modifier sits within a few lines either way.
            window = lines[max(0, index - 12) : index + 6]
            for candidate in window:
                match = re.search(r"R\.string\.([a-z0-9_]+)", candidate)
                if match:
                    found.append((str(path.relative_to(REPO)), match.group(1), index + 1))
                    break
    return found


def main() -> int:
    verbose = "--verbose" in sys.argv
    if not WEAR_SRC.exists():
        print(f"error: {WEAR_SRC} not found", file=sys.stderr)
        return 2

    strings = load_strings()
    if not strings:
        print("error: no strings parsed; check the resource paths", file=sys.stderr)
        return 2

    failures: list[str] = []
    infos: list[str] = []

    for source_file, name, line in find_constrained_texts():
        surface = surface_of(name)
        if surface == "body":
            failures.append(
                f"{source_file}:{line}  {name}\n"
                f"    paragraph copy is constrained to one line; give it more lines "
                f"or move it to a body area"
            )
            continue

        budget = BUDGETS[surface]
        for locale, text in strings.get(name, {}).items():
            length = len(text)
            if length <= budget:
                continue
            if "%" in text:
                infos.append(
                    f"{source_file}:{line}  {name} [{locale}] {length} chars "
                    f"(budget {budget}) — dynamic text, ellipsis allowed"
                )
            else:
                failures.append(
                    f"{source_file}:{line}  {name} [{locale}] {length} chars "
                    f"(budget {budget} for {surface})"
                )

    for info in infos:
        print(f"INFO {info}")

    if failures:
        print(f"\n{len(failures)} string(s) cannot render as authored:\n")
        for failure in failures:
            print(f"FAIL {failure}")
        print(
            "\nshorten the copy for that surface, widen it to more lines, or move it "
            "to a body area"
        )
        return 1

    print(f"ok: {len(find_constrained_texts())} constrained texts within budget")
    if verbose:
        print("budgets:", BUDGETS)
    return 0


if __name__ == "__main__":
    sys.exit(main())
