import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const intensityMeterSource = 'https://www.city.minato.tokyo.jp/documents/46967/20150311_1.pdf';
const officeAddressSource = 'https://www.city.minato.tokyo.jp/soshiki/bosai.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1310330');

if (!station) throw new Error('Station 1310330 not found.');

if (!station.publishedAddressJa && !station.facilityNameJa) {
  station.facilityNameJa = '港区役所本庁舎';
  station.publishedAddressJa = '東京都港区芝公園一丁目5番25号';
  station.metadataStatus = 'Official municipal seismic-network address';
  station.note = 'Minato City states that its intensity meter is installed at the Ward Office main building in Shiba Park; the ward publishes the building address.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), intensityMeterSource, officeAddressSource])];
  writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  console.log('Updated 1310330.');
} else {
  console.log('1310330 already has placement data; no change made.');
}
