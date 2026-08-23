import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.buzen.lg.jp/senkyo-gikai/gikai/documents/2007_6_8.pdf';
const addressSource = 'https://www.city.buzen.lg.jp/sisetu/siyakusyo/index.html';
const station = '4021430';

const data = JSON.parse(readFileSync(path, 'utf8'));
const target = data.stations.find((entry) => entry.code === station);
if (!target) throw new Error(`Missing station ${station}`);
let updated = 0;
if (!target.publishedAddressJa && !target.facilityNameJa) {
  target.facilityNameJa = '豊前市役所';
  target.publishedAddressJa = '福岡県豊前市大字吉木955番地';
  target.metadataStatus = 'Official municipal facility address';
  target.note = 'A city council record identifies the intensity meter in the City Hall General Affairs Division; the city publishes the hall address.';
  target.placementPrecision = 'exact_address';
  target.sourceUrls = [...new Set([...(target.sourceUrls ?? []), placementSource, addressSource])];
  updated = 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
