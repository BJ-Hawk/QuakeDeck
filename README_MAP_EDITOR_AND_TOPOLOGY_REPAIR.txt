QuakeDeck v0.9.83b - Map Editor + Geometry Topology Repair
============================================================

INSTALL
-------
Extract this archive directly over the QuakeDeck project root and replace files.

NO REPAIR SCRIPT NEEDS TO BE RUN AFTER EXTRACTION.
The repaired municipality and JMA polygon resources and all derived border assets
are already included in this package.

OPTIONAL FUTURE REBUILD
-----------------------
tools\repair_single_owner_boundaries.bat is only for rebuilding the topology later
if the source polygon resources change. It repairs both polygon tiers first, validates
that every one-owner edge is exterior/coastline, and only then rebuilds the derived
border resources.

MAP EDITOR
----------
Launch with:
  tools\map-editor\launch_map_editor.bat

The editor's current controls include the fixed top toolbar, Undo/Redo, Add point,
Delete edge, boundary/vortice multi-selection, advanced point tools, deeper zoom,
coastlines, English/Japanese search and automatic Python shutdown when the editor
page closes.

USER OVERRIDES
--------------
This package deliberately does NOT overwrite your existing map-editor override JSON
files under tools\source. Existing manual editor changes therefore remain yours.
