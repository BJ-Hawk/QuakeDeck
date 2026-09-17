import fs from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.ena.lg.jp/material/files/group/4/202503jishin.pdf';
const officeSource = 'https://www.city.ena.lg.jp/shiseijoho/shisetsuichiran/kokyoshisetsunogoannai/3377.html';
const schoolSource = 'https://www.city.ena.lg.jp/enaschoolnetwork/e/kamiyahagi_1/index.html';
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
const updates = new Map([
  ['2121031', ['恵那市岩村町1657番地1', '岩村振興事務所', officeSource]],
  ['2121033', ['恵那市明智町843番地1', '明智振興事務所', officeSource]],
  ['2121035', ['岐阜県恵那市上矢作町1798番地1', '上矢作小学校', schoolSource]],
  ['2121036', ['恵那市山岡町上手向1228番地1', '山岡振興事務所', officeSource]],
  ['2121037', ['恵那市串原3146番地3', '串原振興事務所', officeSource]],
  ['2121038', ['岐阜県恵那市長島町正家一丁目1番地1', '恵那市役所', officeSource]],
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
  station.sourceUrls.push(placementSource, update[2]);
  station.note = 'Ena City’s disaster plan directly identifies the seismic-intensity host facility; the city publishes the recorded facility address.';
  station.placementPrecision = 'exact_address';
  updates.delete(station.code);
}
if (updates.size) throw new Error(`Missing expected stations: ${[...updates.keys()].join(', ')}`);
if (data.coverage.exactPlacementAddressUpdates !== 2057 || data.coverage.localityPlacementRecords !== 1630) {
  throw new Error('Refusing unexpected coverage state');
}
data.coverage.exactPlacementAddressUpdates = 2063;
data.coverage.localityPlacementRecords = 1624;
fs.writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
