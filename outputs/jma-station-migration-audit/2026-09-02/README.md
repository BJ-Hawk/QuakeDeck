# Official JMA station migration audit — 2026-09-02

## Dry-run result: PASS

The candidate was built solely from files downloaded directly from JMA. The
third-party bundle was read only as the comparison baseline. All **4,360 rows
and all nine station fields (39,240 comparisons)** match, without normalizing
Japanese names, rounding coordinates, inferring codes, or guessing operators.
There are no additions, removals, duplicate names/codes, or coordinate/type
changes. The source record order also remains unchanged.

| Checked field | Exact matches | Differences |
| --- | ---: | ---: |
| Station code | 4,360 | 0 |
| Japanese station name | 4,360 | 0 |
| Prefecture | 4,360 | 0 |
| Latitude (published numeric value) | 4,360 | 0 |
| Longitude (published numeric value) | 4,360 | 0 |
| Operator | 4,360 | 0 |
| JMA reporting-area code | 4,360 | 0 |
| Japanese reporting-area name | 4,360 | 0 |
| Municipality/ward code | 4,360 | 0 |

Coverage is 47 prefectures, 188 reporting areas and 1,894 municipality parents:
670 JMA, 800 NIED and 2,890 local-government stations. All 4,360 research records
match the same nine fields, and all 4,360 approved English-name keys survive.
The existing research workbook is not read, regenerated, or changed.

## Sources and exactness boundary

1. [JMA station map](https://www.jma.go.jp/jma/kishou/know/jishin/intens-st/index.html)
   and its [station JSON](https://www.jma.go.jp/jma/kishou/know/jishin/intens-st/stations.json)
   define the complete 4,360-row coordinate/operator inventory. The retained
   `map.js` supplies the prefecture and operator labels.
2. [JMA XML technical materials](https://xml.kishou.go.jp/tec_material.html),
   [2026-08-26 code-table archive](https://xml.kishou.go.jp/jmaxml_20260826_Code.zip),
   `地震火山関連コード表.xlsx`, worksheet `24`, supplies station IDs and the
   reporting-area/municipality hierarchy. Every map name joins to exactly one
   code-table record by its exact Japanese name.

The code table has **4,361 records**, one more than the map. Its row 1467 is
`1336172` / `伊豆大島町岡田`, area `355` / `伊豆大島`, municipality `1336100`.
It has no row in the official station map and is not in the existing bundle.
It is explicitly excluded from this map-catalogue replacement; no coordinates
or operator are invented. The official
[JMA observation history](https://www.data.jma.go.jp/eqev/data/kyoshin/jma-shindo.html)
contains a same-named site with observation dates 2014-08-04 to 2015-01-21, but
does not publish this seven-digit code. That name-only evidence is **not** used
to declare code 1336172 retired or to manufacture a current station record.

“Exact” means exact to the published JMA map values and official code-table
fields as retrieved on the audit date. It does not claim survey-grade physical
coordinates, a full historical-station inventory, or independently established
operational status. The public map JSON does not declare a version/effective
date; retrieval timestamps and SHA-256 hashes identify the audited snapshot.

## Reuse and attribution

JMA's [published terms](https://www.jma.go.jp/jma/kishou/info/coment.html) adopt
[Public Data License 1.0](https://www.digital.go.jp/resources/open_data/public_data_license_v1.0)
unless an item specifies other rights. The credits name JMA, link the actual
source pages and terms, and identify QuakeDeck as the processor. No JMA marks
are used and the derived catalogue is not presented as an unmodified JMA product.
The operator field preserves the JMA, local-government and NIED identities.

Credit: Source: Japan Meteorological Agency website. Station map data and
official XML code tables processed into the QuakeDeck catalogue by QuakeDeck.

Japanese credit: 出典：気象庁ホームページ「震度観測点」及び「気象庁防災情報XMLフォーマット 技術資料」。QuakeDeckが加工して作成。

## Evidence and reproduction

- `sources/`: original downloaded bytes, including the official source pages,
  code-table archive, terms and observation-history context.
- `dry-run/audit.json`: original baseline fingerprint, every source URL/hash,
  field totals, dependency checks, the explicit code-only exception and candidate
  fingerprint. The dry run wrote no production resource.
- `dry-run/row-evidence.json`: every candidate field with its exact map name and
  code-table worksheet row.
- `dry-run/candidate.json`: the independent official-source replacement.

Original bundled file SHA-256:
`10090e2a258365c6f6e9fb0e5009d6443d9ae61948809b07c1b812d6310180ab`.
Both original and candidate canonical station-content SHA-256:
`038c30ca1d5ebc7ce21fc7079e03e66b3f142db52ab8eef461e3f57b5799fa65`.

From the repository root, run with Python 3.10+ and a new empty output directory:

```powershell
python tools/station_names/audit_official_jma_catalog.py --sources outputs/jma-station-migration-audit/2026-09-02/sources --output tmp/jma-station-audit-check
```

The tool uses only the Python standard library and has no network or apply mode.
It rejects duplicate/ambiguous joins, malformed IDs, unknown operator mappings,
unmapped map entries, invalid coordinates and unexpected spreadsheet content.
After migration, the command audits against the installed official-source
bundle; supply `--baseline` with an independently retained original file to
repeat the original comparison. The original dry-run evidence remains intact.

## Migration implementation

Completed locally after the original dry run passed:

- Installed the independently generated candidate into
  `app/src/main/res/raw/jma_intensity_stations.json`. Ordered station arrays,
  including JSON value types, are identical to the previous bundle. Only the
  catalogue provenance changed.
- Updated only the top-level `catalog` object in
  `outputs/station-name-audit/station_metadata_sources.json`. Verified all other
  content against the pre-migration local Git version. The naming builder now
  preserves the complete official provenance when it is next run.
- Removed the third-party URL, remote parser and background refresh. Startup
  always reads the audited bundle and attempts to delete the obsolete private
  cache without ever reading it. Catalogue updates now require an offline audit.
- Added source/processing/PDL1.0 credits at the bottom of the existing Data
  source dialog in English, Czech and Japanese, with direct JMA source and terms
  links. Updated the repository data notes and website credits.
- Advanced local hotfix metadata to `0.10.1a (in progress)` / versionCode `234`;
  retained previous changelog history and appended to the cumulative commit
  message. No staging, commit, push, website publication or APK build.

## Validation after migration

- `post-migration/audit.json`: PASS, the same 39,240 matching fields, zero data
  differences and zero orphan research/English-name keys. Candidate contents
  are identical to the installed resource; compact JSON serialization is the
  only difference from the pretty-printed candidate file.
- Kotlin compilation and 26 focused unit tests passed (20 core-policy,
  four observed-intensity hierarchy, two official-catalogue tests); zero
  failures/errors. The catalogue test parses all 4,360 records through the
  Android parser, compares every field against retained research metadata,
  checks approved-name coverage and all operator/parent counts, and rejects a
  bundle carrying legacy third-party provenance.
- The naming builder's JavaScript syntax check and `git diff --check` passed.
- Source inspection confirms no remaining third-party station URL/reference
  in production app code/data, the live naming builder, README, data notes or
  website. Historical source identity remains in the audit evidence only.
- Approved English names, placement/address/facility records, the research
  workbook, ground-resource coordinates and the ignored forecast engine were
  not changed. Device startup/cache cleanup and visual layout were not tested
  on a handset; no APK was assembled or installed.

Validation command:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests cz.misa.quakedeck.data.OfficialStationCatalogTest --tests cz.misa.quakedeck.data.CorePolicyTest --tests cz.misa.quakedeck.data.ObservedIntensityHierarchyTest
```
