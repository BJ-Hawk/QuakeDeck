import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.town.mizuho.tokyo.jp/tyosei/002/005/p001370_d/fil/01.pdf';
const facilitySource = 'https://www.town.mizuho.tokyo.jp/tyosei/022/001/index.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1330331');

if (
  !station ||
  station.prefectureJa !== '東京都' ||
  station.nameJa !== '瑞穂町箱根ヶ崎' ||
  station.placementPrecision !== 'municipality_or_ward' ||
  station.facilityNameJa ||
  station.publishedAddressJa
) {
  throw new Error('Unexpected starting state for Mizuho Town Hall placement.');
}

station.facilityNameJa = '瑞穂町役場';
station.publishedAddressJa = '東京都西多摩郡瑞穂町大字箱根ケ崎2335番地';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official municipal seismic-meter placement and published facility address';
station.sourceUrls = [...new Set([...station.sourceUrls, placementSource, facilitySource])];
station.note = 'Mizuho Town’s continuity plan identifies the measuring-intensity meter as a system installed in the Town Hall building. The active public station label is Mizuho Town Hakonegasaki, matching the Town Hall locality; the town publishes the facility address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
