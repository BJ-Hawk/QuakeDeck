#!/usr/bin/env python3
"""Package unsimplified N03 municipality polygons for QuakeDeck's 64x map tier.

The source is the official National Land Numerical Information municipality
Shapefile. This converter deliberately preserves every source ring and every
non-duplicate vertex; it only quantises coordinates for the app's compact QDMB
binary format.
"""
from __future__ import annotations

import argparse
import gzip
import struct
from collections import OrderedDict
from pathlib import Path


MAGIC = b"QDMB"
VERSION = 2
DEFAULT_QUANTIZATION = 10_000_000


def unsigned_varint(value: int) -> bytes:
    if value < 0:
        raise ValueError("Cannot encode a negative unsigned varint")
    output = bytearray()
    while value >= 0x80:
        output.append((value & 0x7F) | 0x80)
        value >>= 7
    output.append(value)
    return bytes(output)


def signed_varint(value: int) -> bytes:
    return unsigned_varint((value << 1) ^ (value >> 31))


def read_dbf(path: Path) -> list[dict[str, str]]:
    with path.open("rb") as stream:
        header = stream.read(32)
        record_count = struct.unpack_from("<I", header, 4)[0]
        header_length, record_length = struct.unpack_from("<HH", header, 8)
        fields: list[tuple[str, int, int]] = []
        offset = 1  # DBF deletion marker
        while True:
            first = stream.read(1)
            if first == b"\x0D":
                break
            descriptor = first + stream.read(31)
            if len(descriptor) != 32:
                raise ValueError("Unexpected end of N03 DBF header")
            name = descriptor[:11].split(b"\0", 1)[0].decode("ascii")
            length = descriptor[16]
            fields.append((name, offset, length))
            offset += length
        if stream.tell() != header_length:
            raise ValueError("Unexpected N03 DBF header length")

        rows = []
        for _ in range(record_count):
            record = stream.read(record_length)
            if len(record) != record_length:
                raise ValueError("Unexpected end of N03 DBF")
            if record[:1] == b"*":
                rows.append({})
                continue
            rows.append(
                {
                    name: record[field_offset : field_offset + length]
                    .decode("utf-8", errors="replace")
                    .strip()
                    for name, field_offset, length in fields
                }
            )
    return rows


def encode_ring(points: list[tuple[float, float]], quantization: int) -> bytes | None:
    quantized: list[tuple[int, int]] = []
    for longitude, latitude in points:
        point = (round(longitude * quantization), round(latitude * quantization))
        if not quantized or point != quantized[-1]:
            quantized.append(point)
    if len(quantized) < 3:
        return None
    if quantized[0] != quantized[-1]:
        quantized.append(quantized[0])

    encoded = bytearray(unsigned_varint(len(quantized)))
    previous_x = previous_y = 0
    for index, (x, y) in enumerate(quantized):
        encoded.extend(signed_varint(x if index == 0 else x - previous_x))
        encoded.extend(signed_varint(y if index == 0 else y - previous_y))
        previous_x, previous_y = x, y
    return bytes(encoded)


def source_parts(content: bytes) -> list[list[tuple[float, float]]]:
    shape_type = struct.unpack_from("<i", content)[0]
    if shape_type == 0:
        return []
    if shape_type not in {5, 15, 25}:
        raise ValueError(f"Unsupported N03 shape type: {shape_type}")
    part_count, point_count = struct.unpack_from("<II", content, 36)
    part_offsets = struct.unpack_from(f"<{part_count}I", content, 44)
    points_offset = 44 + part_count * 4
    if points_offset + point_count * 16 > len(content):
        raise ValueError("Malformed N03 polygon record")
    points = [
        struct.unpack_from("<dd", content, points_offset + index * 16)
        for index in range(point_count)
    ]
    return [
        points[start : part_offsets[index + 1] if index + 1 < part_count else point_count]
        for index, start in enumerate(part_offsets)
    ]


def municipality_name(row: dict[str, str]) -> str:
    return "".join(part for part in (row.get("N03_004", ""), row.get("N03_005", "")) if part)


def build(source: Path, output: Path, quantization: int) -> tuple[int, int]:
    shp = source / "N03-20260101.shp"
    dbf = source / "N03-20260101.dbf"
    rows = read_dbf(dbf)
    groups: OrderedDict[str, tuple[str, list[bytes]]] = OrderedDict()
    source_points = 0

    with shp.open("rb") as stream:
        stream.read(100)  # Shapefile header
        for index, row in enumerate(rows):
            record_header = stream.read(8)
            if len(record_header) != 8:
                raise ValueError("N03 Shapefile has fewer records than its DBF")
            _, word_count = struct.unpack(">II", record_header)
            content = stream.read(word_count * 2)
            if len(content) != word_count * 2:
                raise ValueError("Unexpected end of N03 Shapefile")
            code = row.get("N03_007", "")
            if not code:
                continue
            # N03 uses the five-digit Local Government Code; JMA's municipality
            # payload adds the two trailing zeroes used by the station catalogue.
            jma_code = f"{code}00"
            name, rings = groups.setdefault(jma_code, (municipality_name(row), []))
            for part in source_parts(content):
                source_points += len(part)
                encoded = encode_ring(part, quantization)
                if encoded is not None:
                    rings.append(encoded)

        if stream.read(1):
            raise ValueError("N03 Shapefile has more records than its DBF")

    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as raw:
        with gzip.GzipFile(fileobj=raw, mode="wb", compresslevel=9, mtime=0, filename="") as stream:
            stream.write(MAGIC)
            stream.write(struct.pack(">III", VERSION, quantization, len(groups)))
            for code, (name, rings) in groups.items():
                for value in (code, name):
                    encoded = value.encode("utf-8")
                    stream.write(struct.pack(">H", len(encoded)))
                    stream.write(encoded)
                stream.write(unsigned_varint(len(rings)))
                for ring in rings:
                    stream.write(ring)
    return len(groups), source_points


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--quantization", type=int, default=DEFAULT_QUANTIZATION)
    args = parser.parse_args()
    if args.quantization <= 0 or args.quantization > 10_000_000:
        parser.error("quantization must be between 1 and 10,000,000")
    areas, source_points = build(args.source, args.output, args.quantization)
    print(
        f"{areas:,} areas; {source_points:,} source points; "
        f"{args.output.stat().st_size:,} compressed bytes; quantization {args.quantization:,}"
    )


if __name__ == "__main__":
    main()
