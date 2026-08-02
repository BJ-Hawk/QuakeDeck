# Changelog

QuakeDeck release history, newest first.

## v0.9.65

- Replaces the previous margin-multiplier interpretation with a hard screen-space constraint based on the true projected N03 extremes.
- In portrait, the left-most N03 point can never move to the right of 25% of the screen and the right-most point can never move to the left of 75%; top and bottom use 30% / 70%. Landscape swaps the 25% and 30% allowances.
- Calculates the 1× scale so Japan already spans at least those inner boundaries, then applies the same limits to every pan, pinch, zoom-button, focus, fit, resize and orientation clamp.
- Removes the old pan rule that allowed the map to be dragged until only 10% of the shorter viewport dimension remained visible.

## v0.9.64

- Corrects the 1× Japan framing to use the true left-most, right-most, top-most, and bottom-most projected N03 points, removing the old hidden 3.5% geometry padding.
- Applies the requested margins per edge rather than as a total viewport increase: portrait adds 25% of Japan's full width on both left and right and 30% of its full height above and below; landscape swaps those values.
- The resulting target span is therefore 150% × 160% of the raw N03 bounds in portrait and 160% × 150% in landscape, shared by drawing, resize preservation, and pan clamping.

## v0.9.63

- Adds a temporary municipality-detail threshold slider directly along the map's bottom edge. It defaults to 40×, updates the municipality/ward layer live, and allows whole-step tuning from 24× through 64×.
- Raises the minimum camera zoom to 1×. At 1×, portrait viewports are 25% wider and 30% taller than Japan's bounds; landscape viewports use 30% horizontal and 25% vertical context. Drawing and pan clamping share the same orientation-aware fit calculation.
- Removes only the ordinary `DETAILED INTENSITY REPORT` metadata label from entries in the Recent earthquakes list. Corrections and every other report-stage label remain, and the detailed label remains on the main event card.

## v0.9.62a municipality geometry size hotfix

- Rebuilds the deep-zoom JMA municipality layer with phone-appropriate geometry detail instead of preserving near-source coastline density nationwide.
- Uses approximately 55-metre shape simplification and 11-metre coordinate quantisation, still finer than the useful screen resolution when municipality rendering begins above 64× zoom.
- Upgrades the municipality binary format to ZigZag variable-length coordinate deltas. The bundled resource falls from 4,955,891 bytes to 941,694 bytes (81% smaller) without removing any of the 1,894 reportable municipalities or designated-city wards.
- Retains backward decoding support for the original fixed-width v1 resource format and keeps municipality loading lazy.

## v0.9.62 deep-zoom municipality intensity map

- Adds the official JMA `20241128_AreaInformationCity_quake_GIS` municipality/ward layer with 1,894 reportable polygons, simplified and bundled as a quantized gzip resource.
- Keeps the N03-derived Japan map and existing JMA detailed-region colouring through 64× zoom. Above 64×, actual observation stations colour their containing JMA municipality or designated-city ward by the strongest station value there.
- Leaves municipalities without a resolved observation neutral rather than inheriting the broader reporting region's maximum; exact station dots remain visible above the polygons.
- Loads the municipality resource only after deep zoom is requested, streams its binary payload without building a giant temporary JSON tree, and uses a projected spatial index for station lookup plus viewport culling for rendering.
- Draws fine municipality boundaries only in the deep-zoom view, while retaining the stronger N03 prefecture/coast outline as the national base.
- Raises maximum map zoom from 64× to 256× and makes the +/- controls use progressively larger steps beyond normal regional zoom.
- Extends `tools/build_jma_area_geometry.py` so any JMA layer can be rebuilt independently, including the municipality archive.

## v0.9.61 country-specific yearly public-holiday calendars

- Hotfix: enabling public holidays starts the yearly download immediately and the schedule dialog now updates live from downloading to ready without being closed and reopened.
- Hotfix: compacts the holiday-country picker, slightly enlarges individual-day schedule labels, and limits manual selection to countries currently covered by Nager.Date. Unsupported automatic network results fall through to the SIM or phone region instead.
- Replaces the seven-day worldwide holiday feed with Nager.Date's complete yearly calendar endpoint.
- Downloads holiday data only for the automatically detected or manually selected country, with the ISO country code included in the provider request as explicitly accepted by the user.
- Caches the complete current and following calendar years locally and refreshes each loaded year weekly; failed refreshes preserve the last valid calendar.
- Retains every public-holiday record returned for that country, including subdivision-scoped entries. Country-wide holidays affect quiet hours now; regional entries remain cached for a later subdivision selector.
- Removes the provider-wide worldwide-feed request. Manual selection is restricted to Nager.Date's published country coverage list; no unsupported ISO countries are shown.

## v0.9.60 direct worldwide public-holiday data

- Removes the project-hosted holiday file, bundled Czech/Japanese seed, and holiday dataset builder tooling.
- Downloads data directly from the public Nager.Date API. Every installation requests the same supported-country list and the same worldwide upcoming-holidays feed, so the selected or detected country is never disclosed.
- Includes every public-holiday record returned for every supported country, retaining both country-wide and subdivision-scoped dates. Country-wide dates are applied now; subdivision data is cached for later regional selection.
- Refreshes the common worldwide window twice daily and retains recent overlap for overnight schedules; a failed refresh cannot overwrite the last valid cache.

## v0.9.59 weekly quiet-hours schedule and public holidays

- Replaces the single shared quiet-hours range with separate weekday and weekend schedules, each with its own enable state and start/end time.
- Adds optional per-day overrides so Monday through Sunday can each use different hours when needed, while retaining the simpler weekday/weekend defaults.
- Adds **Include public holidays** beneath the weekend schedule. A public holiday reuses the weekend hours even when it falls on a weekday.
- Detects the holiday country entirely on-device using the current mobile-network country first, then SIM country, then phone region, with a persistent manual override. No carrier, SIM identifier, detected country or schedule setting is transmitted.
- Bundles a bootstrap public-holiday calendar and adds a generic dataset updater: every installation requests the same file and selects its country locally.
- Keeps overnight schedule semantics explicit: a period belongs to the day on which it starts, and can continue into the following morning.
- Migrates the v0.9.58 day mask and shared time range into the new weekly schedule without changing existing quiet-hour behavior.

## v0.9.58 notification policy and Settings hierarchy

- Rebuilds Notifications as a clear parent/child hierarchy. Turning off the master switch now collapses the complete section, while disabled alert types hide their dependent controls instead of leaving a field of greyed-out settings.
- Separates the persistent reference location from optional location filtering and shows inherited coverage explicitly for earthquake reports, EEW and tsunami alerts.
- Moves the lower-intensity silent-report policy under Earthquake reports and makes it independent of location filtering: it can deliver all confirmed reports across Japan silently below either the selected-location threshold or the event-wide threshold.
- Adds compact nested controls and contextual circled-help overlays for location filtering, coverage, lower-intensity reports, updates and quiet-hour behaviour.
- Expands quiet hours into a local-time schedule with editable start/end times, selectable weekdays, and three policies: critical alerts only, everything silently, or no notifications at all. Overnight schedules treat the selected day as the day on which quiet hours begin.
- Avoids reopening Notification delivery setup when notification permission is already granted and battery use is already unrestricted.
- Reorganizes Report archive controls beneath Store received reports. Download and automatic backfill hide when storage is disabled, while Browse archive remains available whenever saved reports exist; archive count and size now sit beside Browse.

## v0.9.57 notification sound policies

