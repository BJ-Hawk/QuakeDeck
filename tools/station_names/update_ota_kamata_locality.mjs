import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.city.ota.tokyo.jp/kuseijoho/ota_plan/plan_seika/syuyousesaku2023.files/202306.pdf';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1311130');

if (
  !station ||
  station.prefectureJa !== '東京都' ||
  station.nameJa !== '東京大田区蒲田' ||
  station.placementPrecision !== 'municipality_or_ward' ||
  station.placementLocalityJa !== '東京都大田区' ||
  station.facilityNameJa ||
  station.publishedAddressJa
) {
  throw new Error('Unexpected starting state for station 1311130.');
}

station.placementLocalityJa = '東京都大田区蒲田';
station.metadataStatus = 'Official municipal intensity-system station locality';
station.sourceUrls = [...new Set([...station.sourceUrls, source])];
station.note = 'Ota City\'s official plan identifies the municipal measurement-intensity system result as Ota City Kamata. The source does not identify a host facility or street address, so this record remains locality-only.';

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
