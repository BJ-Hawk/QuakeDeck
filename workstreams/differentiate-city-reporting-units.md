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

- 3,693 stations have recorded exact published Japanese addresses. The
  Yamanashi source-recovery qualification below still requires follow-up;
  this count is not a fresh recertification of earlier sources.
- 667 retain municipality-or-ward precision. Six of those also have a
  source-supported facility identity but no verified published address; the
  remaining 661 still require facility/address research.
- The latest research address/precision validation reported zero mismatches;
  this documentation-only update did not rerun a whole-dataset validation.

### Latest continuation checkpoint — 17 October 2026

Two hundred and twenty-five completed JSON-only promotions against the previously
compared HEAD baseline. Since the previous documentation checkpoint, two guarded
scripts have been applied: Ohtawara Honcho's exact host-site address with
Yuzukami's provenance qualification, and Fujisato's provenance-only plan recovery.
Coverage advances by arithmetic only: 3,692 + 1 = 3,693 exact addresses;
668 - 1 = 667 municipality/ward records; the six facility-only records are
unchanged (a subset of those 667); 662 - 1 = 661 still without either a facility
or exact address. Promotions advance 224 + 1 = 225. Scripts advance 94 + 2 = 96:
81 + 1 = 82 placement and 13 + 1 = 14 provenance-only. None should be rerun.
There was no coverage recount or new whole-dataset recertification for this
documentation update. All 4,360 English names remain resolved; placement gaps
are not unresolved English names. Hommachi's saved address and the preceding Kawasato address
and three Miyazaki historical/current-address qualifications remain saved;
the latter leave all three placements unassigned. The Oita Maizurumachi
continuity qualification and Tokai completed-move-without-destination notes remain
saved without placement assignments.
The preceding checkpoint's 16 Saitama exact-address additions remain saved:
eight town/branch hosts, seven fire/branch hosts and Higashichichibu's existing
village office, alongside Yashio's planned-transfer provenance.
The earlier 68-address Tokyo/Saitama batch
and formerly pending ten-station Tokyo script are already applied and included.
Mimasaka's earlier relocation/planned-host provenance remains unchanged and does
not establish a completed installation.
The first 37 are listed below;
the subsequent Yokohama, Tokyo, and latest municipal batches follow the table.

### Newly saved placement and provenance — Ohtawara and Fujisato

