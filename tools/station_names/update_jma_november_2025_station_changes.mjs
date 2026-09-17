import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.jma.go.jp/bosai/oshirase/dokujioshirase_pdf/JPSP/20251023052630_0_Z__J_JPSP_20251023052500_MET_INF_Jdokujioshirase32_NJ000n00_image.pdf';
const additions = [
  ['4064734', '福岡県', '築上町築城', '築上町図書館', '福岡県築上郡築上町大字築城1096'],
  ['1223835', '千葉県', 'いすみ市弥正', 'いすみ市夷隅庁舎', '千葉県いすみ市弥正87-1'],
  ['2520335', '滋賀県', '長浜市高月町渡岸寺', '高月まちづくりセンター', '滋賀県長浜市高月町渡岸寺141-1'],
  ['2520341', '滋賀県', '長浜市宮部町', '長浜市役所虎姫生きがいセンター', '滋賀県長浜市宮部町3445'],
  ['2520342', '滋賀県', '長浜市難波町', '長浜市役所びわ文化学習センター', '滋賀県長浜市難波町505'],
  ['2521434', '滋賀県', '米原市長岡', '米原市役所山東支所', '滋賀県米原市長岡1206'],
  ['2520731', '滋賀県', '守山市石田町', '守山市コミュニティー防災センター', '滋賀県守山市石田町337-1'],
  ['2521030', '滋賀県', '野洲市西河原', '野洲市役所北部合同庁舎', '滋賀県野洲市西河原2400'],
  ['2520930', '滋賀県', '甲賀市土山町', '甲賀市役所土山地域市民センター', '滋賀県甲賀市土山町北土山1715'],
  ['2520932', '滋賀県', '甲賀市甲南町', '甲賀市役所甲南地域市民センター', '滋賀県甲賀市甲南町野田810'],
  ['2520933', '滋賀県', '甲賀市信楽町', '甲賀市役所信楽地域市民センター', '滋賀県甲賀市信楽町長野1202-10'],
  ['2520934', '滋賀県', '甲賀市甲賀町相模', '甲賀市役所甲賀地域市民センター', '滋賀県甲賀市甲賀町相模173-1'],
  ['2521337', '滋賀県', '東近江市山上町', '永源寺支所', '滋賀県東近江市山上町1316']
];
const movedStations = [
  ['0230431', '青森県', '蓬田村阿弥陀川', '蓬田村役場', '青森県東津軽郡蓬田村大字蓬田字汐越1番地3', '蓬田村役場', '青森県東津軽郡蓬田村大字阿弥陀川字汐干126-1'],
  ['1421021', '神奈川県', '三浦市三崎町', '市立三崎中学校', '神奈川県三浦市城山町5-1', '三崎中学校', '神奈川県三浦市三崎町六合45-1'],
  ['2121941', '岐阜県', '郡上市美並町', '郡上市美並庁舎', '岐阜県郡上市美並町白山725番地3', '郡上市美並振興事務所', '岐阜県郡上市美並町白山430-3'],
  ['4320237', '熊本県', '八代市泉支所', '八代市泉支所', '八代市泉町柿迫3131', '八代市泉支所', '熊本県八代市泉町柿迫3188-2'],
  ['4652535', '鹿児島県', '瀬戸内町加計呂麻島', '瀬戸内町加計呂麻島瀬相港', null, null, '鹿児島県大島郡瀬戸内町大字瀬相186-2']
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
  station.metadataStatus = 'Official JMA November 2025 station-code notice and published address';
  station.sourceUrls = [...new Set([...station.sourceUrls, source])];
  station.note = 'JMA’s November 2025 current station-code notice directly pairs this station name with the published host facility and Japanese address.';
  delete station.placementLocalityJa;
}

for (const [code, prefectureJa, nameJa, previousFacilityNameJa, previousAddressJa, facilityNameJa, publishedAddressJa] of movedStations) {
  const station = data.stations.find((entry) => entry.code === code);
  if (!station || station.prefectureJa !== prefectureJa || station.nameJa !== nameJa || station.facilityNameJa !== previousFacilityNameJa || station.publishedAddressJa !== previousAddressJa || station.placementPrecision !== (previousAddressJa ? 'exact_address' : 'municipality_or_ward')) {
    throw new Error(`Unexpected starting state for ${code}.`);
  }
  if (facilityNameJa) station.facilityNameJa = facilityNameJa;
  else delete station.facilityNameJa;
  station.publishedAddressJa = publishedAddressJa;
  station.placementPrecision = 'exact_address';
  station.metadataStatus = 'Official JMA November 2025 station-code notice and published address';
  station.sourceUrls = [...new Set([...station.sourceUrls, source])];
  station.note = 'JMA’s November 2025 current station-code notice identifies the current station location after a move; earlier host/address evidence is retained only in the record’s source history.';
  delete station.placementLocalityJa;
}

data.coverage.publishedAddresses += additions.length + 1;
data.coverage.exactPlacementAddressUpdates += additions.length + 1;
data.coverage.localityPlacementRecords -= additions.length + 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
