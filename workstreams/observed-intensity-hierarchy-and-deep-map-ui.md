# Observed-intensity hierarchy and deep-map UI

## Objective

Turn Observed Intensities into a four-level prefecture, JMA reporting area,
municipality, and station navigator; add administrative map focus and stable
station details; and improve deep-map labels and inherited intensity context.

## User decisions already made

- Keep the existing Focus, Observed intensities, and Close controls.
- When a station is selected, show station information in the existing report
  card without changing its dimensions.
- Put the compact Earthquake/Station information toggle in place of the current
  two-line `MAX INTENSITY / predicted` label block immediately left of the
  large Shindo badge. Keep the badge and bottom controls in place.
- In station mode, change Close report to a contextual Close station info action.
- Use `outputs/station-name-audit/station_metadata_sources.json` as the
  authoritative station-details source. A compact generated runtime projection
  is acceptable; any work using or changing the source must be reflected in the
  Differentiate city reporting units continuation.
- Neighborhood boundaries and OSM are both explicitly excluded. Do not design,
  prepare, or implement either one in this workstream.
- Preserve the existing fixed report controls, floating navigation behavior,
  camera restoration, and station-selection behavior unless the new hierarchy
  requires a narrowly scoped extension.

## Work completed

- Reviewed the current prefecture-to-station UI, report-card layout, station
  focus state, three map vector tiers, and station metadata source.
- Confirmed the station metadata source contains 4,360 records and published
  addresses only where a source actually provides one.
- Added the four-level code-keyed hierarchy with independent animated expansion,
  inherited maximum intensities, guide rails, and the existing floating
  prefecture/station navigation behavior preserved.
- Added separate administrative-focus reticles immediately before each level's
  intensity badge. They fit the actual prefecture, JMA-area, or municipality
  geometry without turning the whole header into a focus action.
- Added station information inside the existing fixed earthquake report card.
  The compact toggle replaces the former two-line maximum-intensity caption
  immediately left of the unchanged large Shindo badge; station mode retains
  Focus station and Observed intensities and changes the last control to Close
  station info.
- Added a build-derived compact station-details resource. Every APK pre-build
  reads the authoritative audit JSON, validates all 4,360 station codes, and
  regenerates the resource so source changes cannot leave stale station-card
  data in a later APK.
- Added translucent collision-aware dot-and-name label chips and dim parent-tier
  intensity inheritance where a detailed JMA area or municipality has no direct
  observation. Direct observations always take precedence.
- Corrected the first device build after review: JMA-area and municipality
  headers now reproduce the prefecture floating/push-off behavior, including a
  selected hierarchy path and the later expanded header's secondary slot.
  Stable composition keys and opaque floating surfaces remove the stale label
  overlap visible behind the JMA-area name.
- Compared the exact 2026-08-21 21:02 Hachijojima payload with the bundled
  catalogue: all 93 Tokyo stations resolve, including the four Shindo-3 Miyake
  readings and all Shindo-2 ward/island readings. Added a 93-row hierarchy
  regression that verifies no station is dropped and each tier retains its
  actual maximum.
- Administrative focus now leases the camera as an explicit manual request and
  uses useful 12x JMA-area / 32x municipality minimum display zooms, preventing
  the ordinary event fit from immediately replacing it.
- Removed the invented dot inside translucent labels. Each label now encloses
  the real station marker at its true map coordinate; when collision rejection
  hides the label, that same marker remains visible alone.
- The `0.9.84y` device re-test proved that the earlier nested-header correction
  did not fix the missing rows or text overlap. Root cause: `ObservedAreaGroups`
  emitted every sibling JMA-area `Surface` directly into the same parent `Box`,
  so Compose placed all areas at identical coordinates. The last area visually
  covered the others, every recorded area-header top was identical, and neither
  maxima nor sticky navigation could correspond to what was visible.
- `0.9.84z` puts the JMA-area siblings inside one full-width vertical `Column`.
  Municipality groups and station rows already had vertical parents. The exact
  23:27 Kumamoto report is now covered as two JMA areas, six municipalities, and
  all 16 reporting stations: area `741` contains 15 stations with maximum Shindo
  2, while area `743` contains one station with maximum Shindo 1.
- The `0.9.84z` device re-test confirmed that sibling JMA areas and the working
  sticky hierarchy now display together, then exposed two separate data-path
  defects. Island reporting areas such as `357` 三宅島 and `355` 伊豆大島 were
  absent from the general English place dictionary, and deep-map municipality
  inheritance depended on the mutable runtime station cache.
- `0.9.84aa` extends the existing APK pre-build projection to validate and emit
  English names for all 188 station-backed JMA reporting areas plus all 1,894
  municipality-code → JMA-area/prefecture parent relationships from
  `station_metadata_sources.json`. The renderer now keys direct and inherited
  administrative intensity by stable codes, so an unreported municipality uses
  its faded area colour, then its faded prefecture colour, while a direct value
  still wins. The generated English catalogue contains zero Japanese labels.
- OSM and Neighborhood work were not prepared or implemented.

## Why this approach is used

Stable administrative codes prevent translated names or repeated locality
labels from merging unrelated nodes. Reusable hierarchy rows avoid copying the
existing prefecture navigation three times. A compact generated metadata
projection keeps research-only provenance out of the APK while preserving the
audit JSON as the source of truth. Administrative geometry and parent indexes
remain prepared or loaded off the UI thread.

## Current unfinished state

The expanded sticky hierarchy, real-marker labels, and sibling JMA-area stacking
now work together. `0.9.84aa` corrects the remaining English-area and inherited
municipality-colour data paths without changing those working layout behaviors.
The build remains unapproved/uncommitted and requires another device interaction
review; OSM and Neighborhood remain out of scope.

## Important things not to redo or change

- Do not replace the fixed Observed Intensities report with a sticky-after-scroll
  design.
- Do not merge station selection, visible information mode, and camera focus
  into one ambiguous state.
- Do not infer street addresses from rounded catalogue or provider coordinates.
- Do not load or derive heavy administrative geometry during Compose drawing.
- Do not commit, push, or finalize the cumulative hotfix without approval.
- Preserve unrelated files and the two existing untracked Python cache folders.

## Exact next steps

1. Build/deploy `0.9.84aa` from Android Studio and verify that every JMA-area
   header is English in English mode and that unreported municipality children
   retain the faded colour of their generated area/prefecture parent.
2. Confirm Close station info restores the camera in event-focus and free-camera
   entry paths, and that the information toggle does not deselect the station.
3. If the user approves the result, finalize the cumulative hotfix using the
   normal numeric-bump workflow; otherwise keep editing this same workstream.

## Logical changes and Git state

- Starting from local `main` at commit `82c8c05` (`0.9.84w`).
- The only pre-existing local status entries were untracked `tools/__pycache__/`
  and `tools/map-editor/__pycache__/`; they are unrelated and must remain
  untouched.
- No implementation commit or push has been made.
- Local implementation uses `0.9.84aa` / versionCode `206` with one cumulative
  `(in progress)` changelog entry and an Android Studio commit message prepared
  in `.gitmessage`.
