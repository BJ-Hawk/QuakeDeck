import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.town.okagaki.lg.jp/s005/010/010/1041/08kutyozumen.pdf';
const addressSource = 'https://www.town.okagaki.lg.jp/s001/060/010/201502050003.html';
const station = '4038330';

const data = JSON.parse(readFileSync(path, 'utf8'));
const target = data.stations.find((entry) => entry.code === station);
if (!target) throw new Error(`Missing station ${station}`);
let updated = 0;
if (!target.publishedAddressJa && !target.facilityNameJa) {
  target.facilityNameJa = '岡垣町役場';
  target.publishedAddressJa = '福岡県遠賀郡岡垣町野間一丁目1番1号';
  target.metadataStatus = 'Official municipal facility address';
  target.note = 'The town hall construction drawing labels the installed instrument as an intensity meter; the town publishes the hall address.';
  target.placementPrecision = 'exact_address';
  target.sourceUrls = [...new Set([...(target.sourceUrls ?? []), placementSource, addressSource])];
  updated = 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
