#!/usr/bin/env python3
"""Repair QuakeDeck polygon layers into one exact shared planar coverage.

The invariant enforced by this tool is simple:
- an interior polygon edge is shared by exactly two areas;
- a one-owner edge may exist only on the exterior/water boundary;
- no edge may have more than two owners.

The repair is geometry-first. It nodes all source rings into one planar network,
polygonises that network into faces, assigns each face to exactly one source
area, then reconstructs every area's rings from one global edge graph. This
removes the short one-owner shadow chains that occur where neighbouring source
polygons leave a junction on slightly different paths.
"""

from __future__ import annotations

import argparse
import gzip
import io
import json
import math
import struct
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path

import shapely
from shapely.geometry import LineString, Point, Polygon
from shapely.ops import polygonize, unary_union

from build_topological_municipalities import read_areas, write_areas


@dataclass
class CoverageData:
    kind: str
    quantization: int
    areas: list[tuple[str, str, list[list[tuple[int, int]]]]]
    json_template: dict | None = None


def canonical(first, second):
    return (first, second) if first <= second else (second, first)


def decode_delta_ring(encoded):
    x, y = int(encoded[0]), int(encoded[1])
    ring = [(x, y)]
    for dx, dy in zip(encoded[2::2], encoded[3::2]):
        x += int(dx)
        y += int(dy)
        ring.append((x, y))
    if len(ring) > 1 and ring[0] == ring[-1]:
        ring.pop()
    return ring


def encode_delta_ring(ring):
    encoded = [int(ring[0][0]), int(ring[0][1])]
    previous_x, previous_y = ring[0]
    for x, y in ring[1:]:
        encoded.extend((int(x - previous_x), int(y - previous_y)))
        previous_x, previous_y = x, y
    return encoded


def read_json_areas(path: Path) -> CoverageData:
    with gzip.open(path, "rt", encoding="utf-8") as handle:
        root = json.load(handle)
    if not root.get("closed"):
        raise ValueError(f"{path} does not contain closed polygon areas")
    areas = []
    for item in root["areas"]:
        code, name, parts = str(item[0]), str(item[1]), item[2]
        rings = [decode_delta_ring(part) for part in parts]
        rings = [ring for ring in rings if len(ring) >= 3]
        areas.append((code, name, rings))
    return CoverageData("json", int(root["quantization"]), areas, root)


def read_coverage(path: Path, kind: str) -> CoverageData:
    if kind == "municipality":
        quantization, areas = read_areas(path)
        return CoverageData(kind, quantization, areas)
    if kind == "jma":
        return read_json_areas(path)
    raise ValueError(f"Unsupported coverage kind: {kind}")


def area_geometry(rings):
    polygons = []
    for ring in rings:
        if len(ring) < 3:
            continue
        polygon = Polygon(ring)
        if not polygon.is_valid:
            polygon = polygon.buffer(0)
        if not polygon.is_empty:
            polygons.append(polygon)
    if not polygons:
        return Polygon()
    # Android uses EVEN_ODD fill semantics for these ring collections.
    return shapely.symmetric_difference_all(polygons)


def quantized_face_edges(face, owner: int, scale_factor: int, edge_faces):
    """Record which planar face owner lies on each occurrence of an edge.

    We intentionally keep occurrences rather than only a set of owners. Two
    adjacent faces can belong to the *same* area; that seam is internal to the
    polygon and must disappear from the repaired topology. An edge occurring
    once is exterior/coastline. An edge occurring twice with different owners
    is a real shared administrative boundary.
    """
    for source_ring in (face.exterior, *face.interiors):
        points = []
        for x, y in source_ring.coords:
            point = (round(x * scale_factor), round(y * scale_factor))
            if not points or points[-1] != point:
                points.append(point)
        if len(points) > 1 and points[0] == points[-1]:
            points.pop()
        for first, second in zip(points, points[1:] + points[:1]):
            if first != second:
                edge_faces[canonical(first, second)].append(owner)


def assign_faces(faces, source_geometries):
    tree = shapely.STRtree(source_geometries)
    owners = []
    nearest_assignments = 0
    overlap_assignments = 0
    for face in faces:
        representative = face.representative_point()
        candidates = tree.query(representative, predicate="covered_by")
        if len(candidates) == 1:
            owner = int(candidates[0])
        elif len(candidates) > 1:
            overlap_assignments += 1
            owner = max(
                map(int, candidates),
                key=lambda index: source_geometries[index].intersection(face).area,
            )
        else:
            nearest_assignments += 1
            owner = int(tree.nearest(representative))
        owners.append(owner)
    return owners, nearest_assignments, overlap_assignments


