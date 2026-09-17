import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const updates = [
  {
    code: '1322730',
    nameJa: '羽村市緑ヶ丘',
    facilityNameJa: '羽村市役所',
    publishedAddressJa: '東京都羽村市緑ヶ丘5丁目2番地1',
    placementSource: 'https://www.city.hamura.tokyo.jp/cmsfiles/contents/0000007/7491/shindokei%282018%29.pdf',
    addressSource: 'https://www.city.hamura.tokyo.jp/0000002127.html',
    note: 'Hamura City\'s official evaluation of its measurement intensity-meter system identifies the installation site as Hamura City Hall. The active catalogue station is the matching Hamura City Midorigaoka record, and the city publishes the City Hall address.',
  },
  {
    code: '1322231',
    nameJa: '東久留米市本町',
    facilityNameJa: '東久留米市役所',
    publishedAddressJa: '東京都東久留米市本町3丁目3番1号',
    placementSource: 'https://www.city.higashikurume.lg.jp/mayor/1006478/1023841.html',
    addressSource: 'https://www.city.higashikurume.lg.jp/shisei/shiyakusho/chosha/1003444.html',
    note: 'Higashikurume City identifies its measurement intensity meter at the main City Hall and planned its relocation from the basement to a ground-level position within the main-office grounds. The active catalogue station is the matching Higashikurume City Honcho record, and the city publishes the City Hall address.',
  },
];

for (const update of updates) {
  const station = data.stations.find((entry) => entry.code === update.code);
  if (
    !station ||
    station.prefectureJa !== '東京都' ||
    station.nameJa !== update.nameJa ||
    station.placementPrecision !== 'municipality_or_ward' ||
    station.facilityNameJa ||
    station.publishedAddressJa
  ) {
    throw new Error(`Unexpected starting state for station ${update.code}.`);
  }

  station.facilityNameJa = update.facilityNameJa;
  station.publishedAddressJa = update.publishedAddressJa;
  station.placementPrecision = 'exact_address';
  station.metadataStatus = 'Official municipal seismic-meter placement and published facility address';
  station.sourceUrls = [...new Set([
    ...station.sourceUrls,
    update.placementSource,
    update.addressSource,
  ])];
  station.note = update.note;
  delete station.placementLocalityJa;
}

data.coverage.publishedAddresses += updates.length;
data.coverage.exactPlacementAddressUpdates += updates.length;
data.coverage.localityPlacementRecords -= updates.length;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
