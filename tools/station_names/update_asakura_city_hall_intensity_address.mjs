import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.asakura.lg.jp/www/contents/1761294319019/files/8.pdf';
const station = '4022833';

const data = JSON.parse(readFileSync(path, 'utf8'));
const target = data.stations.find((entry) => entry.code === station);
if (!target) throw new Error(`Missing station ${station}`);
let updated = 0;
if (!target.publishedAddressJa && !target.facilityNameJa) {
  target.facilityNameJa = '朝倉市役所';
  target.publishedAddressJa = '福岡県朝倉市甘木232番地1';
  target.metadataStatus = 'Official municipal facility address';
  target.note = 'The city procurement record confirms that the intensity meter was moved from the former City Hall to the new City Hall at this address.';
  target.placementPrecision = 'exact_address';
  target.sourceUrls = [...new Set([...(target.sourceUrls ?? []), placementSource])];
  updated = 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
