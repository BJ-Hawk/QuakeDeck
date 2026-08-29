import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find(({ code }) => code === '0820233');

if (!station) throw new Error('Station 0820233 was not found.');
if (station.publishedAddressJa || station.facilityNameJa) {
  throw new Error('Station 0820233 already has placement metadata.');
}

station.facilityNameJa = '日立市役所十王支所';
station.publishedAddressJa = '茨城県日立市十王町友部2581';
station.metadataStatus = 'Official Hitachi City seismic-meter placement confirmation';
station.note = 'Hitachi City’s disaster plan directly identifies this seismic-intensity meter at the Juo Branch Office and the city publishes the branch address.';
station.placementPrecision = 'exact_address';
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), 'https://www.city.hitachi.lg.jp/_res/projects/default_project/_page_/001/004/798/jishin03.pdf', 'https://www.city.hitachi.lg.jp/shisei/shinososhiki/1004542/1016000.html'])];
delete station.placementLocalityJa;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
