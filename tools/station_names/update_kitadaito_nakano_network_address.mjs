import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const sourceUrls = [
  'https://www.jma-net.go.jp/daitou/shosai/kansoku/kansoku_jishin.html',
  'https://vill.kitadaito.okinawa.jp/sonsei/gyosei.html',
];
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find(({ code }) => code === '4735831');

if (!station) throw new Error('Station 4735831 not found.');
if (station.facilityNameJa || station.publishedAddressJa) {
  throw new Error('Station 4735831 already has placement data.');
}

station.facilityNameJa = '北大東村役場';
station.publishedAddressJa = '沖縄県島尻郡北大東村中野218';
station.metadataStatus = 'Official JMA placement and municipal address';
station.note = "JMA's Daito Islands office directly identifies the North Daito Nakano intensity meter as being at Kitadaito Village Hall; the village publishes the hall's address.";
station.placementPrecision = 'exact_address';
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), ...sourceUrls])];

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log('Updated station 4735831.');
