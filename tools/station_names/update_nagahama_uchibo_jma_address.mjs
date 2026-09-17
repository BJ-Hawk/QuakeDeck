import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const sourceUrl = 'https://www.jma.go.jp/bosai/oshirase/dokujioshirase_pdf/JPSP/20251023052630_0_Z__J_JPSP_20251023052500_MET_INF_Jdokujioshirase32_NJ000n00_image.pdf';
const data = JSON.parse(readFileSync(path, 'utf8'));

const station = data.stations.find((entry) => entry.code === '2520331');
if (!station || station.prefectureJa !== '滋賀県' || station.nameJa !== '長浜市内保町') {
  throw new Error('Unexpected station for 2520331');
}
if (station.placementPrecision !== 'municipality_or_ward' || station.facilityNameJa || station.publishedAddressJa) {
  throw new Error('Station 2520331 already has placement metadata');
}

station.facilityNameJa = '長浜市役所浅井支所';
station.publishedAddressJa = '長浜市内保町2490-1';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'JMA official station-address notice';
station.sourceUrls = [...new Set([...station.sourceUrls, sourceUrl])];
station.note = 'JMA’s official station-code notice explicitly lists the current station name, its address, and Nagahama City Hall Azai Branch Office as its host facility.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log('Updated 2520331 Nagahama Uchibocho.');
