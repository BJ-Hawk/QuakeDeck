import { readFileSync, writeFileSync } from 'node:fs';

const path = 'outputs/station-name-audit/station_metadata_sources.json';
const source = 'https://www.city.fukui-sakai.lg.jp/anzen/paburikku/documents/05siryou.pdf';
const updates = new Map([
  ['1821030', ['丸岡総合支所', '福井県坂井市丸岡町西里丸岡12-21-1']],
  ['1821032', ['坂井市役所', '福井県坂井市坂井町下新庄1-1']],
  ['1821033', ['春江総合支所', '福井県坂井市春江町随応寺17-10']],
]);

const data = JSON.parse(readFileSync(path, 'utf8'));
let updated = 0;
for (const station of data.stations) {
  const placement = updates.get(station.code);
  if (!placement) continue;
  if (station.publishedAddressJa || station.facilityNameJa) {
    throw new Error(`${station.code} already has placement data.`);
  }
  station.facilityNameJa = placement[0];
  station.publishedAddressJa = placement[1];
  station.metadataStatus = 'Official municipal seismic-network address';
  station.note = 'Sakai City\'s disaster-plan annex directly lists this intensity meter, its host office, and its address.';
  station.placementPrecision = 'exact_address';
  station.sourceUrls = [...new Set([...(station.sourceUrls ?? []), source])];
  updated += 1;
}

if (updated !== updates.size) throw new Error(`Updated ${updated} stations; expected ${updates.size}.`);
writeFileSync(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
console.log(`Updated ${updated} stations.`);
