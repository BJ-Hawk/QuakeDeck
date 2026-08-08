# Changelog

QuakeDeck release history
## v0.9.83j (cumulative since v0.9.83c)
- Makes official JMA report links wait until JMA has published the matching detail payload, then enables them automatically while the report card is open.
- Prepared the high-resolution N03 source pipeline out of the app package for future experiments. A disabled source-isolation hook have been prepared and remain available without affecting normal rendering.
- Map Editor: Improves the municipality-boundary workflow: refreshes corrected boundary data, clears obsolete inner warning-area borders, and adds an OpenStreetMap reference basemap plus separate coastline visibility.

## v0.9.83b
- Repairs the underlying municipality and middle-tier JMA polygon topology from one global planar coverage so every inland edge is shared by exactly two owners; one-owner edges are now valid only on the exterior/water boundary. This removes the duplicated single-owner shadow tails around three-way junctions instead of merely hiding or reclassifying them.
- Rebuilds municipality, warning-zone, prefecture, and JMA reporting-border resources from the repaired topology and scales build-time border matching with resource quantization so administrative classes remain stable at the higher repair precision.
- Adds the Windows QuakeDeck Map Editor with its fixed top editing toolbar, deep coastline-aware editing, vortice multi-selection, advanced point editing, Add point, Delete edge, Undo/Redo, automatic server shutdown, and bilingual place search.
- Adds a reusable geometry-first topology repair utility that validates the invariant before replacing app resources; the packaged resources are already repaired, so running the utility is not required for this release.
- Bumps the Android hotfix version to `0.9.83b` (`versionCode` 168) and updates the README release marker.

## v0.9.83a
- Reduces the compact notification place line to match the other three lines, preventing the final compact-detail row from being clipped.
- Makes earthquake and EEW notification taps report-aware: QuakeDeck opens the exact originating live/history report and focuses its event on the map instead of only opening the generic latest view.
- Shortens English notification place names only: removes `Region` and writes `Prefecture` as `Pref.` to preserve the compact card's usable width.
- Bumps the Android hotfix version to `0.9.83a` (`versionCode` 167) and updates the README release marker.

## v0.9.83
- Makes map/report interaction more dependable: Re-focus survives build/deploy, live updates no longer steal a manually selected report, historical frames retain their map association, and Return to Live preserves the current camera.
- Adds double-tap-to-zoom at the tapped point and centres the zoom label and controls without moving the rail. The control width now accommodates all supported zoom values through `128.00×`.
- Refines report lists: removes the redundant Region label, shortens English prefectures to `Pref.`, omits initial regional readings once a detailed report is available, and groups confirmed observed intensities into independently expandable prefectures with maximum-Shindo badges, compact indented station rows, provider chips, and scan dividers.
- Makes prefecture collapse scrolling predictable and scopes expansion to its event: frames of the same historical incident retain state, while closing the event, returning to Live, or selecting another event resets it.
- Adds verified English airport labels, improved initial-report area labels, and an official JMA detailed-report link from report status strips (Japanese for Japanese UI; English otherwise).
- Adds an optional on-device Japanese-to-English Google ML Kit model for observed place names without an official JMA English label. Before download they remain Japanese with guidance; translated labels show `English (Japanese)` and Google attribution. The Language settings section is localised, appears only for non-Japanese UI languages, and includes a test-only Delete action that fully resets the downloaded model and cached results.
- Adds a persisted 5–60 second delay to Sandbox live-pipeline injections for locked-screen notification testing, and reconnects P2PQuake’s routine WebSocket rollover promptly while retaining a conservative retry for unrelated background failures.
- Bumps the Android application version to `0.9.83` (`versionCode` 166) and updates the README release marker.

