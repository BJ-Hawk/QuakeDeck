#!/usr/bin/env python3
"""Build QuakeDeck's 47-prefecture base map from JMA prefecture forecast GIS."""

from __future__ import annotations

import argparse
import gzip
import json
import struct
import zipfile
from collections import defaultdict
from pathlib import Path

from shapely import make_valid
from shapely.geometry import MultiPolygon, Point, Polygon
from shapely.ops import unary_union
from shapely.strtree import STRtree


QUANTIZATION = 100_000
DEFAULT_TOLERANCE = 0.009  # Roughly 1 km, matching the replaced low-zoom asset.
DEFAULT_MINIMUM_AREA = 0.000001
MINAMITORISHIMA_BOUNDS = (153.8, 24.1, 154.1, 24.5)

PREFECTURES = [
    "北海道", "青森県", "岩手県", "宮城県", "秋田県", "山形県", "福島県", "茨城県",
    "栃木県", "群馬県", "埼玉県", "千葉県", "東京都", "神奈川県", "新潟県", "富山県",
    "石川県", "福井県", "山梨県", "長野県", "岐阜県", "静岡県", "愛知県", "三重県",
    "滋賀県", "京都府", "大阪府", "兵庫県", "奈良県", "和歌山県", "鳥取県", "島根県",
    "岡山県", "広島県", "山口県", "徳島県", "香川県", "愛媛県", "高知県", "福岡県",
    "佐賀県", "長崎県", "熊本県", "大分県", "宮崎県", "鹿児島県", "沖縄県",
]


def dbf_rows(archive: zipfile.ZipFile) -> list[dict[str, str]]:
    member = next(item for item in archive.infolist() if item.filename.lower().endswith(".dbf"))
    data = archive.read(member)
    count = struct.unpack_from("<I", data, 4)[0]
    header_length = struct.unpack_from("<H", data, 8)[0]
    record_length = struct.unpack_from("<H", data, 10)[0]
    fields = []
    offset = 32
    while data[offset] != 0x0D:
        name = data[offset:offset + 11].split(b"\0", 1)[0].decode("ascii")
        fields.append((name, data[offset + 16]))
        offset += 32
    rows = []
    for index in range(count):
        record = data[
            header_length + index * record_length:
            header_length + (index + 1) * record_length
        ]
        position = 1
        row = {}
        for name, length in fields:
            row[name] = record[position:position + length].rstrip(b" \0").decode("utf-8")
            position += length
        rows.append(row)
    return rows


def signed_area(points: list[tuple[float, float]]) -> float:
    return sum(
        first[0] * second[1] - second[0] * first[1]
        for first, second in zip(points, points[1:] + points[:1])
    ) / 2.0


def decode_polygon_record(data: bytes) -> list[list[tuple[float, float]]]:
    if struct.unpack_from("<i", data, 0)[0] != 5:
        raise ValueError("JMA source contains a non-polygon record")
    part_count, point_count = struct.unpack_from("<2i", data, 36)
    parts = list(struct.unpack_from(f"<{part_count}i", data, 44))
    parts.append(point_count)
    point_offset = 44 + part_count * 4
    return [
        [
            struct.unpack_from("<2d", data, point_offset + point_index * 16)
            for point_index in range(start, end)
        ]
        for start, end in zip(parts, parts[1:])
    ]


def polygon_geometry(rings: list[list[tuple[float, float]]]):
    candidates = [
        (ring[:-1] if len(ring) > 1 and ring[0] == ring[-1] else ring)
        for ring in rings
    ]
    candidates = [ring for ring in candidates if len(ring) >= 3]
    if not candidates:
        return MultiPolygon()
    outer_sign = 1 if signed_area(max(candidates, key=lambda ring: abs(signed_area(ring)))) > 0 else -1
    shells = [ring for ring in candidates if signed_area(ring) * outer_sign > 0]
    holes = [ring for ring in candidates if signed_area(ring) * outer_sign < 0]
    shell_polygons = [Polygon(shell) for shell in shells]
    hole_groups: dict[int, list[list[tuple[float, float]]]] = defaultdict(list)
    if holes and shell_polygons:
        tree = STRtree(shell_polygons)
        for hole in holes:
            owners = tree.query(Point(hole[0]), predicate="within")
            if len(owners):
                owner = min(owners, key=lambda index: shell_polygons[index].area)
                hole_groups[int(owner)].append(hole)
    polygons = [
        Polygon(shell, hole_groups[index])
        for index, shell in enumerate(shells)
    ]
    return make_valid(MultiPolygon(polygons))


