import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const sourceUrl = 'https://www.city.naka.lg.jp/bousai-anzen-saigai/bousai/page008623.html';

const updates = [
  {
    code: '0822630',
    nameJa: '那珂市福田',
    facilityNameJa: '那珂市役所本庁',
    publishedAddressJa: '茨城県那珂市福田1819番地5',
  },
  {
    code: '0822631',
    nameJa: '那珂市瓜連',
    facilityNameJa: '那珂市役所瓜連支所',
    publishedAddressJa: '茨城県那珂市瓜連321番地',
  },
];

for (const update of updates) {
  const station = data.stations.find((item) => item.code === update.code);
  if (!station || station.nameJa !== update.nameJa) {
    throw new Error(`Expected ${update.code} ${update.nameJa}`);
  }
  if (station.publishedAddressJa || station.facilityNameJa) {
    throw new Error(`${update.code} already has placement data`);
  }

  station.publishedAddressJa = update.publishedAddressJa;
  station.facilityNameJa = update.facilityNameJa;
  station.metadataStatus = 'Official municipal seismic-meter placement and address';
  station.placementPrecision = 'exact_address';
  delete station.placementLocalityJa;
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
  station.note = 'Naka City states that Ibaraki Prefecture installed the city’s intensity meters on the grounds of the main office and Urizura Branch.';
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
