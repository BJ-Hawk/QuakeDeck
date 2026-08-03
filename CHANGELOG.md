# Changelog

QuakeDeck release history, newest first.

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
