#!/usr/bin/env python3
"""Build the derived AVS30 lookup used by the optional local EEW engine.

The output contains only AVS30 values sampled at QuakeDeck's bundled JMA
intensity-station coordinates. It is not a copy of the nationwide J-SHIS
dataset. Source: NIED J-SHIS shallow-subsurface V4 Web API.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import gzip
import json
import time
import urllib.parse
import urllib.request
import urllib.error
from pathlib import Path


API = "https://www.j-shis.bosai.go.jp/map/api/sstrct/V4/meshinfo.geojson"
SOURCE = "NIED J-SHIS shallow-subsurface V4 AVS30"
SOURCE_URL = "https://www.j-shis.bosai.go.jp/api-sstruct-meshinfo"


def fetch_avs(station: list, attempts: int = 4) -> list | None:
    code, name, prefecture, latitude, longitude, _network, area_code, area_name, *_ = station
    query = urllib.parse.urlencode(
        {
            "position": f"{float(longitude):.8f},{float(latitude):.8f}",
            "epsg": "4326",
            "attr": "AVS",
        }
    )
    error = None
    for attempt in range(attempts):
        try:
            request = urllib.request.Request(
                f"{API}?{query}",
                headers={"User-Agent": "QuakeDeck-ground-resource-builder/1.0"},
            )
            with urllib.request.urlopen(request, timeout=30) as response:
                payload = json.load(response)
            properties = payload["features"][0]["properties"]
            return [
                code,
                area_code,
                area_name,
                prefecture,
                name,
                float(latitude),
                float(longitude),
                float(properties["AVS"]),
                properties.get("meshcode", ""),
            ]
        except urllib.error.HTTPError as exc:
            if exc.code == 404:
                return None
            error = exc
            time.sleep(0.5 * (attempt + 1))
        except Exception as exc:  # noqa: BLE001 - retry and report exact station
            error = exc
            time.sleep(0.5 * (attempt + 1))
    raise RuntimeError(f"{code} {name}: {error}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--stations",
        default="app/src/main/res/raw/jma_intensity_stations.json",
    )
    parser.add_argument(
        "--output",
        default="app/src/main/res/raw/local_eew_station_avs30.gz",
    )
    parser.add_argument("--workers", type=int, default=6)
    args = parser.parse_args()

    station_payload = json.loads(Path(args.stations).read_text(encoding="utf-8"))
    stations = station_payload["stations"]
    rows: list[list | None] = [None] * len(stations)

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
        pending = {
            executor.submit(fetch_avs, station): index
            for index, station in enumerate(stations)
        }
        for completed, future in enumerate(concurrent.futures.as_completed(pending), 1):
            rows[pending[future]] = future.result()
            if completed % 250 == 0 or completed == len(stations):
                print(f"Fetched {completed}/{len(stations)} station meshes", flush=True)

    result = {
        "version": 1,
        "source": SOURCE,
        "sourceUrl": SOURCE_URL,
        "apiVersion": "V4",
        "stationSourceBlob": station_payload.get("sourceBlob"),
        "columns": [
            "stationCode",
            "areaCode",
            "areaNameJa",
            "prefectureJa",
            "stationNameJa",
            "latitude",
            "longitude",
            "avs30MetersPerSecond",
            "meshCode250m",
        ],
        "missingStationCodes": [
            stations[index][0] for index, row in enumerate(rows) if row is None
        ],
        "stations": [row for row in rows if row is not None],
    }
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(output, "wt", encoding="utf-8", compresslevel=9) as handle:
        json.dump(result, handle, ensure_ascii=False, separators=(",", ":"))
        handle.write("\n")
    print(f"Wrote {output} ({output.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
