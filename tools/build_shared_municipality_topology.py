#!/usr/bin/env python3
"""Build a planar, shared-arc municipality resource from original JMA rings.

Requires Shapely only on the build machine. It is not an Android dependency.
"""

from __future__ import annotations

import argparse
import gzip
import math
import struct
from collections import defaultdict
from pathlib import Path

from shapely.geometry import LineString, Polygon
from shapely.ops import polygonize, unary_union
from scipy.spatial import cKDTree

from build_topological_municipalities import (
    read_areas,
    transform,
    write_areas,
)


GRID_SIZE = 2_000


def bounds(ring):
    xs, ys = zip(*ring)
    return min(xs), min(ys), max(xs), max(ys)


def contains(rings, point):
    point_x, point_y = point
    inside = False
    for ring in rings:
        previous_x, previous_y = ring[-1]
        for current_x, current_y in ring:
            if (current_y > point_y) != (previous_y > point_y):
                crossing_x = (previous_x - current_x) * (point_y - current_y) / (
                    previous_y - current_y
                ) + current_x
                if point_x < crossing_x:
                    inside = not inside
            previous_x, previous_y = current_x, current_y
    return inside


def area_index(areas):
    index = defaultdict(set)
    area_bounds = []
    for area_id, (_, _, rings) in enumerate(areas):
        left = min(bounds(ring)[0] for ring in rings)
        top = min(bounds(ring)[1] for ring in rings)
        right = max(bounds(ring)[2] for ring in rings)
        bottom = max(bounds(ring)[3] for ring in rings)
        area_bounds.append((left, top, right, bottom))
        for grid_x in range(math.floor(left / GRID_SIZE), math.floor(right / GRID_SIZE) + 1):
            for grid_y in range(math.floor(top / GRID_SIZE), math.floor(bottom / GRID_SIZE) + 1):
                index[grid_x, grid_y].add(area_id)
    return index, area_bounds


def owner_for(point, areas, index, area_bounds):
    point_x, point_y = point
    candidates = index[math.floor(point_x / GRID_SIZE), math.floor(point_y / GRID_SIZE)]
    for area_id in candidates:
        left, top, right, bottom = area_bounds[area_id]
        if left <= point_x <= right and top <= point_y <= bottom and contains(areas[area_id][2], point):
            return area_id
    return None


def distance_squared_to_segment(point, first, second):
    point_x, point_y = point
    first_x, first_y = first
    vector_x, vector_y = second[0] - first_x, second[1] - first_y
    length_squared = vector_x * vector_x + vector_y * vector_y
    if length_squared == 0:
        return (point_x - first_x) ** 2 + (point_y - first_y) ** 2
    t = ((point_x - first_x) * vector_x + (point_y - first_y) * vector_y) / length_squared
    t = max(0.0, min(1.0, t))
    nearest_x, nearest_y = first_x + t * vector_x, first_y + t * vector_y
    return (point_x - nearest_x) ** 2 + (point_y - nearest_y) ** 2


def segment_index(areas):
    midpoints, starts, ends, owners = [], [], [], []
    for area_id, (_, _, rings) in enumerate(areas):
        for ring in rings:
            for first, second in zip(ring, ring[1:] + ring[:1]):
                starts.append(first)
                ends.append(second)
                owners.append(area_id)
                midpoints.append(((first[0] + second[0]) / 2, (first[1] + second[1]) / 2))
    return cKDTree(midpoints), starts, ends, owners


def nearest_owner(point, tree, starts, ends, owners):
    # A face created by a near-miss lies right beside the source boundary.
    # Inspecting a small nearest-midpoint set is exact enough after the final
    # point-to-segment calculation and avoids an O(faces × all-segments) pass.
    _, candidates = tree.query(point, k=min(32, len(owners)))
    best_index, best_distance = None, math.inf
    for index in candidates:
        distance = distance_squared_to_segment(point, starts[index], ends[index])
        if distance < best_distance:
            best_index, best_distance = index, distance
    return owners[best_index], math.sqrt(best_distance)


OUTPUT_SCALE = 10


def ring_from_coordinates(coordinates):
    # Noding may create fractional source-coordinate intersections. Preserve
    # them at 10× the source quantisation instead of rounding tiny faces away.
    points = [(round(x * OUTPUT_SCALE), round(y * OUTPUT_SCALE)) for x, y in coordinates[:-1]]
    deduplicated = []
    for point in points:
        if not deduplicated or deduplicated[-1] != point:
            deduplicated.append(point)
    if len(deduplicated) < 3:
        return None
    return deduplicated