- Adds a nested **Silent notifications for reports under selected intensity** option beneath location-based notifications. When enabled, every confirmed earthquake report is still delivered, but reports that do not reach the chosen intensity at the selected location use a dedicated silent channel.
- Keeps the selected intensity as the audible-alert threshold: if a previously silent incident later reaches the threshold at the selected location, it is promoted to an audible notification once; later report revisions remain silent.
- Clarifies that location-based notification filtering applies to all alert classes: ordinary earthquake reports use local observed intensity, while EEW and tsunami notifications are sent only when the selected location is affected.
- Adds a nested **Silent all notifications during quiet hours** option. When enabled, all otherwise eligible notifications—including EEW and tsunami warnings—are delivered through the silent channel during quiet hours instead of waking the user.
- Preserves the original quiet-hours behaviour when the new option is disabled: ordinary reports are suppressed while urgent EEW and tsunami warnings may still alert.
- Separates EEW relevance tracking from confirmed-report tracking so silent all-report delivery cannot accidentally produce an unrelated EEW-ended notification.

## v0.9.56 manual alert location and location-aware warnings

- Adds a persistent user-selected alert location, defaulting to Tokyo, without requesting Android location permission.
- Adds city/postcode search in Notification settings. City lookup accepts Japanese and romanized names, normalizes diacritics and common variants such as **Tokyo**, **Tokio**, **Tōkyō** and **Toukyou**, and uses Android's system geocoder.
- Accepts either all seven digits of a Japanese postcode or only the first three digits for a deliberately broader postal area.
- Adds an optional **Location-based notifications** policy. Confirmed earthquakes use the observed intensity in the selected detailed JMA area, EEW alerts require the selected public EEW area to be included, and tsunami alerts are matched to the selected prefecture's forecast zones.
- Keeps ordinary all-Japan notification behaviour as the default; selecting a city changes the EEW destination immediately, while notification filtering remains explicitly opt-in.
- Replaces the hardcoded Tokyo EEW destination, official forecast-area lookup, countdown and map destination marker with the selected location.
- Preserves silent lifecycle updates and only issues cancellation/end notifications for location-relevant incidents that previously notified the user.
- Structures the saved location as a coarse representative point plus JMA detailed/EEW area metadata so a later Android approximate-location source can feed the same policy without redesigning it.

## v0.9.55 notification delivery setup

- Adds a guided notification-delivery setup when notifications are enabled: Android notification permission first, followed by background battery configuration.
- Requests exemption from battery optimization through Android's system dialog and shows the current unrestricted/optimized state in Notification settings.
- Adds a compact, reopenable **Delivery setup** row so users can review or repair delivery prerequisites later.
- Detects Google Pixel devices and explains Notification cooldown, including the Settings > Notifications > Notification cooldown path and a shortcut to system notification settings.
- Clearly states that unrestricted battery use improves process-alive background reception but does not guarantee delivery after Android kills or force-stops QuakeDeck.
- Updates notification-description text to reflect the v0.9.54 process-scoped background receiver.

## v0.9.54 process-scoped background reception

- Moves the live P2PQuake provider and notification coordinator from the Activity/Compose lifecycle into a process-scoped `QuakeDeckRuntime`.
- Keeps live WebSocket reception, reconnect handling, report archiving and notification evaluation active while QuakeDeck is merely in the background and the application process remains executable.
- Makes notification delivery independent of Compose recomposition, so a stopped Activity no longer prevents a newly received report from being evaluated.
- Activity destruction or recreation now detaches only the visible UI; it no longer closes the live provider.
- Reopening QuakeDeck immediately receives the latest process-held snapshot and the provider's actual last-update time.
- Deliberately does not promise operation after Android freezes or kills the process; persistent foreground-service monitoring remains the following reliability milestone.

## v0.9.53 camera-control and provider-status fixes

- Completes localization of runtime provider status text shown in the Data source dialog and status drawer, including live earthquake report stages, EEW lifecycle states, tsunami updates, recovery messages, and source synchronization states.
- Prevents startup/bootstrap races from automatically focusing the most recent report; only a genuinely newer live-update sequence may claim the camera after the initial UI state is established.
- Limits delayed camera reclamation after a user pan or zoom to an active EEW. Ordinary earthquake reports now leave the manually positioned camera alone.
- Makes the map **Fit Japan** control clear both Focus and Re-focus state while preserving the selected report text, so the event can be focused again explicitly later.
- Updates outbound QuakeDeck User-Agent version identification to match the current build.

## v0.9.52 unified string-resource localization

- Replaces the legacy exact-English-sentence lookup map with direct `R.string` resource IDs throughout the established UI, including the map, report cards, Settings, Sandbox, status drawer, data-source dialog and historical report browser.
- Removes `UiLocalization.text(...)` and the fragile English-key `resourceIds` map entirely; changing punctuation or wording at a call site can no longer silently break translation.
- Keeps runtime provider connection states in a dedicated status formatter that maps known state messages and parameterized replay/disconnection states to localized string resources.
- Converts conditional labels—report controls, historical sorting, tsunami grades and bulletin/arrival states, EEW arrival labels and Settings navigation—to choose resource IDs before formatting.
- Keeps JMA place-name translation separate from interface localization while moving its generic fallback labels to direct resources.
- Validates identical 319-key English, Czech and Japanese resource sets, all referenced `R.string` IDs, and the absence of legacy sentence-based localization calls.

## v0.9.51 notification localization and resource-ID cleanup

- Localizes the complete Notifications settings section in English, Czech and Japanese, including permission guidance, thresholds, quiet-hours text and the test-notification control.
- Localizes all Android notification channel names/descriptions and all earthquake, EEW, tsunami, cancellation and test-notification content through direct `R.string` resource IDs.
- Removes hardcoded notification titles, descriptions, depth/intensity wording and threshold labels from Kotlin; notification resources no longer depend on exact English-sentence lookup keys.
- Recreates the existing Android notification channels when the selected app language changes so their user-visible names and descriptions follow the chosen language while retaining stable channel IDs and user settings.
- Displays language choices as autonyms in every interface language: **English**, **Čeština** and **日本語**.
- Keeps magnitude notation locale-neutral (`M4.6`, never `M4,6`) while localizing the surrounding notification detail text.

## v0.9.50 clock-detail overlay and translated notification places

- Simplifies the drawer clock status line to the active time source only; RTT and device-clock difference no longer occupy the always-visible header.
- Makes the displayed date/time, synchronization status, and Sandbox Actual time open the same compact time-information dialog.
- Shows the active source, synchronization state, RTT explanation/value, device-clock difference in plain ahead/behind wording, last successful synchronization, sampling method, monotonic clock basis, retry schedule, and any last synchronization error.
- Translates earthquake epicentre names and tsunami forecast-area names in Android notifications through the same configured JMA place-name language path used by the main UI, eliminating raw Japanese names in English/Czech mode.

## v0.9.49 aligned status header, compact settings and origin-aware navigation

- Rebuilds the expanded status header as a true row-aligned two-column grid: the app name and displayed clock now share one row, while the version and NICT synchronization note share the next row. Sandbox **Actual time** remains a separate third row.
- Hides the seconds field from human-facing earthquake occurrence times when it is exactly `:00`; canonical timestamps and non-zero EEW seconds remain unchanged internally and on screen.
- Applies the drawer's compact typography and spacing hierarchy across the main Settings and Sandbox pages: tight title/description blocks, smaller card padding and deliberate gaps only between real sections or controls.
- Keeps Settings composed beneath the Data source dialog and Past reports browser, preserving the exact Settings page and scroll position when **Done** or **Back** is pressed.
- Records whether Data source or Past reports was opened from Settings or the map, returning to that origin; selecting an archived event still intentionally opens it on the map.
- Extends the reconstructed pre-v0.9 changelog from the original build-publication messages instead of leaving the v0.2-to-v0.9 gap undocumented.

