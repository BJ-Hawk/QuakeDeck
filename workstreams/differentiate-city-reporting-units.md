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

- 2026-09-02 catalogue-source migration: all 4,360 station rows and all nine
  fields exactly match a new derivation from the official JMA map plus XML
  code-table worksheet 24. Only top-level `catalog` provenance changed in the
  research JSON; station research, approved English names and the workbook were
  preserved. The repeatable builder now retains the full official provenance.
  See [audit and migration record](../outputs/jma-station-migration-audit/2026-09-02/README.md).

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

- 3,458 stations have source-supported address data, all with an exact published
  Japanese address.
- 902 retain municipality-or-ward precision. Three of those also have a
  source-supported facility identity but no verified published address; the
  remaining 899 still require facility/address research.
- Address/precision validation currently reports zero mismatches.

The latest locality-only refinement preserved those coverage counts while making
the fallback data more useful: 944 catalogue-only records now retain their
official JMA observation-point label as `placementLocalityJa`, rather than a
coarser municipality or ward label. This is locality evidence only: none was
promoted to a facility or an exact address, and the JMA list recheck found no
additional exact name-to-address match. The three facility-only records remain
explicitly distinct from the 899 records still needing facility/address
research.

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
publication. `2021532` (former Narakawa Branch Office) was subsequently
promoted when Shiojiri City's historical-site page published the former branch
address, `長野県塩尻市木曽平沢2221番地`. `2040733` (Achi Seinaiji) was subsequently
promoted when JMA's July 2023 current row directly identified its new host and
address. Fukui Prefecture's official seismic-meter location dataset subsequently
directly identified the facilities and published addresses for 21 further
records. `2520431` (Ōmihachiman Azuchi Shimo Toyoura) was subsequently promoted
to Azuchi Community Center, `滋賀県近江八幡市安土町下豊浦4660番地`, using the city’s
current disaster-plan placement table and official facility address. `3321538`
(`美作市美来`) remains deliberately unmapped: available evidence
does not directly map the reporting station to a candidate facility.

The newest bounded batch added 39 exact Nara Prefecture seismic-network
placements. Ikoma City's current official disaster-plan appendix directly pairs
each affected observation label with its host facility and published Japanese
address, including Nara City Hall, Uda's four regional offices, and municipal
or village offices across the prefecture. `2920734` (Gojō Okaguchi) remains
locality-only: the available material does not identify its host facility or
street address.

Subsequent focused official records added three Fukushima municipality sites,
two Kiryu branch offices, two Asahi branch facilities, the Mabashi Fire Station
in Matsudo, Isumi City Hall's Misaki Office, Iga's Abo District Civic Center,
and Kitaakita's Moriyoshi and Aikawa branch offices. Each address was retained
only where the official evidence directly identified the station host and the
published facility address.
Evidence URLs and the full research provenance remain outside the compact runtime
projection.

The current official-source passes added direct, published placement/address
evidence in Akita for `0521130` (Katagami City Hall Showa Branch), `0521131`
(Katagami City Hall Iitagawa Branch), four Yuzawa City offices, `0520340`
(Yokote Jūmonji Office), `0536332` (Hachirogata Town Hall), `0534835` (Mitane
Yamamoto Branch), and `0521238` (Daisen City Hall Omagari Office); in Fukushima
for four Nihonmatsu City offices (`0721030`, `0721031`, `0721032`, and
`0721034`); and in Saga for `4120137` (Saga City Hall Yamato Branch) and
`4120838` (Ogi City Hall Mikatsuki Office). The Oga municipal plan only supports
the localities for `0520632` and `0520633`, and Mitane's current plan does not
directly identify the two remaining host facilities; neither case was promoted
to a facility or exact address.

Subsequent JSON-only official-source batches added one Kumamoto municipal
placement, six Fukuoka City fire-station placements, and municipality/fire
authority placements in Nogata, Umi, Shingu, Kasuya, and Yoshitomi. An official
Hokkaido Kamikawa installation table also confirmed the host facilities for
stations in Takasu, Higashikagura, Toma, Pippu, Higashikawa, Kenbuchi, Nayoro,
Wassamu, Otoineppu, Nakagawa, and two public-site placements. Exact addresses
were recorded only where the matching host authority published them; the
remaining table evidence is retained as facility-only or source provenance.

