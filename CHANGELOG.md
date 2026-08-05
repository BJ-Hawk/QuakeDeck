# Changelog

QuakeDeck release history, newest first.

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