## v0.9.48 compact, content-sized status drawer

- Replaces the fixed 350 dp portrait / 250 dp landscape drawer cap with content-sized expansion, limited only by the available screen height; scrolling is used only when the drawer genuinely cannot fit.
- Keeps the pull-down animation tied to the drawer's measured height, so compact and Sandbox drawers open to their actual required size instead of a guessed preset.
- Removes blanket spacing between every drawer child and applies larger gaps only between real sections.
- Gives the header, service states, descriptions, last-update text, info rows, clock note, Historical card and Sandbox card explicit compact line heights.
- Tightens Sandbox/Historical card padding while preserving readable separation before their action buttons.
- Splits the former changelog-in-disguise into a real `README.md` and `CHANGELOG.md`, and seeds Android Studio to open `CHANGELOG.md` as the initial editor tab for a newly extracted standalone version folder.

## v0.9.47 status-chrome and changelog cleanup

- Simplifies the fixed top status bar to a compact `v0.9.47` label; the full QuakeDeck name remains in the expanded drawer.
- Moves the JST synchronization state out of the Connections list and places one compact source/latency/device-offset note directly below the drawer clock.
- Keeps the synchronization indicator source-aware: green for a direct NICT NTP result, amber for a retained/fallback network anchor, and red while only device time is available.
- Reorders the release history newest-first and restores the early v0.1–v0.2.1 entries currently documented in the project.

## v0.9.46 independently synchronized JST clock

- Synchronizes the visible live JST clock directly from NICT's public `ntp.nict.jp` service instead of deriving it solely from the phone wall clock.
- Takes three lightweight SNTP samples and keeps the response with the lowest measured network delay.
- Anchors synchronized epoch time to Android's monotonic `elapsedRealtime()` clock, so later manual changes to the phone date, time, or timezone do not move QuakeDeck's JST display.
- Uses Android's non-user-adjustable network-time clock as a fallback on Android 13 and newer when direct NTP is unavailable.
- Falls back visibly to device time only when no independent source has yet been obtained; after a successful sync, temporary network failure retains and advances the last synchronized anchor.
- Resynchronizes every six hours while QuakeDeck is running and retries after five minutes when no independent source is available.
- Adds a Japan Standard Time status row to the pull-down drawer with source, sync state, NTP round-trip time, and the measured offset from the device wall clock.
- Keeps Sandbox scenario time unchanged, while its secondary Actual time readout and manual historical browsing use the independently synchronized live JST reference.

## v0.9.45 notification foundation

The notification milestone introduces a central `NotificationCoordinator` rather than posting directly from providers. It consumes genuine WebSocket progression (`liveUpdateSequence`), applies persistent user policy, and uses stable incident tags so report serials update existing Android notifications instead of creating duplicates.

Included in this foundation:

- Android 13+ notification permission flow and channel creation
- separate channels for EEW, tsunami alerts, earthquake reports, quiet updates and tests
- master, earthquake, EEW, tsunami and lifecycle-update switches
- minimum earthquake intensity and tsunami alert level
- 22:00–07:00 quiet-hours policy for ordinary reports
- urgent EEW and tsunami-warning exemption from quiet hours
- test notification control and permission-state warning
- official P2PQuake sandbox events use the exact same notification path and appearance as production events
- only the three deterministic user-started built-in replay fixtures are excluded from Android notifications

This stage intentionally monitors only while the existing in-app provider is alive. Foreground background monitoring, saved locations, geometry-based relevance and DND policy access remain the next notification stages.

## v0.9.44a archive deduplication and history navigation hotfix

- Adds a database-level semantic uniqueness key for JMA earthquake bulletins, independent of P2PQuake wrapper IDs and reception timestamps.
- Repairs existing duplicate live/backfill pairs during the archive schema upgrade instead of merely hiding them in the historical UI.
- Keeps true follow-up and correction bulletins separate by using issue time, report type, correction scope, event ID, and earthquake origin time as the bulletin identity.
- Orders Associated reports by original P2PQuake reception sequence and uses the earthquake frame index as the tie-breaker, so numbered reports remain in 1, 2, 3… order.
- Adds the floating **Top** button to the Past reports browser, matching the Recent earthquakes and Associated reports lists.

## v0.9.44

- Added an Associated reports list below the historical report controls.
- Archived earthquake reports are clickable and jump directly to their cumulative historical frame.
- Conservatively associated archived EEW detection/report and tsunami packets appear in chronological order as information-only rows.
- Opening Observed intensities replaces the Associated reports area and closing it restores the list.
- The portrait minimum panel detent now anchors between the historical controls and the Associated reports list.

## v0.9.43c focus toggle hotfix

- Pressing an already active **Focus event** now clears the mapped report and returns the camera to Fit Japan.
- After manual pan/zoom, **Re-focus event** still restores the event footprint in one press.
- Applies equally to live, selected-history, and manual historical-report viewing.

## v0.9.43b compact historical header

- Removed the oversized inherited line height from the historical-report summary card.
- Removed its vertical content padding and reduced surrounding spacing.
- Tightened the card corners and horizontal inset without changing historical browsing behavior.

## v0.9.43a compile hotfix

- Escapes Android positional-format dollar signs in Kotlin source (`%1\$d`, `%2\$d`) so they remain literal `%1$d` / `%2$d` placeholders at runtime instead of being parsed as references to a nonexistent Kotlin variable named `d`.
- Fixes the archived-event result counter in both the localization lookup and the Past reports browser.

## v0.9.43 historical report browser

- Adds a full-screen **Past reports** browser for locally archived earthquake incidents, opened from the Report archive section in Settings.
- Sorts archived events by occurrence time or accumulated maximum intensity, and filters them by inclusive calendar dates plus exact Shindo checkboxes. An empty intensity selection means All.
- Opens every incident at its oldest archived report and manually steps through First, Previous, Next, and Last report states without timed playback.
- Reconstructs each displayed frame cumulatively: an initial intensity report remains visible when the later hypocentre report arrives, and later combined, detailed, distant, or corrected reports update only the data they actually provide.
- Freezes live-driven map changes while historical data is displayed, while providers continue receiving and archiving packets in the background. Returning to live immediately shows the newest accumulated live state.
- Adds a dedicated blue **HISTORY** top-bar state and flags live EEW/tsunami activity without allowing it to replace the historical map; tapping the live-warning status returns directly to live mode.
- Changes the map action to a one-press **Re-focus event** whenever the user pans/zooms away or a newly selected historical report changes the ideal event footprint; report changes never hijack the camera automatically.

## v0.9.42 landscape sidebar tap toggle

- Changes landscape sidebar hiding from an over-drag action to an explicit short tap on the vertical divider.
- Keeps normal horizontal drag resizing clamped to the readable 45–66% map-width range; dragging past the minimum sidebar width no longer hides it.
- Leaves a slim restore handle at the right edge while hidden and restores the sidebar to its previous width instead of resetting it.
- Uses the same short-tap versus hold/drag distinction and haptic arming behaviour as the portrait divider.
- Leaves portrait panel behaviour unchanged.

## v0.9.41 incident reports and local archive

