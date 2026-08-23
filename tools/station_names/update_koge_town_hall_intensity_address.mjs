import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.town.koge.lg.jp/material/files/group/2/2999_4456_misc.pdf';
const addressSource = 'https://www.town.koge.lg.jp/soshiki/chocho/10/1_1/4/816.html';
const station = '4064632';

const data = JSON.parse(readFileSync(path, 'utf8'));
const target = data.stations.find((entry) => entry.code === station);
if (!target) throw new Error(`Missing station ${station}`);
let updated = 0;
if (!target.publishedAddressJa && !target.facilityNameJa) {
  target.facilityNameJa = '上毛町役場';
  target.publishedAddressJa = '福岡県築上郡上毛町大字垂水1321番地1';
  target.metadataStatus = 'Official municipal facility address';
  target.note = 'The town disaster plan states that its intensity meter is installed at Koge Town Hall; the town publishes the hall address.';
  target.placementPrecision = 'exact_address';
  target.sourceUrls = [...new Set([...(target.sourceUrls ?? []), placementSource, addressSource])];
  updated = 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
