# Differentiate city reporting units

## Objective

Give English users distinct, useful station/locality names instead of repeated
municipality-only labels such as `Yatsushiro City`, while preserving the full
Japanese station names and keeping every English value attributable to a source.
The eventual naming rule must be shared by report rows and deep-zoom map labels.

## User decisions already made

- Japanese station names stay unchanged.
- Prefer a confidently matched official DAABR English locality name.
- If DAABR has no English name or the DAABR/GSI match remains unresolved, a
  manually verified Google Maps English locality label is an acceptable fallback.
- Mark Google-derived values with separate provenance such as
  `Google Maps matched`; never describe them as official DAABR names.
- If Google Maps is ambiguous or conflicts with stronger evidence, retain the
  app's current city-level English name rather than guessing.
- Record the Maps URL, check date, displayed Japanese and English locality, and
  a short verification note for each Maps fallback.
- The future station-details UI may disclose: `English locality name based on
  Google Maps.`
- Research/table preparation and app implementation are separate approval steps.
- A municipality-level English name is acceptable when the full current bundled
  station catalogue contains exactly one reporting station in that municipality.
  The check must include already approved/mapped stations, not only unresolved
  rows.
- The user approved municipality-level names for the 13 singleton cases listed
  below. Do not spend further time searching for neighbourhood names for them.
- Prefer a verified official facility identity over a street/locality label when
  it more clearly identifies the physical reporting station for non-Japanese
  users.
- Maintain detailed provenance for every bundled station so a future details UI
  can show an exact published address, facility name, provider-station identity,
  coordinates, and evidence links where known. A blank exact address means
  unknown; never manufacture one from the catalogue's rounded coordinates.

## Completed

- Confirmed the original issue: the source preserves full Japanese identities,
  but the English/Czech place-name path reduces station labels to the translated
  municipality prefix.
- Audited 563 ambiguous stations from
  `app/src/main/res/raw/jma_intensity_stations.json` against the user-supplied
  DAABR town/aza master at `tools/source/mt_town_all.csv`.
- Used official JMA/NIED metadata where available and GSI reverse geocoding for
  coordinate-based candidate matching.
- Generated `outputs/station-name-audit/ambiguous_station_name_audit.xlsx` with
  `Summary`, `Proposed Mapping`, `DAABR Candidates`, `Needs Research`, and
  `Station Sources` sheets. The final sheet contains one provenance row for
  every bundled station. Recorded workbook SHA-256:
  `55054D49123B3DF117C498759EC1FD01BAB3AC0DAA5707C5B7AC2BF5C6F64B09`.
- Generated machine-readable
  `outputs/station-name-audit/station_metadata_sources.json` from the same
  repeatable builder. It contains 4,360 unique station codes, 673 sourced
  addresses, 790 precise NIED provider-coordinate matches out of 800 NIED
  catalogue stations, and 2,898 catalogue-only records. The ten unmatched NIED
  records are explicitly counted; catalogue-only coordinates are not treated
  as exact addresses. Recorded JSON SHA-256:
  `1ADEF220938B106479CC1B0AED78C3883DC9CEC37100A10F571458E9B0E1DFF9`.
- Added repeatable audit tooling and caches under `tools/station_names/`.
- Confirmed station `4320231` (`八代市鏡町`) from its official installation
  address as `鏡町内田` / `Kagamimachi Uchida`, not `Kagamimachi Kagami`.
- Audit result: 469 proposed DAABR English assignments, comprising 38 address
  confirmed, 56 provider-coordinate matched, 365 coordinate matched, and 10
  coordinate-envelope matched. A further 50 have a known locality but no DAABR
  English romanization; 44 remain unresolved. No duplicate station codes exist.
- Cross-checked all 94 research rows against all 4,360 stations in the current
  bundled catalogue, including already approved mappings. Thirteen rows are the
  sole reporting station in their municipality; the other 81 rows belong to 55
  municipalities containing multiple reporting stations.
- The user approved these 13 municipality-level English mappings:
  - `0130431` `新篠津村第４７線` -> `Shinshinotsu`
  - `0110740` `札幌西区琴似` -> `Sapporo Nishi Ward`
  - `0111040` `札幌清田区平岡` -> `Sapporo Kiyota Ward`
  - `0122632` `砂川市西７条` -> `Sunagawa`
  - `0142831` `長沼町中央` -> `Naganuma`
  - `0145332` `東神楽町南１条` -> `Higashikagura`
  - `0145431` `当麻町３条` -> `Toma`
  - `0154301` `美幌町東３条` -> `Bihoro`
  - `0163820` `中札内村東２条` -> `Nakasatsunai`
  - `2821633` `高砂市荒井町` -> `Takasago`
  - `4038231` `水巻町頃末` -> `Mizumaki`
  - `4120431` `多久市北多久町` -> `Taku`
  - `4351331` `球磨村渡` -> `Kuma`
