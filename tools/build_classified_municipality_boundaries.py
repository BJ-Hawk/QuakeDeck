#!/usr/bin/env python3
"""Classify shared municipal arcs into mutually exclusive border hierarchies."""

from __future__ import annotations

import argparse
import gzip
import json
import math
from pathlib import Path

from scipy.spatial import cKDTree

from build_municipality_topology import build_chunks, write_target
from build_topological_municipalities import read_areas


def read_overlay_segments(path: Path, target_quantization: int):
    with gzip.open(path, "rt", encoding="utf-8") as handle:
        root = json.load(handle)
    source_quantization = root["quantization"]
    scale = target_quantization / source_quantization
    segments = []
    for border in root["borders"]:
        for _, encoded in border[4]:
            x, y = encoded[0] * scale, encoded[1] * scale
            previous = (x, y)
            for dx, dy in zip(encoded[2::2], encoded[3::2]):
                current = (previous[0] + dx * scale, previous[1] + dy * scale)
                if current != previous:
                    segments.append((previous, current))
                previous = current
    return segments


def squared_distance_to_segment(point, first, second):
    vector_x, vector_y = second[0] - first[0], second[1] - first[1]
    length_squared = vector_x * vector_x + vector_y * vector_y
    if length_squared == 0:
        return (point[0] - first[0]) ** 2 + (point[1] - first[1]) ** 2
    t = ((point[0] - first[0]) * vector_x + (point[1] - first[1]) * vector_y) / length_squared
    t = max(0.0, min(1.0, t))
    nearest = (first[0] + t * vector_x, first[1] + t * vector_y)
    return (point[0] - nearest[0]) ** 2 + (point[1] - nearest[1]) ** 2


def make_matcher(segments):
    midpoints = [((a[0] + b[0]) / 2, (a[1] + b[1]) / 2) for a, b in segments]
    return cKDTree(midpoints), segments


def matches(edge, matcher, tolerance: float):
    tree, segments = matcher
    start, end = edge
    midpoint = ((start[0] + end[0]) / 2, (start[1] + end[1]) / 2)
    _, candidate_indexes = tree.query(midpoint, k=min(16, len(segments)))
    edge_x, edge_y = end[0] - start[0], end[1] - start[1]
    edge_length = math.hypot(edge_x, edge_y)
    if edge_length == 0:
        return False
    for index in candidate_indexes:
        first, second = segments[index]
        segment_x, segment_y = second[0] - first[0], second[1] - first[1]
        segment_length = math.hypot(segment_x, segment_y)
        if segment_length == 0:
            continue
        alignment = abs((edge_x * segment_x + edge_y * segment_y) / (edge_length * segment_length))
        if alignment < 0.80:
            continue
        if squared_distance_to_segment(midpoint, first, second) <= tolerance * tolerance:
            return True
    return False


def edge_id(edge):
    (ax, ay), (bx, by) = edge
    if (bx, by) < (ax, ay):
        ax, ay, bx, by = bx, by, ax, ay
    return f"{ax},{ay}:{bx},{by}"


def load_overrides(path: Path | None):
    if path is None or not path.is_file():
        return {}
    payload = json.loads(path.read_text(encoding="utf-8"))
    return payload if isinstance(payload, dict) else {}


def read_municipality_areas(path: Path, overrides):
    quantization, areas = read_areas(path)
    geometry = {
        str(item.get("code")): item
        for item in overrides.get("geometryAreas", [])
        if isinstance(item, dict) and item.get("code") and isinstance(item.get("rings"), list)
    }
    applied_geometry = 0
    result = []
    for code, name, area_rings in areas:
        override = geometry.get(str(code))
        if override is not None:
            replacement = []
            for ring in override["rings"]:
                points = [(int(point[0]), int(point[1])) for point in ring if len(point) >= 2]
                if len(points) >= 3:
                    replacement.append(points)
            if replacement:
                area_rings = replacement
                applied_geometry += 1
        result.append((code, name, area_rings))
    return quantization, result, applied_geometry


def classify_edge_owners(areas):
    owners = {}
    for area_index, (_code, _name, area_rings) in enumerate(areas):
        for ring in area_rings:
            for first, second in zip(ring, ring[1:] + ring[:1]):
                if first == second:
                    continue
                edge = (first, second) if first <= second else (second, first)
                owners.setdefault(edge, set()).add(area_index)
    return owners


