import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.kkj.go.jp/d/?A=a2Fnb3NoaW1hL2thZ29zaGltYV9wcmVmLzIwMjQvMjAyNDAzMDhfMDA2MjVfMDMucGRmCg%3D%3D&L=ja';
const placements = new Map([
  ['4620132', { address: '鹿児島県鹿児島市新島町3348-1' }],
  ['4630333', { facility: '三島開発センター' }],
  ['4630334', { facility: '三島村竹島出張所' }],
  ['4630335', { facility: '三島村片泊出張所' }],
  ['4630439', { facility: '十島村平島出張所' }],
  ['4630443', { facility: '十島村宝島出張所' }],
  ['4630446', { facility: '十島村諏訪之瀬島公民館' }],
  ['4630447', { facility: '十島村悪石島出張所' }],
  ['4630448', { facility: '十島村小宝島公民館' }],
  ['4650231', { facility: '南種子町役場', address: '鹿児島県熊毛郡南種子町中之上2793-1' }],
  ['4652531', { facility: '瀬戸内町請島池地集会所' }],
  ['4652534', { facility: '瀬戸内町与路集会所' }],
  ['4652535', { facility: '瀬戸内町加計呂麻島瀬相港' }],
]);

const data = JSON.parse(readFileSync(path, 'utf8'));
let updated = 0;
for (const station of data.stations) {
  const placement = placements.get(station.code);
  if (!placement) continue;
  if (station.facilityNameJa || station.publishedAddressJa) {
    throw new Error(`${station.code} already has placement data.`);
  }
  station.facilityNameJa = placement.facility ?? null;
  station.publishedAddressJa = placement.address ?? null;
  station.metadataStatus = placement.address
    ? 'Official prefectural seismic-network address'
    : 'Official prefectural seismic-network facility';
  station.note = placement.address
    ? 'Kagoshima Prefecture maintenance table directly identifies this intensity-meter installation and its address.'
    : 'Kagoshima Prefecture maintenance table directly identifies the host facility for this intensity-meter installation; no street address is given.';
  station.placementPrecision = placement.address ? 'exact_address' : 'municipality_or_ward';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), source])];
  updated += 1;
}

if (updated !== placements.size) throw new Error(`Expected ${placements.size} updates, made ${updated}.`);
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updated} Kagoshima stations.`);
