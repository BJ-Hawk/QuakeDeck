import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSourceUrl = 'https://www.city.ashiya.lg.jp/kouhou/kensaku/h19/documents/07101523.pdf';
const currentStationSourceUrl = 'https://www.data.jma.go.jp/osaka/jishinkazan/kanbox/202501.pdf';
const addressSourceUrl = 'https://www.city.ashiya.lg.jp/shisetsu/seidou_s.html';
const data = JSON.parse(readFileSync(path, 'utf8'));

const station = data.stations.find((entry) => entry.code === '2820631');
if (!station || station.prefectureJa !== '兵庫県' || station.nameJa !== '芦屋市精道町') {
  throw new Error('Unexpected station for 2820631');
}
if (station.placementPrecision !== 'municipality_or_ward' || station.facilityNameJa || station.publishedAddressJa) {
  throw new Error('Station 2820631 already has placement metadata');
}

station.facilityNameJa = '精道小学校';
station.publishedAddressJa = '兵庫県芦屋市精道町8番25号';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official Ashiya City seismic-instrument placement, current JMA station report, and school address record';
station.sourceUrls = [...new Set([...station.sourceUrls, placementSourceUrl, currentStationSourceUrl, addressSourceUrl])];
station.note = 'Ashiya City identifies the station as installed at Seido Elementary School; JMA continued to report 芦屋市精道町 in 2025, and Ashiya City publishes the school address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log('Updated 2820631 Seido Elementary School.');
