#!/usr/bin/env python3
"""The brand literal audit (AATD-90): what a partner's build shows that still names Astrid.

    scripts/check-brand-literals.py <profile.brand.json> <merged res dir> [<merged manifest>]
                                    [--text <dir>]… [--apk <apk>] [--budget N]

Reads the profile's `expect.forbidLiterals` (astrid-web `brands/*.brand.json`, the same list web's
brand matrix checks) and looks for each one, ignoring case as web does, in the text a person can be
shown: every <string>, <plurals> item and <string-array> item in the brand build's merged resources,
and the merged manifest's labels. Resource NAMES are not text and are not checked
(`review_loving_title` is fine; "Loving Astrid?" is not). Each --text directory adds its .txt files,
line by line: the brand's Play listing (brands/<slug>/play), which a person reads in the store.

The goal is none. Until then, --budget N is the most this build may have: one more fails, and one
fewer fails too, saying to lower the budget, so the number only ever goes down. With no budget, any
hit fails. Each hit is printed with its file and resource name.

--apk adds every string compiled into the packaged APK's dex: Kotlin's own text, and the defaults
and fallbacks in it that a resource audit cannot see. The dex is mostly names (classes, fields,
JSON keys, preference files: `astridModel`, `com.gracefultools.astrid…`), which a person is never
shown, so there a name literal ("Astrid") counts only as a whole word in its own case, and a
domain or address literal ("astrid.cc") in any case but not inside a longer word. `AstridTheme`
and `astrid.sqlite` are names; `"Astrid"` and `support@astrid.cc` are text.

--apk also reads the APK's native libraries (lib/<abi>/*.so): the core, which carries the core's
own defaults. Their strings are packed with nothing between them ("…androidastridhttps://astrid.cc
temp_…"), so a name cannot be told from part of an identifier (`setAstridModel`) and is not
counted there. A domain or address literal is, in any case and wherever it appears. One library
built for several ABIs is one library: its hits count once (the ABI with the most).
"""
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
import zipfile


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


def plain_texts(text_dir):
    for folder, _, files in sorted(os.walk(text_dir)):
        for file in sorted(f for f in files if f.endswith(".txt")):
            path = os.path.join(folder, file)
            where = os.path.relpath(path, os.path.dirname(text_dir.rstrip("/")))
            for n, line in enumerate(open(path, encoding="utf-8"), 1):
                if line.strip():
                    yield where, f"line {n}", line


def uleb128(data, at):
    value, shift = 0, 0
    while True:
        byte = data[at]
        at += 1
        value |= (byte & 0x7F) << shift
        if byte < 0x80:
            return value, at
        shift += 7


def dex_strings(data):
    """Every entry of a dex file's string pool (string_ids_size at 0x38, string_ids_off at 0x3C)."""
    if data[:4] != b"dex\n":
        raise ValueError("not a dex file")
    count = int.from_bytes(data[0x38:0x3C], "little")
    table = int.from_bytes(data[0x3C:0x40], "little")
    for i in range(count):
        offset = int.from_bytes(data[table + 4 * i:table + 4 * i + 4], "little")
        _, start = uleb128(data, offset)  # the length in UTF-16 units; the bytes end at a NUL
        # Modified UTF-8: close enough to UTF-8 for matching ASCII literals.
        yield data[start:data.index(b"\0", start)].decode("utf-8", "replace")


def apk_texts(apk):
    with zipfile.ZipFile(apk) as z:
        for name in sorted(n for n in z.namelist() if re.fullmatch(r"classes\d*\.dex", n)):
            for text in dex_strings(z.read(name)):
                yield name, "string", text


def printable_runs(data):
    """Every run of 4+ printable ASCII bytes: a native library's strings, packed together."""
    for match in re.finditer(rb"[\x20-\x7e]{4,}", data):
        yield match.group().decode("ascii")


def native_hits(apk, literals):
    """(file, "native", [literal], context) for each domain/address literal in lib/<abi>/*.so."""
    addresses = [l for l in literals if re.search(r"[.@]", l)]
    by_library = {}
    with zipfile.ZipFile(apk) as z:
        for name in sorted(n for n in z.namelist() if re.fullmatch(r"lib/[^/]+/[^/]+\.so", n)):
            hits = []
            for run in printable_runs(z.read(name)):
                lowered = run.lower()
                for literal in addresses:
                    at = lowered.find(literal.lower())
                    while at >= 0:
                        hits.append((name, "native", [literal], run[max(0, at - 30):at + len(literal) + 30]))
                        at = lowered.find(literal.lower(), at + 1)
            library = os.path.basename(name)
            if len(hits) > len(by_library.get(library, [])):
                by_library[library] = hits
    return [hit for library in sorted(by_library) for hit in by_library[library]]


def dex_pattern(literal):
    """A literal as it would appear in text inside the dex's names: see the module docstring."""
    edge = r"(?<![A-Za-z0-9_]){}(?![A-Za-z0-9_])"
    if re.search(r"[.@]", literal):
        return re.compile(edge.format(re.escape(literal)), re.IGNORECASE)
    return re.compile(edge.format(re.escape(literal)))


def main(argv):
    budget = None
    text_dirs = []
    while "--text" in argv:
        i = argv.index("--text")
        text_dirs.append(argv[i + 1])
        argv = argv[:i] + argv[i + 2:]
    apk = None
    if "--apk" in argv:
        i = argv.index("--apk")
        apk = argv[i + 1]
        argv = argv[:i] + argv[i + 2:]
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
    for text_dir in text_dirs:
        if not os.path.isdir(text_dir):
            print(f"✗ --text {text_dir}: no such directory", file=sys.stderr)
            return 2
        texts += list(plain_texts(text_dir))
    # One hit per string, however many literals it holds: "astrid.cc" is also "Astrid".
    hits = [(where, name, found, text) for where, name, text in texts
            if (found := [l for l in literals if l.lower() in text.lower()])]
    if apk:
        patterns = [(l, dex_pattern(l)) for l in literals]
        dex = list(apk_texts(apk))
        texts += dex
        hits += [(where, name, found, text) for where, name, text in dex
                 if (found := [l for l, pattern in patterns if pattern.search(text)])]
        hits += native_hits(apk, literals)

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
