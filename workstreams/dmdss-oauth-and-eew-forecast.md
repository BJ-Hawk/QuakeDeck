# DM-D.S.S OAuth and EEW forecast

## Status — 0.10.1 finalized; integration pending further live-event testing

The implementation is finalized as **0.10.1** (`versionCode` 233), as approved
on 2026-09-02. It is no longer an in-progress development release.

**DM-D.S.S is partially live-event validated, not production-verified.**
Unobserved escalation, cancellation, lifecycle, reconnect, and missed-bulletin
recovery paths remain explicitly pending live-event testing. Release
finalization does not imply that every real-world path has passed.

## Scope and fixed boundaries

- User-facing OAuth authorization-code PKCE, encrypted credentials, automatic
  refresh, non-destructive re-authorization, and explicit revocation.
- Scopes: `contract.list`, `socket.start`, `socket.close`,
  `eew.get.forecast`, and the approved read-only `gd.eew` recovery check.
  No client secret is embedded in the APK.
- Active account plans are displayed; only an active `eew.forecast`
  entitlement enables the implemented forecast socket.
- One existing P2PQuake runtime remains the permanent baseline for confirmed
  reports, tsunami, history, Sandbox, monitoring, and fallback Warning delivery.
  Missing authorization, subscription, or DM-D.S.S connectivity must not remove
  that baseline.
- Warning and Forecast retain independent default-on notification switches,
  channels, thresholds, and wake/full-screen controls. Forecast offers Shindo
  0–4 floors with separate below-floor handling; Warning offers 5− through 7.
- No new DM-D.S.S classifications, second P2PQuake socket, standalone felt-only
  events, or changes to the felt-confidence gate are included in this release.
- Work remains local to the permanent checkout. No staging, commits, pushes,
  fetches, or publishing are authorized by this workstream.

## Completed implementation

### Connection and delivery

- `dmdata.v2` WebSocket transport, text/binary payloads, bounded Base64/GZIP
  converted-JSON decoding, VXSE44/VXSE45 semantic deduplication, serial ordering,
  cancellation, expiry, and reconnect.
- Normal WebSocket close handshakes; granted `socket.close` cleanup after
  abnormal failure. Only the newest queued JSON `pingId` is answered.
- Bounded `gd.eew` recovery after startup/reconnect: inspect the last five
  minutes, deliver only a still-active recent candidate, suppress duplicates,
  and preserve Forecast-to-Warning escalation.
- Existing authorizations can remain usable while missing newer scopes;
  cancelling Update authorization preserves the working account.

### Alerts and navigation

- First revision and changed relevant Shindo alert; unchanged revisions
  silently refresh the existing notification. Forecast-to-Warning creates a
  fresh Warning identity, and Ended remains a separate status.
- DM-D.S.S active lifetime stays at event origin + 180 seconds. Retained launch
  data cannot override explicit ended/cancelled state or reappear after expiry.
- Cold-start notification payloads restore the appropriate incident and focus;
  one-shot navigation prevents rotation from replaying an older report serial.
  Regular reports keep confirmed JMA data rather than inheriting active EEW
  intensity or notification identity.
- Live/local and Japan-wide delivery and attention share the same scope policy.
  Forecast, Warning, and tsunami attention remain independently controlled.

### Diagnostics, archives, and felt reports

- Source-labelled newest-20 human-readable packet preview for DM-D.S.S and
  P2PQuake; bounded raw, credential-redacted JSON export through explicit user
  action. Routine ping/pong and code-555 peer counts are omitted.
- Failure records retain network transport, socket age, listener currency,
  and planned reconnect delay; rejection and transport failures stay distinct.
- Accepted DM-D.S.S Forecasts and P2PQuake Warnings share the source-neutral EEW
  archive representation. Replay orders EEW, attached felt snapshots, and
  confirmed reports by bulletin chronology, not insertion time.
- Informative `9611` aggregates attach to a matching EEW/confirmed incident,
  transfer to the confirmed report, remain cumulative across recovery, and
  appear in replay plus final historical-catalogue/Recent-earthquakes counts.
  Unmatched or insufficient-confidence aggregates remain diagnostics-only.

### Historical intensity shading — completed 2026-09-02