def simplify_source_rings(areas, tolerance: float):
    if tolerance <= 0:
        return areas
    result = []
    before = after = 0
    for code, name, rings in areas:
        simplified_rings = []
        for ring in rings:
            before += len(ring)
            simplified = LineString(ring + [ring[0]]).simplify(
                tolerance,
                preserve_topology=False,
            )
            candidate = ring_from_coordinates(simplified.coords)
            # Keep every tiny island/ward whose line simplification collapses.
            candidate = [(x // OUTPUT_SCALE, y // OUTPUT_SCALE) for x, y in candidate] \
                if candidate is not None else ring
            if len(candidate) < 3:
                candidate = ring
            simplified_rings.append(candidate)
            after += len(candidate)
        result.append((code, name, simplified_rings))
    print(
        f"Simplified original rings by {tolerance:g} source units: "
        f"{before}->{after} points …",
        flush=True,
    )
    return result


def build(areas, simplify_tolerance: float, preserve_topology: bool):
    lines = [
        LineString(ring + [ring[0]])
        for _, _, rings in areas
        for ring in rings
        if len(ring) >= 3
    ]
    print(f"Noding {len(lines)} source rings …", flush=True)
    noded = unary_union(lines)
    if simplify_tolerance > 0:
        # The linework is already globally noded and de-duplicated here. A
        # single topology-preserving simplify therefore reduces the exact same
        # arc for both neighbouring fills instead of letting them drift apart.
        noded = noded.simplify(
            simplify_tolerance,
            preserve_topology=preserve_topology,
        )
        print(
            f"Simplified the shared network by {simplify_tolerance:g} source units …",
            flush=True,
        )
    faces = list(polygonize(noded))
    print(f"Polygonised into {len(faces)} faces …", flush=True)

    index, area_bounds = area_index(areas)
    tree, segment_starts, segment_ends, segment_owners = segment_index(areas)
    owned = [[] for _ in areas]
    unowned = []
    inferred_distances = []
    for face in faces:
        representative = face.representative_point()
        owner = owner_for((representative.x, representative.y), areas, index, area_bounds)
        if owner is None:
            owner, distance = nearest_owner(
                (representative.x, representative.y),
                tree,
                segment_starts,
                segment_ends,
                segment_owners,
            )
            if owner is None:
                unowned.append(face)
            else:
                owned[owner].append(face)
                inferred_distances.append(distance)
        else:
            owned[owner].append(face)
    if unowned:
        areas = sorted(face.area for face in unowned)
        raise ValueError(
            f"{len(unowned)} polygonised faces have no municipality owner "
            f"(total area={sum(areas):.1f}, largest={areas[-1]:.1f}, "
            f"median={areas[len(areas) // 2]:.1f})"
        )

    if inferred_distances:
        print(
            "Assigned "
            f"{len(inferred_distances)} boundary slivers to their nearest municipality "
            f"(max representative distance={max(inferred_distances):.2f} source units) …",
            flush=True,
        )

    result = []
    discarded_degenerate_faces = 0
    for area_id, (code, name, _) in enumerate(areas):
        merged = unary_union(owned[area_id])
        polygons = [merged] if isinstance(merged, Polygon) else list(merged.geoms)
        rings = []
        for polygon in polygons:
            exterior = ring_from_coordinates(polygon.exterior.coords)
            if exterior is None:
                discarded_degenerate_faces += 1
                continue
            rings.append(exterior)
            rings.extend(
                interior_ring
                for interior in polygon.interiors
                if (interior_ring := ring_from_coordinates(interior.coords)) is not None
            )
        if not rings:
            raise ValueError(f"Municipality {code} lost all polygonised faces")
        result.append((code, name, rings))
    if discarded_degenerate_faces:
        print(f"Discarded {discarded_degenerate_faces} zero-area faces after noding …", flush=True)
    return result, len(lines), len(faces)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--snap-tolerance-units", type=int, default=1)
    parser.add_argument("--source-simplify-tolerance-units", type=float, default=0.0)
    parser.add_argument("--simplify-tolerance-units", type=float, default=0.0)
    parser.add_argument(
        "--fast-shared-arc-simplify",
        action="store_true",
        help="Use endpoint-preserving per-arc simplification, then validate by polygonising.",
    )
    args = parser.parse_args()

    quantization, source = read_areas(args.input)
    normalised, before_vertices, after_vertices, before_points, after_points = transform(
        source, args.snap_tolerance_units
    )
    normalised = simplify_source_rings(
        normalised,
        args.source_simplify_tolerance_units,
    )
    if args.simplify_tolerance_units < 0:
        raise ValueError("Simplification tolerance cannot be negative")
    result, source_rings, faces = build(
        normalised,
        args.simplify_tolerance_units,
        preserve_topology=not args.fast_shared_arc_simplify,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    write_areas(args.output, quantization * OUTPUT_SCALE, result)
    print(
        f"{len(source)} areas, {source_rings} rings, {faces} faces, "
        f"{before_vertices}->{after_vertices} canonical vertices, "
        f"{before_points}->{after_points} source points -> {args.output}"
    )


if __name__ == "__main__":
    main()
