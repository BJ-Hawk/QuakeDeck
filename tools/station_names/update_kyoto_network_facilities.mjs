import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://sms.dpri.kyoto-u.ac.jp/k-asano/station.html';
const placements = new Map([
  ['2621230', '京丹後市役所'],
  ['2621231', '京丹後市役所大宮庁舎'],
  ['2621233', '京丹後市役所丹後庁舎'],
  ['2621235', '京丹後市役所久美浜庁舎'],
  ['2621237', '京丹後市役所弥栄庁舎'],
  ['2646331', '伊根町役場'],
  ['2646530', '与謝野町役場加悦庁舎'],
  ['2646531', '与謝野町役場'],
  ['2646532', '与謝野町役場野田川庁舎'],
  ['2610830', '京都市右京区役所京北出張所'],
  ['2620731', '城陽市役所'],
  ['2621030', '八幡市役所分庁舎'],
  ['2621130', '京田辺市消防本部'],
  ['2621432', '木津川市役所加茂支所'],
  ['2621433', '木津川市役所'],
  ['2621434', '木津川市役所山城支所'],
  ['2632230', '久御山町消防本部'],
  ['2634331', '井手町役場'],
  ['2636430', '笠置町役場'],
  ['2636530', '和束町役場'],
  ['2636631', '精華町役場'],
  ['2636730', '南山城村役場'],
]);

const data = JSON.parse(readFileSync(path, 'utf8'));
let updated = 0;
for (const [code, facilityNameJa] of placements) {
  const station = data.stations.find((item) => item.code === code);
  if (!station) throw new Error(`Missing station ${code}`);
  if (station.facilityNameJa || station.publishedAddressJa) continue;
  station.facilityNameJa = facilityNameJa;
  station.metadataStatus = 'Documented seismic-network placement facility';
  station.note = 'Kyoto University’s field-survey page identifies the Kyoto Prefecture seismic meter at this host facility; a street address has not been inferred.';
  station.placementPrecision = 'municipality_or_ward';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), source])];
  updated += 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${updated} station(s)`);
