import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const meterSource = 'https://www.city.nagareyama.chiba.jp/_res/projects/default_project/_page_/001/008/596/1-2jishin.pdf';
const addressSource = 'https://www.city.togane.chiba.jp/0000000042.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1221331');

if (
  !station ||
  station.prefectureJa !== '千葉県' ||
  station.nameJa !== '東金市東岩崎' ||
  station.placementPrecision !== 'municipality_or_ward' ||
  station.facilityNameJa ||
  station.publishedAddressJa
) {
  throw new Error('Unexpected starting state for station 1221331.');
}

station.facilityNameJa = '東金市役所';
station.publishedAddressJa = '千葉県東金市東岩崎1番地1';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official prefectural-network placement rule and published municipal facility address';
station.sourceUrls = [...new Set([
  ...station.sourceUrls,
  meterSource,
  addressSource,
])];
station.note = 'An official Chiba Prefecture network description states that its municipal intensity meters are installed in each municipality\'s main office building or grounds. The active station label exactly matches Togane City Hall\'s 東岩崎 locality, and the city publishes the recorded address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
