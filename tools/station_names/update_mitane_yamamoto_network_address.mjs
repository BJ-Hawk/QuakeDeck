import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((candidate) => candidate.code === '0534835');
if (!station) throw new Error('Missing station 0534835');
if (station.nameJa !== '三種町森岳') throw new Error(`Unexpected station label ${station.nameJa}`);
if (station.facilityNameJa || station.publishedAddressJa) throw new Error('0534835: placement already present');

station.facilityNameJa = '三種町山本支所（山本地域拠点センター）';
station.publishedAddressJa = '秋田県山本郡三種町森岳字町尻35';
station.metadataStatus = 'Official municipal seismic-network placement and address';
station.note = 'Mitane Town’s council bulletin identifies the prefectural intensity-network system as relocated to the new building when the Yamamoto General Branch moved; the town publishes the current Yamamoto Branch address.';
station.placementPrecision = 'exact_address';
delete station.placementLocalityJa;
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), 'https://www.town.mitane.akita.jp/material/files/group/16/dayori55.pdf', 'https://www.town.mitane.akita.jp/soshikikarasagasu/somuka/1/360.html'])];

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