- Keeps `ScalePrompt`, `Destination`, `ScaleAndDestination`, and `DetailScale` as complementary reports belonging to one earthquake incident instead of replacing one another.
- Displays the initial regional intensity immediately, then adds the hypocentre, magnitude, depth, combined observations, details, and corrections as those reports arrive; a report that omits a field no longer erases previously known data.
- Preserves explicit Initial intensity, Hypocentre, Hypocentre & intensity, Detailed intensity, Distant-earthquake, and Corrected report states.
- Adds an opt-in SQLite archive that stores complete event-related P2PQuake JSON payloads without collapsing them into summaries and deduplicates them by upstream ID or content fingerprint.
- Shows the archived report count, incident count, and actual local database size in Settings, with a confirmed Delete archive action.
- Adds Download past reports, which paginates through the complete available `/history` retention window rather than saving only the newest 100 entries or the finalized `/jma/quake` summaries.
- Adds Automatic historical download after startup/reconnection with overlap-safe deduplication and rate-limit-aware paging.
- Stores and backfills earthquake, tsunami, EEW-detection, and EEW report packets while excluding unrelated high-volume peer/userquake traffic.
- Rebuilds incidents from archived reports in original chronological order, providing the raw sequence needed for a future authentic replay implementation.

## v0.9.40 compact drawer controls

- Makes the Sandbox drawer actions and the generic Reconnect / Settings actions honor their requested compact heights by disabling Material's 48 dp layout reservation only around those button rows.
- Reduces the bottom panel drag strip to roughly 14 / 17 / 23 dp at 80 / 100 / 130% text size while retaining its full-width gesture target.

## v0.9.39 compact drawer actions and centred clock

- Makes status-drawer action buttons genuinely compact at small text sizes by reducing visible height, padding, and pill-like corner radius.
- Applies the same tighter segmented-control padding policy to the Dot / Cross selector in Settings.
- Anchors the JST clock to the exact physical centre of the complete top bar instead of centring it between unequal left and right groups.
- Keeps LIVE at its normal size while SANDBOX and CONNECTING labels automatically use a smaller portrait treatment when enlarged text would otherwise collide with the centred clock.
- Continues hiding the version first when portrait space becomes constrained; the full version remains available inside the drawer.

## v0.9.38 responsive compact controls

- Makes the portrait bottom-panel drag strip follow the effective text scale instead of reserving a fixed 24 dp height at every setting.
- Makes the Dot / Cross marker-style controls reduce and increase their vertical padding with the live Settings text preview.
- Makes the status drawer action buttons, including Return to live data, Sandbox settings, Reconnect, and Settings, follow the effective text scale instead of retaining the same large Material button height at 80%.
- Centralizes these dimensions in one shared responsive-control sizing policy so later compact controls can use the same behavior.

## v0.9.37 distant reports and report stages

- Treats valid JMA/P2PQuake distant-earthquake bulletins as off-map reports rather than clamping their global coordinates to the nearest edge of the Japan-only map.
- Keeps Fit Japan for distant reports without Japanese observations; if a later bulletin includes Japanese observation areas, focuses the Japanese footprint while still ignoring the off-map epicentre.
- Hides the epicentre marker when the source coordinate lies outside the bundled Japan geometry and disables the Focus action with an explicit Outside Japan map label.
- Displays unavailable P2PQuake depth and magnitude sentinel values as an em dash instead of values such as -1 km.
- Preserves the report progression for one earthquake and labels cards/history as Preliminary report, Final report, Distant-earthquake report, or Corrected report from the P2PQuake issue metadata.
- Adds localized English, Czech, and Japanese wording for the new states, a distant-earthquake fallback for unknown foreign names, and an explicit JMA translation for ローヤリティー諸島南東方 (Southeast of the Loyalty Islands).

## v0.9.36 status-bar interaction and sizing

- Adds a visible animated chevron to the collapsed status bar.
- Tapping the chevron opens the drawer; tapping it again closes the drawer, while drag and tap-outside controls remain available.
- Removes the old decorative pull-handle line now that the drawer has an explicit control.
- Makes the status-bar height respond to the effective app/system text scale: slimmer at 80%, modest at 100%, and only as tall as needed at larger sizes.
- Lets status-bar typography use the actual selected text scale instead of silently capping it at 118%.
- Keeps the version as the first collapsible item when enlarged text makes portrait space tight; the full version remains in the drawer.

## v0.9.35d gesture-layer clipping hotfix

- Clips the entire map viewport at its parent boundary, including the retained/off-screen Japan layer used during live pan and pinch gestures.
- Prevents both Japan and regional/world geometry from temporarily drawing over the top status bar while the map is moving.
- Keeps the previous native regional-context clip as a second, renderer-local safeguard.
- Does not change map coordinates, camera constraints, gesture behaviour, or status-bar layout.

## v0.9.35c regional-map clipping hotfix

- Clips the native regional/world context layer to the actual map viewport before applying pan and zoom transforms.
- Prevents China, Korea, Russia, Taiwan, and other context geometry from painting over the new top status bar.
- Leaves Japan geometry, camera calculations, gestures, status chrome, and provider behavior unchanged.

## v0.9.35b Gradle daemon JVM cleanup

- Replaces the incomplete `#GRADLE_LOCAL_JAVA_HOME` project setting with checked-in Gradle Daemon JVM criteria.
- Requests JetBrains Runtime / JDK 25 for the Gradle daemon, matching current Android Studio Panda-era embedded JBR installations and keeping IDE and command-line daemon selection aligned.
- Avoids shipping a machine-specific absolute `java.home` path in `.gradle/config.properties`.
- Keeps the automatic `:app:assembleDebug` startup task unchanged.

## v0.9.35a compile and startup-JDK hotfix

- Removes three accidental imports of Compose's internal `RowColumnParentData.weight` property; `Modifier.weight(...)` now resolves through the public Row/Column scope API.
- Adds project-local Gradle JVM configuration using Android Studio's Gradle Local Java Home resolver, so the project-open `assembleDebug` task does not inherit an undefined Project JDK.

## v0.9.35 expandable status chrome

- Replaces the separate map branding, clock, source chip and settings cog overlays with one slim full-width status bar that owns its layout strip above the map and report panel.
- Adds a two-anchor pull-down status drawer with current and requested provider details, last-update age, reconnect, source selection and a full Settings action; expansion overlays the content without resizing the map.
- Keeps active EEW and tsunami warning badges on the map instead of absorbing them into connection chrome.
- Turns the entire QuakeDeck status bar orange whenever Sandbox is armed or active, with an immediate Return to live data action in the drawer.
- Introduces one compile-time `SandboxFeature.ENABLED` boundary for all Sandbox entry points, colours, drawer content, settings pages and replay callbacks.
- Moves Sandbox-owned settings and drawer UI into dedicated packages and adds a shared display-clock controller whose JST timeline follows existing Sandbox packets and built-in scenarios while live mode follows wall time.
- Reads the visible version from `BuildConfig.VERSION_NAME` instead of duplicating a hard-coded UI version string.
- Includes a project-local Android Studio startup task that runs `:app:assembleDebug` whenever that standalone version folder is opened.

## v0.9.34 system-theme stability

- Handles Android `uiMode` changes in place instead of recreating `MainActivity`.
- System Light/Dark switching now lets Compose update the active palette without tearing down the live provider, map state, or an open Settings screen.
- Manual in-app System / Light / Dark selection remains unchanged.

## v0.9.33 appearance settings

