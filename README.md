# QuakeDeck

QuakeDeck is an Android earthquake-monitoring prototype focused on Japan. It uses Kotlin and Jetpack Compose to combine live P2PQuake/JMA reports, EEW visualization, observed intensity mapping, tsunami information, historical report browsing, and deterministic Sandbox testing in one map-first interface.

Current development version: **0.9.76**. QuakeDeck is still pre-1.0 and should not be treated as an official emergency-warning application.

## Current capabilities

- Live P2PQuake REST bootstrap and WebSocket updates
- JMA earthquake reports, public warning-level EEW, and tsunami bulletins
- Normalized 1×–128× map scale, where 1× uses the former 1.5× framing
- Zoom-exclusive Japan vectors: N03 prefectures below 6.5×, 194 detailed JMA earthquake-reporting areas from 6.5× to below 21×, and municipalities/wards from 21× upward
- Highest-reported-Shindo coloring for the active vector layer, backed by a bundled station-to-area catalogue so detailed fills remain available offline
- Official JMA regional, deep-zoom municipality/ward, and tsunami forecast-zone geometry
- Persistent JMA, NIED, and local-government station filters for the idle map, with report views restricted to their own observed stations
- P/S wavefront visualization, event focus, observed intensity lists, and report history
- Persistent local raw-report archive and historical report browser
- English, Czech, and Japanese UI/place-name handling
- Light, dark, and system appearance modes
- Configurable Android notifications with location-aware coverage, audible/silent thresholds, scheduled quiet-hour delivery policies, manual city/postcode relevance filtering, and a shared EEW destination
- Process-scoped live reception while the app is backgrounded and Android keeps the process executable
- Official rotating P2PQuake Sandbox plus deterministic built-in replay scenarios
- Independently synchronized NICT-based JST display clock

## Data sources

The usable provider is currently the free P2PQuake feed. The DM-D.S.S adapter remains a planned provider and falls back to P2PQuake until account integration is implemented. See `THIRD_PARTY_DATA.md` for bundled-data attribution and licensing notes.

## Opening and building

Open the extracted project folder in Android Studio. The checked-in project startup configuration runs `:app:assembleDebug` when the standalone version folder is opened. Android Studio should initially open `CHANGELOG.md`; after the first session, normal IDE workspace behaviour takes over.

## Documentation

- [`CHANGELOG.md`](CHANGELOG.md) — documented release history, newest first
- [`ROADMAP.md`](ROADMAP.md) — planned work and feature direction
- [`THIRD_PARTY_DATA.md`](THIRD_PARTY_DATA.md) — source and licensing notes for bundled datasets

## Important limitation

QuakeDeck is an independent hobby project, not an official JMA warning client. Delivery, latency, availability, and interpretation depend on third-party data and Android background-execution behaviour. The current background receiver does not survive Android freezing or killing the application process; persistent foreground-service monitoring is still planned. Always follow official emergency information and local authorities.