- Historical EEW revisions now call the existing FULL intensity engine on the
  archive executor, using only that revision's inputs. The map uses the same
  official-first/local hybrid presentation as live; modelled Shindo 0 remains
  neutral and official areas are never overwritten.
- New frames retain magnitude unit, hypocentre condition, source accuracy,
  region codes, PLUM/Warning flags, and cancellation state. Existing archives
  remain readable but cannot recover metadata older versions discarded.
- Replay estimates use the installed engine/resources. They are not presented
  as a frozen copy of an older engine's output.
- Historical P/S rings, countdowns, live alerts, and automatic refits between
  report steps remain disabled. Confirmed frames retain observed data only.
- LITE keeps official-only shading. The ignored engine and its calculation
  contracts are unchanged; a FULL checkout on another machine still requires
  the manually copied matching `LocalEewForecastEngine.kt`, which Git cannot
  transfer.
- Calculation details and limitations:
  [Local EEW intensity workstream](local-eew-intensity-prediction.md).

## Retained live evidence

### DM-D.S.S events — 2026-08-24/25

- Event `20260824203209` delivered four serials, each over VXSE44 and VXSE45,
  with overall Shindo 3. The original parser rejected all eight Base64/GZIP
  bodies before notification policy; the production-envelope fix addressed it.
- Event `20260825002747` then validated decoding, paired-feed deduplication,
  five logical revisions, Shindo 1 then 2 notification delivery, full-screen
  launch, final-bulletin receipt, and timing close to JQuake.
- That event exposed repeated unchanged alerts, retained-payload reactivation,
  failed UI expiry, and rotation replaying Report #1. Those fixes shipped in
  `0.10.0`; their documented live-validation gaps remain open.
- A recorded network handover caused a socket abort followed by prompt
  reconnection and a recovery check reporting no newly missed recent EEW.
  This does not prove every interruption/recovery path.

### P2PQuake felt-association diagnosis — 2026-09-01

The supplied `quakedeck-delivery-diagnostics-20260901-121052.json` showed
both missing aggregates arrived inside the association window:

- 11:32 JST Kumamoto: aggregate at +81 seconds, `count: 1`,
  `confidence: 0`, no `area_confidences`.
- 12:01 JST Amakusa/Ashikita: aggregate at +45 seconds, later updated to
  `count: 2`, still zero confidence and no regional confidence entries.

Both were rejected by the existing
`reportCount >= 2 && (confidence > 0 || areas.isNotEmpty())` gate, not by
transport or timing. The post-confirmation policy decision remains separate:
keep the gate, allow matching count ≥2 after an official `551`, or allow
a matching singleton only after confirmation. No option has been implemented;
the active-EEW gate and ban on felt-only events remain unchanged.

## Validation and remaining checks

- FULL and forced-LITE debug Kotlin compilation and 107 unit tests pass,
  including eight new archive/replay forecast regressions. No APK was built
  for this release task.
- Device-check historical empty-region and mixed official/local EEW frames,
  forward/backward revision changes, and the transition to confirmed data in
  portrait/landscape. Confirm no historical rings/countdowns appear.
- On the next suitable authorized-device live event, verify unchanged versus
  changed-intensity alerts, exact 180-second termination, Ended navigation,
  rotation, and independent Forecast-to-Warning escalation.
- Exercise cancellation, foreground-service/network reconnection, and
  `gd.eew` recovery after a genuinely missed live bulletin.
- Recheck account/subscription fallback, Update authorization, independent
  attention thresholds, and cold-start notification focus. Successful live
  authorization already demonstrates a usable registered callback; control
  panel changes remain account-side.
- Check raw export redaction/retention boundaries with further real traffic.
- Obtain a separate decision before changing confirmed-event felt acceptance,
  then validate that decision against real diagnostic evidence.
- Keep the pending-live-testing label until the user reviews the missing live
  evidence. A future commit or publication still needs explicit authorization.

## Release record

- Initial OAuth/Forecast integration finalized in `0.10.0` (code 220).
- Hybrid calculations, replay, diagnostics, felt counters, and the historical
  shading extension finalized in `0.10.1` (code 233).
- The cumulative [changelog](../CHANGELOG.md) is the release summary; this
  workstream records boundaries, evidence, and remaining validation rather
  than repeating the full development-version chronology.
