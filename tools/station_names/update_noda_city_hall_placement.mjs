import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const meterSource = 'https://www.city.nagareyama.chiba.jp/_res/projects/default_project/_page_/001/008/596/1-2jishin.pdf';
const addressSource = 'https://www.city.noda.chiba.jp/shisei/saiyou/1001376.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1220832');

if (
  !station ||
  station.prefectureJa !== '千葉県' ||
  station.nameJa !== '野田市鶴奉' ||
  station.placementPrecision !== 'municipality_or_ward' ||
  station.facilityNameJa ||
  station.publishedAddressJa
) {
  throw new Error('Unexpected starting state for station 1220832.');
}

station.facilityNameJa = '野田市役所';
station.publishedAddressJa = '千葉県野田市鶴奉7番地の1';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official prefectural-network placement rule and published municipal facility address';
station.sourceUrls = [...new Set([
  ...station.sourceUrls,
  meterSource,
  addressSource,
])];
station.note = 'Chiba Prefecture network intensity meters are officially described as installed at each municipality\'s main office building or grounds. This station\'s Tsuruho label matches Noda City Hall, and Noda City publishes the City Hall address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
