# Local EEW intensity and JMA2001 travel-time prediction

## Status — implementation finalized in 0.10.1; device/live validation tracked separately

The approved implementation is finalized as `0.10.1` (`versionCode` 233),
including official-authoritative hybrid coverage in live view and historical
EEW replay. The release is no longer in progress. Remaining device/live checks
are validation follow-ups, not an unfinished development-version marker.

## Objective

Fill the regional-intensity gap in DM-D.S.S EEW bulletins without modifying or
relabelling official provider values. QuakeDeck calculates a separate local
estimate from the EEW source parameters, merges only missing areas into the
presentation, displays its provenance and limitations, and uses it for the
selected-location notification decision only when that location lacks an
official regional forecast.

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
  `localIntensityForecast` object; presentation merges them by stable JMA area
  code and uses local results only for missing areas.
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
- Notifications use an official selected-location prediction when one exists
  and otherwise use the local destination estimate even if another area has an
  official value. Japan-wide mode uses the local nationwide maximum only when
  the provider maximum is unknown. Existing Forecast and Warning controls,
  thresholds, escalation identity, and P2PQuake fallback remain unchanged.
- Map shading, regional rows, destination countdowns, cold-start notification
  restoration, DM-D.S.S diagnostics, and the built-in Sandbox path understand
  the separate local result and label it as local.
- When DM-D.S.S provides an official nationwide maximum but no official
  regional list, the engine applies the smallest continuous-intensity offset
  needed to put the strongest local station inside that official category.
  The provider remains authoritative for amplitude while the local estimate
  supplies only the spatial distribution.
- When official regions are present, their intensity categories become
  calibration anchors alongside the nationwide maximum. Officially omitted
  areas are constrained below Shindo 4; any irreconcilable local result is
  capped at Shindo 3 and counted in diagnostics instead of contradicting the
  provider. The official value, arrival, PLUM flag, Warning flag, and event
  classification are never overwritten.
- Modelled Shindo 0 remains available for selected-location decisions and the
  destination card, but is not drawn as a regional map overlay. Unaffected or
  unforecasted land therefore keeps the normal neutral map colour.
- Forecast and Warning now have distinct yellow-versus-red active-map and
  event-panel treatment.
  The destination card translates its ground-proxy name through the selected
  UI language and uses a compact layout while retaining provenance and the
  below-validation-range disclosure.
- Expanding predicted intensities during an active EEW keeps the report controls
  pinned without retaining the hidden inline EEW banner's height, so the
  destination card sits directly below the fixed report card in both Sandbox
  and live DM-D.S.S regional forecasts.

## Historical replay — completed 2026-09-02

- Each matched archived EEW revision calls the existing intensity engine once
  on the archive executor. It uses that revision's hypocentre, magnitude,
  provider maximum, and official regions, never the final confirmed event.
- The shared `presentationIntensityPoints()` path supplies the same
  official-first hybrid map colours as live, excludes modelled Shindo 0, and
  requests detailed JMA geometry even when the official region list is empty.
  A compact localized note identifies local supplements in the historical panel.
- New archive frames preserve magnitude unit, hypocentre condition, source
  accuracy, region codes, PLUM/Warning flags, and cancellation state. The
  provider-wide maximum remains authoritative even if regional values differ.
- Existing archives remain readable and can calculate from their retained
  inputs. Previously omitted metadata cannot be reconstructed. Estimates are
  recomputed using the installed engine/resources, not claimed to be an exact
  snapshot of an older engine's output.
- Felt frames carry forward their preceding EEW estimate; confirmed-report
  frames retain official observations only. Historical navigation still does
  not enable P/S rings, destination countdowns, live notifications, or automatic
  refits between report steps.
- LITE or an unavailable/no-result engine retains official areas only. No
  equation, coefficient, resource, forecast contract, or ignored engine file
  changed for this replay extension.

## Validation and approval boundary

- FULL debug Kotlin compilation and unit tests pass with the ignored engine and
  both attributed resources present.
- LITE validation must continue to exclude the engine while compiling the same
  tracked contracts/resources and retaining official-provider behavior.
- The replay regression suite covers archived input preservation, per-revision
  calculations, official/local presentation parity, legacy empty-region frames,
  parser sorting, LITE/no-result handling, and cancelled/confirmed exclusions.
- No APK was built for this release task. Historical map rendering still needs
  a device check; broader hybrid-map, selected-location, omission-ceiling, and
  live DM-D.S.S coverage remain follow-ups. Automated tests are not production
  proof and release finalization does not change that boundary.

## Mandatory cross-machine transfer reminder

`LocalEewForecastEngine.kt` is ignored by Git. This replay extension did not
change it, but pulling or pushing the tracked plumbing **will not transfer the
engine**. The other QuakeDeck machine still needs a manually copied matching
engine at the same relative path and a compile check before its FULL build can
be described as current. Without it, historical replay remains official-only.
