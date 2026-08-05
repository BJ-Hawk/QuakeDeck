#!/usr/bin/env python3
"""Precompute sea-facing prefecture boundaries for QuakeDeck.

The Android app previously rasterized all Japanese land polygons into a 4096x4096
mask and flood-filled the ocean on every cold launch. This tool performs the same
classification during development and writes compact gzip-compressed binary paths.
"""

from __future__ import annotations

import argparse
import gzip
import json
import math
import struct
from pathlib import Path
from typing import Iterable

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

MASK_SIZE = 4096
PADDING = 2.0
QUANTIZATION = 100_000_000
MAGIC = 0x5144434C  # QDCL
VERSION = 1

Point = tuple[float, float]


def project_geo(latitude: float, longitude: float) -> Point:
    clamped = max(-85.05112878, min(85.05112878, latitude))
    lat_rad = math.radians(clamped)
    return math.radians(longitude), -math.log(math.tan(math.pi / 4.0 + lat_rad / 2.0))


def decode_topology(path: Path):
    with gzip.open(path, "rt", encoding="utf-8") as source:
        root = json.load(source)
    transform = root["transform"]
    sx, sy = transform["scale"]
    tx, ty = transform["translate"]

    decoded: list[list[Point]] = []
    min_x = min_y = float("inf")
    max_x = max_y = float("-inf")
    for encoded_arc in root["arcs"]:
        qx = qy = 0
        points: list[Point] = []
        for dx, dy in encoded_arc:
            qx += dx
            qy += dy
            point = project_geo(qy * sy + ty, qx * sx + tx)
            points.append(point)
            min_x = min(min_x, point[0])
            min_y = min(min_y, point[1])
            max_x = max(max_x, point[0])
            max_y = max(max_y, point[1])
        decoded.append(points)
    geometries = root["objects"]["data"]["geometries"]
    return decoded, geometries, min_x, min_y, max_x, max_y


def oriented_arc(decoded: list[list[Point]], ref: int) -> list[Point]:
    source = decoded[ref if ref >= 0 else -ref - 1]
    return source if ref >= 0 else list(reversed(source))


def ring_points(refs: Iterable[int], decoded: list[list[Point]]) -> list[Point]:
    result: list[Point] = []
    for ref in refs:
        arc = oriented_arc(decoded, ref)
        if not arc:
            continue
        if result and result[-1] == arc[0]:
            result.extend(arc[1:])
        else:
            result.extend(arc)
    return result


def geometry_polygons(geometry: dict) -> list[list[list[int]]]:
    if geometry["type"] == "Polygon":
        return [geometry["arcs"]]
    if geometry["type"] == "MultiPolygon":
        return geometry["arcs"]
    return []


def build_masks(decoded, geometries, min_x, min_y, max_x, max_y):
    span_x = max(max_x - min_x, 1e-9)
    span_y = max(max_y - min_y, 1e-9)
    scale = min((MASK_SIZE - PADDING * 2.0) / span_x, (MASK_SIZE - PADDING * 2.0) / span_y)
    offset_x = PADDING - min_x * scale
    offset_y = PADDING - min_y * scale

    image = Image.new("L", (MASK_SIZE, MASK_SIZE), 0)
    draw = ImageDraw.Draw(image)

    def raster(points: list[Point]) -> list[tuple[float, float]]:
        return [(x * scale + offset_x, y * scale + offset_y) for x, y in points]

    # Draw each polygon outer ring as land and inner rings as holes. Prefecture
    # polygons form a union because every outer ring is painted onto one mask.
    for geometry in geometries:
        for polygon in geometry_polygons(geometry):
            if not polygon:
                continue
            outer = ring_points(polygon[0], decoded)
            if len(outer) >= 3:
                draw.polygon(raster(outer), fill=255)
            for hole_refs in polygon[1:]:
                hole = ring_points(hole_refs, decoded)
                if len(hole) >= 3:
                    draw.polygon(raster(hole), fill=0)

    land = np.asarray(image, dtype=np.uint8) >= 128
    water = ~land
    seeds = np.zeros_like(water, dtype=bool)
    seeds[0, :] = water[0, :]
    seeds[-1, :] = water[-1, :]
    seeds[:, 0] = water[:, 0]
    seeds[:, -1] = water[:, -1]
    ocean = ndimage.binary_propagation(seeds, mask=water)
    return land, ocean, scale, offset_x, offset_y


