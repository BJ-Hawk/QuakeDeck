import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((candidate) => candidate.code === '4120143');
if (!station) throw new Error('Missing station 4120143');
if (station.facilityNameJa !== '佐賀市諸富支所') throw new Error(`Unexpected facility ${station.facilityNameJa}`);
if (station.publishedAddressJa) throw new Error('4120143: address already present');

station.publishedAddressJa = '佐賀市諸富町大字為重529番地5';
station.metadataStatus = 'Official prefectural placement and municipal address';
station.note = `${station.prefectureJa} Prefecture's official seismic-network table identifies 佐賀市諸富支所 as the host site; Saga City publishes the facility address.`;
station.placementPrecision = 'exact_address';
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), 'https://www.city.saga.lg.jp/sangyo-machizukuri/sangyo-rodo/1/2/2254.html'])];

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