The subsequent direct municipal passes added four exact Ibaraki placements
(two Mito facilities, Hitachi Jūō Branch, and Kitaibaraki City Hall), 46
Shimane prefectural network placements from its official station/facility
list, four Kazo City instruments at City Hall and the three general branch
offices, and two Gyoda City instruments at City Hall and Minamikawara Branch.
The Shimane, Kazo, and Gyoda records carry both the official placement evidence
and the facility/address source URLs; non-matching labels remain locality-only.

The latest bounded batch added 34 exact placements from Tottori Prefecture's
2023 official seismic-observation list. That direct station-label/facility/
address table covered Tottori City general branches, municipal and town halls,
branch offices, public facilities, and the remaining local-government sites in
the published list. Each record retains the prefectural list URL, has
`exact_address` precision, and was promoted only because the source explicitly
pairs the station with its host facility and Japanese address.

The next direct municipal-evidence pass added seven exact placements: Zushi
Fire Headquarters, Yamato City Hall, Minamiashigara City Hall, Nakai Town Hall,
Matsuda Town Hall, Isehara Fire Station, and Mizunami City Hall. Each promotion
uses a host authority's explicit seismic-instrument placement statement paired
with that authority's published facility address; no placement was inferred
from coordinates or locality names.

The latest Shiga pass added 27 exact placements. A Shiga Prefecture seismic
station/host-facility table was matched only where its station label exactly
matches the current JMA Hikone station list; each resulting municipal hall,
branch office, fire-station, or public-facility identity was then paired with a
published address from the responsible municipality. The additions cover
Nagahama, Takashima, Toyosato, Taga, Hikone, Kora, Hino, Ryuo, Aisho, Yasu,
Kusatsu, and Higashiomi. Former or discontinued host facilities remain
unpromoted when continuity between the historical table and a current site is
not directly supported.

The subsequent Hyogo pass added five exact placements using direct municipal
installation statements, supported where needed by current JMA regional station
lists and the host authority's own address publication: Kawanishi City Hall,
Takarazuka City Hall, Kurokko Plaza in Nishiwaki, Seido Elementary School in
Ashiya, and Nishinomiya North Fire Station. Each record retains the direct
official evidence URLs. No facility was inferred from a locality, coordinate,
or nearby site.

The next bounded municipal-evidence pass added five exact placements without
touching the workbook: Kawamata Town Hall in Fukushima, and Isumi City Hall
Ohara Office, Mobara City Hall, Funabashi City Hall, and Abiko City Hall in
Chiba. Each source directly identifies the seismic instrument at the named
host site and the host authority publishes the recorded Japanese address. The
former Isumi Misaki office was deliberately left locality-only because the
current official page does not establish continuity with the historical meter
location.

The most recent Chiba pass added 39 exact municipal-hall and town-hall
placements. Chiba's official disaster plan states that the prefectural
municipal intensity instruments are installed at each municipality's main
office building or grounds. Records were promoted only when the current
station's Japanese locality exactly matched that main-office locality and the
responsible municipality published the facility's full Japanese address. This
adds, among others, city halls in Nagareyama, Ichihara, Sosa, Togane, Ichikawa,
Narashino, Kashiwa, Matsudo, Tateyama, Kimitsu, Inzai, and Minamiboso, plus
municipal and town halls in Chonan, Kozaki, Otaki, Onjuku, and Kyonan. Branch,
secondary, relocated, and merely nearby sites remain locality-only unless a
direct source identifies them.

The subsequent official-source pass added two further Chiba main-office
placements under the same rule: Noda City Hall (`1220832`) and Shisui Town Hall
(`1232231`). Direct ward publications then established the measurement meters
at Shinagawa City Office (`1310931`) and Koto City Office (`1310831`), with the
ward-published addresses recorded. Ota's official system publication supports
only the more precise Kamata locality for `1311130`; it deliberately remains
locality-only because it does not identify a host facility or street address.

