#!/usr/bin/env python3
"""Snap near-identical JMA municipal borders into one exact topology.

This preserves the original polygon rings, but canonicalises vertices that are
within a tiny source-coordinate tolerance across different municipalities and
removes only vertices that are within that same tolerance of a straight shared
edge. The output remains QDMB v2, so Android can load it with the normal
municipality parser while its neighbouring fill paths now meet exactly.
"""

from __future__ import annotations

import argparse
import gzip
import struct
from collections import Counter, defaultdict
from pathlib import Path


MAGIC = b"QDMB"


def read_exact(handle, count: int) -> bytes:
    result = handle.read(count)
    if len(result) != count:
        raise ValueError("Unexpected end of municipality resource")
    return result


def read_uvarint(handle) -> int:
    result = shift = 0
    while shift < 35:
        byte = read_exact(handle, 1)[0]
        result |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return result
        shift += 7
    raise ValueError("Malformed varint")


def read_svarint(handle) -> int:
    value = read_uvarint(handle)
    return (value >> 1) ^ -(value & 1)


def write_uvarint(handle, value: int) -> None:
    if value < 0:
        raise ValueError("Negative unsigned varint")
    while value >= 0x80:
        handle.write(bytes([(value & 0x7F) | 0x80]))
        value >>= 7
    handle.write(bytes([value]))


def write_svarint(handle, value: int) -> None:
    write_uvarint(handle, (value << 1) ^ (value >> 31))


def read_string(handle) -> str:
    return read_exact(handle, struct.unpack(">H", read_exact(handle, 2))[0]).decode("utf-8")


def write_string(handle, value: str) -> None:
    encoded = value.encode("utf-8")
    handle.write(struct.pack(">H", len(encoded)))
    handle.write(encoded)


def read_areas(path: Path):
    with gzip.open(path, "rb") as handle:
        if read_exact(handle, 4) != MAGIC:
            raise ValueError("Not a QDMB municipality resource")
        version, quantization, area_count = struct.unpack(">III", read_exact(handle, 12))
        if version not in (1, 2):
            raise ValueError(f"Unsupported QDMB version: {version}")
        areas = []
        for _ in range(area_count):
            code, name = read_string(handle), read_string(handle)
            part_count = read_uvarint(handle) if version >= 2 else struct.unpack(">I", read_exact(handle, 4))[0]
            parts = []
            for _ in range(part_count):
                point_count = read_uvarint(handle) if version >= 2 else struct.unpack(">I", read_exact(handle, 4))[0]
                x = y = 0
                ring = []
                for index in range(point_count):
                    dx = read_svarint(handle) if version >= 2 else struct.unpack(">i", read_exact(handle, 4))[0]
                    dy = read_svarint(handle) if version >= 2 else struct.unpack(">i", read_exact(handle, 4))[0]
                    x, y = (dx, dy) if index == 0 else (x + dx, y + dy)
                    ring.append((x, y))
                parts.append(ring)
            areas.append((code, name, parts))
    return quantization, areas


class UnionFind:
    def __init__(self, points):
        self.parent = {point: point for point in points}

    def find(self, point):
        root = point
        while self.parent[root] != root:
            root = self.parent[root]
        while point != root:
            parent = self.parent[point]
            self.parent[point] = root
            point = parent
        return root

    def union(self, first, second):
        first, second = self.find(first), self.find(second)
        if first != second:
            if first < second:
                self.parent[second] = first
            else:
                self.parent[first] = second


