import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const sourceUrls = [
  'https://www.pref.toyama.jp/documents/25269/02.pdf',
  'https://www.city.takaoka.toyama.jp/gyosei/gyoseijoho/yakusho_madoguchiannai/1/6568.html',
];
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find(({ code }) => code === '1620233');

if (!station) throw new Error('Station 1620233 not found.');
if (station.facilityNameJa !== '福岡総合行政センター') {
  throw new Error('Station 1620233 does not identify the Fukuoka General Administrative Center as its facility.');
}
if (station.publishedAddressJa) throw new Error('Station 1620233 already has an address.');

station.publishedAddressJa = '富山県高岡市福岡町大滝12';
station.metadataStatus = 'Official prefectural placement and municipal address';
station.note = "Toyama Prefecture's official seismic-network table identifies the Fukuoka General Administrative Center as the host site; its successor, the Fukuoka Branch, is published at the same former Fukuoka government-office address.";
station.placementPrecision = 'exact_address';
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), ...sourceUrls])];

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log('Updated station 1620233.');
