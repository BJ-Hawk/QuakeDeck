import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://taro.eri.u-tokyo.ac.jp/saigai/fukuoka/fukuoka.html';
const addressSource = 'https://www.city.fukuoka.lg.jp/syobo/somu/about/documents/r6.syoubounennpou.pdf';
const station = '4013330';

const data = JSON.parse(readFileSync(path, 'utf8'));
const target = data.stations.find((entry) => entry.code === station);
if (!target) throw new Error(`Missing station ${station}`);
let updated = 0;
if (!target.publishedAddressJa && !target.facilityNameJa) {
  target.facilityNameJa = '福岡市消防本部';
  target.publishedAddressJa = '福岡県福岡市中央区舞鶴三丁目9番7号';
  target.metadataStatus = 'Published facility address';
  target.note = 'A University of Tokyo earthquake survey identifies the Central Ward Maizuru intensity site as Fukuoka City Fire Headquarters; the city fire annual report publishes the headquarters address.';
  target.placementPrecision = 'exact_address';
  target.sourceUrls = [...new Set([...(target.sourceUrls ?? []), placementSource, addressSource])];
  updated = 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
