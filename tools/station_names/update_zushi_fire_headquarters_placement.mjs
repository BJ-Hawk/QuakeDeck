import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSourceUrl = 'https://www.city.zushi.kanagawa.jp/_res/projects/default_project/_page_/001/001/551/3.pdf';
const addressSourceUrl = 'https://www.city.zushi.kanagawa.jp/shisei/1009287/sisetu/1007834/1007840.html';
const data = JSON.parse(readFileSync(path, 'utf8'));

const station = data.stations.find((entry) => entry.code === '1420830');
if (!station || station.prefectureJa !== '神奈川県' || station.nameJa !== '逗子市桜山') {
  throw new Error('Unexpected station for 1420830');
}
if (station.placementPrecision !== 'municipality_or_ward' || station.facilityNameJa || station.publishedAddressJa) {
  throw new Error('Station 1420830 already has placement metadata');
}

station.facilityNameJa = '逗子市消防本部';
station.publishedAddressJa = '神奈川県逗子市桜山2-3-31';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official Zushi City seismic-observation and Fire Headquarters records';
station.sourceUrls = [...new Set([...station.sourceUrls, placementSourceUrl, addressSourceUrl])];
station.note = 'Zushi City states that the prefectural Zushi Sakurayama observation point is installed at the Fire Headquarters; the city publishes the headquarters address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log('Updated 1420830 Zushi City Sakurayama.');
