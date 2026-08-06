#!/usr/bin/env python3
"""Build an exact, one-copy municipality-boundary resource for QuakeDeck."""

from __future__ import annotations

import argparse
import gzip
import math
import struct
from collections import defaultdict
from pathlib import Path


SOURCE_MAGIC = b"QDMB"
TARGET_MAGIC = b"QDMC"
TARGET_VERSION = 1
GRID_SIZE = 0.01


def read_exact(handle, count: int) -> bytes:
    value = handle.read(count)
    if len(value) != count:
        raise ValueError("Unexpected end of municipality resource")
    return value


def read_uvarint(handle) -> int:
    value = shift = 0
    while shift < 35:
        byte = read_exact(handle, 1)[0]
        value |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return value
        shift += 7
    raise ValueError("Malformed varint")


def read_svarint(handle) -> int:
    value = read_uvarint(handle)
    return (value >> 1) ^ -(value & 1)


def write_uvarint(handle, value: int) -> None:
    while value >= 0x80:
        handle.write(bytes([(value & 0x7F) | 0x80]))
        value >>= 7
    handle.write(bytes([value]))


def write_svarint(handle, value: int) -> None:
    write_uvarint(handle, (value << 1) ^ (value >> 31))


def read_string(handle) -> str:
    return read_exact(handle, struct.unpack(">H", read_exact(handle, 2))[0]).decode("utf-8")


def skip_source(handle, version: int) -> tuple[list[list[tuple[int, int]]], int]:
    rings, point_count = [], 0
    area_count = struct.unpack(">I", read_exact(handle, 4))[0]
    for _ in range(area_count):
        read_string(handle)
        read_string(handle)
        part_count = read_uvarint(handle) if version >= 2 else struct.unpack(">I", read_exact(handle, 4))[0]
        for _ in range(part_count):
            count = read_uvarint(handle) if version >= 2 else struct.unpack(">I", read_exact(handle, 4))[0]
            x = y = 0
            ring = []
            for index in range(count):
                dx = read_svarint(handle) if version >= 2 else struct.unpack(">i", read_exact(handle, 4))[0]
                dy = read_svarint(handle) if version >= 2 else struct.unpack(">i", read_exact(handle, 4))[0]
                x, y = (dx, dy) if index == 0 else (x + dx, y + dy)
                ring.append((x, y))
            rings.append(ring)
            point_count += len(ring)
    return rings, point_count


def read_source(path: Path):
    with gzip.open(path, "rb") as handle:
        if read_exact(handle, 4) != SOURCE_MAGIC:
            raise ValueError("Not a QDMB municipality resource")
        version = struct.unpack(">I", read_exact(handle, 4))[0]
        if version not in (1, 2):
            raise ValueError(f"Unsupported QDMB version: {version}")
        quantization = struct.unpack(">I", read_exact(handle, 4))[0]
        return quantization, *skip_source(handle, version)


def grid_for(point: tuple[int, int], quantization: int) -> tuple[int, int]:
    longitude = point[0] / quantization
    latitude = max(-85.05112878, min(85.05112878, point[1] / quantization))
    x = math.radians(longitude)
    y = -math.log(math.tan(math.pi / 4 + math.radians(latitude) / 2))
    return math.floor(x / GRID_SIZE), math.floor(y / GRID_SIZE)


def canonical(first: tuple[int, int], second: tuple[int, int]):
    return (first, second) if first <= second else (second, first)


def chain_edges(edges: list[tuple[tuple[int, int], tuple[int, int]]]):
    """Convert a chunk's exact undirected segments into maximal polylines."""
    adjacency: dict[tuple[int, int], list[int]] = defaultdict(list)
    for index, (first, second) in enumerate(edges):
        adjacency[first].append(index)
        adjacency[second].append(index)
    unused = set(range(len(edges)))
    paths = []

    def follow(start: tuple[int, int], first_edge: int):
        path, current, edge_index = [start], start, first_edge
        while True:
            unused.remove(edge_index)
            first, second = edges[edge_index]
            current = second if current == first else first
            path.append(current)
            candidates = [candidate for candidate in adjacency[current] if candidate in unused]
            if len(adjacency[current]) != 2 or not candidates:
                return path
            edge_index = candidates[0]

    for vertex, incident in adjacency.items():
        if len(incident) != 2:
            for edge_index in incident:
                if edge_index in unused:
                    paths.append(follow(vertex, edge_index))
    while unused:  # closed cycles have no branching vertex
        edge_index = min(unused)
        paths.append(follow(edges[edge_index][0], edge_index))
    return paths


def build_chunks(quantization: int, rings):
    unique = set()
    for ring in rings:
        for first, second in zip(ring, ring[1:] + ring[:1]):
            if first != second:
                unique.add(canonical(first, second))
    chunks: dict[tuple[int, int], list] = defaultdict(list)
    overflow = []
    for edge in sorted(unique):
        start_grid, end_grid = grid_for(edge[0], quantization), grid_for(edge[1], quantization)
        if abs(start_grid[0] - end_grid[0]) > 1 or abs(start_grid[1] - end_grid[1]) > 1:
            overflow.append(edge)
        else:
            midpoint = ((edge[0][0] + edge[1][0]) // 2, (edge[0][1] + edge[1][1]) // 2)
            chunks[grid_for(midpoint, quantization)].append(edge)
    return unique, {key: chain_edges(value) for key, value in chunks.items()}, chain_edges(overflow)


def write_paths(handle, paths) -> None:
    write_uvarint(handle, len(paths))
    for path in paths:
        write_uvarint(handle, len(path))
        x, y = path[0]
        write_svarint(handle, x)
        write_svarint(handle, y)
        for next_x, next_y in path[1:]:
            write_svarint(handle, next_x - x)
            write_svarint(handle, next_y - y)
            x, y = next_x, next_y


def write_target(path: Path, quantization: int, chunks, overflow) -> None:
    with gzip.open(path, "wb", compresslevel=9) as handle:
        handle.write(TARGET_MAGIC)
        handle.write(struct.pack(">II", TARGET_VERSION, quantization))
        handle.write(struct.pack(">I", len(chunks)))
        for (x, y), paths in sorted(chunks.items()):
            write_svarint(handle, x)
            write_svarint(handle, y)
            write_paths(handle, paths)
        write_paths(handle, overflow)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    quantization, rings, source_points = read_source(args.input)
    unique, chunks, overflow = build_chunks(quantization, rings)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    write_target(args.output, quantization, chunks, overflow)
    print(
        f"{source_points} source points, {len(unique)} unique edges, "
        f"{len(chunks)} chunks, {sum(map(len, chunks.values()))} paths, "
        f"{len(overflow)} overflow paths -> {args.output}"
    )


if __name__ == "__main__":
    main()