def canonical_vertices(areas, tolerance: int):
    owners = defaultdict(set)
    counts = Counter()
    for area_index, (_, _, parts) in enumerate(areas):
        for ring in parts:
            for point in ring:
                owners[point].add(area_index)
                counts[point] += 1

    union_find = UnionFind(owners)
    for x, y in sorted(owners):
        for dx in range(-tolerance, tolerance + 1):
            for dy in range(-tolerance, tolerance + 1):
                if dx == 0 and dy == 0 or dx * dx + dy * dy > tolerance * tolerance:
                    continue
                candidate = (x + dx, y + dy)
                if candidate <= (x, y) or candidate not in owners:
                    continue
                # Never collapse a detail point against itself. Nearby points
                # from separate municipalities are the imperfect shared edges.
                if owners[(x, y)].isdisjoint(owners[candidate]):
                    union_find.union((x, y), candidate)

    clusters = defaultdict(list)
    for point in owners:
        clusters[union_find.find(point)].append(point)
    canonical = {}
    for members in clusters.values():
        chosen = min(members, key=lambda point: (-counts[point], point))
        for point in members:
            canonical[point] = chosen
    return canonical, len(owners), len(clusters)


def near_straight(previous, current, following, tolerance: int) -> bool:
    vx, vy = following[0] - previous[0], following[1] - previous[1]
    wx, wy = current[0] - previous[0], current[1] - previous[1]
    length_squared = vx * vx + vy * vy
    if length_squared == 0:
        return True
    dot = wx * vx + wy * vy
    if dot <= 0 or dot >= length_squared:
        return False
    cross = wx * vy - wy * vx
    return cross * cross <= tolerance * tolerance * length_squared


def normalise_ring(ring, canonical, tolerance: int):
    original = ring
    points = [canonical[point] for point in ring]
    points = [point for index, point in enumerate(points) if index == 0 or point != points[index - 1]]
    if len(points) > 1 and points[0] == points[-1]:
        points.pop()
    changed = True
    while changed and len(points) >= 3:
        changed = False
        retained = []
        for index, point in enumerate(points):
            if near_straight(points[index - 1], point, points[(index + 1) % len(points)], tolerance):
                changed = True
            else:
                retained.append(point)
        if len(retained) < 3:
            break
        points = retained
    if len(points) < 3:
        # Tiny offshore polygons can legitimately fit inside the snap radius.
        # Retain their untouched ring rather than ever introducing a hole.
        return original
    return points


def transform(areas, tolerance: int):
    canonical, original_vertices, canonical_vertices_count = canonical_vertices(areas, tolerance)
    result = []
    original_points = final_points = 0
    for code, name, parts in areas:
        normalised_parts = []
        for ring in parts:
            original_points += len(ring)
            normalised = normalise_ring(ring, canonical, tolerance)
            final_points += len(normalised)
            normalised_parts.append(normalised)
        result.append((code, name, normalised_parts))
    return result, original_vertices, canonical_vertices_count, original_points, final_points


def write_areas(path: Path, quantization: int, areas) -> None:
    with gzip.open(path, "wb", compresslevel=9) as handle:
        handle.write(MAGIC)
        handle.write(struct.pack(">III", 2, quantization, len(areas)))
        for code, name, parts in areas:
            write_string(handle, code)
            write_string(handle, name)
            write_uvarint(handle, len(parts))
            for ring in parts:
                write_uvarint(handle, len(ring))
                previous_x = previous_y = 0
                for index, (x, y) in enumerate(ring):
                    write_svarint(handle, x if index == 0 else x - previous_x)
                    write_svarint(handle, y if index == 0 else y - previous_y)
                    previous_x, previous_y = x, y


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--tolerance-units", type=int, default=1)
    args = parser.parse_args()
    if args.tolerance_units < 0 or args.tolerance_units > 5:
        raise ValueError("Tolerance must be between 0 and 5 source units")
    quantization, areas = read_areas(args.input)
    transformed, original_vertices, final_vertices, original_points, final_points = transform(
        areas, args.tolerance_units
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    write_areas(args.output, quantization, transformed)
    print(
        f"{len(areas)} areas, {original_vertices}->{final_vertices} canonical vertices, "
        f"{original_points}->{final_points} ring points, tolerance={args.tolerance_units} "
        f"-> {args.output}"
    )


if __name__ == "__main__":
    main()
