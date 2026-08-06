#!/usr/bin/env python3
"""Precompile small JMA fine/prefecture paths from reporting-area source rings."""

from __future__ import annotations

import argparse
import gzip
import json
import struct
from pathlib import Path

from build_classified_municipality_boundaries import make_matcher, matches, read_overlay_segments
from build_municipality_topology import canonical, write_paths


MAGIC = b"QDBP"
VERSION = 1


def read_quake_area_rings(path: Path):
    with gzip.open(path, "rt", encoding="utf-8") as handle:
        root = json.load(handle)
    if not root.get("closed"):
        raise ValueError("JMA reporting areas must contain closed paths")
    areas = []
    for area in root["areas"]:
        rings = []
        for encoded in area[2]:
            x, y = encoded[0], encoded[1]
            ring = [(x, y)]
            for dx, dy in zip(encoded[2::2], encoded[3::2]):
                x += dx
                y += dy
                ring.append((x, y))
            rings.append(ring)
        areas.append(rings)
    return root["quantization"], areas


def split_ring(ring, flags, expected):
    count = len(ring)
    if all(flag == expected for flag in flags):
        return [ring + [ring[0]]]
    paths = []
    for start in range(count):
        if flags[start] != expected or flags[(start - 1) % count] == expected:
            continue
        path = [ring[start]]
        edge = start
        while flags[edge] == expected:
            path.append(ring[(edge + 1) % count])
            edge = (edge + 1) % count
        paths.append(path)
    return paths


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("areas", type=Path)
    parser.add_argument("prefecture_overlay", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--match-tolerance-units", type=float, default=180.0)
    args = parser.parse_args()

    quantization, areas = read_quake_area_rings(args.areas)
    prefecture = make_matcher(read_overlay_segments(args.prefecture_overlay, quantization))
    owned = set()
    compiled, owner_count, duplicate_count = [], 0, 0
    for area in areas:
        fine_paths, prefecture_paths = [], []
        for ring in area:
            flags = []
            for first, second in zip(ring, ring[1:] + ring[:1]):
                if not matches((first, second), prefecture, args.match_tolerance_units):
                    flags.append(0)
                    continue
                edge = canonical(first, second)
                if edge in owned:
                    flags.append(2)
                    duplicate_count += 1
                else:
                    owned.add(edge)
                    flags.append(1)
                    owner_count += 1
            fine_paths.extend(split_ring(ring, flags, expected=0))
            prefecture_paths.extend(split_ring(ring, flags, expected=1))
        compiled.append((fine_paths, prefecture_paths))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(args.output, "wb", compresslevel=9) as handle:
        handle.write(MAGIC)
        handle.write(struct.pack(">III", VERSION, quantization, len(compiled)))
        for fine_paths, prefecture_paths in compiled:
            write_paths(handle, fine_paths)
            write_paths(handle, prefecture_paths)
    print(
        f"{len(compiled)} source path groups: {owner_count} owned prefecture edges, "
        f"{duplicate_count} duplicate prefecture edges -> {args.output}"
    )


if __name__ == "__main__":
    main()
