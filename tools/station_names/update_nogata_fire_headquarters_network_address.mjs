import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find(({ code }) => code === '4020431');

if (!station) throw new Error('Station 4020431 was not found.');

station.facilityNameJa = '直方市消防本部・直方市消防署';
station.publishedAddressJa = '直方市新町2丁目5番10号';
station.metadataStatus = 'Official Nogata City Fire Department records';
station.note = 'Nogata City’s official fire report records installation of the Fukuoka Prefecture seismic-intensity network meter at the Fire Headquarters; the current annual report identifies the Fire Headquarters and Fire Station at this address.';
station.placementPrecision = 'exact_address';
station.sourceUrls = [...new Set([
  ...(station.sourceUrls ?? []),
  'https://www.city.nogata.fukuoka.jp/library/data/kurashi/pdf/shoubouhonbu/h20nenpou.pdf',
  'https://www.city.nogata.fukuoka.jp/var/rev0/0011/8270/1257815933.pdf'
])];
delete station.placementLocalityJa;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
