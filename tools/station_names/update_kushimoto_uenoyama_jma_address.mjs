import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const sourceUrl = 'https://www.jma.go.jp/bosai/oshirase/dokujioshirase_pdf/JPSP/20260217061734_0_Z__J_JPSP_20260217061600_MET_INF_Jdokujioshirase32_NJ000n00_image.pdf';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find(({ code }) => code === '3042831');

if (!station) throw new Error('Station 3042831 not found.');
if (station.facilityNameJa || station.publishedAddressJa) {
  throw new Error('Station 3042831 already has placement data.');
}

station.facilityNameJa = '串本町消防本部古座消防署';
station.publishedAddressJa = '和歌山県東牟婁郡串本町上野山289';
station.metadataStatus = 'Official JMA published address';
station.note = 'JMA\'s official observation-point change notice directly lists this station code, the fire station, and the address.';
station.placementPrecision = 'exact_address';
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log('Updated station 3042831.');