- Adds persistent System default, Light, and Dark appearance choices with immediate switching from Settings.
- Gives the map, controls, report panel, settings cards, dialogs, labels, and system-bar icons coordinated light and dark palettes instead of repainting only the outer shell.
- Keeps official Shindo, tsunami, EEW wavefront, and connection-state colours semantically consistent in both appearances while adapting surrounding contrast.
- Rebuilds the cached map-resize raster after an appearance change so it cannot retain the previous palette.
- Prevents the fixed 100% text-size marker from clipping during large live text previews.

## v0.9.32 settings ergonomics

- Moved Testing & Sandbox to the top of Settings for immediate access.
- Kept the text-size slider on a stable interaction density so it can be dragged normally while the page text previews the new scale live.
- Centered the 100% default pointer exactly over its slider position.
- Reduced the epicenter marker preview height.


## v0.9.31 settings corrections

- Marks the fixed 100% text-size position directly beneath the slider instead of explaining it in prose.
- Previews text scaling live inside the existing Settings window and commits the app-wide scale only when Settings closes, preserving page and scroll state while dragging.
- Uses one shared epicenter-marker renderer for both the map and Settings preview, so geometry, line widths, focus outline, and selected size are identical.
- Removes the unnecessary non-null assertion reported by the Kotlin compiler in `P2pQuakeProvider.kt`.

## v0.9.30 settings housekeeping

- Replaces the long settings alert with a responsive full-screen settings surface.
- Groups normal preferences into General, Map display, and Data & connection cards.
- Moves all historical and simulated controls onto a dedicated warning-coloured Testing & Sandbox page.
- Adds a live epicenter-marker preview and a clear connection-status row.
- Returns to the map automatically when a built-in replay is started, while preserving the existing five-second arm delay.
- Extracts persistent preferences into `AppSettings.kt` and settings UI out of `MainActivity.kt` without changing preference keys or defaults.

## v0.9.29 official JMA regional geometry

- Bundles preprocessed official JMA geometry for 194 detailed earthquake/EEW forecast regions, 56 public EEW warning areas, and 70 tsunami forecast coastlines.
- Colors the finest matching reported region instead of automatically filling an entire prefecture. Station observations use the catalogue's official detailed-area code/name first, then coordinates when needed.
- Uses the same regional geometry for Focus/EEW camera bounds, so mainland Tokyo no longer drags Hachijo or Ogasawara into view unless those areas are actually reported.
- Replaces QuakeDeck's hand-split tsunami coastline approximation with the official JMA forecast-zone lines.
- Keeps N03 prefectures as a safe fallback for unknown or unmatched bulletin names.

## v0.9.28 EEW lifecycle and camera control

- Ends public EEW state from the latest official warning-area arrival plus a short grace period, with bounded fallbacks when timing data is absent.
- Keeps a matching confirmed earthquake report visible without prematurely killing an active EEW wave-passage overlay.
- Auto-frames expanding EEW rings, while manual pan/zoom has absolute control for ten seconds after the last gesture.
- Refits focused events when later report revisions add observed intensity areas.
- Preserves the two-row report-card geometry for locations without a prefecture row.
- Enables Gradle configuration cache.

## v0.9.27 camera and responsive-detent fixes

- Preserves the user-adjusted focused-map zoom and geographic centre while the portrait report panel is drag-resized, instead of recalculating the automatic Focus fit on every viewport-height change.
- Reapplies the measured minimised detent after text-size changes, including when reducing the text size makes the report summary shorter.

## v0.9.26e wider portrait panel and separated controls

- Reduces the event-panel side inset from 16 dp to 8 dp so reports and history use more of the available screen width.
- Adds a small scaled gap between the three report controls.
- Adds button-only horizontal text insets so wrapped translations do not touch rounded button edges.

## v0.9.26d portrait detent and button-row correction

- Lets the portrait panel minimise to the actual measured report/list divider instead of stopping at the former 18% minimum-panel cap.
- Increases the dedicated report-button row to 2.5× its font/line height.
- Allows button labels to wrap onto two lines before adaptive shrinking or ellipsis.

## v0.9.26c fixed-grid correction pass

- Locks the report grid at 55/45 and removes the temporary ratio slider.
- Makes the Shindo badge span the computed region + prefecture rows, with right-aligned labels and a fixed gap.
- Removes coordinates, adds a compact spacer row, and gives controls a dedicated 25/50/25 row.
- Adds localized `Depth` text and small dividers between tsunami forecast areas.
- Restores the portrait collapsed detent to follow the measured event-summary boundary more closely.

- Makes every report row content-sized: no fixed row heights, no vertical centring, no cell padding and no wrapper-generated dead space.
- Keeps **Max intensity** alone on the first row; the second row puts optional **Predicted** immediately to the left of a stable right-aligned Shindo badge.
- Gives inactive-but-enabled report actions a subtle shaded background so they read as buttons before activation.
- Removes Compose font padding from adaptive report text and tsunami typography, replaces the tall tsunami AssistChip, and eliminates the remaining artificial vertical gaps.

## v0.9.26a UI correction pass

- Rebuilds the maximum-intensity area as two fixed rows: localized **Max intensity** above, then optional **Predicted** plus the right-aligned Shindo badge below.
- Removes all visible report-grid borders and all per-cell content padding, then tightens the fixed row heights.
- Compacts tsunami warning typography by using font-sized line heights and removing nearly all inter-line gaps.
- Cleans up the always-true return-scroll condition reported by the Kotlin compiler.

## v0.9.26 report card, manual localisation and camera polish

- Replaces the free-flowing event summary with a fixed cell-based report card: region/prefecture, maximum intensity, timestamp, magnitude/depth, coordinates and all actions now retain deterministic positions.
- Splits supported English JMA hypocentre labels into an independently wrapping region and shortened prefecture line, while unknown formats remain intact.
- Gives the maximum-intensity panel two fixed rows and a Shindo-coloured value badge; v0.9.26a keeps the label on row one and the optional prediction marker plus badge on row two.
- Scales report-card rows, buttons, corners and badges together with effective UI font scaling instead of shrinking text inside fixed empty boxes.
- Adds a persistent 50/50–70/30 report-card column-ratio test slider, defaulting to 60/40.
- Removes runtime machine translation. QuakeDeck-owned strings now use Android resources with manually curated English, Czech and Japanese text; unsupported languages fall back to English, while official JMA place names remain Japanese only in Japanese and English otherwise.
- Removes the decorative JAPAN LIVE label, moves the version to the top-left, centres the JST clock and nudges the source/status controls toward the safe right edge.
- Preserves free-map zoom and pan while the portrait report divider is resized, and keeps Focus active when another event is selected.
- Shortens resize-bar arming to 200 ms with a centre-out fill, immediate drag on touch-slop movement and haptic feedback when drag mode engages.
- Cleans the four unnecessary-safe-call Kotlin warnings reported by Android Studio.

## v0.9.25 compact historical rows

- Compresses Previous/Recent event entries by putting time, magnitude and depth on one metadata line.
- Reduces row padding, title line height, intensity-badge padding and the gap before the badge.
- Replaces the low-opacity inset separator with a full-width, opaque 1 dp divider that remains visible on the dark panel.

## v0.9.24 denser history and smoother panel resizing

- Tightens spacing between rows in the Previous/Recent events list.
- Adds a subtle divider between historical earthquake rows.
- Uses a compact cached map raster only while the event panel divider is actively dragged, avoiding repeated N03 vector tessellation on every intermediate viewport size.
- Restores the full vector map immediately after resizing ends; tsunami coastlines, EEW overlays, map controls, labels and hit-testing remain live.
- Temporarily suppresses the dense deep-zoom station-dot layer during the drag to avoid unnecessary per-frame work.

