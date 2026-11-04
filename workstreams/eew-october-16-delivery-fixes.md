# October 16 EEW delivery and lifecycle fixes

Status: implemented locally in `0.10.3-dev.5` / 243. FULL and LITE compile and pass all 142 unit tests each (19 suites; zero failures/errors/skips). FULL compilation is restored. Device notification, timer/background and live-network checks remain pending. No APK, staging, commit, push or fetch was performed.

## Incident and scope

The supplied delivery export has SHA-256 `8d18b06b4c879fcd3d1300a48675022f584ed165ec25792a6d27a148ecbae167`. Its incident `20261016123810` includes 41 ordinary serial numbers in VXSE44/VXSE45 (no retained #40), final Warning #42 and a later cancellation #37. Shindo 7 reached DM-D.S.S at 05:40:02 Czech time, after the old origin-derived 05:39:27 deadline. Both public P2PQuake Warning packets also failed the local FULL cap. Subsequent generic detection packets could borrow the previous Kumamoto/Shindo 1 report.

`app/src/test/resources/incidents/eew-20261016.json` retains 92 exact original records: 84 DM-D.S.S incident packets, two public Warnings, two generic detections, three connection-gap records and the preceding confirmed Kumamoto report. Only this bounded incident slice is included; no transport credentials or private forecasting implementation is copied.

## Changes

- A shared issue-time freshness policy keeps a new official bulletin active independently of estimated origin or modelled passage. FULL may extend the display, but cannot shorten the minimum issue-based deadline. LITE uses the same operational deadline. Stored/displayed origin times are unchanged.
- Both providers use bounded source-revision tracking. Later matching cancellations can have a lower serial; old cancellations and post-cancellation nonterminal revisions are rejected. Derived local forecasts/crowd counts no longer defeat source deduplication. Real VXSE44/VXSE45 coverage differences remain eligible.
- Runtime selection allows a newer public Warning to supersede a paid Forecast for the same incident. Bounded cancellation state prevents either feed's older Warning reopening the incident. Comparisons across feeds use issue time, never their incomparable serial numbers.
- Generic detections use an unmapped, unknown-Shindo pending event, with a neutral localized notification line. Detection expiry retains that identity rather than borrowing the last confirmed earthquake.
- EEW updates carry their own event separately from the visible confirmed report. Cancellation/expiry notifications and cold-start restoration use that identity and EEW category/location settings, independently of confirmed-earthquake notification bookkeeping. Actual cancellation and ordinary timeout have distinct titles.
- DM-D.S.S recovery reconciles returned newer same-level revisions and terminal state in source issue order, archives each eligible revision and publishes only the final resulting state. After process death it can reconstruct the matching accepted incident from retained wire packets and diagnostics; it never borrows an unrelated quake. Stale data and unrelated active incidents remain protected. Recovery cannot recreate an upstream revision the recovery API did not return.
- EEW-only catalogue rows and static replay include cancellation frames. Existing minute-only historical confirmed matching and its location gate are preserved. Content-based archive IDs avoid reused-serial and companion-region collisions; existing archive rows remain readable. Frames already lost by earlier builds cannot be reconstructed without the original data.
- Coalesced JSON pongs run on a dedicated worker, with atomic newest-ID check/send and stale-connection guards. Bounded context is retained on keepalive errors. Routine ping/pong retention remains excluded. Network aborts, outages and provider connection limits are not claimed fixed by the unit suite.
- Export includes build/version/edition and last accepted issue/cancellation fields. The existing bounded/redacted packet trail now records per-revision lifecycle, notification policy and Android-submission outcomes, including silent/suppression/layout/full-screen eligibility. Android submission is not proof of audible sound or successful full-screen presentation.

## Audit correction

The investigation's original standard-layout retry suppression finding was incorrect: `silent` already includes `suppressAlert`, so the retry already preserves suppression. No speculative retry fix was made. Repeated Settings history entries are not proof of simultaneous cards or repeated audible alerts.

## Validation

The exact incident regression suite injects recorded reception clocks; it never rewrites the source timestamps or delivers the fixture to live feeds. It exercises the production parsers, deadline policies, revision/cancellation tracking, notification event routing, recovery selection and static historical builders. Additional keepalive concurrency coverage verifies atomic latest-ping enqueueing.

Run locally/offline with the installed Android Studio JDK:

```powershell
.\gradlew.bat --offline --no-configuration-cache :app:compileDebugKotlin :app:testDebugUnitTest
.\gradlew.bat --offline --no-configuration-cache -PomitLocalEewForecastEngine=true :app:compileDebugKotlin :app:testDebugUnitTest
```

Both commands passed all 142 tests across 19 suites, with zero failures/errors/skips. Coverage includes 17 exact-incident regressions plus the new atomic ping concurrency test. All 92 fixture records were compared back to their exact positions in the original export. `git diff --check` passed. Neither command packages an APK. The workspace was restored to FULL compilation after LITE validation.

## Continuity

The ignored `LocalEewForecastEngine.kt` and both matching private resource files are unchanged. The fix is in tracked lifecycle plumbing; it introduces no private-engine contract change or manual engine-transfer requirement. Existing unrelated Python cache directories remain untouched. All changes remain unstaged and uncommitted.
