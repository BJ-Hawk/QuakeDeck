# QuakeDeck

QuakeDeck is an Android earthquake-monitoring prototype focused on Japan. It uses Kotlin and Jetpack Compose to combine live P2PQuake/JMA reports, EEW visualization, observed intensity mapping, tsunami information, historical report browsing, and deterministic Sandbox testing in one map-first interface.

Current version: **0.10.1-dev.2**. QuakeDeck is still pre-1.0 and should not be treated as an official emergency-warning application.

> **Intentionally omitted EEW forecasting implementation**
>
> QuakeDeck's locally calculated earthquake-motion forecasting implementation is intentionally excluded from Git for legal reasons. The omitted file is `app/src/main/java/cz/misa/quakedeck/data/LocalEewForecastEngine.kt`; it currently contains modelled P-wave and S-wave propagation, arrival-time predictions, related destination countdowns, and forecast-derived warning-passage timing.
>
> A public checkout still builds successfully, but those locally calculated features are unavailable without the omitted implementation. Forecasts and warnings supplied directly by JMA, DM-D.S.S, or P2PQuake remain supported. The omission is deliberate and is not a missing dependency or repository error.

## Current capabilities

- Immediate remembered-report startup view followed by concurrent P2PQuake REST bootstrap and WebSocket updates
- JMA earthquake reports, public warning-level EEW, audible paid DM-D.S.S forecast notifications, and tsunami bulletins
- Normalized 1×–128× map scale, where 1× uses the former 1.5× framing
- Zoom-exclusive Japan vectors: N03 prefectures below 6.5×, 194 detailed JMA earthquake-reporting areas from 6.5× to below 21×, and municipalities/wards from 21× upward
- Highest-reported-Shindo coloring for the active vector layer, backed by a bundled station-to-area catalogue so detailed fills remain available offline
- Official JMA regional, deep-zoom municipality/ward, and tsunami forecast-zone geometry with precomputed prefecture coastlines for faster cold starts
- Persistent JMA, NIED, and local-government station filters for the idle map, with report views restricted to their own observed stations
- P/S wavefront visualization and destination countdowns when the intentionally local forecast engine is present, plus event focus, observed intensity lists, report history, and persistent main-map camera/panel layout in every build
- Persistent local raw-report archive and historical report browser
- English, Czech, and Japanese UI/place-name handling
- Light, dark, and system appearance modes
- Custom Android notification cards with Shindo or tsunami-grade graphics, alert-level borders, multiline event details, location-aware coverage, audible/silent thresholds, scheduled quiet-hour delivery policies, manual city/postcode relevance filtering, and expiry-safe cold-start incident restoration; active EEW rings/countdowns are restored only when the intentionally local forecast engine is present
- Opt-in foreground live monitoring with a permanent silent connection-status notification; it reuses the single P2PQuake runtime and continues after QuakeDeck is closed
- Independent Warning, paid Forecast, and tsunami notification attention controls: off, a brief wake-screen, or a full-screen alert. Forecast delivery offers Shindo 0 through 4 with separate handling below the selected level; Warning offers Shindo 5− through 7; tsunami attention starts at Warning or Major Warning. The first EEW bulletin and each changed relevant Shindo alert, unchanged revisions silently refresh the card, and crossing from Forecast to Warning posts a new Warning alert; full-screen delivery requires Android's separate permission
- Official rotating P2PQuake Sandbox, deterministic built-in replay scenarios, and one-shot live-pipeline injections for earthquake reports, EEW warnings, audible DM-D.S.S-style EEW forecasts, and tsunami warnings
- Independently synchronized NICT-based JST display clock

## Data sources

P2PQuake remains the always-running free baseline and fallback. Users may optionally authorize DM-D.S.S to overlay live EEW forecasts when their account has an active `eew.forecast` plan; the source panel also shows their other active DM-D.S.S plans without using those data feeds yet. **DM-D.S.S integration status: pending testing with actual live EEW events.** A real five-revision event has validated post-fix Base64/GZIP parsing, paired-feed deduplication, Shindo-change notification delivery, full-screen launch, final-bulletin receipt, and timing close to JQuake. The subsequent fix for silent unchanged revisions, exact event-time-plus-180-seconds termination, ended-state restoration, rotation, escalation, foreground reconnection, and post-event recovery is not yet production-verified. See `THIRD_PARTY_DATA.md` for bundled-data attribution and licensing notes.

## Opening and building

Open the repository root (the folder containing `settings.gradle.kts`) in Android Studio. The project compiles against Android API 37, currently targets API 36 pending its Android 17 behavior-validation pass, and uses Java 17; Android Studio normally provisions the appropriate JDK and offers any missing SDK components during sync.

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
