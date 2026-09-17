import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const sourceUrl = 'https://www.city.funabashi.lg.jp/bousai/003/shitsumon/004/p092886.html';
const data = JSON.parse(readFileSync(path, 'utf8'));

const station = data.stations.find((entry) => entry.code === '1220431');
if (!station || station.prefectureJa !== '千葉県' || station.nameJa !== '船橋市湊町') {
  throw new Error('Unexpected station for 1220431');
}
if (station.placementPrecision !== 'municipality_or_ward' || station.facilityNameJa || station.publishedAddressJa) {
  throw new Error('Station 1220431 already has placement metadata');
}

station.facilityNameJa = '船橋市役所';
station.publishedAddressJa = '千葉県船橋市湊町2-10-25';
station.placementPrecision = 'exact_address';
station.metadataStatus = 'Official Funabashi City intensity-meter record';
station.sourceUrls = [...new Set([...station.sourceUrls, sourceUrl])];
station.note = 'Funabashi City explicitly states that its intensity meter is installed at Funabashi City Hall at this address.';
delete station.placementLocalityJa;

data.coverage.publishedAddresses += 1;
data.coverage.exactPlacementAddressUpdates += 1;
data.coverage.localityPlacementRecords -= 1;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log('Updated 1220431 Funabashi City Hall.');
