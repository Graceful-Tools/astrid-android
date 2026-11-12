#!/usr/bin/env python3
"""The brand literal audit (AATD-90): what a partner's build shows that still names Astrid.

    scripts/check-brand-literals.py <profile.brand.json> <merged res dir> [<merged manifest>] [--budget N]

Reads the profile's `expect.forbidLiterals` (astrid-web `brands/*.brand.json`, the same list web's
brand matrix checks) and looks for each one, ignoring case as web does, in the text a person can be
shown: every <string>, <plurals> item and <string-array> item in the brand build's merged resources,
and the merged manifest's labels. Resource NAMES are not text and are not checked
(`review_loving_title` is fine; "Loving Astrid?" is not).

The goal is none. Until then, --budget N is the most this build may have: one more fails, and one
fewer fails too, saying to lower the budget, so the number only ever goes down. With no budget, any
hit fails. Each hit is printed with its file and resource name.

Not yet: strings compiled into the dex and the core library, which only a packaged APK has.
"""
import json
import os
import sys
import xml.etree.ElementTree as ET


def text_of(element):
    return "".join(element.itertext())


def resource_texts(res_dir):
    """(file, resource name, text) for every string a person can be shown, in every values* dir."""
    for folder in sorted(os.listdir(res_dir)):
        if not folder.startswith("values"):
            continue
        for file in sorted(os.listdir(os.path.join(res_dir, folder))):
            if not file.endswith(".xml"):
                continue
            path = os.path.join(res_dir, folder, file)
            for element in ET.parse(path).getroot():
                name = element.get("name", "")
                if element.tag == "string":
                    yield f"{folder}/{file}", name, text_of(element)
                elif element.tag in ("plurals", "string-array"):
                    for i, item in enumerate(element.findall("item")):
                        yield f"{folder}/{file}", f"{name}[{item.get('quantity', i)}]", text_of(item)


def manifest_texts(manifest):
    android = "{http://schemas.android.com/apk/res/android}"
    for element in ET.parse(manifest).getroot().iter():
        for attr in ("label", "description"):
            value = element.get(android + attr)
            if value and not value.startswith("@"):
                yield "AndroidManifest.xml", f"{element.tag} android:{attr}", value


def main(argv):
    budget = None
    if "--budget" in argv:
        i = argv.index("--budget")
        budget = int(argv[i + 1])
        argv = argv[:i] + argv[i + 2:]
    if len(argv) not in (2, 3):
        print(__doc__, file=sys.stderr)
        return 2
    profile_path, res_dir = argv[0], argv[1]
    manifest = argv[2] if len(argv) == 3 else None

    profile = json.load(open(profile_path, encoding="utf-8"))
    literals = [l for l in (profile.get("expect") or {}).get("forbidLiterals") or [] if l.strip()]
    if not literals:
        print(f"{os.path.basename(profile_path)} forbids no literals: nothing to audit")
        return 0

    texts = list(resource_texts(res_dir))
    if manifest:
        texts += list(manifest_texts(manifest))
    # One hit per string, however many literals it holds: "astrid.cc" is also "Astrid".
    hits = [(where, name, found, text) for where, name, text in texts
            if (found := [l for l in literals if l.lower() in text.lower()])]

    label = os.path.basename(profile_path)
    for where, name, found, text in hits:
        print(f"  {where}: {name} has {', '.join(repr(l) for l in found)}: {text.strip()[:120]}")
    count = len(hits)
    allowed = budget or 0
    if count > allowed:
        print(f"✗ {label}: {count} string(s) naming Astrid in the brand build, the budget is {allowed}. "
              "Take the name from the brand (%1$s with app_name, or Brand) instead.", file=sys.stderr)
        return 1
    if count < allowed:
        print(f"✗ {label}: {count} string(s) naming Astrid, under the budget of {allowed} — lower the budget "
              f"to {count} in scripts/predeploy.sh so it cannot creep back.", file=sys.stderr)
        return 1
    print(f"✓ {label}: {count} string(s) naming Astrid of {len(texts)} strings" +
          (f" (budget {allowed}; the goal is 0, AATD-90)" if allowed else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