The latest direct Tokyo municipal pass added seven exact placements: Hino
Disaster Information Center, Musashimurayama First Elementary School, Hamura
City Hall, Higashikurume City Hall, Ogikubo Fire Station, Suginami Fire
Station's Takaido Branch, and Adachi City Office. Each addition pairs an
official seismic-meter placement statement with the host authority's published
Japanese address; no site was inferred from coordinates or the station locality.

The subsequent bounded official-source pass added four further Tokyo municipal
placements (Komae, Kodaira, Musashino, and Mizuho) and two Toshima Village
branch offices (Hirashima and Takarajima). Further remote-island research added
exact Mishima Village facilities, Toshima Village Akusekijima Branch Office,
and Setouchi Ikejichi Assembly Hall. A current JMA March 2026 station-code
notice then directly established the Suwanosejima and Kodakarajima branch-office
placements, superseding prior prefectural Community Hall labels.

JMA's November 2025 and March 2026 current station-code notices supplied
direct placement/address rows for further stations and corrected records where
the newer source documents a move. The November notice, for example, added
direct municipal placements in Chikujo, Isumi, Nagahama, Maibara, Moriyama,
Yasu, Koka, and Higashiomi, and corrected moves in Yomogita, Miura, Gujo,
Yatsushiro, and Kakeroma. Kakeroma's former Seso Port facility label was
removed: the current notice supplies its new exact address but does not name a
replacement host facility, so none was invented.

JMA's March 2025 current station-code notice added four further exact
facility/address placements: Nakajima Village Hall, Inzai City Inba Branch,
Shimokitayama Village Hall, and Kurate Town Hall. It also documents moves for
Akashi Futami and Minamisatsuma Bonotsucho Kushi. The new Akashi row supplies
an exact address but no host facility, so the former Nakazaki Park label was
removed; Kushi is directly identified at Kushi Branch Office.

A retrospective pass through JMA's July 2023 current station-code notice added
31 exact placements. Its current rows directly identify the host and address
for local-government stations in Ibaraki, Saitama, Tokyo, Kanagawa, Fukui,
Yamanashi, Nagano, Nara, Shimane, Saga, and Oita. The evidence also supersedes
the former Seinaiji Promotion Office label for `2040733`: the current row
places it at Achi Village Seinaiji Promotion Office, `762-1 Seinaiji`, with a
published address. No old-side address was used.

Ena City's current disaster plan directly maps six local-government station
labels to their host facilities. The city's own facility pages supply the
published addresses for Iwamura, Akechi, Yamaoka, and Kushihara Promotion
Offices, Ena City Hall, and Kamiyahagi Elementary School. All six were promoted
to exact addresses; no station location was inferred from the address alone.

Hiroshima Prefecture's August 2026 seismic-network upgrade specification
directly lists 66 work sites with their host facilities and published Japanese
addresses. The confirmed records span Hiroshima's wards and the cities and
towns of Miyoshi, Shobara, Akitakata, Akiota, Mihara, Onomichi, Fukuyama,
Kure, Otake, Etajima, Fuchu, Sera, Jinseki Kogen, Fuchu Town, and
Osakikamijima. Each was promoted only where the station locality matched the
listed network work site; no facility or address was inferred from coordinates.

Three direct Fukushima field-survey and municipal-source records placed the
Soma, Kunimi, and Shinchi stations at their respective city or town halls.
Kiryu City's own seismic-instrument page then directly identified the
Kurohone and Niisato stations as being on the grounds of their branch offices;
the city profile page supplied their published Japanese addresses. These five
records were promoted to exact addresses without relying on locality or
coordinate inference.

The current JMA-list recheck found no exact name-to-address match among the
remaining locality-only records, so it produced no placement promotions.
Coordinate-adjacent candidates were explicitly rejected as insufficient
evidence. JMA's February 2024 observation report did refine `2120342`
(Takayama Takane) to the official locality `高根町上ケ洞`; it identifies neither
a host facility nor a street address, so the record remains at
municipality-or-ward precision and the coverage counts are unchanged.

