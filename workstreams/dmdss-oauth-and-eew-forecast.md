# DM-D.S.S OAuth and EEW forecast

## Status — pending testing with live EEW events

**This integration is not yet live-event validated or production-verified.** The repository implementation, Sandbox exercises, compilation, and automated tests are complete, but those checks cannot prove real DM-D.S.S delivery. Validation remains pending against actual live EEW Forecast events, including notification delivery, successive revisions, Forecast-to-Warning escalation, cancellation/final bulletins, socket interruption and foreground-service reconnection, and bounded `gd.eew` recovery after a genuinely missed live bulletin.

Until that validation is observed on a real authorized device/account, documentation must describe the DM-D.S.S integration as **pending live-event testing**, not complete or proven reliable. P2PQuake remains the permanent baseline and fallback throughout testing.

### Latest validation observation — 2026-08-24

- Foreground monitoring remained connected during device testing. A Wi-Fi/data/network handover produced the recorded `SocketException: Software caused connection abort`; QuakeDeck reconnected promptly and the following recovery check reported no newly missed recent EEW.
- The corrected diagnostics distinguished that transport interruption from a rejected bulletin and updated the open packet-history view without requiring a cold start.
- No real EEW event arrived during the observation window. Live Forecast intake, successive revisions, notification delivery, Forecast-to-Warning escalation, cancellation/final handling, and recovery of a genuinely missed bulletin therefore remain unvalidated.
- The Shindo 0–4 Forecast floor and below-floor off/silent/regular policy passed 15 focused DM-D.S.S tests, but no APK was assembled on the resource-limited development machine, so that latest policy has not yet been exercised on-device.

## Objective

Implement the first production-shaped Android DM-D.S.S integration: user OAuth and live `eew.forecast` reception, while retaining the existing P2PQuake provider as the single baseline for earthquake reports, tsunami information, archives, Sandbox tooling, and fallback EEW warnings.

## User decisions already made

- Start DM-D.S.S app work now.
- Limit this first slice to OAuth and EEW forecasts.
- Follow the current QuakeDeck local-development rules: work in the permanent checkout on `main`, use the next lettered in-progress version, preserve all unrelated work, and do not stage, commit, push, branch, fork, clone, or fetch.
- OAuth must be a user-facing application flow. Users authorize the public QuakeDeck client with their own DM-D.S.S accounts; no client secret belongs in the APK.
- P2PQuake must remain unchanged and always provide the baseline/fallback, including when OAuth succeeds but the user has no valid EEW forecast subscription.
- Request only the scopes used by this slice: read-only `contract.list` for the user-visible account capability summary, `socket.start`, `socket.close`, and `eew.get.forecast` for the implemented live forecast feed and abnormal-failure cleanup, plus the explicitly approved read-only `gd.eew` post-event recovery check.
- Show the user which active DM-D.S.S data plans the account really exposes and distinguish the EEW forecast used now from plans reserved for later integrations.
- Give Warning and paid Forecast notifications completely separate delivery and attention controls. Both remain enabled by default. Forecast offers Shindo 0 through 4 as the full-notification floor; below that floor the user can choose off, silent, or a regular notification without wake/full-screen. At Shindo 0 the below-floor choice is hidden because no lower JMA intensity exists. Warning continues to offer Shindo 5− through 7.
- Retain every diagnostically meaningful DM-D.S.S WebSocket application packet, including bulletin bodies, in a bounded app-private history. Exclude routine ping/pong heartbeats so they cannot evict useful traffic, but continue using them for connection activity and protocol replies. Redact credentials, never store OAuth tokens or socket tickets, and make the complete retained history explicitly exportable as machine-readable JSON.

## Work completed

