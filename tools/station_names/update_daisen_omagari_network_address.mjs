import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((candidate) => candidate.code === '0521238');
if (!station) throw new Error('Missing station 0521238');
if (station.nameJa !== '大仙市大曲花園町') throw new Error(`Unexpected station label ${station.nameJa}`);
if (station.facilityNameJa || station.publishedAddressJa) throw new Error('0521238: placement already present');

station.facilityNameJa = '大仙市役所大曲庁舎';
station.publishedAddressJa = '秋田県大仙市大曲花園町1番1号';
station.metadataStatus = 'Official municipal seismic-network placement and address';
station.note = 'Daisen City’s office-renovation record identifies the measuring-intensity-meter relocation as work at the Omagari Office; the city publishes the current office address.';
station.placementPrecision = 'exact_address';
delete station.placementLocalityJa;
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), 'https://www.city.daisen.lg.jp/uploads/contents/archive_0000000671_00/241.pdf', 'https://www.city.daisen.lg.jp/archive/contents-14100'])];

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
