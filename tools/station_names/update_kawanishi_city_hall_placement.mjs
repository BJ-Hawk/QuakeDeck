import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementAndAddressSourceUrl = 'https://www.city.kawanishi.hyogo.jp/kurashi/bosai_bohan_kyukyu/1017400/1019991/1002227.html';
const data = JSON.parse(readFileSync(path, 'utf8'));

const station = data.stations.find((entry) => entry.code === '2821730');
if (!station || station.prefectureJa !== '兵庫県' || station.nameJa !== '川西市中央町') {
  throw new Error('Unexpected station for 2821730');
}
if (station.placementPrecision !== 'municipality_or_ward' || station.facilityNameJa || station.publishedAddressJa) {
  throw new Error('Station 2821730 already has placement metadata');
}

station.facilityNameJa = '川西市役所';
station.publishedAddressJa = '兵庫県川西市中央町12番1号';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official Kawanishi City seismic-instrument placement and City Hall record';
station.sourceUrls = [...new Set([...station.sourceUrls, placementAndAddressSourceUrl])];
station.note = 'Kawanishi City states that the seismic-intensity station reported as 川西市中央町 is installed in Kawanishi City Hall, and publishes the City Hall address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log('Updated 2821730 Kawanishi City Hall.');