## v0.9.23 responsive report actions

- Historical-report actions now use both columns instead of reserving a separate full-width row.
- The event summary measures both columns and places **Close report** under whichever one is shorter.
- This naturally follows the left metadata upward at compact text sizes and uses the free space below the right-hand controls when large text wraps the place name.
- The vertical gap between **Focus event** and **Observed intensities** is reduced.

## v0.9.22 responsive map controls and timed live focus

- The JST clock shifts right for either a solo Shindo legend or the wider tsunami legend.
- The complete Shindo ladder scales with the map viewport; at the tightest detents its numeric labels disappear while all colour bands remain visible.
- Zoom buttons scale uniformly and switch to a horizontal `−  +  Zoom` row when the map becomes short instead of being clipped away.
- Automatically focused live earthquake/EEW reports remain mapped for 15 seconds after the newest update, then return to a clean Fit Japan view; another update refreshes the timer.

## v0.9.21 compact live-alert UI

- The connection chip and settings cog remain fixed together at the top-right; replay, tsunami and EEW labels stack below without moving the cog.
- JAPAN LIVE and the version are rendered as a map branding layer beneath the foreground Shindo/tsunami legends.
- The JST clock shifts right while both tall legends are present so it remains readable.
- Active EEW report number and issue timestamp are folded into the red EEW banner as one compact line.
- Combined EEW + tsunami summaries can collapse to a smaller scrollable portrait panel instead of forcing the map down to roughly 30% of the screen.

## v0.9.20 sea-connected coastline filtering

The tsunami coastline extractor now distinguishes the open ocean from enclosed
water in the rasterized Japan land mask. Only a segment with land on one side
and **outside-connected ocean** on the other is eligible to flash. Lake,
reservoir, and other inland-hole boundaries are therefore excluded instead of
being mistaken for coastlines merely because their opposite side is not land.
The side test must also agree at multiple sampling distances, filtering out
single-pixel cracks between nearly coincident prefecture polygons.

## v0.9.19 coastline extraction and live camera fixes

- Slows the tsunami coastline bright/dim phase to a one-second interval.
- Replaces TopoJSON reference-count-only coastline detection with a rasterized union-of-land test. Every candidate segment is sampled on both sides: sea/land edges are retained, while land/land prefecture borders are rejected even when the source topology stores them as independent near-coincident arcs.
- Keeps lake and other inner-ring boundaries excluded by considering only polygon outer rings before the land-mask test.
- Prevents an old tsunami **Fit Japan** request from replaying after `JapanMap` is recreated for a newly arrived live earthquake. The newest camera command now wins, so a fresh EEW or confirmed report focuses its epicentre and footprint normally.
- Clears stale camera requests when switching between live and testing mode.

## v0.9.18 flashing coastline warnings + combined replay

- Replaces whole-prefecture tsunami shading with thick coloured strokes on the sea-facing coastline, leaving observed/predicted Shindo fills readable underneath.
- Coastal strokes pulse between bright and dim phases every 500 ms while a warning or advisory remains active, with a dark backing line preserving visibility against every map colour.
- Splits the bundled prefecture coastline into all known JMA tsunami forecast-area labels. Forecast areas that share a prefecture are separated by conservative geographic partitions; the textual bulletin remains authoritative for exact region membership and timing.
- Uses TopoJSON topology to identify coastline arcs during the existing off-main-thread map load: shared prefecture-border arcs are excluded, while islands and exterior coastal arcs are retained.
- Adds **Replay combined 2024 Noto EEW + tsunami** in Settings. After the usual five-second arm delay it replays successive EEW revisions, introduces tsunami warnings while the EEW and P/S fronts are still active, confirms the earthquake, then downgrades and cancels the tsunami warning.
- The combined fixture uses the same `551`, `552`, `554`, and `556` parser/state paths as live and sandbox traffic, allowing simultaneous header alerts, cards, countdowns, wavefronts, and coastline flashing to be tested deterministically.

## v0.9.17 tsunami warnings + deterministic replay

- Adds production handling for P2PQuake code `552` tsunami bulletins, including major tsunami warnings, tsunami warnings, advisories, forecast-only information, updates, expiration, and cancellation.
- Restores the latest still-relevant tsunami bulletin after a production WebSocket reconnect through a bounded `/history?codes=552` lookup.
- Adds a prominent tsunami card with affected forecast areas, expected maximum height, first-arrival timing/status, and official evacuation guidance while warnings are active.
- Highlights affected coastal prefectures on the map using the highest warning grade mapped to each prefecture. The detailed list remains authoritative because the map shading is an intentional prefecture-level approximation of JMA coastal forecast areas.
- Adds a compact tsunami-grade legend and a header warning indicator.
- Adds **Replay 1 January 2024 Noto tsunami** in Settings. It waits five seconds, then feeds a reconstructed `551` earthquake plus successive `552` major-warning, arrival, downgrade, and cancellation packets through the same parser/state/UI path used by live traffic.
- Historical replay timestamps are rebased to the current clock, pending packets are generation-scoped and cancellable, and the cancellation remains visible before QuakeDeck reconnects to the official rotating sandbox.

The replay is based on JMA's published 1 January 2024 Noto Peninsula M7.6 event. Its P2PQuake JSON envelopes and shortened timing progression are reconstructed solely for deterministic integration testing; they are not an archived verbatim P2PQuake capture.

## v0.9.16 epicenter-centred event focus

- Keeps the observed/predicted event footprint as the input for automatic focus zoom, so broad EEW forecasts still open at a useful regional scale.
- Changes the focus pan target from the footprint bounding-box centre to the actual hypocentre. **Focus event** and live auto-focus now place the epicentre at the centre of the map viewport whenever the Japan camera bounds permit it.
- Fixes the deterministic Noto replay appearing near the top of the map because its reconstructed forecast footprint extends southeast toward Tokyo.

## v0.9.15 deterministic built-in EEW replay

- Adds **Replay 5 May 2023 Noto EEW** beneath the official sandbox switch in Settings.
- Pressing it automatically enables test mode, disconnects the network sandbox for the duration of the fixture, and arms a five-second delay so Settings can be closed before the first packet appears.
- Replays an offline detection plus several meaningful JMA EEW revisions (reports 7, 10, 11, 16, 20 and 26) through the exact same `processLiveMessage` / EEW parser and UI state path used by live P2PQuake traffic.
- The fixture is based on the published 5 May 2023 Noto Peninsula M6.5 event and includes a deterministic Tokyo forecast area so the active banner, report updates, P/S fronts, predicted intensity and full Tokyo countdown can be tested on demand.
- Keeps the EEW active long enough for the Tokyo S-wave countdown to finish, then sends a confirmed `551` earthquake report and reconnects the rotating official sandbox.
- Cancels every pending replay packet when test mode changes, a new replay is started, or the provider stops, preventing delayed test packets from leaking into a later live session.

The fixture's hypocentre/report progression follows JMA's published revisions. Its P2PQuake JSON envelope and forecast-area list are reconstructed for deterministic integration testing because the official rotating sandbox exposes neither event selection nor downloadable archived raw frames.

## v0.9.14 EEW replay, wavefronts and Tokyo countdown

