# Omit locally calculated EEW forecasting implementation from Git

## Status — implemented locally; build-validated; pending device/live approval

The approved source-publication boundary is implemented in the permanent
checkout. The complete local APK includes the ignored engine; a forced-absent
public-source configuration also compiles, passes unit tests, and packages an
APK. No device or live-event validation has been performed for this workstream.

## Objective

Keep QuakeDeck, its Android app, and its website in the existing single public
GitHub repository while deliberately omitting one Kotlin implementation file
that contains every QuakeDeck-generated live ground-motion forecast
calculation. The permanent development checkout must build the complete APK
when that ignored file is present. A fresh public checkout, where the file is
purposefully absent, must still build successfully through a tracked
"forecasting unavailable" fallback.

The public README must make the omission unmistakable and explain that the
locally calculated forecast implementation is excluded from Git for legal
reasons. This disclosure must identify the omitted filename and clarify that
its absence is deliberate rather than a broken checkout or missing dependency.

## User decisions already made

- Keep exactly one repository containing both the Android app and website.
- Keep that repository public. Do not make it private, split it, replace it
  with a website-only repository, or move Android development to another
  repository.
- Do not delete, abandon, or stop developing the forecasting features.
- Do not rely on withholding an APK: the source itself is the publication
  boundary that this workstream must address.
- Do not seek a licence or provider partnership as the solution implemented by
  this workstream.
- Move **all potentially regulated QuakeDeck-generated forecasting**, not only
  the planned regional-intensity calculation, behind one deliberately omitted
  Kotlin implementation file.
- Keep the complete implementation file in the permanent working checkout but
  exclude it from Git. The full local APK must discover and use it
  automatically.
- The public checkout must compile and package successfully without the file.
- Make the legal omission prominent in `README.md`. The currently omitted
  behavior must explicitly include P-wave and S-wave predictions.
- Do not change application code until the user approves this workstream's
  design and authorizes implementation.

## Agreed forecasting boundary

The single omitted implementation must own every QuakeDeck-generated live
ground-motion forecast calculation, including:

- modelled P-wave and S-wave propagation radii;
- animated P/S wavefront state derived from elapsed time, depth, and assumed
  propagation velocities;
- modelled P-wave and S-wave arrival epochs;
- destination arrival countdowns derived by QuakeDeck;
- any destination intensity calculated by QuakeDeck;
- the planned locally calculated regional-intensity forecast;
- forecasting constants, coefficients, velocity assumptions, and algorithms;
- any EEW lifecycle deadline derived from those modelled calculations.

The tracked public source may retain non-forecasting contracts and plumbing:

- input and output data classes/interfaces needed by callers;
- a loader or provider boundary that does not statically reference the missing
  implementation class;
- a deterministic unavailable/no-result fallback;
- unchanged JMA, DM-D.S.S, and P2PQuake reception, parsing, and presentation;
- official provider-supplied forecast values;
- ordinary time parsing, map geometry, and great-circle distance helpers that
  do not themselves predict ground motion.

Before implementation, audit every caller rather than assuming
`EewWaveModel.kt` is the only forecast path. In particular, inspect map rings,
destination cards, notification/location policy, EEW expiry/lifecycle logic,
cold-start payload restoration, Sandbox/replay behavior, and tests.

## Proposed technical architecture

Use one exact ignored source path, provisionally:

`app/src/main/java/cz/misa/quakedeck/data/LocalEewForecastEngine.kt`

The tracked source will define the stable forecast-engine contract and a
fallback that reports the local engine as unavailable. A tracked loader will
discover the ignored implementation without a compile-time class reference,
so its physical absence cannot break Kotlin compilation. Reflection is the
current preferred mechanism because the Android release build is not minified;
if minification is enabled later, the optional implementation will require an
explicit keep rule.

The ignored file will live inside the normal Android main source set. When it
exists in the permanent checkout, Kotlin compilation will include it and the
loader will instantiate it, producing the complete APK. When it is absent from
a public clone, the same callers receive the unavailable fallback and the APK
still builds. UI and runtime callers must consume nullable/unavailable results
without duplicating forecast calculations outside the omitted file.

