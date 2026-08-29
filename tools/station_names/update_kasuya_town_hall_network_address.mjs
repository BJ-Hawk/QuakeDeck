import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const station = data.stations.find(({ code }) => code === '4034930');
if (!station) throw new Error('Station 4034930 was not found.');

station.facilityNameJa = '粕屋町役場';
station.publishedAddressJa = '福岡県糟屋郡粕屋町駕与丁1丁目1-1';
station.metadataStatus = 'Official Kasuya Town disaster plan and town-hall address';
station.note = 'Kasuya Town’s disaster plan identifies the in-office intensity meter used for the JMA-published Kasuya observation point; the town publishes Town Hall’s address.';
station.placementPrecision = 'exact_address';
station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), 'https://www.town.kasuya.fukuoka.jp/s013/020/010/020/file/shinnsaiR4.7.pdf', 'https://www.town.kasuya.fukuoka.jp/li/110/010/index.html'])];
delete station.placementLocalityJa;
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
