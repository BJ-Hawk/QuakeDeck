import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const intensityMeterSource = 'https://www.city.bunkyo.lg.jp/documents/9819/honpen.pdf';
const facilityAddressSource = 'https://www.city.bunkyo.lg.jp/b015/p004391.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1310551');

if (!station) throw new Error('Station 1310551 not found.');

if (!station.publishedAddressJa && !station.facilityNameJa) {
  station.facilityNameJa = '文京スポーツセンター';
  station.publishedAddressJa = '東京都文京区大塚三丁目29番2号';
  station.metadataStatus = 'Official municipal seismic-network address';
  station.note = 'Bunkyo City identifies the municipality intensity meter at Bunkyo Sports Center; the city publishes the facility address.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), intensityMeterSource, facilityAddressSource])];
  writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  console.log('Updated 1310551.');
} else {
  console.log('1310551 already has placement data; no change made.');
}