The `.gitignore` rule must name the exact implementation file rather than a
broad source directory or wildcard. Validation must prove both that Git ignores
the local implementation and that Git does not track it.

An ignored file has no Git history and is vulnerable to local loss. A backup or
encrypted same-repository storage design is not yet approved. Do not introduce
a second repository, upload the plaintext Kotlin file, or silently add an
encrypted artifact without a separate user decision.

## README disclosure requirement

Place a prominent callout near the beginning of `README.md`, not solely in the
build instructions. It must communicate at least:

> **Intentionally omitted EEW forecasting implementation**
>
> QuakeDeck's locally calculated earthquake-motion forecasting implementation
> is intentionally excluded from Git for legal reasons. This currently
> includes modelled P-wave and S-wave propagation, arrival-time predictions,
> and related countdowns.
>
> A public checkout still builds successfully, but these locally calculated
> features are unavailable without the omitted implementation. Forecasts and
> warnings supplied directly by JMA, DM-D.S.S, or P2PQuake remain supported.
> The omission is deliberate and is not a missing dependency or repository
> error.

The final text must name the actual omitted file after implementation. Do not
state that QuakeDeck has been adjudged illegal; describe the omission as a
precaution taken for legal reasons.

## Work completed

- Recorded the single-public-repository constraint and rejected approaches.
- Inspected the current tracked `EewWaveModel.kt` boundary. It currently owns
  P/S velocity constants, wavefront radii, destination arrival calculations,
  official-area matching, warning-end estimation, time parsing, geodesic-circle
  generation, and great-circle distance.
- Confirmed current callers include the main map/detail UI,
  `AlertLocationPolicy`, and P2PQuake warning lifecycle handling. This is only
  the starting inventory; a complete migration audit remains required.
- Confirmed the planned locally calculated regional-intensity engine has not
  yet been implemented in the current tracked source.
- Agreed that the public build must succeed without the omitted implementation
  and the permanent checkout must build the complete APK with it.
- Agreed on the prominent README disclosure requirement.
- Created this coordination-only workstream without changing app version or
  `CHANGELOG.md`.
- Completed the read-only caller audit before implementation. The production
  coupling was limited to the destination card, two map wavefront paths,
  P2PQuake warning cleanup, shared distance geometry, provider snapshot merging,
  and notification cold-start restoration.
- Added the exact `.gitignore` rule before creating
  `app/src/main/java/cz/misa/quakedeck/data/LocalEewForecastEngine.kt`. Git
  confirms that the file is ignored and not tracked.
- Moved P/S velocities, depth-adjusted wavefront radii, modelled destination
  arrivals/countdowns, and forecast-derived warning-end timing into that single
  ignored file. No matching calculation constants or helpers remain tracked.
- Replaced direct access with the tracked `LocalEewForecastProvider` contract,
  reflection loader, and distinct available/no-result/unavailable results. The
  loader has no static implementation-class reference and caches the result.
- Kept ordinary time parsing, official-area matching, great-circle distance,
  and geodesic-circle drawing tracked. `AlertLocationPolicy` therefore remains
  independent of the optional forecasting engine.
- Added a non-predictive 180-second P2PQuake safety timeout for builds without
  the local engine. Its wording does not claim estimated wave passage.
- Added `activeEewUntilMillis` to provider/combined snapshots and notification
  routing. Both P2PQuake and DM-D.S.S now carry their selected deadline into the
  existing notification payload expiry guard, preventing indefinite cold-start
  restoration.
- Updated the destination UI and map rendering so missing local forecasting
  suppresses rings/countdowns, retains official provider data, and shows a
  localized English/Czech/Japanese explanation rather than appearing broken.
- Added a build property that excludes the local implementation without moving
  or deleting it, plus focused loader/result/lifecycle/routing tests.
- Added the prominent README disclosure naming the exact omitted file and
  qualified the P/S-wave/countdown capability descriptions.
