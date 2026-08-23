import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.pref.okayama.jp/uploaded/attachment/385084.pdf';
const placements = new Map([
  ['3360636', ['鏡野町上齋原振興センター', '岡山県苫田郡鏡野町上齋原514番地1']],
  ['3320836', ['総社市消防本部', '岡山県総社市小寺377番地']],
]);

const data = JSON.parse(readFileSync(path, 'utf8'));
let updated = 0;
for (const station of data.stations) {
  const placement = placements.get(station.code);
  if (!placement || station.publishedAddressJa || station.facilityNameJa) continue;
  const [facilityNameJa, publishedAddressJa] = placement;
  station.facilityNameJa = facilityNameJa;
  station.publishedAddressJa = publishedAddressJa;
  station.metadataStatus = 'Official prefectural seismic-network address';
  station.note = 'Okayama Prefecture lists this prefectural intensity meter with its installation facility and address.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), source])];
  updated += 1;
}
if (updated !== placements.size) throw new Error(`Expected ${placements.size} updates, made ${updated}.`);
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updated} Okayama stations.`);
