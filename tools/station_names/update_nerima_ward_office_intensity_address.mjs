import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const intensityMeterSource = 'https://www.city.nerima.tokyo.jp/kurashi/bosai/keikaku/chiikibou_r5.files/05honpen_bousaikeikaku_d.pdf';
const facilityAddressSource = 'https://www.city.nerima.tokyo.jp/shisetsu/ku/ku/about.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1312031');

if (!station) throw new Error('Station 1312031 not found.');

if (!station.publishedAddressJa && !station.facilityNameJa) {
  station.facilityNameJa = '練馬区役所';
  station.publishedAddressJa = '東京都練馬区豊玉北六丁目12番1号';
  station.metadataStatus = 'Official municipal seismic-network address';
  station.note = 'Nerima City states that the intensity meter is installed at the Ward Office main building; the ward publishes the office address.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), intensityMeterSource, facilityAddressSource])];
  writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  console.log('Updated 1312031.');
} else {
  console.log('1312031 already has placement data; no change made.');
}
