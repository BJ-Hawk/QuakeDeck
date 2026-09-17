import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.city.suginami.tokyo.jp/_res/projects/default_project/_page_/001/065/985/sinsai-oukyuhukkyutaisaku.pdf';
const data = JSON.parse(readFileSync(path, 'utf8'));
const updates = [
  {
    code: '1311550',
    nameJa: '東京杉並区桃井',
    facilityNameJa: '東京消防庁荻窪消防署',
    publishedAddressJa: '東京都杉並区桃井3丁目15番1号',
  },
  {
    code: '1311551',
    nameJa: '東京杉並区高井戸',
    facilityNameJa: '東京消防庁杉並消防署高井戸出張所',
    publishedAddressJa: '東京都杉並区高井戸東3丁目32番2号',
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
  station.sourceUrls = [...new Set([...station.sourceUrls, source])];
  station.note = 'Suginami Ward\'s official disaster plan explicitly identifies the meter at this named Tokyo Fire Department facility and gives its Japanese address. The active catalogue station is the matching locality record.';
  delete station.placementLocalityJa;
}

data.coverage.publishedAddresses += updates.length;
data.coverage.exactPlacementAddressUpdates += updates.length;
data.coverage.localityPlacementRecords -= updates.length;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
