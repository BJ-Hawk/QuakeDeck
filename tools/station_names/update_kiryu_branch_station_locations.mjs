import fs from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.kiryu.lg.jp/anzen/bousai/joho/1020089.html';
const addressSource = 'https://www.city.kiryu.lg.jp/shisei/profile/1002961.html';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const updates = new Map([
  ['1020331', ['群馬県桐生市黒保根町水沼182-3', '黒保根支所']],
  ['1020332', ['群馬県桐生市新里町武井693-1', '新里支所']],
]);

for (const station of data.stations) {
  const update = updates.get(station.code);
  if (!update) continue;
  if (station.publishedAddressJa !== null || station.facilityNameJa !== null || station.placementPrecision !== 'municipality_or_ward') {
    throw new Error(`Refusing unexpected pre-update state for ${station.code}`);
  }
  station.publishedAddressJa = update[0];
  station.facilityNameJa = update[1];
  station.metadataStatus = 'source_verified';
  station.sourceUrls.push(placementSource, addressSource);
  station.note = 'Kiryu City’s official intensity-meter table directly identifies this station at the named branch office; Kiryu City publishes the branch office’s Japanese address.';
  station.placementPrecision = 'exact_address';
  updates.delete(station.code);
}

if (updates.size) throw new Error(`Missing expected stations: ${[...updates.keys()].join(', ')}`);
if (data.coverage.exactPlacementAddressUpdates !== 2132 || data.coverage.localityPlacementRecords !== 1555) {
  throw new Error('Refusing unexpected coverage state');
}
data.coverage.exactPlacementAddressUpdates = 2134;
data.coverage.localityPlacementRecords = 1553;
fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
