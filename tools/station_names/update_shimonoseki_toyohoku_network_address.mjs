import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const intensityMeterSource = 'https://www.city.shimonoseki.lg.jp/uploaded/attachment/94966.pdf';
const facilityAddressSource = 'https://www.city.shimonoseki.lg.jp/soshiki/93/4939.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '3520140');

if (!station) throw new Error('Station 3520140 not found.');
if (!station.publishedAddressJa && !station.facilityNameJa) {
  station.facilityNameJa = '下関市役所豊北総合支所';
  station.publishedAddressJa = '山口県下関市豊北町大字滝部3140番地1';
  station.metadataStatus = 'Official municipal seismic-network address';
  station.note = 'Shimonoseki City identifies the prefectural intensity meter at the Toyohoku General Branch Office; the city publishes the facility address.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), intensityMeterSource, facilityAddressSource])];
  writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  console.log('Updated 3520140.');
} else {
  console.log('3520140 already has placement data; no change made.');
}