def is_coast_segment(a: Point, b: Point, land, ocean, scale, offset_x, offset_y) -> bool:
    ax, ay = a[0] * scale + offset_x, a[1] * scale + offset_y
    bx, by = b[0] * scale + offset_x, b[1] * scale + offset_y
    dx, dy = bx - ax, by - ay
    length = math.hypot(dx, dy)
    if length < 0.20:
        return False
    mx, my = (ax + bx) / 2.0, (ay + by) / 2.0
    nx, ny = -dy / length, dx / length

    def sample(array, x: float, y: float, outside: bool) -> bool:
        # Match Kotlin/Java roundToInt for non-negative pixel coordinates.
        ix, iy = math.floor(x + 0.5), math.floor(y + 0.5)
        if ix < 0 or ix >= MASK_SIZE or iy < 0 or iy >= MASK_SIZE:
            return outside
        return bool(array[iy, ix])

    confirmations = 0
    for distance in (1.25, 2.5, 4.0):
        x1, y1 = mx + nx * distance, my + ny * distance
        x2, y2 = mx - nx * distance, my - ny * distance
        if (
            sample(land, x1, y1, False) and sample(ocean, x2, y2, True)
        ) or (
            sample(ocean, x1, y1, True) and sample(land, x2, y2, False)
        ):
            confirmations += 1
    return confirmations >= 2


def collect_coastlines(decoded, geometries, land, ocean, scale, offset_x, offset_y):
    result: dict[str, list[list[Point]]] = {}
    seen: set[tuple[str, int]] = set()

    for geometry in geometries:
        prefecture = geometry.get("properties", {}).get("name", "")
        if not prefecture:
            continue
        for polygon in geometry_polygons(geometry):
            if not polygon:
                continue
            for ref in polygon[0]:  # outer ring only; lakes are not coast
                arc_index = ref if ref >= 0 else -ref - 1
                key = (prefecture, arc_index)
                if key in seen or arc_index < 0 or arc_index >= len(decoded):
                    continue
                seen.add(key)
                arc = oriented_arc(decoded, ref)
                current: list[Point] = []
                for a, b in zip(arc, arc[1:]):
                    if is_coast_segment(a, b, land, ocean, scale, offset_x, offset_y):
                        if not current:
                            current = [a]
                        elif current[-1] != a:
                            result.setdefault(prefecture, []).append(current)
                            current = [a]
                        current.append(b)
                    elif current:
                        result.setdefault(prefecture, []).append(current)
                        current = []
                if current:
                    result.setdefault(prefecture, []).append(current)

    return {
        name: [segment for segment in segments if len(segment) >= 2]
        for name, segments in result.items()
        if any(len(segment) >= 2 for segment in segments)
    }


def write_binary(path: Path, coastlines: dict[str, list[list[Point]]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as raw_output, gzip.GzipFile(
        filename="",
        mode="wb",
        compresslevel=9,
        fileobj=raw_output,
        mtime=0,
    ) as output:
        output.write(struct.pack(">iiii", MAGIC, VERSION, QUANTIZATION, len(coastlines)))
        for name in sorted(coastlines):
            encoded = name.encode("utf-8")
            output.write(struct.pack(">i", len(encoded)))
            output.write(encoded)
            segments = coastlines[name]
            output.write(struct.pack(">i", len(segments)))
            for segment in segments:
                output.write(struct.pack(">i", len(segment)))
                for x, y in segment:
                    output.write(struct.pack(">ii", round(x * QUANTIZATION), round(y * QUANTIZATION)))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    decoded, geometries, min_x, min_y, max_x, max_y = decode_topology(args.input)
    land, ocean, scale, offset_x, offset_y = build_masks(
        decoded, geometries, min_x, min_y, max_x, max_y
    )
    coastlines = collect_coastlines(
        decoded, geometries, land, ocean, scale, offset_x, offset_y
    )
    write_binary(args.output, coastlines)
    segments = sum(len(parts) for parts in coastlines.values())
    points = sum(len(part) for parts in coastlines.values() for part in parts)
    print(f"wrote {args.output}: {len(coastlines)} prefectures, {segments} segments, {points} points")


if __name__ == "__main__":
    main()
