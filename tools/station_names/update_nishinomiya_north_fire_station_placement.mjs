import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSourceUrl = 'https://www.city.nishinomiya.lg.jp/kurashi/anshin/shobokyoku/soshikitokei/shobo_soshiki/enkaku.html';
const currentStationSourceUrl = 'https://www.data.jma.go.jp/osaka/jishinkazan/kanbox/202402.pdf';
const addressSourceUrl = 'https://www.city.nishinomiya.lg.jp/kurashi/anshin/shobokyoku/kita/shisetsu/kita-shokai.html';
const data = JSON.parse(readFileSync(path, 'utf8'));

const station = data.stations.find((entry) => entry.code === '2820440');
if (!station || station.prefectureJa !== '兵庫県' || station.nameJa !== '西宮市名塩') {
  throw new Error('Unexpected station for 2820440');
}
if (station.placementPrecision !== 'municipality_or_ward' || station.facilityNameJa || station.publishedAddressJa) {
  throw new Error('Station 2820440 already has placement metadata');
}

station.facilityNameJa = '西宮市北消防署';
station.publishedAddressJa = '兵庫県西宮市名塩新町7番地の1';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official Nishinomiya Fire Department seismic-instrument placement, current JMA station report, and North Fire Station record';
station.sourceUrls = [...new Set([...station.sourceUrls, placementSourceUrl, currentStationSourceUrl, addressSourceUrl])];
station.note = 'Nishinomiya Fire Department records that its seismic instrument was moved to North Fire Station and began JMA transmission there; JMA continues to report 西宮市名塩, and the city publishes the station address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log('Updated 2820440 Nishinomiya North Fire Station.');
