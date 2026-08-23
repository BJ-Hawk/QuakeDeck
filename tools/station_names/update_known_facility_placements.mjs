import { readFileSync, writeFileSync } from 'node:fs';

const path = new URL('../../outputs/station-name-audit/station_metadata_sources.json', import.meta.url);
const updates = new Map([
  ['4120143', ['佐賀市諸富支所', 'https://www.pref.saga.lg.jp/kiji003118535/3_118535_up_31abmgvh.pdf']]
]);

const data = JSON.parse(readFileSync(path, 'utf8'));
let changed = 0;
for (const station of data.stations) {
  const update = updates.get(station.code);
  if (!update || station.publishedAddressJa || station.facilityNameJa) continue;
  const [facility, source] = update;
  station.facilityNameJa = facility;
  station.metadataStatus = 'Official prefectural network placement facility';
  station.sourceUrls = [...new Set([...station.sourceUrls, source])];
  station.note = 'Saga Prefecture names this facility in its seismic-intensity network maintenance schedule; no street address was inferred.';
  changed += 1;
}
if (changed) writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`updated ${changed} station(s)`);
