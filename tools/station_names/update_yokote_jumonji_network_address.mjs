import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((candidate) => candidate.code === '0520340');
if (!station) throw new Error('Missing station 0520340');
if (station.nameJa !== '横手市十文字町') throw new Error(`Unexpected station label ${station.nameJa}`);
if (station.facilityNameJa || station.publishedAddressJa) throw new Error('0520340: placement already present');

station.facilityNameJa = '横手市十文字庁舎（十文字地域多目的総合施設）';
station.publishedAddressJa = '秋田県横手市十文字町字海道下12番地5';
station.metadataStatus = 'Official municipal seismic-network placement and address';
station.note = 'Yokote City’s construction record identifies the intensity-meter relocation as work for the Jumonji regional multipurpose facility; the city publishes the current Jumonji office address.';
station.placementPrecision = 'exact_address';
delete station.placementLocalityJa;
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), 'https://www.city.yokote.lg.jp/shigoto/1001164/1001363/1005295/1003080.html', 'https://www.city.yokote.lg.jp/shisetsu/1001525/1003643.html'])];

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
