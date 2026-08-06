#!/usr/bin/env python3
"""Build a coarse, phone-appropriate N03 prefecture vector resource.

The original TopoJSON is intentionally coastline-accurate, but its 105k tiny
arcs are excessive once JMA regions replace it at 6.5x. This tool joins each
prefecture ring before simplifying it, so it can remove the artificial arc
fragmentation that a per-arc simplifier cannot touch.
"""
from __future__ import annotations

import argparse
import gzip
import json
import math
from pathlib import Path


Point = tuple[float, float]


def squared_distance_to_segment(point: Point, start: Point, end: Point) -> float:
    dx, dy = end[0] - start[0], end[1] - start[1]
    length_squared = dx * dx + dy * dy
    if length_squared == 0.0:
        return (point[0] - start[0]) ** 2 + (point[1] - start[1]) ** 2
    projection = ((point[0] - start[0]) * dx + (point[1] - start[1]) * dy) / length_squared
    projection = max(0.0, min(1.0, projection))
    closest = (start[0] + dx * projection, start[1] + dy * projection)
    return (point[0] - closest[0]) ** 2 + (point[1] - closest[1]) ** 2


def simplify_open(points: list[Point], tolerance: float) -> list[Point]:
    if len(points) <= 2:
        return points
    retained = [False] * len(points)
    retained[0] = retained[-1] = True
    pending = [(0, len(points) - 1)]
    tolerance_squared = tolerance * tolerance
    while pending:
        start, end = pending.pop()
        farthest = -1
        greatest_distance = tolerance_squared
        for index in range(start + 1, end):
            distance = squared_distance_to_segment(points[index], points[start], points[end])
            if distance > greatest_distance:
                greatest_distance = distance
                farthest = index
        if farthest >= 0:
            retained[farthest] = True
            pending.extend(((start, farthest), (farthest, end)))
    return [point for index, point in enumerate(points) if retained[index]]


def simplify_ring(ring: list[Point], tolerance: float) -> list[Point]:
    """Simplify a closed ring without the degenerate start-to-start segment."""
    if len(ring) >= 2 and ring[0] == ring[-1]:
        ring = ring[:-1]
    if len(ring) < 4:
        return ring

    first = ring[0]
    split = max(
        range(1, len(ring)),
        key=lambda index: (ring[index][0] - first[0]) ** 2 + (ring[index][1] - first[1]) ** 2,
    )
    first_half = simplify_open(ring[: split + 1], tolerance)
    second_half = simplify_open(ring[split:] + [first], tolerance)
    simplified = first_half + second_half[1:-1]
    return simplified if len(simplified) >= 3 else ring


def decode_arcs(topology: dict) -> list[list[Point]]:
    scale = topology["transform"]["scale"]
    translate = topology["transform"]["translate"]
    decoded = []
    for encoded_arc in topology["arcs"]:
        x = y = 0
        arc = []
        for delta_x, delta_y in encoded_arc:
            x += delta_x
            y += delta_y
            arc.append((x * scale[0] + translate[0], y * scale[1] + translate[1]))
        decoded.append(arc)
    return decoded


def ring_points(refs: list[int], arcs: list[list[Point]]) -> list[Point]:
    ring: list[Point] = []
    for reference in refs:
        arc = arcs[reference] if reference >= 0 else list(reversed(arcs[-reference - 1]))
        if not arc:
            continue
        ring.extend(arc if not ring or ring[-1] != arc[0] else arc[1:])
    return ring


def polygons(geometry: dict) -> list[list[list[int]]]:
    if geometry["type"] == "Polygon":
        return [geometry["arcs"]]
    if geometry["type"] == "MultiPolygon":
        return geometry["arcs"]
    return []


def encode_ring(ring: list[Point], quantization: int) -> list[int]:
    encoded: list[int] = []
    previous_x = previous_y = 0
    for longitude, latitude in ring:
        x = round(longitude * quantization)
        y = round(latitude * quantization)
        encoded.extend((x - previous_x, y - previous_y))
        previous_x, previous_y = x, y
    return encoded


def ring_area(ring: list[Point]) -> float:
    return abs(sum(
        start[0] * end[1] - end[0] * start[1]
        for start, end in zip(ring, ring[1:] + ring[:1])
    )) / 2.0


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument(
        "--tolerance-degrees",
        type=float,
        default=0.0015,
        help="Maximum ring deviation; 0.0015 degrees is suitable below 6.5x.",
    )
    parser.add_argument(
        "--minimum-ring-area-degrees2",
        type=float,
        default=0.000001,
        help="Discard sub-hectare N03 islets, retaining each prefecture's largest part.",
    )
    args = parser.parse_args()
    if not math.isfinite(args.tolerance_degrees) or args.tolerance_degrees <= 0.0:
        raise ValueError("--tolerance-degrees must be positive and finite")
    if (
        not math.isfinite(args.minimum_ring_area_degrees2) or
        args.minimum_ring_area_degrees2 < 0.0
    ):
        raise ValueError("--minimum-ring-area-degrees2 must be finite and non-negative")

    with gzip.open(args.input, "rt", encoding="utf-8") as source:
        topology = json.load(source)
    arcs = decode_arcs(topology)
    min_longitude = min(point[0] for arc in arcs for point in arc)
    max_longitude = max(point[0] for arc in arcs for point in arc)
    min_latitude = min(point[1] for arc in arcs for point in arc)
    max_latitude = max(point[1] for arc in arcs for point in arc)

    quantization = 100_000
    areas = []
    original_points = 0
    simplified_points = 0
    for geometry in topology["objects"]["data"]["geometries"]:
        name = geometry.get("properties", {}).get("name", "")
        if not name:
            continue
        candidate_parts = []
        for polygon in polygons(geometry):
            for refs in polygon:
                original = ring_points(refs, arcs)
                simplified = simplify_ring(original, args.tolerance_degrees)
                if len(simplified) < 3:
                    continue
                original_points += len(original)
                simplified_points += len(simplified)
                candidate_parts.append((ring_area(simplified), simplified))
        parts = [
            encode_ring(ring, quantization)
            for area, ring in candidate_parts
            if area >= args.minimum_ring_area_degrees2
        ]
        if not parts and candidate_parts:
            # Remote-island prefectures must retain at least their principal
            # island even if a future threshold is set too aggressively.
            parts = [encode_ring(max(candidate_parts, key=lambda item: item[0])[1], quantization)]
        if parts:
            areas.append([name, parts])

    payload = {
        "version": 1,
        "quantization": quantization,
        "bounds": [min_longitude, min_latitude, max_longitude, max_latitude],
        "areas": areas,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(args.output, "wt", encoding="utf-8", compresslevel=9) as output:
        json.dump(payload, output, ensure_ascii=False, separators=(",", ":"))
    print(
        f"wrote {args.output}: {original_points} source ring points -> "
        f"{sum(sum(len(part) // 2 for part in parts) for _, parts in areas)} retained points, "
        f"{sum(len(parts) for _, parts in areas)} rings, {args.output.stat().st_size} bytes"
    )


if __name__ == "__main__":
    main()