- Bumped the in-progress local version from `0.10.1-dev.1` through
  `0.10.1-dev.1a` to `0.10.1-dev.2`, retained versionCode 221, and extended the
  single Unreleased changelog entry.
- Added an always-visible `FULL`/`LITE` badge beside the version in the fixed
  status strip and beside the app name in the expanded status drawer. The
  edition requires both a generated marker confirming that the optional source
  belongs to this compilation and a loader that can instantiate it.
- Fixed the incremental-build failure discovered during device checking: the
  previous Gradle exclusion was not a Kotlin task input, so switching editions
  without cleaning could leave the compile task up to date and preserve a
  stale engine class. A generated build-info source now records whether the
  optional source is present for that compilation, gates reflection before any
  stale class can load, and explicitly invalidates Kotlin compilation when the
  edition changes. The status chrome no longer remembers an earlier edition.
- Adjusted the expanded drawer header after the first device screenshots: the
  identity column now reserves slightly more width and the badge uses tighter
  spacing so the final `L` in both `FULL` and `LITE` is not clipped.
- Replaced the confusing provider-valued developer toggle with a plain
  `omitLocalEewForecastEngine` Boolean. Changing that single value to `true`
  now safely forces `LITE`; an identically named Gradle property remains the
  higher-priority override for automated validation.

## Why it is being done this way

The repository must remain public and must continue to contain both the Android
app and website. Withholding APK artifacts would not withhold source, while
deleting the forecasting implementation would prevent continued development.
An optional ignored implementation preserves one permanent checkout and the
complete local build while creating a precise source-publication boundary.

A compile-time reference to the missing class would make the public checkout
fail. A narrow tracked contract plus runtime discovery allows both the full
checkout and the intentionally incomplete public checkout to compile from the
same tracked sources without copying or branching the project.

## Current unfinished state

Implementation and automated build validation are complete. Device screenshots
confirm that actual full and source-absent builds select the green `FULL` and
amber `LITE` states respectively. Broader Sandbox and live-event behavior still
needs IRL testing, so the workstream must not yet be described as production-
verified. The ignored plaintext file also has no separately approved backup
location.

Current commits already contain the existing P/S model. Removing it from the
current tracked tree will prevent future versions from publishing it at the
repository tip, but it will remain readable in earlier Git history unless that
history is rewritten. History rewriting is destructive, affects existing
clones, tags, and commit identities, and cannot erase copies or forks already
made. It is not authorized by this workstream and must remain a separate,
explicit user decision.

## Important things not to redo or change

- Do not propose a private repository, second repository, website-only
  repository, local-only repository, fork, clone, branch, or separate project.
- Do not delete or abandon the forecasting features.
- Do not describe withholding APK releases as a solution to public source.
- Do not replace the complete app with a permanently reduced feature set. The
  full permanent checkout must retain and build the complete functionality.
- Do not scatter forecast logic across multiple ignored files. The agreed
  publication boundary is one deliberately omitted Kotlin implementation file.
- Do not place provider parsing, OAuth credentials, sockets, official forecast
  values, P2PQuake fallback behavior, or unrelated map geometry inside the
  omitted file merely because they participate in EEW presentation.
- Do not add new locally calculated forecasting outside the omitted engine.
- Do not upload the plaintext omitted file, stage it forcibly, or weaken its
  exact ignore rule.
- Do not rewrite Git history, delete tags/releases, force-push, stage, commit,
  push, or publish without explicit user authorization.
- Do not touch unrelated station, reporting-unit, observed-intensity, map, or
  DM-D.S.S diagnostic work.
- Preserve all unrelated modified and untracked files in the permanent
  checkout.

## Mandatory cross-machine transfer reminder

`LocalEewForecastEngine.kt` is ignored by Git and therefore **will never be
transferred by pull, push, commit, or any other repository synchronization**.
If any work changes this file—even a one-line fix—the active workstream must
explicitly remind the user to manually copy the updated plaintext file to the
same project-relative path in the other permanent QuakeDeck checkout:

