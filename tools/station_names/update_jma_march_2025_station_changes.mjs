import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.jma.go.jp/bosai/oshirase/dokujioshirase_pdf/JPSP/20250214065336_0_Z__J_JPSP_20250214065200_MET_INF_Jdokujioshirase32_NJ000n00_image.pdf';
const additions = [
  ['0746532', '福島県', '中島村滑津', '中島村役場', '福島県西白河郡中島村大字滑津字中島西11-1'],
  ['1223134', '千葉県', '印西市美瀬', '印西市印旛支所', '千葉県印西市美瀬1-25'],
  ['2945032', '奈良県', '下北山村寺垣内', '下北山村役場', '奈良県吉野郡下北山村大字寺垣内1002'],
  ['4040231', '福岡県', '鞍手町小牧', '鞍手町役場', '福岡県鞍手郡鞍手町大字小牧2080-2']
];
const movedStations = [
  ['2820321', '兵庫県', '明石市二見', '中崎公園', '兵庫県明石市相生1丁目93-6', null, '兵庫県明石市二見町西二見767-3'],
  ['4622040', '鹿児島県', '南さつま市坊津町久志', '南さつま市坊津支所', '南さつま市坊津町久志２４２２の１', '久志出張所', '鹿児島県南さつま市坊津町久志4358']
];
const data = JSON.parse(readFileSync(path, 'utf8'));

for (const [code, prefectureJa, nameJa, facilityNameJa, publishedAddressJa] of additions) {
  const station = data.stations.find((entry) => entry.code === code);
  if (!station || station.prefectureJa !== prefectureJa || station.nameJa !== nameJa || station.facilityNameJa || station.publishedAddressJa || station.placementPrecision !== 'municipality_or_ward') {
    throw new Error(`Unexpected starting state for ${code}.`);
  }
  station.facilityNameJa = facilityNameJa;
  station.publishedAddressJa = publishedAddressJa;
  station.placementPrecision = 'exact_address';
  station.metadataStatus = 'Official JMA March 2025 station-code notice and published address';
  station.sourceUrls = [...new Set([...station.sourceUrls, source])];
  station.note = 'JMA’s March 2025 current station-code notice directly pairs this station name with the published host facility and Japanese address.';
  delete station.placementLocalityJa;
}

for (const [code, prefectureJa, nameJa, previousFacilityNameJa, previousAddressJa, facilityNameJa, publishedAddressJa] of movedStations) {
  const station = data.stations.find((entry) => entry.code === code);
  if (!station || station.prefectureJa !== prefectureJa || station.nameJa !== nameJa || station.facilityNameJa !== previousFacilityNameJa || station.publishedAddressJa !== previousAddressJa || station.placementPrecision !== 'exact_address') {
    throw new Error(`Unexpected starting state for ${code}.`);
  }
  if (facilityNameJa) station.facilityNameJa = facilityNameJa;
  else delete station.facilityNameJa;
  station.publishedAddressJa = publishedAddressJa;
  station.metadataStatus = 'Official JMA March 2025 station-code notice and published address';
  station.sourceUrls = [...new Set([...station.sourceUrls, source])];
  station.note = 'JMA’s March 2025 current station-code notice identifies the current station location after a move; earlier host/address evidence is retained only in the record’s source history.';
}

data.coverage.publishedAddresses += additions.length;
data.coverage.exactPlacementAddressUpdates += additions.length;
data.coverage.localityPlacementRecords -= additions.length;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
