#!/usr/bin/env python3
"""Build compact QuakeDeck geometry resources from official JMA GIS shapefiles.

Input archives are JMA GIS ZIPs. Generated files are quantized, delta-encoded
and gzip-compressed so Android can parse them without shipping a shapefile
stack. The deep-zoom municipality layer uses a streaming binary payload to
avoid a large temporary JSON object graph on phones; legacy regional layers
retain their existing JSON payload. Any subset can be rebuilt independently.
"""
from __future__ import annotations

import argparse
import gzip
import io
import json
import shutil
import struct
import tempfile
import zipfile
from pathlib import Path
from typing import Iterable, Iterator

import shapefile  # pyshp
from shapely.geometry import shape
from shapely.ops import linemerge

QUANTIZATION = 100_000
MUNICIPALITY_QUANTIZATION = 10_000


def extract_archive(archive: Path, target: Path) -> Path:
    with zipfile.ZipFile(archive) as zf:
        for member in zf.infolist():
            suffix = Path(member.filename).suffix.lower()
            if suffix in {".shp", ".shx", ".dbf"}:
                out = target / f"layer{suffix}"
                with zf.open(member) as src, out.open("wb") as dst:
                    shutil.copyfileobj(src, dst)
    shp = target / "layer.shp"
    if not shp.is_file():
        raise RuntimeError(f"No shapefile found in {archive}")
    return shp


def polygon_rings(geometry) -> Iterator[list[tuple[float, float]]]:
    if geometry.is_empty:
        return
    polygons = [geometry] if geometry.geom_type == "Polygon" else list(geometry.geoms)
    for polygon in polygons:
        yield list(polygon.exterior.coords)
        for interior in polygon.interiors:
            yield list(interior.coords)


def line_parts(geometry) -> Iterator[list[tuple[float, float]]]:
    if geometry.is_empty:
        return
    merged = linemerge(geometry)
    lines = [merged] if merged.geom_type == "LineString" else list(merged.geoms)
    for line in lines:
        # Approximately 100 m. Tiny isolated shoreline fragments are invisible
        # on a phone and account for most of the source shapefile's part count.
        if line.length >= 0.001:
            yield list(line.coords)


def encode_part(
    coords: Iterable[tuple[float, float]],
    closed: bool,
    quantization: int = QUANTIZATION,
) -> list[int] | None:
    quantized: list[tuple[int, int]] = []
    for lon, lat in coords:
        point = (round(lon * quantization), round(lat * quantization))
        if not quantized or point != quantized[-1]:
            quantized.append(point)
    if closed:
        if len(quantized) < 3:
            return None
        if quantized[0] != quantized[-1]:
            quantized.append(quantized[0])
        if len(quantized) < 4:
            return None
    elif len(quantized) < 2:
        return None

    encoded = [quantized[0][0], quantized[0][1]]
    previous_x, previous_y = quantized[0]
    for x, y in quantized[1:]:
        encoded.extend((x - previous_x, y - previous_y))
        previous_x, previous_y = x, y
    return encoded


def record_identity(record: dict, kind: str) -> tuple[str, str] | None:
    if kind == "municipality":
        code = str(record.get("regioncode", "")).strip()
        # Blank-code records are remote islands, disputed territory or tiny
        # boundary/land-reclamation placeholders, not reportable municipalities.
        if not code:
            return None
        name = str(record.get("name", "")).strip() or str(record.get("regionname", "")).strip()
        return code, name
    return str(record["code"]), str(record["name"])


def write_binary_string(stream, value: str) -> None:
    encoded = value.encode("utf-8")
    if len(encoded) > 65_535:
        raise ValueError("Municipality string exceeds binary format limit")
    stream.write(struct.pack(">H", len(encoded)))
    stream.write(encoded)


def zigzag(value: int) -> int:
    return (value << 1) ^ (value >> 31)


def write_unsigned_varint(stream, value: int) -> None:
    if value < 0:
        raise ValueError("Unsigned varint cannot encode a negative value")
    while value >= 0x80:
        stream.write(bytes(((value & 0x7F) | 0x80,)))
        value >>= 7
    stream.write(bytes((value,)))


def write_signed_varint(stream, value: int) -> None:
    write_unsigned_varint(stream, zigzag(value))