def apply_class_overrides(classified, overrides):
    target_by_id = {
        str(item.get("id")): str(item.get("class"))
        for item in overrides.get("geometryEdgeClasses", [])
        if isinstance(item, dict)
        and item.get("id")
        and item.get("class") in {*classified, "none"}
    }
    target_by_id.update(
        {
            str(item.get("id")): str(item.get("to"))
            for item in overrides.get("overrides", [])
            if isinstance(item, dict)
            and item.get("id")
            and item.get("to") in {*classified, "none"}
        }
    )
    if not target_by_id:
        return 0, []

    edge_lookup = {}
    edge_classes = {}
    for name, edges in classified.items():
        for edge in edges:
            identifier = edge_id(edge)
            edge_lookup[identifier] = edge
            edge_classes[identifier] = name

    buckets = {name: [] for name in classified}
    for identifier, edge in edge_lookup.items():
        target = target_by_id.get(identifier, edge_classes[identifier])
        if target == "none":
            continue
        buckets[target].append(edge)

    unmatched = [identifier for identifier in target_by_id if identifier not in edge_lookup]
    for name, edges in buckets.items():
        classified[name] = sorted(edges)
    return len(target_by_id) - len(unmatched), unmatched


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("municipalities", type=Path)
    parser.add_argument("prefecture_overlay", type=Path)
    parser.add_argument("warning_overlay", type=Path)
    parser.add_argument("fine_output", type=Path)
    parser.add_argument("warning_output", type=Path)
    parser.add_argument("prefecture_output", type=Path)
    parser.add_argument("--match-tolerance-units", type=float)
    parser.add_argument("--overrides", type=Path)
    parser.add_argument(
        "--include-single-owner",
        action="store_true",
        help=(
            "Include one-owner polygon edges in rendered administrative-boundary resources. "
            "By default only exact two-owner shared edges are emitted; coastlines are rendered "
            "separately and one-owner internal tails are topology noise."
        ),
    )
    args = parser.parse_args()

    overrides = load_overrides(args.overrides)
    quantization, areas, geometry_applied = read_municipality_areas(
        args.municipalities,
        overrides,
    )
    match_tolerance = (
        args.match_tolerance_units
        if args.match_tolerance_units is not None
        else 180.0 * quantization / 100_000.0
    )
    edge_owners = classify_edge_owners(areas)
    owner_histogram = {}
    for edge_owner_set in edge_owners.values():
        owner_count = len(edge_owner_set)
        owner_histogram[owner_count] = owner_histogram.get(owner_count, 0) + 1

    if args.include_single_owner:
        edges = sorted(edge_owners)
    else:
        edges = sorted(edge for edge, edge_owner_set in edge_owners.items() if len(edge_owner_set) == 2)
    prefecture = make_matcher(read_overlay_segments(args.prefecture_overlay, quantization))
    warning = make_matcher(read_overlay_segments(args.warning_overlay, quantization))
    classified = {"fine": [], "warning": [], "prefecture": []}
    for edge in edges:
        if matches(edge, prefecture, match_tolerance):
            classified["prefecture"].append(edge)
        elif matches(edge, warning, match_tolerance):
            classified["warning"].append(edge)
        else:
            classified["fine"].append(edge)

    applied, unmatched = apply_class_overrides(classified, overrides)

    for name, output in (
        ("fine", args.fine_output),
        ("warning", args.warning_output),
        ("prefecture", args.prefecture_output),
    ):
        output.parent.mkdir(parents=True, exist_ok=True)
        _, chunks, overflow = build_chunks(quantization, [list(edge) for edge in classified[name]])
        write_target(output, quantization, chunks, overflow)
    single_owner = owner_histogram.get(1, 0)
    two_owner = owner_histogram.get(2, 0)
    non_manifold = sum(count for owners, count in owner_histogram.items() if owners > 2)
    summary = (
        f"{len(edge_owners)} unique polygon edges: one-owner={single_owner}, "
        f"two-owner={two_owner}, >2-owner={non_manifold}; "
        f"rendered={len(edges)}: fine={len(classified['fine'])}, "
        f"warning={len(classified['warning'])}, prefecture={len(classified['prefecture'])}"
    )
    if not args.include_single_owner:
        summary += f", suppressed one-owner edges={single_owner}"
    if args.overrides is not None:
        summary += f", geometry areas applied={geometry_applied}, class overrides applied={applied}"
        if unmatched:
            summary += f", unmatched class overrides={len(unmatched)}"
    print(summary)


if __name__ == "__main__":
    main()
