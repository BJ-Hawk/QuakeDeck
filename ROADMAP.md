# QuakeDeck Roadmap

## Interactive seismic-station inspection

### Observed-intensity list → station
- Make every station row in **Observed intensities** tappable.
- Tapping a row selects that station and moves/zooms the map to it at a station-detail zoom level.
- Keep the parent earthquake selected; station focus is a map/detail sub-selection, not a new earthquake selection.
- Open a station-detail card with the station name, operator/network, coordinates, and the values available for the selected event.

### Map station → station details
- Make visible station dots tappable on the map.
- Tapping a dot selects the station and opens the same station-detail card used by the Observed-intensities list.
- Selected-station highlighting must remain visible without obscuring the Shindo color of the station itself.

### Measurements as richer sources become available
The station-detail card should be source-capability driven rather than hard-coded to one provider. Show only values the active report/source actually exposes, for example:
- observed JMA Shindo / intensity class
- instrumental intensity value when available
- PGA when available
- later real-time/live values supplied by DM-D.S.S or another legitimate station source

Historical-event station inspection should continue to display the measurements belonging to that historical event; live mode should update the same station-detail UI in place as newer measurements arrive.

### Camera behavior
- Station focus should use a deeper camera target than earthquake-area focus.
- The exact station-detail zoom remains tunable now that the 64x–256x municipality layer and deep-map behaviour are available.
- **Fit Japan**, **Focus event**, and **Focus station** remain separate camera actions.

## Live data progression

### Implemented in 0.9.11
- P2PQuake EEW detection and successive warning-report progression.
- EEW cancellation handling.
- Live confirmed JMA earthquake reports replacing/updating the current report.
- Clean default map with explicit report focus, plus automatic one-time focus for genuinely new live events.

### Later enrichment
- Replace the 0.9.14 constant-velocity P/S display model with a documented layered travel-time model when an appropriate source/model is selected.
- Make the destination configurable; 0.9.14 uses Tokyo and prefers the official EEW forecast-area S-wave arrival when available.
- DM-D.S.S OAuth/socket adapter when a usable QuakeDeck entitlement exists.
- Live station instrumental intensity/PGA only when a legitimate source exposes those measurements.


### Implemented in 0.9.12
- Historical report selection scrolls the report summary and controls fully into view.
- Closing a report restores the previous history-list position.
- Floating **Top** shortcut for long report lists.
- Corrected vertical centring in the compact Shindo legend.
- Live WebSocket startup no longer waits for catalogue/history HTTP requests.
- Reconnect catch-up through P2PQuake `/history`, including stale-report/serial guards.
- Finite HTTP deadlines and jittered WebSocket reconnect backoff.


### Implemented in 0.9.13
- Official P2PQuake replay-sandbox mode for repeatable EEW/event-progression testing.
- Explicit TEST/SANDBOX UI state and production/sandbox data isolation.
- Tap-to-minimize/restore portrait divider with long-press drag resizing.
- Observation expansion now restores the prior portrait panel size when closed.


### Implemented in 0.9.14
- Sandbox EEW chronology is isolated from production anti-rollback guards and rebased onto a live animation clock.
- Sandbox reconnects reset replay-only packet deduplication and timeline offsets.
- Active EEWs display depth-aware constant-velocity P/S fronts.
- Tokyo destination card with P/S arrival countdown and forecast intensity when available.


### Implemented in 0.9.15
- One-tap deterministic offline replay based on the 5 May 2023 Noto Peninsula EEW.
- Five-second arm delay for closing Settings before playback begins.
- Multiple EEW revisions traverse the same parser/state path as live and official-sandbox packets.
- Replay remains active through the Tokyo S-wave arrival, then emits a confirmed earthquake report and returns to the official rotating sandbox.
- Replay callbacks are generation-scoped and cancelled on mode changes/provider shutdown.


### Implemented in 0.9.16
- Event-focus zoom still accounts for the full observed/predicted footprint, while camera pan now centres the actual epicentre rather than the footprint centroid.
- Fixes coastal EEWs such as the built-in Noto replay being visually displaced toward a map edge.

### Implemented in 0.9.17
- Live P2PQuake code-552 tsunami bulletin parsing, active-state/expiration handling, updates, cancellation, and reconnect recovery.
- Tsunami warning card with affected areas, expected heights and first-arrival state/countdown.
- Prefecture-level coastal warning overlay and map legend, with the exact JMA forecast-area labels retained in the details list.
- Deterministic five-second-delayed replay based on the 1 January 2024 Noto tsunami sequence, including major warning, arrival update, downgrade and cancellation through the production parser path.


### Implemented in 0.9.18
- Tsunami overlays now colour only sea-facing coastline segments instead of filling whole prefectures.
- Active warning/advisory coastlines use a bright/dim 500 ms pulse with a persistent dark backing stroke.
- Bundled TopoJSON topology identifies exterior coastal arcs and divides them among all known JMA tsunami forecast-area labels; exact bulletin text remains authoritative where a visual partition is approximate.
- Deterministic combined 1 January 2024 Noto replay overlaps active EEW reports and P/S fronts with tsunami bulletins, then tests earthquake confirmation, warning downgrade and cancellation.


### Implemented in 0.9.19
- Tsunami coastline flashing now changes phase once per second instead of every 500 ms.
- Coastline extraction samples both sides of every candidate outer-ring segment against a temporary rasterized Japan land mask, eliminating falsely flashing inland prefecture borders caused by non-shared/near-coincident TopoJSON arcs.
- New live EEW and confirmed-earthquake reports take camera priority over stale tsunami Fit-Japan requests, restoring automatic epicentre/footprint focus after testing or tsunami updates.

