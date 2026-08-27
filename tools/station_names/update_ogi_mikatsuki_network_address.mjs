import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((candidate) => candidate.code === '4120838');
if (!station) throw new Error('Missing station 4120838');
if (station.nameJa !== '小城市三日月') throw new Error(`Unexpected station label ${station.nameJa}`);
if (station.facilityNameJa || station.publishedAddressJa) throw new Error('4120838: placement already present');

station.facilityNameJa = '小城市役所三日月庁舎';
station.publishedAddressJa = '佐賀県小城市三日月町長神田2312番地2';
station.metadataStatus = 'Official MLIT observation-point placement and address';
station.note = 'The MLIT Kase River management plan identifies the Ogi City Mikatsuki observation point as installed on the Mikatsuki Office premises and publishes its exact address.';
station.placementPrecision = 'exact_address';
delete station.placementLocalityJa;
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), 'https://www.qsr.mlit.go.jp/takeo/site_files/file/kasegawa/kaseijikanrikeikaku1.pdf'])];

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
