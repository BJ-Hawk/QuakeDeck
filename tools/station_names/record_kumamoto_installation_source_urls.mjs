import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const sourceUrl = 'https://data.bodik.jp/dataset/430005_00245/resource/dad15967-bb1a-487f-ab7b-908ff373156d';
const status = 'Kumamoto Prefecture seismic-intensity installation list (2022)';
const data = JSON.parse(readFileSync(path, 'utf8'));

for (const station of data.stations) {
  if (station.metadataStatus !== status) continue;
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