- Updated `ambiguous_station_name_audit.xlsx`: those 13 Proposed Mapping rows
  now carry the approved municipality English names and are marked ready; the
  same 13 rows were removed from `Needs Research`.
- The user approved two verified Sapporo facility identities:
  - `0110100` `札幌中央区北２条` -> `JMA Sapporo Regional Headquarters`.
    JMA publishes `札幌市中央区北2条西18-2（札幌管区気象台）` and officially
    uses the English facility name `Sapporo Regional Headquarters`.
  - `0110140` `札幌中央区南４条` -> `Sapporo Chuo Fire Station`. Sapporo
    identifies ward intensity as measured at the ward fire station, locates the
    Chuo Fire Station at `札幌市中央区南4条西10丁目`, and publishes that English
    name.
- Encoded all 13 municipality approvals, both Sapporo facility approvals, and
  the existing `4320231` Yatsushiro confirmation in the repeatable builder so a
  workbook regeneration preserves the decisions. `0110140` was removed from
  `Needs Research`, leaving 80 rows.
- The user approved both Kutchan identities:
  - `0140000` `倶知安町南１条` ->
    `JMA Kutchan Special Automated Weather Station`. JMA publishes the address
    `虻田郡倶知安町南1条東3-1（倶知安特別地域気象観測所）`.
  - `0140020` `倶知安町北４条` -> `K-NET Kutchan`. NIED identifies the
    precise provider station as K-NET `HKD144` `KUCCHAN`; the user-supplied
    Google Street View link visibly places its enclosure at
    `北海道虻田郡倶知安町北6条東7丁目` / `7 Chome Kita 6 Johigashi, Kutchan,
    Abuta District, Hokkaido 044-0006`.
- Recorded the future station-card location note for `0140020`: `Located in the
  southwestern corner of the grounds of the Shu Ogawara Museum of Art.`
- Encoded both Kutchan approvals and their provenance in the repeatable builder,
  workbook, and JSON source export. `0140020` was removed from `Needs Research`,
  leaving 79 rows; `0140000` was already research-ready from its official JMA
  address, so its approval changes the selected English identity without
  reducing the research count a second time.
- The station-information card now consumes a compact runtime projection of
  `station_metadata_sources.json`. The APK pre-build regenerates that projection
  every time, validates the source schema, requires exactly 4,360 unique
  seven-digit station codes, and carries published address, facility, provider
  identity, coordinates, note, and municipality English-name fields. The audit
  JSON itself remains authoritative and is not moved or rewritten.
- The same generated projection now derives the complete stable administrative
  relationships already present in the audit source: 188 code-keyed JMA
  reporting areas and 1,894 code-keyed municipality parents. The build combines
  those records with the bundled JMA English dictionary and validates that every
  station-backed reporting area has a non-Japanese English label. No audit-source
  row or workbook field was edited for this extension.
- The card explicitly reports an unavailable address when the published-address
  field is blank; it does not promote catalogue or provider coordinates into an
  inferred street address.

## Why this approach was used

The published Japanese station label can be a shortened locality prefix, so
name-only matching can select the wrong neighbourhood. Stable station codes,
official installation addresses, provider coordinates, DAABR vocabulary, and
recorded confidence make the result reproducible and auditable. The workbook
keeps research separate from runtime behavior, while one future shared resolver
will keep list and map labels consistent.

## Current unfinished point

The complete audited 4,360-station English map and the first station-information
card are active in the app. Placement research is underway in
`outputs/station-name-audit/station_metadata_sources.json` only:

- 2,992 stations have source-supported facility and/or address data.
- 2,977 have an exact published Japanese address.
- 15 have a confirmed facility but no exact address yet.
- 1,368 remain locality-only and require further research.
- Address/precision validation currently reports zero mismatches.

