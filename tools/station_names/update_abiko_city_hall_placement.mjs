import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const sourceUrl = 'https://www.city.abiko.chiba.jp/anshin/bousai/bousai_info/shindokei.html';
const data = JSON.parse(readFileSync(path, 'utf8'));

const station = data.stations.find((entry) => entry.code === '1222230');
if (!station || station.prefectureJa !== '千葉県' || station.nameJa !== '我孫子市我孫子') {
  throw new Error('Unexpected station for 1222230');
}
if (station.placementPrecision !== 'municipality_or_ward' || station.facilityNameJa || station.publishedAddressJa) {
  throw new Error('Station 1222230 already has placement metadata');
}

station.facilityNameJa = '我孫子市役所';
station.publishedAddressJa = '千葉県我孫子市我孫子1858番地';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official Abiko City intensity-meter record';
station.sourceUrls = [...new Set([...station.sourceUrls, sourceUrl])];
station.note = 'Abiko City explicitly states that the Chiba Prefecture intensity meter is installed on the Main City Hall grounds at this address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log('Updated 1222230 Abiko City Hall.');
