import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.miyama.lg.jp/s005/anzen/030/010/tiikibousaikeikakuhonnpen.pdf';
const addressSource = 'https://www.city.miyama.lg.jp/li/shisei/030/180/20210623112702.html';
const placements = new Map([
  ['4022930', ['みやま市役所', '福岡県みやま市瀬高町小川5番地']],
  ['4022933', ['みやま市役所山川支所', '福岡県みやま市山川町立山1278番地']],
  ['4022934', ['みやま市役所高田支所', '福岡県みやま市高田町濃施15番地']],
]);

const data = JSON.parse(readFileSync(path, 'utf8'));
let updated = 0;
for (const station of data.stations) {
  const placement = placements.get(station.code);
  if (!placement || station.publishedAddressJa || station.facilityNameJa) continue;
  const [facility, address] = placement;
  station.facilityNameJa = facility;
  station.publishedAddressJa = address;
  station.metadataStatus = 'Official municipal facility address';
  station.note = 'The city disaster plan identifies intensity meters at City Hall, Yamakawa Branch, and Takata Branch; the city publishes the address for each office.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), placementSource, addressSource])];
  updated += 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
