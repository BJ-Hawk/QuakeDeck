import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const intensityMeterSource = 'https://www.city.yamaguchi.lg.jp/soshiki/4/164594.html';
const facilityAddressSource = 'https://www.city.yamaguchi.lg.jp/map/7167.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '3520350');

if (!station) throw new Error('Station 3520350 not found.');
if (!station.publishedAddressJa && !station.facilityNameJa) {
  station.facilityNameJa = '大殿地域交流センター';
  station.publishedAddressJa = '山口県山口市大殿大路120番地4';
  station.metadataStatus = 'Official municipal seismic-network address';
  station.note = 'Yamaguchi City states that the prefectural intensity meter was moved to the Ōtono Community Exchange Center in November 2024 and publishes the facility address.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), intensityMeterSource, facilityAddressSource])];
  writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  console.log('Updated 3520350.');
} else {
  console.log('3520350 already has placement data; no change made.');
}
