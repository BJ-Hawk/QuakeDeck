#!/usr/bin/env python3
"""Inventory source references and resolved software licences without modifying inputs."""
import argparse
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import re
import subprocess
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "outputs/source-legality-audit/2026-09-02"
URL = re.compile(r'https?://[^\s<>"\]]+')


def urls(value):
    if isinstance(value, dict):
        for item in value.values():
            yield from urls(item)
    elif isinstance(value, list):
        for item in value:
            yield from urls(item)
    elif isinstance(value, str):
        yield from URL.findall(value)


def source_terms(url):
    host = urllib.parse.urlsplit(url).netloc
    if host in {"www.jma.go.jp", "www.data.jma.go.jp", "xml.kishou.go.jp"}:
        return "JMA default PDL1.0 reviewed; item-specific rights not individually cleared", "https://www.jma.go.jp/jma/kishou/info/coment.html"
    if host == "catalog.registries.digital.go.jp":
        return "ABR default PDL1.0 reviewed; exact dataset notices must be retained", "https://www.digital.go.jp/policies/base_registry_address_tos"
    if host.endswith("gsi.go.jp"):
        return "GSI default PDL1.0 reviewed; endpoint-specific conditions unresolved", "https://www.gsi.go.jp/kikakuchousei/kikakuchousei40182.html"
    return "Individual source terms not cleared; factual evidence is not a blanket redistribution licence", None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--fetch-missing-poms", action="store_true")
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    source = ROOT / "outputs/station-name-audit/station_metadata_sources.json"
    data = json.loads(source.read_text("utf-8"))
    source_urls = defaultdict(set)
    for row in data["stations"]:
        for url in urls(row):
            source_urls[url].add(row["code"])
    for key, value in data.items():
        if key != "stations":
            for url in urls(value):
                source_urls[url]
    records = []
    for url, codes in sorted(source_urls.items()):
        status, terms = source_terms(url)
        records.append(dict(url=url, host=urllib.parse.urlsplit(url).netloc,
                            stationCodes=sorted(codes), reviewStatus=status, termsUrl=terms))
    (OUT / "station-source-register.json").write_text(json.dumps(dict(
        sourceFile=source.relative_to(ROOT).as_posix(), sourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),
        uniqueUrls=len(records), uniqueHosts=len({r['host'] for r in records}), sources=records
    ), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    # Find every literal endpoint/reference in app code and offline tools. These
    # references include documentation and examples, not only active requests.
    references = defaultdict(set)
    tracked = subprocess.check_output(["git", "ls-files", "-z", "app", "tools", "web"], cwd=ROOT).decode().split("\0")
    for name in filter(None, tracked):
        path = ROOT / name
        if not path.is_file() or path.suffix not in {".kt", ".java", ".py", ".mjs", ".js", ".html"}:
            continue
        for url in URL.findall(path.read_text("utf-8", errors="replace")):
            references[url.rstrip("');,}")].add(name)
    (OUT / "code-source-references.json").write_text(json.dumps([
        dict(url=url, files=sorted(files), status="Reference inventory; see README for actual use and terms")
        for url, files in sorted(references.items())
    ], ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    dependencies = defaultdict(set)
    for line in (OUT / "dependency-resolution.txt").read_text("utf-8-sig").splitlines():
        if line.startswith("AUDIT_DEPENDENCY\t"):
            _, scope, coordinate = line.split("\t")
            if not coordinate.startswith("QuakeDeck:"):
                dependencies[coordinate].add(scope)
    cache = Path.home() / ".gradle/caches/modules-2/files-2.1"

    def inspect(item):
        coordinate, scopes = item
        group, name, version = coordinate.split(":")
        google = group.startswith(("androidx.", "com.google.android", "com.google.mlkit", "com.google.firebase"))
        repo = "https://dl.google.com/dl/android/maven2/" if google else "https://repo.maven.apache.org/maven2/"
        url = repo + group.replace(".", "/") + f"/{name}/{version}/{name}-{version}.pom"
        poms = list((cache / group / name / version).glob("*/*.pom"))
        raw = poms[0].read_bytes() if poms else None
        error = None
        if raw is None and args.fetch_missing_poms:
            try:
                with urllib.request.urlopen(url, timeout=30) as response:
                    raw = response.read()
            except Exception as exc:
                error = str(exc)
        licenses = []
        parent_sources = []
        if raw:
            root = ET.fromstring(raw)
            for node in root.iter():
                if node.tag.split("}")[-1] == "license":
                    licenses.append({child.tag.split("}")[-1]: child.text for child in node})
            # Some Maven artifacts inherit the declaration from their parent.
            while not licenses and args.fetch_missing_poms and len(parent_sources) < 3:
                parent = next((n for n in root if n.tag.split("}")[-1] == "parent"), None)
                if parent is None:
                    break
                fields = {n.tag.split("}")[-1]: n.text for n in parent}
                pg, pn, pv = (fields.get(k, "") for k in ("groupId", "artifactId", "version"))
                if not all((pg, pn, pv)) or "$" in pg + pn + pv:
                    break
                parent_url = repo + pg.replace(".", "/") + f"/{pn}/{pv}/{pn}-{pv}.pom"
                try:
                    with urllib.request.urlopen(parent_url, timeout=30) as response:
                        parent_raw = response.read()
                    root = ET.fromstring(parent_raw)
                    parent_sources.append(dict(url=parent_url, sha256=hashlib.sha256(parent_raw).hexdigest()))
                    for node in root.iter():
                        if node.tag.split("}")[-1] == "license":
                            licenses.append({child.tag.split("}")[-1]: child.text for child in node})
                except Exception as exc:
                    error = str(exc)
                    break
        return dict(coordinate=coordinate, configurations=sorted(scopes), pomUrl=url,
                    pomSha256=hashlib.sha256(raw).hexdigest() if raw else None,
                    evidence="cached publisher POM" if poms else "publisher POM fetched" if raw else "unavailable",
                    declaredLicenses=licenses, parentPomSources=parent_sources, error=error,
                    status="Declaration found; required notices and nested components still need packaging review" if licenses else "No licence declaration verified; unresolved")

    with ThreadPoolExecutor(max_workers=6) as executor:
        rows = list(executor.map(inspect, sorted(dependencies.items())))
    (OUT / "dependency-licenses.json").write_text(json.dumps(rows, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    lines = ["# Resolved software licence declarations", "",
             "Resolved from the local checkout on 2026-09-02. Includes platform/metadata modules, debug tools and tests; a coordinate count is not an APK library count. Licence declarations do not prove all required notices are packaged. See dependency-licenses.json for exact configurations, evidence hashes and inherited parent POMs.", "",
             "| Coordinate | Included in | Publisher declaration |", "| --- | --- | --- |"]
    for row in rows:
        scope = "release" if "releaseRuntimeClasspath" in row["configurations"] else "debug/test only"
        label = "; ".join(x.get("name", "unspecified") for x in row["declaredLicenses"]) or "Unresolved"
        lines.append(f"| `{row['coordinate']}` | {scope} | [{label}]({row['pomUrl']}) |")
    (OUT / "dependency-licenses.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Station references: {len(records)} URLs / {len({r['host'] for r in records})} hosts")
    print(f"Software: {len(rows)} coordinates / {sum(bool(r['declaredLicenses']) for r in rows)} with POM licence declarations")
    for row in rows:
        if not row['declaredLicenses']:
            print("Unresolved:", row['coordinate'], row['error'] or "no declaration")


if __name__ == "__main__":
    main()
