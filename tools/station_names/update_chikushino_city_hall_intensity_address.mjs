import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.chikushino.fukuoka.jp/uploaded/attachment/24754.pdf';
const addressSource = 'https://www.city.chikushino.fukuoka.jp/map/7315.html';
const station = '4021731';

const data = JSON.parse(readFileSync(path, 'utf8'));
const target = data.stations.find((entry) => entry.code === station);
if (!target) throw new Error(`Missing station ${station}`);
let updated = 0;
if (!target.publishedAddressJa && !target.facilityNameJa) {
  target.facilityNameJa = '筑紫野市役所';
  target.publishedAddressJa = '福岡県筑紫野市石崎一丁目1番1号';
  target.metadataStatus = 'Official municipal facility address';
  target.note = 'The city disaster plan’s observation table identifies the prefectural intensity meter at Chikushino City Hall; the city publishes the hall address.';
  target.placementPrecision = 'exact_address';
  target.sourceUrls = [...new Set([...(target.sourceUrls ?? []), placementSource, addressSource])];
  updated = 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
