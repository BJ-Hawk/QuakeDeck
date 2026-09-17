import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.musashimurayama.lg.jp/_res/projects/default_project/_page_/001/000/059/keikaku01sinnsai.pdf';
const facilitySource = 'https://www.city.musashimurayama.lg.jp/school/mmced1s/2000026.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1322331');

if (
  !station ||
  station.prefectureJa !== '東京都' ||
  station.nameJa !== '武蔵村山市本町' ||
  station.placementPrecision !== 'municipality_or_ward' ||
  station.facilityNameJa ||
  station.publishedAddressJa
) {
  throw new Error('Unexpected starting state for station 1322331.');
}

station.facilityNameJa = '武蔵村山市立第一小学校';
station.publishedAddressJa = '東京都武蔵村山市本町1丁目1番地の11';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official municipal seismic-meter placement and published facility address';
station.sourceUrls = [...new Set([
  ...station.sourceUrls,
  placementSource,
  facilitySource,
])];
station.note = 'Musashimurayama City\'s current disaster plan identifies its measurement intensity meter in the First Elementary School grounds. The active catalogue station is the matching Musashimurayama City Hommachi record, and the city publishes the school address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
