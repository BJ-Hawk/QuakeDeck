import { readFileSync, writeFileSync } from 'node:fs';
const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.city.sapporo.jp/kikikanri/torikumi/keikaku/documents/01r7-jishin-chapter2.pdf';
const codes = new Set(['0110140','0110240','0110241','0110341','0110441','0110540','0110642','0110643','0110740','0110840','0110940','0111040']);
const data = JSON.parse(readFileSync(path, 'utf8'));
for (const station of data.stations) if (codes.has(station.code)) station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), source])];
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