## v0.9.82
- Moves normal pan and pinch interaction onto the retained GPU-transformed map layer, then schedules a settled vector redraw after a short quiet pause. The pause is layer-aware: 5 ms for the simplified N03 tier and 1 ms for JMA and municipality tiers.
- Makes vector-layer selection follow the zoom currently visible during a gesture, preserving the intended 6.5× JMA and 21× municipality transitions without briefly showing a stale lower-detail layer.
- Replaces the nationwide N03 source with an offline-generated, topology-safe 1 km resource. It retains all 47 prefectures in 15,775 fill points and strokes 13,288 shared boundary edges exactly once through 47 precompiled prefecture-sized paths, eliminating the duplicated N03 border traversal. The prior 167 m candidate remains under `tools/source` as a rollback asset.
- Keeps detailed JMA and municipality geometry off the cold-start path, retains viewport culling for municipality fills, and keeps dynamic markers, labels, and alerts out of the expensive retained land render.
- Rebuilds municipality fills and outlines from a planar topology generated from the original JMA source rings. Noded shared arcs remove mismatched double municipal borders and assign only narrow source-boundary slivers to their nearest municipality, so neighbouring fills meet exactly without holes.
- Fills the visible municipality mesh in one antialiased even/odd pass instead of separately antialiasing every neutral municipal polygon, eliminating background-blended hairline seams before the shared municipal outline is stroked.
- Simplifies original municipality rings by twelve source units (about 133 m) before rebuilding the shared topology, reducing the installed municipal fill mesh from 547,503 to 285,875 points while preserving all 1,897 municipalities and one-copy boundaries.
- Splits shared municipality outlines into mutually exclusive fine, warning-zone, and prefecture batches. Each line is now drawn exactly once with its intended hierarchy stroke, instead of broad overlays repainting fine municipal borders.
- Precompiles the JMA middle-tier reporting-area source into 194 small fine/prefecture path groups. Prefecture edges are assigned once, removed from their fine paths, and drawn with the highlighted stroke without a giant merged Path or any on-device classification/splitting.
- Removes now build-only municipality and JMA JSON overlay sources from the APK; compact preclassified boundary resources are loaded only with their detailed map tier.
- Releases cached municipality geometry only after the settled view has remained at or below 16× for four seconds, avoiding threshold thrash near 21× while allowing the N03 and JMA tiers to return to their lighter steady-state memory footprint.
- Restores/updates the detailed-map loading states and localised status strings, and modernises the affected timing calls to Kotlin duration APIs.
- Bumps the Android application version to `0.9.82` (`versionCode` 148) and updates the README release marker.

## v0.9.81a
- Removes remaining cold-start work from the UI thread: EEW destination-area geometry resolves in the background, detailed JMA and municipality map layers load on demand, and holiday-cache preparation runs at background priority.
- Bumps the Android hotfix version to `0.9.81a` (`versionCode` 122) and updates the README release marker.

## v0.9.81
- Prevents a just-before-launch report recovered from the P2PQuake recent-feed endpoint from automatically focusing the map. Recovery still updates the latest report, history, and connection status, but only a packet received after QuakeDeck is already running can now claim the camera.
- Bumps the Android application version to `0.9.81` (`versionCode` 121) and updates the README release marker.

## v0.9.80
- Restores a small always-on cache of the latest confirmed earthquake reports immediately after a cold start, while clearly marking them as saved data until the current recent-report request completes.
- Starts the live socket, remembered-report hydration, current recent-report request, station catalogue, and map preload concurrently; remembered reports use a non-live update kind and can never trigger notifications.
- Removes the invariant 4096 × 4096 land-mask and ocean flood-fill from every launch by bundling build-generated prefecture coastline paths for both standard and high-resolution Japan geometry.
- Displays the base Japan map as soon as it is ready, loads regional and municipality detail afterward with a compact progress banner, and gives the base geometry priority over deep-layer parsing.
- Makes the saved collapsed portrait-panel state authoritative after the real summary detent is measured, so a minimized panel restores to the exact divider rather than just above it.
- Applies the static-analysis review by inlining the redundant saved-camera local variable.
- Bumps the Android application version to `0.9.80` (`versionCode` 120) and updates the README release marker.

## v0.9.79
- Restores the last user-controlled main-map centre and zoom separately for portrait and landscape, while keeping automatic EEW/report/tsunami framing temporary and leaving Fit Japan as an explicit reset.
- Persists portrait bottom-panel size, its collapsed/expanded state and restore detent, plus landscape side-panel width and collapsed state; divider writes are debounced until resizing stops.
- Removes the duplicated quiet-hours schedule summary from the main Quiet hours switch row, leaving the schedule readout only in the nested Schedule control shown while quiet hours are enabled.
- Bumps the Android application version to `0.9.79` (`versionCode` 119) and updates the README release marker.

