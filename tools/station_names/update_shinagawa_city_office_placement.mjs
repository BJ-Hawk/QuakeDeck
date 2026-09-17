import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const meterSource = 'https://www.city.shinagawa.tokyo.jp/ct/other000026700/kouhou20110901.pdf';
const addressSource = 'https://www.city.shinagawa.tokyo.jp/PC/shisetsu/shisetsu-kuyakusyo/shisetsu-kuyakusyo-shinagawakuyakusyo/index.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1310931');

if (
  !station ||
  station.prefectureJa !== '東京都' ||
  station.nameJa !== '東京品川区広町' ||
  station.placementPrecision !== 'municipality_or_ward' ||
  station.facilityNameJa ||
  station.publishedAddressJa
) {
  throw new Error('Unexpected starting state for station 1310931.');
}

station.facilityNameJa = '品川区役所';
station.publishedAddressJa = '東京都品川区広町2丁目1番36号';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official municipal seismic-meter placement and published facility address';
station.sourceUrls = [...new Set([
  ...station.sourceUrls,
  meterSource,
  addressSource,
])];
station.note = 'Shinagawa City explicitly states that its measurement-intensity meter is installed at Shinagawa City Office. The active catalogue station is the matching Hiromachi record, and the ward publishes the City Office address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
