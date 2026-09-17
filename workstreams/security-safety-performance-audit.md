# Code security, safety and performance audit remediation

## Status — prepared; not implemented

Prepared on **17 September 2026** from the read-only audit of the current local
QuakeDeck checkout. **All nine findings remain open.** This workstream records
the evidence, remediation scope and validation needed for a later implementation;
creating this document does not implement or validate the fixes.

The user requested a comprehensive repository workstream for the nine findings.
This preparation changes only this file and its entry in `WORKSTREAMS.md`.
It does not authorize an APK build, installation, live alert injection, release,
staging, commit, push or fetch. Work uses local source, not a GitHub checkout.

The inspected baseline is `0.10.2-dev.5` / `versionCode` 238. The checkout already
contains staged, unstaged, untracked and ignored work. Preserve all of it.
No version, changelog, README or `.gitmessage` update belongs to this
documentation-only preparation. Recheck the current baseline before implementing;
the version recorded here is an audit reference, not a future version assignment.

## Required outcome

Prevent untrusted notification launches and obsolete notification/socket state
from presenting false live alerts; preserve authorization through temporary
failures while making disconnect definitive; restrict the developer map editor
to authenticated, intended file operations; and remove avoidable UI-thread and
archive work from time-sensitive paths.

Close each finding separately with evidence. A unit-test pass is not proof of
Android launch security, production delivery latency or live-event correctness.

## Audit evidence and limits

The audit examined the Android manifest, activity/notification entry points,
runtime/provider lifecycle, OAuth storage and requests, notification restoration,
diagnostics, archive/backfill paths, relevant rendering and forecast integration,
the local map editor, website OAuth code and build configuration. This was a
targeted source audit, not exhaustive verification of every file or scientific
validation of the private forecasting model.

The nine findings are supported by source tracing. Two additional read-only
probes were performed:

- **Map editor:** the actual `Handler.do_POST()` accepted an in-memory request
  with `Origin: https://untrusted.example`, `Content-Type: text/plain`, and a
  write targeting `build.gradle.kts`. It returned HTTP 200 with
  `{"ok": true, "written": ["build.gradle.kts"]}` and reached one mocked
  `Path.write_text` call. Filesystem mutation methods were intercepted; no server
  was opened and no target file was changed. An outside-root path was separately
  rejected. This demonstrates the missing request boundary, not a path-traversal
  bypass or a tested browser exploit.
- **Archive statistics:** the current `CREATE TABLE`, declared indexes and
  statistics SELECT were evaluated using SQLite `:memory:` and
  `EXPLAIN QUERY PLAN`. The result included `SCAN reports` and
  `USE TEMP B-TREE FOR count(DISTINCT)`. This confirms query work, not Android
  latency, battery impact or measured memory exhaustion.

No Gradle build, Android unit-test run, APK assembly, installation, device
exploitation, live provider test, browser loopback exploit or performance profile
was performed for this audit. Dependency CVE coverage was not included. Existing
test results in other workstreams are not validation of these proposed fixes.

