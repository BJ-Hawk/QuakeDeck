# Codex cross-machine notes

This file is a small, Git-synchronised mailbox for Codex chats working on this
repository from different machines.

## Chat instructions

1. Pull the latest Git changes before reading this file.
2. Read all `OPEN` notes that apply to the current task.
3. Add new notes at the top of **Messages** using the template below.
4. Never delete or rewrite another chat's note. Change `OPEN` to `DONE` and add
   a short reply when it has been handled.
5. Do not put passwords, tokens, signing secrets, or private user data here.
6. Commit and push this file only when the user explicitly asks for it.

Git is the transport: the other machine will see a note only after this file is
committed, pushed, and pulled there.

## Message template

```text
### OPEN | YYYY-MM-DD HH:MM TZ | machine/chat label

For: other machine, or a specific chat/task
Topic: short subject

Message or handoff details.

Reply: leave blank until handled
```

## Messages

### OPEN | 2026-08-17 14:05 CEST | local Codex station-name audit

For: the QuakeDeck Codex chat continuing English station-locality naming
Topic: DAABR audit completed; Google Maps fallback policy decided but not yet researched

#### Why this work exists

In English, multiple reporting stations within the same municipality are
currently flattened to the same city-level display name (for example, many
different stations can all appear as `Yatsushiro City`). Japanese is not
flattened: the source already contains fuller station names such as
`八代市鏡町`. The goal is therefore to give English users a useful locality or
neighbourhood name without changing the Japanese names.

The user wants names that are attributable to a real source, not invented or
silently machine-translated. An exact official English name is preferred, but
the user has now approved Google Maps English locality names as a clearly
labelled fallback.

#### What has been done

- Audited the ambiguous station names from
  `app/src/main/res/raw/jma_intensity_stations.json` against the Digital Agency
  Address Base Registry (DAABR) national town/aza master supplied by the user
  at `tools/source/mt_town_all.csv`.
- Used official JMA/NIED station metadata where available and GSI reverse
  geocoding for coordinate-based candidate matching.
- Created the research-only workbook
  `outputs/station-name-audit/ambiguous_station_name_audit.xlsx` with sheets
  `Summary`, `Proposed Mapping`, `DAABR Candidates`, and `Needs Research`.
- The workbook SHA-256 is
  `8F40E352F3F79047740D85B48A32F96DD2FC369E4D75872BD00E7D5FAE463F58`.
- Added the repeatable audit builder and caches under `tools/station_names/`:
  `build_station_name_audit.mjs`, `gsi_reverse_geocoder_cache.json`, and
  `official_station_metadata_cache.json`.
- Confirmed the example station code `4320231`, `八代市鏡町`, from an official
  observation-facility address as `鏡町内田` / `Kagamimachi Uchida`.

Audit totals across 563 ambiguous stations:

- 38 `Address confirmed`
- 56 `Provider coordinate matched`
- 365 `Coordinate matched`
- 10 `Coordinate envelope matched`
- 50 `Romanization missing`
- 44 `Unresolved`

This gives 469 proposed DAABR English assignments. The 50
`Romanization missing` rows already have a Japanese locality identified, but
DAABR publishes no English romanization for that locality. The 44 `Unresolved`
rows comprise 39 local-government and 5 NIED stations; their current failure
reasons are 28 coordinate mismatches, 13 without a usable GSI coordinate
result, and 3 municipality mismatches. There are no duplicate station codes.

#### Decision made after the audit

Use this source hierarchy for the eventual English station-name mapping:

1. DAABR official English locality name when confidently matched.
2. Google Maps English neighbourhood/locality label when DAABR has no English
   name or the automatic DAABR/GSI match remains unresolved.
3. The app's existing city-level English name when Google Maps is genuinely
   ambiguous or cannot be verified.

Google Maps-derived values must have a separate provenance/status such as
`Google Maps matched`; they must never be described as official DAABR names.
For each lookup:

1. Search using the exact Japanese station name and its coordinates.
2. View Google Maps in English and copy the displayed English locality label
   exactly; do not create a new transliteration.
3. Check that the corresponding Japanese locality and municipality agree with
   the station source. If Maps conflicts with an official address or clearly
   points elsewhere, do not force a match.
4. Record the Google Maps URL, the date checked, the displayed Japanese and
   English locality, and a short verification note in the research table.
5. Prefer the existing city-level fallback whenever the evidence remains
   ambiguous.

The future station-details UI may disclose this provenance with wording such
as: `English locality name based on Google Maps.` The immediate next task is to
work through the 50 `Romanization missing` and 44 `Unresolved` rows together
with the user, recording evidence and decisions. No Google Maps lookups or
Google-derived names have been added yet.

#### Scope and repository state

This work is research/table preparation only. Do not implement the mapping in
the app, bump the version, or edit the changelog until the user separately
authorises app implementation. At the time of this note,
`app/build.gradle.kts` remains at versionCode `201` / versionName `0.9.84v`,
and `CHANGELOG.md` remains unchanged. Nothing from this audit has been staged,
committed, pushed, or deployed. Preserve unrelated user files and local edits.

Reply:

### OPEN | 2026-08-17 | local setup

For: all QuakeDeck Codex chats
Topic: Shared mailbox created

Use this file for concise facts, decisions, warnings, and handoffs that another
local Codex chat or machine should know.

Reply:
