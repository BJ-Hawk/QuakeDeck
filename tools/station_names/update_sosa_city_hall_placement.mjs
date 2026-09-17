import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const meterSource = 'https://www.city.nagareyama.chiba.jp/_res/projects/default_project/_page_/001/008/596/1-2jishin.pdf';
const addressSource = 'https://www.city.sosa.lg.jp/page/page001466.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1223530');

if (
  !station ||
  station.prefectureJa !== '千葉県' ||
  station.nameJa !== '匝瑳市八日市場ハ' ||
  station.placementPrecision !== 'municipality_or_ward' ||
  station.facilityNameJa ||
  station.publishedAddressJa
) {
  throw new Error('Unexpected starting state for station 1223530.');
}

station.facilityNameJa = '匝瑳市役所';
station.publishedAddressJa = '千葉県匝瑳市八日市場ハ793番地2';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official prefectural-network placement rule and published municipal facility address';
station.sourceUrls = [...new Set([
  ...station.sourceUrls,
  meterSource,
  addressSource,
])];
station.note = 'An official Chiba Prefecture network description states that its municipal intensity meters are installed in each municipality\'s main office building or grounds. The active station label exactly matches Sosa City Hall\'s 八日市場ハ locality, and the city publishes the recorded address. The separate Noei branch-area station is not covered by this main-office evidence.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
