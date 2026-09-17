import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const meterSource = 'https://www.city.nagareyama.chiba.jp/_res/projects/default_project/_page_/001/008/596/1-2jishin.pdf';
const addressSource = 'https://www.city.ichihara.chiba.jp/ichiharagaku/pageindices/index118.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1221930');

if (
  !station ||
  station.prefectureJa !== '千葉県' ||
  station.nameJa !== '市原市国分寺台中央' ||
  station.placementPrecision !== 'municipality_or_ward' ||
  station.facilityNameJa ||
  station.publishedAddressJa
) {
  throw new Error('Unexpected starting state for station 1221930.');
}

station.facilityNameJa = '市原市役所';
station.publishedAddressJa = '千葉県市原市国分寺台中央1-1-1';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official prefectural-network placement rule and published municipal facility address';
station.sourceUrls = [...new Set([
  ...station.sourceUrls,
  meterSource,
  addressSource,
])];
station.note = 'An official Chiba Prefecture network description states that its municipal intensity meters are installed in each municipality\'s main office building or grounds. The active station label is 市原市国分寺台中央, which exactly matches Ichihara City Hall; the city publishes that facility at the recorded address. A separate municipal station at Anegasaki remains independently documented.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
