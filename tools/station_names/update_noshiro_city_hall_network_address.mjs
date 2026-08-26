import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((candidate) => candidate.code === '0520232');
if (!station) throw new Error('Missing station 0520232');
if (station.nameJa !== '能代市上町') throw new Error(`Unexpected station label ${station.nameJa}`);
if (station.facilityNameJa || station.publishedAddressJa) throw new Error('0520232: placement already present');

station.facilityNameJa = '能代市役所';
station.publishedAddressJa = '秋田県能代市上町1番3号';
station.metadataStatus = 'Official municipal seismic-network placement and address';
station.note = 'Noshiro City’s official construction record identifies an Akita Prefecture seismic-information-network relocation at this City Hall address.';
station.placementPrecision = 'exact_address';
delete station.placementLocalityJa;
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), 'https://www.city.noshiro.lg.jp/up/files/www/city/nyusatsu/koji-kensa/h26/archives/172592download.pdf', 'https://www.city.noshiro.lg.jp/section/somu/somu/gyosei/12244'])];

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
