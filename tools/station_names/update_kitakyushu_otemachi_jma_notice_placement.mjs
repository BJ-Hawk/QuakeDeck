import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.jma.go.jp/bosai/oshirase/dokujioshirase_pdf/JPSP/20260217061734_0_Z__J_JPSP_20260217061600_MET_INF_Jdokujioshirase32_NJ000n00_image.pdf';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '4010630');

if (
  !station ||
  station.prefectureJa !== '福岡県' ||
  station.nameJa !== '北九州小倉北区大手町' ||
  station.facilityNameJa ||
  station.publishedAddressJa ||
  station.placementPrecision !== 'municipality_or_ward'
) {
  throw new Error('Unexpected starting state for 4010630.');
}

station.facilityNameJa = '北九州市消防局';
station.publishedAddressJa = '福岡県北九州市小倉北区大手町3-9';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official JMA current station-code notice and published address';
station.sourceUrls = [...new Set([...station.sourceUrls, source])];
station.note = 'JMA’s March 2026 current station-code notice directly pairs this station name with the Kitakyushu City Fire Bureau and its published Japanese address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