`app/src/main/java/cz/misa/quakedeck/data/LocalEewForecastEngine.kt`

Do not describe the other machine as synchronized or its `FULL` build as
current until the user confirms that manual transfer. If the tracked forecast
contract changes at the same time, remind the user that the transferred engine
must be the matching version and should be compile-checked on the other machine.

## Exact next steps

1. Run the built-in EEW and combined EEW/tsunami replays on a device with the
   complete local APK. Confirm P/S rings, destination countdowns, automatic map
   framing, expiry, rotation, and notification restoration remain correct.
2. Install or otherwise exercise a forced-absent build and confirm official
   areas/intensities remain visible, rings/countdowns are absent, the localized
   explanation appears, and the P2P safety timeout ends without resurrection.
3. Recheck the next suitable live P2PQuake warning and DM-D.S.S Forecast event.
   The DM-D.S.S exact event-time-plus-180-second behavior must remain unchanged.
4. Decide separately whether and where to back up the ignored plaintext file.
5. Keep Git-history rewriting separate unless the user explicitly authorizes
   it. Earlier commits still expose the former tracked implementation.
6. After user approval, finalize or continue the shared Unreleased hotfix group
   according to the then-current repository state. Do not stage, commit, push,
   fetch, or publish merely because the automated checks passed.

## Relevant logical changes and Git state

- Repository: `BJ-Hawk/QuakeDeck`; permanent checkout
  `C:\Users\bjsit\Documents\GitHub\QuakeDeck`; branch `main`.
- Current HEAD at workstream creation: `0a6cc35` (`0.10.1-dev.1: separate
  overlapping live products and clarify diagnostics`).
- Current app metadata at workstream creation: `0.10.1-dev.1`, versionCode 221.
- Existing unrelated modified files include `WORKSTREAMS.md`,
  `outputs/station-name-audit/station_metadata_sources.json`, and
  `workstreams/differentiate-city-reporting-units.md`; unrelated untracked
  station-research scripts and generated caches also exist. Preserve them.
- Approved implementation metadata is now `0.10.1-dev.2`, versionCode 221,
  with one compound addition under `Unreleased — target v0.10.1 (in progress)`.
- Full local validation: clean debug APK packaged, Kotlin compiled, and all 81
  unit tests passed with zero failures/errors/skips. The runtime classes jar
  contains `LocalEewForecastEngine.class`.
- Forced-absent validation: clean Kotlin compilation, unit tests, and debug APK
  packaging passed with `omitLocalEewForecastEngine=true`; the runtime classes
  jar contains the tracked contract/loader but not
  `LocalEewForecastEngine.class`.
- `0.10.1-dev.2` edition-badge validation repeated both clean configurations:
  all 81 unit tests and debug APK packaging passed in each. The forced-absent
  runtime jar contained zero engine classes and therefore reports `LITE`; the
  restored final local runtime jar contained one engine class and reports
  `FULL`. The final debug APK metadata is versionName `0.10.1-dev.2`,
  versionCode 221. On-device visual placement remains pending.
- Follow-up compile-only validation reproduced the stale `FULL` failure with a
  no-clean edition switch, then verified the fix without producing another
  APK. The same no-clean switch now executes Kotlin compilation, generates a
  `LITE` marker, and leaves zero compiled engine classes. Both forced-`LITE`
  and restored-`FULL` unit-test runs passed all 81 tests; the final generated
  compilation marker is `FULL`. APK assembly was explicitly skipped for this
  follow-up at the user's request.
- Device screenshots then confirmed both edition colors and exposed only a
  clipped final `L` in the larger drawer badge. The drawer identity share grew
  from 42% to 44%, its clock share narrowed from 58% to 56%, and non-compact
  badge spacing was tightened. With the engine physically absent, the latest
  compile-only check generated `engineIncluded = false` and found zero compiled
  engine classes. No APK task was run for this adjustment.
- Nothing was staged, committed, pushed, fetched, branched, cloned, or
  published. Existing unrelated station/research changes remain untouched.