def reconstruct_coverage(data: CoverageData, scale_factor: int):
    source_geometries = [area_geometry(rings) for _code, _name, rings in data.areas]
    linework = [
        LineString(ring + [ring[0]])
        for _code, _name, rings in data.areas
        for ring in rings
        if len(ring) >= 3
    ]
    noded = unary_union(linework)
    faces = list(polygonize(noded))
    owners, nearest_assignments, overlap_assignments = assign_faces(faces, source_geometries)

    owned_faces = [[] for _ in data.areas]
    edge_faces = defaultdict(list)
    for face, owner in zip(faces, owners):
        owned_faces[owner].append(face)
        quantized_face_edges(face, owner, scale_factor, edge_faces)

    non_manifold = [edge for edge, face_owners in edge_faces.items() if len(face_owners) > 2]
    if non_manifold:
        raise ValueError(f"Global face network contains {len(non_manifold)} non-manifold edges")

    # Collapse the planar face network into the true polygon coverage:
    #   1 occurrence       -> exterior/coastline edge, one owner
    #   2 same owners      -> internal face seam, discard
    #   2 different owners -> genuine shared boundary, two owners
    edge_owners = {}
    for edge, face_owners in edge_faces.items():
        if len(face_owners) == 1:
            edge_owners[edge] = {face_owners[0]}
        elif len(face_owners) == 2 and face_owners[0] != face_owners[1]:
            edge_owners[edge] = {face_owners[0], face_owners[1]}

    owner_edges = [[] for _ in data.areas]
    for edge, owners_for_edge in edge_owners.items():
        for owner in owners_for_edge:
            owner_edges[owner].append(edge)

    rebuilt = []
    for area_index, ((code, name, _rings), edges) in enumerate(zip(data.areas, owner_edges)):
        if not edges:
            raise ValueError(f"Area {code or area_index} lost all boundary edges")
        owned_union = unary_union(owned_faces[area_index])
        candidate_polygons = list(polygonize([LineString(edge) for edge in edges]))
        rings = []
        for polygon in candidate_polygons:
            representative = polygon.representative_point()
            source_point = Point(
                representative.x / scale_factor,
                representative.y / scale_factor,
            )
            if not owned_union.covers(source_point):
                continue
            exterior = [(round(x), round(y)) for x, y in list(polygon.exterior.coords)[:-1]]
            if len(exterior) >= 3:
                rings.append(exterior)
            for interior in polygon.interiors:
                ring = [(round(x), round(y)) for x, y in list(interior.coords)[:-1]]
                if len(ring) >= 3:
                    rings.append(ring)
        if not rings:
            raise ValueError(f"Area {code or area_index} lost all reconstructed polygons")
        rebuilt.append((code, name, rings))

    return rebuilt, faces, edge_owners, nearest_assignments, overlap_assignments


def ownership_histogram(areas):
    owners = defaultdict(set)
    for area_index, (_code, _name, rings) in enumerate(areas):
        for ring in rings:
            for first, second in zip(ring, ring[1:] + ring[:1]):
                if first != second:
                    owners[canonical(first, second)].add(area_index)
    return owners, Counter(len(value) for value in owners.values())


def validate_coverage(areas, gap_width: float):
    geometries = [area_geometry(rings) for _code, _name, rings in areas]
    invalid_areas = [index for index, geometry in enumerate(geometries) if not geometry.is_valid]
    if invalid_areas:
        raise ValueError(f"{len(invalid_areas)} repaired areas are geometrically invalid")
    if not shapely.coverage_is_valid(geometries, gap_width=gap_width):
        invalid_edges = shapely.coverage_invalid_edges(geometries, gap_width=gap_width)
        count = sum(not edge.is_empty for edge in invalid_edges)
        length = sum(edge.length for edge in invalid_edges)
        raise ValueError(
            f"Repaired coverage still contains gaps/overlaps: {count} areas, "
            f"invalid edge length={length:.3f}"
        )


