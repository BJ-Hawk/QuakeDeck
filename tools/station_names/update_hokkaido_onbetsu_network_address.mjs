import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.pref.hokkaido.lg.jp/fs/3/3/1/1/6/9/0/_/siryou2-1.2-2.pdf';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '0120641');

if (!station) throw new Error('Station 0120641 not found.');
if (station.facilityNameJa || station.publishedAddressJa) {
  throw new Error('Station 0120641 already has placement data.');
}

station.facilityNameJa = '釧路市音別町行政センター';
station.publishedAddressJa = '北海道釧路市音別町中園1-13-4';
station.metadataStatus = 'Official prefectural seismic-network address';
station.note = 'Hokkaido publishes this local-government intensity-meter location as the Onbetsu Administrative Center, with its street address.';
station.placementPrecision = 'exact_address';
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), source])];

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log('Updated 0120641.');
