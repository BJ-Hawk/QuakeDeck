import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.joho.tagawa.fukuoka.jp/kiji0031997/3_1997_7322_up_324qazyd.pdf';
const data = JSON.parse(readFileSync(path, 'utf8'));
let updated = 0;
for (const station of data.stations) {
  if (station.prefectureJa !== '福岡県' || station.publishedAddressJa) continue;
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), source])];
  station.placementLocalityJa = station.nameJa;
  if (station.note === 'No exact address or precise provider-station metadata is recorded yet.') {
    station.note = 'The published Fukuoka prefectural network list confirms this observation-point locality, but not a parcel or host facility.';
  }
  updated += 1;
}
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`recorded locality source for ${updated} Fukuoka station(s)`);
