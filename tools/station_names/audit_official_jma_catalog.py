"""Build an official-source candidate and audit it; never modify app resources.

Python 3.10+, standard library only. Run from any directory with --sources and
--output pointing to separate local directories. Download sources separately;
this tool deliberately has no network or apply mode. See the audit README.
"""

import argparse
from collections import Counter
from datetime import datetime, timezone
from decimal import Decimal
from hashlib import sha256
from io import BytesIO
import json
from pathlib import Path
import posixpath
import re
import xml.etree.ElementTree as ET
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[2]
MAP_PAGE = "https://www.jma.go.jp/jma/kishou/know/jishin/intens-st/index.html"
MAP_URL = "https://www.jma.go.jp/jma/kishou/know/jishin/intens-st/stations.json"
XML_PAGE = "https://xml.kishou.go.jp/tec_material.html"
TERMS = "https://www.jma.go.jp/jma/kishou/info/coment.html"
PDL = "https://www.digital.go.jp/resources/open_data/public_data_license_v1.0"
WORKBOOK = "地震火山関連コード表.xlsx"
FIELDS = ["code", "nameJa", "prefectureJa", "latitude", "longitude", "networkJa",
          "areaCode", "areaNameJa", "municipalityCode"]
NS = {"s": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def digest(path):
    return sha256(path.read_bytes()).hexdigest()


def canonical_hash(rows):
    normalized = [[str(Decimal(str(v)).normalize()) if i in (3, 4) else v
                   for i, v in enumerate(row)] for row in sorted(rows)]
    return sha256(json.dumps(normalized, ensure_ascii=False,
                             separators=(",", ":")).encode()).hexdigest()


def xlsx_rows(payload, sheet_name):
    """Read literal OOXML cells, preserving text IDs and rejecting formulas."""
    with ZipFile(BytesIO(payload)) as archive:
        workbook = ET.fromstring(archive.read("xl/workbook.xml"))
        relationships = ET.fromstring(archive.read("xl/_rels/workbook.xml.rels"))
        targets = {r.attrib["Id"]: r.attrib["Target"] for r in relationships}
        sheet = next(s for s in workbook.find("s:sheets", NS)
                     if s.attrib["name"] == sheet_name)
        relationship_id = sheet.attrib[
            "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"]
        target = targets[relationship_id]
        target = target.lstrip("/") if target.startswith("/") else posixpath.normpath("xl/" + target)
        strings = []
        if "xl/sharedStrings.xml" in archive.namelist():
            strings = ["".join(t.text or "" for t in si.iterfind(".//s:t", NS))
                       for si in ET.fromstring(archive.read("xl/sharedStrings.xml"))]
        data = ET.fromstring(archive.read(target)).find("s:sheetData", NS)
        for row in data:
            cells = {}
            for cell in row:
                if cell.find("s:f", NS) is not None:
                    raise ValueError(f"Unexpected formula at {sheet_name}!{cell.attrib['r']}")
                column = re.match(r"[A-Z]+", cell.attrib["r"])[0]
                value = cell.findtext("s:v", default="", namespaces=NS)
                if cell.attrib.get("t") == "s":
                    value = strings[int(value)]
                elif cell.attrib.get("t") == "inlineStr":
                    value = "".join(t.text or "" for t in cell.iterfind(".//s:t", NS))
                cells[column] = value
            yield int(row.attrib["r"]), cells


def unique_index(items, key, label):
    result = {}
    for item in items:
        identity = key(item)
        if identity in result:
            raise ValueError(f"Duplicate {label}: {identity}")
        result[identity] = item
    return result


def audit(sources, output, baseline):
    # A dry run must not overwrite either its inputs or production resources.
    output.mkdir(parents=True, exist_ok=True)
    if output == sources or output == ROOT or output.is_relative_to(ROOT / "app"):
        raise ValueError("Output must be a separate audit directory outside app/")
    if any(output.iterdir()):
        raise ValueError("Output directory must be empty; preserve previous dry-run evidence")
    archives = list(sources.glob("jmaxml_*_Code.zip"))
    if len(archives) != 1:
        raise ValueError("Expected exactly one official JMA code archive")
    code_archive = archives[0]
    code_url = "https://xml.kishou.go.jp/" + code_archive.name
    source_urls = {
        "jma-map-stations.json": MAP_URL,
        "jma-map.js": MAP_PAGE.replace("index.html", "map.js"),
        "jma-map-index.html": MAP_PAGE,
        code_archive.name: code_url,
        "jma-xml-technical.html": XML_PAGE,
        "jma-terms.html": TERMS,
        "pdl-1.0.html": PDL,
        "jma-observation-history.html": "https://www.data.jma.go.jp/eqev/data/kyoshin/jma-shindo.html",
    }
    source_records = []
    for filename, url in source_urls.items():
        path = sources / filename
        source_records.append({"file": filename, "url": url, "sha256": digest(path),
                               "bytes": path.stat().st_size,
                               "savedAtUtc": datetime.fromtimestamp(
                                   path.stat().st_mtime, timezone.utc).isoformat()})
    baseline_before = digest(baseline)
    old_document = read_json(baseline)
    old_rows = old_document["stations"]
    old_by_code = unique_index(old_rows, lambda r: r[0], "baseline code")
    map_rows = read_json(sources / "jma-map-stations.json")
    map_by_name = unique_index(map_rows, lambda r: r["name"], "official map name")
    script = (sources / "jma-map.js").read_text(encoding="utf-8")
    prefectures = json.loads(re.search(r"var prefPrm\s*=\s*(\{.*?\});", script, re.S)[1])
    # This mapping is verified against the retained official map.js, not inferred
    # from station code suffixes (which differ between networks).
    networks = {"0": "気象庁", "1": "地方公共団体", "2": "防災科学技術研究所"}
    if not (re.search(r'var Syozoku\s*=\s*"気象庁"', script)
            and re.search(r'affi == 1\).*?Syozoku = "地方公共団体"', script, re.S)
            and re.search(r'affi == 2\).*?Syozoku = "防災科学技術研究所"', script, re.S)):
        raise ValueError("Official operator mapping changed; review map.js")
    with ZipFile(code_archive) as archive:
        workbook_payload = archive.read(WORKBOOK)
    code_rows = []
    nonstation_rows = []
    for number, cells in xlsx_rows(workbook_payload, "24"):
        code = cells.get("G", "")
        if not re.fullmatch(r"[0-9]{7}", code):
            if number > 3 and any(cells.values()):
                raise ValueError(f"Unexpected nonstation content at sheet 24 row {number}")
            nonstation_rows.append(number)
            continue
        if not re.fullmatch(r"[0-9]{3}", cells.get("A", "")):
            raise ValueError(f"Invalid area code at row {number}")
        if not re.fullmatch(r"[0-9]{7}", cells.get("D", "")):
            raise ValueError(f"Invalid municipality code at row {number}")
        code_rows.append({"code": code, "name": cells["H"], "areaCode": cells["A"],
                          "areaName": cells["B"], "municipalityCode": cells["D"],
                          "municipalityName": cells["E"], "worksheetRow": number})
    unique_index(code_rows, lambda r: r["code"], "official station code")
    code_by_name = unique_index(code_rows, lambda r: r["name"], "official code-table name")
    unmatched_map = sorted(map_by_name.keys() - code_by_name.keys())
    if unmatched_map:
        raise ValueError(f"Official map stations have no code: {unmatched_map}")
    candidate_rows = []
    evidence = []
    for record in code_rows:
        if record["name"] not in map_by_name:
            continue
        point = map_by_name[record["name"]]
        lat, lon = Decimal(str(point["lat"])), Decimal(str(point["lon"]))
        if not lat.is_finite() or not lon.is_finite() or not (20 <= lat <= 46 and 122 <= lon <= 154):
            raise ValueError(f"Invalid official coordinates: {record['code']}")
        row = [record["code"], point["name"], prefectures[point["pref"]],
               point["lat"], point["lon"], networks[point["affi"]],
               record["areaCode"], record["areaName"], record["municipalityCode"]]
        candidate_rows.append(row)
        evidence.append({"code": record["code"], "mapName": point["name"],
                         "worksheet": "24", "worksheetRow": record["worksheetRow"],
                         "officialValues": dict(zip(FIELDS, row))})
    candidate_by_code = unique_index(candidate_rows, lambda r: r[0], "candidate code")
    differences = []
    type_changes = []
    field_matches = Counter()
    for code in sorted(old_by_code.keys() & candidate_by_code.keys()):
        old, new = old_by_code[code], candidate_by_code[code]
        if len(old) != 9:
            raise ValueError(f"Unexpected baseline row shape: {code}")
        for index, field in enumerate(FIELDS):
            equal = (Decimal(str(old[index])) == Decimal(str(new[index]))) if index in (3, 4) else old[index] == new[index]
            if equal:
                field_matches[field] += 1
                if type(old[index]) is not type(new[index]):
                    type_changes.append({"code": code, "field": field,
                                         "beforeType": type(old[index]).__name__,
                                         "afterType": type(new[index]).__name__})
            else:
                differences.append({"code": code, "field": field,
                                    "before": old[index], "after": new[index]})
    extra_codes = [r for r in code_rows if r["name"] not in map_by_name]
    additions = sorted(candidate_by_code.keys() - old_by_code.keys())
    removals = sorted(old_by_code.keys() - candidate_by_code.keys())
    metadata_path = ROOT / "outputs/station-name-audit/station_metadata_sources.json"
    metadata = read_json(metadata_path)
    metadata_rows = unique_index(metadata["stations"], lambda r: r["code"], "research code")
    metadata_differences = []
    mapping = {"code": 0, "nameJa": 1, "prefectureJa": 2, "catalogueLatitude": 3,
               "catalogueLongitude": 4, "providerJa": 5, "areaCode": 6,
               "areaNameJa": 7, "municipalityCode": 8}
    for code, row in candidate_by_code.items():
        if code not in metadata_rows:
            metadata_differences.append({"code": code, "field": "missingResearchRecord"})
            continue
        for key, index in mapping.items():
            value = metadata_rows[code].get(key)
            equal = Decimal(str(value)) == Decimal(str(row[index])) if index in (3, 4) else value == row[index]
            if not equal:
                metadata_differences.append({"code": code, "field": key,
                                             "research": value, "candidate": row[index]})
    english_path = ROOT / "app/src/main/res/raw/station_english_names.json"
    english_codes = set(read_json(english_path)["names"])
    english_orphans = sorted(english_codes - candidate_by_code.keys())
    research_orphans = sorted(metadata_rows.keys() - candidate_by_code.keys())
    result = "PASS" if not (additions or removals or differences or metadata_differences or research_orphans or english_orphans) else "REVIEW_REQUIRED"
    catalog_metadata = {
        "version": 1, "source": "Japan Meteorological Agency (JMA)",
        "sourceUrls": [MAP_URL, code_url],
        "sourcePages": [MAP_PAGE, XML_PAGE],
        "sourceFiles": [{"url": r["url"], "sha256": r["sha256"]}
                        for r in source_records if r["file"] in ("jma-map-stations.json", "jma-map.js", code_archive.name)],
        "codeTable": {"workbook": WORKBOOK, "sheet": "24",
                      "workbookSha256": sha256(workbook_payload).hexdigest()},
        "license": "Public Data License 1.0 (PDL1.0)",
        "termsUrl": TERMS, "licenseUrl": PDL,
        "attribution": "Source: Japan Meteorological Agency website. Station map data and official XML code tables processed into the QuakeDeck catalogue by QuakeDeck.",
        "processing": "Exact Japanese-name join of all official map stations to sheet 24; prefecture/operator labels decoded using JMA map.js. Published coordinate values retained without rounding. Code-table entries absent from the station map are excluded; this is a QuakeDeck adaptation, not an unmodified JMA product.",
        "codeTableEntriesWithoutMapCoordinates": extra_codes,
    }
    candidate = {**catalog_metadata, "stations": candidate_rows}
    summary = {
        "result": result, "scope": "Complete JMA public station-map inventory, joined to official XML codes",
        "createdAtUtc": datetime.now(timezone.utc).isoformat(),
        "baseline": {"path": str(baseline.relative_to(ROOT)) if baseline.is_relative_to(ROOT) else str(baseline),
                     "sha256": baseline_before, "source": old_document.get("source"),
                     "sourceBlob": old_document.get("sourceBlob"), "rows": len(old_rows)},
        "officialMapRows": len(map_rows), "officialCodeTableRows": len(code_rows),
        "candidateRows": len(candidate_rows), "fieldMatchCounts": dict(field_matches),
        "addedCodes": additions, "removedCodes": removals, "fieldDifferences": differences,
        "coordinateTypeChangesOnly": type_changes,
        "codeTableOnlyEntries": extra_codes,
        "codeTableOnlyPolicy": "Excluded because JMA publishes no matching map row. No coordinates or operator inferred. This does not establish the operational status of the code.",
        "prefectures": len({r[2] for r in candidate_rows}),
        "reportingAreas": len({r[6] for r in candidate_rows}),
        "municipalityParents": len({r[8] for r in candidate_rows}),
        "operatorCounts": dict(Counter(r[5] for r in candidate_rows)),
        "researchMetadata": {"sha256": digest(metadata_path), "rows": len(metadata_rows),
                             "fieldDifferences": metadata_differences,
                             "orphanCodes": research_orphans},
        "englishNames": {"sha256": digest(english_path), "rows": len(english_codes),
                         "orphanCodes": english_orphans},
        "baselineCanonicalStationSha256": canonical_hash(old_rows),
        "candidateCanonicalStationSha256": canonical_hash(candidate_rows),
        "sourceFiles": source_records,
        "productionResourcesWritten": False,
    }
    if digest(baseline) != baseline_before:
        raise ValueError("Baseline changed during audit")
    def save(name, document):
        (output / name).write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    save("candidate.json", candidate)
    summary["candidateFileSha256"] = digest(output / "candidate.json")
    save("audit.json", summary)
    save("row-evidence.json", evidence)
    print(json.dumps({k: summary[k] for k in ["result", "officialMapRows", "officialCodeTableRows",
                      "candidateRows", "fieldMatchCounts", "fieldDifferences", "operatorCounts",
                      "codeTableOnlyEntries", "candidateCanonicalStationSha256"]}, ensure_ascii=False, indent=2))
    return 0 if result == "PASS" else 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sources", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--baseline", type=Path, default=ROOT / "app/src/main/res/raw/jma_intensity_stations.json")
    args = parser.parse_args()
    raise SystemExit(audit(args.sources.resolve(), args.output.resolve(), args.baseline.resolve()))
