# Local EEW intensity and JMA2001 travel-time prediction

## Status — implemented; automated validation complete; pending device/live approval

Implemented in `0.10.1-dev.3` (`versionCode` 222).

## Objective

Fill the regional-intensity gap in DM-D.S.S EEW bulletins without modifying,
merging into, or relabelling official provider values. QuakeDeck calculates a
separate local estimate from the EEW source parameters, displays its provenance
and limitations, and uses it for the selected-location notification decision
only when an official regional forecast is absent.

Every forecasting equation, coefficient, interpolation, aggregation rule,
quality gate, cache, and proxy-selection rule remains in the deliberately
Git-ignored `app/src/main/java/cz/misa/quakedeck/data/LocalEewForecastEngine.kt`.
Tracked source contains only provider metadata, contracts, attributed derived
resources, unavailable-state plumbing, presentation, and notification policy.

## Official method and inputs

- Ground-motion intensity follows JMA's current EEW technical reference:
  Mj-to-Mw conversion, point-source and finite-fault distance bounds, the
  hypocentral PGV relation, conversion to Vs=700 m/s, AVS30 amplification, and
  instrumental-intensity conversion.
- P-wave and S-wave arrivals/rings use the official JMA2001 travel-time table
  with interpolation in depth and epicentral distance. The former fixed 7/4
  km/s approximation is removed.
- Ground conditions use NIED J-SHIS V4 AVS30 sampled at QuakeDeck's bundled
  JMA intensity-station coordinates. The committed `.gz` is a derived station
  lookup, not a redistribution of the nationwide raw dataset. Its generator is
  `tools/build_local_eew_ground_resource.py`.
- The generated snapshot contains 4,277 station mesh records. Of those, 4,243
  have AVS30 inside the method's valid input range and cover all 188 reporting
  areas. Eighty-three uncovered/offshore station coordinates are recorded as
  missing and 34 zero-AVS records are excluded, never silently clamped or
  replaced.

References:

- JMA EEW calculation reference: https://ds.data.jma.go.jp/eew/data/nc/katsuyou/reference.pdf
- JMA2001 travel-time table: https://www.data.jma.go.jp/eqev/data/bulletin/catalog/appendix/trtime/tjma2001.zip
- NIED J-SHIS V4 surface-ground API: https://www.j-shis.bosai.go.jp/api-sstruct-meshinfo
- DM-D.S.S converted EEW schema: https://dmdata.jp/docs/reference/conversion/json/schema/eew-information/

## Implemented behavior

- DM-D.S.S parsing now preserves region codes, PLUM/warning flags, magnitude
  unit, assumed-hypocentre condition, and source-accuracy fields.
- Official regional forecasts always win. Local results live in a separate
  `localIntensityForecast` object and become presentation points only when the
  official list is empty.
- Regional maximum intensity and earliest S-wave arrival are independently
  selected from the station set, matching JMA's published aggregation rule.
- Each result contains point-source and finite-fault lower/upper intensity
  bounds. Estimates below instrumental intensity 3.5 are explicitly marked as
  extrapolations below JMA's published validation range.
- Source-plus-magnitude intensity calculation is suppressed for assumed-source
  PLUM bulletins and source depths greater than 150 km. Travel-time prediction
  remains bounded by the JMA2001 table instead of falling back to invented
  velocities.
- Selected-location estimates use the nearest bundled station's AVS30 only
  within 50 km and disclose that station as a ground proxy. No proxy means no
  locally calculated destination intensity.
- Notifications use a local selected-location estimate only when official
  regions are empty. Japan-wide mode uses the local nationwide maximum only
  when the provider maximum is unknown. Existing Forecast and Warning controls,
  thresholds, escalation identity, and P2PQuake fallback remain unchanged.
- Map shading, regional rows, destination countdowns, cold-start notification
  restoration, DM-D.S.S diagnostics, and the built-in Sandbox path understand
  the separate local result and label it as local.

## Validation and approval boundary

- FULL debug Kotlin compilation and unit tests pass with the ignored engine and
  both attributed resources present.
- LITE validation must continue to exclude the engine while compiling the same
  tracked contracts/resources and retaining official-provider behavior.
- No APK was built for this workstream. Device rendering, Sandbox behavior, and
  the next suitable live DM-D.S.S event remain pending approval; automated
  tests are not production proof.

## Mandatory cross-machine transfer reminder

`LocalEewForecastEngine.kt` is ignored by Git. Pulling or pushing this workstream
will transfer the matching tracked contracts and resources but **will not
transfer the engine**. Manually copy the updated file to the same relative path
on the other QuakeDeck machine and compile-check it there before describing that
machine's FULL build as current.
