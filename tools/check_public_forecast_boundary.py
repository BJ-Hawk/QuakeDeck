#!/usr/bin/env python3
"""Check a proposed public tree; never stage, delete, or rewrite Git history.

Default: inspect the index (the next commit). --scope worktree inspects the
prospective files before staging. Neither mode certifies the older Git history
or provider permissions; see outputs/source-legality-audit/2026-09-02/README.md.
"""
import argparse
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PRIVATE_NAMES = {"LocalEewForecastEngine.kt", "jma2001_travel_times.gz",
                 "local_eew_station_avs30.gz"}
# Known current/legacy implementation signatures; not a semantic code audit.
IMPLEMENTATION = re.compile(
    r"\bclass\s+LocalEewForecastEngine\b|\bfun\s+surfaceRadiusKm\s*\(|"
    r"\b(?:P_WAVE_SPEED_KM_PER_SECOND|S_WAVE_SPEED_KM_PER_SECOND|"
    r"EMPTY_REGION_EPICENTRE_FALLBACK_KM|EEW_POINT_FALLBACK_KM)\b"
)


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scope", choices=("index", "worktree"), default="index")
    parser.add_argument("--compiled-jar", type=Path)
    parser.add_argument("--resource-symbols", type=Path)
    args = parser.parse_args()
    options = ["ls-files", "-z", "--cached"]
    if args.scope == "worktree":
        options += ["--others", "--exclude-standard"]
    paths = sorted(set(git(*options).decode("utf-8").strip("\0").split("\0")))
    errors = []
    for name in filter(None, paths):
        path = ROOT / name
        if args.scope == "worktree" and not path.is_file():
            continue
        if path.name in PRIVATE_NAMES or name.startswith("app/src/localForecast/"):
            errors.append(f"Private forecast input/implementation in {args.scope}: {name}")
        if path.suffix not in {".kt", ".java"}:
            continue
        raw = path.read_bytes() if args.scope == "worktree" else git("show", f":{name}")
        if IMPLEMENTATION.search(raw.decode("utf-8", errors="replace")):
            errors.append(f"Forecast implementation signature in public source: {name}")
    for private in ["app/src/main/java/cz/misa/quakedeck/data/LocalEewForecastEngine.kt",
                    "app/src/localForecast/res/raw/jma2001_travel_times.gz",
                    "app/src/localForecast/res/raw/local_eew_station_avs30.gz"]:
        result = subprocess.run(["git", "check-ignore", "--no-index", "-q", private], cwd=ROOT)
        if result.returncode != 0:
            errors.append(f"Missing ignore rule: {private}")
    if args.compiled_jar:
        with zipfile.ZipFile(args.compiled_jar) as archive:
            if any("LocalEewForecastEngine" in name for name in archive.namelist()):
                errors.append("Compiled JAR contains the private forecast engine")
    if args.resource_symbols:
        symbols = args.resource_symbols.read_text("utf-8")
        if any(name.removesuffix(".gz") in symbols for name in PRIVATE_NAMES if name.endswith(".gz")):
            errors.append("Resource symbols include private forecast inputs")
    if errors:
        print("\n".join(errors))
        return 1
    print(f"PASS: known private forecast files absent from {args.scope} and requested build artifacts.")
    print("This checks the selected tree only; older history and legal permissions require separate review.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
