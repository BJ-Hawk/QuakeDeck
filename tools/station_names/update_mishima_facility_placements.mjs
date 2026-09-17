import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.kkj.go.jp/d/?A=a2Fnb3NoaW1hL2thZ29zaGltYV9wcmVmLzIwMjQvMjAyNDAzMDhfMDA2MjVfMDMucGRmCg%3D%3D&L=ja';
const developmentCentreSource = 'https://mishimamura.com/system/wp-content/uploads/2023/04/b994f2ae9d2db83d0804b2c7b03ef75a.pdf';
const branchOfficeSource = 'https://mishimamura.com/reiki_int/reiki_honbun/q718RG00000026.html';
const updates = [
  {
    code: '4630333',
    nameJa: '三島村硫黄島',
    facilityNameJa: '三島開発センター',
    publishedAddressJa: '鹿児島県鹿児島郡三島村大字硫黄島90番地61',
    facilitySource: developmentCentreSource,
    note: 'Kagoshima Prefecture’s seismic-meter maintenance table identifies the host as Mishima Development Centre. Mishima Village’s current hazard material publishes the address of Mishima Development General Centre, the same named host facility.'
  },
  {
    code: '4630334',
    nameJa: '三島村竹島',
    facilityNameJa: '三島村竹島出張所',
    publishedAddressJa: '鹿児島県鹿児島郡三島村大字竹島7番地',
    facilitySource: branchOfficeSource,
    note: 'Kagoshima Prefecture’s seismic-meter maintenance table identifies the host facility. Mishima Village’s current branch-office ordinance publishes the Takeshima Branch Office address.'
  },
  {
    code: '4630335',
    nameJa: '三島村黒島',
    facilityNameJa: '三島村片泊出張所',
    publishedAddressJa: '鹿児島県鹿児島郡三島村大字黒島31番地の2',
    facilitySource: branchOfficeSource,
    note: 'Kagoshima Prefecture’s seismic-meter maintenance table identifies the host facility. Mishima Village’s current branch-office ordinance publishes the Katadomari Branch Office address.'
  }
];
const data = JSON.parse(readFileSync(path, 'utf8'));

for (const update of updates) {
  const station = data.stations.find((entry) => entry.code === update.code);
  if (
    !station ||
    station.prefectureJa !== '鹿児島県' ||
    station.nameJa !== update.nameJa ||
    station.facilityNameJa !== update.facilityNameJa ||
    station.publishedAddressJa ||
    station.placementPrecision !== 'municipality_or_ward'
  ) {
    throw new Error(`Unexpected starting state for ${update.code}.`);
  }

  station.publishedAddressJa = update.publishedAddressJa;
  station.placementPrecision = 'exact_address';
  station.metadataStatus = 'Official prefectural seismic-meter placement and published facility address';
  station.sourceUrls = [...new Set([...station.sourceUrls, placementSource, update.facilitySource])];
  station.note = update.note;
  delete station.placementLocalityJa;
}

data.coverage.publishedAddresses += updates.length;
data.coverage.exactPlacementAddressUpdates += updates.length;
data.coverage.localityPlacementRecords -= updates.length;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
