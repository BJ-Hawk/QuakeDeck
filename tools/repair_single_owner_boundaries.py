#!/usr/bin/env python3
"""Repair QuakeDeck polygon topology, then rebuild all derived border resources.

Invariant enforced for both the municipality/deep layer and the JMA/middle layer:
- every inland polygon edge is shared by exactly two owners;
- a one-owner edge is allowed only on the exterior/water boundary;
- no edge may have more than two owners.

The repair is geometry-first. It reconstructs each polygon layer from one global
planar face graph, removes same-owner internal seams, keeps exterior coastline
edges once, and writes shared administrative seams once for both owners. Only
after the polygon resources pass validation are the derived border assets rebuilt.
"""
from __future__ import annotations

import argparse
import os
import subprocess
import sys
import tempfile
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_ROOT = SCRIPT_DIR.parent


def paths_for(root: Path):
    return {
        "municipality_geometry": root / "app/src/main/res/raw/jma_quake_municipalities_topology.gz",
        "jma_geometry": root / "app/src/main/res/raw/jma_quake_regions.gz",
        "fine": root / "app/src/main/res/raw/jma_municipality_fine_boundaries.gz",
        "warning": root / "app/src/main/res/raw/jma_municipality_warning_boundaries.gz",
        "prefecture": root / "app/src/main/res/raw/jma_municipality_prefecture_boundaries.gz",
        "jma_borders": root / "app/src/main/res/raw/jma_quake_region_borders.gz",
        "municipality_prefecture_overlay": root / "tools/source/jma_municipality_prefecture_borders.gz",
        "municipality_warning_overlay": root / "tools/source/jma_municipality_warning_zone_borders.gz",
        "jma_prefecture_overlay": root / "tools/source/jma_quake_region_prefecture_borders.gz",
        "municipality_overrides": root / "tools/source/jma_municipality_boundary_overrides.json",
        "jma_overrides": root / "tools/source/jma_quake_region_editor_overrides.json",
        "coverage_repair": root / "tools/repair_polygon_coverage.py",
        "municipality_builder": root / "tools/build_classified_municipality_boundaries.py",
        "jma_builder": root / "tools/build_jma_quake_border_classes.py",
    }


def require(paths):
    required = (
        "municipality_geometry", "jma_geometry", "municipality_prefecture_overlay",
        "municipality_warning_overlay", "jma_prefecture_overlay", "coverage_repair",
        "municipality_builder", "jma_builder",
    )
    missing = [str(paths[name]) for name in required if not paths[name].is_file()]
    if missing:
        raise FileNotFoundError("Missing required QuakeDeck files:\n  " + "\n  ".join(missing))


def run(command, cwd):
    print("\n> " + " ".join(map(str, command)))
    subprocess.run(command, cwd=cwd, check=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    args = parser.parse_args()

    root = args.root.resolve()
    paths = paths_for(root)
    require(paths)
    print(f"QuakeDeck root: {root}")

    with tempfile.TemporaryDirectory(prefix="quakedeck-topology-repair-") as temp_name:
        temp = Path(temp_name)
        municipality_geometry = temp / paths["municipality_geometry"].name
        jma_geometry = temp / paths["jma_geometry"].name

        run([
            sys.executable, str(paths["coverage_repair"]), "municipality",
            str(paths["municipality_geometry"]), str(municipality_geometry),
        ], root / "tools")
        run([
            sys.executable, str(paths["coverage_repair"]), "jma",
            str(paths["jma_geometry"]), str(jma_geometry),
        ], root / "tools")

        fine = temp / paths["fine"].name
        warning = temp / paths["warning"].name
        prefecture = temp / paths["prefecture"].name
        municipality_command = [
            sys.executable, str(paths["municipality_builder"]),
            str(municipality_geometry),
            str(paths["municipality_prefecture_overlay"]),
            str(paths["municipality_warning_overlay"]),
            str(fine), str(warning), str(prefecture),
        ]
        if paths["municipality_overrides"].is_file():
            municipality_command.extend(("--overrides", str(paths["municipality_overrides"])))
        run(municipality_command, root / "tools")

        jma_borders = temp / paths["jma_borders"].name
        jma_command = [
            sys.executable, str(paths["jma_builder"]),
            str(jma_geometry), str(paths["jma_prefecture_overlay"]), str(jma_borders),
        ]
        if paths["jma_overrides"].is_file():
            jma_command.extend(("--overrides", str(paths["jma_overrides"])))
        run(jma_command, root / "tools")

        replacements = (
            (municipality_geometry, paths["municipality_geometry"]),
            (jma_geometry, paths["jma_geometry"]),
            (fine, paths["fine"]),
            (warning, paths["warning"]),
            (prefecture, paths["prefecture"]),
            (jma_borders, paths["jma_borders"]),
        )
        for source, target in replacements:
            target.parent.mkdir(parents=True, exist_ok=True)
            os.replace(source, target)
            print(f"Updated {target.relative_to(root)} ({target.stat().st_size:,} bytes)")

    print("\nTopology repair complete. Every one-owner edge remaining in both polygon layers is coastline/exterior.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
