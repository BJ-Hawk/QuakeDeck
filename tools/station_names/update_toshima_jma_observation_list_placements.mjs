import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.jma.go.jp/bosai/oshirase/dokujioshirase_pdf/JPSP/20260217061734_0_Z__J_JPSP_20260217061600_MET_INF_Jdokujioshirase32_NJ000n00_image.pdf';
const updates = [
  ['4630446', '鹿児島十島村諏訪之瀬島', '十島村諏訪之瀬島公民館', '十島村諏訪之瀬島出張所', '鹿児島県鹿児島郡十島村大字諏訪之瀬島279'],
  ['4630448', '鹿児島十島村小宝島', '十島村小宝島公民館', '十島村小宝島出張所', '鹿児島県鹿児島郡十島村大字小宝島4番地19']
];
const data = JSON.parse(readFileSync(path, 'utf8'));

for (const [code, nameJa, previousFacilityNameJa, facilityNameJa, publishedAddressJa] of updates) {
  const station = data.stations.find((entry) => entry.code === code);
  if (
    !station ||
    station.prefectureJa !== '鹿児島県' ||
    station.nameJa !== nameJa ||
    station.facilityNameJa !== previousFacilityNameJa ||
    station.publishedAddressJa ||
    station.placementPrecision !== 'municipality_or_ward'
  ) {
    throw new Error(`Unexpected starting state for ${code}.`);
  }

  station.facilityNameJa = facilityNameJa;
  station.publishedAddressJa = publishedAddressJa;
  station.placementPrecision = 'exact_address';
  station.metadataStatus = 'Official JMA current station-code notice and published address';
  station.sourceUrls = [...new Set([...station.sourceUrls, source])];
  station.note = 'JMA’s current station-code notice directly pairs this station name and coordinate with the published branch-office host and Japanese address. This current JMA source supersedes the older prefectural facility label.';
  delete station.placementLocalityJa;
}

data.coverage.publishedAddresses += updates.length;
data.coverage.exactPlacementAddressUpdates += updates.length;
data.coverage.localityPlacementRecords -= updates.length;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