### Implemented in 0.9.20

- Restrict flashing tsunami segments to water connected to the open ocean.
- Reject enclosed lake/reservoir boundaries left over from the raster land-mask heuristic.
- Require multi-distance agreement to suppress hairline topology gaps along inland borders.

### Implemented in 0.9.21

- Keep the source/status control and settings cog anchored in one fixed top-right row.
- Stack sandbox, tsunami and EEW status messages beneath that row.
- Render JAPAN LIVE/version beneath the foreground legend layer, shifting the JST clock aside when both legends would cover it.
- Move active EEW report number and issued timestamp into the EEW banner.
- Cap the portrait summary detent so unusually tall combined alerts remain scrollable and leave more map visible.


### Implemented in 0.9.22

- Shift the JST clock around a solo Shindo ladder as well as the tsunami legend.
- Scale the entire Shindo legend with viewport height and hide only the values at the smallest size.
- Keep zoom controls available through a uniformly scaled horizontal compact layout.
- Hold automatic live-event focus for 15 quiet seconds, refreshing on each new EEW/earthquake report before returning to a clean Fit Japan map.



### Implemented in 0.9.24

- Denser Previous events rows with subtle separators.
- Resize-time cached land/intensity raster to reduce panel-drag map jank while preserving the precise vector render outside the drag.

### Implemented in 0.9.23

- Measure both event-summary columns and place **Close report** beneath the shorter one instead of reserving a separate bottom row.
- This follows the left metadata at compact text sizes and uses free space beneath the right action stack when larger text wraps the place name.
- Tighten spacing between **Focus event** and **Observed intensities**.

### Implemented in 0.9.25

- Collapse historical event time, magnitude and depth onto one compact metadata line.
- Reduce Previous events row padding and intensity-badge size.
- Use a clearly visible opaque divider between adjacent history entries.

### Implemented in 0.9.26

- Fixed cell-based report card with deterministic region/prefecture, intensity, metadata and action positions.
- Independently adaptive two-line headings and a Shindo-coloured maximum-intensity badge.
- Proportional report-card geometry scaling and a persisted 50–70% left-column tuning slider.
- Manual Android localisation resources for English, Czech and Japanese, replacing runtime machine translation.
- Simplified map header: version left, JST centred, status/settings right, and no JAPAN LIVE label.
- Preserve free-map camera state through portrait panel resizing.
- Keep event Focus active when selecting another report.
- 200 ms centre-out resize-handle arming with touch-slop drag activation and haptic feedback.
- Remove the four unnecessary-safe-call Kotlin compiler warnings.

### Implemented in 0.9.26a

- Removed visible report-grid borders and per-cell padding.
- Locked the maximum-intensity panel to a fixed title row and optional prediction/badge row.
- Compacted tsunami warning line spacing and removed the always-true Kotlin condition.

### Implemented in 0.9.26b

- Replaced fixed report-card heights with content-sized, top-aligned rows and disabled Compose font padding.
- Placed the optional prediction marker immediately to the left of the fixed right-aligned Shindo badge.
- Added subtle inactive backgrounds to enabled report buttons.
- Replaced the tall tsunami forecast-area AssistChip with a compact local control and removed remaining vertical gaps.


### Implemented in 0.9.26c
- Locked the report-card columns at 55/45 and removed the temporary testing slider.
- Rebuilt the top-right intensity readout: a narrow Shindo badge spans both computed location rows; `Max intensity` and lowercase `predicted` remain right-aligned with a fixed gap.
- Removed coordinates, inserted a fixed spacer row, and moved controls to a dedicated 25/50/25 row.
- Added localized `Depth` wording.
- Added tiny separators between expanded tsunami forecast areas.
- Relaxed the portrait summary-detent cap so tap-to-minimize can reach the event-list boundary for ordinary summaries.


### Implemented in 0.9.26d
- Removed the old 18% portrait minimum-panel floor so the tap detent follows the measured report/list divider.
- Increased the fixed report action row to 2.5× its font/line height.
- Allowed report action labels to wrap to two lines before adaptive shrinking or ellipsis.


### Implemented in 0.9.27

- Focused-map camera state now survives report-panel drag resizing without re-running the calculated Focus zoom.
- A minimised portrait panel follows the newly measured report divider after text-size changes.


### Implemented in 0.9.26e

- Wider bottom event panel using an 8 dp side inset.
- Scaled spacing between report action buttons.
- Button-only horizontal text padding for large fonts and wrapped translations.

### Implemented in 0.9.28

- Estimated EEW passage completion from official area arrivals, including cancellation and sparse-packet fallbacks.
- Separate active EEW overlay state so confirmed reports can arrive before wave passage completes.
- Expanding-ring camera fit with a ten-second manual pan/zoom override lease.
- Focused-camera refit when report footprints gain final observed areas.
- Stable blank second report row for place names without a prefecture.
- Gradle configuration cache enabled.

### Implemented in 0.9.29

- Added compact official JMA GIS geometry for 194 detailed earthquake regions, 56 public EEW warning areas, and 70 tsunami forecast coastlines.
- Colour and focus the finest reported region, with station-area codes/names and coordinates used before falling back to broader warning areas or N03 prefectures.
- Keep mainland Tokyo, the Izu Islands, and Ogasawara as separate camera/colouring footprints.
- Replace the approximate tsunami coastline splitter with official JMA forecast-zone linework.
