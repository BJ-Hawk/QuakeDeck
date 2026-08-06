#!/usr/bin/env python3
"""Classify shared municipal arcs into mutually exclusive border hierarchies."""

from __future__ import annotations

import argparse
import gzip
import json
import math
from pathlib import Path

from scipy.spatial import cKDTree

from build_municipality_topology import build_chunks, read_source, write_target


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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("municipalities", type=Path)
    parser.add_argument("prefecture_overlay", type=Path)
    parser.add_argument("warning_overlay", type=Path)
    parser.add_argument("fine_output", type=Path)
    parser.add_argument("warning_output", type=Path)
    parser.add_argument("prefecture_output", type=Path)
    parser.add_argument("--match-tolerance-units", type=float, default=180.0)
    args = parser.parse_args()

    quantization, rings, _ = read_source(args.municipalities)
    unique, _, _ = build_chunks(quantization, rings)
    edges = sorted(unique)
    prefecture = make_matcher(read_overlay_segments(args.prefecture_overlay, quantization))
    warning = make_matcher(read_overlay_segments(args.warning_overlay, quantization))
    classified = {"fine": [], "warning": [], "prefecture": []}
    for edge in edges:
        if matches(edge, prefecture, args.match_tolerance_units):
            classified["prefecture"].append(edge)
        elif matches(edge, warning, args.match_tolerance_units):
            classified["warning"].append(edge)
        else:
            classified["fine"].append(edge)

    for name, output in (
        ("fine", args.fine_output),
        ("warning", args.warning_output),
        ("prefecture", args.prefecture_output),
    ):
        output.parent.mkdir(parents=True, exist_ok=True)
        _, chunks, overflow = build_chunks(quantization, [list(edge) for edge in classified[name]])
        write_target(output, quantization, chunks, overflow)
    print(
        f"{len(edges)} total shared edges: fine={len(classified['fine'])}, "
        f"warning={len(classified['warning'])}, prefecture={len(classified['prefecture'])}"
    )


if __name__ == "__main__":
    main()
