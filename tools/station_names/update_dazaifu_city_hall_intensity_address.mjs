import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.dazaifu.lg.jp/uploaded/attachment/5475.pdf';
const addressSource = 'https://www.city.dazaifu.lg.jp/soshiki/7/1644.html';
const station = '4022131';

const data = JSON.parse(readFileSync(path, 'utf8'));
const target = data.stations.find((entry) => entry.code === station);
if (!target) throw new Error(`Missing station ${station}`);
let updated = 0;
if (!target.publishedAddressJa && !target.facilityNameJa) {
  target.facilityNameJa = '太宰府市役所';
  target.publishedAddressJa = '福岡県太宰府市観世音寺一丁目1番1号';
  target.metadataStatus = 'Official municipal facility address';
  target.note = 'A city council record identifies the intensity meter in the City Hall building; the city publishes the hall address.';
  target.placementPrecision = 'exact_address';
  target.sourceUrls = [...new Set([...(target.sourceUrls ?? []), placementSource, addressSource])];
  updated = 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
