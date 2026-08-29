import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find(({ code }) => code === '4034130');

if (!station) throw new Error('Station 4034130 was not found.');

station.facilityNameJa = '宇美町役場';
station.publishedAddressJa = '福岡県糟屋郡宇美町宇美5丁目1番1号';
station.metadataStatus = 'Official Umi Town disaster plan and town-hall address';
station.note = 'Umi Town’s current disaster-plan appendix directly lists the prefecture-installed meter inside Umi Town Hall; the town publishes the hall’s address.';
station.placementPrecision = 'exact_address';
station.sourceUrls = [...new Set([
  ...(station.sourceUrls ?? []),
  'https://www.town.umi.lg.jp/uploaded/attachment/20731.pdf',
  'https://www.town.umi.lg.jp/life/sub/23/31/144/'
])];
delete station.placementLocalityJa;

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