The latest direct municipal records added nine exact placements. Fukaya City's
official emergency-response plan explicitly accounts for its four meters at
City Hall and the Hanazono, Okabe, and Kawamoto general branch offices; the
current station labels map one-to-one to those four hosts. The city publishes
each office's Japanese address, so `1121834` through `1121837` were promoted
to exact addresses. Kiyose's relocation report directly places `1322141` at
the rebuilt Kiyose City Hall, whose facility page publishes its address.
Kunitachi's and Tachikawa's official disaster plans directly place `1321530`
and `1320231`, respectively, on their city-hall grounds; each city publishes
the corresponding Japanese address. Hinode Town's official 2022
instrument-replacement record directly identifies `1330540` as installed at
Hinode Town Hall and supplies its Japanese address. The separate `1322152`
record remains locality-only: no source identifies its host facility or exact
address. Machida City's official newsletter directly identifies the meter at
City Hall, allowing `1320932` (Machida Morino) to be placed at Machida City Hall
with its published Japanese address; `1320952` (Machida Honmachida) remains
locality-only because that evidence does not identify its host facility.

The current JSON-only continuation added exact placements at Matsusaka City
Hall, Meiwa Town Hall, Okutama Town Hall, Kadogawa Town Hall, Omachi Town Hall,
Kamimine Town Hall, Kouhoku Town Hall, Kiyama Town Hall, Hanyu City Hall, Shiki
City Hall, Hasuda City Hall, and Kanra Town Hall. Each promotion was made only
after a direct official station-to-facility statement and a separate official
publication of that facility's Japanese address. In particular, Kanra Town's
current disaster plan explicitly identifies the `甘楽町小幡` meter as being at
Kanra Town Hall; the town's facility page publishes
`群馬県甘楽郡甘楽町大字小幡161-1`. Focused Fukushima, Gunma, Kyoto, Saga,
Miyazaki, and Okinawa searches that named only a locality or a municipality's
network participation made no promotion.

The latest Miyazaki official-source pass added 11 exact placements: Kijo Town
Hall, Nishimera Village Office, Morotsuka Village Office, Shiiba Village Office,
Nobeoka City Hall's Kitaura General Branch and main office, Misato Town Hall,
Kawaminami Town Hall, Gokase Town Hall, and Miyazaki City's Takaoka and
Sadowara General Branches. The NIED Miyazaki seismic-network catalogue directly
ties each station to its host parcel; the relevant current municipality confirms
the host's present address. Two tempting candidates were deliberately not
promoted: the former Togo Town Hall site no longer hosts the relocated branch,
and the former Takanabe Town Hall parcel differs from its current office.

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
   currently prioritizing prefectural maintenance specifications and direct
   municipal evidence in still-unfilled prefectures. Do not treat a former or
   relocated office as a current station placement without direct evidence.
2. Preserve any former or relocated facility record without an address unless a
   source specifically publishes the relevant historic or current host-site
   address.
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
- Placement research is maintained in the metadata JSON only; its exact-address
  entries require both a direct station-to-facility mapping and a published
  official address.
- No source metadata row was edited for the station-card or administrative
  projection implementations. The build-derived compact resource is generated
  below `app/build/` and is not a second hand-maintained source.
- Placement research remains uncommitted in the existing dirty local worktree.
  Its newest recorded promotions are 39 directly mapped Nara Prefecture
  seismic-network stations, after the 21 Fukui Prefecture stations and `2520431`
  at Azuchi Community Center. This continuation update is coordination-only: it
  does not alter release metadata or the changelog.
- The current continuation extends that uncommitted JSON-only research with the
  exact municipal placements listed above, most recently the 11 Miyazaki
  placements. It also updates this workstream and its `WORKSTREAMS.md` index;
  no workbook, runtime resource, release metadata, changelog, or unrelated file
  was changed.
