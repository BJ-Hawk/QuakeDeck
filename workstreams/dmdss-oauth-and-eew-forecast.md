# DM-D.S.S OAuth and EEW forecast

## Status — pending testing with live EEW events

**This integration is not yet live-event validated or production-verified.** The repository implementation, Sandbox exercises, compilation, and automated tests are complete, but those checks cannot prove real DM-D.S.S delivery. Validation remains pending against actual live EEW Forecast events, including notification delivery, successive revisions, Forecast-to-Warning escalation, cancellation/final bulletins, socket interruption and foreground-service reconnection, and bounded `gd.eew` recovery after a genuinely missed live bulletin.

Until that validation is observed on a real authorized device/account, documentation must describe the DM-D.S.S integration as **pending live-event testing**, not complete or proven reliable. P2PQuake remains the permanent baseline and fallback throughout testing.

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
- Give Warning and paid Forecast notifications completely separate delivery and attention controls. Both are audible and enabled by default; Forecast offers Shindo 3 or 4, while Warning offers Shindo 5− through 7.

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
- Split notification policy completely by EEW level. Warning and Forecast each have their own default-on delivery switch, audible Android notification channel, wake/full-screen mode, predicted-intensity threshold, notification identity, and attention identity while sharing only the reference location and Android's system-level full-screen permission. Forecast exposes only Shindo 3 and 4; Warning exposes Shindo 5−, 5+, 6−, 6+, and 7. Attention activates on the first revision that actually crosses the respective threshold. When an active event escalates from Forecast to Warning, the Forecast card is removed and a distinct Warning notification is posted on the Warning channel so it alerts again.
- Added a one-shot Sandbox EEW forecast beside the existing warning injector. It uses the same configurable delay and normal runtime/notification route, does not replace the active live or official Sandbox connection, and verifies the audible Forecast policy without touching `P2pQuakeProvider`. Active Sandbox mode retains the existing protection against waking or taking over the device.
- Replaced the partial cold-start event handoff with a bounded incident payload. Forecast and Warning notification launches now restore active EEW state, forecast points, timeline offset, P/S-wave rings, countdown, detail card, and explicit camera focus even after process removal; matching live runtime state takes over when available. Notification focus replays after final card layout and refines when detailed JMA geometry loads, so a second user tap is no longer required. The same handoff restores tsunami forecast areas and whole-Japan coastal focus without inventing an earthquake relationship, and reactivates a matching cached-but-inactive bulletin so its coasts flash.
- Added a dedicated affected-coast tsunami camera command shared by live and cold-start notification bulletins. It frames bundled prefecture-coast fallbacks immediately, refines to exact JMA coast geometry when ready, and replays after the alert card settles the viewport without changing the manual whole-country Fit Japan action.
- Added bounded `gd.eew` recovery after every successful live startup/reconnect. It examines only recently completed events, posts only a still-active candidate issued within three minutes, suppresses an event already accepted by the live path, and preserves Forecast-to-Warning escalation without using the archive as a real-time substitute.
- Replaced silent parser drops with explicit rejection reasons; accepted messages, socket activity, recovery results, and notification-policy outcomes are persisted without tokens or bulletin bodies and shown in the Data source dialog.
- Confirmed foreground monitoring already owns the shared process runtime rather than only P2PQuake, and made service enable/restart explicitly cancel any pending DM-D.S.S retry delay and reconnect the paid forecast socket immediately.
- Advanced the cumulative in-progress metadata again to `0.9.84ai` (`versionCode` 214) for missed-EEW recovery and delivery diagnostics.

## Why it is being done this way

- The P2PQuake provider already owns confirmed-report history, tsunami state, offline replays, diagnostics, and notification behavior. Replacing it wholesale in the first DM-D.S.S slice would needlessly destabilize unrelated features.
- A second P2PQuake connection is forbidden. The DM-D.S.S adapter is therefore an additional paid EEW forecast source, merged at the process-scoped runtime boundary over the existing single FREE baseline.
- Tokens are device secrets even though the OAuth client ID is public. Persist them encrypted with an Android Keystore key; keep PKCE state/verifier short-lived and validate the callback state before exchanging the authorization code.

