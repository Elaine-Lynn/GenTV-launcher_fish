#!/usr/bin/env python3
"""Offline source/privacy checks. Prints categories and paths, never matched secrets."""
from pathlib import Path
import re
import struct
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
BAD_SUFFIXES = {".jks", ".keystore", ".pem", ".p12", ".pfx", ".apk", ".aab", ".hprof", ".log"}
ALLOWED_EMAIL = "Elaine-Lynn" + "@users.noreply.github.com"
EMAIL = re.compile(r"[A-Za-z0-9_.+%-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
PATTERNS = {
    "home-directory": re.compile("/" + r"(?:Users|home)/[A-Za-z0-9_.-]+/"),
    "private-network": re.compile(r"\b(?:192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})\b"),
    "private-key": re.compile(r"BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY"),
    "access-token": re.compile(r"(?:gh[pousr]_[A-Za-z0-9]{25,}|github_pat_[A-Za-z0-9_]{30,}|sk-[A-Za-z0-9]{25,})"),
}
def paths():
    if (ROOT / ".git").exists():
        result = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, capture_output=True, check=True)
        return [ROOT / p for p in result.stdout.decode().split("\0") if p]
    return [p for p in ROOT.rglob("*") if p.is_file() and not any(x in p.parts for x in (".git", "build", ".gradle"))]

def check_png(path):
    content = path.read_bytes()
    offset = 8
    while offset + 12 <= len(content):
        length = struct.unpack(">I", content[offset:offset + 4])[0]
        kind = content[offset + 4:offset + 8]
        if kind in {b"eXIf", b"tEXt", b"zTXt", b"iTXt"}:
            return False
        offset += length + 12
    return True

def main():
    findings = []
    files = paths()
    for path in files:
        name = path.relative_to(ROOT).as_posix()
        if path.suffix.lower() in BAD_SUFFIXES or path.name in {"local.properties", "signing.local.properties", ".env"}:
            findings.append(("private-or-generated-file", name))
        if path.suffix == ".png" and not check_png(path):
            findings.append(("png-metadata", name))
        try:
            body = path.read_text()
        except (UnicodeError, OSError):
            continue
        for category, pattern in PATTERNS.items():
            if pattern.search(body):
                findings.append((category, name))
        if any(match.group() != ALLOWED_EMAIL for match in EMAIL.finditer(body)):
            findings.append(("non-project-email", name))
    if (ROOT / ".git").exists():
        log = subprocess.check_output(["git", "log", "--all", "--no-merges", "--format=%an|%ae|%cn|%ce"], cwd=ROOT, text=True)
        for row in log.splitlines():
            if row != "|".join(("Elaine-Lynn", ALLOWED_EMAIL, "Elaine-Lynn", ALLOWED_EMAIL)):
                findings.append(("non-project-commit-identity", ".git history"))
    for category, name in findings:
        print(f"FAIL {category}: {name}")
    if findings:
        return 1
    print(f"PASS: {len(files)} public files checked; no private paths, non-project emails, private IPs, key patterns or forbidden artifacts detected.")
    return 0
if __name__ == "__main__":
    sys.exit(main())
