# Historical event loading

Implemented locally in `0.10.3-dev.6` / 244. FULL and LITE compilation and all
149 unit tests pass (20 suites; zero failures/errors/skips). Host SQLite checks
pass. Actual handset opening times and Android database integration remain
unmeasured. No APK, staging, commit, push or fetch was performed.

## Cause and change

Opening History previously loaded every confirmed report's complete JSON,
resolved and merged its station observations, decoded all associated EEW/felt/
tsunami records, and repeatedly searched unrelated incidents for associations.
The same single archive worker also performed synchronous HTTP backfill and
rate-limit waits, so a browse/replay request could queue behind a download.

- Archive schema 3 adds a disposable `catalog_json` projection for codes 551 and
  556. New writes store it alongside the complete original payload. Existing
  rows convert once in batches of 64, with a partial index tracking unfinished
  rows. Completed batches survive interruption; subsequent opens read compact
  JSON without decoding archived station/region arrays. The first conversion
  still reads each eligible legacy payload once, so its duration depends on
  archive size and device storage.
- List rows retain report identity/count/order, hypocentre, magnitude, maximum
  intensity, corrections and EEW level/terminal state. Legacy public EEW maximum
  intensity is calculated before omitting its areas. Felt association and full
  observed/forecast detail load only when an incident is selected.
- Confirmed-to-EEW catalogue association groups confirmed origins by minute,
  retaining the existing minute-only comparison and hypocentre gate. Replay
  ordering computes each source timestamp once before sorting.
- A separate reader executor handles catalogue and selected-incident requests.
  SQLite WAL allows reads alongside short archive write transactions. Readers
  no longer queue behind backfill HTTP calls or its rate-limit pause.
- Selected confirmed incidents query associated reports with separate indexed
  ranges for dashed and slashed timestamps. Lexical prefix bounds avoid mixing
  timestamp formats at year boundaries. EEW-only incidents retain their exact
  event-key query. Complete source JSON and replay/forecast behavior are retained.
- The UI coalesces ordinary catalogue requests and rejects outdated callbacks
  after retry or archive clearing, including pending explicit incident selection.

## Validation

Seven catalogue regressions cover intact original station payloads, more than
100-fold projection-size reduction for a synthetic 4,360-station report,
source-neutral Forecast and legacy Warning summaries, bare cancellation with
prior state, invalid origins, and association parity against the previous
all-pairs search using 10,000 confirmed incidents and 80 EEW revisions.
Existing exact-incident and replay regressions remain included in the full suite.
The size reduction is a fixture result, not a handset loading-time measurement.

Run locally with the installed Android Studio JDK:

```powershell
.\gradlew.bat --offline --no-configuration-cache :app:compileDebugKotlin :app:testDebugUnitTest
.\gradlew.bat --offline --no-configuration-cache -PomitLocalEewForecastEngine=true :app:compileDebugKotlin :app:testDebugUnitTest
python tools/test_historical_catalog_sql.py
```

The SQLite check reads the production table/migration/index/day-selection SQL
and tests a disposable host database: v2 upgrade and fresh v3 creation,
interrupted/resumed conversion, all original fields across 1,000 rows, semantic
deduplication, clearing, both timestamp formats, leap/month/year boundaries,
and range-index query plans. It does not exercise Android's SQLite wrapper/WAL.

Pending device checks: compare first conversion, later cold opens and repeated
opens on the user's actual archive; browse during a backfill; select confirmed
and EEW-only incidents and compare their replay frames; clear/retry while loading.

The ignored forecast engine and both private resource files are unchanged.
FULL compilation is restored after LITE validation. The separate general audit's
statistics-scan and main-handler backfill findings are not claimed resolved.
Existing unrelated work, including concurrent station research, is preserved.
