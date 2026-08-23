import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.nakama.lg.jp/uploaded/attachment/13683.pdf';
const addressSource = 'https://www.city.nakama.lg.jp/site/bousai/1147.html';
const station = '4021530';

const data = JSON.parse(readFileSync(path, 'utf8'));
const target = data.stations.find((entry) => entry.code === station);
if (!target) throw new Error(`Missing station ${station}`);
let updated = 0;
if (!target.publishedAddressJa && !target.facilityNameJa) {
  target.facilityNameJa = '中間市消防本部';
  target.publishedAddressJa = '福岡県中間市中間二丁目2番2号';
  target.metadataStatus = 'Official municipal facility address';
  target.note = 'The city disaster plan identifies its local-government intensity meter at the Fire Headquarters; the city publishes the headquarters address.';
  target.placementPrecision = 'exact_address';
  target.sourceUrls = [...new Set([...(target.sourceUrls ?? []), placementSource, addressSource])];
  updated = 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
