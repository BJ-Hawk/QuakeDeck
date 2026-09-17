import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const meterSource = 'https://www.city.nagareyama.chiba.jp/_res/projects/default_project/_page_/001/008/596/1-2jishin.pdf';
const currentMeterSource = 'https://www.city.nagareyama.chiba.jp/life/1003604/1003712/1039747/1032416.html';
const addressSource = 'https://www.city.nagareyama.chiba.jp/institution/1004033/1004034.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1222030');

if (
  !station ||
  station.prefectureJa !== '千葉県' ||
  station.nameJa !== '流山市平和台' ||
  station.placementPrecision !== 'municipality_or_ward' ||
  station.facilityNameJa ||
  station.publishedAddressJa
) {
  throw new Error('Unexpected starting state for station 1222030.');
}

station.facilityNameJa = '流山市役所';
station.publishedAddressJa = '千葉県流山市平和台1丁目1番地の1';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official municipal seismic-meter placement and published facility address';
station.sourceUrls = [...new Set([
  ...station.sourceUrls,
  meterSource,
  currentMeterSource,
  addressSource,
])];
station.note = 'Nagareyama City\'s disaster plan states that Chiba Prefecture network intensity meters are installed in each municipality\'s main office building or grounds. The current city page confirms that Nagareyama operates its municipal meter; this is the sole catalogue station in Nagareyama and its Heiwadai label matches City Hall. The city publishes the City Hall address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
