#!/usr/bin/env python3
"""Tests for scripts/audit-watch-strings.py.

The audit is the guard that stops a label from rendering as "Rhythm and…". A
guard nobody checks is a guard that quietly stops working, so its classification
rules and its non-vacuity are asserted here.

Usage: scripts/test-audit-watch-strings.py
"""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SCRIPT = REPO / "scripts" / "audit-watch-strings.py"

failures: list[str] = []


def check(label: str, condition: bool, detail: str = "") -> None:
    if condition:
        print(f"ok   {label}")
    else:
        print(f"FAIL {label}{': ' + detail if detail else ''}")
        failures.append(label)


def load_audit():
    spec = importlib.util.spec_from_file_location("audit_watch_strings", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def main() -> int:
    if not SCRIPT.exists():
        print(f"error: {SCRIPT} not found", file=sys.stderr)
        return 2

    audit = load_audit()

    # Classification: the budget a string gets depends only on its surface.
    check("quarter-row labels classified",
          audit.surface_of("stress_window_dawn") == "quarter_row")
    check("titles classified as titles",
          audit.surface_of("hr_zones_title") == "title")
    check("a title whose name mentions an explanation is still a title",
          audit.surface_of("temp_delta_explanation_title") == "title",
          f"got {audit.surface_of('temp_delta_explanation_title')}")
    check("paragraph strings classified as body",
          audit.surface_of("vitals_insight_stable") == "body")
    check("plain labels fall through to label",
          audit.surface_of("avg_resting_hr") == "label")

    # Budgets must stay ordered: a quarter row is narrower than a full row.
    check("quarter-row budget is the tightest",
          audit.BUDGETS["quarter_row"] < audit.BUDGETS["label"] <= audit.BUDGETS["title"])
    check("body surfaces are effectively unbudgeted",
          audit.BUDGETS["body"] > 1000)

    # Non-vacuity: the audit must actually find constrained texts in this tree,
    # otherwise every future run would pass for the wrong reason.
    constrained = audit.find_constrained_texts()
    check("the audit sees constrained texts in this repo",
          len(constrained) > 0, f"found {len(constrained)}")
    every_named = all(name for _, name, _ in constrained)
    check("every constrained text names a string resource", every_named)

    strings = audit.load_strings()
    check("both locales parsed", set(strings.get("no_data", {})) == {"EN", "ES"},
          f"locales: {sorted(strings.get('no_data', {}))}")

    if failures:
        print(f"\n{len(failures)} check(s) failed", file=sys.stderr)
        return 1
    print("\nall watch-string audit checks pass")
    return 0


if __name__ == "__main__":
    sys.exit(main())
