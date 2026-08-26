import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((candidate) => candidate.code === '0536332');
if (!station) throw new Error('Missing station 0536332');
if (station.nameJa !== '八郎潟町大道') throw new Error(`Unexpected station label ${station.nameJa}`);
if (station.facilityNameJa || station.publishedAddressJa) throw new Error('0536332: placement already present');

station.facilityNameJa = '八郎潟町役場庁舎';
station.publishedAddressJa = '秋田県南秋田郡八郎潟町字大道80番地';
station.metadataStatus = 'Official municipal seismic-network placement and address';
station.note = 'Hachirogata Town’s council record identifies the prefectural intensity-network equipment as relocated for the new Town Hall project; the town publishes the current Hall address.';
station.placementPrecision = 'exact_address';
delete station.placementLocalityJa;
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), 'https://www.town.hachirogata.akita.jp/_res/projects/default_project/_page_/001/003/306/r4.3gatsu.pdf', 'https://www.town.hachirogata.akita.jp/shisetsu/1002923/1002936.html'])];

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
