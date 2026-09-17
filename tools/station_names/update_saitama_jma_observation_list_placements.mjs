import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.jma.go.jp/bosai/oshirase/dokujioshirase_pdf/JPSP/20260217061734_0_Z__J_JPSP_20260217061600_MET_INF_Jdokujioshirase32_NJ000n00_image.pdf';
const updates = [
  ['1123230', '久喜市菖蒲', '久喜市菖蒲行政センター', '埼玉県久喜市菖蒲町新堀38'],
  ['1123231', '久喜市栗橋', '久喜市栗橋行政センター', '埼玉県久喜市間鎌251-1'],
  ['1123232', '久喜市鷲宮', '久喜市鷲宮行政センター', '埼玉県久喜市鷲宮6-1-1'],
  ['1138530', '上里町七本木', '上里町役場', '埼玉県児玉郡上里町大字七本木5518'],
  ['1110130', 'さいたま西区西大宮', 'さいたま市西区役所', '埼玉県さいたま市西区西大宮3丁目4番地2'],
  ['1123731', '三郷市中央', '三郷市消防本部', '埼玉県三郷市中央5-45-4']
];
const data = JSON.parse(readFileSync(path, 'utf8'));

for (const [code, nameJa, facilityNameJa, publishedAddressJa] of updates) {
  const station = data.stations.find((entry) => entry.code === code);
  if (
    !station ||
    station.prefectureJa !== '埼玉県' ||
    station.nameJa !== nameJa ||
    station.publishedAddressJa ||
    station.facilityNameJa ||
    station.placementPrecision !== 'municipality_or_ward'
  ) {
    throw new Error(`Unexpected starting state for ${code}.`);
  }

  station.facilityNameJa = facilityNameJa;
  station.publishedAddressJa = publishedAddressJa;
  station.placementPrecision = 'exact_address';
  station.metadataStatus = 'Official JMA current station-code notice and published address';
  station.sourceUrls = [...new Set([...station.sourceUrls, source])];
  station.note = 'JMA’s current station-code notice directly pairs this station name with the published host facility and Japanese address.';
  delete station.placementLocalityJa;
}

data.coverage.publishedAddresses += updates.length;
data.coverage.exactPlacementAddressUpdates += updates.length;
data.coverage.localityPlacementRecords -= updates.length;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
