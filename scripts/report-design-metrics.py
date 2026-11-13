#!/usr/bin/env python3
"""How much of the Android UI is typed in by hand instead of taken from the theme and strings.xml (AATD-86).

    scripts/report-design-metrics.py              # print the counts
    scripts/report-design-metrics.py --check      # fail if a count rose above its budget (predeploy)
    scripts/report-design-metrics.py --update     # lower the budgets to today's counts
    scripts/report-design-metrics.py --json       # the counts alone, as JSON
        [--root <checkout>] [--budget <budget.json>]

This ports astrid-web's scripts/report-design-metrics.ts (PR #700). Each count is a place where a
value that has a shared home was typed in instead: a colour, a size, or a sentence. The rules are in
astrid-web docs/specs/DESIGN_SYSTEM.md, and the register is in astrid-core design/README.md. Kotlin
under ui/ is counted, but not ui/theme, which is where those values belong. Comments don't count:
they are prose about the code.

The counts only go down. A change that needs one more should have used a token, AstridTheme or a
string resource. `--update` never raises a budget, so raising one means editing the JSON and giving
the reason in the commit message.

The totals are per metric, not per file. What matters is the direction, and a table per file would
be edited by nearly every change.
"""
import json
import os
import re
import sys

BUDGET_FILE = "scripts/design-metrics.budget.json"
UI_ROOT = "app/src/main/java/com/gracefultools/astrid/ui"
EXEMPT = re.compile(r"^theme/")

NAMED = "Red|Blue|Green|Black|White|Gray|LightGray|DarkGray|Yellow|Cyan|Magenta"
NUMBER = r"(?<![\w.])\d+(?:\.\d+)?"
# A sentence a person reads: a capitalised word and at least one more ("Close the sheet",
# "Sign out"). Keys ("astridModel"), addresses and templates that start with a value are not.
SENTENCE = re.compile(r"^[A-Z][a-z']*(?:[ ,.!?:;…–—-]+\S+)+")
STRING = re.compile(r'"((?:[^"\\\n]|\\.)*)"')

# (id, what it counts, what to use instead, pattern over code with comments removed)
METRICS = [
    ("colorLiterals", "Color(0x…) and other colour literals",
     "a colour in AstridColors (ui/theme), read through AstridTheme.colors",
     re.compile(r"\bColor\(\s*(?:0x|\d|red\s*=)")),
    ("namedColors", "Compose's named colours (Color.Red, Color.White, …)",
     "an AstridTheme colour; Color.Transparent and Color.Unspecified are not colour choices and are fine",
     re.compile(rf"\bColor\.(?:{NAMED})\b")),
    ("dpLiterals", "literal .dp sizes",
     "the spacing, radius and size tokens (ui/theme)",
     re.compile(rf"{NUMBER}\.dp\b")),
    ("roundedCorners", "RoundedCornerShape(N.dp)",
     "a radius token (ui/theme)",
     re.compile(rf"\bRoundedCornerShape\(\s*{NUMBER}\.dp")),
    ("spLiterals", "literal .sp type sizes",
     "MaterialTheme.typography / the type tokens",
     re.compile(rf"{NUMBER}\.sp\b")),
    ("hardcodedCopy", "English sentences typed into UI code (every occurrence)",
     "stringResource(R.string.…), with the text in res/values*/strings.xml",
     "copy"),
    ("uniqueHardcodedStrings", "distinct English sentences typed into UI code",
     "stringResource(R.string.…); a sentence used twice is one resource",
     "copy-distinct"),
]


def without_comments(source):
    """The code with // and /* */ comments removed. String literals are kept whole, so the // in "https://" stays."""
    out, i, n = [], 0, len(source)
    while i < n:
        c = source[i]
        if source.startswith('"""', i):
            end = source.find('"""', i + 3)
            end = n if end == -1 else end + 3
            out.append(source[i:end]); i = end
        elif c == '"':
            j = i + 1
            while j < n and source[j] not in '"\n':
                j += 2 if source[j] == "\\" else 1
            out.append(source[i:j + 1]); i = j + 1
        elif c == "'":
            # A char literal: '"' would otherwise open a string.
            m = re.match(r"'(?:\\.|[^'\\])'", source[i:])
            step = len(m.group(0)) if m else 1
            out.append(source[i:i + step]); i += step
        elif source.startswith("//", i):
            end = source.find("\n", i)
            i = n if end == -1 else end
        elif source.startswith("/*", i):
            end = source.find("*/", i + 2)
            i = n if end == -1 else end + 2
            out.append(" ")
        else:
            out.append(c); i += 1
    return "".join(out)


def sources(root):
    """(path under ui/, code without comments) for each Kotlin file outside ui/theme."""
    base = os.path.join(root, UI_ROOT)
    for folder, dirs, files in os.walk(base):
        dirs.sort()
        for name in sorted(files):
            if not name.endswith(".kt"):
                continue
            path = os.path.join(folder, name)
            rel = os.path.relpath(path, base).replace(os.sep, "/")
            if EXEMPT.match(rel):
                continue
            with open(path, encoding="utf-8") as f:
                yield rel, without_comments(f.read())


def measure(root):
    code = [text for _, text in sources(root)]
    copy = [s for text in code for s in STRING.findall(text) if SENTENCE.match(s)]
    counts = {}
    for metric_id, _, _, pattern in METRICS:
        if pattern == "copy":
            counts[metric_id] = len(copy)
        elif pattern == "copy-distinct":
            counts[metric_id] = len({" ".join(s.split()) for s in copy})
        else:
            counts[metric_id] = sum(len(pattern.findall(text)) for text in code)
    return counts


def over_budget(counts, budget, budget_file):
    problems = []
    for metric_id, what, instead, _ in METRICS:
        allowed = budget.get(metric_id)
        if allowed is None:
            problems.append(f"{metric_id} has no budget in {budget_file}")
        elif counts[metric_id] > allowed:
            problems.append(f"{metric_id}: {counts[metric_id]} > {allowed}: "
                            f"{counts[metric_id] - allowed} new {what}. Use {instead}.")
    return problems


def main(args):
    def flag(name, default):
        return args[args.index(name) + 1] if name in args else default

    root = os.path.normpath(flag("--root", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")))
    budget_path = flag("--budget", os.path.join(root, BUDGET_FILE))
    counts = measure(root)

    if "--json" in args:
        print(json.dumps(counts, indent=2))
        return 0

    try:
        with open(budget_path, encoding="utf-8") as f:
            budget = json.load(f)
    except FileNotFoundError:
        budget = {}  # First run: --update writes it.

    if "--update" in args:
        budget = {m: counts[m] if budget.get(m) is None else min(budget[m], counts[m]) for m, *_ in METRICS}
        with open(budget_path, "w", encoding="utf-8") as f:
            f.write(json.dumps(budget, indent=2) + "\n")
        print(f"The budgets in {budget_path} are now today's counts wherever a count fell.\n")

    width = max(len(m) for m, *_ in METRICS)
    for metric_id, what, _, _ in METRICS:
        allowed = budget.get(metric_id)
        slack = f"  (budget {allowed}: lower it with --update)" if allowed is not None and counts[metric_id] < allowed else ""
        print(f"{metric_id.ljust(width)}  {counts[metric_id]:>5}  {what}{slack}")

    if "--check" in args:
        problems = over_budget(counts, budget, budget_path)
        if problems:
            print("\nA hand-written design count rose (astrid-web docs/specs/DESIGN_SYSTEM.md):\n"
                  + "\n".join(f"  {p}" for p in problems), file=sys.stderr)
            return 1
        print("\nNo design count rose.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
