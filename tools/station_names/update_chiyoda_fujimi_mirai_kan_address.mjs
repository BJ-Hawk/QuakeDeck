import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const intensityMeterSource = 'https://www.city.chiyoda.lg.jp/documents/30954/taisaku.pdf';
const facilityAddressSource = 'https://www.city.chiyoda.lg.jp/koho/kurashi/bosai/bosai-taisaku/taisei/aed.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1310134');

if (!station) throw new Error('Station 1310134 not found.');

if (!station.publishedAddressJa && !station.facilityNameJa) {
  station.facilityNameJa = '富士見みらい館';
  station.publishedAddressJa = '東京都千代田区富士見一丁目10番3号';
  station.metadataStatus = 'Official municipal seismic-network address';
  station.note = 'Chiyoda City states that its intensity meter was moved to Fujimi Mirai-kan; the city publishes the facility address.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), intensityMeterSource, facilityAddressSource])];
  writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  console.log('Updated 1310134.');
} else {
  console.log('1310134 already has placement data; no change made.');
}
