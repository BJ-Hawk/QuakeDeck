# P2PQuake felt-flood ingress mitigation

## Status — implemented locally; build/unit-validated; pending device/live approval

On 17 September 2026 the user authorized implementation of this scoped mitigation
and advancement to the next dev version. Local metadata is now `0.10.2-dev.5`
(`versionCode` 238), retaining the cumulative in-progress changelog and every
earlier commit-template bullet. An APK build, staging, commits, pushes, fetching
and UI work remain outside scope. Existing unrelated staged and unstaged changes
are preserved on local `main`.

## Work completed and current handoff

- `BatchedDiagnosticPacketHistory.kt` provides one registry entry per history
  path and one serial background executor shared by all diagnostics stores.
  Initial disk loading runs once on that executor; packets received during the
  load are merged after restored history without making receipt wait for I/O.
- New packets are sanitized as before and encoded individually. A short lock
  appends them and maintains exact UTF-8 JSON-array byte counts, including
  escaping, commas and brackets. Trimming requires **both** more than 200 entries
  **and** more than 2 MB; there is no new drop policy.
- A 750 ms scheduled batch writes an immutable snapshot through `AtomicFile`
  outside the state lock. Revision tracking keeps later appends dirty and
  schedules another write. Failed writes retain dirty state and retry; orderly
  provider stops request an immediate asynchronous flush.
- Both providers use the same cache and writer. Visible/exported diagnostics
  read that cache, including data not yet persisted. No UI or export-schema
  changes were required.
- `P2pQuakeProvider.onMessage()` uses a tested ingress function that records 561
  before returning without a main-handler post. Other valid packets keep their
  existing routing and order. No 9611 coalescing or priority queue was added.
- Neither forecast engine/private resources nor archive, association, event UI,
  or historical replay implementation was changed.

Focused Kotlin compilation and diagnostics, crowd-association and EEW archive
tests passed. Eight new tests cover batching, a 600-packet 561 flood followed by
551 while disk writing is blocked, retained/exported flood evidence, append-during-
write persistence, shared startup merging, failed-write recovery, exact UTF-8
retention boundaries and unchanged operational dispatch. The synthetic ingress
test covers the callback-to-dispatch boundary; it does not instantiate an Android
main Looper or prove device/network delivery latency.

Final validation on 17 September 2026: `:app:compileDebugKotlin` and the complete
`:app:testDebugUnitTest` suite passed (18 suites, 122 tests, zero failures, errors
or skips). `git diff --check` passed. No APK was assembled and no files were
staged, committed, pushed or fetched by this workstream.

Next steps: device/live flood validation and user approval remain pending. Check
that an official report following crowd traffic arrives promptly and that the
export retains 561 evidence; recheck cumulative counts through EEW-to-confirmed
handoff and provider stop/restart. Do not
reintroduce synchronous packet-history I/O or silently drop 561 evidence.
An abrupt process kill can lose the not-yet-written batch; stop flushing is
best-effort asynchronous and cannot guarantee persistence after forced termination.

## Observed incident — 17 September 2026, 00:07 JST

For the Ibaraki South Shindo 3 earthquake originating at `00:07:00 JST`,
official P2PQuake/JMA reports were handled well after their issued times while
DM-D.S.S EEW arrived normally on its independent socket:

| Report | Issued | Handled by QuakeDeck | Delay |
| --- | --- | --- | --- |
| `551` ScalePrompt | 00:09:18 | 00:11:37.247 | 139.2 s |
| `551` ScalePrompt | 00:10:18 | 00:12:13.304 | 115.3 s |
| `554` Destination | 00:10:49 | 00:12:25.122 | 96.1 s |
| `552` DetailScale | 00:13:04 | 00:13:24.057 | 20.1 s |
| `552` DetailScale | 00:16:46 | 00:17:08.278 | 22.3 s |

Before the first `551` was handled, the diagnostics recorded 229 code `561`
packets, 37 code `9611` packets, and 16 packets without a P2P code. The felt
traffic began at 00:07:58 and continued until 00:11:36.601, immediately before
the first official report was handled.

The diagnostic timestamps show when QuakeDeck handled the messages, not their
low-level network arrival time. They therefore cannot prove an upstream delay;
they do demonstrate a plausible local callback backlog.

## Diagnosed cause

Before this mitigation, `P2pQuakeProvider.onMessage()` recorded nearly every decoded P2P
packet before routing it. The shared diagnostics implementation performed full
history-file read, JSON parse, append, serialization, and atomic rewrite for
each recorded packet. This work happened on the WebSocket callback path.

WebSocket callbacks are serialized. During a felt-report flood, repeated
full-file persistence can therefore delay subsequent official packets queued on
that same P2P connection. Every packet is also posted to the main handler;
code `561` has no live event behaviour and is needless main-thread work.

The separate DM-D.S.S connection does not share this P2P callback workload,
which explains why EEW could remain timely while P2P official reports were not.

## Required outcome

