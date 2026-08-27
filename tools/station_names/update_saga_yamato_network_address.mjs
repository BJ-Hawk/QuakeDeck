import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((candidate) => candidate.code === '4120137');
if (!station) throw new Error('Missing station 4120137');
if (station.nameJa !== '佐賀市大和') throw new Error(`Unexpected station label ${station.nameJa}`);
if (station.facilityNameJa || station.publishedAddressJa) throw new Error('4120137: placement already present');

station.facilityNameJa = '佐賀市大和支所';
station.publishedAddressJa = '佐賀県佐賀市大和町大字尼寺1870番地';
station.metadataStatus = 'Official MLIT observation-point placement and address';
station.note = 'The MLIT Kase River management plan identifies the Saga City Yamato observation point as installed on the Yamato Branch premises and publishes its exact address.';
station.placementPrecision = 'exact_address';
delete station.placementLocalityJa;
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), 'https://www.qsr.mlit.go.jp/takeo/site_files/file/kasegawa/kaseijikanrikeikaku1.pdf'])];

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
