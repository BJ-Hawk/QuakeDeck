import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const intensityMeterSource = 'https://www.city.tokyo-nakano.lg.jp/kusei/zaisei/kessan/r5kessan.files/R5kessansetsumeisho.pdf';
const facilityAddressSource = 'https://www.city.tokyo-nakano.lg.jp/about/kuyakusho.html';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((entry) => entry.code === '1311431');

if (!station) throw new Error('Station 1311431 not found.');

if (!station.publishedAddressJa && !station.facilityNameJa) {
  station.facilityNameJa = '中野区役所';
  station.publishedAddressJa = '東京都中野区中野四丁目11番19号';
  station.metadataStatus = 'Official municipal seismic-network address';
  station.note = 'Nakano City records installation of the intensity meter with the new Ward Office move; the ward publishes the office address.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), intensityMeterSource, facilityAddressSource])];
  writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
  console.log('Updated 1311431.');
} else {
  console.log('1311431 already has placement data; no change made.');
}
