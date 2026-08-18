#!/usr/bin/env python3
"""Every message key the Java code asks for must exist in messages_ar.yml.

A missing key is invisible until that exact line runs in game, where the player gets the raw
path printed in red. Checking it statically turns a live embarrassment into a build failure.

It also catches the YAML 1.1 trap: a bare `on:` or `yes:` key parses as a boolean, not a
string, so the message silently stops resolving. Those keys have to stay quoted.
"""
import pathlib, re, sys

try:
    import yaml
except ImportError:
    sys.exit("needs pyyaml: pip install pyyaml")

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java"
MESSAGES = ROOT / "src/main/resources/messages_ar.yml"

# Keys are only read through msg(), or handed to a button() helper as a label/hover pair.
CALL = re.compile(r'(?:msg\(\)\s*\.\s*(?:get|send|getList)|\bbutton)\s*\((.{0,400}?)\)\s*;', re.S)
# Some keys are picked by a ternary and passed through a variable, e.g. `String messageKey = ...`.
ASSIGN = re.compile(r'\w*[Kk]ey\s*=\s*(.{0,300}?);', re.S)
KEY = re.compile(r'"([a-z0-9_]+(?:\.[a-z0-9_-]+)+)"')
# YAML 1.1 turns these into booleans unless quoted.
YAML11_BOOL = {"y", "n", "yes", "no", "on", "off", "true", "false"}


def flatten(node, prefix=""):
    keys = set()
    if isinstance(node, dict):
        for key, value in node.items():
            path = f"{prefix}{key}"
            keys.add(path)
            keys |= flatten(value, path + ".")
    return keys


def main():
    raw = MESSAGES.read_text(encoding="utf-8")
    loaded = yaml.safe_load(raw) or {}
    defined = flatten(loaded)

    problems = 0

    # A boolean key means somebody wrote `on:` where they meant `"on":`.
    booleans = sorted(k for k in defined if k.rsplit(".", 1)[-1] in ("True", "False"))
    for key in booleans:
        print(f"BOOLEAN KEY  {key}  <- quote it in messages_ar.yml, YAML read it as a boolean")
        problems += 1

    used = {}
    for java in sorted(SRC.rglob("*.java")):
        text = java.read_text(encoding="utf-8")
        for pattern in (CALL, ASSIGN):
            for match in pattern.finditer(text):
                line_no = text.count("\n", 0, match.start()) + 1
                for key in KEY.findall(match.group(1)):
                    used.setdefault(key, set()).add(f"{java.relative_to(ROOT)}:{line_no}")

    for key in sorted(k for k in used if k not in defined):
        print(f"MISSING  {key}")
        for where in sorted(used[key]):
            print(f"         {where}")
        problems += 1

    leaves = {k for k in defined if not any(d.startswith(k + ".") for d in defined)}
    unused = sorted(k for k in leaves if k not in used and k != "prefix")
    for key in unused:
        print(f"unused   {key}   (harmless, but usually a half-finished rename)")

    print(f"\n{len(used)} key(s) used, {len(defined)} defined, "
          f"{problems} problem(s), {len(unused)} unused")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