Android's documented exported-component behavior supports finding A01:
[Android exported-component security guidance](https://developer.android.com/privacy-and-security/risks/android-exported).
Source symbols below are the durable references; line numbers from the original
audit may shift with unrelated local work.

## Finding register

P1 means address first because incorrect trusted alert presentation is possible.
P2 means an actionable security, reliability or performance defect requiring a
scoped fix. These are engineering priorities, not CVSS scores.

| ID | Original audit item | Priority | Area | Finding | Status |
| --- | --- | --- | --- | --- | --- |
| A01 | 1 | P1 | Security / safety | Exported activity trusts notification content and full-screen extras | Open |
| A02 | 2 | P1 | Safety | Retained tsunami payload overrides cancellation or expiry | Open |
| A03 | 3 | P2 | Reliability | Temporary refresh-response failure clears authorization | Open |
| A04 | 4 | P2 | Safety / lifecycle | Obsolete DM-D.S.S socket messages mutate current state | Open |
| A05 | 5 | P2 | Security / lifecycle | In-flight authorization work can restore credentials after disconnect | Open |
| A06 | 6 | P2 | Developer-tool security | Map editor accepts unauthenticated repository writes | Open |
| A07 | 7 | P2 | UI performance | Authorization getters decrypt credentials during rendering | Open |
| A08 | 8 | P2 | Archive performance | Live archive writes repeatedly scan the whole archive for statistics | Open |
| A09 | 9 | P2 | UI / ingress performance | Historical backfill merges an unbounded result set on the main handler | Open |

## Fixed boundaries and related work

- P2PQuake remains the permanent baseline and fallback. DM-D.S.S failure or
  authorization loss must not remove confirmed reports, tsunami or P2P Warning
  delivery. Preserve independent Forecast/Warning switches, thresholds, channels,
  quiet hours and attention permissions.
- Keep official provider values authoritative. Do not change forecast formulas,
  regional relevance policy, station research, map tiers or geographic resources
  as a shortcut to fixing these findings.
- Preserve the implemented
  [felt-flood ingress mitigation](p2p-felt-flood-ingress-mitigation.md): shared
  history cache, background atomic persistence, 750 ms batching, code `561`
  retained without UI dispatch, and existing `9611` association semantics.
  Trim diagnostics only when **both** more than 200 entries **and** more than
  2 MB of serialized history are present. Do not restore the old per-packet
  synchronous file rewrite or introduce silent drops.
- Preserve source-neutral P2P `556` and DM-D.S.S VXSE44/VXSE45 EEW archives,
  semantic deduplication, incident identity, source-time ordering and static
  historical replay. Historical replay must not gain P/S waves or countdowns.
- Keep the established FULL/LITE boundary. This workstream does not need changes
  to the ignored `LocalEewForecastEngine.kt` or its private resources. If later
  implementation requires such changes, follow the global manual-transfer rule
  in [WORKSTREAMS.md](../WORKSTREAMS.md), including both matching private resource
  files in `app/src/localForecast/res/raw/`; Git does not transfer them.
- Coordinate lifecycle changes with the
  [DM-D.S.S integration workstream](dmdss-oauth-and-eew-forecast.md), retaining its
  outstanding device/live validation. Do not rewrite that workstream's release
  history or label unobserved paths production-verified.
- Restrict map-editor changes to its server/client security boundary and related
  tests/documentation. Do not regenerate or edit the real map data during testing.
- Dependency upgrades/CVE research, source-legality clearance, privacy-policy
  rewrites, a full OAuth redesign and a general UI redesign are separate work.

## A01 — Authenticate notification launches before trusting alert data

**Evidence and trigger.** [AndroidManifest.xml](../app/src/main/AndroidManifest.xml)
exports `MainActivity`. In
[MainActivity.kt](../app/src/main/java/cz/misa/quakedeck/MainActivity.kt),
`updateNotificationNavigation()` reads caller-supplied report ID/event JSON and
`applyFullScreenEewWindowMode()` trusts `EXTRA_FULL_SCREEN_EEW`.
`NotificationEventPayload.decodeLaunch()` and `withNotificationLaunch()` then
adopt that data for UI presentation. A separately installed app able to launch
the activity can fabricate these extras. The exported launcher/OAuth routes are
legitimate; their existence does not authenticate notification extras.

**Impact and qualification.** Forged earthquake/tsunami content can be displayed
as an active incident, with caller-requested screen-on/lock-screen presentation.
The restored snapshot uses `LiveUpdateKind.NONE`; this finding does not claim
the payload automatically traverses the live audible-notification pipeline.
Android-version/background-launch restrictions require device verification.
Immutable PendingIntents prevent modification of those PendingIntents, not
separate explicit launches of the exported activity.

**Remediation plan.**

- Introduce a trusted internal notification-launch boundary. Prefer a
  non-exported internal destination reached by app-created immutable
  PendingIntents, with public launcher/OAuth entry points accepting only their
  intended inputs. A persisted unpredictable reference to app-private notification
  data or authenticated payload is an alternative if justified by cold-start needs.
- Select and document the mechanism before editing. An exported forwarding
  activity must not blindly copy privileged extras. An action string, package
  extra, report ID, intent filter or referrer alone is not authentication.
- Validate origin before decoding event data or setting window attention flags.
  Bound payload size, validate its schema/identity, and check freshness before
  restoring active state. Do not deserialize or calculate forecasts for rejected
  input on the main thread.
- Preserve process-death restoration, normal launcher entry, OAuth PKCE/state
  validation, rotation behavior and legitimate notification navigation. Define
  safe handling of notifications issued before the fix; invalid legacy payloads
  must not gain trust merely for compatibility.

**Acceptance and tests.** A hostile external launch with plausible report JSON,
all three known extras and a future/missing expiry cannot create an active alert
or enable privileged window flags. Rejected payloads cannot override an actual
live event. Legitimate earthquake, EEW and tsunami notification taps still work
after process death and across configuration changes. Cover trust decisions in
unit tests and exported-component behavior with an instrumented external caller
on an authorized emulator/device; testing from the same app UID is insufficient.

## A02 — Keep authoritative tsunami state above retained notification state

**Evidence and trigger.** In
[NotificationEventPayload.kt](../app/src/main/java/cz/misa/quakedeck/data/NotificationEventPayload.kt),
the TSUNAMI branch of `AppSnapshot.withNotificationLaunch()` sets
`activeTsunami = !notificationTsunami.cancelled` whenever the live-active/same-ID
case fails. It checks neither payload expiry nor a later authoritative
cancellation. `MainActivity` retains the launch payload, but its provider-ended
cleanup and expiry effect specifically handle EEW.

**Reproduction scenario.** Open a valid active tsunami notification; retain its
launch payload; then deliver a same-incident cancellation or expiry from the
provider. The next UI merge can reactivate the retained warning. This does not
depend on retaining an obsolete Android notification after cancellation.

**Remediation plan.**

- Give newer authoritative cancellation, expiry and revision state precedence
  over retained payloads. Clear the retained tsunami launch when superseded.
- Compare incident identity and issue/revision time, not just a Boolean active
  flag. A notification for an older/different incident must not replace a newer
  active tsunami. Retain explicit historical navigation without labeling it live.
- Distinguish an uninitialized cold-start runtime from a runtime that has
  authoritatively ended an incident; simply refusing every inactive snapshot
  would break legitimate cold starts. Define the fallback freshness policy when
  an upstream tsunami bulletin has no explicit expiry, rather than inventing an
  arbitrary universal warning lifetime.
- Apply existing Sandbox timeline offsets consistently; do not change the
  actual provider warning/cancellation lifetime or static replay presentation.

**Acceptance and tests.** Extend
[NotificationEventPayloadTest.kt](../app/src/test/java/cz/misa/quakedeck/data/NotificationEventPayloadTest.kt)
with cancellation after tap, already-expired payload, expiry while visible,
newer same-incident bulletin, newer different incident, absent expiry and fresh
cold-start cases. Test retained-state cleanup in addition to the pure merge
helper. Existing EEW end/expiry and legitimate tsunami cold-start tests must
remain valid. No cancelled/expired warning becomes active again on recomposition.

## A03 — Preserve authorization on retryable refresh failures

**Evidence and trigger.** In
[DmDssOAuthClient.kt](../app/src/main/java/cz/misa/quakedeck/data/DmDssOAuthClient.kt),
`executeTokenRequest()` calls `store.clearAll()` for every failed parsed refresh
response. HTTP 429/5xx, malformed JSON and response-read failures caught in
`onResponse()` therefore erase credentials. The separate transport `onFailure()`
currently does not clear them; retain that distinction in the reproduction.

**Impact.** A temporary service error becomes a persistent requirement to sign in
again. DM-D.S.S forecasts cannot automatically resume, although P2P fallback
remains available.

**Remediation plan.**

- Return structured failure categories: retryable transport/service failure,
  definitive invalid/revoked grant, and protocol/scope/configuration failure.
  Only clear the applicable credential generation for a definitive invalid grant;
  malformed or missing error data is not proof of revocation.
- Retain encrypted refresh credentials through rate limiting, server errors and
  transient parsing/read failures. Use bounded retry/backoff, respect valid
  Retry-After information, and avoid retry storms or reuse of expired access tokens.
- Keep scope enforcement strict. Do not convert this fix into acceptance of a
  token missing required permissions. Surface actionable authorization-update or
  configuration errors without destroying unrelated valid account state.
- Coordinate every asynchronous store mutation with A05 so an old request cannot
  clear or replace a newer account/session. Keep credential values out of errors
  and diagnostics.

**Acceptance and tests.** With a fake token service and credential store, cover
429, 500/503, non-JSON response, truncated/read-failing response, transport timeout,
valid refresh, definitive invalid grant and insufficient scope. Retryable failures
retain credentials and later recovery succeeds without sign-in; definitive grant
failure invalidates only the matching session. Confirm P2P fallback throughout.

## A04 — Reject obsolete socket callbacks before mutating provider state

**Evidence and trigger.** In
[DmDssProvider.kt](../app/src/main/java/cz/misa/quakedeck/data/DmDssProvider.kt),
`handleSocketMessage()` processes `start` and `data` without checking the captured
connection generation or stopped state. `stop()`/`reconnect()` advance the
generation and close the socket, but close is not an immediate barrier against
already-running or subsequently delivered callbacks. Other paths already use
generation checks.

**Impact.** A late old listener can set `connected` true, process/archive an old
event, emit a snapshot, or interfere with reconnection. Different-event data is
not protected by a same-event serial comparison.

**Remediation plan.**

- Check connection currency before semantic processing of all incoming messages,
  including `start`, `data`, malformed data, error/control messages and recovery.
  Preserve explicitly labeled stale-listener diagnostics where useful.
- Make validation and state mutation ordered against stop/reconnect; an isolated
  early check followed by unsynchronized mutation still permits a race. Choose
  one state owner or an equivalent generation-aware synchronization design.
- Recheck queued work at application time. Keep expensive parsing/forecast work
  off the main thread and network I/O outside locks; do not fix lifecycle races
  by serializing all expensive live processing on the UI handler.
- Include pending recovery and expiry callbacks in the lifecycle review. Stale
  callbacks may clean up their own abandoned socket, but must not clear a newer
  socket, active event, connection state or retry schedule.

**Acceptance and tests.** Deterministically interleave old `start`/`data`/error/
close callbacks with stop, reconnect and a new accepted revision. Test a callback
paused before state application, then invalidate its generation before resuming.
No obsolete listener produces live state, live notifications or accepted-event
archive writes. The newest connection still starts, responds to pings, recovers
missed EEW and expires its own event correctly.

## A05 — Make disconnect invalidate outstanding OAuth work

**Evidence and trigger.** `DmDssOAuthClient.disconnect()` clears preferences and
revokes captured tokens, while `withAccessToken()` saves successful refresh
results unconditionally. A refresh response arriving after disconnect can put
credentials back. Review `completeAuthorization()` for the equivalent late
authorization-code exchange and its runtime success callback.

**Impact and qualification.** Locally cleared credentials and the authorized
indicator can return after disconnect. Whether the returned tokens remain usable
depends on provider revocation behavior; the audit did not establish that they do.

**Remediation plan.**

- Add a session/authorization generation captured by refresh, code exchange,
  contract checks and relevant callbacks. Advance it on disconnect and account
  replacement. Perform the generation check and store mutation atomically.
- Cancel outstanding requests where practical, but treat cancellation as
  best-effort; stale responses must be harmless even when cancellation loses.
- Resolve all waiting refresh callbacks exactly once. Do not leave the refresh
  coalescer stuck or let old callbacks connect a provider or select DM-D.S.S again.
- Prevent old failures from clearing a newer session. Best-effort cleanup of
  stale newly issued tokens must not revoke tokens adopted by a newer valid
  session or repopulate storage. Document revocation failure without undoing
  local disconnect.
- Coordinate A03 and A07 with this lifecycle, including cache invalidation and
  process restart after disconnect.

**Acceptance and tests.** Delay token success, disconnect, then release success:
storage and observable authorization remain disconnected. Repeat for late failure,
late code exchange, multiple refresh waiters, successful reauthorization to a
new session, and failed remote revocation. Verify no credential resurrection or
new-session deletion and no unintended source-mode change.

## A06 — Restrict map-editor requests and file access

**Evidence and trigger.** In [server.py](../tools/map-editor/server.py),
`Handler.do_POST()` parses a request body as JSON without request authentication,
Origin/Host validation or a JSON content-type requirement. `safe_project_path()`
blocks escape from the repository but permits arbitrary files inside it.
`/api/read` also provides broad repository reads; shutdown/disconnect mutate
server lifecycle. Binding to `127.0.0.1` limits network exposure but does not
establish which browser page issued a request.

**Threat boundary.** The demonstrated issue is an untrusted request reaching
privileged local file operations while the editor runs. Browser exploitability
depends on browser version, secure-context/local-network restrictions and user
permissions. Do not claim an unconditional remote exploit or a sandbox escape.
A hostile local process already running as the same OS user is a different
boundary and is not fully excluded by browser request controls.

**Remediation plan.**

- Require an unpredictable per-run session capability for API operations and
  validate the configured loopback Host and same-origin browser requests.
  Reject foreign/null origins on state-changing requests. Handle legitimate
  lifecycle requests explicitly; do not leave unauthenticated GET shutdown.
- Provide the capability only to the intended local editor session, not in
  logs, a permissive CORS response, or a broadly readable unauthenticated API.
  Keep browser usage and the existing launcher straightforward; no persistent
  account or cloud service is required.
- Require the intended content type and bounded positive body length; validate
  the complete payload, encodings and all targets before starting a batch write.
  Content type alone is not sufficient authorization.
- Define separate exact read/write allowlists from actual editor operations.
  Preserve both editable layer families, their override JSON and the specific
  baseline save/restore files under `tools/source/map_editor_baseline/`.
  Never allow arbitrary descendants of the repository or baseline directory.
- Keep resolved-path containment checks, including Windows path forms and
  symlink/junction behavior. Deny build scripts, `.git`, local credentials and
  unrelated source. Validate every batch member before any file mutation.
- Update [app.js](../tools/map-editor/assets/app.js), launcher only if necessary,
  and [editor documentation](../tools/map-editor/README.md) together. Preserve
  save, reload, baseline restore and graceful tab-close behavior.

**Acceptance and tests.** In an isolated temporary fixture or fully intercepted
filesystem, the audit's foreign-origin `text/plain` request is rejected before
any write. Cover missing/wrong token, foreign Host, foreign/null Origin, invalid
length/encoding, mixed valid/invalid batch, path traversal, Windows drive/UNC
paths and disallowed in-root targets. Test read/lifecycle routes too. Authenticated
normal save and baseline restore for both layers succeed. Browser verification,
when authorized, uses copied fixture data, never the real map resources.

## A07 — Remove credential decryption from rendering

**Evidence and trigger.** `MainActivity` passes `runtime.isDmdssAuthorized` and
`runtime.isDmdssAuthorizationUpdateRequired` into status/source UI.
`DmDssOAuthClient.isAuthorized` and `authorizationUpdateRequired` each call
`CredentialStore.loadCredentials()`, which decrypts both tokens with Android
Keystore. Reevaluating both expressions therefore performs four token decryptions
on the caller's thread. Rendering invokes them on the UI thread.

**Qualification.** This establishes avoidable synchronous work. The exact rate
depends on Compose recomposition scope; do not assume every 250 ms countdown
tick necessarily reruns all expressions. No device jank duration was measured.

**Remediation plan.**

- Expose immutable, observable authorization metadata (loading/authorized/scope
  update required/disconnected as appropriate), initialized asynchronously.
  UI getters must not read/decrypt credentials or mutate credential storage.
- Refresh the metadata on successful authorization/refresh, definitive
  invalidation, disconnect and storage/Keystore failure. Use A05's generation
  rules so a late background load cannot resurrect old state.
- Keep token access private to the network/credential path. Do not put plaintext
  tokens in Compose state, diagnostic objects, saved instance state or logs.
  No redundant permanent plaintext token cache is required just to answer
  authorization-status questions.

**Acceptance and tests.** A counting fake credential store shows zero decrypts
from repeated UI status reads after initialization. Cold start and all lifecycle
transitions display correct metadata without stale connected state. Profile an
authorized device with active EEW/tsunami animation and the source dialog open;
record main-thread Keystore activity and frame timing before/after, without
claiming a timing improvement from unit tests alone.

## A08 — Coalesce archive statistics instead of rescanning per receipt

**Evidence and trigger.** In `P2pQuakeProvider.archiveReport()`, an applicable
non-556 report queues `storeReports()` followed by `archiveStore.stats()`; 556
also schedules a statistics refresh. In
[ReportArchiveStore.kt](../app/src/main/java/cz/misa/quakedeck/data/ReportArchiveStore.kt),
`stats()` calculates total rows, distinct confirmed incidents and payload bytes
over `reports`. The in-memory query plan confirms a full scan and distinct-count
temporary structure. These tasks share the serial archive executor.

**Impact.** Work per statistics refresh grows with archive size and can delay
queued archive operations. This does not prove that the live socket callback
waits for SQLite, nor does it by itself explain the earlier felt-flood delay.

**Remediation plan.**

- Prefer a dirty revision plus one bounded/coalesced background refresh, with a
  trailing refresh after writes settle. Reuse the existing statistics snapshot
  between refreshes; provide a final accurate result after explicit operations.
- If incremental counters are justified instead, update them transactionally
  across both provider writers, duplicate suppression, migration, clear and
  backfill. Define migration/reconciliation and crash behavior. Incident counts
  are distinct confirmed incidents, not simply one increment per inserted row.
- Protect against old statistics arriving after archive clear or a newer
  generation. Keep listeners on their intended thread. Do not silently change
  retention, archive enablement, payload contents or reporting units.
- Preserve real SQLite sidecar-aware disk-size reporting; a cached estimate must
  not masquerade as an exact current footprint. Share invalidation across P2P
  and DM-D.S.S rather than independent stale per-provider counters.

**Acceptance and tests.** Burst insertion generates a bounded number of scans,
not one per report, and final totals equal a direct reference query. Cover
duplicates, multiple revisions of one incident, both providers, backfill, clear
during an in-flight refresh and write failure. Profile synthetic small and large
archives off-device/in a fixture first; record scan count, executor delay and
main-thread activity. Do not use a planner result as a latency benchmark.

## A09 — Bound backfill memory and main-thread application

**Evidence and trigger.** `P2pQuakeProvider`'s historical-download worker appends
all pages to `collected`. At completion it posts a main-handler callback which
sorts the collected earthquake reports, calls `acceptMessage()`, `parseQuake()`
and `addToHistory()` for each, then schedules the recent cache update.
The live P2P dispatch handler uses that same main Looper.

**Impact and qualification.** A large backfill can retain substantial payload
memory and occupy the main handler while live reports wait. The recent event
list itself is already capped at 30 by `addToHistory()`; the defect is unbounded
download/result processing, not an unbounded visible history list.

**Remediation plan.**

- Stream pages into the archive and release page payloads. Build only the bounded
  recent-display result needed by the UI, with all required revisions of those
  incidents, rather than retaining the complete downloaded window in memory.
- Do pure decode/sort/preparation off-thread. Keep mutation of shared
  `seenMessageIds`, incident/crowd association state and live history under its
  established state owner; moving existing mutable methods to a worker directly
  is not a safe fix.
- Apply immutable results with a generation/freshness check, using bounded
  batches that yield or a proven bounded merge. A late historical result must not
  overwrite a newer live revision, cancellation, source-mode change or clear.
- Keep live archive writes and user clear/cancel actions responsive while
  backfill performs HTTP calls or rate-limit waits on the archive executor.
  Separate network/page scheduling from short database tasks where needed,
  preserving write ordering and transaction integrity.
- Preserve automatic stop-at-known-page behavior, manual retained-window
  semantics, existing request pacing, semantic deduplication and no live-alert
  delivery for historical data. Do not truncate the durable archive to make the
  recent display cheap.

**Acceptance and tests.** Use controlled multi-page fixtures and deterministic
executor barriers. Inject an official live report during download and result
application; it is handled without waiting for the complete historical merge.
Test newer live revisions, identical report IDs, cancellation, archive disable,
clear, Sandbox/source changes, network failure and duplicate-only pages. Memory
retained for display processing stays bounded as page count grows, while every
eligible historical report is archived correctly. Device profiling must measure
main-thread slices, dispatch latency and peak memory under a large fixture.

## Implementation sequence and decision record

Keep the findings as separate reviewable units within this workstream; do not
mix unrelated station/map/release work into an audit fix.

1. Recheck local status and the symbols above. Capture a baseline for the files
   to be changed, including ignored-engine presence and existing test results.
2. Resolve A01 and A02 first. Record the chosen launch-authentication mechanism,
   legacy-notification behavior and missing-expiry tsunami policy.
3. Implement A05's session ownership together with A03's classified failures;
   then use that ownership for A07's observable metadata. Record lock/state-owner
   rules and retry policy explicitly.
4. Resolve A04's provider state ownership and stale-callback behavior. Ensure it
   composes with OAuth disconnect and missed-event recovery.
5. Harden A06 as a separate developer-tool change. Record exact read/write
   allowlists, token bootstrap and lifecycle request authentication.
6. Implement A08, then A09, measuring each independently. Record coalescing
   interval or counter strategy, backfill batch bounds and device targets.
7. Run integrated regressions and document device/live results separately from
   local build/test evidence. Advance status only for findings actually closed.

These are implementation choices to resolve from current code and tests, not
requests for another permission round during document preparation. Avoid
specifying a security design solely to match the old implementation. Changes
that genuinely extend the user's scope must be identified before proceeding.

## Validation matrix for implementation

| Validation layer | Required coverage | Evidence to record |
| --- | --- | --- |
| Notification trust | A01 external vs internal entry, cold start, OAuth, rotation, legacy inputs | Unit cases plus authorized cross-UID device/emulator result |
| Alert lifecycle | A02 cancellation/expiry/newer incident; existing EEW end handling | Pure merge and retained-state tests; device cancellation scenario |
| OAuth lifecycle | A03/A05 retry classes, response races, disconnect/account replacement | Fake HTTP/store tests with deterministic callback ordering |
| Provider lifecycle | A04 old/new socket, recovery, expiry, stop/restart | Controlled interleaving tests; authorized reconnect smoke test |
| Developer tool | A06 all API boundaries and both layer save/baseline flows | Python fixture tests, mocked rejected writes and browser fixture result |
| Rendering cost | A07 status reads and lifecycle consistency | Decrypt-call counts and measured device trace |
| Archive statistics | A08 duplicates, both writers, clear/backfill, bounded scans | Exact reference totals and scan-count/queue-delay measurements |
| Backfill | A09 streamed pages, live interleaving, cancellation and bounded memory | Fixture equivalence, memory growth and dispatch-latency measurements |
| Cross-cutting regressions | Diagnostics retention/batching, crowd association, notification routing, source-neutral replay, FULL/LITE | Focused suites followed by complete relevant Android unit suite |

When implementation is authorized, use the local wrapper for
`:app:compileDebugKotlin` and `:app:testDebugUnitTest`, starting with focused
behavioral tests and then the full suite. Use forced LITE validation if tracked
forecast/loading contracts are touched; verify FULL separately where applicable
without moving/deleting the ignored implementation as a test trick. Inspect the
current build switches first. Do not assemble an APK as part of these commands.

Existing suites to retain include `NotificationEventPayloadTest`,
`QuakeDeckRuntimeNotificationRoutingTest`, `EewNotificationAlertTrackerTest`,
`DmDssEewParserTest`, `DmDssDiagnosticsTest`, `BatchedDiagnosticPacketHistoryTest`,
`EewArchiveFrameTest`, `P2pCrowdSignalTest`, `CorePolicyTest` and
`SandboxFeatureTest`. New tests should exercise rejected input, races and
behavioral invariants, not mirror implementation details. Additional Android
test infrastructure may be needed for true activity/Looper behavior.

Performance acceptance requires an explicit fixture size, device/emulator,
Android/build edition, before/after trace and chosen budget. The audit supplies
no measured baseline or universal millisecond target. Structural tests must
still prove zero render-path decrypts, coalesced scans and bounded backfill
application before timing claims are made.

Device validation is a later, explicitly reported step and may require a
separately authorized APK build/install. Use clearly marked synthetic data and
controlled notification channels on a test environment; do not inject fake live
warnings into ordinary monitoring. If device validation is unavailable, report
the specific remaining gap rather than declaring end-to-end completion.

## Completion and continuation checklist

- [x] Record all nine original findings with stable IDs and priorities.
- [x] Record actual audit evidence and its limits.
- [x] Define implementation boundaries, acceptance tests and sequencing.
- [x] Add the workstream to `WORKSTREAMS.md`.
- [ ] A01 implementation and acceptance evidence.
- [ ] A02 implementation and acceptance evidence.
- [ ] A03 implementation and acceptance evidence.
- [ ] A04 implementation and acceptance evidence.
- [ ] A05 implementation and acceptance evidence.
- [ ] A06 implementation and acceptance evidence.
- [ ] A07 implementation and acceptance evidence.
- [ ] A08 implementation and acceptance evidence.
- [ ] A09 implementation and acceptance evidence.
- [ ] Integrated compilation/unit validation with exact commands/results.
- [ ] Authorized device/browser scenarios and performance profiles, or an
      explicit per-finding pending-validation handoff.
- [ ] Final focused diff review, `git diff --check`, and status review preserving
      unrelated staged/unstaged/untracked work.

For each later implementation handoff, append the changed symbols/files,
decision taken, test command/result, observed runtime evidence and remaining
limitations under the relevant finding or a dated continuation entry. Distinguish
implemented, build/unit-validated and device/live-validated states. Do not mark
the whole workstream complete while any finding or required validation is open.

**Current handoff:** documentation prepared; A01–A09 unimplemented. The earlier
diagnostics batching fix remains a separate implemented workstream. No source,
tests, map data, private engine/resources or release metadata were changed by
this preparation; no build, APK, install, staging, commit, push or fetch was run.