## v0.9.78
- Completes a whole-project static-analysis cleanup across Compose UI, preference storage, map geometry, notification graphics, archive transactions, time synchronization, and historical report handling without changing intended behavior.
- Adopts AndroidX KTX helpers where they improve clarity, modernizes coroutine timing to Kotlin durations, removes redundant null assertions, qualifiers, variables, fixed parameters, and verified dead holiday/station bookkeeping.
- Adds the missing Czech plural categories and a focused lint configuration for intentional safety-policy, locale-packaging, adaptive-icon, and Japanese-postcode exceptions instead of allowing known false positives to bury real findings.
- Preserves the compile-time Sandbox master switch and future DM-D.S.S/legacy compatibility placeholders with explicit inspection intent, while deliberately deferring SDK, Gradle, Kotlin, and AndroidX upgrades until they can be build-tested.
- Bumps the Android application version to `0.9.78` (`versionCode` 118) and updates the README release marker.

## v0.9.77
- Fixes archived station observations that retain a preliminary area flag so their exact bundled station can still resolve and colour the correct municipality or ward.
- Replaces runtime raw-resource name lookups with compile-time `R.raw` references, protecting bundled map and place-name data from resource shrinking and eliminating false unused-resource reports.
- Makes in-app English, Czech, and Japanese selection safe for App Bundle delivery, adds explicit package visibility for Android settings screens, and declares the app's current left-to-right UI policy.
- Adds proper plural forms for historical-event and tsunami-area counts, makes decimal formatting follow the selected app language, and removes a private Compose resource-name collision.
- Improves custom-notification readability, adds adaptive-icon monochrome support and genuine circular legacy round icons, and removes small verified dead-code leftovers.
- Bumps the Android application version to `0.9.77` (`versionCode` 117) and updates the README release marker.

## v0.9.76d
- Adds one-shot live-pipeline injectors for a confirmed earthquake report, an EEW warning, and a tsunami warning from the dedicated Sandbox settings page.
- Sends injected snapshots through the same process-scoped runtime and notification coordinator as genuine WebSocket updates while keeping the current live or official Sandbox socket connected.
- Keeps synthetic events out of the raw archive and persistent incident history, preserves provider ordering state, clearly labels every test, and restores the genuine provider state after 45 seconds unless a real update arrives first.
- Applies normal notification toggles, intensity/tsunami thresholds, selected-location filtering, sound channels, and quiet-hour policy; the three deterministic built-in replay scenarios remain notification-muted.
- Bumps the Android hotfix version to `0.9.76d` (`versionCode` 116) and updates the README release marker.

## v0.9.76c
- Fixes all live and official Sandbox notifications being rejected by System UI because the custom `RemoteViews` frame used unsupported plain `View` elements.
- Replaces the four dynamic frame edges with supported `ImageView` widgets in compact, expanded, and heads-up notification layouts.
- Reuses one bounded 128 px badge bitmap across each notification and retries with Android's standard multiline template if an OEM rejects the custom card.
- Bumps the Android hotfix version to `0.9.76c` (`versionCode` 115) and updates the README release marker.

## v0.9.76b
- Replaces wrapped earthquake notification text with compact, heads-up, and expanded custom cards that keep the alert title, place, magnitude, and depth on deliberate separate lines.
- Adds dynamically rendered Shindo badges, using the selected location intensity when location filtering is active and preserving the Japan-wide maximum in the expanded details.
- Gives EEW notifications a bright yellow frame and predicted-intensity badge, while ended or cancelled alerts switch to a muted status treatment.
- Gives tsunami notifications a wave badge and a frame coloured by the highest relevant level: magenta for major warning, red for warning, yellow for advisory, blue for information, and grey for cancellation.
- Adds expanded tsunami height, arrival, affected-area, and per-zone details without changing notification eligibility, channel, sound, quiet-hour, or deduplication policy.
- Bumps the Android hotfix version to `0.9.76b` (`versionCode` 114) and updates the README release marker.

## v0.9.76a
- Keeps the middle JMA reporting-area layer visually restrained: only prefecture borders use the highlighted 3 px slate-blue stroke, while internal reporting-area boundaries return to the normal subtle map line.
- Reserves the amber 3 px warning-zone borders and municipality-border legend entries for the municipality layer.
- Bumps the Android hotfix version to `0.9.76a` (`versionCode` 113) and updates the README release marker.

