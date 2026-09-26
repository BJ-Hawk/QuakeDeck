# NIED station metadata: correction based on actual use

Reviewed: **19 September 2026**, current local working tree. Documentation correction only; application code, station data and generated resources were not changed or rebuilt.

## Corrected conclusion

**This audit has not established that displaying a station's factual address, facility or location breaches a NIED licence.** The previous instruction to remove all 790 provider records or obtain permission overstated the evidence and is withdrawn.

There is still a specific, unresolved question about the systematic extraction and distribution of factual records under the respective source-site notices. Neither the operator label “NIED” nor an address on a station card answers that question. Equally, public access and a different output format do not establish permission. The review below records the actual uses that a source-specific assessment must address.

## Follow-up: evidence for the specific NIED allegation

The follow-up investigated the NIED imports themselves, not the legality of the JMA catalogue. **Copying of NIED factual metadata is confirmed; a legal breach is not established.** The published restrictions are real, but treating them as proof that every copied factual field infringes copyright was unsupported. No express permission specifically covering QuakeDeck's metadata distribution was found either.

### Published restrictions and their exact scope

- The [K-NET/KiK-net homepage](https://www.kyoshin.bosai.go.jp/ja/), under data use, broadly restricts redistribution of supplied content. The [station-list page](https://www.kyoshin.bosai.go.jp/ja/stationlist/) supplies precisely the factual columns imported by the research script. This is evidence of a published policy concern about distributing copied values; it is not a judgment about the legal protection of those values.
- NIED's [network data-use rules](https://www.mowlas.bosai.go.jp/policy/data/), revised 1 October 2024, section 2(2), specifically name files containing observed waveforms and hypocentre information in the redistribution prohibition. The six station identity/location fields imported here contain neither. This narrower clause does **not itself establish a metadata prohibition**, but also does not expressly grant metadata redistribution permission.
- The same centre's [general data policy](https://www.mowlas.bosai.go.jp/policy/guideline/), sections 2–3, defines data more broadly and retains a general redistribution restriction. Therefore the narrower rule cannot responsibly be presented as an explicit exemption overriding every broader notice. No published metadata-specific reconciliation was found in the pages reviewed.
- The procurement PDF is subject to the separate [main-site copyright notice](https://www.bosai.go.jp/about/use.html). The [Hi-net FAQ, Q6–Q7](https://www.hinet.bosai.go.jp/faq/?LANG=ja), permits publication of analysis/processing results with attribution while distinguishing redistribution of supplied data. It does not specifically classify extracting an unchanged station address as an exempt result. Merely selecting columns or changing JSON structure is not evidence of such classification.

### Legal evidence that limits the original allegation

The [Agency for Cultural Affairs' copyright guidance](https://www.bunka.go.jp/seisaku/chosakuken/kaizoku/contact.html), under the first copyright-basics question, expressly excludes mere data from works and separately recognizes creative selection/arrangement. This supports treating bare names, codes, addresses and coordinate numbers as factual information. It does not automatically clear copying a protected compilation as a whole.

A [Tokyo District Court judgment of 28 April 2010, case 2006 (Wa) 24088](https://www.courts.go.jp/assets/hanrei/hanrei-pdf-80205.pdf), printed pages 194–195, rejected the asserted creativity of an ordinary member-information database's structure and necessary information selection. This is an example showing that tabulating facts does not automatically establish database copyright; it is not a decision about NIED's catalogue.

The [Civil Code, Articles 522 and 548-2](https://www.japaneselawtranslation.go.jp/en/laws/view/4848), separately addresses contract formation and incorporation of standard terms. An account or a checkbox is not always necessary for a contract, so their absence does not prove the notices are nonbinding. Conversely, the audit has not established the contract formation/incorporation facts needed to declare a contractual breach. The public importer uses a CSRF token, not an account/password or an explicit acceptance field. The historical acquisition session and any separate agreements were not established from that code.

**Assessment:** the ordinary factual-field use has affirmative support under copyright principles. The exact NIED terms question remains unresolved because neither a protected compilation claim nor contractual applicability has been established or excluded. The original categorical “illegal / must remove” conclusion remains withdrawn. This is not an unconditional legal clearance.

### Newly verified PDF source issue

The current [NIED procurement PDF](https://www.bosai.go.jp/information/tender/supply/pdf/shiyousho.pdf) was downloaded and its station table checked directly. Its SHA-256 is `17bcbe3cec35e680707845b6151f2a0b553c7eb4f551a6e0c20ed8897e1334eb`; PDF metadata records creation/modification on 6 October 2017.

The table spans printed pages 68–90 and yields 1,045 station entries with the importer's column logic. **Detailed address and facility characters are encoded in white.** On visually inspected page 69, the station code/name and county remain visible, while the detailed address and facility portions appear blank. They nevertheless remain available to ordinary PDF text extraction, which is what the importer uses. No encryption/access control was bypassed in this verification.

For the **559 current research rows retaining the exact procurement-importer note**:

- All 559 address values match the normalized PDF extraction, and each source address cell includes white text.
- 558 also match the extracted facility exactly. The remaining row, JMA `0821420` / provider `IBR002`, has a different facility and also cites a Takahagi municipal source. Its unchanged importer note is not sufficient per-field provenance.
- 555 of the corresponding source facility cells contain nonblank white text.

These results replace the earlier assumption that all extracted detail was ordinarily visible in the PDF. White text alone does **not** prove confidentiality, intentional redaction, unlawful access or copyright infringement. It does mean that mere availability inside the file is inadequate evidence that NIED intended these details for public reuse. The historical PDF used by the importer was not retained for comparison, so this finding describes the current source and its agreement with current data.

The [machine-readable verification](nied-pdf-evidence.json) records the population, counts, hashes and limits. Application data was not removed or altered. A focused follow-up should corroborate these extracted details against visibly published official sources, or obtain clarification on their intended publication; do not merge that provenance issue with the JMA catalogue or call it a proven licence breach.

## Source to field to display

Paths below are relative to the repository root. Line numbers describe the reviewed checkout.

| Data | Acquisition and transformation | Storage and actual runtime use |
|---|---|---|
| Base coordinates, Japanese station identity and operator | JMA station map/code-table catalogue; 4,360 rows, including 800 labelled NIED-operated | `jma_intensity_stations.json` is loaded by `StationCatalog.kt:199–210`. Map projection uses `station.latitude/longitude` in `MainActivity.kt:7743`; P2P point completion uses these in `P2pQuakeProvider.kt:1385`. These are not the separate provider coordinates. |
| NIED provider code, network, Japanese/English name, latitude/longitude | `build_station_name_audit.mjs:1110–1153` reads the public station-list API using a CSRF cookie/token, without an account/password in that function. `matchNiedStation` matches by prefecture, distance and name/nearest-station criteria. Lines 1278–1283 copy six fields into research records. | 790 matched records: **783 K-NET, 7 KiK-net**. All six fields match the retained provider cache exactly. The build includes them in `station_details.json` indices 4–9. Station cards display network/code at `MainActivity.kt:3770–3781`. Raw provider names/coordinates have no runtime consumer beyond their declaration/deserialization in the searched application Kotlin sources. |
| Published address and facility | `update_nied_knet_placement_metadata.py` extracts prefecture + address and facility columns from the main NIED site's procurement PDF, normalizes text, joins on provider station code, and fills missing K-NET addresses. Other scripts/overrides use prefectural, municipal and Hi-net evidence. The station-list API importer contains no street-address or facility field. | Build indices 0–2 hold address/Japanese facility/English facility. `MainActivity.kt:3742–3749` chooses facility or administrative fallback; lines 3784 onward show address. These fields identify a site; they do not replace the map coordinates. No source PDF or its page/table layout is copied by this build task. |
| Approved English names | Offline resolution uses Japanese station labels, ABR romanization, GSI locality matching, provider coordinates and explicit overrides. Among the 800 NIED-operated rows, 65 approved-name method fields record NIED-coordinate + GSI + ABR derivation; one records official NIED identity (`0140020`, “K-NET Kutchan”). Other methods cover the remaining 734 rows. | `station_english_names.json` exactly matches all 800 approved research names. `StationCatalog.kt:119–128, 215–240` loads it; station cards and other labels call `approvedEnglishName`. Thus “provider coordinates unused” was true only of the raw runtime fields, not their offline contribution to displayed names. |
| Notes, status and provenance | The research builder unions multiple source URLs for each station; these are not per-field provenance. Notes record later address updates and may supersede earlier source evidence. | `app/build.gradle.kts:94–105` includes note/status, plus `automaticEnglishName` as a municipality fallback. The URL list is omitted. Notes/status are loaded, without further runtime consumers found. A NIED URL on a row does not mean every field came from NIED. |

The source input is `outputs/station-name-audit/station_metadata_sources.json`; the build wiring is `app/build.gradle.kts:232–247`. No station-list runtime endpoint was found in the application Kotlin sources. Acquisition described here is offline research. Publishing a resource or research file would still distribute its contents even without a relay server.

## Verified populations and examples

Counts below use the **800 research rows whose `providerJa` is `防災科学技術研究所`**, unless otherwise stated. They identify the operator, not necessarily the metadata supplier.

- All 800 contain `publishedAddressJa`; 619 contain `facilityNameJa`; none contain `facilityNameEn`.
- 790 contain complete provider tuples and cite the station list. Ten do not have a matched tuple.
- 562 reference the procurement PDF through its desktop or mobile URL. Of these, 559 retain the exact note written by the bulk importer: `Verified against NIED’s official K-NET station specification.` These are current-note counts, not a reconstructed historical import total.
- Seven reference Hi-net station notices: `0632220`, `1936420`, `2420920`, `2421521`, `3920620`, `4120520`, `4649120`.
- These source populations overlap and must not be added together. Source URL membership alone is not proof that an address was extracted from that URL.

Concrete checks against current records:

| JMA code | Actual evidence chain |
|---|---|
| `0123520` — Atsuta | `HKD177` and provider coordinates come from the station list. The address `石狩市厚田区厚田18-1(厚田支所)` is attributed by the current note and URL to Hokkaido's station table. Its English name records provider-coordinate/GSI/ABR derivation. Applying the K-NET notice to the address solely because the same row cites K-NET was unsupported. |
| `0155222` — Saroma Nishitomi | `HKD051` is the station-list join key. The current address `北海道常呂郡佐呂間町字幸町14` and facility `コミュニティセンター敷地内` carry the procurement importer's note and PDF reference. The approved English name instead records transliteration of the official station label. |
| `2042320` — Nagiso Elementary School | No matched provider tuple. `update_nied_legacy_placement_metadata.mjs` explicitly assigns the school address/facility with the procurement PDF source. Removing 790 tuple fields would not remove this separate NIED-sourced address/facility use. |
| `2421521` — Shimacho Fuseda | `MIEH07` is a KiK-net match. The address record separately cites a Hi-net replacement notice. It is neither a K-NET match nor a street address supplied by the station-list API. |

These checks establish local data provenance and use, not a fresh survey of whether each historical station address remains physically current. The initial web screenshot request failed; the follow-up above subsequently rendered the downloaded PDF locally, inspected page 69, and checked table text/colors across pages 68–90.

## Terms: what was checked and what remains unresolved

1. **Station-list source:** the [K-NET/KiK-net notice](https://www.kyoshin.bosai.go.jp/en/) includes a broad restriction on redistribution and alteration of site content. It is not safe to dismiss that wording as waveform-only merely because waveform downloads require registration. The actual question concerns the selected factual station records and derived names documented above.
2. **Address/facility PDF:** the [procurement specification](https://www.bosai.go.jp/information/tender/supply/pdf/shiyousho.pdf) is supplied by the main NIED website. Its [own copyright notice](https://www.bosai.go.jp/about/use.html) restricts copying/reuse without permission and provides a works-use application process. The K-NET subsite notice was the wrong sole basis for assessing this source. The bulk import deserves separate consideration.
3. **Hi-net notices:** [Hi-net's policy](https://www.hinet.bosai.go.jp/about_data/?LANG=en) distinguishes redistribution of supplied data from publication of a user's results and sets acknowledgement/reporting conditions. Whether these short factual extractions fall within those provisions has not been established.
4. **Facts versus protected material:** the official [Copyright Act translation](https://www.japaneselawtranslation.go.jp/en/laws/view/4207/en), Articles 2(1)(i), 12 and 12-2, distinguishes creative expression and creative compilations/databases. That supports assessing factual addresses separately from protected text or database selection/construction. It does not decide the protection of these particular sources or the contractual applicability of website notices. This is an assessment boundary, not a ruling that either all extraction is permitted or all factual display infringes.

**Open item:** determine whether the documented factual selections and derived results require permission under applicable source terms/rights. Any provider or legal clarification should identify the 790 station-list matches, the separate procurement extraction and Hi-net notice use, their transformations, and both research-file and app distribution. No permission request was sent and no removal was performed. The separate FULL forecast/J-SHIS findings were not reassessed by this correction.

## Reproducibility and validation limits

Read-only checks parsed the JSON inputs, counted field coverage and provenance notes/URLs, compared all six provider fields to the cached API items keyed by network/code, compared all 12 generated detail fields to research, and compared approved names to the runtime name resource. Text searches traced their consumers. Research/import/build scripts were inspected, not executed.

- Provider cache: 1,749 items, recorded fetch time `2026-08-17T10:55:33.613Z`; **zero mismatches** for all 790 selected provider tuples. This verifies agreement with the retained acquisition snapshot, not today's remote station inventory.
- Existing generated details: 4,360 rows; **zero row mismatches for the 800 NIED-operated stations** across the 12 emitted fields. Eleven other rows differ from current research, so the existing output is partially stale. No regeneration was performed.
- Approved English names: **zero mismatches** across the 800 NIED-operated records.
- No released APK or running-device UI was inspected. Runtime-display conclusions come from the current code; packaging conclusions come from the build recipe and existing generated resource.

| File | SHA-256 |
|---|---|
| `outputs/station-name-audit/station_metadata_sources.json` | `ed03a14864d68dd25141729be444d3bb34544b16f7ff694e54aeb1fbf37fc7ca` |
| `tools/station_names/official_station_metadata_cache.json` | `25ce8517c337f8e72643bbe198da9bbbb1e417ebbc21346a7af310e11277981b` |
| `app/src/main/res/raw/jma_intensity_stations.json` | `e56412f7116b5bae79af901ab3c0a94d36406060ec36b26b6b140ad4704f716f` |
| `app/src/main/res/raw/station_english_names.json` | `0fbf2df0387fb3a6c2d74d0fcc0e8382192fda645d3e014b172c4072dff6e8d1` |
| `app/build/generated/station-details/res/raw/station_details.json` | `d2a091eeeb5dcc7ff1566d15314b8531673a2c090c54905d3cfc0261c0741a36` |
