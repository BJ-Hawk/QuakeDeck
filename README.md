# QuakeDeck

QuakeDeck is an Android earthquake-monitoring prototype focused on Japan. It uses Kotlin and Jetpack Compose to combine live P2PQuake/JMA reports, EEW visualization, observed intensity mapping, tsunami information, historical report browsing, and deterministic Sandbox testing in one map-first interface.

Current development version: **0.9.84v (in progress)**. QuakeDeck is still pre-1.0 and should not be treated as an official emergency-warning application.

## Current capabilities

- Immediate remembered-report startup view followed by concurrent P2PQuake REST bootstrap and WebSocket updates
- JMA earthquake reports, public warning-level EEW, and tsunami bulletins
- Normalized 1×–128× map scale, where 1× uses the former 1.5× framing
- Zoom-exclusive Japan vectors: N03 prefectures below 6.5×, 194 detailed JMA earthquake-reporting areas from 6.5× to below 21×, and municipalities/wards from 21× upward
- Highest-reported-Shindo coloring for the active vector layer, backed by a bundled station-to-area catalogue so detailed fills remain available offline
- Official JMA regional, deep-zoom municipality/ward, and tsunami forecast-zone geometry with precomputed prefecture coastlines for faster cold starts
- Persistent JMA, NIED, and local-government station filters for the idle map, with report views restricted to their own observed stations
- P/S wavefront visualization, event focus, observed intensity lists, report history, and persistent main-map camera/panel layout
- Persistent local raw-report archive and historical report browser
- English, Czech, and Japanese UI/place-name handling
- Light, dark, and system appearance modes
- Custom Android notification cards with Shindo or tsunami-grade graphics, alert-level borders, multiline event details, location-aware coverage, audible/silent thresholds, scheduled quiet-hour delivery policies, manual city/postcode relevance filtering, and a shared EEW destination
- Opt-in foreground live monitoring with a permanent silent connection-status notification; it reuses the single P2PQuake runtime and continues after QuakeDeck is closed
- Opt-in local-EEW attention: off, a brief wake-screen, or a full-screen alert at a selected predicted Shindo threshold; full-screen delivery requires Android's separate permission
- Official rotating P2PQuake Sandbox, deterministic built-in replay scenarios, and one-shot live-pipeline injections for earthquake, EEW, and tsunami testing
- Independently synchronized NICT-based JST display clock

## Data sources

The usable provider is currently the free P2PQuake feed. The DM-D.S.S adapter remains a planned provider and falls back to P2PQuake until account integration is implemented. See `THIRD_PARTY_DATA.md` for bundled-data attribution and licensing notes.

## Opening and building

Open the repository root (the folder containing `settings.gradle.kts`) in Android Studio. The project targets Android API 36 and uses Java 17; Android Studio normally provisions the appropriate JDK and offers any missing SDK components during sync.

To build a debug APK from a terminal, run:

```powershell
.\gradlew.bat :app:assembleDebug
```

The resulting APK is written below `app\build\outputs\apk\debug`. To run the unit tests, use `./gradlew.bat :app:testDebugUnitTest`.

## Documentation

- [`CHANGELOG.md`](CHANGELOG.md) — documented release history, newest first
- [`ROADMAP.md`](ROADMAP.md) — planned work and feature direction
- [`THIRD_PARTY_DATA.md`](THIRD_PARTY_DATA.md) — source and licensing notes for bundled datasets
- [`tools/map-editor/README.md`](tools/map-editor/README.md) — local visual editor for map geometry and boundary classes

## Important limitation

QuakeDeck is an independent hobby project, not an official JMA warning client. Delivery, latency, availability, and interpretation depend on third-party data and Android background-execution behaviour. The opt-in monitoring service materially improves background reliability, but it cannot recover from force-stop, Android's Stop control, a reboot, network/provider outages, or aggressive manufacturer battery controls. Always follow official emergency information and local authorities.