## Current unfinished state

- The repository implementation is code-complete, but the DM-D.S.S integration remains **pending testing with live EEW events** and is not production-verified. Existing authorizations must use Update authorization once to add `socket.close` and `gd.eew`; cancelling that flow deliberately leaves the working authorization and live forecast access intact, but post-event recovery remains unavailable.
- Sandbox injections and automated tests verify internal routing and policy only. They do not establish that a real DM-D.S.S WebSocket envelope will arrive, parse, notify, survive long-running foreground monitoring, or recover correctly after a real connection interruption.
- The public OAuth client must have the exact Android redirect URI `cz.misa.quakedeck://oauth/dmdss` registered in the DM-D.S.S control panel before a live device authorization can complete. Repository code cannot verify that account-side setting.

## Important things not to redo or change

- Do not broaden scopes beyond `contract.list`, `socket.start`, `socket.close`, `eew.get.forecast`, and the explicitly approved `gd.eew`, or add socket listing, earthquake reports, warning-only, realtime intensity, or tsunami classifications in this slice.
- Do not add a second P2PQuake provider/socket.
- Do not store a client secret, access token, refresh token, socket ticket, or account data in source files, logs, exported reports, or unencrypted preferences.
- Do not disturb current station/hierarchy work or redesign existing report, map, notification, archive, Sandbox, or monitoring behavior.

## Exact next steps

1. Confirm the exact callback URI is registered for the supplied public OAuth client.
2. Exercise authorization on a device with an account that has `eew.forecast`, then wait for and document at least one actual live Forecast event; confirm intake diagnostics, detail state, and audible notification delivery.
3. Exercise authorization with an account lacking `eew.forecast`, confirm its other active plans remain visible, and confirm P2PQuake remains the effective EEW fallback without a DM-D.S.S socket attempt.
4. Confirm Warning and Forecast notifications are independently audible and default on, with Forecast choices limited to Shindo 3/4 and Warning choices covering Shindo 5−/5+/6−/6+/7.
5. Confirm a later Forecast or Warning revision activates attention exactly once when it first crosses its own selected threshold, and that the same active event crossing from Forecast to Warning produces a new Warning notification.
6. Exercise both one-shot Sandbox EEW injectors with the configured delay and confirm each follows its own notification control while active Sandbox mode retains the existing no-wake/no-takeover protection.
7. Swipe QuakeDeck away, then open newly posted Forecast, Warning, earthquake, and tsunami Sandbox notifications. Confirm their detail cards and camera state restore, including EEW rings/countdown and flashing tsunami forecast areas framed around the affected coasts.
8. Update authorization on the live account, reconnect, and confirm the diagnostics show a successful post-event recovery check without duplicating a live event.
9. Validate a real successive-revision sequence, including Forecast-to-Warning escalation if one occurs, and retain the pending status for any path that has not actually occurred live.
10. Remove the pending-live-event status only after the user reviews the observed live evidence, then obtain approval before finalizing or committing the cumulative release.

## Relevant logical changes and Git state

- Branch: `main` in `C:\Users\bjsit\Documents\GitHub\QuakeDeck`.
- The checkout already contains extensive unrelated modified and untracked station/hierarchy work. Preserve it exactly.
- This workstream began on cumulative in-progress version `0.9.84aa` (`versionCode` 206), advanced through `0.9.84ab`, `0.9.84ac`, `0.9.84ad`, `0.9.84ae`, `0.9.84af`, `0.9.84ag`, and `0.9.84ah`, and now targets `0.9.84ai` (`versionCode` 214).
- Nothing is staged, committed, pushed, fetched, or published by this workstream.