- Confirmed the existing website capability checker is separate and requests only `contract.list`; it never opens a socket.
- Confirmed the Android runtime currently owns exactly one P2PQuake provider and the DM-D.S.S source choice is only a visual fallback placeholder.
- Confirmed from the current official DM-D.S.S documentation that native clients should use authorization-code PKCE, Socket Start requires `socket.start` plus `eew.get.forecast`, the `eew.forecast` classification can be delivered as converted JSON, and WebSocket ping frames require matching JSON pong replies.
- Added authorization-code PKCE with callback-state validation, Keystore-encrypted access and refresh tokens, token refresh, revocation, and the supplied public client ID without a client secret.
- Added `contract.list` capability discovery and localized account-plan presentation. Only an active `eew.forecast` entitlement enables the DM-D.S.S forecast connection; all other active plans are shown as available but reserved for later integrations.
- Added Socket Start and `dmdata.v2` WebSocket handling for converted VXSE44/VXSE45 forecasts, including ping/pong, update ordering, cancellation, expiry, reconnect, and regional forecast intensity parsing.
- Kept the existing P2PQuake instance as the always-running source for baseline data and fallback EEW. DM-D.S.S only overlays its forecast while selected, authorized, entitled, and connected.
- Added localized connection/capability text and focused regression tests for parsing, cancellation, classification filtering, PKCE, required scopes, and entitlement gating.
- Advanced the cumulative in-progress metadata to `0.9.84ab` (`versionCode` 207) without staging, committing, pushing, fetching, or publishing.
- Replaced the diagnostics drawer's obsolete hardcoded Not configured state with independent P2PQuake and DM-D.S.S connection rows plus an accurate composite-provider label.
- Added the `socket.close` grant and retained socket IDs so abnormal disconnects can release their server slot before retrying; clean shutdowns still use the normal WebSocket close handshake.
- Added non-destructive authorization updates. A cancelled update preserves current credentials and connectivity, while a successful update atomically replaces credentials and revokes the superseded tokens.
- Split notification policy completely by EEW level. Warning and Forecast each have their own default-on delivery switch, Android notification channel, wake/full-screen mode, predicted-intensity threshold, notification identity, and attention identity while sharing only the reference location and Android's system-level full-screen permission. Forecast exposes Shindo 0 through 4 as its full-notification threshold and offers off, silent, or regular notification delivery below it; regular below-level notifications never wake or use full-screen, and the below-level control disappears at Shindo 0. Existing installations default to regular below-level delivery to preserve their prior notification behavior. Attention activates on the first revision that actually crosses the respective threshold, with the prior lower-level card replaced so the crossing can alert freshly. Warning still exposes Shindo 5−, 5+, 6−, 6+, and 7. When an active event escalates from Forecast to Warning, the Forecast card is removed and a distinct Warning notification is posted on the Warning channel so it alerts again.
- Added a one-shot Sandbox EEW forecast beside the existing warning injector. It uses the same configurable delay and normal runtime/notification route, does not replace the active live or official Sandbox connection, and verifies the audible Forecast policy without touching `P2pQuakeProvider`. Active Sandbox mode retains the existing protection against waking or taking over the device.
- Replaced the partial cold-start event handoff with a bounded incident payload. Forecast and Warning notification launches now restore active EEW state, forecast points, timeline offset, P/S-wave rings, countdown, detail card, and explicit camera focus even after process removal; matching live runtime state takes over when available. Notification focus replays after final card layout and refines when detailed JMA geometry loads, so a second user tap is no longer required. The same handoff restores tsunami forecast areas and whole-Japan coastal focus without inventing an earthquake relationship, and reactivates a matching cached-but-inactive bulletin so its coasts flash.
- Added a dedicated affected-coast tsunami camera command shared by live and cold-start notification bulletins. It frames bundled prefecture-coast fallbacks immediately, refines to exact JMA coast geometry when ready, and replays after the alert card settles the viewport without changing the manual whole-country Fit Japan action.
- Added bounded `gd.eew` recovery after every successful live startup/reconnect. It examines only recently completed events, posts only a still-active candidate issued within three minutes, suppresses an event already accepted by the live path, and preserves Forecast-to-Warning escalation without using the archive as a real-time substitute.
- Corrected the `gd.eew` recovery query to use the documented timezone-free, whole-second datetime refinement after the live API rejected Java `Instant` output containing fractional seconds and `Z`. Corrected the diagnostic exception-class label and made meaningful packet/error changes publish an immediate UI snapshot so an open history view does not require a cold start.
- Replaced silent parser drops with explicit rejection reasons; accepted messages, socket activity, recovery results, and notification-policy outcomes are persisted and shown in the Data source dialog.
- Separated transport failures from ignored bulletins and retained exact WebSocket error code/message/close data, close and failure details, true connection time, last activity, and the underlying post-event recovery failure. Added a bounded app-private, credential-redacted history of diagnostically meaningful inbound and outbound DM-D.S.S application packets, excluding routine ping/pong heartbeats so they cannot evict useful traffic, plus a newest-packet preview and explicit schema-versioned JSON export of the complete retained diagnostics through Android's document picker.
- Confirmed foreground monitoring already owns the shared process runtime rather than only P2PQuake, and made service enable/restart explicitly cancel any pending DM-D.S.S retry delay and reconnect the paid forecast socket immediately.
- Advanced the cumulative in-progress metadata through `0.9.84ai` (`versionCode` 214) for missed-EEW recovery and delivery diagnostics, to `0.9.84aj` (`versionCode` 215) for complete bounded packet diagnostics and machine-readable export, to `0.9.84ak` (`versionCode` 216) so routine ping/pong heartbeats no longer consume the retained history, and to `0.9.84al` (`versionCode` 217) for Shindo 0–4 Forecast delivery policy.

## Why it is being done this way

