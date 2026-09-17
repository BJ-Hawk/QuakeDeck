import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.musashino.lg.jp/_res/projects/default_project/_page_/001/039/234/r4bousaikaigisiryou4.pdf';
const facilitySource = 'https://www.city.musashino.lg.jp/shisetsu_annai/musashinoshi_kanren/shiyakusho.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1320331');

if (
  !station ||
  station.prefectureJa !== '東京都' ||
  station.nameJa !== '武蔵野市緑町' ||
  station.placementPrecision !== 'municipality_or_ward' ||
  station.facilityNameJa ||
  station.publishedAddressJa
) {
  throw new Error('Unexpected starting state for Musashino City Hall placement.');
}

station.facilityNameJa = '武蔵野市役所';
station.publishedAddressJa = '東京都武蔵野市緑町2丁目2番28号';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official municipal seismic-meter placement and published facility address';
station.sourceUrls = [...new Set([...station.sourceUrls, placementSource, facilitySource])];
station.note = 'Musashino City’s official disaster-plan system diagram places its measuring-intensity meter at City Hall. The active public station label is Musashino City Midoricho, matching the City Hall locality; the city publishes the facility address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
