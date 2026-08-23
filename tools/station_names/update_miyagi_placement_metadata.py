"""Apply current Miyagi Prefecture station-address records to the metadata JSON."""

import argparse
import json
import os
import re
import unicodedata
from pathlib import Path

import pdfplumber

SOURCE_URL = "https://www.pref.miyagi.jp/documents/8097/04-02-2_siryou-honnpenn.pdf"
PDF_PATH = Path(os.environ["TEMP"]) / "quakedeck-miyagi-stations.pdf"
JSON_PATH = Path("outputs/station-name-audit/station_metadata_sources.json")
SOURCE_PROVIDER = {
    "県": "地方公共団体",
    "防科研": "防災科学技術研究所",
    "気象庁": "気象庁",
}


def normalized(value: str) -> str:
    return unicodedata.normalize("NFKC", value).replace("‐", "-").replace("‒", "-").strip()


def source_rows():
    rows = []
    with pdfplumber.open(PDF_PATH) as pdf:
        for page_number in (38, 39):
            table = pdf.pages[page_number].extract_tables()[0]
            for row in table[2:]:
                code = (row[8] or "").strip()
                if not re.fullmatch(r"\d{7}", code):
                    continue
                combined = normalized(row[9] or "")
                match = re.match(r"^(県|防科研|気象庁)\s*(.*)$", combined)
                if not match:
                    raise ValueError(f"Could not parse source row {code}: {combined!r}")
                rows.append({
                    "code": code,
                    "name": normalized(row[5] or ""),
                    "provider": SOURCE_PROVIDER[match.group(1)],
                    "address": match.group(2),
                    "facility": normalized(row[10] or "") or None,
                })
    return rows


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    catalog = json.loads(JSON_PATH.read_text(encoding="utf-8"))
    stations = {station["code"]: station for station in catalog["stations"]}
    proposed = []
    for source in source_rows():
        station = stations.get(source["code"])
        if station and (station["prefectureJa"] != "宮城県" or station["providerJa"] != source["provider"]):
            station = None
        if not station:
            candidates = [
                candidate for candidate in catalog["stations"]
                if candidate["prefectureJa"] == "宮城県"
                and candidate["providerJa"] == source["provider"]
                and normalized(candidate["nameJa"]) == source["name"]
            ]
            if len(candidates) != 1:
                candidates = [
                    candidate for candidate in catalog["stations"]
                    if candidate["prefectureJa"] == "宮城県"
                    and candidate["providerJa"] == source["provider"]
                    and candidate["code"].endswith(source["code"][-4:])
                ]
            if len(candidates) != 1:
                raise ValueError(f"Could not uniquely map source station {source['code']} ({source['name']}): {[candidate['code'] for candidate in candidates]}")
            station = candidates[0]
        if not station["publishedAddressJa"]:
            proposed.append((station, source))

    if args.apply:
        for station, source in proposed:
            station["publishedAddressJa"] = source["address"]
            if source["facility"]:
                station["facilityNameJa"] = source["facility"]
            station["placementPrecision"] = "exact_address"
            station.pop("placementLocalityJa", None)
            station["metadataStatus"] = "Miyagi Prefecture seismic-intensity network list (2025)"
            if SOURCE_URL not in station["sourceUrls"]:
                station["sourceUrls"].append(SOURCE_URL)
            note = "Exact placement address and facility sourced from Miyagi Prefecture seismic-intensity network list (2025)."
            station["note"] = f"{station['note']} {note}" if station["note"] else note
        coverage = catalog["coverage"]
        coverage["publishedAddresses"] += len(proposed)
        coverage["exactPlacementAddressUpdates"] += len(proposed)
        coverage["localityPlacementRecords"] -= len(proposed)
        JSON_PATH.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(json.dumps({
        "sourceRows": len(source_rows()),
        "exactAddressUpdates": len(proposed),
        "updates": [{"code": station["code"], "address": source["address"], "facility": source["facility"]} for station, source in proposed],
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
