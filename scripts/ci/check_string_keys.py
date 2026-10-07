#!/usr/bin/env python3
"""Compares every values-*/strings.xml with the default values/strings.xml.

For each resource root given on the command line it reports, as `file: key: problem`:
  - keys missing from or extra in a translation,
  - empty values,
  - a different set of format arguments (%1$s, %2$d, ...).
Keys marked translatable="false" in the default file need no translation.
A root without a values/strings.xml is skipped, so adding one later is covered automatically.
Exit code 1 when any problem is found.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# %s, %1$s, %2$d, %.2f ... but not the literal "%%".
FORMAT_ARG = re.compile(r"(?<!%)%(\d+\$)?[-#+0,(]*\d*(?:\.\d+)?[a-zA-Z]")


def read(path):
    """Returns {name: (text, translatable)} for string, plurals and string-array entries."""
    entries = {}
    for element in ET.parse(path).getroot():
        name = element.get("name")
        if element.tag not in ("string", "plurals", "string-array") or name is None:
            continue
        text = "".join(element.itertext()).strip()
        translatable = element.get("translatable", "true") != "false"
        entries[name] = (text, translatable)
    return entries


def format_args(text):
    return sorted(match.group(0) for match in FORMAT_ARG.finditer(text))


def check_root(root):
    problems = []
    default_file = root / "values" / "strings.xml"
    if not default_file.is_file():
        return problems, False
    default = read(default_file)
    required = {name for name, (_, translatable) in default.items() if translatable}

    for translated_file in sorted(root.glob("values-*/strings.xml")):
        translated = read(translated_file)
        where = str(translated_file)
        for name in sorted(required - translated.keys()):
            problems.append(f"{where}: {name}: missing translation")
        for name in sorted(translated.keys() - default.keys()):
            problems.append(f"{where}: {name}: not in values/strings.xml")
        for name in sorted(required & translated.keys()):
            text = translated[name][0]
            if not text:
                problems.append(f"{where}: {name}: empty value")
            elif format_args(text) != format_args(default[name][0]):
                problems.append(
                    f"{where}: {name}: format arguments {format_args(text)} "
                    f"differ from {format_args(default[name][0])}"
                )
    return problems, True


def main(argv):
    if len(argv) < 2:
        print("usage: check_string_keys.py <resources-root>...", file=sys.stderr)
        return 2
    problems = []
    checked = 0
    for arg in argv[1:]:
        root_problems, found = check_root(Path(arg))
        problems += root_problems
        checked += 1 if found else 0
    if checked == 0:
        print("check-string-keys: no values/strings.xml found in the given roots", file=sys.stderr)
        return 2
    if problems:
        print("String resources are out of sync (see docs/localization.md):")
        print("\n".join(problems))
        return 1
    print(f"check-string-keys: OK ({checked} resource root(s))")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
