#!/usr/bin/env python3
"""Build a shared-boundary, topology-safe simplified N03 resource."""

from __future__ import annotations

import argparse
import gzip
import json
from pathlib import Path

from build_municipality_topology import canonical, chain_edges
from build_shared_municipality_topology import OUTPUT_SCALE, build
from build_simplified_n03 import decode_arcs, polygons, ring_area, ring_points


SOURCE_QUANTIZATION = 100_000


def encode_quantized_path(path):
    encoded = []
    previous_x = previous_y = 0
    for x, y in path:
        encoded.extend((x - previous_x, y - previous_y))
        previous_x, previous_y = x, y
    return encoded


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument(
        "--tolerance-degrees",
        type=float,
        default=0.009,
        help="Shared-network simplification tolerance; 0.009 degrees is about 1 km.",
    )
    parser.add_argument(
        "--minimum-ring-area-degrees2",
        type=float,
        default=0.000001,
        help="Match the active N03 builder's small-island filter.",
    )
    args = parser.parse_args()
    if args.tolerance_degrees <= 0:
        raise ValueError("--tolerance-degrees must be positive")

    with gzip.open(args.input, "rt", encoding="utf-8") as handle:
        topology = json.load(handle)
    arcs = decode_arcs(topology)
    source_areas = []
    for geometry in topology["objects"]["data"]["geometries"]:
        name = geometry.get("properties", {}).get("name", "")
        if not name:
            continue
        candidates = []
        for polygon in polygons(geometry):
            for references in polygon:
                ring = ring_points(references, arcs)
                if len(ring) >= 3:
                    candidates.append((ring_area(ring), ring))
        kept = [ring for area, ring in candidates if area >= args.minimum_ring_area_degrees2]
        if not kept and candidates:
            kept = [max(candidates, key=lambda item: item[0])[1]]
        rings = [
            [
                (round(longitude * SOURCE_QUANTIZATION), round(latitude * SOURCE_QUANTIZATION))
                for longitude, latitude in ring
            ]
            for ring in kept
        ]
        if rings:
            source_areas.append((name, name, rings))
    if len(source_areas) != 47:
        raise ValueError(f"Expected 47 prefectures, got {len(source_areas)}")

    simplified_areas, source_rings, faces = build(
        source_areas,
        args.tolerance_degrees * SOURCE_QUANTIZATION,
        preserve_topology=True,
    )
    quantization = SOURCE_QUANTIZATION * OUTPUT_SCALE
    unique_edges = set()
    boundary_groups = [[] for _ in simplified_areas]
    for area_index, (_, _, rings) in enumerate(simplified_areas):
        for ring in rings:
            for first, second in zip(ring, ring[1:] + ring[:1]):
                if first != second:
                    edge = canonical(first, second)
                    if edge not in unique_edges:
                        unique_edges.add(edge)
                        boundary_groups[area_index].append(edge)
    boundary_paths = [chain_edges(sorted(edges)) for edges in boundary_groups]

    points = sum(len(ring) for _, _, rings in simplified_areas for ring in rings)
    min_longitude = min(point[0] for arc in arcs for point in arc)
    max_longitude = max(point[0] for arc in arcs for point in arc)
    min_latitude = min(point[1] for arc in arcs for point in arc)
    max_latitude = max(point[1] for arc in arcs for point in arc)
    payload = {
        "version": 2,
        "quantization": quantization,
        "bounds": [min_longitude, min_latitude, max_longitude, max_latitude],
        "areas": [
            [name, [encode_quantized_path(ring) for ring in rings]]
            for _, name, rings in simplified_areas
        ],
        "boundaries": [
            [encode_quantized_path(path) for path in group]
            for group in boundary_paths
        ],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(args.output, "wt", encoding="utf-8", compresslevel=9) as handle:
        json.dump(payload, handle, ensure_ascii=False, separators=(",", ":"))
    print(
        f"{len(simplified_areas)} prefectures, {source_rings} source rings, {faces} faces, "
        f"{points} fill points, {len(unique_edges)} unique boundary edges in "
        f"{sum(map(len, boundary_paths))} contours across {len(boundary_paths)} paths, "
        f"{args.output.stat().st_size} bytes"
    )


if __name__ == "__main__":
    main()