Make P2P receive handling cheap enough that a flood of `561`/`9611` crowd
traffic does not delay official JMA reports, while retaining diagnostic evidence
and the existing cumulative felt-report semantics.

## Fixed boundaries

- Keep code `561` in diagnostics. It is useful flood evidence and must not be
  silently dropped.
- Keep the exact diagnostics retention rule: trim only after **both** more than
  200 entries **and** a serialized history larger than 2 MB. It is explicitly
  the later limit, never the first limit reached.
- Do not introduce a standalone felt event, expiry, dismissal, temporary UI,
  new controls, or a new settings option.
- Do not change the existing EEW/official-event UI: informative felt aggregates
  stay attached to the matching incident and a regular report amends that same
  incident with its cumulative felt count.
- Do not change existing historical replay controls or presentation. Historical
  replay remains static, with P/S waves and countdowns suppressed.
- Preserve P2P `556` warning and DM-D.S.S `VXSE44`/`VXSE45` source-neutral
  archiving and the existing `9611` felt-association rules.
- Do not touch `LocalEewForecastEngine.kt` or its ignored resources. If that
  boundary ever becomes necessary, follow the global transfer rule in
  `WORKSTREAMS.md`.
- No APK build. Do not stage, commit, push, fetch, or alter release metadata
  unless separately and explicitly authorized.

## Implementation plan

### 1. Batch diagnostics persistence off callback threads

Refactor the shared diagnostics packet history so `recordPacket()` only
sanitizes and appends to one shared in-memory history under a short lock.

- Load the persisted history once into shared state.
- Preserve packet order, source labels, and current redaction/sanitization.
- Apply the existing later-of-200-or-2-MB trimming rule to the in-memory state.
- Mark state dirty and schedule one background write after a short debounce
  (roughly 500–1000 ms).
- A single shared writer atomically writes a current snapshot. If packets arrive
  during that write, it must schedule a following write rather than losing them.
- Do not retain the shared-state lock during file I/O.
- P2PQuake and DM-D.S.S must use the same cache and write coordinator for this
  shared file; separate instance caches may overwrite newer records.
- Flush pending data during an orderly shutdown where practical, without making
  normal packet receipt wait for disk I/O.

### 2. Make code 561 diagnostics-only at live ingress

In `P2pQuakeProvider.onMessage()`:

- Record a `561` packet through the cheap in-memory diagnostic path.
- Return it without posting it to the main handler.
- Keep normal routing for official reports and operational messages, including
  `551`, `552`, `554`, and `556`.
- Keep `9611` routed to its current cumulative felt association logic.

This removes irrelevant main-thread work without discarding the raw evidence.

### 3. Defer 9611 coalescing unless measurements require it

The first implementation must solve synchronous file I/O and `561` main-thread
posts. Those are the demonstrated load sources. Do not add a priority queue or
rewrite event ordering pre-emptively.

If a post-fix stress test still demonstrates significant `9611` dispatch load,
coalesce only pending updates for the same incident key and preserve the newest
cumulative aggregate. Every raw packet must remain diagnostic history, and no
felt-only event may be created.

## Files and tests to inspect before changing code

- `app/src/main/java/cz/misa/quakedeck/data/P2pQuakeProvider.kt`
  - WebSocket `onMessage`, diagnostic call, main-handler routing, `561`, and
    `9611` handling.
- `app/src/main/java/cz/misa/quakedeck/data/DmDssDiagnostics.kt`
  - shared packet-history read/write, locking, snapshot, sanitization, and
    retention helpers.
- Existing diagnostics and P2P/felt unit tests.

Do not trust historical line numbers: inspect the current local checkout before
editing because it contains unrelated user work.

## Acceptance criteria

1. A burst of hundreds of `561` packets does not perform a full diagnostic file
   read/rewrite per packet on the WebSocket callback.
2. `561` remains available in exported/visible diagnostics after batched write.
3. An official `551` immediately after a synthetic `561` burst reaches normal
   official-report handling without a locally induced multi-minute delay.
4. `9611` still attaches and transfers cumulative felt evidence under the
   existing matching policy.
5. Tests prove the retention rule is later-of-200-entries-or-2-MB, including
   both one-limit-only cases.
6. Existing archive/history behaviour is unchanged: deduplicated VXSE44/VXSE45,
   normalized `556`, static historical replay, and no replay UI changes.
7. Validate with focused tests, `:app:compileDebugKotlin`,
   `:app:testDebugUnitTest`, and `git diff --check` when implementation is
   authorized. Do not assemble an APK.

## Risks and decisions

- Do not replace the shared cache with one cache per provider; that risks lost
  diagnostic records.
- Do not clear dirty state after writing an older snapshot when a newer append
  occurred during the write.
- Do not hold locks across disk I/O.
- Do not drop crowd packets as a shortcut; retain `561` diagnostics and retain
  meaningful `9611` aggregation.
- Do not attribute this incident conclusively to P2PQuake bandwidth. The
  evidence supports a local backlog caused by QuakeDeck's own callback work.
