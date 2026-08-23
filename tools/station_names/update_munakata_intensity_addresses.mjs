import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.munakata.lg.jp/kiji003942/3_942_5_04sinsaiokyu.pdf';
const addressSource = 'https://www.city.munakata.lg.jp/kiji003942/3_942_8126_up_6wxx7t31.pdf';
const placements = new Map([
  ['4022030', ['宗像市役所', '福岡県宗像市東郷一丁目1番1号']],
  ['4022033', ['大島行政センター', '福岡県宗像市大島1628番地3']],
  ['4022034', ['福津消防署玄海出張所', '福岡県宗像市牟田尻1860番地41']],
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
  station.note = 'The city disaster plan identifies the intensity meters at City Hall, Oshima Administrative Center, and the Genkai fire-station branch; the plan appendix publishes their addresses.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), placementSource, addressSource])];
  updated += 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