def write_municipality_payload(
    output: Path,
    areas: list[list],
    quantization: int,
) -> None:
    """Write compact v2 geometry using delta + ZigZag varints.

    Fixed-width Int32 storage made the already-gzipped municipality layer almost
    five megabytes. Most coordinate deltas are tiny, so varints reduce both the
    raw payload and the final APK resource substantially without changing paths.
    """
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as raw_stream:
        with gzip.GzipFile(
            fileobj=raw_stream,
            mode="wb",
            compresslevel=9,
            mtime=0,
            filename=""
        ) as stream:
            stream.write(b"QDMB")
            stream.write(struct.pack(">III", 2, quantization, len(areas)))
            for code, name, parts in areas:
                write_binary_string(stream, code)
                write_binary_string(stream, name)
                write_unsigned_varint(stream, len(parts))
                for encoded in parts:
                    write_unsigned_varint(stream, len(encoded) // 2)
                    for value in encoded:
                        write_signed_varint(stream, value)


def write_json_payload(output: Path, payload: dict) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    # Use a fixed gzip timestamp and no embedded source filename so repeated
    # conversions of the same JMA archives produce byte-identical resources.
    with output.open("wb") as raw_stream:
        with gzip.GzipFile(
            fileobj=raw_stream,
            mode="wb",
            compresslevel=9,
            mtime=0,
            filename=""
        ) as compressed:
            with io.TextIOWrapper(compressed, encoding="utf-8") as stream:
                json.dump(payload, stream, ensure_ascii=False, separators=(",", ":"))


def convert(archive: Path, output: Path, kind: str, tolerance: float) -> dict:
    closed = kind != "tsunami"
    with tempfile.TemporaryDirectory(prefix=f"quakedeck-{kind}-") as tmp:
        shp_path = extract_archive(archive, Path(tmp))
        reader = shapefile.Reader(str(shp_path), encoding="utf-8")
        areas = []
        source_points = 0
        output_points = 0
        output_parts = 0

        for shape_record in reader.iterShapeRecords():
            record = shape_record.record.as_dict()
            identity = record_identity(record, kind)
            if identity is None:
                continue
            code, name = identity
            source_points += len(shape_record.shape.points)
            geometry = shape(shape_record.shape.__geo_interface__)
            simplified = geometry.simplify(tolerance, preserve_topology=False)
            if kind == "municipality" and not geometry.is_empty and not simplified.is_empty:
                # Non-topology-preserving simplification is compact, but a few
                # intricate waterfront wards can lose the land fragment that
                # best represents the original feature. Re-run only those rare
                # records safely, rather than inflating every municipality.
                representative = geometry.representative_point()
                if not simplified.covers(representative):
                    topology_safe = geometry.simplify(tolerance, preserve_topology=True)
                    if not topology_safe.is_empty:
                        simplified = topology_safe
            if simplified.is_empty and not geometry.is_empty:
                # Preserve tiny named offshore regions that are smaller than the
                # normal phone-map simplification tolerance.
                simplified = geometry.simplify(tolerance / 10.0, preserve_topology=False)
                if simplified.is_empty:
                    simplified = geometry
            raw_parts = line_parts(simplified) if kind == "tsunami" else polygon_rings(simplified)
            parts = []
            part_quantization = (
                MUNICIPALITY_QUANTIZATION if kind == "municipality" else QUANTIZATION
            )
            for raw_part in raw_parts:
                encoded = encode_part(
                    raw_part,
                    closed=closed,
                    quantization=part_quantization,
                )
                if encoded is None:
                    continue
                parts.append(encoded)
                output_parts += 1
                output_points += len(encoded) // 2
            if parts:
                areas.append([code, name, parts])

    if kind == "municipality":
        write_municipality_payload(output, areas, MUNICIPALITY_QUANTIZATION)
    else:
        write_json_payload(
            output,
            {
                "version": 1,
                "quantization": QUANTIZATION,
                "closed": closed,
                "areas": areas,
            },
        )
    return {
        "kind": kind,
        "areas": len(areas),
        "source_points": source_points,
        "output_points": output_points,
        "output_parts": output_parts,
        "bytes": output.stat().st_size,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--quake", type=Path)
    parser.add_argument("--eew", type=Path)
    parser.add_argument("--tsunami", type=Path)
    parser.add_argument("--municipality", type=Path)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()

    requested_jobs = [
        (args.quake, args.output_dir / "jma_quake_regions.gz", "quake", 0.001),
        (args.eew, args.output_dir / "jma_eew_regions.gz", "eew", 0.001),
        (args.tsunami, args.output_dir / "jma_tsunami_coastlines.gz", "tsunami", 0.001),
        # Municipality borders appear only after 64x. About 55 m of geometric
        # simplification and 11 m coordinate quantisation remain visually finer
        # than the layer's useful screen resolution while avoiding a multi-MB APK.
        (
            args.municipality,
            args.output_dir / "jma_quake_municipalities.gz",
            "municipality",
            0.0005,
        ),
    ]
    jobs = [job for job in requested_jobs if job[0] is not None]
    if not jobs:
        parser.error("At least one input layer must be specified")

    summaries = [convert(*job) for job in jobs]
    print(json.dumps(summaries, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
