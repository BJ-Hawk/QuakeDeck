import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.hino.lg.jp/_res/projects/default_project/_page_/001/017/631/siryouhen.pdf';
const facilitySource = 'https://www.city.hino.lg.jp/shisei/profile/soshiki/soumu/1004676.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1321230');

if (
  !station ||
  station.prefectureJa !== '東京都' ||
  station.nameJa !== '日野市神明' ||
  station.placementPrecision !== 'municipality_or_ward' ||
  station.facilityNameJa ||
  station.publishedAddressJa
) {
  throw new Error('Unexpected starting state for station 1321230.');
}

station.facilityNameJa = '防災情報センター';
station.publishedAddressJa = '東京都日野市神明1丁目11番地の16';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official municipal seismic-meter placement and published facility address';
station.sourceUrls = [...new Set([
  ...station.sourceUrls,
  placementSource,
  facilitySource,
])];
station.note = 'Hino City\'s official disaster-plan annex identifies its intensity meter at the Disaster Information Center, Shimmei 1-11-16. JMA identifies the matching public local-government station as Hino City Shimmei, and the city publishes the facility address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