def exterior_edge_set(areas):
    """Return exact quantized segments on the union exterior/water boundary."""
    geometries = [area_geometry(rings) for _code, _name, rings in areas]
    coverage = unary_union(geometries)
    edges = set()

    def add_ring(coordinates):
        points = [(round(x), round(y)) for x, y in coordinates]
        for first, second in zip(points, points[1:]):
            if first != second:
                edges.add(canonical(first, second))

    polygons = []
    if coverage.geom_type == "Polygon":
        polygons = [coverage]
    elif coverage.geom_type == "MultiPolygon":
        polygons = list(coverage.geoms)
    elif coverage.geom_type == "GeometryCollection":
        polygons = [geometry for geometry in coverage.geoms if geometry.geom_type == "Polygon"]
        polygons.extend(
            polygon
            for geometry in coverage.geoms
            if geometry.geom_type == "MultiPolygon"
            for polygon in geometry.geoms
        )
    for polygon in polygons:
        add_ring(polygon.exterior.coords)
        for interior in polygon.interiors:
            add_ring(interior.coords)
    return edges


def validate_owner_invariant(areas):
    owners, histogram = ownership_histogram(areas)
    non_manifold = [edge for edge, edge_owners in owners.items() if len(edge_owners) > 2]
    if non_manifold:
        raise ValueError(f"{len(non_manifold)} repaired edges have more than two owners")
    one_owner = {edge for edge, edge_owners in owners.items() if len(edge_owners) == 1}
    exterior = exterior_edge_set(areas)
    internal_single = one_owner - exterior
    missing_exterior = exterior - one_owner
    if internal_single:
        raise ValueError(
            f"{len(internal_single)} one-owner edges remain inland after repair"
        )
    if missing_exterior:
        raise ValueError(
            f"{len(missing_exterior)} exterior edges are not represented as one-owner coastline"
        )
    return owners, histogram, len(exterior)


def choose_repair(data: CoverageData):
    max_coordinate = max(
        max(abs(x), abs(y))
        for _code, _name, rings in data.areas
        for ring in rings
        for x, y in ring
    )
    for scale_factor in (1, 10, 100):
        if max_coordinate * scale_factor > 2_000_000_000:
            continue
        try:
            rebuilt, faces, edge_owners, nearest_count, overlap_count = reconstruct_coverage(
                data,
                scale_factor,
            )
            # The repaired rings are assembled from one exact global edge graph.
            # A tiny tolerance catches true gaps/overlaps without treating nearby
            # legitimate parallel coast/detail lines as invalid coverage.
            validate_coverage(rebuilt, gap_width=2.0)
        except ValueError:
            continue
        try:
            owners, histogram, _exterior_count = validate_owner_invariant(rebuilt)
        except ValueError:
            continue
        return scale_factor, rebuilt, faces, owners, histogram, nearest_count, overlap_count
    raise ValueError("Unable to construct a valid shared coverage within Int32 coordinate range")


def write_json_areas(path: Path, data: CoverageData, quantization: int, areas):
    root = dict(data.json_template or {})
    root["version"] = int(root.get("version", 1))
    root["quantization"] = int(quantization)
    root["closed"] = True
    root["areas"] = [
        [code, name, [encode_delta_ring(ring) for ring in rings]]
        for code, name, rings in areas
    ]
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as raw_stream, gzip.GzipFile(
        fileobj=raw_stream,
        mode="wb",
        compresslevel=9,
        mtime=0,
        filename="",
    ) as compressed:
        with io.TextIOWrapper(compressed, encoding="utf-8") as stream:
            json.dump(root, stream, ensure_ascii=False, separators=(",", ":"))


def write_coverage(path: Path, data: CoverageData, quantization: int, areas):
    path.parent.mkdir(parents=True, exist_ok=True)
    if data.kind == "municipality":
        write_areas(path, quantization, areas)
    else:
        write_json_areas(path, data, quantization, areas)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=("municipality", "jma"))
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    data = read_coverage(args.input, args.kind)
    before_owners, before_histogram = ownership_histogram(data.areas)
    (
        scale_factor,
        rebuilt,
        faces,
        after_owners,
        after_histogram,
        nearest_count,
        overlap_count,
    ) = choose_repair(data)
    output_quantization = data.quantization * scale_factor
    write_coverage(args.output, data, output_quantization, rebuilt)

    print(
        f"{args.kind}: {len(data.areas)} areas, {len(faces)} planar faces; "
        f"quantization {data.quantization}->{output_quantization} (x{scale_factor}); "
        f"edge owners before={dict(sorted(before_histogram.items()))}, "
        f"after={dict(sorted(after_histogram.items()))}; "
        f"nearest face assignments={nearest_count}, overlap face assignments={overlap_count}; "
        f"coverage valid; every one-owner edge is exterior/coastline -> {args.output}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
