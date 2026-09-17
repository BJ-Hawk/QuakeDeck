import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const meterSource = 'https://www.city.koto.lg.jp/057101/bosai/bosai-top/taisakukeikaku/documents/kotokeikaku.pdf';
const addressSource = 'https://www.city.koto.lg.jp/051101/kuse/profile/ichi/3099.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1310831');

if (
  !station ||
  station.prefectureJa !== '東京都' ||
  station.nameJa !== '東京江東区東陽' ||
  station.placementPrecision !== 'municipality_or_ward' ||
  station.facilityNameJa ||
  station.publishedAddressJa
) {
  throw new Error('Unexpected starting state for station 1310831.');
}

station.facilityNameJa = '江東区役所';
station.publishedAddressJa = '東京都江東区東陽4丁目11番28号';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official municipal seismic-meter placement and published facility address';
station.sourceUrls = [...new Set([
  ...station.sourceUrls,
  meterSource,
  addressSource,
])];
station.note = 'Koto City\'s current disaster plan identifies the Koto City Office intensity meter. The active catalogue station is the matching Toyo record, and the ward publishes the City Office address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
