import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.gyoda.lg.jp/material/files/group/14/2sinnsaitaisakur5kaisei.pdf';
const addressSources = [
  'https://www.city.gyoda.lg.jp/soshiki/sougouseisakubu/zaisan_kanri/gyomu/syuyo/3020.html',
  'https://www.city.gyoda.lg.jp/soshiki/shiminseikatubu/shisyo/gyomu/shinogaiyo/soshiki_annai/2826.html',
];
const rows = [
  ['1120630', '行田市本丸', '行田市役所', '埼玉県行田市本丸2番5号'],
  ['1120631', '行田市南河原', '南河原支所', '埼玉県行田市南河原790'],
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
    metadataStatus: 'Official Gyoda City seismic-network placement and addresses',
    note: 'Verified against Gyoda City’s seismic-instrument placement statement and official facility addresses.',
  });
  delete station.placementLocalityJa;
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), placementSource, ...addressSources])];
}

data.coverage.publishedAddresses += rows.length;
data.coverage.exactPlacementAddressUpdates += rows.length;
data.coverage.localityPlacementRecords -= rows.length;
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${rows.length} Gyoda stations.`);
