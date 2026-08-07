# QuakeDeck Map Editor

A Windows-oriented local visual editor for QuakeDeck map geometry and boundary classes.

## Start it

Double-click:

`tools\map-editor\launch_map_editor.bat`

The launcher starts a tiny local Python server, opens the editor, and automatically uses the QuakeDeck repository containing `tools\map-editor` as the project root. Use Microsoft Edge or Google Chrome.

Closing the editor browser tab/window automatically stops the local Python server after a short reload grace period. The fixed top-bar **Close** button stops it immediately.

## Fixed top toolbar

Editing controls stay visible in a fixed two-row toolbar:

- layer selector
- Save / Reload
- Undo / Redo (`Ctrl+Z`, `Ctrl+Y`, `Ctrl+Shift+Z`)
- boundary classification
- Revert / Delete edge / Focus
- Advanced geometry toggle
- Move points / Add point / Combine points / Combine vortices / Delete points
- display toggles, search, zoom and status

The sidebar is used for search results, selection details and help rather than primary editing controls.

## Editable layers

### Municipalities — deep zoom

- `app/src/main/res/raw/jma_quake_municipalities_topology.gz`
- `app/src/main/res/raw/jma_municipality_fine_boundaries.gz`
- `app/src/main/res/raw/jma_municipality_warning_boundaries.gz`
- `app/src/main/res/raw/jma_municipality_prefecture_boundaries.gz`
- persistent edits: `tools/source/jma_municipality_boundary_overrides.json`

Boundary classes are Municipality / Warning zone / Prefecture.

### JMA reporting areas — middle zoom

- `app/src/main/res/raw/jma_quake_regions.gz`
- `app/src/main/res/raw/jma_quake_region_borders.gz`
- persistent edits: `tools/source/jma_quake_region_editor_overrides.json`

Boundary classes are JMA reporting area / Prefecture.

The editor also renders the high-resolution precomputed Japan coastline resource for visual context.

## Vortice selection

A **vortice** in the editor means one boundary segment. Geometry nodes are called **points**.

- click: select one vortice
- Shift-click: add/remove one vortice
- Ctrl-click a second vortice: select the shortest directly connected vortice chain
- Shift-drag: box-select vortices
- click empty map space: clear selection
- normal drag: pan
- mouse wheel: zoom
- double-click: fit the active layer

The maximum editor zoom is intentionally far deeper than QuakeDeck's runtime zoom.

## Advanced geometry editing

Enable **Advanced** in the fixed toolbar before editing polygon points. Geometry points appear only from 80× editor zoom onward.

- Alt-click: select one point
- Alt+Shift-click: add/remove a point
- Alt+Shift-drag: box-select points
- Move points: drag one selected point; the whole selected set moves together
- Add point: enter placement mode, then click the exact position on a vortice; every polygon sharing that edge receives the new point and both split edges retain the original class
- Combine points: merge all selected points into the Primary point
- Combine vortices: replace a simple selected vortice chain with one straight vortice
- Delete points: remove selected points from every polygon that uses them

Invalid operations that would collapse a polygon ring below three points or combine through a branch are blocked.

Holding **Alt** never starts a map pan. The editor also suppresses the page-level Alt key event so activating the browser's Windows menu cannot feed movement into the map interaction state.

## Delete edge

**Delete edge** removes selected vortices from QuakeDeck's rendered boundary resources while leaving polygon fill geometry closed and valid. Deleted boundaries remain visible as modified red edges while the modified overlay is enabled, so they can still be selected/reverted. The `none` classification is persisted in overrides and is understood by both resource rebuild scripts.

## Undo and Redo

The editor keeps up to 40 in-session edit steps. Boundary reclassification, edge deletion, Move/Add/Combine/Delete point operations and Combine vortices are undoable/redoable. History works across both editor layers and is reset by Reload.

## Saving, baselines and rebuilds

Save writes the actual QuakeDeck resources and the corresponding override JSON. Geometry overrides contain only affected areas plus the required edge classifications around them.

Before the first editor write to a layer, baseline files are stored under:

`tools/source/map_editor_baseline/`

**Restore baseline** restores the saved baseline for the active layer.

`build_classified_municipality_boundaries.py` and `build_jma_quake_border_classes.py` accept `--overrides` and reapply classification, geometry edits and deleted (`none`) edges during future resource rebuilds.