## v0.9.76
- Adds station counts to the JMA, NIED, and local-government map-provider switches and separates the event-only station rule into a clear note beneath them.
- Color-codes prefecture boundaries in muted slate blue and JMA warning/reporting-zone boundaries in soft amber at the detailed and municipality tiers, with both major border types fixed at a 3 px screen-space stroke.
- Adds a compact map-border help popup for prefecture, warning/reporting-zone, and municipality boundaries whenever the detailed tiers are visible.
- Fades the left logarithmic zoom rail after map inactivity and restores it immediately on touch, pan, or zoom.
- Forces distinct weekday and weekend quiet-hour summaries onto two deliberate rows instead of relying on accidental word wrapping.
- Bumps the Android application version to `0.9.76` (`versionCode` 112) and updates the README release marker.

## v0.9.75b
- Removes the erroneous viewport-top exclusion from station markers, so idle provider dots and reported intensity stations no longer disappear along a fixed horizontal line.
- Makes the app Surface cover the edge-to-edge system-bar areas and makes the status/navigation-bar icon appearance follow the selected in-app light or dark theme on every device.
- Restores the strict preliminary-area rule: area-level intensity reports colour their warning zones only and cannot be misinterpreted as municipality station observations.
- Bumps the Android hotfix version to `0.9.75b` (`versionCode` 111) and updates the README release marker.

## v0.9.75
- Adds persistent idle-map station visibility switches for JMA, NIED, and local-government seismic-intensity networks.
- Keeps station-name visibility independent from provider filtering so labels can be disabled without hiding station dots.
- Makes active live, latest, and historical reports replace the idle catalogue with all and only their own observed stations, regardless of the saved provider switches.
- Shows no station markers for preliminary reports that do not yet contain point observations.
- Bumps the Android application version to `0.9.75` (`versionCode` 109) and updates the README release marker.

## v0.9.74a
- Corrects the 10×–32× source layer from the 56 broad public EEW forecast areas to the 194 detailed JMA earthquake-reporting areas, restoring subdivisions such as Kumamoto's four regions.
- Resolves observed Shindo directly against the detailed area geometry while retaining broad EEW-area colouring as a fallback beneath the fine boundaries.
- Keeps the v0.9.74 retained-parent cache fix and the exclusive N03/detailed/municipality tier boundaries unchanged.
- Bumps the Android hotfix version to `0.9.74a` (`versionCode` 102) and updates the README release marker.

## v0.9.74
- Restores the complete 56-zone JMA EEW map division from 10× up to, but not including, 32×, including neutral land, warning-zone boundaries, and reported Shindo fills.
- Keys the retained off-screen map parent at the 10× and 32× tier boundaries so its cached N03 or JMA texture cannot survive into the next vector tier.
- Removes the ineffective child-layer cache workaround introduced after v0.9.73.
- Bumps the Android application version to `0.9.74` (`versionCode` 101) and updates the README release marker.

## v0.9.73
- Adds mutually exclusive map-vector tiers: N03 prefectures below 10×, JMA EEW areas from 10× to below 32×, and municipalities/wards at 32× and above.
- Aggregates the highest reported Shindo independently for prefectures, EEW areas, and municipalities, while explicitly painting every unreported polygon with the neutral map color.
- Removes the configurable municipality threshold and the shared high-resolution geometry mutation so no prefecture, EEW, or municipality vector leaks into another tier.
- Prepares municipality geometry concurrently, limits divider-resize raster use to the N03 tier, and reuses the municipality spatial index for deep-zoom viewport culling.
- Corrects detailed-tier selection to follow the effective on-screen zoom and removes N03 fallback coastlines from JMA EEW and municipality views.
- Rebuilds the retained vector child at tier changes and gives the 10×–32× range a dedicated high-contrast boundary stroke from the 56 official JMA EEW zones.
- Completes a separate behavior-preserving performance and reliability pass across the Android app and companion website.
- Reuses compiled status/search patterns, JST formatters, and timezone objects instead of rebuilding them during live updates and UI rendering.
- Caches historical-event timestamps and derived filter state so date filtering and sorting no longer reparse timestamps inside every comparison.
- Adds a final notification-permission guard, API 26/27-safe navigation-bar theme resources, and an explicit cleartext-traffic prohibition.
- Adds six JVM regression tests covering event-time display, Japan map coverage, quiet-hours serialization, malformed schedules, overnight boundaries, and public holidays.
- Protects the OAuth callback from referrer leakage, limits local-data cleanup to QuakeDeck's own session keys, and aligns the website, privacy policy, terms, and README with the existing one-shot Socket Start/Close probe.
- Resolves all blocking Android lint findings; debug compilation, unit tests, APK assembly, and lint verification pass.
- Bumps the Android application version to `0.9.73` (`versionCode` 100) and updates the README release marker.

