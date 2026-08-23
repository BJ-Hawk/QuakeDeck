import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.town.godo.gifu.jp/politics/pdf/08_28.pdf';
const addressSource = 'https://www.town.godo.gifu.jp/contents/content04.html';
const station = '2138131';

const data = JSON.parse(readFileSync(path, 'utf8'));
const target = data.stations.find((entry) => entry.code === station);
if (!target) throw new Error(`Missing station ${station}`);
let updated = 0;
if (!target.publishedAddressJa && !target.facilityNameJa) {
  target.facilityNameJa = '神戸町役場';
  target.publishedAddressJa = '岐阜県安八郡神戸町大字神戸1111番地';
  target.metadataStatus = 'Official municipal facility address';
  target.note = 'The town earthquake plan identifies the Gifu prefectural network intensity meter as installed on the Town Hall grounds; the town publishes the hall address.';
  target.placementPrecision = 'exact_address';
  target.sourceUrls = [...new Set([...(target.sourceUrls ?? []), placementSource, addressSource])];
  updated = 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