- Fixes P2PQuake sandbox EEWs being rejected by production anti-rollback chronology checks when the replay moves between historical samples.
- Gives each sandbox EEW a stable live-time offset for its replay session: the original 2023 source timestamps remain visible, while animation and countdown calculations behave as if the replay report were issued now.
- Resets sandbox-only message deduplication and replay timing after each forced reconnect, allowing the service to reuse its historical packet IDs without leaving QuakeDeck connected but silent.
- Adds depth-aware P- and S-wave map fronts using effective 7.0 km/s and 4.0 km/s display velocities. These are modelled visual estimates, not official JMA travel-time calculations.
- Adds a prominent active-EEW banner and a Tokyo destination card. The S-wave countdown uses the official P2PQuake Tokyo forecast-area arrival when supplied, otherwise it falls back to the same travel-time model; predicted Tokyo intensity is shown when the area is present.

## v0.9.13 sandbox testing + portrait panel shortcuts

- Adds a persistent **P2PQuake testing mode** switch in Settings. It replaces the production WebSocket with the official replay sandbox, visibly marks the app as **TEST / SANDBOX REPLAY**, clears production reports, and keeps sandbox history isolated from production REST recovery. Switching it off clears the replay state and reconnects to the live service.
- The sandbox socket uses `wss://api-realtime-sandbox.p2pquake.net/v2/ws`; the official service replays historical EEW, earthquake and tsunami samples roughly every 30 seconds and may force-disconnect WebSocket clients after 10 minutes, which QuakeDeck handles through the normal reconnect path.
- A short tap on the portrait resize handle toggles between the exact current panel size and the measured minimum detent. A long press followed by a drag retains normal manual resizing.
- Opening **Observed intensities** remembers the current portrait split and expands the panel only as far as the configured maximum. Hiding the observations restores the previous split, including returning to the minimized state when that is where the list started.

## v0.9.12 report navigation + live-feed recovery

- Fixes the compact Shindo legend so every label has a taller line box and is vertically centred instead of being clipped.
- Opening any historical report records the current history-list anchor and scrolls the selected report summary and controls completely to the top.
- **Close report** restores the prior list position after returning to the latest report; key-based restoration survives the list-content change, with the opened report row as a fallback anchor.
- Adds a compact floating **Top** button while the report/history panel is scrolled, returning directly to the selected/latest report summary.
- Opens the P2PQuake WebSocket immediately at app start. Station-catalogue loading and the slower `/jma/quake` history request now run independently and can no longer delay or strand the live connection.
- Uses separate OkHttp clients: the WebSocket keeps its unlimited stream timeout, while catalogue/history calls have finite connect/read/call deadlines.
- After every successful WebSocket connection or reconnection, queries P2PQuake's near-real-time `/history` feed for recent `551`/`556` traffic. This recovers an EEW or preliminary/final report missed while disconnected instead of waiting for the delayed JMA history endpoint.
- Recovered packets are deduplicated, applied oldest-to-newest, constrained to a short live-event window, and guarded against older EEW serials or preliminary reports rolling the UI backwards over newer/final data.
- Adds jitter to exponential reconnect delays and shortens the WebSocket ping interval so a dead connection is noticed sooner without causing synchronized retry storms.

## v0.9.11 P2PQuake live progression + clean map mode

- Implements progressive P2PQuake WebSocket handling for EEW detection (`554`), successive EEW warning reports (`556`), cancellation, and live confirmed JMA earthquake reports (`551`). Exact duplicate WebSocket message IDs are ignored as recommended by the P2PQuake v2 specification.
- Active EEW reports update in place by event ID/report serial, show predicted intensity ranges and available S-wave arrival estimates, and color/focus the affected N03 prefecture footprint where the forecast-area name can be resolved.
- A newly arriving live report automatically opens on the map once. Later serials update the existing view without repeatedly stealing the camera; closing a live report keeps later serials of that same event closed.
- QuakeDeck now launches on a clean, centered Japan map. The latest report remains available in the information panel but does not paint prefectures or show an epicenter until **Focus event** is pressed.
- **Focus event** is now a toggle. Its selected state is visibly filled; pressing it again clears the report from the map and restores the whole-Japan camera.
- Historical **Return to latest event** is replaced by **Close report**, which clears the selected report from the map, returns the panel to the latest report, and centers Japan.
- The regional Natural Earth resource is preprocessed without Japan, preventing its coarse Japanese coastline from sitting underneath the detailed N03 geometry without adding an expensive per-frame clip operation.
- Zoom controls move to the lower left directly above the zoom readout. A compact Shindo color scale appears above them whenever a report is painted on the map. Gestures beginning on the zoom buttons remain isolated from map pan/pinch handling.
- Adds the QuakeDeck website icon as the Android launcher/adaptive icon.

## v0.9.10 clock + discrete zoom controls

- Adds a live 24-hour **JST** clock (`Asia/Tokyo`) to the header, updated on wall-clock second boundaries.
- Adds vertically stacked **+ / −** zoom buttons on the left side of the map.
- Button zoom lands on fixed 0.5x levels rather than adding 0.5 to an arbitrary pinch-zoom value: for example, from 3.56x, **+** goes to 4.0x and **−** goes to 3.5x. Pinch zoom itself remains continuous.
- Zoom-button camera changes preserve the geographic point under the viewport centre and still obey the existing Japan-based pan clamp.
- Gestures that begin on either zoom button are explicitly excluded from the map pan/pinch recognizer, matching the Fit Japan control behavior.

## v0.9.9 regional-context + portrait-detent correction

- Replaces the too-small regional clip with a much wider low-detail Natural Earth context envelope (30–245°E, 70°S–85°N), including a dateline-shifted copy of relevant polygons. This is intentionally larger than the Japan-constrained camera window, so every area the existing pan clamp can expose on phone portrait/landscape views has geographic context instead of empty ocean/background. Camera limits themselves are unchanged.
- The low-detail context remains a separate live-rendered native Path layer; the expensive N03 Japan geometry keeps its retained/off-screen gesture cache.
- Fixes the portrait minimum-detent update race when changing between Latest and historical events. The previous code reset the measured event-block height after the newly selected block could already have reported its size, leaving the old/fallback detent until another layout change (such as text-size adjustment).
- Event-block size changes now clamp the portrait split immediately, so the hard minimum follows the actual divider as soon as selection/layout changes.

## v0.9.8 portrait detent + regional-context fix

- Portrait minimum panel height is now measured to the actual horizontal divider between the current/selected event block and Recent earthquakes. The detent therefore includes Focus event, Observed intensities, Return to latest when present, wrapped labels, and text scaling without a guessed fixed height.
- The event block and divider are a single LazyColumn item, preserving full-panel/landscape scrolling while giving portrait an exact hard stop.
- Regional context geometry now covers a wider Japan-adjacent navigation envelope (116–154°E, 18–52°N) while camera/pan bounds remain Japan-based. Taiwan is included, along with nearby China/Korea/Russian Far East/Philippines context where visible.
- The tiny regional context paths are rendered as a separate live layer during gestures. They can reveal newly exposed geography immediately without waking the expensive retained N03 Japan layer.

## v0.9.7 layout + regional context

- Observed-intensity toggle moved under Focus event in the event-summary action column.
- Portrait divider now has a non-collapsing minimum detent sized to keep the summary and Return to latest visible.
- Return to latest is tucked closer to the event summary.
- Added an ultra-light regional land/border context layer around Japan, clipped to the existing Japan viewport extent so it cannot become a general world map.
- Regional context is drawn inside the same retained off-screen map layer, preserving the v0.9.6 performance model.

## v0.9.6 performance pass

- Restores the retained/off-screen land layer from v0.9.5.
- Removes the unused AppSnapshot parameter from JapanMap so connection/history UI updates cannot invalidate the map unnecessarily.
- Marks immutable snapshot/event models for Compose skipping.
- EventPanel uses LazyColumn so only visible history rows are composed and laid out while scrolling.
- Map label Paint objects are retained instead of allocated per label/per frame.
- Official English observation/station-name translation results are cached after the first lookup.
- City label priority is pre-sorted once instead of every overlay draw.