## v0.9.72
- Approximately doubles the visible JMA EEW/warning-area border weight in municipality zoom.
- Expands only the municipality-clipped JMA warning outlines; prefecture and fine municipality borders keep their existing thickness.
- Keeps the asynchronous municipality-layer loading and border preparation introduced in v0.9.71 unchanged.
- Bumps the Android application version to `0.9.72` (`versionCode` 99) and updates the README release marker.

## v0.9.71
- Fixes the municipality layer failing to appear after v0.9.70 by removing synchronous prefecture/JMA path processing from the municipality loader.
- Installs the municipality land mask immediately, allowing neutral land, observed fills, coastline, and fine municipality borders to render without waiting for broad-boundary preparation.
- Keeps the original high-resolution N03 borders visible between 8× zoom and the configured municipality threshold until replacement broad borders are ready.
- Builds municipality-clipped prefecture and JMA EEW/warning-area outlines asynchronously, then installs them as the thicker border hierarchy above the municipality mesh.
- Bumps the Android application version to `0.9.71` (`versionCode` 98) and updates the README release marker.

## v0.9.70
- Replaces the empty high-resolution boundary path introduced in v0.9.69 with municipality-clipped prefecture and JMA EEW-area outlines, so broad borders remain visible after returning below the municipality threshold.
- Keeps the municipality union as the authoritative detailed land/coast geometry once loaded, preventing the displaced N03 coastline from returning.
- Makes prefecture outlines thicker than JMA warning-area outlines, while the existing municipality borders remain the finest level at deep zoom.
- Handles either asynchronous load order: the prepared municipality geometry is applied whether the high-resolution N03 map or municipality dataset finishes first.
- Bumps the Android application version to `0.9.70` (`versionCode` 97) and updates the README release marker.

## v0.9.69
- Uses the detailed JMA municipality/ward polygons as the authoritative high-zoom land mask instead of leaving the N03 land geometry underneath them.
- Removes the second N03 boundary/coastline overlay at high zoom, so the visible coast follows the same geometry as municipality fills.
- Builds the neutral grey land from every municipality polygon before observed municipalities are coloured, eliminating protruding N03 land around unobserved areas.
- Retains N03 prefecture data for lower zoom levels and existing broader report fallbacks.
- Bumps the Android application version to `0.9.69` (`versionCode` 96) and updates the README release marker.

## v0.9.68
- Persists the municipality-detail threshold slider instead of resetting it to 40× whenever QuakeDeck starts.
- Restores the saved whole-number threshold from the existing `quakedeck_settings` preferences and clamps it to the established 24×–64× range.
- Saves slider changes immediately so orientation changes and later app launches retain the selected value.
- Bumps the Android application version to `0.9.68` (`versionCode` 95) and updates the README release marker.

## v0.9.67
- Fixes the contextual Settings help boxes opened from the circled `?` controls so their titles, body text, and action buttons follow QuakeDeck's configured Text size, including the live Settings preview.
- Fixes the date/time synchronization information box opened from the top status drawer so its title, detail rows, explanatory notes, and close action follow the configured Text size.
- Captures QuakeDeck's app density before the separate Material dialog window and restores it independently inside every compact dialog title, body, and button slot, preventing fallback to Android's system font scale.
- Bumps the Android application version to `0.9.67` (`versionCode` 94) and updates the README release marker.

## v0.9.66
- Fixes Settings overlay information/help dialogs ignoring the live Text size preview.
- Explicitly provides the current preview font density to notification help, alert-location, quiet-hours schedule, and quiet-hours mode overlays, including their nested dialogs.
- Overlay text now resizes immediately while the Text size slider is adjusted, matching the underlying Settings page before the selection is committed.
- Bumps the Android application version to `0.9.66` (`versionCode` 93) and updates the README release marker.

Earlier release history is preserved in [`CHANGELOG_HISTORY.md`](CHANGELOG_HISTORY.md).