The latest completed official-source batches converted confirmed facility-only
records to exact published addresses: one in Okinawa (`4735831`, Kita Daito
Village Hall), 21 in Toyama, 74 in Nagano, 69 in Osaka, one in Saga, and 22 in
Kyoto. An official Noshiro City construction record also directly placed
`0520232` at Noshiro City Hall and supplied its address. Nagano's records
cover municipal halls, branch offices, fire stations, public facilities, and
parks whose host identities come from Nagano Prefecture's official seismic-network
table and whose exact addresses come from the relevant municipal, fire-authority,
or other official facility publication. Osaka's completed records cover Osaka
City and Sakai fire services plus municipal and town offices, using Osaka
Prefecture's official seismic-network table and each host authority's address
publication. Two Nagano facility-only records remain deliberately without a
substituted address: `2021532` (former Narakawa Branch Office) and `2040733`
(former Seinaiji Promotion Office), because the available official evidence
identifies a former or moved facility but does not publish its historic station
address. `3321538` (`美作市美来`) remains deliberately unmapped: available evidence
does not directly map the reporting station to a candidate facility.
Evidence URLs and the full research provenance remain outside the compact runtime
projection.

The current Akita pass added direct, published placement/address evidence for
`0521130` (Katagami City Hall Showa Branch), `0521131` (Katagami City Hall
Iitagawa Branch), four Yuzawa City offices, `0520340` (Yokote Jūmonji Office),
`0536332` (Hachirogata Town Hall), and `0534835` (Mitane Yamamoto Branch). The
Oga municipal plan only supports the localities for `0520632` and `0520633`,
and Mitane's current plan does not directly identify the two remaining host
facilities; neither case was promoted to a facility or exact address.

## Do not redo or change

- Do not rebuild the audit from scratch. For the current placement-research
  phase, use only `station_metadata_sources.json`; do not read, edit, regenerate,
  or synchronize the workbook unless the user explicitly requests it.
- Do not re-research, replace, or return the 13 approved municipality-singleton
  mappings to `Needs Research` unless the user explicitly changes the decision
  or the bundled catalogue later gains another station in that municipality.
- Do not replace the two approved Sapporo facility labels with neighbourhood
  names unless the user explicitly changes the decision.
- Do not replace the two approved Kutchan labels with neighbourhood names unless
  the user explicitly changes the decision. Preserve the verified `0140020`
  parcel address and museum-grounds location note.
- Do not infer exact addresses from catalogue or provider coordinates. Record a
  street address only when a source actually publishes it, with its evidence URL.
- Do not invent transliterations, silently relabel Google values as official, or
  force uncertain matches.
- Do not alter Japanese names, parser/report merging, station coordinates, or
  provider architecture for this workstream.
- Do not make a report-only or map-only naming workaround; the eventual behavior
  belongs in the shared naming path.
- Do not commit `tools/source/mt_town_all.csv`; it is a large local reference and
  is ignored by `.gitignore`.
- Do not re-bundle the preserved baseline map in the APK. It is retained at
  `outputs/station-name-audit/station_english_names_baseline.json` as the exact
  pre-implementation snapshot. The active APK resource is
  `app/src/main/res/raw/station_english_names.json`.
- Preserve unrelated local edits. These notes describe shared task state and do
  not claim exclusive ownership of any file.

## Exact next steps

1. Continue the JSON-only search with the remaining locality-only stations,
   currently testing direct municipal evidence in Akita. Do not treat a former
   or relocated office as a current station placement without direct evidence.
2. Preserve the two former Nagano facility records without an address unless a
   source specifically publishes the historic host-site address.
3. For each subsequent station, first seek an official installation address or
   facility identity. Record verified evidence in
   `station_metadata_sources.json`, but never infer a placement from
   coordinates, Street View, nearby facilities, or a generic municipality
   statement.
4. After each bounded batch, run the JSON address/precision consistency check.
   Do not re-read or update the workbook for this phase. The station-details APK
   projection will refresh automatically on the next build.

## Logical changes and Git state

- Research artifacts were committed as `15adfa0` and `5bf125a`, both titled
  `Prepared sources for better English-name association for stations.`
- `5bf125a` also added `/tools/source/mt_town_all.csv` to `.gitignore`.
- Current branch is local `main`; this workstream was resumed from `82c8c05`.
- App version is `0.9.84aa` / versionCode `206`; the cumulative changelog entry
  records the active station-name implementation and the unapproved station-card
  hotfix.
- The station-code resolver now drives English observed-station rows and idle and
  report map labels. The baseline resource was deliberately moved out of the APK
  to `outputs/station-name-audit/station_english_names_baseline.json`.
- The current `Station Sources` workbook and metadata JSON were compared record
  by record: 4,360 records, zero missing rows, and zero mismatches across all 32
  exported fields. The workbook was also opened successfully in Excel after its
  package repair.
- No source metadata row was edited for the station-card or administrative
  projection implementations. The build-derived compact resource is generated
  below `app/build/` and is not a second hand-maintained source.