## v0.9.5 interaction/performance polish

- Restores the off-screen cached map layer during active pan/pinch gestures. The committed N03 map is GPU-transformed while fingers are down and is crisply redrawn when the gesture ends, returning the buttery behavior from v0.9.2.
- Retains the v0.9.3 pan boundaries and Fit Japan gesture isolation.
- Moves **Focus event** into the right-hand event-statistics column beneath max intensity / magnitude / depth, freeing vertical space in the event panel.

## v0.9.4 event camera control

- Adds a **Focus event** control to the currently displayed earthquake card.
- The button uses the same affected-area framing as automatic historical-event selection.
- It works for both the latest event and a selected historical event, and can be pressed repeatedly after panning or using Fit Japan.
- Camera focus is kept separate from selection: **Fit Japan** remains the country-view control, while **Focus event** only changes the camera.
- A focus request is tied to the earthquake ID so a later incoming event does not inherit an old manual focus request.

## v0.9.3 map interaction polish

- Oki-no-Tori Shima and Minami-Torishima are removed from both packaged map LODs so whole-Japan framing is spent on the useful archipelago.
- The retained land layer no longer forces viewport-sized offscreen compositing; this lets the GPU-transformed vector display list reveal geometry that began outside the screen while panning.
- Gestures beginning on the Fit Japan control are excluded from map pan/zoom handling.
- Camera pan is bounded so Japan cannot be dragged completely off-screen.

## v0.9.2 map readability tuning

- map zoom range extended to 64x
- city labels are priority/zoom tiered with Tokyo guaranteed first priority at country view
- prefectural capitals still phase in as the map is enlarged
- real observed station dots now appear only from 12x
- neutral station-network dots appear from 18x
- station names are optional (off by default) and only appear at very deep zoom (36x observed / 48x base network)
- label placement now uses text bounding-box collision suppression instead of point spacing
- added one-tap fit-Japan camera button and retained the debug zoom readout

## v0.9 map work

- Deep map zoom increased to 32x.
- Two map LODs: fast 0.008 geometry normally, automatic 0.003 high-detail geometry after a deep-zoom gesture finishes.
- Temporary on-map zoom-level readout for LOD tuning.
- Major-city labels at country scale; all prefectural capitals at regional scale.
- Deep-zoom seismic-station layer and station labels.
- Detailed P2PQuake/JMA station observations are geolocated and colored by observed Shindo where a station-coordinate match exists.
- Prefectures are filled with the maximum observed Shindo for the current or selected historical event.
- Selecting history now fits the observed footprint + epicenter to roughly 78% of the map viewport instead of using a fixed 2x camera.
- JMA-style Shindo colors are used consistently for station dots, badges and prefecture fills.
- Active EEWs draw depth-aware modelled P/S wavefronts; replay timestamps are rebased so sandbox events animate on a live clock.

> The v0.3.1–v0.8 entries below were reconstructed from the original source-ZIP publication messages; they were not maintained as a formal changelog at the time.

## v0.8 localization and report identity polish

- Localizes the interface and enforces the place-name rule used by QuakeDeck today: Japanese JMA names appear only in Japanese mode, while every other language uses official JMA English names rather than machine translation.
- Removes untranslated Japanese suffixes leaking into non-Japanese place labels.
- Normalizes intensity labels to `5-`, `5+`, `6-` and `6+`, with Japanese rendering retained where appropriate, and localizes **Max intensity**.
- Deduplicates earthquake reports by occurrence/origin time.
- Changes overlapping epicentre marker rings to touch cleanly, using white for the latest event and light blue for historical events.
- Removes the nullable OkHttp safe-call warning in the provider path.

## v0.7 provider lifecycle and source controls

- Reduces epicentre-marker shrinkage so it retains roughly 80% of its screen size at 6× zoom instead of becoming a tiny dot.
- Replaces the large P2PQuake status banner with a compact coloured `FREE` / `DM-D.S.S` source chip and a Data source dialog.
- Establishes extensible source selection and clearer DM-D.S.S fallback reporting.
- Cancels a pending reconnect delay when the app returns to the foreground, resets backoff and reconnects immediately using connection-generation tracking; background retries remain best-effort at 30 seconds.
- Moves provider ownership out of Compose and into `MainActivity` in preparation for future monitoring. This version still does not provide continuous screen-off monitoring.

## v0.6.1 gesture caching and panel polish

- Uses a cached GPU image while a map gesture is active, followed by one crisp vector redraw after release.
- Makes the epicentre marker shrink strongly with zoom in this interim build; v0.7 subsequently softened that behaviour.
- Expands the landscape information panel to a more readable 34% and makes it collapsible.
- Omits the current latest event from **Recent earthquakes** while the Latest view is already displaying it.

## v0.6 retained map rendering and landscape layout

- Packages the official N03 map at `TOLERANCE=0.008` and keeps it in a retained GPU layer for faster panning and zooming without losing the accurate projection.
- Preserves map/UI state through configuration changes such as device rotation.
- Introduces the landscape map-left / information-right layout with an initial 68% / 32% split and a draggable divider.
- Retains safe-area handling around system bars and cut-outs.

## v0.5 official N03 map and selectable history

- Replaces the interim map with official N03 TopoJSON and uses the same Web Mercator projection for geometry and earthquake positions.
- Uses shared TopoJSON arcs to remove gaps between neighbouring prefectures.
- Adds a 30-event history showing JST date/time, magnitude, depth and maximum intensity.
- Makes historical events selectable and focuses the map at approximately 2× zoom.
- Moves station observations behind a compact **Observed intensities (n)** control.

## v0.4 historical-event browsing and map correction

- Corrects the early prefecture rendering seams and large geometry gaps.
- Adds a scrollable 30-event history with report timestamps.
- Makes past events selectable, placing their epicentre on the map and centring the corresponding event view.
- Adds coordinate/debug readouts used to validate the early map projection and event placement.

## v0.3.1 early settings, place names and socket/map fixes

- Adds the first Settings controls and translated display-place handling used by the early prototype.
- Fixes the P2PQuake live-socket path.
- Replaces the rough initial Japan drawing with more accurate locally packaged map geometry.

## v0.2.1 provider and build hotfix

- Fixes the P2PQuake parser empty-result/null return path that failed Kotlin compilation inside `runCatching`.
- Updates the Android project baseline to AGP 9.3.1 with Gradle 9.5 compatibility.

## v0.2 live P2PQuake provider

- Adds the live P2PQuake/JMA REST bootstrap and WebSocket data path.
- Handles public `556` EEW warning messages and displays real epicentres and reported intensity areas.
- Adds connection-state reporting and the normalized FREE / DM-D.S.S source model, with FREE remaining the usable fallback.
- Moves the networking baseline to OkHttp 5.4.0 and Gradle 9.5.

## v0.1.1 Kotlin build compatibility hotfix

- Replaces the deprecated Kotlin `kotlinOptions` configuration with the modern `compilerOptions` DSL.
- Aligns the initial project with the required JDK/Gradle toolchain.

## v0.1 initial Android prototype

- Establishes the free-mode QuakeDeck baseline with normalized FREE / DM-D.S.S provider interfaces and no embedded API key.
- Adds the portrait Japan map UI, collapsible information panel, pan/zoom controls, epicentre and wave-ring drawing, intensity markers, and report history.
