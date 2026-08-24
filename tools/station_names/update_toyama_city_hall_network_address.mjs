import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const sourceUrls = [
  'https://www.pref.toyama.jp/documents/25269/02.pdf',
  'https://www.city.toyama.lg.jp/bosai/bosai/1010655/1017500.html',
];
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find(({ code }) => code === '1620130');

if (!station) throw new Error('Station 1620130 not found.');
if (station.facilityNameJa !== '富山市役所') {
  throw new Error('Station 1620130 does not identify Toyama City Hall as its facility.');
}
if (station.publishedAddressJa) {
  throw new Error('Station 1620130 already has an address.');
}

station.publishedAddressJa = '富山県富山市新桜町7番38号';
station.metadataStatus = 'Official prefectural placement and municipal address';
station.note = "Toyama Prefecture's official seismic-network table identifies Toyama City Hall as the host site; Toyama City publishes the city hall address.";
station.placementPrecision = 'exact_address';
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), ...sourceUrls])];

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log('Updated station 1620130.');