def prefecture_name(row: dict[str, str]) -> str:
    prefix = row["code"][:2]
    if prefix == "01":
        return "北海道"
    if prefix == "46":
        return "鹿児島県"
    if prefix == "47":
        return "沖縄県"
    return row["name"]


def encode_ring(points) -> list[int]:
    encoded = []
    previous_x = previous_y = 0
    for longitude, latitude in points:
        x = round(longitude * QUANTIZATION)
        y = round(latitude * QUANTIZATION)
        encoded.extend((x - previous_x, y - previous_y))
        previous_x, previous_y = x, y
    return encoded


def is_minamitorishima(polygon: Polygon) -> bool:
    point = polygon.representative_point()
    left, bottom, right, top = MINAMITORISHIMA_BOUNDS
    return left <= point.x <= right and bottom <= point.y <= top


def encoded_parts(name: str, geometry, minimum_area: float) -> list[list[int]]:
    polygons = [geometry] if isinstance(geometry, Polygon) else list(geometry.geoms)
    if name == "東京都":
        polygons = [polygon for polygon in polygons if not is_minamitorishima(polygon)]
    retained = [polygon for polygon in polygons if polygon.area >= minimum_area]
    if not retained and polygons:
        retained = [max(polygons, key=lambda polygon: polygon.area)]
    parts = []
    for polygon in retained:
        exterior = list(polygon.exterior.coords)[:-1]
        if len(exterior) >= 3:
            parts.append(encode_ring(exterior))
        for interior in polygon.interiors:
            hole = list(interior.coords)[:-1]
            if len(hole) >= 3 and Polygon(hole).area >= minimum_area:
                parts.append(encode_ring(hole))
    return parts


def convert(source: Path, output: Path, tolerance: float, minimum_area: float) -> None:
    by_prefecture = defaultdict(list)
    source_points = 0
    with zipfile.ZipFile(source) as archive:
        rows = dbf_rows(archive)
        shp_member = next(
            item for item in archive.infolist() if item.filename.lower().endswith(".shp")
        )
        with archive.open(shp_member) as shp:
            shp.read(100)
            row_index = 0
            while record_header := shp.read(8):
                _, length_words = struct.unpack(">2i", record_header)
                rings = decode_polygon_record(shp.read(length_words * 2))
                row = rows[row_index]
                row_index += 1
                source_points += sum(map(len, rings))
                by_prefecture[prefecture_name(row)].append(polygon_geometry(rings))

    if set(by_prefecture) != set(PREFECTURES):
        raise ValueError(f"Expected 47 prefectures, got {sorted(by_prefecture)}")
    areas = []
    retained_points = 0
    for name in PREFECTURES:
        merged = unary_union(by_prefecture[name])
        simplified = merged.simplify(tolerance, preserve_topology=True)
        parts = encoded_parts(name, simplified, minimum_area)
        retained_points += sum(len(part) // 2 for part in parts)
        areas.append([name, parts])
    absolute_points = []
    for _, parts in areas:
        for part in parts:
            longitude = latitude = 0
            for offset in range(0, len(part), 2):
                longitude += part[offset]
                latitude += part[offset + 1]
                absolute_points.append((longitude / QUANTIZATION, latitude / QUANTIZATION))
    bounds = [
        min(point[0] for point in absolute_points),
        min(point[1] for point in absolute_points),
        max(point[0] for point in absolute_points),
        max(point[1] for point in absolute_points),
    ]
    payload = {
        "version": 4,
        "source": "JMA 20190125 AreaForecastLocalM prefecture GIS",
        "quantization": QUANTIZATION,
        "bounds": bounds,
        "areas": areas,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", compresslevel=9, fileobj=raw, mtime=0) as compressed:
            compressed.write(json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode())
    print(
        f"wrote {output}: 64 JMA forecast records -> 47 prefectures, "
        f"{source_points} source points -> {retained_points} retained points, "
        f"{output.stat().st_size} bytes"
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--tolerance-degrees", type=float, default=DEFAULT_TOLERANCE)
    parser.add_argument("--minimum-ring-area-degrees2", type=float, default=DEFAULT_MINIMUM_AREA)
    args = parser.parse_args()
    convert(args.source, args.output, args.tolerance_degrees, args.minimum_ring_area_degrees2)


if __name__ == "__main__":
    main()
