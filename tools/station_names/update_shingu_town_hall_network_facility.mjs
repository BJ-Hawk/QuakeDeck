import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find(({ code }) => code === '4034530');

if (!station) throw new Error('Station 4034530 was not found.');

station.facilityNameJa = '新宮町役場';
station.publishedAddressJa = '福岡県糟屋郡新宮町緑ケ浜一丁目1-1';
station.metadataStatus = 'Official Shingu Town Council meter-placement confirmation';
station.note = 'Shingu Town’s official council bulletin directly identifies the town’s seismic-intensity meter as installed at Town Hall; the town publishes the hall’s address.';
station.placementPrecision = 'exact_address';
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), 'https://www.town.shingu.fukuoka.jp/material/files/group/14/gikaikouhou107gou.pdf', 'https://www.town.shingu.fukuoka.jp/soshiki/chiiki_kyodo/6/1/1141.html'])];
delete station.placementLocalityJa;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
