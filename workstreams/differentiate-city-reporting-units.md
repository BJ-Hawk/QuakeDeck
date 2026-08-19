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
  `Summary`, `Proposed Mapping`, `DAABR Candidates`, and `Needs Research` sheets.
  Recorded workbook SHA-256:
  `8F40E352F3F79047740D85B48A32F96DD2FC369E4D75872BD00E7D5FAE463F58`.
- Added repeatable audit tooling and caches under `tools/station_names/`.
- Confirmed station `4320231` (`八代市鏡町`) from its official installation
  address as `鏡町内田` / `Kagamimachi Uchida`, not `Kagamimachi Kagami`.
- Audit result: 469 proposed DAABR English assignments, comprising 38 address
  confirmed, 56 provider-coordinate matched, 365 coordinate matched, and 10
  coordinate-envelope matched. A further 50 have a known locality but no DAABR
  English romanization; 44 remain unresolved. No duplicate station codes exist.

## Why this approach was used

The published Japanese station label can be a shortened locality prefix, so
name-only matching can select the wrong neighbourhood. Stable station codes,
official installation addresses, provider coordinates, DAABR vocabulary, and
recorded confidence make the result reproducible and auditable. The workbook
keeps research separate from runtime behavior, while one future shared resolver
will keep list and map labels consistent.

## Current unfinished point

No Google Maps fallback lookups have been performed and no Google-derived names
have been recorded. The next research pass is the 50 `Romanization missing` rows
plus the 44 `Unresolved` rows. No station-code English mapping has been wired into
the app, and the future station-details provenance disclosure is not implemented.

## Do not redo or change

- Do not rebuild the audit from scratch; continue from the workbook, builder,
  and caches already present.
- Do not invent transliterations, silently relabel Google values as official, or
  force uncertain matches.
- Do not alter Japanese names, parser/report merging, station coordinates, or
  provider architecture for this workstream.
- Do not make a report-only or map-only naming workaround; the eventual behavior
  belongs in the shared naming path.
- Do not commit `tools/source/mt_town_all.csv`; it is a large local reference and
  is ignored by `.gitignore`.
- Do not implement app changes, bump the version, or edit `CHANGELOG.md` until
  the user explicitly authorises the implementation phase.
- Preserve unrelated local edits. These notes describe shared task state and do
  not claim exclusive ownership of any file.

## Exact next steps

1. Open the audit workbook and review the 50 `Romanization missing` and 44
   `Unresolved` rows with the user, keeping the two categories distinct.
2. For each candidate fallback, search Google Maps using the exact Japanese
   station name and coordinates, switch Maps to English, and copy the displayed
   locality exactly.
3. Verify that the Japanese locality and municipality agree with the station
   source and stronger official-address evidence; otherwise keep the existing
   city-level fallback.
4. Record the Maps URL, date checked, Japanese/English labels, provenance status,
   and verification note in the research table. Recalculate category totals and
   workbook checksum after the reviewed research update.
5. Ask for explicit implementation approval. Only then design the stable
   station-code mapping and shared resolver used by both report rows and map
   labels, with focused tests and the required letter version/changelog update.

## Logical changes and Git state

- Research artifacts were committed as `15adfa0` and `5bf125a`, both titled
  `Prepared sources for better English-name association for stations.`
- `5bf125a` also added `/tools/source/mt_town_all.csv` to `.gitignore`.
- Current branch is `main`; HEAD is merge commit `f0e0c55`, and the checkout was
  synchronized with `origin/main` (`+0/-0`) and clean before this note was added.
- App version remains `0.9.84v` / versionCode `201`; `CHANGELOG.md` was not
  changed for the research work.
