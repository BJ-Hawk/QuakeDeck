import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const branchOfficeSource = 'https://www.tokara.jp/reiki_int/reiki_honbun/q719RG00000024.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '4630447');

if (
  !station ||
  station.prefectureJa !== '鹿児島県' ||
  station.nameJa !== '鹿児島十島村悪石島' ||
  station.facilityNameJa !== '十島村悪石島出張所' ||
  station.publishedAddressJa ||
  station.placementPrecision !== 'municipality_or_ward'
) {
  throw new Error('Unexpected starting state for 4630447.');
}

station.publishedAddressJa = '鹿児島県鹿児島郡十島村大字悪石島108番地35';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official prefectural seismic-meter placement and published facility address';
station.sourceUrls = [...new Set([...station.sourceUrls, branchOfficeSource])];
station.note = 'Kagoshima Prefecture’s seismic-meter maintenance table identifies the host as the Toshima Village Akusekijima Branch Office. Toshima Village’s current branch-office ordinance publishes that office’s address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
