# Silent DM-D.S.S test drills

## Objective and current state

Receive the scheduled 5 November 2026 DM-D.S.S EEW exercise through QuakeDeck's visual alert flow, with clear drill labels and silent notifications. Scheduled delivery is approximately 10:00 JST / 02:00 Europe/Prague.

Implemented locally as `0.10.3-dev.7` / `versionCode` 245. The user explicitly requested a numeric version bump on 4 November 2026. The cumulative `CHANGELOG.md` group remains **in progress**. Build, unit-test, device and live-drill validation are pending; implementation is not final approval of the entire hotfix group.

## User decisions

- Use the existing permanent Android Studio project at `C:\Users\bjsit\Documents\GitHub\QuakeDeck`; do not create another copy, branch, fork or PR.
- Do not access or change GitHub for this workstream. Direct edits to this existing local project were explicitly authorized because native Android Studio control is unavailable in this chat.
- The user declined direct build/test execution. Leave builds, tests, run and installation to Android Studio. Do not run command-line builds or tests.
- Add the simple setting **Receive silent test drills**, off by default, with English, Czech and Japanese text.
- Follow the current QuakeDeck Development Rules, including workstream continuity and one cumulative current-hotfix entry. The explicit numeric-bump instruction governs the version correction for this task.

## Completed changes and reasons

- `AppSettings.kt`, `MainActivity.kt` and `ui/settings/SettingsScreen.kt` persist the opt-in under Data & connection and refresh the existing DM-D.S.S socket subscription immediately.
- `DmDssProvider.kt` requests `test: including` only when enabled. `DmDssEewParser.kt` accepts known exercise/test statuses (`訓練`, `試験`) or the envelope test marker only with opt-in, retains normal validation, and adds an explicit `isDrill` flag with a separate `drill:dmdss:` incident ID.
- `Models.kt`, `EewLifecyclePolicy.kt` and `QuakeDeckRuntime.kt` preserve drill identity, prevent drill cancellations crossing into real incidents, give real EEWs priority, and prevent real official/felt reports being attached to a fictional exercise.
- `NotificationCoordinator.kt` uses a dedicated silent drill channel and the existing visual notification builder. The drill opt-in deliberately permits nationwide visual notifications during quiet hours and below real-alert thresholds, while retaining the master notification switch and Android permission. Drills never request sound, vibration, screen wake or full screen. Opt-out clears current and Android-retained drill notifications.
- `NotificationEventPayload.kt` retains the drill marker on notification reopening, prevents an old drill replacing an active real EEW, and keeps the existing expiry deadline. The UI rejects drill restoration after opt-out.
- Map badges, active banners, detail cards, destination predictions, runtime status, and phone/watch notification text label exercises as DRILL / CVIČENÍ / 訓練. `UiLocalization.kt`, `NotificationWatchText.kt`, localized resources and decision diagnostics retain that identity.
- Exercises remain in raw diagnostics but are excluded from earthquake History. Existing real-alert settings and unrelated work are preserved.
- Added six integration/regression cases in `app/src/test/java/cz/misa/quakedeck/data/SilentTestDrillFlowTest.kt` and one phone/watch labelling case in `app/src/test/java/cz/misa/quakedeck/notifications/NotificationWatchTextTest.kt`. These have **not** been run.
- Version metadata, README, the current changelog entry and the existing local commit-message draft consistently name `0.10.3-dev.7`. Updating that draft does not stage or commit anything.

## Unfinished validation and exact next steps

1. In Android Studio, sync/build the existing project and run the new drill checks plus the existing parser, notification-payload, delivery-routing and lifecycle regressions. No successful compilation/test result is claimed for the drill changes.
2. Run/install the updated app from Android Studio. Select the authorized DM-D.S.S source, allow notifications, enable the app's master notifications and **Receive silent test drills**. Use the existing foreground-monitoring option when background reception is required.
3. Verify opt-out rejects exercise packets; opt-in produces clearly labelled, silent notification revisions and map/detail/destination presentation. Check phone/watch text, notification reopening after process loss, expiry/cancellation and opt-out cleanup.
4. Verify real alerts take priority and neither source's cancellation or official/felt enrichment crosses between real and drill identities. Confirm no exercise appears in earthquake History.
5. Record actual device and live-drill evidence. Keep the cumulative hotfix group in progress until the user explicitly approves it. Do not commit/push or finalize on the basis of source inspection alone.

## Do not redo or change

- Do not redesign existing real-alert handling, notifications, map controls or the separate history/October 16 fixes merely to continue this workstream.
- Do not turn the existing P2PQuake sandbox on for this exercise; this opt-in is for DM-D.S.S provider exercise messages.
- Do not enable audible alerts, wake/full-screen attention or earthquake-history storage for drills.
- Do not overwrite concurrent changes in shared files; re-read this continuation and the actual owning source first.
- The ignored `LocalEewForecastEngine.kt` and both private resource inputs were not read or changed by this implementation. This workstream introduces no private-engine transfer requirement.

## Local Git and coordination state

Read-only local metadata on 4 November 2026 identifies branch `main` at HEAD `e544dd7bd2efd8249d9ff978b3f3a77fb3b2393f`. No GitHub calls, fetch/pull, Git mutations, staging, commits or pushes were performed for this workstream. Other existing local changes remain present; their staging state was not inspected through Android Studio.

The index is `WORKSTREAMS.md`. Detailed continuity for other unfinished work remains in its own files. Historical validation recorded under earlier versions is not evidence that this new drill change compiles or passes tests.
