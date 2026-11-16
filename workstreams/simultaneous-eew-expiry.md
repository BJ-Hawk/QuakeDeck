# Simultaneous EEWs and screen-off expiry

The November 16 `0.10.4-dev.4` export recorded Noto `20261116132447` due at 13:28:57 JST. Shizuoka `20261116132858` arrived seven seconds later and replaced the single timer. Shizuoka's last bulletin set 13:32:31 as its deadline. QuakeDeck executed the timeout at 13:39:16.206, about 6 minutes 45 seconds late. Both sockets disconnected within 360 milliseconds afterward. The export does not establish the device's sleep state.

In `0.10.4-dev.5` / 254, each official warning has its own deadline, three minutes after its latest accepted bulletin. The timer uses wall and elapsed clocks and a bounded partial wake lock with a short notification grace period. This keeps the screen off and supports CPU execution across ordinary suspend. Android can defer background execution under device power restrictions, so the providers also reconcile due deadlines on foreground and feed activity.

The active collection is limited by incident expiry and has no fixed count limit. Feed selection deduplicates the same incident across DM-D.S.S and P2PQuake. The map presents selectable per-incident badges and independent P/S waves where the local engine is available. Explicit expiry and cancellation carry the affected incident to notification delivery even with a different warning still active.

FULL and LITE compiled and passed all 211 unit tests in each edition. The default FULL configuration was verified again after LITE.

Device checks should cover screen-off expiry from the latest bulletin, a new incident arriving before and just after an earlier deadline, separate badge selection and dismissal in portrait and landscape, and forecast/warning reconciliation when both feeds carry the same ID. Watch the Ended entries for both events and verify the surviving map rings. The source work is local on `main`, based on `1e8e9e7`.

The same build version adds **Sandbox → Built-in scenarios → Overlapping EEWs**. After the five-second arm delay, synthetic Noto starts first and eastern Shizuoka starts 12 seconds later. Their badges and FULL wavefronts overlap for 63 seconds. Noto then cancels, leaving 20 seconds to inspect Shizuoka alone before it cancels. The replay uses the live packet decoder and active-warning collection under the existing muted built-in Sandbox mode.

After adding the replay, FULL compiled and passed all 213 unit tests, including the shared-timeline and independent-cancellation fixture checks. Map rendering still needs a device check.
