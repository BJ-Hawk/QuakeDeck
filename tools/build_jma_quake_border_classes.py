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


def edge_id(edge):
    first, second = canonical(*edge)
    return f"{first[0]},{first[1]}:{second[0]},{second[1]}"


def load_overrides(path: Path | None):
    if path is None or not path.is_file():
        return {}
    payload = json.loads(path.read_text(encoding="utf-8"))
    return payload if isinstance(payload, dict) else {}


def read_quake_area_rings(path: Path, overrides):
    with gzip.open(path, "rt", encoding="utf-8") as handle:
        root = json.load(handle)
    if not root.get("closed"):
        raise ValueError("JMA reporting areas must contain closed paths")
    geometry = {
        str(item.get("code")): item
        for item in overrides.get("geometryAreas", [])
        if isinstance(item, dict) and item.get("code") and isinstance(item.get("rings"), list)
    }
    areas = []
    geometry_applied = 0
    for area in root["areas"]:
        code = str(area[0])
        override = geometry.get(code)
        if override is not None:
            rings = [
                [(int(point[0]), int(point[1])) for point in ring if len(point) >= 2]
                for ring in override["rings"]
            ]
            rings = [ring for ring in rings if len(ring) >= 3]
            if rings:
                geometry_applied += 1
                areas.append((code, rings))
                continue
        rings = []
        for encoded in area[2]:
            x, y = encoded[0], encoded[1]
            ring = [(x, y)]
            for dx, dy in zip(encoded[2::2], encoded[3::2]):
                x += dx
                y += dy
                ring.append((x, y))
            if len(ring) > 1 and ring[0] == ring[-1]:
                ring.pop()
            rings.append(ring)
        areas.append((code, rings))
    return root["quantization"], areas, geometry_applied


def class_targets(overrides):
    result = {
        str(item.get("id")): str(item.get("class"))
        for item in overrides.get("geometryEdgeClasses", [])
        if isinstance(item, dict)
        and item.get("id")
        and item.get("class") in {"fine", "prefecture", "none"}
    }
    result.update(
        {
            str(item.get("id")): str(item.get("to"))
            for item in overrides.get("overrides", [])
            if isinstance(item, dict)
            and item.get("id")
            and item.get("to") in {"fine", "prefecture", "none"}
        }
    )
    return result


def classify_edge_owners(areas):
    owners = {}
    for area_index, (_code, area_rings) in enumerate(areas):
        for ring in area_rings:
            for first, second in zip(ring, ring[1:] + ring[:1]):
                if first == second:
                    continue
                edge = canonical(first, second)
                owners.setdefault(edge, set()).add(area_index)
    return owners


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
    parser.add_argument("--match-tolerance-units", type=float)
    parser.add_argument("--overrides", type=Path)
    args = parser.parse_args()

    overrides = load_overrides(args.overrides)
    quantization, areas, geometry_applied = read_quake_area_rings(args.areas, overrides)
    override_classes = class_targets(overrides)
    match_tolerance = (
        args.match_tolerance_units
        if args.match_tolerance_units is not None
        else 180.0 * quantization / 100_000.0
    )
    prefecture = make_matcher(read_overlay_segments(args.prefecture_overlay, quantization))
    edge_owners = classify_edge_owners(areas)
    owner_histogram = {}
    for edge_owner_set in edge_owners.values():
        owner_count = len(edge_owner_set)
        owner_histogram[owner_count] = owner_histogram.get(owner_count, 0) + 1

    emitted = set()
    seen_override_ids = set()
    compiled = []
    duplicate_count = 0
    suppressed_single_owner = 0
    emitted_fine = 0
    emitted_prefecture = 0
    for _code, area_rings in areas:
        fine_paths, prefecture_paths = [], []
        for ring in area_rings:
            flags = []
            for first, second in zip(ring, ring[1:] + ring[:1]):
                identifier = edge_id((first, second))
                target_class = override_classes.get(identifier)
                if target_class is not None:
                    seen_override_ids.add(identifier)
                edge = canonical(first, second)
                if target_class == "none":
                    flags.append(3)
                    continue
                if len(edge_owners.get(edge, ())) != 2:
                    flags.append(3)
                    suppressed_single_owner += 1
                    continue
                if edge in emitted:
                    flags.append(2)
                    duplicate_count += 1
                    continue

                is_prefecture = (
                    target_class == "prefecture"
                    if target_class is not None
                    else matches((first, second), prefecture, match_tolerance)
                )
                emitted.add(edge)
                if is_prefecture:
                    flags.append(1)
                    emitted_prefecture += 1
                else:
                    flags.append(0)
                    emitted_fine += 1
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
    unmatched = sorted(set(override_classes) - seen_override_ids)
    one_owner = owner_histogram.get(1, 0)
    two_owner = owner_histogram.get(2, 0)
    non_manifold = sum(count for owners, count in owner_histogram.items() if owners > 2)
    summary = (
        f"{len(compiled)} source path groups: one-owner={one_owner}, "
        f"two-owner={two_owner}, >2-owner={non_manifold}; "
        f"emitted fine={emitted_fine}, prefecture={emitted_prefecture}, "
        f"duplicate occurrences suppressed={duplicate_count}, "
        f"single-owner occurrences suppressed={suppressed_single_owner}, "
        f"geometry areas applied={geometry_applied}"
    )
    if args.overrides is not None:
        summary += f", class overrides applied={len(seen_override_ids)}"
        if unmatched:
            summary += f", unmatched class overrides={len(unmatched)}"
    print(summary + f" -> {args.output}")


if __name__ == "__main__":
    main()
