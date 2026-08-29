import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.kazo.lg.jp/kurashi_bosai/bosai/1/32453.html';
const addressSource = 'https://www.city.kazo.lg.jp/soshiki/kasai_chiikishinkou/6364.html';
const rows = [
  ['1121030', '加須市三俣', '加須市役所', '埼玉県加須市三俣二丁目1番地1'],
  ['1121031', '加須市騎西', '騎西総合支所', '埼玉県加須市騎西36番地1'],
  ['1121032', '加須市北川辺', '北川辺総合支所', '埼玉県加須市麦倉1481番地1'],
  ['1121033', '加須市大利根', '大利根総合支所', '埼玉県加須市北下新井1679番地1'],
];

const data = JSON.parse(readFileSync(path, 'utf8'));
for (const [code, nameJa, facilityNameJa, publishedAddressJa] of rows) {
  const station = data.stations.find((candidate) => candidate.code === code);
  if (!station || station.prefectureJa !== '埼玉県' || station.nameJa !== nameJa) {
    throw new Error(`Unexpected station identity for ${code}`);
  }
  if (station.publishedAddressJa || station.facilityNameJa || station.placementPrecision !== 'municipality_or_ward') {
    throw new Error(`Station ${code} already has placement data`);
  }
  Object.assign(station, {
    facilityNameJa,
    publishedAddressJa,
    placementPrecision: 'exact_address',
    metadataStatus: 'Official Kazo City seismic-network placement and addresses',
    note: 'Verified against Kazo City’s seismic-instrument placement statement and official facility addresses.',
  });
  delete station.placementLocalityJa;
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), placementSource, addressSource])];
}

data.coverage.publishedAddresses += rows.length;
data.coverage.exactPlacementAddressUpdates += rows.length;
data.coverage.localityPlacementRecords -= rows.length;
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${rows.length} Kazo stations.`);
