import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const sourceUrls = [
  'https://www.city.okinawa.okinawa.jp/documents/25/honpen.pdf',
  'https://www.city.okinawa.okinawa.jp/k050/anshin/shouboukyuukyuu/shoubou/1008/1108.html',
];
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find(({ code }) => code === '4721131');

if (!station) throw new Error('Station 4721131 not found.');
if (station.facilityNameJa || station.publishedAddressJa) {
  throw new Error('Station 4721131 already has placement data.');
}

station.facilityNameJa = '沖縄市消防本部';
station.publishedAddressJa = '沖縄県沖縄市美里五丁目29番1号';
station.metadataStatus = 'Official municipal seismic-network address';
station.note = "Okinawa City's current disaster plan directly places the Misato seismic meter inside the Fire Headquarters; the city publishes the headquarters address.";
station.placementPrecision = 'exact_address';
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), ...sourceUrls])];

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log('Updated station 4721131.');
