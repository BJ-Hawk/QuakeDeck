import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const placementSource = 'https://www.city.echizen.lg.jp/office/010/060/bousai/tiikibousaikeikaku_d/fil/sinnsai.pdf';
const facilitySource = 'https://www.city.echizen.lg.jp/office/200/030/index.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1820932');

if (!station) throw new Error('Station 1820932 not found.');
if (!station.publishedAddressJa && !station.facilityNameJa) {
  station.facilityNameJa = '今立総合支所東側';
  station.publishedAddressJa = '福井県越前市粟田部町9-1-9';
  station.metadataStatus = 'Official municipal seismic-network address';
  station.note = 'Echizen City\'s disaster plan places the prefectural intensity meter on the east side of Imadate General Branch Office; the city publishes the branch office address.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), placementSource, facilitySource])];
  writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  console.log('Updated 1820932.');
} else {
  console.log('1820932 already has placement data; no change made.');
}