- **Honcho `0921033`: Ohtawara City Hall, Main Building's host-site address saved.**
  The city-published [13 June 2023 comprehensive education meeting record](https://www.city.ohtawara.tochigi.jp/docs/2015070300033/file_contents/R0506sougoukyouiku.pdf),
  complete PDF pages 1, 3 and 4, identifies city-history natural-science committee
  lecturer 伴敦志 and explicitly discusses intensity measured at the main hall
  (本庁でとった震度). Page 4 was rendered and visually inspected.
  The independent [municipal major-facility directory](https://www.city.ohtawara.tochigi.jp/docs/2013082774873/),
  published 1 April 2026, gives **栃木県大田原市本町1丁目4番1号**.
  Saved facility: **大田原市役所本庁舎 / Ohtawara City Hall, Main Building**.
  This is dated city-published researcher testimony plus a published host-site
  address, not an equipment inventory, enclosure survey, source-printed
  seven-digit crosswalk or fresh 2026 instrument inspection. The testimony does
  not date the underlying measurements or certify continuous sensor retention
  through reconstruction. Approved name **Honcho** is unchanged.
- **Yuzukami `0921034`: provenance only, placement unchanged.** The same lecturer's
  installation explanation is conjectural (設置したのかと思います).
  The published branch address at 湯津上5番地1081 is not sufficient by itself.
  Facility/address fields remain blank and municipality/ward precision remains.
  For both Ohtawara records, the FY2019 annual report's 391 pages were fully
  native-text searched; PDF pages 70–71 were read and page 71 visually inspected.
  Its 410,400-yen bollard-work item gives no host, work-site address or station
  association. The current main plan (218 pages) and appendix (211 pages) were
  fully native-text searched without individual host evidence. The BCP's
  January 2019 hall opening is office context, not a sensor-transfer certificate.
  These text searches are not complete whole-volume visual/OCR examinations.
- **Fujikoto `0534631`: recovered-plan provenance only.** Following the live
  organization index recovered Fujisato's [current plan page](https://www.town.fujisato.akita.jp/town/soshiki/c52/bousaianzenshitsu/517)
  rather than retrying the obsolete `c54/syobo-bosai/bousai/517` 404.
  Linked R7 volumes ① (92 pages), ② (84), ④ (38), ⑤ (31), and ⑦ (75) were
  fully searched as whitespace-normalized native text. Volume ⑤ PDF page 16 /
  printed 304 describes generic early-warning detection; appendix PDF page 9
  is a generic intensity-scale table. No individual host was recovered.
  This is not a whole-volume visual/OCR negative; general-response latter
  part ③ remains unsearched. The former contact address 藤琴字藤琴8 and the
  separate KiK-net station are not this municipal meter's placement evidence.
  Facility/address fields, locality precision and approved name remain unchanged.

Applied once during research, not rerun for this documentation request:
`tools/station_names/update_ohtawara_honcho_recorded_measurement_host.mjs` and
`tools/station_names/record_fujisato_recovered_current_plan_host_gap.mjs`.
The former changes Honcho's placement/provenance and Yuzukami's provenance; the
latter changes only Fujikoto's notes/source URLs. Guarded research checks
preserved all other records, names, coordinates, order and previous provenance,
with zero address/precision mismatches. Selected saved rows were reopened for
this documentation update; no coverage recount or update script was run.

### Previously saved placements and provenance — Senju, Akabane and three host gaps

- **Senju Nakaicho `1312153`: exact host-site address saved.** The dated
  [SK-net fire-department installation inventory](http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_shobo.html),
  heading 11 January 2022, names 千住消防署 for TFD `0139080`.
  The [current metropolitan directory](https://www.metro.tokyo.lg.jp/tosei/iken-sodan/sodan/kesatsu)
  independently publishes **東京都足立区千住中居町9-14**, telephone
  03-3882-0119. The [2009 assembly record](https://www.gikai.metro.tokyo.lg.jp/record/police-fire-fighting/2009-10.html)
  describes the different parcel and planned office opening on 26 February
  2010; that date is not asserted as a sensor-transfer date. The individual
  2022 host inventory postdates that move, unlike the earlier 2005 newsletter.
  Its former identifier before 21 February 2012 is not a physical-transfer date.
  This is a dated named-host/site association, not an enclosure survey,
  source-printed seven-digit crosswalk or fresh 2026 instrument inspection.
  Approved name **Senju Nakaicho** is unchanged.
- **Akabaneminami `1311753`: facility identity only saved.** The same inventory
  explicitly names **赤羽消防署 / Akabane Fire Station** for TFD `0139170`
  and former TFD `015`, with equipment notes from March 2018 and through
  February 2018 respectively. Complete selected rows/headings were read.
  Kita's [20 February 2020 newsletter](https://www.city.kita.lg.jp/_res/projects/default_project/_page_/001/013/921/020220.pdf),
  complete printed/PDF page 2, was text-read and visually inspected. It announces
  office operation from 10 March 2020 at 赤羽南1-10-4, after temporary operation
  at 神谷3-11-22. The current metropolitan directory confirms the new office
  address, but neither source establishes where the ground sensor was retained
  or transferred. The inventory's equipment dates precede the office return.
  The exact address therefore remains null, with municipality/ward precision
  unchanged. The November 2019 third-party relocation-tender lead is not an
  inspected official specification or completion record. Approved name
  **Akabaneminami** is unchanged.
- **Mimasaka Mirai `3321538`: obsolete public-GIS host qualified.** The normal
  public Okayama selected-layer search returned named feature `4897-26`,
  美作市役所, explicitly at **美作市栄町38-2**. Its complete attributes were
  read; this is the former host, not the successor Mirai installation. An
  all-attribute 美来 search returned zero; the feature-ID attribute endpoint
  returned no further record. No layer update date was established. This
  completes the previously pending named-feature lookup without adding a host.
- **Onojo Akebonomachi `4021930`: processor-only relocation qualified.** The
  [official contract 22774](https://www.city.onojo.fukuoka.jp/kiji0032194/index.html)
  specifies 計測震度計（処理部）移設 一式 and system testing at
  大野城市曙町二丁目2番1号, contracted 20 May 2021 for 21 May–21 July 2021.
  Its complete body establishes contracted processor work, not a ground-sensor
  host or completed sensor transfer. The selected current appendix section
  (48 pages) was fully native-text searched without another host; this is not
  a complete visual examination of low-text pages or other sections.
- **Yamanakako `1942531`: current-plan host gap qualified.** The
  [current plan publication](https://www.vill.yamanakako.lg.jp/info/41)
  supplies the March 2025 amended main plan (403 pages) and appendix (229 pages).
  Both were recovered and fully native-text searched. Main-plan PDF page 284 /
  printed page 276 was also rendered and visually inspected: its meter is an
  input to a communications/mobilization diagram, not a published installation
  site. Page 322 concerns generic Tokai observations. No named host was found;
  the office contact at 山中237-1 is not installation evidence. This is not a
  whole-document visual/OCR negative.

Already applied once, with protected fields/order/prior provenance preserved
and zero address/precision mismatches during research:
`update_senju_nakaicho_postmove_inventory_address.mjs`,
`update_akabane_minami_inventory_facility_only.mjs`,
`record_mimasaka_obsolete_public_gis_host.mjs`,
`record_onojo_processing_unit_relocation_gap.mjs`, and
`record_yamanakako_current_plan_host_gap.mjs`. Do not rerun them.

### Previous checkpoint's saved placement — Hommachi / Yoyogi fire branch

- `1311350`, approved English name **Hommachi**, now records
  `渋谷消防署代々木出張所` / descriptive English **Shibuya Fire Station, Yoyogi Branch**,
  at **東京都渋谷区本町1-6-6**. This is a facility/address addition, not a station rename.
- The public [University of Tokyo SK-net fire-service inventory](http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_shobo.html),
  heading 11 January 2022, explicitly names that host for `TFD 0139130`;
  the separate `TFD 013` row names the same branch. Its former identifier
  `0530640 before 21 February 2012` is not interpreted as a transfer date.
  Complete selected rows and column headings were read without registration
  or restricted waveform access.
- Two independent official Tokyo Fire Department / Shibuya guides,
  [建物の使用を開始する方へ](https://www.tfd.metro.tokyo.lg.jp/content/000044114.pdf)
  and [届出内容に関する注意事項](https://www.tfd.metro.tokyo.lg.jp/content/000041970.pdf),
  each one complete page, were text-read, rendered and visually inspected.
  Both publish the named branch at 本町1-6-6; the latter also gives 151-0071.
  This is not Shibuya City Hall in Udagawacho or the ward's administrative
  Hommachi branch office.
- The stored address is the inventory host's address, not a surveyed sensor
  enclosure/floor, a fresh 2026 instrument inspection or an operational-continuity
  recertification. No source-printed seven-digit JMA-code crosswalk or physical
  transfer date is asserted. These source-age and association limits are saved
  in the JSON note.
- `tools/station_names/update_shibuya_hommachi_yoyogi_fire_address.mjs` was
  dry-run and applied once during research. Do not rerun it. The research save
  checked all 4,360 records for protected fields, order, unrelated-row preservation,
  prior provenance and address/precision consistency, with zero mismatches;
  the saved selected record was reopened. This documentation update only checks
  the selected record/script and unchanged JSON hash, not a coverage recount.

### Previous checkpoint's saved Tokyo additions

The following 11 host/address records are saved in the authoritative JSON:

| Station code | Source-supported host facility | Published Japanese address |
| --- | --- | --- |
| `1310632` | Taito City Office main building grounds | 東京都台東区東上野4丁目5番6号 |
| `1310650` | Nihonzutsumi Fire Station | 東京都台東区千束四丁目一番一号 |
| `1311030` | Meguro City Disaster Prevention Center | 東京都目黒区中央町一丁目9番7号 |
| `1310230` | Chuo City Office | 東京都中央区築地一丁目1番1号 |
| `1310730` | Sumida City Office | 東京都墨田区吾妻橋一丁目23番20号 |
| `1310151` | Kojimachi Fire Station | 東京都千代田区麹町1-12 |
| `1310852` | Joto Fire Station | 東京都江東区亀戸6-42-9 |
| `1310951` | Ebara Fire Station | 東京都品川区平塚3-16-20 |
| `1310250` | Nihonbashi Fire Station | 東京都中央区日本橋兜町14-12 |
| `1310750` | Mukojima Fire Station | 東京都墨田区東向島6-22-3 |
| `1310950` | Shinagawa Fire Station | 東京都品川区北品川3-7-31 |

- Taito: the [adopted fiscal 2024 disaster plan](https://www.city.taito.lg.jp/bosai/houshin/bosaikaigi/chikibou_r6.files/r6chiikibou00.pdf),
  printed page 273 / PDF page 295, explicitly identifies both instrument hosts.
  The complete page was text-read and visually inspected during research. The
  [current hall guide](https://www.city.taito.lg.jp/kusei/shisetsu/kuyakusho/chosha.html)
  and [metropolitan fire-station ordinance](https://www.reiki.metro.tokyo.lg.jp/reiki/reiki_honbun/g101RG00002227.html)
  independently supply their addresses. These are host-site addresses, not
  surveyed sensor positions.
- Meguro, Chuo and Sumida: the public University of Tokyo Earthquake Research
  Institute [metropolitan observation inventory](http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_tocho.html),
  heading last updated **2 January 2021**, explicitly names the hosts for
  `TKY.1100`, `TKY.1020` and `TKY.1070`. Independent official municipal guides
  supply the addresses; the JSON retains each URL. Chuo's current hall remains
  in use while redevelopment is considered. Sumida's renovation notice does
  not establish any sensor move or floor. A Meguro office's basement address
  does not locate the sensor in that basement.
- Six fire stations: the public [fire-service observation inventory](http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_shobo.html),
  heading last updated **11 January 2022**, explicitly names their hosts;
  [Tokyo's official facility directory](https://www.metro.tokyo.lg.jp/tosei/iken-sodan/sodan/kesatsu)
  independently supplies the addresses. The selected Nihonbashi, Mukojima and
  Shinagawa rows apply from 1 March 2018; separate rows ending February 2018
  are not substituted. Kojimachi's pre-2012 identifier history is not a move date.
- The dated inventories are not fresh 2026 instrument inspections or full
  operational-continuity recertifications. Matching uses explicit installation
  hosts and independently published localities, not coordinates/proximity or a
  claimed source-printed seven-digit JMA-code crosswalk. Facility English is
  descriptive; approved station names remain unchanged. Only selected public
  host statements were used, without logging in or retrieving restricted
  waveform data; no whole-catalogue import was performed.

### Earlier saved 68-address documentation checkpoint

All eight placement scripts below were dry-run and applied once during research.
The previous documentation update independently reopened their 68 selected JSON records
and confirmed each saved address, facility, precision and unchanged approved
English/Japanese name against its script, without executing any update script.

| Applied script under `tools/station_names/` | Exact-address additions | Saved station codes |
| --- | --- | --- |
| `update_ten_tokyo_fire_inventory_addresses.mjs` | 10 | `1310353`, `1311153`, `1311253`, `1311452`, `1310550`, `1310352`, `1311252`, `1311150`, `1311250`, `1310451` |
| `update_thirteen_tokyo_fire_inventory_addresses.mjs` | 13 | `1321351`, `1320952`, `1311850`, `1312351`, `1322152`, `1311951`, `1311950`, `1321852`, `1312252`, `1311152`, `1311451`, `1310854`, `1320352` |
| `update_eight_tokyo_municipal_inventory_addresses.mjs` | 8 | `1310432`, `1311931`, `1312230`, `1320631`, `1320730`, `1321331`, `1322030`, `1336330` |
| `update_morishita_komiya_tsukishima_inventory_addresses.mjs` | 3 | `1310850`, `1320150`, `1310253` |
| `update_eight_saitama_city_inventory_addresses.mjs` | 8 | `1110230`, `1110430`, `1110530`, `1110630`, `1110730`, `1110831`, `1110930`, `1111031` |
| `update_thirteen_saitama_hall_inventory_addresses.mjs` | 13 | `1120930`, `1121230`, `1121530`, `1121730`, `1121931`, `1122530`, `1122730`, `1122930`, `1123031`, `1123530`, `1124130`, `1124230`, `1124331` |
| `update_kumagaya_three_warabi_inventory_addresses.mjs` | 4 | `1120230`, `1120231`, `1120232`, `1122331` |
| `update_nine_saitama_town_inventory_addresses.mjs` | 9 | `1132430`, `1132630`, `1132730`, `1134130`, `1134730`, `1136230`, `1136330`, `1146430`, `1146530` |

- Tokyo's 34 additions use the dated public metropolitan/fire-service inventories
  linked above, paired with independent official facility addresses. They include
  the Takanawa and other formerly pending fire stations; Haneda, Egota, Edagawa,
  Kichijoji, Morishita, Komiya and Tsukishima branches; and eight municipal hosts.
  `1320952` (Machida Honmachida) and `1322152` (Kiyose Nakakiyoto), previously
  lacking host proof, now have explicit fire-station placements. Older statements
  below describing those records as locality-only are superseded by these saves.
- Saitama's 34 additions use the public [Saitama installation inventory](http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_saitama.html),
  heading last updated **16 May 2026**, paired with independently published
  municipal/school addresses. The complete selected rows and column headings
  were read. The inventory's date is not a physical inspection date for every
  instrument, and no source-printed seven-digit JMA-code crosswalk is claimed.
  Important distinctions: `1110831` is at **Urawa Osato Elementary School**,
  `埼玉県さいたま市南区別所7丁目14-28`, not the nearby South Ward Office;
  `1111031` is at **Iwatsuki Hommaru Community Center**,
  `埼玉県さいたま市岩槻区本丸3-17-1`, not the relocated ward office;
  `1122331` is at **Warabi Civic Hall**, `埼玉県蕨市中央4-21-29`, not City Hall.
  Official guides distinguish those sites; their URLs remain in the JSON.
  Iruma/Niiza reconstruction context does not establish a sensor wing, floor,
  within-parcel position or transfer date.
- The nine latest town hosts are Miyoshi, Moroyama, Ogose, Namegawa, Yoshimi,
  Minano, Nagatoro, Sugito and Matsubushi town halls. Moroyama's AED facility
  table supplies only the office address; the earthquake-instrument host comes
  separately from the installation inventory. Minano's office-location ordinance
  is independently corroborated by the current municipal homepage. Sugito's
  address is not an assignment to a future replacement building.
- `record_arakawa_disaster_center_continuity_evidence.mjs` was applied once as
  provenance only. `1311830` remains without an assigned facility/address:
  the dated inventory names the former Disaster Prevention Center, and an
  official 2018 facility sheet publishes its address, but the February 2024
  policy says the department moved in FY2019 while unspecified equipment
  remained. That does not identify the meter or prove its retention/transfer.
  The 2025 election-page address is indexed corroboration only; direct retrieval
  returns HTTP 404. The saved note retains these qualifications, confirmed during
  this documentation update; no placement promotion or coverage change resulted.

### Saved since the previous documentation checkpoint: 16 Saitama addresses

These three placement scripts were dry-run and applied once during research.
This documentation update verified their selected saved JSON records without
executing an update script or recounting coverage. The May 2026 public inventory
names the installation hosts; independent official facility guides supply the
addresses. The inventory date remains provenance, not a fresh sensor inspection.

| Applied script under `tools/station_names/` | Exact-address additions | Saved station codes |
| --- | --- | --- |
| `update_eight_saitama_town_branch_inventory_addresses.mjs` | 8 | `1134631`, `1138130`, `1138333`, `1134930`, `1134931`, `1123930`, `1124532`, `1121734` |
| `update_seven_saitama_fire_branch_inventory_addresses.mjs` | 7 | `1121430`, `1121440`, `1120333`, `1120331`, `1120731`, `1120733`, `1120735` |
| `update_higashichichibu_existing_office_inventory_address.mjs` | 1 | `1136930` |

- The eight town/branch hosts are Kawajima, Misato and Kamikawa town halls;
  Tokigawa's second and main buildings; Sakado City Hall; Fujimino Oi General
  Branch; and Konosu Fukiage Branch. The direct [Tokigawa main-building guide](https://www.town.tokigawa.lg.jp/info/292)
  was recovered and independently confirms 大字玉川2490番地; this is no longer
  pending or based on another building's contact footer.
- The seven fire/branch hosts are Kasukabe Showa General Branch and Fire
  Headquarters; Kawaguchi East Fire Station Angyo Branch and Hatogaya Branch
  Office; and Chichibu Yoshida, Arakawa and Otaki general branches. Angyo's
  official address is 安行領家968. The sandbag-distribution table supplies only
  that address; the installation inventory supplies host proof, and the fire
  jurisdiction page corroborates only the parent organization. Hatogaya's
  三ツ和1-14-3 remains the branch site despite moves of other departments;
  neither its first-floor office nor the main-hall mailing address locates the sensor.
- Higashichichibu `1136930`: the saved existing Village Office address is
  **埼玉県秩父郡東秩父村大字御堂634番地**, independently published in the
  [official access guide](https://www.vill.higashichichibu.saitama.jp/soshiki/01/muraaccess.html).
  The [February 2026 newsletter](https://www.vill.higashichichibu.saitama.jp/uploaded/attachment/6007.pdf),
  complete printed/PDF page 13, was text-read and visually inspected during
  research. It plans new-building construction through June 2027, opening in
  September 2027 and subsequent demolition/external works through August 2028.
  These are not meter-transfer dates. The [construction-progress page](https://www.vill.higashichichibu.saitama.jp/site/sintyousyakensetu/sintyousya-kouji-shintyoku.html)
  describes steel-frame work, not a completed replacement office. No future
  building, sensor enclosure, floor or room is assigned; recheck continuity
  when the replacement office opens. The unrecovered mechanical drawing was not used.
- `record_yashio_planned_meter_transfer_evidence.mjs` was applied once as
  provenance only. `1123431` retains blank facility/address fields. The
  [10 December 2023 newsletter](https://www.city.yashio.lg.jp/shisei/koho_kocho/kohoyashio/r5/kohoyashio0512.files/yashio_12P1-P6.pdf),
  complete printed/PDF page 2, was text-read and visually inspected: it plans
  meter-transfer work and an observation interruption from 27 December 2023
  to 10 January 2024. The [new-hall completion guide](https://www.city.yashio.lg.jp/shisei/assetmanagement/tyousyataishinka/shinchosya-kouji/shinchoshakansei.html)
  confirms building completion and office opening, not completed meter transfer.
  The [current hall guide](https://www.city.yashio.lg.jp/kurashi/shisetsuguide/shisetsuannai/shiyakusyo/index.html)
  publishes 中央一丁目2番地1, but this is not by itself proof of the meter's
  destination. Seek completed-transfer or explicit current meter-host evidence.

### Latest saved address and Miyazaki provenance

- Konosu Kawasato `1121733`: saved **鴻巣市川里支所** / descriptive facility
  English **Konosu City Office, Kawasato Branch**, at **埼玉県鴻巣市広田3141-1**,
  using `update_kounosu_kawasato_inventory_host_address.mjs`. The public
  [Saitama inventory](http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_saitama.html),
  heading updated 16 May 2026, explicitly names the host for SIT.082; the
  [current city guide](https://www.city.kounosu.saitama.jp/page/1555.html)
  identifies the branch within かわさと館 and independently publishes its address.
  The complete [9 September 2014 committee record](https://www.city.kounosu.saitama.jp/uploaded/attachment/4053.pdf),
  PDF page 42 / printed page 40, was text-read, rendered and visually inspected:
  it describes a temporary office move during July–December 2013 construction,
  not an instrument transfer. The [official appendix](https://www.city.kounosu.saitama.jp/uploaded/attachment/11217.pdf),
  complete PDF page 36 / printed page 28, was likewise inspected: the 8 May 1998
  agreement gives the former village office at 大字広田3141番地1. Historical
  and current official office records therefore use the same host-site parcel.
  The saved address identifies that named host site, not a new-building wing,
  room, floor or sensor enclosure. No uninterrupted operation through construction,
  sensor-transfer date or fresh physical inspection is asserted.
- `record_three_miyazaki_historical_host_address_gaps.mjs` saves notes and
  source URLs only for `4540132`, `4520238` and `4520239`. Their facilities,
  addresses and municipality/ward precision remain unchanged. The original
  [March 2001 catalogue](https://dil-opac.bosai.go.jp/publication/edm/pdf/edm_tr12.pdf),
  complete PDF page 17 / printed page 12, Table 2-1, was visually inspected with
  both network headings; its cover date was also checked. The historical
  intensity-network rows below must not be confused with the separate K-NET rows.

  - Takanabe Uwae `4540132`, historical row 95004: Town Hall grounds at
    上江1207-1. The [current municipal rules](https://www.town.takanabe.lg.jp/section/reiki_int/reiki_honbun/q627RG00000030.html),
    Article 2(2), give 上江8437. The [6 April 2009 prefectural gazette](https://www.pref.miyazaki.lg.jp/documents/2404/000118848.pdf),
    complete PDF/printed page 4, Notice 316, was text-read and visually inspected:
    it records the Town Hall revenue-stamp sales address changing between those
    numbers on 28 January 2006. It does not establish cadastral renumbering,
    physical office relocation or meter transfer; differing printed addresses
    alone are not proof of a physically different parcel.
  - Yamanokuchi Hananoki `4520238`, historical row 95037: Town Hall grounds at
    花木2005. The [current branch organization page](https://www.city.miyakonojo.miyazaki.jp/soshiki/9.html)
    gives 花木1934番地1. The [older facility guide](https://www.city.miyakonojo.miyazaki.jp/map/14533.html)
    still gives 花木2005; neither office address certifies the meter's current site.
  - Yamada Yamada `4520239`, historical row 95039: Town Hall grounds at
    山田3881. The [current branch organization page](https://www.city.miyakonojo.miyazaki.jp/soshiki/8.html)
    gives 山田3881番地7. An [indexed official facility-record lead](https://www.city.miyakonojo.miyazaki.jp/uploaded/attachment/36835.pdf)
    describes office functions moving to Yamada General Center on 30 March 2020,
    but direct retrieval returned 404 and the original complete page was not
    recovered or visually inspected. This is a qualified lead, not meter-transfer
    proof; a shared parent parcel number does not prove the meter occupies the
    current office subparcel.

Both scripts were dry-run and applied once during research, with protected-field,
record-order, prior-provenance and address/precision checks passing. This requested
documentation update reopened only the four affected JSON records and read the
scripts; it did not execute them or recount coverage. Approved names and coordinates
remain unchanged. The three historical-host qualifications add no placements.

### Remaining Saitama and latest research handoff

- Konosu Kawasato is now saved above; its complete construction-history and
  historical-address pages have been inspected. Do not repeat it as an address gap.
- Shiraoka `1124630`: the previously inspected 31 July 2026 meter-restoration
  notice does not name the host. The 2025 hall fire and subsequent temporary
  office arrangements do not establish the sensor's current site.
- Yashio remains pending as qualified above. For Saitama Omiya Daimoncho (`1110331`), do not
substitute the inventory's Omiya Elementary School in Miyacho or a proposed
new-ward-hall destination without direct completed-placement evidence.

### Preceding saved provenance — no placement promotions

- Oita Maizurumachi `4420134`: the [March 2023 city earthquake plan](https://www.city.oita.oita.jp/o009/kurashi/anshinanzen/documents/sinnsai.pdf)
  has an indexed Fire Bureau meter statement, but now returns 404; the complete
  old page was not recovered. This is a qualified historical lead only. The
  [current index](https://www.city.oita.oita.jp/o009/kurashi/anshinanzen/1272444132022.html)
  links the [March 2025 earthquake plan](https://www.city.oita.oita.jp/o009/kurashi/anshinanzen/documents/shinsaitaisakuhen.pdf).
  Its complete PDF page 122 was text-read and visually inspected: the corresponding
  warning-reception paragraph omits the old meter sentence. That omission does
  not prove removal or relocation. The [official organization table](https://www.city.oita.oita.jp/o212/kurashi/anshinanzen/1180577976689.html),
  current as of 1 July 2024, distinguishes the Fire Bureau at 荷揚町3番45号
  from Central Fire Station at 舞鶴町一丁目1番1号; the [station guide](https://www.city.oita.oita.jp/o029/shisejoho/annai/1000000000193.html)
  independently confirms the latter address. Neither office relocation nor
  locality matching proves which site retains this meter. Saved notes and URLs
  using `record_oita_maizuru_fire_bureau_continuity_gap.mjs`; facility/address
  fields remain blank pending explicit current host or instrument-transfer evidence.
- Tokai `0834132`: the village's [FY2011 consultation follow-up](https://www.vill.tokai.ibaraki.jp/material/files/group/7/103001_20120710_0001.pdf),
  complete PDF page 10, row 25, was text-read and visually inspected with headings.
  The December 2011 column plans relocation; the distinct May 2012 column says
  meter-relocation work was completed on **27 March 2012**, following consultation
  with Ibaraki Prefecture. Neither origin nor destination facility/address is named.
  白方 identifies the consultation district, not a proven instrument site; the
  document also supplies no station-code crosswalk or proof against later moves.
  Saved notes and the source using
  `record_tokai_completed_meter_move_without_destination.mjs`; facility/address
  fields remain blank. Seek a direct destination/current-host record, not the
  village hall's contact address or a nuclear-facility meter.

Both scripts were dry-run and applied once during research, with protected-field,
record-order, previous-provenance and address/precision checks passing. This
preceding documentation update checked the two saved JSON records and scripts without
running either script or recounting coverage.

Remaining search-only leads are **not saved placements and not counted**:

- Munakata's [2018 plan comparison](https://www.city.munakata.lg.jp/kiji003938/3_938_10_3sshinkyuutaisyouhyou30.pdf)
  appears to replace an older fire-service host designation with 神湊出張所.
  Check the JSON first and visually distinguish old/new columns; do not mix them
  or treat a historic comparison as current continuity proof.
- Miyama's three stations already have exact addresses in the JSON; the plan
  lead is not a new address gap. Chikugo Yamanoi `4021130` already records the
  [official non-disclosure answer](https://www.city.chikugo.lg.jp/shisei/_5644/_7347/_31889.html?media=pc)
  withholding the precise site to prevent interference. Do not guess that site.

Additional completed searches supplied **no new host/address**:

- Oita's March 2026 appendix `tiikibousaikeikakushiryouhenr08.pdf` was searched
  in full (578 pages); relevant hits were generic intensity explanations, not
  a named Maizurumachi meter host. Do not repeat it as an uninspected inventory.
- Gojo's April 2024 earthquake-plan and appendix volumes were searched in full
  (114 and 193 pages), with matching passages read. They do not identify the
  Okaguchi `2920734` host. The JMA code-change notice establishes Honmachi to
  Okaguchi reporting changes, not a host or physical move date; do not reuse the
  old Honmachi City Hall parcel for Okaguchi. Nagahama's Biwa branch transfer
  evidence concerns another, already-addressed station, not Yahatahigashicho.
- The complete Sakai 2005 and two 2011 survey indices were checked; useful
  surveyed hosts were already covered or have later relocation gaps. The
  324-page March 2011 JMA monthly report was searched for installation/host
  wording; its explicit Nakanojo Kosame and Fujikawaguchiko Nagahama hosts are
  already addressed, not evidence for remaining nearby stations.
- Tokai's 24-page March 2019 nuclear-plant presentation was searched and matching
  pages read; generic installation criteria and plant meters do not identify the
  village reporting meter's destination. The old Mito installation list remains
  unrecovered, not a completed negative source inspection.
- Further Kobe searches found no direct host inventory for the remaining city
  stations. Wakakusa Elementary's disaster-response role alone is not meter
  evidence; historic JMA Nagata/Kaguracho observations and harbor/research networks
  must not be transferred to current municipal reporting sites. The previously
  completed current-plan searches remain recorded below and need not be repeated.
- Gifu's third-party 2025 maintenance-specification listing exposed no host table;
  its attachment is registration-gated and was not accessed. This does not apply
  to the login-free official procurement information system; its published
  weekday hours and the pending official inventory are recorded below. Saga's
  older specification link returned 404. These are access/source-recovery limits,
  not inspected documents proving absence of hosts.

Completed checks since the last documentation checkpoint supplied no additional
placements: the public SK-net Gunma, Tochigi and Ibaraki pages have blank selected
installation-facility columns; Chiba mainly supplies locality labels, including
a conflicting municipality entry, not usable host proof. Ora's 340-page and
Tamamura's 353-page plan text searches found generic network material, not named
hosts. Tatebayashi's selected PDF returned 404; that is not a completed negative
text search. Nagano and Shizuoka inventories cover prefectures with no remaining
JSON address gaps and should not be reprocessed. Kyoto's dated academic
field-survey captions mostly cover already-addressed sites or qualified former
sites; do not repurpose an explicitly uncertain caption. Kitaibaraki's City Hall
meter statement concerns Isohara, not the remaining Nakagocho station `0821540`.
Hyogo's prefectural list supplies locality labels, not individual host facilities.

Other Tokyo handoff: continue explicit public inventory rows, independently
checking branch addresses and relocation continuity. Morishita, Edagawa,
Honhaneda, Egota, Kachidoki, Komiya and Hommachi are completed above; other
relocation cases still require direct evidence.
The 11 remaining Tokyo records are `1311130`, `1311231`, `1311332`,
`1311631`, `1311753`, `1311830`, `1312153`, `1312352`, `1320433`, `1320651`
and `1322941`. Do not substitute Toshima's inventory host in Higashiikebukuro
for Minamiikebukuro, or Mitaka City Hall in Nozaki for Shinkawa. Fuchu's former
Shiraitodai fire branch was renamed/relocated to Asahi in December 2016, but
meter-transfer proof remains missing. Funabori's inventory parent name is
outdated; current branch naming/address alone does not establish continuity.
Setagaya's new hall, Nakano's 2024 hall move, Shibuya's rebuilt hall and Akabane's
relocation lead need continuity evidence before assigning replacement-site
addresses. The current Koto plan PDFs inspected for text extraction are image-only;
zero extracted text is not a negative host search. Chuo's alternate 454-page
revision and appendix text searches were completed without direct host proof,
with encoding limitations retained; do not repeat them as uninspected leads.

### Earlier completed placement batches

| Station code | Verified host facility |
| --- | --- |
| `1140830` | Yorii Town Hall |
| `1136130` | Yokoze Town Hall |
| `1134230` | Ranzan Town Hall |
| `1124530` | Fujimino City Hall Main Office |
| `0821631` | Kasama City Hall Main Office |
| `0820731` | Yuki City Hall |
| `0821730` | Toride Fire Headquarters |
| `0822431` | Moriya City Hall |
| `0821632` | Kasama City Hall Iwama Regional Branch |
| `0821634` | Kasama City Hall Kasama Regional Branch |
| `0830931` | Oarai Elementary School |
| `0821033` | Shimotsuma City Hall |
| `0921130` | Yaita City Hall |
| `0940730` | Nasu Town Hall Main Office |
| `0921430` | Sakura City Hall Main Office |
| `1052431` | Oizumi Town Hall (new building) |
| `0920639` | Nikko City Hall Main Office |
| `0920638` | Kuriyama Administrative Center |
| `0921633` | Minamikawachi Library |
| `0921635` | Ishibashi Library |
| `0941132` | Nakagawa Town Hall Main Office |
| `2152130` | Mitake Town Hall grounds |
| `2421435` | Inabe City Fire Brigade, Inabe First Division Station |
| `2421436` | Inabe City Fire Brigade, Daian North Division Station |
| `2421438` | Inabe City Hall Main Office |
| `2121532` | Yamagata Cable Television Station grounds |
| `4420834` | Taketa City Hall |
| `4420231` | Beppu City Hall grounds |
| `4420433` | Hita City Kamitsue Regional Office |
| `4420435` | Hita City Hall Main Office |
| `4420438` | Hita City Nakatsue Regional Office |
| `4420439` | Hita City Oyama Regional Office |
| `4420440` | Hita City Maetsue Regional Office |
| `4421134` | Usa City Hall Main Office |
| `4421135` | Usa City Ajimu Branch Office |
| `4121034` | Kanzaki City Chiyoda Branch Office (Chiyoda Community Exchange Center) |
| `2121835` | Motosu City Hall Main Office |

The subsequent 43 completed promotions are:

- Yokohama: 13 public-works offices, 16 fire-service hosts, six schools,
  Northern No. 2 Water Reclamation Center, and Yokohama Bayside Marina: 37 new
  exact addresses. The city's 10 July 2026 instrument-host inventory explicitly
  names the hosts; municipal, school, and marina-operator sources supply their
  published addresses. All 39 listed municipal hosts now have addresses,
  including two already-addressed records left unchanged. Matching combines
  the explicit ward host inventory with the published host locality; the sources
  are not claimed to contain seven-digit JMA station codes. The separate
  prefectural station `1410442` (Nihon Odori) remains unpromoted.
- Nerima: `1312051`, Shakujii Fire Station Oizumi Branch,
  `東京都練馬区東大泉6-34-44`; and `1312052`, Hikarigaoka Fire Station,
  `東京都練馬区光が丘2-9-1`. The ward's official FAQ, updated 4 February 2026,
  explicitly identifies the hosts and addresses. Its external FAQ service is
  linked from the ward's own homepage; private Tokyo Gas sensors are not used.
- Tama: `1322851`, Akigawa Fire Station,
  `東京都あきる野市伊奈466番地`; and `1320550`, Ome Fire Station Hinatawada
  Branch, `東京都青梅市日向和田2-309-1`. The responsible metropolitan official's
  4 October 2011 committee answer explicitly identifies these meter hosts;
  current fire-service guides supply their addresses. The historical placement
  statement is not represented as a fresh 2026 instrument inspection.
- Adachi: `1312152`, Adachi Fire Station Shinmei Branch,
  `東京都足立区神明南一丁目13番9号`; and `1312154`, Nishiarai Fire Station,
  `東京都足立区伊興二丁目5番11号`. The ward's 25 August 2005 newsletter
  explicitly identifies both hosts; current fire-service guides publish their
  addresses. The complete relevant newsletter page was read and visually
  inspected. Historical placement is distinguished from a current within-site
  survey. The separate Senju station was not assigned from this source because
  its host moved afterward and instrument-relocation evidence is still needed.

The preceding six completed promotions are:

| Station code | Verified host facility | Published Japanese address |
| --- | --- | --- |
| `1123132` | Okegawa City Hall | `埼玉県桶川市泉1丁目3番28号` |
| `1123331` | Kitamoto City Hall | `埼玉県北本市本町1丁目111番地` |
| `2822632` | Awaji City Ichinomiya Office | `兵庫県淡路市郡家170番地1` |
| `2822734` | Shiso City Ichinomiya Community Collaboration Center (Ichinopia) | `兵庫県宍粟市一宮町安積1347番地3` |
| `2822735` | Shiso City Chikusa Community Collaboration Center | `兵庫県宍粟市千種町千草168番地` |
| `2620931` | Nagaokakyo City Hall | `京都府長岡京市開田1丁目1番1号` |

- Okegawa's final March 2023 earthquake-response plan, printed page 260 / PDF
  page 8 of `shinnsaitaisaku.pdf`, explicitly places the meter at City Hall.
  The final-plan cover and complete relevant page were checked, including
  visual inspection; the July 2025 City Hall guide supplies the address. This
  completes the former November 2022 draft-plan lead.
- Kitamoto's adopted March 2024 response plan, printed page 3-2 / PDF page 4,
  explicitly places the meter at City Hall; printed page 3-42 independently
  confirms it. The cover and complete relevant pages were checked, including
  visual inspection of the first statement. The October 2025 official City Hall
  guide supplies the address; the undated draft was not used as final evidence.
- Awaji Gunge is directly identified behind Ichinomiya Office by Kobe and
  Hiroshima University field surveys from April 2013, independently corroborated
  by JMA's field report. The current municipal office guide supplies its address.
  These are historical host observations, not a fresh 2026 inspection; the rear
  parking description is source context, not a verified current meter position.
- Shiso's adopted July 2026 prevention plan, printed page 災害予防計画-8 / PDF
  page 14, directly lists both centers and their addresses. The inventory itself
  is dated 31 March 2024, not a new 2026 inspection. Current municipal guides
  support the center identities, superseding older 市民局 labels. The complete
  installation page was text-read and visually inspected.
- Nagaokakyo's current earthquake-response plan, printed page 100 / PDF page
  108 of `shinsaitaisaku2.pdf`, explicitly places the meter on City Hall grounds.
  The complete page was text-read and visually inspected; the official City Hall
  profile supplies the address. The plan index was updated 14 September 2026;
  that is not asserted as a plan-adoption or instrument-transfer date. During
  phased reconstruction, no particular hall wing, floor, or within-site position
  is claimed, and a budgeted relocation is not treated as a completed move.

The complete source URLs and qualifications for these six additions are retained
in their JSON records and guarded update scripts.

The preceding Hyogo/Chofu six completed promotions are:

| Station code | Verified host facility | Published Japanese address |
| --- | --- | --- |
| `2821832` | Ono City Disaster Prevention Center | `兵庫県小野市王子町809番地` |
| `2820230` | Amagasaki City Disaster Prevention Center | `兵庫県尼崎市昭和通2丁目6番75号` |
| `2822030` | Kasai City Hall | `兵庫県加西市北条町横尾1000番地` |
| `2848131` | Kamigori Town Hall | `兵庫県赤穂郡上郡町大持278番地` |
| `1320830` | Chofu City Hall | `東京都調布市小島町2-35-1` |
| `1320850` | Chofu Fire Station Tsutsujigaoka Branch | `東京都調布市西つつじヶ丘三丁目8番地3` |

- Ono's September 2022 municipal newsletter, printed page 4 / PDF page 3,
  directly identifies the center's meter and its transmission through the
  prefecture to JMA. The complete spread was text-read and visually inspected;
  the municipal center profile supplies its address. The separate main City
  Hall address in the page footer was not used.
- Amagasaki's 5 January 2024 policy-promotion meeting report, PDF page 3,
  records the fire bureau director's statement that JMA's published city
  intensity comes from the Disaster Prevention Center meter, describing its
  actual Noto earthquake observation. The cover and full relevant page were
  checked, including visual inspection; the February 2025 facility guide
  supplies the address. The department's fourth-floor contact is not the
  asserted meter location.
- Kasai's fiscal 2025 disaster-plan revision, printed page 27 / PDF page 33,
  explicitly lists the City Hall intensity meter and prefectural telemetry
  connection, separately from JMA and university instruments. The cover and
  complete table page were checked, including visual inspection; the municipal
  facility guide supplies the address. The plan index's 27 March 2026 update is
  not an installation or transfer date.
- JMA Kobe's August 2020 earthquake report, printed page 4 / PDF page 5,
  explicitly pairs station `2848131` (`上郡町大持`) with Town Hall.
  The complete page was text-read and visually inspected; the town's facility
  guide supplies the address. This is direct historical host evidence, not a
  placement inferred from the adjacent map.
- Chofu's 14 November 2015 council report, printed/PDF page 8, records the
  administration's answer placing the Kojimacho and Tsutsujigaoka reporting
  meters at City Hall and the fire-service branch. The cover and complete
  relevant page were recovered as cached full PDF text, resolving the earlier
  search-preview-only lead. Direct PDF retrieval returned 404 and screenshot
  retrieval failed: no visual verification or fresh download is claimed.
  City Hall's indexed official access-page text supplies its address (direct
  retrieval returned 403); the current Tokyo Fire Department guide supplies
  the branch address. Neither the main Chofu Fire Station nor the municipal
  Jindai branch office was substituted for Tsutsujigaoka Branch.

The six records retain all source URLs and retrieval qualifications in the JSON
and three guarded update scripts. Dated host statements are not represented as
fresh 2026 instrument inspections; no current room, floor, or precise within-site
position is asserted. Approved station names remain unchanged.

The preceding six completed promotions are:

| Station code | Verified host facility | Published Japanese address |
| --- | --- | --- |
| `1311730` | Kita City Disaster Prevention Center (Earthquake Science Museum) | `東京都北区西ケ原2-1-6` |
| `0521430` | Nikaho City Hall Nikaho Office | `秋田県にかほ市平沢字鳥ノ子渕21番地` |
| `0521431` | Nikaho City Hall Konoura Office | `秋田県にかほ市金浦字花潟93-1` |
| `0521432` | Nikaho City Hall Kisakata Office | `秋田県にかほ市象潟町字浜ノ田1番地` |
| `0721233` | Minamisoma City Kashima Ward Office | `福島県南相馬市鹿島区西町一丁目1番地` |
| `0734230` | Kagamiishi Town Hall | `福島県岩瀬郡鏡石町不時沼345` |

- Kita's fiscal 2024 individual-project list, printed/PDF page 2, row 46,
  explicitly places the intensity-meter renewal project at Disaster Prevention
  Center. The full page was text-read and visually inspected. The disaster-plan
  appendix, PDF page 28 / printed 資-26, independently distinguishes the
  center's meter from the Ward Hall intensity display. The official facility
  guide, updated 25 August 2026, supplies the address. The project period and
  budget do not prove a completed renewal date; the topology diagram does not
  prove a precise outdoor position. This completes the former prepared lead;
  the separate Kita Akabane Minami station remains unfilled.
- Nikaho's mayoral report of 22 February 2024, printed/PDF page 2, explicitly
  states that the meters at all three city halls recorded Shindo 3 during the
  1 January 2024 Noto earthquake. The complete page was text-read and visually
  inspected. The municipal hall/department guide, updated 10 April 2026, and
  hall-establishment ordinance identify the three hosts and their addresses.
  Composite matching pairs Hirasawa with Nikaho Office, Konoura with Konoura
  Office, and Kisakatamachi Hamanota with Kisakata Office; no source is claimed
  to publish all three seven-digit station codes in a single host table. The
  following paragraph's three evacuation shelters are not the meter hosts.
- The primary field-survey article in Bulletin of JAEE No. 47 (October 2022),
  printed page 7 / PDF page 9, section 3.1(4), explicitly places the Kashima
  Nishimachi meter at Kashima Ward Office. The complete page and the separate
  office/instrument photographs on printed page 8 / PDF page 10 were read and
  visually inspected. This concerns the March 2022 earthquake survey, not a
  fresh 2026 inspection. The official office guide, updated 29 March 2024,
  supplies the address; its second-floor department contact and separate main
  City Hall footer are not meter locations.
- Yuki Sakai's original 2011 survey page explicitly places the Kagamiishi
  Fujinuma meter behind Town Hall; the parent index dates the visit to
  7 April 2011. The complete Japanese HTML text was read using its original
  encoding; no new visual inspection of its photographs is claimed. The
  September 2025 council record, printed page 98 / PDF page 102, records the
  responsible official's answer that the 1972 hall remains in use with
  retrofitting/life-extension work, with replacement only a future consideration.
  The full answer page was text-read and visually inspected. This supports
  building continuity, not a fresh meter inspection. The current official
  map/access page supplies the address.

The full source URLs and qualifications remain in the six JSON records and
four guarded update scripts. English facility labels are descriptive, not
asserted as official English titles. Approved station names are unchanged;
no current room, floor, precise within-site position, or transfer date is claimed.

The preceding five completed promotions are:

| Station code | Verified host facility | Published Japanese address |
| --- | --- | --- |
| `0730131` | Koori Town Hall | `福島県伊達郡桑折町大字谷地字道下22番地7` |
| `0754633` | Futaba Town Hall | `福島県双葉郡双葉町大字長塚字町西73番地4` |
| `1322431` | Tama City Hall | `東京都多摩市関戸六丁目12番地1` |
| `1322450` | Tama Fire Station Tama Center Branch | `東京都多摩市鶴牧一丁目27番1号` |
| `1322530` | Inagi Fire Station | `東京都稲城市東長沼2111番地` |

- Koori: Kazuo Seo's primary field report hosted by the Architectural Institute
  of Japan, PDF page 12, explicitly captions the Town Hall meter in its appendix
  about the February 2021 earthquake and distinguishes the newly relocated hall
  from the former annex. The cover/context and complete appendix page were read,
  and the host page was visually inspected. The official hall guide, updated
  5 January 2026, supplies the address. This is historical host evidence, not a
  fresh 2026 meter inspection or an exact instrument-transfer date.
- Futaba: prefectural contract `21-05010-0006`, signed 10 February 2022,
  explicitly describes relocating the equipment from Morotake Community Hall to
  the temporary Town Hall in Nagatsuka Machinishi. Both image-only contract pages
  were visually inspected in full. JMA's 24 October 2022 notice, PDF page 3,
  identifies the relocation from `0754632` to `0754633`; its full table was read
  and visually inspected. The March 2025 municipal facility plan, printed/PDF
  page 6, identifies the 2022 hall and address, independently published on the
  current town homepage. Neither the contract's scheduled completion nor the
  XML parameter effective date is asserted as the actual physical move date.
  The former Morotake and off-site Iwaki office addresses are not used.
- Tama: the adopted August 2025 disaster plan, printed page 177 / PDF page 192,
  section 6, explicitly places operational network meters on City Hall grounds
  and at Tama Fire Station Tama Center Branch. The cover and complete relevant
  page were read, and the host page was visually inspected; the draft was not
  used. The current municipal hall guide and Tokyo Fire Department branch guide
  supply the two addresses. The 25 September 2026 hall-reconstruction update is
  basic-design work, not evidence of a completed instrument move. The municipal
  fire-facility list independently supports the branch address in indexed
  official text; direct retrieval omitted its dynamic rows. The separate main
  fire station in Suwa and municipal station-counter office in Ochiai are not
  these hosts. The plan's second-floor disaster-control room is not the meter.
- Inagi: the fiscal 2024 administrative report, printed page 195 / PDF page 196,
  section 5, directly identifies the maintained seismometer system as installed
  at the fire station. The cover and complete relevant page were read and the
  table visually inspected. The official facility guide, updated 16 May 2025,
  names Inagi Fire Station and supplies the address. Matching uses the explicit
  host statement, Higashinaganuma locality and sole bundled Inagi reporting
  station; no source is claimed to publish its seven-digit code. This is the
  municipal fire station, not a Tokyo Fire Department branch or City Hall.

These five records retain all source URLs and full qualifications in the JSON
and three guarded update scripts. The composite host/locality matches are not
represented as a single published code-to-address inventory. English facility
labels are descriptive; approved station names remain unchanged. No fresh 2026
meter inspection, current room/floor or precise within-site position is asserted.

The latest four completed promotions are:

| Station code | Verified host facility | Published Japanese address |
| --- | --- | --- |
| `1120831` | Tokorozawa City Hall | `埼玉県所沢市並木一丁目1番地の1` |
| `1124030` | Satte City Hall grounds | `埼玉県幸手市東4丁目6番8号` |
| `1144231` | Miyashiro Town Hall grounds | `埼玉県南埼玉郡宮代町笠原1丁目4番1号` |
| `1130130` | Ina Town Hall | `埼玉県北足立郡伊奈町中央四丁目355番地` |

- Tokorozawa: the [15 March 2022 Budget Standing Committee record](https://www.city.tokorozawa.saitama.jp/shigikai/tokubetuiinkaijyouhou/jyonininkai/yosankaigikiroku.files/r4.3.15yosan.pdf),
  printed page 31 / PDF page 30, records two responsible officials explicitly
  identifying the reporting instruments at Meiho Elementary School and City
  Hall. Opening/context and the full relevant page were read; the host page was
  visually inspected. The official hall guide supplies the Namiki address; the
  school guide independently places the other host in Kitayurakucho. The
  already-addressed `1120820` record was left unchanged. This composite match
  is not a published seven-digit-code inventory or a fresh 2026 inspection.
- Satte and Miyashiro: JMA Kumagaya's [18 March 2011 field-survey release](https://www.data.jma.go.jp/kumagaya/shosai/topics/2011/20110318.pdf),
  complete cover/summary page 1, explicitly names both reporting points and
  places their instruments on the respective hall grounds. The page was
  text-read and visually inspected. The report distinguishes surrounding
  ground subsidence from the inspected meter bases, which had no tilt or
  cracks. Satte's hall-management rules and current hall guide supply its
  address; Miyashiro's official residential-address guide, updated 17 September
  2026, and administrative profile, updated 9 June 2026, supply its address.
  The historical survey is not represented as a fresh 2026 inspection.
- Ina: the adopted April 2023 [earthquake-response plan](https://www.town.saitama-ina.lg.jp/cmsfiles/contents/0000007/7508/shinsai.pdf),
  printed page 2-139 / PDF page 139, section 2.3(1), explicitly identifies the
  meter installed at Town Hall. The parent index and cover establish the
  revision; the full host page was text-read and visually inspected. The
  official access guide supplies the Chuo address. The [fiscal 2026 implementation plan](https://www.town.saitama-ina.lg.jp/cmsfiles/contents/0000000/30/r8_zissikeikaku.pdf),
  issued February 2026, printed page 31 / PDF page 33, schedules the meter move
  for fiscal 2027 (Reiwa 9), not as completed in 2026. Its cover and full table
  page were read and the table visually inspected. The 9 June 2026 construction
  notice reports works starting in January and changed visitor parking, not a
  completed meter transfer. The record therefore identifies the documented
  existing hall site, not a replacement wing or a guessed instrument position.

These four records retain all source URLs and detailed qualifications in the
JSON and three guarded scripts. English facility labels are descriptive;
approved station names remain unchanged. No current room, floor, exact
within-site position, installation date or physical transfer date is asserted.

The latest seven saved records, added after those four, are:

| Station code | Source-supported facility | Published address retained in JSON |
| --- | --- | --- |
| `1034530` | Yoshioka Town Hall | 群馬県北群馬郡吉岡町大字下野田560番地 |
| `4544232` | Hinokage Town Hall | 宮崎県西臼杵郡日之影町大字七折9079番地 |
| `2634431` | Ujitawara Town Hall | 京都府綴喜郡宇治田原町大字立川小字坂口18番地の1 |
| `2610142` | Kyoto City Kita Fire Station | 京都府京都市北区大宮西脇台町17番地の2 |
| `2822832` | Takino General Park Gymnasium (Skypia) | 兵庫県加東市河高4007番地 |
| `2822833` | Kato Fire Station Tojo Branch | Withheld: conflicting official parcel numbers, 123 and 129 |
| `2821633` | Takasago City Hall | 兵庫県高砂市荒井町千鳥1丁目1番1号 |

Their evidence and qualifications are retained in the JSON:

- Yoshioka: the adopted February 2026 plan's [appendix volume 1](https://www.town.yoshioka.lg.jp/chousei/pdf/4b62ad06edd8b5f72cc827b16b55108c.pdf),
  printed pages 221–222 / PDF pages 17–18, reproduces the prefecture/town
  intensity-network installation agreement. Both complete scanned pages were
  visually inspected. Articles 2, 3 and 7 establish the town-government-building
  host, use of its facilities/grounds, and the intensity meter. The agreement
  dates from 1 January 1997 with automatic renewal; inclusion in the adopted
  2026 plan is not a fresh sensor inspection. The referenced separate equipment
  schedule is not reproduced. The official access page and an independent
  municipal article updated 19 August 2026 support Shimonoda 560; no specific
  wing or within-site position is claimed.
- Hinokage: the [28 February 2022 prefectural assembly minutes](https://www.pref.miyazaki.lg.jp/documents/82306/202202_1.pdf),
  printed page 165 / PDF page 177, record the responsible official confirming
  completed hall-associated meter relocation and resumed normal operation after
  JMA approval. The cover, complete relevant page and following discussion were
  read; the relevant page was visually inspected. The [official new-hall guide](https://www.town.hinokage.lg.jp/docs/2016031600018/),
  published 6 May 2021, supplies Nanaori 9079. Neither publication date is treated
  as the exact physical instrument-transfer date, and the old hall is not used.
- Ujitawara: the [20 October 2015 committee minutes](https://www.town.ujitawara.kyoto.jp/material/files/group/15/20151020sousan.pdf),
  page 71 with preceding speaker context, explicitly identify the former Araki
  hall-ground meter. The intensity-six-lower passage is a drill scenario, not
  an actual earthquake observation. The [fiscal 2020 executed-results report](https://www.town.ujitawara.kyoto.jp/material/files/group/3/R2_syuyonasesaku.pdf),
  printed page 5 / PDF page 7, explicitly records completed seismic-network
  equipment relocation with the new hall. The full relevant pages were read
  and visually inspected. The [current hall guide](https://www.town.ujitawara.kyoto.jp/soshiki/yakuba/yakubashokai/518.html)
  supplies Tachikawa Sakaguchi 18-1. This composite evidence supports the new
  host without assigning the former Araki address or inferring an exact move date.
- Kyoto Kita: the current official plan's [observation-site inventory](https://bousai-city-kyoto.meclib.jp/library/books/bk-siryou02/book/pdf/0038.pdf),
  printed page 72 / e-book page 38, explicitly dated 1 July 2025, identifies the
  city-managed Kita site as Kita Fire Station at Omiya Nishiwakidaicho 17-2.
  The entire single-page PDF was text-read and visually inspected. The official
  fire-service directory dated 21 April 2025 independently confirms the address.
  The 26 March 2021 fire-station move notice distinguishes the former Shichiku
  Shimomidoricho 87 site; its building-opening time is not a sensor-transfer date.
- Kato: the [municipal instrument-host article](https://www.city.kato.lg.jp/kakukanogoannai/soumuzaiseibu/bosaika/bosai001/14915.html),
  updated 29 March 2024, explicitly names the gymnasium and Tojo fire branch
  among the city's three meter hosts. The complete body was read. The official
  sports guide and municipal facility directory independently agree on Kotaka
  4007 for the gymnasium, distinguishing other Takino sports/office facilities.
  For Tojo Branch, the [fire-authority facility guide](https://kitaharima119.net/home/sisetu/sisetu.html)
  and its [15 June 2023 work-site notice](https://kitaharima119.net/home/gyousei/nyuusatu/230615-1.html)
  publish Tenjin 123, while the still-published municipal directory dated
  28 December 2021 gives Tenjin 129 for the same branch and phone number.
  The facility identity is recorded, but `publishedAddressJa` remains null and
  precision remains `municipality_or_ward`. The 2023 work concerns radio-base
  relocation supervision, not meter relocation. No parcel was guessed.
- Takasago: the city's [6 January 2026 disaster-network message](https://bosai.net/announce/detail?info=149634)
  explicitly reports the City Hall meter's intensity-two observation that
  morning. The full original municipal message was retrieved and read; the city
  independently confirms this disaster-information service. The official hall
  guide, updated 24 September 2026, supplies Arai-cho Chidori 1-1-1 and
  distinguishes the south annex at 2-2-16. The site address is recorded without
  claiming a particular building wing, room or sensor position. The September
  2021 main-hall completion date is not treated as an instrument-move date.

All seven retain their approved station names and prior source URLs. English
facility labels are descriptive, not asserted official English titles. Composite
host/address matching is not represented as a source publishing the seven-digit
station code, nor as a fresh 2026 physical sensor inspection.

Since that seven-record batch, one exact address and one provenance-only
conflict note have been saved:

- Nogi `0936430` (`野木町丸林`): **Nogi Town Hall / 野木町役場**, at
  `栃木県下都賀郡野木町大字丸林571番地`. The adopted March 2023
  [disaster plan](https://www.town.nogi.lg.jp/data/doc/1679615868_doc_33_0.pdf),
  printed page 震災－応急－205 / PDF page 220, explicitly places the single
  intensity meter between the main hall and annex. The cover and complete
  relevant page were text-read and the relevant page visually inspected.
  The [official plan index](https://www.town.nogi.lg.jp/anzen_anshin/bousai/page000639.html)
  identifies the adopted publication; the [hall guide](https://www.town.nogi.lg.jp/cyousei/cyousya_annai/page001313.html),
  updated 1 April 2026, and independent [access guide](https://www.town.nogi.lg.jp/cyousei/profile/page001655.html)
  corroborate Marubayashi 571. Matching uses the explicit host and sole bundled
  Nogi reporting station, not coordinates. The between-buildings description
  is dated 2023 evidence, not a fresh 2026 sensor inspection. The English
  facility label is descriptive; the approved station name is unchanged.
- Shinonsen `2858631` (`新温泉町湯`): **no promotion**. The adopted February
  2025 [earthquake plan](https://www.town.shinonsen.hyogo.jp/uppdf/1740557053.pdf),
  printed page 39 / PDF page 50, lists the Onsen General Branch Office meter
  at Yu 1604. However, the [December 2006 newsletter](https://www.town.shinonsen.hyogo.jp/shinonsen/kouhou/061214.pdf),
  full page 5, announces branch relocation to Yu 990-8, with ordinary service
  starting 4 January 2007. Both relevant PDF pages were read and visually
  inspected. The [current office guide](https://www.town.shinonsen.hyogo.jp/page/?mode=detail&page_id=2bfcaf22a8b15dbc485514b0e46671c9)
  places the branch/community center at 990-8, while the [department page](https://www.town.shinonsen.hyogo.jp/section_detail.php?id=3b1c1ffc224d684c69d6bbadf6b2bf9c)
  distinguishes Yakushiyu at 1604. The plan may describe a retained former
  meter site or stale office information; inspected sources do not settle
  instrument continuity. Only the note and source URLs changed. Facility and
  address fields remain blank, and this station remains among the 762 needing
  facility/address research. Seek a current installation inventory or explicit
  meter-relocation evidence, not another ordinary office address.

Two further exact-address records have since been saved and individually guarded:

- Chichibu `1120734` (`秩父市熊木町`): **Chichibu City Hall / 秩父市役所**,
  `埼玉県秩父市熊木町8番15号`. The adopted fiscal 2023 revision, emergency-response
  volume, printed page 3-37 / PDF page 39, explicitly identifies the City Hall
  intensity meter. The 218-page volume was text-searched; the cover and complete
  relevant page were read and visually inspected. The [official plan index](https://www.city.chichibu.lg.jp/4102.html)
  identifies the adopted revision despite working-document wording in PDF
  metadata; the [official directory](https://www.city.chichibu.lg.jp/6214.html)
  independently supplies the named facility and address. Full PDF source URLs
  are retained in the JSON and guarded script. Matching uses the explicit host
  and unique 熊木町 reporting locality, not coordinates; Yoshida, Arakawa and
  Otaki were not assigned from this main-hall statement.
- Ota `1020530` (`太田市浜町`): **Ota City Hall / 太田市役所**,
  `群馬県太田市浜町2番35号`. The adopted April 2026 [disaster plan](https://www.city.ota.gunma.jp/uploaded/attachment/39566.pdf),
  printed page 応【震災】－1－2 / PDF page 279, explicitly lists 市役所震度計
  and 危機管理室内計測震度計. The 448-page plan was text-searched; the cover
  and complete relevant page were read and visually inspected. The [plan index](https://www.city.ota.gunma.jp/page/1690.html)
  confirms the adopted revision; the [hall access guide](https://www.city.ota.gunma.jp/page/1014037.html)
  and independent [prefectural facility table](https://www.pref.gunma.jp/page/14636.html)
  corroborate the address. The table may describe collection/display apparatus,
  so no ground-sensor room, floor or within-site position is asserted. The other
  Ota reporting localities were not assigned from this statement.

Both additions preserve the approved English/Japanese station names. Their English
facility labels are descriptive; neither source is claimed to print the seven-digit
code or establish a fresh physical inspection, sensor-transfer date or exact
within-site position.

Two more exact-address records were saved after Chichibu and Ota:

- Oshino `1942432` (`忍野村忍草`): **Oshino Village Hall / 忍野村役場**,
  `山梨県南都留郡忍野村忍草1514`. The [official plan index](https://www.vill.oshino.lg.jp/page/1772.html),
  updated 16 June 2025, links the adopted revision's earthquake volume.
  Its [complete host page](https://www.vill.oshino.lg.jp/uploaded/attachment/2179.pdf),
  printed page 207 / PDF page 33, section 4, explicitly identifies the meter at
  the village office building. The separately linked [cover](https://www.vill.oshino.lg.jp/uploaded/attachment/2547.pdf)
  is dated May 2025; the earthquake volume's older attachment number is not an
  independently verified May 2025 cover date. The [hall guide](https://www.vill.oshino.lg.jp/page/1669.html)
  supplies the full address. Matching uses the explicit host and sole bundled
  Oshino reporting station, not coordinates. The approved Shibokusa name stays.
- Ogori `4021630` (`小郡市小郡`): **Ogori City Hall / 小郡市役所**,
  `福岡県小郡市小郡255番地1`. The adopted June 2026 [initial-response manual](https://www.city.ogori.fukuoka.jp/download_file/view/44442/735),
  complete PDF page 22, explicitly lists the meter installed in 防災安全課.
  The preceding response page and [municipal organization table](https://www.city.ogori.fukuoka.jp/1139/1145/4453)
  identify the division at City Hall; the [hall guide](https://www.city.ogori.fukuoka.jp/1139/1145/2009)
  and division guide supply the address. The [plan index](https://www.city.ogori.fukuoka.jp/197/702/735)
  confirms the current June 2026 edition, replacing the May 2025 discovery lead.
  The office reference may concern collection/display apparatus: it does not
  locate the outdoor ground sensor on a particular floor or in a room. Matching
  uses the explicit instrument reference and sole bundled Ogori reporting
  station, not coordinates; the approved Ogori name stays.

Both complete relevant PDF texts were read from public cached views, not merely
search previews. Direct retrieval returned 404 for Oshino and 403 for Ogori;
screenshots failed with cache miss. No visual or fresh physical sensor inspection
is claimed. Neither composite source chain is represented as a published
seven-digit-code inventory, exact within-site position or physical transfer date.
The JSON retains full qualifications and source URLs; English facility labels
are descriptive, and approved English/Japanese station names remain unchanged.

Doshi `1942232` (`道志村釜之前`) received **provenance only**, not a placement:
the [17 February 2021 JMA notice](https://xml.kishou.go.jp/20210217_jmaxml_info_1.pdf),
cover and full PDF page 3, explicitly lists `1942231 道志村役場` to
`1942232 道志村釜之前` under relocated observation points. Both pages were read
and visually inspected. The parameter change takes effect at noon on 17 March
2021; that is not a proven physical move time. No destination host/address is
published there. The older 2012 Village Hall preview predates the relocation
and cannot establish today's host. Only note/source URLs changed; coverage did
not change, and the current host/address remain blank.

The earlier exact-address additions retain the published Japanese address and direct primary
station/instrument-to-facility evidence in the JSON. Yokoze's installation
inventory is explicitly identified as a published January 2026 draft. Ranzan's
official newsletter identifies the instrument at the town hall; Fujimino's
historical position description is not presented as a verified current
within-site position. Kasama's national field-survey report distinguishes the
main hall from its branches. Yuki's relocation evidence identifies the new
Chuocho station and avoids the former hall's address in the newsletter header.

The subsequent ten records retain the following evidence and qualifications:

- Toride's fire-service annual report identifies the instrument in the
  headquarters building and publishes its Ino address; the national fire-service
  directory independently corroborates that address. Its first-floor description
  is historical, not a fresh inspection of the instrument's current position.
- Moriya's council transcript contains the responsible official's explicit
  answer placing the instrument at City Hall, not merely a councillor's question.
- Kasama's council answer confirms the Iwama installation; contemporary and
  current office guides support its address. Executed relocation expenditure
  establishes the Kasama branch move; the old Ishii branch address is not used.
- Oarai's July 2023 newsletter reports the completed move to Oarai Elementary
  School, superseding the former fire-headquarters placement.
- Shimotsuma's fiscal 2023 results report records completed instrument relocation
  to the new City Hall, whose opening notice publishes its address.
- The joint NILIM/BRI 2011 field report directly identifies the Yaita, Nasu, and
  Sakura hosts; current municipal publications supply their addresses. Historical
  within-site descriptions are not claimed as current verified positions.
- Oizumi's municipal-authored safety emails report relocation to the new hall
  and completion on 13 March 2026. Those emails are preserved in a third-party
  archive, explicitly identified as such in the JSON. The official new-hall
  opening page publishes `群馬県邑楽郡大泉町日の出51番1号`; the former
  `55番1号` address in the older notice's header is not used.

The preceding six records retain the following evidence and qualifications:

- Nikko's fiscal 2018 expenditure detail records completed instrument-relocation
  work for the new main hall (printed pages 150–151) and new Kuriyama hall
  (printed pages 173–174). Current municipal guides publish `今市本町1番地`
  and `黒部54番地1`, respectively. The former Kuriyama Hikage site is not used,
  and no current within-site instrument position is asserted.
- Shimotsuke's library-management specification for April 2022–March 2027,
  printed page 11, explicitly lists an intensity meter at each of Minamikawachi
  and Ishibashi libraries. The specification and current municipal facility pages
  publish `田中681番地1` and `大松山一丁目7番3号`, respectively. This evidence
  is not applied to the separate Sasahara station.
- Nakagawa's fiscal 2017 results report records completed prefectural intensity-
  network relocation as part of the new hall's opening. JMA's 22 February 2018
  notice independently identifies the relocated Bato station as `0941132`;
  current municipal pages publish `馬頭555番地`. The separate Ogawa station's
  destination remains unverified.
- Mitake's earthquake-response plan explicitly places its prefectural intensity
  meter on the Town Hall grounds; the town publishes `御嵩1239番地1` for the
  existing hall. Its October 2026 temporary-office notice does not identify a
  completed instrument move. The JSON therefore identifies those documented
  grounds, not a temporary office, a room, or the future new-hall site.

The preceding thirteen records retain the following evidence and qualifications:

- Inabe's fiscal 2024 evaluation report, printed page 30 (PDF page 44), explicitly
  lists intensity-meter communication lines at the main hall and named brigade
  buildings. Its facility inventory, printed page 38 (PDF page 52), supplies
  `員弁町笠田新田111` and `大安町丹生川久下2386-1`; the current City Hall guide
  supplies `北勢町阿下喜31番地`. The contemporary Daian address supersedes the
  older `2808` entry. The separate Fujiwara station remains unassigned because
  two First Division sites are listed.
- Panasonic's primary installation case study directly identifies the outdoor
  network intensity meter at Yamagata's cable-TV station. The city's facility
  profile publishes `高富2203番地`, independently corroborated by CCN's current
  shop guide and event publication. CCN's transfer announcement documents
  succession to the municipal cable-TV business from April 2022. The record
  preserves the documented cable-TV host identity without claiming current
  municipal operation, transfer of meter ownership, or a fresh 2026 inspection.
- Taketa's earthquake plan explicitly identifies the meter within City Hall;
  its current guide publishes `大字会々1650番地`. This is not the separate
  elementary-school instrument at `1637`.
- Beppu's earthquake/tsunami plan explicitly distinguishes the prefectural meter
  on City Hall grounds from the JMA park and school instruments. The current
  City Hall guide publishes `上野口町1番15号`. A councillor's question alone was
  not treated as placement evidence.
- Hita's February 2026 plan, printed page 57 (PDF page 46 of the prevention
  volume), explicitly places meters at the main office and each regional office.
  Current municipal guides supply the five addresses. This statement postdates
  the Nakatsue and Oyama office moves in 2015 and 2016. The fiscal 2021 results
  report additionally records completed Maetsue meter relocation to its new
  office, rather than merely a proposed office move. The former office
  addresses are not used; the separate JMA sites are not assigned from this
  evidence.
- Usa's earthquake/tsunami plan, printed/PDF page 50, explicitly places meters
  at the main office and both branch offices. Its municipal guide supplies
  `大字上田1030番地の1` for the main hall and `安心院町下毛2115番地` for Ajimu.
  The already-addressed Innai record is unchanged.

The final two records in the first 37 retain the following evidence and qualifications:

- Kanzaki Chiyoda (`4121034`): JMA Technical Report No. 135, chapter 2,
  printed page 88 (PDF page 6), explicitly identifies the instrument within the
  Chiyoda General Branch Office. The current municipal office guide and
  polling-place list publish `佐賀県神埼市千代田町直鳥166番地1` for the
  Chiyoda Branch Office / Chiyoda Community Exchange Center. The March 2019
  draft reuse plan documents continued use of that building, distinguishing it
  from replacement of the separate main City Hall. The historical wall-position
  description is not asserted as a current within-site position.
- Motosu Hayano (`2121835`): the installer's primary work-history table records
  the specific prefectural intensity-meter relocation associated with Motosu's
  hall move, separately from its general disaster-communications relocation.
  JMA's 14 February 2024 code-table notice links former Mitsuhashi station
  `2121832` to Hayano station `2121835`; the municipal opening guide publishes
  `岐阜県本巣市早野255番地` for the new main hall. This is composite evidence,
  not a claim that one source supplies the complete code/host/address mapping.
  No exact instrument-transfer date is inferred from the code-table notice,
  hall-opening date, or construction period, and the former hall address is not
  used.

These records retain their evidence URLs and full qualifications in the JSON.
Descriptive English facility translations are not asserted to be official
English titles, and no new room, floor, or precise within-site position is
claimed.

### Latest saved Kanagawa additions

Four exact addresses and one facility-only identification were saved after the
Oshino/Ogori checkpoint. These are placement metadata, not changes to approved
English station names.

| Station code | Recorded host | Published address |
| --- | --- | --- |
| `1438230` | 箱根町役場 / Hakone Town Hall | 神奈川県足柄下郡箱根町湯本256 |
| `1421831` | 綾瀬市消防本部 / Ayase City Fire Headquarters | 神奈川県綾瀬市深谷中1-4-30 |
| `1430130` | 葉山町消防本部 / Hayama Town Fire Headquarters | 神奈川県三浦郡葉山町堀内2050番地の10 |
| `1421248` | 神奈川県総合防災センター / Kanagawa Prefectural General Disaster Prevention Center | 神奈川県厚木市下津古久280 |
| `1410442` | 神奈川県庁 / Kanagawa Prefectural Government Offices | Unknown; facility-only, locality precision unchanged |

- Hakone: the [town's volcano guide](https://www.town.hakone.kanagawa.jp/www/contents/1100000002068/index.html)
  and the prefectural research institute's [FAQ](https://www.onken.odawara.kanagawa.jp/volcano-geology/faq/)
  explicitly identify the Town Hall meter as the source of published JMA
  intensity. The institute's [current activity page](https://www.onken.odawara.kanagawa.jp/volcano-geology/hakone-volcanic-activity/?content_id=1)
  corroborates that host while distinguishing its own mountain sensors.
  The [official hall guide](https://www.town.hakone.kanagawa.jp/www/contents/1100000002301/index.html)
  publishes Yumoto 256. The institute's separate Yumoto 997 site is not used.
- Ayase: the [adopted procedures volume](https://www.city.ayase.kanagawa.jp/material/files/group/3/syuhohen.pdf),
  complete printed/PDF page 7, explicitly identifies an existing intensity meter
  at Fire Headquarters. The [adoption notice](https://www.city.ayase.kanagawa.jp/soshiki/kikikanrika/bosai_bohan_anzen_anshin/3/3/24888.html)
  says the revised plan was adopted on 19 May 2026 but takes effect on 1 April
  2027; it is not described as the currently effective plan or a future meter
  installation. The [facility profile](https://www.city.ayase.kanagawa.jp/soshiki/hishokohoka/locationsite/rokelibrary/publicfacility/13435.html)
  supplies the address and the new headquarters' 7 March 2020 opening, preceding
  the adopted instrument statement. Cover and complete relevant PDF page were
  text-read and visually inspected; draft discovery files were not used.
- Hayama: the [2025 fire annual](https://www.town.hayama.lg.jp/material/files/group/20/shoubounennpou7.pdf),
  printed page 72 / PDF page 88, explicitly lists one prefectural intensity-meter
  set in the operations/disaster-radio room, with the inventory dated 1 April
  2025. Cover and complete inventory page were text-read and visually inspected.
  The [current municipal statistics page](https://www.town.hayama.lg.jp/soshiki/shoubousoumu/1304.html),
  updated 31 March 2026, publishes the headquarters address. The inventory room
  may identify processing/display equipment; no outdoor sensor room or floor
  is asserted.
- Atsugi and Nihonodori: the [current prefectural plan index](https://www.pref.kanagawa.jp/docs/j8g/cnt/f5150/documents/jishin4.html)
  links the [network diagram](https://www.pref.kanagawa.jp/documents/85497/j4-1.pdf),
  printed page 743 / PDF page 1. Its explicit prefecturally installed meter-site
  box lists both 総合防災C and 県庁. The complete diagram was text-read and
  visually inspected. The [center's official profile](https://www.pref.kanagawa.jp/docs/u5r/cnt/f550/tabi-095.html)
  supplies Shimotsukoku 280. The separate Atsugi Nakamachi station was unchanged.
  For Nihonodori, the [government offices' access guide](https://www.pref.kanagawa.jp/access/index.html)
  gives a general 日本大通1 address and separate building guides, not the exact
  sensor parcel. Neither that contact address nor the diagram's separate West
  Building server label was promoted into a sensor address/building. Its exact
  address remains blank.

All five retain full source URLs and qualifications in JSON. English facility
labels are descriptive; approved Yumoto, Fukaya Naka, Horiuchi, Shimotsukoku and
Nihonodori names are unchanged. No fresh physical inspection, exact sensor move
date, or precise within-site sensor position is claimed.

### Latest saved Mie progress

- `2456131` (approved name **Mihama Atawa**): recorded 御浜町役場 / Mihama
  Town Hall at `三重県南牟婁郡御浜町大字阿田和6120番地1`. The
  [current municipal index](https://www.town.mihama.mie.jp/soshikikarasagasu/somuka/kurashinojoho/3/177.html),
  updated 4 June 2026, links the adopted March 2024
  [earthquake/tsunami plan](https://www.town.mihama.mie.jp/material/files/group/14/jisintunamibousai.pdf).
  Complete printed page 84 / PDF page 90 explicitly states that the town's
  intensity comes from the Town Hall meter. A separate official
  [facility-address inventory](https://www.town.mihama.mie.jp/material/files/group/2/011003.pdf),
  dated 1 April 2023, pairs the named hall with that address; the
  [current Town Hall page](https://www.town.mihama.mie.jp/gyoseijoho/mihamachoyakubanitsuite/index.html)
  corroborates it. The plan cover, complete relevant page and complete address
  inventory were text-read and visually inspected during research. The separate
  JMA Teradani General Park station `2456100` was unchanged. No room, sensor
  position, physical inspection or exact installation/transfer date is claimed.
- `2421637` (approved name **Shijukucho**): saved relocation evidence only.
  The [18 June 2019 JMA notice](https://xml.kishou.go.jp/20190618_jmaxml_info.pdf),
  PDF page 3, explicitly pairs former `2421630` 伊賀市上野丸之内 with current
  `2421637` 伊賀市四十九町. The code-table change took effect at noon on
  18 July 2019; this is not proof of the physical transfer date. The
  [municipal procurement index](https://www.city.iga.lg.jp/0000006028.html)
  links the [2018 relocation contract](https://www.city.iga.lg.jp/cmsfiles/contents/0000006/6028/2018001148kekka.pdf),
  which gives 四十九町外地内, not an individual sensor parcel. The
  [fiscal 2019 audit](https://www.city.iga.lg.jp/cmsfiles/contents/0000001/1299/1teikikansa.pdf),
  printed page 9 / PDF page 11, records that named project under carried-over
  hall-relocation expenses, fully spent as of 30 September 2019. Full spending
  does not establish the ground-sensor destination; network relocation can also
  concern processing/display equipment. The JMA cover/table and complete audit
  page were text-read and visually inspected; both contract pages were text-read.
  The [current plan index](https://www.city.iga.lg.jp/0000003257.html) links the
  [February 2026 earthquake plan](https://www.city.iga.lg.jp/cmsfiles/contents/0000003/3257/2602_tiiki_shinsai.pdf)
  (256 pages) and [April 2026 first appendix](https://www.city.iga.lg.jp/cmsfiles/contents/0000003/3257/2602_tiiki_si1.pdf)
  (229 pages); complete normalized-text searches found no named instrument host.
  This is not complete visual inspection. Facility/address fields remain blank,
  locality precision is unchanged, and neither the old hall parcel nor the
  current City Hall's general address was assigned.

Both saved records retain their earlier notes and URLs. Approved English/Japanese
station names, coordinates, order and all unrelated fields were preserved by the
research guards; the latest address/precision check reported zero mismatches.

The sixty-six applied guarded placement-update scripts are
`update_yorii_yokoze_intensity_addresses.mjs`,
`update_ranzan_town_hall_intensity_address.mjs`,
`update_fujimino_main_hall_intensity_address.mjs`,
`update_kasama_main_hall_intensity_address.mjs`,
`update_yuki_city_hall_intensity_address.mjs`,
`update_toride_fire_headquarters_intensity_address.mjs`,
`update_moriya_iwama_intensity_addresses.mjs`,
`update_kasama_regional_branch_intensity_address.mjs`,
`update_oarai_school_intensity_address.mjs`,
`update_shimotsuma_new_hall_intensity_address.mjs`,
`update_yaita_nasu_sakura_intensity_addresses.mjs`,
`update_oizumi_new_hall_intensity_address.mjs`,
`update_nikko_main_kuriyama_intensity_addresses.mjs`,
`update_shimotsuke_library_intensity_addresses.mjs`,
`update_nakagawa_bato_new_hall_intensity_address.mjs`,
`update_mitake_town_hall_intensity_address.mjs`,
`update_inabe_three_intensity_addresses.mjs`,
`update_yamagata_takatomi_cable_tv_intensity_address.mjs`,
`update_taketa_beppu_city_hall_intensity_addresses.mjs`,
`update_hita_main_regional_office_intensity_addresses.mjs`,
`update_usa_main_ajimu_intensity_addresses.mjs`,
`update_kanzaki_chiyoda_branch_intensity_address.mjs`,
`update_motosu_hayano_new_hall_intensity_address.mjs`,
`update_yokohama_public_works_intensity_addresses.mjs`,
`update_yokohama_fire_host_intensity_addresses.mjs`,
`update_yokohama_school_water_marina_intensity_addresses.mjs`,
`update_nerima_fire_host_intensity_addresses.mjs`,
`update_akigawa_hinatawada_intensity_addresses.mjs`,
`update_adachi_shinmei_nishiarai_intensity_addresses.mjs`,
`update_okegawa_kitamoto_hall_intensity_addresses.mjs`,
`update_awaji_gunge_ichinomiya_intensity_address.mjs`,
`update_shiso_ichinomiya_chikusa_intensity_addresses.mjs`,
`update_nagaokakyo_hall_intensity_address.mjs`,
`update_ono_amagasaki_kasai_intensity_addresses.mjs`,
`update_kamigori_hall_intensity_address.mjs`,
`update_chofu_hall_tsutsujigaoka_intensity_addresses.mjs`,
`update_kita_disaster_center_intensity_address.mjs`,
`update_nikaho_three_hall_intensity_addresses.mjs`,
`update_minamisoma_kashima_intensity_address.mjs`,
`update_kagamiishi_hall_intensity_address.mjs`,
`update_koori_yachi_hall_intensity_address.mjs`,
`update_futaba_nagatsuka_hall_intensity_address.mjs`,
`update_tama_inagi_intensity_addresses.mjs`,
`update_tokorozawa_namiki_hall_intensity_address.mjs`,
`update_satte_miyashiro_hall_intensity_addresses.mjs`,
`update_ina_chuo_hall_intensity_address.mjs`,
`update_yoshioka_shimonoda_hall_intensity_address.mjs`,
`update_hinokage_nanaori_hall_intensity_address.mjs`,
`update_ujitawara_tachikawa_hall_intensity_address.mjs`,
`update_kyoto_kita_fire_intensity_address.mjs`,
`update_kato_kotaka_tenjin_intensity_hosts.mjs`,
`update_takasago_arai_hall_intensity_address.mjs`, and
`update_nogi_marubayashi_hall_intensity_address.mjs`,
`update_chichibu_kumakicho_hall_intensity_address.mjs`, and
`update_ota_hamacho_hall_intensity_address.mjs`,
`update_oshino_shibokusa_hall_intensity_address.mjs`, and
`update_ogori_city_hall_intensity_address.mjs`,
`update_hakone_yumoto_hall_intensity_address.mjs`,
`update_ayase_fukayanaka_fire_intensity_address.mjs`,
`update_hayama_horiuchi_fire_intensity_address.mjs`,
`update_atsugi_shimotsukoku_disaster_center_address.mjs`, and
`update_nihonodori_prefectural_offices_meter_host.mjs`, and
`update_mihama_atawa_hall_intensity_address.mjs`,
`update_taito_hall_nihonzutsumi_intensity_addresses.mjs`,
`update_meguro_chuo_sumida_inventory_addresses.mjs`, and
`update_six_tokyo_fire_inventory_addresses.mjs`, all under
`tools/station_names/`. Each was dry-run and applied during research; do not
reapply them to already-populated records.

The separate `record_shinonsen_yu_placement_conflict.mjs`,
`record_doshi_kamanomae_relocation_evidence.mjs`,
`record_iga_shijukucho_relocation_evidence.mjs`,
`record_inabe_fujiwara_host_ambiguity.mjs`, and
`record_mimasaka_mirai_relocation_evidence.mjs`, and
`record_arakawa_disaster_center_continuity_evidence.mjs`, and
`record_yashio_planned_meter_transfer_evidence.mjs`,
`record_oita_maizuru_fire_bureau_continuity_gap.mjs`, and
`record_tokai_completed_meter_move_without_destination.mjs`, and
`record_three_miyazaki_historical_host_address_gaps.mjs` were also dry-run and applied
once. They change only notes and source URLs, not placement coverage; there are
  96 applied guarded scripts, comprising 82 placement scripts and 14 provenance-only
scripts.

The last full research JSON comparison against HEAD confirmed the preceding
98 changed station records. The 127 subsequent additions were individually
guarded and validated during research, giving 225 cumulative promotions by
arithmetic; no new full comparison or coverage recount is claimed here. Changes
are limited to placement/provenance fields; English and Japanese station names,
coordinates, record order and all existing source URLs are preserved. The latest
research address/precision validation reported zero mismatches. The
earlier documentation update checked the 16 newly saved placement records, their three
applied scripts and Yashio's provenance-only record/script without running any
update script. That checkpoint advanced coverage arithmetically by 8 + 7 + 1: 3,673 to 3,689
exact addresses, 687 to 671 municipality-or-ward-precision records, and 682 to 666 remaining
facility/address-research records. No coverage recount was performed. The
facility-only subset remains five. That checkpoint added four scripts
(three placement and one provenance-only) and 16 promotions. The immediately
preceding checkpoint added two saved provenance-only scripts: 84 + 2 = 86 scripts,
with zero new addresses, facilities or promotions. The preceding checkpoint added Kawasato's
placement script and the three-record Miyazaki provenance script: 86 + 2 = 88
scripts, 77 + 1 = 78 placement and 9 + 1 = 10 provenance-only; 220 + 1 = 221
promotions. Coverage advanced to 3,690 exact addresses, 670 municipality/ward
records and 665 still needing facility/address research, with five facility-only
records unchanged. No coverage recount was performed. Kawasato's latest placement
script and the previous three Saitama placement scripts are listed above in
addition to the earlier script list here; all are applied, not pending.
The preceding checkpoint added Hommachi's Yoyogi fire-branch placement script,
listed in its dedicated section above: 88 + 1 = 89 scripts,
78 + 1 = 79 placement and ten provenance-only; 221 + 1 = 222 promotions.
Coverage advances by arithmetic only to 3,691 exact addresses, 669
municipality/ward records and 664 still needing facility/address research;
the five facility-only records were unchanged at that checkpoint. Since then,
Senju's address and Akabane's facility-only identity add two promotions and
two placement scripts; Mimasaka, Onojo and Yamanakako add three provenance-only
scripts. Thus 89 + 5 = 94 scripts (81 placement, 13 provenance-only),
222 + 2 = 224 promotions, 3,691 + 1 = 3,692 exact addresses,
669 - 1 = 668 municipality/ward records, 5 + 1 = 6 facility-only records,
and 664 - 2 = 662 still without a facility or exact address. Since that checkpoint,
Ohtawara's address/provenance script and Fujisato's provenance-only script advance
94 + 2 = 96 scripts (82 placement, 14 provenance-only), 224 + 1 = 225 promotions,
3,692 + 1 = 3,693 exact addresses, 668 - 1 = 667 municipality/ward records,
and 662 - 1 = 661 still without a facility or exact address. The six facility-only
records are unchanged. No update script
was run for this documentation request and no coverage recount was performed.
Oita's continuity qualification, Tokai's move-without-destination evidence,
Yashio's planned-transfer note, Arakawa's continuity note, Shinonsen's conflict note, the Doshi/Iga relocation notes, Fujiwara's host
ambiguity evidence, Mimasaka's relocation/planned-host evidence and the three
Miyazaki historical/current-address qualifications do not affect
coverage.
This checkpoint changes only this
document and its index; it does
not build an APK, regenerate its projection, or alter the workbook or release
metadata.

### Latest search-only handoff — no further placement changes

Search-only checks remain distinct from the saved Ohtawara and Fujisato changes
above. Mimasaka's completed GIS lookup is also saved provenance; Gifu/Hashima's
latest access and relocation checks are handoff only, not additional JSON saves
or fresh verification of all earlier placements.

- **Tochigi: Ohtawara annual-report check completed; Kanuma still needs a destination.** Ohtawara's
  [FY2019 municipal annual report](https://www.city.ohtawara.tochigi.jp/docs/2019120200014/file_contents/R1shiseinennpou.pdf)
  ([municipal archive copy](https://adeac.jp/viewitem/otawara-city/viewer/viewer/ad00000006/data/ad00000006.pdf))
  printed page 61 records 大田原市震度計バリカー設置工事, 410,400 yen.
  The full relevant pages have now been read and visually checked as described
  above, without a work-site association. Honcho is saved from separate
  city-published measurement testimony; Yuzukami still needs unequivocal host
  evidence. Do not repeat the annual report as a preview-only lead.
  Kanuma's [2017 property inventory](https://www.city.kanuma.tochigi.jp/manage/contents/upload/594b64a3ab782.pdf)
  has an indexed 震度計 item among office furnishings; the complete row has
  not been inspected and could describe a display/processor. It predates
  later rebuilding and does not close `0920532` 今宮町's saved relocation gap.
  Seek the actual post-move sensor destination rather than repeat the
  previously searched 2022 committee volume. No Kanuma placement or JSON edit resulted.
- **Sano: full FAQ answer read, localities only.** The
  [2021 municipal answer](https://www.city.sano.lg.jp/soshikiichiran/sougou/toshibrandsuishin/gyomuannai/koho/6/r03/r0302/17197.html)
  names 高砂町、田沼町、葛生東、中町, but gives no host facility/address.
  The footer at 高砂町1 is not installation evidence. No promotion or JSON save.
- **Toshima Minamiikebukuro `1311631`: current adopted-plan text searches completed.**
  The [current index](https://www.city.toshima.lg.jp/514/kuse/shisaku/shisaku/kekaku/documents/documents/2303131135.html)
  supplies replacement [main part 2](https://www.city.toshima.lg.jp/documents/49964/20260714175811.pdf)
  (354 pages) and [appendix](https://www.city.toshima.lg.jp/documents/49964/20260714180512.pdf)
  (119 pages). Full whitespace-normalized native-text searches found no
  individual sensor host. Main-plan PDF page 154 describes an office display,
  electric/mechanical sensors and Tokyo-to-JMA transmission; a preceding
  main-office radio statement does not locate the sensor. The appendix had
  no instrument hits. Low-text pages were not all visually/OCR examined.
  Old SK-net TKY1160 at 生活産業プラザ in Higashiikebukuro is not this
  Minamiikebukuro station; the 2015 office opening is not meter-transfer proof.
  No promotion or JSON save; do not repeat the superseded PDF's 404.

- **Mimasaka Mirai `3321538`: named-feature lookup completed, obsolete host only.** Okayama's official
  [disaster-map entry](https://www.gis.pref.okayama.jp/pref-okayama/Agreement?IsPost=False&MapId=7&RequestPage=%2Fpref-okayama%2FPositionSelect%3Fmid%3D7)
  is accessible through its normal free-viewing agreement, without login.
  Terms were read: the seismic layer describes instrument installation places,
  but data can be dated and address search is approximate. No marker, parcel
  or coordinate inference is permitted. The map's public layer tree identifies
  `震度情報NWS`, layer `26`, field set `324`, category
  `26-125-1-1006`, with open-data download enabled. The selected named search
  returned feature `4897-26`, 美作市役所 at 美作市栄町38-2, whose full attributes
  were read and saved in provenance. The 美来 all-attribute search returned
  zero records. This is the former site, not current Mirai placement. The seismic layer's
  currency has not been established; the shelter layer's March 2023 date is
  not its date. The successful normal endpoint was `InfoSearch/Search`,
  `mtl=26`, `mcl=26,125,1,1006`, `skwt=1` for facility-name 美作 and
  `skwt=0` for all-attribute 美来. `Map/GetAttribute` returned no further record;
  that does not erase the named-search attributes. The public bundle is
  `/v/common/Scripts/bundles/scripts.plugin.js`; the map root is
  `https://www.gis.pref.okayama.jp/pref-okayama/`.
  Normal agreement flow is GET `Agreement`, POST `Agreement/Agree` with
  `MapId=7`, then GET `Map?mid=7`, retaining only ephemeral viewing cookies.
  A named current host and independently published address are required before
  saving; an old Sakaemachi record or the new hall's contact address alone
  cannot establish the completed meter placement. Do not bulk-import the map.
- **Funabori `1312352`: no continuity proof.** The dated public inventory calls
  the host 江戸川消防署船堀出張所, while the current
  [Kasai Fire Station guide](https://www.tfd.metro.tokyo.lg.jp/fs/kasai/index.html)
  names 葛西消防署船堀出張所 at 船堀6丁目11番17号.
  The station history (new Kasai headquarters in August 2000 and a Funabori
  special unit in December 2004) and the ward facility guide do not explicitly
  establish branch/meter continuity or a completed transfer. The metropolitan
  organization rules inspected do not list the individual Funabori branch;
  their general effective-date clause is not transfer evidence. No promotion.
- **Ota Kamata `1311130`: instrument identity remains uncertain.** The public
  SK-net Tokyo-government inventory names 大田区役所 for `TKY1110` with a
  地下４階 remark. The current hall guide publishes 蒲田5-13-14, but the
  basement instrument must not be assumed to be this JMA reporting meter.
  The 30-page 2020 disaster-information-system basic plan lists five ward
  instrument locations only as a count; inspected text portions do not name
  the individual host. This was not a complete visual examination. No promotion.
- **Fukushima adopted plans: native-text searches completed without new hosts.**
  [Tamura's current plan index](https://www.city.tamura.lg.jp/soshiki/8/tamura-tiikibousaikeikaku2.html)
  supplies the March 2026 plan (642 pages) and appendix (63 pages); complete
  PDFium text searches found only generic network/reference material, not
  named branch hosts. [Motomiya's July 2022 plan](https://www.city.motomiya.lg.jp/soshiki/38/79.html)
  (328 pages) and appendix (134 pages), Otama's March 2021 plan (359 pages),
  and Samegawa's March 2023 plan (308 pages) likewise gave no named host in
  full native-text searches; Samegawa's 37-page appendix only had the intensity
  scale. Otama's 57-page appendix and Mishima's 121-page plan / 64-page
  appendix had web-text checks, not complete native-text/visual inspections.
  These are text-search negatives, not proof that every scanned page lacks
  evidence. Seek direct installation/maintenance or transfer sources next.
- **Akita: Noshiro and Yokote text checks completed.**
  [Noshiro's adopted plan index](https://www.city.noshiro.lg.jp/kurashi/bosai-shobo/chiiki-bosai-keikaku/25454)
  supplies the 134-page earthquake volume and 234-page appendix. Complete
  PDFium text searches found a count of five city instruments and a prefectural
  location map (PDF page 11 / printed 389), but no five-host table or direct
  host for Futatsuimachi Kamidai `0520233`. The office contact address is not
  instrument proof. [Yokote's March 2026 plan](https://www.city.yokote.lg.jp/kurashi/1001136/1001183/1002952.html),
  earthquake volume `r0803-3.pdf` (40 pages) and appendix
  `siryou-honbun-r804.pdf` (469 pages), was fully searched as native text;
  hits were generic early-warning/rain-gauge material, not seismic hosts.
  Do not repeat these as uninspected leads. Kamikoani's plan index was opened
  but its PDF was not recovered or searched. The Akita 2017 network tender's
  site inventory remains unrecovered; budget aggregates are not the inventory.
- **Ebino `4520930`: qualify the former uninspected-appendix handoff.**
  From the [April 2025 plan index](https://www.city.ebino.lg.jp/gyosei_shisei/keikaku_seisaku/2087.html),
  the 61-page laws/rules and eight-page transport/public-facility appendices
  were fully native-text searched without host evidence. The 181-page agreement
  PDF (`009`, approximately 16.5 MB) was downloaded into memory and searched
  using two extractors, but most pages are image-only: no conclusive whole-volume
  visual/OCR search has been done. Inspect scanned agreements selectively or
  find a direct municipal instrument source. K-NET MYZ009's City Hall parcel
  remains evidence for a different station, not the municipal reporting meter.
- **Other completed checks:** the Panasonic Yamagata case concerns already-saved
  Takatomi `2121532`, not remaining Daimon `2121531`. Do not reuse it for Daimon.
  Gifu's third-party maintenance attachment remains registration-gated; the
  official public system is login-free, with weekday hours documented below.
  No access bypass or registration was attempted. No station source, placement or count changed
  from these search-only checks. Research evidence temporary files have been
  removed; no temporary PDF/image cleanup remains pending.

### Earlier research completion log (retained)

- Mimasaka `3321538` (Mirai): saved notes and four evidence URLs in the JSON
  using `record_mimasaka_mirai_relocation_evidence.mjs`, dry-run and applied
  once during research. The [JMA code-change notice](https://xml.kishou.go.jp/20250624_jmaxml_info.pdf),
  complete PDF page 4, pairs former `3321533` Sakaemachi with Mirai as relocated
  observations. Page 2's 24 July 2025 at 12:00 is the code-table update time,
  not the instrument move. The [December 2025 Okayama report](https://www.data.jma.go.jp/okayama/jishin/geppou/202512.pdf),
  complete printed page 5 / PDF page 6, records Sakaemachi ending on 4 May 2025
  at 09:00 and Mirai starting on 17 June 2025 at 16:15. Both complete evidence
  pages were text-read and visually inspected. The public nine-page
  [basic-design summary](https://www.city.mimasaka.lg.jp/material/files/group/6/kihonnsekkeigaiyou.pdf)
  has a visually checked September 2022 cover; complete drawing A-03 / PDF
  page 5 was visually inspected and explicitly labels 地震計 on the planned
  new City Hall grounds. The [publication page](https://www.city.mimasaka.lg.jp/shisei/shiyakusho/new_chosha/shinchoshakensetsu/3717.html)
  describes a basic design preceding detailed implementation design. This is
  planned-host evidence only: no public as-built drawing, completed transfer
  record or current host inventory has yet tied the installed reporting meter
  to that site. Facility/address fields remain blank; neither the old parcel
  nor the new hall's general contact address was assigned. Research validation
  preserved all names, coordinates, row order, unrelated fields, prior notes
  and URLs, with zero address/precision mismatches. This documentation update
  only inspected the saved record/script; it did not rerun the applied script.
- Saga: the complete normalized text of the [FY2018 annual report](https://www.pref.saga.lg.jp/kiji00367810/3_67810_134830_up_ejb3cqt6.pdf)
  (113 pages) was searched. Complete PDF page 109 / printed page 101 contains
  table 41-3's aggregate network diagram, not individual meter hosts. The
  contents identifies table 41-2 as radio-communications facilities, not a
  seismometer inventory. No placement was saved. The FY2026 tender appendix
  previously recorded below is also aggregate/blank forms; its specification,
  diagram and live parent endpoints returned 404. Do not repeat the annual
  report or treat communications-office addresses as ground-sensor hosts.
- Kasuga `4021831` (Haramachi): the complete normalized text of the official
  [appendix](https://www.city.kasuga.fukuoka.jp/_res/projects/default_project/_page_/001/003/203/050902siryou.pdf)
  (424 pages) and [earthquake volume](https://www.city.kasuga.fukuoka.jp/_res/projects/default_project/_page_/001/003/202/0509jishin.pdf)
  (171 pages) was searched. Hits were generic intensity/early-warning
  definitions, not a direct individual host. The current official plan index
  was checked; no facility/address was assigned from the shared Haramachi
  locality. These were text searches, not complete visual inspections.
- Sumida: the [March 2026 plan index](https://www.city.sumida.lg.jp/kuseijoho/sumida_kihon/ku_kakusyukeikaku/bousaiplan_r2.html)
  was checked. Complete normalized-text searches of the 30-page information
  [appendix](https://www.city.sumida.lg.jp/kuseijoho/sumida_kihon/ku_kakusyukeikaku/bousaiplan_r2.files/05bessatu2026.pdf)
  and 18-page communications [chapter 7](https://www.city.sumida.lg.jp/kuseijoho/sumida_kihon/ku_kakusyukeikaku/bousaiplan_r2.files/09honsatu2026.pdf)
  yielded no individual host; the latter only mentions the fire-service
  network generically. This does not claim the entire plan was searched or
  visually inspected. A separate building-response sensor installation at the
  ward office is not proof of the regional reporting station's host.
- Chuo: complete text searches of the 454-page [adopted main plan](https://www.city.chuo.lg.jp/documents/2898/r6honpen.pdf)
  produced no usable individual host, but the Japanese text layer is partly
  misencoded. This is not a definitive absence finding or a complete visual
  inspection. The cleaner alternate revision material and appendix remain
  uninspected leads below. Generic system-renewal budgets identify no host.
- Fukushima / Izumizaki: complete normalized-text searches of the 355-page
  main plan and 53-page appendix found only generic observation information,
  not an individual meter host. No placement was saved. Seek an installation
  inventory or completed relocation record rather than repeating those volumes.
- Other Tokyo checks supplied no new placement: Minato's own City Hall meter
  is explicitly distinct from the JMA-reported intensity and does not identify
  the Minami-Aoyama or Shirokane hosts. Shinjuku's four-meter article remains
  aggregate information. The 2021 Tokyo Gakugei paper's old Kokubunji/Koganei
  hosts must not overwrite already-addressed, relocated current stations.
  A 2006 reference to Shibuya's old hall meter does not establish continuity
  at the redeveloped/current hall. Already-covered Dazaifu and Sagamihara
  records were left unchanged.

### Research retained from the preceding documentation checkpoint

- Saved Fujiwara evidence is detailed below. This is a provenance-only update,
  not a newly verified placement or an unresolved English name.
- Akita: Ikawa's complete [main plan](https://www.town.ikawa.akita.jp/uploaded/attachment/2126.pdf)
  (270 pages) and [appendix](https://www.town.ikawa.akita.jp/uploaded/attachment/2127.pdf)
  (154 pages) were searched in normalized text. The hits concern prefectural
  maintenance, early warning, weather instruments or generic definitions, not
  an individual reporting-meter host. Mitane's 74-page `ccccc.pdf` similarly
  supplies localities/generic information, not the missing hosts. Kosaka's
  current March 2025 earthquake-plan link was recovered at
  `material/files/group/2/7-5_kosakamachi_tiikibousaikeikaku_jishinsaigaitaisaku.pdf`;
  the older municipal-hall observation is not a current bundled station. The
  historical Kitaakita Ani Ginzan branch observation is also absent from the
  current catalogue; the four current Kitaakita records already have addresses.
  Do not add off-catalogue stations or repeat these completed checks. No Akita
  placement was saved; the 2017 network-update site inventory remains a lead.
- Gifu: complete normalized-text searches of Hida's February 2026 plan
  `33515.pdf` (303 pages), Takayama's July 2026 adopted earthquake/general
  volumes `chiboukei2026jishinn.pdf` (280 pages) and
  `chiboukei2026ippann.pdf` (348 pages), and Gero's `22669.pdf` appendix
  (303 pages) found no new direct host for the unfilled stations. These are
  completed text searches, not complete visual inspections. Seek installation
  inventories or completed relocation evidence instead of repeating them.
- Chiba: the current [prefectural earthquake plan](https://www.pref.chiba.lg.jp/bousai/documents/r7honpen2.pdf)
  (258 pages), complete text of printed 地-2-63 / PDF page 76, describes
  pre-merger main-office buildings/grounds as the **basic** placement policy,
  with existing instruments used where appropriate. The historical
  [plan](https://www.pref.chiba.lg.jp/bousai/documents/3-02.pdf)
  (221 pages), printed 地-2-61 / PDF page 73, predates later upgrades and moves.
  Neither establishes today's host for an individual unfilled branch station.
  The [network overview](https://www.pref.chiba.lg.jp/bousai/saigaitaisaku/jouhou/shindo.html)
  is also not an individual host inventory. No branch placement was promoted
  from this general rule. The relevant plan pages were text-read, not visually
  inspected in this pass.
- Narita: the [official plan index](https://www.city.narita.chiba.jp/anshin/page070300.html)
  links the [common volume](https://www.city.narita.chiba.jp/content/000195809.pdf)
  (120 pages) and [appendix](https://www.city.narita.chiba.jp/content/000142083.pdf)
  (660 pages). Complete normalized-text searches found only generic network
  equipment updates in the common volume, and no instrument-host hits in the
  appendix. Neither identifies the hosts for `1221134` or `1221135`; no
  placement was saved. Do not repeat these two completed volume searches.
- Mimasaka `3321538`: the public [29 June 2023 construction-committee material](https://www.city.mimasaka.lg.jp/material/files/group/6/chousyasiryou22.pdf)
  (eight pages) was searched; complete PDF page 3 was text-read. PDF page 2
  explains that detailed designs require a password request to the city, and
  page 3 lists the protected design categories. No direct instrument host was
  found. Do not bypass the restriction or contact the city without authority.
  The public basic-design summary linked from the
  [official design page](https://www.city.mimasaka.lg.jp/shisei/shiyakusho/new_chosha/shinchoshakensetsu/3717.html)
  was subsequently inspected as detailed above; its planned layout still
  needs completed-placement evidence. The
  [24 June 2025 JMA code-change notice](https://xml.kishou.go.jp/20250624_jmaxml_info.pdf)
  was subsequently inspected and saved as relocation provenance, not a host
  assignment. The general City Hall contact address at 美来1番地 does not by
  itself identify the sensor host. The previously checked JMA observation
  start/stop dates remain recorded below.
- Fukushima: the latest general installation-information page and prefectural
  network-diagram previews do not identify individual hosts. The statement that
  **most** prefectural meters are on municipal-office grounds is insufficient
  for a specific station. The next municipal-source batch was selected but not
  completed: Fukushima Iino/Goronaicho, Sukagawa Hachimancho, Tamura's branches,
  Date, Motomiya and remaining village/town sites. No new Fukushima placement
  or provenance was saved in this pass. Use direct installation inventories or
  completed relocation records, not the unavailable procurement system.

Unfinished research to resume, not completed placements:

- Superseded Tokyo leads: Meguro's [FY2019 priority
  budget](https://www.city.meguro.tokyo.jp/documents/2262/si_04.pdf) previews an
  intensity-meter replacement; Taito's [FY2024 disaster-meeting material](https://www.city.taito.lg.jp/bosai/houshin/kaigi/index.files/siryou0628.pdf)
  previews a generic system diagram. Meguro Chuocho and Taito Higashi-Ueno /
  Senzoku are now saved from the direct sources documented above; these older
  budget/meeting previews need not be repeated. A manufacturer's Chuocho
  contact address is not the station host.
  Chuo's cleaner [revision material](https://www.city.chuo.lg.jp/documents/2712/04r6shusei.pdf)
  and [appendix](https://www.city.chuo.lg.jp/documents/2898/r6siryouhen.pdf) are
  now text-searched without direct host proof, retaining encoding limitations.
  Ota's
  [2023 contract summary](https://www.city.ota.tokyo.jp/jigyousha/keiyaku/nyusatsukanshiiinkai.files/0501soukatuhyou.pdf)
  previews a meter replacement but no host. A third-party November 2019 Akabane
  Fire Station relocation notice needs the official original and completed
  placement evidence. Display-panel relocation contracts are not sensor moves.
  Yoshinogari's [FY2026 budget](https://www.town.yoshinogari.lg.jp/material/files/group/6/R080100.pdf)
  previews a meter-relocation allocation on printed page 145, not a completed
  host assignment; seek the public work specification and completion record.
- Current search handoff — Mie, **unfinished leads separate from the saved
  Mihama placement and Iga provenance above**: the indexed [2019 contract list](https://www.pref.mie.lg.jp/common/content/000863144.pdf)
  and [large-contract summary](https://www.pref.mie.lg.jp/common/content/000863528.pdf)
  identify 三重県計測震度計等更新工事, 津市広明町13番地ほか78箇所,
  contracted on 7 October 2019 to Japan Radio's Chubu branch, with work through
  19 March 2021. These PDFs have not yet been fully read. Recover the original
  tender specification/site inventory; the contract's lead-site address and
  aggregate scope are not individual sensor addresses. The indexed
  [24 June 2008 prefectural committee record](https://www.pref.mie.lg.jp/KENGIKAI/27408009644.htm)
  was subsequently fully text-read: the questioner names a City Hall meter,
  while the response discusses grounds generally. Current Toba meter continuity
  remains unproved. Complete normalized-text searches of the March 2025 plan's
  [68-page appendix](https://www.city.toba.mie.jp/material/files/group/6/s06bkhonbun01.pdf)
  and [132-page response volume](https://www.city.toba.mie.jp/material/files/group/6/aq06bk30.pdf)
  found no instrument-host hits; these were not fully visually inspected.
  Do not assign today's hall address to `2421132` from the historical discussion.
  Tsu `2420145` (Hisai Myojincho) has
  only a third-party copy of a 2013 fire-dispatch construction specification as
  a lead; recover the official original and an explicit meter-host link before
  using the dispatch center's address. Generic prefectural committee discussion
  of municipal grounds, radio-network host lists, and already-addressed Kyoto
  University field-survey sites supply no new placement proof. The earlier
  Fujiwara and Yokkaichi leads below remain open. The complete normalized text
  of [Tsu's 2026 fire annual](https://www.info.city.tsu.mie.jp/_res/projects/default_project/_page_/001/003/221/syoubounennpoureiwahachinenn.pdf)
  (66 pages) and [Nabari's 2026 fire annual](https://www.city.nabari.lg.jp/s068/030/010/010/030/R8nennpou00.pdf)
  (47 pages) yielded no hits for 震度計, 地震計 or 計測震度. These were not
  complete visual inspections. The new central dispatch center's address is
  not a verified meter placement; Nabari `2420830` remains without a proven host.
- Remaining Kanagawa follow-up: complete normalized extracted-text searches of
  [Ebina's 2024 fire annual](https://www.city.ebina.kanagawa.jp/_res/projects/default_project/_page_/001/007/801/2024nennpou.pdf)
  (96 pages), [Zama's March 2026 appendix](https://www.city.zama.kanagawa.jp/_res/projects/default_project/_page_/001/012/416/R8.3_siryo.pdf)
  (142 pages), and [Zama's 2024 fire annual](https://www.city.zama.kanagawa.jp/_res/projects/default_project/_page_/001/012/552/14.pdf)
  (129 pages) yielded no instrument-host hits. This is not a claim of complete
  visual inspection. The prefectural diagram's other municipality labels are
  not named facilities; do not assign ordinary office addresses to them.
  Aikawa's obsolete `R6nenpou.pdf` returned 404, but its
  [current index](https://www.town.aikawa.kanagawa.jp/soshiki/syoubouhonbu/syoubou/soumu/info/1422277535815.html)
  was subsequently recovered and its [94-page annual](https://www.town.aikawa.kanagawa.jp/material/files/group/31/R7nenpou.pdf)
  completely normalized-text searched without instrument-host hits; not a full
  visual inspection. Remaining facility/address
  gaps are `1421530`, `1421631`, `1432130`, `1434131`, `1434230`, `1436631`,
  `1438331`, `1440131` and `1440230`. Ninomiya `1434230` has historical
  fire-station installation leads in the [2017 prefectural response](https://admin.c2-kanagawa.jp/wp-content/uploads/2025/11/h29-ken_syotijyoukyou.pdf)
  and [2015 original article](https://www.taro.org/2015/06/post-1610.php).
  The original article has now been read in full: it says the prefecture installed
  the meter at the fire station in 2010 and identifies 中里, but remains historical.
  The 2017 response's complete relevant page remains uninspected.
  The [current fire-building refurbishment notice](https://www.town.ninomiya.kanagawa.jp/0000002514.html)
  was read in full: it gives 中里711-1, planned August 2024–January 2026 works,
  continued fire operations on the grounds, and office return on 2 June 2025.
  It is not itself proof of sensor continuity or transfer. The public
  [SK-net Kanagawa inventory](http://www.sknet.eri.u-tokyo.ac.jp/OBS/obs_kanagawa.html),
  heading updated 10 July 2010, was also checked: the selected AIK, EBN, KIS,
  KYK, MNZ, NNM, OIS, SMK and ZAM rows have blank installation-facility columns.
  Their coordinates are not host-address evidence. The Onken facility directory
  describes a separate research network, not proof of these municipal hosts.
  No placements resulted. Kiyokawa disaster-HQ
  rooms, Kaisei shaking complaints and a water-utility sensor in another Oiso
  locality are not reporting-meter host evidence.
- Fujimi `1123530` (Tsuruma): the original field-photo article
  [十数年ぶりに富士見市へ（前篇）](https://yumeid.com/futa-blog-matiaruki-saitama-009/)
  was fully text-read and describes a cover marked 計測震度計 on the City Hall
  grounds. The author's connection to published intensity is speculation, not
  proof. The original photo URL was recovered but the image remains visually
  uninspected. The new-hall specification (13 pages), March 2024 basic plan
  (97 pages), site building/work list and Q&A (18 and 6 pages) supplied no direct
  reporting-meter destination. The procurement notice says detailed survey
  drawings are distributed on physical media at the office, not as an inspected
  public download. The March 2025 plan (314 pages) and March 2026 appendix
  (159 pages) likewise supplied no host. Planned hall rebuilding is not a completed
  instrument relocation. Do not assign Tsuruma 1800-1 from the hall address alone.
- Kanuma `0920532` (Imamiyacho): the indexed March 2020 committee record
  `5e814af3a7c71.pdf` explicitly describes a prefectural meter beside the main
  hall's south parking area and discusses moving it during rebuilding, but
  direct retrieval returned 404. The destination and continuity remain unknown.
  The September 2022 record `6417d646dcd3c.pdf` (72 pages) was subsequently
  text-searched and PDF page 15 fully read: it discusses moving five systems,
  including the prefectural intensity network, in fiscal 2023 but does not
  establish the sensor destination. No host/address was saved.
- Completed latest Saitama text searches: Hidaka's current linked general,
  earthquake and appendix volumes (50/222/244 pages); Yoshikawa's complete
  March 2024 plan (720 pages); Kawajima's `30sinnsai.pdf` (90 pages); and
  Sugito's March 2022 plan/appendix (531/160 pages) yielded no direct host for
  the missing stations. This was full extracted-text searching, not a claim
  to have visually inspected every scanned page. Kawajima's separate appendix
  files remain uninspected. Sugito's 23 April 2024 restoration notice also
  names no host. JMA's 30 August 2018 XML notice supplies codes/localities and
  relocation dates, not a new facility/address for these gaps.
  Subsequent complete text searches of Sakado's February 2025 plan/appendix
  (208/272 pages), Nagatoro's adopted March 2023 whole plan including appendix
  (546 pages), and Chichibu's 136-page appendix supplied no further host.
- Shiraoka follow-up: the councillor's 17 June and 1 August 2026 articles were
  subsequently fully text-read. They confirm installation progress/completion,
  but identify no host; they do not resolve the official-notice gap below.
  Saitama's 2022 network-rebuild tender
  and 2025 maintenance listing likewise have no inspected official host inventory.
- Completed Tochigi text searches: Shioya's adopted March 2025 common,
  earthquake-response and appendix volumes (122/40/127 pages); Motegi's
  December 2025 plan/appendix (330/91 pages); and Ichikai's linked plan/appendix
  (349/138 pages) supplied generic network material, not direct hosts. Tochigi
  City's current `70717.pdf` agreement names generic government buildings and
  includes the fire headquarters, without an individual station-site schedule;
  it does not justify assigning all six remaining stations to branch offices.
- Other completed negative checks: Miki's current `58709.pdf` plan (252 pages)
  identifies already-covered sites, not the missing Yokawa host; its
  `50078.pdf` appendix (76 pages) supplied none. Gojo's earthquake/appendix
  volumes (114/193 pages) supplied no Okaguchi host. Himeji's `hyouka4.pdf`
  evaluation (61 pages) gives network totals/equipment, not individual hosts.
  The recovered seven-page January 2010 Kyoto report in the National Diet
  Library gives station labels/providers only. Do not repeat these as
  uninspected host inventories.
- Earlier Saitama leads, now superseded: `1122930` (Wako Hirosawa), `1122730`
  (Asaka Honcho), and `1121931` (Ageo Honcho) are completed in the saved batches
  above using the explicit installation inventory plus independent addresses.
  Tokorozawa Namiki is also
  completed above from explicit council answers, not the earlier budget preview.
  Wako's official intensity FAQ, updated 18 January 2024, confirms one reporting
  point but names no host; its Hirosawa 1-5 department contact is not placement
  evidence.
  The public Saitama installation inventory has now been recovered and used;
  do not repeat that discovery search. Third-party tender listings and generic
  prefectural plan passages remain discovery leads, not verified inventories.
- Kawaguchi `1120333` (Angyo Ryoke): a third-party November 2023 tender title
  names an intensity-meter replacement/relocation at the East Fire Station's
  Angyo Branch. The listing and a March 2024 maintenance listing were retrieved,
  but no official supporting source or verified completed destination was
  recovered. Do not assign a facility from the tender title alone or repeat
  the same unsuccessful listing extraction.
- Higashichichibu `1136930` (Mido): an indexed new-hall mechanical drawing
  mentions a meter move, but `8998_51706_misc.pdf` returned 404 and was not
  fully inspected. The official construction update reports steelwork underway
  in August 2026 within works scheduled through August 2028, not a completed
  instrument move. Recover official drawings or completed relocation evidence;
  do not assign the current hall address merely from its contact details.
- Completed JMA Kobe follow-up: September–December 2020 monthly reports were
  checked. September's memo describes generic installation standards; October
  names the already-addressed Kobe meteorological office; November concerns
  historical observations; December supplies no additional host. The August
  report's Kamigori caption was already applied to `2848131`; do not reapply it
  or repeat these adjacent-report checks as unfinished leads.
- Kato Tojo `2822833`: the host is completed above, but official Tenjin parcel
  123/129 disagreement remains open. Seek authoritative clarification before
  filling its address; this facility-only record is not one of the 762 stations
  still lacking both a verified facility and an exact address.
- Completed Yoshioka follow-up: all four current appendix volumes were checked;
  the volume-1 agreement now supports the saved placement above. No separate
  equipment schedule was recovered. Do not repeat the former uninspected-draft
  lead or redownload all four large volumes merely to rediscover that agreement.
- Completed Hyogo negative checks: Kobe's current earthquake-plan volume
  `20260326112303.pdf` (95 pages) and general-plan volume `20260326112227.pdf`
  (239 pages) were searched in full for instrument/observation terms. They did
  not identify hosts for the unfilled municipal stations. Toyooka's official
  14 February 2024 council document `bousai06.02.14.pdf` was searched, with the
  relevant full pages read; its explicit Sakuramachi observatory reference is
  already covered, not evidence for the five unfilled municipal sites. Generic
  Kobe fire-facility addresses and Himeji's aggregate counts remain insufficient.
- Mimasaka `3321538` (Mirai): JMA Okayama's complete six-page December 2025
  report `202512.pdf`, final-page footnote, confirms that the former Sakaemachi
  observation ended at 09:00 on 4 May 2025 and Mirai began at 16:15 on
  17 June 2025. It does not name a host or address. Do not assign the new City
  Hall merely from its locality or the pre-move prefectural inventory. The
  apparent current procurement PDF `1024648_9899255_misc.pdf` and its parent
  page returned 404; no current installation inventory was inspected there.
- Completed Kyoto negative checks: Fukuchiyama's 2009 fire annual-report diagram
  `15795.pdf`, PDF page 61, was fully read and visually inspected; branch labels
  are telephone connections, not meter hosts. Its July 2026-published fiscal
  2025 annual report `87257_154090_misc.pdf` was searched in full without an
  intensity-meter hit. Do not infer the three unfilled branch hosts from that
  old diagram. The Kyoto University field-photo table's useful other hosts were
  already recorded; Ujitawara's old Araki location is superseded above.
- Kyoto source recovery: the old `siryou1.pdf` endpoint returns 404. Use the
  official disaster-plan index and public e-book library instead. The current
  `bk-siryou02` viewer exposes public `data/page.txt`, `data/txtsearch.txt` and
  `pdf/NNNN.pdf` pages; its observation inventory is page 38 (`0038.pdf`). The
  other listed sites are already covered; do not reapply them.
- Miyazaki follow-up: Hinokage is completed above. The other focused municipal
  searches and the 233-page 2016 JMA disaster-report search did not identify
  additional missing hosts. Generic hall addresses and planned/recent hall moves
  do not establish meter continuity. Kagoshima's three earlier facility-only
  records still lack verified current addresses: demolished former Shishijima
  and Ikarajima centers require site-continuity evidence, and Yoro Assembly Hall
  is not proven identical to the center published at parcel 383.
- Sosa `1223532` (Imaizumi): the complete 539-page official February 2023
  [combined disaster plan](https://www.city.sosa.lg.jp/data/doc/1678429704_doc_119_0.pdf)
  was searched for intensity-meter and seismometer terms. Relevant hits concern
  gas/railway sensors and generic prefectural network descriptions, not the
  municipal reporting station's host. Do not repeat this completed negative check.
- Maebashi `1020130` (Otemachi): an indexed fiscal 2026 City Hall operations
  specification mentions seismometer maintenance among exclusions, but its
  official PDF returned 404 and was not inspected in full. The preview alone
  does not distinguish a reporting meter from a building instrument; no host
  was assigned. Subsequently, the complete 696-page April 2026 disaster-plan
  appendix was searched; its agreement/equipment pages 173–176 describe generic
  city equipment, not a named reporting-station host inventory. The 227-page
  2011 field-report mirror's chapter 3 was also searched for Gunma hosts; the
  relevant named placement was already recorded, and its deployment-municipality
  list is not a host inventory. Do not repeat these completed negative checks.
- Tokyo follow-up: Shinjuku's 2 March 2026 official article gives four-meter
  totals, not individual hosts. A third-party 2019 Fussa Fire Station relocation
  tender preview names Fussa 1072 plus another site, not a verified completed
  destination for `1321852`; recover official relocation evidence before
  assigning it. Higashimurayama search material did not identify a direct host.
  A Fuchu City Hall host result concerned Hiroshima, not Tokyo; it must not be
  applied to `1320631`. No promotion was made from these checks.
- Iwaki `0720433` (Taira Umemoto): the 2011 national field-report lead names
  City Hall grounds but reports instrument damage, and JMA's November 2011
  change concerns earlier codes `0720431` to `0720432`. Establish continuity
  through the later move to current code `0720433` before assigning that host.
  The field-report PDF was not fully downloaded; this remains a discovery lead.
- Nantan's linked response and prevention chapters and April 2024 appendix were
  searched for intensity-meter hosts. The appendix's only meter reference is
  generic intensity information, not a facility inventory; no promotion was
  made. The response chapter has now also been checked without a specific host.
  Sonobe `2621336`
  needs direct evidence for its post-2024-move host. Recover current links from
  `https://www.city.nantan.kyoto.jp/www/gove/135/008/000/index_1006755.html`;
  the older indexed appendix returned 404 and should not be retried unchanged.
- Nagahama `2520340` (Hachiman Higashicho): the published City Hall address
  alone does not identify the meter host. Seek direct installation or completed
  instrument-relocation evidence; do not assign the hall from locality alone.
- Awaji Shizuki and Minamiawaji Minato have explicit 2013 field-survey hosts,
  but their former offices were subsequently demolished or redeveloped. Confirm
  instrument continuity before using a replacement facility or its address.
  Himeji's current meter page supplies aggregate counts, not individual hosts;
  its equipment image is a photograph, not a placement diagram.
- JMA's July 2024 and November 2022 XML code-change notices were checked but
  contain codes and names, not host addresses. Find their corresponding direct
  placement/address notices if continuing that route; do not redo the already
  applied July 2023 address-notice batch or infer hosts from code changes.
- Saitama City's earthquake plan directly describes instruments at city/ward
  offices, but supplies no individual host-to-station inventory for the nine
  unfilled city records. Do not apply this generic statement to all nine.
  Omiya and Iwatsuki station localities differ from current office localities;
  Minami also needs relocation continuity rather than an address-only match.
  Omiya's fiscal 2018 proposed relocation budget and fiscal 2019 generic
  relocation-results statement do not yet establish the named current host.
  The relevant plan is
  `https://www.city.saitama.lg.jp/001/011/015/003/001/p018122_d/fil/shinsaitaisaku.pdf`;
  PDF pages 27 and 45 were fully text-read. No promotion was made.
- Current Katori and Minamiboso earthquake plans/appendices were searched for
  instrument-host statements after recovering replacement links from their
  official parent pages. They supplied generic network/response information,
  not direct hosts. Chiba's 2002 annual-report installation table concerns a
  different strong-motion network, not the missing municipal reporting stations.
  No promotion was made from these sources; do not repeat the obsolete PDF
  downloads that returned 404.
- `1312153` (Adachi Senju Nakaicho): the 2005 ward newsletter predates Senju
  Fire Station's February 2010 move. Confirm the instrument moved before using
  the current station address. Chofu's two former leads are now completed above
  after recovery of the full relevant cached source text, not from its preview.
- Fukushima's 2024 network-upgrade tender gives aggregate scope, not an inspected
  site inventory. Its original public document-viewing system is reported down
  by the prefecture's 8 October 2026 notice, with no restoration date; use the
  agency's announced alternative publication route rather than repeatedly
  retrying that system. Yurihonjo's April 2026 plan search yielded no named
  instrument hosts. The LASCOM 2011 instrument photograph belongs to the
  already-addressed Nihonmatsu Iwashiro section, not the following Motomiya
  section; it supplies no new placement.
- `2421437` (Inabe Fujiwara): complete normalized text of PDF pages 44 and 52
  in the [fiscal 2024 evaluation report](https://www.city.inabe.mie.jp/_res/projects/default_project/_page_/001/014/543/r6zimuzigyouhyouka02.pdf)
  (362 pages, published August 2025) was read. The instrument-line inventory
  names 藤原第1分団詰所, but the same report's detailed brigade-facility inventory
  still lists two First Division sites, `藤原町市場125-4` and `藤原町坂本56-3`.
  Thus the ambiguity is not limited to the earlier fiscal 2019 inventory. These
  complete pages have now been rendered and visually inspected, and their
  evidence was saved to this station's JSON note/source URLs only. Printed
  page 30 / PDF page 44, form dated 25 July 2025, item 5, explicitly lists
  藤原第１分団詰所 among four instrument-line hosts. Printed page 38 / PDF
  page 52 lists five detailed Fujiwara brigade facilities despite a summary
  saying three; that discrepancy does not establish which building closed.
  Neither the 市場 locality nor a candidate building's address resolves the
  meter-bearing site. Facility/address fields and locality precision remain
  unchanged. Discovery previews of the
  [eighth administrative-reform committee material](https://www.city.inabe.mie.jp/_res/projects/default_project/_page_/001/014/851/gyoukaku8siryou.pdf)
  and [2024 design-contract result](https://www.city.inabe.mie.jp/_res/projects/default_project/_page_/001/014/162/170.pdf)
  suggest consolidation/new brigade premises; full relevant context and completed
  meter-transfer evidence remain unchecked. A design contract or planned closure
  is not a completed relocation. No Fujiwara placement was promoted;
  the other three Inabe placements are completed above.
- Akita discovery handoff: seek the original 2017 prefectural network-update
  tender/site inventory, not just aggregate budget amounts. Search previews of
  the [FY2016 budget](https://www.pref.akita.lg.jp/uploads/public/archive_0000071819_00/H28tousyo-gaiyou2.pdf)
  and FY2017 budget identify system-design/update spending, not individual hosts;
  relevant budget pages were not read. Kosaka's current official plan link has
  now been recovered, and Ikawa's main plan and appendix text searches are
  complete without host proof; see the completed-research checkpoint above.
  Do not retry the obsolete Kosaka PDF or repeat the two Ikawa searches.
  Initial still-unfilled targets include `0520233`, `0520632`, `0520633`,
  `0521133`, `0534631`, `0534832`, `0534834`, `0534932`, `0536631` and
  `0536831`. No Akita placement or provenance update was saved in this pass;
  earlier Ogata/Daisen searches below remain completed, not new leads.
- `2420232` (Yokkaichi Kusumachi Kita Gomeizuka): the official
  [fiscal 2022 building-maintenance report](https://www.city.yokkaichi.lg.jp/www/contents/1001000001638/simple/R4nendo.pdf),
  PDF page 16, has a site-plan intensity-meter label near Kusu Civic Center and
  Kusu North Kindergarten. Visual inspection found the meter label on the
  preconstruction plan, but did not establish the completed site's host boundary
  or a verified current Civic Center placement. The center's published address
  alone does not establish placement.
- `2120932` (Hashima): an official 2024 meter-relocation notice must be followed
  through to its completed destination before assigning the former or current
  City Hall address. A historical installation case study is not continuity
  evidence by itself.
- Nakagawa's separate Ogawa station and Nakatsugawa's Kashimo station retain
  relocation leads without verified completed destinations. No placement
  promotion has been made for these leads; Yamagata's cable-TV placement is now
  completed above.
- `0920133` (Utsunomiya Nakaokamoto): the official
  [2025 disaster-plan appendix](https://www.city.utsunomiya.lg.jp/_res/projects/default_project/_page_/001/003/239/r7.3_siryohen.pdf)
  links the 2015 move of the Shirasawa meter to the Kawachi centre relocation.
  The current centre guide publishes `宇都宮市中岡本町3221番地4`, but stronger
  direct destination/host evidence is still needed; the shared locality alone
  is insufficient. No placement promotion has been made.
  Sakai's original 2011 survey page `http://sakaiy.main.jp/113/008.htm` was
  fully text-read and identifies the former Shirasawa/Kawachi center host;
  it predates the 2015 move and does not establish the current destination.
- Recover the former official Mito station/facility table at
  `https://www.jma-net.go.jp/mito/eq/seismograph_list.html`, if possible.
  The current endpoint and the web-tool archive request did not yield the
  table. A third-party reference is only a discovery lead, not placement
  evidence. The current Mito station map does not replace the missing table.
- Focused Gunma searches and the prefectural network diagram yielded locality
  or generic network information only for the other candidates checked. Do not
  promote those records without direct instrument-to-host evidence.
  The March 2026 prefectural appendix (1,012 pages) was fully text-searched,
  including broader observation terms and its contents, without an individual
  meter-host inventory. Further complete extracted-text searches found no
  additional hosts in Ota's 128-page appendix, Numata's 85/93-page volumes,
  Isesaki's March 2025 plan (380 pages), Takasaki's March 2026 earthquake/appendix
  volumes (103/146 pages), Shibukawa's May 2026 volumes (200/124 pages),
  Tatebayashi's earthquake/appendix volumes (224/48 pages), Meiwa's March 2025
  appendix (99 pages), Chiyoda's March 2023 appendix (59 pages), or Oura's
  340-page draft-like plan (blank adoption date on its cover). These are text
  searches, not visual inspection of every page; do not redownload them as new
  leads or infer branch-office hosts from generic network diagrams.
- Okinawa's [September 2026 upgrade specification](https://www.pref.okinawa.lg.jp/_res/projects/default_project/_page_/001/041/409/03shiyousyo.pdf)
  describes server work and network totals, not individual placement addresses.
  The [Q&A appendix](https://www.pref.okinawa.lg.jp/_res/projects/default_project/_page_/001/041/409/06shitumonkaitour8dshindo.pdf)
  has image-only diagrams on PDF pages 3–7; all five have now been visually
  inspected and show server-room/rack information, not station-host addresses.
  The [fiscal 2026 maintenance tender](https://www.pref.okinawa.lg.jp/shigoto/nyusatsukeiyaku/1015342/1025066/1037591/1039485.html)
  advertised two source ZIP bundles, but both downloads returned 404 during
  research. A public tender-index preview lists `07_bessi1_kasyoitiran_R8dshindo.pdf`
  as a possible site inventory; recover an official copy before using it.
  No placement was promoted from network totals or the third-party preview.
- Gifu's [7 February 2025 procurement notice](https://www.kouhou.pref.gifu.lg.jp/gougai/pdf/25020703.pdf)
  identifies the April 2025–March 2030 intensity-network maintenance contract
  and directs readers to the prefecture's electronic procurement information
  system for its documents. Locate the official specification for tender
  `21111111520250001`; its public preview mentions a maintenance-target section,
  but no official station inventory has yet been inspected. No placement was
  promoted from the notice or a generic claim about meters at municipal halls.
  The official [bid index](https://www.pref.gifu.lg.jp/bid/search/bid_static_html_2.html)
  links the [public procurement information system](https://www.e-chotatsu.dentyo.pref.gifu.lg.jp/ppij/PpiJGyomuStart.do?kinouid=GP5000_Top&dantaiCd=21).
  The [official system specification](https://www.pref.gifu.lg.jp/uploaded/attachment/480178.pdf),
  PDF pages 4–5 / printed 2–3, explicitly distinguishes login-free bid-information
  viewing from authenticated bidding and gives public-information hours of
  **weekdays 06:00–23:00 JST**. The portal/e-portal timed out on Saturday
  17 October 2026, outside those published hours; this is not evidence of a
  registration requirement or permanent outage. Resume through the normal
  official route on an operating weekday, without an account or bypass.
  No official station inventory has been recovered; this remains an open lead.
  The complete four-page gazette was subsequently read. The separate public
  [CALS archive](https://www2.cals.pref.gifu.jp/qualified/ppi.html) was searched
  with its correct Shift_JIS encoding: its two intensity-related rows concern
  2024 server work at the prefecture/disaster-exchange centre, not meter hosts.
  The third-party listing previously returned 403 and the latest public request
  reached a viewing quota/registration offer; research stopped at that gate.
  Its search preview is not an inspected installation schedule. Hashima
  `2120932`: the [27 March 2024 restoration notice](https://www.city.hashima.lg.jp/5434.html),
  also linked from the [crisis-management index](https://www.city.hashima.lg.jp/1088.html),
  was fully read and confirms the City Hall meter was unavailable from 7 March
  for relocation and restored on 27 March, without
  identifying the destination. Panasonic's public City Hall installation case
  does not establish a post-move date; neither source supports assigning the
  current hall parcel yet.
- Yamanashi source-recovery qualification, correcting the preceding no-hit
  inference: `https://www.pref.yamanashi.jp/shobo/documents/88770017458.pdf`
  still returns 404 directly, and the cached 25-page chapter omits most diagram
  text. Subsequent search-index results expose its printed page 40 / PDF page
  24, 山梨県震度情報ネットワークシステム回線構成図, including Hokuto branch
  hosts (明野, 高根, 長坂, 大泉, 白州, 武川 and 小淵沢), Kai Futaba and southern
  branch offices. The earlier cached-text no-hits therefore do **not** establish
  a wrong source. This is an older diagram, with historical municipality names;
  full visual recovery and current host/relocation corroboration remain pending.
  Do not silently recertify the earlier Hokuto, Kai and southern Yamanashi
  assignments or reuse the diagram without checking continuity. No station
  field or coverage count changed from this discovery.
  The March 2025 prefectural plan (419 pages) was fully text-searched; its
  network description at PDF page 69 is generic, not a named host inventory.
  The March 2026 main/appendix contents were checked, but the full volumes
  remain unsearched. Hokuto's 2014 evaluation preview describes moves with
  branch offices, but the full document could not be retrieved. Kofu's 2008
  Kamikuishiki relocation tender is a lead, not verified present placement;
  the current Yamanakako plan likewise remains unread after failed retrieval.
  After Oshino's saved addition, seven Yamanashi gaps remain: `1920133`,
  `1920134`, `1921137`, `1936431`, `1942232`, `1942531` and `1943034`.
  Doshi's 2012 plan preview predates the now-verified JMA relocation above;
  the full older plan and the indexed March 2021 revision timed out. Seek an
  explicit destination or current installation source, not the old hall address.
- Latest unfinished Chiba search: Kashiwa `1221733` (`柏市大島田`, approved
  Oshimata) remains locality-only. The [15 July 2014 municipal newsletter](https://www.city.kashiwa.lg.jp/policy_pr/pr/e-book/h260715/HTML/index4.html),
  complete HTML page 4, advertises a children's visit to see a 地震計 at
  沼南庁舎 and a separate rain gauge at the fire station. This establishes a
  historical generic seismometer reference, not yet identity/continuity of the
  reporting intensity meter. The facility guide's published Oshimata 48-1
  address is not grounds for an assignment by itself. The [current plan index](https://www.city.kashiwa.lg.jp/kiki/anshinanzen/disaster/bosaikeikaku/bosaikekaku.html),
  updated 2 April 2026, identifies the adopted March 2025 revision. Its
  [353-page main plan](https://www.city.kashiwa.lg.jp/documents/45/03.pdf) and
  [204-page appendix](https://www.city.kashiwa.lg.jp/documents/45/02.pdf) were
  subsequently fully text-searched for 震度計, 地震計, 感震器 and 強震, with
  no hits. Main-plan PDF pages 54–55 and 109–111 were fully text-read and give
  generic communications information, not a host. This is not complete visual
  inspection of every page. Seek a different direct installation/continuity
  source rather than repeating this text search. Do not substitute a later
  revision proposal for the adopted plan.
  No Kashiwa JSON field was changed. Noda Higashihoshubana searches supplied
  hazard maps, not host evidence. JMA's [11 May 2023 Kisarazu field report](https://www.data.jma.go.jp/choshi/shosai/info/20230511_houdou4.pdf)
  was fully text-read: it discusses the Fujimi meter and nearby damage but
  names no facility/address. No placement was assigned to either station.
- Latest Nagahama check: the [current plan index](https://www.city.nagahama.lg.jp/0000001062.html),
  updated 14 April 2026, links replacement main/appendix PDFs, not the old
  `honpen.pdf`. The large main PDF could not be read (web size limit/direct
  403); the 222-page appendix gave no meter hits in cached-text searches,
  which is not a complete visual inspection. Ritto's March 2025 appendix,
  printed 資185 / PDF page 193, names only Ritto City Hall, not Nagahama.
  Do not assign Nagahama's current main-hall address from proximity.
- Further Fukuoka/Oita and historical-source checks yielded no additional
  promotion beyond Ogori. JMA technical report 135, Chapter 2 (99 pages), was
  fully text-searched; the first nine pages were read. Explicit southern hosts
  encountered were already addressed; later image-only tables were not fully
  visually inspected. Yuki Sakai's [original 2005 survey](http://sakaiy.main.jp/fos.htm)
  and its Miyaki Kitashigeyasu detail were text-read in their original encoding.
  The latter places the meter at the old hall, but an indexed 2017 municipal
  facility plan describes subsequent rebuilding/partial demolition. The full
  rebuilding PDF remains unread and instrument continuity is unproved: do not
  promote `4134636` from the old hall statement or today's office address.
- Miyazaki follow-up: Ebino's April 2025 earthquake parts (23/21 pages) and
  communications appendix (3 pages) yielded no meter-host hits in web text
  searches. The [official plan index](https://www.city.ebino.lg.jp/gyosei_shisei/keikaku_seisaku/2087.html),
  updated 23 October 2025, and the four-page [appendix contents](https://www.city.ebino.lg.jp/material/files/group/5/007.pdf)
  have now been read. The 61-page [laws/rules appendix](https://www.city.ebino.lg.jp/material/files/group/5/008.pdf)
  and eight-page [transport/public-facilities appendix](https://www.city.ebino.lg.jp/material/files/group/5/014.pdf)
  were opened only at initial excerpts, not fully searched or inspected. The
  [agreements appendix](https://www.city.ebino.lg.jp/material/files/group/5/009.pdf)
  exceeded the web reader's size limit (about 16.5 MB) and remains uninspected;
  try direct public download and text search before examining matching complete
  pages. This read-only continuation produced no saved placement or provenance.
  Do not assign K-NET MYZ009's City Hall host/address to the municipal Ebino
  station `4520930`; the historical municipal-network table has no Ebino row.
  Misato's March 2023 plan (504 pages) has a generic network diagram at PDF
  page 46; only the first matching web-text context was inspected, not every
  possible occurrence. The prefecture's current index identifies the adopted
  March 2026 plan, but its large earthquake PDF remains uninspected. These
  checks produced no promotion. Bunkyo's Civic Center meter reference concerns
  Kasuga, not the unfilled Hongo station; do not transfer that host to Hongo.
- Saga's fiscal 2026 network-maintenance appendix supplied aggregate information
  and blank report forms rather than station hosts. Other linked specification
  and diagram downloads returned 404. The latest Fukuoka/Tagawa appendix check
  likewise supplied locality information without direct host evidence. None of
  these checks produced a placement promotion.
- The latest focused Fukuoka searches and Saiki, Yufu, Bungo-Ono, Usuki, and
  Nakatsu plan checks did not supply additional direct host evidence. Leave
  their unverified records unchanged rather than assigning ordinary office
  addresses from locality matches or generic network descriptions.
- Setagaya `1311231`: Meisei Topics VOL.52 (26 August 2010), fully text-read,
  explicitly captions the ward-hall intensity-meter system. Confirm instrument
  continuity through the phased hall reconstruction before assigning a current
  wing or replacement building. This report does not identify the three other
  Setagaya fire-service hosts; no promotion was made.
- Ota `1311130`: Meisei Topics VOL.61 (26 May 2011) confirms system supply but
  does not explicitly name its placement facility. The official fiscal 2022
  contract-list lead `0501soukatuhyou.pdf`, row 27, identifies an instrument
  renewal contract (10 January 2023), not its host; the complete document and
  individual specification still need inspection. Do not infer the main hall.
  Meisei Topics VOL.33 (29 January 2009), for Bunkyo, has now been fully
  text-read and visually inspected: an unnamed suitable meter site transmits
  to Civic Center. It does not establish Civic Center as the meter host;
  the earlier basement-instrument reference may concern a different instrument.
- Nakano's new-hall meter procurement and relocation expenditure do not yet
  identify the remaining Egota/Chuo reporting hosts. Itabashi's official page
  names a Takashimadaira meter but not its facility. No promotion was made.
- Shiraoka `1124630`: the official 31 July 2026 restoration notice was fully
  read. It confirms service restoration after the May 2025 City Hall fire but
  does not identify the reinstated meter's host; do not assign the main hall
  or temporary offices from that event or the department contact address.
- Hyogo's recovered February 2026 inventory `yobou4-1.pdf` supplies localities
  and providers, not facility hosts. Tamba's fiscal 2025 Tanigawa reinstall
  contract lead likewise lacks a named host. Samukawa citizen comments about
  hearing a broadcast near Town Hall are not an official instrument-host
  statement. Recent Okinawa municipal-plan checks supplied no additional
  direct hosts; the Daito JMA observation page concerned already-addressed
  stations. None of these checks produced a promotion.
- Akita follow-up produced no additional hosts. Ogata's March 2021 prevention,
  earthquake and appendix volumes were searched; earthquake PDF page 29 /
  printed page 765 says real-time intensity can be checked at Village Hall,
  not that the meter is installed there. Recover links from
  `https://www.vill.ogata.akita.jp/archive/contents-10`. Daisen's November 2025
  earthquake and appendix volumes were fully text-searched with whitespace
  normalization and yielded no intensity-meter host statement; their current
  index is `https://www.city.daisen.lg.jp/archive/contents-9695`. Mitane and
  Ikawa search previews supplied only localities or generic maintenance;
  those previews were not full document inspections. Katagami's 2011 council
  question naming Tenno Hall predates its 2015 move and is not an administrative
  placement answer. Oga's prefectural inventory supplies localities/providers,
  not hosts. Akita JMA's July 2026 monthly report was fully checked without a
  host statement; the other linked monthly reports were not bulk-read.
- Date `0721336` (Maekawara): the official fiscal 2024 relocation contract
  `https://www.city.fukushima-date.lg.jp/uploaded/attachment/66085.pdf`
  identifies work in Maekawara but does not establish its completed destination.
  The new Date General Branch opened on 23 March 2026 at Maekawara 25 after
  temporary use of Date Welfare Center. Do not infer that the 2024 meter move
  went to the new hall. The other unfilled Yanagawa, Hobara and Tsukidate hosts
  need individual evidence; Ryozen `0721337` is already addressed from JMA's
  March 2026 notice. Search previews of the new-hall material are discovery
  leads, not completed source inspections.
- Sukagawa `0720737` (Hachimancho): Sakai's original 2011 survey page
  `http://sakaiy.main.jp/113/015.htm` was fully text-read and explicitly
  identifies the former City Hall host. That hall was destroyed in 2011 and
  rebuilt in 2017; establish meter continuity before assigning the new hall,
  even if its address matches. The survey parent is
  `http://sakaiy.main.jp/113d.htm`; the original pages can be read over HTTP
  with Japanese cp932 encoding when HTTPS web retrieval fails.
- The recovered 2021 Fukushima field-survey paper
  `https://www.jstage.jst.go.jp/article/jaee/24/1/24_1_148/_article/-char/ja/`
  (published February 2024) was fully downloaded and text-searched. Its 11
  stations already have addresses; it supplied no additional promotions. The
  2022 survey index at `http://nakazawa.main.jp/eq/2022fks.html` was fully
  text-read; apart from the now-completed Kashima record, its sites are already
  addressed. The former Kyoto repository PDF link for `ndrc_59.pdf` now returns
  an HTML application rather than a PDF, and `shiomitsu.site` returns empty
  HTML; do not repeat those failed downloads as if they were source inspections.

### Earlier placement research

The earlier locality-only refinement preserved the then-current coverage counts while making
the fallback data more useful: 944 catalogue-only records now retain their
official JMA observation-point label as `placementLocalityJa`, rather than a
coarser municipality or ward label. This is locality evidence only: none was
promoted to a facility or an exact address, and the JMA list recheck found no
additional exact name-to-address match. The three facility-only records remain
explicitly distinct from the 806 records still needing facility/address
research.

The latest Yamanashi Prefecture pass promoted 13 stations to exact addresses.
Its official earthquake-network documentation names the host offices, while
Hokuto City, Kai City, Ichikawamisato Town, Minobu Town, and Nanbu Town publish
their full Japanese addresses. The additions cover seven Hokuto general
branches, Kai City's Futaba and Shikishima offices, Ichikawamisato's Mitama
Branch, Minobu's Shimobe Branch, and Nanbu Town's main and Nanbu branch offices.
No placement was inferred from a coordinate, office proximity, or a matching
locality name alone.

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

1. Follow Hashima `2120932`'s actual completed meter-move destination, not its
   current City Hall footer or undated Panasonic case. Resume Gifu's official
   login-free procurement route during published weekday hours for tender
   `21111111520250001`'s individual site inventory; do not retry the third-party
   registration gate. Alternatively, seek unequivocal Yuzukami `0921034` host
   evidence or Kanuma's actual post-rebuilding sensor destination. Honcho
   `0921033` is now saved and is not an outstanding placement. Ohtawara's
   annual-report and current-plan text checks are completed; do not repeat
   them as uninspected leads. Fujisato's recovered R7 plan searches are saved
   provenance, with volume ③ still unsearched; prefer an individual installation
   inventory or site contract rather than repeating the five completed volumes.
   Mimasaka's named public-GIS
   feature lookup is completed and its obsolete Sakaemachi host is saved in
   provenance; seek a current individual inventory/as-built/completed transfer,
   not another copy of that former-site record. Toshima's current adopted-plan
   text searches and Sano's locality-only FAQ are completed without a new host.
   Akabane's facility identity is saved, but its exact sensor-site address still
   needs retention/transfer evidence after the 2020 office return; recover the
   official original/completion of the 2019 tender lead, not merely the new
   office address. Senju's host-site address is saved and must not be researched
   again as an unassigned placement. Alternatively, inspect
   Ebino's mostly scanned agreement pages or follow the three
   saved Miyazaki historical-host/current-address gaps above. Ebino's laws/rules
   and transport/public-facility appendix text searches are completed, not pending.
   Establish the
   reporting meter's current host or retention/transfer, not just today's office
   address. Alternatively, follow Munakata's old/new comparison or Oita's
   current-host continuity gap. Miyama, Hommachi and Kawasato are now
   saved and must not be treated as an outstanding address gap. First check
   the relevant JSON records before researching already-addressed stations.
   Inspect complete installation statements and independent current facility
   addresses before saving. Alternatively, seek Yashio's completed meter-transfer
   evidence, Shiraoka's current meter host or
   Omiya Daimoncho's actual placement. Tokigawa's direct main-building guide is
   recovered and its address saved. All listed Saitama and Tokyo placement
   scripts are already applied: do not rerun them. Continue the remaining public Tokyo inventory rows with
   independently verified addresses and relocation continuity. Alternatively,
   seek a public Mimasaka as-built/completed-transfer record, or a
   direct Fukushima municipal installation/relocation inventory, rather than
   repeating the completed Tamura/Motomiya/Otama/Samegawa plan text searches.
   Noshiro's and Yokote's selected Akita plan/appendix text searches are also
   completed without another host. Mimasaka's
   basic-design drawing and JMA notice are now inspected and saved; do not
   repeat them or promote the planned host without completion evidence. The
   Saga annual report, Kasuga volumes, selected Sumida information volumes and
   Izumizaki text searches are completed; Chuo's misencoded main-plan search
   remains qualified; alternate revision/appendix text searches are now completed
   without direct host proof. Akita's
   2017 network-update site inventory remains a lead, but Kosaka's current
   plan link is recovered and Ikawa/Mitane text searches are completed.
   Continue Mie at the 2019 network-update site inventory,
   Iga's exact relocated destination and current Toba meter continuity; the full
   2008 Toba committee text has already been read. Fujiwara's latest inventory
   pages are now visually inspected and their ambiguity saved; read the
   consolidation records and seek completed sensor-transfer evidence rather
   than repeating those pages. Follow Yokkaichi and remaining Kanagawa leads above.
   Continue JSON-only research. The listed Hida/Takayama/Gero, Ikawa/Mitane and
   Narita volume searches are completed without new direct hosts. Chiba's
   general pre-merger-office policy is not individual branch-host proof.
   Kashiwa's adopted plan/appendix text search is
   finished without host proof; seek another direct source and do not equate
   the 2014 generic seismometer visit with today's reporting meter. Recover the Yamanashi diagram and
   current host continuity, and find Doshi's post-relocation destination. Then seek the
   remaining Saitama inventory rows and direct evidence for the remaining
   Tokyo, Kyoto and Nagahama sites,
   then following the unfinished Fujiwara, Yokkaichi, relocation, Utsunomiya,
   Mito-table, Okinawa procurement, and Gifu maintenance-specification leads
   recorded in the latest continuation checkpoint, the Date/Sukagawa/Iwaki relocation-
   continuity leads and further Akita host evidence, then prefectural maintenance
   specifications and direct municipal evidence in still-unfilled prefectures.
   Do not treat a former or
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
  Earlier recorded batches include 39 directly mapped Nara Prefecture
  seismic-network stations, after the 21 Fukui Prefecture stations and `2520431`
  at Azuchi Community Center. This continuation update is coordination-only: it
  does not alter release metadata or the changelog.
- The latest saved research adds Ohtawara Honcho's main-hall host-site address
  and Yuzukami's qualified provenance in one placement script, plus Fujisato's
  recovered-plan provenance-only script. The preceding Senju address, Akabane
  facility-only identity, and Mimasaka/Onojo/Yamanakako provenance remain saved.
  Hommachi's and Kawasato's addresses and the three Miyazaki
  historical/current-address qualifications remain saved; the latter leave all
  placement fields unchanged. Script totals are 96 (82 placement and
  14 provenance-only), and cumulative promotions are 225, by arithmetic only.
  Oita's current-host continuity gap and Tokai's completed move without a named
  destination remain provenance-only. The preceding 16 exact-address
  additions in three placement scripts remain saved. Mimasaka's
  provenance-only relocation/planned-host note, Fujiwara's
  ambiguity evidence, Mihama Town Hall's exact
  placement, Iga Shijukucho's relocation-only note, the four Kanagawa exact
  placements and Nihonodori's facility-only record are preserved. Coverage
  is 3,693 exact addresses, six facility-only records and 661 still
  needing facility/address research; these are not unresolved English names.
  The formerly pending ten-station fire-host script is applied and included;
  Arakawa's and Yashio's provenance-only scripts leave their placements blank.
  Previously unsaved Saitama town/fire/branch leads are now saved as listed above;
  remaining relocation cases and latest search-only leads are excluded from totals.
  Completed searches, source-age qualifications and the
  next public/direct-source leads are recorded above. This requested
  documentation checkpoint updates this workstream and its `WORKSTREAMS.md`
  index only;
  no workbook, runtime resource, release metadata, changelog, or unrelated file
  was changed.
  The station JSON SHA-256 before and after this documentation update is
  `3C81D688ACDBC30D91A117BD4273002BDA27FEA8AE5DAA4E78D5636D37D6ED86`.
