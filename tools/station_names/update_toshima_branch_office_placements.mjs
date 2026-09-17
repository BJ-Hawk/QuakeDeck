import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.kkj.go.jp/d/?A=a2Fnb3NoaW1hL2thZ29zaGltYV9wcmVmLzIwMjQvMjAyNDAzMDhfMDA2MjVfMDMucGRmCg%3D%3D&L=ja';
const facilitySource = 'https://www.tokara.jp/resource/pdf/houshin/tel.pdf';
const updates = [
  {
    code: '4630439',
    nameJa: '鹿児島十島村平島',
    facilityNameJa: '十島村平島出張所',
    publishedAddressJa: '鹿児島県鹿児島郡十島村大字平島293番地'
  },
  {
    code: '4630443',
    nameJa: '鹿児島十島村宝島',
    facilityNameJa: '十島村宝島出張所',
    publishedAddressJa: '鹿児島県鹿児島郡十島村大字宝島923番地'
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
  station.sourceUrls = [...new Set([...station.sourceUrls, placementSource, facilitySource])];
  station.note = 'Kagoshima Prefecture’s seismic-meter maintenance table identifies the host facility. Ten Village’s current official facility directory publishes that branch office address.';
  delete station.placementLocalityJa;
}

data.coverage.publishedAddresses += updates.length;
data.coverage.exactPlacementAddressUpdates += updates.length;
data.coverage.localityPlacementRecords -= updates.length;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
