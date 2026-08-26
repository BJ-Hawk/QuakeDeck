import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const data = JSON.parse(readFileSync(path, 'utf8'));
const sourceUrl = 'https://www.city.oga.akita.jp/material/files/group/31/bousai_3-1_R6.pdf';
const updates = [
  ['0520632', '男鹿市船川', '船川'],
  ['0520633', '男鹿市角間崎', '角間崎'],
];

for (const [code, stationName, locality] of updates) {
  const station = data.stations.find((candidate) => candidate.code === code);
  if (!station) throw new Error(`Missing station ${code}`);
  if (station.nameJa !== stationName) throw new Error(`${code}: expected ${stationName}, got ${station.nameJa}`);
  if (station.facilityNameJa || station.publishedAddressJa) throw new Error(`${code}: placement unexpectedly present`);
  station.metadataStatus = 'Official municipal seismic-network locality';
  station.note = `Oga City’s official disaster plan identifies the prefecture-installed intensity meter at ${locality}; no host facility or street address is published.`;
  station.placementLocalityJa = locality;
  station.placementPrecision = 'municipality_or_ward';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), sourceUrl])];
}

writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
