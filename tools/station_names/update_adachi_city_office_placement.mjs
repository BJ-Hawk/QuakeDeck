import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.adachi.tokyo.jp/documents/10042/03f-dai3bu.pdf';
const addressSource = 'https://www.city.adachi.tokyo.jp/documents/40930/r6-adachi_1stguide.pdf';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1312130');

if (
  !station ||
  station.prefectureJa !== '東京都' ||
  station.nameJa !== '東京足立区中央本町' ||
  station.placementPrecision !== 'municipality_or_ward' ||
  station.facilityNameJa ||
  station.publishedAddressJa
) {
  throw new Error('Unexpected starting state for station 1312130.');
}

station.facilityNameJa = '足立区役所';
station.publishedAddressJa = '東京都足立区中央本町1丁目17番1号';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official municipal seismic-meter placement and published facility address';
station.sourceUrls = [...new Set([
  ...station.sourceUrls,
  placementSource,
  addressSource,
])];
station.note = 'Adachi Ward\'s current disaster plan identifies two intensity meters in the main ward-office building. The active catalogue station is the matching Chuo Honcho record, and the ward publishes the main office address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