- The P2PQuake provider already owns confirmed-report history, tsunami state, offline replays, diagnostics, and notification behavior. Replacing it wholesale in the first DM-D.S.S slice would needlessly destabilize unrelated features.
- A second P2PQuake connection is forbidden. The DM-D.S.S adapter is therefore an additional paid EEW forecast source, merged at the process-scoped runtime boundary over the existing single FREE baseline.
- Tokens are device secrets even though the OAuth client ID is public. Persist them encrypted with an Android Keystore key; keep PKCE state/verifier short-lived and validate the callback state before exchanging the authorization code.

## Current unfinished state

- The repository implementation is code-complete, but the DM-D.S.S integration remains **pending testing with live EEW events** and is not production-verified. Existing authorizations must use Update authorization once to add `socket.close` and `gd.eew`; cancelling that flow deliberately leaves the working authorization and live forecast access intact, but post-event recovery remains unavailable.
- Sandbox injections and automated tests verify internal routing and policy only. They do not establish that a real DM-D.S.S WebSocket envelope will arrive, parse, notify, survive long-running foreground monitoring, or recover correctly after a real connection interruption.
- The bounded packet trace, live dialog refresh, corrected recovery refinement, exact transport/recovery reasons, and machine-readable JSON export still require device validation with real DM-D.S.S traffic. Packet bodies are retained only for this diagnosis purpose and are exported only through an explicit user action.
- The public OAuth client must have the exact Android redirect URI `cz.misa.quakedeck://oauth/dmdss` registered in the DM-D.S.S control panel before a live device authorization can complete. Repository code cannot verify that account-side setting.

## Important things not to redo or change

- Do not broaden scopes beyond `contract.list`, `socket.start`, `socket.close`, `eew.get.forecast`, and the explicitly approved `gd.eew`, or add socket listing, earthquake reports, warning-only, realtime intensity, or tsunami classifications in this slice.
- Do not add a second P2PQuake provider/socket.
- Do not store or export a client secret, access token, refresh token, socket ticket, authorization header, or account credentials. The explicitly approved bounded DM-D.S.S packet history may retain bulletin bodies in app-private storage and user-requested redacted JSON exports only.
- Do not disturb current station/hierarchy work or redesign existing report, map, notification, archive, Sandbox, or monitoring behavior.

## Exact next steps

1. Confirm the exact callback URI is registered for the supplied public OAuth client.
2. Exercise authorization on a device with an account that has `eew.forecast`, then wait for and document at least one actual live Forecast event; confirm intake diagnostics, detail state, and audible notification delivery.
3. Exercise authorization with an account lacking `eew.forecast`, confirm its other active plans remain visible, and confirm P2PQuake remains the effective EEW fallback without a DM-D.S.S socket attempt.
4. Confirm Warning and Forecast notifications remain independent and default on. Exercise every Forecast floor from Shindo 0 through 4 and each below-floor choice (off, silent, and regular without wake/full-screen); confirm the below-floor row is absent at Shindo 0. Confirm Warning choices remain Shindo 5−/5+/6−/6+/7.
5. Confirm a later Forecast or Warning revision activates attention exactly once when it first crosses its own selected threshold, and that the same active event crossing from Forecast to Warning produces a new Warning notification.
6. Exercise both one-shot Sandbox EEW injectors with the configured delay and confirm each follows its own notification control while active Sandbox mode retains the existing no-wake/no-takeover protection.
7. Swipe QuakeDeck away, then open newly posted Forecast, Warning, earthquake, and tsunami Sandbox notifications. Confirm their detail cards and camera state restore, including EEW rings/countdown and flashing tsunami forecast areas framed around the affected coasts.
8. Update authorization on the live account, reconnect, and confirm the diagnostics show a successful post-event recovery check without duplicating a live event.
9. After real DM-D.S.S activity or a transport failure, confirm the packet preview distinguishes bulletin rejection from transport errors, then export the complete JSON and verify its schema, ordering, raw bulletin preservation, credential redaction, and retention bounds.
10. Validate a real successive-revision sequence, including Forecast-to-Warning escalation if one occurs, and retain the pending status for any path that has not actually occurred live.
11. Remove the pending-live-event status only after the user reviews the observed live evidence, then obtain approval before finalizing or committing the cumulative release.

## Relevant logical changes and Git state

- Branch: `main` in `C:\Users\bjsit\Documents\GitHub\QuakeDeck`.
- The checkout already contains extensive unrelated modified and untracked station/hierarchy work. Preserve it exactly.
- This workstream began on cumulative in-progress version `0.9.84aa` (`versionCode` 206), advanced through `0.9.84ab`, `0.9.84ac`, `0.9.84ad`, `0.9.84ae`, `0.9.84af`, `0.9.84ag`, `0.9.84ah`, `0.9.84ai`, `0.9.84aj`, and `0.9.84ak`, and now targets `0.9.84al` (`versionCode` 217).
- Nothing is staged, committed, pushed, fetched, or published by this workstream.
