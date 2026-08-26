import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find((candidate) => candidate.code === '2636631');
if (!station) throw new Error('Missing station 2636631');
if (station.facilityNameJa !== '精華町役場') throw new Error(`Unexpected facility ${station.facilityNameJa}`);
if (station.publishedAddressJa) throw new Error('2636631: address already present');

station.publishedAddressJa = '京都府相楽郡精華町大字南稲八妻小字北尻70番地';
station.metadataStatus = 'Documented seismic-network placement and municipal address';
station.note = `${station.prefectureJa} Prefecture's seismic-network documentation identifies 精華町役場 as the host site; Seika Town publishes the facility address.`;
station.placementPrecision = 'exact_address';
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), 'https://www.town.seika.kyoto.jp/chosei/machiyakuba/index.html'])];

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
