import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const sources = [
  'https://www.city-yuzawa.jp/uploaded/attachment/26950.pdf',
  'https://www.city-yuzawa.jp/uploaded/attachment/34329.pdf',
];
const updates = [
  ['0520731', '湯沢市川連町', '湯沢市役所稲川庁舎', '秋田県湯沢市川連町字上平城120番地'],
  ['0520734', '湯沢市佐竹町', '湯沢市役所本庁舎', '秋田県湯沢市佐竹町1番1号'],
  ['0520735', '湯沢市横堀', '湯沢市役所雄勝庁舎', '秋田県湯沢市横堀字下柴田39番地'],
  ['0520736', '湯沢市皆瀬', '湯沢市役所皆瀬庁舎', '秋田県湯沢市皆瀬字沢梨台66番地1'],
];

for (const [code, stationName, facility, address] of updates) {
  const station = data.stations.find((candidate) => candidate.code === code);
  if (!station) throw new Error(`Missing station ${code}`);
  if (station.nameJa !== stationName) throw new Error(`${code}: expected ${stationName}, got ${station.nameJa}`);
  if (station.facilityNameJa || station.publishedAddressJa) throw new Error(`${code}: placement unexpectedly present`);

  station.facilityNameJa = facility;
  station.publishedAddressJa = address;
  station.metadataStatus = 'Official municipal seismic-network placement and address';
  station.note = 'Yuzawa City’s current disaster plan identifies this prefectural intensity-meter host; the same official publication set gives the office address.';
  station.placementPrecision = 'exact_address';
  delete station.placementLocalityJa;
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), ...sources])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
