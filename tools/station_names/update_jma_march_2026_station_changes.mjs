import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.jma.go.jp/bosai/oshirase/dokujioshirase_pdf/JPSP/20260217061734_0_Z__J_JPSP_20260217061600_MET_INF_Jdokujioshirase32_NJ000n00_image.pdf';
const additions = [
  ['1120931', '埼玉県', '飯能市名栗', '飯能市名栗地区行政センター', '埼玉県飯能市大字上名栗3125-1'],
  ['0721337', '福島県', '福島伊達市霊山町', '伊達市役所霊山総合支所', '福島県伊達市霊山町掛田西裏17'],
  ['4520438', '宮崎県', '日南市南郷町南町', '日南市消防署南郷出張所', '宮崎県日南市南郷町南町8-2'],
  ['1942332', '山梨県', '西桂町小沼', '西桂町役場', '山梨県南都留郡西桂町小沼1500-1'],
  ['2620331', '京都府', '綾部市若竹町', '綾部市役所', '京都府綾部市若竹町8-1'],
  ['2820146', '兵庫県', '姫路市林田', '姫路西消防署林田出張所', '兵庫県姫路市林田町六九谷136-2']
];
const corrections = [
  ['3720843', '香川県', '三豊市詫間町', '三豊市詫間町図書館', '香川県三豊市詫間町詫間1338番地5', '三豊市詫間支所', '香川県三豊市詫間町詫間1338番地13'],
  ['3921139', '高知県', '高知香南市吉川町吉原', '香南市吉川庁舎', '香南市吉川村吉原95', '吉川防災コミュニティセンター', '高知県香南市吉川町吉原287-1'],
  ['4320238', '熊本県', '八代市坂本町', '八代市坂本支所', '八代市坂本町坂本1051-2', '八代市立坂本中学校', '熊本県八代市坂本町荒瀬6000'],
  ['2234230', '静岡県', '長泉町中土狩', '長泉町消防庁舎', '駿東郡長泉町中土狩910-1', '富士山南東消防本部長泉消防署', '静岡県駿東郡長泉町中土狩910-1']
];
const data = JSON.parse(readFileSync(path, 'utf8'));

for (const [code, prefectureJa, nameJa, facilityNameJa, publishedAddressJa] of additions) {
  const station = data.stations.find((entry) => entry.code === code);
  if (
    !station ||
    station.prefectureJa !== prefectureJa ||
    station.nameJa !== nameJa ||
    station.facilityNameJa ||
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
  station.note = 'JMA’s March 2026 current station-code notice directly pairs this station name with the published host facility and Japanese address.';
  delete station.placementLocalityJa;
}

for (const [code, prefectureJa, nameJa, previousFacilityNameJa, previousAddressJa, facilityNameJa, publishedAddressJa] of corrections) {
  const station = data.stations.find((entry) => entry.code === code);
  if (
    !station ||
    station.prefectureJa !== prefectureJa ||
    station.nameJa !== nameJa ||
    station.facilityNameJa !== previousFacilityNameJa ||
    station.publishedAddressJa !== previousAddressJa ||
    station.placementPrecision !== 'exact_address'
  ) {
    throw new Error(`Unexpected starting state for ${code}.`);
  }

  station.facilityNameJa = facilityNameJa;
  station.publishedAddressJa = publishedAddressJa;
  station.metadataStatus = 'Official JMA current station-code notice and published address';
  station.sourceUrls = [...new Set([...station.sourceUrls, source])];
  station.note = 'JMA’s March 2026 current station-code notice identifies the current host facility and address after this station’s move; earlier host/address evidence is retained as historical provenance.';
}

data.coverage.publishedAddresses += additions.length;
data.coverage.exactPlacementAddressUpdates += additions.length;
data.coverage.localityPlacementRecords -= additions.length;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
