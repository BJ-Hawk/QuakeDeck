import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.fukutsu.lg.jp/material/files/group/1/R04bousaihonpen.pdf';
const addressSource = 'https://www.city.fukutsu.lg.jp/shisei/shisetsu/8/4136.html';
const placements = new Map([
  ['4022430', ['福津市役所', '福岡県福津市中央一丁目1番1号']],
  ['4022431', ['福津市複合文化センター', '福岡県福津市津屋崎一丁目7番2号']],
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
  station.note = 'The city disaster plan identifies the intensity meters at City Hall and the Fukutsu City Complex Cultural Center; the city publishes the facility addresses.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), placementSource, addressSource])];
  updated += 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
