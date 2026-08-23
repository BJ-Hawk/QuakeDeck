import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.city.shirakawa.fukushima.jp/data/doc/1648634721_doc_43_0.pdf';
const updates = new Map([
  ['0720532', '福島県白河市東釜子字殿田表50'],
  ['0720534', '福島県白河市表郷金山字長者久保2'],
  ['0720535', '福島県白河市大信町屋字沢田18'],
  ['0720536', '福島県白河市八幡小路7番地1'],
]);
const data = JSON.parse(readFileSync(path, 'utf8'));
let updated = 0;

for (const station of data.stations) {
  const update = updates.get(station.code);
  if (!update || station.publishedAddressJa || station.facilityNameJa) continue;
  station.publishedAddressJa = update;
  station.metadataStatus = 'Official municipal seismic-network address';
  station.note = 'Shirakawa City lists this Fukushima Prefecture seismic-network meter and its full placement address in the municipal disaster plan.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), source])];
  updated += 1;
}

if (updated) writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updated} station(s).`);
